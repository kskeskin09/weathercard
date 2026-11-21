package com.weathercard.app.widget

import android.graphics.Bitmap
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import com.weathercard.app.ui.drawCondition
import com.weathercard.core.Precipitation
import com.weathercard.core.Sky
import com.weathercard.core.WeatherCondition

/**
 * Widgets cannot run Compose Canvas code, so the app's own vector artwork is drawn into bitmaps.
 * Everything is local: no images or network requests.
 */
internal object WidgetArt {
    private fun render(width: Int, height: Int, block: DrawScope.() -> Unit): Bitmap {
        val image = ImageBitmap(width.coerceAtLeast(1), height.coerceAtLeast(1))
        CanvasDrawScope().draw(Density(1f), LayoutDirection.Ltr, Canvas(image), Size(image.width.toFloat(), image.height.toFloat()), block)
        return image.asAndroidBitmap()
    }

    fun glyph(code: Int?, isDay: Boolean, sizePx: Int): Bitmap = render(sizePx, sizePx) {
        val factor = size.minDimension / 100f
        scale(factor, factor, pivot = Offset.Zero) { drawCondition(WeatherCondition.from(code, isDay), isDay) }
    }

    fun locationMark(sizePx: Int): Bitmap = render(sizePx, sizePx) {
        val arrow = Path().apply {
            moveTo(size.width * .93f, size.height * .07f)
            lineTo(size.width * .62f, size.height * .95f)
            lineTo(size.width * .42f, size.height * .57f)
            lineTo(size.width * .05f, size.height * .39f)
            close()
        }
        drawPath(arrow, Color.White)
    }

    /** Same blue/grey palette family as the in-app backdrop, as a rounded card. */
    fun background(code: Int?, isDay: Boolean, width: Int, height: Int, radiusPx: Float): Bitmap = render(width, height) {
        val condition = WeatherCondition.from(code, isDay)
        val rain = condition.precipitation in listOf(Precipitation.RAIN, Precipitation.DRIZZLE, Precipitation.SHOWERS)
        val snow = condition.precipitation in listOf(Precipitation.SNOW, Precipitation.SNOW_GRAINS, Precipitation.SNOW_SHOWERS)
        val palette = when {
            condition.thunder -> listOf(Color(0xFF343E5C), Color(0xFF455C7A))
            condition.sky == Sky.FOG -> listOf(Color(0xFF475A74), Color(0xFF59758E))
            snow -> listOf(Color(0xFF405A75), Color(0xFF50748D))
            rain -> listOf(Color(0xFF344A6A), Color(0xFF496887))
            !isDay -> listOf(Color(0xFF26324F), Color(0xFF3B5272))
            condition.sky == Sky.OVERCAST -> listOf(Color(0xFF3B506F), Color(0xFF4B6A88))
            else -> listOf(Color(0xFF3A5A86), Color(0xFF52789C))
        }
        val corner = CornerRadius(radiusPx, radiusPx)
        drawRoundRect(Brush.verticalGradient(palette), cornerRadius = corner)
        val glow = if (isDay && !rain && !snow) Color(0xFFFFD99C) else Color(0xFF9EBEFF)
        val center = Offset(size.width * .9f, size.height * .05f)
        drawRoundRect(Brush.radialGradient(listOf(glow.copy(alpha = .14f), Color.Transparent), center, size.width * .7f), cornerRadius = corner)
        drawRoundRect(Color.White.copy(alpha = .08f), cornerRadius = corner, style = androidx.compose.ui.graphics.drawscope.Stroke(width = 2f))
    }
}
