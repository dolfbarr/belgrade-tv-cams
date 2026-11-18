package rs.belgrade.tvcams.domain.time

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

object CameraClockFormatter {
    private const val DEFAULT_ZONE_ID = "Europe/Belgrade"
    private val TIME_FORMATTER: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm", Locale.US)

    fun formatTime(instant: Instant, timezoneId: String): String {
        val zone = runCatching { ZoneId.of(timezoneId) }
            .getOrElse { ZoneId.of(DEFAULT_ZONE_ID) }
        return instant.atZone(zone).format(TIME_FORMATTER)
    }
}
