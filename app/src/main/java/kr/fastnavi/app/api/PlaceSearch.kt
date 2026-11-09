package kr.fastnavi.app.api

import android.content.Context
import android.location.Geocoder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kr.fastnavi.app.ApiException
import kr.fastnavi.app.Place
import java.net.URLEncoder
import java.util.Locale

/**
 * 장소 검색: 키가 없으면 폰에 내장된 주소/장소 검색(Geocoder) 사용.
 * (카카오/TMAP 키를 넣어두면 그쪽 장소 검색을 우선 사용)
 */
object PlaceSearch {

    suspend fun search(
        context: Context,
        query: String,
        kakaoKey: String,
        tmapKey: String,
        near: Place?,
    ): List<Place> = when {
        kakaoKey.isNotBlank() -> kakao(query, kakaoKey, near)
        tmapKey.isNotBlank() -> tmap(query, tmapKey)
        else -> geocoder(context, query)
    }

    @Suppress("DEPRECATION")
    private suspend fun geocoder(context: Context, query: String): List<Place> = withContext(Dispatchers.IO) {
        if (!Geocoder.isPresent()) throw ApiException("이 폰에는 내장 주소 검색이 없습니다")
        val list = try {
            Geocoder(context, Locale.KOREA).getFromLocationName(query, 10).orEmpty()
        } catch (e: Exception) {
            throw ApiException("주소 검색 실패 (인터넷 연결 확인): ${e.message ?: ""}")
        }
        list.map { a ->
            val line = a.getAddressLine(0).orEmpty().removePrefix("대한민국 ").trim()
            val feature = a.featureName.orEmpty()
            // 장소명이 번지수만이면 검색어를 이름으로
            val name = if (feature.isBlank() || feature.all { it.isDigit() || it == '-' }) query else feature
            Place(name = name, address = line, lat = a.latitude, lng = a.longitude)
        }.distinctBy { "%.5f,%.5f".format(it.lat, it.lng) }
    }

    private fun enc(s: String) = URLEncoder.encode(s, "UTF-8")

    private suspend fun kakao(query: String, key: String, near: Place?): List<Place> {
        val url = buildString {
            append("https://dapi.kakao.com/v2/local/search/keyword.json?size=15&query=${enc(query)}")
            if (near != null) append("&x=${near.lng}&y=${near.lat}") // 가까운 곳 우선 가중치
        }
        val json = Http.get(url, mapOf("Authorization" to "KakaoAK $key"))
        val docs = json.optJSONArray("documents") ?: return emptyList()
        return (0 until docs.length()).mapNotNull { i ->
            val d = docs.optJSONObject(i) ?: return@mapNotNull null
            val lng = d.optString("x").toDoubleOrNull() ?: return@mapNotNull null
            val lat = d.optString("y").toDoubleOrNull() ?: return@mapNotNull null
            Place(
                name = d.optString("place_name"),
                address = d.optString("road_address_name").ifBlank { d.optString("address_name") },
                lat = lat,
                lng = lng,
            )
        }
    }

    private suspend fun tmap(query: String, key: String): List<Place> {
        val url = "https://apis.openapi.sk.com/tmap/pois?version=1&count=15" +
            "&reqCoordType=WGS84GEO&resCoordType=WGS84GEO&searchKeyword=${enc(query)}"
        val json = Http.get(url, mapOf("appKey" to key))
        val arr = json.optJSONObject("searchPoiInfo")?.optJSONObject("pois")?.optJSONArray("poi")
            ?: return emptyList()
        return (0 until arr.length()).mapNotNull { i ->
            val p = arr.optJSONObject(i) ?: return@mapNotNull null
            // frontLat/Lon = 입구(차량 진입) 좌표, 없으면 중심 좌표
            val lat = p.optString("frontLat").toDoubleOrNull() ?: p.optString("noorLat").toDoubleOrNull()
            val lng = p.optString("frontLon").toDoubleOrNull() ?: p.optString("noorLon").toDoubleOrNull()
            if (lat == null || lng == null) return@mapNotNull null
            val addr = listOf("upperAddrName", "middleAddrName", "lowerAddrName", "detailAddrName")
                .map { p.optString(it) }.filter { it.isNotBlank() }.joinToString(" ")
            Place(name = p.optString("name"), address = addr, lat = lat, lng = lng)
        }
    }
}
