package com.weathercard.app

import android.graphics.Bitmap
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.performClick
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
import androidx.compose.ui.test.swipeRight
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.ComposeTimeoutException
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.foundation.Image
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.weathercard.app.ui.WeatherGlyph
import com.weathercard.app.ui.labelRes
import com.weathercard.core.*
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.time.Instant
import java.time.ZoneId
import java.time.temporal.ChronoUnit

@RunWith(AndroidJUnit4::class)
class WeatherUiSmokeTest {
    @get:Rule val compose = createEmptyComposeRule()

    @Test fun cachedDashboardAndAllConditionIconsRender() {
        val app = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as WeatherApplication
        val place = Place("İstanbul", 41.0082, 28.9784)
        val now = Instant.now()
        val first = now.truncatedTo(ChronoUnit.HOURS).epochSecond
        val codes = listOf(0, 1, 2, 3, 45, 48, 51, 53, 55, 56, 57, 61, 63, 65, 66, 67, 71, 73, 75, 77, 80, 81, 82, 85, 86, 95, 96, 99)
        val hours = (0..167).map { i -> HourWeather(first + i * 3600, 18.0 + i % 5, 17.0, 64.0,
            20.0, 0.2, codes[i % codes.size], 30.0, 1015.0, 12000.0, 12.0, 180.0, 20.0, 3.0, true) }
        val today = now.atZone(ZoneId.of("Europe/Istanbul")).toLocalDate()
        val days = (0..6).map { i -> DayWeather(today.plusDays(i.toLong()).toString(), codes[i], 12.0 + i, 22.0 + i, 20.0, 0.2, 14.0, null, null, 3.0) }
        // Seed real disk persistence. The app should open without a weather HTTP call.
        runBlocking {
            com.weathercard.app.data.DiskForecastStore(File(app.filesDir, "forecasts"))
                .write(WeatherSnapshot(place, "Europe/Istanbul", hours, days, now.epochSecond))
            app.usage.record(RequestKind.FULL_FORECAST)
            repeat(3) { app.usage.record(RequestKind.SIX_HOURS) }
            app.usage.record(RequestKind.GEOCODING)
        }
        app.getSharedPreferences("settings", 0).edit().clear().commit()
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            // The header shows the saved city name at once; wait for the cached forecast itself.
            compose.waitUntil(15_000) { compose.onAllNodesWithText("18°").fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithText("İstanbul").assertIsDisplayed()
            compose.onAllNodesWithText("18°").onFirst().assertIsDisplayed()
            compose.onNodeWithContentDescription(app.getString(R.string.action_more)).performClick()
            compose.onNodeWithText(app.getString(R.string.menu_quota)).performClick()
            compose.waitUntil(10_000) { compose.onAllNodesWithText(app.getString(R.string.usage_month)).fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithText(app.getString(R.string.usage_title)).assertIsDisplayed()
            screenshot(app, "usage.png")
            compose.onNodeWithText(app.getString(R.string.action_back)).performClick()
            scenario.onActivity { activity ->
                activity.setContent {
                    WeatherCardTheme {
                        Column(Modifier.fillMaxSize().background(Color(0xFF0B1324)).padding(horizontal = 12.dp, vertical = 32.dp)) {
                            Text("WEATHERCARD · WEATHER ICONS", color = Color(0xFFB3D4FF), fontSize = 13.sp)
                            Image(painterResource(R.drawable.ic_launcher_foreground), contentDescription = "WeatherCard app logo", modifier = Modifier.size(64.dp))
                            Text("28 WMO codes · day / night", color = Color(0xFF9FAFC8), fontSize = 11.sp, modifier = Modifier.padding(bottom = 10.dp))
                            codes.chunked(4).forEach { row ->
                                Row(Modifier.fillMaxWidth().weight(1f), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                                    row.forEach { code ->
                                        Column(Modifier.weight(1f).fillMaxHeight().background(Color(0xFF142239), RoundedCornerShape(12.dp)).padding(4.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                                            Row {
                                                WeatherGlyph(code, true, Modifier.size(38.dp))
                                                WeatherGlyph(code, false, Modifier.size(38.dp))
                                            }
                                            Text("$code · ${app.getString(WeatherCondition.from(code).kind.labelRes)}", color = Color(0xFFE5ECF8), fontSize = 9.sp, lineHeight = 11.sp)
                                        }
                                    }
                                }
                                Spacer(Modifier.height(5.dp))
                            }
                        }
                    }
                }
            }
            compose.waitForIdle()
            compose.onNodeWithContentDescription(app.getString(R.string.condition_clear_night)).assertIsDisplayed()
            screenshot(app, "condition-icons.png")
        }
    }

    private fun screenshot(app: WeatherApplication, name: String) {
        val target = File(app.getExternalFilesDir("screenshots"), name)
        var lastError: ComposeTimeoutException? = null
        repeat(3) {
            try {
                compose.waitForIdle()
                compose.mainClock.advanceTimeByFrame()
                val bitmap = compose.onRoot(useUnmergedTree = true).captureToImage().asAndroidBitmap()
                target.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
                bitmap.recycle()
                return
            } catch (error: ComposeTimeoutException) {
                lastError = error
                // PixelCopy's first frame can time out on a cold software-rendered CI device.
                android.os.SystemClock.sleep(500)
            }
        }
        throw checkNotNull(lastError)
    }

    @Test fun savedCitiesSwipeFromCacheWithoutNetwork() {
        val app = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as WeatherApplication
        val zone = ZoneId.of("Europe/Istanbul")
        val now = Instant.now()
        val first = now.truncatedTo(ChronoUnit.HOURS).epochSecond
        val today = now.atZone(zone).toLocalDate()
        val istanbul = Place("İstanbul", 41.0082, 28.9784)
        val ankara = Place("Ankara", 39.9334, 32.8597)
        val izmir = Place("İzmir", 38.4237, 27.1428)
        // Each city gets a distinct temperature so the swipe is visible in the screenshots.
        listOf(istanbul to 18.0, ankara to 7.0, izmir to 24.0).forEach { (place, temperature) ->
            val hours = (0..167).map { i -> HourWeather(first + i * 3600, temperature, temperature - 1, 60.0,
                10.0, 0.0, if (place == ankara) 3 else 1, 30.0, 1015.0, 12000.0, 10.0, 200.0, 18.0, 2.0, true) }
            val days = (0..6).map { i -> DayWeather(today.plusDays(i.toLong()).toString(), 1, temperature - 5, temperature + 3, 10.0, 0.0, 12.0, null, null, 2.0) }
            runBlocking {
                com.weathercard.app.data.DiskForecastStore(File(app.filesDir, "forecasts"))
                    .write(WeatherSnapshot(place, "Europe/Istanbul", hours, days, now.epochSecond))
            }
        }
        val saved = kotlinx.serialization.json.Json.encodeToString(SavedPlaces.serializer(), SavedPlaces(listOf(istanbul, ankara, izmir)))
        app.getSharedPreferences("settings", 0).edit().clear().putString("places_v1", saved).commit()
        ActivityScenario.launch(MainActivity::class.java).use {
            compose.waitUntil(15_000) { compose.onAllNodesWithText("18°").fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithContentDescription(app.getString(R.string.city_pages, 1, 3)).assertIsDisplayed()
            screenshot(app, "cities-first.png")
            compose.onNodeWithTag("city-pager").performTouchInput { swipeLeft() }
            compose.waitUntil(10_000) { compose.onAllNodesWithText("Ankara").fetchSemanticsNodes().isNotEmpty() }
            compose.waitUntil(10_000) { compose.onAllNodesWithText("7°").fetchSemanticsNodes().isNotEmpty() }
            compose.onAllNodesWithText("7°").onFirst().assertIsDisplayed()
            compose.onNodeWithContentDescription(app.getString(R.string.city_pages, 2, 3)).assertIsDisplayed()
            screenshot(app, "cities-second.png")
            // Kerem's report: a swipe that starts on the hourly chart used to stop between two cities.
            // The chart is already at its first hour, so this rightward drag passes through to the pager.
            compose.onNodeWithTag("forecast-list").performScrollToNode(hasTestTag("hourly-chart"))
            compose.onNodeWithTag("hourly-chart").performTouchInput {
                down(Offset(width * .1f, height / 2f))
                repeat(12) { moveBy(Offset(width * .05f, 0f), delayMillis = 16) }
                up()
            }
            compose.waitUntil(10_000) { compose.onAllNodesWithText("İstanbul").fetchSemanticsNodes().isNotEmpty() }
            // Only one page is laid out once the pager has settled on a whole page.
            compose.waitUntil(5_000) { compose.onAllNodesWithTag("forecast-list").fetchSemanticsNodes().size == 1 }
            compose.waitUntil(10_000) { compose.onAllNodesWithText("18°").fetchSemanticsNodes().isNotEmpty() }
            screenshot(app, "cities-chart-swipe.png")
        }
    }

    @Test fun renderAtmospherePreviews() {
        val app = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as WeatherApplication
        val zone = ZoneId.of("Europe/Istanbul")
        val date = java.time.LocalDate.now(zone)
        val cases = listOf(Triple("day", 0, 12 * 60), Triple("night", 0, 23 * 60), Triple("rain", 63, 14 * 60), Triple("snow", 73, 10 * 60),
            Triple("sunset", 2, 17 * 60 + 35))
        cases.forEach { (name, code, displayMinute) ->
            val reference = date.atStartOfDay(zone).plusMinutes(displayMinute.toLong()).toInstant()
            val start = date.atStartOfDay(zone).toEpochSecond()
            val base = when (name) { "snow" -> 2.0; "night" -> 12.0; "rain" -> 16.0; "sunset" -> 15.0; else -> 21.0 }
            val hours = (0..167).map { i ->
                val time = start + i * 3600
                val localHour = Instant.ofEpochSecond(time).atZone(zone).hour
                HourWeather(time, base + kotlin.math.sin(i / 4.0) * 3, base - 1, 68.0,
                    if (code >= 60) 80.0 else 10.0, if (code >= 60) 0.8 else 0.0, code, 40.0,
                    1015.0, 10000.0, 16.7, 270.0, 25.0, if (localHour in 8..17) 3.0 else 0.0, localHour in 8..17)
            }
            val days = (0..6).map { i ->
                val day = date.plusDays(i.toLong())
                DayWeather(day.toString(), code, base - 4, base + 3, 20.0, 0.2, 16.7,
                    day.atTime(7, 56).atZone(zone).toEpochSecond(), day.atTime(17, 41).atZone(zone).toEpochSecond(), 3.0)
            }
            runBlocking {
                com.weathercard.app.data.DiskForecastStore(File(app.filesDir, "forecasts")).write(
                    WeatherSnapshot(Place("İstanbul", 41.0082, 28.9784, isCurrentLocation = true), "Europe/Istanbul", hours, days, reference.epochSecond)
                )
            }
            app.getSharedPreferences("settings", 0).edit().clear().putBoolean("is_current", true).commit()
            ActivityScenario.launch(MainActivity::class.java).use { scenario ->
                scenario.onActivity { activity -> activity.setContent { WeatherCardTheme { WeatherScreen(previewNow = reference) } } }
                compose.waitUntil(15_000) { compose.onAllNodesWithText("İstanbul").fetchSemanticsNodes().isNotEmpty() }
                compose.onAllNodesWithText("İstanbul").onFirst().assertIsDisplayed()
                compose.onNodeWithContentDescription(app.getString(R.string.current_location)).assertIsDisplayed()
                screenshot(app, "$name-preview.png")
                if (name == "day") {
                    compose.onNodeWithTag("forecast-list").performScrollToIndex(1)
                    screenshot(app, "day-hourly.png")
                    compose.onNodeWithTag("forecast-list").performScrollToIndex(5)
                    screenshot(app, "day-wind-sun.png")
                }
            }
        }
    }
    /** The UI follows the system language; render the Turkish resources explicitly so both languages get screenshots. */
    @Test fun turkishLanguageRendersTurkishText() {
        val app = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as WeatherApplication
        val zone = ZoneId.of("Europe/Istanbul")
        val now = Instant.now()
        val first = now.truncatedTo(ChronoUnit.HOURS).epochSecond
        val today = now.atZone(zone).toLocalDate()
        val place = Place("İstanbul", 41.0082, 28.9784)
        val hours = (0..167).map { i -> HourWeather(first + i * 3600, 18.0, 17.0, 60.0,
            10.0, 0.0, 61, 30.0, 1015.0, 12000.0, 10.0, 200.0, 18.0, 2.0, true) }
        val days = (0..6).map { i -> DayWeather(today.plusDays(i.toLong()).toString(), 61, 13.0, 21.0, 10.0, 0.0, 12.0, null, null, 2.0) }
        runBlocking {
            com.weathercard.app.data.DiskForecastStore(File(app.filesDir, "forecasts")).write(WeatherSnapshot(place, "Europe/Istanbul", hours, days, now.epochSecond))
        }
        app.getSharedPreferences("settings", 0).edit().clear().commit()
        val turkish = java.util.Locale.forLanguageTag("tr-TR")
        val previous = java.util.Locale.getDefault()
        java.util.Locale.setDefault(turkish)
        try {
            ActivityScenario.launch(MainActivity::class.java).use { scenario ->
                scenario.onActivity { activity ->
                    val configuration = android.content.res.Configuration(activity.resources.configuration).apply { setLocale(turkish) }
                    val context = activity.createConfigurationContext(configuration)
                    activity.setContent {
                        androidx.compose.runtime.CompositionLocalProvider(
                            androidx.compose.ui.platform.LocalContext provides context,
                            androidx.compose.ui.platform.LocalConfiguration provides configuration,
                            // These owners are normally found by walking LocalContext up to the activity.
                            androidx.activity.compose.LocalActivityResultRegistryOwner provides activity,
                            androidx.activity.compose.LocalOnBackPressedDispatcherOwner provides activity,
                        ) { WeatherCardTheme { WeatherScreen() } }
                    }
                }
                compose.waitUntil(15_000) { compose.onAllNodesWithText("Saatlik tahmin").fetchSemanticsNodes().isNotEmpty() }
                compose.onAllNodesWithText("Hafif yağmur").onFirst().assertIsDisplayed()
                screenshot(app, "turkish-preview.png")
            }
        } finally { java.util.Locale.setDefault(previous) }
    }
}
