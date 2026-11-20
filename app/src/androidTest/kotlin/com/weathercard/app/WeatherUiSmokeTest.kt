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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.weathercard.app.ui.WeatherGlyph
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
        }
        app.getSharedPreferences("settings", 0).edit().clear().commit()
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            compose.waitUntil(15_000) { compose.onAllNodes(androidx.compose.ui.test.hasText("İstanbul")).fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithText("İstanbul").assertIsDisplayed()
            compose.onAllNodesWithText("18°").onFirst().assertIsDisplayed()
            screenshot(app, "dashboard.png")
            scenario.onActivity { activity ->
                activity.setContent {
                    MaterialTheme(colorScheme = darkColorScheme()) {
                        Column(Modifier.fillMaxSize().background(Color(0xFF0B1324)).padding(horizontal = 12.dp, vertical = 32.dp)) {
                            Text("WEATHERCARD · HAVA DURUMU İKONLARI", color = Color(0xFFB3D4FF), fontSize = 13.sp)
                            Text("28 WMO kodu · gündüz / gece", color = Color(0xFF9FAFC8), fontSize = 11.sp, modifier = Modifier.padding(bottom = 10.dp))
                            codes.chunked(4).forEach { row ->
                                Row(Modifier.fillMaxWidth().weight(1f), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                                    row.forEach { code ->
                                        Column(Modifier.weight(1f).fillMaxHeight().background(Color(0xFF142239), RoundedCornerShape(12.dp)).padding(4.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                                            Row {
                                                WeatherGlyph(code, true, Modifier.size(38.dp))
                                                WeatherGlyph(code, false, Modifier.size(38.dp))
                                            }
                                            Text("$code · ${WeatherCondition.from(code).description}", color = Color(0xFFE5ECF8), fontSize = 9.sp, lineHeight = 11.sp)
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
            compose.onNodeWithContentDescription("Açık gece").assertIsDisplayed()
            screenshot(app, "condition-icons.png")
        }
    }

    private fun screenshot(app: WeatherApplication, name: String) {
        val target = File(app.getExternalFilesDir("screenshots"), name)
        val bitmap = InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot()
        target.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
    }
}
