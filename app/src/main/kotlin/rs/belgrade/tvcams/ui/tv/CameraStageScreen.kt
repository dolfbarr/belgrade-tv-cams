@file:OptIn(ExperimentalTvMaterial3Api::class)

package rs.belgrade.tvcams.ui.tv

import android.view.KeyEvent
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
import rs.belgrade.tvcams.domain.model.CameraCity
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
        if (state.overlayMode != OverlayMode.QUICK_STRIP) {
            stageFocusRequester.requestFocus()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .onPreviewKeyEvent { keyEvent ->
                val nativeCode = keyEvent.nativeKeyEvent.keyCode
                if (!enableBackHandler && nativeCode == KeyEvent.KEYCODE_BACK) {
                    if (keyEvent.type == KeyEventType.KeyUp) {
                        onAction(TvRemoteAction.NavigateBack)
                    }
                    return@onPreviewKeyEvent true
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
                    telemetry is PlaybackTelemetry.DegradedRetrying &&
                    state.overlayMode != OverlayMode.QUICK_STRIP
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

        // Layer 1: Non-blocking top status / spinner / notice badges inside TV safe area
        TopStatusLayer(
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
        if (telemetry is PlaybackTelemetry.DegradedRetrying &&
            state.overlayMode != OverlayMode.QUICK_STRIP
        ) {
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

        if (state.overlayMode != OverlayMode.QUICK_STRIP) {
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

        // Layer 3: Bottom Quick Strip Camera Carousel & Toggles (DPAD_DOWN / MENU)
        AnimatedVisibility(
            visible = state.overlayMode == OverlayMode.QUICK_STRIP,
            enter = slideInVertically { it } + fadeIn(),
            exit = slideOutVertically { it } + fadeOut(),
            modifier = Modifier.align(Alignment.BottomCenter),
        ) {
            CameraQuickStrip(
                state = state,
                isScreensaverMode = isScreensaverMode,
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
                // Omit country name when rendering the country flag; display city only
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
    KeyEvent.KEYCODE_BACK -> TvKeyCode.BACK
    else -> TvKeyCode.OTHER
}

@Composable
private fun TopStatusLayer(
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
            color = TextPrimary,
            fontSize = 15.sp,
            fontWeight = FontWeight.Medium,
        )
    }
}

@Composable
private fun CatalogLoadingStage() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF060A14))
            .padding(horizontal = TvOverscanHorizontal, vertical = TvOverscanVertical),
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

        // Bottom ambient panel skeleton shaped like loaded content
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
        retryFocusRequester.requestFocus()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF060A14))
            .padding(horizontal = TvOverscanHorizontal, vertical = TvOverscanVertical),
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
                modifier = Modifier.focusRequester(retryFocusRequester),
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
            .padding(horizontal = 22.dp, vertical = 16.dp),
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
            .padding(horizontal = 20.dp, vertical = 14.dp),
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

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun CameraQuickStrip(
    state: TvStageState,
    isScreensaverMode: Boolean,
    onAction: (TvRemoteAction) -> Unit,
) {
    val defaultCountryChipFocusRequester = remember { FocusRequester() }
    val effectiveRotate = state.autoRotateInterval.effectiveFor(isScreensaverMode)

    LaunchedEffect(Unit) {
        defaultCountryChipFocusRequester.requestFocus()
    }

    val allCameras = state.cameras
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

    val chipColors = FilterChipDefaults.colors(
        containerColor = Color(0xE60F172A),
        contentColor = TextPrimary,
        focusedContainerColor = AccentSky,
        focusedContentColor = Color(0xFF0F172A),
        selectedContainerColor = Color(0xFF1E3A5F),
        selectedContentColor = AccentSky,
        focusedSelectedContainerColor = AccentSky,
        focusedSelectedContentColor = Color(0xFF0F172A),
    )

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
            ),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        // Row 1: Quality Tier, Auto-Rotate, Panel Position, Zap Scope & Display Overlays
        LazyRow(
            contentPadding = PaddingValues(horizontal = TvOverscanHorizontal),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item(key = "toggle_quality_filter") {
                val qualityText = when (state.qualityFilter) {
                    QualityFilter.ALL -> "Quality: All (${allCameras.size})"
                    QualityFilter.HD_ONLY -> "Quality: HD only (${state.hdTotalCount})"
                    QualityFilter.SD_ONLY -> "Quality: SD only (${state.sdTotalCount})"
                }
                FilterChip(
                    selected = state.qualityFilter != QualityFilter.ALL,
                    onClick = { onAction(TvRemoteAction.CycleQualityFilter) },
                    colors = chipColors,
                ) {
                    Text(text = qualityText)
                }
            }
            item(key = "toggle_transport_filter") {
                val streamText = when (state.transportFilter) {
                    TransportFilter.ALL -> "Stream: All"
                    TransportFilter.LIVE_VIDEO_ONLY -> "Stream: Live video (${state.liveVideoTotalCount})"
                    TransportFilter.SNAPSHOT_ONLY -> "Stream: Snapshots (${state.snapshotTotalCount})"
                }
                FilterChip(
                    selected = state.transportFilter != TransportFilter.ALL,
                    onClick = { onAction(TvRemoteAction.CycleTransportFilter) },
                    colors = chipColors,
                ) {
                    Text(text = streamText)
                }
            }
            item(key = "toggle_performance_mode") {
                val perfText = "Perf: ${state.performanceMode.displayLabel()}"
                FilterChip(
                    selected = state.performanceMode != PerformanceMode.AUTO,
                    onClick = { onAction(TvRemoteAction.CyclePerformanceMode) },
                    colors = chipColors,
                ) {
                    Text(text = perfText)
                }
            }
            item(key = "toggle_auto_rotate") {
                val rotateText = if (isScreensaverMode && !state.autoRotateInterval.enabled) {
                    "Rotate: ${effectiveRotate.displayLabel()} (Screensaver)"
                } else {
                    "Rotate: ${state.autoRotateInterval.displayLabel()}"
                }
                FilterChip(
                    selected = effectiveRotate.enabled,
                    onClick = { onAction(TvRemoteAction.CycleAutoRotate) },
                    colors = chipColors,
                ) {
                    Text(text = rotateText)
                }
            }
            item(key = "toggle_panel_position") {
                val panelText = "Panel: ${state.infoPanelPosition.displayLabel()}"
                FilterChip(
                    selected = state.infoPanelPosition != InfoPanelPosition.BOTTOM_CENTER,
                    onClick = { onAction(TvRemoteAction.CycleInfoPanelPosition) },
                    colors = chipColors,
                ) {
                    Text(text = panelText)
                }
            }
            item(key = "toggle_zap_scope") {
                val scopeText = "Zap: ${state.zapScope.displayLabel()}"
                FilterChip(
                    selected = state.zapScope != ZapScope.ALL,
                    onClick = { onAction(TvRemoteAction.ToggleZapScope) },
                    colors = chipColors,
                ) {
                    Text(text = scopeText)
                }
            }
            item(key = "toggle_clock") {
                FilterChip(
                    selected = state.showClockOverlay,
                    onClick = { onAction(TvRemoteAction.ToggleClockOverlay) },
                    colors = chipColors,
                ) {
                    Text(
                        text = if (state.showClockOverlay) {
                            stringResource(R.string.toggle_clock_on)
                        } else {
                            stringResource(R.string.toggle_clock_off)
                        },
                    )
                }
            }
            item(key = "toggle_weather") {
                FilterChip(
                    selected = state.showWeatherOverlay,
                    onClick = { onAction(TvRemoteAction.ToggleWeatherOverlay) },
                    colors = chipColors,
                ) {
                    Text(
                        text = if (state.showWeatherOverlay) {
                            stringResource(R.string.toggle_weather_on)
                        } else {
                            stringResource(R.string.toggle_weather_off)
                        },
                    )
                }
            }
            item(key = "toggle_aqi") {
                FilterChip(
                    selected = state.showAirQualityOverlay,
                    onClick = { onAction(TvRemoteAction.ToggleAirQualityOverlay) },
                    colors = chipColors,
                ) {
                    Text(
                        text = if (state.showAirQualityOverlay) {
                            stringResource(R.string.toggle_aqi_on)
                        } else {
                            stringResource(R.string.toggle_aqi_off)
                        },
                    )
                }
            }
            item(key = "toggle_solar") {
                FilterChip(
                    selected = state.showSolarOverlay,
                    onClick = { onAction(TvRemoteAction.ToggleSolarOverlay) },
                    colors = chipColors,
                ) {
                    Text(
                        text = if (state.showSolarOverlay) {
                            stringResource(R.string.toggle_solar_on)
                        } else {
                            stringResource(R.string.toggle_solar_off)
                        },
                    )
                }
            }
            item(key = "toggle_oled") {
                FilterChip(
                    selected = state.oledProtectionEnabled,
                    onClick = { onAction(TvRemoteAction.ToggleOledProtection) },
                    colors = chipColors,
                ) {
                    Text(
                        text = if (state.oledProtectionEnabled) {
                            stringResource(R.string.toggle_oled_on)
                        } else {
                            stringResource(R.string.toggle_oled_off)
                        },
                    )
                }
            }
        }

        // Row 2: Country Grouping Tabs & Street Categories
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
            items(countryAndCategoryFilters.size) { idx ->
                val (filterItem, title, isSelected) = countryAndCategoryFilters[idx]
                val chipModifier = if (idx == 0) {
                    Modifier.focusRequester(defaultCountryChipFocusRequester)
                } else {
                    Modifier
                }
                FilterChip(
                    selected = isSelected,
                    onClick = { onAction(TvRemoteAction.SelectStripFilter(filterItem)) },
                    colors = chipColors,
                    modifier = chipModifier,
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

        // Row 3: Contextual City Filter Row (Cities of selected Country, or all World Cities)
        val cityFilters: List<Pair<StripFilter, String>> = buildList {
            if (selectedCountry != null) {
                val totalCountry = countryCounts[selectedCountry] ?: 0
                add(
                    StripFilter.Country(selectedCountry) to
                        "All ${state.countryDisplayLabel(selectedCountry)} ($totalCountry)",
                )
                for (city in state.citiesForCountry(selectedCountry)) {
                    val count = cityCounts[city] ?: 0
                    add(StripFilter.City(city) to "${state.cityDisplayLabel(city)} ($count)")
                }
            } else {
                for (city in state.availableCities) {
                    val count = cityCounts[city] ?: 0
                    add(StripFilter.City(city) to "${state.cityDisplayLabel(city)} ($count)")
                }
            }
        }

        LazyRow(
            contentPadding = PaddingValues(horizontal = TvOverscanHorizontal),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
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

        // Row 4: Country-Grouped Camera Preview Cards (with HD vs SD split per Country)
        val visibleCameras = state.stripCameras
        val countryGroups = state.stripCountryGroups
        val activeId = state.activeCamera?.id
        val favIds = state.favoriteIds
        if (visibleCameras.isEmpty()) {
            EmptyStripStateCard(
                state = state,
                onAction = onAction,
            )
        } else {
            LazyRow(
                contentPadding = PaddingValues(horizontal = TvOverscanHorizontal),
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                verticalAlignment = Alignment.CenterVertically,
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
                            CameraPreviewCard(
                                camera = item,
                                isPlaying = item.id == activeId,
                                isFavorite = favIds.contains(item.id),
                                onSelect = { onAction(TvRemoteAction.SelectCamera(item.id)) },
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
            .padding(horizontal = 20.dp, vertical = 16.dp),
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
        modifier = Modifier.width(228.dp),
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

                // Bottom-left LIVE video vs SNAP snapshot transport badge
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
