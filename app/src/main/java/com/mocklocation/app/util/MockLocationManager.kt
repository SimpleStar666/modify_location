package com.mocklocation.app.util

import android.content.Context
import android.location.Location
import android.location.LocationManager
import android.os.Build
import android.os.SystemClock

class MockLocationManager(context: Context) {

    private val locationManager =
        context.getSystemService(Context.LOCATION_SERVICE) as LocationManager

    private val providers = listOf(
        LocationManager.GPS_PROVIDER,
        LocationManager.NETWORK_PROVIDER,
        LocationManager.PASSIVE_PROVIDER
    )

    fun startMocking(latitude: Double, longitude: Double): Boolean {
        return try {
            for (provider in providers) {
                try {
                    locationManager.removeTestProvider(provider)
                } catch (_: Exception) {
                }
            }

            for (provider in providers) {
                try {
                    locationManager.addTestProvider(
                        provider,
                        false, false, false, false,
                        true, true, true, 0, 1
                    )
                    locationManager.setTestProviderEnabled(provider, true)
                } catch (_: Exception) {
                }
            }

            pushLocation(latitude, longitude)
            true
        } catch (e: SecurityException) {
            false
        } catch (e: Exception) {
            false
        }
    }

    fun pushLocation(latitude: Double, longitude: Double) {
        for (provider in providers) {
            try {
                val location = Location(provider).apply {
                    this.latitude = latitude
                    this.longitude = longitude
                    altitude = 0.0
                    accuracy = 3.0f
                    time = System.currentTimeMillis()
                    elapsedRealtimeNanos = SystemClock.elapsedRealtimeNanos()
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        bearingAccuracyDegrees = 0.0f
                        verticalAccuracyMeters = 3.0f
                        speedAccuracyMetersPerSecond = 0.0f
                    }
                    speed = 0.0f
                    bearing = 0.0f
                }
                locationManager.setTestProviderLocation(provider, location)
            } catch (_: Exception) {
            }
        }
    }

    fun stopMocking() {
        for (provider in providers) {
            try {
                locationManager.removeTestProvider(provider)
            } catch (_: Exception) {
            }
        }
    }
}
