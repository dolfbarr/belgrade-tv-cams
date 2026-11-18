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
import rs.belgrade.tvcams.domain.favorites.ZapScope
import rs.belgrade.tvcams.domain.model.Camera
import rs.belgrade.tvcams.domain.model.CameraCategory
import rs.belgrade.tvcams.domain.model.SerbiaCity
import rs.belgrade.tvcams.domain.model.StreamTransportType
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
    httpClient: OkHttpClient,
    onAction: (TvRemoteAction) -> Unit,
    onTelemetryChanged: (PlaybackTelemetry) -> Unit,
) {
    val stageFocusRequester = remember { FocusRequester() }
    val keyRouter = remember { TvKeyRouter() }
    val activeCam = state.activeCamera
    val timezoneId = activeCam?.timezone ?: "Europe/Belgrade"
    val clockState = rememberCameraClockText(
        timezoneId = timezoneId,
        enabled = state.showClockOverlay,
    )

    BackHandler(enabled = true) {
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
                val phase = when (keyEvent.type) {
                    KeyEventType.KeyDown -> TvKeyPhase.DOWN
                    KeyEventType.KeyUp -> TvKeyPhase.UP
                    else -> return@onPreviewKeyEvent false
                }
                val decision = keyRouter.route(
                    keyCode = mapAndroidKeyCode(keyEvent.nativeKeyEvent.keyCode),
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
        // Layer 0: Permanent edge-to-edge 1920x1080 camera stream surface (zero layout shift)
        DualTransportCameraSurface(
            camera = streamCamera ?: activeCam,
            httpClient = httpClient,
            onTelemetryChanged = onTelemetryChanged,
            modifier = Modifier.fillMaxSize(),
        )

        // Layer 1A: Independent Top-Left Ambient Clock & Weather Bar (visible in HUD & Pure modes)
        val clockText = if (state.showClockOverlay) clockState.value else null
        val weatherSummary = if (state.showWeatherOverlay) currentWeather?.formattedSummary() else null
        if (!clockText.isNullOrBlank() || !weatherSummary.isNullOrBlank()) {
            AmbientClockWeatherBar(
                clockText = clockText,
                weatherSummary = weatherSummary,
                cityLabel = activeCam?.city?.displayLabel(),
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(
                        start = TvOverscanHorizontal,
                        end = TvOverscanHorizontal,
                        top = 38.dp,
                    ),
            )
        }

        // Layer 1B: Non-blocking top-right status / notice badges inside TV safe area
        TopStatusLayer(
            noticeMessage = state.noticeMessage,
            telemetry = telemetry,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(horizontal = TvOverscanHorizontal, vertical = TvOverscanVertical),
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
private fun AmbientClockWeatherBar(
    clockText: String?,
    weatherSummary: String?,
    cityLabel: String?,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .background(PillSurface)
            .border(1.dp, PillBorder, RoundedCornerShape(10.dp))
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (!clockText.isNullOrBlank()) {
            Text(
                text = clockText,
                style = TabularStyle,
                color = TextPrimary,
                fontSize = 19.sp,
                fontWeight = FontWeight.Bold,
            )
        }

        if (!clockText.isNullOrBlank() && !weatherSummary.isNullOrBlank()) {
            Box(
                modifier = Modifier
                    .width(4.dp)
                    .height(4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(TextMuted),
            )
        }

        if (!weatherSummary.isNullOrBlank()) {
            val labeledWeather = if (!cityLabel.isNullOrBlank()) {
                "$cityLabel · $weatherSummary"
            } else {
                weatherSummary
            }
            Text(
                text = labeledWeather,
                style = TabularStyle,
                color = TextSecondary,
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
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

            // Block container enforces clean text-overflow per layout invariants
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
    val belgradeCount = remember(allCameras) { allCameras.count { it.city == SerbiaCity.BELGRADE } }
    val noviSadCount = remember(allCameras) { allCameras.count { it.city == SerbiaCity.NOVI_SAD } }
    val suboticaCount = remember(allCameras) { allCameras.count { it.city == SerbiaCity.SUBOTICA } }
    val nisCount = remember(allCameras) { allCameras.count { it.city == SerbiaCity.NIS } }

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
        // Row 1: Display Overlays (Clock, Weather) & Full-Screen Zap Scope
        LazyRow(
            contentPadding = PaddingValues(horizontal = TvOverscanHorizontal),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
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

        // Row 2: Serbia City Tabs & Street Categories
        val currentStripFilter = state.stripFilter
        val filters: List<Pair<StripFilter, String>> = listOf(
            StripFilter.All to "${stringResource(R.string.category_all)} (${allCameras.size})",
            StripFilter.Favorites to "${stringResource(R.string.category_favorites)} (${state.favoriteIds.size})",
            StripFilter.City(SerbiaCity.BELGRADE) to "${stringResource(R.string.city_belgrade)} ($belgradeCount)",
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

        // Row 3: Filtered Camera Preview Cards
        val visibleCameras = state.stripCameras
        val activeId = state.activeCamera?.id
        val favIds = state.favoriteIds
        if (visibleCameras.isEmpty()) {
            EmptyFavoritesStripNotice(
                onBrowseAll = { onAction(TvRemoteAction.SelectStripFilter(StripFilter.All)) },
            )
        } else {
            LazyRow(
                contentPadding = PaddingValues(horizontal = TvOverscanHorizontal),
                horizontalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                items(
                    items = visibleCameras,
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
