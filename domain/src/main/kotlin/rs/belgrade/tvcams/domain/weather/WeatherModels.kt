package rs.belgrade.tvcams.domain.weather

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable
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

data class CurrentWeather(
    val temperatureCelsius: Double,
    val apparentTemperatureCelsius: Double,
    val windSpeedKmh: Double,
    val condition: WeatherCondition,
    val isDay: Boolean,
    val fetchedAtEpochMs: Long,
) {
    fun formattedSummary(): String = String.format(
        Locale.US,
        "%.1f°C · %s · %.0f km/h",
        temperatureCelsius,
        condition.displayLabel(isDay),
        windSpeedKmh,
    )
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
        // 0.05-degree (~5.5 km) grid cell coalesces urban cameras within the same city
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
