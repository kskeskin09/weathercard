package com.weathercard.app.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.weathercard.app.R
import com.weathercard.app.UiText
import com.weathercard.app.resolve
import com.weathercard.core.UsageSummary
import com.weathercard.core.UsageTotals
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private val Muted = Color(0xFF9FAFC8)

@Composable
fun UsageScreen(summary: UsageSummary?, error: UiText?, onBack: () -> Unit, onReload: () -> Unit) {
    BackHandler(onBack = onBack)
    Surface(Modifier.fillMaxSize(), color = Color(0xFF0B1324)) {
        LazyColumn(Modifier.statusBarsPadding().navigationBarsPadding(), contentPadding = PaddingValues(22.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            item {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = onBack) { Text(stringResource(R.string.action_back)) }
                    Spacer(Modifier.weight(1f))
                    TextButton(onClick = onReload) { Text(stringResource(R.string.usage_reload)) }
                }
                Text(stringResource(R.string.usage_title), fontSize = 30.sp, fontWeight = FontWeight.SemiBold)
                Text(stringResource(R.string.usage_subtitle), color = Muted, fontSize = 13.sp, modifier = Modifier.padding(top = 6.dp))
            }
            item {
                Card(colors = CardDefaults.cardColors(containerColor = Color(0xFF253851))) {
                    Text(stringResource(R.string.usage_disclaimer), Modifier.padding(16.dp), fontSize = 12.sp)
                }
            }
            error?.let { item { Text(it.resolve(), color = Color(0xFFFFB8B8)) } }
            if (summary == null) {
                item { if (error == null) CircularProgressIndicator() }
            } else {
                item { QuotaCard(stringResource(R.string.usage_hour), summary.hour.quotaTenths, 5_000, summary.hour.weatherRequests) }
                item { QuotaCard(stringResource(R.string.usage_day), summary.day.quotaTenths, 10_000, summary.day.weatherRequests) }
                item { QuotaCard(stringResource(R.string.usage_month), summary.month.quotaTenths, 300_000, summary.month.weatherRequests) }
                item {
                    Text(stringResource(R.string.usage_periods_note), color = Muted, fontSize = 11.sp)
                }
                item {
                    Column(Modifier.fillMaxWidth().background(Color(0xFF142239), RoundedCornerShape(22.dp)).padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(stringResource(R.string.usage_month_details), fontWeight = FontWeight.SemiBold, fontSize = 18.sp)
                        Detail(stringResource(R.string.usage_full), summary.month.full.toString())
                        Detail(stringResource(R.string.usage_partial), summary.month.partial.toString())
                        Detail(stringResource(R.string.usage_searches), summary.month.searches.toString())
                        HorizontalDivider(color = Color.White.copy(alpha = .1f))
                        Detail(stringResource(R.string.usage_lifetime), stringResource(R.string.usage_units, units(summary.lifetime.quotaTenths)))
                        Text(stringResource(R.string.usage_search_note), color = Muted, fontSize = 11.sp)
                    }
                }
                item {
                    Text(stringResource(R.string.usage_how), fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
                    Text(stringResource(R.string.usage_costs, units(UsageTotals.FULL_COST_TENTHS), units(UsageTotals.PARTIAL_COST_TENTHS)), color = Muted, fontSize = 13.sp, lineHeight = 22.sp, modifier = Modifier.padding(top = 8.dp))
                    Text(stringResource(R.string.usage_started, Instant.ofEpochSecond(summary.startedAt).atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("d MMM yyyy HH:mm", Locale.getDefault()))), color = Muted, fontSize = 11.sp, modifier = Modifier.padding(top = 12.dp))
                }
            }
            item {
                val uri = LocalUriHandler.current
                TextButton(onClick = { uri.openUri("https://open-meteo.com/en/pricing") }) { Text(stringResource(R.string.usage_pricing_link), fontSize = 12.sp) }
            }
        }
    }
}

@Composable
private fun QuotaCard(title: String, usedTenths: Long, limit: Long, requests: Long) {
    val ratio = usedTenths.toDouble() / (limit * 10)
    Column(Modifier.fillMaxWidth().background(Color(0xFF142239), RoundedCornerShape(22.dp)).padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(title, fontWeight = FontWeight.SemiBold)
            Text(stringResource(R.string.percent, String.format(Locale.getDefault(), "%.2f", ratio * 100)), color = Muted, fontSize = 12.sp)
        }
        Text(stringResource(R.string.usage_units_of, units(usedTenths), String.format(Locale.getDefault(), "%,d", limit)), fontSize = 23.sp)
        LinearProgressIndicator(progress = { ratio.coerceIn(0.0, 1.0).toFloat() }, modifier = Modifier.fillMaxWidth(), color = if (ratio >= .8) Color(0xFFFFCE86) else Color(0xFFA6CCFF))
        Text(LocalContext.current.resources.getQuantityString(R.plurals.usage_requests, requests.coerceAtMost(Int.MAX_VALUE.toLong()).toInt(), requests), color = Muted, fontSize = 11.sp)
    }
}

@Composable
private fun Detail(label: String, value: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, color = Muted, fontSize = 12.sp)
        Text(value, fontSize = 12.sp)
    }
}

private fun units(tenths: Long) = String.format(Locale.getDefault(), "%,.1f", tenths / 10.0)

