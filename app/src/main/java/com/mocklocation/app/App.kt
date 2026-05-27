package com.mocklocation.app

import android.app.Application
import com.mocklocation.app.data.db.AppDatabase
import com.mocklocation.app.data.repository.FavoriteRepository
import com.mocklocation.app.data.repository.HistoryRepository

class App : Application() {

    val database by lazy { AppDatabase.getInstance(this) }
    val favoriteRepository by lazy { FavoriteRepository(database.favoriteDao()) }
    val historyRepository by lazy { HistoryRepository(database.historyDao()) }
}
