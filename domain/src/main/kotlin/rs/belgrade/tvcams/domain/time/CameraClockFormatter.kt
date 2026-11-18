package rs.belgrade.tvcams.domain.time

import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.abs

data class DualClockReading(
    val localTime: String,
    val cameraTime: String,
    val cameraUtcOffsetLabel: String,
    val isSameZoneTime: Boolean,
) {
    fun formattedLabel(): String = if (isSameZoneTime) {
        "$cameraTime ($cameraUtcOffsetLabel)"
    } else {
        "$cameraTime ($cameraUtcOffsetLabel) · Local $localTime"
    }
}

object CameraClockFormatter {
    private const val DEFAULT_ZONE_ID = "Europe/Belgrade"
    private val TIME_FORMATTER: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm", Locale.US)

    fun formatTime(instant: Instant, timezoneId: String): String {
        val zone = runCatching { ZoneId.of(timezoneId) }
            .getOrElse { ZoneId.of(DEFAULT_ZONE_ID) }
        return instant.atZone(zone).format(TIME_FORMATTER)
    }

    fun formatDualClock(
        instant: Instant,
        cameraTimezoneId: String,
        viewerZoneId: ZoneId = ZoneId.systemDefault(),
    ): DualClockReading {
        val camZone = runCatching { ZoneId.of(cameraTimezoneId) }
            .getOrElse { ZoneId.of(DEFAULT_ZONE_ID) }
        val localZoned = instant.atZone(viewerZoneId)
        val camZoned = instant.atZone(camZone)

        val localTime = localZoned.format(TIME_FORMATTER)
        val cameraTime = camZoned.format(TIME_FORMATTER)
        val offsetLabel = formatUtcOffset(camZoned.offset)
        val isSame = localZoned.offset == camZoned.offset && localTime == cameraTime

        return DualClockReading(
            localTime = localTime,
            cameraTime = cameraTime,
            cameraUtcOffsetLabel = offsetLabel,
            isSameZoneTime = isSame,
        )
    }

    private fun formatUtcOffset(offset: ZoneOffset): String {
        val totalSec = offset.totalSeconds
        if (totalSec == 0) return "UTC"
        val sign = if (totalSec >= 0) "+" else "-"
        val absSec = abs(totalSec)
        val hours = absSec / 3600
        val minutes = (absSec % 3600) / 60
        return if (minutes == 0) {
            "UTC$sign$hours"
        } else {
            String.format(Locale.US, "UTC%s%d:%02d", sign, hours, minutes)
        }
    }
}
