package com.weathercard.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SkyLightTest {
    private val sunrise = 1_000_000L
    private val sunset = sunrise + 12 * 3600

    @Test fun noonIsBrightestAndNotGolden() {
        val light = SkyLight.at(sunrise + 6 * 3600, sunrise, sunset, isDayFallback = true)
        assertEquals(1f, light.daylight, .001f)
        assertEquals(0f, light.golden)
        assertEquals(.5f, light.sunProgress!!, .001f)
    }

    @Test fun midnightIsDark() {
        val light = SkyLight.at(sunset + 6 * 3600, sunrise, sunset, isDayFallback = false)
        assertEquals(0f, light.daylight)
        assertEquals(0f, light.golden)
        assertNull(light.sunProgress)
    }

    @Test fun sunriseAndSunsetAreGoldenAndDim() {
        listOf(sunrise, sunset).forEach { time ->
            val light = SkyLight.at(time, sunrise, sunset, isDayFallback = true)
            assertEquals(1f, light.golden)
            assertTrue(light.daylight in 0.2f..0.4f)
        }
    }

    @Test fun twilightFadesBeforeSunrise() {
        val early = SkyLight.at(sunrise - 40 * 60, sunrise, sunset, isDayFallback = false)
        val late = SkyLight.at(sunrise - 10 * 60, sunrise, sunset, isDayFallback = false)
        assertTrue(early.daylight < late.daylight)
        assertTrue(early.golden in 0f..late.golden)
        assertNull(late.sunProgress)
    }

    @Test fun missingSunTimesFallBackToIsDay() {
        assertTrue(SkyLight.at(0, null, null, isDayFallback = true).daylight > .5f)
        assertEquals(0f, SkyLight.at(0, null, null, isDayFallback = false).daylight)
    }
}
