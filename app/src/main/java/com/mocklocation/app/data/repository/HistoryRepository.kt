package com.mocklocation.app.data.repository

import com.mocklocation.app.data.db.dao.HistoryDao
import com.mocklocation.app.data.db.entity.LocationHistory
import kotlinx.coroutines.flow.Flow

class HistoryRepository(private val dao: HistoryDao) {
    fun getAll(): Flow<List<LocationHistory>> = dao.getAll()

    suspend fun insert(history: LocationHistory): Long {
        val id = dao.insert(history)
        dao.trimToLimit()
        return id
    }

    suspend fun delete(history: LocationHistory) = dao.delete(history)

    suspend fun deleteAll() = dao.deleteAll()
}
