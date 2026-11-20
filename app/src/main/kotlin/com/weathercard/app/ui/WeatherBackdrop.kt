package com.weathercard.app.ui

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import com.weathercard.core.Precipitation
import com.weathercard.core.Sky
import com.weathercard.core.WeatherCondition

/** Full-screen atmosphere drawn locally; no images or network requests are needed. */
@Composable
fun WeatherBackdrop(code: Int?, isDay: Boolean, modifier: Modifier = Modifier) {
    val condition = WeatherCondition.from(code, isDay)
    val rain = condition.precipitation in listOf(Precipitation.RAIN, Precipitation.DRIZZLE, Precipitation.SHOWERS)
    val snow = condition.precipitation in listOf(Precipitation.SNOW, Precipitation.SNOW_GRAINS, Precipitation.SNOW_SHOWERS)
    val palette = when {
        condition.thunder -> listOf(Color(0xFF343E5C), Color(0xFF3D4C6D), Color(0xFF455C7A))
        condition.sky == Sky.FOG -> listOf(Color(0xFF475A74), Color(0xFF506985), Color(0xFF59758E))
        snow -> listOf(Color(0xFF405A75), Color(0xFF486681), Color(0xFF50748D))
        rain -> listOf(Color(0xFF344A6A), Color(0xFF3D5677), Color(0xFF496887))
        !isDay -> listOf(Color(0xFF2E3D60), Color(0xFF394E70), Color(0xFF456380))
        condition.sky == Sky.OVERCAST -> listOf(Color(0xFF3B506F), Color(0xFF435D7C), Color(0xFF4B6A88))
        else -> listOf(Color(0xFF344C70), Color(0xFF405C7D), Color(0xFF4A6C8A))
    }
    Canvas(modifier) {
        val w = size.width
        val h = size.height
        drawRect(Brush.verticalGradient(palette))
        val glow = if (isDay && !rain && !snow) Color(0xFFFFD99C) else Color(0xFF9EBEFF)
        drawCircle(Brush.radialGradient(listOf(glow.copy(alpha = .06f), Color.Transparent), Offset(w * .88f, h * .12f), w * .85f), w * .85f, Offset(w * .88f, h * .12f))
        if (!isDay && !rain && !snow && condition.sky != Sky.FOG) {
            repeat(40) { i ->
                val x = ((i * 71 + 17) % 101) / 101f * w
                val y = ((i * 37 + 11) % 97) / 97f * h * .58f
                drawCircle(Color.White.copy(alpha = .04f + (i % 4) * .03f), if (i % 5 == 0) 2f else 1f, Offset(x, y))
            }
        }
        // Broad translucent cloud banks give depth without competing with the data.
        repeat(3) { i ->
            val y = h * (.19f + i * .17f)
            val cloud = Path().apply {
                moveTo(-w * .3f, y + w * .2f)
                cubicTo(w * .05f, y - w * .12f, w * .32f, y + w * .18f, w * .63f, y)
                cubicTo(w * .9f, y - w * .13f, w * 1.1f, y + w * .05f, w * 1.3f, y + w * .18f)
                lineTo(w * 1.3f, y + w * .3f); lineTo(-w * .3f, y + w * .3f); close()
            }
            val cloudy = condition.sky in listOf(Sky.OVERCAST, Sky.FOG) || rain || snow
            drawPath(cloud, Color.White.copy(alpha = if (cloudy) .022f else .012f))
        }
        if (rain || condition.thunder) repeat(55) { i ->
            val x = ((i * 43 + 7) % 103) / 103f * w
            val y = ((i * 61 + 3) % 107) / 107f * h
            drawLine(Color(0xFFC7E5FF).copy(alpha = .11f), Offset(x, y), Offset(x - 7, y + 25 + condition.intensity * 6), 1.5f, StrokeCap.Round)
        }
        if (snow || condition.precipitation == Precipitation.HAIL) repeat(65) { i ->
            val x = ((i * 53 + 5) % 109) / 109f * w
            val y = ((i * 29 + 17) % 101) / 101f * h
            drawCircle(Color.White.copy(alpha = .15f + (i % 3) * .08f), 1.5f + i % 4, Offset(x, y))
        }
        if (condition.sky == Sky.FOG) repeat(5) { i ->
            val center = Offset(w * .5f, h * (.15f + i * .17f))
            drawOval(Color.White.copy(alpha = .045f), Offset(-w * .2f, center.y), androidx.compose.ui.geometry.Size(w * 1.4f, h * .08f))
        }
        // A gentle lower scrim keeps forecast and footer contrast consistent in every condition.
        drawRect(Brush.verticalGradient(listOf(Color.Transparent, Color(0xFF050D20).copy(alpha = .06f))))
    }
}
