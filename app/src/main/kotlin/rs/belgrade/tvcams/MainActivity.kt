package rs.belgrade.tvcams

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.darkColorScheme
import rs.belgrade.tvcams.ui.tv.CameraStageScreen
import rs.belgrade.tvcams.ui.tv.CameraStageViewModel

class MainActivity : ComponentActivity() {
    @OptIn(ExperimentalTvMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowInsetsControllerCompat(window, window.decorView).apply {
            hide(WindowInsetsCompat.Type.systemBars())
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }

        val appContainer = (application as BelgradeTvApplication).container

        setContent {
            MaterialTheme(colorScheme = darkColorScheme()) {
                val stageViewModel: CameraStageViewModel = viewModel(
                    factory = CameraStageViewModel.Factory(
                        catalogCoordinator = appContainer.catalogCoordinator,
                        favoritesRepository = appContainer.favoritesRepository,
                    ),
                )

                val state by stageViewModel.stageState.collectAsStateWithLifecycle()
                val streamCamera by stageViewModel.debouncedStreamCamera.collectAsStateWithLifecycle()
                val telemetry by stageViewModel.telemetry.collectAsStateWithLifecycle()

                LaunchedEffect(stageViewModel) {
                    stageViewModel.exitEvents.collect {
                        finish()
                    }
                }

                CameraStageScreen(
                    state = state,
                    streamCamera = streamCamera,
                    telemetry = telemetry,
                    httpClient = appContainer.httpClient,
                    onAction = stageViewModel::dispatch,
                    onTelemetryChanged = stageViewModel::updateTelemetry,
                )
            }
        }
    }
}
