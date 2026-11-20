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
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

enum class MetricKind { FEELS_LIKE, RAIN, WIND, HUMIDITY, UV, PRESSURE }

@Composable
fun MetricTile(label: String, value: String, unit: String, kind: MetricKind, modifier: Modifier = Modifier) {
    Column(modifier.height(76.dp).background(Color.White.copy(alpha = .07f), RoundedCornerShape(19.dp))
        .border(1.dp, Color.White.copy(alpha = .1f), RoundedCornerShape(19.dp)).padding(12.dp), verticalArrangement = Arrangement.SpaceBetween) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
            MetricSymbol(kind, Modifier.size(14.dp))
            Text(label, color = Color(0xFFC3D8ED), fontSize = 10.sp, maxLines = 1)
        }
        Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(value, fontSize = 22.sp, fontWeight = FontWeight.Medium, letterSpacing = (-.6).sp)
            if (unit.isNotEmpty()) Text(unit, color = Color(0xFFB8D3EB), fontSize = 9.sp, modifier = Modifier.padding(bottom = 4.dp))
        }
    }
}

@Composable
private fun MetricSymbol(kind: MetricKind, modifier: Modifier) {
    Canvas(modifier) {
        val color = Color(0xFFB5D9F7)
        scale(size.minDimension / 24f, size.minDimension / 24f, Offset.Zero) {
            when (kind) {
                MetricKind.FEELS_LIKE -> {
                    drawRoundRect(color, Offset(9f, 2f), androidx.compose.ui.geometry.Size(6f, 15f), androidx.compose.ui.geometry.CornerRadius(3f), style = Stroke(1.8f))
                    drawCircle(color, 5f, Offset(12f, 17f), style = Stroke(1.8f))
                    drawLine(color, Offset(12f, 9f), Offset(12f, 18f), 2f, StrokeCap.Round)
                }
                MetricKind.RAIN, MetricKind.HUMIDITY -> {
                    val drop = Path().apply { moveTo(12f, 2f); cubicTo(9f, 8f, 4f, 12f, 5f, 16f); cubicTo(6f, 24f, 19f, 24f, 19f, 15f); cubicTo(19f, 11f, 14f, 5f, 12f, 2f); close() }
                    drawPath(drop, color, style = Stroke(1.8f))
                    if (kind == MetricKind.HUMIDITY) drawLine(color, Offset(9f, 17f), Offset(15f, 11f), 1.5f, StrokeCap.Round)
                }
                MetricKind.WIND -> {
                    repeat(3) { i ->
                        val y = 6f + i * 6
                        val wind = Path().apply { moveTo(2f, y); lineTo(15f - i * 2, y); cubicTo(24f, y, 22f, y - 7, 17f, y - 4) }
                        drawPath(wind, color, style = Stroke(1.8f, cap = StrokeCap.Round))
                    }
                }
                MetricKind.UV -> {
                    drawCircle(color, 5f, Offset(12f, 12f), style = Stroke(1.8f))
                    repeat(8) { i ->
                        val angle = i * Math.PI / 4
                        fun point(r: Float) = Offset(12f + kotlin.math.cos(angle).toFloat() * r, 12f + kotlin.math.sin(angle).toFloat() * r)
                        drawLine(color, point(8f), point(10f), 1.8f, StrokeCap.Round)
                    }
                }
                MetricKind.PRESSURE -> {
                    drawArc(color, 140f, 260f, false, Offset(3f, 3f), androidx.compose.ui.geometry.Size(18f, 18f), style = Stroke(1.8f, cap = StrokeCap.Round))
                    drawLine(color, Offset(12f, 14f), Offset(17f, 8f), 1.8f, StrokeCap.Round)
                    drawCircle(color, 2f, Offset(12f, 14f))
                }
            }
        }
    }
}
