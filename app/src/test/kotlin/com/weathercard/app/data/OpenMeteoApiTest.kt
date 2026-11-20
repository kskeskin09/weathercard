package com.weathercard.app.data

import com.weathercard.core.HourWindow
import com.weathercard.core.Place
import java.time.Instant
import kotlin.test.*
import org.json.JSONObject
import org.junit.Test

class OpenMeteoApiTest {
    private val place = Place("İstanbul", 41.0082, 28.9784)

    @Test fun `refresh query requests exactly six hours across midnight without daily or full forecast`() {
        val window = HourWindow.at(Instant.parse("2026-11-20T22:35:00Z"))
        val url = OpenMeteoApi.sixHourUrl(place, window)
        assertEquals("2026-11-20T22:00", url.queryParameter("start_hour"))
        assertEquals("2026-11-21T03:00", url.queryParameter("end_hour"))
        assertEquals("GMT", url.queryParameter("timezone"))
        assertEquals("unixtime", url.queryParameter("timeformat"))
        assertNull(url.queryParameter("daily"))
        assertNull(url.queryParameter("current"))
        assertNull(url.queryParameter("forecast_days"))
        assertEquals(14, url.queryParameter("hourly")!!.split(',').size)
    }

    @Test fun `full query provides seven daily and hourly forecast days`() {
        val url = OpenMeteoApi.fullUrl(place)
        assertEquals("7", url.queryParameter("forecast_days"))
        assertEquals("auto", url.queryParameter("timezone"))
        assertNotNull(url.queryParameter("daily"))
    }

    @Test fun `partial query uses the same local hour grid as full response`() {
        val window = HourWindow.at(Instant.parse("2026-11-20T22:35:00Z"), "Asia/Kolkata")
        val url = OpenMeteoApi.sixHourUrl(place, window)
        assertEquals("Asia/Kolkata", url.queryParameter("timezone"))
        assertEquals("2026-11-21T04:00", url.queryParameter("start_hour"))
        assertEquals("2026-11-21T09:00", url.queryParameter("end_hour"))
    }

    @Test fun `null measurements stay unknown instead of silently becoming zero`() {
        val fields = OpenMeteoApi.hourlyFields.joinToString(",") { "\"$it\":[null]" }
        val response = JSONObject("{\"hourly\":{\"time\":[1795212000],$fields}}")
        val parsed = OpenMeteoApi.parseHours(response).single()
        assertNull(parsed.temperature)
        assertNull(parsed.weatherCode)
        assertNull(parsed.isDay)
    }
}

