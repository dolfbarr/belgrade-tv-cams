package rs.belgrade.tvcams.tvchannel

import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import androidx.core.content.edit
import androidx.core.graphics.createBitmap
import androidx.core.net.toUri
import androidx.tvprovider.media.tv.PreviewChannel
import androidx.tvprovider.media.tv.PreviewChannelHelper
import androidx.tvprovider.media.tv.PreviewProgram
import androidx.tvprovider.media.tv.TvContractCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import rs.belgrade.tvcams.BelgradeTvApplication
import rs.belgrade.tvcams.MainActivity
import rs.belgrade.tvcams.domain.model.Camera
import rs.belgrade.tvcams.domain.tv.HomeChannelSelector

class TvHomeChannelSynchronizer(
    private val appContext: Context,
) {
    private val mutex = Mutex()
    private val prefs = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    suspend fun syncChannel(
        cameras: List<Camera>,
        favoriteIds: Set<String>,
    ) = withContext(Dispatchers.IO) {
        if (cameras.isEmpty()) return@withContext

        mutex.withLock {
            runCatching {
                val selection = HomeChannelSelector.selectChannelCameras(
                    cameras = cameras,
                    favoriteIds = favoriteIds,
                    maxItems = 12,
                )
                val helper = PreviewChannelHelper(appContext)
                val appLinkIntent = Intent(appContext, MainActivity::class.java).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                }

                val channelBuilder = PreviewChannel.Builder()
                    .setDisplayName(selection.channelTitle)
                    .setDescription("Live street, bridge, and plaza cameras across Belgrade, Novi Sad, Subotica, and Niš")
                    .setAppLinkIntent(appLinkIntent)
                    .setInternalProviderId(CHANNEL_INTERNAL_ID)
                    .setLogo(createChannelLogoBitmap())

                val storedChannelId = prefs.getLong(KEY_CHANNEL_ID, -1L)
                val existingChannel = if (storedChannelId > 0L) {
                    helper.getPreviewChannel(storedChannelId)
                } else {
                    helper.allChannels.firstOrNull { it.internalProviderId == CHANNEL_INTERNAL_ID }
                }

                val channelId = if (existingChannel != null) {
                    val existingId = existingChannel.id
                    helper.updatePreviewChannel(existingId, channelBuilder.build())
                    prefs.edit { putLong(KEY_CHANNEL_ID, existingId) }
                    existingId
                } else {
                    val createdId = helper.publishDefaultChannel(channelBuilder.build())
                    prefs.edit { putLong(KEY_CHANNEL_ID, createdId) }
                    TvContractCompat.requestChannelBrowsable(appContext, createdId)
                    createdId
                }

                if (channelId <= 0L) return@runCatching

                appContext.contentResolver.delete(
                    TvContractCompat.buildPreviewProgramsUriForChannel(channelId),
                    null,
                    null,
                )

                val selectedList = selection.cameras
                for (index in selectedList.indices) {
                    val cam = selectedList[index]
                    val program = buildCameraPreviewProgram(
                        channelId = channelId,
                        cam = cam,
                        weight = selectedList.size - index,
                    )
                    helper.publishPreviewProgram(program)
                }
            }
        }
    }

    // Workaround for AndroidX tvprovider Kotlin Lint issue (BasePreviewProgram.Builder @RestrictTo)
    @SuppressLint("RestrictedApi")
    private fun buildCameraPreviewProgram(
        channelId: Long,
        cam: Camera,
        weight: Int,
    ): PreviewProgram {
        val deepLinkUri = "$DEEP_LINK_SCHEME://$DEEP_LINK_HOST/${cam.id}".toUri()
        val launchIntent = Intent(Intent.ACTION_VIEW, deepLinkUri).apply {
            setClassName(appContext.packageName, MainActivity::class.java.name)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        }
        val cityLabel = cam.city.displayLabel()
        val streetsLabel = cam.streets.joinToString(" · ")
        return PreviewProgram.Builder()
            .setChannelId(channelId)
            .setType(TvContractCompat.PreviewProgramColumns.TYPE_EVENT)
            .setLive(true)
            .setTitle(cam.name)
            .setDescription("$cityLabel · $streetsLabel")
            .setPosterArtUri(cam.posterUrl.toUri())
            .setPosterArtAspectRatio(TvContractCompat.PreviewProgramColumns.ASPECT_RATIO_16_9)
            .setIntent(launchIntent)
            .setInternalProviderId(cam.id)
            .setWeight(weight)
            .build()
    }

    private fun createChannelLogoBitmap(): Bitmap {
        val size = 96
        val bmp = createBitmap(size, size)
        val canvas = Canvas(bmp)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)

        paint.color = 0xFF0F172A.toInt()
        canvas.drawRoundRect(RectF(0f, 0f, size.toFloat(), size.toFloat()), 20f, 20f, paint)

        paint.color = 0xFFF8FAFC.toInt()
        canvas.drawRoundRect(RectF(18f, 30f, 64f, 66f), 8f, 8f, paint)
        paint.color = 0xFF0F172A.toInt()
        canvas.drawCircle(41f, 48f, 11f, paint)
        paint.color = 0xFF38BDF8.toInt()
        canvas.drawCircle(41f, 48f, 6f, paint)
        paint.color = 0xFFEF4444.toInt()
        canvas.drawCircle(74f, 30f, 7f, paint)

        return bmp
    }

    companion object {
        const val DEEP_LINK_SCHEME = "serbiatvcams"
        const val DEEP_LINK_HOST = "camera"
        private const val PREFS_NAME = "tv_home_channel_state"
        private const val KEY_CHANNEL_ID = "default_channel_id"
        private const val CHANNEL_INTERNAL_ID = "serbia_live_cams_primary"
    }
}

class TvChannelInitReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        val action = intent?.action ?: return
        if (action != TvContractCompat.ACTION_INITIALIZE_PROGRAMS &&
            action != Intent.ACTION_BOOT_COMPLETED
        ) {
            return
        }
        val pendingResult = goAsync()
        val app = context.applicationContext as? BelgradeTvApplication
        if (app == null) {
            pendingResult.finish()
            return
        }
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                val manifest = app.container.catalogCoordinator.loadInitial()
                val prefs = app.container.favoritesRepository.preferences.first()
                app.container.homeChannelSynchronizer.syncChannel(
                    cameras = manifest.cameras,
                    favoriteIds = prefs.favoriteIds,
                )
            } finally {
                pendingResult.finish()
            }
        }
    }
}
