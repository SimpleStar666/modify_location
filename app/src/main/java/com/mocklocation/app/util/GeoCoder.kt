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

    data class SearchResult(
        val result: GeoResult? = null,
        val error: String? = null
    )

    suspend fun reverseGeocode(lat: Double, lng: Double): SearchResult {
        return withContext(Dispatchers.IO) {
            tryAmapReverse(lat, lng)
                ?: tryNominatimReverse(lat, lng)
                ?: tryOverpassReverse(lat, lng)
                ?: SearchResult(error = "无法获取地名信息")
        }
    }

    suspend fun search(query: String): SearchResult {
        return withContext(Dispatchers.IO) {
            val key = amapKey
            if (key.isNullOrBlank()) {
                return@withContext SearchResult(error = "请先设置高德 Web 服务 API Key")
            }
            tryAmapSearch(query, key)
        }
    }

    private fun tryAmapReverse(lat: Double, lng: Double): SearchResult? {
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
            if (status != "1") {
                val info = json.get("info")?.asString ?: "未知错误"
                val infocode = json.get("infocode")?.asString ?: ""
                if (infocode == "10009") {
                    return SearchResult(error = "API Key 平台类型不匹配，请使用「Web服务」类型的 Key")
                }
                return null
            }
            val regeocode = json.getAsJsonObject("regeocode") ?: return null
            val formattedAddress = regeocode.get("formatted_address")?.asString ?: return null
            val addressComponent = regeocode.getAsJsonObject("addressComponent")
            val name = addressComponent?.get("township")?.asString
                ?: addressComponent?.get("district")?.asString
                ?: formattedAddress
            SearchResult(result = GeoResult(name, formattedAddress, lat, lng))
        } catch (_: Exception) {
            null
        }
    }

    private fun tryAmapSearch(query: String, key: String): SearchResult {
        return try {
            val encoded = URLEncoder.encode(query, "UTF-8")
            val url = "https://restapi.amap.com/v3/place/text?key=$key&keywords=$encoded&offset=5&page=1&output=JSON"
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", "MockLocationApp/1.0")
                .build()
            val response = client.newCall(request).execute()
            if (!response.isSuccessful) {
                return SearchResult(error = "网络请求失败 (HTTP ${response.code})")
            }
            val body = response.body?.string() ?: return SearchResult(error = "服务器返回空数据")
            val json = gson.fromJson(body, JsonObject::class.java)
            val status = json.get("status")?.asString
            val info = json.get("info")?.asString ?: "未知错误"
            val infocode = json.get("infocode")?.asString ?: ""

            if (status != "1") {
                val errorMsg = when (infocode) {
                    "10001" -> "API Key 不存在，请检查是否输入正确"
                    "10002" -> "API Key 没有权限，请检查是否开通了相关服务"
                    "10003" -> "API Key 调用次数超限"
                    "10004" -> "API Key 调用频率超限"
                    "10005" -> "API Key 绑定的 IP 不匹配"
                    "10006" -> "API Key 绑定的域名不匹配"
                    "10007" -> "API Key 安全码验证失败"
                    "10009" -> "API Key 平台类型不匹配！请删除当前 Key，重新创建一个「Web服务」类型的 Key（不需要填 SHA1 和包名）"
                    "10010" -> "API Key 不存在或已过期"
                    "10012" -> "API Key 权限不足"
                    "10013" -> "API Key 被禁用"
                    "10014" -> "API Key 对应的服务未开通"
                    else -> "高德 API 错误 ($infocode): $info"
                }
                return SearchResult(error = errorMsg)
            }

            val pois = json.getAsJsonArray("pois")
            if (pois == null || pois.size() == 0) {
                return SearchResult(error = "未找到「$query」，请尝试更具体的关键词")
            }

            val first = pois[0].asJsonObject
            val name = first.get("name")?.asString ?: return SearchResult(error = "解析结果失败")
            val address = first.get("address")?.asString ?: name
            val location = first.get("location")?.asString ?: return SearchResult(error = "解析位置失败")
            val parts = location.split(",")
            if (parts.size != 2) return SearchResult(error = "解析坐标失败")
            val lng = parts[0].toDoubleOrNull() ?: return SearchResult(error = "解析经度失败")
            val lat = parts[1].toDoubleOrNull() ?: return SearchResult(error = "解析纬度失败")
            SearchResult(result = GeoResult(name, address, lat, lng))
        } catch (e: java.net.SocketTimeoutException) {
            SearchResult(error = "网络超时，请检查网络连接")
        } catch (e: java.net.UnknownHostException) {
            SearchResult(error = "无法连接高德服务器，请检查网络")
        } catch (e: Exception) {
            SearchResult(error = "搜索出错: ${e.message}")
        }
    }

    private fun tryNominatimReverse(lat: Double, lng: Double): SearchResult? {
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
            SearchResult(result = GeoResult(name, displayName, lat, lng))
        } catch (_: Exception) {
            null
        }
    }

    private fun tryOverpassReverse(lat: Double, lng: Double): SearchResult? {
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
                val dist = Math.sqrt(Math.pow(eLat - lat, 2.0) + Math.pow(eLon - lng, 2.0))
                if (dist < closestDist) {
                    closestDist = dist
                    closestName = name
                }
            }
            if (closestName != null) SearchResult(result = GeoResult(closestName, closestName, lat, lng)) else null
        } catch (_: Exception) {
            null
        }
    }
}
