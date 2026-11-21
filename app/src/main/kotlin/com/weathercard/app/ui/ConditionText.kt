package com.weathercard.app.ui

import androidx.annotation.StringRes
import com.weathercard.app.R
import com.weathercard.core.ConditionKind
import com.weathercard.core.ConditionKind.*

/** Localized, accessible name of a WMO condition. */
@get:StringRes
val ConditionKind.labelRes: Int get() = when (this) {
    CLEAR_DAY -> R.string.condition_clear_day
    CLEAR_NIGHT -> R.string.condition_clear_night
    MOSTLY_CLEAR -> R.string.condition_mostly_clear
    PARTLY_CLOUDY -> R.string.condition_partly_cloudy
    OVERCAST -> R.string.condition_overcast
    FOG -> R.string.condition_fog
    RIME_FOG -> R.string.condition_rime_fog
    LIGHT_DRIZZLE -> R.string.condition_light_drizzle
    MODERATE_DRIZZLE -> R.string.condition_moderate_drizzle
    DENSE_DRIZZLE -> R.string.condition_dense_drizzle
    LIGHT_FREEZING_DRIZZLE -> R.string.condition_light_freezing_drizzle
    DENSE_FREEZING_DRIZZLE -> R.string.condition_dense_freezing_drizzle
    LIGHT_RAIN -> R.string.condition_light_rain
    MODERATE_RAIN -> R.string.condition_moderate_rain
    HEAVY_RAIN -> R.string.condition_heavy_rain
    LIGHT_FREEZING_RAIN -> R.string.condition_light_freezing_rain
    HEAVY_FREEZING_RAIN -> R.string.condition_heavy_freezing_rain
    LIGHT_SNOW -> R.string.condition_light_snow
    MODERATE_SNOW -> R.string.condition_moderate_snow
    HEAVY_SNOW -> R.string.condition_heavy_snow
    SNOW_GRAINS -> R.string.condition_snow_grains
    LIGHT_SHOWERS -> R.string.condition_light_showers
    MODERATE_SHOWERS -> R.string.condition_moderate_showers
    VIOLENT_SHOWERS -> R.string.condition_violent_showers
    LIGHT_SNOW_SHOWERS -> R.string.condition_light_snow_showers
    HEAVY_SNOW_SHOWERS -> R.string.condition_heavy_snow_showers
    THUNDERSTORM -> R.string.condition_thunderstorm
    THUNDERSTORM_LIGHT_HAIL -> R.string.condition_thunderstorm_light_hail
    THUNDERSTORM_HEAVY_HAIL -> R.string.condition_thunderstorm_heavy_hail
    UNKNOWN -> R.string.condition_unknown
}
