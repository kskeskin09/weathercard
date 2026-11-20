package com.weathercard.core

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.Clock

interface WeatherApi {
    suspend fun fullForecast(place: Place): WeatherSnapshot
    suspend fun sixHours(place: Place, window: HourWindow): List<HourWeather>
}

interface ForecastStore {
    suspend fun read(place: Place): WeatherSnapshot?
    suspend fun write(snapshot: WeatherSnapshot)
}

class WeatherRepository(
    private val api: WeatherApi,
    private val store: ForecastStore,
    private val clock: Clock = Clock.systemUTC(),
) {
    private val mutex = Mutex()

    /** An existing disk snapshot never triggers a full network request implicitly. */
    suspend fun open(place: Place): WeatherSnapshot = mutex.withLock {
        store.read(place)?.copy(place = place) ?: fetchAndStore(place)
    }

    /** Only these six timestamps change; daily forecasts and full-fetch age stay intact. */
    suspend fun refreshSixHours(place: Place): WeatherSnapshot = mutex.withLock {
        val cached = store.read(place) ?: return@withLock fetchAndStore(place)
        val window = HourWindow.at(clock.instant())
        val updates = api.sixHours(place, window)
        require(updates.size == 6 && updates.map { it.time }.toSet() == window.timestamps) {
            "Altı saatlik yanıt eksik veya istenen zaman aralığının dışında."
        }
        val merged = (cached.hours.filterNot { it.time in window.timestamps } + updates).sortedBy { it.time }
        cached.copy(
            place = place,
            hours = merged,
            sixHoursFetchedAt = clock.instant().epochSecond,
        ).also { store.write(it) }
    }

    /** A separate, explicitly selected action for a new seven-day forecast. */
    suspend fun reloadAll(place: Place): WeatherSnapshot = mutex.withLock { fetchAndStore(place) }

    private suspend fun fetchAndStore(place: Place): WeatherSnapshot =
        api.fullForecast(place).copy(fullFetchedAt = clock.instant().epochSecond).also { snapshot ->
            require(snapshot.place.cacheKey == place.cacheKey)
            require(snapshot.days.size >= 7 && snapshot.hours.size >= 48) { "Tam tahmin yanıtı eksik." }
            require(snapshot.hours.map { it.time }.distinct().size == snapshot.hours.size)
            store.write(snapshot)
        }
}

