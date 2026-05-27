package com.mocklocation.app.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.mocklocation.app.data.db.dao.FavoriteDao
import com.mocklocation.app.data.db.dao.HistoryDao
import com.mocklocation.app.data.db.entity.FavoriteLocation
import com.mocklocation.app.data.db.entity.LocationHistory

@Database(
    entities = [FavoriteLocation::class, LocationHistory::class],
    version = 1,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun favoriteDao(): FavoriteDao
    abstract fun historyDao(): HistoryDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "mock_location_db"
                ).build().also { INSTANCE = it }
            }
        }
    }
}
