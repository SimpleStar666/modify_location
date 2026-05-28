package com.mocklocation.app.util

import android.content.Context
import android.location.Location
import android.location.LocationManager
import android.os.Build
import android.os.SystemClock

class MockLocationManager(context: Context) {

    private val locationManager =
        context.getSystemService(Context.LOCATION_SERVICE) as LocationManager

    fun startMocking(latitude: Double, longitude: Double): Boolean {
        return try {
            try {
                locationManager.removeTestProvider(LocationManager.GPS_PROVIDER)
            } catch (_: Exception) {
            }

            locationManager.addTestProvider(
                LocationManager.GPS_PROVIDER,
                false, false, false, false,
                true, true, true, 0, 1
            )
            locationManager.setTestProviderEnabled(LocationManager.GPS_PROVIDER, true)
            pushLocation(latitude, longitude)
            true
        } catch (e: SecurityException) {
            false
        } catch (e: Exception) {
            false
        }
    }

    fun pushLocation(latitude: Double, longitude: Double) {
        try {
            val location = Location(LocationManager.GPS_PROVIDER).apply {
                this.latitude = latitude
                this.longitude = longitude
                altitude = 0.0
                accuracy = 5.0f
                time = System.currentTimeMillis()
                elapsedRealtimeNanos = SystemClock.elapsedRealtimeNanos()
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    bearingAccuracyDegrees = 0.0f
                    verticalAccuracyMeters = 5.0f
                    speedAccuracyMetersPerSecond = 0.0f
                }
                speed = 0.0f
                bearing = 0.0f
            }
            locationManager.setTestProviderLocation(LocationManager.GPS_PROVIDER, location)
        } catch (_: Exception) {
        }
    }

    fun stopMocking() {
        try {
            locationManager.removeTestProvider(LocationManager.GPS_PROVIDER)
        } catch (_: Exception) {
        }
    }
}
