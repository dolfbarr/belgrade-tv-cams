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
import rs.belgrade.tvcams.domain.model.StreamDescriptor
import rs.belgrade.tvcams.domain.model.StreamTransportType
import rs.belgrade.tvcams.domain.stream.FailoverDecision
import rs.belgrade.tvcams.domain.stream.SnapshotUrlBuilder
import rs.belgrade.tvcams.domain.stream.StreamFailoverTracker
import rs.belgrade.tvcams.domain.tv.OverlayMode
import rs.belgrade.tvcams.domain.tv.TvKeyCode
import rs.belgrade.tvcams.domain.tv.TvKeyPhase
import rs.belgrade.tvcams.domain.tv.TvKeyRouter
import rs.belgrade.tvcams.domain.tv.TvRemoteAction
import rs.belgrade.tvcams.domain.tv.TvStageReducer
import rs.belgrade.tvcams.domain.tv.TvStageState
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DomainSuiteTest {

    private fun sampleCamera(
        id: String,
        name: String,
        category: CameraCategory = CameraCategory.SQUARES,
    ): Camera = Camera(
        id = id,
        name = name,
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
    fun `whole-graph round trip of real Belgrade catalog is fixed-point identical`() {
        val catalogPath = System.getProperty("belgrade.catalog.path")
        assertNotNull(catalogPath, "System property belgrade.catalog.path must be set")
        val rawDiskJson = File(catalogPath).readText(Charsets.UTF_8)

        val decoded = CatalogJsonCodec.decode(rawDiskJson)
        assertEquals(1, decoded.schemaVersion)
        assertEquals(27, decoded.cameras.size)

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
    fun `3-tier catalog bootstrap prefers newer valid cache and guards against corrupt remote`() = runTest {
        val seedManifest = CatalogManifest(
            schemaVersion = 1,
            revision = 100L,
            updatedAt = "2026-11-17T20:00:00Z",
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
            cameras = listOf(sampleCamera("bg-1", "Slavija"), sampleCamera("bg-2", "Gazela")),
        )
        var nextRemoteResult: RemoteCatalogResult = RemoteCatalogResult.Updated(remoteManifest, "\"etag-200\"")
        val remoteClient = object : RemoteCatalogClient {
            override suspend fun fetchCatalog(ifNoneMatch: String?): RemoteCatalogResult = nextRemoteResult
        }

        val coordinator = CatalogBootstrapCoordinator(seedSource, cacheStore, remoteClient)

        // Cold boot uses seed
        val initial = coordinator.loadInitial()
        assertEquals(100L, initial.revision)

        // Sync promotes valid newer remote manifest
        val syncApplied = coordinator.syncRemote(currentRevision = initial.revision)
        assertTrue(syncApplied is CatalogSyncOutcome.Applied)
        assertEquals(200L, syncApplied.manifest.revision)
        assertEquals("\"etag-200\"", storedEtag)

        // Subsequent launch uses cached revision 200 over seed revision 100
        assertEquals(200L, coordinator.loadInitial().revision)

        // Corrupt/empty remote payload is rejected without clobbering cache
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

        // First error switches immediately to secondary SNAPSHOT_POLL stream
        val firstFailure = tracker.onTransportFailure()
        assertTrue(firstFailure is FailoverDecision.SwitchStreamImmediately)
        assertEquals(StreamTransportType.SNAPSHOT_POLL, firstFailure.nextStream.type)

        // Second error exhausts streams and schedules 3000ms retry on primary
        val secondFailure = tracker.onTransportFailure()
        assertTrue(secondFailure is FailoverDecision.RetryAfterBackoff)
        assertEquals(3_000L, secondFailure.delayMillis)
        assertEquals(StreamTransportType.HLS, secondFailure.nextStream.type)

        // Consecutive cycle failures escalate backoff up to 30_000ms ceiling
        tracker.onTransportFailure() // fallback
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
    fun `tv stage reducer handles pure camera toggle, favorites zapping, and layered back`() {
        val cameras = listOf(
            sampleCamera("c1", "Brankov most", CameraCategory.BRIDGES),
            sampleCamera("c2", "Slavija", CameraCategory.SQUARES),
            sampleCamera("c3", "Autokomanda", CameraCategory.SQUARES),
        )
        var state = TvStageState.initial(
            cameras = cameras,
            favoriteIds = setOf("c1", "c3"),
            initialCameraId = "c1",
        )
        assertEquals(OverlayMode.HUD_VISIBLE, state.overlayMode)
        assertEquals("c1", state.activeCamera?.id)

        // Short OK press hides HUD into Pure Camera Mode
        state = TvStageReducer.reduce(state, TvRemoteAction.ToggleInfoOrSelect).state
        assertEquals(OverlayMode.PURE_CAMERA, state.overlayMode)

        // Short OK press in Pure Camera Mode pins HUD visible (does not auto-hide)
        state = TvStageReducer.reduce(state, TvRemoteAction.ToggleInfoOrSelect).state
        assertEquals(OverlayMode.HUD_VISIBLE, state.overlayMode)
        assertTrue(state.hudPinned)
        state = TvStageReducer.reduce(state, TvRemoteAction.AutoHideTick).state
        assertEquals(OverlayMode.HUD_VISIBLE, state.overlayMode)

        // BACK from HUD returns to Pure Camera Mode without exiting app
        val backFromHud = TvStageReducer.reduce(state, TvRemoteAction.NavigateBack)
        assertFalse(backFromHud.shouldExitApp)
        state = backFromHud.state
        assertEquals(OverlayMode.PURE_CAMERA, state.overlayMode)

        // Switch zap scope to FAVORITES (c1, c3) and zap next -> skips c2 and lands on c3
        state = TvStageReducer.reduce(state, TvRemoteAction.ToggleZapScope).state
        assertEquals(ZapScope.FAVORITES, state.zapScope)

        state = TvStageReducer.reduce(state, TvRemoteAction.ZapNext).state
        assertEquals("c3", state.activeCamera?.id)
        assertEquals(OverlayMode.HUD_VISIBLE, state.overlayMode)
        assertFalse(state.hudPinned)

        // Auto-hide timer returns unpinned zap HUD to Pure Camera Mode
        state = TvStageReducer.reduce(state, TvRemoteAction.AutoHideTick).state
        assertEquals(OverlayMode.PURE_CAMERA, state.overlayMode)

        // BACK while already in Pure Camera Mode signals app exit
        val backFromPure = TvStageReducer.reduce(state, TvRemoteAction.NavigateBack)
        assertTrue(backFromPure.shouldExitApp)
        assertNull(backFromPure.state.noticeMessage)
    }

    @Test
    fun `tv key router distinguishes short press OK from hold OK favorite and respects quick strip focus`() {
        val router = TvKeyRouter()

        // Short OK in PURE_CAMERA: KeyDown(repeat=0) consumes without firing, KeyUp fires ToggleInfoOrSelect
        val downShort = router.route(TvKeyCode.DPAD_CENTER, TvKeyPhase.DOWN, repeatCount = 0, OverlayMode.PURE_CAMERA)
        assertTrue(downShort.consumed)
        assertNull(downShort.action)

        val upShort = router.route(TvKeyCode.DPAD_CENTER, TvKeyPhase.UP, repeatCount = 0, OverlayMode.PURE_CAMERA)
        assertTrue(upShort.consumed)
        assertEquals(TvRemoteAction.ToggleInfoOrSelect, upShort.action)

        // Hold OK in HUD_VISIBLE: KeyDown(repeat=1) fires ToggleFavoriteCurrent once; KeyUp does not toggle HUD
        router.route(TvKeyCode.DPAD_CENTER, TvKeyPhase.DOWN, repeatCount = 0, OverlayMode.HUD_VISIBLE)
        val downHold = router.route(TvKeyCode.DPAD_CENTER, TvKeyPhase.DOWN, repeatCount = 1, OverlayMode.HUD_VISIBLE)
        assertTrue(downHold.consumed)
        assertEquals(TvRemoteAction.ToggleFavoriteCurrent, downHold.action)

        val upAfterHold = router.route(TvKeyCode.DPAD_CENTER, TvKeyPhase.UP, repeatCount = 0, OverlayMode.HUD_VISIBLE)
        assertTrue(upAfterHold.consumed)
        assertNull(upAfterHold.action)

        // In QUICK_STRIP mode, D-pad arrows & OK pass through (consumed = false) so Compose FilterChips/Cards focus
        val stripRight = router.route(TvKeyCode.DPAD_RIGHT, TvKeyPhase.DOWN, repeatCount = 0, OverlayMode.QUICK_STRIP)
        assertFalse(stripRight.consumed)
        assertNull(stripRight.action)
    }
}
