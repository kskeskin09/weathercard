package com.weathercard.app.ui

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.res.stringResource
import com.weathercard.app.R
import com.weathercard.core.Precipitation
import com.weathercard.core.Sky
import com.weathercard.core.WeatherCondition
import kotlin.math.cos
import kotlin.math.sin

/** Original vector artwork; the same code covers every WMO condition, without remote assets. */
@Composable
fun WeatherGlyph(code: Int?, isDay: Boolean = true, modifier: Modifier = Modifier) {
    val condition = WeatherCondition.from(code, isDay)
    val description = stringResource(condition.kind.labelRes)
    Canvas(modifier.semantics { contentDescription = description }) {
        val factor = size.minDimension / 100f
        scale(factor, factor, pivot = Offset.Zero) { drawCondition(condition, isDay) }
    }
}

internal fun DrawScope.drawCondition(condition: WeatherCondition, isDay: Boolean) {
    val clear = condition.sky == Sky.CLEAR
    val showCelestial = condition.sky in listOf(Sky.CLEAR, Sky.MOSTLY_CLEAR, Sky.PARTLY_CLOUDY)
    if (showCelestial) {
        val center = if (clear) Offset(50f, 46f) else Offset(66f, 31f)
        val radius = if (clear) 22f else 16f
        if (isDay) {
            drawCircle(Color(0xFFFFCF65).copy(alpha = .12f), radius + 12, center)
            repeat(8) { index ->
                val angle = index * Math.PI / 4
                fun point(r: Float) = center + Offset(cos(angle).toFloat() * r, sin(angle).toFloat() * r)
                drawLine(Color(0xFFFFCC66), point(radius + 5), point(radius + 11), 3f, StrokeCap.Round)
            }
            drawCircle(Brush.linearGradient(listOf(Color(0xFFFFE5A0), Color(0xFFFFB83E)), center - Offset(radius, radius), center + Offset(radius, radius)), radius, center)
        } else {
            val moon = Path().apply {
                moveTo(center.x + radius * .35f, center.y - radius)
                cubicTo(center.x - radius * 1.6f, center.y - radius, center.x - radius * 1.2f, center.y + radius * 1.5f, center.x + radius * .7f, center.y + radius * .7f)
                cubicTo(center.x - radius * .3f, center.y + radius * .4f, center.x - radius * .5f, center.y - radius * .5f, center.x + radius * .35f, center.y - radius)
                close()
            }
            drawPath(moon, Brush.linearGradient(listOf(Color(0xFFEDF0FF), Color(0xFF9EAFF5))))
            drawCircle(Color(0xFFF4E9B5), 2f, center + Offset(20f, -11f))
            drawLine(Color(0xFFF4E9B5), center + Offset(15f, 7f), center + Offset(21f, 7f), 2f, StrokeCap.Round)
            drawLine(Color(0xFFF4E9B5), center + Offset(18f, 4f), center + Offset(18f, 10f), 2f, StrokeCap.Round)
        }
    }
    if (!clear && condition.sky != Sky.UNKNOWN) {
        val small = condition.sky == Sky.MOSTLY_CLEAR
        val cloud = Path().apply {
            moveTo(24f, 64f)
            cubicTo(6f, 64f, 7f, 40f, 25f, 40f)
            cubicTo(27f, 16f, 61f, 15f, 66f, 39f)
            cubicTo(91f, 32f, 97f, 64f, 77f, 64f)
            close()
        }
        scale(if (small) .65f else 1f, if (small) .65f else 1f, Offset(20f, 60f)) {
            drawPath(cloud, Brush.linearGradient(
                if (condition.thunder) listOf(Color(0xFF9AAACC), Color(0xFF4E6089))
                else listOf(Color(0xFFF3F7FF), Color(0xFFA9C6E9)), Offset(40f, 26f), Offset(60f, 68f)))
            drawPath(cloud, Color.White.copy(alpha = .3f), style = Stroke(1f))
        }
    }
    if (condition.sky == Sky.FOG) {
        repeat(3) { i ->
            drawLine(Color(0xFFB7D5E6).copy(alpha = 1f - i * .15f), Offset(18f + i * 5, 67f + i * 8), Offset(83f - i * 7, 67f + i * 8), 4f, StrokeCap.Round)
        }
    }
    if (condition.thunder) {
        val bolt = Path().apply {
            moveTo(51f, 53f); lineTo(37f, 77f); lineTo(49f, 77f)
            lineTo(43f, 96f); lineTo(67f, 67f); lineTo(54f, 67f); lineTo(63f, 53f); close()
        }
        drawPath(bolt, Color(0xFFFFD465))
    }
    if (condition.precipitation != Precipitation.NONE) {
        val count = condition.intensity + 1
        repeat(count) { i ->
            val x = 24f + (i + .5f) * 54f / count
            val y = 75f + (i % 2) * 6f
            val color = if (condition.freezing) Color(0xFFB2F5F2) else Color(0xFF76BEFF)
            when (condition.precipitation) {
                Precipitation.DRIZZLE -> {
                    drawCircle(color, 1.7f, Offset(x, y))
                    drawCircle(color.copy(alpha = .7f), 1.7f, Offset(x - 3, y + 8))
                }
                Precipitation.SNOW, Precipitation.SNOW_SHOWERS -> snowflake(Offset(x, y + 4), 4f)
                Precipitation.SNOW_GRAINS -> {
                    drawCircle(Color(0xFFE3F5FF), 2.6f, Offset(x, y))
                    drawCircle(Color(0xFFE3F5FF), 1.8f, Offset(x - 3, y + 9))
                }
                Precipitation.HAIL -> drawCircle(Color(0xFFDCF6FF), 3f, Offset(x, y + 8))
                else -> drawLine(color, Offset(x + 2, y - 3), Offset(x - 2, y + 6), if (condition.intensity == 3) 3.5f else 2.5f, StrokeCap.Round)
            }
        }
    }
    if (condition.freezing) snowflake(Offset(82f, 77f), 6f)
    if (condition.sky == Sky.UNKNOWN) {
        drawCircle(Color(0xFFB7C6DA), 24f, Offset(50f, 46f), style = Stroke(3f))
        drawLine(Color(0xFFB7C6DA), Offset(50f, 33f), Offset(50f, 48f), 4f, StrokeCap.Round)
        drawCircle(Color(0xFFB7C6DA), 2.5f, Offset(50f, 57f))
    }
}

private fun DrawScope.snowflake(center: Offset, radius: Float) {
    repeat(3) { i ->
        val angle = i * Math.PI / 3
        val arm = Offset(cos(angle).toFloat() * radius, sin(angle).toFloat() * radius)
        drawLine(Color(0xFFD2F6FF), center - arm, center + arm, 1.5f, StrokeCap.Round)
    }
}

