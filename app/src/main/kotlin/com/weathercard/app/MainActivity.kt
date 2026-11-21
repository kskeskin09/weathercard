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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.semantics.clearAndSetSemantics
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
import androidx.compose.ui.res.stringResource
import com.weathercard.app.ui.labelRes
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
    var confirmRemove by remember { mutableStateOf(false) }
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
    val pager = rememberPagerState(initialPage = state.page) { state.places.size }
    // The settled page becomes the selected city; programmatic changes (new city, removal) scroll the pager.
    LaunchedEffect(pager) { snapshotFlow { pager.settledPage }.collect(vm::selectPage) }
    LaunchedEffect(state.page, state.places.size) { if (pager.currentPage != state.page) pager.animateScrollToPage(state.page) }
    // Header and backdrop follow the page under the finger so they change as soon as the swipe passes halfway.
    val shownPlace = state.places.getOrNull(pager.currentPage) ?: state.saved.current
    val shown = state.snapshotFor(shownPlace)
    val activeZone = shown?.timezone?.let(ZoneId::of) ?: ZoneId.systemDefault()
    val activeHour = now.atZone(activeZone).truncatedTo(ChronoUnit.HOURS).toEpochSecond()
    val activeWeather = shown?.hours?.find { it.time == activeHour }
    val daylight = activeWeather?.isDay ?: (now.atZone(activeZone).hour in 7..18)
    val addCity = stringResource(R.string.action_add_city)
    val moreOptions = stringResource(R.string.action_more)
    Surface(Modifier.fillMaxSize(), color = Ink) {
      Box(Modifier.fillMaxSize()) {
        WeatherBackdrop(activeWeather?.weatherCode, daylight, Modifier.fillMaxSize(), now, activeZone, shown?.days.orEmpty())
        Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { showSearch = true }, enabled = !state.busy, modifier = Modifier.semantics { contentDescription = addCity }) {
                    Text("+", fontSize = 38.sp, lineHeight = 40.sp, fontWeight = FontWeight.Normal)
                }
                Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (shownPlace.isCurrentLocation) { LocationMark(Modifier.size(14.dp)); Spacer(Modifier.width(7.dp)) }
                        Text(shownPlace.name.substringBefore(","), fontSize = 21.sp, lineHeight = 26.sp,
                            fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    if (state.places.size > 1) PageDots(state.places, pager.currentPage, Modifier.padding(top = 5.dp))
                }
                Box {
                    IconButton(onClick = { showMenu = true }, modifier = Modifier.semantics { contentDescription = moreOptions }) { Text("⋮", fontSize = 28.sp) }
                    DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                        DropdownMenuItem(text = { Text(stringResource(R.string.menu_use_location)) }, enabled = !state.busy, onClick = {
                            showMenu = false; location.launch(arrayOf(Manifest.permission.ACCESS_COARSE_LOCATION, Manifest.permission.ACCESS_FINE_LOCATION))
                        })
                        DropdownMenuItem(text = { Text(addCity) }, enabled = state.saved.canAdd, onClick = { showMenu = false; showSearch = true })
                        if (state.places.size > 1) DropdownMenuItem(text = { Text(stringResource(R.string.menu_remove_city)) }, enabled = !state.busy,
                            onClick = { showMenu = false; confirmRemove = true })
                        DropdownMenuItem(text = { Text(stringResource(R.string.menu_quota)) }, onClick = { showMenu = false; showUsage = true })
                        DropdownMenuItem(text = { Text(stringResource(R.string.menu_reload_week)) }, enabled = !state.busy, onClick = { showMenu = false; confirmFull = true })
                    }
                }
            }
            PullToRefreshBox(isRefreshing = state.busy, onRefresh = vm::refresh, modifier = Modifier.weight(1f)) {
                HorizontalPager(pager, Modifier.fillMaxSize().testTag("city-pager"), key = { state.places.getOrNull(it)?.cacheKey ?: it }) { page ->
                    state.places.getOrNull(page)?.let { place ->
                        ForecastPage(state, place, page == state.page, now, vm, onFullRefresh = { confirmFull = true })
                    }
                }
            }
        }
      }
    }
    if (showSearch) CityDialog(state, vm::search, onSelect = { vm.load(it); showSearch = false }, onDismiss = { showSearch = false })
    if (confirmRemove) AlertDialog(
        onDismissRequest = { confirmRemove = false },
        title = { Text(stringResource(R.string.remove_title)) },
        text = { Text(stringResource(R.string.remove_text, state.saved.current.name.substringBefore(","))) },
        confirmButton = { TextButton(onClick = { confirmRemove = false; vm.removeCurrent() }) { Text(stringResource(R.string.action_remove)) } },
        dismissButton = { TextButton(onClick = { confirmRemove = false }) { Text(stringResource(R.string.action_cancel)) } },
    )
    if (confirmFull) AlertDialog(
        onDismissRequest = { confirmFull = false },
        title = { Text(stringResource(R.string.menu_reload_week)) },
        text = { Text(stringResource(R.string.reload_week_text)) },
        confirmButton = { TextButton(onClick = { confirmFull = false; vm.reloadAll() }) { Text(stringResource(R.string.action_update_all)) } },
        dismissButton = { TextButton(onClick = { confirmFull = false }) { Text(stringResource(R.string.action_cancel)) } },
    )
}

@Composable
private fun ForecastPage(
    state: WeatherUiState, place: Place, selected: Boolean, now: Instant, vm: WeatherViewModel, onFullRefresh: () -> Unit,
) {
    val snapshot = state.snapshotFor(place)
    LazyColumn(
        modifier = Modifier.fillMaxSize().testTag("forecast-list"),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        state.status?.takeIf { selected }?.let { status -> item { Text(status.resolve(), color = Accent, fontSize = 13.sp) } }
        state.message?.takeIf { selected }?.let { message ->
            item {
                val context = LocalContext.current
                Card(colors = CardDefaults.cardColors(containerColor = Color(0xFF3A2933))) {
                    Text(message.resolve(), Modifier.padding(16.dp), fontSize = 13.sp)
                    if (message.locationHelp) Row(Modifier.padding(horizontal = 8.dp)) {
                        TextButton(onClick = { context.startActivity(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)) }) { Text(stringResource(R.string.location_settings), fontSize = 12.sp) }
                        TextButton(onClick = { context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}"))) }) { Text(stringResource(R.string.app_permission), fontSize = 12.sp) }
                    }
                }
            }
        }
        if (snapshot == null) {
            val loading = (selected && state.busy) || place.cacheKey in state.opening
            item {
                Column(Modifier.fillMaxWidth().padding(vertical = 64.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    WeatherGlyph(2, modifier = Modifier.size(110.dp))
                    Text(stringResource(if (loading) R.string.loading_forecast else R.string.forecast_unavailable), color = Muted)
                    if (!loading && selected) Button(onClick = vm::refresh) { Text(stringResource(R.string.action_retry)) }
                }
            }
        } else {
            val zone = ZoneId.of(snapshot.timezone)
            val hour = now.atZone(zone).truncatedTo(ChronoUnit.HOURS).toEpochSecond()
            val current = snapshot.hours.find { it.time == hour }
            item { CurrentCard(current, now, zone) }
            item {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.hourly_title), fontSize = 15.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                    Text(LocalContext.current.resources.getQuantityString(R.plurals.hourly_count, snapshot.hours.size, snapshot.hours.size), color = Muted, fontSize = 10.sp, letterSpacing = 2.sp)
                    TextButton(onClick = vm::refresh, enabled = !state.busy) { Text(stringResource(R.string.action_refresh), fontSize = 12.sp) }
                }
            }
            item {
                key(snapshot.place.cacheKey) { HourlyForecastChart(snapshot.hours, hour, zone) }
            }
            val today = now.atZone(zone).toLocalDate()
            val days = snapshot.days.filter { !LocalDate.parse(it.date).isBefore(today) }
            item { ForecastDaysCard(days, today, !state.busy, onFullRefresh) }
            item { Text(stringResource(R.string.details_header), color = Muted, fontSize = 10.sp, letterSpacing = 1.5.sp, modifier = Modifier.padding(top = 10.dp)) }
            item(key = "weather-details") { WeatherDetails(current, snapshot.days.find { it.date == today.toString() }, now, zone) }
            item {
                Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    val fullTime = Instant.ofEpochSecond(snapshot.fullFetchedAt)
                    if (days.size < 7 || fullTime.plus(24, ChronoUnit.HOURS).isBefore(now)) {
                        Text(stringResource(R.string.daily_stale), color = Color(0xFFFFD68B), fontSize = 12.sp)
                    }
                    Text(stringResource(R.string.full_fetched, formatTime(snapshot.fullFetchedAt, zone)), color = Muted, fontSize = 11.sp)
                    snapshot.sixHoursFetchedAt?.let { Text(stringResource(R.string.six_fetched, formatTime(it, zone)), color = Muted, fontSize = 11.sp) }
                    Text(stringResource(R.string.refresh_note), color = Muted, fontSize = 11.sp)
                }
            }
        }
        item {
            val uri = LocalUriHandler.current
            Column {
                TextButton(onClick = { uri.openUri("https://open-meteo.com/") }) { Text(stringResource(R.string.attribution), color = Muted, fontSize = 11.sp) }
                Text("WeatherCard ${BuildConfig.VERSION_NAME}", color = Muted, fontSize = 10.sp, modifier = Modifier.padding(start = 12.dp))
            }
        }
    }
}

/** One dot per saved city; the current-location page shows the location arrow instead of a dot. */
@Composable
private fun PageDots(places: List<Place>, current: Int, modifier: Modifier = Modifier) {
    val description = stringResource(R.string.city_pages, current + 1, places.size)
    Row(modifier.semantics(mergeDescendants = true) { contentDescription = description },
        horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
        places.forEachIndexed { index, place ->
            val alpha = if (index == current) 1f else .4f
            if (place.isCurrentLocation) Box(Modifier.size(9.dp).alpha(alpha).clearAndSetSemantics {}) { LocationMark(Modifier.fillMaxSize()) }
            else Box(Modifier.size(7.dp).background(Color.White.copy(alpha = alpha), CircleShape))
        }
    }
}

@Composable
private fun CurrentCard(hour: HourWeather?, now: Instant, zone: ZoneId) {
    val condition = WeatherCondition.from(hour?.weatherCode, hour?.isDay ?: true)
    Column(Modifier.fillMaxWidth().heightIn(min = 240.dp).padding(top = 28.dp, bottom = 30.dp),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Text(degrees(hour?.temperature), fontSize = 104.sp, lineHeight = 108.sp, letterSpacing = (-4).sp, fontWeight = FontWeight.Bold)
        Text(stringResource(condition.kind.labelRes), color = Accent, fontSize = 18.sp, lineHeight = 24.sp, fontWeight = FontWeight.SemiBold)
        Text(now.atZone(zone).format(DateTimeFormatter.ofPattern(stringResource(R.string.pattern_current_date), Locale.getDefault())), color = Muted, fontSize = 11.sp,
            lineHeight = 16.sp, modifier = Modifier.padding(top = 12.dp))
    }
}

@Composable
private fun ForecastDaysCard(days: List<DayWeather>, today: LocalDate, enabled: Boolean, onFullRefresh: () -> Unit) {
    var expanded by remember(days) { mutableStateOf(false) }
    Column(Modifier.fillMaxWidth().background(Color.White.copy(alpha = .07f), RoundedCornerShape(28.dp))
        .border(1.dp, Color.White.copy(alpha = .07f), RoundedCornerShape(28.dp)).padding(16.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.daily_title), color = Muted, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
            TextButton(onClick = onFullRefresh, enabled = enabled, contentPadding = PaddingValues(horizontal = 4.dp)) { Text(stringResource(R.string.action_update_all), fontSize = 10.sp) }
        }
        days.take(if (expanded) 7 else 3).forEach { DayRow(it, today) }
        if (days.isEmpty()) Text(stringResource(R.string.daily_expired), fontSize = 12.sp, color = Muted)
        if (days.size > 3) FilledTonalButton(onClick = { expanded = !expanded }, modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
            colors = ButtonDefaults.filledTonalButtonColors(containerColor = Color.White.copy(alpha = .15f), contentColor = Color.White)) {
            Text(stringResource(if (expanded) R.string.action_collapse else R.string.daily_title), fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun DayRow(day: DayWeather, today: LocalDate) {
    Row(Modifier.fillMaxWidth().padding(vertical = 7.dp), verticalAlignment = Alignment.CenterVertically) {
        val date = LocalDate.parse(day.date)
        WeatherGlyph(day.weatherCode, modifier = Modifier.size(34.dp))
        Spacer(Modifier.width(8.dp))
        Text(if (date == today) stringResource(R.string.today) else if (date == today.plusDays(1)) stringResource(R.string.tomorrow) else date.format(DateTimeFormatter.ofPattern("EEE", Locale.getDefault())), fontSize = 12.sp,
            // Wide enough for "Tomorrow" at large font sizes too; never wraps onto a second line.
            fontWeight = FontWeight.SemiBold, maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis,
            modifier = Modifier.widthIn(min = 72.dp).padding(end = 6.dp))
        Text(stringResource(WeatherCondition.from(day.weatherCode).kind.labelRes), color = Muted, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
        Spacer(Modifier.width(6.dp))
        Text(degrees(day.maximum), fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
        Text(" / ${degrees(day.minimum)}", color = Muted, fontSize = 13.sp)
    }
}

@Composable
private fun CityDialog(state: WeatherUiState, onSearch: (String) -> Unit, onSelect: (Place) -> Unit, onDismiss: () -> Unit) {
    var query by remember { mutableStateOf("") }
    AlertDialog(onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.search_title)) },
        text = {
            Column {
                OutlinedTextField(query, onValueChange = { query = it }, label = { Text(stringResource(R.string.search_label)) }, singleLine = true)
                TextButton(onClick = { onSearch(query) }, enabled = !state.searching && query.trim().length >= 2) { Text(stringResource(if (state.searching) R.string.searching else R.string.action_search)) }
                state.searchMessage?.let { Text(it.resolve(), color = Muted, fontSize = 12.sp) }
                LazyColumn(Modifier.heightIn(max = 300.dp)) {
                    items(state.searchResults) { place -> TextButton(onClick = { onSelect(place) }) { Text(place.name) } }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_close)) } },
    )
}

private fun degrees(value: Double?) = value?.roundToInt()?.let { "$it°" } ?: "—°"
private fun formatTime(value: Long, zone: ZoneId) = Instant.ofEpochSecond(value).atZone(zone).format(DateTimeFormatter.ofPattern("d MMM HH:mm", Locale.getDefault()))
