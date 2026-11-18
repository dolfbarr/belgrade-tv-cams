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
import rs.belgrade.tvcams.domain.stream.StreamPayloadParsers
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
        assertEquals("13.7°C · 15 km/h", slavijaWeather.compactMetricsLabel())

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

        val eveningInstant = Instant.parse("2026-11-18T16:52:00Z")
        val viewerZone = ZoneId.of("Europe/Belgrade")
        val madridClock = CameraClockFormatter.formatDualClock(eveningInstant, "Europe/Madrid", viewerZone)
        assertTrue(madridClock.isSameZoneTime)
        assertEquals("17:52 (UTC+1)", madridClock.formattedLabel())

        val spbClock = CameraClockFormatter.formatDualClock(eveningInstant, "Europe/Moscow", viewerZone)
        assertFalse(spbClock.isSameZoneTime)
        assertEquals("19:52 (UTC+3) · Local 17:52", spbClock.formattedLabel())

        val tomskClock = CameraClockFormatter.formatDualClock(eveningInstant, "Asia/Tomsk", viewerZone)
        assertFalse(tomskClock.isSameZoneTime)
        assertEquals("23:52 (UTC+7) · Local 17:52", tomskClock.formattedLabel())
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
        assertEquals("AQI 29", a1.compactLabel())
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
        assertEquals("06:39", nightEarly.targetTimeLabel)

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
        assertEquals("16:07", eveningGolden.targetTimeLabel)

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
        assertEquals(78, decoded.cameras.size)
        assertEquals(8, decoded.cameras.count { it.city == SerbiaCity.ZEMUN })
        assertEquals(51, decoded.cameras.count { it.resolvedQuality.isHd })
        assertEquals(27, decoded.cameras.count { !it.resolvedQuality.isHd })
        assertTrue(decoded.cameras.any { it.id == "bg-bulevar-mihajla-pupina" && "TC Ušće" in it.streets })
        assertTrue(decoded.cameras.any { it.id == "bg-gazela" && "TC Galerija Belgrade" in it.streets })

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

        // Selecting an SD camera directly (e.g. via TV Home deep link) while HD_ONLY is active relaxes filter to ALL
        val deepLinkedSd = TvStageReducer.reduce(hdOnly, TvRemoteAction.SelectCamera("bg-sd-1")).state
        assertEquals("bg-sd-1", deepLinkedSd.activeCameraId)
        assertEquals(QualityFilter.ALL, deepLinkedSd.qualityFilter)

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

    @Test
    fun `auto rotate interval cycles presets, defaults screensaver to 30s, and silent rotation falls back when scope has single camera`() {
        assertEquals(rs.belgrade.tvcams.domain.favorites.AutoRotateInterval.OFF, rs.belgrade.tvcams.domain.favorites.AutoRotateInterval.OFF.effectiveFor(isScreensaverMode = false))
        assertEquals(rs.belgrade.tvcams.domain.favorites.AutoRotateInterval.SEC_30, rs.belgrade.tvcams.domain.favorites.AutoRotateInterval.OFF.effectiveFor(isScreensaverMode = true))
        assertEquals(rs.belgrade.tvcams.domain.favorites.AutoRotateInterval.SEC_60, rs.belgrade.tvcams.domain.favorites.AutoRotateInterval.SEC_60.effectiveFor(isScreensaverMode = true))

        val cams = listOf(
            sampleCamera("bg-2k-1", "TC Ušće", SerbiaCity.BELGRADE, quality = VideoQuality.UHD_2K),
            sampleCamera("zm-2k-1", "Zemun Lakat", SerbiaCity.ZEMUN, quality = VideoQuality.UHD_2K),
            sampleCamera("bg-sd-1", "Gazela Galerija", SerbiaCity.BELGRADE, quality = VideoQuality.SD),
        )
        var state = TvStageState.initial(
            cameras = cams,
            favoriteIds = setOf("bg-2k-1"),
            initialCameraId = "bg-2k-1",
            zapScope = ZapScope.FAVORITES,
            qualityFilter = QualityFilter.HD_ONLY,
        )
        assertEquals(rs.belgrade.tvcams.domain.favorites.AutoRotateInterval.OFF, state.autoRotateInterval)

        // Cycle OFF -> 15s -> 30s -> 60s -> 2m -> OFF
        state = TvStageReducer.reduce(state, TvRemoteAction.CycleAutoRotate).state
        assertEquals(rs.belgrade.tvcams.domain.favorites.AutoRotateInterval.SEC_15, state.autoRotateInterval)
        assertEquals("Auto-rotate: 15s", state.noticeMessage)

        state = TvStageReducer.reduce(state, TvRemoteAction.CycleAutoRotate).state
        assertEquals(rs.belgrade.tvcams.domain.favorites.AutoRotateInterval.SEC_30, state.autoRotateInterval)
        assertEquals("Auto-rotate: 30s", state.noticeMessage)

        state = TvStageReducer.reduce(state, TvRemoteAction.CycleAutoRotate).state
        assertEquals(rs.belgrade.tvcams.domain.favorites.AutoRotateInterval.SEC_60, state.autoRotateInterval)
        assertEquals("Auto-rotate: 60s", state.noticeMessage)

        state = TvStageReducer.reduce(state, TvRemoteAction.CycleAutoRotate).state
        assertEquals(rs.belgrade.tvcams.domain.favorites.AutoRotateInterval.SEC_120, state.autoRotateInterval)
        assertEquals("Auto-rotate: 2m", state.noticeMessage)

        val resetState = TvStageReducer.reduce(state, TvRemoteAction.CycleAutoRotate).state
        assertEquals(rs.belgrade.tvcams.domain.favorites.AutoRotateInterval.OFF, resetState.autoRotateInterval)
        assertEquals("Auto-rotate: Off", resetState.noticeMessage)

        // Pin HUD and run ZapNextSilent with a 1-item Favorites playlist:
        // must fall back to qualityFilteredCameras (HD_ONLY -> bg-2k-1, zm-2k-1) and preserve pinned HUD
        val pinnedState = state.copy(overlayMode = OverlayMode.HUD_VISIBLE, hudPinned = true)
        assertEquals(listOf("bg-2k-1"), pinnedState.zapPlaylist.map { it.id })

        val rotated1 = TvStageReducer.reduce(pinnedState, TvRemoteAction.ZapNextSilent).state
        assertEquals("zm-2k-1", rotated1.activeCameraId)
        assertEquals(OverlayMode.HUD_VISIBLE, rotated1.overlayMode)
        assertTrue(rotated1.hudPinned)

        val pureState = rotated1.copy(overlayMode = OverlayMode.PURE_CAMERA, hudPinned = false)
        val rotated2 = TvStageReducer.reduce(pureState, TvRemoteAction.ZapNextSilent).state
        assertEquals("bg-2k-1", rotated2.activeCameraId)
        assertEquals(OverlayMode.PURE_CAMERA, rotated2.overlayMode)
    }

    @Test
    fun `whole-graph round trip of v4 world catalog preserves 4 countries, 11 cities, and multi-timezone invariants`() {
        val catalogV4Path = System.getProperty("belgrade.catalog.v4.path")
        assertNotNull(catalogV4Path, "System property belgrade.catalog.v4.path must be set")
        val rawDiskJson = File(catalogV4Path).readText(Charsets.UTF_8)
        val decoded = CatalogJsonCodec.decode(rawDiskJson)

        assertEquals(4, decoded.schemaVersion)
        assertEquals(128, decoded.cameras.size)
        assertEquals(
            mapOf(
                CameraCountry.SERBIA to 78,
                CameraCountry.RUSSIA to 23,
                CameraCountry.SPAIN to 22,
                CameraCountry.ROMANIA to 5,
            ),
            decoded.cameras.groupingBy { it.resolvedCountry }.eachCount(),
        )
        assertEquals(11, decoded.cameras.map { it.city }.toSet().size)
        assertTrue(decoded.cameras.filter { it.city == CameraCity.TOMSK }.all { it.timezone == "Asia/Tomsk" })
        assertTrue(decoded.cameras.filter { it.city == CameraCity.SAINT_PETERSBURG }.all { it.timezone == "Europe/Moscow" })
        assertTrue(decoded.cameras.filter { it.city == CameraCity.ROSTOV_ON_DON }.all { it.timezone == "Europe/Moscow" })
        assertTrue(decoded.cameras.filter { it.city == CameraCity.MADRID }.all { it.timezone == "Europe/Madrid" })
        assertTrue(decoded.cameras.filter { it.city == CameraCity.BARCELONA }.all { it.timezone == "Europe/Madrid" })
        assertTrue(decoded.cameras.filter { it.city == CameraCity.BUCHAREST }.all { it.timezone == "Europe/Bucharest" })

        val featured = HomeChannelSelector.selectChannelCameras(
            cameras = decoded.cameras,
            favoriteIds = emptySet(),
            maxItems = 12,
        )
        assertEquals("World Live Cams — Featured", featured.channelTitle)
        assertEquals(
            setOf(
                CameraCountry.SERBIA,
                CameraCountry.RUSSIA,
                CameraCountry.SPAIN,
                CameraCountry.ROMANIA,
            ),
            featured.cameras.map { it.resolvedCountry }.toSet(),
        )
        assertEquals(
            setOf(
                CameraCity.BELGRADE,
                CameraCity.ZEMUN,
                CameraCity.NOVI_SAD,
                CameraCity.SUBOTICA,
                CameraCity.NIS,
                CameraCity.SAINT_PETERSBURG,
                CameraCity.ROSTOV_ON_DON,
                CameraCity.TOMSK,
                CameraCity.BARCELONA,
                CameraCity.MADRID,
                CameraCity.BUCHAREST,
            ),
            featured.cameras.map { it.city }.toSet(),
        )

        val firstEncode = CatalogJsonCodec.encode(decoded)
        val reDecoded = CatalogJsonCodec.decode(firstEncode)
        val secondEncode = CatalogJsonCodec.encode(reDecoded)

        assertEquals(decoded, reDecoded)
        assertEquals(firstEncode, secondEncode)
        assertEquals(rawDiskJson, firstEncode)
    }

    @Test
    fun `country grouping, contextual city scope, country zapping, and bottom-center default panel position`() {
        val cams = listOf(
            sampleCamera("bg-hd", "Belgrade London", CameraCity.BELGRADE, quality = VideoQuality.UHD_2K),
            sampleCamera("spb-hd", "Palace Bridge", CameraCity.SAINT_PETERSBURG, quality = VideoQuality.FHD_1080P),
            sampleCamera("tof-sd", "Tomsky Lane", CameraCity.TOMSK, quality = VideoQuality.SD),
            sampleCamera("bcn-sd", "Port Olímpic", CameraCity.BARCELONA, quality = VideoQuality.SD),
            sampleCamera("mad-hd", "Plaza de Cibeles", CameraCity.MADRID, quality = VideoQuality.HD_720P),
            sampleCamera("buh-hd", "Piața Romană", CameraCity.BUCHAREST, quality = VideoQuality.FHD_1080P),
        )
        var state = TvStageState.initial(
            cameras = cams,
            favoriteIds = setOf("buh-hd"),
            initialCameraId = "spb-hd",
        )

        // Default panel position must be BOTTOM_CENTER
        assertEquals(InfoPanelPosition.BOTTOM_CENTER, state.infoPanelPosition)
        assertFalse(state.infoPanelPosition.isTop)

        // Cycle BOTTOM_CENTER -> BOTTOM_RIGHT -> BOTTOM_LEFT -> TOP_LEFT -> TOP_RIGHT -> BOTTOM_CENTER
        state = TvStageReducer.reduce(state, TvRemoteAction.CycleInfoPanelPosition).state
        assertEquals(InfoPanelPosition.BOTTOM_RIGHT, state.infoPanelPosition)
        assertEquals("Panel position: Bottom right", state.noticeMessage)

        state = TvStageReducer.reduce(state, TvRemoteAction.CycleInfoPanelPosition).state
        assertEquals(InfoPanelPosition.BOTTOM_LEFT, state.infoPanelPosition)

        state = TvStageReducer.reduce(state, TvRemoteAction.CycleInfoPanelPosition).state
        assertEquals(InfoPanelPosition.TOP_LEFT, state.infoPanelPosition)
        assertTrue(state.infoPanelPosition.isTop)

        state = TvStageReducer.reduce(state, TvRemoteAction.CycleInfoPanelPosition).state
        assertEquals(InfoPanelPosition.TOP_RIGHT, state.infoPanelPosition)
        assertTrue(state.infoPanelPosition.isTop)

        state = TvStageReducer.reduce(state, TvRemoteAction.CycleInfoPanelPosition).state
        assertEquals(InfoPanelPosition.BOTTOM_CENTER, state.infoPanelPosition)

        // Strip groups cameras per country (Serbia -> Russia -> Spain -> Romania), HD before SD within each country
        assertNull(state.selectedStripCountry)
        assertEquals(
            listOf(CameraCountry.SERBIA, CameraCountry.RUSSIA, CameraCountry.SPAIN, CameraCountry.ROMANIA),
            state.stripCountryGroups.map { it.country },
        )
        val spainGroup = state.stripCountryGroups.first { it.country == CameraCountry.SPAIN }
        assertEquals(listOf("mad-hd"), spainGroup.hdCameras.map { it.id })
        assertEquals(listOf("bcn-sd"), spainGroup.sdCameras.map { it.id })

        // Selecting Russia country filter scopes strip to Russia (HD first, then SD) and exposes Russian cities
        state = TvStageReducer.reduce(
            state,
            TvRemoteAction.SelectStripFilter(StripFilter.Country(CameraCountry.RUSSIA)),
        ).state
        assertEquals(CameraCountry.RUSSIA, state.selectedStripCountry)
        assertEquals(listOf("spb-hd", "tof-sd"), state.stripCameras.map { it.id })
        assertEquals(
            listOf(
                CameraCity.MOSCOW,
                CameraCity.SAINT_PETERSBURG,
                CameraCity.ROSTOV_ON_DON,
                CameraCity.TOMSK,
                CameraCity.MAGADAN,
            ),
            CameraCity.citiesFor(CameraCountry.RUSSIA),
        )

        // Selecting Tomsk city keeps selectedStripCountry == RUSSIA
        state = TvStageReducer.reduce(
            state,
            TvRemoteAction.SelectStripFilter(StripFilter.City(CameraCity.TOMSK)),
        ).state
        assertEquals(CameraCountry.RUSSIA, state.selectedStripCountry)
        assertEquals(listOf("tof-sd"), state.stripCameras.map { it.id })

        // Multi-country ZapScope cycles ALL -> COUNTRY (Russia: spb-hd, tof-sd) -> CITY (Saint Petersburg: spb-hd) -> FAVORITES (buh-hd) -> ALL
        state = TvStageReducer.reduce(state, TvRemoteAction.ToggleZapScope).state
        assertEquals(ZapScope.COUNTRY, state.zapScope)
        assertEquals(listOf("spb-hd", "tof-sd"), state.zapPlaylist.map { it.id })
        assertEquals("Zapping Russia (2)", state.noticeMessage)

        state = TvStageReducer.reduce(state, TvRemoteAction.ToggleZapScope).state
        assertEquals(ZapScope.CITY, state.zapScope)
        assertEquals(listOf("spb-hd"), state.zapPlaylist.map { it.id })
        assertEquals("Zapping Saint Petersburg (1)", state.noticeMessage)

        state = TvStageReducer.reduce(state, TvRemoteAction.ToggleZapScope).state
        assertEquals(ZapScope.FAVORITES, state.zapScope)
        assertEquals(listOf("buh-hd"), state.zapPlaylist.map { it.id })

        state = TvStageReducer.reduce(state, TvRemoteAction.ToggleZapScope).state
        assertEquals(ZapScope.ALL, state.zapScope)
    }

    @Test
    fun `stream payload parsers extract YouTube Live, Rutube, and IPCamLive HLS manifests accurately`() {
        assertEquals(
            "https://m.youtube.com/watch?v=dfVK7ld38Ys",
            StreamPayloadParsers.normalizeYouTubeLiveWatchUrl("dfVK7ld38Ys"),
        )
        assertEquals(
            "https://m.youtube.com/watch?v=ZxCeWQ3Lcig",
            StreamPayloadParsers.normalizeYouTubeLiveWatchUrl("https://www.youtube.com/watch?v=ZxCeWQ3Lcig&ab_channel=EarthCam"),
        )
        assertEquals(
            "https://m.youtube.com/@MadridSkyLive/live",
            StreamPayloadParsers.normalizeYouTubeLiveWatchUrl("https://www.youtube.com/@MadridSkyLive/live"),
        )

        val ytWatchHtml = """
            {"streamingData":{"expiresInSeconds":"21540","hlsManifestUrl":"https:\/\/manifest.googlevideo.com\/api\/manifest\/hls_variant\/id\/dfVK7ld38Ys.5\/file\/index.m3u8\u0026keepalive=yes"}}
        """.trimIndent()
        assertEquals(
            "https://manifest.googlevideo.com/api/manifest/hls_variant/id/dfVK7ld38Ys.5/file/index.m3u8&keepalive=yes",
            StreamPayloadParsers.extractYouTubeHlsUrl(ytWatchHtml),
        )

        val rutubeJson = """
            {"live_streams":{"hls":[{"url":"https://bl.rutube.ru/livestream/eba7bc75dd7994f0d7e22e1ad9e76a87/index.m3u8?s=sig123&e=2090000000"}]}}
        """.trimIndent()
        assertEquals(
            "eba7bc75dd7994f0d7e22e1ad9e76a87",
            StreamPayloadParsers.extractRutubeVideoId("https://rutube.ru/video/eba7bc75dd7994f0d7e22e1ad9e76a87/"),
        )
        assertEquals(
            "https://bl.rutube.ru/livestream/eba7bc75dd7994f0d7e22e1ad9e76a87/index.m3u8?s=sig123&e=2090000000",
            StreamPayloadParsers.extractRutubeHlsUrl(rutubeJson),
        )

        val ipCamLiveJson = """
            {"details":{"alias":"kameratrg","streamavailable":"1","streamid":"6eawzkcys3di59czx","address":"http:\/\/s110.ipcamlive.com\/"},"streaminfo":{"live":{"levels":[{"url":"stream.m3u8"}]}}}
        """.trimIndent()
        assertEquals(
            "kameratrg",
            StreamPayloadParsers.extractIpCamLiveAlias("https://www.ipcamlive.com/player/player.php?alias=kameratrg"),
        )
        assertEquals(
            "https://s110.ipcamlive.com/streams/6eawzkcys3di59czx/stream.m3u8",
            StreamPayloadParsers.extractIpCamLiveHlsUrl(ipCamLiveJson),
        )
    }

    @Test
    fun `live video sorts ahead of snapshots, transport filter and popular tab scope cleanly, and performance mode adapts to low-RAM TVs`() {
        val snapshot4k = sampleCamera(
            id = "tyo-snap-4k",
            name = "Tokyo Snapshot 4K",
            city = CameraCity.TOKYO,
            quality = VideoQuality.UHD_4K,
        ).copy(
            featured = false,
            streams = listOf(
                StreamDescriptor(
                    type = StreamTransportType.SNAPSHOT_POLL,
                    url = "https://imgproxy.windy.com/_/full/plain/current/1/original.jpg",
                ),
            ),
        )
        val live1080 = sampleCamera(
            id = "tyo-live-1080",
            name = "Shibuya Scramble Live",
            city = CameraCity.TOKYO,
            quality = VideoQuality.FHD_1080P,
        ).copy(
            featured = true,
            streams = listOf(
                StreamDescriptor(
                    type = StreamTransportType.YOUTUBE_LIVE,
                    url = "dfVK7ld38Ys",
                ),
            ),
        )

        assertFalse(snapshot4k.isLiveVideo)
        assertTrue(live1080.isLiveVideo)
        assertTrue(live1080.isFeatured)
        assertEquals("4K UHD", snapshot4k.resolvedQuality.shortBadgeLabel())

        var state = TvStageState.initial(
            cameras = listOf(snapshot4k, live1080),
            initialCameraId = "tyo-snap-4k",
        )

        // Live video sorts ahead of snapshot within the same country even when snapshot has higher static resolution
        assertEquals(listOf("tyo-live-1080", "tyo-snap-4k"), state.stripCameras.map { it.id })
        assertEquals(1, state.liveVideoTotalCount)
        assertEquals(1, state.snapshotTotalCount)

        // Popular filter surfaces featured cameras
        state = TvStageReducer.reduce(state, TvRemoteAction.SelectStripFilter(StripFilter.Popular)).state
        assertEquals(listOf("tyo-live-1080"), state.stripCameras.map { it.id })

        // Reset strip filter to All and cycle TransportFilter: ALL -> LIVE_VIDEO_ONLY -> SNAPSHOT_ONLY -> ALL
        state = TvStageReducer.reduce(state, TvRemoteAction.SelectStripFilter(StripFilter.All)).state
        state = TvStageReducer.reduce(state, TvRemoteAction.CycleTransportFilter).state
        assertEquals(TransportFilter.LIVE_VIDEO_ONLY, state.transportFilter)
        assertEquals("tyo-live-1080", state.activeCameraId)
        assertEquals(listOf("tyo-live-1080"), state.stripCameras.map { it.id })

        state = TvStageReducer.reduce(state, TvRemoteAction.CycleTransportFilter).state
        assertEquals(TransportFilter.SNAPSHOT_ONLY, state.transportFilter)
        assertEquals("tyo-snap-4k", state.activeCameraId)
        assertEquals(listOf("tyo-snap-4k"), state.stripCameras.map { it.id })

        // Performance mode cycling & low-RAM surface / resolution cap decisions
        assertEquals(PerformanceMode.AUTO, state.performanceMode)
        assertTrue(PerformanceMode.AUTO.shouldUseLowSpecSurface(isLowRamDevice = true))
        assertFalse(PerformanceMode.AUTO.shouldUseLowSpecSurface(isLowRamDevice = false))
        assertEquals(1080, PerformanceMode.AUTO.maxVideoHeightPx(displayHeightPx = 1080, isLowRamDevice = false))
        assertEquals(720, PerformanceMode.LOW_SPEC.maxVideoHeightPx(displayHeightPx = 2160, isLowRamDevice = false))
        assertEquals(2160, PerformanceMode.MAX_QUALITY.maxVideoHeightPx(displayHeightPx = 1080, isLowRamDevice = true))

        state = TvStageReducer.reduce(state, TvRemoteAction.CyclePerformanceMode).state
        assertEquals(PerformanceMode.LOW_SPEC, state.performanceMode)
        assertEquals("Performance: Low-spec", state.noticeMessage)
    }

    @Test
    fun `whole-graph round trip of v5 world catalog preserves 13 countries, 22 cities, and live video in every city`() {
        val catalogV5Path = System.getProperty("belgrade.catalog.v5.path")
        assertNotNull(catalogV5Path, "System property belgrade.catalog.v5.path must be set")
        val rawDiskJson = File(catalogV5Path).readText(Charsets.UTF_8)
        val decoded = CatalogJsonCodec.decode(rawDiskJson)

        assertEquals(5, decoded.schemaVersion)
        assertEquals(225, decoded.cameras.size)
        assertEquals(CameraCountry.entries.toSet(), decoded.cameras.map { it.resolvedCountry }.toSet())
        assertEquals(CameraCity.entries.toSet(), decoded.cameras.map { it.city }.toSet())

        CameraCity.entries.forEach { city ->
            val cityCams = decoded.cameras.filter { it.city == city }
            assertTrue(
                cityCams.any { it.isLiveVideo },
                "City $city must retain at least 1 primary live video camera after probing",
            )
        }

        val firstEncode = CatalogJsonCodec.encode(decoded)
        val reDecoded = CatalogJsonCodec.decode(firstEncode)
        val secondEncode = CatalogJsonCodec.encode(reDecoded)

        assertEquals(decoded, reDecoded)
        assertEquals(firstEncode, secondEncode)
        assertEquals(rawDiskJson, firstEncode)
    }
}
