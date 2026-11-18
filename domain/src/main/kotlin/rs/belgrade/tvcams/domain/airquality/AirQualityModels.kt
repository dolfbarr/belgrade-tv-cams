package rs.belgrade.tvcams.domain.airquality

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.Locale
import kotlin.math.roundToInt

enum class AirQualityBand {
    GOOD,
    FAIR,
    MODERATE,
    POOR,
    VERY_POOR,
    EXTREMELY_POOR,
    ;

    fun displayLabel(): String = when (this) {
        GOOD -> "Good"
        FAIR -> "Fair"
        MODERATE -> "Moderate"
        POOR -> "Poor"
        VERY_POOR -> "Very poor"
        EXTREMELY_POOR -> "Extremely poor"
    }
}

object EaqiMapper {
    fun fromIndex(europeanAqi: Int): AirQualityBand = when {
        europeanAqi <= 20 -> AirQualityBand.GOOD
        europeanAqi <= 40 -> AirQualityBand.FAIR
        europeanAqi <= 60 -> AirQualityBand.MODERATE
        europeanAqi <= 80 -> AirQualityBand.POOR
        europeanAqi <= 100 -> AirQualityBand.VERY_POOR
        else -> AirQualityBand.EXTREMELY_POOR
    }
}

data class CurrentAirQuality(
    val europeanAqi: Int,
    val pm25: Double,
    val pm10: Double,
    val band: AirQualityBand,
    val fetchedAtEpochMs: Long,
) {
    fun formattedSummary(): String = String.format(
        Locale.US,
        "AQI %d %s · PM2.5 %.0f",
        europeanAqi,
        band.displayLabel(),
        pm25,
    )
}

interface AirQualityRepository {
    suspend fun getCurrentAirQuality(lat: Double, lon: Double): CurrentAirQuality?
}

class GridCachedAirQualityRepository(
    private val upstream: AirQualityRepository,
    private val clockMillis: () -> Long = System::currentTimeMillis,
    private val ttlMillis: Long = DEFAULT_TTL_MS,
    private val staleGraceMillis: Long = DEFAULT_STALE_GRACE_MS,
) : AirQualityRepository {

    private data class GridKey(
        val latBucket: Int,
        val lonBucket: Int,
    )

    private data class CacheEntry(
        val airQuality: CurrentAirQuality,
        val cachedAtMs: Long,
    )

    private val mutex = Mutex()
    private val entries = HashMap<GridKey, CacheEntry>()

    override suspend fun getCurrentAirQuality(lat: Double, lon: Double): CurrentAirQuality? {
        val key = GridKey(
            latBucket = (lat * 20.0).roundToInt(),
            lonBucket = (lon * 20.0).roundToInt(),
        )

        return mutex.withLock {
            val now = clockMillis()
            val existing = entries[key]
            if (existing != null && now - existing.cachedAtMs < ttlMillis) {
                return@withLock existing.airQuality
            }

            val roundedLat = key.latBucket / 20.0
            val roundedLon = key.lonBucket / 20.0
            val fresh = upstream.getCurrentAirQuality(roundedLat, roundedLon)
            if (fresh != null) {
                entries[key] = CacheEntry(airQuality = fresh, cachedAtMs = now)
                return@withLock fresh
            }

            if (existing != null && now - existing.cachedAtMs < staleGraceMillis) {
                existing.airQuality
            } else {
                null
            }
        }
    }

    companion object {
        const val DEFAULT_TTL_MS: Long = 30L * 60L * 1000L
        const val DEFAULT_STALE_GRACE_MS: Long = 90L * 60L * 1000L
    }
}
