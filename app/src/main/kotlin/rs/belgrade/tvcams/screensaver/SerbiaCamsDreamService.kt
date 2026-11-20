package rs.belgrade.tvcams.screensaver

import android.service.dreams.DreamService
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.darkColorScheme
import rs.belgrade.tvcams.BelgradeTvApplication
import rs.belgrade.tvcams.ui.tv.CameraStageScreen
import rs.belgrade.tvcams.ui.tv.CameraStageViewModel

private class DreamComposeHostOwner :
    LifecycleOwner,
    ViewModelStoreOwner,
    SavedStateRegistryOwner {

    private val lifecycleRegistry = LifecycleRegistry(this)
    private val savedStateController = SavedStateRegistryController.create(this)
    override val viewModelStore: ViewModelStore = ViewModelStore()

    override val lifecycle: Lifecycle
        get() = lifecycleRegistry

    override val savedStateRegistry: SavedStateRegistry
        get() = savedStateController.savedStateRegistry

    fun onCreate() {
        savedStateController.performAttach()
        savedStateController.performRestore(null)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)
    }

    fun onStartAndResume() {
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_START)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_RESUME)
    }

    fun onPauseAndStop() {
        if (lifecycleRegistry.currentState.isAtLeast(Lifecycle.State.STARTED)) {
            lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_PAUSE)
            lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_STOP)
        }
    }

    fun onDestroy() {
        if (lifecycleRegistry.currentState != Lifecycle.State.DESTROYED) {
            lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_DESTROY)
        }
        viewModelStore.clear()
    }
}

class SerbiaCamsDreamService : DreamService() {
    private var hostOwner: DreamComposeHostOwner? = null

    @OptIn(ExperimentalTvMaterial3Api::class)
    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        isInteractive = true
        isFullscreen = true
        isScreenBright = true

        val owner = DreamComposeHostOwner().also {
            it.onCreate()
            hostOwner = it
        }

        window?.decorView?.let { decor ->
            decor.setViewTreeLifecycleOwner(owner)
            decor.setViewTreeViewModelStoreOwner(owner)
            decor.setViewTreeSavedStateRegistryOwner(owner)
        }

        val appContainer = (application as BelgradeTvApplication).container
        val stageViewModel = ViewModelProvider(
            owner,
            CameraStageViewModel.Factory(
                catalogCoordinator = appContainer.catalogCoordinator,
                favoritesRepository = appContainer.favoritesRepository,
                weatherRepository = appContainer.weatherRepository,
                airQualityRepository = appContainer.airQualityRepository,
                currencyRepository = appContainer.currencyRepository,
                isScreensaverMode = true,
            ),
        )[CameraStageViewModel::class.java]

        val composeView = ComposeView(this).apply {
            setViewTreeLifecycleOwner(owner)
            setViewTreeViewModelStoreOwner(owner)
            setViewTreeSavedStateRegistryOwner(owner)
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnDetachedFromWindow)
            setContent {
                MaterialTheme(colorScheme = darkColorScheme()) {
                    val state by stageViewModel.stageState.collectAsStateWithLifecycle(
                        lifecycleOwner = owner,
                    )
                    val streamCamera by stageViewModel.debouncedStreamCamera.collectAsStateWithLifecycle(
                        lifecycleOwner = owner,
                    )
                    val telemetry by stageViewModel.telemetry.collectAsStateWithLifecycle(
                        lifecycleOwner = owner,
                    )
                    val currentWeather by stageViewModel.currentWeather.collectAsStateWithLifecycle(
                        lifecycleOwner = owner,
                    )
                    val currentAirQuality by stageViewModel.currentAirQuality.collectAsStateWithLifecycle(
                        lifecycleOwner = owner,
                    )
                    val currentCurrencyQuote by stageViewModel.currentCurrencyQuote.collectAsStateWithLifecycle(
                        lifecycleOwner = owner,
                    )
                    val streamRetryNonce by stageViewModel.streamRetryNonce.collectAsStateWithLifecycle(
                        lifecycleOwner = owner,
                    )

                    LaunchedEffect(stageViewModel) {
                        stageViewModel.exitEvents.collect {
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
                        onAction = stageViewModel::dispatch,
                        onTelemetryChanged = stageViewModel::updateTelemetry,
                        streamRetryNonce = streamRetryNonce,
                        enableBackHandler = false,
                        isScreensaverMode = true,
                    )
                }
            }
        }

        setContentView(composeView)
    }

    override fun onDreamingStarted() {
        super.onDreamingStarted()
        hostOwner?.onStartAndResume()
    }

    override fun onDreamingStopped() {
        hostOwner?.onPauseAndStop()
        super.onDreamingStopped()
    }

    override fun onDetachedFromWindow() {
        hostOwner?.onDestroy()
        hostOwner = null
        super.onDetachedFromWindow()
    }
}
