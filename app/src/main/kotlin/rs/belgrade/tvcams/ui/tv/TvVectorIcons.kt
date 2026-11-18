package rs.belgrade.tvcams.ui.tv

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathOperation
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import rs.belgrade.tvcams.domain.model.CameraCountry
import rs.belgrade.tvcams.domain.solar.SolarPhase
import rs.belgrade.tvcams.domain.weather.WeatherCondition
import kotlin.math.cos
import kotlin.math.sin

private val FlagShape = RoundedCornerShape(2.5.dp)
private val FlagBorderColor = Color(0x4DFFFFFF)

@Composable
fun CountryFlagIcon(
    country: CameraCountry,
    width: Dp = 20.dp,
    height: Dp = 14.dp,
    modifier: Modifier = Modifier,
) {
    Canvas(
        modifier = modifier
            .size(width = width, height = height)
            .clip(FlagShape)
            .border(0.75.dp, FlagBorderColor, FlagShape),
    ) {
        val w = size.width
        val h = size.height
        when (country) {
            CameraCountry.SERBIA -> {
                val bandH = h / 3f
                drawRect(color = Color(0xFFC6363C), topLeft = Offset(0f, 0f), size = Size(w, bandH))
                drawRect(color = Color(0xFF0C4076), topLeft = Offset(0f, bandH), size = Size(w, bandH))
                drawRect(color = Color(0xFFF8FAFC), topLeft = Offset(0f, bandH * 2f), size = Size(w, bandH))
                // Coat-of-arms shield & crown silhouette on hoist third
                val shieldW = w * 0.18f
                val shieldH = h * 0.36f
                val shieldX = w * 0.25f
                val shieldY = h * 0.34f
                drawRoundRect(
                    color = Color(0xFFF1BF00),
                    topLeft = Offset(shieldX, shieldY - h * 0.08f),
                    size = Size(shieldW, h * 0.08f),
                    cornerRadius = CornerRadius(1f, 1f),
                )
                drawRoundRect(
                    color = Color(0xFFC6363C),
                    topLeft = Offset(shieldX, shieldY),
                    size = Size(shieldW, shieldH),
                    cornerRadius = CornerRadius(shieldW * 0.4f, shieldW * 0.4f),
                )
                drawLine(
                    color = Color(0xFFF8FAFC),
                    start = Offset(shieldX + shieldW / 2f, shieldY + 1f),
                    end = Offset(shieldX + shieldW / 2f, shieldY + shieldH - 1f),
                    strokeWidth = 1f,
                )
                drawLine(
                    color = Color(0xFFF8FAFC),
                    start = Offset(shieldX + 1f, shieldY + shieldH / 2f),
                    end = Offset(shieldX + shieldW - 1f, shieldY + shieldH / 2f),
                    strokeWidth = 1f,
                )
            }

            CameraCountry.RUSSIA -> {
                val bandH = h / 3f
                drawRect(color = Color(0xFFF8FAFC), topLeft = Offset(0f, 0f), size = Size(w, bandH))
                drawRect(color = Color(0xFF0039A6), topLeft = Offset(0f, bandH), size = Size(w, bandH))
                drawRect(color = Color(0xFFD52B1E), topLeft = Offset(0f, bandH * 2f), size = Size(w, bandH))
            }

            CameraCountry.SPAIN -> {
                val q = h * 0.25f
                drawRect(color = Color(0xFFAA151B), topLeft = Offset(0f, 0f), size = Size(w, q))
                drawRect(color = Color(0xFFF1BF00), topLeft = Offset(0f, q), size = Size(w, q * 2f))
                drawRect(color = Color(0xFFAA151B), topLeft = Offset(0f, q * 3f), size = Size(w, q))
                // Coat-of-arms crest mark on gualda band
                drawRoundRect(
                    color = Color(0xFFAA151B),
                    topLeft = Offset(w * 0.24f, h * 0.34f),
                    size = Size(w * 0.15f, h * 0.32f),
                    cornerRadius = CornerRadius(1.5f, 1.5f),
                )
            }

            CameraCountry.ROMANIA -> {
                val colW = w / 3f
                drawRect(color = Color(0xFF002B7F), topLeft = Offset(0f, 0f), size = Size(colW, h))
                drawRect(color = Color(0xFFFCD116), topLeft = Offset(colW, 0f), size = Size(colW, h))
                drawRect(color = Color(0xFFCE1126), topLeft = Offset(colW * 2f, 0f), size = Size(colW, h))
            }
        }
    }
}

@Composable
fun WeatherConditionIcon(
    condition: WeatherCondition,
    isDay: Boolean,
    size: Dp = 17.dp,
    modifier: Modifier = Modifier,
) {
    Canvas(modifier = modifier.size(size)) {
        val w = this.size.width
        val h = this.size.height
        val stroke = w * 0.105f

        when (condition) {
            WeatherCondition.CLEAR -> {
                if (isDay) {
                    drawSunGlyph(
                        center = Offset(w * 0.5f, h * 0.5f),
                        coreRadius = w * 0.22f,
                        rayInner = w * 0.31f,
                        rayOuter = w * 0.45f,
                        stroke = stroke,
                        color = Color(0xFFFBBF24),
                    )
                } else {
                    drawCrescentMoon(
                        center = Offset(w * 0.50f, h * 0.50f),
                        radius = w * 0.34f,
                        color = Color(0xFF93C5FD),
                    )
                }
            }

            WeatherCondition.PARTLY_CLOUDY -> {
                if (isDay) {
                    drawSunGlyph(
                        center = Offset(w * 0.34f, h * 0.34f),
                        coreRadius = w * 0.16f,
                        rayInner = w * 0.22f,
                        rayOuter = w * 0.32f,
                        stroke = stroke * 0.85f,
                        color = Color(0xFFFBBF24),
                    )
                } else {
                    drawCrescentMoon(
                        center = Offset(w * 0.34f, h * 0.32f),
                        radius = w * 0.22f,
                        color = Color(0xFF93C5FD),
                    )
                }
                drawCloudShape(
                    left = w * 0.22f,
                    top = h * 0.38f,
                    width = w * 0.72f,
                    height = h * 0.46f,
                    color = Color(0xFFE2E8F0),
                )
            }

            WeatherCondition.OVERCAST -> {
                drawCloudShape(
                    left = w * 0.24f,
                    top = h * 0.18f,
                    width = w * 0.66f,
                    height = h * 0.42f,
                    color = Color(0xFF94A3B8),
                )
                drawCloudShape(
                    left = w * 0.08f,
                    top = h * 0.34f,
                    width = w * 0.78f,
                    height = h * 0.48f,
                    color = Color(0xFFE2E8F0),
                )
            }

            WeatherCondition.FOG -> {
                val fogColorTop = Color(0xFFE2E8F0)
                val fogColorBot = Color(0xFF94A3B8)
                drawLine(
                    color = fogColorTop,
                    start = Offset(w * 0.14f, h * 0.30f),
                    end = Offset(w * 0.86f, h * 0.30f),
                    strokeWidth = stroke * 1.1f,
                    cap = StrokeCap.Round,
                )
                drawLine(
                    color = fogColorTop,
                    start = Offset(w * 0.08f, h * 0.52f),
                    end = Offset(w * 0.78f, h * 0.52f),
                    strokeWidth = stroke * 1.1f,
                    cap = StrokeCap.Round,
                )
                drawLine(
                    color = fogColorBot,
                    start = Offset(w * 0.22f, h * 0.74f),
                    end = Offset(w * 0.90f, h * 0.74f),
                    strokeWidth = stroke * 1.1f,
                    cap = StrokeCap.Round,
                )
            }

            WeatherCondition.DRIZZLE,
            WeatherCondition.RAIN,
            WeatherCondition.SHOWERS,
            -> {
                drawCloudShape(
                    left = w * 0.10f,
                    top = h * 0.12f,
                    width = w * 0.80f,
                    height = h * 0.48f,
                    color = Color(0xFFCBD5E1),
                )
                val dropColor = Color(0xFF38BDF8)
                val xs = if (condition == WeatherCondition.DRIZZLE) {
                    listOf(0.36f, 0.64f)
                } else {
                    listOf(0.28f, 0.50f, 0.72f)
                }
                for (rx in xs) {
                    drawLine(
                        color = dropColor,
                        start = Offset(w * rx, h * 0.68f),
                        end = Offset(w * (rx - 0.07f), h * 0.90f),
                        strokeWidth = stroke,
                        cap = StrokeCap.Round,
                    )
                }
            }

            WeatherCondition.SNOW -> {
                drawCloudShape(
                    left = w * 0.10f,
                    top = h * 0.12f,
                    width = w * 0.80f,
                    height = h * 0.48f,
                    color = Color(0xFFCBD5E1),
                )
                val snowColor = Color(0xFFBAE6FD)
                for (rx in listOf(0.28f, 0.50f, 0.72f)) {
                    drawCircle(
                        color = snowColor,
                        radius = w * 0.065f,
                        center = Offset(w * rx, h * 0.78f),
                    )
                }
            }

            WeatherCondition.THUNDERSTORM -> {
                drawCloudShape(
                    left = w * 0.10f,
                    top = h * 0.10f,
                    width = w * 0.80f,
                    height = h * 0.46f,
                    color = Color(0xFF94A3B8),
                )
                val bolt = Path().apply {
                    moveTo(w * 0.54f, h * 0.52f)
                    lineTo(w * 0.38f, h * 0.74f)
                    lineTo(w * 0.52f, h * 0.74f)
                    lineTo(w * 0.44f, h * 0.95f)
                    lineTo(w * 0.66f, h * 0.69f)
                    lineTo(w * 0.51f, h * 0.69f)
                    close()
                }
                drawPath(path = bolt, color = Color(0xFFFBBF24))
            }
        }
    }
}

@Composable
fun SolarPhaseIcon(
    phase: SolarPhase,
    size: Dp = 17.dp,
    modifier: Modifier = Modifier,
) {
    Canvas(modifier = modifier.size(size)) {
        val w = this.size.width
        val h = this.size.height
        val stroke = w * 0.105f

        when (phase) {
            SolarPhase.GOLDEN_HOUR_MORNING,
            SolarPhase.GOLDEN_HOUR_EVENING,
            -> {
                val gold = Color(0xFFFBBF24)
                drawCircle(
                    color = Color(0x38FBBF24),
                    radius = w * 0.44f,
                    center = Offset(w * 0.5f, h * 0.5f),
                )
                drawSunGlyph(
                    center = Offset(w * 0.5f, h * 0.5f),
                    coreRadius = w * 0.21f,
                    rayInner = w * 0.30f,
                    rayOuter = w * 0.45f,
                    stroke = stroke,
                    color = gold,
                )
            }

            SolarPhase.NIGHT,
            SolarPhase.DAWN,
            -> {
                // Sunrise: horizon line + rising half-sun + upward chevron arrow
                val sunColor = Color(0xFFFBBF24)
                val horizonY = h * 0.78f
                drawArc(
                    color = sunColor,
                    startAngle = 180f,
                    sweepAngle = 180f,
                    useCenter = true,
                    topLeft = Offset(w * 0.22f, horizonY - w * 0.28f),
                    size = Size(w * 0.56f, w * 0.56f),
                )
                drawLine(
                    color = Color(0xFF94A3B8),
                    start = Offset(w * 0.08f, horizonY),
                    end = Offset(w * 0.92f, horizonY),
                    strokeWidth = stroke,
                    cap = StrokeCap.Round,
                )
                val arrow = Path().apply {
                    moveTo(w * 0.34f, h * 0.32f)
                    lineTo(w * 0.50f, h * 0.14f)
                    lineTo(w * 0.66f, h * 0.32f)
                }
                drawPath(
                    path = arrow,
                    color = Color(0xFF38BDF8),
                    style = Stroke(width = stroke, cap = StrokeCap.Round, join = StrokeJoin.Round),
                )
            }

            SolarPhase.DAY,
            SolarPhase.DUSK,
            -> {
                // Sunset: horizon line + setting half-sun + downward chevron arrow
                val sunColor = Color(0xFFFB923C)
                val horizonY = h * 0.78f
                drawArc(
                    color = sunColor,
                    startAngle = 180f,
                    sweepAngle = 180f,
                    useCenter = true,
                    topLeft = Offset(w * 0.22f, horizonY - w * 0.28f),
                    size = Size(w * 0.56f, w * 0.56f),
                )
                drawLine(
                    color = Color(0xFF94A3B8),
                    start = Offset(w * 0.08f, horizonY),
                    end = Offset(w * 0.92f, horizonY),
                    strokeWidth = stroke,
                    cap = StrokeCap.Round,
                )
                val arrow = Path().apply {
                    moveTo(w * 0.34f, h * 0.16f)
                    lineTo(w * 0.50f, h * 0.34f)
                    lineTo(w * 0.66f, h * 0.16f)
                }
                drawPath(
                    path = arrow,
                    color = sunColor,
                    style = Stroke(width = stroke, cap = StrokeCap.Round, join = StrokeJoin.Round),
                )
            }
        }
    }
}

private fun DrawScope.drawSunGlyph(
    center: Offset,
    coreRadius: Float,
    rayInner: Float,
    rayOuter: Float,
    stroke: Float,
    color: Color,
) {
    drawCircle(color = color, radius = coreRadius, center = center)
    for (i in 0 until 8) {
        val rad = Math.toRadians(i * 45.0)
        val c = cos(rad).toFloat()
        val s = sin(rad).toFloat()
        drawLine(
            color = color,
            start = Offset(center.x + c * rayInner, center.y + s * rayInner),
            end = Offset(center.x + c * rayOuter, center.y + s * rayOuter),
            strokeWidth = stroke,
            cap = StrokeCap.Round,
        )
    }
}

private fun DrawScope.drawCrescentMoon(
    center: Offset,
    radius: Float,
    color: Color,
) {
    val outer = Path().apply {
        addOval(Rect(center = center, radius = radius))
    }
    val cutout = Path().apply {
        addOval(
            Rect(
                center = Offset(center.x + radius * 0.42f, center.y - radius * 0.28f),
                radius = radius * 0.88f,
            ),
        )
    }
    val crescent = Path.combine(PathOperation.Difference, outer, cutout)
    drawPath(path = crescent, color = color)
}

private fun DrawScope.drawCloudShape(
    left: Float,
    top: Float,
    width: Float,
    height: Float,
    color: Color,
) {
    val baseTop = top + height * 0.38f
    val baseHeight = height * 0.62f
    drawRoundRect(
        color = color,
        topLeft = Offset(left, baseTop),
        size = Size(width, baseHeight),
        cornerRadius = CornerRadius(baseHeight / 2f, baseHeight / 2f),
    )
    drawCircle(
        color = color,
        radius = height * 0.44f,
        center = Offset(left + width * 0.40f, top + height * 0.44f),
    )
    drawCircle(
        color = color,
        radius = height * 0.34f,
        center = Offset(left + width * 0.68f, top + height * 0.52f),
    )
}
