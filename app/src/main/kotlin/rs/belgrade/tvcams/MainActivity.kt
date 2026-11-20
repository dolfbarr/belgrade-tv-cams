package rs.belgrade.tvcams

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.darkColorScheme
import rs.belgrade.tvcams.tvchannel.TvHomeChannelSynchronizer
import rs.belgrade.tvcams.ui.tv.CameraStageScreen
import rs.belgrade.tvcams.ui.tv.CameraStageViewModel

class MainActivity : ComponentActivity() {
    private var stageViewModel: CameraStageViewModel? = null

    @OptIn(ExperimentalTvMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowInsetsControllerCompat(window, window.decorView).apply {
            hide(WindowInsetsCompat.Type.systemBars())
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }

        val appContainer = (application as BelgradeTvApplication).container
        val deepLinkId = extractDeepLinkedCameraId(intent)

        val vm = ViewModelProvider(
            this,
            CameraStageViewModel.Factory(
                catalogCoordinator = appContainer.catalogCoordinator,
                favoritesRepository = appContainer.favoritesRepository,
                weatherRepository = appContainer.weatherRepository,
                airQualityRepository = appContainer.airQualityRepository,
                currencyRepository = appContainer.currencyRepository,
                homeChannelSynchronizer = appContainer.homeChannelSynchronizer,
                initialDeepLinkCameraId = deepLinkId,
            ),
        )[CameraStageViewModel::class.java]
        stageViewModel = vm

        setContent {
            MaterialTheme(colorScheme = darkColorScheme()) {
                val state by vm.stageState.collectAsStateWithLifecycle()
                val streamCamera by vm.debouncedStreamCamera.collectAsStateWithLifecycle()
                val telemetry by vm.telemetry.collectAsStateWithLifecycle()
                val streamRetryNonce by vm.streamRetryNonce.collectAsStateWithLifecycle()
                val currentWeather by vm.currentWeather.collectAsStateWithLifecycle()
                val currentAirQuality by vm.currentAirQuality.collectAsStateWithLifecycle()
                val currentCurrencyQuote by vm.currentCurrencyQuote.collectAsStateWithLifecycle()

                LaunchedEffect(vm) {
                    vm.exitEvents.collect {
                        finish()
                    }
                }

                CameraStageScreen(
                    state = state,
                    streamCamera = streamCamera,
                    telemetry = telemetry,
                    currentWeather = currentWeather,
                    currentAirQuality = currentAirQuality,
                    currentCurrencyQuote = currentCurrencyQuote,
                    httpClient = appContainer.mediaHttpClient,
                    streamResolver = appContainer.streamUrlResolver,
                    onAction = vm::dispatch,
                    onTelemetryChanged = vm::updateTelemetry,
                    streamRetryNonce = streamRetryNonce,
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        extractDeepLinkedCameraId(intent)?.let { targetId ->
            stageViewModel?.selectDeepLinkedCamera(targetId)
        }
    }

    private fun extractDeepLinkedCameraId(incoming: Intent?): String? {
        val data = incoming?.data ?: return null
        if (data.scheme != TvHomeChannelSynchronizer.DEEP_LINK_SCHEME ||
            data.host != TvHomeChannelSynchronizer.DEEP_LINK_HOST
        ) {
            return null
        }
        return data.lastPathSegment?.takeIf { it.isNotBlank() }
    }
}
