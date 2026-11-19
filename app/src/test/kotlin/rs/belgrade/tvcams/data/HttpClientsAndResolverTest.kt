package rs.belgrade.tvcams.data

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import okhttp3.Protocol
import org.junit.After
import org.junit.Before
import org.junit.Test
import rs.belgrade.tvcams.data.airquality.OpenMeteoAirQualityClient
import rs.belgrade.tvcams.data.catalog.FileCatalogCacheStore
import rs.belgrade.tvcams.data.catalog.OkHttpRemoteCatalogClient
import rs.belgrade.tvcams.data.favorites.DataStoreFavoritesRepository
import rs.belgrade.tvcams.data.stream.OkHttpStreamUrlResolver
import rs.belgrade.tvcams.data.weather.OpenMeteoWeatherClient
import rs.belgrade.tvcams.domain.airquality.AirQualityBand
import rs.belgrade.tvcams.domain.catalog.RemoteCatalogResult
import rs.belgrade.tvcams.domain.favorites.AutoRotateInterval
import rs.belgrade.tvcams.domain.favorites.InfoPanelPosition
import rs.belgrade.tvcams.domain.favorites.PerformanceMode
import rs.belgrade.tvcams.domain.favorites.QualityFilter
import rs.belgrade.tvcams.domain.favorites.TransportFilter
import rs.belgrade.tvcams.domain.favorites.ZapScope
import rs.belgrade.tvcams.domain.model.Camera
import rs.belgrade.tvcams.domain.model.CameraCategory
import rs.belgrade.tvcams.domain.model.CameraCity
import rs.belgrade.tvcams.domain.model.CameraCountry
import rs.belgrade.tvcams.domain.model.CatalogJsonCodec
import rs.belgrade.tvcams.domain.model.CatalogManifest
import rs.belgrade.tvcams.domain.model.GeoCoordinates
import rs.belgrade.tvcams.domain.model.StreamDescriptor
import rs.belgrade.tvcams.domain.model.StreamTransportType
import rs.belgrade.tvcams.domain.model.VideoQuality
import rs.belgrade.tvcams.domain.stream.StreamPayloadParsers
import rs.belgrade.tvcams.domain.weather.WeatherCondition
import java.io.BufferedInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.InputStream
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.net.SocketException
import java.net.URI
import java.nio.file.Files
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class HttpClientsAndResolverTest {

    private lateinit var server: LoopbackHttpServer
    private lateinit var tempDir: File
    private lateinit var httpClient: OkHttpClient
    private lateinit var mediaHttpClient: OkHttpClient
    private var baseUrl: String = ""

    @Before
    fun setUp() {
        tempDir = Files.createTempDirectory("tvcams-http-test").toFile()
        server = LoopbackHttpServer().apply { start() }
        baseUrl = "http://127.0.0.1:${server.port}"

        httpClient = OkHttpClient.Builder()
            .followRedirects(true)
            .build()
        mediaHttpClient = httpClient.newBuilder()
            .protocols(listOf(Protocol.HTTP_1_1))
            .build()
    }

    @After
    fun tearDown() {
        server.close()
        tempDir.deleteRecursively()
    }

    private fun jsonResponse(
        status: Int,
        body: String,
        headers: Map<String, String> = emptyMap(),
    ): LoopbackResponse = LoopbackResponse(
        status = status,
        contentType = "application/json; charset=utf-8",
        headers = headers,
        body = body.toByteArray(Charsets.UTF_8),
    )

    @Test
    fun okHttpStreamUrlResolverResolvesYouTubeInnertubeChannelFallbackRutubeAndIpCamLiveWithTtlCache() = runTest {
        val ytPostCount = AtomicInteger(0)
        var currentMs = 1_000_000L

        server.route("/youtubei/v1/player") { req ->
            ytPostCount.incrementAndGet()
            assertEquals("POST", req.method)
            assertEquals("3", req.header("X-YouTube-Client-Name"))
            assertEquals(
                StreamPayloadParsers.YOUTUBE_ANDROID_APP_UA,
                req.header("User-Agent"),
            )
            if (req.body.contains("Y4G5H53X7Tw") || req.body.contains("Jw9tsexipts")) {
                jsonResponse(
                    200,
                    """
                    {
                      "playabilityStatus": {"status": "OK"},
                      "videoDetails": {"videoId": "Y4G5H53X7Tw", "isLive": true},
                      "streamingData": {
                        "expiresInSeconds": "120",
                        "hlsManifestUrl": "https://manifest.googlevideo.com/api/manifest/hls_variant/id/Y4G5H53X7Tw.1/file/index.m3u8"
                      }
                    }
                    """.trimIndent(),
                )
            } else {
                jsonResponse(200, """{"playabilityStatus":{"status":"LIVE_STREAM_OFFLINE"}}""")
            }
        }

        server.route("/channel/UC3DrGPmCHsrionkmduqX0qg/live") {
            val html = """<link rel="canonical" href="https://www.youtube.com/watch?v=Jw9tsexipts"><script>var a={"isLive":true};</script>"""
            LoopbackResponse(
                status = 200,
                contentType = "text/html; charset=utf-8",
                body = html.toByteArray(Charsets.UTF_8),
            )
        }

        server.route("/rutube/eba7bc75dd7994f0d7e22e1ad9e76a87/") {
            jsonResponse(
                200,
                """{"live_streams":{"hls":[{"url":"https://bl.rutube.ru/livestream/eba7bc75dd7994f0d7e22e1ad9e76a87/index.m3u8?s=ok"}]}}""",
            )
        }

        server.route("/ipcamlive") { req ->
            if (req.query?.contains("kameratrg") == true) {
                jsonResponse(
                    200,
                    """{"details":{"streamavailable":"1","streamid":"abc123","address":"http://s110.ipcamlive.com/"}}""",
                )
            } else {
                jsonResponse(500, "{}")
            }
        }

        val resolver = OkHttpStreamUrlResolver(
            generalHttpClient = httpClient,
            mediaHttpClient = mediaHttpClient,
            clockMillis = { currentMs },
            youtubeInnertubeUrl = "$baseUrl/youtubei/v1/player",
            rutubeOptionsBaseUrl = "$baseUrl/rutube",
            ipCamLiveStateUrl = "$baseUrl/ipcamlive",
        )

        // Direct HLS and SNAPSHOT_POLL fast paths
        assertNull(
            resolver.resolveToHls(StreamDescriptor(StreamTransportType.SNAPSHOT_POLL, "https://example.com/a.jpg")),
        )
        val directHls = resolver.resolveToHls(
            StreamDescriptor(StreamTransportType.HLS, "https://example.com/live.m3u8", headers = mapOf("Referer" to "https://x")),
        )
        assertNotNull(directHls)
        assertEquals("https://example.com/live.m3u8", directHls.hlsUrl)

        // YouTube Innertube POST + cache hit within expiresInSeconds (120s - 60s = 60s TTL)
        val ytStream = StreamDescriptor(StreamTransportType.YOUTUBE_LIVE, "https://www.youtube.com/watch?v=Y4G5H53X7Tw")
        val resolvedYt1 = resolver.resolveToHls(ytStream)
        assertNotNull(resolvedYt1)
        assertEquals(
            "https://manifest.googlevideo.com/api/manifest/hls_variant/id/Y4G5H53X7Tw.1/file/index.m3u8",
            resolvedYt1.hlsUrl,
        )
        assertEquals(StreamPayloadParsers.YOUTUBE_ANDROID_APP_UA, resolvedYt1.headers["User-Agent"])
        assertEquals(1, ytPostCount.get())

        // Within 60s clamped TTL -> served from cache without network call
        currentMs += 30_000L
        assertNotNull(resolver.resolveToHls(ytStream))
        assertEquals(1, ytPostCount.get())

        // Past 60s clamped TTL -> re-resolves via network
        currentMs += 35_000L
        assertNotNull(resolver.resolveToHls(ytStream))
        assertEquals(2, ytPostCount.get())

        // Explicit invalidate forces fresh network call
        resolver.invalidate(ytStream.url)
        assertNotNull(resolver.resolveToHls(ytStream))
        assertEquals(3, ytPostCount.get())

        // Single-camera /channel/<id>/live canonical resolution -> Innertube POST
        val channelStream = StreamDescriptor(
            StreamTransportType.YOUTUBE_LIVE,
            "$baseUrl/channel/UC3DrGPmCHsrionkmduqX0qg/live",
        )
        val resolvedFromChannel = resolver.resolveToHls(channelStream)
        assertNotNull(resolvedFromChannel)
        assertEquals(4, ytPostCount.get())

        // Offline YouTube stream returns null
        assertNull(
            resolver.resolveToHls(StreamDescriptor(StreamTransportType.YOUTUBE_LIVE, "dQw4w9WgXcQ")),
        )

        // Rutube & IPCamLive resolution
        val resolvedRutube = resolver.resolveToHls(
            StreamDescriptor(StreamTransportType.RUTUBE_LIVE, "https://rutube.ru/video/eba7bc75dd7994f0d7e22e1ad9e76a87/"),
        )
        assertNotNull(resolvedRutube)
        assertTrue(resolvedRutube.hlsUrl.contains("eba7bc75dd7994f0d7e22e1ad9e76a87"))

        val resolvedIpCam = resolver.resolveToHls(
            StreamDescriptor(StreamTransportType.IPCAMLIVE, "https://ipcamlive.com/kameratrg"),
        )
        assertNotNull(resolvedIpCam)
        assertEquals("https://s110.ipcamlive.com/streams/abc123/stream.m3u8", resolvedIpCam.hlsUrl)
        assertEquals("https://ipcamlive.com/", resolvedIpCam.headers["Referer"])
    }

    @Test
    fun catalogCacheRemoteClientWeatherAndAirQualityClientsHandleHappyAndFailurePaths() = runTest {
        val sampleCam = Camera(
            id = "bg-test",
            name = "Slavija",
            country = CameraCountry.SERBIA,
            city = CameraCity.BELGRADE,
            quality = VideoQuality.FHD_1080P,
            timezone = "Europe/Belgrade",
            streets = listOf("Trg Slavija"),
            municipality = "Vračar",
            category = CameraCategory.SQUARES,
            direction = "North",
            posterUrl = "https://example.com/p.jpg",
            coordinates = GeoCoordinates(44.80, 20.46),
            streams = listOf(StreamDescriptor(StreamTransportType.HLS, "https://example.com/s.m3u8")),
        )
        val manifest = CatalogManifest(
            schemaVersion = 5,
            revision = 2026111918L,
            updatedAt = "2026-11-19T18:00:00Z",
            cameras = listOf(sampleCam),
        )

        // 1. FileCatalogCacheStore round-trip
        val cacheDir = File(tempDir, "catalog_cache")
        val cacheStore = FileCatalogCacheStore(cacheDir)
        assertNull(cacheStore.readCached())
        assertNull(cacheStore.readETag())
        cacheStore.writeCached(manifest, "\"etag-v5\"")
        assertEquals(manifest, cacheStore.readCached())
        assertEquals("\"etag-v5\"", cacheStore.readETag())

        // 2. OkHttpRemoteCatalogClient ETag 304, 200, 503, and corrupt JSON
        var catalogMode = "ok"
        server.route("/catalog.v5.json") { req ->
            val inm = req.header("If-None-Match")
            when {
                inm == "\"etag-v5\"" -> jsonResponse(304, "")
                catalogMode == "corrupt" -> jsonResponse(200, "{not-valid-json")
                catalogMode == "503" -> jsonResponse(503, "")
                else -> jsonResponse(
                    200,
                    CatalogJsonCodec.encode(manifest),
                    headers = mapOf("ETag" to "\"etag-v5\""),
                )
            }
        }
        val remoteClient = OkHttpRemoteCatalogClient(httpClient, "$baseUrl/catalog.v5.json")
        val fetched = remoteClient.fetchCatalog(ifNoneMatch = null)
        assertTrue(fetched is RemoteCatalogResult.Updated)
        assertEquals(manifest, fetched.manifest)
        assertEquals("\"etag-v5\"", fetched.etag)

        assertEquals(RemoteCatalogResult.NotModified, remoteClient.fetchCatalog(ifNoneMatch = "\"etag-v5\""))

        catalogMode = "corrupt"
        assertTrue(remoteClient.fetchCatalog(null) is RemoteCatalogResult.Unreachable)

        catalogMode = "503"
        assertTrue(remoteClient.fetchCatalog(null) is RemoteCatalogResult.Unreachable)

        // 3. OpenMeteoWeatherClient & OpenMeteoAirQualityClient
        var weatherStatus = 200
        server.route("/v1/forecast") {
            if (weatherStatus != 200) {
                jsonResponse(weatherStatus, "")
            } else {
                jsonResponse(
                    200,
                    """
                    {
                      "timezone": "Asia/Tokyo",
                      "current": {
                        "temperature_2m": 16.4,
                        "apparent_temperature": 15.2,
                        "weather_code": 61,
                        "wind_speed_10m": 12.8,
                        "is_day": 0
                      },
                      "daily": {
                        "sunrise": ["2026-11-19T06:21", "2026-11-20T06:22"],
                        "sunset": ["2026-11-19T16:31", "2026-11-20T16:30"]
                      }
                    }
                    """.trimIndent(),
                )
            }
        }

        var aqiStatus = 200
        server.route("/v1/air-quality") {
            if (aqiStatus != 200) {
                jsonResponse(aqiStatus, "")
            } else {
                jsonResponse(
                    200,
                    """
                    {
                      "current": {
                        "european_aqi": 18,
                        "pm2_5": 6.2,
                        "pm10": 11.4
                      }
                    }
                    """.trimIndent(),
                )
            }
        }

        val weatherClient = OpenMeteoWeatherClient(httpClient, "$baseUrl/v1/forecast")
        val weather = weatherClient.getCurrentWeather(35.6895, 139.6917)
        assertNotNull(weather)
        assertEquals(16.4, weather.temperatureCelsius)
        assertEquals(WeatherCondition.RAIN, weather.condition)
        assertFalse(weather.isDay)
        assertEquals(2, weather.solarWindows.size)

        weatherStatus = 500
        assertNull(weatherClient.getCurrentWeather(35.6895, 139.6917))

        val aqiClient = OpenMeteoAirQualityClient(httpClient, "$baseUrl/v1/air-quality")
        val aqi = aqiClient.getCurrentAirQuality(35.6895, 139.6917)
        assertNotNull(aqi)
        assertEquals(18, aqi.europeanAqi)
        assertEquals(AirQualityBand.GOOD, aqi.band)

        aqiStatus = 502
        assertNull(aqiClient.getCurrentAirQuality(35.6895, 139.6917))
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun dataStoreFavoritesRepositoryPersistsAllViewerPreferencesCleanly() = runTest {
        val storeFile = File(tempDir, "viewer_prefs.preferences_pb")
        val dataStore = PreferenceDataStoreFactory.create(
            scope = backgroundScope,
            produceFile = { storeFile },
        )
        val repo = DataStoreFavoritesRepository(dataStore)

        val defaults = repo.preferences.first()
        assertTrue(defaults.favoriteIds.isEmpty())
        assertEquals(ZapScope.ALL, defaults.zapScope)
        assertEquals(InfoPanelPosition.BOTTOM_CENTER, defaults.infoPanelPosition)

        repo.setFavorites(setOf("us-nyc-times-square-4k", "jp-tyo-shibuya-scramble"))
        repo.setLastCamera("jp-tyo-shibuya-scramble")
        repo.setZapScope(ZapScope.COUNTRY)
        repo.setQualityFilter(QualityFilter.HD_ONLY)
        repo.setTransportFilter(TransportFilter.LIVE_VIDEO_ONLY)
        repo.setPerformanceMode(PerformanceMode.LOW_SPEC)
        repo.setAutoRotateInterval(AutoRotateInterval.SEC_30)
        repo.setInfoPanelPosition(InfoPanelPosition.TOP_LEFT)
        repo.setShowClockOverlay(false)
        repo.setShowWeatherOverlay(false)
        repo.setShowAirQualityOverlay(false)
        repo.setShowSolarOverlay(false)
        repo.setOledProtectionEnabled(false)

        val updated = repo.preferences.first()
        assertEquals(setOf("us-nyc-times-square-4k", "jp-tyo-shibuya-scramble"), updated.favoriteIds)
        assertEquals("jp-tyo-shibuya-scramble", updated.lastCameraId)
        assertEquals(ZapScope.COUNTRY, updated.zapScope)
        assertEquals(QualityFilter.HD_ONLY, updated.qualityFilter)
        assertEquals(TransportFilter.LIVE_VIDEO_ONLY, updated.transportFilter)
        assertEquals(PerformanceMode.LOW_SPEC, updated.performanceMode)
        assertEquals(AutoRotateInterval.SEC_30, updated.autoRotateInterval)
        assertEquals(InfoPanelPosition.TOP_LEFT, updated.infoPanelPosition)
        assertFalse(updated.showClockOverlay)
        assertFalse(updated.showWeatherOverlay)
        assertFalse(updated.showAirQualityOverlay)
        assertFalse(updated.showSolarOverlay)
        assertFalse(updated.oledProtectionEnabled)
    }
}

private data class LoopbackRequest(
    val method: String,
    val path: String,
    val query: String?,
    private val headersLower: Map<String, String>,
    val body: String,
) {
    fun header(name: String): String? = headersLower[name.lowercase()]
}

private data class LoopbackResponse(
    val status: Int,
    val contentType: String = "application/json; charset=utf-8",
    val headers: Map<String, String> = emptyMap(),
    val body: ByteArray = ByteArray(0),
)

private class LoopbackHttpServer : AutoCloseable {
    private val socket = ServerSocket(0, 50, InetAddress.getByName("127.0.0.1"))
    private val workers: ExecutorService = Executors.newCachedThreadPool()
    private val routes = ConcurrentHashMap<String, (LoopbackRequest) -> LoopbackResponse>()

    @Volatile
    private var running = false
    private var acceptThread: Thread? = null

    val port: Int
        get() = socket.localPort

    fun route(prefix: String, handler: (LoopbackRequest) -> LoopbackResponse) {
        routes[prefix] = handler
    }

    fun start() {
        running = true
        acceptThread = Thread(
            {
                while (running && !socket.isClosed) {
                    try {
                        val client = socket.accept()
                        workers.execute { handleClient(client) }
                    } catch (_: SocketException) {
                        break
                    }
                }
            },
            "loopback-http-accept",
        ).apply {
            isDaemon = true
            start()
        }
    }

    private fun handleClient(client: Socket) {
        client.use { sock ->
            sock.soTimeout = 5_000
            val input = BufferedInputStream(sock.getInputStream())
            val requestLine = readAsciiLine(input) ?: return
            val parts = requestLine.split(' ')
            if (parts.size < 2) return
            val method = parts[0]
            val targetUri = runCatching { URI(parts[1]) }.getOrNull()
            val path = targetUri?.path ?: parts[1].substringBefore('?')
            val query = targetUri?.rawQuery ?: parts[1].substringAfter('?', "").ifEmpty { null }

            val headers = LinkedHashMap<String, String>()
            while (true) {
                val line = readAsciiLine(input) ?: break
                if (line.isEmpty()) break
                val colonIdx = line.indexOf(':')
                if (colonIdx > 0) {
                    val key = line.substring(0, colonIdx).trim().lowercase()
                    val value = line.substring(colonIdx + 1).trim()
                    headers[key] = value
                }
            }

            val contentLength = headers["content-length"]?.toIntOrNull()?.coerceAtLeast(0) ?: 0
            val bodyBytes = readExactBytes(input, contentLength)
            val req = LoopbackRequest(
                method = method,
                path = path,
                query = query,
                headersLower = headers,
                body = String(bodyBytes, Charsets.UTF_8),
            )

            val matchedHandler = routes.entries
                .filter { (prefix, _) -> path.startsWith(prefix) }
                .maxByOrNull { it.key.length }
                ?.value

            val resp = try {
                matchedHandler?.invoke(req) ?: LoopbackResponse(status = 404)
            } catch (err: AssertionError) {
                LoopbackResponse(
                    status = 500,
                    contentType = "text/plain; charset=utf-8",
                    body = (err.message ?: "AssertionError").toByteArray(Charsets.UTF_8),
                )
            }

            val reason = when (resp.status) {
                200 -> "OK"
                304 -> "Not Modified"
                404 -> "Not Found"
                502 -> "Bad Gateway"
                503 -> "Service Unavailable"
                else -> "Internal Server Error"
            }

            val out = sock.getOutputStream()
            val headerBuilder = StringBuilder()
            headerBuilder.append("HTTP/1.1 ").append(resp.status).append(' ').append(reason).append("\r\n")
            headerBuilder.append("Connection: close\r\n")
            if (resp.status != 304) {
                headerBuilder.append("Content-Type: ").append(resp.contentType).append("\r\n")
                headerBuilder.append("Content-Length: ").append(resp.body.size).append("\r\n")
            }
            resp.headers.forEach { (k, v) ->
                headerBuilder.append(k).append(": ").append(v).append("\r\n")
            }
            headerBuilder.append("\r\n")

            out.write(headerBuilder.toString().toByteArray(Charsets.ISO_8859_1))
            if (resp.status != 304 && resp.body.isNotEmpty()) {
                out.write(resp.body)
            }
            out.flush()
        }
    }

    private fun readAsciiLine(input: InputStream): String? {
        val buf = ByteArrayOutputStream(128)
        var readAny = false
        while (true) {
            val b = input.read()
            if (b == -1) {
                return if (readAny) buf.toString(Charsets.ISO_8859_1.name()).trimEnd('\r') else null
            }
            readAny = true
            if (b == '\n'.code) {
                return buf.toString(Charsets.ISO_8859_1.name()).trimEnd('\r')
            }
            buf.write(b)
        }
    }

    private fun readExactBytes(input: InputStream, count: Int): ByteArray {
        if (count <= 0) return ByteArray(0)
        val out = ByteArray(count)
        var offset = 0
        while (offset < count) {
            val n = input.read(out, offset, count - offset)
            if (n <= 0) break
            offset += n
        }
        return if (offset == count) out else out.copyOf(offset)
    }

    override fun close() {
        running = false
        runCatching { socket.close() }
        workers.shutdownNow()
        acceptThread?.join(1_000)
    }
}
