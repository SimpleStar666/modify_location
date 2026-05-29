package com.mocklocation.app.util

import android.content.Context
import android.location.Location
import android.location.LocationManager
import android.os.Build
import android.os.SystemClock

data class MockStatus(
    val gpsMocked: Boolean = false,
    val networkMocked: Boolean = false,
    val passiveMocked: Boolean = false,
    val fusedMocked: Boolean = false,
    val gpsError: String? = null,
    val networkError: String? = null,
    val passiveError: String? = null,
    val fusedError: String? = null,
    val currentGpsLocation: Location? = null,
    val currentNetworkLocation: Location? = null
) {
    val anyMocked: Boolean get() = gpsMocked || networkMocked
    val summary: String
        get() {
            val parts = mutableListOf<String>()
            parts.add("GPS: ${if (gpsMocked) "✅" else "❌${gpsError?.let { "($it)" } ?: ""}"}")
            parts.add("Network: ${if (networkMocked) "✅" else "❌${networkError?.let { "($it)" } ?: ""}"}")
            return parts.joinToString("  ")
        }
}

class MockLocationManager(context: Context) {

    private val locationManager =
        context.getSystemService(Context.LOCATION_SERVICE) as LocationManager

    private var mockStatus = MockStatus()

    fun getMockStatus(): MockStatus = mockStatus

    fun startMocking(latitude: Double, longitude: Double): MockStatus {
        val status = MockStatus()

        val gpsResult = tryAddProvider(LocationManager.GPS_PROVIDER)
        val networkResult = tryAddProvider(LocationManager.NETWORK_PROVIDER)
        val passiveResult = tryAddProvider(LocationManager.PASSIVE_PROVIDER)
        var fusedResult: Pair<Boolean, String?> = Pair(false, null)
        try {
            locationManager.addTestProvider(
                "fused", false, false, false, false,
                true, true, true, 0, 1
            )
            locationManager.setTestProviderEnabled("fused", true)
            fusedResult = Pair(true, null)
        } catch (e: SecurityException) {
            fusedResult = Pair(false, "无权限")
        } catch (e: Exception) {
            fusedResult = Pair(false, e.javaClass.simpleName)
        }

        mockStatus = status.copy(
            gpsMocked = gpsResult.first,
            networkMocked = networkResult.first,
            passiveMocked = passiveResult.first,
            fusedMocked = fusedResult.first,
            gpsError = gpsResult.second,
            networkError = networkResult.second,
            passiveError = passiveResult.second,
            fusedError = fusedResult.second
        )

        if (gpsResult.first || networkResult.first) {
            pushLocation(latitude, longitude)
        }

        return mockStatus
    }

    private fun tryAddProvider(provider: String): Pair<Boolean, String?> {
        return try {
            try {
                locationManager.removeTestProvider(provider)
            } catch (_: Exception) {
            }
            locationManager.addTestProvider(
                provider,
                false, false, false, false,
                true, true, true, 0, 1
            )
            locationManager.setTestProviderEnabled(provider, true)
            Pair(true, null)
        } catch (e: SecurityException) {
            Pair(false, "未设为模拟定位应用")
        } catch (e: IllegalArgumentException) {
            Pair(false, "不支持")
        } catch (e: Exception) {
            Pair(false, e.javaClass.simpleName)
        }
    }

    fun pushLocation(latitude: Double, longitude: Double) {
        val now = System.currentTimeMillis()
        val elapsedNanos = SystemClock.elapsedRealtimeNanos()

        val providers = mutableListOf<String>()
        if (mockStatus.gpsMocked) providers.add(LocationManager.GPS_PROVIDER)
        if (mockStatus.networkMocked) providers.add(LocationManager.NETWORK_PROVIDER)
        if (mockStatus.passiveMocked) providers.add(LocationManager.PASSIVE_PROVIDER)
        if (mockStatus.fusedMocked) providers.add("fused")

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
                }
                locationManager.setTestProviderLocation(provider, location)
            } catch (_: Exception) {
            }
        }
    }

    fun stopMocking() {
        val allProviders = listOf(
            LocationManager.GPS_PROVIDER,
            LocationManager.NETWORK_PROVIDER,
            LocationManager.PASSIVE_PROVIDER,
            "fused"
        )
        for (provider in allProviders) {
            try {
                locationManager.removeTestProvider(provider)
            } catch (_: Exception) {
            }
        }
        mockStatus = MockStatus()
    }

    fun readGpsLocation(): Location? {
        return try {
            locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER)
        } catch (_: Exception) {
            null
        }
    }

    fun readNetworkLocation(): Location? {
        return try {
            locationManager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER)
        } catch (_: Exception) {
            null
        }
    }
}
