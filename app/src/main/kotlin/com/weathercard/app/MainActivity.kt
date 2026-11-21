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
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.weathercard.app.ui.WeatherGlyph
import com.weathercard.app.ui.UsageScreen
import com.weathercard.app.ui.WeatherBackdrop
import com.weathercard.app.ui.HourlyForecastChart
import com.weathercard.app.ui.WindCard
import com.weathercard.app.ui.SunTimesCard
import com.weathercard.app.ui.WeatherTypography
import com.weathercard.app.ui.WeatherDetails
import com.weathercard.app.ui.LocationMark
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.style.TextOverflow
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
    ), typography = WeatherTypography, content = content)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun WeatherScreen(vm: WeatherViewModel = viewModel(), previewNow: Instant? = null) {
    val state by vm.state.collectAsStateWithLifecycle()
    var showSearch by remember { mutableStateOf(false) }
    var confirmFull by remember { mutableStateOf(false) }
    var showUsage by remember { mutableStateOf(false) }
    var showMenu by remember { mutableStateOf(false) }
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
    // Hourly six-hour refresh, only while the app is on screen; previews never touch the network.
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(previewNow, lifecycle) {
        if (previewNow == null) lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) { vm.refreshEveryHourWhileVisible() }
    }
    val activeZone = state.snapshot?.timezone?.let(ZoneId::of) ?: ZoneId.systemDefault()
    val activeHour = now.atZone(activeZone).truncatedTo(ChronoUnit.HOURS).toEpochSecond()
    val activeWeather = state.snapshot?.hours?.find { it.time == activeHour }
    val daylight = activeWeather?.isDay ?: (now.atZone(activeZone).hour in 7..18)
    Surface(Modifier.fillMaxSize(), color = Ink) {
      Box(Modifier.fillMaxSize()) {
        WeatherBackdrop(activeWeather?.weatherCode, daylight, Modifier.fillMaxSize(), now, activeZone, state.snapshot?.days.orEmpty())
        Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { showSearch = true }, enabled = !state.busy, modifier = Modifier.semantics { contentDescription = "Şehir ekle" }) {
                    Text("+", fontSize = 38.sp, lineHeight = 40.sp, fontWeight = FontWeight.Normal)
                }
                Row(Modifier.weight(1f), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                    if (state.snapshot?.place?.isCurrentLocation == true) { LocationMark(Modifier.size(14.dp)); Spacer(Modifier.width(7.dp)) }
                    Text(state.snapshot?.place?.name?.substringBefore(",") ?: "Hava durumu", fontSize = 21.sp, lineHeight = 26.sp,
                        fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                Box {
                    IconButton(onClick = { showMenu = true }, modifier = Modifier.semantics { contentDescription = "Diğer seçenekler" }) { Text("⋮", fontSize = 28.sp) }
                    DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                        DropdownMenuItem(text = { Text("Konumumu kullan") }, enabled = !state.busy, onClick = {
                            showMenu = false; location.launch(arrayOf(Manifest.permission.ACCESS_COARSE_LOCATION, Manifest.permission.ACCESS_FINE_LOCATION))
                        })
                        DropdownMenuItem(text = { Text("Şehir seç") }, onClick = { showMenu = false; showSearch = true })
                        DropdownMenuItem(text = { Text("Kota") }, onClick = { showMenu = false; showUsage = true })
                        DropdownMenuItem(text = { Text("7 günü yeniden yükle") }, enabled = !state.busy, onClick = { showMenu = false; confirmFull = true })
                    }
                }
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
                        item { CurrentCard(current, now, zone) }
                        item {
                            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                Text("Saatlik tahmin", fontSize = 15.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                                Text("${snapshot.hours.size} SAAT", color = Muted, fontSize = 10.sp, letterSpacing = 2.sp)
                                TextButton(onClick = vm::refresh, enabled = !state.busy) { Text("Yenile ↻", fontSize = 12.sp) }
                            }
                        }
                        item {
                            key(snapshot.place.cacheKey) { HourlyForecastChart(snapshot.hours, hour, zone) }
                        }
                        val today = now.atZone(zone).toLocalDate()
                        val days = snapshot.days.filter { !LocalDate.parse(it.date).isBefore(today) }
                        item { ForecastDaysCard(days, today, !state.busy) { confirmFull = true } }
                        item { Text("DETAYLAR", color = Muted, fontSize = 10.sp, letterSpacing = 1.5.sp, modifier = Modifier.padding(top = 10.dp)) }
                        item(key = "weather-details") { WeatherDetails(current, snapshot.days.find { it.date == today.toString() }, now, zone) }
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
private fun CurrentCard(hour: HourWeather?, now: Instant, zone: ZoneId) {
    val condition = WeatherCondition.from(hour?.weatherCode, hour?.isDay ?: true)
    Column(Modifier.fillMaxWidth().heightIn(min = 240.dp).padding(top = 28.dp, bottom = 30.dp),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Text(degrees(hour?.temperature), fontSize = 104.sp, lineHeight = 108.sp, letterSpacing = (-4).sp, fontWeight = FontWeight.Bold)
        Text(condition.description, color = Accent, fontSize = 18.sp, lineHeight = 24.sp, fontWeight = FontWeight.SemiBold)
        Text(now.atZone(zone).format(DateTimeFormatter.ofPattern("HH:mm · d MMMM EEEE", Turkish)), color = Muted, fontSize = 11.sp,
            lineHeight = 16.sp, modifier = Modifier.padding(top = 12.dp))
    }
}

@Composable
private fun ForecastDaysCard(days: List<DayWeather>, today: LocalDate, enabled: Boolean, onFullRefresh: () -> Unit) {
    var expanded by remember(days) { mutableStateOf(false) }
    Column(Modifier.fillMaxWidth().background(Color.White.copy(alpha = .07f), RoundedCornerShape(28.dp))
        .border(1.dp, Color.White.copy(alpha = .07f), RoundedCornerShape(28.dp)).padding(16.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("7 günlük tahmin", color = Muted, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
            TextButton(onClick = onFullRefresh, enabled = enabled, contentPadding = PaddingValues(horizontal = 4.dp)) { Text("Tümünü güncelle", fontSize = 10.sp) }
        }
        days.take(if (expanded) 7 else 3).forEach { DayRow(it, today) }
        if (days.isEmpty()) Text("Kayıtlı günlük tahminin süresi doldu.", fontSize = 12.sp, color = Muted)
        if (days.size > 3) FilledTonalButton(onClick = { expanded = !expanded }, modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
            colors = ButtonDefaults.filledTonalButtonColors(containerColor = Color.White.copy(alpha = .15f), contentColor = Color.White)) {
            Text(if (expanded) "Kısalt" else "7 günlük tahmin", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun DayRow(day: DayWeather, today: LocalDate) {
    Row(Modifier.fillMaxWidth().padding(vertical = 7.dp), verticalAlignment = Alignment.CenterVertically) {
        val date = LocalDate.parse(day.date)
        WeatherGlyph(day.weatherCode, modifier = Modifier.size(34.dp))
        Spacer(Modifier.width(8.dp))
        Text(if (date == today) "Bugün" else if (date == today.plusDays(1)) "Yarın" else date.format(DateTimeFormatter.ofPattern("EEE", Turkish)), fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold, modifier = Modifier.width(48.dp))
        Text(WeatherCondition.from(day.weatherCode).description, color = Muted, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
        Spacer(Modifier.width(6.dp))
        Text(degrees(day.maximum), fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
        Text(" / ${degrees(day.minimum)}", color = Muted, fontSize = 13.sp)
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
