package rs.belgrade.tvcams.ui.tv

import android.view.KeyEvent
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Button
import androidx.tv.material3.Card
import androidx.tv.material3.CardDefaults
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.FilterChip
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import coil3.compose.AsyncImage
import coil3.network.NetworkHeaders
import coil3.network.httpHeaders
import coil3.request.ImageRequest
import okhttp3.OkHttpClient
import rs.belgrade.tvcams.R
import rs.belgrade.tvcams.domain.model.Camera
import rs.belgrade.tvcams.domain.model.CameraCategory
import rs.belgrade.tvcams.domain.model.StreamTransportType
import rs.belgrade.tvcams.domain.tv.OverlayMode
import rs.belgrade.tvcams.domain.tv.StripFilter
import rs.belgrade.tvcams.domain.tv.TvRemoteAction
import rs.belgrade.tvcams.domain.tv.TvStageState
import rs.belgrade.tvcams.player.DualTransportCameraSurface
import rs.belgrade.tvcams.player.PlaybackTelemetry

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

@Composable
fun CameraStageScreen(
    state: TvStageState,
    streamCamera: Camera?,
    telemetry: PlaybackTelemetry,
    httpClient: OkHttpClient,
    onAction: (TvRemoteAction) -> Unit,
    onTelemetryChanged: (PlaybackTelemetry) -> Unit,
) {
    val stageFocusRequester = remember { FocusRequester() }
    var okLongPressConsumed by remember { mutableStateOf(false) }

    LaunchedEffect(state.overlayMode) {
        if (state.overlayMode != OverlayMode.QUICK_STRIP) {
            stageFocusRequester.requestFocus()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .focusRequester(stageFocusRequester)
            .focusable()
            .onPreviewKeyEvent { keyEvent ->
                handleTvRemoteKeyEvent(
                    nativeEvent = keyEvent.nativeKeyEvent,
                    eventType = keyEvent.type,
                    overlayMode = state.overlayMode,
                    okLongPressConsumed = okLongPressConsumed,
                    onSetLongPressConsumed = { okLongPressConsumed = it },
                    onAction = onAction,
                )
            },
    ) {
        // Layer 0: Permanent edge-to-edge 1920x1080 camera stream surface (zero layout shift)
        DualTransportCameraSurface(
            camera = streamCamera ?: state.activeCamera,
            httpClient = httpClient,
            onTelemetryChanged = onTelemetryChanged,
            modifier = Modifier.fillMaxSize(),
        )

        // Layer 1: Non-blocking top-right status / notice badges inside TV safe area
        TopStatusLayer(
            noticeMessage = state.noticeMessage,
            telemetry = telemetry,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(horizontal = TvOverscanHorizontal, vertical = TvOverscanVertical),
        )

        // Layer 2: Street & Camera Info HUD (hidden completely in Pure Camera Mode)
        AnimatedVisibility(
            visible = state.overlayMode == OverlayMode.HUD_VISIBLE && state.activeCamera != null,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.BottomStart),
        ) {
            val cam = state.activeCamera
            if (cam != null) {
                StreetInfoHud(
                    camera = cam,
                    positionOneBased = state.activeZapPositionOneBased,
                    totalInScope = state.zapPlaylist.size,
                    scopeLabel = state.zapScope.displayLabel(),
                    isFavorite = state.isCurrentFavorite,
                    telemetry = telemetry,
                )
            }
        }

        // Layer 3: Bottom Quick Strip Camera Carousel (DPAD_DOWN)
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

private fun handleTvRemoteKeyEvent(
    nativeEvent: KeyEvent,
    eventType: KeyEventType,
    overlayMode: OverlayMode,
    okLongPressConsumed: Boolean,
    onSetLongPressConsumed: (Boolean) -> Unit,
    onAction: (TvRemoteAction) -> Unit,
): Boolean {
    val keyCode = nativeEvent.keyCode

    // Always support dedicated TV channel rockers and info/bookmark buttons
    if (eventType == KeyEventType.KeyDown && nativeEvent.repeatCount == 0) {
        when (keyCode) {
            KeyEvent.KEYCODE_CHANNEL_UP -> {
                onAction(TvRemoteAction.ZapNext)
                return true
            }
            KeyEvent.KEYCODE_CHANNEL_DOWN -> {
                onAction(TvRemoteAction.ZapPrevious)
                return true
            }
            KeyEvent.KEYCODE_INFO -> {
                onAction(TvRemoteAction.ToggleInfoOrSelect)
                return true
            }
            KeyEvent.KEYCODE_BOOKMARK -> {
                onAction(TvRemoteAction.ToggleFavoriteCurrent)
                return true
            }
            KeyEvent.KEYCODE_MENU,
            KeyEvent.KEYCODE_GUIDE,
            -> {
                onAction(TvRemoteAction.OpenQuickStrip)
                return true
            }
            KeyEvent.KEYCODE_BACK -> {
                onAction(TvRemoteAction.NavigateBack)
                return true
            }
        }
    }

    // When Quick Strip is open, let Compose focus system traverse chips & camera cards
    if (overlayMode == OverlayMode.QUICK_STRIP) {
        return false
    }

    // Stage D-pad handling in HUD_VISIBLE and PURE_CAMERA modes
    when (keyCode) {
        KeyEvent.KEYCODE_DPAD_CENTER,
        KeyEvent.KEYCODE_ENTER,
        KeyEvent.KEYCODE_NUMPAD_ENTER,
        -> {
            if (eventType == KeyEventType.KeyDown) {
                if (nativeEvent.repeatCount == 0) {
                    onSetLongPressConsumed(false)
                } else if (!okLongPressConsumed && nativeEvent.repeatCount >= 1) {
                    onSetLongPressConsumed(true)
                    onAction(TvRemoteAction.ToggleFavoriteCurrent)
                }
                return true
            }
            if (eventType == KeyEventType.KeyUp) {
                if (!okLongPressConsumed) {
                    onAction(TvRemoteAction.ToggleInfoOrSelect)
                }
                onSetLongPressConsumed(false)
                return true
            }
        }

        KeyEvent.KEYCODE_DPAD_RIGHT -> if (eventType == KeyEventType.KeyDown) {
            onAction(TvRemoteAction.ZapNext)
            return true
        }

        KeyEvent.KEYCODE_DPAD_LEFT -> if (eventType == KeyEventType.KeyDown) {
            onAction(TvRemoteAction.ZapPrevious)
            return true
        }

        KeyEvent.KEYCODE_DPAD_DOWN -> if (eventType == KeyEventType.KeyDown && nativeEvent.repeatCount == 0) {
            onAction(TvRemoteAction.OpenQuickStrip)
            return true
        }

        KeyEvent.KEYCODE_DPAD_UP -> if (eventType == KeyEventType.KeyDown && nativeEvent.repeatCount == 0) {
            onAction(TvRemoteAction.ToggleZapScope)
            return true
        }
    }

    return false
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
                    color = AccentSky,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                )

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
    val firstChipFocusRequester = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        firstChipFocusRequester.requestFocus()
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                Brush.verticalGradient(
                    colors = listOf(StageScrimTop, Color(0xF2080D1A)),
                ),
            )
            .padding(
                top = 36.dp,
                bottom = TvOverscanVertical,
            ),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        val filters: List<Pair<StripFilter, String>> = listOf(
            StripFilter.All to stringResource(R.string.category_all),
            StripFilter.Favorites to "${stringResource(R.string.category_favorites)} (${state.favoriteIds.size})",
            StripFilter.Category(CameraCategory.BRIDGES) to stringResource(R.string.category_bridges),
            StripFilter.Category(CameraCategory.SQUARES) to stringResource(R.string.category_squares),
            StripFilter.Category(CameraCategory.BOULEVARDS) to stringResource(R.string.category_boulevards),
            StripFilter.Category(CameraCategory.HIGHWAYS) to stringResource(R.string.category_highways),
        )

        LazyRow(
            contentPadding = PaddingValues(horizontal = TvOverscanHorizontal),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            items(filters.size) { idx ->
                val (filterItem, title) = filters[idx]
                val selected = state.stripFilter == filterItem
                val chipModifier = if (idx == 0) {
                    Modifier.focusRequester(firstChipFocusRequester)
                } else {
                    Modifier
                }
                FilterChip(
                    selected = selected,
                    onClick = { onAction(TvRemoteAction.SelectStripFilter(filterItem)) },
                    modifier = chipModifier,
                ) {
                    Text(text = title)
                }
            }
        }

        val visibleCameras = state.stripCameras
        if (visibleCameras.isEmpty()) {
            EmptyFavoritesStripNotice(
                onBrowseAll = { onAction(TvRemoteAction.SelectStripFilter(StripFilter.All)) },
            )
        } else {
            LazyRow(
                contentPadding = PaddingValues(horizontal = TvOverscanHorizontal),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                items(
                    items = visibleCameras,
                    key = { it.id },
                ) { item ->
                    CameraPreviewCard(
                        camera = item,
                        isPlaying = item.id == state.activeCamera?.id,
                        isFavorite = state.favoriteIds.contains(item.id),
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
            .padding(horizontal = TvOverscanHorizontal, vertical = 16.dp),
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
    val imageRequest = remember(camera.posterUrl, camera.posterHeaders) {
        val networkHeaders = NetworkHeaders.Builder().apply {
            camera.posterHeaders.orEmpty().forEach { (k, v) -> set(k, v) }
        }.build()
        ImageRequest.Builder(context)
            .data(camera.posterUrl)
            .httpHeaders(networkHeaders)
            .build()
    }

    Card(
        onClick = onSelect,
        modifier = Modifier.width(248.dp),
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
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(
                    text = camera.name,
                    style = MaterialTheme.typography.titleSmall,
                    color = TextPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = camera.streets.firstOrNull().orEmpty(),
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}
