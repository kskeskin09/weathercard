package com.weathercard.app

import android.annotation.SuppressLint
import android.content.Context
import android.location.Criteria
import android.location.Location
import android.location.LocationManager
import android.os.SystemClock
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import com.weathercard.app.data.DeviceLocationProvider
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class NativeLocationTest {
    @SuppressLint("MissingPermission")
    @Suppress("DEPRECATION")
    @Test fun platformGpsCanResolveLocationWithoutGoogleProvider() = runBlocking {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        fun shell(command: String) {
            val descriptor = instrumentation.uiAutomation.executeShellCommand(command)
            android.os.ParcelFileDescriptor.AutoCloseInputStream(descriptor).bufferedReader().use { it.readText() }
        }
        shell("pm grant ${context.packageName} android.permission.ACCESS_COARSE_LOCATION")
        shell("pm grant ${context.packageName} android.permission.ACCESS_FINE_LOCATION")
        shell("appops set ${context.packageName} android:mock_location allow")
        shell("cmd location set-location-enabled true")
        // Production requests a while-in-use permission: the test must also be foreground.
        ActivityScenario.launch(MainActivity::class.java).use {
            instrumentation.waitForIdleSync()
            val manager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
            manager.addTestProvider(LocationManager.GPS_PROVIDER, false, false, false, false, false, false, false, Criteria.POWER_LOW, Criteria.ACCURACY_FINE)
            try {
                manager.setTestProviderEnabled(LocationManager.GPS_PROVIDER, true)
                manager.setTestProviderLocation(LocationManager.GPS_PROVIDER, Location(LocationManager.GPS_PROVIDER).apply {
                    latitude = 41.01
                    longitude = 28.98
                    accuracy = 20f
                    time = System.currentTimeMillis()
                    elapsedRealtimeNanos = SystemClock.elapsedRealtimeNanos()
                })
                val place = DeviceLocationProvider(context, allowGoogleServices = false).locate()
                assertEquals(41.01, place.latitude, 0.001)
                assertEquals(28.98, place.longitude, 0.001)
            } finally {
                manager.removeTestProvider(LocationManager.GPS_PROVIDER)
                shell("appops set ${context.packageName} android:mock_location default")
            }
        }
    }
}
