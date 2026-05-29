package com.mocklocation.app.data.db.dao

import androidx.room.*
import com.mocklocation.app.data.db.entity.LocationHistory
import kotlinx.coroutines.flow.Flow

@Dao
interface HistoryDao {
    @Query("SELECT * FROM history ORDER BY usedAt DESC")
    fun getAll(): Flow<List<LocationHistory>>

    @Insert
    suspend fun insert(history: LocationHistory): Long

    @Query("DELETE FROM history WHERE latitude = :lat AND longitude = :lng")
    suspend fun deleteByLocation(lat: Double, lng: Double)

    @Query("DELETE FROM history WHERE id NOT IN (SELECT id FROM history ORDER BY usedAt DESC LIMIT 200)")
    suspend fun trimToLimit()

    @Query("DELETE FROM history")
    suspend fun deleteAll()

    @Delete
    suspend fun delete(history: LocationHistory)
}
