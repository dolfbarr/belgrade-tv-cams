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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
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
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import okhttp3.OkHttpClient
import rs.belgrade.tvcams.R
import rs.belgrade.tvcams.domain.airquality.AirQualityBand
import rs.belgrade.tvcams.domain.airquality.CurrentAirQuality
import rs.belgrade.tvcams.domain.favorites.QualityFilter
import rs.belgrade.tvcams.domain.favorites.ZapScope
import rs.belgrade.tvcams.domain.model.Camera
import rs.belgrade.tvcams.domain.model.CameraCategory
import rs.belgrade.tvcams.domain.model.SerbiaCity
import rs.belgrade.tvcams.domain.model.StreamTransportType
import rs.belgrade.tvcams.domain.model.VideoQuality
import rs.belgrade.tvcams.domain.oled.PixelOrbitCalculator
import rs.belgrade.tvcams.domain.oled.PixelOrbitOffset
import rs.belgrade.tvcams.domain.solar.SolarPhaseCalculator
import rs.belgrade.tvcams.domain.solar.SolarStatus
import rs.belgrade.tvcams.domain.solar.SolarWindow
import rs.belgrade.tvcams.domain.time.CameraClockFormatter
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
private val StageScrimBottom = Color(0xDD080D1A)
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
    onAction: (TvRemoteAction) -> Unit,
    onTelemetryChanged: (PlaybackTelemetry) -> Unit,
    enableBackHandler: Boolean = true,
) {
    val stageFocusRequester = remember { FocusRequester() }
    val keyRouter = remember { TvKeyRouter() }
    val activeCam = state.activeCamera
    val timezoneId = activeCam?.timezone ?: "Europe/Belgrade"

    val clockState = rememberCameraClockText(
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
                decision.action?.let(onAction)
                decision.consumed
            }
            .focusRequester(stageFocusRequester)
            .focusable(),
    ) {
        // Layer 0: Edge-to-edge 1920x1080 stream surface with poster underlay & OLED pixel orbit
        DualTransportCameraSurface(
            camera = streamCamera ?: activeCam,
            httpClient = httpClient,
            oledOrbitOffset = oledOrbit,
            onTelemetryChanged = onTelemetryChanged,
            modifier = Modifier.fillMaxSize(),
        )

        // Layer 1A: Top-Left Ambient Clock, Weather, Air Quality & Solar Bar
        val clockText = if (state.showClockOverlay) clockState.value else null
        val weatherSummary = if (state.showWeatherOverlay) currentWeather?.formattedSummary() else null
        val aqiModel = if (state.showAirQualityOverlay) currentAirQuality else null
        val solarModel = if (state.showSolarOverlay) solarState.value else null

        if (!clockText.isNullOrBlank() ||
            !weatherSummary.isNullOrBlank() ||
            aqiModel != null ||
            solarModel != null
        ) {
            AmbientClockWeatherBar(
                clockText = clockText,
                weatherSummary = weatherSummary,
                airQuality = aqiModel,
                solarStatus = solarModel,
                cityLabel = activeCam?.city?.displayLabel(),
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(
                        start = TvOverscanHorizontal,
                        end = TvOverscanHorizontal,
                        top = 38.dp,
                    )
                    .offset(x = oledOrbit.dxDp.dp, y = oledOrbit.dyDp.dp),
            )
        }

        // Layer 1B: Non-blocking top-right status / notice badges inside TV safe area
        TopStatusLayer(
            noticeMessage = state.noticeMessage,
            telemetry = telemetry,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(horizontal = TvOverscanHorizontal, vertical = TvOverscanVertical)
                .offset(x = oledOrbit.dxDp.dp, y = oledOrbit.dyDp.dp),
        )

        // Layer 2: Street & Camera Info HUD (hidden completely in Pure Camera Mode)
        AnimatedVisibility(
            visible = state.overlayMode == OverlayMode.HUD_VISIBLE && activeCam != null,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.BottomStart),
        ) {
            if (activeCam != null) {
                StreetInfoHud(
                    camera = activeCam,
                    positionOneBased = state.activeZapPositionOneBased,
                    totalInScope = state.zapPlaylist.size,
                    scopeLabel = state.zapScope.displayLabel(),
                    isFavorite = state.isCurrentFavorite,
                    telemetry = telemetry,
                )
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
                onAction = onAction,
            )
        }
    }
}

@Composable
private fun rememberCameraClockText(
    timezoneId: String,
    enabled: Boolean,
): State<String> = produceState(
    initialValue = if (enabled) CameraClockFormatter.formatTime(Instant.now(), timezoneId) else "",
    key1 = timezoneId,
    key2 = enabled,
) {
    if (!enabled) {
        value = ""
        return@produceState
    }
    while (isActive) {
        value = CameraClockFormatter.formatTime(Instant.now(), timezoneId)
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
    clockText: String?,
    weatherSummary: String?,
    airQuality: CurrentAirQuality?,
    solarStatus: SolarStatus?,
    cityLabel: String?,
    modifier: Modifier = Modifier,
) {
    var hasPreceding = false

    Row(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .background(PillSurface)
            .border(1.dp, PillBorder, RoundedCornerShape(10.dp))
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        if (!clockText.isNullOrBlank()) {
            Text(
                text = clockText,
                style = TabularStyle,
                color = TextPrimary,
                fontSize = 19.sp,
                fontWeight = FontWeight.Bold,
            )
            hasPreceding = true
        }

        if (!weatherSummary.isNullOrBlank()) {
            if (hasPreceding) SeparatorDot()
            val labeledWeather = if (!cityLabel.isNullOrBlank()) {
                "$cityLabel · $weatherSummary"
            } else {
                weatherSummary
            }
            Text(
                text = labeledWeather,
                style = TabularStyle,
                color = TextSecondary,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
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
                    text = airQuality.formattedSummary(),
                    style = TabularStyle,
                    color = TextPrimary,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                )
            }
            hasPreceding = true
        }

        if (solarStatus != null) {
            if (hasPreceding) SeparatorDot()
            val isGolden = solarStatus.isGoldenHour
            Text(
                text = solarStatus.summaryLabel,
                style = TabularStyle,
                color = if (isGolden) AccentAmber else TextSecondary,
                fontSize = 14.sp,
                fontWeight = if (isGolden) FontWeight.SemiBold else FontWeight.Medium,
                maxLines = 1,
            )
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
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.End,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (noticeMessage != null) {
            StatusPill(
                text = noticeMessage,
                accentColor = AccentSky,
            )
        }
        if (telemetry is PlaybackTelemetry.DegradedRetrying) {
            StatusPill(
                text = stringResource(R.string.status_retrying),
                accentColor = AccentAmber,
            )
        }
    }
}

@Composable
private fun StatusPill(
    text: String,
    accentColor: Color,
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
        Box(
            modifier = Modifier
                .width(8.dp)
                .height(8.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(accentColor),
        )
        Text(
            text = text,
            color = TextPrimary,
            fontSize = 15.sp,
            fontWeight = FontWeight.Medium,
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
    telemetry: PlaybackTelemetry,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                Brush.verticalGradient(
                    colors = listOf(StageScrimTop, StageScrimBottom),
                ),
            )
            .padding(
                start = TvOverscanHorizontal,
                end = TvOverscanHorizontal,
                top = 44.dp,
                bottom = TvOverscanVertical,
            ),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    text = "%02d / %02d · %s".format(positionOneBased, totalInScope, scopeLabel),
                    style = TabularStyle,
                    color = AccentSky,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                )

                CityBadge(cityLabel = camera.city.displayLabel())

                QualityBadge(quality = camera.resolvedQuality)

                Text(
                    text = camera.category.displayLabel(),
                    color = TextSecondary,
                    fontSize = 14.sp,
                )

                TransportBadge(telemetry = telemetry)

                if (isFavorite) {
                    Text(
                        text = "★ Favorite",
                        color = AccentAmber,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }

            Text(
                text = camera.name,
                color = TextPrimary,
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )

            Box(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = "${camera.streets.joinToString(" · ")} — ${camera.municipality}",
                    color = TextPrimary,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            Box(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = camera.direction,
                    color = TextSecondary,
                    fontSize = 15.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = stringResource(R.string.hud_remote_hints),
                color = TextMuted,
                fontSize = 13.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun CityBadge(cityLabel: String) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(Color(0x3338BDF8))
            .border(1.dp, Color(0x6638BDF8), RoundedCornerShape(6.dp))
            .padding(horizontal = 9.dp, vertical = 3.dp),
    ) {
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
        is PlaybackTelemetry.PlayingLive -> when (telemetry.transport) {
            StreamTransportType.HLS -> AccentLive to stringResource(R.string.badge_live_hls)
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
    onAction: (TvRemoteAction) -> Unit,
) {
    val defaultCityChipFocusRequester = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        defaultCityChipFocusRequester.requestFocus()
    }

    val allCameras = state.cameras
    val qualityPool = state.qualityFilteredCameras
    val belgradeCount = remember(qualityPool) { qualityPool.count { it.city == SerbiaCity.BELGRADE } }
    val zemunCount = remember(qualityPool) { qualityPool.count { it.city == SerbiaCity.ZEMUN } }
    val noviSadCount = remember(qualityPool) { qualityPool.count { it.city == SerbiaCity.NOVI_SAD } }
    val suboticaCount = remember(qualityPool) { qualityPool.count { it.city == SerbiaCity.SUBOTICA } }
    val nisCount = remember(qualityPool) { qualityPool.count { it.city == SerbiaCity.NIS } }
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
                    0.16f to Color(0xDF080D1A),
                    1.00f to Color(0xFA080D1A),
                ),
            )
            .padding(
                top = 32.dp,
                bottom = TvOverscanVertical,
            ),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        // Row 1: Quality Tier Filter, Display Overlays (Clock, Weather, AQI, Sun, OLED guard) & Zap Scope
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
        }

        // Row 2: Serbia City Tabs (including Zemun) & Street Categories
        val currentStripFilter = state.stripFilter
        val filters: List<Pair<StripFilter, String>> = listOf(
            StripFilter.All to "${stringResource(R.string.category_all)} (${qualityPool.size})",
            StripFilter.Favorites to "${stringResource(R.string.category_favorites)} ($favCount)",
            StripFilter.City(SerbiaCity.BELGRADE) to "${stringResource(R.string.city_belgrade)} ($belgradeCount)",
            StripFilter.City(SerbiaCity.ZEMUN) to "${stringResource(R.string.city_zemun)} ($zemunCount)",
            StripFilter.City(SerbiaCity.NOVI_SAD) to "${stringResource(R.string.city_novi_sad)} ($noviSadCount)",
            StripFilter.City(SerbiaCity.SUBOTICA) to "${stringResource(R.string.city_subotica)} ($suboticaCount)",
            StripFilter.City(SerbiaCity.NIS) to "${stringResource(R.string.city_nis)} ($nisCount)",
            StripFilter.Category(CameraCategory.BRIDGES) to stringResource(R.string.category_bridges),
            StripFilter.Category(CameraCategory.SQUARES) to stringResource(R.string.category_squares),
            StripFilter.Category(CameraCategory.BOULEVARDS) to stringResource(R.string.category_boulevards),
            StripFilter.Category(CameraCategory.HIGHWAYS) to stringResource(R.string.category_highways),
        )

        LazyRow(
            contentPadding = PaddingValues(horizontal = TvOverscanHorizontal),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            items(filters.size) { idx ->
                val (filterItem, title) = filters[idx]
                val selected = currentStripFilter == filterItem
                val chipModifier = if (idx == 0) {
                    Modifier.focusRequester(defaultCityChipFocusRequester)
                } else {
                    Modifier
                }
                FilterChip(
                    selected = selected,
                    onClick = { onAction(TvRemoteAction.SelectStripFilter(filterItem)) },
                    colors = chipColors,
                    modifier = chipModifier,
                ) {
                    Text(text = title)
                }
            }
        }

        // Row 3: Split HD vs SD Camera Preview Cards
        val visibleCameras = state.stripCameras
        val hdCameras = state.hdStripCameras
        val sdCameras = state.sdStripCameras
        val activeId = state.activeCamera?.id
        val favIds = state.favoriteIds
        if (visibleCameras.isEmpty()) {
            if (state.stripFilter == StripFilter.Favorites && state.favoriteIds.isEmpty()) {
                EmptyFavoritesStripNotice(
                    onBrowseAll = { onAction(TvRemoteAction.SelectStripFilter(StripFilter.All)) },
                )
            } else {
                EmptyQualityStripNotice(
                    onCycleQuality = { onAction(TvRemoteAction.CycleQualityFilter) },
                )
            }
        } else {
            LazyRow(
                contentPadding = PaddingValues(horizontal = TvOverscanHorizontal),
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (hdCameras.isNotEmpty()) {
                    item(key = "section_header_hd") {
                        QualitySectionCard(
                            isHd = true,
                            count = hdCameras.size,
                        )
                    }
                    items(
                        items = hdCameras,
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
                if (sdCameras.isNotEmpty()) {
                    item(key = "section_header_sd") {
                        QualitySectionCard(
                            isHd = false,
                            count = sdCameras.size,
                        )
                    }
                    items(
                        items = sdCameras,
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

@Composable
private fun QualitySectionCard(
    isHd: Boolean,
    count: Int,
) {
    val accentColor = if (isHd) Color(0xFF34D399) else TextMuted
    val borderColor = if (isHd) Color(0x5510B981) else PillBorder
    val containerColor = if (isHd) Color(0xD90A1E24) else Color(0xD90F172A)

    Column(
        modifier = Modifier
            .width(120.dp)
            .height(182.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(containerColor)
            .border(1.dp, borderColor, RoundedCornerShape(12.dp))
            .padding(horizontal = 12.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.SpaceBetween,
    ) {
        Box(
            modifier = Modifier
                .width(28.dp)
                .height(4.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(accentColor),
        )

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
private fun EmptyQualityStripNotice(
    onCycleQuality: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = TvOverscanHorizontal, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = stringResource(R.string.empty_quality_heading),
            color = TextPrimary,
            fontSize = 18.sp,
            fontWeight = FontWeight.SemiBold,
        )
        Text(
            text = stringResource(R.string.empty_quality_body),
            color = TextSecondary,
            fontSize = 14.sp,
        )
        Spacer(modifier = Modifier.height(4.dp))
        Button(onClick = onCycleQuality) {
            Text(text = stringResource(R.string.empty_quality_action))
        }
    }
}

@Composable
private fun EmptyFavoritesStripNotice(
    onBrowseAll: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = TvOverscanHorizontal, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = stringResource(R.string.empty_favorites_heading),
            color = TextPrimary,
            fontSize = 18.sp,
            fontWeight = FontWeight.SemiBold,
        )
        Text(
            text = stringResource(R.string.empty_favorites_body),
            color = TextSecondary,
            fontSize = 14.sp,
        )
        Spacer(modifier = Modifier.height(4.dp))
        Button(onClick = onBrowseAll) {
            Text(text = stringResource(R.string.empty_favorites_action))
        }
    }
}

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
            .build()
    }

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
                    .background(Color(0xFF0F172A)),
            ) {
                AsyncImage(
                    model = imageRequest,
                    contentDescription = camera.name,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )

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

                val camQuality = camera.resolvedQuality
                val badgeTextColor = when (camQuality) {
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
                    "${camera.city.displayLabel()} · $firstStreet"
                } else {
                    camera.city.displayLabel()
                }
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
