package com.mocklocation.app.ui.history

import android.app.Application
import android.content.Intent
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.mocklocation.app.App
import com.mocklocation.app.data.db.entity.LocationHistory
import com.mocklocation.app.service.MockLocationService
import com.mocklocation.app.util.MockLocationManager
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class HistoryViewModel(application: Application) : AndroidViewModel(application) {

    private val app = application as App
    private val historyRepo = app.historyRepository
    private val mockLocationManager = MockLocationManager(application)

    val history = historyRepo.getAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun startMocking(item: LocationHistory) {
        if (!mockLocationManager.isMockLocationEnabled()) {
            return
        }
        try {
            val intent = Intent(getApplication(), MockLocationService::class.java).apply {
                putExtra(MockLocationService.EXTRA_LATITUDE, item.latitude)
                putExtra(MockLocationService.EXTRA_LONGITUDE, item.longitude)
                putExtra(MockLocationService.EXTRA_NAME, item.name)
            }
            getApplication<Application>().startForegroundService(intent)
        } catch (_: Exception) {
            return
        }
    }

    fun delete(item: LocationHistory) {
        viewModelScope.launch {
            historyRepo.delete(item)
        }
    }

    fun clearAll() {
        viewModelScope.launch {
            historyRepo.deleteAll()
        }
    }
}
