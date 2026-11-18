package rs.belgrade.tvcams.data.airquality

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import rs.belgrade.tvcams.domain.airquality.AirQualityRepository
import rs.belgrade.tvcams.domain.airquality.CurrentAirQuality
import rs.belgrade.tvcams.domain.airquality.EaqiMapper
import java.io.IOException
import java.util.Locale

@Serializable
private data class OpenMeteoAirQualityPayload(
    val current: OpenMeteoAirCurrentBlock? = null,
)

@Serializable
private data class OpenMeteoAirCurrentBlock(
    @SerialName("european_aqi")
    val europeanAqi: Int? = null,
    @SerialName("pm2_5")
    val pm25: Double? = null,
    val pm10: Double? = null,
)

class OpenMeteoAirQualityClient(
    private val httpClient: OkHttpClient,
    private val baseEndpoint: String = DEFAULT_AIR_QUALITY_ENDPOINT,
) : AirQualityRepository {

    private val json = Json {
        ignoreUnknownKeys = true
    }

    override suspend fun getCurrentAirQuality(lat: Double, lon: Double): CurrentAirQuality? =
        withContext(Dispatchers.IO) {
            val url = baseEndpoint.toHttpUrl().newBuilder()
                .addQueryParameter("latitude", String.format(Locale.US, "%.4f", lat))
                .addQueryParameter("longitude", String.format(Locale.US, "%.4f", lon))
                .addQueryParameter("current", "european_aqi,pm2_5,pm10")
                .build()

            val request = Request.Builder()
                .url(url)
                .get()
                .header("Accept", "application/json")
                .build()

            try {
                httpClient.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) return@withContext null
                    val rawBody = response.body?.string() ?: return@withContext null
                    val decoded = runCatching {
                        json.decodeFromString(OpenMeteoAirQualityPayload.serializer(), rawBody)
                    }.getOrNull() ?: return@withContext null

                    val block = decoded.current ?: return@withContext null
                    val aqi = block.europeanAqi ?: return@withContext null
                    CurrentAirQuality(
                        europeanAqi = aqi,
                        pm25 = block.pm25 ?: 0.0,
                        pm10 = block.pm10 ?: 0.0,
                        band = EaqiMapper.fromIndex(aqi),
                        fetchedAtEpochMs = System.currentTimeMillis(),
                    )
                }
            } catch (_: IOException) {
                null
            }
        }

    companion object {
        const val DEFAULT_AIR_QUALITY_ENDPOINT = "https://air-quality-api.open-meteo.com/v1/air-quality"
    }
}
