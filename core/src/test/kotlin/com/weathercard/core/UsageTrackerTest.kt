package com.weathercard.core

import kotlinx.coroutines.test.runTest
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import kotlin.test.*

class UsageTrackerTest {
    @Test fun `weighted weather calls and geocoding are counted separately and survive restart`() = runTest {
        val store = MemoryStore()
        val clock = MutableClock(Instant.parse("2026-11-20T14:30:00Z"))
        val tracker = UsageTracker(store, clock)
        tracker.record(RequestKind.FULL_FORECAST)
        tracker.record(RequestKind.SIX_HOURS)
        tracker.record(RequestKind.GEOCODING)
        val summary = UsageTracker(store, clock).summary()
        assertEquals(37L, summary.month.quotaTenths)
        assertEquals(2L, summary.month.weatherRequests)
        assertEquals(1L, summary.month.searches)
        assertEquals(summary.hour, summary.day)
        assertEquals(summary.month, summary.lifetime)
    }

    @Test fun `hour day and month roll over in UTC without resetting lifetime`() = runTest {
        val store = MemoryStore()
        val clock = MutableClock(Instant.parse("2026-11-30T23:59:00Z"))
        val tracker = UsageTracker(store, clock)
        tracker.record(RequestKind.FULL_FORECAST)
        clock.now = Instant.parse("2026-12-01T00:01:00Z")
        assertEquals(0L, tracker.summary().hour.quotaTenths)
        assertEquals(0L, tracker.summary().day.quotaTenths)
        assertEquals(0L, tracker.summary().month.quotaTenths)
        tracker.record(RequestKind.SIX_HOURS)
        assertEquals(14L, tracker.summary().month.quotaTenths)
        assertEquals(37L, tracker.summary().lifetime.quotaTenths)
        assertEquals(1, store.value!!.buckets.size)
    }

    @Test fun `read only cache views do not consume quota or write statistics`() = runTest {
        val store = MemoryStore()
        val tracker = UsageTracker(store)
        repeat(5) { assertEquals(0L, tracker.summary().lifetime.quotaTenths) }
        assertNull(store.value)
    }

    @Test fun `repeated refreshes aggregate into one hourly bucket with exact arithmetic`() = runTest {
        val store = MemoryStore()
        val tracker = UsageTracker(store, Clock.fixed(Instant.parse("2026-11-20T12:00:00Z"), ZoneOffset.UTC))
        repeat(100) { tracker.record(RequestKind.SIX_HOURS) }
        assertEquals(1400L, tracker.summary().month.quotaTenths)
        assertEquals(1, store.value!!.buckets.size)
    }

    private class MemoryStore(var value: UsageLedger? = null) : UsageStore {
        override suspend fun read() = value
        override suspend fun write(ledger: UsageLedger) { value = ledger }
    }
    private class MutableClock(var now: Instant) : Clock() {
        override fun getZone(): ZoneId = ZoneOffset.UTC
        override fun withZone(zone: ZoneId): Clock = this
        override fun instant() = now
    }
}

