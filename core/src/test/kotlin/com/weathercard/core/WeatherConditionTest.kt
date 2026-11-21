package com.weathercard.core

import kotlin.test.*

class WeatherConditionTest {
    @Test fun `all documented WMO codes have distinct accessible descriptions`() {
        val codes = listOf(0, 1, 2, 3, 45, 48, 51, 53, 55, 56, 57, 61, 63, 65, 66, 67, 71, 73, 75, 77, 80, 81, 82, 85, 86, 95, 96, 99)
        val conditions = codes.map { WeatherCondition.from(it) }
        assertTrue(conditions.none { it.sky == Sky.UNKNOWN })
        assertEquals(codes.size, conditions.map { it.kind }.distinct().size)
    }
    @Test fun `clear night has its own description`() {
        assertEquals(ConditionKind.CLEAR_NIGHT, WeatherCondition.from(0, false).kind)
        assertEquals(ConditionKind.CLEAR_DAY, WeatherCondition.from(0, true).kind)
    }
    @Test fun `unknown code uses fallback and freezing hail remain distinguishable`() {
        assertEquals(Sky.UNKNOWN, WeatherCondition.from(null).sky)
        assertEquals(Sky.UNKNOWN, WeatherCondition.from(100).sky)
        assertEquals(ConditionKind.UNKNOWN, WeatherCondition.from(100).kind)
        assertTrue(WeatherCondition.from(67).freezing)
        assertFalse(WeatherCondition.from(65).freezing)
        assertEquals(Precipitation.HAIL, WeatherCondition.from(99).precipitation)
    }
}

