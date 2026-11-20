package com.weathercard.core

import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import kotlin.test.*

class LocationResolverTest {
    private val fix = LocationFix(41.01, 28.98, 2000, 25f)

    @Test fun `null fused result does not prevent native GPS fallback`() = runTest {
        val result = LocationResolver(listOf({ null }, { delay(1000); fix })).find()
        assertEquals(fix, result)
    }
    @Test fun `provider exception does not cancel the other provider`() = runTest {
        assertEquals(fix, LocationResolver(listOf({ throw IllegalStateException("unavailable") }, { fix })).find())
    }
    @Test fun `remaining providers are cancelled after first usable fix`() = runTest {
        var cleaned = false
        val result = LocationResolver(listOf({ try { awaitCancellation() } finally { cleaned = true } }, { delay(10); fix })).find()
        assertEquals(fix, result)
        assertTrue(cleaned)
    }
    @Test fun `stale inaccurate and invalid fixes are rejected`() = runTest {
        assertNull(LocationResolver(listOf(
            { fix.copy(ageMillis = 900_001) }, { fix.copy(latitude = Double.NaN) },
            { fix.copy(accuracyMeters = 20_000f) }, { fix.copy(longitude = 181.0) },
        )).find())
    }
    @Test fun `bounded wait cancels providers instead of leaving background location running`() = runTest {
        var cleaned = false
        val result = LocationResolver(listOf({ try { awaitCancellation() } finally { cleaned = true } }), timeoutMillis = 500).find()
        assertNull(result)
        assertTrue(cleaned)
    }
}

