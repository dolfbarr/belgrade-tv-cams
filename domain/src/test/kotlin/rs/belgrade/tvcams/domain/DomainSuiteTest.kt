package rs.belgrade.tvcams.domain

import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import rs.belgrade.tvcams.domain.airquality.AirQualityBand
import rs.belgrade.tvcams.domain.airquality.AirQualityRepository
import rs.belgrade.tvcams.domain.airquality.CurrentAirQuality
import rs.belgrade.tvcams.domain.airquality.EaqiMapper
import rs.belgrade.tvcams.domain.airquality.GridCachedAirQualityRepository
import rs.belgrade.tvcams.domain.catalog.CatalogBootstrapCoordinator
import rs.belgrade.tvcams.domain.catalog.CatalogCacheStore
import rs.belgrade.tvcams.domain.catalog.CatalogSeedSource
import rs.belgrade.tvcams.domain.catalog.CatalogSyncOutcome
import rs.belgrade.tvcams.domain.catalog.RemoteCatalogClient
import rs.belgrade.tvcams.domain.catalog.RemoteCatalogResult
import rs.belgrade.tvcams.domain.favorites.QualityFilter
import rs.belgrade.tvcams.domain.favorites.ZapScope
import rs.belgrade.tvcams.domain.model.Camera
import rs.belgrade.tvcams.domain.model.CameraCategory
import rs.belgrade.tvcams.domain.model.CatalogJsonCodec
import rs.belgrade.tvcams.domain.model.CatalogManifest
import rs.belgrade.tvcams.domain.model.GeoCoordinates
import rs.belgrade.tvcams.domain.model.SerbiaCity
import rs.belgrade.tvcams.domain.model.StreamDescriptor
import rs.belgrade.tvcams.domain.model.StreamTransportType
import rs.belgrade.tvcams.domain.model.VideoQuality
import rs.belgrade.tvcams.domain.oled.PixelOrbitCalculator
import rs.belgrade.tvcams.domain.solar.SolarPhase
import rs.belgrade.tvcams.domain.solar.SolarPhaseCalculator
import rs.belgrade.tvcams.domain.solar.SolarWindow
import rs.belgrade.tvcams.domain.stream.FailoverDecision
import rs.belgrade.tvcams.domain.stream.SnapshotUrlBuilder
import rs.belgrade.tvcams.domain.stream.StreamFailoverTracker
import rs.belgrade.tvcams.domain.time.CameraClockFormatter
import rs.belgrade.tvcams.domain.tv.HomeChannelSelector
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
import java.time.LocalDateTime
import java.time.ZoneId
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DomainSuiteTest {

    private fun sampleCamera(
        id: String,
        name: String,
        city: SerbiaCity = SerbiaCity.BELGRADE,
        category: CameraCategory = CameraCategory.SQUARES,
        quality: VideoQuality? = null,
    ): Camera = Camera(
        id = id,
        name = name,
        city = city,
        quality = quality,
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
        val rawDiskJson = File(catalogV2Path).readText(Charsets.UTF_8)
        val decoded = CatalogJsonCodec.decode(rawDiskJson)
        assertEquals(2, decoded.schemaVersion)
        assertEquals(56, decoded.cameras.size)
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
    fun `tv stage reducer handles pure camera toggle, city and favorites zapping, and ambient toggles`() {
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
            showAirQualityOverlay = true,
            showSolarOverlay = true,
            oledProtectionEnabled = true,
        )
        assertEquals(OverlayMode.HUD_VISIBLE, state.overlayMode)
        assertEquals("ns-1", state.activeCamera?.id)
        assertTrue(state.showClockOverlay)
        assertTrue(state.showWeatherOverlay)
        assertTrue(state.showAirQualityOverlay)
        assertTrue(state.showSolarOverlay)
        assertTrue(state.oledProtectionEnabled)

        // Toggle Clock, Weather, AQI, Solar, and OLED guard independently
        state = TvStageReducer.reduce(state, TvRemoteAction.ToggleClockOverlay).state
        assertFalse(state.showClockOverlay)

        state = TvStageReducer.reduce(state, TvRemoteAction.ToggleWeatherOverlay).state
        assertFalse(state.showWeatherOverlay)

        state = TvStageReducer.reduce(state, TvRemoteAction.ToggleAirQualityOverlay).state
        assertFalse(state.showAirQualityOverlay)
        assertEquals("Air quality overlay: Off", state.noticeMessage)

        state = TvStageReducer.reduce(state, TvRemoteAction.ToggleSolarOverlay).state
        assertFalse(state.showSolarOverlay)
        assertFalse(state.hasAnyAmbientOverlay)
        assertEquals("Sun & golden hour: Off", state.noticeMessage)

        state = TvStageReducer.reduce(state, TvRemoteAction.ToggleOledProtection).state
        assertFalse(state.oledProtectionEnabled)
        assertEquals("OLED burn-in guard: Off", state.noticeMessage)

        // Toggle AQI back on -> hasAnyAmbientOverlay becomes true
        state = TvStageReducer.reduce(state, TvRemoteAction.ToggleAirQualityOverlay).state
        assertTrue(state.showAirQualityOverlay)
        assertTrue(state.hasAnyAmbientOverlay)

        // Cycle ZapScope: ALL -> CITY (Novi Sad: ns-1, ns-2) -> FAVORITES (bg-1, ni-1)
        state = TvStageReducer.reduce(state, TvRemoteAction.ToggleZapScope).state
        assertEquals(ZapScope.CITY, state.zapScope)
        assertEquals(listOf("ns-1", "ns-2"), state.zapPlaylist.map { it.id })

        state = TvStageReducer.reduce(state, TvRemoteAction.ZapNext).state
        assertEquals("ns-2", state.activeCamera?.id)

        state = TvStageReducer.reduce(state, TvRemoteAction.ToggleZapScope).state
        assertEquals(ZapScope.FAVORITES, state.zapScope)
        assertEquals("bg-1", state.activeCamera?.id)

        // City filter in Quick Strip
        state = TvStageReducer.reduce(state, TvRemoteAction.SelectStripFilter(StripFilter.City(SerbiaCity.NOVI_SAD))).state
        assertEquals(OverlayMode.QUICK_STRIP, state.overlayMode)
        assertEquals(listOf("ns-1", "ns-2"), state.stripCameras.map { it.id })

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

        val slavijaWeather = cachedRepo.getCurrentWeather(44.8025, 20.4664)
        val republikeWeather = cachedRepo.getCurrentWeather(44.8162, 20.4603)
        assertNotNull(slavijaWeather)
        assertEquals(slavijaWeather, republikeWeather)
        assertEquals(1, upstreamCalls)
        assertEquals("13.7°C · Rain · 15 km/h", slavijaWeather.formattedSummary())

        cachedRepo.getCurrentWeather(45.2543, 19.8457)
        assertEquals(2, upstreamCalls)

        nowMs += 16 * 60 * 1000L
        simulatedWeather = null
        val fallbackWeather = cachedRepo.getCurrentWeather(44.8025, 20.4664)
        assertNotNull(fallbackWeather)
        assertEquals(13.7, fallbackWeather.temperatureCelsius)
        assertEquals(3, upstreamCalls)

        val instant = Instant.parse("2026-11-18T00:45:30Z")
        assertEquals("01:45", CameraClockFormatter.formatTime(instant, "Europe/Belgrade"))
        assertEquals("01:45", CameraClockFormatter.formatTime(instant, "Invalid/Zone"))
    }

    @Test
    fun `eaqi air quality mapper and grid air quality cache satisfy contracts`() = runTest {
        assertEquals(AirQualityBand.GOOD, EaqiMapper.fromIndex(15))
        assertEquals(AirQualityBand.FAIR, EaqiMapper.fromIndex(29))
        assertEquals(AirQualityBand.MODERATE, EaqiMapper.fromIndex(52))
        assertEquals(AirQualityBand.POOR, EaqiMapper.fromIndex(74))
        assertEquals(AirQualityBand.VERY_POOR, EaqiMapper.fromIndex(92))
        assertEquals(AirQualityBand.EXTREMELY_POOR, EaqiMapper.fromIndex(135))

        var aqiCalls = 0
        var nowMs = 5_000_000L
        val sampleAqi = CurrentAirQuality(
            europeanAqi = 29,
            pm25 = 9.5,
            pm10 = 12.0,
            band = EaqiMapper.fromIndex(29),
            fetchedAtEpochMs = nowMs,
        )
        val upstream = object : AirQualityRepository {
            override suspend fun getCurrentAirQuality(lat: Double, lon: Double): CurrentAirQuality? {
                aqiCalls += 1
                return sampleAqi
            }
        }
        val cached = GridCachedAirQualityRepository(
            upstream = upstream,
            clockMillis = { nowMs },
        )

        val a1 = cached.getCurrentAirQuality(44.8025, 20.4664)
        val a2 = cached.getCurrentAirQuality(44.8149, 20.4533)
        assertNotNull(a1)
        assertEquals(a1, a2)
        assertEquals(1, aqiCalls)
        assertEquals("AQI 29 Fair · PM2.5 10", a1.formattedSummary())
    }

    @Test
    fun `solar phase calculator identifies pre-dawn, golden hours, day, dusk, and tomorrow sunrise`() {
        val zone = ZoneId.of("Europe/Belgrade")
        fun epochAt(isoLocal: String): Long =
            LocalDateTime.parse(isoLocal).atZone(zone).toInstant().toEpochMilli()

        val windows = listOf(
            SolarWindow(
                sunriseEpochMs = epochAt("2026-11-18T06:39:00"),
                sunsetEpochMs = epochAt("2026-11-18T16:07:00"),
            ),
            SolarWindow(
                sunriseEpochMs = epochAt("2026-11-19T06:40:00"),
                sunsetEpochMs = epochAt("2026-11-19T16:06:00"),
            ),
        )

        // 03:00 -> Night before today's sunrise
        val nightEarly = SolarPhaseCalculator.evaluate(
            now = Instant.ofEpochMilli(epochAt("2026-11-18T03:00:00")),
            timezoneId = "Europe/Belgrade",
            windows = windows,
        )
        assertNotNull(nightEarly)
        assertEquals(SolarPhase.NIGHT, nightEarly.phase)
        assertFalse(nightEarly.isGoldenHour)
        assertEquals("Sunrise 06:39", nightEarly.summaryLabel)

        // 06:10 -> Dawn twilight (29m before sunrise)
        val dawn = SolarPhaseCalculator.evaluate(
            now = Instant.ofEpochMilli(epochAt("2026-11-18T06:10:00")),
            timezoneId = "Europe/Belgrade",
            windows = windows,
        )
        assertNotNull(dawn)
        assertEquals(SolarPhase.DAWN, dawn.phase)
        assertEquals("Dawn · Sunrise 06:39", dawn.summaryLabel)

        // 07:00 -> Morning golden hour (21m after sunrise)
        val morningGolden = SolarPhaseCalculator.evaluate(
            now = Instant.ofEpochMilli(epochAt("2026-11-18T07:00:00")),
            timezoneId = "Europe/Belgrade",
            windows = windows,
        )
        assertNotNull(morningGolden)
        assertEquals(SolarPhase.GOLDEN_HOUR_MORNING, morningGolden.phase)
        assertTrue(morningGolden.isGoldenHour)
        assertEquals("Golden hour · Sunrise 06:39", morningGolden.summaryLabel)

        // 12:00 -> Midday
        val midday = SolarPhaseCalculator.evaluate(
            now = Instant.ofEpochMilli(epochAt("2026-11-18T12:00:00")),
            timezoneId = "Europe/Belgrade",
            windows = windows,
        )
        assertNotNull(midday)
        assertEquals(SolarPhase.DAY, midday.phase)
        assertEquals("Sunset 16:07", midday.summaryLabel)

        // 15:40 -> Evening golden hour (27m before sunset)
        val eveningGolden = SolarPhaseCalculator.evaluate(
            now = Instant.ofEpochMilli(epochAt("2026-11-18T15:40:00")),
            timezoneId = "Europe/Belgrade",
            windows = windows,
        )
        assertNotNull(eveningGolden)
        assertEquals(SolarPhase.GOLDEN_HOUR_EVENING, eveningGolden.phase)
        assertTrue(eveningGolden.isGoldenHour)
        assertEquals("Golden hour · Sunset 16:07", eveningGolden.summaryLabel)

        // 16:30 -> Dusk (23m after sunset)
        val dusk = SolarPhaseCalculator.evaluate(
            now = Instant.ofEpochMilli(epochAt("2026-11-18T16:30:00")),
            timezoneId = "Europe/Belgrade",
            windows = windows,
        )
        assertNotNull(dusk)
        assertEquals(SolarPhase.DUSK, dusk.phase)
        assertEquals("Dusk · Sunset 16:07", dusk.summaryLabel)

        // 22:15 -> Night after sunset points to tomorrow's sunrise (06:40)
        val nightLate = SolarPhaseCalculator.evaluate(
            now = Instant.ofEpochMilli(epochAt("2026-11-18T22:15:00")),
            timezoneId = "Europe/Belgrade",
            windows = windows,
        )
        assertNotNull(nightLate)
        assertEquals(SolarPhase.NIGHT, nightLate.phase)
        assertEquals("Sunrise 06:40", nightLate.summaryLabel)
    }

    @Test
    fun `oled pixel orbit stays within overscan safe bounds and disables cleanly`() {
        val disabled = PixelOrbitCalculator.computeOffset(epochMillis = 500_000L, enabled = false)
        assertEquals(0, disabled.dxDp)
        assertEquals(0, disabled.dyDp)
        assertEquals(1.0f, disabled.videoScale)

        val step0 = PixelOrbitCalculator.computeOffset(epochMillis = 0L, enabled = true)
        val step4 = PixelOrbitCalculator.computeOffset(epochMillis = 4 * 90_000L, enabled = true)
        assertNotEquals(step0, step4)

        for (step in 0 until 32) {
            val offset = PixelOrbitCalculator.computeOffset(epochMillis = step * 90_000L, enabled = true)
            assertTrue(offset.dxDp in -8..8)
            assertTrue(offset.dyDp in -5..5)
            assertEquals(1.025f, offset.videoScale)
        }
    }

    @Test
    fun `home channel selector provides multi-city featured row when favorites empty and switches to favorites`() {
        val catalogPath = System.getProperty("belgrade.catalog.v3.path")
        val manifest = CatalogJsonCodec.decode(File(catalogPath).readText(Charsets.UTF_8))

        val featured = HomeChannelSelector.selectChannelCameras(
            cameras = manifest.cameras,
            favoriteIds = emptySet(),
            maxItems = 12,
        )
        assertFalse(featured.isFavoritesMode)
        assertEquals(12, featured.cameras.size)
        // Featured row covers all 5 Serbian city/district groups including Zemun out of the box
        assertEquals(
            setOf(
                SerbiaCity.BELGRADE,
                SerbiaCity.ZEMUN,
                SerbiaCity.NOVI_SAD,
                SerbiaCity.SUBOTICA,
                SerbiaCity.NIS,
            ),
            featured.cameras.map { it.city }.toSet(),
        )

        val favoritesSelection = HomeChannelSelector.selectChannelCameras(
            cameras = manifest.cameras,
            favoriteIds = setOf("ns-varadinski-most", "bg-slavija"),
            maxItems = 10,
        )
        assertTrue(favoritesSelection.isFavoritesMode)
        assertEquals(
            setOf("ns-varadinski-most", "bg-slavija"),
            favoritesSelection.cameras.map { it.id }.toSet(),
        )
    }

    @Test
    fun `whole-graph round trip of v3 Serbia catalog preserves Zemun and VideoQuality tiers`() {
        val catalogV3Path = System.getProperty("belgrade.catalog.v3.path")
        assertNotNull(catalogV3Path, "System property belgrade.catalog.v3.path must be set")
        val rawDiskJson = File(catalogV3Path).readText(Charsets.UTF_8)
        val decoded = CatalogJsonCodec.decode(rawDiskJson)

        assertEquals(3, decoded.schemaVersion)
        assertEquals(74, decoded.cameras.size)
        assertEquals(6, decoded.cameras.count { it.city == SerbiaCity.ZEMUN })
        assertEquals(47, decoded.cameras.count { it.resolvedQuality.isHd })
        assertEquals(27, decoded.cameras.count { !it.resolvedQuality.isHd })

        val firstEncode = CatalogJsonCodec.encode(decoded)
        val reDecoded = CatalogJsonCodec.decode(firstEncode)
        val secondEncode = CatalogJsonCodec.encode(reDecoded)

        assertEquals(decoded, reDecoded)
        assertEquals(firstEncode, secondEncode)
        assertEquals(rawDiskJson, firstEncode)
    }

    @Test
    fun `tv stage reducer splits HD before SD in strip and cycles quality filter across zapping`() {
        val cams = listOf(
            sampleCamera("bg-sd-1", "Brankov most", SerbiaCity.BELGRADE, quality = VideoQuality.SD),
            sampleCamera("bg-2k-1", "London 2K", SerbiaCity.BELGRADE, quality = VideoQuality.UHD_2K),
            sampleCamera("zm-720-1", "Zemun Sever", SerbiaCity.ZEMUN, quality = VideoQuality.HD_720P),
            sampleCamera("su-sd-1", "Kelebija", SerbiaCity.SUBOTICA, quality = VideoQuality.SD),
        )
        val initial = TvStageState.initial(cameras = cams, initialCameraId = "bg-sd-1")

        // In ALL mode, strip orders HD cameras (2K -> 720p) ahead of SD cameras and splits sections
        assertEquals(listOf("bg-2k-1", "zm-720-1", "bg-sd-1", "su-sd-1"), initial.stripCameras.map { it.id })
        assertEquals(listOf("bg-2k-1", "zm-720-1"), initial.hdStripCameras.map { it.id })
        assertEquals(listOf("bg-sd-1", "su-sd-1"), initial.sdStripCameras.map { it.id })

        // Cycling ALL -> HD_ONLY switches active SD camera onto an HD camera in the same city and restricts zapping
        val hdOnly = TvStageReducer.reduce(initial, TvRemoteAction.CycleQualityFilter).state
        assertEquals(QualityFilter.HD_ONLY, hdOnly.qualityFilter)
        assertEquals("bg-2k-1", hdOnly.activeCameraId)
        assertEquals(listOf("bg-2k-1", "zm-720-1"), hdOnly.zapPlaylist.map { it.id })
        assertEquals(listOf("bg-2k-1", "zm-720-1"), hdOnly.hdStripCameras.map { it.id })
        assertTrue(hdOnly.sdStripCameras.isEmpty())

        // Cycling HD_ONLY -> SD_ONLY switches active HD camera onto an SD camera and restricts zapping to SD
        val sdOnly = TvStageReducer.reduce(hdOnly, TvRemoteAction.CycleQualityFilter).state
        assertEquals(QualityFilter.SD_ONLY, sdOnly.qualityFilter)
        assertEquals("bg-sd-1", sdOnly.activeCameraId)
        assertEquals(listOf("bg-sd-1", "su-sd-1"), sdOnly.zapPlaylist.map { it.id })
        assertTrue(sdOnly.hdStripCameras.isEmpty())
        assertEquals(listOf("bg-sd-1", "su-sd-1"), sdOnly.sdStripCameras.map { it.id })

        // Cycling SD_ONLY -> ALL restores full catalog
        val backToAll = TvStageReducer.reduce(sdOnly, TvRemoteAction.CycleQualityFilter).state
        assertEquals(QualityFilter.ALL, backToAll.qualityFilter)
        assertEquals(4, backToAll.zapPlaylist.size)
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
