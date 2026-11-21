package com.weathercard.app

import android.app.Application
import com.weathercard.app.data.DiskForecastStore
import com.weathercard.app.data.OpenMeteoApi
import com.weathercard.app.data.DiskUsageStore
import com.weathercard.core.UsageTracker
import com.weathercard.core.WeatherRepository
import java.io.File

class WeatherApplication : Application() {
    val usage by lazy { UsageTracker(DiskUsageStore(File(filesDir, "api-usage-v1.json"))) }
    val api by lazy { OpenMeteoApi(usage) }
    val forecastStore by lazy { DiskForecastStore(File(filesDir, "forecasts")) }
    val repository by lazy { WeatherRepository(api, forecastStore) }
}

