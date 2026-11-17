package rs.belgrade.tvcams.player

import android.graphics.BitmapFactory
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.annotation.OptIn
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.hls.HlsMediaSource
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import rs.belgrade.tvcams.R
import rs.belgrade.tvcams.domain.model.Camera
import rs.belgrade.tvcams.domain.model.StreamDescriptor
import rs.belgrade.tvcams.domain.model.StreamTransportType
import rs.belgrade.tvcams.domain.stream.FailoverDecision
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
    onTelemetryChanged: (PlaybackTelemetry) -> Unit,
    modifier: Modifier = Modifier,
) {
    val hostView = LocalView.current
    DisposableEffect(hostView, camera != null) {
        val previous = hostView.keepScreenOn
        hostView.keepScreenOn = camera != null
        onDispose {
            hostView.keepScreenOn = previous
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black),
        contentAlignment = Alignment.Center,
    ) {
        if (camera == null || camera.streams.isEmpty()) return@Box

        val tracker = remember(camera.id, camera.streams) {
            StreamFailoverTracker(camera.streams)
        }
        var activeStream by remember(camera.id, camera.streams) {
            mutableStateOf(tracker.currentStream())
        }
        var attemptNonce by remember(camera.id) { mutableIntStateOf(0) }
        var pendingBackoffMs by remember(camera.id) { mutableStateOf<Long?>(null) }

        LaunchedEffect(camera.id, pendingBackoffMs, attemptNonce) {
            val waitMs = pendingBackoffMs ?: return@LaunchedEffect
            onTelemetryChanged(PlaybackTelemetry.DegradedRetrying(waitMs))
            delay(waitMs)
            pendingBackoffMs = null
        }

        val handleTransportFailure: () -> Unit = {
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
            when (activeStream.type) {
                StreamTransportType.HLS -> HlsStreamRenderer(
                    stream = activeStream,
                    attemptKey = attemptNonce,
                    httpClient = httpClient,
                    onHealthy = {
                        tracker.onPlaybackHealthy()
                        onTelemetryChanged(PlaybackTelemetry.PlayingLive(StreamTransportType.HLS))
                    },
                    onConnecting = {
                        onTelemetryChanged(PlaybackTelemetry.Connecting(StreamTransportType.HLS))
                    },
                    onError = handleTransportFailure,
                )

                StreamTransportType.SNAPSHOT_POLL -> DoubleBufferedSnapshotRenderer(
                    stream = activeStream,
                    attemptKey = attemptNonce,
                    httpClient = httpClient,
                    onFrameRendered = { clock ->
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
    }
}

@OptIn(UnstableApi::class)
@Composable
private fun HlsStreamRenderer(
    stream: StreamDescriptor,
    attemptKey: Int,
    httpClient: OkHttpClient,
    onHealthy: () -> Unit,
    onConnecting: () -> Unit,
    onError: () -> Unit,
) {
    val context = LocalContext.current
    val exoPlayer = remember(context) {
        ExoPlayer.Builder(context).build().apply {
            volume = 0f
            playWhenReady = true
        }
    }

    DisposableEffect(exoPlayer) {
        onDispose {
            exoPlayer.release()
        }
    }

    DisposableEffect(exoPlayer, stream.url, stream.headers, attemptKey) {
        onConnecting()
        val listener = object : Player.Listener {
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

        val dataSourceFactory = OkHttpDataSource.Factory(httpClient)
            .setDefaultRequestProperties(stream.headers.orEmpty())
        val mediaSource = HlsMediaSource.Factory(dataSourceFactory)
            .setAllowChunklessPreparation(true)
            .createMediaSource(MediaItem.fromUri(stream.url))

        exoPlayer.setMediaSource(mediaSource)
        exoPlayer.prepare()
        exoPlayer.playWhenReady = true

        onDispose {
            exoPlayer.removeListener(listener)
            exoPlayer.stop()
        }
    }

    AndroidView(
        modifier = Modifier.fillMaxSize(),
        factory = { viewContext ->
            (LayoutInflater.from(viewContext).inflate(
                R.layout.player_view_texture,
                null,
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
    onFrameRendered: (String) -> Unit,
    onConnecting: () -> Unit,
    onError: () -> Unit,
) {
    var displayedBitmap by remember(stream.url) { mutableStateOf<ImageBitmap?>(null) }

    LaunchedEffect(stream.url, stream.headers, stream.intervalMs, attemptKey) {
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
            val frame = fetchSnapshotBitmap(httpClient, polledUrl, stream.headers.orEmpty())
            if (frame == null) {
                onError()
                return@LaunchedEffect
            }

            // Atomic front-buffer swap: old frame remains visible until new bitmap is ready
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
): ImageBitmap? = withContext(Dispatchers.IO) {
    val requestBuilder = Request.Builder().url(url).get()
    headers.forEach { (key, value) -> requestBuilder.header(key, value) }

    runCatching {
        httpClient.newCall(requestBuilder.build()).execute().use { response ->
            if (!response.isSuccessful) return@use null
            val bytes = response.body?.bytes() ?: return@use null
            withContext(Dispatchers.Default) {
                BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap()
            }
        }
    }.getOrNull()
}
