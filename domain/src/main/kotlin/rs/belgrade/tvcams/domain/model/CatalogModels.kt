package rs.belgrade.tvcams.domain.model

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.Json
import java.util.Locale

internal fun humanizeLocationCode(rawCode: String): String {
    val cleaned = rawCode.trim()
    if (cleaned.isEmpty()) return "Unknown"
    if (cleaned.length <= 3 && !cleaned.contains('_')) {
        return cleaned.uppercase(Locale.ROOT)
    }
    return cleaned
        .split('_', '-')
        .filter { it.isNotBlank() }
        .mapIndexed { idx, token ->
            val lower = token.lowercase(Locale.ROOT)
            if (idx > 0 && lower in LOWERCASE_PARTICLES) {
                lower
            } else {
                lower.replaceFirstChar { ch -> ch.titlecase(Locale.ROOT) }
            }
        }
        .joinToString(" ")
}

private val LOWERCASE_PARTICLES: Set<String> = setOf("on", "de", "la", "du", "of", "and", "am", "sur")

object CameraCountrySerializer : KSerializer<CameraCountry> {
    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor("CameraCountry", PrimitiveKind.STRING)

    override fun serialize(encoder: Encoder, value: CameraCountry) {
        encoder.encodeString(value.code)
    }

    override fun deserialize(decoder: Decoder): CameraCountry =
        CameraCountry(decoder.decodeString().trim().uppercase(Locale.ROOT))
}

/**
 * Open-world country identifier serialized as an UPPER_SNAKE_CASE string on the wire.
 * Community contributors can add any new country in catalog manifests without modifying Kotlin enums.
 */
@Serializable(with = CameraCountrySerializer::class)
data class CameraCountry(val code: String) {
    val name: String
        get() = code

    val sortOrder: Int
        get() = KNOWN_ORDER[code] ?: Int.MAX_VALUE

    fun displayLabel(overrideName: String? = null): String {
        if (!overrideName.isNullOrBlank()) return overrideName.trim()
        return KNOWN_LABELS[code] ?: humanizeLocationCode(code)
    }

    fun isoAlpha2(overrideIso: String? = null): String {
        if (!overrideIso.isNullOrBlank()) {
            val trimmed = overrideIso.trim().uppercase(Locale.ROOT)
            if (trimmed.length >= 2) return trimmed.take(2)
        }
        return KNOWN_ISO[code] ?: deriveFallbackIso(code)
    }

    companion object {
        val SERBIA = CameraCountry("SERBIA")
        val RUSSIA = CameraCountry("RUSSIA")
        val SPAIN = CameraCountry("SPAIN")
        val ROMANIA = CameraCountry("ROMANIA")
        val UK = CameraCountry("UK")
        val FRANCE = CameraCountry("FRANCE")
        val NETHERLANDS = CameraCountry("NETHERLANDS")
        val GERMANY = CameraCountry("GERMANY")
        val GEORGIA = CameraCountry("GEORGIA")
        val ARMENIA = CameraCountry("ARMENIA")
        val USA = CameraCountry("USA")
        val SOUTH_KOREA = CameraCountry("SOUTH_KOREA")
        val JAPAN = CameraCountry("JAPAN")

        val entries: List<CameraCountry> = listOf(
            SERBIA,
            RUSSIA,
            SPAIN,
            ROMANIA,
            UK,
            FRANCE,
            NETHERLANDS,
            GERMANY,
            GEORGIA,
            ARMENIA,
            USA,
            SOUTH_KOREA,
            JAPAN,
        )

        private val KNOWN_ORDER: Map<String, Int> =
            entries.mapIndexed { idx, item -> item.code to idx }.toMap()

        private val KNOWN_LABELS: Map<String, String> = mapOf(
            "SERBIA" to "Serbia",
            "RUSSIA" to "Russia",
            "SPAIN" to "Spain",
            "ROMANIA" to "Romania",
            "UK" to "United Kingdom",
            "UNITED_KINGDOM" to "United Kingdom",
            "FRANCE" to "France",
            "NETHERLANDS" to "Netherlands",
            "GERMANY" to "Germany",
            "GEORGIA" to "Georgia",
            "ARMENIA" to "Armenia",
            "USA" to "USA",
            "UNITED_STATES" to "USA",
            "SOUTH_KOREA" to "South Korea",
            "JAPAN" to "Japan",
            "UAE" to "UAE",
        )

        private val KNOWN_ISO: Map<String, String> = mapOf(
            "SERBIA" to "RS",
            "RUSSIA" to "RU",
            "SPAIN" to "ES",
            "ROMANIA" to "RO",
            "UK" to "GB",
            "UNITED_KINGDOM" to "GB",
            "FRANCE" to "FR",
            "NETHERLANDS" to "NL",
            "GERMANY" to "DE",
            "GEORGIA" to "GE",
            "ARMENIA" to "AM",
            "USA" to "US",
            "UNITED_STATES" to "US",
            "SOUTH_KOREA" to "KR",
            "JAPAN" to "JP",
            "AUSTRIA" to "AT",
            "SWITZERLAND" to "CH",
            "ITALY" to "IT",
            "PORTUGAL" to "PT",
            "GREECE" to "GR",
            "POLAND" to "PL",
            "CZECHIA" to "CZ",
            "HUNGARY" to "HU",
            "CROATIA" to "HR",
            "MONTENEGRO" to "ME",
            "BULGARIA" to "BG",
            "TURKEY" to "TR",
            "SWEDEN" to "SE",
            "NORWAY" to "NO",
            "FINLAND" to "FI",
            "CANADA" to "CA",
            "BRAZIL" to "BR",
            "AUSTRALIA" to "AU",
        )

        private fun deriveFallbackIso(code: String): String {
            val parts = code.split('_', '-').filter { it.isNotBlank() }
            if (parts.size >= 2) {
                return "${parts[0].first()}${parts[1].first()}".uppercase(Locale.ROOT)
            }
            val letters = code.filter { it.isLetter() }.uppercase(Locale.ROOT)
            return letters.take(2).padEnd(2, 'X')
        }
    }
}

object CameraCitySerializer : KSerializer<CameraCity> {
    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor("CameraCity", PrimitiveKind.STRING)

    override fun serialize(encoder: Encoder, value: CameraCity) {
        encoder.encodeString(value.code)
    }

    override fun deserialize(decoder: Decoder): CameraCity =
        CameraCity(decoder.decodeString().trim().uppercase(Locale.ROOT))
}

/**
 * Open-world city identifier serialized as an UPPER_SNAKE_CASE string on the wire.
 * Community contributors can add any new city in catalog manifests without modifying Kotlin enums.
 */
@Serializable(with = CameraCitySerializer::class)
data class CameraCity(val code: String) {
    val name: String
        get() = code

    val defaultCountry: CameraCountry?
        get() = KNOWN_CITY_COUNTRIES[code]

    val sortOrder: Int
        get() = KNOWN_ORDER[code] ?: Int.MAX_VALUE

    fun displayLabel(overrideName: String? = null): String {
        if (!overrideName.isNullOrBlank()) return overrideName.trim()
        return KNOWN_LABELS[code] ?: humanizeLocationCode(code)
    }

    companion object {
        val BELGRADE = CameraCity("BELGRADE")
        val ZEMUN = CameraCity("ZEMUN")
        val NOVI_SAD = CameraCity("NOVI_SAD")
        val SUBOTICA = CameraCity("SUBOTICA")
        val NIS = CameraCity("NIS")
        val MOSCOW = CameraCity("MOSCOW")
        val SAINT_PETERSBURG = CameraCity("SAINT_PETERSBURG")
        val ROSTOV_ON_DON = CameraCity("ROSTOV_ON_DON")
        val TOMSK = CameraCity("TOMSK")
        val MAGADAN = CameraCity("MAGADAN")
        val BARCELONA = CameraCity("BARCELONA")
        val MADRID = CameraCity("MADRID")
        val BUCHAREST = CameraCity("BUCHAREST")
        val LONDON = CameraCity("LONDON")
        val PARIS = CameraCity("PARIS")
        val AMSTERDAM = CameraCity("AMSTERDAM")
        val BERLIN = CameraCity("BERLIN")
        val TBILISI = CameraCity("TBILISI")
        val YEREVAN = CameraCity("YEREVAN")
        val NEW_YORK = CameraCity("NEW_YORK")
        val SEOUL = CameraCity("SEOUL")
        val TOKYO = CameraCity("TOKYO")

        val entries: List<CameraCity> = listOf(
            BELGRADE,
            ZEMUN,
            NOVI_SAD,
            SUBOTICA,
            NIS,
            MOSCOW,
            SAINT_PETERSBURG,
            ROSTOV_ON_DON,
            TOMSK,
            MAGADAN,
            BARCELONA,
            MADRID,
            BUCHAREST,
            LONDON,
            PARIS,
            AMSTERDAM,
            BERLIN,
            TBILISI,
            YEREVAN,
            NEW_YORK,
            SEOUL,
            TOKYO,
        )

        private val KNOWN_ORDER: Map<String, Int> =
            entries.mapIndexed { idx, item -> item.code to idx }.toMap()

        private val KNOWN_CITY_COUNTRIES: Map<String, CameraCountry> = mapOf(
            "BELGRADE" to CameraCountry.SERBIA,
            "ZEMUN" to CameraCountry.SERBIA,
            "NOVI_SAD" to CameraCountry.SERBIA,
            "SUBOTICA" to CameraCountry.SERBIA,
            "NIS" to CameraCountry.SERBIA,
            "MOSCOW" to CameraCountry.RUSSIA,
            "SAINT_PETERSBURG" to CameraCountry.RUSSIA,
            "ROSTOV_ON_DON" to CameraCountry.RUSSIA,
            "TOMSK" to CameraCountry.RUSSIA,
            "MAGADAN" to CameraCountry.RUSSIA,
            "BARCELONA" to CameraCountry.SPAIN,
            "MADRID" to CameraCountry.SPAIN,
            "BUCHAREST" to CameraCountry.ROMANIA,
            "LONDON" to CameraCountry.UK,
            "PARIS" to CameraCountry.FRANCE,
            "AMSTERDAM" to CameraCountry.NETHERLANDS,
            "BERLIN" to CameraCountry.GERMANY,
            "TBILISI" to CameraCountry.GEORGIA,
            "YEREVAN" to CameraCountry.ARMENIA,
            "NEW_YORK" to CameraCountry.USA,
            "SEOUL" to CameraCountry.SOUTH_KOREA,
            "TOKYO" to CameraCountry.JAPAN,
        )

        private val KNOWN_LABELS: Map<String, String> = mapOf(
            "BELGRADE" to "Belgrade",
            "ZEMUN" to "Zemun",
            "NOVI_SAD" to "Novi Sad",
            "SUBOTICA" to "Subotica",
            "NIS" to "Niš",
            "MOSCOW" to "Moscow",
            "SAINT_PETERSBURG" to "Saint Petersburg",
            "ROSTOV_ON_DON" to "Rostov-on-Don",
            "TOMSK" to "Tomsk",
            "MAGADAN" to "Magadan",
            "BARCELONA" to "Barcelona",
            "MADRID" to "Madrid",
            "BUCHAREST" to "Bucharest",
            "LONDON" to "London",
            "PARIS" to "Paris",
            "AMSTERDAM" to "Amsterdam",
            "BERLIN" to "Berlin",
            "TBILISI" to "Tbilisi",
            "YEREVAN" to "Yerevan",
            "NEW_YORK" to "New York",
            "SEOUL" to "Seoul",
            "TOKYO" to "Tokyo",
        )

        fun citiesFor(country: CameraCountry): List<CameraCity> =
            entries.filter { it.defaultCountry == country }
    }
}

typealias SerbiaCity = CameraCity

@Serializable
enum class VideoQuality {
    UHD_4K,
    UHD_2K,
    FHD_1080P,
    HD_720P,
    SD,
    ;

    val isHd: Boolean
        get() = when (this) {
            UHD_4K, UHD_2K, FHD_1080P, HD_720P -> true
            SD -> false
        }

    val sortRank: Int
        get() = when (this) {
            UHD_4K -> 0
            UHD_2K -> 1
            FHD_1080P -> 2
            HD_720P -> 3
            SD -> 4
        }

    fun shortBadgeLabel(): String = when (this) {
        UHD_4K -> "4K UHD"
        UHD_2K -> "2K QHD"
        FHD_1080P -> "1080p HD"
        HD_720P -> "720p HD"
        SD -> "SD"
    }
}

@Serializable
enum class CameraCategory {
    BRIDGES,
    SQUARES,
    BOULEVARDS,
    HIGHWAYS,
    ;

    fun displayLabel(): String = when (this) {
        BRIDGES -> "Bridges"
        SQUARES -> "Squares"
        BOULEVARDS -> "Boulevards"
        HIGHWAYS -> "Highway & border"
    }
}

@Serializable
enum class StreamTransportType {
    HLS,
    YOUTUBE_LIVE,
    RUTUBE_LIVE,
    IPCAMLIVE,
    SNAPSHOT_POLL,
    ;

    val isLiveVideo: Boolean
        get() = this != SNAPSHOT_POLL
}

@Serializable
data class GeoCoordinates(
    val lat: Double,
    val lon: Double,
)

@Serializable
data class StreamDescriptor(
    val type: StreamTransportType,
    val url: String,
    @SerialName("interval_ms")
    val intervalMs: Long? = null,
    @SerialName("cache_buster_param")
    val cacheBusterParam: String? = null,
    val headers: Map<String, String>? = null,
)

@Serializable
data class Camera(
    val id: String,
    val name: String,
    val country: CameraCountry? = null,
    @SerialName("country_name")
    val countryName: String? = null,
    @SerialName("country_iso")
    val countryIso: String? = null,
    val city: CameraCity = CameraCity.BELGRADE,
    @SerialName("city_name")
    val cityName: String? = null,
    val quality: VideoQuality? = null,
    val featured: Boolean? = null,
    val timezone: String = "Europe/Belgrade",
    val streets: List<String>,
    val municipality: String,
    val category: CameraCategory,
    val direction: String,
    @SerialName("poster_url")
    val posterUrl: String,
    val coordinates: GeoCoordinates,
    val streams: List<StreamDescriptor>,
    @SerialName("poster_headers")
    val posterHeaders: Map<String, String>? = null,
) {
    val resolvedCountry: CameraCountry
        get() = country ?: city.defaultCountry ?: CameraCountry.SERBIA

    val resolvedCountryLabel: String
        get() = resolvedCountry.displayLabel(countryName)

    val resolvedCountryIso: String
        get() = resolvedCountry.isoAlpha2(countryIso)

    val resolvedCityLabel: String
        get() = city.displayLabel(cityName)

    val resolvedQuality: VideoQuality
        get() = quality ?: VideoQuality.SD

    val isFeatured: Boolean
        get() = featured == true

    val isLiveVideo: Boolean
        get() = streams.firstOrNull()?.type?.isLiveVideo == true
}

@Serializable
data class CatalogManifest(
    @SerialName("schema_version")
    val schemaVersion: Int,
    val revision: Long,
    @SerialName("updated_at")
    val updatedAt: String,
    val cameras: List<Camera>,
)

object CatalogJsonCodec {
    val SUPPORTED_SCHEMA_VERSIONS: Set<Int> = setOf(1, 2, 3, 4, 5)
    const val LATEST_SCHEMA_VERSION: Int = 5

    @OptIn(ExperimentalSerializationApi::class)
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        explicitNulls = false
        prettyPrint = true
        prettyPrintIndent = "  "
    }

    fun decode(raw: String): CatalogManifest =
        json.decodeFromString(CatalogManifest.serializer(), raw)

    fun encode(manifest: CatalogManifest): String =
        json.encodeToString(CatalogManifest.serializer(), manifest) + "\n"
}
