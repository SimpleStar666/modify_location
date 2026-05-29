package com.mocklocation.app.util

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import android.os.Build
import android.os.SystemClock
import androidx.core.content.ContextCompat

data class MockDiagnostic(
    val hasFineLocation: Boolean,
    val hasCoarseLocation: Boolean,
    val canAddTestProvider: Boolean,
    val testProviderRawError: String?,
    val androidVersion: Int
) {
    val isReady: Boolean get() = hasFineLocation && canAddTestProvider

    fun toDisplayText(): String {
        return buildString {
            append("精确位置权限: ${if (hasFineLocation) "✅已授予" else "❌未授予"}\n")
            append("大致位置权限: ${if (hasCoarseLocation) "✅已授予" else "❌未授予"}\n")
            append("模拟定位应用: ${if (canAddTestProvider) "✅已选择" else "❌未生效"}\n")
            append("Android版本: $androidVersion (API ${androidVersion})\n")
            if (testProviderRawError != null) {
                append("\n系统原始错误:\n$testProviderRawError")
            }
        }
    }

    fun getSolutionText(): String {
        return buildString {
            if (!hasFineLocation) {
                append("【必须】授予精确位置权限：\n")
                append("长按本应用图标 → 应用信息 → 权限 → 位置信息 → 选择「精确位置」\n\n")
            }
            if (!canAddTestProvider) {
                append("【必须】设置模拟定位应用：\n")
                append("设置 → 开发者选项 → 选择模拟位置信息应用 → 选择「模拟定位」\n")
                append("⚠️如果已经选择了本应用但仍不生效，请：\n")
                append("  1. 先切换选择其他应用\n")
                append("  2. 再重新选择本应用\n")
                append("  3. 完全关闭本应用后重新打开\n\n")
            }
        }
    }
}

data class MockStatus(
    val gpsMocked: Boolean = false,
    val networkMocked: Boolean = false,
    val passiveMocked: Boolean = false,
    val fusedMocked: Boolean = false,
    val gpsError: String? = null,
    val networkError: String? = null,
    val passiveError: String? = null,
    val fusedError: String? = null,
    val gpsRawError: String? = null,
    val networkRawError: String? = null
) {
    val anyMocked: Boolean get() = gpsMocked || networkMocked
}

private data class ProviderResult(
    val success: Boolean,
    val error: String?,
    val rawError: String?
)

class MockLocationManager(context: Context) {

    private val appContext = context.applicationContext
    private val locationManager =
        context.getSystemService(Context.LOCATION_SERVICE) as LocationManager

    private var mockStatus = MockStatus()

    fun getMockStatus(): MockStatus = mockStatus

    fun hasFineLocationPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            appContext, Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
    }

    fun hasCoarseLocationPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            appContext, Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
    }

    fun runDiagnostic(): MockDiagnostic {
        val hasFine = hasFineLocationPermission()
        val hasCoarse = hasCoarseLocationPermission()

        var canAdd = false
        var rawError: String? = null
        try {
            locationManager.addTestProvider(
                "__diag_check__", false, false, false, false,
                false, false, false, 0, 1
            )
            locationManager.removeTestProvider("__diag_check__")
            canAdd = true
        } catch (e: SecurityException) {
            rawError = e.message ?: e.toString()
        } catch (e: Exception) {
            rawError = "${e.javaClass.simpleName}: ${e.message}"
        }

        return MockDiagnostic(
            hasFineLocation = hasFine,
            hasCoarseLocation = hasCoarse,
            canAddTestProvider = canAdd,
            testProviderRawError = rawError,
            androidVersion = Build.VERSION.SDK_INT
        )
    }

    fun startMocking(latitude: Double, longitude: Double): MockStatus {
        if (!hasFineLocationPermission()) {
            return MockStatus(
                gpsError = "需要精确位置权限",
                networkError = "需要精确位置权限",
                passiveError = "需要精确位置权限",
                fusedError = "需要精确位置权限"
            )
        }

        val gpsResult = tryAddProvider(LocationManager.GPS_PROVIDER)
        val networkResult = tryAddProvider(LocationManager.NETWORK_PROVIDER)
        val passiveResult = tryAddProvider(LocationManager.PASSIVE_PROVIDER)
        val fusedResult = tryAddProvider("fused")

        mockStatus = MockStatus(
            gpsMocked = gpsResult.success,
            networkMocked = networkResult.success,
            passiveMocked = passiveResult.success,
            fusedMocked = fusedResult.success,
            gpsError = gpsResult.error,
            networkError = networkResult.error,
            passiveError = passiveResult.error,
            fusedError = fusedResult.error,
            gpsRawError = gpsResult.rawError,
            networkRawError = networkResult.rawError
        )

        if (gpsResult.success || networkResult.success) {
            pushLocation(latitude, longitude)
        }

        return mockStatus
    }

    private fun diagnoseSecurityException(e: SecurityException): String {
        val msg = e.message ?: ""
        return when {
            msg.contains("ACCESS_FINE_LOCATION", ignoreCase = true) ->
                "需要精确位置权限"
            msg.contains("ACCESS_MOCK_LOCATION", ignoreCase = true) ->
                "未选为模拟定位应用"
            else ->
                "未选为模拟定位应用"
        }
    }

    private fun tryAddProvider(provider: String): ProviderResult {
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
            ProviderResult(true, null, null)
        } catch (e: SecurityException) {
            ProviderResult(false, diagnoseSecurityException(e), e.message ?: e.toString())
        } catch (e: IllegalArgumentException) {
            ProviderResult(false, "不支持", e.message ?: e.toString())
        } catch (e: Exception) {
            ProviderResult(false, e.javaClass.simpleName, e.message ?: e.toString())
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
