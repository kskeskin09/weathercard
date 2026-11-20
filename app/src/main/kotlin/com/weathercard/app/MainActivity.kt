package com.weathercard.app

import android.Manifest
import android.os.Bundle
import android.content.Intent
import android.provider.Settings
import android.net.Uri
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.weathercard.app.ui.WeatherGlyph
import com.weathercard.app.ui.UsageScreen
import com.weathercard.app.ui.WeatherBackdrop
import com.weathercard.app.ui.MetricTile
import com.weathercard.app.ui.MetricKind
import com.weathercard.app.ui.HourlyForecastChart
import com.weathercard.app.ui.WindCard
import com.weathercard.app.ui.SunTimesCard
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
private val Muted = Color(0xFFBCD0E5)
private val Accent = Color(0xFFD8EDFF)
private val Turkish = Locale.forLanguageTag("tr-TR")

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            WeatherCardTheme { WeatherScreen() }
        }
    }
}

@Composable
internal fun WeatherCardTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = darkColorScheme(
        primary = Accent, background = Ink, surface = Panel,
        onBackground = Color(0xFFF2F5FC), onSurface = Color(0xFFF2F5FC),
    ), content = content)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun WeatherScreen(vm: WeatherViewModel = viewModel(), previewNow: Instant? = null) {
    val state by vm.state.collectAsStateWithLifecycle()
    var showSearch by remember { mutableStateOf(false) }
    var confirmFull by remember { mutableStateOf(false) }
    var showUsage by remember { mutableStateOf(false) }
    if (showUsage) {
        LaunchedEffect(Unit) { vm.loadUsage() }
        UsageScreen(state.usage, state.usageError, onBack = { showUsage = false }, onReload = vm::loadUsage)
        return
    }
    val location = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { permissions ->
        if (permissions[Manifest.permission.ACCESS_COARSE_LOCATION] == true || permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true) vm.useLocation() else vm.permissionDenied()
    }
    // Advancing the display clock never makes a network request.
    var now by remember(previewNow) { mutableStateOf(previewNow ?: Instant.now()) }
    LaunchedEffect(previewNow) { if (previewNow == null) while (true) { now = Instant.now(); delay(30_000) } }
    val activeZone = state.snapshot?.timezone?.let(ZoneId::of) ?: ZoneId.systemDefault()
    val activeHour = now.atZone(activeZone).truncatedTo(ChronoUnit.HOURS).toEpochSecond()
    val activeWeather = state.snapshot?.hours?.find { it.time == activeHour }
    val daylight = activeWeather?.isDay ?: (now.atZone(activeZone).hour in 7..18)
    Surface(Modifier.fillMaxSize(), color = Ink) {
      Box(Modifier.fillMaxSize()) {
        WeatherBackdrop(activeWeather?.weatherCode, daylight, Modifier.fillMaxSize())
        Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("WEATHERCARD", color = Accent, fontSize = 10.sp, letterSpacing = 2.sp, fontWeight = FontWeight.SemiBold)
                }
                TextButton(onClick = { location.launch(arrayOf(Manifest.permission.ACCESS_COARSE_LOCATION, Manifest.permission.ACCESS_FINE_LOCATION)) }, enabled = !state.busy, contentPadding = PaddingValues(horizontal = 10.dp)) { Text("Konum", fontSize = 12.sp) }
                TextButton(onClick = { showSearch = true }, enabled = !state.busy, contentPadding = PaddingValues(horizontal = 10.dp)) { Text("Şehir", fontSize = 12.sp) }
                TextButton(onClick = { showUsage = true }, contentPadding = PaddingValues(horizontal = 10.dp)) { Text("Kota", fontSize = 12.sp) }
            }
            PullToRefreshBox(isRefreshing = state.busy, onRefresh = vm::refresh, modifier = Modifier.weight(1f)) {
                LazyColumn(
                    modifier = Modifier.fillMaxSize().testTag("forecast-list"),
                    contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 28.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    state.status?.let { status -> item { Text(status, color = Accent, fontSize = 13.sp) } }
                    state.message?.let { message ->
                        item {
                            val context = LocalContext.current
                            Card(colors = CardDefaults.cardColors(containerColor = Color(0xFF3A2933))) {
                                Text(message, Modifier.padding(16.dp), fontSize = 13.sp)
                                if (message.contains("konum", ignoreCase = true)) Row(Modifier.padding(horizontal = 8.dp)) {
                                    TextButton(onClick = { context.startActivity(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)) }) { Text("Konum ayarları", fontSize = 12.sp) }
                                    TextButton(onClick = { context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}"))) }) { Text("Uygulama izni", fontSize = 12.sp) }
                                }
                            }
                        }
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
                        val hour = now.atZone(zone).truncatedTo(ChronoUnit.HOURS).toEpochSecond()
                        val current = snapshot.hours.find { it.time == hour }
                        item { CurrentCard(snapshot.place, current, now, zone) }
                        item {
                            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                Text("Saat saat", fontSize = 20.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                                Text("${snapshot.hours.size} SAAT", color = Muted, fontSize = 10.sp, letterSpacing = 2.sp)
                                TextButton(onClick = vm::refresh, enabled = !state.busy) { Text("Yenile ↻", fontSize = 12.sp) }
                            }
                        }
                        item {
                            key(snapshot.place.cacheKey) { HourlyForecastChart(snapshot.hours, hour, zone) }
                        }
                        val today = now.atZone(zone).toLocalDate()
                        item { WindCard(current) }
                        item { SunTimesCard(snapshot.days.find { it.date == today.toString() }, now, zone) }
                        item {
                            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                Text("7 günlük tahmin", fontSize = 20.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                                TextButton(onClick = { confirmFull = true }, enabled = !state.busy) { Text("Tümünü güncelle", fontSize = 11.sp) }
                            }
                        }
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
                        Column {
                            TextButton(onClick = { uri.openUri("https://open-meteo.com/") }) { Text("Hava durumu: Open-Meteo · CC BY 4.0", color = Muted, fontSize = 11.sp) }
                            Text("WeatherCard ${BuildConfig.VERSION_NAME}", color = Muted, fontSize = 10.sp, modifier = Modifier.padding(start = 12.dp))
                        }
                    }
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
    Column(Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 4.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(place.name.substringBefore(","), fontSize = 24.sp, fontWeight = FontWeight.Medium, maxLines = 1)
                Text(now.atZone(zone).format(DateTimeFormatter.ofPattern("d MMMM · EEEE", Turkish)), color = Muted, fontSize = 12.sp, modifier = Modifier.padding(top = 4.dp))
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(now.atZone(zone).format(DateTimeFormatter.ofPattern("HH:mm")), fontSize = 24.sp, fontWeight = FontWeight.Light)
                Text("YEREL SAAT", color = Muted, fontSize = 8.sp, letterSpacing = 1.5.sp)
            }
        }
        Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(degrees(hour?.temperature), fontSize = 94.sp, fontWeight = FontWeight.Light, letterSpacing = (-6).sp, lineHeight = 100.sp)
                Text(condition.description, color = Accent, fontSize = 16.sp, fontWeight = FontWeight.Medium)
            }
            WeatherGlyph(hour?.weatherCode, hour?.isDay ?: true, Modifier.size(136.dp))
        }
        Row(Modifier.fillMaxWidth().padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            MetricTile("Hissedilen", degrees(hour?.apparentTemperature), "", MetricKind.FEELS_LIKE, Modifier.weight(1f))
            MetricTile("Yağış ihtimali", percent(hour?.precipitationProbability), "", MetricKind.RAIN, Modifier.weight(1f))
            MetricTile("Rüzgâr", number(hour?.windSpeed), "km/sa", MetricKind.WIND, Modifier.weight(1f))
        }
        Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            MetricTile("Nem", percent(hour?.humidity), "", MetricKind.HUMIDITY, Modifier.weight(1f))
            MetricTile("UV indeksi", number(hour?.uvIndex), "", MetricKind.UV, Modifier.weight(1f))
            MetricTile("Basınç", number(hour?.pressure), "hPa", MetricKind.PRESSURE, Modifier.weight(1f))
        }
    }
}

@Composable
private fun DayRow(day: DayWeather, today: LocalDate) {
    Row(Modifier.fillMaxWidth().background(Color.White.copy(alpha = .055f), RoundedCornerShape(20.dp))
        .border(1.dp, Color.White.copy(alpha = .06f), RoundedCornerShape(20.dp)).padding(horizontal = 16.dp, vertical = 9.dp), verticalAlignment = Alignment.CenterVertically) {
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
