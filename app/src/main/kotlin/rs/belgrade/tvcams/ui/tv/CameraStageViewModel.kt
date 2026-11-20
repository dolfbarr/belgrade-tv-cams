package rs.belgrade.tvcams.ui.tv

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import rs.belgrade.tvcams.domain.airquality.AirQualityRepository
import rs.belgrade.tvcams.domain.airquality.CurrentAirQuality
import rs.belgrade.tvcams.domain.catalog.CatalogBootstrapCoordinator
import rs.belgrade.tvcams.domain.catalog.CatalogSyncOutcome
import rs.belgrade.tvcams.domain.currency.BaseCurrencyPreference
import rs.belgrade.tvcams.domain.currency.CountryCurrencyResolver
import rs.belgrade.tvcams.domain.currency.CurrencyQuote
import rs.belgrade.tvcams.domain.currency.CurrencyRepository
import rs.belgrade.tvcams.domain.favorites.AutoRotateInterval
import rs.belgrade.tvcams.domain.favorites.FavoritesRepository
import rs.belgrade.tvcams.domain.model.Camera
import rs.belgrade.tvcams.domain.model.SerbiaCity
import rs.belgrade.tvcams.domain.model.StreamTransportType
import rs.belgrade.tvcams.domain.tv.CatalogBootstrapState
import rs.belgrade.tvcams.domain.tv.OverlayMode
import rs.belgrade.tvcams.domain.tv.TvRemoteAction
import rs.belgrade.tvcams.domain.tv.TvStageReducer
import rs.belgrade.tvcams.domain.tv.TvStageState
import rs.belgrade.tvcams.domain.weather.CurrentWeather
import rs.belgrade.tvcams.domain.weather.WeatherRepository
import rs.belgrade.tvcams.player.PlaybackTelemetry
import rs.belgrade.tvcams.tvchannel.TvHomeChannelSynchronizer

class CameraStageViewModel(
    private val catalogCoordinator: CatalogBootstrapCoordinator,
    private val favoritesRepository: FavoritesRepository,
    private val weatherRepository: WeatherRepository,
    private val airQualityRepository: AirQualityRepository,
    private val currencyRepository: CurrencyRepository? = null,
    private val homeChannelSynchronizer: TvHomeChannelSynchronizer? = null,
    private val initialDeepLinkCameraId: String? = null,
    private val isScreensaverMode: Boolean = false,
) : ViewModel() {

    private val _stageState = MutableStateFlow(
        TvStageState.initial(
            cameras = emptyList(),
            initialOverlayMode = if (isScreensaverMode) OverlayMode.PURE_CAMERA else OverlayMode.HUD_VISIBLE,
            catalogBootstrapState = CatalogBootstrapState.Loading,
        ),
    )
    val stageState: StateFlow<TvStageState> = _stageState.asStateFlow()

    private val _telemetry = MutableStateFlow<PlaybackTelemetry>(
        PlaybackTelemetry.Connecting(StreamTransportType.HLS),
    )
    val telemetry: StateFlow<PlaybackTelemetry> = _telemetry.asStateFlow()

    private val _streamRetryNonce = MutableStateFlow(0)
    val streamRetryNonce: StateFlow<Int> = _streamRetryNonce.asStateFlow()

    private val _currentWeather = MutableStateFlow<CurrentWeather?>(null)
    val currentWeather: StateFlow<CurrentWeather?> = _currentWeather.asStateFlow()

    private val _currentAirQuality = MutableStateFlow<CurrentAirQuality?>(null)
    val currentAirQuality: StateFlow<CurrentAirQuality?> = _currentAirQuality.asStateFlow()

    private val _currentCurrencyQuote = MutableStateFlow<CurrencyQuote?>(null)
    val currentCurrencyQuote: StateFlow<CurrencyQuote?> = _currentCurrencyQuote.asStateFlow()

    private val _exitEvents = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val exitEvents = _exitEvents.asSharedFlow()

    @OptIn(FlowPreview::class)
    val debouncedStreamCamera: StateFlow<Camera?> = _stageState
        .map { it.activeCamera }
        .distinctUntilChanged { a, b -> a?.id == b?.id }
        .debounce(ZAP_DEBOUNCE_MS)
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000L),
            initialValue = null,
        )

    private var autoHideJob: Job? = null
    private var noticeDismissJob: Job? = null
    private var channelCommitJob: Job? = null
    private var autoHideEnabled: Boolean = true
    private var lastWeatherCity: SerbiaCity? = null
    private var lastAqiCity: SerbiaCity? = null
    private var lastCurrencyIso: String? = null
    private var pendingDeepLinkCameraId: String? = initialDeepLinkCameraId

    private val _consecutiveAutoSkips = MutableStateFlow(0)
    val consecutiveAutoSkips: StateFlow<Int> = _consecutiveAutoSkips.asStateFlow()

    init {
        bootstrap()
        observeActiveCameraWeather()
        observeActiveCameraAirQuality()
        observeActiveCameraCurrency()
        observeAutoRotation()
        observeOfflineAutoSkip()
    }

    private fun bootstrap() {
        viewModelScope.launch {
            _stageState.value = _stageState.value.copy(
                catalogBootstrapState = CatalogBootstrapState.Loading,
            )
            val prefs = runCatching { favoritesRepository.preferences.first() }.getOrNull()
            if (prefs != null) {
                autoHideEnabled = prefs.autoHideHud
            }

            val initialManifestResult = runCatching { catalogCoordinator.loadInitial() }
            val initialManifest = initialManifestResult.getOrNull()
            if (initialManifest == null || initialManifest.cameras.isEmpty()) {
                val errMsg = initialManifestResult.exceptionOrNull()?.message
                    ?.takeIf { it.isNotBlank() }
                    ?: "Seed and cached camera manifests could not be read"
                _stageState.value = _stageState.value.copy(
                    catalogBootstrapState = CatalogBootstrapState.Failed(errMsg),
                    isSyncingRemoteCatalog = false,
                )
                return@launch
            }

            val targetCameraId = pendingDeepLinkCameraId ?: prefs?.lastCameraId
            pendingDeepLinkCameraId = null

            _stageState.value = if (prefs != null) {
                TvStageState.initial(
                    cameras = initialManifest.cameras,
                    favoriteIds = prefs.favoriteIds,
                    initialCameraId = targetCameraId,
                    zapScope = prefs.zapScope,
                    qualityFilter = prefs.qualityFilter,
                    transportFilter = prefs.transportFilter,
                    performanceMode = prefs.performanceMode,
                    autoRotateInterval = prefs.autoRotateInterval,
                    infoPanelPosition = prefs.infoPanelPosition,
                    showClockOverlay = prefs.showClockOverlay,
                    showWeatherOverlay = prefs.showWeatherOverlay,
                    showAirQualityOverlay = prefs.showAirQualityOverlay,
                    showSolarOverlay = prefs.showSolarOverlay,
                    showCurrencyOverlay = prefs.showCurrencyOverlay,
                    baseCurrencyPreference = prefs.baseCurrencyPreference,
                    streamAudioEnabled = prefs.streamAudioEnabled,
                    weatherUnitSystem = prefs.weatherUnitSystem,
                    oledProtectionEnabled = prefs.oledProtectionEnabled,
                    initialOverlayMode = if (isScreensaverMode) OverlayMode.PURE_CAMERA else OverlayMode.HUD_VISIBLE,
                    catalogBootstrapState = CatalogBootstrapState.Ready,
                    isSyncingRemoteCatalog = true,
                )
            } else {
                TvStageState.initial(
                    cameras = initialManifest.cameras,
                    initialCameraId = targetCameraId,
                    initialOverlayMode = if (isScreensaverMode) OverlayMode.PURE_CAMERA else OverlayMode.HUD_VISIBLE,
                    catalogBootstrapState = CatalogBootstrapState.Ready,
                    isSyncingRemoteCatalog = true,
                )
            }
            scheduleAutoHideIfEligible()

            if (!isScreensaverMode) {
                homeChannelSynchronizer?.syncChannel(
                    cameras = initialManifest.cameras,
                    favoriteIds = prefs?.favoriteIds.orEmpty(),
                )
            }

            try {
                when (val sync = catalogCoordinator.syncRemote(initialManifest.revision)) {
                    is CatalogSyncOutcome.Applied -> {
                        val freshCameras = sync.manifest.cameras
                        dispatch(TvRemoteAction.CatalogRefreshed(freshCameras))
                        if (!isScreensaverMode) {
                            homeChannelSynchronizer?.syncChannel(
                                cameras = freshCameras,
                                favoriteIds = _stageState.value.favoriteIds,
                            )
                        }
                    }
                    CatalogSyncOutcome.UpToDate,
                    is CatalogSyncOutcome.Rejected,
                    is CatalogSyncOutcome.Offline,
                    -> Unit
                }
            } finally {
                _stageState.value = _stageState.value.copy(isSyncingRemoteCatalog = false)
            }
        }
    }

    private fun observeActiveCameraWeather() {
        viewModelScope.launch {
            combine(
                _stageState.map { it.activeCamera }.distinctUntilChanged { a, b -> a?.id == b?.id },
                _stageState.map { it.showWeatherOverlay || it.showSolarOverlay }.distinctUntilChanged(),
            ) { cam, needed ->
                cam to needed
            }.collectLatest { (cam, needed) ->
                if (!needed || cam == null) {
                    return@collectLatest
                }
                val camCity = cam.city
                if (lastWeatherCity != null && lastWeatherCity != camCity) {
                    _currentWeather.value = null
                }
                val coords = cam.coordinates
                while (isActive) {
                    val fetched = weatherRepository.getCurrentWeather(coords.lat, coords.lon)
                    if (fetched != null) {
                        lastWeatherCity = camCity
                        _currentWeather.value = fetched
                    }
                    delay(WEATHER_POLL_MS)
                }
            }
        }
    }

    private fun observeActiveCameraAirQuality() {
        viewModelScope.launch {
            combine(
                _stageState.map { it.activeCamera }.distinctUntilChanged { a, b -> a?.id == b?.id },
                _stageState.map { it.showAirQualityOverlay }.distinctUntilChanged(),
            ) { cam, showAqi ->
                cam to showAqi
            }.collectLatest { (cam, showAqi) ->
                if (!showAqi || cam == null) {
                    return@collectLatest
                }
                val camCity = cam.city
                if (lastAqiCity != null && lastAqiCity != camCity) {
                    _currentAirQuality.value = null
                }
                val coords = cam.coordinates
                while (isActive) {
                    val fetched = airQualityRepository.getCurrentAirQuality(coords.lat, coords.lon)
                    if (fetched != null) {
                        lastAqiCity = camCity
                        _currentAirQuality.value = fetched
                    }
                    delay(AIR_QUALITY_POLL_MS)
                }
            }
        }
    }

    private data class CurrencyObservationKey(
        val countryIso: String?,
        val showCurrency: Boolean,
        val basePreference: BaseCurrencyPreference,
    )

    private fun observeActiveCameraCurrency() {
        val repo = currencyRepository ?: return
        viewModelScope.launch {
            _stageState
                .map { state ->
                    CurrencyObservationKey(
                        countryIso = state.activeCamera?.resolvedCountryIso,
                        showCurrency = state.showCurrencyOverlay,
                        basePreference = state.baseCurrencyPreference,
                    )
                }
                .distinctUntilChanged()
                .collectLatest { key ->
                    val iso = key.countryIso
                    if (!key.showCurrency || iso.isNullOrBlank()) {
                        _currentCurrencyQuote.value = null
                        lastCurrencyIso = null
                        return@collectLatest
                    }

                    val baseCode = key.basePreference.resolveCurrencyCode()
                    val localCandidates = CountryCurrencyResolver.resolveCurrencyCandidates(iso)
                    if (localCandidates.isEmpty() || localCandidates.contains(baseCode)) {
                        _currentCurrencyQuote.value = null
                        lastCurrencyIso = iso
                        return@collectLatest
                    }

                    if (lastCurrencyIso != null && lastCurrencyIso != iso) {
                        _currentCurrencyQuote.value = null
                    }
                    while (isActive) {
                        val quote = repo.getQuoteForCountry(baseCode, iso)
                        lastCurrencyIso = iso
                        _currentCurrencyQuote.value = quote
                        delay(CURRENCY_POLL_MS)
                    }
                }
        }
    }

    private data class AutoRotationTrigger(
        val effectiveInterval: AutoRotateInterval,
        val isInteractiveOverlayOpen: Boolean,
        val activeCameraId: String?,
        val hasMultipleCameras: Boolean,
    )

    private fun observeAutoRotation() {
        viewModelScope.launch {
            _stageState
                .map { state ->
                    val interactiveOpen = state.overlayMode == OverlayMode.QUICK_STRIP ||
                        state.overlayMode == OverlayMode.SETTINGS ||
                        state.overlayMode == OverlayMode.SEARCH
                    AutoRotationTrigger(
                        effectiveInterval = state.autoRotateInterval.effectiveFor(isScreensaverMode),
                        isInteractiveOverlayOpen = interactiveOpen,
                        activeCameraId = state.activeCameraId,
                        hasMultipleCameras = state.cameras.size > 1,
                    )
                }
                .distinctUntilChanged()
                .collectLatest { trigger ->
                    if (!trigger.effectiveInterval.enabled ||
                        trigger.isInteractiveOverlayOpen ||
                        !trigger.hasMultipleCameras
                    ) {
                        return@collectLatest
                    }
                    while (isActive) {
                        delay(trigger.effectiveInterval.intervalMillis)
                        dispatch(TvRemoteAction.ZapNextSilent)
                    }
                }
        }
    }

    private data class OfflineSkipCandidate(
        val shouldSkip: Boolean,
        val activeCameraId: String?,
        val skippedCount: Int,
    )

    private fun observeOfflineAutoSkip() {
        viewModelScope.launch {
            combine(
                _stageState,
                _telemetry,
                _consecutiveAutoSkips,
            ) { state, telemetry, skipped ->
                val effectiveRotate = state.autoRotateInterval.effectiveFor(isScreensaverMode)
                val interactiveOpen = state.overlayMode == OverlayMode.QUICK_STRIP ||
                    state.overlayMode == OverlayMode.SETTINGS ||
                    state.overlayMode == OverlayMode.SEARCH
                val playlistSize = state.zapPlaylist.size
                val canSkip = effectiveRotate.enabled &&
                    !interactiveOpen &&
                    playlistSize > 1 &&
                    telemetry is PlaybackTelemetry.DegradedRetrying &&
                    skipped < playlistSize - 1
                OfflineSkipCandidate(
                    shouldSkip = canSkip,
                    activeCameraId = state.activeCameraId,
                    skippedCount = skipped,
                )
            }
                .distinctUntilChanged()
                .collectLatest { candidate ->
                    if (!candidate.shouldSkip) return@collectLatest
                    delay(OFFLINE_AUTO_SKIP_GRACE_MS)
                    _consecutiveAutoSkips.value = candidate.skippedCount + 1
                    dispatch(TvRemoteAction.AutoSkipOfflineCamera)
                }
        }
    }

    fun selectDeepLinkedCamera(cameraId: String) {
        val currentCameras = _stageState.value.cameras
        if (currentCameras.isEmpty()) {
            pendingDeepLinkCameraId = cameraId
        } else {
            dispatch(TvRemoteAction.SelectCamera(cameraId))
        }
    }

    fun dispatch(action: TvRemoteAction) {
        if (action == TvRemoteAction.RetryCatalogLoad) {
            bootstrap()
            return
        }
        if (action == TvRemoteAction.RetryActiveStream) {
            _consecutiveAutoSkips.value = 0
            _streamRetryNonce.value += 1
            return
        }
        if (action is TvRemoteAction.SelectCamera ||
            action == TvRemoteAction.ZapNext ||
            action == TvRemoteAction.ZapPrevious ||
            action == TvRemoteAction.CommitChannelDigits
        ) {
            _consecutiveAutoSkips.value = 0
        }

        val previous = _stageState.value
        val transition = TvStageReducer.reduce(previous, action)
        val next = transition.state
        _stageState.value = next

        if (transition.shouldExitApp) {
            _exitEvents.tryEmit(Unit)
            return
        }

        if (next.pendingChannelDigits != previous.pendingChannelDigits) {
            channelCommitJob?.cancel()
            if (next.pendingChannelDigits.isNotEmpty()) {
                channelCommitJob = viewModelScope.launch {
                    delay(CHANNEL_COMMIT_DELAY_MS)
                    dispatch(TvRemoteAction.CommitChannelDigits)
                }
            }
        }

        val nextCameraId = next.activeCameraId
        if (nextCameraId != null && nextCameraId != previous.activeCameraId) {
            val nextPrimaryTransport = next.activeCamera?.streams?.firstOrNull()?.type
                ?: StreamTransportType.HLS
            _telemetry.value = PlaybackTelemetry.Connecting(nextPrimaryTransport)
            if (!isScreensaverMode) {
                viewModelScope.launch {
                    favoritesRepository.setLastCamera(nextCameraId)
                }
            }
        }

        if (next.favoriteIds != previous.favoriteIds) {
            val updatedFavorites = next.favoriteIds
            val currentCameras = next.cameras
            viewModelScope.launch {
                favoritesRepository.setFavorites(updatedFavorites)
                homeChannelSynchronizer?.syncChannel(
                    cameras = currentCameras,
                    favoriteIds = updatedFavorites,
                )
            }
        }

        if (next.zapScope != previous.zapScope) {
            val updatedScope = next.zapScope
            viewModelScope.launch {
                favoritesRepository.setZapScope(updatedScope)
            }
        }

        if (next.qualityFilter != previous.qualityFilter) {
            val updatedQuality = next.qualityFilter
            viewModelScope.launch {
                favoritesRepository.setQualityFilter(updatedQuality)
            }
        }

        if (next.transportFilter != previous.transportFilter) {
            val updatedTransport = next.transportFilter
            viewModelScope.launch {
                favoritesRepository.setTransportFilter(updatedTransport)
            }
        }

        if (next.performanceMode != previous.performanceMode) {
            val updatedPerf = next.performanceMode
            viewModelScope.launch {
                favoritesRepository.setPerformanceMode(updatedPerf)
            }
        }

        if (next.autoRotateInterval != previous.autoRotateInterval) {
            val updatedRotate = next.autoRotateInterval
            viewModelScope.launch {
                favoritesRepository.setAutoRotateInterval(updatedRotate)
            }
        }

        if (next.infoPanelPosition != previous.infoPanelPosition) {
            val updatedPosition = next.infoPanelPosition
            viewModelScope.launch {
                favoritesRepository.setInfoPanelPosition(updatedPosition)
            }
        }

        if (next.showClockOverlay != previous.showClockOverlay) {
            val showClock = next.showClockOverlay
            viewModelScope.launch {
                favoritesRepository.setShowClockOverlay(showClock)
            }
        }

        if (next.showWeatherOverlay != previous.showWeatherOverlay) {
            val showWeather = next.showWeatherOverlay
            viewModelScope.launch {
                favoritesRepository.setShowWeatherOverlay(showWeather)
            }
        }

        if (next.showAirQualityOverlay != previous.showAirQualityOverlay) {
            val showAqi = next.showAirQualityOverlay
            viewModelScope.launch {
                favoritesRepository.setShowAirQualityOverlay(showAqi)
            }
        }

        if (next.showSolarOverlay != previous.showSolarOverlay) {
            val showSolar = next.showSolarOverlay
            viewModelScope.launch {
                favoritesRepository.setShowSolarOverlay(showSolar)
            }
        }

        if (next.showCurrencyOverlay != previous.showCurrencyOverlay) {
            val showCurrency = next.showCurrencyOverlay
            viewModelScope.launch {
                favoritesRepository.setShowCurrencyOverlay(showCurrency)
            }
        }

        if (next.baseCurrencyPreference != previous.baseCurrencyPreference) {
            val basePref = next.baseCurrencyPreference
            viewModelScope.launch {
                favoritesRepository.setBaseCurrencyPreference(basePref)
            }
        }

        if (next.streamAudioEnabled != previous.streamAudioEnabled) {
            val audioEnabled = next.streamAudioEnabled
            viewModelScope.launch {
                favoritesRepository.setStreamAudioEnabled(audioEnabled)
            }
        }

        if (next.weatherUnitSystem != previous.weatherUnitSystem) {
            val units = next.weatherUnitSystem
            viewModelScope.launch {
                favoritesRepository.setWeatherUnitSystem(units)
            }
        }

        if (next.oledProtectionEnabled != previous.oledProtectionEnabled) {
            val oledEnabled = next.oledProtectionEnabled
            viewModelScope.launch {
                favoritesRepository.setOledProtectionEnabled(oledEnabled)
            }
        }

        val nextNotice = next.noticeMessage
        if (nextNotice != null && nextNotice != previous.noticeMessage) {
            scheduleNoticeDismissal()
        }

        scheduleAutoHideIfEligible()
    }

    fun updateTelemetry(newTelemetry: PlaybackTelemetry) {
        if (newTelemetry is PlaybackTelemetry.PlayingLive) {
            _consecutiveAutoSkips.value = 0
        }
        _telemetry.value = newTelemetry
    }

    private fun scheduleAutoHideIfEligible() {
        autoHideJob?.cancel()
        val current = _stageState.value
        if (!autoHideEnabled) return
        if (current.overlayMode == OverlayMode.HUD_VISIBLE && !current.hudPinned) {
            autoHideJob = viewModelScope.launch {
                delay(HUD_AUTO_HIDE_MS)
                dispatch(TvRemoteAction.AutoHideTick)
            }
        }
    }

    private fun scheduleNoticeDismissal() {
        noticeDismissJob?.cancel()
        noticeDismissJob = viewModelScope.launch {
            delay(NOTICE_VISIBLE_MS)
            dispatch(TvRemoteAction.ClearNotice)
        }
    }

    class Factory(
        private val catalogCoordinator: CatalogBootstrapCoordinator,
        private val favoritesRepository: FavoritesRepository,
        private val weatherRepository: WeatherRepository,
        private val airQualityRepository: AirQualityRepository,
        private val currencyRepository: CurrencyRepository? = null,
        private val homeChannelSynchronizer: TvHomeChannelSynchronizer? = null,
        private val initialDeepLinkCameraId: String? = null,
        private val isScreensaverMode: Boolean = false,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            require(modelClass.isAssignableFrom(CameraStageViewModel::class.java))
            return CameraStageViewModel(
                catalogCoordinator = catalogCoordinator,
                favoritesRepository = favoritesRepository,
                weatherRepository = weatherRepository,
                airQualityRepository = airQualityRepository,
                currencyRepository = currencyRepository,
                homeChannelSynchronizer = homeChannelSynchronizer,
                initialDeepLinkCameraId = initialDeepLinkCameraId,
                isScreensaverMode = isScreensaverMode,
            ) as T
        }
    }

    internal companion object {
        const val ZAP_DEBOUNCE_MS = 250L
        const val HUD_AUTO_HIDE_MS = 5_000L
        const val NOTICE_VISIBLE_MS = 2_800L
        const val CHANNEL_COMMIT_DELAY_MS = 1_200L
        const val OFFLINE_AUTO_SKIP_GRACE_MS = 1_500L
        const val WEATHER_POLL_MS = 15L * 60L * 1_000L
        const val AIR_QUALITY_POLL_MS = 30L * 60L * 1_000L
        const val CURRENCY_POLL_MS = 60L * 60L * 1_000L
    }
}
