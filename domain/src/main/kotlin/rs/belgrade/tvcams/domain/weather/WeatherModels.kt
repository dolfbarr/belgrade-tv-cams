package rs.belgrade.tvcams.domain.weather

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable
import rs.belgrade.tvcams.domain.solar.SolarWindow
import java.util.Locale
import kotlin.math.roundToInt

@Serializable
enum class WeatherCondition {
    CLEAR,
    PARTLY_CLOUDY,
    OVERCAST,
    FOG,
    DRIZZLE,
    RAIN,
    SNOW,
    SHOWERS,
    THUNDERSTORM,
    ;

    fun displayLabel(isDay: Boolean = true): String = when (this) {
        CLEAR -> if (isDay) "Clear" else "Clear"
        PARTLY_CLOUDY -> "Partly cloudy"
        OVERCAST -> "Overcast"
        FOG -> "Fog"
        DRIZZLE -> "Drizzle"
        RAIN -> "Rain"
        SNOW -> "Snow"
        SHOWERS -> "Showers"
        THUNDERSTORM -> "Thunderstorm"
    }
}

object WmoWeatherMapper {
    fun fromCode(wmoCode: Int): WeatherCondition = when (wmoCode) {
        0 -> WeatherCondition.CLEAR
        1, 2 -> WeatherCondition.PARTLY_CLOUDY
        3 -> WeatherCondition.OVERCAST
        45, 48 -> WeatherCondition.FOG
        51, 53, 55, 56, 57 -> WeatherCondition.DRIZZLE
        61, 63, 65, 66, 67 -> WeatherCondition.RAIN
        71, 73, 75, 77, 85, 86 -> WeatherCondition.SNOW
        80, 81, 82 -> WeatherCondition.SHOWERS
        95, 96, 99 -> WeatherCondition.THUNDERSTORM
        else -> WeatherCondition.PARTLY_CLOUDY
    }
}

@Serializable
enum class WeatherUnitSystem {
    AUTO,
    METRIC,
    IMPERIAL,
    ;

    fun next(): WeatherUnitSystem = when (this) {
        AUTO -> METRIC
        METRIC -> IMPERIAL
        IMPERIAL -> AUTO
    }

    fun resolveEffective(locale: Locale = Locale.getDefault()): WeatherUnitSystem = when (this) {
        AUTO -> if (locale.country.uppercase(Locale.ROOT) in IMPERIAL_REGIONS) IMPERIAL else METRIC
        METRIC -> METRIC
        IMPERIAL -> IMPERIAL
    }

    fun displayLabel(locale: Locale = Locale.getDefault()): String = when (this) {
        AUTO -> if (resolveEffective(locale) == IMPERIAL) "Auto (°F, mph)" else "Auto (°C, km/h)"
        METRIC -> "Metric (°C, km/h)"
        IMPERIAL -> "Imperial (°F, mph)"
    }

    companion object {
        private val IMPERIAL_REGIONS: Set<String> = setOf("US", "LR", "MM")
    }
}

data class CurrentWeather(
    val temperatureCelsius: Double,
    val apparentTemperatureCelsius: Double,
    val windSpeedKmh: Double,
    val condition: WeatherCondition,
    val isDay: Boolean,
    val fetchedAtEpochMs: Long,
    val solarWindows: List<SolarWindow> = emptyList(),
) {
    val temperatureFahrenheit: Double
        get() = temperatureCelsius * 9.0 / 5.0 + 32.0

    val apparentTemperatureFahrenheit: Double
        get() = apparentTemperatureCelsius * 9.0 / 5.0 + 32.0

    val windSpeedMph: Double
        get() = windSpeedKmh * KMH_TO_MPH

    fun formattedSummary(
        unitSystem: WeatherUnitSystem = WeatherUnitSystem.METRIC,
        locale: Locale = Locale.getDefault(),
    ): String = when (unitSystem.resolveEffective(locale)) {
        WeatherUnitSystem.IMPERIAL -> String.format(
            Locale.US,
            "%.0f°F · %s · %.0f mph",
            temperatureFahrenheit,
            condition.displayLabel(isDay),
            windSpeedMph,
        )
        WeatherUnitSystem.METRIC,
        WeatherUnitSystem.AUTO,
        -> String.format(
            Locale.US,
            "%.1f°C · %s · %.0f km/h",
            temperatureCelsius,
            condition.displayLabel(isDay),
            windSpeedKmh,
        )
    }

    fun compactMetricsLabel(
        unitSystem: WeatherUnitSystem = WeatherUnitSystem.METRIC,
        locale: Locale = Locale.getDefault(),
    ): String = when (unitSystem.resolveEffective(locale)) {
        WeatherUnitSystem.IMPERIAL -> String.format(
            Locale.US,
            "%.0f°F · %.0f mph",
            temperatureFahrenheit,
            windSpeedMph,
        )
        WeatherUnitSystem.METRIC,
        WeatherUnitSystem.AUTO,
        -> String.format(
            Locale.US,
            "%.1f°C · %.0f km/h",
            temperatureCelsius,
            windSpeedKmh,
        )
    }

    companion object {
        private const val KMH_TO_MPH: Double = 0.621371
    }
}

interface WeatherRepository {
    suspend fun getCurrentWeather(lat: Double, lon: Double): CurrentWeather?
}

class GridCachedWeatherRepository(
    private val upstream: WeatherRepository,
    private val clockMillis: () -> Long = System::currentTimeMillis,
    private val ttlMillis: Long = DEFAULT_TTL_MS,
    private val staleGraceMillis: Long = DEFAULT_STALE_GRACE_MS,
) : WeatherRepository {

    private data class GridKey(
        val latBucket: Int,
        val lonBucket: Int,
    )

    private data class CacheEntry(
        val weather: CurrentWeather,
        val cachedAtMs: Long,
    )

    private val mutex = Mutex()
    private val entries = HashMap<GridKey, CacheEntry>()

    override suspend fun getCurrentWeather(lat: Double, lon: Double): CurrentWeather? {
        val key = GridKey(
            latBucket = (lat * 20.0).roundToInt(),
            lonBucket = (lon * 20.0).roundToInt(),
        )

        return mutex.withLock {
            val now = clockMillis()
            val existing = entries[key]
            if (existing != null && now - existing.cachedAtMs < ttlMillis) {
                return@withLock existing.weather
            }

            val roundedLat = key.latBucket / 20.0
            val roundedLon = key.lonBucket / 20.0
            val fresh = upstream.getCurrentWeather(roundedLat, roundedLon)
            if (fresh != null) {
                entries[key] = CacheEntry(weather = fresh, cachedAtMs = now)
                return@withLock fresh
            }

            if (existing != null && now - existing.cachedAtMs < staleGraceMillis) {
                existing.weather
            } else {
                null
            }
        }
    }

    companion object {
        const val DEFAULT_TTL_MS: Long = 15L * 60L * 1000L
        const val DEFAULT_STALE_GRACE_MS: Long = 60L * 60L * 1000L
    }
}
