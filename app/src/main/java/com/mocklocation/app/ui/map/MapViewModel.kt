package com.mocklocation.app.ui.map

import android.app.Application
import android.content.Intent
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.mocklocation.app.App
import com.mocklocation.app.data.db.entity.FavoriteLocation
import com.mocklocation.app.data.db.entity.LocationHistory
import com.mocklocation.app.service.MockLocationService
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
    val error: String? = null
)

class MapViewModel(application: Application) : AndroidViewModel(application) {

    private val app = application as App
    private val historyRepo = app.historyRepository
    private val favoriteRepo = app.favoriteRepository

    private val _uiState = MutableStateFlow(MapUiState())
    val uiState: StateFlow<MapUiState> = _uiState

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
                error = "启动模拟定位失败，请在手机「设置 → 开发者选项 → 模拟位置信息应用」中选择本应用"
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
}
