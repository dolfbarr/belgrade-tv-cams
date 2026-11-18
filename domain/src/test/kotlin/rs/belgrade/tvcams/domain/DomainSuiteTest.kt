package rs.belgrade.tvcams.domain

import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import rs.belgrade.tvcams.domain.catalog.CatalogBootstrapCoordinator
import rs.belgrade.tvcams.domain.catalog.CatalogCacheStore
import rs.belgrade.tvcams.domain.catalog.CatalogSeedSource
import rs.belgrade.tvcams.domain.catalog.CatalogSyncOutcome
import rs.belgrade.tvcams.domain.catalog.RemoteCatalogClient
import rs.belgrade.tvcams.domain.catalog.RemoteCatalogResult
import rs.belgrade.tvcams.domain.favorites.ZapScope
import rs.belgrade.tvcams.domain.model.Camera
import rs.belgrade.tvcams.domain.model.CameraCategory
import rs.belgrade.tvcams.domain.model.CatalogJsonCodec
import rs.belgrade.tvcams.domain.model.CatalogManifest
import rs.belgrade.tvcams.domain.model.GeoCoordinates
import rs.belgrade.tvcams.domain.model.SerbiaCity
import rs.belgrade.tvcams.domain.model.StreamDescriptor
import rs.belgrade.tvcams.domain.model.StreamTransportType
import rs.belgrade.tvcams.domain.stream.FailoverDecision
import rs.belgrade.tvcams.domain.stream.SnapshotUrlBuilder
import rs.belgrade.tvcams.domain.stream.StreamFailoverTracker
import rs.belgrade.tvcams.domain.time.CameraClockFormatter
import rs.belgrade.tvcams.domain.tv.OverlayMode
import rs.belgrade.tvcams.domain.tv.StripFilter
import rs.belgrade.tvcams.domain.tv.TvKeyCode
import rs.belgrade.tvcams.domain.tv.TvKeyPhase
import rs.belgrade.tvcams.domain.tv.TvKeyRouter
import rs.belgrade.tvcams.domain.tv.TvRemoteAction
import rs.belgrade.tvcams.domain.tv.TvStageReducer
import rs.belgrade.tvcams.domain.tv.TvStageState
import rs.belgrade.tvcams.domain.weather.CurrentWeather
import rs.belgrade.tvcams.domain.weather.GridCachedWeatherRepository
import rs.belgrade.tvcams.domain.weather.WeatherCondition
import rs.belgrade.tvcams.domain.weather.WeatherRepository
import rs.belgrade.tvcams.domain.weather.WmoWeatherMapper
import java.io.File
import java.time.Instant
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DomainSuiteTest {

    private fun sampleCamera(
        id: String,
        name: String,
        city: SerbiaCity = SerbiaCity.BELGRADE,
        category: CameraCategory = CameraCategory.SQUARES,
    ): Camera = Camera(
        id = id,
        name = name,
        city = city,
        timezone = "Europe/Belgrade",
        streets = listOf("Kralja Milana", "Nemanjina"),
        municipality = "Vračar",
        category = category,
        direction = "Pogled ka Slaviji",
        posterUrl = "https://stream.uzivobeograd.rs/live/cam_8.jpg",
        coordinates = GeoCoordinates(lat = 44.8025, lon = 20.4664),
        streams = listOf(
            StreamDescriptor(
                type = StreamTransportType.HLS,
                url = "https://streamer.devnet.rs/hls/slavija.m3u8",
                headers = mapOf("Referer" to "https://www.naxi.rs/kamere"),
            ),
            StreamDescriptor(
                type = StreamTransportType.SNAPSHOT_POLL,
                url = "https://stream.uzivobeograd.rs/live/cam_8.jpg",
                intervalMs = 1500L,
                cacheBusterParam = "_",
                headers = mapOf("Referer" to "https://www.naxi.rs/kamere"),
            ),
        ),
    )

    @Test
    fun `v1 catalog remains backward-compatible with default Belgrade city and timezone`() {
        val catalogPath = System.getProperty("belgrade.catalog.v1.path")
        assertNotNull(catalogPath, "System property belgrade.catalog.v1.path must be set")
        val rawDiskJson = File(catalogPath).readText(Charsets.UTF_8)

        val decoded = CatalogJsonCodec.decode(rawDiskJson)
        assertEquals(1, decoded.schemaVersion)
        assertEquals(27, decoded.cameras.size)
        assertTrue(decoded.cameras.all { it.city == SerbiaCity.BELGRADE })
        assertTrue(decoded.cameras.all { it.timezone == "Europe/Belgrade" })
    }

    @Test
    fun `whole-graph round trip of v2 Serbia catalog is fixed-point identical`() {
        val catalogV2Path = System.getProperty("belgrade.catalog.v2.path")
        assertNotNull(catalogV2Path, "System property belgrade.catalog.v2.path must be set")
        val v2File = File(catalogV2Path)
        if (!v2File.exists()) {
            // Prior to Phase 3 generation, verify synthetic v2 manifest fixed-point serialization
            val synthetic = CatalogManifest(
                schemaVersion = 2,
                revision = 2026111801L,
                updatedAt = "2026-11-18T01:00:00Z",
                cameras = listOf(
                    sampleCamera("bg-1", "Slavija", SerbiaCity.BELGRADE),
                    sampleCamera("ns-1", "Pupinova palata", SerbiaCity.NOVI_SAD),
                    sampleCamera("su-1", "MZ Zorka", SerbiaCity.SUBOTICA),
                    sampleCamera("ni-1", "Niš Sever", SerbiaCity.NIS),
                ),
            )
            val firstEncode = CatalogJsonCodec.encode(synthetic)
            val reDecoded = CatalogJsonCodec.decode(firstEncode)
            val secondEncode = CatalogJsonCodec.encode(reDecoded)
            assertEquals(synthetic, reDecoded)
            assertEquals(firstEncode, secondEncode)
            return
        }

        val rawDiskJson = v2File.readText(Charsets.UTF_8)
        val decoded = CatalogJsonCodec.decode(rawDiskJson)
        assertEquals(2, decoded.schemaVersion)
        assertTrue(decoded.cameras.size >= 50)
        assertEquals(
            setOf(SerbiaCity.BELGRADE, SerbiaCity.NOVI_SAD, SerbiaCity.SUBOTICA, SerbiaCity.NIS),
            decoded.cameras.map { it.city }.toSet(),
        )

        val firstEncode = CatalogJsonCodec.encode(decoded)
        val reDecoded = CatalogJsonCodec.decode(firstEncode)
        val secondEncode = CatalogJsonCodec.encode(reDecoded)

        assertEquals(decoded, reDecoded)
        assertEquals(firstEncode, secondEncode)
        assertEquals(rawDiskJson, firstEncode)
    }

    @Test
    fun `snapshot url builder preserves existing query parameters and replaces stale token`() {
        assertEquals(
            "https://stream.uzivobeograd.rs/live/cam_8.jpg?_=1700000001",
            SnapshotUrlBuilder.buildPolledUrl(
                baseUrl = "https://stream.uzivobeograd.rs/live/cam_8.jpg",
                paramName = "_",
                epochMillis = 1700000001L,
            ),
        )

        assertEquals(
            "https://stream.uzivobeograd.rs/live/cam_14.jpg?quality=lq&rand=1700000002",
            SnapshotUrlBuilder.buildPolledUrl(
                baseUrl = "https://stream.uzivobeograd.rs/live/cam_14.jpg?quality=lq&rand=0.2772",
                paramName = "rand",
                epochMillis = 1700000002L,
            ),
        )
    }

    @Test
    fun `3-tier catalog bootstrap prefers newer valid v2 cache and guards against corrupt remote`() = runTest {
        val seedManifest = CatalogManifest(
            schemaVersion = 2,
            revision = 100L,
            updatedAt = "2026-11-18T00:00:00Z",
            cameras = listOf(sampleCamera("bg-1", "Slavija")),
        )
        var storedManifest: CatalogManifest? = null
        var storedEtag: String? = null

        val seedSource = object : CatalogSeedSource {
            override suspend fun readSeed(): CatalogManifest = seedManifest
        }
        val cacheStore = object : CatalogCacheStore {
            override suspend fun readCached(): CatalogManifest? = storedManifest
            override suspend fun readETag(): String? = storedEtag
            override suspend fun writeCached(manifest: CatalogManifest, etag: String?) {
                storedManifest = manifest
                storedEtag = etag
            }
        }

        val remoteManifest = seedManifest.copy(
            revision = 200L,
            cameras = listOf(
                sampleCamera("bg-1", "Slavija", SerbiaCity.BELGRADE),
                sampleCamera("ns-1", "Varadinski most", SerbiaCity.NOVI_SAD),
            ),
        )
        var nextRemoteResult: RemoteCatalogResult = RemoteCatalogResult.Updated(remoteManifest, "\"etag-200\"")
        val remoteClient = object : RemoteCatalogClient {
            override suspend fun fetchCatalog(ifNoneMatch: String?): RemoteCatalogResult = nextRemoteResult
        }

        val coordinator = CatalogBootstrapCoordinator(seedSource, cacheStore, remoteClient)

        val initial = coordinator.loadInitial()
        assertEquals(100L, initial.revision)

        val syncApplied = coordinator.syncRemote(currentRevision = initial.revision)
        assertTrue(syncApplied is CatalogSyncOutcome.Applied)
        assertEquals(200L, syncApplied.manifest.revision)
        assertEquals("\"etag-200\"", storedEtag)

        assertEquals(200L, coordinator.loadInitial().revision)

        nextRemoteResult = RemoteCatalogResult.Updated(
            remoteManifest.copy(revision = 300L, cameras = emptyList()),
            "\"etag-bad\"",
        )
        val syncRejected = coordinator.syncRemote(currentRevision = 200L)
        assertTrue(syncRejected is CatalogSyncOutcome.Rejected)
        assertEquals(200L, storedManifest?.revision)
    }

    @Test
    fun `stream failover advances immediately to snapshot fallback then backs off exponentially`() {
        val cam = sampleCamera("bg-slavija", "Trg Slavija")
        val tracker = StreamFailoverTracker(cam.streams)

        assertEquals(StreamTransportType.HLS, tracker.currentStream().type)

        val firstFailure = tracker.onTransportFailure()
        assertTrue(firstFailure is FailoverDecision.SwitchStreamImmediately)
        assertEquals(StreamTransportType.SNAPSHOT_POLL, firstFailure.nextStream.type)

        val secondFailure = tracker.onTransportFailure()
        assertTrue(secondFailure is FailoverDecision.RetryAfterBackoff)
        assertEquals(3_000L, secondFailure.delayMillis)
        assertEquals(StreamTransportType.HLS, secondFailure.nextStream.type)

        tracker.onTransportFailure()
        val secondCycle = tracker.onTransportFailure() as FailoverDecision.RetryAfterBackoff
        assertEquals(6_000L, secondCycle.delayMillis)

        tracker.onTransportFailure()
        val thirdCycle = tracker.onTransportFailure() as FailoverDecision.RetryAfterBackoff
        assertEquals(15_000L, thirdCycle.delayMillis)

        tracker.onTransportFailure()
        val fourthCycle = tracker.onTransportFailure() as FailoverDecision.RetryAfterBackoff
        assertEquals(30_000L, fourthCycle.delayMillis)
    }

    @Test
    fun `tv stage reducer handles pure camera toggle, city and favorites zapping, and clock weather toggles`() {
        val cameras = listOf(
            sampleCamera("bg-1", "Brankov most", SerbiaCity.BELGRADE, CameraCategory.BRIDGES),
            sampleCamera("ns-1", "Pupinova palata", SerbiaCity.NOVI_SAD, CameraCategory.BOULEVARDS),
            sampleCamera("ns-2", "Varadinski most", SerbiaCity.NOVI_SAD, CameraCategory.BRIDGES),
            sampleCamera("ni-1", "Niš Sever", SerbiaCity.NIS, CameraCategory.HIGHWAYS),
        )
        var state = TvStageState.initial(
            cameras = cameras,
            favoriteIds = setOf("bg-1", "ni-1"),
            initialCameraId = "ns-1",
            showClockOverlay = true,
            showWeatherOverlay = true,
        )
        assertEquals(OverlayMode.HUD_VISIBLE, state.overlayMode)
        assertEquals("ns-1", state.activeCamera?.id)
        assertTrue(state.showClockOverlay)
        assertTrue(state.showWeatherOverlay)

        // Toggle Clock off & Weather off independently
        state = TvStageReducer.reduce(state, TvRemoteAction.ToggleClockOverlay).state
        assertFalse(state.showClockOverlay)
        assertTrue(state.showWeatherOverlay)
        assertEquals("Clock overlay: Off", state.noticeMessage)

        state = TvStageReducer.reduce(state, TvRemoteAction.ToggleWeatherOverlay).state
        assertFalse(state.showWeatherOverlay)
        assertFalse(state.hasAnyAmbientOverlay)
        assertEquals("Weather overlay: Off", state.noticeMessage)

        // Toggle Clock back on
        state = TvStageReducer.reduce(state, TvRemoteAction.ToggleClockOverlay).state
        assertTrue(state.showClockOverlay)
        assertTrue(state.hasAnyAmbientOverlay)

        // Cycle ZapScope: ALL -> CITY (Novi Sad: ns-1, ns-2)
        state = TvStageReducer.reduce(state, TvRemoteAction.ToggleZapScope).state
        assertEquals(ZapScope.CITY, state.zapScope)
        assertEquals(listOf("ns-1", "ns-2"), state.zapPlaylist.map { it.id })

        state = TvStageReducer.reduce(state, TvRemoteAction.ZapNext).state
        assertEquals("ns-2", state.activeCamera?.id)
        state = TvStageReducer.reduce(state, TvRemoteAction.ZapNext).state
        assertEquals("ns-1", state.activeCamera?.id)

        // Cycle ZapScope: CITY -> FAVORITES (bg-1, ni-1)
        state = TvStageReducer.reduce(state, TvRemoteAction.ToggleZapScope).state
        assertEquals(ZapScope.FAVORITES, state.zapScope)
        assertEquals("bg-1", state.activeCamera?.id)

        // City filter in Quick Strip isolates Subotica / Novi Sad / Niš
        state = TvStageReducer.reduce(state, TvRemoteAction.SelectStripFilter(StripFilter.City(SerbiaCity.NOVI_SAD))).state
        assertEquals(OverlayMode.QUICK_STRIP, state.overlayMode)
        assertEquals(listOf("ns-1", "ns-2"), state.stripCameras.map { it.id })

        // Toggling Clock while inside QUICK_STRIP preserves QUICK_STRIP mode
        state = TvStageReducer.reduce(state, TvRemoteAction.ToggleClockOverlay).state
        assertEquals(OverlayMode.QUICK_STRIP, state.overlayMode)
        assertFalse(state.showClockOverlay)

        // BACK from QUICK_STRIP -> PURE_CAMERA, second BACK -> exit app
        val backFromStrip = TvStageReducer.reduce(state, TvRemoteAction.NavigateBack)
        assertFalse(backFromStrip.shouldExitApp)
        assertEquals(OverlayMode.PURE_CAMERA, backFromStrip.state.overlayMode)

        val backFromPure = TvStageReducer.reduce(backFromStrip.state, TvRemoteAction.NavigateBack)
        assertTrue(backFromPure.shouldExitApp)
    }

    @Test
    fun `wmo mapper, grid weather cache, and camera clock formatter satisfy contracts`() = runTest {
        assertEquals(WeatherCondition.CLEAR, WmoWeatherMapper.fromCode(0))
        assertEquals(WeatherCondition.PARTLY_CLOUDY, WmoWeatherMapper.fromCode(2))
        assertEquals(WeatherCondition.FOG, WmoWeatherMapper.fromCode(45))
        assertEquals(WeatherCondition.RAIN, WmoWeatherMapper.fromCode(61))
        assertEquals(WeatherCondition.SNOW, WmoWeatherMapper.fromCode(75))
        assertEquals(WeatherCondition.THUNDERSTORM, WmoWeatherMapper.fromCode(95))

        var upstreamCalls = 0
        var nowMs = 1_000_000L
        var simulatedWeather: CurrentWeather? = CurrentWeather(
            temperatureCelsius = 13.7,
            apparentTemperatureCelsius = 12.1,
            windSpeedKmh = 15.4,
            condition = WeatherCondition.RAIN,
            isDay = false,
            fetchedAtEpochMs = nowMs,
        )

        val upstream = object : WeatherRepository {
            override suspend fun getCurrentWeather(lat: Double, lon: Double): CurrentWeather? {
                upstreamCalls += 1
                return simulatedWeather
            }
        }
        val cachedRepo = GridCachedWeatherRepository(
            upstream = upstream,
            clockMillis = { nowMs },
            ttlMillis = 15 * 60 * 1000L,
        )

        // Two nearby Belgrade cameras in same 0.05 deg grid coalesce to 1 upstream call
        val slavijaWeather = cachedRepo.getCurrentWeather(44.8025, 20.4664)
        val republikeWeather = cachedRepo.getCurrentWeather(44.8162, 20.4603)
        assertNotNull(slavijaWeather)
        assertEquals(slavijaWeather, republikeWeather)
        assertEquals(1, upstreamCalls)
        assertEquals("13.7°C · Rain · 15 km/h", slavijaWeather.formattedSummary())

        // Novi Sad triggers a distinct grid bucket call
        cachedRepo.getCurrentWeather(45.2543, 19.8457)
        assertEquals(2, upstreamCalls)

        // After TTL expiry, offline upstream gracefully falls back to stale entry within grace window
        nowMs += 16 * 60 * 1000L
        simulatedWeather = null
        val fallbackWeather = cachedRepo.getCurrentWeather(44.8025, 20.4664)
        assertNotNull(fallbackWeather)
        assertEquals(13.7, fallbackWeather.temperatureCelsius)
        assertEquals(3, upstreamCalls)

        // Clock formatting in Europe/Belgrade (CET = UTC+1 in November)
        val instant = Instant.parse("2026-11-18T00:45:30Z")
        assertEquals("01:45", CameraClockFormatter.formatTime(instant, "Europe/Belgrade"))
        assertEquals("01:45", CameraClockFormatter.formatTime(instant, "Invalid/Zone"))
    }

    @Test
    fun `tv key router distinguishes short press OK from hold OK favorite and respects quick strip focus`() {
        val router = TvKeyRouter()

        val downShort = router.route(TvKeyCode.DPAD_CENTER, TvKeyPhase.DOWN, repeatCount = 0, OverlayMode.PURE_CAMERA)
        assertTrue(downShort.consumed)
        assertNull(downShort.action)

        val upShort = router.route(TvKeyCode.DPAD_CENTER, TvKeyPhase.UP, repeatCount = 0, OverlayMode.PURE_CAMERA)
        assertTrue(upShort.consumed)
        assertEquals(TvRemoteAction.ToggleInfoOrSelect, upShort.action)

        router.route(TvKeyCode.DPAD_CENTER, TvKeyPhase.DOWN, repeatCount = 0, OverlayMode.HUD_VISIBLE)
        val downHold = router.route(TvKeyCode.DPAD_CENTER, TvKeyPhase.DOWN, repeatCount = 1, OverlayMode.HUD_VISIBLE)
        assertTrue(downHold.consumed)
        assertEquals(TvRemoteAction.ToggleFavoriteCurrent, downHold.action)

        val upAfterHold = router.route(TvKeyCode.DPAD_CENTER, TvKeyPhase.UP, repeatCount = 0, OverlayMode.HUD_VISIBLE)
        assertTrue(upAfterHold.consumed)
        assertNull(upAfterHold.action)

        val stripRight = router.route(TvKeyCode.DPAD_RIGHT, TvKeyPhase.DOWN, repeatCount = 0, OverlayMode.QUICK_STRIP)
        assertFalse(stripRight.consumed)
        assertNull(stripRight.action)
    }
}
