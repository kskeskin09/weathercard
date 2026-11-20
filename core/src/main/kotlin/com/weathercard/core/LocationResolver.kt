package com.weathercard.core

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

data class LocationFix(val latitude: Double, val longitude: Double, val ageMillis: Long, val accuracyMeters: Float) {
    val usable: Boolean get() = latitude.isFinite() && longitude.isFinite() && latitude in -90.0..90.0 &&
        longitude in -180.0..180.0 && ageMillis in 0..900_000 && accuracyMeters.isFinite() && accuracyMeters in 0f..10_000f
}

/** A null/failing provider must not hide a usable result from another provider. */
class LocationResolver(
    private val providers: List<suspend () -> LocationFix?>,
    private val timeoutMillis: Long = 20_000,
) {
    suspend fun find(): LocationFix? = withTimeoutOrNull(timeoutMillis) {
        coroutineScope {
            val results = Channel<LocationFix?>(Channel.UNLIMITED)
            val jobs = providers.map { provider ->
                launch {
                    val fix = try { provider() }
                    catch (error: CancellationException) { throw error }
                    catch (_: Exception) { null }
                    results.send(fix)
                }
            }
            try {
                repeat(jobs.size) {
                    val result = results.receive()
                    if (result?.usable == true) return@coroutineScope result
                }
                null
            } finally {
                jobs.forEach { it.cancel() }
                results.cancel()
            }
        }
    }
}

