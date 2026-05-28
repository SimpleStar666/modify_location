package com.mocklocation.app

import android.app.Application
import com.mocklocation.app.data.db.AppDatabase
import com.mocklocation.app.data.repository.FavoriteRepository
import com.mocklocation.app.data.repository.HistoryRepository
import org.osmdroid.config.Configuration

class App : Application() {

    val database by lazy { AppDatabase.getInstance(this) }
    val favoriteRepository by lazy { FavoriteRepository(database.favoriteDao()) }
    val historyRepository by lazy { HistoryRepository(database.historyDao()) }

    override fun onCreate() {
        super.onCreate()
        val prefs = getSharedPreferences("osmdroid", MODE_PRIVATE)
        Configuration.getInstance().load(this, prefs)
        Configuration.getInstance().userAgentValue = "MockLocationApp/1.0"
        Configuration.getInstance().osmdroidTileCache = getFileStreamPath("osmdroid_tiles")
    }
}
