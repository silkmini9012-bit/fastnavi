package kr.fastnavi.app.api

import kr.fastnavi.app.ApiException
import kr.fastnavi.app.Place
import java.net.URLEncoder

/** 장소 검색: 카카오 로컬 키워드 검색 우선, 카카오 키가 없으면 TMAP POI 검색 */
object PlaceSearch {

    suspend fun search(
        query: String,
        kakaoKey: String,
        tmapKey: String,
        near: Place?,
    ): List<Place> = when {
        kakaoKey.isNotBlank() -> kakao(query, kakaoKey, near)
        tmapKey.isNotBlank() -> tmap(query, tmapKey)
        else -> throw ApiException("장소 검색에는 카카오 REST 키 또는 TMAP 앱키가 필요합니다")
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
