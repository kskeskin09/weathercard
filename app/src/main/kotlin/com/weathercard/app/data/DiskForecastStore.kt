package com.weathercard.app.data

import android.util.AtomicFile
import com.weathercard.core.ForecastStore
import com.weathercard.core.Place
import com.weathercard.core.WeatherSnapshot
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.security.MessageDigest

class DiskForecastStore(private val directory: File) : ForecastStore {
    private val json = Json { ignoreUnknownKeys = true }
    private fun file(place: Place): AtomicFile {
        val key = MessageDigest.getInstance("SHA-256").digest(place.cacheKey.toByteArray())
            .joinToString("") { "%02x".format(it) }
        return AtomicFile(File(directory, "v1-$key.json"))
    }
    override suspend fun read(place: Place): WeatherSnapshot? = withContext(Dispatchers.IO) {
        try {
            val snapshot = file(place).openRead().bufferedReader().use { json.decodeFromString<WeatherSnapshot>(it.readText()) }
            snapshot.takeIf { it.place.cacheKey == place.cacheKey }
        } catch (_: java.io.IOException) { null }
        catch (_: IllegalArgumentException) { null }
    }
    override suspend fun write(snapshot: WeatherSnapshot): Unit = withContext(Dispatchers.IO) {
        check(directory.isDirectory || directory.mkdirs()) { "Önbellek klasörü açılamadı." }
        val target = file(snapshot.place)
        val stream = target.startWrite()
        try {
            stream.write(json.encodeToString(snapshot).toByteArray(Charsets.UTF_8))
            target.finishWrite(stream)
        } catch (error: Exception) {
            target.failWrite(stream)
            throw error
        }
    }
}

