package rs.belgrade.tvcams.data.weather

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import rs.belgrade.tvcams.domain.weather.CurrentWeather
import rs.belgrade.tvcams.domain.weather.WeatherRepository
import rs.belgrade.tvcams.domain.weather.WmoWeatherMapper
import java.io.IOException
import java.util.Locale

@Serializable
private data class OpenMeteoForecastPayload(
    val current: OpenMeteoCurrentBlock? = null,
)

@Serializable
private data class OpenMeteoCurrentBlock(
    @SerialName("temperature_2m")
    val temperature2m: Double? = null,
    @SerialName("apparent_temperature")
    val apparentTemperature: Double? = null,
    @SerialName("weather_code")
    val weatherCode: Int? = null,
    @SerialName("wind_speed_10m")
    val windSpeed10m: Double? = null,
    @SerialName("is_day")
    val isDay: Int? = null,
)

class OpenMeteoWeatherClient(
    private val httpClient: OkHttpClient,
    private val baseEndpoint: String = DEFAULT_OPEN_METEO_ENDPOINT,
) : WeatherRepository {

    private val json = Json {
        ignoreUnknownKeys = true
    }

    override suspend fun getCurrentWeather(lat: Double, lon: Double): CurrentWeather? =
        withContext(Dispatchers.IO) {
            val url = baseEndpoint.toHttpUrl().newBuilder()
                .addQueryParameter("latitude", String.format(Locale.US, "%.4f", lat))
                .addQueryParameter("longitude", String.format(Locale.US, "%.4f", lon))
                .addQueryParameter(
                    "current",
                    "temperature_2m,apparent_temperature,weather_code,wind_speed_10m,is_day",
                )
                .addQueryParameter("timezone", "auto")
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
                        json.decodeFromString(OpenMeteoForecastPayload.serializer(), rawBody)
                    }.getOrNull() ?: return@withContext null

                    val block = decoded.current ?: return@withContext null
                    val temp = block.temperature2m ?: return@withContext null
                    val code = block.weatherCode ?: 0
                    CurrentWeather(
                        temperatureCelsius = temp,
                        apparentTemperatureCelsius = block.apparentTemperature ?: temp,
                        windSpeedKmh = block.windSpeed10m ?: 0.0,
                        condition = WmoWeatherMapper.fromCode(code),
                        isDay = (block.isDay ?: 1) == 1,
                        fetchedAtEpochMs = System.currentTimeMillis(),
                    )
                }
            } catch (_: IOException) {
                null
            }
        }

    companion object {
        const val DEFAULT_OPEN_METEO_ENDPOINT = "https://api.open-meteo.com/v1/forecast"
    }
}
