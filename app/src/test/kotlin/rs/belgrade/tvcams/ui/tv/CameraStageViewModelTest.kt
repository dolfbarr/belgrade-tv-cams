package rs.belgrade.tvcams.ui.tv

import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test
import rs.belgrade.tvcams.domain.airquality.AirQualityBand
import rs.belgrade.tvcams.domain.airquality.AirQualityRepository
import rs.belgrade.tvcams.domain.airquality.CurrentAirQuality
import rs.belgrade.tvcams.domain.catalog.CatalogBootstrapCoordinator
import rs.belgrade.tvcams.domain.catalog.CatalogCacheStore
import rs.belgrade.tvcams.domain.catalog.CatalogSeedSource
import rs.belgrade.tvcams.domain.catalog.RemoteCatalogClient
import rs.belgrade.tvcams.domain.catalog.RemoteCatalogResult
import rs.belgrade.tvcams.domain.favorites.AutoRotateInterval
import rs.belgrade.tvcams.domain.favorites.FavoritesRepository
import rs.belgrade.tvcams.domain.favorites.InfoPanelPosition
import rs.belgrade.tvcams.domain.favorites.PerformanceMode
import rs.belgrade.tvcams.domain.favorites.QualityFilter
import rs.belgrade.tvcams.domain.favorites.TransportFilter
import rs.belgrade.tvcams.domain.favorites.ViewerPreferences
import rs.belgrade.tvcams.domain.favorites.ZapScope
import rs.belgrade.tvcams.domain.model.Camera
import rs.belgrade.tvcams.domain.model.CameraCategory
import rs.belgrade.tvcams.domain.model.CameraCity
import rs.belgrade.tvcams.domain.model.CameraCountry
import rs.belgrade.tvcams.domain.model.CatalogManifest
import rs.belgrade.tvcams.domain.model.GeoCoordinates
import rs.belgrade.tvcams.domain.model.StreamDescriptor
import rs.belgrade.tvcams.domain.model.StreamTransportType
import rs.belgrade.tvcams.domain.model.VideoQuality
import rs.belgrade.tvcams.domain.tv.CatalogBootstrapState
import rs.belgrade.tvcams.domain.tv.OverlayMode
import rs.belgrade.tvcams.domain.tv.TvRemoteAction
import rs.belgrade.tvcams.domain.weather.CurrentWeather
import rs.belgrade.tvcams.domain.weather.WeatherCondition
import rs.belgrade.tvcams.domain.weather.WeatherRepository
import rs.belgrade.tvcams.player.PlaybackTelemetry
import java.io.IOException
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class CameraStageViewModelTest {

    private lateinit var testDispatcher: TestDispatcher

    @Before
    fun setUp() {
        testDispatcher = StandardTestDispatcher()
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun testCam(
        id: String,
        name: String,
        country: CameraCountry = CameraCountry.USA,
        city: CameraCity = CameraCity.NEW_YORK,
    ): Camera = Camera(
        id = id,
        name = name,
        country = country,
        city = city,
        quality = VideoQuality.UHD_4K,
        featured = true,
        timezone = "America/New_York",
        streets = listOf("Broadway"),
        municipality = "Manhattan",
        category = CameraCategory.SQUARES,
        direction = "North",
        posterUrl = "https://example.com/$id.jpg",
        coordinates = GeoCoordinates(40.758, -73.985),
        streams = listOf(StreamDescriptor(StreamTransportType.YOUTUBE_LIVE, "ZxCeWQ3Lcig")),
    )

    private class FakeFavoritesRepository(
        initial: ViewerPreferences = ViewerPreferences(),
    ) : FavoritesRepository {
        val state = MutableStateFlow(initial)
        override val preferences: Flow<ViewerPreferences> = state

        override suspend fun setFavorites(favoriteIds: Set<String>) {
            state.value = state.value.copy(favoriteIds = favoriteIds)
        }

        override suspend fun setLastCamera(cameraId: String) {
            state.value = state.value.copy(lastCameraId = cameraId)
        }

        override suspend fun setZapScope(scope: ZapScope) {
            state.value = state.value.copy(zapScope = scope)
        }

        override suspend fun setQualityFilter(filter: QualityFilter) {
            state.value = state.value.copy(qualityFilter = filter)
        }

        override suspend fun setTransportFilter(filter: TransportFilter) {
            state.value = state.value.copy(transportFilter = filter)
        }

        override suspend fun setPerformanceMode(mode: PerformanceMode) {
            state.value = state.value.copy(performanceMode = mode)
        }

        override suspend fun setAutoRotateInterval(interval: AutoRotateInterval) {
            state.value = state.value.copy(autoRotateInterval = interval)
        }

        override suspend fun setInfoPanelPosition(position: InfoPanelPosition) {
            state.value = state.value.copy(infoPanelPosition = position)
        }

        override suspend fun setShowClockOverlay(enabled: Boolean) {
            state.value = state.value.copy(showClockOverlay = enabled)
        }

        override suspend fun setShowWeatherOverlay(enabled: Boolean) {
            state.value = state.value.copy(showWeatherOverlay = enabled)
        }

        override suspend fun setShowAirQualityOverlay(enabled: Boolean) {
            state.value = state.value.copy(showAirQualityOverlay = enabled)
        }

        override suspend fun setShowSolarOverlay(enabled: Boolean) {
            state.value = state.value.copy(showSolarOverlay = enabled)
        }

        override suspend fun setOledProtectionEnabled(enabled: Boolean) {
            state.value = state.value.copy(oledProtectionEnabled = enabled)
        }
    }

    @Test
    fun bootstrapFailureRetryDebouncedZappingHudAutoHideNumericJumpAndPrefsPersist() = runTest(testDispatcher) {
        var failSeed = true
        val cams = listOf(
            testCam("cam-1", "Times Square"),
            testCam("cam-2", "Shibuya Scramble", CameraCountry.JAPAN, CameraCity.TOKYO),
            testCam("cam-3", "Yerevan Ararat", CameraCountry.ARMENIA, CameraCity.YEREVAN),
        )
        val seedManifest = CatalogManifest(5, 100L, "2026-11-19T00:00:00Z", cams.take(2))
        val remoteManifest = CatalogManifest(5, 200L, "2026-11-19T12:00:00Z", cams)

        val coordinator = CatalogBootstrapCoordinator(
            seedSource = object : CatalogSeedSource {
                override suspend fun readSeed(): CatalogManifest {
                    if (failSeed) throw IOException("Corrupted seed")
                    return seedManifest
                }
            },
            cacheStore = object : CatalogCacheStore {
                private var cached: CatalogManifest? = null
                private var etag: String? = null
                override suspend fun readCached(): CatalogManifest? = cached
                override suspend fun readETag(): String? = etag
                override suspend fun writeCached(manifest: CatalogManifest, etag: String?) {
                    cached = manifest
                    this.etag = etag
                }
            },
            remoteClient = object : RemoteCatalogClient {
                override suspend fun fetchCatalog(ifNoneMatch: String?): RemoteCatalogResult =
                    RemoteCatalogResult.Updated(remoteManifest, "\"etag-200\"")
            },
        )
        val favRepo = FakeFavoritesRepository()
        val weatherRepo = object : WeatherRepository {
            override suspend fun getCurrentWeather(lat: Double, lon: Double): CurrentWeather =
                CurrentWeather(21.0, 20.0, 9.0, WeatherCondition.CLEAR, true, 1_000L)
        }
        val aqiRepo = object : AirQualityRepository {
            override suspend fun getCurrentAirQuality(lat: Double, lon: Double): CurrentAirQuality =
                CurrentAirQuality(22, 7.0, 11.0, AirQualityBand.FAIR, 1_000L)
        }

        val vmStore = ViewModelStore()
        try {
            val vm = ViewModelProvider(
                vmStore,
                CameraStageViewModel.Factory(
                    catalogCoordinator = coordinator,
                    favoritesRepository = favRepo,
                    weatherRepository = weatherRepo,
                    airQualityRepository = aqiRepo,
                ),
            )[CameraStageViewModel::class.java]

            val debouncedObserved = ArrayList<String>()
            var exited = false
            backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
                vm.debouncedStreamCamera.collect { cam ->
                    if (cam != null) debouncedObserved.add(cam.id)
                }
            }
            backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
                vm.exitEvents.collect { exited = true }
            }

            // 1. Initial bootstrap fails cleanly
            runCurrent()
            assertTrue(vm.stageState.value.catalogBootstrapState is CatalogBootstrapState.Failed)

            // 2. RetryCatalogLoad recovers & applies remote 3-camera manifest + fetches weather/AQI
            failSeed = false
            vm.dispatch(TvRemoteAction.RetryCatalogLoad)
            runCurrent()
            assertEquals(CatalogBootstrapState.Ready, vm.stageState.value.catalogBootstrapState)
            assertEquals(3, vm.stageState.value.cameras.size)
            assertNotNull(vm.currentWeather.value)
            assertNotNull(vm.currentAirQuality.value)

            // Wait initial 250ms debounce for cam-1
            advanceTimeBy(CameraStageViewModel.ZAP_DEBOUNCE_MS + 10L)
            runCurrent()
            assertEquals(listOf("cam-1"), debouncedObserved)

            // 3. Rapid zapping cam-1 -> cam-2 -> cam-3 within 80ms coalesces to cam-3 only
            vm.dispatch(TvRemoteAction.ZapNext)
            advanceTimeBy(80L)
            runCurrent()
            vm.dispatch(TvRemoteAction.ZapNext)
            advanceTimeBy(CameraStageViewModel.ZAP_DEBOUNCE_MS + 20L)
            runCurrent()
            assertEquals(listOf("cam-1", "cam-3"), debouncedObserved)
            assertEquals("cam-3", favRepo.state.value.lastCameraId)

            // 4. HUD auto-hides after 5,000ms
            assertEquals(OverlayMode.HUD_VISIBLE, vm.stageState.value.overlayMode)
            advanceTimeBy(CameraStageViewModel.HUD_AUTO_HIDE_MS + 50L)
            runCurrent()
            assertEquals(OverlayMode.PURE_CAMERA, vm.stageState.value.overlayMode)

            // 5. Numeric channel jump: type '2' -> commits after 1,200ms to cam-2 (index 1 in sorted playlist)
            val expectedSecondId = vm.stageState.value.zapPlaylist[1].id
            vm.dispatch(TvRemoteAction.AppendChannelDigit(2))
            runCurrent()
            assertEquals("2", vm.stageState.value.pendingChannelDigits)
            advanceTimeBy(CameraStageViewModel.CHANNEL_COMMIT_DELAY_MS + 20L)
            runCurrent()
            assertEquals("", vm.stageState.value.pendingChannelDigits)
            assertEquals(expectedSecondId, vm.stageState.value.activeCameraId)

            // 6. All preference mutations persist to FavoritesRepository
            vm.dispatch(TvRemoteAction.ToggleFavoriteCurrent)
            vm.dispatch(TvRemoteAction.ToggleZapScope)
            vm.dispatch(TvRemoteAction.CycleQualityFilter)
            vm.dispatch(TvRemoteAction.CycleTransportFilter)
            vm.dispatch(TvRemoteAction.CyclePerformanceMode)
            vm.dispatch(TvRemoteAction.CycleAutoRotate)
            vm.dispatch(TvRemoteAction.CycleInfoPanelPosition)
            vm.dispatch(TvRemoteAction.ToggleClockOverlay)
            vm.dispatch(TvRemoteAction.ToggleWeatherOverlay)
            vm.dispatch(TvRemoteAction.ToggleAirQualityOverlay)
            vm.dispatch(TvRemoteAction.ToggleSolarOverlay)
            vm.dispatch(TvRemoteAction.ToggleOledProtection)
            vm.dispatch(TvRemoteAction.RetryActiveStream)
            vm.selectDeepLinkedCamera("cam-1")
            runCurrent()

            assertTrue(favRepo.state.value.favoriteIds.isNotEmpty())
            assertEquals(PerformanceMode.LOW_SPEC, favRepo.state.value.performanceMode)
            assertEquals(AutoRotateInterval.SEC_15, favRepo.state.value.autoRotateInterval)
            assertEquals(InfoPanelPosition.BOTTOM_RIGHT, favRepo.state.value.infoPanelPosition)
            assertFalse(favRepo.state.value.showClockOverlay)
            assertFalse(favRepo.state.value.oledProtectionEnabled)
            assertEquals(1, vm.streamRetryNonce.value)
            assertEquals("cam-1", vm.stageState.value.activeCameraId)

            // NavigateBack from HUD -> PURE_CAMERA -> exit
            vm.dispatch(TvRemoteAction.NavigateBack)
            runCurrent()
            assertFalse(exited)
            vm.dispatch(TvRemoteAction.NavigateBack)
            runCurrent()
            assertTrue(exited)
        } finally {
            vmStore.clear()
        }
    }

    @Test
    fun offlineAutoSkipAdvancesDuringScreensaverAndTripsCircuitBreakerOnTotalOutage() = runTest(testDispatcher) {
        val cams = listOf(
            testCam("us-1", "NYC 1"),
            testCam("am-1", "Yerevan 1", CameraCountry.ARMENIA, CameraCity.YEREVAN),
            testCam("jp-1", "Tokyo 1", CameraCountry.JAPAN, CameraCity.TOKYO),
        )
        val coordinator = CatalogBootstrapCoordinator(
            seedSource = object : CatalogSeedSource {
                override suspend fun readSeed(): CatalogManifest =
                    CatalogManifest(5, 10L, "2026-11-19T00:00:00Z", cams)
            },
            cacheStore = object : CatalogCacheStore {
                override suspend fun readCached(): CatalogManifest? = null
                override suspend fun readETag(): String? = null
                override suspend fun writeCached(manifest: CatalogManifest, etag: String?) = Unit
            },
            remoteClient = object : RemoteCatalogClient {
                override suspend fun fetchCatalog(ifNoneMatch: String?): RemoteCatalogResult =
                    RemoteCatalogResult.NotModified
            },
        )
        val favRepo = FakeFavoritesRepository(
            ViewerPreferences(
                showWeatherOverlay = false,
                showAirQualityOverlay = false,
                showSolarOverlay = false,
            ),
        )
        val noopWeather = object : WeatherRepository {
            override suspend fun getCurrentWeather(lat: Double, lon: Double): CurrentWeather? = null
        }
        val noopAqi = object : AirQualityRepository {
            override suspend fun getCurrentAirQuality(lat: Double, lon: Double): CurrentAirQuality? = null
        }

        val vmStore = ViewModelStore()
        try {
            val vm = ViewModelProvider(
                vmStore,
                CameraStageViewModel.Factory(
                    catalogCoordinator = coordinator,
                    favoritesRepository = favRepo,
                    weatherRepository = noopWeather,
                    airQualityRepository = noopAqi,
                    isScreensaverMode = true,
                ),
            )[CameraStageViewModel::class.java]

            runCurrent()
            val playlistIds = vm.stageState.value.zapPlaylist.map { it.id }
            assertEquals(3, playlistIds.size)
            assertEquals(playlistIds[0], vm.stageState.value.activeCameraId)

            // 1st camera offline -> skips after 1.5s grace
            vm.updateTelemetry(PlaybackTelemetry.DegradedRetrying(3_000L, 1))
            advanceTimeBy(CameraStageViewModel.OFFLINE_AUTO_SKIP_GRACE_MS + 50L)
            runCurrent()
            assertEquals(1, vm.consecutiveAutoSkips.value)
            assertEquals(playlistIds[1], vm.stageState.value.activeCameraId)

            // 2nd camera offline -> skips after 1.5s grace
            vm.updateTelemetry(PlaybackTelemetry.DegradedRetrying(3_000L, 1))
            advanceTimeBy(CameraStageViewModel.OFFLINE_AUTO_SKIP_GRACE_MS + 50L)
            runCurrent()
            assertEquals(2, vm.consecutiveAutoSkips.value)
            assertEquals(playlistIds[2], vm.stageState.value.activeCameraId)

            // 3rd camera also offline (all 3 failed -> Wi-Fi outage!) -> circuit breaker trips!
            vm.updateTelemetry(PlaybackTelemetry.DegradedRetrying(6_000L, 1))
            advanceTimeBy(CameraStageViewModel.OFFLINE_AUTO_SKIP_GRACE_MS * 4)
            runCurrent()
            assertEquals(2, vm.consecutiveAutoSkips.value)
            assertEquals(playlistIds[2], vm.stageState.value.activeCameraId)

            // Recovery resets circuit breaker counter to 0
            vm.updateTelemetry(PlaybackTelemetry.PlayingLive(StreamTransportType.YOUTUBE_LIVE))
            runCurrent()
            assertEquals(0, vm.consecutiveAutoSkips.value)
        } finally {
            vmStore.clear()
        }
    }
}
