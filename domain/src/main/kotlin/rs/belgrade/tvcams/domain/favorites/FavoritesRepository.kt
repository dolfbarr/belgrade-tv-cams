package rs.belgrade.tvcams.domain.favorites

import kotlinx.coroutines.flow.Flow
import rs.belgrade.tvcams.domain.model.VideoQuality

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

enum class QualityFilter {
    ALL,
    HD_ONLY,
    SD_ONLY,
    ;

    fun matches(quality: VideoQuality): Boolean = when (this) {
        ALL -> true
        HD_ONLY -> quality.isHd
        SD_ONLY -> !quality.isHd
    }

    fun next(): QualityFilter = when (this) {
        ALL -> HD_ONLY
        HD_ONLY -> SD_ONLY
        SD_ONLY -> ALL
    }

    fun displayLabel(): String = when (this) {
        ALL -> "All"
        HD_ONLY -> "HD only"
        SD_ONLY -> "SD only"
    }
}

data class ViewerPreferences(
    val favoriteIds: Set<String> = emptySet(),
    val lastCameraId: String? = null,
    val zapScope: ZapScope = ZapScope.ALL,
    val qualityFilter: QualityFilter = QualityFilter.ALL,
    val autoHideHud: Boolean = true,
    val showClockOverlay: Boolean = true,
    val showWeatherOverlay: Boolean = true,
    val showAirQualityOverlay: Boolean = true,
    val showSolarOverlay: Boolean = true,
    val oledProtectionEnabled: Boolean = true,
)

interface FavoritesRepository {
    val preferences: Flow<ViewerPreferences>
    suspend fun setFavorites(favoriteIds: Set<String>)
    suspend fun setLastCamera(cameraId: String)
    suspend fun setZapScope(scope: ZapScope)
    suspend fun setQualityFilter(filter: QualityFilter)
    suspend fun setShowClockOverlay(enabled: Boolean)
    suspend fun setShowWeatherOverlay(enabled: Boolean)
    suspend fun setShowAirQualityOverlay(enabled: Boolean)
    suspend fun setShowSolarOverlay(enabled: Boolean)
    suspend fun setOledProtectionEnabled(enabled: Boolean)
}
