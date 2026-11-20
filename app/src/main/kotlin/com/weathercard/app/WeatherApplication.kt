package com.weathercard.app

import android.app.Application
import com.weathercard.app.data.DiskForecastStore
import com.weathercard.app.data.OpenMeteoApi
import com.weathercard.core.WeatherRepository
import java.io.File

class WeatherApplication : Application() {
    val api by lazy { OpenMeteoApi() }
    val repository by lazy { WeatherRepository(api, DiskForecastStore(File(filesDir, "forecasts"))) }
}

