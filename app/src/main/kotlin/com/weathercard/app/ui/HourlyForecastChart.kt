package com.weathercard.app.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.res.stringResource
import com.weathercard.app.R
import com.weathercard.core.HourWeather
import com.weathercard.core.WeatherCondition
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.roundToInt

/** Virtualized hour columns share matching cubic segments, so the curve remains continuous. */
@Composable
fun HourlyForecastChart(hours: List<HourWeather>, currentHour: Long, zone: ZoneId, modifier: Modifier = Modifier) {
    // Past hours are never drawn: the curve starts at the current hour and the latest hour stays on the right.
    val ordered = hours.filter { it.time >= currentHour }.sortedBy { it.time }
    if (ordered.isEmpty()) return
    val temperatures = ordered.mapNotNull { it.temperature }
    val low = temperatures.minOrNull() ?: 0.0
    val high = temperatures.maxOrNull() ?: low
    val range = (high - low).coerceAtLeast(6.0)
    val center = (high + low) / 2
    fun y(index: Int): Float = (64 - (((ordered[index.coerceIn(0, ordered.lastIndex)].temperature ?: center) - center) / range) * 58).toFloat()
    val scroll = rememberLazyListState()
    // When the clock passes an hour the old first column disappears; keep "Now" at the left edge.
    LaunchedEffect(currentHour) { scroll.scrollToItem(0) }
    val locale = Locale.getDefault()
    LazyRow(
        state = scroll,
        modifier = modifier.fillMaxWidth().clip(RoundedCornerShape(28.dp))
            .background(Color.White.copy(alpha = .075f))
            .border(1.dp, Color.White.copy(alpha = .1f), RoundedCornerShape(28.dp)),
        contentPadding = PaddingValues(vertical = 12.dp),
    ) {
        itemsIndexed(ordered, key = { _, hour -> hour.time }) { index, hour ->
            val current = hour.time == currentHour
            val local = Instant.ofEpochSecond(hour.time).atZone(zone)
            val label = if (current) stringResource(R.string.now) else local.format(DateTimeFormatter.ofPattern("HH:mm"))
            val temperature = hour.temperature?.roundToInt()?.let { "$it°" } ?: "—°"
            val wind = hour.windSpeed?.let { String.format(locale, "%.1f", it) } ?: "—"
            val description = stringResource(WeatherCondition.from(hour.weatherCode, hour.isDay ?: true).kind.labelRes)
            val spoken = stringResource(R.string.hour_description, label, local.toLocalDate(), temperature, description, wind)
            Column(Modifier.width(96.dp).semantics(mergeDescendants = true) {
                contentDescription = spoken
            }, horizontalAlignment = Alignment.CenterHorizontally) {
                Box(Modifier.fillMaxWidth().height(100.dp)) {
                    Canvas(Modifier.fillMaxSize().clipToBounds()) {
                        val w = size.width
                        val centerX = w / 2
                        fun pointY(i: Int) = y(i).dp.toPx()
                        val lineBrush = Brush.verticalGradient(listOf(Color(0xFFF7FF58), Color(0xFFBAF466)))
                        fun segment(left: Int, x: Float) {
                            if (left !in 0 until ordered.lastIndex || ordered[left].temperature == null || ordered[left + 1].temperature == null) return
                            // Missing cached days are gaps, not an invented connecting forecast.
                            if (ordered[left + 1].time - ordered[left].time != 3600L) return
                            val y0 = pointY(left)
                            val y1 = pointY(left + 1)
                            val c0 = y0 + (pointY(left + 1) - pointY(left - 1)) / 6
                            val c1 = y1 - (pointY(left + 2) - pointY(left)) / 6
                            val curve = Path().apply {
                                moveTo(x, y0)
                                cubicTo(x + w / 3, c0, x + w * 2 / 3, c1, x + w, y1)
                            }
                            val fill = Path().apply {
                                addPath(curve); lineTo(x + w, size.height); lineTo(x, size.height); close()
                            }
                            drawPath(fill, Brush.verticalGradient(listOf(Color(0xFFDCF982).copy(alpha = .06f), Color.Transparent)))
                            drawPath(curve, lineBrush, style = Stroke(2.5.dp.toPx(), cap = StrokeCap.Round))
                        }
                        segment(index - 1, -centerX)
                        segment(index, centerX)
                        if (hour.temperature != null) {
                            val cy = pointY(index)
                            if (index == 0) drawLine(lineBrush, Offset(0f, cy), Offset(centerX, cy), 2.5.dp.toPx())
                            if (index == ordered.lastIndex) drawLine(lineBrush, Offset(centerX, cy), Offset(w, cy), 2.5.dp.toPx())
                            if (current) {
                                drawLine(Color.White.copy(alpha = .3f), Offset(centerX, cy + 4.dp.toPx()), Offset(centerX, size.height), 1.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 4.dp.toPx())))
                                drawCircle(Color.White.copy(alpha = .12f), 8.dp.toPx(), Offset(centerX, cy))
                                drawCircle(Color.White, 3.5.dp.toPx(), Offset(centerX, cy))
                            }
                        }
                    }
                    Text(temperature, Modifier.align(Alignment.TopCenter).offset(y = (y(index) - 29).dp), fontSize = 21.sp, lineHeight = 25.sp, fontWeight = FontWeight.SemiBold)
                }
                WeatherGlyph(hour.weatherCode, hour.isDay ?: true, Modifier.size(48.dp))
                Text(wind, color = Color(0xFFD0E0EF), fontSize = 13.sp, lineHeight = 17.sp, modifier = Modifier.padding(top = 3.dp))
                Text(stringResource(R.string.wind_unit), color = Color(0xFFABC5DC), fontSize = 9.sp, lineHeight = 12.sp)
                Text(label, color = if (current) Color.White else Color(0xFFC0D3E5), fontSize = 14.sp, lineHeight = 18.sp, fontWeight = if (current) FontWeight.SemiBold else FontWeight.Normal, modifier = Modifier.padding(top = 9.dp))
                Text(local.format(DateTimeFormatter.ofPattern("d MMM", locale)), color = Color(0xFF9DB8D2), fontSize = 9.sp, lineHeight = 12.sp, modifier = Modifier.padding(top = 3.dp))
                // Short weekday under the date (Pzt / Mon), from the device language.
                Text(local.format(DateTimeFormatter.ofPattern("EEE", locale)), color = Color(0xFF9DB8D2), fontSize = 9.sp, lineHeight = 12.sp, modifier = Modifier.padding(top = 1.dp, bottom = 5.dp))
            }
        }
    }
}
