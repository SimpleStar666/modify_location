package com.mocklocation.app.ui.favorite

import android.app.Application
import android.content.Intent
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.mocklocation.app.App
import com.mocklocation.app.data.db.entity.FavoriteLocation
import com.mocklocation.app.data.db.entity.LocationHistory
import com.mocklocation.app.service.MockLocationService
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class FavoriteViewModel(application: Application) : AndroidViewModel(application) {

    private val app = application as App
    private val favoriteRepo = app.favoriteRepository
    private val historyRepo = app.historyRepository

    val favorites = favoriteRepo.getAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun startMocking(location: FavoriteLocation) {
        try {
            val intent = Intent(getApplication(), MockLocationService::class.java).apply {
                putExtra(MockLocationService.EXTRA_LATITUDE, location.latitude)
                putExtra(MockLocationService.EXTRA_LONGITUDE, location.longitude)
                putExtra(MockLocationService.EXTRA_NAME, location.name)
            }
            getApplication<Application>().startForegroundService(intent)
        } catch (_: Exception) {
            return
        }

        viewModelScope.launch {
            historyRepo.insert(
                LocationHistory(
                    name = location.name,
                    address = location.address,
                    latitude = location.latitude,
                    longitude = location.longitude
                )
            )
        }
    }

    fun addFavorite(name: String, address: String, latitude: Double, longitude: Double) {
        viewModelScope.launch {
            if (favoriteRepo.count() >= 50) return@launch
            favoriteRepo.insert(
                FavoriteLocation(
                    name = name,
                    address = address,
                    latitude = latitude,
                    longitude = longitude
                )
            )
        }
    }

    fun updateFavorite(location: FavoriteLocation) {
        viewModelScope.launch {
            favoriteRepo.update(location)
        }
    }

    fun deleteFavorite(location: FavoriteLocation) {
        viewModelScope.launch {
            favoriteRepo.delete(location)
        }
    }
}
