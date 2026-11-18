package rs.belgrade.tvcams.player

import android.app.ActivityManager
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.hardware.display.DisplayManager
import android.view.Display
import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.annotation.OptIn
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.hls.HlsMediaSource
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import coil3.compose.AsyncImage
import coil3.network.NetworkHeaders
import coil3.network.httpHeaders
import coil3.request.ImageRequest
import coil3.request.allowRgb565
import coil3.size.Size
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import rs.belgrade.tvcams.R
import rs.belgrade.tvcams.data.stream.OkHttpStreamUrlResolver
import rs.belgrade.tvcams.domain.favorites.PerformanceMode
import rs.belgrade.tvcams.domain.model.Camera
import rs.belgrade.tvcams.domain.model.StreamDescriptor
import rs.belgrade.tvcams.domain.model.StreamTransportType
import rs.belgrade.tvcams.domain.oled.PixelOrbitOffset
import rs.belgrade.tvcams.domain.stream.FailoverDecision
import rs.belgrade.tvcams.domain.stream.ResolvedHlsStream
import rs.belgrade.tvcams.domain.stream.SnapshotUrlBuilder
import rs.belgrade.tvcams.domain.stream.StreamFailoverTracker
import java.time.LocalTime
import java.time.format.DateTimeFormatter

sealed interface PlaybackTelemetry {
    data class Connecting(val transport: StreamTransportType) : PlaybackTelemetry
    data class PlayingLive(
        val transport: StreamTransportType,
        val updatedTimeLabel: String? = null,
    ) : PlaybackTelemetry
    data class DegradedRetrying(val retryDelayMs: Long) : PlaybackTelemetry
}

private val CLOCK_FORMATTER: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm:ss")

@Composable
fun DualTransportCameraSurface(
    camera: Camera?,
    httpClient: OkHttpClient,
    streamResolver: OkHttpStreamUrlResolver,
    performanceMode: PerformanceMode,
    oledOrbitOffset: PixelOrbitOffset,
    onTelemetryChanged: (PlaybackTelemetry) -> Unit,
    modifier: Modifier = Modifier,
) {
    val hostView = LocalView.current
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    val isLowRamDevice = remember(context) {
        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
        am?.isLowRamDevice == true
    }
    val displayHeightPx = remember(context) {
        resolvePhysicalDisplayHeightPx(context)
    }
    val useLowSpecSurface = performanceMode.shouldUseLowSpecSurface(isLowRamDevice)
    val maxVideoHeightPx = performanceMode.maxVideoHeightPx(displayHeightPx, isLowRamDevice)
    val maxVideoWidthPx = (maxVideoHeightPx * 16) / 9

    DisposableEffect(hostView, camera != null) {
        val previous = hostView.keepScreenOn
        hostView.keepScreenOn = camera != null
        onDispose {
            hostView.keepScreenOn = previous
        }
    }

    val stageModifier = if (useLowSpecSurface) {
        modifier
            .fillMaxSize()
            .background(Color.Black)
    } else {
        modifier
            .fillMaxSize()
            .background(Color.Black)
            .clipToBounds()
            .graphicsLayer {
                scaleX = oledOrbitOffset.videoScale
                scaleY = oledOrbitOffset.videoScale
                translationX = oledOrbitOffset.dxDp.dp.toPx()
                translationY = oledOrbitOffset.dyDp.dp.toPx()
            }
    }

    Box(
        modifier = stageModifier,
        contentAlignment = Alignment.Center,
    ) {
        if (camera == null || camera.streams.isEmpty()) return@Box

        val camId = camera.id
        val posterUrl = camera.posterUrl
        val posterHeaders = camera.posterHeaders
        val camStreams = camera.streams

        var hasRenderedLiveFrame by remember(camId) { mutableStateOf(false) }
        val posterAlpha by animateFloatAsState(
            targetValue = if (hasRenderedLiveFrame) 0f else 1f,
            animationSpec = tween(durationMillis = if (useLowSpecSurface) 0 else 220),
            label = "posterUnderlayAlpha",
        )

        val posterRequest = remember(posterUrl, posterHeaders, maxVideoWidthPx, maxVideoHeightPx) {
            val networkHeaders = NetworkHeaders.Builder().apply {
                posterHeaders.orEmpty().forEach { (k, v) -> set(k, v) }
            }.build()
            ImageRequest.Builder(context)
                .data(posterUrl)
                .httpHeaders(networkHeaders)
                .size(Size(minOf(maxVideoWidthPx, 1280), minOf(maxVideoHeightPx, 720)))
                .allowRgb565(true)
                .build()
        }

        val tracker = remember(camId, camStreams) {
            StreamFailoverTracker(camStreams)
        }
        var activeStream by remember(camId, camStreams) {
            mutableStateOf(tracker.currentStream())
        }
        var attemptNonce by remember(camId) { mutableIntStateOf(0) }
        var pendingBackoffMs by remember(camId) { mutableStateOf<Long?>(null) }

        LaunchedEffect(camId, pendingBackoffMs, attemptNonce) {
            val waitMs = pendingBackoffMs ?: return@LaunchedEffect
            onTelemetryChanged(PlaybackTelemetry.DegradedRetrying(waitMs))
            delay(waitMs)
            pendingBackoffMs = null
        }

        val handleTransportFailure: () -> Unit = {
            val failedStream = activeStream
            if (failedStream.type != StreamTransportType.HLS &&
                failedStream.type != StreamTransportType.SNAPSHOT_POLL
            ) {
                coroutineScope.launch {
                    streamResolver.invalidate(failedStream.url)
                }
            }
            when (val decision = tracker.onTransportFailure()) {
                is FailoverDecision.SwitchStreamImmediately -> {
                    activeStream = decision.nextStream
                    attemptNonce += 1
                }
                is FailoverDecision.RetryAfterBackoff -> {
                    activeStream = decision.nextStream
                    pendingBackoffMs = decision.delayMillis
                    attemptNonce += 1
                }
            }
        }

        if (pendingBackoffMs == null) {
            if (activeStream.type.isLiveVideo) {
                HlsStreamRenderer(
                    stream = activeStream,
                    attemptKey = attemptNonce,
                    httpClient = httpClient,
                    streamResolver = streamResolver,
                    useLowSpecSurface = useLowSpecSurface,
                    maxVideoWidthPx = maxVideoWidthPx,
                    maxVideoHeightPx = maxVideoHeightPx,
                    isVisible = hasRenderedLiveFrame,
                    onFirstFrame = {
                        hasRenderedLiveFrame = true
                    },
                    onHealthy = {
                        tracker.onPlaybackHealthy()
                        onTelemetryChanged(PlaybackTelemetry.PlayingLive(activeStream.type))
                    },
                    onConnecting = {
                        onTelemetryChanged(PlaybackTelemetry.Connecting(activeStream.type))
                    },
                    onError = handleTransportFailure,
                )
            } else {
                DoubleBufferedSnapshotRenderer(
                    stream = activeStream,
                    attemptKey = attemptNonce,
                    httpClient = httpClient,
                    maxWidthPx = maxVideoWidthPx,
                    maxHeightPx = maxVideoHeightPx,
                    useRgb565 = performanceMode != PerformanceMode.MAX_QUALITY,
                    onFrameRendered = { clock ->
                        hasRenderedLiveFrame = true
                        tracker.onPlaybackHealthy()
                        onTelemetryChanged(
                            PlaybackTelemetry.PlayingLive(
                                transport = StreamTransportType.SNAPSHOT_POLL,
                                updatedTimeLabel = clock,
                            ),
                        )
                    },
                    onConnecting = {
                        onTelemetryChanged(
                            PlaybackTelemetry.Connecting(StreamTransportType.SNAPSHOT_POLL),
                        )
                    },
                    onError = handleTransportFailure,
                )
            }
        }

        // Poster overlay covers initial buffer/decode handshake so zapping never flashes black
        if (posterAlpha > 0.01f) {
            AsyncImage(
                model = posterRequest,
                contentDescription = null,
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .fillMaxSize()
                    .alpha(posterAlpha),
            )
        }
    }
}

private fun resolvePhysicalDisplayHeightPx(context: Context): Int {
    val dm = context.getSystemService(Context.DISPLAY_SERVICE) as? DisplayManager
    val defaultDisplay = dm?.getDisplay(Display.DEFAULT_DISPLAY)
    val modeHeight = defaultDisplay?.mode?.physicalHeight ?: 0
    if (modeHeight >= 720) return modeHeight
    return context.resources.displayMetrics.heightPixels.coerceAtLeast(720)
}

@OptIn(UnstableApi::class)
@Composable
private fun HlsStreamRenderer(
    stream: StreamDescriptor,
    attemptKey: Int,
    httpClient: OkHttpClient,
    streamResolver: OkHttpStreamUrlResolver,
    useLowSpecSurface: Boolean,
    maxVideoWidthPx: Int,
    maxVideoHeightPx: Int,
    isVisible: Boolean,
    onFirstFrame: () -> Unit,
    onHealthy: () -> Unit,
    onConnecting: () -> Unit,
    onError: () -> Unit,
) {
    val context = LocalContext.current
    var resolvedStream by remember(stream.type, stream.url, attemptKey) {
        mutableStateOf<ResolvedHlsStream?>(
            if (stream.type == StreamTransportType.HLS) {
                ResolvedHlsStream(stream.url, stream.headers.orEmpty())
            } else {
                null
            },
        )
    }

    LaunchedEffect(stream.type, stream.url, attemptKey) {
        if (stream.type == StreamTransportType.HLS) {
            resolvedStream = ResolvedHlsStream(stream.url, stream.headers.orEmpty())
            return@LaunchedEffect
        }
        onConnecting()
        val fetched = streamResolver.resolveToHls(stream)
        if (fetched == null) {
            onError()
        } else {
            resolvedStream = fetched
        }
    }

    val exoPlayer = remember(context, useLowSpecSurface) {
        val loadControl = DefaultLoadControl.Builder()
            .setBufferDurationsMs(
                /* minBufferMs = */ 3_000,
                /* maxBufferMs = */ if (useLowSpecSurface) 8_000 else 12_000,
                /* bufferForPlaybackMs = */ 1_000,
                /* bufferForPlaybackAfterRebufferMs = */ 2_000,
            )
            .setPrioritizeTimeOverSizeThresholds(true)
            .setBackBuffer(0, false)
            .build()

        ExoPlayer.Builder(context)
            .setLoadControl(loadControl)
            .build()
            .apply {
                volume = 0f
                playWhenReady = true
            }
    }

    LaunchedEffect(exoPlayer, maxVideoWidthPx, maxVideoHeightPx) {
        exoPlayer.trackSelectionParameters = exoPlayer.trackSelectionParameters
            .buildUpon()
            .setMaxVideoSize(maxVideoWidthPx, maxVideoHeightPx)
            .build()
    }

    DisposableEffect(exoPlayer) {
        onDispose {
            exoPlayer.release()
        }
    }

    val activeResolved = resolvedStream
    DisposableEffect(exoPlayer, activeResolved, attemptKey) {
        if (activeResolved == null) {
            return@DisposableEffect onDispose {}
        }
        onConnecting()
        val listener = object : Player.Listener {
            override fun onRenderedFirstFrame() {
                onFirstFrame()
                onHealthy()
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_READY) {
                    onHealthy()
                } else if (playbackState == Player.STATE_BUFFERING) {
                    onConnecting()
                }
            }

            override fun onPlayerError(error: PlaybackException) {
                onError()
            }
        }
        exoPlayer.addListener(listener)

        val mergedHeaders = HashMap<String, String>(stream.headers.orEmpty()).apply {
            putAll(activeResolved.headers)
        }
        val dataSourceFactory = OkHttpDataSource.Factory(httpClient)
            .setDefaultRequestProperties(mergedHeaders)
        val mediaSource = HlsMediaSource.Factory(dataSourceFactory)
            .setAllowChunklessPreparation(true)
            .createMediaSource(MediaItem.fromUri(activeResolved.hlsUrl))

        exoPlayer.setMediaSource(mediaSource)
        exoPlayer.prepare()
        exoPlayer.playWhenReady = true

        onDispose {
            exoPlayer.removeListener(listener)
            exoPlayer.stop()
        }
    }

    val layoutRes = if (useLowSpecSurface) {
        R.layout.player_view_surface
    } else {
        R.layout.player_view_texture
    }

    AndroidView(
        modifier = if (useLowSpecSurface) {
            Modifier.fillMaxSize()
        } else {
            Modifier
                .fillMaxSize()
                .alpha(if (isVisible) 1f else 0f)
        },
        factory = { viewContext ->
            (LayoutInflater.from(viewContext).inflate(
                layoutRes,
                FrameLayout(viewContext),
                false,
            ) as PlayerView).apply {
                useController = false
                resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                isFocusable = false
                isFocusableInTouchMode = false
                descendantFocusability = ViewGroup.FOCUS_BLOCK_DESCENDANTS
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT,
                )
                player = exoPlayer
            }
        },
        update = { playerView ->
            if (playerView.player !== exoPlayer) {
                playerView.player = exoPlayer
            }
        },
    )
}

@Composable
private fun DoubleBufferedSnapshotRenderer(
    stream: StreamDescriptor,
    attemptKey: Int,
    httpClient: OkHttpClient,
    maxWidthPx: Int,
    maxHeightPx: Int,
    useRgb565: Boolean,
    onFrameRendered: (String) -> Unit,
    onConnecting: () -> Unit,
    onError: () -> Unit,
) {
    var displayedBitmap by remember(stream.url) { mutableStateOf<ImageBitmap?>(null) }

    LaunchedEffect(stream.url, stream.headers, stream.intervalMs, maxWidthPx, maxHeightPx, useRgb565, attemptKey) {
        if (displayedBitmap == null) {
            onConnecting()
        }
        val pollInterval = (stream.intervalMs ?: 1500L).coerceAtLeast(800L)

        while (isActive) {
            val polledUrl = SnapshotUrlBuilder.buildPolledUrl(
                baseUrl = stream.url,
                paramName = stream.cacheBusterParam,
                epochMillis = System.currentTimeMillis(),
            )
            val frame = fetchSnapshotBitmap(
                httpClient = httpClient,
                url = polledUrl,
                headers = stream.headers.orEmpty(),
                maxWidthPx = maxWidthPx,
                maxHeightPx = maxHeightPx,
                useRgb565 = useRgb565,
            )
            if (frame == null) {
                onError()
                return@LaunchedEffect
            }

            displayedBitmap = frame
            onFrameRendered(LocalTime.now().format(CLOCK_FORMATTER))
            delay(pollInterval)
        }
    }

    val currentFrame = displayedBitmap
    if (currentFrame != null) {
        Image(
            bitmap = currentFrame,
            contentDescription = null,
            contentScale = ContentScale.Fit,
            modifier = Modifier.fillMaxSize(),
        )
    }
}

private suspend fun fetchSnapshotBitmap(
    httpClient: OkHttpClient,
    url: String,
    headers: Map<String, String>,
    maxWidthPx: Int,
    maxHeightPx: Int,
    useRgb565: Boolean,
): ImageBitmap? = withContext(Dispatchers.IO) {
    val requestBuilder = Request.Builder().url(url).get()
    headers.forEach { (key, value) -> requestBuilder.header(key, value) }

    runCatching {
        httpClient.newCall(requestBuilder.build()).execute().use { response ->
            if (!response.isSuccessful) return@use null
            val bytes = response.body?.bytes() ?: return@use null
            withContext(Dispatchers.Default) {
                val boundsOpts = BitmapFactory.Options().apply {
                    inJustDecodeBounds = true
                }
                BitmapFactory.decodeByteArray(bytes, 0, bytes.size, boundsOpts)
                val sampleSize = calculateInSampleSize(
                    rawWidth = boundsOpts.outWidth,
                    rawHeight = boundsOpts.outHeight,
                    reqWidth = maxWidthPx,
                    reqHeight = maxHeightPx,
                )
                val decodeOpts = BitmapFactory.Options().apply {
                    inSampleSize = sampleSize
                    inPreferredConfig = if (useRgb565) Bitmap.Config.RGB_565 else Bitmap.Config.ARGB_8888
                }
                BitmapFactory.decodeByteArray(bytes, 0, bytes.size, decodeOpts)?.asImageBitmap()
            }
        }
    }.getOrNull()
}

private fun calculateInSampleSize(
    rawWidth: Int,
    rawHeight: Int,
    reqWidth: Int,
    reqHeight: Int,
): Int {
    var sampleSize = 1
    if (rawHeight > reqHeight || rawWidth > reqWidth) {
        val halfHeight = rawHeight / 2
        val halfWidth = rawWidth / 2
        while ((halfHeight / sampleSize) >= reqHeight && (halfWidth / sampleSize) >= reqWidth) {
            sampleSize *= 2
        }
    }
    return sampleSize.coerceAtLeast(1)
}
