package rs.belgrade.tvcams.domain.solar

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

enum class SolarPhase {
    NIGHT,
    DAWN,
    GOLDEN_HOUR_MORNING,
    DAY,
    GOLDEN_HOUR_EVENING,
    DUSK,
}

data class SolarWindow(
    val sunriseEpochMs: Long,
    val sunsetEpochMs: Long,
)

data class SolarStatus(
    val phase: SolarPhase,
    val summaryLabel: String,
    val targetTimeLabel: String,
    val isGoldenHour: Boolean,
)

object SolarPhaseCalculator {
    private const val DEFAULT_ZONE_ID = "Europe/Belgrade"
    private const val MINUTE_MS = 60_000L
    private const val DAWN_LEAD_MS = 40L * MINUTE_MS
    private const val GOLDEN_LEAD_SHORT_MS = 15L * MINUTE_MS
    private const val GOLDEN_SPAN_LONG_MS = 45L * MINUTE_MS
    private const val DUSK_TRAIL_MS = 40L * MINUTE_MS

    private val TIME_FORMATTER: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm", Locale.US)

    fun evaluate(
        now: Instant,
        timezoneId: String,
        windows: List<SolarWindow>,
    ): SolarStatus? {
        if (windows.isEmpty()) return null
        val zone = runCatching { ZoneId.of(timezoneId) }
            .getOrElse { ZoneId.of(DEFAULT_ZONE_ID) }
        val nowMs = now.toEpochMilli()
        val sorted = windows.sortedBy { it.sunriseEpochMs }

        for (window in sorted) {
            val sr = window.sunriseEpochMs
            val ss = window.sunsetEpochMs
            val srText = Instant.ofEpochMilli(sr).atZone(zone).format(TIME_FORMATTER)
            val ssText = Instant.ofEpochMilli(ss).atZone(zone).format(TIME_FORMATTER)

            when {
                nowMs < sr - DAWN_LEAD_MS -> {
                    return SolarStatus(
                        phase = SolarPhase.NIGHT,
                        summaryLabel = "Sunrise $srText",
                        targetTimeLabel = srText,
                        isGoldenHour = false,
                    )
                }

                nowMs < sr - GOLDEN_LEAD_SHORT_MS -> {
                    return SolarStatus(
                        phase = SolarPhase.DAWN,
                        summaryLabel = "Dawn · Sunrise $srText",
                        targetTimeLabel = srText,
                        isGoldenHour = false,
                    )
                }

                nowMs <= sr + GOLDEN_SPAN_LONG_MS -> {
                    return SolarStatus(
                        phase = SolarPhase.GOLDEN_HOUR_MORNING,
                        summaryLabel = "Golden hour · Sunrise $srText",
                        targetTimeLabel = srText,
                        isGoldenHour = true,
                    )
                }

                nowMs < ss - GOLDEN_SPAN_LONG_MS -> {
                    return SolarStatus(
                        phase = SolarPhase.DAY,
                        summaryLabel = "Sunset $ssText",
                        targetTimeLabel = ssText,
                        isGoldenHour = false,
                    )
                }

                nowMs <= ss + GOLDEN_LEAD_SHORT_MS -> {
                    return SolarStatus(
                        phase = SolarPhase.GOLDEN_HOUR_EVENING,
                        summaryLabel = "Golden hour · Sunset $ssText",
                        targetTimeLabel = ssText,
                        isGoldenHour = true,
                    )
                }

                nowMs <= ss + DUSK_TRAIL_MS -> {
                    return SolarStatus(
                        phase = SolarPhase.DUSK,
                        summaryLabel = "Dusk · Sunset $ssText",
                        targetTimeLabel = ssText,
                        isGoldenHour = false,
                    )
                }
            }
        }

        // Fallback when past the last provided sunset
        val lastSunrise = sorted.last().sunriseEpochMs
        val srText = Instant.ofEpochMilli(lastSunrise).atZone(zone).format(TIME_FORMATTER)
        return SolarStatus(
            phase = SolarPhase.NIGHT,
            summaryLabel = "Sunrise $srText",
            targetTimeLabel = srText,
            isGoldenHour = false,
        )
    }
}
