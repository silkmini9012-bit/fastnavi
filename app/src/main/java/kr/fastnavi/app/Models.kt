package kr.fastnavi.app

/** 좌표는 모두 WGS84 (lat = 위도, lng = 경도) */
data class Place(
    val name: String,
    val address: String,
    val lat: Double,
    val lng: Double,
    val isCurrentLocation: Boolean = false,
)

enum class NavApp(val label: String, val pkg: String, val color: Long) {
    TMAP("티맵", "com.skt.tmap.ku", 0xFF0064FF),
    NAVER("네이버지도", "com.nhn.android.nmap", 0xFF03C75A),
    KAKAO("카카오맵", "net.daum.android.map", 0xFFFFB800),
}

/** 한 앱의 한 경로(기본 or 무료도로) 탐색 결과 */
data class RouteInfo(
    val durationSec: Int,
    val distanceM: Int,
    val tollFare: Int,
    val taxiFare: Int? = null,
    val fuelPrice: Int? = null,
)

/** 앱별 비교 결과: 기본 경로 + (선택) 무료도로 경로 */
data class AppComparison(
    val app: NavApp,
    val installed: Boolean,
    val main: RouteInfo?,
    val mainError: String?,
    val free: RouteInfo?,
    val freeError: String?,
)

/** 가장 빠른 앱을 고르는 기준 */
enum class Criterion(val label: String) {
    DEFAULT("기본 경로(유료도로 포함)"),
    FREE("무료도로 경로"),
}

class ApiException(message: String) : Exception(message)
