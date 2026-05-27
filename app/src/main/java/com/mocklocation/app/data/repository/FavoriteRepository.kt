package com.mocklocation.app.data.repository

import com.mocklocation.app.data.db.dao.FavoriteDao
import com.mocklocation.app.data.db.entity.FavoriteLocation
import kotlinx.coroutines.flow.Flow

class FavoriteRepository(private val dao: FavoriteDao) {
    fun getAll(): Flow<List<FavoriteLocation>> = dao.getAll()

    suspend fun count(): Int = dao.count()

    suspend fun insert(location: FavoriteLocation): Long = dao.insert(location)

    suspend fun update(location: FavoriteLocation) = dao.update(location)

    suspend fun delete(location: FavoriteLocation) = dao.delete(location)
}
