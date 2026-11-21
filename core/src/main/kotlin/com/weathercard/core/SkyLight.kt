package com.weathercard.core

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.pow
import kotlin.math.sin

/**
 * How bright the sky should look at a given moment, derived from the day's sunrise and sunset.
 *
 * @property daylight 0 at deep night, 1 at solar noon. Twilight ramps in and out around the sun times.
 * @property golden 0..1 strength of the warm sunrise/sunset tint, peaking exactly at sunrise or sunset.
 * @property sunProgress 0 at sunrise to 1 at sunset while the sun is up, otherwise null.
 */
data class SkyLight(val daylight: Float, val golden: Float, val sunProgress: Float?) {
    companion object {
        /** Twilight before sunrise and after sunset, in seconds. */
        const val TWILIGHT = 50 * 60L
        /** How long the warm colours linger on each side of sunrise and sunset, in seconds. */
        const val GOLDEN = 70 * 60L
        private const val HORIZON = .3f

        fun at(nowEpochSecond: Long, sunrise: Long?, sunset: Long?, isDayFallback: Boolean): SkyLight {
            if (sunrise == null || sunset == null || sunset <= sunrise) {
                return if (isDayFallback) SkyLight(.8f, 0f, .5f) else SkyLight(0f, 0f, null)
            }
            val now = nowEpochSecond
            val nearest = minOf(abs(now - sunrise), abs(now - sunset))
            val golden = (1f - nearest.toFloat() / GOLDEN).coerceIn(0f, 1f)
            return when {
                now in sunrise..sunset -> {
                    val progress = (now - sunrise).toFloat() / (sunset - sunrise)
                    val elevation = sin(PI * progress).toFloat().coerceAtLeast(0f)
                    SkyLight(HORIZON + (1f - HORIZON) * elevation.pow(.6f), golden, progress)
                }
                now < sunrise && sunrise - now < TWILIGHT -> SkyLight(HORIZON * (1f - (sunrise - now).toFloat() / TWILIGHT), golden, null)
                now > sunset && now - sunset < TWILIGHT -> SkyLight(HORIZON * (1f - (now - sunset).toFloat() / TWILIGHT), golden, null)
                else -> SkyLight(0f, golden, null)
            }
        }
    }
}
