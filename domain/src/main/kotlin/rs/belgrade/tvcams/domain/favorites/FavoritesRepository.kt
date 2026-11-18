package rs.belgrade.tvcams.domain.favorites

import kotlinx.coroutines.flow.Flow
import rs.belgrade.tvcams.domain.model.VideoQuality

enum class ZapScope {
    ALL,
    COUNTRY,
    CITY,
    FAVORITES,
    ;

    fun displayLabel(): String = when (this) {
        ALL -> "All cameras"
        COUNTRY -> "Current country"
        CITY -> "Current city"
        FAVORITES -> "Favorites"
    }
}

enum class InfoPanelPosition {
    BOTTOM_CENTER,
    BOTTOM_RIGHT,
    BOTTOM_LEFT,
    TOP_LEFT,
    TOP_RIGHT,
    ;

    val isTop: Boolean
        get() = when (this) {
            TOP_LEFT, TOP_RIGHT -> true
            BOTTOM_CENTER, BOTTOM_RIGHT, BOTTOM_LEFT -> false
        }

    fun next(): InfoPanelPosition = when (this) {
        BOTTOM_CENTER -> BOTTOM_RIGHT
        BOTTOM_RIGHT -> BOTTOM_LEFT
        BOTTOM_LEFT -> TOP_LEFT
        TOP_LEFT -> TOP_RIGHT
        TOP_RIGHT -> BOTTOM_CENTER
    }

    fun displayLabel(): String = when (this) {
        BOTTOM_CENTER -> "Bottom center"
        BOTTOM_RIGHT -> "Bottom right"
        BOTTOM_LEFT -> "Bottom left"
        TOP_LEFT -> "Top left"
        TOP_RIGHT -> "Top right"
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

enum class TransportFilter {
    ALL,
    LIVE_VIDEO_ONLY,
    SNAPSHOT_ONLY,
    ;

    fun matches(isLiveVideo: Boolean): Boolean = when (this) {
        ALL -> true
        LIVE_VIDEO_ONLY -> isLiveVideo
        SNAPSHOT_ONLY -> !isLiveVideo
    }

    fun next(): TransportFilter = when (this) {
        ALL -> LIVE_VIDEO_ONLY
        LIVE_VIDEO_ONLY -> SNAPSHOT_ONLY
        SNAPSHOT_ONLY -> ALL
    }

    fun displayLabel(): String = when (this) {
        ALL -> "All"
        LIVE_VIDEO_ONLY -> "Live video"
        SNAPSHOT_ONLY -> "Snapshots"
    }
}

enum class PerformanceMode {
    AUTO,
    LOW_SPEC,
    MAX_QUALITY,
    ;

    fun next(): PerformanceMode = when (this) {
        AUTO -> LOW_SPEC
        LOW_SPEC -> MAX_QUALITY
        MAX_QUALITY -> AUTO
    }

    fun displayLabel(): String = when (this) {
        AUTO -> "Auto"
        LOW_SPEC -> "Low-spec"
        MAX_QUALITY -> "Max"
    }

    fun shouldUseLowSpecSurface(isLowRamDevice: Boolean): Boolean = when (this) {
        LOW_SPEC -> true
        AUTO -> isLowRamDevice
        MAX_QUALITY -> false
    }

    fun maxVideoHeightPx(displayHeightPx: Int, isLowRamDevice: Boolean): Int = when (this) {
        LOW_SPEC -> 720
        AUTO -> if (isLowRamDevice) {
            minOf(displayHeightPx.coerceAtLeast(720), 1080)
        } else {
            displayHeightPx.coerceIn(720, 2160)
        }
        MAX_QUALITY -> 2160
    }
}

enum class AutoRotateInterval(val seconds: Int) {
    OFF(0),
    SEC_15(15),
    SEC_30(30),
    SEC_60(60),
    SEC_120(120),
    ;

    val enabled: Boolean
        get() = seconds > 0

    val intervalMillis: Long
        get() = seconds * 1_000L

    fun next(): AutoRotateInterval = when (this) {
        OFF -> SEC_15
        SEC_15 -> SEC_30
        SEC_30 -> SEC_60
        SEC_60 -> SEC_120
        SEC_120 -> OFF
    }

    fun displayLabel(): String = when (this) {
        OFF -> "Off"
        SEC_15 -> "15s"
        SEC_30 -> "30s"
        SEC_60 -> "60s"
        SEC_120 -> "2m"
    }

    fun effectiveFor(isScreensaverMode: Boolean): AutoRotateInterval =
        if (isScreensaverMode && this == OFF) DEFAULT_SCREENSAVER else this

    companion object {
        val DEFAULT_SCREENSAVER: AutoRotateInterval = SEC_30
    }
}

data class ViewerPreferences(
    val favoriteIds: Set<String> = emptySet(),
    val lastCameraId: String? = null,
    val zapScope: ZapScope = ZapScope.ALL,
    val qualityFilter: QualityFilter = QualityFilter.ALL,
    val transportFilter: TransportFilter = TransportFilter.ALL,
    val performanceMode: PerformanceMode = PerformanceMode.AUTO,
    val autoRotateInterval: AutoRotateInterval = AutoRotateInterval.OFF,
    val infoPanelPosition: InfoPanelPosition = InfoPanelPosition.BOTTOM_CENTER,
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
    suspend fun setTransportFilter(filter: TransportFilter) = Unit
    suspend fun setPerformanceMode(mode: PerformanceMode) = Unit
    suspend fun setAutoRotateInterval(interval: AutoRotateInterval)
    suspend fun setInfoPanelPosition(position: InfoPanelPosition) = Unit
    suspend fun setShowClockOverlay(enabled: Boolean)
    suspend fun setShowWeatherOverlay(enabled: Boolean)
    suspend fun setShowAirQualityOverlay(enabled: Boolean)
    suspend fun setShowSolarOverlay(enabled: Boolean)
    suspend fun setOledProtectionEnabled(enabled: Boolean)
}
