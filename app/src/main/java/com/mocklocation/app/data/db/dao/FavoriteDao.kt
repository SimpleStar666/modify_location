package com.mocklocation.app.data.db.dao

import androidx.room.*
import com.mocklocation.app.data.db.entity.FavoriteLocation
import kotlinx.coroutines.flow.Flow

@Dao
interface FavoriteDao {
    @Query("SELECT * FROM favorites ORDER BY createdAt DESC")
    fun getAll(): Flow<List<FavoriteLocation>>

    @Query("SELECT COUNT(*) FROM favorites")
    suspend fun count(): Int

    @Insert
    suspend fun insert(location: FavoriteLocation): Long

    @Update
    suspend fun update(location: FavoriteLocation)

    @Delete
    suspend fun delete(location: FavoriteLocation)
}
