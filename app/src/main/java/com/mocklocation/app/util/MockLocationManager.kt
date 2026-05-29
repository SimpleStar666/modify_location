package com.mocklocation.app.util

import android.content.Context
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Build
import android.os.SystemClock
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

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
                } catch (e: Exception) {
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
        val now = System.currentTimeMillis()
        val elapsedNanos = SystemClock.elapsedRealtimeNanos()

        for (provider in providers) {
            try {
                val location = Location(provider).apply {
                    this.latitude = latitude
                    this.longitude = longitude
                    altitude = 0.0
                    accuracy = 1.0f
                    time = now
                    elapsedRealtimeNanos = elapsedNanos
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        bearingAccuracyDegrees = 0.0f
                        verticalAccuracyMeters = 1.0f
                        speedAccuracyMetersPerSecond = 0.0f
                    }
                    speed = 0.0f
                    bearing = 0.0f
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        setMock(true)
                    }
                }
                locationManager.setTestProviderLocation(provider, location)
            } catch (_: Exception) {
            }
        }

        try {
            val fusedLocation = Location("fused").apply {
                this.latitude = latitude
                this.longitude = longitude
                altitude = 0.0
                accuracy = 1.0f
                time = now
                elapsedRealtimeNanos = elapsedNanos
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    bearingAccuracyDegrees = 0.0f
                    verticalAccuracyMeters = 1.0f
                    speedAccuracyMetersPerSecond = 0.0f
                }
                speed = 0.0f
                bearing = 0.0f
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    setMock(true)
                }
            }
            try {
                locationManager.addTestProvider(
                    "fused", false, false, false, false,
                    true, true, true, 0, 1
                )
                locationManager.setTestProviderEnabled("fused", true)
            } catch (_: Exception) {
            }
            try {
                locationManager.setTestProviderLocation("fused", fusedLocation)
            } catch (_: Exception) {
            }
        } catch (_: Exception) {
        }
    }

    fun stopMocking() {
        val allProviders = providers + "fused"
        for (provider in allProviders) {
            try {
                locationManager.removeTestProvider(provider)
            } catch (_: Exception) {
            }
        }
    }

    fun getCurrentLocation(): Location? {
        for (provider in providers) {
            try {
                val loc = locationManager.getLastKnownLocation(provider)
                if (loc != null) return loc
            } catch (_: Exception) {
            }
        }
        return null
    }

    fun isProviderMocked(provider: String): Boolean {
        return try {
            locationManager.getProvider(provider) != null &&
                locationManager.isProviderEnabled(provider)
        } catch (_: Exception) {
            false
        }
    }
}
