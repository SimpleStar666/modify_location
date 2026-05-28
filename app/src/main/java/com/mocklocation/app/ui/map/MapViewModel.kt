package com.mocklocation.app.ui.map

import android.app.Application
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.IntentFilter.MalformedMimeTypeException
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.mocklocation.app.App
import com.mocklocation.app.data.db.entity.LocationHistory
import com.mocklocation.app.service.MockLocationService
import com.mocklocation.app.util.MockLocationManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

data class MapUiState(
    val selectedLat: Double = 0.0,
    val selectedLng: Double = 0.0,
    val selectedName: String = "",
    val selectedAddress: String = "",
    val isMocking: Boolean = false,
    val mockingName: String = "",
    val error: String? = null
)

class MapViewModel(application: Application) : AndroidViewModel(application) {

    private val app = application as App
    private val historyRepo = app.historyRepository
    private val mockLocationManager = MockLocationManager(application)

    private val _uiState = MutableStateFlow(MapUiState())
    val uiState: StateFlow<MapUiState> = _uiState

    private val mockFailedReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == MockLocationService.ACTION_MOCK_FAILED) {
                _uiState.value = _uiState.value.copy(
                    isMocking = false,
                    mockingName = "",
                    error = "请在手机「开发者选项」中选择「模拟位置信息应用」为本应用"
                )
            }
        }
    }

    init {
        try {
            val filter = IntentFilter(MockLocationService.ACTION_MOCK_FAILED)
            application.registerReceiver(mockFailedReceiver, filter)
        } catch (_: Exception) {
        }
    }

    fun onLocationSelected(lat: Double, lng: Double, name: String, address: String) {
        _uiState.value = _uiState.value.copy(
            selectedLat = lat,
            selectedLng = lng,
            selectedName = name,
            selectedAddress = address
        )
    }

    fun startMocking() {
        if (!mockLocationManager.isMockLocationEnabled()) {
            _uiState.value = _uiState.value.copy(
                error = "请在手机「设置 → 开发者选项 → 模拟位置信息应用」中选择本应用"
            )
            return
        }

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
            mockingName = ""
        )
    }

    fun clearError() {
        _uiState.value = _uiState.value.copy(error = null)
    }

    override fun onCleared() {
        super.onCleared()
        try {
            getApplication<Application>().unregisterReceiver(mockFailedReceiver)
        } catch (_: Exception) {
        }
    }
}
