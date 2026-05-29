package com.mocklocation.app.ui.map

import android.app.Application
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.mocklocation.app.App
import com.mocklocation.app.data.db.entity.FavoriteLocation
import com.mocklocation.app.data.db.entity.LocationHistory
import com.mocklocation.app.service.MockLocationService
import com.mocklocation.app.util.MockDiagnostic
import com.mocklocation.app.util.MockLocationManager
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

data class MapUiState(
    val selectedLat: Double = 0.0,
    val selectedLng: Double = 0.0,
    val gcjLat: Double = 0.0,
    val gcjLng: Double = 0.0,
    val selectedName: String = "",
    val selectedAddress: String = "",
    val isMocking: Boolean = false,
    val mockingName: String = "",
    val gpsMocked: Boolean = false,
    val networkMocked: Boolean = false,
    val gpsError: String? = null,
    val networkError: String? = null,
    val gpsRawError: String? = null,
    val networkRawError: String? = null,
    val verifyGpsLat: Double = 0.0,
    val verifyGpsLng: Double = 0.0,
    val verifyNetworkLat: Double = 0.0,
    val verifyNetworkLng: Double = 0.0,
    val diagnostic: MockDiagnostic? = null,
    val error: String? = null
)

class MapViewModel(application: Application) : AndroidViewModel(application) {

    private val app = application as App
    private val historyRepo = app.historyRepository
    private val favoriteRepo = app.favoriteRepository
    private val mockLocationManager = MockLocationManager(application)

    private val _uiState = MutableStateFlow(MapUiState())
    val uiState: StateFlow<MapUiState> = _uiState

    private val statusReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == MockLocationService.ACTION_MOCK_STATUS) {
                val gpsMocked = intent.getBooleanExtra(MockLocationService.EXTRA_GPS_MOCKED, false)
                val networkMocked = intent.getBooleanExtra(MockLocationService.EXTRA_NETWORK_MOCKED, false)
                val gpsError = intent.getStringExtra(MockLocationService.EXTRA_GPS_ERROR)
                val networkError = intent.getStringExtra(MockLocationService.EXTRA_NETWORK_ERROR)
                val gpsRawError = intent.getStringExtra(MockLocationService.EXTRA_GPS_RAW_ERROR)
                val networkRawError = intent.getStringExtra(MockLocationService.EXTRA_NETWORK_RAW_ERROR)

                _uiState.value = _uiState.value.copy(
                    isMocking = gpsMocked || networkMocked,
                    gpsMocked = gpsMocked,
                    networkMocked = networkMocked,
                    gpsError = if (gpsError.isNullOrEmpty()) null else gpsError,
                    networkError = if (networkError.isNullOrEmpty()) null else networkError,
                    gpsRawError = if (gpsRawError.isNullOrEmpty()) null else gpsRawError,
                    networkRawError = if (networkRawError.isNullOrEmpty()) null else networkRawError
                )

                if (!gpsMocked && !networkMocked) {
                    val gpsErr = gpsError ?: ""
                    val netErr = networkError ?: ""
                    val rawGps = gpsRawError ?: ""
                    val rawNet = networkRawError ?: ""
                    val hasRawInfo = rawGps.isNotBlank() || rawNet.isNotBlank()
                    val errorMsg = when {
                        gpsErr.contains("位置权限") || netErr.contains("位置权限") ->
                            "模拟定位需要「精确位置」权限！\n请长按本应用图标 → 应用信息 → 权限 → 位置信息 → 选择「精确位置」"
                        gpsErr.contains("未选为模拟") || netErr.contains("未选为模拟") -> {
                            buildString {
                                append("模拟定位未生效！请在开发者选项中重新选择本应用。\n\n")
                                if (hasRawInfo) {
                                    append("系统错误详情：\n")
                                    if (rawGps.isNotBlank()) append("GPS: $rawGps\n")
                                    if (rawNet.isNotBlank()) append("网络: $rawNet\n")
                                    append("\n")
                                }
                                append("解决方法：\n")
                                append("1. 设置 → 开发者选项 → 选择模拟位置信息应用\n")
                                append("2. 先选择其他应用，再重新选择本应用\n")
                                append("3. 完全关闭本应用后重新打开")
                            }
                        }
                        else -> {
                            buildString {
                                append("模拟定位启动失败！\n\n")
                                if (hasRawInfo) {
                                    append("系统错误详情：\n")
                                    if (rawGps.isNotBlank()) append("GPS: $rawGps\n")
                                    if (rawNet.isNotBlank()) append("网络: $rawNet\n")
                                    append("\n")
                                }
                                append("请检查：\n")
                                append("1. 已授予「精确位置」权限\n")
                                append("2. 已在开发者选项中选择本应用为模拟定位应用")
                            }
                        }
                    }
                    _uiState.value = _uiState.value.copy(
                        isMocking = false,
                        error = errorMsg
                    )
                }
            }
        }
    }

    init {
        try {
            val filter = IntentFilter(MockLocationService.ACTION_MOCK_STATUS)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                application.registerReceiver(statusReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
            } else {
                application.registerReceiver(statusReceiver, filter)
            }
        } catch (_: Exception) {
        }

        viewModelScope.launch {
            while (true) {
                if (MockLocationService.isRunning) {
                    val gpsLoc = mockLocationManager.readGpsLocation()
                    val networkLoc = mockLocationManager.readNetworkLocation()
                    _uiState.value = _uiState.value.copy(
                        verifyGpsLat = gpsLoc?.latitude ?: 0.0,
                        verifyGpsLng = gpsLoc?.longitude ?: 0.0,
                        verifyNetworkLat = networkLoc?.latitude ?: 0.0,
                        verifyNetworkLng = networkLoc?.longitude ?: 0.0
                    )
                }
                delay(2000)
            }
        }
    }

    fun runDiagnostic(): MockDiagnostic {
        val diagnostic = mockLocationManager.runDiagnostic()
        _uiState.value = _uiState.value.copy(diagnostic = diagnostic)
        return diagnostic
    }

    fun onLocationSelected(
        wgsLat: Double, wgsLng: Double,
        gcjLat: Double, gcjLng: Double,
        name: String, address: String
    ) {
        _uiState.value = _uiState.value.copy(
            selectedLat = wgsLat,
            selectedLng = wgsLng,
            gcjLat = gcjLat,
            gcjLng = gcjLng,
            selectedName = name,
            selectedAddress = address
        )
    }

    fun startMocking() {
        val state = _uiState.value
        try {
            val intent = Intent(getApplication(), MockLocationService::class.java).apply {
                putExtra(MockLocationService.EXTRA_LATITUDE, state.selectedLat)
                putExtra(MockLocationService.EXTRA_LONGITUDE, state.selectedLng)
                putExtra(MockLocationService.EXTRA_NAME, state.selectedName)
            }
            getApplication<Application>().startForegroundService(intent)
        } catch (e: Exception) {
            _uiState.value = _uiState.value.copy(
                error = "启动模拟定位失败: ${e.message}"
            )
            return
        }

        _uiState.value = _uiState.value.copy(
            isMocking = true,
            mockingName = state.selectedName
        )

        viewModelScope.launch {
            historyRepo.insert(
                LocationHistory(
                    name = state.selectedName,
                    address = state.selectedAddress,
                    latitude = state.selectedLat,
                    longitude = state.selectedLng
                )
            )
        }
    }

    fun stopMocking() {
        val intent = Intent(getApplication(), MockLocationService::class.java).apply {
            action = MockLocationService.ACTION_STOP
        }
        try {
            getApplication<Application>().startService(intent)
        } catch (_: Exception) {
        }

        _uiState.value = _uiState.value.copy(
            isMocking = false,
            mockingName = "",
            gpsMocked = false,
            networkMocked = false,
            gpsError = null,
            networkError = null,
            gpsRawError = null,
            networkRawError = null,
            verifyGpsLat = 0.0,
            verifyGpsLng = 0.0,
            verifyNetworkLat = 0.0,
            verifyNetworkLng = 0.0
        )
    }

    fun addFavorite() {
        val state = _uiState.value
        if (state.selectedLat == 0.0 && state.selectedLng == 0.0) return
        viewModelScope.launch {
            if (favoriteRepo.count() >= 50) {
                _uiState.value = _uiState.value.copy(error = "收藏已达上限（50条）")
                return@launch
            }
            favoriteRepo.insert(
                FavoriteLocation(
                    name = state.selectedName,
                    address = state.selectedAddress,
                    latitude = state.selectedLat,
                    longitude = state.selectedLng
                )
            )
            _uiState.value = _uiState.value.copy(error = "已收藏")
        }
    }

    fun clearError() {
        _uiState.value = _uiState.value.copy(error = null)
    }

    override fun onCleared() {
        super.onCleared()
        try {
            getApplication<Application>().unregisterReceiver(statusReceiver)
        } catch (_: Exception) {
        }
    }
}
