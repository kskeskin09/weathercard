package com.weathercard.core

enum class Sky { CLEAR, MOSTLY_CLEAR, PARTLY_CLOUDY, OVERCAST, FOG, UNKNOWN }
enum class Precipitation { NONE, DRIZZLE, RAIN, SNOW, SNOW_GRAINS, SHOWERS, SNOW_SHOWERS, HAIL }

/** Stable identity of a WMO condition; the app turns it into localized text. */
enum class ConditionKind {
    CLEAR_DAY, CLEAR_NIGHT, MOSTLY_CLEAR, PARTLY_CLOUDY, OVERCAST, FOG, RIME_FOG,
    LIGHT_DRIZZLE, MODERATE_DRIZZLE, DENSE_DRIZZLE, LIGHT_FREEZING_DRIZZLE, DENSE_FREEZING_DRIZZLE,
    LIGHT_RAIN, MODERATE_RAIN, HEAVY_RAIN, LIGHT_FREEZING_RAIN, HEAVY_FREEZING_RAIN,
    LIGHT_SNOW, MODERATE_SNOW, HEAVY_SNOW, SNOW_GRAINS, LIGHT_SHOWERS, MODERATE_SHOWERS, VIOLENT_SHOWERS,
    LIGHT_SNOW_SHOWERS, HEAVY_SNOW_SHOWERS, THUNDERSTORM, THUNDERSTORM_LIGHT_HAIL, THUNDERSTORM_HEAVY_HAIL, UNKNOWN,
}

data class WeatherCondition(
    val kind: ConditionKind,
    val sky: Sky = Sky.OVERCAST,
    val precipitation: Precipitation = Precipitation.NONE,
    val intensity: Int = 0,
    val freezing: Boolean = false,
    val thunder: Boolean = false,
) {
    companion object {
        fun from(code: Int?, isDay: Boolean = true): WeatherCondition = when (code) {
            0 -> WeatherCondition(if (isDay) ConditionKind.CLEAR_DAY else ConditionKind.CLEAR_NIGHT, Sky.CLEAR)
            1 -> WeatherCondition(ConditionKind.MOSTLY_CLEAR, Sky.MOSTLY_CLEAR)
            2 -> WeatherCondition(ConditionKind.PARTLY_CLOUDY, Sky.PARTLY_CLOUDY)
            3 -> WeatherCondition(ConditionKind.OVERCAST, Sky.OVERCAST)
            45 -> WeatherCondition(ConditionKind.FOG, Sky.FOG)
            48 -> WeatherCondition(ConditionKind.RIME_FOG, Sky.FOG, freezing = true)
            51 -> WeatherCondition(ConditionKind.LIGHT_DRIZZLE, precipitation = Precipitation.DRIZZLE, intensity = 1)
            53 -> WeatherCondition(ConditionKind.MODERATE_DRIZZLE, precipitation = Precipitation.DRIZZLE, intensity = 2)
            55 -> WeatherCondition(ConditionKind.DENSE_DRIZZLE, precipitation = Precipitation.DRIZZLE, intensity = 3)
            56 -> WeatherCondition(ConditionKind.LIGHT_FREEZING_DRIZZLE, precipitation = Precipitation.DRIZZLE, intensity = 1, freezing = true)
            57 -> WeatherCondition(ConditionKind.DENSE_FREEZING_DRIZZLE, precipitation = Precipitation.DRIZZLE, intensity = 3, freezing = true)
            61 -> WeatherCondition(ConditionKind.LIGHT_RAIN, precipitation = Precipitation.RAIN, intensity = 1)
            63 -> WeatherCondition(ConditionKind.MODERATE_RAIN, precipitation = Precipitation.RAIN, intensity = 2)
            65 -> WeatherCondition(ConditionKind.HEAVY_RAIN, precipitation = Precipitation.RAIN, intensity = 3)
            66 -> WeatherCondition(ConditionKind.LIGHT_FREEZING_RAIN, precipitation = Precipitation.RAIN, intensity = 1, freezing = true)
            67 -> WeatherCondition(ConditionKind.HEAVY_FREEZING_RAIN, precipitation = Precipitation.RAIN, intensity = 3, freezing = true)
            71 -> WeatherCondition(ConditionKind.LIGHT_SNOW, precipitation = Precipitation.SNOW, intensity = 1)
            73 -> WeatherCondition(ConditionKind.MODERATE_SNOW, precipitation = Precipitation.SNOW, intensity = 2)
            75 -> WeatherCondition(ConditionKind.HEAVY_SNOW, precipitation = Precipitation.SNOW, intensity = 3)
            77 -> WeatherCondition(ConditionKind.SNOW_GRAINS, precipitation = Precipitation.SNOW_GRAINS, intensity = 2)
            80 -> WeatherCondition(ConditionKind.LIGHT_SHOWERS, Sky.PARTLY_CLOUDY, Precipitation.SHOWERS, 1)
            81 -> WeatherCondition(ConditionKind.MODERATE_SHOWERS, Sky.PARTLY_CLOUDY, Precipitation.SHOWERS, 2)
            82 -> WeatherCondition(ConditionKind.VIOLENT_SHOWERS, Sky.PARTLY_CLOUDY, Precipitation.SHOWERS, 3)
            85 -> WeatherCondition(ConditionKind.LIGHT_SNOW_SHOWERS, Sky.PARTLY_CLOUDY, Precipitation.SNOW_SHOWERS, 1)
            86 -> WeatherCondition(ConditionKind.HEAVY_SNOW_SHOWERS, Sky.PARTLY_CLOUDY, Precipitation.SNOW_SHOWERS, 3)
            95 -> WeatherCondition(ConditionKind.THUNDERSTORM, thunder = true)
            96 -> WeatherCondition(ConditionKind.THUNDERSTORM_LIGHT_HAIL, precipitation = Precipitation.HAIL, intensity = 1, thunder = true)
            99 -> WeatherCondition(ConditionKind.THUNDERSTORM_HEAVY_HAIL, precipitation = Precipitation.HAIL, intensity = 3, thunder = true)
            else -> WeatherCondition(ConditionKind.UNKNOWN, Sky.UNKNOWN)
        }
    }
}

