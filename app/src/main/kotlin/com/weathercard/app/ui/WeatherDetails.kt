package com.weathercard.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.res.stringResource
import com.weathercard.app.R
import com.weathercard.core.DayWeather
import com.weathercard.core.HourWeather
import java.time.Instant
import java.time.ZoneId
import kotlin.math.roundToInt

private const val PressureRow = 3

@Composable
fun WeatherDetails(hour: HourWeather?, day: DayWeather?, now: Instant, zone: ZoneId) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            WindCard(hour, compact = true)
            SunTimesCard(day, now, zone, compact = true)
        }
        Column(Modifier.weight(1f).height(274.dp)
            .background(Color.White.copy(alpha = .07f), RoundedCornerShape(28.dp))
            .border(1.dp, Color.White.copy(alpha = .09f), RoundedCornerShape(28.dp)).padding(horizontal = 14.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.SpaceEvenly) {
            val rows = listOf(
                stringResource(R.string.detail_humidity) to (hour?.humidity?.roundToInt()?.let { stringResource(R.string.percent, it.toString()) } ?: "—"),
                stringResource(R.string.detail_feels_like) to (hour?.apparentTemperature?.roundToInt()?.let { "$it°" } ?: "—"),
                stringResource(R.string.detail_uv) to (hour?.uvIndex?.roundToInt()?.toString() ?: "—"),
                stringResource(R.string.detail_pressure) to (hour?.pressure?.roundToInt()?.let { "$it hPa" } ?: "—"),
                stringResource(R.string.detail_precipitation) to (hour?.precipitationProbability?.roundToInt()?.let { stringResource(R.string.percent, it.toString()) } ?: "—"),
            )
            rows.forEachIndexed { index, (label, value) ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(label, color = Color(0xFFC1D0E2), fontSize = 10.sp, lineHeight = 14.sp, modifier = Modifier.weight(1f))
                    Text(value, fontSize = if (index == PressureRow) 12.sp else 15.sp, lineHeight = 18.sp, fontWeight = FontWeight.SemiBold)
                }
                if (index != rows.lastIndex) HorizontalDivider(color = Color.White.copy(alpha = .09f))
            }
        }
    }
}
