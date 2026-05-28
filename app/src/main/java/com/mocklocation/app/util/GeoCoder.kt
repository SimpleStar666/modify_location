package com.mocklocation.app.util

import android.content.Context
import com.google.gson.Gson
import com.google.gson.JsonObject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

data class GeoResult(
    val name: String,
    val address: String,
    val latitude: Double,
    val longitude: Double
)

class GeoCoder(private val context: Context) {

    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()
    private val gson = Gson()

    private val amapKey: String?
        get() = context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
            .getString("amap_api_key", null)

    suspend fun reverseGeocode(lat: Double, lng: Double): GeoResult? {
        return withContext(Dispatchers.IO) {
            tryAmapReverse(lat, lng)
                ?: tryNominatimReverse(lat, lng)
                ?: tryOverpassReverse(lat, lng)
        }
    }

    suspend fun search(query: String): GeoResult? {
        return withContext(Dispatchers.IO) {
            tryAmapSearch(query)
                ?: tryNominatimSearch(query)
                ?: tryOverpassSearch(query)
        }
    }

    private fun tryAmapReverse(lat: Double, lng: Double): GeoResult? {
        val key = amapKey ?: return null
        return try {
            val url = "https://restapi.amap.com/v3/geocode/regeo?key=$key&location=$lng,$lat&extensions=base&output=JSON"
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", "MockLocationApp/1.0")
                .build()
            val response = client.newCall(request).execute()
            if (!response.isSuccessful) return null
            val body = response.body?.string() ?: return null
            val json = gson.fromJson(body, JsonObject::class.java)
            val status = json.get("status")?.asString
            if (status != "1") return null
            val regeocode = json.getAsJsonObject("regeocode") ?: return null
            val formattedAddress = regeocode.get("formatted_address")?.asString ?: return null
            val addressComponent = regeocode.getAsJsonObject("addressComponent")
            val name = addressComponent?.get("township")?.asString
                ?: addressComponent?.get("district")?.asString
                ?: formattedAddress
            GeoResult(name, formattedAddress, lat, lng)
        } catch (_: Exception) {
            null
        }
    }

    private fun tryAmapSearch(query: String): GeoResult? {
        val key = amapKey ?: return null
        return try {
            val encoded = URLEncoder.encode(query, "UTF-8")
            val url = "https://restapi.amap.com/v3/place/text?key=$key&keywords=$encoded&offset=5&page=1&output=JSON"
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", "MockLocationApp/1.0")
                .build()
            val response = client.newCall(request).execute()
            if (!response.isSuccessful) return null
            val body = response.body?.string() ?: return null
            val json = gson.fromJson(body, JsonObject::class.java)
            val status = json.get("status")?.asString
            if (status != "1") return null
            val pois = json.getAsJsonArray("pois") ?: return null
            if (pois.size() == 0) return null
            val first = pois[0].asJsonObject
            val name = first.get("name")?.asString ?: return null
            val address = first.get("address")?.asString ?: name
            val location = first.get("location")?.asString ?: return null
            val parts = location.split(",")
            if (parts.size != 2) return null
            val lng = parts[0].toDoubleOrNull() ?: return null
            val lat = parts[1].toDoubleOrNull() ?: return null
            GeoResult(name, address, lat, lng)
        } catch (_: Exception) {
            null
        }
    }

    private fun tryNominatimReverse(lat: Double, lng: Double): GeoResult? {
        return try {
            val url = "https://nominatim.openstreetmap.org/reverse?format=json&lat=$lat&lon=$lng&zoom=18&addressdetails=1&accept-language=zh"
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", "MockLocationApp/1.0")
                .header("Accept-Language", "zh-CN,zh;q=0.9")
                .build()
            val response = client.newCall(request).execute()
            if (!response.isSuccessful) return null
            val body = response.body?.string() ?: return null
            val json = gson.fromJson(body, JsonObject::class.java)
            val displayName = json.get("display_name")?.asString ?: return null
            val name = json.get("name")?.asString
                ?: json.getAsJsonObject("address")?.get("road")?.asString
                ?: displayName
            GeoResult(name, displayName, lat, lng)
        } catch (_: Exception) {
            null
        }
    }

    private fun tryOverpassReverse(lat: Double, lng: Double): GeoResult? {
        return try {
            val query = "[out:json][timeout:10];(node(around:300,$lat,$lng)[\"name\"];way(around:300,$lat,$lng)[\"name\"];relation(around:300,$lat,$lng)[\"name\"];);out body 10;"
            val url = "https://overpass-api.de/api/interpreter?data=" + URLEncoder.encode(query, "UTF-8")
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", "MockLocationApp/1.0")
                .build()
            val response = client.newCall(request).execute()
            if (!response.isSuccessful) return null
            val body = response.body?.string() ?: return null
            val json = gson.fromJson(body, JsonObject::class.java)
            val elements = json.getAsJsonArray("elements") ?: return null
            if (elements.size() == 0) return null
            var closestName: String? = null
            var closestDist = Double.MAX_VALUE
            for (element in elements) {
                val obj = element.asJsonObject
                val tags = obj.getAsJsonObject("tags") ?: continue
                val name = tags.get("name")?.asString ?: continue
                val eLat = obj.get("lat")?.asDouble ?: continue
                val eLon = obj.get("lon")?.asDouble ?: continue
                val dist = kotlin.math.sqrt(kotlin.math.pow(eLat - lat, 2.0) + kotlin.math.pow(eLon - lng, 2.0))
                if (dist < closestDist) {
                    closestDist = dist
                    closestName = name
                }
            }
            if (closestName != null) GeoResult(closestName, closestName, lat, lng) else null
        } catch (_: Exception) {
            null
        }
    }

    private fun tryNominatimSearch(query: String): GeoResult? {
        return try {
            val encoded = URLEncoder.encode(query, "UTF-8")
            val url = "https://nominatim.openstreetmap.org/search?format=json&q=$encoded&limit=5&accept-language=zh"
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", "MockLocationApp/1.0")
                .header("Accept-Language", "zh-CN,zh;q=0.9")
                .build()
            val response = client.newCall(request).execute()
            if (!response.isSuccessful) return null
            val body = response.body?.string() ?: return null
            val results = gson.fromJson(body, com.google.gson.JsonArray::class.java)
            if (results.size() == 0) return null
            val first = results[0].asJsonObject
            val lat = first.get("lat").asString.toDouble()
            val lng = first.get("lon").asString.toDouble()
            val name = first.get("display_name")?.asString ?: ""
            GeoResult(name, name, lat, lng)
        } catch (_: Exception) {
            null
        }
    }

    private fun tryOverpassSearch(query: String): GeoResult? {
        return try {
            val overpassQuery = "[out:json][timeout:10];(node[\"name\"~\"$query\",i](18,73,54,136);way[\"name\"~\"$query\",i](18,73,54,136););out center 5;"
            val url = "https://overpass-api.de/api/interpreter?data=" + URLEncoder.encode(overpassQuery, "UTF-8")
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", "MockLocationApp/1.0")
                .build()
            val response = client.newCall(request).execute()
            if (!response.isSuccessful) return null
            val body = response.body?.string() ?: return null
            val json = gson.fromJson(body, JsonObject::class.java)
            val elements = json.getAsJsonArray("elements") ?: return null
            if (elements.size() == 0) return null
            val first = elements[0].asJsonObject
            val tags = first.getAsJsonObject("tags") ?: return null
            val name = tags.get("name")?.asString ?: return null
            val lat = first.get("lat")?.asDouble ?: first.getAsJsonObject("center")?.get("lat")?.asDouble ?: return null
            val lon = first.get("lon")?.asDouble ?: first.getAsJsonObject("center")?.get("lon")?.asDouble ?: return null
            GeoResult(name, name, lat, lon)
        } catch (_: Exception) {
            null
        }
    }
}
