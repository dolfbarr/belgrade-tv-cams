package rs.belgrade.tvcams.domain.stream

import rs.belgrade.tvcams.domain.model.StreamDescriptor
import java.net.URI

object SnapshotUrlBuilder {
    fun buildPolledUrl(
        baseUrl: String,
        paramName: String?,
        epochMillis: Long,
    ): String {
        val key = paramName?.takeIf { it.isNotBlank() } ?: "_"
        val trimmed = baseUrl.trim()
        val parsed = URI(trimmed)
        val retainedParams = parsed.rawQuery
            ?.split("&")
            ?.filter { it.isNotBlank() && !it.startsWith("$key=") }
            .orEmpty()

        val updatedQuery = (retainedParams + "$key=$epochMillis").joinToString("&")
        val baseWithoutQuery = trimmed.substringBefore("?").substringBefore("#")
        return "$baseWithoutQuery?$updatedQuery"
    }
}

sealed interface FailoverDecision {
    data class SwitchStreamImmediately(val nextStream: StreamDescriptor) : FailoverDecision
    data class RetryAfterBackoff(
        val nextStream: StreamDescriptor,
        val delayMillis: Long,
        val exhaustedCycles: Int = 1,
    ) : FailoverDecision
}

class StreamFailoverTracker(
    streams: List<StreamDescriptor>,
) {
    private val candidates: List<StreamDescriptor> = streams.toList().also {
        require(it.isNotEmpty()) { "Camera must provide at least one StreamDescriptor" }
    }
    private var activeIndex: Int = 0
    private var exhaustedCycles: Int = 0

    fun currentStream(): StreamDescriptor = candidates[activeIndex]

    fun onPlaybackHealthy() {
        exhaustedCycles = 0
    }

    fun resetForManualRetry(): StreamDescriptor {
        activeIndex = 0
        exhaustedCycles = 0
        return candidates[0]
    }

    fun onTransportFailure(): FailoverDecision {
        val nextIndex = activeIndex + 1
        if (nextIndex < candidates.size) {
            activeIndex = nextIndex
            return FailoverDecision.SwitchStreamImmediately(candidates[activeIndex])
        }

        activeIndex = 0
        val backoff = BACKOFF_SCHEDULE_MS[exhaustedCycles.coerceAtMost(BACKOFF_SCHEDULE_MS.lastIndex)]
        exhaustedCycles += 1
        return FailoverDecision.RetryAfterBackoff(
            nextStream = candidates[activeIndex],
            delayMillis = backoff,
            exhaustedCycles = exhaustedCycles,
        )
    }

    companion object {
        private val BACKOFF_SCHEDULE_MS = longArrayOf(3_000L, 6_000L, 15_000L, 30_000L)
    }
}
