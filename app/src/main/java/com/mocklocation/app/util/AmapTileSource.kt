package com.mocklocation.app.util

import org.osmdroid.tileprovider.tilesource.OnlineTileSourceBase
import org.osmdroid.tileprovider.tilesource.TileSourcePolicy
import org.osmdroid.util.MapTileIndex

class AmapTileSource : OnlineTileSourceBase(
    "Amap",
    0,
    19,
    512,
    ".png",
    arrayOf(
        "https://webrd01.is.autonavi.com/appmaptile",
        "https://webrd02.is.autonavi.com/appmaptile",
        "https://webrd03.is.autonavi.com/appmaptile",
        "https://webrd04.is.autonavi.com/appmaptile"
    ),
    "\u00a9 \u9ad8\u5fb7\u5730\u56fe",
    TileSourcePolicy(
        4,
        TileSourcePolicy.FLAG_NO_BULK or TileSourcePolicy.FLAG_NO_PREVENTIVE
    )
) {
    override fun getTileURLString(pMapTileIndex: Long): String {
        val zoom = MapTileIndex.getZoom(pMapTileIndex)
        val x = MapTileIndex.getX(pMapTileIndex)
        val y = MapTileIndex.getY(pMapTileIndex)
        return getBaseUrl() + "?lang=zh_cn&size=1&scale=2&style=8&x=$x&y=$y&z=$zoom"
    }
}
