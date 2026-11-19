package rs.belgrade.tvcams.data.stream

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import rs.belgrade.tvcams.domain.model.StreamDescriptor
import rs.belgrade.tvcams.domain.model.StreamTransportType
import rs.belgrade.tvcams.domain.stream.ResolvedHlsStream
import rs.belgrade.tvcams.domain.stream.StreamPayloadParsers
import rs.belgrade.tvcams.domain.stream.StreamUrlResolver

class OkHttpStreamUrlResolver(
    private val generalHttpClient: OkHttpClient,
    private val mediaHttpClient: OkHttpClient,
    private val clockMillis: () -> Long = System::currentTimeMillis,
    private val cacheTtlMillis: Long = DEFAULT_CACHE_TTL_MS,
    private val youtubeInnertubeUrl: String = StreamPayloadParsers.YOUTUBE_INNERTUBE_PLAYER_URL,
    private val rutubeOptionsBaseUrl: String = DEFAULT_RUTUBE_OPTIONS_BASE_URL,
    private val ipCamLiveStateUrl: String = DEFAULT_IPCAMLIVE_STATE_URL,
) : StreamUrlResolver {

    private data class CachedResolution(
        val resolved: ResolvedHlsStream,
        val expiresAtMs: Long,
    )

    private data class TimedResolution(
        val resolved: ResolvedHlsStream,
        val ttlMillis: Long,
    )

    private val mutex = Mutex()
    private val cache = HashMap<String, CachedResolution>()

    suspend fun invalidate(streamUrl: String) {
        mutex.withLock {
            cache.keys.removeAll { key -> key == streamUrl || key.endsWith(":$streamUrl") }
        }
    }

    override suspend fun resolveToHls(stream: StreamDescriptor): ResolvedHlsStream? {
        if (stream.type == StreamTransportType.SNAPSHOT_POLL) return null
        if (stream.type == StreamTransportType.HLS) {
            return ResolvedHlsStream(
                hlsUrl = stream.url,
                headers = stream.headers.orEmpty(),
            )
        }

        val cacheKey = "${stream.type.name}:${stream.url}"
        mutex.withLock {
            val hit = cache[cacheKey]
            if (hit != null && clockMillis() < hit.expiresAtMs) {
                return hit.resolved
            }
        }

        val timed = withContext(Dispatchers.IO) {
            when (stream.type) {
                StreamTransportType.YOUTUBE_LIVE -> resolveYouTubeLive(stream)
                StreamTransportType.RUTUBE_LIVE -> resolveRutubeLive(stream)
                    ?.let { TimedResolution(it, cacheTtlMillis) }
                StreamTransportType.IPCAMLIVE -> resolveIpCamLive(stream)
                    ?.let { TimedResolution(it, cacheTtlMillis) }
                StreamTransportType.HLS -> TimedResolution(
                    ResolvedHlsStream(stream.url, stream.headers.orEmpty()),
                    cacheTtlMillis,
                )
                StreamTransportType.SNAPSHOT_POLL -> null
            }
        }

        if (timed != null) {
            mutex.withLock {
                cache[cacheKey] = CachedResolution(
                    resolved = timed.resolved,
                    expiresAtMs = clockMillis() + timed.ttlMillis,
                )
            }
        }
        return timed?.resolved
    }

    private fun resolveYouTubeLive(stream: StreamDescriptor): TimedResolution? = runCatching {
        val videoId = StreamPayloadParsers.extractYouTubeVideoId(stream.url)
            ?: resolveCanonicalYouTubeLiveId(stream.url)
            ?: return@runCatching null

        val requestBody = StreamPayloadParsers.buildYouTubeInnertubePlayerPayload(videoId)
            .toRequestBody(JSON_MEDIA_TYPE)

        val req = Request.Builder()
            .url(youtubeInnertubeUrl)
            .post(requestBody)
            .header("User-Agent", StreamPayloadParsers.YOUTUBE_ANDROID_APP_UA)
            .header("X-YouTube-Client-Name", StreamPayloadParsers.YOUTUBE_ANDROID_CLIENT_NAME_ID)
            .header("X-YouTube-Client-Version", StreamPayloadParsers.YOUTUBE_ANDROID_CLIENT_VERSION)
            .header("Origin", "https://www.youtube.com")
            .header("Accept-Language", "en-US,en;q=0.9")
            .build()

        generalHttpClient.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) return@use null
            val body = resp.body?.string() ?: return@use null
            val yt = StreamPayloadParsers.extractYouTubeInnertubeHls(body) ?: return@use null
            val effectiveTtlMs = yt.expiresInSeconds
                ?.let { sec -> ((sec - 60L).coerceAtLeast(30L)) * 1000L }
                ?.let { minOf(cacheTtlMillis, it) }
                ?: cacheTtlMillis

            TimedResolution(
                resolved = ResolvedHlsStream(
                    hlsUrl = yt.hlsUrl,
                    headers = mapOf("User-Agent" to StreamPayloadParsers.YOUTUBE_ANDROID_APP_UA),
                ),
                ttlMillis = effectiveTtlMs,
            )
        }
    }.getOrNull()

    private fun resolveCanonicalYouTubeLiveId(channelLiveUrl: String): String? = runCatching {
        val matchKeyword = StreamPayloadParsers.extractYouTubeMatchQuery(channelLiveUrl)
        val endpointUrl = StreamPayloadParsers.normalizeYouTubeChannelEndpointUrl(channelLiveUrl)
        val req = Request.Builder()
            .url(endpointUrl)
            .get()
            .header("User-Agent", StreamPayloadParsers.YOUTUBE_DESKTOP_WEB_UA)
            .header("Cookie", StreamPayloadParsers.YOUTUBE_SOCS_COOKIE)
            .header("Accept-Language", "en-US,en;q=0.9")
            .build()

        generalHttpClient.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) return@use null
            val html = resp.body?.string() ?: return@use null
            if (matchKeyword != null) {
                StreamPayloadParsers.extractYouTubeStreamsTabLiveVideoId(html, matchKeyword)
            } else {
                StreamPayloadParsers.extractYouTubeCanonicalVideoId(html)
            }
        }
    }.getOrNull()

    private fun resolveRutubeLive(stream: StreamDescriptor): ResolvedHlsStream? = runCatching {
        val videoId = StreamPayloadParsers.extractRutubeVideoId(stream.url)
        if (videoId != null) {
            val apiUrl = "${rutubeOptionsBaseUrl.trimEnd('/')}/$videoId/" +
                "?no_404=true&referer=https%3A%2F%2Frutube.ru"
            val req = Request.Builder()
                .url(apiUrl)
                .get()
                .header("Accept", "application/json")
                .build()
            mediaHttpClient.newCall(req).execute().use { resp ->
                if (resp.isSuccessful) {
                    val body = resp.body?.string()
                    val freshHls = body?.let(StreamPayloadParsers::extractRutubeHlsUrl)
                    if (freshHls != null) {
                        return@runCatching ResolvedHlsStream(
                            hlsUrl = freshHls,
                            headers = stream.headers.orEmpty(),
                        )
                    }
                }
            }
        }
        if (stream.url.contains(".m3u8")) {
            ResolvedHlsStream(hlsUrl = stream.url, headers = stream.headers.orEmpty())
        } else {
            null
        }
    }.getOrNull()

    private fun resolveIpCamLive(stream: StreamDescriptor): ResolvedHlsStream? = runCatching {
        val alias = StreamPayloadParsers.extractIpCamLiveAlias(stream.url) ?: return@runCatching null
        val stateUrl = "$ipCamLiveStateUrl?cameraalias=$alias"
        val req = Request.Builder()
            .url(stateUrl)
            .get()
            .header("Accept", "application/json")
            .header("Referer", "https://ipcamlive.com/$alias")
            .build()

        generalHttpClient.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) return@use null
            val body = resp.body?.string() ?: return@use null
            val hlsUrl = StreamPayloadParsers.extractIpCamLiveHlsUrl(body) ?: return@use null
            ResolvedHlsStream(
                hlsUrl = hlsUrl,
                headers = mapOf("Referer" to "https://ipcamlive.com/"),
            )
        }
    }.getOrNull()

    companion object {
        const val DEFAULT_CACHE_TTL_MS: Long = 20L * 60L * 1000L
        const val DEFAULT_RUTUBE_OPTIONS_BASE_URL: String = "https://rutube.ru/api/play/options"
        const val DEFAULT_IPCAMLIVE_STATE_URL: String = "https://ipcamlive.com/ajax/getcamerastreamstate.php"
        private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
    }
}
