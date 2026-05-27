package com.mocklocation.app.ui.common

import com.mocklocation.app.data.db.entity.FavoriteLocation
import com.mocklocation.app.data.db.entity.LocationHistory

data class LocationItem(
    val id: Long,
    val name: String,
    val address: String,
    val latitude: Double,
    val longitude: Double
)

fun FavoriteLocation.toItem() = LocationItem(id, name, address, latitude, longitude)

fun LocationHistory.toItem() = LocationItem(id, name, address, latitude, longitude)
