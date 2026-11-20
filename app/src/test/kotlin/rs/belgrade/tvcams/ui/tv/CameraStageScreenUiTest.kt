package rs.belgrade.tvcams.ui.tv

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.requestFocus
import androidx.test.core.app.ApplicationProvider
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.darkColorScheme
import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import rs.belgrade.tvcams.data.catalog.AssetCatalogSeedSource
import rs.belgrade.tvcams.data.stream.OkHttpStreamUrlResolver
import rs.belgrade.tvcams.domain.favorites.QualityFilter
import rs.belgrade.tvcams.domain.model.Camera
import rs.belgrade.tvcams.domain.model.CameraCategory
import rs.belgrade.tvcams.domain.model.CameraCity
import rs.belgrade.tvcams.domain.model.CameraCountry
import rs.belgrade.tvcams.domain.model.GeoCoordinates
import rs.belgrade.tvcams.domain.model.StreamDescriptor
import rs.belgrade.tvcams.domain.model.StreamTransportType
import rs.belgrade.tvcams.domain.model.VideoQuality
import rs.belgrade.tvcams.domain.tv.CatalogBootstrapState
import rs.belgrade.tvcams.domain.tv.OverlayMode
import rs.belgrade.tvcams.domain.tv.TvRemoteAction
import rs.belgrade.tvcams.domain.tv.TvStageReducer
import rs.belgrade.tvcams.domain.tv.TvStageState
import rs.belgrade.tvcams.player.PlaybackTelemetry
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w960dp-h540dp-xhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@OptIn(ExperimentalTvMaterial3Api::class)
class CameraStageScreenUiTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val dummyClient = OkHttpClient()
    private val dummyResolver = OkHttpStreamUrlResolver(dummyClient, dummyClient)

    private fun buildSampleCameras(): List<Camera> = listOf(
        Camera(
            id = "bg-slavija",
            name = "Trg Slavija",
            country = CameraCountry.SERBIA,
            city = CameraCity.BELGRADE,
            quality = VideoQuality.FHD_1080P,
            timezone = "Europe/Belgrade",
            streets = listOf("Kralja Milana"),
            municipality = "Vračar",
            category = CameraCategory.SQUARES,
            direction = "North",
            posterUrl = "https://example.com/bg1.jpg",
            coordinates = GeoCoordinates(44.80, 20.46),
            streams = listOf(StreamDescriptor(StreamTransportType.HLS, "https://example.com/1.m3u8")),
        ),
        Camera(
            id = "us-nyc-ts",
            name = "Times Square 4K",
            country = CameraCountry.USA,
            city = CameraCity.NEW_YORK,
            quality = VideoQuality.UHD_4K,
            featured = true,
            timezone = "America/New_York",
            streets = listOf("Broadway"),
            municipality = "Manhattan",
            category = CameraCategory.SQUARES,
            direction = "South",
            posterUrl = "https://example.com/nyc.jpg",
            coordinates = GeoCoordinates(40.75, -73.98),
            streams = listOf(StreamDescriptor(StreamTransportType.YOUTUBE_LIVE, "ZxCeWQ3Lcig")),
        ),
        Camera(
            id = "jp-tyo-shibuya",
            name = "Shibuya Scramble Crossing",
            country = CameraCountry.JAPAN,
            city = CameraCity.TOKYO,
            quality = VideoQuality.UHD_4K,
            featured = true,
            timezone = "Asia/Tokyo",
            streets = listOf("Dogenzaka"),
            municipality = "Shibuya-ku",
            category = CameraCategory.SQUARES,
            direction = "East",
            posterUrl = "https://example.com/tyo.jpg",
            coordinates = GeoCoordinates(35.65, 139.70),
            streams = listOf(StreamDescriptor(StreamTransportType.YOUTUBE_LIVE, "5B4LmH4JbnY")),
        ),
    )

    @Test
    fun assetCatalogSeedSourceLoadsBundledV5WorldManifestCleanly() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val manifest = AssetCatalogSeedSource(context).readSeed()
        assertEquals(5, manifest.schemaVersion)
        assertEquals(301, manifest.cameras.size)
        // Verify legacy v1-v4 seed files were removed from APK assets
        val assetFiles = context.assets.list("").orEmpty().toSet()
        assertTrue(assetFiles.contains("seed_catalog.v5.json"))
        assertTrue(!assetFiles.contains("seed_catalog.v1.json"))
        assertTrue(!assetFiles.contains("seed_catalog.v4.json"))
    }

    @Test
    fun quickStripLocksFocusOnActiveCameraHidesGlobalCityRowAndNavigatesSettingsAndSearchDrawers() {
        val cams = buildSampleCameras()
        var uiState by mutableStateOf(
            TvStageState.initial(
                cameras = cams,
                initialCameraId = "jp-tyo-shibuya",
                initialOverlayMode = OverlayMode.QUICK_STRIP,
            ),
        )

        composeRule.setContent {
            MaterialTheme(colorScheme = darkColorScheme()) {
                CameraStageScreen(
                    state = uiState,
                    streamCamera = uiState.activeCamera,
                    telemetry = PlaybackTelemetry.PlayingLive(StreamTransportType.YOUTUBE_LIVE),
                    currentWeather = null,
                    currentAirQuality = null,
                    httpClient = dummyClient,
                    streamResolver = dummyResolver,
                    onAction = { action ->
                        uiState = TvStageReducer.reduce(uiState, action).state
                    },
                    onTelemetryChanged = {},
                    enableBackHandler = false,
                    enableLiveStreamPlayback = false,
                )
            }
        }

        composeRule.waitForIdle()

        // 1. Contextual city row is hidden on All World, and focus is locked on active Shibuya card
        composeRule.onNodeWithTag("strip_contextual_city_row").assertDoesNotExist()
        composeRule.onNodeWithTag("camera_card_jp-tyo-shibuya")
            .assertIsDisplayed()
            .assertIsFocused()

        // 2. Selecting Japan country chip via TV focus + DPAD_CENTER reveals contextual Tokyo city row
        composeRule.onNodeWithText("Japan (1)").performTvSelect()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("strip_contextual_city_row").assertIsDisplayed()
        composeRule.onNodeWithText("Tokyo (1)").assertIsDisplayed()

        // 3. Opening Settings drawer focuses Quality setting and cycles QualityFilter
        composeRule.onNodeWithTag("strip_open_settings_chip").performTvSelect()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("settings_side_drawer").assertIsDisplayed()
        composeRule.onNodeWithTag("setting_quality_chip")
            .assertIsDisplayed()
            .assertIsFocused()
            .performTvSelect()
        composeRule.waitForIdle()
        assertEquals(QualityFilter.HD_ONLY, uiState.qualityFilter)

        // Back from Settings returns to Quick Strip
        uiState = TvStageReducer.reduce(uiState, TvRemoteAction.NavigateBack).state
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("camera_quick_strip").assertIsDisplayed()

        // 4. Opening Search overlay focuses 'A' key, types 't' + 'i' + 'm', and selects Times Square
        composeRule.onNodeWithTag("strip_open_search_chip").performTvSelect()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("camera_search_overlay").assertIsDisplayed()
        composeRule.onNodeWithTag("search_key_A").assertIsFocused()

        for (token in listOf("T", "I", "M")) {
            composeRule.onNodeWithTag("search_key_bar")
                .performScrollToNode(hasTestTag("search_key_$token"))
            composeRule.onNodeWithTag("search_key_$token").performTvSelect()
        }
        composeRule.waitForIdle()
        assertEquals("tim", uiState.searchQuery)

        val firstCardBounds = composeRule.onNodeWithTag("camera_card_us-nyc-ts")
            .assertIsDisplayed()
            .fetchSemanticsNode()
            .boundsInRoot
        assertTrue(firstCardBounds.left >= 0f, "First search card must not clip off left edge (left=${firstCardBounds.left})")

        composeRule.onNodeWithTag("camera_card_us-nyc-ts")
            .performTvSelect()
        composeRule.waitForIdle()

        assertEquals("us-nyc-ts", uiState.activeCameraId)
        assertEquals(OverlayMode.HUD_VISIBLE, uiState.overlayMode)
        composeRule.onNodeWithTag("street_info_hud").assertIsDisplayed()
    }

    @Test
    fun catalogErrorScreenFocusesRetryButtonAndOfflineBannerRendersCountdown() {
        var retriedCatalog = false
        var uiState by mutableStateOf(
            TvStageState.initial(
                cameras = emptyList(),
                catalogBootstrapState = CatalogBootstrapState.Failed("Disk read error"),
            ),
        )

        composeRule.setContent {
            MaterialTheme(colorScheme = darkColorScheme()) {
                CameraStageScreen(
                    state = uiState,
                    streamCamera = uiState.activeCamera,
                    telemetry = PlaybackTelemetry.DegradedRetrying(6_000L, 2),
                    currentWeather = null,
                    currentAirQuality = null,
                    httpClient = dummyClient,
                    streamResolver = dummyResolver,
                    onAction = { action ->
                        if (action == TvRemoteAction.RetryCatalogLoad) {
                            retriedCatalog = true
                            uiState = TvStageState.initial(
                                cameras = buildSampleCameras(),
                                catalogBootstrapState = CatalogBootstrapState.Ready,
                            )
                        }
                    },
                    onTelemetryChanged = {},
                    enableBackHandler = false,
                    enableLiveStreamPlayback = false,
                )
            }
        }

        composeRule.waitForIdle()
        composeRule.onNodeWithTag("catalog_error_stage").assertIsDisplayed()
        composeRule.onNodeWithTag("catalog_error_retry_button")
            .assertIsFocused()
            .performTvSelect()
        composeRule.waitForIdle()

        assertTrue(retriedCatalog)
        composeRule.onNodeWithTag("stream_unavailable_banner").assertIsDisplayed()
        composeRule.onNodeWithText("Retrying automatically in 6s").assertIsDisplayed()
    }

    @Test
    fun countryFlagIconRendersAllFortyTwoVectorFlagsAndMonogramFallback() {
        val allIsos = listOf(
            "RS", "RU", "ES", "RO", "GB", "FR", "NL", "DE", "GE", "AM", "US", "KR", "JP",
            "AT", "CH", "IT", "BE", "IE", "PL", "UA", "CZ", "HU", "BG", "SE", "NO", "FI", "DK", "CA",
            "HR", "TR", "IS", "PT", "TW", "TH", "AU", "SG", "HK", "AE", "IL", "BR", "SX", "MX",
            "ZZ",
        )
        composeRule.setContent {
            MaterialTheme(colorScheme = darkColorScheme()) {
                androidx.compose.foundation.layout.Row {
                    for (iso in allIsos) {
                        CountryFlagIcon(
                            country = CameraCountry(iso),
                            isoOverride = iso,
                        )
                    }
                }
            }
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithText("ZZ").assertIsDisplayed()
    }

    @OptIn(ExperimentalTestApi::class)
    private fun SemanticsNodeInteraction.performTvSelect(): SemanticsNodeInteraction {
        requestFocus()
        return performKeyInput { pressKey(Key.DirectionCenter) }
    }
}
