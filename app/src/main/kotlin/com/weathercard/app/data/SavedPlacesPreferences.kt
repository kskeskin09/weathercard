package com.weathercard.app.data

import android.content.SharedPreferences
import com.weathercard.core.Place
import com.weathercard.core.SavedPlaces
import kotlinx.serialization.json.Json

/** Saved cities in the "settings" preferences, shared by the main screen and the widget. */
object SavedPlacesPreferences {
    private const val KEY = "places_v1"
    private val json = Json { ignoreUnknownKeys = true }

    /** Earlier versions stored a single place under separate keys; it becomes the first page. */
    fun read(preferences: SharedPreferences): SavedPlaces {
        preferences.getString(KEY, null)?.let { stored ->
            try { return json.decodeFromString(SavedPlaces.serializer(), stored) } catch (_: IllegalArgumentException) {}
        }
        val legacy = try {
            Place(
                preferences.getString("name", "İstanbul")!!,
                preferences.getString("latitude", "41.0082")!!.toDouble(),
                preferences.getString("longitude", "28.9784")!!.toDouble(),
                preferences.getBoolean("is_current", preferences.getString("name", "") == "Mevcut konum"),
            )
        } catch (_: IllegalArgumentException) { Place("İstanbul", 41.0082, 28.9784) }
        return SavedPlaces(listOf(legacy))
    }

    fun write(preferences: SharedPreferences, saved: SavedPlaces) {
        preferences.edit().putString(KEY, json.encodeToString(SavedPlaces.serializer(), saved)).apply()
    }
}
