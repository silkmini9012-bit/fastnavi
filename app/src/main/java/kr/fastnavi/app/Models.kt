package kr.fastnavi.app

/** 좌표는 모두 WGS84 (lat = 위도, lng = 경도) */
data class Place(
    val name: String,
    val address: String,
    val lat: Double,
    val lng: Double,
    val isCurrentLocation: Boolean = false,
    val pinned: Boolean = false,
)

/** withRo: 받침에 맞춘 조사 (티맵으로 / 네이버지도로 / 카카오맵으로) */
enum class NavApp(val label: String, val withRo: String, val pkg: String, val color: Long) {
    TMAP("티맵", "티맵으로", "com.skt.tmap.ku", 0xFF0064FF),
    NAVER("네이버지도", "네이버지도로", "com.nhn.android.nmap", 0xFF03C75A),
    KAKAO("카카오맵", "카카오맵으로", "net.daum.android.map", 0xFFFFB800),
}

/** 한 경로의 정보. tollFare == null 이면 통행료 정보를 못 찾은 것 */
data class RouteInfo(
    val durationSec: Int,
    val distanceM: Int?,
    val tollFare: Int?,
    val taxiFare: Int? = null,
    val fuelPrice: Int? = null,
    val etaMillis: Long? = null,
)

/** 앱별 비교 결과 */
data class AppComparison(
    val app: NavApp,
    val installed: Boolean,
    val main: RouteInfo?,
    val mainError: String?,
    val free: RouteInfo?,
    val freeError: String?,
    val otherCount: Int = 0,
    val rawText: List<String> = emptyList(),
)

/** 가장 빠른 앱을 고르는 기준 */
enum class Criterion(val label: String) {
    DEFAULT("가장 빠른 길 (유료도로 포함)"),
    FREE("무료도로 경로"),
}

/** 비교 방식 */
enum class Mode(val label: String) {
    SCREEN("세 앱을 직접 실행해서 화면 읽기 (키 필요 없음)"),
    API("각 사 API로 조회 (API 키 필요)"),
}

class ApiException(message: String) : Exception(message)
