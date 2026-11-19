package rs.belgrade.tvcams.data

import android.app.Application
import android.graphics.BitmapFactory
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.test.assertEquals
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import rs.belgrade.tvcams.AppContainer
import rs.belgrade.tvcams.data.catalog.AssetCatalogSeedSource
import rs.belgrade.tvcams.domain.model.StreamDescriptor
import rs.belgrade.tvcams.domain.model.StreamTransportType
import rs.belgrade.tvcams.domain.stream.SnapshotUrlBuilder
import java.io.File
import java.math.BigInteger
import java.net.URI
import java.util.concurrent.ConcurrentHashMap
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class LiveCatalogWatchabilityAuditTest {

    data class StreamProbeOutcome(
        val type: String,
        val url: String,
        val watchable: Boolean,
        val stage: String,
        val statusCode: Int? = null,
        val mediaDetail: String? = null,
        val error: String? = null,
    ) {
        fun toJson(): JsonObject = buildJsonObject {
            put("type", type)
            put("url", url)
            put("watchable", watchable)
            put("stage", stage)
            statusCode?.let { put("statusCode", it) }
            mediaDetail?.let { put("mediaDetail", it) }
            error?.let { put("error", it) }
        }
    }

    data class CameraWatchabilityEntry(
        val id: String,
        val name: String,
        val country: String,
        val city: String,
        val watchable: Boolean,
        val activeStreamIndex: Int,
        val activeTransport: String?,
        val degradedFromLiveToSnapshot: Boolean,
        val streamOutcomes: List<StreamProbeOutcome>,
    ) {
        fun toJson(): JsonObject = buildJsonObject {
            put("id", id)
            put("name", name)
            put("country", country)
            put("city", city)
            put("watchable", watchable)
            put("activeStreamIndex", activeStreamIndex)
            activeTransport?.let { put("activeTransport", it) }
            put("degradedFromLiveToSnapshot", degradedFromLiveToSnapshot)
            put("streamOutcomes", buildJsonArray { streamOutcomes.forEach { add(it.toJson()) } })
        }
    }

    private data class StreamKey(
        val type: StreamTransportType,
        val url: String,
        val headers: Map<String, String>,
        val cacheBusterParam: String?,
    )

    @Test
    fun verifyAllBundledStreamsAreEndToEndWatchable() {
        assumeTrue(
            "Pass -PverifyLiveStreams=true to execute live end-to-end stream watchability audit",
            System.getProperty("tvcams.verifyLiveStreams") == "true",
        )

        val app = ApplicationProvider.getApplicationContext<Application>()
        val container = AppContainer(app)
        val manifest = runBlocking {
            AssetCatalogSeedSource(app).readSeed()
        }

        val uniqueDescriptors = LinkedHashMap<StreamKey, StreamDescriptor>()
        for (cam in manifest.cameras) {
            for (stream in cam.streams) {
                val key = StreamKey(
                    type = stream.type,
                    url = stream.url,
                    headers = stream.headers.orEmpty(),
                    cacheBusterParam = stream.cacheBusterParam,
                )
                uniqueDescriptors.putIfAbsent(key, stream)
            }
        }

        val globalSem = Semaphore(14)
        val youtubeSem = Semaphore(3)
        val hostSemaphores = ConcurrentHashMap<String, Semaphore>()

        val outcomesByKey: Map<StreamKey, StreamProbeOutcome> = runBlocking(Dispatchers.IO) {
            uniqueDescriptors.entries.map { (key, descriptor) ->
                async {
                    globalSem.withPermit {
                        val host = runCatching { URI(descriptor.url.trim()).host?.lowercase() }
                            .getOrNull()
                            ?.takeIf { it.isNotBlank() }
                            ?: "youtube-or-alias"
                        val hostSem = hostSemaphores.computeIfAbsent(host) { Semaphore(3) }
                        hostSem.withPermit {
                            val outcome = if (descriptor.type == StreamTransportType.YOUTUBE_LIVE) {
                                youtubeSem.withPermit {
                                    verifyWithSingleRetry(container, descriptor)
                                }
                            } else {
                                verifyWithSingleRetry(container, descriptor)
                            }
                            key to outcome
                        }
                    }
                }
            }.awaitAll().toMap()
        }

        val cameraEntries = manifest.cameras.map { cam ->
            val streamOutcomes = cam.streams.map { s ->
                val key = StreamKey(
                    type = s.type,
                    url = s.url,
                    headers = s.headers.orEmpty(),
                    cacheBusterParam = s.cacheBusterParam,
                )
                outcomesByKey.getValue(key)
            }
            val firstWatchableIdx = streamOutcomes.indexOfFirst { it.watchable }
            val activeTransport = if (firstWatchableIdx >= 0) {
                cam.streams[firstWatchableIdx].type
            } else {
                null
            }
            val primaryIsLive = cam.streams.first().type.isLiveVideo
            val degradedToSnap = firstWatchableIdx > 0 &&
                primaryIsLive &&
                activeTransport == StreamTransportType.SNAPSHOT_POLL

            CameraWatchabilityEntry(
                id = cam.id,
                name = cam.name,
                country = cam.resolvedCountryLabel,
                city = cam.resolvedCityLabel,
                watchable = firstWatchableIdx >= 0,
                activeStreamIndex = firstWatchableIdx,
                activeTransport = activeTransport?.name,
                degradedFromLiveToSnapshot = degradedToSnap,
                streamOutcomes = streamOutcomes,
            )
        }

        val unwatchable = cameraEntries.filter { !it.watchable }
        val degraded = cameraEntries.filter { it.degradedFromLiveToSnapshot }
        val failoverLive = cameraEntries.filter {
            it.watchable && it.activeStreamIndex > 0 && !it.degradedFromLiveToSnapshot
        }
        val brokenFallbacks = cameraEntries.filter { entry ->
            entry.activeStreamIndex == 0 && entry.streamOutcomes.any { !it.watchable }
        }

        val totalCameras = cameraEntries.size
        val watchableCameras = cameraEntries.count { it.watchable }
        val primaryHealthyCameras = cameraEntries.count { it.activeStreamIndex == 0 }
        val failoverHealthyCameras = cameraEntries.count { it.activeStreamIndex > 0 }
        val healthyUniqueStreams = outcomesByKey.values.count { it.watchable }
        val totalUniqueStreams = outcomesByKey.size

        val summaryJson = buildJsonObject {
            put("totalCameras", totalCameras)
            put("watchableCameras", watchableCameras)
            put("primaryHealthyCameras", primaryHealthyCameras)
            put("failoverHealthyCameras", failoverHealthyCameras)
            put("degradedLiveToSnapshotCameras", degraded.size)
            put("completelyUnwatchableCameras", unwatchable.size)
            put("camerasWithBrokenSecondaryFallback", brokenFallbacks.size)
            put("totalUniqueStreams", totalUniqueStreams)
            put("healthyUniqueStreams", healthyUniqueStreams)
            put("brokenUniqueStreams", totalUniqueStreams - healthyUniqueStreams)
            put("unwatchableCameraList", buildJsonArray { unwatchable.forEach { add(it.toJson()) } })
            put("degradedCameraList", buildJsonArray { degraded.forEach { add(it.toJson()) } })
            put("primaryFailedButFailoverLiveList", buildJsonArray { failoverLive.forEach { add(it.toJson()) } })
            put("brokenFallbackCameraList", buildJsonArray { brokenFallbacks.forEach { add(it.toJson()) } })
        }

        val prettyJson = Json { prettyPrint = true }
        val reportText = prettyJson.encodeToString(JsonObject.serializer(), summaryJson)
        val reportFile = File("build/reports/watchability_audit_report.json")
        reportFile.parentFile?.mkdirs()
        reportFile.writeText(reportText)

        println(
            "WATCHABILITY_SUMMARY: total=$totalCameras " +
                "watchable=$watchableCameras " +
                "primaryHealthy=$primaryHealthyCameras " +
                "failover=$failoverHealthyCameras " +
                "degradedToSnap=${degraded.size} " +
                "unwatchable=${unwatchable.size} " +
                "uniqueStreams=$healthyUniqueStreams/$totalUniqueStreams",
        )
        assertEquals(
            totalCameras,
            watchableCameras,
            "Unwatchable cameras detected: ${unwatchable.map { it.id }}",
        )
    }

    private suspend fun verifyWithSingleRetry(
        container: AppContainer,
        descriptor: StreamDescriptor,
    ): StreamProbeOutcome {
        val first = verifySingleStream(container, descriptor)
        if (first.watchable) return first
        delay(450L)
        if (descriptor.type != StreamTransportType.HLS &&
            descriptor.type != StreamTransportType.SNAPSHOT_POLL
        ) {
            container.streamUrlResolver.invalidate(descriptor.url)
        }
        return verifySingleStream(container, descriptor)
    }

    private suspend fun verifySingleStream(
        container: AppContainer,
        descriptor: StreamDescriptor,
    ): StreamProbeOutcome {
        return if (descriptor.type.isLiveVideo) {
            verifyLiveHlsStream(container, descriptor)
        } else {
            verifySnapshotStream(container.mediaHttpClient, descriptor)
        }
    }

    private suspend fun verifyLiveHlsStream(
        container: AppContainer,
        descriptor: StreamDescriptor,
    ): StreamProbeOutcome {
        val resolved = container.streamUrlResolver.resolveToHls(descriptor)
            ?: return StreamProbeOutcome(
                type = descriptor.type.name,
                url = descriptor.url,
                watchable = false,
                stage = "RESOLVE",
                error = "Resolver returned null (offline or unextractable)",
            )

        val mergedHeaders = HashMap<String, String>(descriptor.headers.orEmpty()).apply {
            putAll(resolved.headers)
        }

        // 1. Fetch outer (.m3u8) playlist
        val outerFetch = fetchTextResponse(container.mediaHttpClient, resolved.hlsUrl, mergedHeaders)
        if (!outerFetch.ok || outerFetch.body == null || outerFetch.finalUrl == null) {
            return StreamProbeOutcome(
                type = descriptor.type.name,
                url = descriptor.url,
                watchable = false,
                stage = "MASTER_M3U8",
                statusCode = outerFetch.statusCode,
                error = outerFetch.error ?: "Empty playlist response",
            )
        }
        val outerBody = outerFetch.body.trimStart()
        if (!outerBody.startsWith("#EXTM3U")) {
            return StreamProbeOutcome(
                type = descriptor.type.name,
                url = descriptor.url,
                watchable = false,
                stage = "MASTER_M3U8",
                statusCode = outerFetch.statusCode,
                error = "Body does not start with #EXTM3U",
            )
        }

        // 2. If master playlist (#EXT-X-STREAM-INF), follow first variant playlist
        val mediaBody: String
        val mediaPlaylistUrl: HttpUrl
        if (outerBody.contains("#EXT-X-STREAM-INF")) {
            val variantUri = extractFirstVariantUri(outerBody)
                ?: return StreamProbeOutcome(
                    type = descriptor.type.name,
                    url = descriptor.url,
                    watchable = false,
                    stage = "MASTER_M3U8",
                    statusCode = outerFetch.statusCode,
                    error = "Master #EXTM3U has #EXT-X-STREAM-INF without variant URI",
                )
            val resolvedVariantUrl = outerFetch.finalUrl.resolve(variantUri)
                ?: return StreamProbeOutcome(
                    type = descriptor.type.name,
                    url = descriptor.url,
                    watchable = false,
                    stage = "MEDIA_M3U8",
                    error = "Invalid variant URI: $variantUri",
                )
            val variantFetch = fetchTextResponse(
                container.mediaHttpClient,
                resolvedVariantUrl.toString(),
                mergedHeaders,
            )
            if (!variantFetch.ok || variantFetch.body == null || variantFetch.finalUrl == null) {
                return StreamProbeOutcome(
                    type = descriptor.type.name,
                    url = descriptor.url,
                    watchable = false,
                    stage = "MEDIA_M3U8",
                    statusCode = variantFetch.statusCode,
                    error = variantFetch.error ?: "Variant playlist fetch failed",
                )
            }
            if (!variantFetch.body.trimStart().startsWith("#EXTM3U")) {
                return StreamProbeOutcome(
                    type = descriptor.type.name,
                    url = descriptor.url,
                    watchable = false,
                    stage = "MEDIA_M3U8",
                    statusCode = variantFetch.statusCode,
                    error = "Variant body does not start with #EXTM3U",
                )
            }
            mediaBody = variantFetch.body
            mediaPlaylistUrl = variantFetch.finalUrl
        } else {
            mediaBody = outerBody
            mediaPlaylistUrl = outerFetch.finalUrl
        }

        // 3. Extract latest media segment URI (or EXT-X-MAP init + media segment)
        val segmentUris = extractMediaSegmentUris(mediaBody)
        if (segmentUris.isEmpty()) {
            return StreamProbeOutcome(
                type = descriptor.type.name,
                url = descriptor.url,
                watchable = false,
                stage = "MEDIA_M3U8",
                statusCode = 200,
                error = "Media playlist contains 0 segments",
            )
        }

        val targetSegmentRaw = segmentUris.last()
        val segmentUrl = mediaPlaylistUrl.resolve(targetSegmentRaw)
            ?: return StreamProbeOutcome(
                type = descriptor.type.name,
                url = descriptor.url,
                watchable = false,
                stage = "SEGMENT",
                error = "Invalid segment URI: $targetSegmentRaw",
            )

        // 4. Download up to 64 KB of actual media segment bytes
        val segFetch = fetchMediaPrefixBytes(container.mediaHttpClient, segmentUrl.toString(), mergedHeaders)
        if (!segFetch.ok || segFetch.bytes == null) {
            return StreamProbeOutcome(
                type = descriptor.type.name,
                url = descriptor.url,
                watchable = false,
                stage = "SEGMENT",
                statusCode = segFetch.statusCode,
                error = segFetch.error ?: "Segment fetch failed",
            )
        }

        val bytes = segFetch.bytes
        if (bytes.size < 256) {
            return StreamProbeOutcome(
                type = descriptor.type.name,
                url = descriptor.url,
                watchable = false,
                stage = "SEGMENT",
                statusCode = segFetch.statusCode,
                error = "Segment too small (${bytes.size} bytes)",
            )
        }

        val aesKeyInfo = extractAes128KeyInfo(mediaBody, segmentIndex = segmentUris.lastIndex)
        val payloadBytes: ByteArray
        val encryptionSuffix: String
        if (aesKeyInfo != null) {
            val resolvedKeyUrl = mediaPlaylistUrl.resolve(aesKeyInfo.keyUri)
                ?: return StreamProbeOutcome(
                    type = descriptor.type.name,
                    url = descriptor.url,
                    watchable = false,
                    stage = "HLS_KEY",
                    error = "Invalid #EXT-X-KEY URI: ${aesKeyInfo.keyUri}",
                )
            val keyFetch = fetchMediaPrefixBytes(
                container.mediaHttpClient,
                resolvedKeyUrl.toString(),
                mergedHeaders,
                maxBytes = 64,
            )
            val keyBytes = keyFetch.bytes
            if (!keyFetch.ok || keyBytes == null || keyBytes.size != 16) {
                return StreamProbeOutcome(
                    type = descriptor.type.name,
                    url = descriptor.url,
                    watchable = false,
                    stage = "HLS_KEY",
                    statusCode = keyFetch.statusCode,
                    error = keyFetch.error ?: "Expected 16-byte AES-128 key, got ${keyBytes?.size ?: 0}B",
                )
            }
            payloadBytes = decryptAes128Prefix(bytes, keyBytes, aesKeyInfo.ivBytes)
                ?: return StreamProbeOutcome(
                    type = descriptor.type.name,
                    url = descriptor.url,
                    watchable = false,
                    stage = "HLS_DECRYPT",
                    statusCode = segFetch.statusCode,
                    error = "AES-128 segment decryption failed",
                )
            encryptionSuffix = "+AES-128"
        } else {
            payloadBytes = bytes
            encryptionSuffix = ""
        }

        val containerFormat = classifyMediaSegmentBytes(payloadBytes)
            ?: return StreamProbeOutcome(
                type = descriptor.type.name,
                url = descriptor.url,
                watchable = false,
                stage = "SEGMENT",
                statusCode = segFetch.statusCode,
                error = "Unrecognized segment payload (${bytes.size} bytes)",
            )

        return StreamProbeOutcome(
            type = descriptor.type.name,
            url = descriptor.url,
            watchable = true,
            stage = "OK",
            statusCode = segFetch.statusCode,
            mediaDetail = "$containerFormat$encryptionSuffix (${bytes.size}B sampled, ${segmentUris.size} segs)",
        )
    }

    private fun verifySnapshotStream(
        httpClient: OkHttpClient,
        descriptor: StreamDescriptor,
    ): StreamProbeOutcome {
        val polledUrl = runCatching {
            SnapshotUrlBuilder.buildPolledUrl(
                baseUrl = descriptor.url,
                paramName = descriptor.cacheBusterParam,
                epochMillis = System.currentTimeMillis(),
            )
        }.getOrElse { err ->
            return StreamProbeOutcome(
                type = descriptor.type.name,
                url = descriptor.url,
                watchable = false,
                stage = "SNAPSHOT_URL",
                error = "${err::class.simpleName}: ${err.message}",
            )
        }

        val reqBuilder = Request.Builder().url(polledUrl).get()
        descriptor.headers.orEmpty().forEach { (k, v) -> reqBuilder.header(k, v) }

        return try {
            httpClient.newCall(reqBuilder.build()).execute().use { resp ->
                if (!resp.isSuccessful) {
                    return@use StreamProbeOutcome(
                        type = descriptor.type.name,
                        url = descriptor.url,
                        watchable = false,
                        stage = "SNAPSHOT_HTTP",
                        statusCode = resp.code,
                        error = "HTTP ${resp.code}",
                    )
                }
                val bytes = resp.body?.bytes()
                if (bytes == null || bytes.size < 512) {
                    return@use StreamProbeOutcome(
                        type = descriptor.type.name,
                        url = descriptor.url,
                        watchable = false,
                        stage = "BITMAP_DECODE",
                        statusCode = resp.code,
                        error = "Empty or tiny image body (${bytes?.size ?: 0} bytes)",
                    )
                }
                val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeByteArray(bytes, 0, bytes.size, opts)
                if (opts.outWidth < 64 || opts.outHeight < 64) {
                    return@use StreamProbeOutcome(
                        type = descriptor.type.name,
                        url = descriptor.url,
                        watchable = false,
                        stage = "BITMAP_DECODE",
                        statusCode = resp.code,
                        error = "Invalid image dimensions ${opts.outWidth}x${opts.outHeight} (${bytes.size}B)",
                    )
                }
                StreamProbeOutcome(
                    type = descriptor.type.name,
                    url = descriptor.url,
                    watchable = true,
                    stage = "OK",
                    statusCode = resp.code,
                    mediaDetail = "${opts.outMimeType ?: "image"} ${opts.outWidth}x${opts.outHeight} (${bytes.size}B)",
                )
            }
        } catch (err: Exception) {
            StreamProbeOutcome(
                type = descriptor.type.name,
                url = descriptor.url,
                watchable = false,
                stage = "SNAPSHOT_HTTP",
                error = "${err::class.simpleName}: ${err.message}",
            )
        }
    }

    private data class TextFetchResult(
        val ok: Boolean,
        val statusCode: Int? = null,
        val finalUrl: HttpUrl? = null,
        val body: String? = null,
        val error: String? = null,
    )

    private data class BytesFetchResult(
        val ok: Boolean,
        val statusCode: Int? = null,
        val bytes: ByteArray? = null,
        val error: String? = null,
    )

    private fun fetchTextResponse(
        client: OkHttpClient,
        url: String,
        headers: Map<String, String>,
    ): TextFetchResult {
        val parsed = url.toHttpUrlOrNull()
            ?: return TextFetchResult(ok = false, error = "Malformed URL: $url")
        val reqBuilder = Request.Builder().url(parsed).get()
        headers.forEach { (k, v) -> reqBuilder.header(k, v) }
        return try {
            client.newCall(reqBuilder.build()).execute().use { resp ->
                if (!resp.isSuccessful) {
                    TextFetchResult(ok = false, statusCode = resp.code, error = "HTTP ${resp.code}")
                } else {
                    TextFetchResult(
                        ok = true,
                        statusCode = resp.code,
                        finalUrl = resp.request.url,
                        body = resp.body?.string(),
                    )
                }
            }
        } catch (err: Exception) {
            TextFetchResult(ok = false, error = "${err::class.simpleName}: ${err.message}")
        }
    }

    private fun fetchMediaPrefixBytes(
        client: OkHttpClient,
        url: String,
        headers: Map<String, String>,
        maxBytes: Int = 65_536,
    ): BytesFetchResult {
        val parsed = url.toHttpUrlOrNull()
            ?: return BytesFetchResult(ok = false, error = "Malformed segment URL: $url")
        val reqBuilder = Request.Builder().url(parsed).get()
        headers.forEach { (k, v) -> reqBuilder.header(k, v) }
        return try {
            client.newCall(reqBuilder.build()).execute().use { resp ->
                if (!resp.isSuccessful) {
                    return@use BytesFetchResult(
                        ok = false,
                        statusCode = resp.code,
                        error = "HTTP ${resp.code}",
                    )
                }
                val stream = resp.body?.byteStream()
                    ?: return@use BytesFetchResult(ok = false, statusCode = resp.code, error = "Null body")
                val buf = ByteArray(maxBytes)
                var total = 0
                while (total < maxBytes) {
                    val read = stream.read(buf, total, maxBytes - total)
                    if (read <= 0) break
                    total += read
                }
                BytesFetchResult(
                    ok = true,
                    statusCode = resp.code,
                    bytes = buf.copyOf(total),
                )
            }
        } catch (err: Exception) {
            BytesFetchResult(ok = false, error = "${err::class.simpleName}: ${err.message}")
        }
    }

    private fun extractFirstVariantUri(masterBody: String): String? {
        val lines = masterBody.lines()
        var expectVariant = false
        for (raw in lines) {
            val line = raw.trim()
            if (line.isEmpty()) continue
            if (line.startsWith("#EXT-X-STREAM-INF")) {
                expectVariant = true
                continue
            }
            if (expectVariant && !line.startsWith("#")) {
                return line
            }
        }
        return null
    }

    private fun extractMediaSegmentUris(mediaBody: String): List<String> =
        mediaBody.lines()
            .map { it.trim() }
            .filter { it.isNotEmpty() && !it.startsWith("#") }

    private fun classifyMediaSegmentBytes(bytes: ByteArray): String? {
        val headAscii = String(bytes, 0, minOf(bytes.size, 64), Charsets.ISO_8859_1).lowercase()
        if (headAscii.trimStart().startsWith("<html") || headAscii.trimStart().startsWith("<!doc")) {
            return null
        }
        // Check MPEG-TS 0x47 sync byte (including possible ID3v2 header prefix)
        for (i in 0 until minOf(bytes.size - 188, 1024)) {
            if (bytes[i] == 0x47.toByte() && bytes[i + 188] == 0x47.toByte()) {
                return "MPEG-TS"
            }
        }
        if (bytes[0] == 0x47.toByte()) {
            return "MPEG-TS"
        }
        // Check ISO BMFF / fMP4 box signatures (ftyp, styp, moof, sidx, emsg, free, mdat)
        val boxHeader = String(bytes, 0, minOf(bytes.size, 32), Charsets.ISO_8859_1)
        if (listOf("ftyp", "styp", "moof", "sidx", "emsg", "mdat").any { boxHeader.contains(it) }) {
            return "fMP4"
        }
        // Check AAC ADTS syncword 0xFFFx
        if (bytes.size >= 4 && bytes[0] == 0xFF.toByte() && (bytes[1].toInt() and 0xF0) == 0xF0) {
            return "AAC-ADTS"
        }
        return null
    }

    private data class Aes128KeyInfo(
        val keyUri: String,
        val ivBytes: ByteArray,
    )

    private fun extractAes128KeyInfo(mediaBody: String, segmentIndex: Int): Aes128KeyInfo? {
        val seqRegex = Regex("#EXT-X-MEDIA-SEQUENCE:(\\d+)")
        val mediaSequence = seqRegex.find(mediaBody)?.groupValues?.getOrNull(1)?.toLongOrNull() ?: 0L

        val keyLine = mediaBody.lineSequence()
            .map { it.trim() }
            .lastOrNull { it.startsWith("#EXT-X-KEY:") && it.contains("METHOD=AES-128") }
            ?: return null

        val uriMatch = Regex("URI=\"([^\"]+)\"").find(keyLine) ?: return null
        val keyUri = uriMatch.groupValues[1]

        val ivHexMatch = Regex("IV=0[xX]([0-9a-fA-F]{1,32})").find(keyLine)
        val ivNumber = if (ivHexMatch != null) {
            BigInteger(ivHexMatch.groupValues[1], 16)
        } else {
            BigInteger.valueOf(mediaSequence + segmentIndex.toLong())
        }
        val rawIv = ivNumber.toByteArray()
        val iv16 = ByteArray(16)
        val copyLen = minOf(rawIv.size, 16)
        System.arraycopy(rawIv, rawIv.size - copyLen, iv16, 16 - copyLen, copyLen)

        return Aes128KeyInfo(keyUri = keyUri, ivBytes = iv16)
    }

    private fun decryptAes128Prefix(
        ciphertext: ByteArray,
        keyBytes: ByteArray,
        ivBytes: ByteArray,
    ): ByteArray? = runCatching {
        val blockMultipleLen = (ciphertext.size / 16) * 16
        if (blockMultipleLen < 256) return null
        val cipher = Cipher.getInstance("AES/CBC/NoPadding")
        cipher.init(
            Cipher.DECRYPT_MODE,
            SecretKeySpec(keyBytes, "AES"),
            IvParameterSpec(ivBytes),
        )
        cipher.doFinal(ciphertext, 0, blockMultipleLen)
    }.getOrNull()
}
