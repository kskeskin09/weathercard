package com.weathercard.app.data

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import android.os.CancellationSignal
import android.os.SystemClock
import androidx.core.content.ContextCompat
import androidx.core.location.LocationManagerCompat
import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.GoogleApiAvailability
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import com.weathercard.core.LocationFix
import com.weathercard.core.LocationResolver
import com.weathercard.core.Place
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeoutOrNull
import java.io.IOException
import kotlin.coroutines.resume
import kotlin.math.round

class DeviceLocationProvider(private val context: Context, private val allowGoogleServices: Boolean = true) {
    private val manager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
    private fun permitted(permission: String) = ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED

    @SuppressLint("MissingPermission")
    suspend fun locate(): Place {
        val fine = permitted(Manifest.permission.ACCESS_FINE_LOCATION)
        if (!fine && !permitted(Manifest.permission.ACCESS_COARSE_LOCATION)) throw IOException("Konum izni verilmedi. Uygulama izinlerinden konuma erişimi açabilirsin.")
        if (!LocationManagerCompat.isLocationEnabled(manager)) throw IOException("Telefonun konumu kapalı. Konum ayarlarından açıp tekrar dene.")
        val enabled = manager.getProviders(true).filter {
            it == LocationManager.NETWORK_PROVIDER || (fine && it == LocationManager.GPS_PROVIDER)
        }
        val cached = enabled.mapNotNull { provider -> runCatching { manager.getLastKnownLocation(provider)?.asFix() }.getOrNull() }
            .filter { it.usable }.minByOrNull { it.ageMillis }
        if (cached != null) return cached.asPlace()

        val playServices = allowGoogleServices && GoogleApiAvailability.getInstance().isGooglePlayServicesAvailable(context) == ConnectionResult.SUCCESS
        if (playServices) {
            val last = try {
                withTimeoutOrNull(1500) { LocationServices.getFusedLocationProviderClient(context).lastLocation.await()?.asFix() }
            } catch (error: CancellationException) { throw error }
            catch (_: Exception) { null }
            if (last?.usable == true) return last.asPlace()
        }

        val providers = mutableListOf<suspend () -> LocationFix?>()
        if (playServices) providers += {
            val cancellation = CancellationTokenSource()
            try {
                LocationServices.getFusedLocationProviderClient(context)
                    .getCurrentLocation(if (fine) Priority.PRIORITY_HIGH_ACCURACY else Priority.PRIORITY_BALANCED_POWER_ACCURACY, cancellation.token)
                    .await()?.asFix()
            } finally { cancellation.cancel() }
        }
        enabled.forEach { provider -> providers += { nativeFix(provider)?.asFix() } }
        val result = LocationResolver(providers).find()
        return result?.asPlace() ?: throw IOException(
            if (fine) "Konum alınamadı. Açık alanda tekrar dene veya konum ayarlarını kontrol et. Şehir seçerek de devam edebilirsin."
            else "Yaklaşık konum alınamadı. Uygulama izninde kesin konuma izin vererek GPS'i deneyebilir veya şehir seçebilirsin."
        )
    }

    @SuppressLint("MissingPermission")
    private suspend fun nativeFix(provider: String): Location? = suspendCancellableCoroutine { continuation ->
        val signal = CancellationSignal()
        continuation.invokeOnCancellation { signal.cancel() }
        try {
            LocationManagerCompat.getCurrentLocation(manager, provider, signal, ContextCompat.getMainExecutor(context)) { location ->
                if (continuation.isActive) continuation.resume(location)
            }
        } catch (_: Exception) { if (continuation.isActive) continuation.resume(null) }
    }

    private fun Location.asFix() = LocationFix(
        latitude, longitude,
        (SystemClock.elapsedRealtimeNanos() - elapsedRealtimeNanos) / 1_000_000,
        if (hasAccuracy()) accuracy else 10_000f,
    )

    private suspend fun LocationFix.asPlace(): Place {
        val label = LocationNameResolver(context).resolve(latitude, longitude) ?: "Mevcut konum"
        return Place(label, round(latitude * 100) / 100, round(longitude * 100) / 100, isCurrentLocation = true)
    }
}

