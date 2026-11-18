package rs.belgrade.tvcams.domain.tv

import rs.belgrade.tvcams.domain.favorites.QualityFilter
import rs.belgrade.tvcams.domain.favorites.ZapScope
import rs.belgrade.tvcams.domain.model.Camera
import rs.belgrade.tvcams.domain.model.CameraCategory
import rs.belgrade.tvcams.domain.model.SerbiaCity

enum class OverlayMode {
    HUD_VISIBLE,
    PURE_CAMERA,
    QUICK_STRIP,
}

sealed interface StripFilter {
    data object All : StripFilter
    data object Favorites : StripFilter
    data class City(val city: SerbiaCity) : StripFilter
    data class Category(val category: CameraCategory) : StripFilter
}

sealed interface TvRemoteAction {
    data object ZapNext : TvRemoteAction
    data object ZapPrevious : TvRemoteAction
    data object ZapNextSilent : TvRemoteAction
    data object ToggleInfoOrSelect : TvRemoteAction
    data object ToggleFavoriteCurrent : TvRemoteAction
    data object OpenQuickStrip : TvRemoteAction
    data object ToggleZapScope : TvRemoteAction
    data object CycleQualityFilter : TvRemoteAction
    data object ToggleClockOverlay : TvRemoteAction
    data object ToggleWeatherOverlay : TvRemoteAction
    data object ToggleAirQualityOverlay : TvRemoteAction
    data object ToggleSolarOverlay : TvRemoteAction
    data object ToggleOledProtection : TvRemoteAction
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
    val overlayMode: OverlayMode,
    val hudPinned: Boolean,
    val stripFilter: StripFilter,
    val showClockOverlay: Boolean,
    val showWeatherOverlay: Boolean,
    val showAirQualityOverlay: Boolean,
    val showSolarOverlay: Boolean,
    val oledProtectionEnabled: Boolean,
    val noticeMessage: String?,
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

    val qualityFilteredCameras: List<Camera>
        get() = cameras
            .filter { qualityFilter.matches(it.resolvedQuality) }
            .ifEmpty { cameras }

    val zapPlaylist: List<Camera>
        get() {
            val pool = qualityFilteredCameras
            val scoped = when (zapScope) {
                ZapScope.ALL -> pool
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
            return scoped.sortedBy { it.resolvedQuality.sortRank }
        }

    val activeZapPositionOneBased: Int
        get() {
            val current = activeCamera ?: return 0
            val idx = zapPlaylist.indexOfFirst { it.id == current.id }
            return if (idx >= 0) idx + 1 else 1
        }

    val stripCameras: List<Camera>
        get() {
            val filtered = when (val currentFilter = stripFilter) {
                StripFilter.All -> cameras
                StripFilter.Favorites -> cameras.filter { favoriteIds.contains(it.id) }
                is StripFilter.City -> cameras.filter { it.city == currentFilter.city }
                is StripFilter.Category -> cameras.filter { it.category == currentFilter.category }
            }
            return filtered
                .filter { qualityFilter.matches(it.resolvedQuality) }
                .sortedBy { it.resolvedQuality.sortRank }
        }

    val hdStripCameras: List<Camera>
        get() = stripCameras.filter { it.resolvedQuality.isHd }

    val sdStripCameras: List<Camera>
        get() = stripCameras.filter { !it.resolvedQuality.isHd }

    companion object {
        fun initial(
            cameras: List<Camera>,
            favoriteIds: Set<String> = emptySet(),
            initialCameraId: String? = null,
            zapScope: ZapScope = ZapScope.ALL,
            qualityFilter: QualityFilter = QualityFilter.ALL,
            showClockOverlay: Boolean = true,
            showWeatherOverlay: Boolean = true,
            showAirQualityOverlay: Boolean = true,
            showSolarOverlay: Boolean = true,
            oledProtectionEnabled: Boolean = true,
            initialOverlayMode: OverlayMode = OverlayMode.HUD_VISIBLE,
        ): TvStageState {
            val effectiveScope = if (zapScope == ZapScope.FAVORITES && favoriteIds.isEmpty()) {
                ZapScope.ALL
            } else {
                zapScope
            }
            val matchingPool = cameras
                .filter { qualityFilter.matches(it.resolvedQuality) }
                .sortedBy { it.resolvedQuality.sortRank }
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
                overlayMode = initialOverlayMode,
                hudPinned = false,
                stripFilter = StripFilter.All,
                showClockOverlay = showClockOverlay,
                showWeatherOverlay = showWeatherOverlay,
                showAirQualityOverlay = showAirQualityOverlay,
                showSolarOverlay = showSolarOverlay,
                oledProtectionEnabled = oledProtectionEnabled,
                noticeMessage = null,
            )
        }
    }
}

data class TvStageTransition(
    val state: TvStageState,
    val shouldExitApp: Boolean = false,
)

object TvStageReducer {
    fun reduce(
        state: TvStageState,
        action: TvRemoteAction,
    ): TvStageTransition = when (action) {
        TvRemoteAction.ZapNext -> TvStageTransition(zapBy(state, step = 1, preserveOverlay = false))
        TvRemoteAction.ZapPrevious -> TvStageTransition(zapBy(state, step = -1, preserveOverlay = false))
        TvRemoteAction.ZapNextSilent -> TvStageTransition(zapBy(state, step = 1, preserveOverlay = true))

        TvRemoteAction.ToggleInfoOrSelect -> when (state.overlayMode) {
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
                        noticeMessage = if (added) "Added to Favorites" else "Removed from Favorites",
                    ),
                )
            }
        }

        TvRemoteAction.OpenQuickStrip -> TvStageTransition(
            state.copy(
                overlayMode = OverlayMode.QUICK_STRIP,
                hudPinned = false,
            ),
        )

        TvRemoteAction.ToggleZapScope -> {
            val nextOverlay = if (state.overlayMode == OverlayMode.QUICK_STRIP) {
                OverlayMode.QUICK_STRIP
            } else {
                OverlayMode.HUD_VISIBLE
            }
            val pool = state.qualityFilteredCameras
            when (state.zapScope) {
                ZapScope.ALL -> {
                    val activeCity = state.activeCamera?.city ?: SerbiaCity.BELGRADE
                    val cityCount = pool.count { it.city == activeCity }
                    TvStageTransition(
                        state.copy(
                            zapScope = ZapScope.CITY,
                            overlayMode = nextOverlay,
                            hudPinned = false,
                            noticeMessage = "Zapping ${activeCity.displayLabel()} ($cityCount)",
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
                        val favCams = pool.filter { state.favoriteIds.contains(it.id) }.ifEmpty { pool }
                        val nextActiveId = state.activeCameraId?.takeIf { id -> favCams.any { it.id == id } }
                            ?: favCams.firstOrNull()?.id
                        TvStageTransition(
                            state.copy(
                                zapScope = ZapScope.FAVORITES,
                                activeCameraId = nextActiveId,
                                overlayMode = nextOverlay,
                                hudPinned = false,
                                noticeMessage = "Zapping Favorites (${favCams.size})",
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
                .filter { nextFilter.matches(it.resolvedQuality) }
                .sortedBy { it.resolvedQuality.sortRank }
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
                    (sameCityMatch ?: matching.first()).id
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
            val exists = state.cameras.any { it.id == action.cameraId }
            if (!exists) {
                TvStageTransition(state)
            } else {
                TvStageTransition(
                    state.copy(
                        activeCameraId = action.cameraId,
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

        TvRemoteAction.NavigateBack -> when (state.overlayMode) {
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
                ),
            )
        }
    }

    private fun zapBy(
        state: TvStageState,
        step: Int,
        preserveOverlay: Boolean,
    ): TvStageState {
        val playlist = state.zapPlaylist
        if (playlist.isEmpty()) return state

        val currentIndex = playlist.indexOfFirst { it.id == state.activeCameraId }.let {
            if (it >= 0) it else 0
        }
        val nextIndex = Math.floorMod(currentIndex + step, playlist.size)
        return state.copy(
            activeCameraId = playlist[nextIndex].id,
            overlayMode = if (preserveOverlay) state.overlayMode else OverlayMode.HUD_VISIBLE,
            hudPinned = false,
        )
    }
}
