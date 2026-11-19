@file:OptIn(ExperimentalTvMaterial3Api::class)

package rs.belgrade.tvcams.ui.tv

import android.view.KeyEvent
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Button
import androidx.tv.material3.Card
import androidx.tv.material3.CardDefaults
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.FilterChip
import androidx.tv.material3.FilterChipDefaults
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import coil3.compose.AsyncImage
import coil3.network.NetworkHeaders
import coil3.network.httpHeaders
import coil3.request.ImageRequest
import coil3.request.allowRgb565
import coil3.size.Size
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import okhttp3.OkHttpClient
import rs.belgrade.tvcams.R
import rs.belgrade.tvcams.data.stream.OkHttpStreamUrlResolver
import rs.belgrade.tvcams.domain.airquality.AirQualityBand
import rs.belgrade.tvcams.domain.airquality.CurrentAirQuality
import rs.belgrade.tvcams.domain.favorites.InfoPanelPosition
import rs.belgrade.tvcams.domain.favorites.PerformanceMode
import rs.belgrade.tvcams.domain.favorites.QualityFilter
import rs.belgrade.tvcams.domain.favorites.TransportFilter
import rs.belgrade.tvcams.domain.favorites.ZapScope
import rs.belgrade.tvcams.domain.model.Camera
import rs.belgrade.tvcams.domain.model.CameraCategory
import rs.belgrade.tvcams.domain.model.CameraCountry
import rs.belgrade.tvcams.domain.model.StreamTransportType
import rs.belgrade.tvcams.domain.model.VideoQuality
import rs.belgrade.tvcams.domain.oled.PixelOrbitCalculator
import rs.belgrade.tvcams.domain.oled.PixelOrbitOffset
import rs.belgrade.tvcams.domain.solar.SolarPhaseCalculator
import rs.belgrade.tvcams.domain.solar.SolarStatus
import rs.belgrade.tvcams.domain.solar.SolarWindow
import rs.belgrade.tvcams.domain.time.CameraClockFormatter
import rs.belgrade.tvcams.domain.time.DualClockReading
import rs.belgrade.tvcams.domain.tv.CatalogBootstrapState
import rs.belgrade.tvcams.domain.tv.OverlayMode
import rs.belgrade.tvcams.domain.tv.StripFilter
import rs.belgrade.tvcams.domain.tv.TvKeyCode
import rs.belgrade.tvcams.domain.tv.TvKeyPhase
import rs.belgrade.tvcams.domain.tv.TvKeyRouter
import rs.belgrade.tvcams.domain.tv.TvRemoteAction
import rs.belgrade.tvcams.domain.tv.TvStageState
import rs.belgrade.tvcams.domain.weather.CurrentWeather
import rs.belgrade.tvcams.player.DualTransportCameraSurface
import rs.belgrade.tvcams.player.PlaybackTelemetry
import java.time.Instant

private val TvOverscanHorizontal = 48.dp
private val TvOverscanVertical = 27.dp

private val StageScrimTop = Color(0x00000000)
private val HudCardSurface = Color(0xE0080D1A)
private val DrawerSurface = Color(0xF2080D1A)
private val PillSurface = Color(0xCC0F172A)
private val PillBorder = Color(0x3394A3B8)
private val AccentSky = Color(0xFF38BDF8)
private val AccentAmber = Color(0xFFFBBF24)
private val AccentLive = Color(0xFFEF4444)
private val AqiGood = Color(0xFF22C55E)
private val AqiFair = Color(0xFF84CC16)
private val AqiModerate = Color(0xFFFBBF24)
private val AqiPoor = Color(0xFFF97316)
private val AqiVeryPoor = Color(0xFFEF4444)
private val AqiExtreme = Color(0xFFA855F7)
private val TextPrimary = Color(0xFFF8FAFC)
private val TextSecondary = Color(0xFFCBD5E1)
private val TextMuted = Color(0xFF94A3B8)

private val TabularStyle = TextStyle(fontFeatureSettings = "tnum")

private val SEARCH_KEY_TOKENS: List<String> = buildList {
    for (ch in 'A'..'Z') add(ch.toString())
    for (ch in '0'..'9') add(ch.toString())
}

@Composable
fun CameraStageScreen(
    state: TvStageState,
    streamCamera: Camera?,
    telemetry: PlaybackTelemetry,
    currentWeather: CurrentWeather?,
    currentAirQuality: CurrentAirQuality?,
    httpClient: OkHttpClient,
    streamResolver: OkHttpStreamUrlResolver,
    onAction: (TvRemoteAction) -> Unit,
    onTelemetryChanged: (PlaybackTelemetry) -> Unit,
    streamRetryNonce: Int = 0,
    enableBackHandler: Boolean = true,
    isScreensaverMode: Boolean = false,
    enableLiveStreamPlayback: Boolean = true,
) {
    when (val boot = state.catalogBootstrapState) {
        CatalogBootstrapState.Loading -> {
            CatalogLoadingStage()
            return
        }
        is CatalogBootstrapState.Failed -> {
            CatalogErrorStage(
                reason = boot.reason,
                onRetry = { onAction(TvRemoteAction.RetryCatalogLoad) },
            )
            return
        }
        CatalogBootstrapState.Ready -> Unit
    }

    val stageFocusRequester = remember { FocusRequester() }
    val keyRouter = remember { TvKeyRouter() }
    val activeCam = state.activeCamera
    val timezoneId = activeCam?.timezone ?: "Europe/Belgrade"
    val effectiveRotate = state.autoRotateInterval.effectiveFor(isScreensaverMode)
    val isModalOrStripOpen = state.overlayMode == OverlayMode.QUICK_STRIP ||
        state.overlayMode == OverlayMode.SETTINGS ||
        state.overlayMode == OverlayMode.SEARCH

    var showConnectingBanner by remember { mutableStateOf(false) }
    LaunchedEffect(telemetry) {
        if (telemetry is PlaybackTelemetry.Connecting) {
            delay(350L)
            showConnectingBanner = true
        } else {
            showConnectingBanner = false
        }
    }

    val clockState = rememberDualCameraClock(
        timezoneId = timezoneId,
        enabled = state.showClockOverlay,
    )
    val solarWindows = currentWeather?.solarWindows.orEmpty()
    val solarState = rememberSolarStatus(
        timezoneId = timezoneId,
        windows = solarWindows,
        enabled = state.showSolarOverlay,
    )
    val oledOrbit = rememberPixelOrbitOffset(enabled = state.oledProtectionEnabled).value

    BackHandler(enabled = enableBackHandler) {
        onAction(TvRemoteAction.NavigateBack)
    }

    LaunchedEffect(state.overlayMode) {
        if (!isModalOrStripOpen) {
            runCatching { stageFocusRequester.requestFocus() }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .testTag("camera_stage_root")
            .onPreviewKeyEvent { keyEvent ->
                val nativeCode = keyEvent.nativeKeyEvent.keyCode
                if (!enableBackHandler && nativeCode == KeyEvent.KEYCODE_BACK) {
                    if (keyEvent.type == KeyEventType.KeyUp) {
                        onAction(TvRemoteAction.NavigateBack)
                    }
                    return@onPreviewKeyEvent true
                }

                // Physical / Bluetooth keyboard typing inside Search overlay
                if (state.overlayMode == OverlayMode.SEARCH && keyEvent.type == KeyEventType.KeyDown) {
                    if (nativeCode == KeyEvent.KEYCODE_DEL) {
                        onAction(TvRemoteAction.BackspaceSearchQuery)
                        return@onPreviewKeyEvent true
                    }
                    val unicode = keyEvent.nativeKeyEvent.unicodeChar
                    if (unicode > 0) {
                        val ch = unicode.toChar()
                        if (ch.isLetterOrDigit() || ch == ' ' || ch == '-') {
                            onAction(TvRemoteAction.AppendSearchChar(ch.toString()))
                            return@onPreviewKeyEvent true
                        }
                    }
                }

                val phase = when (keyEvent.type) {
                    KeyEventType.KeyDown -> TvKeyPhase.DOWN
                    KeyEventType.KeyUp -> TvKeyPhase.UP
                    else -> return@onPreviewKeyEvent false
                }
                val decision = keyRouter.route(
                    keyCode = mapAndroidKeyCode(nativeCode),
                    phase = phase,
                    repeatCount = keyEvent.nativeKeyEvent.repeatCount,
                    overlayMode = state.overlayMode,
                )
                if (decision.action == TvRemoteAction.ToggleInfoOrSelect &&
                    state.pendingChannelDigits.isEmpty() &&
                    telemetry is PlaybackTelemetry.DegradedRetrying &&
                    !isModalOrStripOpen
                ) {
                    onAction(TvRemoteAction.RetryActiveStream)
                } else {
                    decision.action?.let(onAction)
                }
                decision.consumed
            }
            .focusRequester(stageFocusRequester)
            .focusable(),
    ) {
        // Layer 0: Adaptive 1920x1080 / low-spec stream surface with poster underlay & OLED pixel orbit
        if (enableLiveStreamPlayback) {
            DualTransportCameraSurface(
                camera = streamCamera ?: activeCam,
                httpClient = httpClient,
                streamResolver = streamResolver,
                performanceMode = state.performanceMode,
                oledOrbitOffset = oledOrbit,
                onTelemetryChanged = onTelemetryChanged,
                manualRetryNonce = streamRetryNonce,
                modifier = Modifier.fillMaxSize(),
            )
        }

        val panelPosition = state.infoPanelPosition
        val statusBoxAlignment = if (panelPosition == InfoPanelPosition.TOP_RIGHT) {
            Alignment.TopStart
        } else {
            Alignment.TopEnd
        }
        val statusHorizontalAlignment = if (panelPosition == InfoPanelPosition.TOP_RIGHT) {
            Alignment.Start
        } else {
            Alignment.End
        }

        // Layer 1: Non-blocking top status / channel jump / spinner / notice badges inside TV safe area
        TopStatusLayer(
            pendingChannelDigits = state.pendingChannelDigits,
            pendingChannelPreview = state.pendingChannelPreviewCamera,
            zapPlaylistSize = state.zapPlaylist.size,
            noticeMessage = state.noticeMessage,
            telemetry = telemetry,
            showConnectingBanner = showConnectingBanner,
            isSyncingRemoteCatalog = state.isSyncingRemoteCatalog,
            horizontalAlignment = statusHorizontalAlignment,
            modifier = Modifier
                .align(statusBoxAlignment)
                .padding(horizontal = TvOverscanHorizontal, vertical = TvOverscanVertical)
                .offset {
                    IntOffset(
                        x = oledOrbit.dxDp.dp.roundToPx(),
                        y = oledOrbit.dyDp.dp.roundToPx(),
                    )
                },
        )

        // Layer 1B: Persistent Stream Offline / Reconnecting Card when all stream candidates fail
        if (telemetry is PlaybackTelemetry.DegradedRetrying && !isModalOrStripOpen) {
            StreamUnavailableBanner(
                retryDelayMs = telemetry.retryDelayMs,
                modifier = Modifier
                    .align(Alignment.Center)
                    .offset {
                        IntOffset(
                            x = oledOrbit.dxDp.dp.roundToPx(),
                            y = oledOrbit.dyDp.dp.roundToPx(),
                        )
                    },
            )
        }

        // Layer 2: Configurable Info Panel (default Bottom Center) with Current View Name + Ambient Telemetry
        val cameraName = activeCam?.name
        val cityLabel = activeCam?.resolvedCityLabel
        val country = activeCam?.resolvedCountry
        val countryIso = activeCam?.resolvedCountryIso
        val countryFallbackLabel = activeCam?.resolvedCountryLabel
        val clockReading = if (state.showClockOverlay) clockState.value else null
        val weatherModel = if (state.showWeatherOverlay) currentWeather else null
        val aqiModel = if (state.showAirQualityOverlay) currentAirQuality else null
        val solarModel = if (state.showSolarOverlay) solarState.value else null
        val showAmbientBar = !cameraName.isNullOrBlank() ||
            clockReading != null ||
            weatherModel != null ||
            aqiModel != null ||
            solarModel != null

        val panelBoxAlignment = when (panelPosition) {
            InfoPanelPosition.BOTTOM_CENTER -> Alignment.BottomCenter
            InfoPanelPosition.BOTTOM_RIGHT -> Alignment.BottomEnd
            InfoPanelPosition.BOTTOM_LEFT -> Alignment.BottomStart
            InfoPanelPosition.TOP_LEFT -> Alignment.TopStart
            InfoPanelPosition.TOP_RIGHT -> Alignment.TopEnd
        }
        val panelColumnAlignment = when (panelPosition) {
            InfoPanelPosition.BOTTOM_CENTER -> Alignment.CenterHorizontally
            InfoPanelPosition.BOTTOM_RIGHT,
            InfoPanelPosition.TOP_RIGHT,
            -> Alignment.End
            InfoPanelPosition.BOTTOM_LEFT,
            InfoPanelPosition.TOP_LEFT,
            -> Alignment.Start
        }

        if (!isModalOrStripOpen) {
            val panelPadding = if (panelPosition.isTop) {
                Modifier.padding(
                    start = TvOverscanHorizontal,
                    end = TvOverscanHorizontal,
                    top = TvOverscanVertical,
                )
            } else {
                Modifier.padding(
                    start = TvOverscanHorizontal,
                    end = TvOverscanHorizontal,
                    bottom = TvOverscanVertical,
                )
            }
            Column(
                modifier = Modifier
                    .align(panelBoxAlignment)
                    .then(panelPadding)
                    .offset {
                        IntOffset(
                            x = oledOrbit.dxDp.dp.roundToPx(),
                            y = oledOrbit.dyDp.dp.roundToPx(),
                        )
                    },
                horizontalAlignment = panelColumnAlignment,
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                if (panelPosition.isTop && showAmbientBar) {
                    AmbientClockWeatherBar(
                        cameraName = cameraName,
                        cityLabel = cityLabel,
                        country = country,
                        countryIso = countryIso,
                        countryFallbackLabel = countryFallbackLabel,
                        clockReading = clockReading,
                        weather = weatherModel,
                        airQuality = aqiModel,
                        solarStatus = solarModel,
                        horizontalAlignment = panelColumnAlignment,
                    )
                }

                AnimatedVisibility(
                    visible = state.overlayMode == OverlayMode.HUD_VISIBLE && activeCam != null,
                    enter = fadeIn(),
                    exit = fadeOut(),
                ) {
                    if (activeCam != null) {
                        StreetInfoHud(
                            camera = activeCam,
                            positionOneBased = state.activeZapPositionOneBased,
                            totalInScope = state.zapPlaylist.size,
                            scopeLabel = state.zapScope.displayLabel(),
                            isFavorite = state.isCurrentFavorite,
                            autoRotateLabel = effectiveRotate.takeIf { it.enabled }?.displayLabel(),
                            telemetry = telemetry,
                        )
                    }
                }

                if (!panelPosition.isTop && showAmbientBar) {
                    AmbientClockWeatherBar(
                        cameraName = cameraName,
                        cityLabel = cityLabel,
                        country = country,
                        countryIso = countryIso,
                        countryFallbackLabel = countryFallbackLabel,
                        clockReading = clockReading,
                        weather = weatherModel,
                        airQuality = aqiModel,
                        solarStatus = solarModel,
                        horizontalAlignment = panelColumnAlignment,
                    )
                }
            }
        }

        // Layer 3: Compact 2-Row Quick Strip Camera Carousel (DPAD_DOWN)
        AnimatedVisibility(
            visible = state.overlayMode == OverlayMode.QUICK_STRIP,
            enter = slideInVertically { it } + fadeIn(),
            exit = slideOutVertically { it } + fadeOut(),
            modifier = Modifier.align(Alignment.BottomCenter),
        ) {
            CameraQuickStrip(
                state = state,
                onAction = onAction,
            )
        }

        // Layer 4: Dedicated Settings Side-Drawer (MENU key or Settings chip)
        AnimatedVisibility(
            visible = state.overlayMode == OverlayMode.SETTINGS,
            enter = slideInHorizontally { it } + fadeIn(),
            exit = slideOutHorizontally { it } + fadeOut(),
            modifier = Modifier.align(Alignment.CenterEnd),
        ) {
            SettingsSideDrawer(
                state = state,
                isScreensaverMode = isScreensaverMode,
                onAction = onAction,
            )
        }

        // Layer 5: Fast D-Pad & Keyboard Camera Search Overlay (SEARCH key or Search chip)
        AnimatedVisibility(
            visible = state.overlayMode == OverlayMode.SEARCH,
            enter = slideInVertically { it } + fadeIn(),
            exit = slideOutVertically { it } + fadeOut(),
            modifier = Modifier.align(Alignment.BottomCenter),
        ) {
            CameraSearchOverlay(
                state = state,
                onAction = onAction,
            )
        }
    }
}

@Composable
private fun rememberDualCameraClock(
    timezoneId: String,
    enabled: Boolean,
): State<DualClockReading?> = produceState(
    initialValue = if (enabled) CameraClockFormatter.formatDualClock(Instant.now(), timezoneId) else null,
    key1 = timezoneId,
    key2 = enabled,
) {
    if (!enabled) {
        value = null
        return@produceState
    }
    while (isActive) {
        value = CameraClockFormatter.formatDualClock(Instant.now(), timezoneId)
        val remainder = System.currentTimeMillis() % 60_000L
        val sleepMs = (60_000L - remainder).coerceIn(500L, 60_000L)
        delay(sleepMs)
    }
}

@Composable
private fun rememberSolarStatus(
    timezoneId: String,
    windows: List<SolarWindow>,
    enabled: Boolean,
): State<SolarStatus?> = produceState(
    initialValue = if (enabled) {
        SolarPhaseCalculator.evaluate(Instant.now(), timezoneId, windows)
    } else {
        null
    },
    key1 = timezoneId,
    key2 = windows,
    key3 = enabled,
) {
    if (!enabled || windows.isEmpty()) {
        value = null
        return@produceState
    }
    while (isActive) {
        value = SolarPhaseCalculator.evaluate(Instant.now(), timezoneId, windows)
        delay(60_000L)
    }
}

@Composable
private fun rememberPixelOrbitOffset(
    enabled: Boolean,
): State<PixelOrbitOffset> = produceState(
    initialValue = PixelOrbitCalculator.computeOffset(
        epochMillis = System.currentTimeMillis(),
        enabled = enabled,
    ),
    key1 = enabled,
) {
    if (!enabled) {
        value = PixelOrbitCalculator.computeOffset(epochMillis = 0L, enabled = false)
        return@produceState
    }
    while (isActive) {
        value = PixelOrbitCalculator.computeOffset(
            epochMillis = System.currentTimeMillis(),
            enabled = true,
        )
        delay(PixelOrbitCalculator.DEFAULT_STEP_INTERVAL_MS)
    }
}

@Composable
private fun AmbientClockWeatherBar(
    cameraName: String?,
    cityLabel: String?,
    country: CameraCountry?,
    countryIso: String?,
    countryFallbackLabel: String?,
    clockReading: DualClockReading?,
    weather: CurrentWeather?,
    airQuality: CurrentAirQuality?,
    solarStatus: SolarStatus?,
    horizontalAlignment: Alignment.Horizontal = Alignment.CenterHorizontally,
    modifier: Modifier = Modifier,
) {
    val hasTelemetry = clockReading != null ||
        weather != null ||
        airQuality != null ||
        solarStatus != null

    Column(
        modifier = modifier
            .widthIn(max = 840.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(PillSurface)
            .border(1.dp, PillBorder, RoundedCornerShape(10.dp))
            .padding(horizontal = 16.dp, vertical = 9.dp),
        horizontalAlignment = horizontalAlignment,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        if (!cameraName.isNullOrBlank()) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = cameraName,
                    color = TextPrimary,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.widthIn(max = 560.dp),
                )
                val locationTag = when {
                    !cityLabel.isNullOrBlank() -> cityLabel
                    country == null && !countryFallbackLabel.isNullOrBlank() -> countryFallbackLabel
                    else -> null
                }
                if (locationTag != null || country != null) {
                    SeparatorDot()
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        if (country != null) {
                            CountryFlagIcon(
                                country = country,
                                isoOverride = countryIso,
                                width = 18.dp,
                                height = 12.dp,
                            )
                        }
                        if (locationTag != null) {
                            Text(
                                text = locationTag,
                                color = AccentSky,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Medium,
                                maxLines = 1,
                            )
                        }
                    }
                }
            }
        }

        if (hasTelemetry) {
            var hasPreceding = false
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                if (clockReading != null) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(5.dp),
                    ) {
                        Text(
                            text = clockReading.cameraTime,
                            style = TabularStyle,
                            color = TextPrimary,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                        )
                        Text(
                            text = clockReading.cameraUtcOffsetLabel,
                            style = TabularStyle,
                            color = AccentSky,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                        )
                        if (!clockReading.isSameZoneTime) {
                            Text(
                                text = "· Local ${clockReading.localTime}",
                                style = TabularStyle,
                                color = TextSecondary,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Medium,
                            )
                        }
                    }
                    hasPreceding = true
                }

                if (weather != null) {
                    if (hasPreceding) SeparatorDot()
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        WeatherConditionIcon(
                            condition = weather.condition,
                            isDay = weather.isDay,
                            size = 17.dp,
                        )
                        Text(
                            text = weather.compactMetricsLabel(),
                            style = TabularStyle,
                            color = TextSecondary,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium,
                            maxLines = 1,
                        )
                    }
                    hasPreceding = true
                }

                if (airQuality != null) {
                    if (hasPreceding) SeparatorDot()
                    val bandColor = aqiBandColor(airQuality.band)
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Box(
                            modifier = Modifier
                                .width(8.dp)
                                .height(8.dp)
                                .clip(RoundedCornerShape(4.dp))
                                .background(bandColor),
                        )
                        Text(
                            text = airQuality.compactLabel(),
                            style = TabularStyle,
                            color = TextPrimary,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium,
                            maxLines = 1,
                        )
                    }
                    hasPreceding = true
                }

                if (solarStatus != null) {
                    if (hasPreceding) SeparatorDot()
                    val isGolden = solarStatus.isGoldenHour
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(5.dp),
                    ) {
                        SolarPhaseIcon(
                            phase = solarStatus.phase,
                            size = 17.dp,
                        )
                        Text(
                            text = solarStatus.targetTimeLabel,
                            style = TabularStyle,
                            color = if (isGolden) AccentAmber else TextSecondary,
                            fontSize = 13.sp,
                            fontWeight = if (isGolden) FontWeight.SemiBold else FontWeight.Medium,
                            maxLines = 1,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SeparatorDot() {
    Box(
        modifier = Modifier
            .width(4.dp)
            .height(4.dp)
            .clip(RoundedCornerShape(2.dp))
            .background(TextMuted),
    )
}

private fun aqiBandColor(band: AirQualityBand): Color = when (band) {
    AirQualityBand.GOOD -> AqiGood
    AirQualityBand.FAIR -> AqiFair
    AirQualityBand.MODERATE -> AqiModerate
    AirQualityBand.POOR -> AqiPoor
    AirQualityBand.VERY_POOR -> AqiVeryPoor
    AirQualityBand.EXTREMELY_POOR -> AqiExtreme
}

private fun mapAndroidKeyCode(keyCode: Int): TvKeyCode = when (keyCode) {
    KeyEvent.KEYCODE_DPAD_CENTER,
    KeyEvent.KEYCODE_ENTER,
    KeyEvent.KEYCODE_NUMPAD_ENTER,
    -> TvKeyCode.DPAD_CENTER
    KeyEvent.KEYCODE_DPAD_LEFT -> TvKeyCode.DPAD_LEFT
    KeyEvent.KEYCODE_DPAD_RIGHT -> TvKeyCode.DPAD_RIGHT
    KeyEvent.KEYCODE_DPAD_UP -> TvKeyCode.DPAD_UP
    KeyEvent.KEYCODE_DPAD_DOWN -> TvKeyCode.DPAD_DOWN
    KeyEvent.KEYCODE_CHANNEL_UP -> TvKeyCode.CHANNEL_UP
    KeyEvent.KEYCODE_CHANNEL_DOWN -> TvKeyCode.CHANNEL_DOWN
    KeyEvent.KEYCODE_INFO -> TvKeyCode.INFO
    KeyEvent.KEYCODE_BOOKMARK -> TvKeyCode.BOOKMARK
    KeyEvent.KEYCODE_MENU,
    KeyEvent.KEYCODE_GUIDE,
    -> TvKeyCode.MENU
    KeyEvent.KEYCODE_SEARCH -> TvKeyCode.SEARCH
    KeyEvent.KEYCODE_BACK -> TvKeyCode.BACK
    KeyEvent.KEYCODE_0, KeyEvent.KEYCODE_NUMPAD_0 -> TvKeyCode.DIGIT_0
    KeyEvent.KEYCODE_1, KeyEvent.KEYCODE_NUMPAD_1 -> TvKeyCode.DIGIT_1
    KeyEvent.KEYCODE_2, KeyEvent.KEYCODE_NUMPAD_2 -> TvKeyCode.DIGIT_2
    KeyEvent.KEYCODE_3, KeyEvent.KEYCODE_NUMPAD_3 -> TvKeyCode.DIGIT_3
    KeyEvent.KEYCODE_4, KeyEvent.KEYCODE_NUMPAD_4 -> TvKeyCode.DIGIT_4
    KeyEvent.KEYCODE_5, KeyEvent.KEYCODE_NUMPAD_5 -> TvKeyCode.DIGIT_5
    KeyEvent.KEYCODE_6, KeyEvent.KEYCODE_NUMPAD_6 -> TvKeyCode.DIGIT_6
    KeyEvent.KEYCODE_7, KeyEvent.KEYCODE_NUMPAD_7 -> TvKeyCode.DIGIT_7
    KeyEvent.KEYCODE_8, KeyEvent.KEYCODE_NUMPAD_8 -> TvKeyCode.DIGIT_8
    KeyEvent.KEYCODE_9, KeyEvent.KEYCODE_NUMPAD_9 -> TvKeyCode.DIGIT_9
    else -> TvKeyCode.OTHER
}

@Composable
private fun TopStatusLayer(
    pendingChannelDigits: String,
    pendingChannelPreview: Camera?,
    zapPlaylistSize: Int,
    noticeMessage: String?,
    telemetry: PlaybackTelemetry,
    showConnectingBanner: Boolean,
    isSyncingRemoteCatalog: Boolean,
    horizontalAlignment: Alignment.Horizontal = Alignment.End,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
        horizontalAlignment = horizontalAlignment,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (pendingChannelDigits.isNotEmpty()) {
            val formattedCh = pendingChannelDigits.padStart(2, '0')
            val label = if (pendingChannelPreview != null) {
                "CH $formattedCh · ${pendingChannelPreview.name}"
            } else {
                "CH $pendingChannelDigits · Out of range (1–$zapPlaylistSize)"
            }
            StatusPill(
                text = label,
                accentColor = if (pendingChannelPreview != null) AccentSky else AccentAmber,
            )
        }
        if (noticeMessage != null) {
            StatusPill(
                text = noticeMessage,
                accentColor = AccentSky,
            )
        }
        if (isSyncingRemoteCatalog) {
            StatusPill(
                text = stringResource(R.string.status_syncing_catalog),
                accentColor = AccentSky,
                showSpinner = true,
            )
        }
        when {
            telemetry is PlaybackTelemetry.SwitchingFallback -> {
                StatusPill(
                    text = stringResource(R.string.status_switching_fallback),
                    accentColor = AccentAmber,
                    showSpinner = true,
                )
            }
            telemetry is PlaybackTelemetry.Connecting && showConnectingBanner -> {
                StatusPill(
                    text = stringResource(R.string.status_connecting),
                    accentColor = AccentSky,
                    showSpinner = true,
                )
            }
            telemetry is PlaybackTelemetry.DegradedRetrying -> {
                StatusPill(
                    text = stringResource(R.string.status_retrying),
                    accentColor = AccentAmber,
                    showSpinner = true,
                )
            }
        }
    }
}

@Composable
private fun StatusPill(
    text: String,
    accentColor: Color,
    showSpinner: Boolean = false,
) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(10.dp))
            .background(PillSurface)
            .border(1.dp, PillBorder, RoundedCornerShape(10.dp))
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        if (showSpinner) {
            LoadingSpinnerIcon(
                size = 14.dp,
                color = accentColor,
            )
        } else {
            Box(
                modifier = Modifier
                    .width(8.dp)
                    .height(8.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(accentColor),
            )
        }
        Text(
            text = text,
            style = TabularStyle,
            color = TextPrimary,
            fontSize = 15.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.widthIn(max = 560.dp),
        )
    }
}

@Composable
private fun CatalogLoadingStage() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF060A14))
            .padding(horizontal = TvOverscanHorizontal, vertical = TvOverscanVertical)
            .testTag("catalog_loading_stage"),
    ) {
        Column(
            modifier = Modifier.align(Alignment.Center),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            WorldGlobeMarkIcon(size = 52.dp)
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                LoadingSpinnerIcon(size = 18.dp, color = AccentSky)
                Text(
                    text = stringResource(R.string.catalog_loading_heading),
                    color = TextPrimary,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }

        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .width(520.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(Color(0x990F172A))
                .border(1.dp, PillBorder, RoundedCornerShape(10.dp))
                .padding(horizontal = 18.dp, vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Box(
                modifier = Modifier
                    .width(280.dp)
                    .height(14.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(Color(0x3394A3B8)),
            )
            Box(
                modifier = Modifier
                    .width(360.dp)
                    .height(12.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(Color(0x2494A3B8)),
            )
        }
    }
}

@Composable
private fun CatalogErrorStage(
    reason: String,
    onRetry: () -> Unit,
) {
    val retryFocusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        runCatching { retryFocusRequester.requestFocus() }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF060A14))
            .padding(horizontal = TvOverscanHorizontal, vertical = TvOverscanVertical)
            .testTag("catalog_error_stage"),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = 560.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(HudCardSurface)
                .border(1.dp, Color(0x66FBBF24), RoundedCornerShape(16.dp))
                .padding(horizontal = 28.dp, vertical = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            CameraSignalOffIcon(size = 38.dp)
            Text(
                text = stringResource(R.string.catalog_error_heading),
                color = TextPrimary,
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = reason,
                color = TextSecondary,
                fontSize = 14.sp,
            )
            Spacer(modifier = Modifier.height(4.dp))
            Button(
                onClick = onRetry,
                modifier = Modifier
                    .focusRequester(retryFocusRequester)
                    .testTag("catalog_error_retry_button"),
            ) {
                Text(text = stringResource(R.string.catalog_error_retry))
            }
        }
    }
}

@Composable
private fun StreamUnavailableBanner(
    retryDelayMs: Long,
    modifier: Modifier = Modifier,
) {
    val seconds = (retryDelayMs / 1000L).coerceAtLeast(1L)
    Column(
        modifier = modifier
            .widthIn(max = 520.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(Color(0xEB080D1A))
            .border(1.dp, Color(0x66FBBF24), RoundedCornerShape(14.dp))
            .padding(horizontal = 22.dp, vertical = 16.dp)
            .testTag("stream_unavailable_banner"),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            CameraSignalOffIcon(size = 22.dp)
            Text(
                text = stringResource(R.string.stream_offline_heading),
                color = TextPrimary,
                fontSize = 17.sp,
                fontWeight = FontWeight.SemiBold,
            )
        }
        Text(
            text = "Retrying automatically in ${seconds}s",
            style = TabularStyle,
            color = AccentAmber,
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
        )
        Text(
            text = "OK: Retry now · ▶: Next camera · ▼: Browse cameras",
            color = TextSecondary,
            fontSize = 12.sp,
        )
    }
}

@Composable
private fun StreetInfoHud(
    camera: Camera,
    positionOneBased: Int,
    totalInScope: Int,
    scopeLabel: String,
    isFavorite: Boolean,
    autoRotateLabel: String?,
    telemetry: PlaybackTelemetry,
) {
    Column(
        modifier = Modifier
            .widthIn(min = 480.dp, max = 640.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(HudCardSurface)
            .border(1.dp, PillBorder, RoundedCornerShape(14.dp))
            .padding(horizontal = 20.dp, vertical = 14.dp)
            .testTag("street_info_hud"),
        verticalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = "%02d / %02d · %s".format(positionOneBased, totalInScope, scopeLabel),
                style = TabularStyle,
                color = AccentSky,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
            )

            LocationBadge(
                country = camera.resolvedCountry,
                countryIso = camera.resolvedCountryIso,
                cityLabel = camera.resolvedCityLabel,
            )

            QualityBadge(quality = camera.resolvedQuality)

            TransportBadge(telemetry = telemetry)

            if (autoRotateLabel != null) {
                Text(
                    text = "↻ $autoRotateLabel",
                    style = TabularStyle,
                    color = AccentSky,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                )
            }

            if (isFavorite) {
                Text(
                    text = "★",
                    color = AccentAmber,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                )
            }
        }

        Text(
            text = camera.name,
            color = TextPrimary,
            fontSize = 21.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )

        Text(
            text = "${camera.streets.joinToString(" · ")} — ${camera.municipality}",
            color = TextPrimary,
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )

        Text(
            text = camera.direction,
            color = TextSecondary,
            fontSize = 13.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )

        Spacer(modifier = Modifier.height(2.dp))

        Text(
            text = stringResource(R.string.hud_remote_hints),
            color = TextMuted,
            fontSize = 11.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun LocationBadge(
    country: CameraCountry,
    countryIso: String,
    cityLabel: String,
) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(Color(0x3338BDF8))
            .border(1.dp, Color(0x6638BDF8), RoundedCornerShape(6.dp))
            .padding(horizontal = 9.dp, vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        CountryFlagIcon(
            country = country,
            isoOverride = countryIso,
            width = 16.dp,
            height = 11.dp,
        )
        Text(
            text = cityLabel,
            color = AccentSky,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

@Composable
private fun QualityBadge(quality: VideoQuality) {
    val (bgColor, borderColor, textColor) = when (quality) {
        VideoQuality.UHD_4K -> Triple(Color(0x33F59E0B), Color(0x77FBBF24), AccentAmber)
        VideoQuality.UHD_2K -> Triple(Color(0x3310B981), Color(0x6610B981), Color(0xFF34D399))
        VideoQuality.FHD_1080P,
        VideoQuality.HD_720P,
        -> Triple(Color(0x330EA5E9), Color(0x6638BDF8), AccentSky)
        VideoQuality.SD -> Triple(Color(0x881E293B), PillBorder, TextMuted)
    }
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(bgColor)
            .border(1.dp, borderColor, RoundedCornerShape(6.dp))
            .padding(horizontal = 8.dp, vertical = 3.dp),
    ) {
        Text(
            text = quality.shortBadgeLabel(),
            color = textColor,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

@Composable
private fun TransportBadge(telemetry: PlaybackTelemetry) {
    val (dotColor, label) = when (telemetry) {
        is PlaybackTelemetry.Connecting -> AccentAmber to stringResource(R.string.status_connecting)
        is PlaybackTelemetry.SwitchingFallback -> AccentAmber to stringResource(R.string.status_switching_fallback)
        is PlaybackTelemetry.PlayingLive -> when (telemetry.transport) {
            StreamTransportType.HLS,
            StreamTransportType.YOUTUBE_LIVE,
            StreamTransportType.RUTUBE_LIVE,
            StreamTransportType.IPCAMLIVE,
            -> AccentLive to stringResource(R.string.badge_live_hls)
            StreamTransportType.SNAPSHOT_POLL -> {
                val base = stringResource(R.string.badge_snapshot)
                val full = if (telemetry.updatedTimeLabel != null) {
                    "$base · ${telemetry.updatedTimeLabel}"
                } else {
                    base
                }
                AccentSky to full
            }
        }
        is PlaybackTelemetry.DegradedRetrying -> AccentAmber to stringResource(R.string.status_retrying)
    }

    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(PillSurface)
            .padding(horizontal = 10.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Box(
            modifier = Modifier
                .width(7.dp)
                .height(7.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(dotColor),
        )
        Text(
            text = label,
            color = TextPrimary,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
        )
    }
}

@Composable
private fun rememberQuickStripChipColors() = FilterChipDefaults.colors(
    containerColor = Color(0xE60F172A),
    contentColor = TextPrimary,
    focusedContainerColor = AccentSky,
    focusedContentColor = Color(0xFF0F172A),
    selectedContainerColor = Color(0xFF1E3A5F),
    selectedContentColor = AccentSky,
    focusedSelectedContainerColor = AccentSky,
    focusedSelectedContentColor = Color(0xFF0F172A),
)

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun CameraQuickStrip(
    state: TvStageState,
    onAction: (TvRemoteAction) -> Unit,
) {
    val searchChipFocusRequester = remember { FocusRequester() }
    val activeCardFocusRequester = remember { FocusRequester() }
    val firstCardFocusRequester = remember { FocusRequester() }
    val cameraListState = rememberLazyListState()
    val chipColors = rememberQuickStripChipColors()

    val qualityPool = state.qualityFilteredCameras
    val countryCounts = remember(qualityPool) {
        qualityPool.groupingBy { it.resolvedCountry }.eachCount()
    }
    val cityCounts = remember(qualityPool) {
        qualityPool.groupingBy { it.city }.eachCount()
    }
    val favCount = remember(qualityPool, state.favoriteIds) {
        qualityPool.count { state.favoriteIds.contains(it.id) }
    }

    val visibleCameras = state.stripCameras
    val countryGroups = state.stripCountryGroups
    val activeId = state.activeCamera?.id
    val activeLazyIdx = state.activeStripLazyItemIndex
    val firstVisibleId = visibleCameras.firstOrNull()?.id

    LaunchedEffect(Unit) {
        if (activeLazyIdx >= 0) {
            cameraListState.scrollToItem((activeLazyIdx - 1).coerceAtLeast(0))
            withFrameNanos { }
            runCatching { activeCardFocusRequester.requestFocus() }
        } else if (visibleCameras.isNotEmpty()) {
            cameraListState.scrollToItem(0)
            withFrameNanos { }
            runCatching { firstCardFocusRequester.requestFocus() }
        } else {
            withFrameNanos { }
            runCatching { searchChipFocusRequester.requestFocus() }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                Brush.verticalGradient(
                    0.00f to StageScrimTop,
                    0.14f to Color(0xDF080D1A),
                    1.00f to Color(0xFA080D1A),
                ),
            )
            .padding(
                top = 24.dp,
                bottom = TvOverscanVertical,
            )
            .testTag("camera_quick_strip"),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        // Row 1: Search & Settings Action Chips + All / Popular / Favorites / Countries / Categories
        val currentStripFilter = state.stripFilter
        val selectedCountry = state.selectedStripCountry
        val countryAndCategoryFilters: List<Triple<StripFilter, String, Boolean>> = buildList {
            add(
                Triple(
                    StripFilter.All,
                    "${stringResource(R.string.category_all)} (${qualityPool.size})",
                    currentStripFilter == StripFilter.All,
                ),
            )
            add(
                Triple(
                    StripFilter.Popular,
                    "${stringResource(R.string.category_popular)} (${state.featuredTotalCount})",
                    currentStripFilter == StripFilter.Popular,
                ),
            )
            add(
                Triple(
                    StripFilter.Favorites,
                    "${stringResource(R.string.category_favorites)} ($favCount)",
                    currentStripFilter == StripFilter.Favorites,
                ),
            )
            for (country in state.availableCountries) {
                val count = countryCounts[country] ?: 0
                add(
                    Triple(
                        StripFilter.Country(country),
                        "${state.countryDisplayLabel(country)} ($count)",
                        selectedCountry == country,
                    ),
                )
            }
            add(
                Triple(
                    StripFilter.Category(CameraCategory.BRIDGES),
                    stringResource(R.string.category_bridges),
                    currentStripFilter == StripFilter.Category(CameraCategory.BRIDGES),
                ),
            )
            add(
                Triple(
                    StripFilter.Category(CameraCategory.SQUARES),
                    stringResource(R.string.category_squares),
                    currentStripFilter == StripFilter.Category(CameraCategory.SQUARES),
                ),
            )
            add(
                Triple(
                    StripFilter.Category(CameraCategory.BOULEVARDS),
                    stringResource(R.string.category_boulevards),
                    currentStripFilter == StripFilter.Category(CameraCategory.BOULEVARDS),
                ),
            )
            add(
                Triple(
                    StripFilter.Category(CameraCategory.HIGHWAYS),
                    stringResource(R.string.category_highways),
                    currentStripFilter == StripFilter.Category(CameraCategory.HIGHWAYS),
                ),
            )
        }

        LazyRow(
            contentPadding = PaddingValues(horizontal = TvOverscanHorizontal),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item(key = "action_open_search") {
                FilterChip(
                    selected = false,
                    onClick = { onAction(TvRemoteAction.OpenSearch) },
                    colors = chipColors,
                    modifier = Modifier
                        .focusRequester(searchChipFocusRequester)
                        .testTag("strip_open_search_chip"),
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(7.dp),
                    ) {
                        SearchLensIcon(size = 14.dp)
                        Text(text = stringResource(R.string.action_search))
                    }
                }
            }
            item(key = "action_open_settings") {
                val activeFiltersBadge = buildList {
                    if (state.qualityFilter != QualityFilter.ALL) add(state.qualityFilter.displayLabel())
                    if (state.transportFilter != TransportFilter.ALL) add(state.transportFilter.displayLabel())
                }.joinToString(" · ")
                val settingsLabel = if (activeFiltersBadge.isEmpty()) {
                    stringResource(R.string.action_settings)
                } else {
                    "${stringResource(R.string.action_settings)} ($activeFiltersBadge)"
                }
                FilterChip(
                    selected = activeFiltersBadge.isNotEmpty(),
                    onClick = { onAction(TvRemoteAction.OpenSettings) },
                    colors = chipColors,
                    modifier = Modifier.testTag("strip_open_settings_chip"),
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(7.dp),
                    ) {
                        SettingsSlidersIcon(size = 14.dp)
                        Text(text = settingsLabel)
                    }
                }
            }
            items(countryAndCategoryFilters.size) { idx ->
                val (filterItem, title, isSelected) = countryAndCategoryFilters[idx]
                FilterChip(
                    selected = isSelected,
                    onClick = { onAction(TvRemoteAction.SelectStripFilter(filterItem)) },
                    colors = chipColors,
                ) {
                    if (filterItem is StripFilter.Country) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(7.dp),
                        ) {
                            CountryFlagIcon(
                                country = filterItem.country,
                                isoOverride = state.countryIsoCode(filterItem.country),
                                width = 18.dp,
                                height = 12.dp,
                            )
                            Text(text = title)
                        }
                    } else {
                        Text(text = title)
                    }
                }
            }
        }

        // Contextual Row 2: Shown ONLY when a Country filter (or City within a Country) is active
        if (selectedCountry != null) {
            val cityFilters: List<Pair<StripFilter, String>> = buildList {
                val totalCountry = countryCounts[selectedCountry] ?: 0
                add(
                    StripFilter.Country(selectedCountry) to
                        "All ${state.countryDisplayLabel(selectedCountry)} ($totalCountry)",
                )
                for (city in state.citiesForCountry(selectedCountry)) {
                    val count = cityCounts[city] ?: 0
                    add(StripFilter.City(city) to "${state.cityDisplayLabel(city)} ($count)")
                }
            }

            LazyRow(
                contentPadding = PaddingValues(horizontal = TvOverscanHorizontal),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.testTag("strip_contextual_city_row"),
            ) {
                items(cityFilters.size) { idx ->
                    val (filterItem, title) = cityFilters[idx]
                    FilterChip(
                        selected = currentStripFilter == filterItem,
                        onClick = { onAction(TvRemoteAction.SelectStripFilter(filterItem)) },
                        colors = chipColors,
                    ) {
                        if (filterItem is StripFilter.Country) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(7.dp),
                            ) {
                                CountryFlagIcon(
                                    country = filterItem.country,
                                    isoOverride = state.countryIsoCode(filterItem.country),
                                    width = 18.dp,
                                    height = 12.dp,
                                )
                                Text(text = title)
                            }
                        } else {
                            Text(text = title)
                        }
                    }
                }
            }
        }

        // Camera Carousel Row: Country-Grouped Camera Preview Cards (HD vs SD split per Country)
        val favIds = state.favoriteIds
        if (visibleCameras.isEmpty()) {
            EmptyStripStateCard(
                state = state,
                onAction = onAction,
            )
        } else {
            LazyRow(
                state = cameraListState,
                contentPadding = PaddingValues(horizontal = TvOverscanHorizontal),
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.testTag("strip_camera_carousel"),
            ) {
                for (group in countryGroups) {
                    if (group.hdCameras.isNotEmpty()) {
                        item(key = "section_hd_${group.country.name}") {
                            QualitySectionCard(
                                country = group.country,
                                countryLabel = group.countryLabel,
                                countryIso = group.countryIso,
                                isHd = true,
                                count = group.hdCameras.size,
                            )
                        }
                        items(
                            items = group.hdCameras,
                            key = { it.id },
                        ) { item ->
                            val cardModifier = when {
                                item.id == activeId -> Modifier.focusRequester(activeCardFocusRequester)
                                activeLazyIdx < 0 && item.id == firstVisibleId ->
                                    Modifier.focusRequester(firstCardFocusRequester)
                                else -> Modifier
                            }
                            CameraPreviewCard(
                                camera = item,
                                isPlaying = item.id == activeId,
                                isFavorite = favIds.contains(item.id),
                                onSelect = { onAction(TvRemoteAction.SelectCamera(item.id)) },
                                modifier = cardModifier,
                            )
                        }
                    }
                    if (group.sdCameras.isNotEmpty()) {
                        item(key = "section_sd_${group.country.name}") {
                            QualitySectionCard(
                                country = group.country,
                                countryLabel = group.countryLabel,
                                countryIso = group.countryIso,
                                isHd = false,
                                count = group.sdCameras.size,
                            )
                        }
                        items(
                            items = group.sdCameras,
                            key = { it.id },
                        ) { item ->
                            val cardModifier = when {
                                item.id == activeId -> Modifier.focusRequester(activeCardFocusRequester)
                                activeLazyIdx < 0 && item.id == firstVisibleId ->
                                    Modifier.focusRequester(firstCardFocusRequester)
                                else -> Modifier
                            }
                            CameraPreviewCard(
                                camera = item,
                                isPlaying = item.id == activeId,
                                isFavorite = favIds.contains(item.id),
                                onSelect = { onAction(TvRemoteAction.SelectCamera(item.id)) },
                                modifier = cardModifier,
                            )
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun SettingsSideDrawer(
    state: TvStageState,
    isScreensaverMode: Boolean,
    onAction: (TvRemoteAction) -> Unit,
) {
    val firstSettingFocusRequester = remember { FocusRequester() }
    val chipColors = rememberQuickStripChipColors()
    val effectiveRotate = state.autoRotateInterval.effectiveFor(isScreensaverMode)
    val allCameras = state.cameras

    LaunchedEffect(Unit) {
        withFrameNanos { }
        runCatching { firstSettingFocusRequester.requestFocus() }
    }

    Column(
        modifier = Modifier
            .width(440.dp)
            .fillMaxHeight()
            .background(DrawerSurface)
            .border(width = 1.dp, color = PillBorder, shape = RoundedCornerShape(topStart = 18.dp, bottomStart = 18.dp))
            .padding(
                start = 24.dp,
                end = TvOverscanHorizontal,
                top = TvOverscanVertical,
                bottom = TvOverscanVertical,
            )
            .testTag("settings_side_drawer"),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            SettingsSlidersIcon(size = 18.dp, color = AccentSky)
            Text(
                text = stringResource(R.string.settings_heading),
                color = TextPrimary,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
            )
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item(key = "header_playback") {
                DrawerSectionHeader(text = stringResource(R.string.settings_section_playback))
            }
            item(key = "setting_quality") {
                val valueLabel = when (state.qualityFilter) {
                    QualityFilter.ALL -> "All (${allCameras.size})"
                    QualityFilter.HD_ONLY -> "HD only (${state.hdTotalCount})"
                    QualityFilter.SD_ONLY -> "SD only (${state.sdTotalCount})"
                }
                DrawerSettingRow(
                    label = "Quality",
                    value = valueLabel,
                    selected = state.qualityFilter != QualityFilter.ALL,
                    chipColors = chipColors,
                    onClick = { onAction(TvRemoteAction.CycleQualityFilter) },
                    modifier = Modifier
                        .focusRequester(firstSettingFocusRequester)
                        .testTag("setting_quality_chip"),
                )
            }
            item(key = "setting_stream") {
                val valueLabel = when (state.transportFilter) {
                    TransportFilter.ALL -> "All"
                    TransportFilter.LIVE_VIDEO_ONLY -> "Live video (${state.liveVideoTotalCount})"
                    TransportFilter.SNAPSHOT_ONLY -> "Snapshots (${state.snapshotTotalCount})"
                }
                DrawerSettingRow(
                    label = "Stream type",
                    value = valueLabel,
                    selected = state.transportFilter != TransportFilter.ALL,
                    chipColors = chipColors,
                    onClick = { onAction(TvRemoteAction.CycleTransportFilter) },
                    modifier = Modifier.testTag("setting_stream_chip"),
                )
            }
            item(key = "setting_zap_scope") {
                DrawerSettingRow(
                    label = "Zap scope",
                    value = state.zapScope.displayLabel(),
                    selected = state.zapScope != ZapScope.ALL,
                    chipColors = chipColors,
                    onClick = { onAction(TvRemoteAction.ToggleZapScope) },
                    modifier = Modifier.testTag("setting_zap_scope_chip"),
                )
            }
            item(key = "setting_auto_rotate") {
                val rotateValue = if (isScreensaverMode && !state.autoRotateInterval.enabled) {
                    "${effectiveRotate.displayLabel()} (Screensaver)"
                } else {
                    state.autoRotateInterval.displayLabel()
                }
                DrawerSettingRow(
                    label = "Auto-rotate",
                    value = rotateValue,
                    selected = effectiveRotate.enabled,
                    chipColors = chipColors,
                    onClick = { onAction(TvRemoteAction.CycleAutoRotate) },
                    modifier = Modifier.testTag("setting_auto_rotate_chip"),
                )
            }
            item(key = "setting_performance") {
                DrawerSettingRow(
                    label = "Performance",
                    value = state.performanceMode.displayLabel(),
                    selected = state.performanceMode != PerformanceMode.AUTO,
                    chipColors = chipColors,
                    onClick = { onAction(TvRemoteAction.CyclePerformanceMode) },
                    modifier = Modifier.testTag("setting_performance_chip"),
                )
            }

            item(key = "header_display") {
                Spacer(modifier = Modifier.height(6.dp))
                DrawerSectionHeader(text = stringResource(R.string.settings_section_display))
            }
            item(key = "setting_panel_pos") {
                DrawerSettingRow(
                    label = "Panel position",
                    value = state.infoPanelPosition.displayLabel(),
                    selected = state.infoPanelPosition != InfoPanelPosition.BOTTOM_CENTER,
                    chipColors = chipColors,
                    onClick = { onAction(TvRemoteAction.CycleInfoPanelPosition) },
                    modifier = Modifier.testTag("setting_panel_position_chip"),
                )
            }
            item(key = "setting_clock") {
                DrawerSettingRow(
                    label = "Dual clock",
                    value = if (state.showClockOverlay) "On" else "Off",
                    selected = state.showClockOverlay,
                    chipColors = chipColors,
                    onClick = { onAction(TvRemoteAction.ToggleClockOverlay) },
                )
            }
            item(key = "setting_weather") {
                DrawerSettingRow(
                    label = "Weather",
                    value = if (state.showWeatherOverlay) "On" else "Off",
                    selected = state.showWeatherOverlay,
                    chipColors = chipColors,
                    onClick = { onAction(TvRemoteAction.ToggleWeatherOverlay) },
                )
            }
            item(key = "setting_aqi") {
                DrawerSettingRow(
                    label = "Air quality",
                    value = if (state.showAirQualityOverlay) "On" else "Off",
                    selected = state.showAirQualityOverlay,
                    chipColors = chipColors,
                    onClick = { onAction(TvRemoteAction.ToggleAirQualityOverlay) },
                )
            }
            item(key = "setting_solar") {
                DrawerSettingRow(
                    label = "Sun & golden hour",
                    value = if (state.showSolarOverlay) "On" else "Off",
                    selected = state.showSolarOverlay,
                    chipColors = chipColors,
                    onClick = { onAction(TvRemoteAction.ToggleSolarOverlay) },
                )
            }
            item(key = "setting_oled") {
                DrawerSettingRow(
                    label = "OLED burn-in guard",
                    value = if (state.oledProtectionEnabled) "On" else "Off",
                    selected = state.oledProtectionEnabled,
                    chipColors = chipColors,
                    onClick = { onAction(TvRemoteAction.ToggleOledProtection) },
                )
            }
        }
    }
}

@Composable
private fun DrawerSectionHeader(text: String) {
    Text(
        text = text,
        color = AccentSky,
        fontSize = 12.sp,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.padding(vertical = 2.dp),
    )
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun DrawerSettingRow(
    label: String,
    value: String,
    selected: Boolean,
    chipColors: androidx.tv.material3.SelectableChipColors,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        colors = chipColors,
        modifier = modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = label,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
            )
            Text(
                text = value,
                style = TabularStyle,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
            )
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun CameraSearchOverlay(
    state: TvStageState,
    onAction: (TvRemoteAction) -> Unit,
) {
    val initialKeyFocusRequester = remember { FocusRequester() }
    val chipColors = rememberQuickStripChipColors()
    val results = state.searchResults
    val activeId = state.activeCamera?.id
    val favIds = state.favoriteIds

    LaunchedEffect(Unit) {
        withFrameNanos { }
        runCatching { initialKeyFocusRequester.requestFocus() }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(DrawerSurface)
            .border(width = 1.dp, color = PillBorder, shape = RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp))
            .padding(
                top = 20.dp,
                bottom = TvOverscanVertical,
            )
            .testTag("camera_search_overlay"),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        // Query header bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = TvOverscanHorizontal),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Row(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(10.dp))
                    .background(Color(0xFF0F172A))
                    .border(1.dp, Color(0x6638BDF8), RoundedCornerShape(10.dp))
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                SearchLensIcon(size = 16.dp, color = AccentSky)
                Text(
                    text = if (state.searchQuery.isEmpty()) {
                        stringResource(R.string.search_placeholder)
                    } else {
                        state.searchQuery
                    },
                    color = if (state.searchQuery.isEmpty()) TextMuted else TextPrimary,
                    fontSize = 16.sp,
                    fontWeight = if (state.searchQuery.isEmpty()) FontWeight.Normal else FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.testTag("search_query_text"),
                )
            }
            Spacer(modifier = Modifier.width(16.dp))
            Text(
                text = if (state.searchQuery.isBlank()) {
                    "Popular (${results.size})"
                } else {
                    "${results.size} matches"
                },
                style = TabularStyle,
                color = AccentSky,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
            )
        }

        // Compact D-pad key strip (A–Z, 0–9, Space, Backspace, Clear)
        LazyRow(
            contentPadding = PaddingValues(horizontal = TvOverscanHorizontal),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.testTag("search_key_bar"),
        ) {
            itemsIndexed(
                items = SEARCH_KEY_TOKENS,
                key = { _, token -> "key_$token" },
            ) { idx, token ->
                FilterChip(
                    selected = false,
                    onClick = { onAction(TvRemoteAction.AppendSearchChar(token.lowercase())) },
                    colors = chipColors,
                    modifier = if (idx == 0) {
                        Modifier
                            .focusRequester(initialKeyFocusRequester)
                            .testTag("search_key_$token")
                    } else {
                        Modifier.testTag("search_key_$token")
                    },
                ) {
                    Text(
                        text = token,
                        style = TabularStyle,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
            item(key = "key_space") {
                FilterChip(
                    selected = false,
                    onClick = { onAction(TvRemoteAction.AppendSearchChar(" ")) },
                    colors = chipColors,
                    modifier = Modifier.testTag("search_key_space"),
                ) {
                    Text(text = "Space")
                }
            }
            item(key = "key_backspace") {
                FilterChip(
                    selected = false,
                    onClick = { onAction(TvRemoteAction.BackspaceSearchQuery) },
                    colors = chipColors,
                    modifier = Modifier.testTag("search_key_backspace"),
                ) {
                    Text(text = "⌫")
                }
            }
            item(key = "key_clear") {
                FilterChip(
                    selected = false,
                    onClick = { onAction(TvRemoteAction.ClearSearchQuery) },
                    colors = chipColors,
                    modifier = Modifier.testTag("search_key_clear"),
                ) {
                    Text(text = stringResource(R.string.search_clear_action))
                }
            }
        }

        // Live-updating result cards
        if (results.isEmpty()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = TvOverscanHorizontal, vertical = 8.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color(0xCC0F172A))
                    .border(1.dp, PillBorder, RoundedCornerShape(12.dp))
                    .padding(horizontal = 20.dp, vertical = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = stringResource(R.string.search_empty_heading),
                    color = TextPrimary,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                )
                Button(
                    onClick = { onAction(TvRemoteAction.ClearSearchQuery) },
                    modifier = Modifier.testTag("search_empty_clear_button"),
                ) {
                    Text(text = stringResource(R.string.search_clear_action))
                }
            }
        } else {
            LazyRow(
                contentPadding = PaddingValues(horizontal = TvOverscanHorizontal),
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.testTag("search_results_carousel"),
            ) {
                items(
                    items = results,
                    key = { "search_${it.id}" },
                ) { item ->
                    CameraPreviewCard(
                        camera = item,
                        isPlaying = item.id == activeId,
                        isFavorite = favIds.contains(item.id),
                        onSelect = { onAction(TvRemoteAction.SelectCamera(item.id)) },
                    )
                }
            }
        }
    }
}

@Composable
private fun QualitySectionCard(
    country: CameraCountry,
    countryLabel: String,
    countryIso: String,
    isHd: Boolean,
    count: Int,
) {
    val accentColor = if (isHd) Color(0xFF34D399) else TextMuted
    val borderColor = if (isHd) Color(0x5510B981) else PillBorder
    val containerColor = if (isHd) Color(0xD90A1E24) else Color(0xD90F172A)

    Column(
        modifier = Modifier
            .width(136.dp)
            .height(182.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(containerColor)
            .border(1.dp, borderColor, RoundedCornerShape(12.dp))
            .padding(horizontal = 12.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.SpaceBetween,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Box(
                modifier = Modifier
                    .width(28.dp)
                    .height(4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(accentColor),
            )
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                CountryFlagIcon(
                    country = country,
                    isoOverride = countryIso,
                    width = 18.dp,
                    height = 12.dp,
                )
                Text(
                    text = countryLabel.uppercase(),
                    color = AccentSky,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }

        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                text = if (isHd) {
                    stringResource(R.string.section_hd_title)
                } else {
                    stringResource(R.string.section_sd_title)
                },
                color = TextPrimary,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
            )
            Text(
                text = if (isHd) {
                    stringResource(R.string.section_hd_subtitle)
                } else {
                    stringResource(R.string.section_sd_subtitle)
                },
                color = accentColor,
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 2,
            )
        }

        Text(
            text = "$count cams",
            style = TabularStyle,
            color = TextSecondary,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
        )
    }
}

@Composable
private fun EmptyStripStateCard(
    state: TvStageState,
    onAction: (TvRemoteAction) -> Unit,
) {
    val scopeName = when (val f = state.stripFilter) {
        StripFilter.All -> "All World"
        StripFilter.Popular -> "Popular"
        StripFilter.Favorites -> "Favorites"
        is StripFilter.Country -> state.countryDisplayLabel(f.country)
        is StripFilter.City -> state.cityDisplayLabel(f.city)
        is StripFilter.Category -> f.category.displayLabel()
    }
    val scopeCams = state.stripScopeCamerasUnfiltered
    val isEmptyFavorites = state.stripFilter == StripFilter.Favorites && state.favoriteIds.isEmpty()
    val blockedByTransport = scopeCams.isNotEmpty() &&
        state.transportFilter != TransportFilter.ALL &&
        scopeCams.any { state.qualityFilter.matches(it.resolvedQuality) }
    val blockedByQuality = scopeCams.isNotEmpty() &&
        state.qualityFilter != QualityFilter.ALL &&
        scopeCams.any { state.transportFilter.matches(it.isLiveVideo) }

    val (heading, body, actionLabel, recoveryAction) = when {
        isEmptyFavorites -> EmptyStripModel(
            heading = stringResource(R.string.empty_favorites_heading),
            body = stringResource(R.string.empty_favorites_body),
            actionLabel = stringResource(R.string.empty_favorites_action),
            action = TvRemoteAction.SelectStripFilter(StripFilter.All),
        )
        blockedByTransport -> {
            val kind = if (state.transportFilter == TransportFilter.SNAPSHOT_ONLY) {
                "snapshot feeds"
            } else {
                "live video feeds"
            }
            EmptyStripModel(
                heading = "No $kind in $scopeName",
                body = "${scopeCams.size} cameras are available in $scopeName under other stream types",
                actionLabel = stringResource(R.string.empty_transport_action),
                action = TvRemoteAction.ShowAllTransports,
            )
        }
        blockedByQuality -> EmptyStripModel(
            heading = "No ${state.qualityFilter.displayLabel()} streams in $scopeName",
            body = "${scopeCams.size} cameras are available in $scopeName across other resolutions",
            actionLabel = stringResource(R.string.empty_quality_action),
            action = TvRemoteAction.ShowAllQualities,
        )
        else -> EmptyStripModel(
            heading = stringResource(R.string.empty_quality_heading),
            body = stringResource(R.string.empty_quality_body),
            actionLabel = stringResource(R.string.empty_reset_action),
            action = TvRemoteAction.ResetFilters,
        )
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = TvOverscanHorizontal, vertical = 10.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(Color(0xCC0F172A))
            .border(1.dp, PillBorder, RoundedCornerShape(12.dp))
            .padding(horizontal = 20.dp, vertical = 16.dp)
            .testTag("empty_strip_state_card"),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = heading,
                color = TextPrimary,
                fontSize = 17.sp,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = body,
                color = TextSecondary,
                fontSize = 13.sp,
            )
        }
        Spacer(modifier = Modifier.width(16.dp))
        Button(onClick = { onAction(recoveryAction) }) {
            Text(text = actionLabel)
        }
    }
}

private data class EmptyStripModel(
    val heading: String,
    val body: String,
    val actionLabel: String,
    val action: TvRemoteAction,
)

@Composable
private fun CameraPreviewCard(
    camera: Camera,
    isPlaying: Boolean,
    isFavorite: Boolean,
    onSelect: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val posterUrl = camera.posterUrl
    val posterHeaders = camera.posterHeaders
    val imageRequest = remember(posterUrl, posterHeaders) {
        val networkHeaders = NetworkHeaders.Builder().apply {
            posterHeaders.orEmpty().forEach { (k, v) -> set(k, v) }
        }.build()
        ImageRequest.Builder(context)
            .data(posterUrl)
            .httpHeaders(networkHeaders)
            .size(Size(320, 180))
            .allowRgb565(true)
            .build()
    }

    var posterLoading by remember(posterUrl) { mutableStateOf(true) }
    var posterError by remember(posterUrl) { mutableStateOf(false) }

    Card(
        onClick = onSelect,
        modifier = modifier
            .width(228.dp)
            .testTag("camera_card_${camera.id}"),
        colors = CardDefaults.colors(
            containerColor = Color(0xFF1E293B),
        ),
    ) {
        Column {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(16f / 9f)
                    .background(if (posterLoading) Color(0xFF162032) else Color(0xFF0F172A)),
            ) {
                if (posterError) {
                    Column(
                        modifier = Modifier.align(Alignment.Center),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        CameraSignalOffIcon(size = 20.dp)
                        Text(
                            text = stringResource(R.string.preview_unavailable),
                            color = TextMuted,
                            fontSize = 10.sp,
                        )
                    }
                } else {
                    AsyncImage(
                        model = imageRequest,
                        contentDescription = camera.name,
                        contentScale = ContentScale.Crop,
                        onLoading = { posterLoading = true },
                        onSuccess = { posterLoading = false },
                        onError = {
                            posterLoading = false
                            posterError = true
                        },
                        modifier = Modifier.fillMaxSize(),
                    )
                }

                if (isPlaying) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .padding(8.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(AccentSky)
                            .padding(horizontal = 8.dp, vertical = 2.dp),
                    ) {
                        Text(
                            text = "Watching",
                            color = Color(0xFF0F172A),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }

                if (isFavorite) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(8.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(PillSurface)
                            .padding(horizontal = 6.dp, vertical = 2.dp),
                    ) {
                        Text(
                            text = "★",
                            color = AccentAmber,
                            fontSize = 12.sp,
                        )
                    }
                }

                val isLiveMotion = camera.isLiveVideo
                Row(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(6.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(if (isLiveMotion) Color(0xE67F1D1D) else Color(0xD90F172A))
                        .border(
                            width = 1.dp,
                            color = if (isLiveMotion) Color(0x99EF4444) else Color(0x5538BDF8),
                            shape = RoundedCornerShape(4.dp),
                        )
                        .padding(horizontal = 6.dp, vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Box(
                        modifier = Modifier
                            .width(5.dp)
                            .height(5.dp)
                            .clip(RoundedCornerShape(3.dp))
                            .background(if (isLiveMotion) AccentLive else AccentSky),
                    )
                    Text(
                        text = if (isLiveMotion) {
                            stringResource(R.string.badge_card_live)
                        } else {
                            stringResource(R.string.badge_card_snap)
                        },
                        color = if (isLiveMotion) TextPrimary else AccentSky,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                    )
                }

                val camQuality = camera.resolvedQuality
                val badgeTextColor = when (camQuality) {
                    VideoQuality.UHD_4K -> AccentAmber
                    VideoQuality.UHD_2K -> Color(0xFF34D399)
                    VideoQuality.FHD_1080P,
                    VideoQuality.HD_720P,
                    -> AccentSky
                    VideoQuality.SD -> TextMuted
                }
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(6.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(Color(0xD9090D16))
                        .border(1.dp, Color(0x3394A3B8), RoundedCornerShape(4.dp))
                        .padding(horizontal = 6.dp, vertical = 2.dp),
                ) {
                    Text(
                        text = camQuality.shortBadgeLabel(),
                        color = badgeTextColor,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(
                    text = camera.name,
                    style = MaterialTheme.typography.titleSmall,
                    color = TextPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                val firstStreet = camera.streets.firstOrNull().orEmpty()
                val subtitle = if (firstStreet.isNotBlank()) {
                    "${camera.resolvedCityLabel} · $firstStreet"
                } else {
                    camera.resolvedCityLabel
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    CountryFlagIcon(
                        country = camera.resolvedCountry,
                        isoOverride = camera.resolvedCountryIso,
                        width = 15.dp,
                        height = 10.dp,
                    )
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}
