package com.weathercard.app

import android.Manifest
import android.app.Application
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.weathercard.app.data.DeviceLocationProvider
import com.weathercard.app.data.SavedPlacesPreferences
import com.weathercard.app.widget.WeatherWidget
import com.weathercard.core.Place
import com.weathercard.core.SavedPlaces
import com.weathercard.core.WeatherSnapshot
import com.weathercard.core.UsageSummary
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import java.time.Duration
import java.time.Instant

data class WeatherUiState(
    val saved: SavedPlaces,
    /** Loaded forecasts by place cache key; swiping to a cached city never asks the network. */
    val snapshots: Map<String, WeatherSnapshot> = emptyMap(),
    /** Cache keys whose saved forecast is being opened. */
    val opening: Set<String> = emptySet(),
    val busy: Boolean = false,
    val message: UiText? = null,
    val searchResults: List<Place> = emptyList(),
    val searching: Boolean = false,
    val searchMessage: UiText? = null,
    val usage: UsageSummary? = null,
    val usageError: UiText? = null,
    val status: UiText? = null,
) {
    val places: List<Place> get() = saved.places
    val page: Int get() = saved.selected
    val snapshot: WeatherSnapshot? get() = snapshotFor(saved.current)
    fun snapshotFor(place: Place): WeatherSnapshot? = snapshots[place.cacheKey]?.copy(place = place)
}

class WeatherViewModel(application: Application) : AndroidViewModel(application) {
    private val app = application as WeatherApplication
    private val preferences = application.getSharedPreferences("settings", 0)
    private val mutableState = MutableStateFlow(WeatherUiState(readSaved()))
    val state = mutableState.asStateFlow()
    private val selected: Place get() = mutableState.value.saved.current
    init { ensureAround(mutableState.value.saved) }

    /** The visible city first, then its neighbours, so a swipe already shows their saved forecast. */
    private fun ensureAround(saved: SavedPlaces) {
        listOf(saved.selected, saved.selected + 1, saved.selected - 1)
            .mapNotNull(saved.places::getOrNull).forEach(::ensureLoaded)
    }

    private fun readSaved(): SavedPlaces = SavedPlacesPreferences.read(preferences)
    private fun save(saved: SavedPlaces) = SavedPlacesPreferences.write(preferences, saved)

    /** Called when the pager settles on a page. Cached cities open from disk only. */
    fun selectPage(index: Int) {
        val current = mutableState.value
        if (index == current.page || index !in current.places.indices) return
        val saved = current.saved.select(index)
        save(saved)
        mutableState.update { it.copy(saved = saved, message = null) }
        ensureAround(saved)
        viewModelScope.launch { refreshWidget() }
    }

    /** Opens a saved page's snapshot without blocking other pages. A city without cache downloads 7 days once. */
    private fun ensureLoaded(place: Place) {
        val key = place.cacheKey
        val current = mutableState.value
        if (key in current.snapshots || key in current.opening) return
        mutableState.update { it.copy(opening = it.opening + key) }
        viewModelScope.launch {
            try {
                val snapshot = app.repository.open(place)
                mutableState.update { it.copy(snapshots = it.snapshots + (key to snapshot)) }
            } catch (error: CancellationException) { throw error }
            catch (error: Exception) {
                if (selected.cacheKey == key) mutableState.update { it.copy(message = error.toUiText()) }
            } finally { mutableState.update { it.copy(opening = it.opening - key) } }
        }
    }

    /** Adds a searched city as a new page (or jumps to it if already saved) after its forecast opens. */
    fun load(place: Place) {
        val existing = mutableState.value.saved.indexOf(place)
        if (existing >= 0) { selectPage(existing); return }
        if (!mutableState.value.saved.canAdd) {
            mutableState.update { it.copy(message = UiText(R.string.error_city_limit, listOf(SavedPlaces.MAX))) }
            return
        }
        runWeather(place) { app.repository.open(place) }
    }

    fun removeCurrent() {
        val current = mutableState.value
        if (current.places.size <= 1 || current.busy) return
        val saved = current.saved.remove(current.page)
        save(saved)
        mutableState.update { it.copy(saved = saved, message = null) }
        ensureAround(saved)
        viewModelScope.launch { refreshWidget() }
    }

    fun refresh() = runWeather { app.repository.refreshSixHours(selected) }
    fun reloadAll() = runWeather { app.repository.reloadAll(selected) }

    /** The menu's "refresh all": a fresh seven days for every saved city, one after another. */
    fun reloadAllCities() {
        if (mutableState.value.busy) return
        mutableState.update { it.copy(busy = true, message = null, status = null) }
        viewModelScope.launch {
            var failure: UiText? = null
            for (place in mutableState.value.places) {
                try {
                    val snapshot = app.repository.reloadAll(place)
                    mutableState.update { it.copy(snapshots = it.snapshots + (place.cacheKey to snapshot)) }
                } catch (error: CancellationException) { throw error }
                // One failed city keeps its cache; the rest still refresh.
                catch (error: Exception) { if (failure == null) failure = error.toUiText() }
            }
            mutableState.update { it.copy(busy = false, message = failure) }
            refreshWidget()
        }
    }

    /** Last automatic attempt per city, so a failed refresh waits an hour instead of looping. */
    private val autoAttempts = mutableMapOf<String, Instant>()

    /**
     * Runs only while the screen is visible (the caller ties it to the lifecycle), so there is no
     * background work. The visible city gets a full seven-day refresh when its last full download
     * (or last automatic attempt) is at least an hour old: on opening the app, on returning to it,
     * on swiping to that city, and every hour while it stays on screen. A failure keeps the cache
     * and waits another hour; it never retries in a loop.
     */
    suspend fun refreshEveryHourWhileVisible() {
        while (true) {
            val settled = state.first { !it.busy }
            val place = settled.saved.current
            val snapshot = settled.snapshot
            var waitUntil: Instant? = null
            if (snapshot != null) {
                val key = place.cacheKey
                val last = listOfNotNull(Instant.ofEpochSecond(snapshot.fullFetchedAt), autoAttempts[key]).max()
                val due = last.plus(AUTO_REFRESH_INTERVAL)
                if (!Instant.now().isBefore(due)) {
                    autoAttempts[key] = Instant.now()
                    reloadAll()
                    state.first { !it.busy }
                    continue
                }
                waitUntil = due
            }
            // Swiping to another city or the first snapshot arriving re-checks at once.
            val timeout = waitUntil?.let { Duration.between(Instant.now(), it).toMillis().coerceAtLeast(1_000) } ?: Long.MAX_VALUE
            withTimeoutOrNull(timeout) {
                state.first { it.saved.current.cacheKey != place.cacheKey || (snapshot == null && it.snapshot != null) }
            }
        }
    }

    fun loadUsage() {
        viewModelScope.launch {
            try {
                val usage = app.usage.summary()
                mutableState.update { it.copy(usage = usage, usageError = null) }
            } catch (error: CancellationException) { throw error }
            catch (_: Exception) { mutableState.update { it.copy(usageError = UiText(R.string.usage_read_failed)) } }
        }
    }

    /**
     * Runs one network action for the selected page, or for [adding] which becomes a page only after
     * its forecast loaded, so a failed search result never leaves an empty page behind.
     */
    private fun runWeather(adding: Place? = null, status: UiText? = null, current: Boolean = false, action: suspend () -> WeatherSnapshot) {
        if (mutableState.value.busy) return
        mutableState.update { it.copy(busy = true, message = null, status = status) }
        viewModelScope.launch {
            try {
                val snapshot = action()
                mutableState.update { state ->
                    val saved = when {
                        current -> state.saved.useCurrentLocation(snapshot.place)
                        adding != null -> state.saved.add(adding)
                        else -> state.saved
                    }
                    if (saved != state.saved) save(saved)
                    state.copy(saved = saved, snapshots = state.snapshots + (snapshot.place.cacheKey to snapshot), busy = false, status = null)
                }
                refreshWidget()
            }
            catch (error: CancellationException) { throw error }
            catch (error: Exception) {
                mutableState.update { it.copy(busy = false, status = null, message = error.toUiText()) }
            }
        }
    }

    /** The widget only reads the disk cache, so redraw it whenever the app saved new data. */
    private suspend fun refreshWidget() {
        try { WeatherWidget.refresh(app) }
        catch (error: CancellationException) { throw error }
        catch (_: Exception) { }
    }

    fun search(query: String) {
        if (query.trim().length < 2 || mutableState.value.searching) return
        mutableState.update { it.copy(searching = true, searchResults = emptyList(), searchMessage = null) }
        viewModelScope.launch {
            try {
                val results = app.api.searchPlaces(query)
                mutableState.update { it.copy(searching = false, searchResults = results, searchMessage = if (results.isEmpty()) UiText(R.string.search_none) else null) }
            } catch (error: CancellationException) { throw error }
            catch (_: Exception) { mutableState.update { it.copy(searching = false, searchMessage = UiText(R.string.search_failed)) } }
        }
    }

    fun permissionDenied() {
        mutableState.update { it.copy(message = UiText(R.string.location_denied, locationHelp = true)) }
    }

    fun useLocation() {
        if (ContextCompat.checkSelfPermission(app, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            permissionDenied(); return
        }
        runWeather(status = UiText(R.string.locating), current = true) {
            val place = DeviceLocationProvider(app).locate().copy(isCurrentLocation = true)
            app.repository.open(place).copy(place = place)
        }
    }
}

private val AUTO_REFRESH_INTERVAL: Duration = Duration.ofHours(1)
