package com.mocklocation.app.ui.map

import android.app.Application
import android.content.Intent
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.mocklocation.app.App
import com.mocklocation.app.data.db.entity.LocationHistory
import com.mocklocation.app.service.MockLocationService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

data class MapUiState(
    val selectedLat: Double = 0.0,
    val selectedLng: Double = 0.0,
    val selectedName: String = "",
    val selectedAddress: String = "",
    val isMocking: Boolean = false,
    val mockingName: String = ""
)

class MapViewModel(application: Application) : AndroidViewModel(application) {

    private val app = application as App
    private val historyRepo = app.historyRepository

    private val _uiState = MutableStateFlow(MapUiState())
    val uiState: StateFlow<MapUiState> = _uiState

    fun onLocationSelected(lat: Double, lng: Double, name: String, address: String) {
        _uiState.value = _uiState.value.copy(
            selectedLat = lat,
            selectedLng = lng,
            selectedName = name,
            selectedAddress = address
        )
    }

    fun startMocking() {
        val state = _uiState.value
        val intent = Intent(getApplication(), MockLocationService::class.java).apply {
            putExtra(MockLocationService.EXTRA_LATITUDE, state.selectedLat)
            putExtra(MockLocationService.EXTRA_LONGITUDE, state.selectedLng)
            putExtra(MockLocationService.EXTRA_NAME, state.selectedName)
        }
        getApplication<Application>().startForegroundService(intent)

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
        getApplication<Application>().startService(intent)

        _uiState.value = _uiState.value.copy(
            isMocking = false,
            mockingName = ""
        )
    }
}
