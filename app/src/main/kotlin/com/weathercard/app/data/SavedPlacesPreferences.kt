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
                preferences.getBoolean("is_current", preferences.getString("name", "") in setOf("Mevcut konum", "Current location")),
            )
        } catch (_: IllegalArgumentException) { Place("İstanbul", 41.0082, 28.9784) }
        return SavedPlaces(listOf(legacy))
    }

    private const val LOCATION_PAGE = "location_page_enabled"

    /** The page that follows the device location; on unless the user turns it off in settings. */
    fun locationPageEnabled(preferences: SharedPreferences): Boolean = preferences.getBoolean(LOCATION_PAGE, true)
    fun setLocationPageEnabled(preferences: SharedPreferences, enabled: Boolean) {
        preferences.edit().putBoolean(LOCATION_PAGE, enabled).apply()
    }

    fun write(preferences: SharedPreferences, saved: SavedPlaces) {
        preferences.edit().putString(KEY, json.encodeToString(SavedPlaces.serializer(), saved)).apply()
    }
}
