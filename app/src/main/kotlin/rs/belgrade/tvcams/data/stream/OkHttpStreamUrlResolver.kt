package rs.belgrade.tvcams.data.stream

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
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
) : StreamUrlResolver {

    private data class CachedResolution(
        val resolved: ResolvedHlsStream,
        val resolvedAtMs: Long,
    )

    private val mutex = Mutex()
    private val cache = HashMap<String, CachedResolution>()

    suspend fun invalidate(streamUrl: String) {
        mutex.withLock {
            cache.remove(streamUrl)
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
            if (hit != null && clockMillis() - hit.resolvedAtMs < cacheTtlMillis) {
                return hit.resolved
            }
        }

        val resolved = withContext(Dispatchers.IO) {
            when (stream.type) {
                StreamTransportType.YOUTUBE_LIVE -> resolveYouTubeLive(stream)
                StreamTransportType.RUTUBE_LIVE -> resolveRutubeLive(stream)
                StreamTransportType.IPCAMLIVE -> resolveIpCamLive(stream)
                StreamTransportType.HLS -> ResolvedHlsStream(stream.url, stream.headers.orEmpty())
                StreamTransportType.SNAPSHOT_POLL -> null
            }
        }

        if (resolved != null) {
            mutex.withLock {
                cache[cacheKey] = CachedResolution(
                    resolved = resolved,
                    resolvedAtMs = clockMillis(),
                )
            }
        }
        return resolved
    }

    private fun resolveYouTubeLive(stream: StreamDescriptor): ResolvedHlsStream? = runCatching {
        val watchUrl = StreamPayloadParsers.normalizeYouTubeLiveWatchUrl(stream.url)
        val req = Request.Builder()
            .url(watchUrl)
            .get()
            .header("User-Agent", StreamPayloadParsers.YOUTUBE_IOS_SAFARI_UA)
            .header("Cookie", StreamPayloadParsers.YOUTUBE_SOCS_COOKIE)
            .header("Accept-Language", "en-US,en;q=0.9")
            .build()

        generalHttpClient.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) return@use null
            val body = resp.body?.string() ?: return@use null
            val hlsUrl = StreamPayloadParsers.extractYouTubeHlsUrl(body) ?: return@use null
            ResolvedHlsStream(
                hlsUrl = hlsUrl,
                headers = mapOf("User-Agent" to StreamPayloadParsers.YOUTUBE_IOS_SAFARI_UA),
            )
        }
    }.getOrNull()

    private fun resolveRutubeLive(stream: StreamDescriptor): ResolvedHlsStream? = runCatching {
        val videoId = StreamPayloadParsers.extractRutubeVideoId(stream.url)
        if (videoId != null) {
            val apiUrl = "https://rutube.ru/api/play/options/$videoId/" +
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
        val stateUrl = "https://ipcamlive.com/ajax/getcamerastreamstate.php?cameraalias=$alias"
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
    }
}
