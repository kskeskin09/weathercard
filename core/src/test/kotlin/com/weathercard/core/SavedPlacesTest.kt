package com.weathercard.core

import kotlin.test.*

class SavedPlacesTest {
    private val istanbul = Place("İstanbul", 41.0082, 28.9784)
    private val ankara = Place("Ankara", 39.9334, 32.8597)
    private val izmir = Place("İzmir", 38.4237, 27.1428)
    private val here = Place("Mevcut konum", 40.99, 29.02, isCurrentLocation = true)

    @Test fun `adding a city appends and selects it`() {
        val saved = SavedPlaces(listOf(istanbul)).add(ankara)
        assertEquals(listOf(istanbul, ankara), saved.places)
        assertEquals(ankara, saved.current)
    }
    @Test fun `adding a saved city selects the existing page instead of duplicating`() {
        val saved = SavedPlaces(listOf(istanbul, ankara), 1).add(istanbul.copy(name = "Istanbul, TR"))
        assertEquals(2, saved.places.size)
        assertEquals(0, saved.selected)
    }
    @Test fun `current location replaces the old one and becomes the first page`() {
        val moved = here.copy(latitude = 41.05)
        val saved = SavedPlaces(listOf(here, istanbul, ankara), 2).useCurrentLocation(moved)
        assertEquals(listOf(moved, istanbul, ankara), saved.places)
        assertEquals(0, saved.selected)
    }
    @Test fun `current location at a saved city keeps a single page`() {
        val saved = SavedPlaces(listOf(istanbul, ankara)).useCurrentLocation(ankara)
        assertEquals(listOf(ankara.copy(isCurrentLocation = true), istanbul), saved.places)
    }
    @Test fun `removing keeps a sensible selection and never empties the list`() {
        val saved = SavedPlaces(listOf(istanbul, ankara, izmir), 2)
        assertEquals(1, saved.remove(2).selected)
        assertEquals(1, saved.remove(0).selected)
        assertEquals(izmir, saved.remove(0).current)
        assertEquals(listOf(istanbul), SavedPlaces(listOf(istanbul)).remove(0).places)
    }
    @Test fun `city limit is enforced`() {
        var saved = SavedPlaces(listOf(istanbul))
        (1 until SavedPlaces.MAX).forEach { saved = saved.add(Place("Şehir $it", it.toDouble(), it.toDouble())) }
        assertFalse(saved.canAdd)
        assertFailsWith<IllegalArgumentException> { saved.add(ankara) }
    }
}
