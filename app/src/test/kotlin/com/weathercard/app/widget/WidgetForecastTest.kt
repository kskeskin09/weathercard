package com.weathercard.app.widget

import com.weathercard.core.DayWeather
import com.weathercard.core.HourWeather
import com.weathercard.core.Place
import com.weathercard.core.WeatherSnapshot
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class WidgetForecastTest {
    private fun hour(time: Instant, temperature: Double, code: Int, isDay: Boolean?) = HourWeather(
        time.epochSecond, temperature, null, null, null, null, code, null, null, null, null, null, null, null, isDay,
    )

    private val start = Instant.parse("2026-11-21T09:00:00Z") // 12:00 in Istanbul
    private val snapshot = WeatherSnapshot(
        place = Place("Kadıköy", 40.99, 29.03, isCurrentLocation = true),
        timezone = "Europe/Istanbul",
        hours = listOf(hour(start, 14.4, 3, true), hour(start.plusSeconds(3600), 15.6, 61, true), hour(start.plusSeconds(7200 * 6), 9.0, 0, null)),
        days = listOf(DayWeather("2026-11-21", 61, 8.2, 16.7, null, null, null,
            Instant.parse("2026-11-21T04:40:00Z").epochSecond, Instant.parse("2026-11-21T14:45:00Z").epochSecond, null)),
        fullFetchedAt = start.epochSecond,
    )

    @Test fun picksHourContainingNow() {
        val forecast = WidgetForecast.from(snapshot, start.plusSeconds(3600 + 25 * 60))!!
        assertEquals(15.6, forecast.temperature)
        assertEquals(61, forecast.weatherCode)
        assertEquals("Hafif yağmur", forecast.description)
        assertEquals(16.7, forecast.maximum)
        assertEquals(8.2, forecast.minimum)
        assertTrue(forecast.isCurrentLocation)
    }

    @Test fun fallsBackToLocalHourWhenIsDayMissing() {
        val forecast = WidgetForecast.from(snapshot, start.plusSeconds(7200 * 6 + 60))!!
        assertFalse(forecast.isDay)
        assertEquals("Açık gece", forecast.description)
    }

    @Test fun returnsNullWhenCacheDoesNotCoverThisHour() {
        assertNull(WidgetForecast.from(snapshot, start.minusSeconds(3600)))
    }

    @Test fun formatsDegreesLikeTheApp() {
        assertEquals("15°", widgetDegrees(14.6))
        assertEquals("-3°", widgetDegrees(-2.6))
        assertEquals("—°", widgetDegrees(null))
    }
}
