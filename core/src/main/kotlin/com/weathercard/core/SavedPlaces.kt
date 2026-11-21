package com.weathercard.core

import kotlinx.serialization.Serializable

/**
 * Cities shown as swipeable pages on the main screen. Identity is the cache key, so the same
 * coordinates are never saved twice. At most one entry is the device's current location and it
 * is always the first page.
 */
@Serializable
data class SavedPlaces(val places: List<Place>, val selected: Int = 0) {
    init {
        require(places.isNotEmpty())
        require(places.map { it.cacheKey }.distinct().size == places.size)
        require(places.drop(1).none { it.isCurrentLocation })
    }

    val current: Place get() = places[selected.coerceIn(places.indices)]
    val canAdd: Boolean get() = places.size < MAX

    fun indexOf(place: Place): Int = places.indexOfFirst { it.cacheKey == place.cacheKey }

    fun select(index: Int): SavedPlaces = copy(selected = index.coerceIn(places.indices))

    /** Adds a searched city at the end, or selects it if already saved. */
    fun add(place: Place): SavedPlaces {
        val city = place.copy(isCurrentLocation = false)
        indexOf(city).takeIf { it >= 0 }?.let { return select(it) }
        require(canAdd) { "At most $MAX cities can be saved." }
        return copy(places = places + city, selected = places.size)
    }

    /** The located device position replaces the old current-location page and becomes page one. */
    fun useCurrentLocation(place: Place): SavedPlaces {
        val located = place.copy(isCurrentLocation = true)
        val rest = places.filterNot { it.isCurrentLocation || it.cacheKey == located.cacheKey }
        return SavedPlaces((listOf(located) + rest).take(MAX), 0)
    }

    /** Drops the current-location page; null when it was the only page. */
    fun withoutCurrentLocation(): SavedPlaces? {
        if (places.none { it.isCurrentLocation }) return this
        val rest = places.filterNot { it.isCurrentLocation }
        if (rest.isEmpty()) return null
        return SavedPlaces(rest, (selected - 1).coerceIn(rest.indices))
    }

    /** On a fresh app start the current-location page, when there is one, is shown first. */
    fun startPage(): SavedPlaces = if (places.first().isCurrentLocation) select(0) else this

    /** The last remaining city cannot be removed. */
    fun remove(index: Int): SavedPlaces {
        if (places.size <= 1 || index !in places.indices) return this
        val next = places.filterIndexed { i, _ -> i != index }
        val newSelected = when {
            selected > index -> selected - 1
            selected == index -> (index - 1).coerceAtLeast(0)
            else -> selected
        }
        return SavedPlaces(next, newSelected.coerceIn(next.indices))
    }

    companion object { const val MAX = 10 }
}
