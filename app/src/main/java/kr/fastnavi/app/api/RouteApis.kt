package kr.fastnavi.app.api

import kr.fastnavi.app.ApiException
import kr.fastnavi.app.Place
import kr.fastnavi.app.RouteInfo
import org.json.JSONObject

/**
 * TMAP 자동차 경로안내 API (SK open API)
 * POST https://apis.openapi.sk.com/tmap/routes?version=1  / header: appKey
 * features[0].properties 에 totalTime(초), totalDistance(m), totalFare(통행료), taxiFare
 */
object TmapRouteApi {
    suspend fun route(appKey: String, start: Place, goal: Place, avoidToll: Boolean): RouteInfo {
        val body = JSONObject().apply {
            put("startX", start.lng.toString())
            put("startY", start.lat.toString())
            put("endX", goal.lng.toString())
            put("endY", goal.lat.toString())
            put("reqCoordType", "WGS84GEO")
            put("resCoordType", "WGS84GEO")
            // 0: 교통최적+추천(기본), 1: 교통최적+무료우선
            put("searchOption", if (avoidToll) "1" else "0")
            put("trafficInfo", "N")
            put("totalValue", 2) // 2: 요약 정보만 (응답 경량화)
        }
        val json = Http.postJson(
            "https://apis.openapi.sk.com/tmap/routes?version=1",
            mapOf("appKey" to appKey),
            body,
        )
        val props = json.optJSONArray("features")?.optJSONObject(0)?.optJSONObject("properties")
            ?: throw ApiException("경로 결과 없음")
        return RouteInfo(
            durationSec = props.optInt("totalTime"),
            distanceM = props.optInt("totalDistance"),
            tollFare = props.optInt("totalFare"),
            taxiFare = props.optInt("taxiFare").takeIf { it > 0 },
        )
    }
}

/**
 * 네이버 클라우드 Maps Directions 5
 * GET https://maps.apigw.ntruss.com/map-direction/v1/driving?start=lng,lat&goal=lng,lat&option=...
 * route.<option>[0].summary 에 duration(ms), distance(m), tollFare, taxiFare, fuelPrice
 */
object NaverRouteApi {
    suspend fun route(
        clientId: String,
        clientSecret: String,
        start: Place,
        goal: Place,
        avoidToll: Boolean,
    ): RouteInfo {
        val option = if (avoidToll) "traavoidtoll" else "traoptimal"
        val url = "https://maps.apigw.ntruss.com/map-direction/v1/driving" +
            "?start=${start.lng},${start.lat}&goal=${goal.lng},${goal.lat}&option=$option"
        val json = Http.get(
            url,
            mapOf(
                "x-ncp-apigw-api-key-id" to clientId,
                "x-ncp-apigw-api-key" to clientSecret,
            ),
        )
        if (json.optInt("code", -1) != 0) {
            throw ApiException(json.optString("message").ifBlank { "경로 탐색 실패" })
        }
        val summary = json.optJSONObject("route")?.optJSONArray(option)
            ?.optJSONObject(0)?.optJSONObject("summary")
            ?: throw ApiException("경로 결과 없음")
        return RouteInfo(
            durationSec = (summary.optLong("duration") / 1000L).toInt(),
            distanceM = summary.optInt("distance"),
            tollFare = summary.optInt("tollFare"),
            taxiFare = summary.optInt("taxiFare").takeIf { it > 0 },
            fuelPrice = summary.optInt("fuelPrice").takeIf { it > 0 },
        )
    }
}

/**
 * 카카오모빌리티 자동차 길찾기
 * GET https://apis-navi.kakaomobility.com/v1/directions?origin=lng,lat&destination=lng,lat
 * header: Authorization: KakaoAK {REST API 키}
 * routes[0].summary 에 duration(초), distance(m), fare.toll, fare.taxi
 */
object KakaoRouteApi {
    suspend fun route(restKey: String, start: Place, goal: Place, avoidToll: Boolean): RouteInfo {
        val url = buildString {
            append("https://apis-navi.kakaomobility.com/v1/directions")
            append("?origin=${start.lng},${start.lat}")
            append("&destination=${goal.lng},${goal.lat}")
            append("&priority=RECOMMEND&summary=true")
            if (avoidToll) append("&avoid=toll")
        }
        val json = Http.get(url, mapOf("Authorization" to "KakaoAK $restKey"))
        val route = json.optJSONArray("routes")?.optJSONObject(0)
            ?: throw ApiException("경로 결과 없음")
        if (route.optInt("result_code", -1) != 0) {
            throw ApiException(route.optString("result_msg").ifBlank { "경로 탐색 실패" })
        }
        val summary = route.optJSONObject("summary") ?: throw ApiException("경로 요약 없음")
        val fare = summary.optJSONObject("fare")
        return RouteInfo(
            durationSec = summary.optInt("duration"),
            distanceM = summary.optInt("distance"),
            tollFare = fare?.optInt("toll") ?: 0,
            taxiFare = fare?.optInt("taxi")?.takeIf { it > 0 },
        )
    }
}
