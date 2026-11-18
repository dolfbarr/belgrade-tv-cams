package rs.belgrade.tvcams.domain.model

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
enum class CameraCountry {
    SERBIA,
    RUSSIA,
    SPAIN,
    ROMANIA,
    ;

    fun displayLabel(): String = when (this) {
        SERBIA -> "Serbia"
        RUSSIA -> "Russia"
        SPAIN -> "Spain"
        ROMANIA -> "Romania"
    }
}

@Serializable
enum class CameraCity(val country: CameraCountry) {
    BELGRADE(CameraCountry.SERBIA),
    ZEMUN(CameraCountry.SERBIA),
    NOVI_SAD(CameraCountry.SERBIA),
    SUBOTICA(CameraCountry.SERBIA),
    NIS(CameraCountry.SERBIA),
    SAINT_PETERSBURG(CameraCountry.RUSSIA),
    ROSTOV_ON_DON(CameraCountry.RUSSIA),
    TOMSK(CameraCountry.RUSSIA),
    BARCELONA(CameraCountry.SPAIN),
    MADRID(CameraCountry.SPAIN),
    BUCHAREST(CameraCountry.ROMANIA),
    ;

    fun displayLabel(): String = when (this) {
        BELGRADE -> "Belgrade"
        ZEMUN -> "Zemun"
        NOVI_SAD -> "Novi Sad"
        SUBOTICA -> "Subotica"
        NIS -> "Niš"
        SAINT_PETERSBURG -> "Saint Petersburg"
        ROSTOV_ON_DON -> "Rostov-on-Don"
        TOMSK -> "Tomsk"
        BARCELONA -> "Barcelona"
        MADRID -> "Madrid"
        BUCHAREST -> "Bucharest"
    }

    companion object {
        fun citiesFor(country: CameraCountry): List<CameraCity> =
            entries.filter { it.country == country }
    }
}

typealias SerbiaCity = CameraCity

@Serializable
enum class VideoQuality {
    UHD_2K,
    FHD_1080P,
    HD_720P,
    SD,
    ;

    val isHd: Boolean
        get() = when (this) {
            UHD_2K, FHD_1080P, HD_720P -> true
            SD -> false
        }

    val sortRank: Int
        get() = when (this) {
            UHD_2K -> 0
            FHD_1080P -> 1
            HD_720P -> 2
            SD -> 3
        }

    fun shortBadgeLabel(): String = when (this) {
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
    SNAPSHOT_POLL,
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
    val city: CameraCity = CameraCity.BELGRADE,
    val quality: VideoQuality? = null,
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
        get() = country ?: city.country

    val resolvedQuality: VideoQuality
        get() = quality ?: VideoQuality.SD
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
    val SUPPORTED_SCHEMA_VERSIONS: Set<Int> = setOf(1, 2, 3, 4)
    const val LATEST_SCHEMA_VERSION: Int = 4

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
