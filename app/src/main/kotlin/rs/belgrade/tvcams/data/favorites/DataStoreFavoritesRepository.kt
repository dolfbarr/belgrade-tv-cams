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
import rs.belgrade.tvcams.domain.currency.BaseCurrencyPreference
import rs.belgrade.tvcams.domain.favorites.AutoRotateInterval
import rs.belgrade.tvcams.domain.favorites.FavoritesRepository
import rs.belgrade.tvcams.domain.favorites.InfoPanelPosition
import rs.belgrade.tvcams.domain.favorites.PerformanceMode
import rs.belgrade.tvcams.domain.favorites.QualityFilter
import rs.belgrade.tvcams.domain.favorites.TransportFilter
import rs.belgrade.tvcams.domain.favorites.ViewerPreferences
import rs.belgrade.tvcams.domain.favorites.ZapScope
import rs.belgrade.tvcams.domain.weather.WeatherUnitSystem

private val Context.viewerPreferencesDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "viewer_preferences",
)

class DataStoreFavoritesRepository(
    private val dataStore: DataStore<Preferences>,
) : FavoritesRepository {

    constructor(appContext: Context) : this(appContext.viewerPreferencesDataStore)

    override val preferences: Flow<ViewerPreferences> =
        dataStore.data.map { prefs ->
            val rawScope = prefs[KEY_ZAP_SCOPE]
            val scope = runCatching {
                if (rawScope != null) ZapScope.valueOf(rawScope) else ZapScope.ALL
            }.getOrDefault(ZapScope.ALL)

            val rawQualityFilter = prefs[KEY_QUALITY_FILTER]
            val qualityFilter = runCatching {
                if (rawQualityFilter != null) QualityFilter.valueOf(rawQualityFilter) else QualityFilter.ALL
            }.getOrDefault(QualityFilter.ALL)

            val rawTransportFilter = prefs[KEY_TRANSPORT_FILTER]
            val transportFilter = runCatching {
                if (rawTransportFilter != null) {
                    TransportFilter.valueOf(rawTransportFilter)
                } else {
                    TransportFilter.ALL
                }
            }.getOrDefault(TransportFilter.ALL)

            val rawPerformanceMode = prefs[KEY_PERFORMANCE_MODE]
            val performanceMode = runCatching {
                if (rawPerformanceMode != null) {
                    PerformanceMode.valueOf(rawPerformanceMode)
                } else {
                    PerformanceMode.AUTO
                }
            }.getOrDefault(PerformanceMode.AUTO)

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

            val rawBaseCurrency = prefs[KEY_BASE_CURRENCY_PREFERENCE]
            val baseCurrencyPreference = runCatching {
                if (rawBaseCurrency != null) {
                    BaseCurrencyPreference.valueOf(rawBaseCurrency)
                } else {
                    BaseCurrencyPreference.AUTO
                }
            }.getOrDefault(BaseCurrencyPreference.AUTO)

            val rawWeatherUnits = prefs[KEY_WEATHER_UNIT_SYSTEM]
            val weatherUnitSystem = runCatching {
                if (rawWeatherUnits != null) {
                    WeatherUnitSystem.valueOf(rawWeatherUnits)
                } else {
                    WeatherUnitSystem.AUTO
                }
            }.getOrDefault(WeatherUnitSystem.AUTO)

            ViewerPreferences(
                favoriteIds = prefs[KEY_FAVORITE_IDS].orEmpty(),
                lastCameraId = prefs[KEY_LAST_CAMERA_ID],
                zapScope = scope,
                qualityFilter = qualityFilter,
                transportFilter = transportFilter,
                performanceMode = performanceMode,
                autoRotateInterval = autoRotateInterval,
                infoPanelPosition = infoPanelPosition,
                autoHideHud = prefs[KEY_AUTO_HIDE_HUD] ?: true,
                showClockOverlay = prefs[KEY_SHOW_CLOCK_OVERLAY] ?: true,
                showWeatherOverlay = prefs[KEY_SHOW_WEATHER_OVERLAY] ?: true,
                showAirQualityOverlay = prefs[KEY_SHOW_AIR_QUALITY_OVERLAY] ?: true,
                showSolarOverlay = prefs[KEY_SHOW_SOLAR_OVERLAY] ?: true,
                showCurrencyOverlay = prefs[KEY_SHOW_CURRENCY_OVERLAY] ?: true,
                baseCurrencyPreference = baseCurrencyPreference,
                streamAudioEnabled = prefs[KEY_STREAM_AUDIO_ENABLED] ?: false,
                weatherUnitSystem = weatherUnitSystem,
                oledProtectionEnabled = prefs[KEY_OLED_PROTECTION_ENABLED] ?: true,
            )
        }

    override suspend fun setFavorites(favoriteIds: Set<String>) {
        dataStore.edit { prefs ->
            prefs[KEY_FAVORITE_IDS] = favoriteIds.toSet()
        }
    }

    override suspend fun setLastCamera(cameraId: String) {
        dataStore.edit { prefs ->
            prefs[KEY_LAST_CAMERA_ID] = cameraId
        }
    }

    override suspend fun setZapScope(scope: ZapScope) {
        dataStore.edit { prefs ->
            prefs[KEY_ZAP_SCOPE] = scope.name
        }
    }

    override suspend fun setQualityFilter(filter: QualityFilter) {
        dataStore.edit { prefs ->
            prefs[KEY_QUALITY_FILTER] = filter.name
        }
    }

    override suspend fun setTransportFilter(filter: TransportFilter) {
        dataStore.edit { prefs ->
            prefs[KEY_TRANSPORT_FILTER] = filter.name
        }
    }

    override suspend fun setPerformanceMode(mode: PerformanceMode) {
        dataStore.edit { prefs ->
            prefs[KEY_PERFORMANCE_MODE] = mode.name
        }
    }

    override suspend fun setAutoRotateInterval(interval: AutoRotateInterval) {
        dataStore.edit { prefs ->
            prefs[KEY_AUTO_ROTATE_INTERVAL] = interval.name
        }
    }

    override suspend fun setInfoPanelPosition(position: InfoPanelPosition) {
        dataStore.edit { prefs ->
            prefs[KEY_INFO_PANEL_POSITION] = position.name
        }
    }

    override suspend fun setShowClockOverlay(enabled: Boolean) {
        dataStore.edit { prefs ->
            prefs[KEY_SHOW_CLOCK_OVERLAY] = enabled
        }
    }

    override suspend fun setShowWeatherOverlay(enabled: Boolean) {
        dataStore.edit { prefs ->
            prefs[KEY_SHOW_WEATHER_OVERLAY] = enabled
        }
    }

    override suspend fun setShowAirQualityOverlay(enabled: Boolean) {
        dataStore.edit { prefs ->
            prefs[KEY_SHOW_AIR_QUALITY_OVERLAY] = enabled
        }
    }

    override suspend fun setShowSolarOverlay(enabled: Boolean) {
        dataStore.edit { prefs ->
            prefs[KEY_SHOW_SOLAR_OVERLAY] = enabled
        }
    }

    override suspend fun setShowCurrencyOverlay(enabled: Boolean) {
        dataStore.edit { prefs ->
            prefs[KEY_SHOW_CURRENCY_OVERLAY] = enabled
        }
    }

    override suspend fun setBaseCurrencyPreference(preference: BaseCurrencyPreference) {
        dataStore.edit { prefs ->
            prefs[KEY_BASE_CURRENCY_PREFERENCE] = preference.name
        }
    }

    override suspend fun setStreamAudioEnabled(enabled: Boolean) {
        dataStore.edit { prefs ->
            prefs[KEY_STREAM_AUDIO_ENABLED] = enabled
        }
    }

    override suspend fun setWeatherUnitSystem(unitSystem: WeatherUnitSystem) {
        dataStore.edit { prefs ->
            prefs[KEY_WEATHER_UNIT_SYSTEM] = unitSystem.name
        }
    }

    override suspend fun setOledProtectionEnabled(enabled: Boolean) {
        dataStore.edit { prefs ->
            prefs[KEY_OLED_PROTECTION_ENABLED] = enabled
        }
    }

    private companion object {
        val KEY_FAVORITE_IDS = stringSetPreferencesKey("favorite_camera_ids")
        val KEY_LAST_CAMERA_ID = stringPreferencesKey("last_camera_id")
        val KEY_ZAP_SCOPE = stringPreferencesKey("zap_scope")
        val KEY_QUALITY_FILTER = stringPreferencesKey("quality_filter")
        val KEY_TRANSPORT_FILTER = stringPreferencesKey("transport_filter")
        val KEY_PERFORMANCE_MODE = stringPreferencesKey("performance_mode")
        val KEY_AUTO_ROTATE_INTERVAL = stringPreferencesKey("auto_rotate_interval")
        val KEY_INFO_PANEL_POSITION = stringPreferencesKey("info_panel_position")
        val KEY_AUTO_HIDE_HUD = booleanPreferencesKey("auto_hide_hud")
        val KEY_SHOW_CLOCK_OVERLAY = booleanPreferencesKey("show_clock_overlay")
        val KEY_SHOW_WEATHER_OVERLAY = booleanPreferencesKey("show_weather_overlay")
        val KEY_SHOW_AIR_QUALITY_OVERLAY = booleanPreferencesKey("show_air_quality_overlay")
        val KEY_SHOW_SOLAR_OVERLAY = booleanPreferencesKey("show_solar_overlay")
        val KEY_SHOW_CURRENCY_OVERLAY = booleanPreferencesKey("show_currency_overlay")
        val KEY_BASE_CURRENCY_PREFERENCE = stringPreferencesKey("base_currency_preference")
        val KEY_STREAM_AUDIO_ENABLED = booleanPreferencesKey("stream_audio_enabled")
        val KEY_WEATHER_UNIT_SYSTEM = stringPreferencesKey("weather_unit_system")
        val KEY_OLED_PROTECTION_ENABLED = booleanPreferencesKey("oled_protection_enabled")
    }
}
