package rs.belgrade.tvcams.domain.favorites

import kotlinx.coroutines.flow.Flow

enum class ZapScope {
    ALL,
    CITY,
    FAVORITES,
    ;

    fun displayLabel(): String = when (this) {
        ALL -> "All cameras"
        CITY -> "Current city"
        FAVORITES -> "Favorites"
    }
}

data class ViewerPreferences(
    val favoriteIds: Set<String> = emptySet(),
    val lastCameraId: String? = null,
    val zapScope: ZapScope = ZapScope.ALL,
    val autoHideHud: Boolean = true,
    val showClockOverlay: Boolean = true,
    val showWeatherOverlay: Boolean = true,
)

interface FavoritesRepository {
    val preferences: Flow<ViewerPreferences>
    suspend fun setFavorites(favoriteIds: Set<String>)
    suspend fun setLastCamera(cameraId: String)
    suspend fun setZapScope(scope: ZapScope)
    suspend fun setShowClockOverlay(enabled: Boolean)
    suspend fun setShowWeatherOverlay(enabled: Boolean)
}
