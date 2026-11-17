package rs.belgrade.tvcams.domain.tv

import rs.belgrade.tvcams.domain.favorites.ZapScope
import rs.belgrade.tvcams.domain.model.Camera
import rs.belgrade.tvcams.domain.model.CameraCategory

enum class OverlayMode {
    HUD_VISIBLE,
    PURE_CAMERA,
    QUICK_STRIP,
}

sealed interface StripFilter {
    data object All : StripFilter
    data object Favorites : StripFilter
    data class Category(val category: CameraCategory) : StripFilter
}

sealed interface TvRemoteAction {
    data object ZapNext : TvRemoteAction
    data object ZapPrevious : TvRemoteAction
    data object ToggleInfoOrSelect : TvRemoteAction
    data object ToggleFavoriteCurrent : TvRemoteAction
    data object OpenQuickStrip : TvRemoteAction
    data object ToggleZapScope : TvRemoteAction
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
    val overlayMode: OverlayMode,
    val hudPinned: Boolean,
    val stripFilter: StripFilter,
    val noticeMessage: String?,
) {
    val activeCamera: Camera?
        get() = cameras.firstOrNull { it.id == activeCameraId } ?: cameras.firstOrNull()

    val isCurrentFavorite: Boolean
        get() = activeCamera?.id?.let { favoriteIds.contains(it) } ?: false

    val zapPlaylist: List<Camera>
        get() = when (zapScope) {
            ZapScope.ALL -> cameras
            ZapScope.FAVORITES -> cameras.filter { favoriteIds.contains(it.id) }.ifEmpty { cameras }
        }

    val activeZapPositionOneBased: Int
        get() {
            val current = activeCamera ?: return 0
            val idx = zapPlaylist.indexOfFirst { it.id == current.id }
            return if (idx >= 0) idx + 1 else 1
        }

    val stripCameras: List<Camera>
        get() = when (val currentFilter = stripFilter) {
            StripFilter.All -> cameras
            StripFilter.Favorites -> cameras.filter { favoriteIds.contains(it.id) }
            is StripFilter.Category -> cameras.filter { it.category == currentFilter.category }
        }

    companion object {
        fun initial(
            cameras: List<Camera>,
            favoriteIds: Set<String> = emptySet(),
            initialCameraId: String? = null,
            zapScope: ZapScope = ZapScope.ALL,
        ): TvStageState {
            val effectiveScope = if (zapScope == ZapScope.FAVORITES && favoriteIds.isEmpty()) {
                ZapScope.ALL
            } else {
                zapScope
            }
            val resolvedId = initialCameraId?.takeIf { id -> cameras.any { it.id == id } }
                ?: cameras.firstOrNull()?.id
            return TvStageState(
                cameras = cameras,
                activeCameraId = resolvedId,
                favoriteIds = favoriteIds.toSet(),
                zapScope = effectiveScope,
                overlayMode = OverlayMode.HUD_VISIBLE,
                hudPinned = false,
                stripFilter = StripFilter.All,
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
        TvRemoteAction.ZapNext -> TvStageTransition(zapBy(state, step = 1))
        TvRemoteAction.ZapPrevious -> TvStageTransition(zapBy(state, step = -1))

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
            if (state.zapScope == ZapScope.ALL) {
                if (state.favoriteIds.isEmpty()) {
                    TvStageTransition(
                        state.copy(
                            noticeMessage = "No favorite cameras saved yet — hold OK on any camera to save it",
                        ),
                    )
                } else {
                    val favCams = state.cameras.filter { state.favoriteIds.contains(it.id) }
                    val nextActiveId = state.activeCameraId?.takeIf { id -> favCams.any { it.id == id } }
                        ?: favCams.firstOrNull()?.id
                    TvStageTransition(
                        state.copy(
                            zapScope = ZapScope.FAVORITES,
                            activeCameraId = nextActiveId,
                            overlayMode = OverlayMode.HUD_VISIBLE,
                            hudPinned = false,
                            noticeMessage = "Zapping Favorites (${favCams.size})",
                        ),
                    )
                }
            } else {
                TvStageTransition(
                    state.copy(
                        zapScope = ZapScope.ALL,
                        overlayMode = OverlayMode.HUD_VISIBLE,
                        hudPinned = false,
                        noticeMessage = "Zapping all cameras (${state.cameras.size})",
                    ),
                )
            }
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

    private fun zapBy(state: TvStageState, step: Int): TvStageState {
        val playlist = state.zapPlaylist
        if (playlist.isEmpty()) return state

        val currentIndex = playlist.indexOfFirst { it.id == state.activeCameraId }.let {
            if (it >= 0) it else 0
        }
        val nextIndex = Math.floorMod(currentIndex + step, playlist.size)
        return state.copy(
            activeCameraId = playlist[nextIndex].id,
            overlayMode = OverlayMode.HUD_VISIBLE,
            hudPinned = false,
        )
    }
}
