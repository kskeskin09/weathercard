package com.weathercard.app.ui

import android.provider.Settings
import androidx.compose.foundation.layout.Spacer
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.animation.core.withInfiniteAnimationFrameMillis
import com.weathercard.core.DayWeather
import com.weathercard.core.Precipitation
import com.weathercard.core.Sky
import com.weathercard.core.SkyLight
import com.weathercard.core.WeatherCondition
import kotlinx.coroutines.delay
import java.time.Instant
import java.time.ZoneId
import kotlin.math.PI
import kotlin.math.floor
import kotlin.math.sin

/** ~30 fps is plenty for slow ambient motion and keeps the GPU mostly idle. */
private const val FRAME_MS = 33L

private val NightSky = listOf(Color(0xFF070D20), Color(0xFF0F1B38), Color(0xFF18284A))
private val NoonSky = listOf(Color(0xFF3A7DC9), Color(0xFF3C74B4), Color(0xFF2D5A8F))
private val GoldenSky = listOf(Color(0xFF2E3F78), Color(0xFFB86F72), Color(0xFF3F3A62))
private val NightGrey = listOf(Color(0xFF1C2330), Color(0xFF232B3A), Color(0xFF2A3344))
private val DayGrey = listOf(Color(0xFF66778C), Color(0xFF5A6A7F), Color(0xFF435367))

/**
 * Full-screen, softly animated atmosphere drawn locally (no images, no network).
 *
 * Brightness follows the sun: dark at night, brightest at solar noon, with warm tints around
 * sunrise and sunset taken from the forecast's daily sun times. Clouds drift, rain and snow fall,
 * stars twinkle and storms flash occasionally. Motion stops when system animations are off and
 * pauses automatically whenever the screen is not being drawn.
 */
@Composable
fun WeatherBackdrop(
    code: Int?,
    isDay: Boolean,
    modifier: Modifier = Modifier,
    now: Instant? = null,
    zone: ZoneId = ZoneId.systemDefault(),
    days: List<DayWeather> = emptyList(),
) {
    val condition = WeatherCondition.from(code, isDay)
    val moment = now ?: Instant.now()
    val today = moment.atZone(zone).toLocalDate().toString()
    val sun = days.find { it.date == today }
    val light = SkyLight.at(moment.epochSecond, sun?.sunrise, sun?.sunset, isDay)
    val scene = remember(condition, light) { Scene(condition, light) }

    val context = LocalContext.current
    val motion = remember(context) {
        Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) > 0f
    }
    val clock = remember { mutableFloatStateOf(0f) }
    if (motion) LaunchedEffect(Unit) {
        // Infinite-animation frames are skipped by Compose UI tests, so screenshots stay deterministic.
        val start = withInfiniteAnimationFrameMillis { it }
        while (true) {
            withInfiniteAnimationFrameMillis { clock.floatValue = (it - start) / 1000f }
            delay(FRAME_MS)
        }
    }

    Spacer(modifier.drawWithCache {
        val seeds = Seeds(120)
        val clouds = List(5) { cloudPath(size.width * (.55f + seeds.a[it] * .3f)) }
        onDrawBehind { scene.draw(this, clock.floatValue, seeds, clouds) }
    })
}

/** Everything that depends on weather and time of day, resolved once per change rather than per frame. */
private class Scene(val condition: WeatherCondition, val light: SkyLight) {
    val rain = condition.precipitation in listOf(Precipitation.RAIN, Precipitation.DRIZZLE, Precipitation.SHOWERS)
    val drizzle = condition.precipitation == Precipitation.DRIZZLE
    val snow = condition.precipitation in listOf(Precipitation.SNOW, Precipitation.SNOW_GRAINS, Precipitation.SNOW_SHOWERS)
    val hail = condition.precipitation == Precipitation.HAIL
    val fog = condition.sky == Sky.FOG

    /** 0 = clean blue sky, 1 = fully grey. */
    val grey: Float = maxOf(
        when (condition.sky) {
            Sky.CLEAR -> 0f; Sky.MOSTLY_CLEAR -> .1f; Sky.PARTLY_CLOUDY -> .25f
            Sky.OVERCAST -> .6f; Sky.FOG -> .55f; Sky.UNKNOWN -> .3f
        },
        if (rain || snow || hail) .45f + .08f * condition.intensity else 0f,
        if (condition.thunder) .8f else 0f,
    ).coerceAtMost(.85f)

    val colors: List<Color> = List(3) { i ->
        var c = lerp(NightSky[i], NoonSky[i], light.daylight)
        c = lerp(c, GoldenSky[i], light.golden * (1f - grey) * .75f)
        c = lerp(c, lerp(NightGrey[i], DayGrey[i], light.daylight), grey)
        if (condition.thunder) lerp(c, Color.Black, .2f) else c
    }

    val cloudCount = when {
        grey < .05f -> 0; grey < .15f -> 2; grey < .3f -> 3; else -> 5
    }
    val cloudColor: Color = lerp(Color(0xFF9AA8C0), Color.White, light.daylight).let {
        if (rain || condition.thunder) lerp(it, Color(0xFF55657A), .35f) else it
    }.copy(alpha = (.05f + .09f * grey) * (.6f + .4f * light.daylight))

    val starAlpha = ((1f - light.daylight / .3f).coerceIn(0f, 1f) * (1f - grey * 1.4f)).coerceAtLeast(0f)
    val sunAlpha = if (light.sunProgress != null) (1f - grey * 1.3f).coerceAtLeast(0f) else 0f
    val sunColor = lerp(Color(0xFFFFF0C2), Color(0xFFFFA86B), light.golden)

    fun draw(scope: DrawScope, t: Float, seeds: Seeds, clouds: List<Path>) = with(scope) {
        val w = size.width
        val h = size.height
        drawRect(Brush.verticalGradient(colors))
        if (starAlpha > 0f) drawNight(t, seeds, w, h)
        if (sunAlpha > 0f) drawSun(t, w, h)
        repeat(cloudCount) { i ->
            val speed = .004f + seeds.b[i] * .004f
            val x = (fract(seeds.c[i] + t * speed) * 1.9f - .8f) * w
            val y = h * (.04f + i * .11f) + sin(t * .15f + i) * h * .006f
            translate(x, y) { drawPath(clouds[i], cloudColor) }
        }
        if (fog) repeat(5) { i ->
            val dx = sin(t * .05f + i * 1.7f) * w * .12f
            drawOval(Color.White.copy(alpha = .05f), Offset(-w * .3f + dx, h * (.12f + i * .17f)), Size(w * 1.6f, h * .09f))
        }
        if (rain || condition.thunder) drawRain(t, seeds, w, h)
        if (snow) drawSnow(t, seeds, w, h)
        if (hail) drawHail(t, seeds, w, h)
        if (condition.thunder) {
            // One soft double flash roughly every nine seconds; far below any flicker-sensitivity threshold.
            val p = fract(t / 9f) * 9f
            val flash = when { p < .12f -> 1f - p / .12f; p in .3f..0.5f -> .6f * (1f - (p - .3f) / .2f); else -> 0f }
            if (flash > 0f) drawRect(Color(0xFFE8F0FF).copy(alpha = .13f * flash))
        }
        // A lower scrim keeps forecast cards readable even under the bright midday sky.
        drawRect(Brush.verticalGradient(listOf(Color.Transparent, Color(0xFF050D20).copy(alpha = .08f + .22f * light.daylight)), startY = h * .35f))
    }

    private fun DrawScope.drawNight(t: Float, seeds: Seeds, w: Float, h: Float) {
        repeat(48) { i ->
            val twinkle = .55f + .45f * sin(t * (.6f + seeds.a[i] * 1.4f) + seeds.b[i] * 6.28f)
            val r = (if (i % 6 == 0) 1.6f else 1f).dp.toPx()
            drawCircle(Color.White.copy(alpha = starAlpha * twinkle * (.25f + seeds.c[i] * .45f)), r, Offset(seeds.a[i + 50] * w, seeds.b[i + 50] * h * .6f))
        }
        val moon = Offset(w * .22f, h * .11f)
        val r = w * .045f
        drawCircle(Brush.radialGradient(listOf(Color(0xFFDDE6FF).copy(alpha = .14f * starAlpha), Color.Transparent), moon, r * 5f), r * 5f, moon)
        drawCircle(Color(0xFFF1F4FF).copy(alpha = .55f * starAlpha), r, moon)
    }

    private fun DrawScope.drawSun(t: Float, w: Float, h: Float) {
        val p = light.sunProgress ?: return
        val arc = sin(PI * p).toFloat()
        val center = Offset(w * (.12f + .76f * p), h * (.3f - .22f * arc))
        val pulse = 1f + .05f * sin(t * .7f)
        val glow = w * .55f * pulse
        drawCircle(Brush.radialGradient(listOf(sunColor.copy(alpha = .22f * sunAlpha), Color.Transparent), center, glow), glow, center)
        rotate(t * 2.5f, center) {
            repeat(12) { i ->
                val angle = i * PI.toFloat() / 6f
                val dir = Offset(kotlin.math.cos(angle), sin(angle))
                drawLine(sunColor.copy(alpha = .07f * sunAlpha), center + dir * (w * .1f), center + dir * (w * .2f), 3.dp.toPx(), StrokeCap.Round)
            }
        }
        drawCircle(sunColor.copy(alpha = .5f * sunAlpha), w * .065f, center)
    }

    private fun DrawScope.drawRain(t: Float, seeds: Seeds, w: Float, h: Float) {
        val count = if (drizzle) 35 else 40 + 22 * condition.intensity
        val length = (if (drizzle) 9f else 16f + 4f * condition.intensity).dp.toPx()
        val color = Color(0xFFCFE6FF).copy(alpha = if (drizzle) .16f else .22f)
        repeat(count.coerceAtMost(110)) { i ->
            val speed = (if (drizzle) .45f else .9f) + seeds.b[i] * .4f
            val y = fract(seeds.c[i] + t * speed) * (h + length) - length
            val x = fract(seeds.a[i] - y / h * .08f) * w
            drawLine(color, Offset(x, y), Offset(x - length * .18f, y + length), 1.3.dp.toPx(), StrokeCap.Round)
        }
    }

    private fun DrawScope.drawSnow(t: Float, seeds: Seeds, w: Float, h: Float) {
        repeat(30 + 18 * condition.intensity) { i ->
            val speed = .035f + seeds.b[i] * .04f
            val y = fract(seeds.c[i] + t * speed) * h * 1.05f - h * .025f
            val x = fract(seeds.a[i] + sin(t * .6f + i) * .015f) * w
            drawCircle(Color.White.copy(alpha = .3f + seeds.a[i + 1] * .35f), (1.2f + seeds.c[i + 2] * 2.4f).dp.toPx(), Offset(x, y))
        }
    }

    private fun DrawScope.drawHail(t: Float, seeds: Seeds, w: Float, h: Float) {
        repeat(30 + 12 * condition.intensity) { i ->
            val y = fract(seeds.c[i] + t * (.8f + seeds.b[i] * .3f)) * h
            drawCircle(Color(0xFFEAF4FF).copy(alpha = .4f), (1.4f + seeds.a[i] * 1.4f).dp.toPx(), Offset(seeds.a[i] * w, y))
        }
    }
}

/** Stable pseudo-random values so particles keep their identity frame to frame. */
private class Seeds(n: Int) {
    val a = FloatArray(n) { hash(it, 1) }
    val b = FloatArray(n) { hash(it, 2) }
    val c = FloatArray(n) { hash(it, 3) }
    private fun hash(i: Int, k: Int) = fract(sin(i * 12.9898f + k * 78.233f) * 43758.547f)
}

private fun fract(x: Float) = x - floor(x)

/** A soft cloud silhouette from overlapping ovals; non-zero fill merges them without darker overlaps. */
private fun cloudPath(width: Float): Path = Path().apply {
    val h = width * .42f
    addOval(Rect(0f, h * .45f, width, h))
    addOval(Rect(width * .12f, h * .22f, width * .52f, h * .9f))
    addOval(Rect(width * .34f, 0f, width * .8f, h * .85f))
    addOval(Rect(width * .6f, h * .3f, width * .95f, h * .92f))
}
