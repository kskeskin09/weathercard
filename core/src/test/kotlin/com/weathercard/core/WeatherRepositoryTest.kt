package com.weathercard.core

import kotlinx.coroutines.test.runTest
import java.io.IOException
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlin.test.*

class WeatherRepositoryTest {
    private val place = Place("İstanbul", 41.0082, 28.9784)
    // Six-hour refresh crosses both a UTC and Turkey-local midnight.
    private val now = Instant.parse("2026-11-20T22:35:00Z")
    private val clock = Clock.fixed(now, ZoneOffset.UTC)

    @Test fun `opening persisted forecast performs no network calls`() = runTest {
        val store = MemoryStore(snapshot())
        val api = FakeApi()
        assertEquals(snapshot(), WeatherRepository(api, store, clock).open(place))
        assertEquals(0, api.fullCalls)
        assertEquals(0, api.partialCalls)
    }

    @Test fun `first launch fetches once and persists for next repository instance`() = runTest {
        val store = MemoryStore()
        val api = FakeApi()
        WeatherRepository(api, store, clock).open(place)
        WeatherRepository(api, store, clock).open(place)
        assertEquals(1, api.fullCalls)
        assertEquals(1, store.writes)
    }

    @Test fun `refresh replaces exactly current hour plus five and preserves all other data`() = runTest {
        val original = snapshot()
        val store = MemoryStore(original)
        val api = FakeApi()
        val updated = WeatherRepository(api, store, clock).refreshSixHours(place)
        val window = HourWindow.at(now, "Europe/Istanbul")
        assertEquals(6, window.timestamps.size)
        assertEquals(Instant.parse("2026-11-21T03:00:00Z"), window.endInclusive)
        assertEquals(0, api.fullCalls)
        assertEquals(1, api.partialCalls)
        assertEquals(window, api.requestedWindow)
        assertEquals(original.days, updated.days)
        assertEquals(original.timezone, updated.timezone)
        assertEquals(original.fullFetchedAt, updated.fullFetchedAt)
        assertEquals(now.epochSecond, updated.sixHoursFetchedAt)
        assertEquals(original.hours.filterNot { it.time in window.timestamps }, updated.hours.filterNot { it.time in window.timestamps })
        assertTrue(updated.hours.filter { it.time in window.timestamps }.all { it.temperature == 99.0 })
        assertEquals(original.hours.size, updated.hours.size)
        assertEquals(updated, store.value)
    }

    @Test fun `network failure leaves persisted forecast untouched and does not fall back to full`() = runTest {
        val original = snapshot()
        val store = MemoryStore(original)
        val api = FakeApi().apply { fail = true }
        assertFailsWith<IOException> { WeatherRepository(api, store, clock).refreshSixHours(place) }
        assertEquals(original, store.value)
        assertEquals(0, store.writes)
        assertEquals(0, api.fullCalls)
    }

    @Test fun `incomplete partial payload cannot overwrite cache`() = runTest {
        val store = MemoryStore(snapshot())
        val api = FakeApi().apply { truncate = true }
        assertFailsWith<IllegalArgumentException> { WeatherRepository(api, store, clock).refreshSixHours(place) }
        assertEquals(0, store.writes)
        assertEquals(snapshot(), store.value)
    }

    @Test fun `out of window partial payload cannot overwrite cache`() = runTest {
        val store = MemoryStore(snapshot())
        val api = FakeApi().apply { shift = true }
        assertFailsWith<IllegalArgumentException> { WeatherRepository(api, store, clock).refreshSixHours(place) }
        assertEquals(0, store.writes)
    }

    @Test fun `different city fetches its own complete forecast`() = runTest {
        val store = MemoryStore(snapshot())
        val api = FakeApi()
        val other = Place("Ankara", 39.9334, 32.8597)
        assertEquals(other, WeatherRepository(api, store, clock).open(other).place)
        assertEquals(1, api.fullCalls)
    }

    @Test fun `stale cache does not silently download unchanged forecast`() = runTest {
        val store = MemoryStore(snapshot().copy(fullFetchedAt = 1))
        val api = FakeApi()
        WeatherRepository(api, store, clock).open(place)
        WeatherRepository(api, store, clock).refreshSixHours(place)
        assertEquals(0, api.fullCalls)
        assertEquals(1L, store.value!!.fullFetchedAt)
    }

    @Test fun `explicit complete reload updates both hourly and daily cache`() = runTest {
        val store = MemoryStore(snapshot().copy(fullFetchedAt = 1))
        val api = FakeApi()
        val updated = WeatherRepository(api, store, clock).reloadAll(place)
        assertEquals(1, api.fullCalls)
        assertEquals(0, api.partialCalls)
        assertEquals(now.epochSecond, updated.fullFetchedAt)
        assertEquals(updated, store.value)
    }

    @Test fun `persisted snapshot serializes losslessly including null measurements`() {
        val source = snapshot()
        val json = kotlinx.serialization.json.Json
        val encoded = json.encodeToString(WeatherSnapshot.serializer(), source)
        assertEquals(source, json.decodeFromString(WeatherSnapshot.serializer(), encoded))
    }

    @Test fun `half hour timezone aligns refresh to cached local hours`() = runTest {
        val localStart = HourWindow.at(now, "Asia/Kolkata").start.epochSecond
        val original = snapshot().copy(timezone = "Asia/Kolkata", hours = (0..167).map { hour(localStart + (it - 24) * 3600, 10.0) })
        val store = MemoryStore(original)
        val api = FakeApi()
        val updated = WeatherRepository(api, store, clock).refreshSixHours(place)
        assertEquals(1800L, api.requestedWindow!!.start.epochSecond % 3600)
        assertEquals(original.hours.size, updated.hours.size)
        assertEquals(6, updated.hours.count { it.temperature == 99.0 })
    }

    private fun snapshot(p: Place = place): WeatherSnapshot {
        val start = now.minusSeconds(24 * 3600).epochSecond / 3600 * 3600
        return WeatherSnapshot(p, "Europe/Istanbul", (0..167).map { hour(start + it * 3600, 10.0) },
            (20..26).map { DayWeather("2026-11-$it", 2, 8.0, 16.0, 20.0, 0.1, 12.0, null, null, 3.0) },
            now.minusSeconds(3600).epochSecond)
    }

    private fun hour(time: Long, temp: Double) = HourWeather(time, temp, 9.0, 70.0, 20.0, 0.1, 2, 40.0, 1015.0, 10000.0, 12.0, 180.0, 20.0, null, true)

    private inner class FakeApi : WeatherApi {
        var fullCalls = 0
        var partialCalls = 0
        var requestedWindow: HourWindow? = null
        var fail = false
        var truncate = false
        var shift = false
        override suspend fun fullForecast(place: Place): WeatherSnapshot { fullCalls++; return snapshot(place) }
        override suspend fun sixHours(place: Place, window: HourWindow): List<HourWeather> {
            partialCalls++
            requestedWindow = window
            if (fail) throw IOException("offline")
            val result = window.timestamps.sorted().map { hour(it + if (shift) 3600 else 0, 99.0) }
            return if (truncate) result.take(5) else result
        }
    }

    private class MemoryStore(var value: WeatherSnapshot? = null) : ForecastStore {
        var writes = 0
        override suspend fun read(place: Place) = value?.takeIf { it.place.cacheKey == place.cacheKey }
        override suspend fun write(snapshot: WeatherSnapshot) { writes++; value = snapshot }
    }
}

