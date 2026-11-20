package com.weathercard.app.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.weathercard.core.DayWeather
import com.weathercard.core.HourWeather
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.cos
import kotlin.math.sin

private val CardShape = RoundedCornerShape(28.dp)
private val Quiet = Color(0xFFBED1E4)
private fun Modifier.glassCard() = fillMaxWidth().background(Color.White.copy(alpha = .07f), CardShape)
    .border(1.dp, Color.White.copy(alpha = .09f), CardShape)

@Composable
fun WindCard(hour: HourWeather?, modifier: Modifier = Modifier) {
    val direction = hour?.windDirection
    val names = listOf("Kuzey", "Kuzeydoğu", "Doğu", "Güneydoğu", "Güney", "Güneybatı", "Batı", "Kuzeybatı")
    val name = direction?.let { names[((it + 22.5) / 45).toInt().mod(8)] } ?: "Yön bilinmiyor"
    val speed = hour?.windSpeed?.let { String.format(Locale.forLanguageTag("tr-TR"), "%.1f", it) } ?: "—"
    Row(modifier.glassCard().padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("RÜZGÂR", color = Quiet, fontSize = 10.sp, letterSpacing = 2.sp)
            Text(name, fontSize = 25.sp, fontWeight = FontWeight.Medium)
            Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                Text(speed, fontSize = 30.sp, fontWeight = FontWeight.SemiBold)
                Text("km/sa", color = Quiet, fontSize = 12.sp, modifier = Modifier.padding(bottom = 5.dp))
            }
            hour?.windGusts?.let { Text("Hamle ${String.format(Locale.forLanguageTag("tr-TR"), "%.1f", it)} km/sa", color = Quiet, fontSize = 11.sp) }
        }
        Box(Modifier.size(132.dp), contentAlignment = Alignment.Center) {
            Canvas(Modifier.fillMaxSize()) {
                val c = center
                val radius = size.minDimension * .46f
                drawCircle(Color.White.copy(alpha = .2f), radius, c, style = Stroke(2.dp.toPx()))
                repeat(32) { index ->
                    val angle = index * Math.PI / 16
                    val long = index % 8 == 0
                    fun point(r: Float) = c + Offset(sin(angle).toFloat() * r, -cos(angle).toFloat() * r)
                    if (index % 8 != 0) drawLine(Color.White.copy(alpha = .18f), point(radius - 4.dp.toPx()), point(radius - if (long) 11.dp.toPx() else 7.dp.toPx()), 1.dp.toPx())
                }
                if (direction != null) {
                    // WMO direction is where wind comes FROM; the arrow points where it goes.
                    rotate(((direction + 180) % 360).toFloat(), c) {
                        drawLine(Color.White, c + Offset(0f, radius * .34f), c - Offset(0f, radius * .36f), 3.dp.toPx(), StrokeCap.Round)
                        val arrow = Path().apply {
                            moveTo(c.x, c.y - radius * .55f)
                            lineTo(c.x - 5.dp.toPx(), c.y - radius * .22f)
                            lineTo(c.x + 5.dp.toPx(), c.y - radius * .22f); close()
                        }
                        drawPath(arrow, Color.White)
                    }
                }
            }
            Text("K", color = Quiet, fontSize = 11.sp, modifier = Modifier.align(Alignment.TopCenter).padding(top = 11.dp))
            Text("G", color = Quiet, fontSize = 11.sp, modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 11.dp))
            Text("B", color = Quiet, fontSize = 11.sp, modifier = Modifier.align(Alignment.CenterStart).padding(start = 11.dp))
            Text("D", color = Quiet, fontSize = 11.sp, modifier = Modifier.align(Alignment.CenterEnd).padding(end = 11.dp))
        }
    }
}

@Composable
fun SunTimesCard(day: DayWeather?, now: Instant, zone: ZoneId, modifier: Modifier = Modifier) {
    val rise = day?.sunrise
    val set = day?.sunset
    val time = DateTimeFormatter.ofPattern("HH:mm").withZone(zone)
    fun format(epoch: Long?) = epoch?.let { time.format(Instant.ofEpochSecond(it)) } ?: "—"
    val daylight = rise != null && set != null && now.epochSecond in rise..set
    Row(modifier.glassCard().heightIn(min = 164.dp).padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(15.dp)) {
            Text("GÜNEŞİN YOLU", color = Quiet, fontSize = 10.sp, letterSpacing = 1.5.sp)
            Column {
                Text(format(rise), fontSize = 27.sp, fontWeight = FontWeight.SemiBold)
                Text("Gün doğumu", color = Quiet, fontSize = 12.sp)
            }
            Column {
                Text(format(set), fontSize = 27.sp, fontWeight = FontWeight.SemiBold)
                Text("Gün batımı", color = Quiet, fontSize = 12.sp)
            }
        }
        Column(Modifier.width(146.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Canvas(Modifier.fillMaxWidth().height(112.dp)) {
                val base = size.height * .78f
                val amplitude = size.height * .6f
                val left = 8.dp.toPx()
                val width = size.width - left * 2
                drawLine(Color.White.copy(alpha = .2f), Offset(0f, base), Offset(size.width, base), 1.dp.toPx())
                repeat(3) { i -> drawLine(Color.White.copy(alpha = .05f), Offset(0f, base + (i + 1) * 4.dp.toPx()), Offset(size.width, base + (i + 1) * 4.dp.toPx()), 1.dp.toPx()) }
                if (rise != null && set != null && set > rise) {
                    val arc = Path().apply {
                        moveTo(left, base)
                        repeat(60) { i ->
                            val fraction = (i + 1) / 60f
                            lineTo(left + width * fraction, base - sin(fraction * Math.PI).toFloat() * amplitude)
                        }
                    }
                    drawPath(arc, Color(0xFFDDEBFA).copy(alpha = .65f), style = Stroke(2.dp.toPx(), cap = StrokeCap.Round))
                    val fraction = ((now.epochSecond - rise).toDouble() / (set - rise)).coerceIn(0.0, 1.0)
                    val point = Offset(left + width * fraction.toFloat(), base - sin(fraction * Math.PI).toFloat() * amplitude)
                    drawCircle(Color.White.copy(alpha = .1f), 11.dp.toPx(), point)
                    drawCircle(if (daylight) Color(0xFFFFDA8C) else Color(0xFF1E304C), 5.dp.toPx(), point)
                    drawCircle(Color.White, 5.dp.toPx(), point, style = Stroke(1.2.dp.toPx()))
                }
            }
            Text(if (rise == null || set == null) "Güneş verisi yok" else if (daylight) "Gündüz" else "Gece", color = Quiet, fontSize = 11.sp)
        }
    }
}
