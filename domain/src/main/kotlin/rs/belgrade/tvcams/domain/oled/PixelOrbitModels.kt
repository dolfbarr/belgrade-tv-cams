package rs.belgrade.tvcams.domain.oled

data class PixelOrbitOffset(
    val dxDp: Int,
    val dyDp: Int,
    val videoScale: Float,
)

object PixelOrbitCalculator {
    const val DEFAULT_STEP_INTERVAL_MS: Long = 90_000L
    const val ACTIVE_VIDEO_SCALE: Float = 1.025f

    // 16-point closed diamond loop within [-8..8] x [-5..5] dp (stays inside TV overscan safe margins)
    private val ORBIT_PATH: List<Pair<Int, Int>> = listOf(
        0 to 0,
        3 to -2,
        6 to -3,
        8 to -1,
        7 to 2,
        4 to 4,
        1 to 5,
        -2 to 4,
        -5 to 3,
        -8 to 1,
        -7 to -2,
        -4 to -4,
        -1 to -5,
        2 to -4,
        5 to -1,
        2 to 2,
    )

    fun computeOffset(
        epochMillis: Long,
        enabled: Boolean,
        stepIntervalMs: Long = DEFAULT_STEP_INTERVAL_MS,
    ): PixelOrbitOffset {
        if (!enabled) {
            return PixelOrbitOffset(dxDp = 0, dyDp = 0, videoScale = 1.0f)
        }
        val safeInterval = stepIntervalMs.coerceAtLeast(1_000L)
        val step = Math.floorMod(epochMillis / safeInterval, ORBIT_PATH.size.toLong()).toInt()
        val (dx, dy) = ORBIT_PATH[step]
        return PixelOrbitOffset(
            dxDp = dx,
            dyDp = dy,
            videoScale = ACTIVE_VIDEO_SCALE,
        )
    }
}
