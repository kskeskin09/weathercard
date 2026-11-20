package com.weathercard.core

enum class Sky { CLEAR, MOSTLY_CLEAR, PARTLY_CLOUDY, OVERCAST, FOG, UNKNOWN }
enum class Precipitation { NONE, DRIZZLE, RAIN, SNOW, SNOW_GRAINS, SHOWERS, SNOW_SHOWERS, HAIL }

data class WeatherCondition(
    val description: String,
    val sky: Sky = Sky.OVERCAST,
    val precipitation: Precipitation = Precipitation.NONE,
    val intensity: Int = 0,
    val freezing: Boolean = false,
    val thunder: Boolean = false,
) {
    companion object {
        fun from(code: Int?, isDay: Boolean = true): WeatherCondition = when (code) {
            0 -> WeatherCondition(if (isDay) "Açık ve güneşli" else "Açık gece", Sky.CLEAR)
            1 -> WeatherCondition("Çoğunlukla açık", Sky.MOSTLY_CLEAR)
            2 -> WeatherCondition("Parçalı bulutlu", Sky.PARTLY_CLOUDY)
            3 -> WeatherCondition("Kapalı", Sky.OVERCAST)
            45 -> WeatherCondition("Sisli", Sky.FOG)
            48 -> WeatherCondition("Kırağılı sis", Sky.FOG, freezing = true)
            51 -> WeatherCondition("Hafif çiseleme", precipitation = Precipitation.DRIZZLE, intensity = 1)
            53 -> WeatherCondition("Orta çiseleme", precipitation = Precipitation.DRIZZLE, intensity = 2)
            55 -> WeatherCondition("Yoğun çiseleme", precipitation = Precipitation.DRIZZLE, intensity = 3)
            56 -> WeatherCondition("Hafif donan çiseleme", precipitation = Precipitation.DRIZZLE, intensity = 1, freezing = true)
            57 -> WeatherCondition("Yoğun donan çiseleme", precipitation = Precipitation.DRIZZLE, intensity = 3, freezing = true)
            61 -> WeatherCondition("Hafif yağmur", precipitation = Precipitation.RAIN, intensity = 1)
            63 -> WeatherCondition("Orta yağmur", precipitation = Precipitation.RAIN, intensity = 2)
            65 -> WeatherCondition("Şiddetli yağmur", precipitation = Precipitation.RAIN, intensity = 3)
            66 -> WeatherCondition("Hafif donan yağmur", precipitation = Precipitation.RAIN, intensity = 1, freezing = true)
            67 -> WeatherCondition("Şiddetli donan yağmur", precipitation = Precipitation.RAIN, intensity = 3, freezing = true)
            71 -> WeatherCondition("Hafif kar", precipitation = Precipitation.SNOW, intensity = 1)
            73 -> WeatherCondition("Orta kar", precipitation = Precipitation.SNOW, intensity = 2)
            75 -> WeatherCondition("Yoğun kar", precipitation = Precipitation.SNOW, intensity = 3)
            77 -> WeatherCondition("Kar taneleri", precipitation = Precipitation.SNOW_GRAINS, intensity = 2)
            80 -> WeatherCondition("Hafif sağanak", Sky.PARTLY_CLOUDY, Precipitation.SHOWERS, 1)
            81 -> WeatherCondition("Orta sağanak", Sky.PARTLY_CLOUDY, Precipitation.SHOWERS, 2)
            82 -> WeatherCondition("Şiddetli sağanak", Sky.PARTLY_CLOUDY, Precipitation.SHOWERS, 3)
            85 -> WeatherCondition("Hafif kar sağanağı", Sky.PARTLY_CLOUDY, Precipitation.SNOW_SHOWERS, 1)
            86 -> WeatherCondition("Yoğun kar sağanağı", Sky.PARTLY_CLOUDY, Precipitation.SNOW_SHOWERS, 3)
            95 -> WeatherCondition("Gök gürültülü fırtına", thunder = true)
            96 -> WeatherCondition("Hafif dolulu fırtına", precipitation = Precipitation.HAIL, intensity = 1, thunder = true)
            99 -> WeatherCondition("Şiddetli dolulu fırtına", precipitation = Precipitation.HAIL, intensity = 3, thunder = true)
            else -> WeatherCondition("Hava durumu bilinmiyor", Sky.UNKNOWN)
        }
    }
}

