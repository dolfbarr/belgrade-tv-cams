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
import rs.belgrade.tvcams.domain.catalog.CatalogBootstrapCoordinator
import rs.belgrade.tvcams.domain.catalog.CatalogSyncOutcome
import rs.belgrade.tvcams.domain.favorites.FavoritesRepository
import rs.belgrade.tvcams.domain.model.Camera
import rs.belgrade.tvcams.domain.model.SerbiaCity
import rs.belgrade.tvcams.domain.model.StreamTransportType
import rs.belgrade.tvcams.domain.tv.OverlayMode
import rs.belgrade.tvcams.domain.tv.TvRemoteAction
import rs.belgrade.tvcams.domain.tv.TvStageReducer
import rs.belgrade.tvcams.domain.tv.TvStageState
import rs.belgrade.tvcams.domain.weather.CurrentWeather
import rs.belgrade.tvcams.domain.weather.WeatherRepository
import rs.belgrade.tvcams.player.PlaybackTelemetry

class CameraStageViewModel(
    private val catalogCoordinator: CatalogBootstrapCoordinator,
    private val favoritesRepository: FavoritesRepository,
    private val weatherRepository: WeatherRepository,
) : ViewModel() {

    private val _stageState = MutableStateFlow(TvStageState.initial(cameras = emptyList()))
    val stageState: StateFlow<TvStageState> = _stageState.asStateFlow()

    private val _telemetry = MutableStateFlow<PlaybackTelemetry>(
        PlaybackTelemetry.Connecting(StreamTransportType.HLS),
    )
    val telemetry: StateFlow<PlaybackTelemetry> = _telemetry.asStateFlow()

    private val _currentWeather = MutableStateFlow<CurrentWeather?>(null)
    val currentWeather: StateFlow<CurrentWeather?> = _currentWeather.asStateFlow()

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
    private var autoHideEnabled: Boolean = true
    private var lastWeatherCity: SerbiaCity? = null

    init {
        bootstrap()
        observeActiveCameraWeather()
    }

    private fun bootstrap() {
        viewModelScope.launch {
            val prefs = favoritesRepository.preferences.first()
            autoHideEnabled = prefs.autoHideHud

            val initialManifest = catalogCoordinator.loadInitial()
            _stageState.value = TvStageState.initial(
                cameras = initialManifest.cameras,
                favoriteIds = prefs.favoriteIds,
                initialCameraId = prefs.lastCameraId,
                zapScope = prefs.zapScope,
                showClockOverlay = prefs.showClockOverlay,
                showWeatherOverlay = prefs.showWeatherOverlay,
            )
            scheduleAutoHideIfEligible()

            when (val sync = catalogCoordinator.syncRemote(initialManifest.revision)) {
                is CatalogSyncOutcome.Applied -> {
                    dispatch(TvRemoteAction.CatalogRefreshed(sync.manifest.cameras))
                }
                CatalogSyncOutcome.UpToDate,
                is CatalogSyncOutcome.Rejected,
                is CatalogSyncOutcome.Offline,
                -> Unit
            }
        }
    }

    private fun observeActiveCameraWeather() {
        viewModelScope.launch {
            combine(
                _stageState.map { it.activeCamera }.distinctUntilChanged { a, b -> a?.id == b?.id },
                _stageState.map { it.showWeatherOverlay }.distinctUntilChanged(),
            ) { cam, showWeather ->
                cam to showWeather
            }.collectLatest { (cam, showWeather) ->
                if (!showWeather || cam == null) {
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

    fun dispatch(action: TvRemoteAction) {
        val previous = _stageState.value
        val transition = TvStageReducer.reduce(previous, action)
        val next = transition.state
        _stageState.value = next

        if (transition.shouldExitApp) {
            _exitEvents.tryEmit(Unit)
            return
        }

        val nextCameraId = next.activeCameraId
        if (nextCameraId != null && nextCameraId != previous.activeCameraId) {
            val nextPrimaryTransport = next.activeCamera?.streams?.firstOrNull()?.type
                ?: StreamTransportType.HLS
            _telemetry.value = PlaybackTelemetry.Connecting(nextPrimaryTransport)
            viewModelScope.launch {
                favoritesRepository.setLastCamera(nextCameraId)
            }
        }

        if (next.favoriteIds != previous.favoriteIds) {
            val updatedFavorites = next.favoriteIds
            viewModelScope.launch {
                favoritesRepository.setFavorites(updatedFavorites)
            }
        }

        if (next.zapScope != previous.zapScope) {
            val updatedScope = next.zapScope
            viewModelScope.launch {
                favoritesRepository.setZapScope(updatedScope)
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

        val nextNotice = next.noticeMessage
        if (nextNotice != null && nextNotice != previous.noticeMessage) {
            scheduleNoticeDismissal()
        }

        scheduleAutoHideIfEligible()
    }

    fun updateTelemetry(newTelemetry: PlaybackTelemetry) {
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
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            require(modelClass.isAssignableFrom(CameraStageViewModel::class.java))
            return CameraStageViewModel(
                catalogCoordinator = catalogCoordinator,
                favoritesRepository = favoritesRepository,
                weatherRepository = weatherRepository,
            ) as T
        }
    }

    private companion object {
        const val ZAP_DEBOUNCE_MS = 250L
        const val HUD_AUTO_HIDE_MS = 5_000L
        const val NOTICE_VISIBLE_MS = 2_800L
        const val WEATHER_POLL_MS = 15L * 60L * 1_000L
    }
}
