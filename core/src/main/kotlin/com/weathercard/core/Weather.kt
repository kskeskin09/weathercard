package com.weathercard.core

import kotlinx.serialization.Serializable
import java.time.Instant
import java.time.ZoneId
import java.time.temporal.ChronoUnit

@Serializable
data class Place(val name: String, val latitude: Double, val longitude: Double) {
    init {
        require(latitude.isFinite() && latitude in -90.0..90.0)
        require(longitude.isFinite() && longitude in -180.0..180.0)
    }
    // Names do not determine cache identity. GPS positions are normalized before construction.
    val cacheKey: String get() = "$latitude,$longitude"
}

@Serializable
data class HourWeather(
    val time: Long,
    val temperature: Double?,
    val apparentTemperature: Double?,
    val humidity: Double?,
    val precipitationProbability: Double?,
    val precipitation: Double?,
    val weatherCode: Int?,
    val cloudCover: Double?,
    val pressure: Double?,
    val visibility: Double?,
    val windSpeed: Double?,
    val windDirection: Double?,
    val windGusts: Double?,
    val uvIndex: Double?,
    val isDay: Boolean?,
)

@Serializable
data class DayWeather(
    val date: String,
    val weatherCode: Int?,
    val minimum: Double?,
    val maximum: Double?,
    val precipitationProbability: Double?,
    val precipitation: Double?,
    val windSpeed: Double?,
    val sunrise: Long?,
    val sunset: Long?,
    val uvIndex: Double?,
)

@Serializable
data class WeatherSnapshot(
    val place: Place,
    val timezone: String,
    val hours: List<HourWeather>,
    val days: List<DayWeather>,
    val fullFetchedAt: Long,
    val sixHoursFetchedAt: Long? = null,
)

data class HourWindow(val start: Instant, val timezone: String = "GMT") {
    init {
        val local = start.atZone(ZoneId.of(timezone))
        require(local == local.truncatedTo(ChronoUnit.HOURS))
    }
    val endInclusive: Instant get() = start.plus(5, ChronoUnit.HOURS)
    val timestamps: Set<Long> get() = (0L..5L).map { start.plus(it, ChronoUnit.HOURS).epochSecond }.toSet()
    companion object {
        fun at(now: Instant, timezone: String = "GMT") = HourWindow(
            now.atZone(ZoneId.of(timezone)).truncatedTo(ChronoUnit.HOURS).toInstant(), timezone,
        )
    }
}

