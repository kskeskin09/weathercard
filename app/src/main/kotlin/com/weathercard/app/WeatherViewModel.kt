package com.weathercard.app

import android.Manifest
import android.app.Application
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.weathercard.app.data.DeviceLocationProvider
import com.weathercard.core.Place
import com.weathercard.core.SavedPlaces
import com.weathercard.core.WeatherSnapshot
import com.weathercard.core.UsageSummary
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

data class WeatherUiState(
    val saved: SavedPlaces,
    /** Loaded forecasts by place cache key; swiping to a cached city never asks the network. */
    val snapshots: Map<String, WeatherSnapshot> = emptyMap(),
    /** Cache keys whose saved forecast is being opened. */
    val opening: Set<String> = emptySet(),
    val busy: Boolean = false,
    val message: String? = null,
    val searchResults: List<Place> = emptyList(),
    val searching: Boolean = false,
    val searchMessage: String? = null,
    val usage: UsageSummary? = null,
    val usageError: String? = null,
    val status: String? = null,
) {
    val places: List<Place> get() = saved.places
    val page: Int get() = saved.selected
    val snapshot: WeatherSnapshot? get() = snapshotFor(saved.current)
    fun snapshotFor(place: Place): WeatherSnapshot? = snapshots[place.cacheKey]?.copy(place = place)
}

class WeatherViewModel(application: Application) : AndroidViewModel(application) {
    private val app = application as WeatherApplication
    private val preferences = application.getSharedPreferences("settings", 0)
    private val json = Json { ignoreUnknownKeys = true }
    private val mutableState = MutableStateFlow(WeatherUiState(readSaved()))
    val state = mutableState.asStateFlow()
    private val selected: Place get() = mutableState.value.saved.current
    init { ensureAround(mutableState.value.saved) }

    /** The visible city first, then its neighbours, so a swipe already shows their saved forecast. */
    private fun ensureAround(saved: SavedPlaces) {
        listOf(saved.selected, saved.selected + 1, saved.selected - 1)
            .mapNotNull(saved.places::getOrNull).forEach(::ensureLoaded)
    }

    /** Earlier versions stored a single place under separate keys; keep it as the first page. */
    private fun readSaved(): SavedPlaces {
        preferences.getString("places_v1", null)?.let { stored ->
            try { return json.decodeFromString<SavedPlaces>(stored) } catch (_: IllegalArgumentException) {}
        }
        val legacy = Place(
            preferences.getString("name", "İstanbul")!!,
            preferences.getString("latitude", "41.0082")!!.toDouble(),
            preferences.getString("longitude", "28.9784")!!.toDouble(),
            preferences.getBoolean("is_current", preferences.getString("name", "") == "Mevcut konum"),
        )
        return SavedPlaces(listOf(legacy))
    }

    private fun save(saved: SavedPlaces) {
        preferences.edit().putString("places_v1", json.encodeToString(saved)).apply()
    }

    /** Called when the pager settles on a page. Cached cities open from disk only. */
    fun selectPage(index: Int) {
        val current = mutableState.value
        if (index == current.page || index !in current.places.indices) return
        val saved = current.saved.select(index)
        save(saved)
        mutableState.update { it.copy(saved = saved, message = null) }
        ensureAround(saved)
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
                if (selected.cacheKey == key) mutableState.update { it.copy(message = error.message ?: "Bağlantı kurulamadı. Kayıtlı veriler korunuyor.") }
            } finally { mutableState.update { it.copy(opening = it.opening - key) } }
        }
    }

    /** Adds a searched city as a new page (or jumps to it if already saved) after its forecast opens. */
    fun load(place: Place) {
        val existing = mutableState.value.saved.indexOf(place)
        if (existing >= 0) { selectPage(existing); return }
        if (!mutableState.value.saved.canAdd) {
            mutableState.update { it.copy(message = "En fazla ${SavedPlaces.MAX} şehir kaydedebilirsin. Önce bir şehri kaldır.") }
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
    }

    fun refresh() = runWeather { app.repository.refreshSixHours(selected) }
    fun reloadAll() = runWeather { app.repository.reloadAll(selected) }

    fun loadUsage() {
        viewModelScope.launch {
            try {
                val usage = app.usage.summary()
                mutableState.update { it.copy(usage = usage, usageError = null) }
            } catch (error: CancellationException) { throw error }
            catch (_: Exception) { mutableState.update { it.copy(usageError = "Kullanım kaydı okunamadı.") } }
        }
    }

    /**
     * Runs one network action for the selected page, or for [adding] which becomes a page only after
     * its forecast loaded, so a failed search result never leaves an empty page behind.
     */
    private fun runWeather(adding: Place? = null, status: String? = null, current: Boolean = false, action: suspend () -> WeatherSnapshot) {
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
            }
            catch (error: CancellationException) { throw error }
            catch (error: Exception) {
                mutableState.update { it.copy(busy = false, status = null, message = error.message ?: "Bağlantı kurulamadı. Kayıtlı veriler korunuyor.") }
            }
        }
    }

    fun search(query: String) {
        if (query.trim().length < 2 || mutableState.value.searching) return
        mutableState.update { it.copy(searching = true, searchResults = emptyList(), searchMessage = null) }
        viewModelScope.launch {
            try {
                val results = app.api.searchPlaces(query)
                mutableState.update { it.copy(searching = false, searchResults = results, searchMessage = if (results.isEmpty()) "Şehir bulunamadı." else null) }
            } catch (error: CancellationException) { throw error }
            catch (_: Exception) { mutableState.update { it.copy(searching = false, searchMessage = "Şehir araması başarısız. Tekrar dene.") } }
        }
    }

    fun permissionDenied() {
        mutableState.update { it.copy(message = "Konum izni verilmedi. Şehir seçerek devam edebilirsin.") }
    }

    fun useLocation() {
        if (ContextCompat.checkSelfPermission(app, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            permissionDenied(); return
        }
        runWeather(status = "Konum bulunuyor…", current = true) {
            val place = DeviceLocationProvider(app).locate().copy(isCurrentLocation = true)
            app.repository.open(place).copy(place = place)
        }
    }
}
