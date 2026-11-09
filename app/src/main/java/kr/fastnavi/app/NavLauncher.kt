package kr.fastnavi.app

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import java.net.URLEncoder

/**
 * 각 앱의 URL 스킴으로 길안내 실행.
 * - 티맵:   tmap://route?goalname=&goalx=(경도)&goaly=(위도)  → 현재 위치에서 바로 안내
 * - 네이버: nmap://navigation?dlat=&dlng=&dname=&appname=     → 현재 위치에서 바로 안내
 *           (출발지를 따로 지정했다면 nmap://route/car 로 경로 화면을 띄움)
 * - 카카오: kakaomap://route?sp=&ep=&by=CAR                    → 자동차 길찾기 화면 (안내 시작 버튼 1회 터치)
 */
object NavLauncher {

    fun isInstalled(context: Context, app: NavApp): Boolean =
        context.packageManager.getLaunchIntentForPackage(app.pkg) != null

    fun launch(context: Context, app: NavApp, start: Place, goal: Place) {
        val uri = buildUri(context, app, start, goal)
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(uri)).apply {
            setPackage(app.pkg)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        try {
            context.startActivity(intent)
        } catch (e: ActivityNotFoundException) {
            // 미설치 → 플레이 스토어
            val store = Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=${app.pkg}"))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            runCatching { context.startActivity(store) }
        }
    }

    private fun enc(s: String) = URLEncoder.encode(s, "UTF-8").replace("+", "%20")

    fun buildUri(context: Context, app: NavApp, start: Place, goal: Place): String = when (app) {
        NavApp.TMAP ->
            "tmap://route?goalname=${enc(goal.name)}&goalx=${goal.lng}&goaly=${goal.lat}"

        NavApp.NAVER -> {
            val appName = context.packageName
            if (start.isCurrentLocation) {
                "nmap://navigation?dlat=${goal.lat}&dlng=${goal.lng}&dname=${enc(goal.name)}&appname=$appName"
            } else {
                "nmap://route/car?slat=${start.lat}&slng=${start.lng}&sname=${enc(start.name)}" +
                    "&dlat=${goal.lat}&dlng=${goal.lng}&dname=${enc(goal.name)}&appname=$appName"
            }
        }

        NavApp.KAKAO -> buildString {
            append("kakaomap://route?")
            if (!start.isCurrentLocation) append("sp=${start.lat},${start.lng}&")
            append("ep=${goal.lat},${goal.lng}&by=CAR")
        }
    }
}
