package rs.belgrade.tvcams.domain.stream

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import rs.belgrade.tvcams.domain.model.StreamDescriptor

data class ResolvedHlsStream(
    val hlsUrl: String,
    val headers: Map<String, String> = emptyMap(),
)

interface StreamUrlResolver {
    suspend fun resolveToHls(stream: StreamDescriptor): ResolvedHlsStream?
}

object StreamPayloadParsers {
    const val YOUTUBE_IOS_SAFARI_UA: String =
        "Mozilla/5.0 (iPhone; CPU iPhone OS 18_2 like Mac OS X) " +
            "AppleWebKit/605.1.15 (KHTML, like Gecko) Version/18.2 Mobile/15E148 Safari/604.1"

    const val YOUTUBE_SOCS_COOKIE: String =
        "SOCS=CAISNQgDEitib3FfaWRlbnRpdHlmcm9udGVuZHVpc2VydmVyXzIwMjMwODI5LjA3X3AxGgJlbiACGgYIgJnPpwY"

    private val YOUTUBE_ID_RE = Regex("^[A-Za-z0-9_-]{11}$")
    private val YOUTUBE_WATCH_ID_RE = Regex("[?&]v=([A-Za-z0-9_-]{11})")
    private val YOUTUBE_SHORT_ID_RE = Regex("youtu\\.be/([A-Za-z0-9_-]{11})")
    private val YOUTUBE_EMBED_ID_RE = Regex("youtube\\.com/(?:embed|live)/([A-Za-z0-9_-]{11})")

    private val RAW_HLS_MANIFEST_RE = Regex("\"hlsManifestUrl\"\\s*:\\s*\"(https:[^\"]+)\"")
    private val ESCAPED_HLS_MANIFEST_RE =
        Regex("\\\\x22hlsManifestUrl\\\\x22\\s*:\\s*\\\\x22(https:[^\"\\\\]+)\\\\x22")

    private val RUTUBE_HEX_RE = Regex("([0-9a-f]{32})", RegexOption.IGNORE_CASE)
    private val IPCAMLIVE_ALIAS_PARAM_RE = Regex("[?&](?:camera)?alias=([A-Za-z0-9_-]+)")
    private val IPCAMLIVE_PATH_RE = Regex("ipcamlive\\.com/([A-Za-z0-9_-]+)/?$")
    private val PLAIN_ALIAS_RE = Regex("^[A-Za-z0-9_-]{3,64}$")

    private val lenientJson = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    fun normalizeYouTubeLiveWatchUrl(urlOrId: String): String {
        val trimmed = urlOrId.trim()
        if (YOUTUBE_ID_RE.matches(trimmed)) {
            return "https://m.youtube.com/watch?v=$trimmed"
        }
        YOUTUBE_WATCH_ID_RE.find(trimmed)?.groupValues?.getOrNull(1)?.let { id ->
            return "https://m.youtube.com/watch?v=$id"
        }
        YOUTUBE_SHORT_ID_RE.find(trimmed)?.groupValues?.getOrNull(1)?.let { id ->
            return "https://m.youtube.com/watch?v=$id"
        }
        YOUTUBE_EMBED_ID_RE.find(trimmed)?.groupValues?.getOrNull(1)?.let { id ->
            return "https://m.youtube.com/watch?v=$id"
        }
        return trimmed
            .replace("https://www.youtube.com/", "https://m.youtube.com/")
            .replace("https://youtube.com/", "https://m.youtube.com/")
    }

    fun extractYouTubeHlsUrl(htmlOrJson: String): String? {
        val rawCandidate = RAW_HLS_MANIFEST_RE.find(htmlOrJson)?.groupValues?.getOrNull(1)
            ?: ESCAPED_HLS_MANIFEST_RE.find(htmlOrJson)?.groupValues?.getOrNull(1)
            ?: return null

        val unescaped = rawCandidate
            .replace("\\/", "/")
            .replace("\\u0026", "&")
            .replace("\\x26", "&")
            .replace("\\x3d", "=")
            .trim()

        return unescaped.takeIf { it.startsWith("https://") && it.contains(".m3u8") }
    }

    fun extractRutubeVideoId(urlOrId: String): String? =
        RUTUBE_HEX_RE.find(urlOrId.trim())?.groupValues?.getOrNull(1)?.lowercase()

    fun extractRutubeHlsUrl(optionsJson: String): String? = runCatching {
        val root = lenientJson.parseToJsonElement(optionsJson).jsonObject
        val hlsArray = root["live_streams"]?.jsonObject?.get("hls")?.jsonArray ?: return null
        val firstUrl = hlsArray.firstOrNull()?.jsonObject?.get("url")?.jsonPrimitive?.content
        firstUrl?.trim()?.takeIf { it.startsWith("http") && it.contains(".m3u8") }
    }.getOrNull()

    fun extractIpCamLiveAlias(urlOrAlias: String): String? {
        val trimmed = urlOrAlias.trim()
        if (PLAIN_ALIAS_RE.matches(trimmed)) return trimmed
        IPCAMLIVE_ALIAS_PARAM_RE.find(trimmed)?.groupValues?.getOrNull(1)?.let { return it }
        IPCAMLIVE_PATH_RE.find(trimmed)?.groupValues?.getOrNull(1)?.let { return it }
        return null
    }

    fun extractIpCamLiveHlsUrl(stateJson: String): String? = runCatching {
        val root = lenientJson.parseToJsonElement(stateJson).jsonObject
        val details = root["details"]?.jsonObject ?: return null
        val available = details["streamavailable"]?.jsonPrimitive?.content
        if (available != "1") return null

        val rawAddress = details["address"]?.jsonPrimitive?.content?.trim().orEmpty()
        val streamId = details["streamid"]?.jsonPrimitive?.content?.trim().orEmpty()
        if (rawAddress.isEmpty() || streamId.isEmpty()) return null

        val httpsBase = rawAddress
            .replace("http://", "https://")
            .trimEnd('/')
        val playlistName = root["streaminfo"]
            ?.jsonObject?.get("live")
            ?.jsonObject?.get("levels")
            ?.jsonArray?.firstOrNull()
            ?.jsonObject?.get("url")
            ?.jsonPrimitive?.content
            ?.trim()
            ?.takeIf { it.isNotEmpty() }
            ?: "stream.m3u8"

        "$httpsBase/streams/$streamId/$playlistName"
    }.getOrNull()
}
