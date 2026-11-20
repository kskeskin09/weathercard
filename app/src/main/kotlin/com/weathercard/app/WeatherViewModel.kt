package com.weathercard.app

import android.Manifest
import android.app.Application
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.weathercard.app.data.DeviceLocationProvider
import com.weathercard.core.Place
import com.weathercard.core.WeatherSnapshot
import com.weathercard.core.UsageSummary
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class WeatherUiState(
    val snapshot: WeatherSnapshot? = null,
    val busy: Boolean = false,
    val message: String? = null,
    val searchResults: List<Place> = emptyList(),
    val searching: Boolean = false,
    val searchMessage: String? = null,
    val usage: UsageSummary? = null,
    val usageError: String? = null,
    val status: String? = null,
)

class WeatherViewModel(application: Application) : AndroidViewModel(application) {
    private val app = application as WeatherApplication
    private val preferences = application.getSharedPreferences("settings", 0)
    private var selected = Place(
        preferences.getString("name", "İstanbul")!!,
        preferences.getString("latitude", "41.0082")!!.toDouble(),
        preferences.getString("longitude", "28.9784")!!.toDouble(),
        preferences.getBoolean("is_current", preferences.getString("name", "") == "Mevcut konum"),
    )
    private val mutableState = MutableStateFlow(WeatherUiState())
    val state = mutableState.asStateFlow()
    init { load(selected) }

    fun load(place: Place) = runWeather {
        // Preserve the old visible snapshot until the next location has loaded successfully.
        val snapshot = app.repository.open(place)
        selected = place
        preferences.edit().putString("name", place.name)
            .putString("latitude", place.latitude.toString()).putString("longitude", place.longitude.toString())
            .putBoolean("is_current", place.isCurrentLocation).apply()
        snapshot
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

    private fun runWeather(status: String? = null, action: suspend () -> WeatherSnapshot) {
        if (mutableState.value.busy) return
        mutableState.update { it.copy(busy = true, message = null, status = status) }
        viewModelScope.launch {
            try {
                val snapshot = action()
                mutableState.update { it.copy(snapshot = snapshot, busy = false, status = null) }
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
        runWeather("Konum bulunuyor…") {
            val place = DeviceLocationProvider(app).locate()
            val snapshot = app.repository.open(place)
            selected = place
            preferences.edit().putString("name", place.name).putString("latitude", place.latitude.toString())
                .putString("longitude", place.longitude.toString()).putBoolean("is_current", true).apply()
            snapshot
        }
    }
}
