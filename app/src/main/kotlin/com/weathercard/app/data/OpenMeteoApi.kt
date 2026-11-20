package com.weathercard.app.data

import com.weathercard.core.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.IOException
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.concurrent.TimeUnit

class OpenMeteoApi(private val clock: Clock = Clock.systemUTC()) : WeatherApi {
    private val client = OkHttpClient.Builder()
        .callTimeout(25, TimeUnit.SECONDS)
        .retryOnConnectionFailure(false)
        .build()

    override suspend fun fullForecast(place: Place): WeatherSnapshot {
        val response = request(fullUrl(place))
        return WeatherSnapshot(
            place = place,
            timezone = response.getString("timezone"),
            hours = parseHours(response),
            days = parseDays(response),
            fullFetchedAt = clock.instant().epochSecond,
        )
    }

    override suspend fun sixHours(place: Place, window: HourWindow): List<HourWeather> =
        parseHours(request(sixHourUrl(place, window)))

    suspend fun searchPlaces(query: String): List<Place> {
        val url = "https://geocoding-api.open-meteo.com/v1/search".toHttpUrl().newBuilder()
            .addQueryParameter("name", query.trim())
            .addQueryParameter("count", "8")
            .addQueryParameter("language", "tr")
            .addQueryParameter("format", "json").build()
        val results = request(url).optJSONArray("results") ?: return emptyList()
        return (0 until results.length()).map { index ->
            val row = results.getJSONObject(index)
            val label = listOf(row.getString("name"), row.optString("admin1"), row.optString("country"))
                .filter { it.isNotBlank() }.distinct().joinToString(", ")
            Place(label, row.getDouble("latitude"), row.getDouble("longitude"))
        }
    }

    private suspend fun request(url: HttpUrl): JSONObject = withContext(Dispatchers.IO) {
        client.newCall(Request.Builder().url(url).header("User-Agent", "WeatherCard/0.1 (Android)").build()).execute().use { response ->
            if (response.code == 429) throw IOException("İstek kotası doldu. Bir süre sonra tekrar dene; kayıtlı tahminlerin korunuyor.")
            if (!response.isSuccessful) throw IOException("Hava durumu servisine ulaşılamadı (HTTP ${response.code}).")
            JSONObject(response.body?.string() ?: throw IOException("Servis boş yanıt döndürdü."))
        }
    }

    companion object {
        internal val hourlyFields = listOf(
            "temperature_2m", "apparent_temperature", "relative_humidity_2m",
            "precipitation_probability", "precipitation", "weather_code", "cloud_cover",
            "pressure_msl", "visibility", "wind_speed_10m", "wind_direction_10m",
            "wind_gusts_10m", "uv_index", "is_day",
        )
        private val dailyFields = listOf(
            "weather_code", "temperature_2m_min", "temperature_2m_max", "precipitation_probability_max",
            "precipitation_sum", "wind_speed_10m_max", "sunrise", "sunset", "uv_index_max",
        )
        private fun base(place: Place) = "https://api.open-meteo.com/v1/forecast".toHttpUrl().newBuilder()
            .addQueryParameter("latitude", place.latitude.toString())
            .addQueryParameter("longitude", place.longitude.toString())
            .addQueryParameter("hourly", hourlyFields.joinToString(","))
            .addQueryParameter("temperature_unit", "celsius")
            .addQueryParameter("wind_speed_unit", "kmh")
            .addQueryParameter("precipitation_unit", "mm")
            .addQueryParameter("timeformat", "unixtime")

        internal fun fullUrl(place: Place): HttpUrl = base(place)
            .addQueryParameter("timezone", "auto")
            .addQueryParameter("forecast_days", "7")
            .addQueryParameter("daily", dailyFields.joinToString(",")).build()

        internal fun sixHourUrl(place: Place, window: HourWindow): HttpUrl {
            val formatter = DateTimeFormatter.ofPattern("uuuu-MM-dd'T'HH:mm").withZone(ZoneOffset.UTC)
            return base(place)
                .addQueryParameter("timezone", "GMT")
                .addQueryParameter("start_hour", formatter.format(window.start))
                .addQueryParameter("end_hour", formatter.format(window.endInclusive))
                .build()
        }

        internal fun parseHours(response: JSONObject): List<HourWeather> {
            val data = response.getJSONObject("hourly")
            val times = data.getJSONArray("time")
            return (0 until times.length()).map { i ->
                fun number(key: String) = data.numberAt(key, i)
                HourWeather(
                    time = times.getLong(i), temperature = number("temperature_2m"),
                    apparentTemperature = number("apparent_temperature"), humidity = number("relative_humidity_2m"),
                    precipitationProbability = number("precipitation_probability"), precipitation = number("precipitation"),
                    weatherCode = number("weather_code")?.toInt(), cloudCover = number("cloud_cover"),
                    pressure = number("pressure_msl"), visibility = number("visibility"),
                    windSpeed = number("wind_speed_10m"), windDirection = number("wind_direction_10m"),
                    windGusts = number("wind_gusts_10m"), uvIndex = number("uv_index"),
                    isDay = number("is_day")?.let { it == 1.0 },
                )
            }
        }
        private fun parseDays(response: JSONObject): List<DayWeather> {
            val data = response.getJSONObject("daily")
            val zone = java.time.ZoneId.of(response.getString("timezone"))
            val times = data.getJSONArray("time")
            return (0 until times.length()).map { i ->
                fun number(key: String) = data.numberAt(key, i)
                DayWeather(
                    date = Instant.ofEpochSecond(times.getLong(i)).atZone(zone).toLocalDate().toString(),
                    weatherCode = number("weather_code")?.toInt(), minimum = number("temperature_2m_min"),
                    maximum = number("temperature_2m_max"), precipitationProbability = number("precipitation_probability_max"),
                    precipitation = number("precipitation_sum"), windSpeed = number("wind_speed_10m_max"),
                    sunrise = number("sunrise")?.toLong(), sunset = number("sunset")?.toLong(), uvIndex = number("uv_index_max"),
                )
            }
        }
        private fun JSONObject.numberAt(key: String, index: Int): Double? {
            val array = optJSONArray(key) ?: throw IOException("Servis yanıtında $key eksik.")
            if (index >= array.length()) throw IOException("Servis yanıtındaki $key dizisi eksik.")
            return if (array.isNull(index)) null else array.getDouble(index).takeIf { it.isFinite() }
        }
    }
}

