package com.weathercard.app

import android.Manifest
import android.app.Application
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import com.weathercard.core.Place
import com.weathercard.core.WeatherSnapshot
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeout
import kotlin.math.round

data class WeatherUiState(
    val snapshot: WeatherSnapshot? = null,
    val busy: Boolean = false,
    val message: String? = null,
    val searchResults: List<Place> = emptyList(),
    val searching: Boolean = false,
    val searchMessage: String? = null,
)

class WeatherViewModel(application: Application) : AndroidViewModel(application) {
    private val app = application as WeatherApplication
    private val preferences = application.getSharedPreferences("settings", 0)
    private var selected = Place(
        preferences.getString("name", "İstanbul")!!,
        preferences.getString("latitude", "41.0082")!!.toDouble(),
        preferences.getString("longitude", "28.9784")!!.toDouble(),
    )
    private val mutableState = MutableStateFlow(WeatherUiState())
    val state = mutableState.asStateFlow()
    init { load(selected) }

    fun load(place: Place) = runWeather {
        // Preserve the old visible snapshot until the next location has loaded successfully.
        val snapshot = app.repository.open(place)
        selected = place
        preferences.edit().putString("name", place.name)
            .putString("latitude", place.latitude.toString()).putString("longitude", place.longitude.toString()).apply()
        snapshot
    }
    fun refresh() = runWeather { app.repository.refreshSixHours(selected) }
    fun reloadAll() = runWeather { app.repository.reloadAll(selected) }

    private fun runWeather(action: suspend () -> WeatherSnapshot) {
        if (mutableState.value.busy) return
        mutableState.update { it.copy(busy = true, message = null) }
        viewModelScope.launch {
            try {
                val snapshot = action()
                mutableState.update { it.copy(snapshot = snapshot, busy = false) }
            }
            catch (error: CancellationException) { throw error }
            catch (error: Exception) {
                mutableState.update { it.copy(busy = false, message = error.message ?: "Bağlantı kurulamadı. Kayıtlı veriler korunuyor.") }
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

    @android.annotation.SuppressLint("MissingPermission")
    fun useLocation() {
        if (ContextCompat.checkSelfPermission(app, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            permissionDenied(); return
        }
        runWeather {
            val cancellation = CancellationTokenSource()
            val location = try {
                withTimeout(20_000) {
                    LocationServices.getFusedLocationProviderClient(app)
                        .getCurrentLocation(Priority.PRIORITY_BALANCED_POWER_ACCURACY, cancellation.token).await()
                }
            } catch (error: kotlinx.coroutines.TimeoutCancellationException) {
                throw java.io.IOException("Konum alınamadı. Konumu aç veya şehir seç.")
            } finally { cancellation.cancel() }
            requireNotNull(location) { "Konum bulunamadı. Konumu aç veya şehir seç." }
            // ~1 km precision avoids creating new cache files for tiny GPS movements.
            val place = Place("Mevcut konum", round(location.latitude * 100) / 100, round(location.longitude * 100) / 100)
            val snapshot = app.repository.open(place)
            selected = place
            preferences.edit().putString("name", place.name).putString("latitude", place.latitude.toString())
                .putString("longitude", place.longitude.toString()).apply()
            snapshot
        }
    }
}
