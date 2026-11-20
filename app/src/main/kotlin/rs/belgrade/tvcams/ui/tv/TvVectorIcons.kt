package rs.belgrade.tvcams.ui.tv

import android.provider.Settings
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Text
import rs.belgrade.tvcams.domain.model.CameraCountry
import rs.belgrade.tvcams.domain.solar.SolarPhase
import rs.belgrade.tvcams.domain.weather.WeatherCondition
import kotlin.math.cos
import kotlin.math.sin

private val FlagShape = RoundedCornerShape(2.5.dp)
private val FlagBorderColor = Color(0x4DFFFFFF)

private val VECTOR_FLAG_ISOS: Set<String> = setOf(
    "RS", "RU", "ES", "RO", "GB", "FR", "NL", "DE", "GE", "AM", "US", "KR", "JP",
    "AT", "CH", "IT", "BE", "IE", "PL", "UA", "CZ", "HU", "BG", "SE", "NO", "FI", "DK", "CA",
    "HR", "TR", "IS", "PT", "TW", "TH", "AU", "SG", "HK", "AE", "IL", "BR", "SX", "MX",
)

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun CountryFlagIcon(
    country: CameraCountry,
    isoOverride: String? = null,
    width: Dp = 20.dp,
    height: Dp = 14.dp,
    modifier: Modifier = Modifier,
) {
    val iso = country.isoAlpha2(isoOverride)
    if (iso !in VECTOR_FLAG_ISOS) {
        // Universal open-world ISO monogram flag badge for community-contributed countries
        Box(
            modifier = modifier
                .size(width = width, height = height)
                .clip(FlagShape)
                .background(Color(0xFF1E3A5F))
                .border(0.75.dp, Color(0x8838BDF8), FlagShape),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = iso.take(2),
                color = Color(0xFFF8FAFC),
                fontSize = 7.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
            )
        }
        return
    }

    Canvas(
        modifier = modifier
            .size(width = width, height = height)
            .clip(FlagShape)
            .border(0.75.dp, FlagBorderColor, FlagShape),
    ) {
        val w = size.width
        val h = size.height
        when (iso) {
            "RS" -> {
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

            "RU" -> {
                drawHorizontalTricolor(w, h, Color(0xFFF8FAFC), Color(0xFF0039A6), Color(0xFFD52B1E))
            }

            "ES" -> {
                val q = h * 0.25f
                drawRect(color = Color(0xFFAA151B), topLeft = Offset(0f, 0f), size = Size(w, q))
                drawRect(color = Color(0xFFF1BF00), topLeft = Offset(0f, q), size = Size(w, q * 2f))
                drawRect(color = Color(0xFFAA151B), topLeft = Offset(0f, q * 3f), size = Size(w, q))
                drawRoundRect(
                    color = Color(0xFFAA151B),
                    topLeft = Offset(w * 0.24f, h * 0.34f),
                    size = Size(w * 0.15f, h * 0.32f),
                    cornerRadius = CornerRadius(1.5f, 1.5f),
                )
            }

            "RO" -> {
                drawVerticalTricolor(w, h, Color(0xFF002B7F), Color(0xFFFCD116), Color(0xFFCE1126))
            }

            "GB" -> {
                drawRect(color = Color(0xFF012169), topLeft = Offset(0f, 0f), size = Size(w, h))
                drawLine(color = Color.White, start = Offset(0f, 0f), end = Offset(w, h), strokeWidth = h * 0.20f)
                drawLine(color = Color.White, start = Offset(w, 0f), end = Offset(0f, h), strokeWidth = h * 0.20f)
                drawLine(color = Color(0xFFC8102E), start = Offset(0f, 0f), end = Offset(w, h), strokeWidth = h * 0.09f)
                drawLine(color = Color(0xFFC8102E), start = Offset(w, 0f), end = Offset(0f, h), strokeWidth = h * 0.09f)
                drawRect(color = Color.White, topLeft = Offset(0f, h * 0.34f), size = Size(w, h * 0.32f))
                drawRect(color = Color.White, topLeft = Offset(w * 0.38f, 0f), size = Size(w * 0.24f, h))
                drawRect(color = Color(0xFFC8102E), topLeft = Offset(0f, h * 0.40f), size = Size(w, h * 0.20f))
                drawRect(color = Color(0xFFC8102E), topLeft = Offset(w * 0.42f, 0f), size = Size(w * 0.16f, h))
            }

            "FR" -> {
                drawVerticalTricolor(w, h, Color(0xFF002654), Color(0xFFF8FAFC), Color(0xFFED2939))
            }

            "NL" -> {
                drawHorizontalTricolor(w, h, Color(0xFFAE1C28), Color(0xFFF8FAFC), Color(0xFF21468B))
            }

            "DE" -> {
                drawHorizontalTricolor(w, h, Color(0xFF09090B), Color(0xFFDD0000), Color(0xFFFFCE00))
            }

            "GE" -> {
                drawRect(color = Color(0xFFF8FAFC), topLeft = Offset(0f, 0f), size = Size(w, h))
                val georgiaRed = Color(0xFFFF0000)
                drawRect(color = georgiaRed, topLeft = Offset(0f, h * 0.39f), size = Size(w, h * 0.22f))
                drawRect(color = georgiaRed, topLeft = Offset(w * 0.42f, 0f), size = Size(w * 0.16f, h))
                for ((cx, cy) in listOf(0.21f to 0.20f, 0.79f to 0.20f, 0.21f to 0.80f, 0.79f to 0.80f)) {
                    drawRect(color = georgiaRed, topLeft = Offset(w * cx - 2f, h * cy - 1f), size = Size(4f, 2f))
                    drawRect(color = georgiaRed, topLeft = Offset(w * cx - 1f, h * cy - 2f), size = Size(2f, 4f))
                }
            }

            "AM" -> {
                drawHorizontalTricolor(w, h, Color(0xFFD90012), Color(0xFF0033A0), Color(0xFFF2A800))
            }

            "US" -> {
                val stripeH = h / 7f
                for (i in 0 until 7) {
                    val color = if (i % 2 == 0) Color(0xFFB22234) else Color(0xFFF8FAFC)
                    drawRect(color = color, topLeft = Offset(0f, stripeH * i), size = Size(w, stripeH))
                }
                val cantonW = w * 0.46f
                val cantonH = stripeH * 4f
                drawRect(color = Color(0xFF3C3B6E), topLeft = Offset(0f, 0f), size = Size(cantonW, cantonH))
                for (row in 0..1) {
                    for (col in 0..2) {
                        drawCircle(
                            color = Color.White,
                            radius = h * 0.042f,
                            center = Offset(
                                cantonW * (0.20f + col * 0.30f),
                                cantonH * (0.32f + row * 0.38f),
                            ),
                        )
                    }
                }
            }

            "KR" -> {
                drawRect(color = Color(0xFFF8FAFC), topLeft = Offset(0f, 0f), size = Size(w, h))
                val r = h * 0.26f
                val center = Offset(w * 0.5f, h * 0.5f)
                drawArc(
                    color = Color(0xFFCD2E3A),
                    startAngle = 180f,
                    sweepAngle = 180f,
                    useCenter = true,
                    topLeft = Offset(center.x - r, center.y - r),
                    size = Size(r * 2f, r * 2f),
                )
                drawArc(
                    color = Color(0xFF0047A0),
                    startAngle = 0f,
                    sweepAngle = 180f,
                    useCenter = true,
                    topLeft = Offset(center.x - r, center.y - r),
                    size = Size(r * 2f, r * 2f),
                )
                val barColor = Color(0xFF0F172A)
                drawLine(color = barColor, start = Offset(w * 0.16f, h * 0.28f), end = Offset(w * 0.26f, h * 0.16f), strokeWidth = 1.3f)
                drawLine(color = barColor, start = Offset(w * 0.74f, h * 0.16f), end = Offset(w * 0.84f, h * 0.28f), strokeWidth = 1.3f)
                drawLine(color = barColor, start = Offset(w * 0.16f, h * 0.72f), end = Offset(w * 0.26f, h * 0.84f), strokeWidth = 1.3f)
                drawLine(color = barColor, start = Offset(w * 0.74f, h * 0.84f), end = Offset(w * 0.84f, h * 0.72f), strokeWidth = 1.3f)
            }

            "JP" -> {
                drawRect(color = Color(0xFFF8FAFC), topLeft = Offset(0f, 0f), size = Size(w, h))
                drawCircle(color = Color(0xFFBC002D), radius = h * 0.29f, center = Offset(w * 0.5f, h * 0.5f))
            }

            "AT" -> drawHorizontalTricolor(w, h, Color(0xFFED2939), Color(0xFFF8FAFC), Color(0xFFED2939))
            "IT" -> drawVerticalTricolor(w, h, Color(0xFF009246), Color(0xFFF8FAFC), Color(0xFFCE2B37))
            "BE" -> drawVerticalTricolor(w, h, Color(0xFF09090B), Color(0xFFFDDA24), Color(0xFFEF3340))
            "IE" -> drawVerticalTricolor(w, h, Color(0xFF169B62), Color(0xFFF8FAFC), Color(0xFFFF883E))
            "HU" -> drawHorizontalTricolor(w, h, Color(0xFFCE2939), Color(0xFFF8FAFC), Color(0xFF477050))
            "BG" -> drawHorizontalTricolor(w, h, Color(0xFFF8FAFC), Color(0xFF00966E), Color(0xFFD62612))
            "HR" -> drawHorizontalTricolor(w, h, Color(0xFFFF0000), Color(0xFFF8FAFC), Color(0xFF171796))
            "PL" -> {
                drawRect(color = Color(0xFFF8FAFC), topLeft = Offset(0f, 0f), size = Size(w, h * 0.5f))
                drawRect(color = Color(0xFFDC143C), topLeft = Offset(0f, h * 0.5f), size = Size(w, h * 0.5f))
            }
            "UA" -> {
                drawRect(color = Color(0xFF0057B7), topLeft = Offset(0f, 0f), size = Size(w, h * 0.5f))
                drawRect(color = Color(0xFFFFDD00), topLeft = Offset(0f, h * 0.5f), size = Size(w, h * 0.5f))
            }
            "CZ" -> {
                drawRect(color = Color(0xFFF8FAFC), topLeft = Offset(0f, 0f), size = Size(w, h * 0.5f))
                drawRect(color = Color(0xFFD7141A), topLeft = Offset(0f, h * 0.5f), size = Size(w, h * 0.5f))
                val wedge = Path().apply {
                    moveTo(0f, 0f)
                    lineTo(w * 0.48f, h * 0.5f)
                    lineTo(0f, h)
                    close()
                }
                drawPath(wedge, Color(0xFF11457E))
            }
            "CH" -> {
                drawRect(color = Color(0xFFFF0000), topLeft = Offset(0f, 0f), size = Size(w, h))
                drawRect(color = Color.White, topLeft = Offset(w * 0.28f, h * 0.40f), size = Size(w * 0.44f, h * 0.20f))
                drawRect(color = Color.White, topLeft = Offset(w * 0.42f, h * 0.20f), size = Size(w * 0.16f, h * 0.60f))
            }
            "SE" -> drawNordicCross(w, h, bg = Color(0xFF006AA7), cross = Color(0xFFFECC02))
            "FI" -> drawNordicCross(w, h, bg = Color(0xFFF8FAFC), cross = Color(0xFF003580))
            "DK" -> drawNordicCross(w, h, bg = Color(0xFFC8102E), cross = Color(0xFFF8FAFC))
            "NO" -> {
                drawNordicCross(w, h, bg = Color(0xFFBA0C2F), cross = Color(0xFFF8FAFC), thickness = 0.26f)
                drawRect(color = Color(0xFF00205B), topLeft = Offset(0f, h * 0.43f), size = Size(w, h * 0.14f))
                drawRect(color = Color(0xFF00205B), topLeft = Offset(w * 0.31f, 0f), size = Size(w * 0.10f, h))
            }
            "CA" -> {
                drawRect(color = Color(0xFFFF0000), topLeft = Offset(0f, 0f), size = Size(w * 0.25f, h))
                drawRect(color = Color(0xFFF8FAFC), topLeft = Offset(w * 0.25f, 0f), size = Size(w * 0.50f, h))
                drawRect(color = Color(0xFFFF0000), topLeft = Offset(w * 0.75f, 0f), size = Size(w * 0.25f, h))
                drawCircle(color = Color(0xFFFF0000), radius = h * 0.22f, center = Offset(w * 0.50f, h * 0.50f))
            }
            "IS" -> {
                drawNordicCross(w, h, bg = Color(0xFF02529C), cross = Color(0xFFF8FAFC), thickness = 0.26f)
                drawRect(color = Color(0xFFDC1E35), topLeft = Offset(0f, h * 0.43f), size = Size(w, h * 0.14f))
                drawRect(color = Color(0xFFDC1E35), topLeft = Offset(w * 0.31f, 0f), size = Size(w * 0.10f, h))
            }
            "TR" -> {
                drawRect(color = Color(0xFFE30A17), topLeft = Offset(0f, 0f), size = Size(w, h))
                drawCircle(color = Color.White, radius = h * 0.26f, center = Offset(w * 0.40f, h * 0.50f))
                drawCircle(color = Color(0xFFE30A17), radius = h * 0.21f, center = Offset(w * 0.45f, h * 0.50f))
                drawCircle(color = Color.White, radius = h * 0.08f, center = Offset(w * 0.60f, h * 0.50f))
            }
            "PT" -> {
                drawRect(color = Color(0xFF006600), topLeft = Offset(0f, 0f), size = Size(w * 0.40f, h))
                drawRect(color = Color(0xFFFF0000), topLeft = Offset(w * 0.40f, 0f), size = Size(w * 0.60f, h))
                drawCircle(color = Color(0xFFFFCC00), radius = h * 0.21f, center = Offset(w * 0.40f, h * 0.50f))
            }
            "TW" -> {
                drawRect(color = Color(0xFFFE0000), topLeft = Offset(0f, 0f), size = Size(w, h))
                drawRect(color = Color(0xFF000095), topLeft = Offset(0f, 0f), size = Size(w * 0.50f, h * 0.52f))
                drawCircle(color = Color.White, radius = h * 0.14f, center = Offset(w * 0.25f, h * 0.26f))
            }
            "TH" -> {
                val u = h / 6f
                drawRect(color = Color(0xFFA51931), topLeft = Offset(0f, 0f), size = Size(w, u))
                drawRect(color = Color(0xFFF8FAFC), topLeft = Offset(0f, u), size = Size(w, u))
                drawRect(color = Color(0xFF2D2A4A), topLeft = Offset(0f, u * 2f), size = Size(w, u * 2f))
                drawRect(color = Color(0xFFF8FAFC), topLeft = Offset(0f, u * 4f), size = Size(w, u))
                drawRect(color = Color(0xFFA51931), topLeft = Offset(0f, u * 5f), size = Size(w, u))
            }
            "AU" -> {
                drawRect(color = Color(0xFF00008B), topLeft = Offset(0f, 0f), size = Size(w, h))
                val cw = w * 0.48f
                val ch = h * 0.50f
                drawRect(color = Color.White, topLeft = Offset(0f, ch * 0.36f), size = Size(cw, ch * 0.28f))
                drawRect(color = Color.White, topLeft = Offset(cw * 0.38f, 0f), size = Size(cw * 0.24f, ch))
                drawRect(color = Color(0xFFC8102E), topLeft = Offset(0f, ch * 0.42f), size = Size(cw, ch * 0.16f))
                drawRect(color = Color(0xFFC8102E), topLeft = Offset(cw * 0.42f, 0f), size = Size(cw * 0.16f, ch))
                drawCircle(color = Color.White, radius = h * 0.08f, center = Offset(w * 0.24f, h * 0.76f))
                for ((sx, sy) in listOf(0.75f to 0.22f, 0.86f to 0.42f, 0.75f to 0.80f, 0.64f to 0.48f)) {
                    drawCircle(color = Color.White, radius = h * 0.055f, center = Offset(w * sx, h * sy))
                }
            }
            "SG" -> {
                drawRect(color = Color(0xFFEF3340), topLeft = Offset(0f, 0f), size = Size(w, h * 0.50f))
                drawRect(color = Color(0xFFF8FAFC), topLeft = Offset(0f, h * 0.50f), size = Size(w, h * 0.50f))
                drawCircle(color = Color.White, radius = h * 0.17f, center = Offset(w * 0.24f, h * 0.25f))
                drawCircle(color = Color(0xFFEF3340), radius = h * 0.14f, center = Offset(w * 0.29f, h * 0.25f))
            }
            "HK" -> {
                drawRect(color = Color(0xFFDE2910), topLeft = Offset(0f, 0f), size = Size(w, h))
                for (i in 0 until 5) {
                    val angle = Math.toRadians((i * 72.0) - 90.0)
                    drawCircle(
                        color = Color.White,
                        radius = h * 0.095f,
                        center = Offset(
                            (w * 0.50f) + (h * 0.16f * cos(angle).toFloat()),
                            (h * 0.50f) + (h * 0.16f * sin(angle).toFloat()),
                        ),
                    )
                }
            }
            "AE" -> {
                drawHorizontalTricolor(w, h, Color(0xFF00732F), Color(0xFFF8FAFC), Color(0xFF09090B))
                drawRect(color = Color(0xFFFF0000), topLeft = Offset(0f, 0f), size = Size(w * 0.26f, h))
            }
            "IL" -> {
                drawRect(color = Color(0xFFF8FAFC), topLeft = Offset(0f, 0f), size = Size(w, h))
                val blue = Color(0xFF0038B8)
                drawRect(color = blue, topLeft = Offset(0f, h * 0.12f), size = Size(w, h * 0.14f))
                drawRect(color = blue, topLeft = Offset(0f, h * 0.74f), size = Size(w, h * 0.14f))
                drawCircle(color = blue, radius = h * 0.17f, center = Offset(w * 0.50f, h * 0.50f), style = Stroke(width = 1.4f))
            }
            "BR" -> {
                drawRect(color = Color(0xFF009C3B), topLeft = Offset(0f, 0f), size = Size(w, h))
                val diamond = Path().apply {
                    moveTo(w * 0.50f, h * 0.12f)
                    lineTo(w * 0.88f, h * 0.50f)
                    lineTo(w * 0.50f, h * 0.88f)
                    lineTo(w * 0.12f, h * 0.50f)
                    close()
                }
                drawPath(diamond, Color(0xFFFFDF00))
                drawCircle(color = Color(0xFF002776), radius = h * 0.20f, center = Offset(w * 0.50f, h * 0.50f))
            }
            "SX" -> {
                drawRect(color = Color(0xFFED2939), topLeft = Offset(0f, 0f), size = Size(w, h * 0.50f))
                drawRect(color = Color(0xFF002654), topLeft = Offset(0f, h * 0.50f), size = Size(w, h * 0.50f))
                val triangle = Path().apply {
                    moveTo(0f, 0f)
                    lineTo(w * 0.42f, h * 0.50f)
                    lineTo(0f, h)
                    close()
                }
                drawPath(triangle, Color(0xFFF8FAFC))
            }
            "MX" -> {
                drawVerticalTricolor(w, h, Color(0xFF006847), Color(0xFFF8FAFC), Color(0xFFCE1126))
                drawCircle(color = Color(0xFF8C6239), radius = h * 0.13f, center = Offset(w * 0.50f, h * 0.50f))
            }
        }
    }
}

private fun DrawScope.drawHorizontalTricolor(
    w: Float,
    h: Float,
    top: Color,
    mid: Color,
    bottom: Color,
) {
    val bandH = h / 3f
    drawRect(color = top, topLeft = Offset(0f, 0f), size = Size(w, bandH))
    drawRect(color = mid, topLeft = Offset(0f, bandH), size = Size(w, bandH))
    drawRect(color = bottom, topLeft = Offset(0f, bandH * 2f), size = Size(w, bandH))
}

private fun DrawScope.drawVerticalTricolor(
    w: Float,
    h: Float,
    left: Color,
    mid: Color,
    right: Color,
) {
    val colW = w / 3f
    drawRect(color = left, topLeft = Offset(0f, 0f), size = Size(colW, h))
    drawRect(color = mid, topLeft = Offset(colW, 0f), size = Size(colW, h))
    drawRect(color = right, topLeft = Offset(colW * 2f, 0f), size = Size(colW, h))
}

private fun DrawScope.drawNordicCross(
    w: Float,
    h: Float,
    bg: Color,
    cross: Color,
    thickness: Float = 0.20f,
) {
    drawRect(color = bg, topLeft = Offset(0f, 0f), size = Size(w, h))
    drawRect(color = cross, topLeft = Offset(0f, h * (0.5f - thickness / 2f)), size = Size(w, h * thickness))
    drawRect(color = cross, topLeft = Offset(w * 0.30f, 0f), size = Size(w * (thickness * 0.75f), h))
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

@Composable
fun rememberReducedMotionEnabled(): Boolean {
    val context = LocalContext.current
    return remember(context) {
        val scale = runCatching {
            Settings.Global.getFloat(
                context.contentResolver,
                Settings.Global.ANIMATOR_DURATION_SCALE,
                1f,
            )
        }.getOrDefault(1f)
        scale == 0f
    }
}

@Composable
fun LoadingSpinnerIcon(
    size: Dp = 16.dp,
    color: Color = Color(0xFF38BDF8),
    modifier: Modifier = Modifier,
) {
    val reducedMotion = rememberReducedMotionEnabled()
    val angle = if (reducedMotion) {
        -90f
    } else {
        val transition = rememberInfiniteTransition(label = "spinnerRotation")
        val animated by transition.animateFloat(
            initialValue = 0f,
            targetValue = 360f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 900, easing = LinearEasing),
                repeatMode = RepeatMode.Restart,
            ),
            label = "spinnerAngle",
        )
        animated
    }

    Canvas(modifier = modifier.size(size)) {
        val stroke = this.size.width * 0.14f
        val pad = stroke / 2f
        val arcSize = Size(this.size.width - stroke, this.size.height - stroke)
        drawArc(
            color = color.copy(alpha = 0.22f),
            startAngle = 0f,
            sweepAngle = 360f,
            useCenter = false,
            topLeft = Offset(pad, pad),
            size = arcSize,
            style = Stroke(width = stroke),
        )
        drawArc(
            color = color,
            startAngle = angle,
            sweepAngle = 250f,
            useCenter = false,
            topLeft = Offset(pad, pad),
            size = arcSize,
            style = Stroke(width = stroke, cap = StrokeCap.Round),
        )
    }
}

@Composable
fun CameraSignalOffIcon(
    size: Dp = 28.dp,
    color: Color = Color(0xFF94A3B8),
    accentColor: Color = Color(0xFFFBBF24),
    modifier: Modifier = Modifier,
) {
    Canvas(modifier = modifier.size(size)) {
        val w = this.size.width
        val h = this.size.height
        val stroke = w * 0.085f
        drawRoundRect(
            color = color,
            topLeft = Offset(w * 0.12f, h * 0.26f),
            size = Size(w * 0.56f, h * 0.48f),
            cornerRadius = CornerRadius(w * 0.09f, w * 0.09f),
            style = Stroke(width = stroke),
        )
        val lensCone = Path().apply {
            moveTo(w * 0.68f, h * 0.42f)
            lineTo(w * 0.88f, h * 0.30f)
            lineTo(w * 0.88f, h * 0.70f)
            lineTo(w * 0.68f, h * 0.58f)
            close()
        }
        drawPath(
            path = lensCone,
            color = color,
            style = Stroke(width = stroke, join = StrokeJoin.Round),
        )
        drawLine(
            color = accentColor,
            start = Offset(w * 0.16f, h * 0.84f),
            end = Offset(w * 0.84f, h * 0.16f),
            strokeWidth = stroke * 1.1f,
            cap = StrokeCap.Round,
        )
    }
}

@Composable
fun WorldGlobeMarkIcon(
    size: Dp = 44.dp,
    primaryColor: Color = Color(0xFF38BDF8),
    accentColor: Color = Color(0xFFEF4444),
    modifier: Modifier = Modifier,
) {
    Canvas(modifier = modifier.size(size)) {
        val w = this.size.width
        val h = this.size.height
        val r = w * 0.42f
        val c = Offset(w * 0.5f, h * 0.5f)
        val stroke = w * 0.065f
        drawCircle(color = primaryColor, radius = r, center = c, style = Stroke(width = stroke))
        drawOval(
            color = primaryColor.copy(alpha = 0.7f),
            topLeft = Offset(c.x - r * 0.52f, c.y - r),
            size = Size(r * 1.04f, r * 2f),
            style = Stroke(width = stroke * 0.85f),
        )
        drawLine(
            color = primaryColor.copy(alpha = 0.7f),
            start = Offset(c.x - r, c.y),
            end = Offset(c.x + r, c.y),
            strokeWidth = stroke * 0.85f,
        )
        drawCircle(
            color = accentColor,
            radius = w * 0.09f,
            center = Offset(c.x + r * 0.55f, c.y - r * 0.55f),
        )
    }
}

@Composable
fun SearchLensIcon(
    size: Dp = 15.dp,
    color: Color = Color.Unspecified,
    modifier: Modifier = Modifier,
) {
    val resolvedColor = if (color == Color.Unspecified) Color(0xFFF8FAFC) else color
    Canvas(modifier = modifier.size(size)) {
        val w = this.size.width
        val stroke = w * 0.13f
        val r = w * 0.30f
        val center = Offset(w * 0.42f, w * 0.42f)
        drawCircle(
            color = resolvedColor,
            radius = r,
            center = center,
            style = Stroke(width = stroke),
        )
        drawLine(
            color = resolvedColor,
            start = Offset(w * 0.64f, w * 0.64f),
            end = Offset(w * 0.88f, w * 0.88f),
            strokeWidth = stroke,
            cap = StrokeCap.Round,
        )
    }
}

@Composable
fun SettingsSlidersIcon(
    size: Dp = 15.dp,
    color: Color = Color.Unspecified,
    modifier: Modifier = Modifier,
) {
    val resolvedColor = if (color == Color.Unspecified) Color(0xFFF8FAFC) else color
    Canvas(modifier = modifier.size(size)) {
        val w = this.size.width
        val h = this.size.height
        val stroke = w * 0.11f
        val knobR = w * 0.11f
        // Top slider
        drawLine(
            color = resolvedColor,
            start = Offset(w * 0.12f, h * 0.28f),
            end = Offset(w * 0.88f, h * 0.28f),
            strokeWidth = stroke,
            cap = StrokeCap.Round,
        )
        drawCircle(color = resolvedColor, radius = knobR, center = Offset(w * 0.34f, h * 0.28f))
        // Bottom slider
        drawLine(
            color = resolvedColor,
            start = Offset(w * 0.12f, h * 0.72f),
            end = Offset(w * 0.88f, h * 0.72f),
            strokeWidth = stroke,
            cap = StrokeCap.Round,
        )
        drawCircle(color = resolvedColor, radius = knobR, center = Offset(w * 0.68f, h * 0.72f))
    }
}
