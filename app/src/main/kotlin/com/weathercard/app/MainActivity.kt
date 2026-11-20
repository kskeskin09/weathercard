package com.weathercard.app

import android.Manifest
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.weathercard.app.ui.WeatherGlyph
import com.weathercard.core.*
import kotlinx.coroutines.delay
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale
import kotlin.math.roundToInt

private val Ink = Color(0xFF0B1324)
private val Panel = Color(0xFF142239)
private val Muted = Color(0xFF9FAFC8)
private val Accent = Color(0xFFB3D4FF)
private val Turkish = Locale.forLanguageTag("tr-TR")

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MaterialTheme(colorScheme = darkColorScheme(
                primary = Accent, background = Ink, surface = Panel,
                onBackground = Color(0xFFF2F5FC), onSurface = Color(0xFFF2F5FC),
            )) { WeatherScreen() }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun WeatherScreen(vm: WeatherViewModel = viewModel()) {
    val state by vm.state.collectAsStateWithLifecycle()
    var showSearch by remember { mutableStateOf(false) }
    var confirmFull by remember { mutableStateOf(false) }
    val location = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) vm.useLocation() else vm.permissionDenied()
    }
    // Advancing the display clock never makes a network request.
    var now by remember { mutableStateOf(Instant.now()) }
    LaunchedEffect(Unit) { while (true) { now = Instant.now(); delay(30_000) } }
    Surface(Modifier.fillMaxSize(), color = Ink) {
        Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 18.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("WEATHERCARD", color = Accent, fontSize = 11.sp, letterSpacing = 3.sp, fontWeight = FontWeight.Bold)
                    Text("Hava, bir bakışta.", color = Muted, fontSize = 12.sp, modifier = Modifier.padding(top = 5.dp))
                }
                TextButton(onClick = { showSearch = true }, enabled = !state.busy) { Text("Şehir seç") }
            }
            PullToRefreshBox(isRefreshing = state.busy, onRefresh = vm::refresh, modifier = Modifier.weight(1f)) {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 28.dp),
                    verticalArrangement = Arrangement.spacedBy(18.dp),
                ) {
                    item {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                            OutlinedButton(onClick = { location.launch(Manifest.permission.ACCESS_COARSE_LOCATION) }, enabled = !state.busy) { Text("Konumumu kullan") }
                            TextButton(onClick = vm::refresh, enabled = !state.busy) { Text("Yenile ↻") }
                        }
                    }
                    state.message?.let { message ->
                        item { Card(colors = CardDefaults.cardColors(containerColor = Color(0xFF3A2933))) { Text(message, Modifier.padding(16.dp), fontSize = 13.sp) } }
                    }
                    val snapshot = state.snapshot
                    if (snapshot == null) {
                        item {
                            Column(Modifier.fillMaxWidth().padding(vertical = 64.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                WeatherGlyph(2, modifier = Modifier.size(110.dp))
                                Text(if (state.busy) "Gökyüzüne bakıyoruz…" else "Tahminler henüz yüklenemedi", color = Muted)
                                if (!state.busy) Button(onClick = vm::refresh) { Text("Tekrar dene") }
                            }
                        }
                    } else {
                        val zone = ZoneId.of(snapshot.timezone)
                        val hour = now.truncatedTo(ChronoUnit.HOURS).epochSecond
                        val current = snapshot.hours.find { it.time == hour }
                        val visibleHours = snapshot.hours.filter { it.time >= hour }.take(48)
                        item { CurrentCard(snapshot.place, current, now, zone) }
                        item {
                            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                Text("Saat saat", fontSize = 20.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                                Text("48 SAAT", color = Muted, fontSize = 10.sp, letterSpacing = 2.sp)
                            }
                        }
                        item {
                            if (visibleHours.isEmpty()) Text("Saatlik tahminin süresi doldu. 7 günlük tahmini yeniden yükleyebilirsin.", color = Muted)
                            else Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                visibleHours.forEach { HourCard(it, zone, it.time == hour) }
                            }
                        }
                        item {
                            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                Text("7 günlük tahmin", fontSize = 20.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                                TextButton(onClick = { confirmFull = true }, enabled = !state.busy) { Text("Tümünü güncelle", fontSize = 11.sp) }
                            }
                        }
                        val today = now.atZone(zone).toLocalDate()
                        val days = snapshot.days.filter { !LocalDate.parse(it.date).isBefore(today) }
                        items(days, key = { it.date }) { DayRow(it, today) }
                        item {
                            Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                                val fullTime = Instant.ofEpochSecond(snapshot.fullFetchedAt)
                                if (days.size < 7 || fullTime.plus(24, ChronoUnit.HOURS).isBefore(now)) {
                                    Text("Günlük tahmin eski. Yeni 7 gün için “Tümünü güncelle”yi kullan.", color = Color(0xFFFFD68B), fontSize = 12.sp)
                                }
                                Text("7 günlük kayıt: ${formatTime(snapshot.fullFetchedAt, zone)}", color = Muted, fontSize = 11.sp)
                                snapshot.sixHoursFetchedAt?.let { Text("Son 6 saat yenilemesi: ${formatTime(it, zone)}", color = Muted, fontSize = 11.sp) }
                                Text("Yenileme yalnızca bu saat dahil 6 saatlik tahmini değiştirir. Günlük tahminler korunur.", color = Muted, fontSize = 11.sp)
                            }
                        }
                    }
                    item {
                        val uri = LocalUriHandler.current
                        TextButton(onClick = { uri.openUri("https://open-meteo.com/") }) { Text("Hava durumu: Open-Meteo · CC BY 4.0", color = Muted, fontSize = 11.sp) }
                    }
                }
            }
        }
    }
    if (showSearch) CityDialog(state, vm::search, onSelect = { vm.load(it); showSearch = false }, onDismiss = { showSearch = false })
    if (confirmFull) AlertDialog(
        onDismissRequest = { confirmFull = false },
        title = { Text("7 günü yeniden yükle") },
        text = { Text("Bu işlem saatlik ve günlük tahminlerin tamamını yeniden indirir. Normal yenileme yalnızca 6 saati günceller.") },
        confirmButton = { TextButton(onClick = { confirmFull = false; vm.reloadAll() }) { Text("Tümünü güncelle") } },
        dismissButton = { TextButton(onClick = { confirmFull = false }) { Text("Vazgeç") } },
    )
}

@Composable
private fun CurrentCard(place: Place, hour: HourWeather?, now: Instant, zone: ZoneId) {
    val condition = WeatherCondition.from(hour?.weatherCode, hour?.isDay ?: true)
    Column(Modifier.fillMaxWidth().background(
        Brush.linearGradient(listOf(Color(0xFF254D7A), Color(0xFF172C4A))), RoundedCornerShape(30.dp)
    ).padding(24.dp)) {
        Text(place.name, fontSize = 24.sp, fontWeight = FontWeight.SemiBold)
        Text(now.atZone(zone).format(DateTimeFormatter.ofPattern("d MMMM EEEE", Turkish)), color = Accent, fontSize = 13.sp, modifier = Modifier.padding(top = 6.dp))
        Row(Modifier.fillMaxWidth().padding(vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(degrees(hour?.temperature), fontSize = 76.sp, fontWeight = FontWeight.Light, letterSpacing = (-4).sp)
                Text(condition.description, fontSize = 16.sp)
                Text("Hissedilen ${degrees(hour?.apparentTemperature)}", color = Accent, fontSize = 13.sp, modifier = Modifier.padding(top = 6.dp))
            }
            WeatherGlyph(hour?.weatherCode, hour?.isDay ?: true, Modifier.size(126.dp))
        }
        HorizontalDivider(color = Color.White.copy(alpha = .12f))
        Row(Modifier.fillMaxWidth().padding(top = 18.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            Stat("Yağış", percent(hour?.precipitationProbability))
            Stat("Rüzgâr", "${number(hour?.windSpeed)} km/sa")
            Stat("Nem", percent(hour?.humidity))
        }
        Row(Modifier.fillMaxWidth().padding(top = 16.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            Stat("Basınç", "${number(hour?.pressure)} hPa")
            Stat("Görüş", "${number(hour?.visibility?.div(1000))} km")
            Stat("UV", number(hour?.uvIndex))
        }
    }
}

@Composable
private fun Stat(label: String, value: String) {
    Column { Text(label, color = Accent, fontSize = 11.sp); Text(value, fontSize = 14.sp, modifier = Modifier.padding(top = 5.dp)) }
}

@Composable
private fun HourCard(hour: HourWeather, zone: ZoneId, current: Boolean) {
    Column(Modifier.width(76.dp).background(if (current) Color(0xFF28486E) else Panel, RoundedCornerShape(22.dp)).padding(vertical = 14.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(if (current) "Şimdi" else Instant.ofEpochSecond(hour.time).atZone(zone).format(DateTimeFormatter.ofPattern("HH:mm")), color = if (current) Color.White else Muted, fontSize = 12.sp)
        WeatherGlyph(hour.weatherCode, hour.isDay ?: true, Modifier.size(53.dp).padding(top = 5.dp))
        Text(degrees(hour.temperature), fontSize = 20.sp, fontWeight = FontWeight.Medium)
        Text(percent(hour.precipitationProbability), color = Accent, fontSize = 11.sp, modifier = Modifier.padding(top = 5.dp))
    }
}

@Composable
private fun DayRow(day: DayWeather, today: LocalDate) {
    Row(Modifier.fillMaxWidth().background(Panel, RoundedCornerShape(20.dp)).padding(horizontal = 16.dp, vertical = 9.dp), verticalAlignment = Alignment.CenterVertically) {
        val date = LocalDate.parse(day.date)
        Column(Modifier.weight(1f)) {
            Text(if (date == today) "Bugün" else date.format(DateTimeFormatter.ofPattern("EEEE", Turkish)), fontSize = 14.sp)
            Text(WeatherCondition.from(day.weatherCode).description, color = Muted, fontSize = 10.sp)
        }
        WeatherGlyph(day.weatherCode, modifier = Modifier.size(48.dp))
        Text(percent(day.precipitationProbability), color = Accent, fontSize = 11.sp, modifier = Modifier.width(42.dp))
        Text(degrees(day.minimum), color = Muted, modifier = Modifier.width(38.dp))
        Text(degrees(day.maximum), fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun CityDialog(state: WeatherUiState, onSearch: (String) -> Unit, onSelect: (Place) -> Unit, onDismiss: () -> Unit) {
    var query by remember { mutableStateOf("") }
    AlertDialog(onDismissRequest = onDismiss,
        title = { Text("Şehir bul") },
        text = {
            Column {
                OutlinedTextField(query, onValueChange = { query = it }, label = { Text("Şehir veya ilçe") }, singleLine = true)
                TextButton(onClick = { onSearch(query) }, enabled = !state.searching && query.trim().length >= 2) { Text(if (state.searching) "Aranıyor…" else "Ara") }
                state.searchMessage?.let { Text(it, color = Muted, fontSize = 12.sp) }
                LazyColumn(Modifier.heightIn(max = 300.dp)) {
                    items(state.searchResults) { place -> TextButton(onClick = { onSelect(place) }) { Text(place.name) } }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Kapat") } },
    )
}

private fun degrees(value: Double?) = value?.roundToInt()?.let { "$it°" } ?: "—°"
private fun number(value: Double?) = value?.roundToInt()?.toString() ?: "—"
private fun percent(value: Double?) = value?.roundToInt()?.let { "%$it" } ?: "—"
private fun formatTime(value: Long, zone: ZoneId) = Instant.ofEpochSecond(value).atZone(zone).format(DateTimeFormatter.ofPattern("d MMM HH:mm", Turkish))
