package com.weathercard.app.widget

import com.weathercard.core.WeatherCondition
import com.weathercard.core.WeatherSnapshot
import java.time.Instant
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import kotlin.math.roundToInt

/** What the home-screen widget shows; derived only from the on-disk cache, never from the network. */
data class WidgetForecast(
    val placeName: String,
    val isCurrentLocation: Boolean,
    val temperature: Double?,
    val weatherCode: Int?,
    val isDay: Boolean,
    val description: String,
    val maximum: Double?,
    val minimum: Double?,
) {
    companion object {
        /** Picks the hour that contains [now]; returns null when the cache no longer covers this hour. */
        fun from(snapshot: WeatherSnapshot, now: Instant): WidgetForecast? {
            val zone = runCatching { ZoneId.of(snapshot.timezone) }.getOrDefault(ZoneId.systemDefault())
            val local = now.atZone(zone)
            val hourStart = local.truncatedTo(ChronoUnit.HOURS).toEpochSecond()
            val hour = snapshot.hours.find { it.time == hourStart } ?: return null
            val today = snapshot.days.find { it.date == local.toLocalDate().toString() }
            val isDay = hour.isDay ?: run {
                val sunrise = today?.sunrise
                val sunset = today?.sunset
                if (sunrise != null && sunset != null) now.epochSecond in sunrise until sunset else local.hour in 7..18
            }
            return WidgetForecast(
                placeName = snapshot.place.name,
                isCurrentLocation = snapshot.place.isCurrentLocation,
                temperature = hour.temperature,
                weatherCode = hour.weatherCode,
                isDay = isDay,
                description = WeatherCondition.from(hour.weatherCode, isDay).description,
                maximum = today?.maximum,
                minimum = today?.minimum,
            )
        }
    }
}

internal fun widgetDegrees(value: Double?): String = value?.roundToInt()?.let { "$it°" } ?: "—°"
