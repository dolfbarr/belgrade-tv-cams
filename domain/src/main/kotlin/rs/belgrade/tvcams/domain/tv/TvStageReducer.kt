package rs.belgrade.tvcams.domain.tv

import rs.belgrade.tvcams.domain.favorites.AutoRotateInterval
import rs.belgrade.tvcams.domain.favorites.InfoPanelPosition
import rs.belgrade.tvcams.domain.favorites.PerformanceMode
import rs.belgrade.tvcams.domain.favorites.QualityFilter
import rs.belgrade.tvcams.domain.favorites.TransportFilter
import rs.belgrade.tvcams.domain.favorites.ZapScope
import rs.belgrade.tvcams.domain.model.Camera
import rs.belgrade.tvcams.domain.model.CameraCategory
import rs.belgrade.tvcams.domain.model.CameraCity
import rs.belgrade.tvcams.domain.model.CameraCountry

private val CAMERA_DISPLAY_COMPARATOR: Comparator<Camera> =
    compareBy<Camera> { it.resolvedCountry.sortOrder }
        .thenBy { it.resolvedCountry.code }
        .thenByDescending { it.isLiveVideo }
        .thenBy { it.resolvedQuality.sortRank }

enum class OverlayMode {
    HUD_VISIBLE,
    PURE_CAMERA,
    QUICK_STRIP,
    SETTINGS,
    SEARCH,
}

sealed interface CatalogBootstrapState {
    data object Loading : CatalogBootstrapState
    data object Ready : CatalogBootstrapState
    data class Failed(val reason: String) : CatalogBootstrapState
}

sealed interface StripFilter {
    data object All : StripFilter
    data object Popular : StripFilter
    data object Favorites : StripFilter
    data class Country(val country: CameraCountry) : StripFilter
    data class City(val city: CameraCity) : StripFilter
    data class Category(val category: CameraCategory) : StripFilter
}

data class CountryCameraGroup(
    val country: CameraCountry,
    val countryLabel: String,
    val countryIso: String,
    val hdCameras: List<Camera>,
    val sdCameras: List<Camera>,
) {
    val totalCount: Int
        get() = hdCameras.size + sdCameras.size
}

sealed interface TvRemoteAction {
    data object ZapNext : TvRemoteAction
    data object ZapPrevious : TvRemoteAction
    data object ZapNextSilent : TvRemoteAction
    data object AutoSkipOfflineCamera : TvRemoteAction
    data object ToggleInfoOrSelect : TvRemoteAction
    data object ToggleFavoriteCurrent : TvRemoteAction
    data object OpenQuickStrip : TvRemoteAction
    data object OpenSettings : TvRemoteAction
    data object OpenSearch : TvRemoteAction
    data class UpdateSearchQuery(val query: String) : TvRemoteAction
    data class AppendSearchChar(val text: String) : TvRemoteAction
    data object BackspaceSearchQuery : TvRemoteAction
    data object ClearSearchQuery : TvRemoteAction
    data class AppendChannelDigit(val digit: Int) : TvRemoteAction
    data object CommitChannelDigits : TvRemoteAction
    data object CancelChannelDigits : TvRemoteAction
    data object ToggleZapScope : TvRemoteAction
    data object CycleQualityFilter : TvRemoteAction
    data object CycleTransportFilter : TvRemoteAction
    data object CyclePerformanceMode : TvRemoteAction
    data object CycleAutoRotate : TvRemoteAction
    data object CycleInfoPanelPosition : TvRemoteAction
    data object ToggleClockOverlay : TvRemoteAction
    data object ToggleWeatherOverlay : TvRemoteAction
    data object ToggleAirQualityOverlay : TvRemoteAction
    data object ToggleSolarOverlay : TvRemoteAction
    data object ToggleOledProtection : TvRemoteAction
    data object ShowAllQualities : TvRemoteAction
    data object ShowAllTransports : TvRemoteAction
    data object ResetFilters : TvRemoteAction
    data object RetryActiveStream : TvRemoteAction
    data object RetryCatalogLoad : TvRemoteAction
    data class SelectCamera(val cameraId: String) : TvRemoteAction
    data class SelectStripFilter(val filter: StripFilter) : TvRemoteAction
    data object NavigateBack : TvRemoteAction
    data object AutoHideTick : TvRemoteAction
    data object ClearNotice : TvRemoteAction
    data class CatalogRefreshed(val cameras: List<Camera>) : TvRemoteAction
}

data class TvStageState(
    val cameras: List<Camera>,
    val activeCameraId: String?,
    val favoriteIds: Set<String>,
    val zapScope: ZapScope,
    val qualityFilter: QualityFilter,
    val transportFilter: TransportFilter = TransportFilter.ALL,
    val performanceMode: PerformanceMode = PerformanceMode.AUTO,
    val autoRotateInterval: AutoRotateInterval,
    val infoPanelPosition: InfoPanelPosition = InfoPanelPosition.BOTTOM_CENTER,
    val overlayMode: OverlayMode,
    val modalReturnOverlayMode: OverlayMode = OverlayMode.PURE_CAMERA,
    val searchQuery: String = "",
    val pendingChannelDigits: String = "",
    val hudPinned: Boolean,
    val stripFilter: StripFilter,
    val showClockOverlay: Boolean,
    val showWeatherOverlay: Boolean,
    val showAirQualityOverlay: Boolean,
    val showSolarOverlay: Boolean,
    val oledProtectionEnabled: Boolean,
    val noticeMessage: String?,
    val catalogBootstrapState: CatalogBootstrapState = CatalogBootstrapState.Ready,
    val isSyncingRemoteCatalog: Boolean = false,
) {
    val activeCamera: Camera?
        get() = cameras.firstOrNull { it.id == activeCameraId } ?: cameras.firstOrNull()

    val isCurrentFavorite: Boolean
        get() = activeCamera?.id?.let { favoriteIds.contains(it) } ?: false

    val hasAnyAmbientOverlay: Boolean
        get() = showClockOverlay || showWeatherOverlay || showAirQualityOverlay || showSolarOverlay

    val hdTotalCount: Int
        get() = cameras.count { it.resolvedQuality.isHd }

    val sdTotalCount: Int
        get() = cameras.count { !it.resolvedQuality.isHd }

    val liveVideoTotalCount: Int
        get() = cameras.count { it.isLiveVideo }

    val snapshotTotalCount: Int
        get() = cameras.count { !it.isLiveVideo }

    val featuredTotalCount: Int
        get() = qualityFilteredCameras.count { it.isFeatured }

    val searchResults: List<Camera>
        get() = CameraSearchMatcher.filterCameras(cameras, searchQuery, this)

    val pendingChannelPreviewCamera: Camera?
        get() {
            if (pendingChannelDigits.isEmpty()) return null
            val num = pendingChannelDigits.toIntOrNull() ?: return null
            val playlist = zapPlaylist
            return if (num in 1..playlist.size) playlist[num - 1] else null
        }

    /**
     * Dynamically discovers all countries present in the catalog, preserving curated order
     * for built-in countries and appending community-contributed countries alphabetically.
     */
    val availableCountries: List<CameraCountry>
        get() = cameras
            .map { it.resolvedCountry }
            .distinct()
            .sortedWith(compareBy<CameraCountry> { it.sortOrder }.thenBy { it.code })

    fun countryDisplayLabel(country: CameraCountry): String {
        val explicit = cameras.firstOrNull {
            it.resolvedCountry == country && !it.countryName.isNullOrBlank()
        }?.countryName
        return country.displayLabel(explicit)
    }

    fun countryIsoCode(country: CameraCountry): String {
        val explicit = cameras.firstOrNull {
            it.resolvedCountry == country && !it.countryIso.isNullOrBlank()
        }?.countryIso
        return country.isoAlpha2(explicit)
    }

    fun cityDisplayLabel(city: CameraCity): String {
        val explicit = cameras.firstOrNull {
            it.city == city && !it.cityName.isNullOrBlank()
        }?.cityName
        return city.displayLabel(explicit)
    }

    fun citiesForCountry(country: CameraCountry): List<CameraCity> = cameras
        .filter { it.resolvedCountry == country }
        .map { it.city }
        .distinct()
        .sortedWith(compareBy<CameraCity> { it.sortOrder }.thenBy { it.code })

    val availableCities: List<CameraCity>
        get() = availableCountries.flatMap { citiesForCountry(it) }

    val qualityFilteredCameras: List<Camera>
        get() = cameras
            .filter {
                qualityFilter.matches(it.resolvedQuality) &&
                    transportFilter.matches(it.isLiveVideo)
            }
            .ifEmpty { cameras }

    val zapPlaylist: List<Camera>
        get() {
            val pool = qualityFilteredCameras
            val scoped = when (zapScope) {
                ZapScope.ALL -> pool
                ZapScope.COUNTRY -> {
                    val country = activeCamera?.resolvedCountry
                    if (country != null) {
                        pool.filter { it.resolvedCountry == country }.ifEmpty { pool }
                    } else {
                        pool
                    }
                }
                ZapScope.CITY -> {
                    val city = activeCamera?.city
                    if (city != null) {
                        pool.filter { it.city == city }.ifEmpty { pool }
                    } else {
                        pool
                    }
                }
                ZapScope.FAVORITES -> pool.filter { favoriteIds.contains(it.id) }.ifEmpty { pool }
            }
            return scoped.sortedWith(CAMERA_DISPLAY_COMPARATOR)
        }

    val activeZapPositionOneBased: Int
        get() {
            val current = activeCamera ?: return 0
            val idx = zapPlaylist.indexOfFirst { it.id == current.id }
            return if (idx >= 0) idx + 1 else 1
        }

    val selectedStripCountry: CameraCountry?
        get() = when (val currentFilter = stripFilter) {
            is StripFilter.Country -> currentFilter.country
            is StripFilter.City -> {
                cameras.firstOrNull { it.city == currentFilter.city }?.resolvedCountry
                    ?: currentFilter.city.defaultCountry
            }
            StripFilter.All,
            StripFilter.Popular,
            StripFilter.Favorites,
            is StripFilter.Category,
            -> null
        }

    val stripScopeCamerasUnfiltered: List<Camera>
        get() = when (val currentFilter = stripFilter) {
            StripFilter.All -> cameras
            StripFilter.Popular -> cameras.filter { it.isFeatured }
            StripFilter.Favorites -> cameras.filter { favoriteIds.contains(it.id) }
            is StripFilter.Country -> cameras.filter { it.resolvedCountry == currentFilter.country }
            is StripFilter.City -> cameras.filter { it.city == currentFilter.city }
            is StripFilter.Category -> cameras.filter { it.category == currentFilter.category }
        }

    val stripCameras: List<Camera>
        get() = stripScopeCamerasUnfiltered
            .filter {
                qualityFilter.matches(it.resolvedQuality) &&
                    transportFilter.matches(it.isLiveVideo)
            }
            .sortedWith(CAMERA_DISPLAY_COMPARATOR)

    val hdStripCameras: List<Camera>
        get() = stripCameras.filter { it.resolvedQuality.isHd }

    val sdStripCameras: List<Camera>
        get() = stripCameras.filter { !it.resolvedQuality.isHd }

    val stripCountryGroups: List<CountryCameraGroup>
        get() {
            val byCountry = stripCameras.groupBy { it.resolvedCountry }
            return availableCountries.mapNotNull { country ->
                val cams = byCountry[country].orEmpty()
                if (cams.isEmpty()) {
                    null
                } else {
                    CountryCameraGroup(
                        country = country,
                        countryLabel = countryDisplayLabel(country),
                        countryIso = countryIsoCode(country),
                        hdCameras = cams.filter { it.resolvedQuality.isHd },
                        sdCameras = cams.filter { !it.resolvedQuality.isHd },
                    )
                }
            }
        }

    /**
     * Exact item index of [activeCamera] inside Quick Strip's camera LazyRow,
     * accounting for preceding [QualitySectionCard] HD/SD headers per country.
     * Returns -1 when [activeCamera] is not present in [stripCameras].
     */
    val activeStripLazyItemIndex: Int
        get() {
            val targetId = activeCamera?.id ?: return -1
            var cursor = 0
            for (group in stripCountryGroups) {
                if (group.hdCameras.isNotEmpty()) {
                    cursor += 1 // section_hd_<country>
                    val idx = group.hdCameras.indexOfFirst { it.id == targetId }
                    if (idx >= 0) return cursor + idx
                    cursor += group.hdCameras.size
                }
                if (group.sdCameras.isNotEmpty()) {
                    cursor += 1 // section_sd_<country>
                    val idx = group.sdCameras.indexOfFirst { it.id == targetId }
                    if (idx >= 0) return cursor + idx
                    cursor += group.sdCameras.size
                }
            }
            return -1
        }

    companion object {
        fun initial(
            cameras: List<Camera>,
            favoriteIds: Set<String> = emptySet(),
            initialCameraId: String? = null,
            zapScope: ZapScope = ZapScope.ALL,
            qualityFilter: QualityFilter = QualityFilter.ALL,
            transportFilter: TransportFilter = TransportFilter.ALL,
            performanceMode: PerformanceMode = PerformanceMode.AUTO,
            autoRotateInterval: AutoRotateInterval = AutoRotateInterval.OFF,
            infoPanelPosition: InfoPanelPosition = InfoPanelPosition.BOTTOM_CENTER,
            showClockOverlay: Boolean = true,
            showWeatherOverlay: Boolean = true,
            showAirQualityOverlay: Boolean = true,
            showSolarOverlay: Boolean = true,
            oledProtectionEnabled: Boolean = true,
            initialOverlayMode: OverlayMode = OverlayMode.HUD_VISIBLE,
            catalogBootstrapState: CatalogBootstrapState = CatalogBootstrapState.Ready,
            isSyncingRemoteCatalog: Boolean = false,
        ): TvStageState {
            val effectiveScope = if (zapScope == ZapScope.FAVORITES && favoriteIds.isEmpty()) {
                ZapScope.ALL
            } else {
                zapScope
            }
            val matchingPool = cameras
                .filter {
                    qualityFilter.matches(it.resolvedQuality) &&
                        transportFilter.matches(it.isLiveVideo)
                }
                .sortedWith(CAMERA_DISPLAY_COMPARATOR)
                .ifEmpty { cameras }
            val resolvedId = initialCameraId
                ?.takeIf { id -> matchingPool.any { it.id == id } }
                ?: matchingPool.firstOrNull()?.id
            return TvStageState(
                cameras = cameras,
                activeCameraId = resolvedId,
                favoriteIds = favoriteIds.toSet(),
                zapScope = effectiveScope,
                qualityFilter = qualityFilter,
                transportFilter = transportFilter,
                performanceMode = performanceMode,
                autoRotateInterval = autoRotateInterval,
                infoPanelPosition = infoPanelPosition,
                overlayMode = initialOverlayMode,
                hudPinned = false,
                stripFilter = StripFilter.All,
                showClockOverlay = showClockOverlay,
                showWeatherOverlay = showWeatherOverlay,
                showAirQualityOverlay = showAirQualityOverlay,
                showSolarOverlay = showSolarOverlay,
                oledProtectionEnabled = oledProtectionEnabled,
                noticeMessage = null,
                catalogBootstrapState = catalogBootstrapState,
                isSyncingRemoteCatalog = isSyncingRemoteCatalog,
            )
        }
    }
}

data class TvStageTransition(
    val state: TvStageState,
    val shouldExitApp: Boolean = false,
)

object TvStageReducer {
    private const val MAX_SEARCH_QUERY_LEN = 40

    fun reduce(
        state: TvStageState,
        action: TvRemoteAction,
    ): TvStageTransition = when (action) {
        TvRemoteAction.ZapNext -> TvStageTransition(
            zapBy(state.copy(pendingChannelDigits = ""), step = 1, preserveOverlay = false),
        )
        TvRemoteAction.ZapPrevious -> TvStageTransition(
            zapBy(state.copy(pendingChannelDigits = ""), step = -1, preserveOverlay = false),
        )
        TvRemoteAction.ZapNextSilent -> TvStageTransition(
            zapBy(state, step = 1, preserveOverlay = true),
        )
        TvRemoteAction.AutoSkipOfflineCamera -> TvStageTransition(
            zapBy(state, step = 1, preserveOverlay = true).copy(
                noticeMessage = "Stream offline — skipping…",
            ),
        )

        TvRemoteAction.ToggleInfoOrSelect -> {
            if (state.pendingChannelDigits.isNotEmpty()) {
                commitChannelDigits(state)
            } else {
                when (state.overlayMode) {
                    OverlayMode.HUD_VISIBLE -> TvStageTransition(
                        state.copy(
                            overlayMode = OverlayMode.PURE_CAMERA,
                            hudPinned = false,
                            noticeMessage = null,
                        ),
                    )
                    OverlayMode.PURE_CAMERA -> TvStageTransition(
                        state.copy(
                            overlayMode = OverlayMode.HUD_VISIBLE,
                            hudPinned = true,
                        ),
                    )
                    OverlayMode.QUICK_STRIP -> TvStageTransition(
                        state.copy(
                            overlayMode = OverlayMode.HUD_VISIBLE,
                            hudPinned = false,
                        ),
                    )
                    OverlayMode.SETTINGS,
                    OverlayMode.SEARCH,
                    -> TvStageTransition(state)
                }
            }
        }

        TvRemoteAction.ToggleFavoriteCurrent -> {
            val currentId = state.activeCamera?.id
            if (currentId == null) {
                TvStageTransition(state)
            } else {
                val updatedFavorites = if (state.favoriteIds.contains(currentId)) {
                    state.favoriteIds - currentId
                } else {
                    state.favoriteIds + currentId
                }
                val added = updatedFavorites.contains(currentId)
                val updatedScope = if (state.zapScope == ZapScope.FAVORITES && updatedFavorites.isEmpty()) {
                    ZapScope.ALL
                } else {
                    state.zapScope
                }
                TvStageTransition(
                    state.copy(
                        favoriteIds = updatedFavorites,
                        zapScope = updatedScope,
                        noticeMessage = if (added) "Added to favorites" else "Removed from favorites",
                    ),
                )
            }
        }

        TvRemoteAction.OpenQuickStrip -> TvStageTransition(
            state.copy(
                overlayMode = OverlayMode.QUICK_STRIP,
                pendingChannelDigits = "",
                hudPinned = false,
            ),
        )

        TvRemoteAction.OpenSettings -> {
            val returnMode = if (state.overlayMode == OverlayMode.QUICK_STRIP) {
                OverlayMode.QUICK_STRIP
            } else if (state.overlayMode == OverlayMode.SETTINGS) {
                state.modalReturnOverlayMode
            } else {
                OverlayMode.PURE_CAMERA
            }
            TvStageTransition(
                state.copy(
                    overlayMode = OverlayMode.SETTINGS,
                    modalReturnOverlayMode = returnMode,
                    pendingChannelDigits = "",
                    hudPinned = false,
                ),
            )
        }

        TvRemoteAction.OpenSearch -> {
            val returnMode = if (state.overlayMode == OverlayMode.QUICK_STRIP) {
                OverlayMode.QUICK_STRIP
            } else if (state.overlayMode == OverlayMode.SEARCH) {
                state.modalReturnOverlayMode
            } else {
                OverlayMode.PURE_CAMERA
            }
            TvStageTransition(
                state.copy(
                    overlayMode = OverlayMode.SEARCH,
                    modalReturnOverlayMode = returnMode,
                    pendingChannelDigits = "",
                    hudPinned = false,
                ),
            )
        }

        is TvRemoteAction.UpdateSearchQuery -> TvStageTransition(
            state.copy(searchQuery = action.query.take(MAX_SEARCH_QUERY_LEN)),
        )

        is TvRemoteAction.AppendSearchChar -> TvStageTransition(
            state.copy(searchQuery = (state.searchQuery + action.text).take(MAX_SEARCH_QUERY_LEN)),
        )

        TvRemoteAction.BackspaceSearchQuery -> TvStageTransition(
            state.copy(
                searchQuery = if (state.searchQuery.isNotEmpty()) {
                    state.searchQuery.dropLast(1)
                } else {
                    ""
                },
            ),
        )

        TvRemoteAction.ClearSearchQuery -> TvStageTransition(
            state.copy(searchQuery = ""),
        )

        is TvRemoteAction.AppendChannelDigit -> {
            if (action.digit !in 0..9 ||
                (state.pendingChannelDigits.isEmpty() && action.digit == 0)
            ) {
                TvStageTransition(state)
            } else {
                val updated = if (state.pendingChannelDigits.length >= 3) {
                    state.pendingChannelDigits.drop(1) + action.digit
                } else {
                    state.pendingChannelDigits + action.digit
                }
                TvStageTransition(state.copy(pendingChannelDigits = updated))
            }
        }

        TvRemoteAction.CommitChannelDigits -> commitChannelDigits(state)

        TvRemoteAction.CancelChannelDigits -> TvStageTransition(
            state.copy(pendingChannelDigits = ""),
        )

        TvRemoteAction.ToggleZapScope -> {
            val nextOverlay = if (
                state.overlayMode == OverlayMode.QUICK_STRIP ||
                state.overlayMode == OverlayMode.SETTINGS
            ) {
                state.overlayMode
            } else {
                OverlayMode.HUD_VISIBLE
            }
            val pool = state.qualityFilteredCameras
            val hasMultipleCountries = pool.map { it.resolvedCountry }.distinct().size > 1
            when (state.zapScope) {
                ZapScope.ALL -> {
                    if (hasMultipleCountries) {
                        val activeCountry = state.activeCamera?.resolvedCountry ?: CameraCountry.SERBIA
                        val countryCount = pool.count { it.resolvedCountry == activeCountry }
                        TvStageTransition(
                            state.copy(
                                zapScope = ZapScope.COUNTRY,
                                overlayMode = nextOverlay,
                                hudPinned = false,
                                noticeMessage = "Zapping ${state.countryDisplayLabel(activeCountry)} ($countryCount)",
                            ),
                        )
                    } else {
                        val activeCity = state.activeCamera?.city ?: CameraCity.BELGRADE
                        val cityCount = pool.count { it.city == activeCity }
                        TvStageTransition(
                            state.copy(
                                zapScope = ZapScope.CITY,
                                overlayMode = nextOverlay,
                                hudPinned = false,
                                noticeMessage = "Zapping ${state.cityDisplayLabel(activeCity)} ($cityCount)",
                            ),
                        )
                    }
                }

                ZapScope.COUNTRY -> {
                    val activeCity = state.activeCamera?.city ?: CameraCity.BELGRADE
                    val cityCount = pool.count { it.city == activeCity }
                    TvStageTransition(
                        state.copy(
                            zapScope = ZapScope.CITY,
                            overlayMode = nextOverlay,
                            hudPinned = false,
                            noticeMessage = "Zapping ${state.cityDisplayLabel(activeCity)} ($cityCount)",
                        ),
                    )
                }

                ZapScope.CITY -> {
                    if (state.favoriteIds.isEmpty()) {
                        TvStageTransition(
                            state.copy(
                                zapScope = ZapScope.ALL,
                                overlayMode = nextOverlay,
                                hudPinned = false,
                                noticeMessage = "Zapping all cameras (${pool.size})",
                            ),
                        )
                    } else {
                        val favCams = pool
                            .filter { state.favoriteIds.contains(it.id) }
                            .sortedWith(CAMERA_DISPLAY_COMPARATOR)
                            .ifEmpty { pool }
                        val nextActiveId = state.activeCameraId?.takeIf { id -> favCams.any { it.id == id } }
                            ?: favCams.firstOrNull()?.id
                        TvStageTransition(
                            state.copy(
                                zapScope = ZapScope.FAVORITES,
                                activeCameraId = nextActiveId,
                                overlayMode = nextOverlay,
                                hudPinned = false,
                                noticeMessage = "Zapping favorites (${favCams.size})",
                            ),
                        )
                    }
                }

                ZapScope.FAVORITES -> TvStageTransition(
                    state.copy(
                        zapScope = ZapScope.ALL,
                        overlayMode = nextOverlay,
                        hudPinned = false,
                        noticeMessage = "Zapping all cameras (${pool.size})",
                    ),
                )
            }
        }

        TvRemoteAction.CycleQualityFilter -> {
            val nextFilter = state.qualityFilter.next()
            val matching = state.cameras
                .filter {
                    nextFilter.matches(it.resolvedQuality) &&
                        state.transportFilter.matches(it.isLiveVideo)
                }
                .sortedWith(CAMERA_DISPLAY_COMPARATOR)
            if (matching.isEmpty()) {
                TvStageTransition(
                    state.copy(
                        qualityFilter = QualityFilter.ALL,
                        noticeMessage = "No ${nextFilter.displayLabel()} cameras available",
                    ),
                )
            } else {
                val activeCam = state.activeCamera
                val nextActiveId = if (activeCam != null && nextFilter.matches(activeCam.resolvedQuality)) {
                    activeCam.id
                } else {
                    val sameCityMatch = activeCam?.city?.let { city ->
                        matching.firstOrNull { it.city == city }
                    }
                    val sameCountryMatch = activeCam?.resolvedCountry?.let { country ->
                        matching.firstOrNull { it.resolvedCountry == country }
                    }
                    (sameCityMatch ?: sameCountryMatch ?: matching.first()).id
                }
                TvStageTransition(
                    state.copy(
                        qualityFilter = nextFilter,
                        activeCameraId = nextActiveId,
                        noticeMessage = "Quality: ${nextFilter.displayLabel()} (${matching.size})",
                    ),
                )
            }
        }

        TvRemoteAction.CycleTransportFilter -> {
            val nextTransport = state.transportFilter.next()
            val matching = state.cameras
                .filter {
                    state.qualityFilter.matches(it.resolvedQuality) &&
                        nextTransport.matches(it.isLiveVideo)
                }
                .sortedWith(CAMERA_DISPLAY_COMPARATOR)
            if (matching.isEmpty()) {
                TvStageTransition(
                    state.copy(
                        transportFilter = TransportFilter.ALL,
                        noticeMessage = "No ${nextTransport.displayLabel()} cameras available",
                    ),
                )
            } else {
                val activeCam = state.activeCamera
                val nextActiveId = if (activeCam != null && nextTransport.matches(activeCam.isLiveVideo)) {
                    activeCam.id
                } else {
                    val sameCityMatch = activeCam?.city?.let { city ->
                        matching.firstOrNull { it.city == city }
                    }
                    val sameCountryMatch = activeCam?.resolvedCountry?.let { country ->
                        matching.firstOrNull { it.resolvedCountry == country }
                    }
                    (sameCityMatch ?: sameCountryMatch ?: matching.first()).id
                }
                TvStageTransition(
                    state.copy(
                        transportFilter = nextTransport,
                        activeCameraId = nextActiveId,
                        noticeMessage = "Stream: ${nextTransport.displayLabel()} (${matching.size})",
                    ),
                )
            }
        }

        TvRemoteAction.ShowAllQualities -> TvStageTransition(
            state.copy(
                qualityFilter = QualityFilter.ALL,
                noticeMessage = "Quality: All (${state.cameras.size})",
            ),
        )

        TvRemoteAction.ShowAllTransports -> TvStageTransition(
            state.copy(
                transportFilter = TransportFilter.ALL,
                noticeMessage = "Stream: All (${state.cameras.size})",
            ),
        )

        TvRemoteAction.ResetFilters -> TvStageTransition(
            state.copy(
                qualityFilter = QualityFilter.ALL,
                transportFilter = TransportFilter.ALL,
                stripFilter = StripFilter.All,
                noticeMessage = "Showing all cameras (${state.cameras.size})",
            ),
        )

        TvRemoteAction.RetryActiveStream,
        TvRemoteAction.RetryCatalogLoad,
        -> TvStageTransition(state)

        TvRemoteAction.CyclePerformanceMode -> {
            val nextMode = state.performanceMode.next()
            TvStageTransition(
                state.copy(
                    performanceMode = nextMode,
                    noticeMessage = "Performance: ${nextMode.displayLabel()}",
                ),
            )
        }

        TvRemoteAction.CycleAutoRotate -> {
            val nextInterval = state.autoRotateInterval.next()
            TvStageTransition(
                state.copy(
                    autoRotateInterval = nextInterval,
                    noticeMessage = "Auto-rotate: ${nextInterval.displayLabel()}",
                ),
            )
        }

        TvRemoteAction.CycleInfoPanelPosition -> {
            val nextPosition = state.infoPanelPosition.next()
            TvStageTransition(
                state.copy(
                    infoPanelPosition = nextPosition,
                    noticeMessage = "Panel position: ${nextPosition.displayLabel()}",
                ),
            )
        }

        TvRemoteAction.ToggleClockOverlay -> {
            val nextClock = !state.showClockOverlay
            TvStageTransition(
                state.copy(
                    showClockOverlay = nextClock,
                    noticeMessage = if (nextClock) "Clock overlay: On" else "Clock overlay: Off",
                ),
            )
        }

        TvRemoteAction.ToggleWeatherOverlay -> {
            val nextWeather = !state.showWeatherOverlay
            TvStageTransition(
                state.copy(
                    showWeatherOverlay = nextWeather,
                    noticeMessage = if (nextWeather) "Weather overlay: On" else "Weather overlay: Off",
                ),
            )
        }

        TvRemoteAction.ToggleAirQualityOverlay -> {
            val nextAqi = !state.showAirQualityOverlay
            TvStageTransition(
                state.copy(
                    showAirQualityOverlay = nextAqi,
                    noticeMessage = if (nextAqi) "Air quality overlay: On" else "Air quality overlay: Off",
                ),
            )
        }

        TvRemoteAction.ToggleSolarOverlay -> {
            val nextSolar = !state.showSolarOverlay
            TvStageTransition(
                state.copy(
                    showSolarOverlay = nextSolar,
                    noticeMessage = if (nextSolar) "Sun & golden hour: On" else "Sun & golden hour: Off",
                ),
            )
        }

        TvRemoteAction.ToggleOledProtection -> {
            val nextOled = !state.oledProtectionEnabled
            TvStageTransition(
                state.copy(
                    oledProtectionEnabled = nextOled,
                    noticeMessage = if (nextOled) "OLED burn-in guard: On" else "OLED burn-in guard: Off",
                ),
            )
        }

        is TvRemoteAction.SelectCamera -> {
            val target = state.cameras.firstOrNull { it.id == action.cameraId }
            if (target == null) {
                TvStageTransition(state)
            } else {
                val updatedQualityFilter = if (state.qualityFilter.matches(target.resolvedQuality)) {
                    state.qualityFilter
                } else {
                    QualityFilter.ALL
                }
                val updatedTransportFilter = if (state.transportFilter.matches(target.isLiveVideo)) {
                    state.transportFilter
                } else {
                    TransportFilter.ALL
                }
                TvStageTransition(
                    state.copy(
                        activeCameraId = target.id,
                        qualityFilter = updatedQualityFilter,
                        transportFilter = updatedTransportFilter,
                        pendingChannelDigits = "",
                        overlayMode = OverlayMode.HUD_VISIBLE,
                        hudPinned = false,
                    ),
                )
            }
        }

        is TvRemoteAction.SelectStripFilter -> TvStageTransition(
            state.copy(
                stripFilter = action.filter,
                overlayMode = OverlayMode.QUICK_STRIP,
            ),
        )

        TvRemoteAction.NavigateBack -> {
            if (state.pendingChannelDigits.isNotEmpty()) {
                TvStageTransition(
                    state = state.copy(pendingChannelDigits = ""),
                    shouldExitApp = false,
                )
            } else {
                when (state.overlayMode) {
                    OverlayMode.SETTINGS,
                    OverlayMode.SEARCH,
                    -> TvStageTransition(
                        state = state.copy(
                            overlayMode = state.modalReturnOverlayMode,
                            hudPinned = false,
                            noticeMessage = null,
                        ),
                        shouldExitApp = false,
                    )
                    OverlayMode.QUICK_STRIP,
                    OverlayMode.HUD_VISIBLE,
                    -> TvStageTransition(
                        state = state.copy(
                            overlayMode = OverlayMode.PURE_CAMERA,
                            hudPinned = false,
                            noticeMessage = null,
                        ),
                        shouldExitApp = false,
                    )
                    OverlayMode.PURE_CAMERA -> TvStageTransition(
                        state = state,
                        shouldExitApp = true,
                    )
                }
            }
        }

        TvRemoteAction.AutoHideTick -> {
            if (state.overlayMode == OverlayMode.HUD_VISIBLE && !state.hudPinned) {
                TvStageTransition(
                    state.copy(
                        overlayMode = OverlayMode.PURE_CAMERA,
                        noticeMessage = null,
                    ),
                )
            } else {
                TvStageTransition(state)
            }
        }

        TvRemoteAction.ClearNotice -> TvStageTransition(state.copy(noticeMessage = null))

        is TvRemoteAction.CatalogRefreshed -> {
            val preservedId = state.activeCameraId?.takeIf { id -> action.cameras.any { it.id == id } }
                ?: action.cameras.firstOrNull()?.id
            TvStageTransition(
                state.copy(
                    cameras = action.cameras,
                    activeCameraId = preservedId,
                    catalogBootstrapState = CatalogBootstrapState.Ready,
                ),
            )
        }
    }

    private fun commitChannelDigits(state: TvStageState): TvStageTransition {
        if (state.pendingChannelDigits.isEmpty()) return TvStageTransition(state)
        val playlist = state.zapPlaylist
        val channelNum = state.pendingChannelDigits.toIntOrNull()
        return if (channelNum != null && channelNum in 1..playlist.size) {
            val target = playlist[channelNum - 1]
            TvStageTransition(
                state.copy(
                    activeCameraId = target.id,
                    pendingChannelDigits = "",
                    overlayMode = OverlayMode.HUD_VISIBLE,
                    hudPinned = false,
                    noticeMessage = "Channel %02d · %s".format(channelNum, target.name),
                ),
            )
        } else {
            TvStageTransition(
                state.copy(
                    pendingChannelDigits = "",
                    noticeMessage = "Channel ${state.pendingChannelDigits} out of range (1–${playlist.size})",
                ),
            )
        }
    }

    private fun zapBy(
        state: TvStageState,
        step: Int,
        preserveOverlay: Boolean,
    ): TvStageState {
        val scopedPlaylist = state.zapPlaylist
        val playlist = if (preserveOverlay && scopedPlaylist.size <= 1) {
            state.qualityFilteredCameras
                .sortedWith(CAMERA_DISPLAY_COMPARATOR)
                .ifEmpty { scopedPlaylist }
        } else {
            scopedPlaylist
        }
        if (playlist.isEmpty()) return state

        val currentIndex = playlist.indexOfFirst { it.id == state.activeCameraId }.let {
            if (it >= 0) it else 0
        }
        val nextIndex = Math.floorMod(currentIndex + step, playlist.size)
        return state.copy(
            activeCameraId = playlist[nextIndex].id,
            overlayMode = if (preserveOverlay) state.overlayMode else OverlayMode.HUD_VISIBLE,
            hudPinned = if (preserveOverlay) state.hudPinned else false,
        )
    }
}
