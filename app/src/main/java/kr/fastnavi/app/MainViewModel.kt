package kr.fastnavi.app

import android.app.Application
import android.content.Intent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kr.fastnavi.app.api.KakaoRouteApi
import kr.fastnavi.app.api.NaverRouteApi
import kr.fastnavi.app.api.PlaceSearch
import kr.fastnavi.app.api.TmapRouteApi

enum class Target { START, GOAL }

class MainViewModel(app: Application) : AndroidViewModel(app) {
    private val ctx get() = getApplication<Application>()
    private val prefs = Prefs(app)

    var settings by mutableStateOf(prefs.load()); private set
    /** null = 현재 위치 */
    var start by mutableStateOf<Place?>(null); private set
    var goal by mutableStateOf<Place?>(null); private set

    var comparing by mutableStateOf(false); private set
    var progress by mutableStateOf<String?>(null); private set
    var results by mutableStateOf<List<AppComparison>>(emptyList()); private set
    var comparedAt by mutableStateOf(0L); private set
    var message by mutableStateOf<String?>(null)

    var searchResults by mutableStateOf<List<Place>>(emptyList()); private set
    var searching by mutableStateOf(false); private set
    var searchError by mutableStateOf<String?>(null); private set
    var recentGoals by mutableStateOf(prefs.recentGoals()); private set

    var countdown by mutableStateOf<Int?>(null); private set
    private var countdownJob: Job? = null
    private var lastLocation: Place? = null

    fun saveSettings(s: Settings) {
        prefs.save(s)
        settings = prefs.load()
    }

    fun setPlace(target: Target, place: Place?) {
        if (target == Target.START) start = place else if (place != null) {
            goal = place
            prefs.addRecentGoal(place)
            recentGoals = prefs.recentGoals()
        }
        results = emptyList()
        cancelCountdown()
    }

    fun swap() {
        val g = goal ?: return
        val s = start
        goal = s ?: run { message = "출발지가 현재 위치라서 바꿀 수 없어요"; return }
        start = g
        results = emptyList()
    }

    fun clearSearch() {
        searchResults = emptyList(); searchError = null
    }

    fun search(query: String) {
        if (query.isBlank()) return
        viewModelScope.launch {
            searching = true; searchError = null
            try {
                searchResults = PlaceSearch.search(ctx, query.trim(), settings.kakaoKey, settings.tmapKey, lastLocation)
                if (searchResults.isEmpty()) searchError = "검색 결과가 없습니다. 주소(예: 다산순환로 220)나 다른 이름으로 검색해 보세요."
            } catch (e: Exception) {
                searchResults = emptyList()
                searchError = e.message
            } finally {
                searching = false
            }
        }
    }

    /** 위치 권한 획득 후 호출 (API 방식에서만 좌표가 필요) */
    fun useCurrentLocation(target: Target, onDone: () -> Unit) {
        if (target == Target.START) {
            setPlace(Target.START, null); onDone(); return
        }
        viewModelScope.launch {
            searching = true
            val loc = LocationHelper.current(ctx)
            searching = false
            if (loc == null) {
                searchError = "현재 위치를 가져올 수 없습니다. 위치(GPS)가 켜져 있는지 확인하세요."
                return@launch
            }
            setPlace(target, Place("현재 위치", "", loc.latitude, loc.longitude, isCurrentLocation = true))
            onDone()
        }
    }

    // ─────────────────────────── 비교 ───────────────────────────

    fun compare(hasLocationPermission: Boolean) {
        val g = goal ?: run { message = "도착지를 입력하세요"; return }
        cancelCountdown()
        if (settings.mode == Mode.SCREEN) compareByScreen(g) else compareByApi(g, hasLocationPermission)
    }

    private val installedApps get() = NavApp.entries.filter { NavLauncher.isInstalled(ctx, it) }

    /** 방식 1: 세 앱을 차례로 실행해서 결과 화면 읽기 */
    private fun compareByScreen(g: Place) {
        val svc = ScanBus.service ?: run {
            message = "먼저 위쪽의 '접근성 권한 켜기'를 완료하세요"; return
        }
        val apps = installedApps
        if (apps.isEmpty()) { message = "티맵·네이버지도·카카오맵 중 설치된 앱이 없습니다"; return }
        viewModelScope.launch {
            comparing = true
            val out = mutableListOf<AppComparison>()
            try {
                apps.forEachIndexed { i, app ->
                    progress = "${app.label} 경로 확인 중… (${i + 1}/${apps.size})"
                    out += scanApp(svc, app, start, g)
                }
                NavApp.entries.filter { it !in apps }.forEach {
                    out += AppComparison(it, false, null, "설치되지 않음", null, null)
                }
            } finally {
                comparing = false
                progress = null
                // 우리 앱을 다시 앞으로
                runCatching {
                    svc.startActivity(
                        Intent(svc, MainActivity::class.java).addFlags(
                            Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT or
                                Intent.FLAG_ACTIVITY_SINGLE_TOP,
                        ),
                    )
                }
            }
            results = out
            comparedAt = System.currentTimeMillis()
            afterCompare()
        }
    }

    private suspend fun scanApp(svc: ScanService, app: NavApp, s: Place?, g: Place): AppComparison {
        fun fail(msg: String, raw: List<String> = emptyList()) =
            AppComparison(app, true, null, msg, null, null, rawText = raw)

        try {
            svc.startActivity(NavLauncher.intentFor(app, NavLauncher.scanUri(ctx, app, s, g)))
        } catch (e: Exception) {
            return fail("앱 실행 실패")
        }
        // 앱이 앞으로 올 때까지
        var waited = 0
        while (svc.foregroundPackage() != app.pkg) {
            if (waited > 10_000) return fail("앱이 열리지 않았습니다")
            delay(300); waited += 300
        }
        // 경로 결과가 뜨고 내용이 안정될 때까지 읽기 (최대 18초)
        val t0 = System.currentTimeMillis()
        var prev: List<RouteParser.Option>? = null
        var texts = emptyList<String>()
        var opts = emptyList<RouteParser.Option>()
        while (System.currentTimeMillis() - t0 < 18_000) {
            delay(900)
            if (svc.foregroundPackage() != app.pkg) continue
            texts = svc.collectTexts(app.pkg)
            opts = RouteParser.parse(texts)
            if (opts.isNotEmpty() && opts == prev && System.currentTimeMillis() - t0 > 2_500) break
            prev = opts
        }
        val scannedAt = System.currentTimeMillis()
        if (opts.isEmpty()) return fail("화면에서 소요시간을 찾지 못했습니다", texts)

        fun info(o: RouteParser.Option) = RouteInfo(
            durationSec = o.durationMin * 60,
            distanceM = o.distanceM,
            tollFare = o.toll,
            etaMillis = o.arrivalMillis ?: (scannedAt + o.durationMin * 60_000L),
        )
        val bestOpt = RouteParser.best(opts)!!
        val freeOpt = RouteParser.bestFree(opts)
        val main = info(bestOpt)
        val free = freeOpt?.takeIf { it.durationMin != bestOpt.durationMin || bestOpt.toll != 0 }?.let { info(it) }
        val distinct = opts.map { it.durationMin to it.toll }.distinct().size
        return AppComparison(
            app = app, installed = true,
            main = main, mainError = null,
            free = free, freeError = null,
            otherCount = (distinct - 1).coerceAtLeast(0),
            rawText = texts,
        )
    }

    /** 방식 2: 각 사 API (키 필요) */
    private fun compareByApi(g: Place, hasLocationPermission: Boolean) {
        if (NavApp.entries.none { settings.hasKey(it) }) {
            message = "API 방식은 설정에서 키를 입력해야 합니다"; return
        }
        viewModelScope.launch {
            comparing = true
            try {
                val s = start ?: run {
                    if (!hasLocationPermission) { message = "위치 권한을 허용하세요"; return@launch }
                    val loc = LocationHelper.current(ctx)
                        ?: run { message = "현재 위치를 가져올 수 없습니다"; return@launch }
                    Place("현재 위치", "", loc.latitude, loc.longitude, true).also { lastLocation = it }
                }
                val st = settings
                val now = System.currentTimeMillis()
                results = coroutineScope {
                    NavApp.entries.map { app -> async { apiOne(app, st, s, g, now) } }.awaitAll()
                }
                comparedAt = now
                afterCompare()
            } finally {
                comparing = false
            }
        }
    }

    private suspend fun apiOne(app: NavApp, st: Settings, s: Place, g: Place, now: Long): AppComparison {
        val installed = NavLauncher.isInstalled(ctx, app)
        if (!st.hasKey(app)) return AppComparison(app, installed, null, "API 키 미설정", null, null)
        suspend fun call(avoidToll: Boolean): Result<RouteInfo> = runCatching {
            val r = when (app) {
                NavApp.TMAP -> TmapRouteApi.route(st.tmapKey, s, g, avoidToll)
                NavApp.NAVER -> NaverRouteApi.route(st.naverId, st.naverSecret, s, g, avoidToll)
                NavApp.KAKAO -> KakaoRouteApi.route(st.kakaoKey, s, g, avoidToll)
            }
            r.copy(etaMillis = now + r.durationSec * 1000L)
        }
        return coroutineScope {
            val mainD = async { call(false) }
            val freeD = if (st.compareFree || st.criterion == Criterion.FREE) async { call(true) } else null
            val main = mainD.await()
            val free = freeD?.await()
            AppComparison(
                app, installed,
                main.getOrNull(), main.exceptionOrNull()?.message,
                free?.getOrNull(), free?.exceptionOrNull()?.message,
            )
        }
    }

    private fun afterCompare() {
        val best = fastest()
        if (best == null) {
            message = "경로를 읽지 못했습니다. 각 앱 카드의 '읽은 글자 보기'를 캡처해서 알려주세요."
        } else if (settings.autoLaunch) {
            startCountdown(best.app)
        }
    }

    fun routeFor(c: AppComparison): RouteInfo? =
        if (settings.criterion == Criterion.FREE) c.free ?: c.main?.takeIf { it.tollFare == 0 } else c.main

    fun etaOf(r: RouteInfo): Long = r.etaMillis ?: (comparedAt + r.durationSec * 1000L)

    /** 도착 예정 시각이 가장 빠른 앱 */
    fun fastest(): AppComparison? =
        results.filter { it.installed && routeFor(it) != null }.minByOrNull { etaOf(routeFor(it)!!) }

    // ─────────────────────────── 안내 실행 ───────────────────────────

    fun launchNav(app: NavApp) {
        cancelCountdown()
        val g = goal ?: return
        if (settings.criterion == Criterion.FREE) {
            message = "${app.label}에서 경로 옵션을 '무료도로'로 선택하세요"
        }
        NavLauncher.launch(ctx, app, start, g)
        // 경로 화면에 '안내 시작' 버튼이 있으면 대신 눌러줌
        val svc = ScanBus.service ?: return
        viewModelScope.launch {
            val startBtn = Regex("(?:길\\s*)?안내\\s*시작|경로\\s*안내\\s*시작|내비\\s*시작|안내\\s*시작하기")
            var waited = 0
            while (waited < 12_000) {
                delay(700); waited += 700
                if (svc.foregroundPackage() == app.pkg && svc.clickMatching(app.pkg, startBtn)) return@launch
            }
        }
    }

    private fun startCountdown(app: NavApp) {
        countdownJob?.cancel()
        countdownJob = viewModelScope.launch {
            for (i in 3 downTo 1) {
                countdown = i
                delay(1000)
            }
            countdown = null
            countdownJob = null
            this@MainViewModel.launchNav(app)
        }
    }

    fun cancelCountdown() {
        countdownJob?.cancel()
        countdownJob = null
        countdown = null
    }
}
