package com.weathercard.app.data

import android.content.Context
import android.location.Address
import android.location.Geocoder
import android.os.Build
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import java.util.Locale
import java.util.concurrent.Executors
import kotlin.coroutines.resume

/** Android's optional reverse geocoder; failure must never prevent weather loading. */
class LocationNameResolver(private val context: Context) {
    @Suppress("DEPRECATION")
    suspend fun resolve(latitude: Double, longitude: Double): String? {
        if (!Geocoder.isPresent()) return null
        return try {
            withTimeoutOrNull(3500) {
                val geocoder = Geocoder(context, Locale.getDefault())
                val address = if (Build.VERSION.SDK_INT >= 33) {
                    suspendCancellableCoroutine<Address?> { continuation ->
                        geocoder.getFromLocation(latitude, longitude, 1, object : Geocoder.GeocodeListener {
                            override fun onGeocode(addresses: MutableList<Address>) {
                                if (continuation.isActive) continuation.resume(addresses.firstOrNull())
                            }
                            override fun onError(errorMessage: String?) {
                                if (continuation.isActive) continuation.resume(null)
                            }
                        })
                    }
                } else suspendCancellableCoroutine<Address?> { continuation ->
                    val task = legacyExecutor.submit {
                        val result = try { geocoder.getFromLocation(latitude, longitude, 1)?.firstOrNull() } catch (_: Exception) { null }
                        if (continuation.isActive) continuation.resume(result)
                    }
                    continuation.invokeOnCancellation { task.cancel(true) }
                }
                listOf(address?.subLocality, address?.locality, address?.subAdminArea, address?.adminArea)
                    .firstOrNull { !it.isNullOrBlank() }
            }
        } catch (error: CancellationException) { throw error }
        catch (_: Exception) { null }
    }
    private companion object {
        val legacyExecutor = Executors.newSingleThreadExecutor { runnable -> Thread(runnable, "weathercard-geocoder").apply { isDaemon = true } }
    }
}
