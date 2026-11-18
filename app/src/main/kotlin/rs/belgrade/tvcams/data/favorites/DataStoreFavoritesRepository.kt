package rs.belgrade.tvcams.data.favorites

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import rs.belgrade.tvcams.domain.favorites.AutoRotateInterval
import rs.belgrade.tvcams.domain.favorites.FavoritesRepository
import rs.belgrade.tvcams.domain.favorites.InfoPanelPosition
import rs.belgrade.tvcams.domain.favorites.QualityFilter
import rs.belgrade.tvcams.domain.favorites.ViewerPreferences
import rs.belgrade.tvcams.domain.favorites.ZapScope

private val Context.viewerPreferencesDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "viewer_preferences",
)

class DataStoreFavoritesRepository(
    private val appContext: Context,
) : FavoritesRepository {

    override val preferences: Flow<ViewerPreferences> =
        appContext.viewerPreferencesDataStore.data.map { prefs ->
            val rawScope = prefs[KEY_ZAP_SCOPE]
            val scope = runCatching {
                if (rawScope != null) ZapScope.valueOf(rawScope) else ZapScope.ALL
            }.getOrDefault(ZapScope.ALL)

            val rawQualityFilter = prefs[KEY_QUALITY_FILTER]
            val qualityFilter = runCatching {
                if (rawQualityFilter != null) QualityFilter.valueOf(rawQualityFilter) else QualityFilter.ALL
            }.getOrDefault(QualityFilter.ALL)

            val rawAutoRotate = prefs[KEY_AUTO_ROTATE_INTERVAL]
            val autoRotateInterval = runCatching {
                if (rawAutoRotate != null) AutoRotateInterval.valueOf(rawAutoRotate) else AutoRotateInterval.OFF
            }.getOrDefault(AutoRotateInterval.OFF)

            val rawPanelPosition = prefs[KEY_INFO_PANEL_POSITION]
            val infoPanelPosition = runCatching {
                if (rawPanelPosition != null) {
                    InfoPanelPosition.valueOf(rawPanelPosition)
                } else {
                    InfoPanelPosition.BOTTOM_CENTER
                }
            }.getOrDefault(InfoPanelPosition.BOTTOM_CENTER)

            ViewerPreferences(
                favoriteIds = prefs[KEY_FAVORITE_IDS].orEmpty(),
                lastCameraId = prefs[KEY_LAST_CAMERA_ID],
                zapScope = scope,
                qualityFilter = qualityFilter,
                autoRotateInterval = autoRotateInterval,
                infoPanelPosition = infoPanelPosition,
                autoHideHud = prefs[KEY_AUTO_HIDE_HUD] ?: true,
                showClockOverlay = prefs[KEY_SHOW_CLOCK_OVERLAY] ?: true,
                showWeatherOverlay = prefs[KEY_SHOW_WEATHER_OVERLAY] ?: true,
                showAirQualityOverlay = prefs[KEY_SHOW_AIR_QUALITY_OVERLAY] ?: true,
                showSolarOverlay = prefs[KEY_SHOW_SOLAR_OVERLAY] ?: true,
                oledProtectionEnabled = prefs[KEY_OLED_PROTECTION_ENABLED] ?: true,
            )
        }

    override suspend fun setFavorites(favoriteIds: Set<String>) {
        appContext.viewerPreferencesDataStore.edit { prefs ->
            prefs[KEY_FAVORITE_IDS] = favoriteIds.toSet()
        }
    }

    override suspend fun setLastCamera(cameraId: String) {
        appContext.viewerPreferencesDataStore.edit { prefs ->
            prefs[KEY_LAST_CAMERA_ID] = cameraId
        }
    }

    override suspend fun setZapScope(scope: ZapScope) {
        appContext.viewerPreferencesDataStore.edit { prefs ->
            prefs[KEY_ZAP_SCOPE] = scope.name
        }
    }

    override suspend fun setQualityFilter(filter: QualityFilter) {
        appContext.viewerPreferencesDataStore.edit { prefs ->
            prefs[KEY_QUALITY_FILTER] = filter.name
        }
    }

    override suspend fun setAutoRotateInterval(interval: AutoRotateInterval) {
        appContext.viewerPreferencesDataStore.edit { prefs ->
            prefs[KEY_AUTO_ROTATE_INTERVAL] = interval.name
        }
    }

    override suspend fun setInfoPanelPosition(position: InfoPanelPosition) {
        appContext.viewerPreferencesDataStore.edit { prefs ->
            prefs[KEY_INFO_PANEL_POSITION] = position.name
        }
    }

    override suspend fun setShowClockOverlay(enabled: Boolean) {
        appContext.viewerPreferencesDataStore.edit { prefs ->
            prefs[KEY_SHOW_CLOCK_OVERLAY] = enabled
        }
    }

    override suspend fun setShowWeatherOverlay(enabled: Boolean) {
        appContext.viewerPreferencesDataStore.edit { prefs ->
            prefs[KEY_SHOW_WEATHER_OVERLAY] = enabled
        }
    }

    override suspend fun setShowAirQualityOverlay(enabled: Boolean) {
        appContext.viewerPreferencesDataStore.edit { prefs ->
            prefs[KEY_SHOW_AIR_QUALITY_OVERLAY] = enabled
        }
    }

    override suspend fun setShowSolarOverlay(enabled: Boolean) {
        appContext.viewerPreferencesDataStore.edit { prefs ->
            prefs[KEY_SHOW_SOLAR_OVERLAY] = enabled
        }
    }

    override suspend fun setOledProtectionEnabled(enabled: Boolean) {
        appContext.viewerPreferencesDataStore.edit { prefs ->
            prefs[KEY_OLED_PROTECTION_ENABLED] = enabled
        }
    }

    private companion object {
        val KEY_FAVORITE_IDS = stringSetPreferencesKey("favorite_camera_ids")
        val KEY_LAST_CAMERA_ID = stringPreferencesKey("last_camera_id")
        val KEY_ZAP_SCOPE = stringPreferencesKey("zap_scope")
        val KEY_QUALITY_FILTER = stringPreferencesKey("quality_filter")
        val KEY_AUTO_ROTATE_INTERVAL = stringPreferencesKey("auto_rotate_interval")
        val KEY_INFO_PANEL_POSITION = stringPreferencesKey("info_panel_position")
        val KEY_AUTO_HIDE_HUD = booleanPreferencesKey("auto_hide_hud")
        val KEY_SHOW_CLOCK_OVERLAY = booleanPreferencesKey("show_clock_overlay")
        val KEY_SHOW_WEATHER_OVERLAY = booleanPreferencesKey("show_weather_overlay")
        val KEY_SHOW_AIR_QUALITY_OVERLAY = booleanPreferencesKey("show_air_quality_overlay")
        val KEY_SHOW_SOLAR_OVERLAY = booleanPreferencesKey("show_solar_overlay")
        val KEY_OLED_PROTECTION_ENABLED = booleanPreferencesKey("oled_protection_enabled")
    }
}
