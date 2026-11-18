package rs.belgrade.tvcams.domain.model

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
enum class SerbiaCity {
    BELGRADE,
    NOVI_SAD,
    SUBOTICA,
    NIS,
    ;

    fun displayLabel(): String = when (this) {
        BELGRADE -> "Belgrade"
        NOVI_SAD -> "Novi Sad"
        SUBOTICA -> "Subotica"
        NIS -> "Niš"
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
    val city: SerbiaCity = SerbiaCity.BELGRADE,
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
)

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
    val SUPPORTED_SCHEMA_VERSIONS: Set<Int> = setOf(1, 2)
    const val LATEST_SCHEMA_VERSION: Int = 2

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
