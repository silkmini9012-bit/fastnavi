package kr.fastnavi.app

import android.app.Application
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
    var start by mutableStateOf<Place?>(null); private set
    var goal by mutableStateOf<Place?>(null); private set

    var comparing by mutableStateOf(false); private set
    var results by mutableStateOf<List<AppComparison>>(emptyList()); private set
    var comparedAt by mutableStateOf(0L); private set
    var message by mutableStateOf<String?>(null)

    // 장소 검색
    var searchResults by mutableStateOf<List<Place>>(emptyList()); private set
    var searching by mutableStateOf(false); private set
    var searchError by mutableStateOf<String?>(null); private set
    var recentGoals by mutableStateOf(prefs.recentGoals()); private set

    // 자동 실행 카운트다운
    var countdown by mutableStateOf<Int?>(null); private set
    private var countdownJob: Job? = null

    private var lastLocation: Place? = null

    fun saveSettings(s: Settings) {
        prefs.save(s)
        settings = prefs.load()
    }

    fun setPlace(target: Target, place: Place) {
        if (target == Target.START) start = place else {
            goal = place
            prefs.addRecentGoal(place)
            recentGoals = prefs.recentGoals()
        }
        results = emptyList()
        cancelCountdown()
    }

    fun swap() {
        val s = start; start = goal; goal = s
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
                searchResults = PlaceSearch.search(query.trim(), settings.kakaoKey, settings.tmapKey, lastLocation)
                if (searchResults.isEmpty()) searchError = "검색 결과가 없습니다"
            } catch (e: Exception) {
                searchResults = emptyList()
                searchError = e.message
            } finally {
                searching = false
            }
        }
    }

    /** 위치 권한 획득 후 호출 */
    fun useCurrentLocation(target: Target, onDone: () -> Unit) {
        viewModelScope.launch {
            searching = true
            val loc = LocationHelper.current(ctx)
            searching = false
            if (loc == null) {
                searchError = "현재 위치를 가져올 수 없습니다. 위치(GPS)가 켜져 있는지 확인하세요."
                return@launch
            }
            val p = Place("현재 위치", "", loc.latitude, loc.longitude, isCurrentLocation = true)
            lastLocation = p
            setPlace(target, p)
            onDone()
        }
    }

    /** 출발지가 비어 있으면 현재 위치로 채운 뒤 비교 */
    fun compare(hasLocationPermission: Boolean) {
        val g = goal ?: run { message = "도착지를 입력하세요"; return }
        val keyed = NavApp.entries.filter { settings.hasKey(it) }
        if (keyed.isEmpty()) {
            message = "설정에서 API 키를 하나 이상 입력하세요"; return
        }
        cancelCountdown()
        viewModelScope.launch {
            comparing = true
            try {
                val s = start?.let { if (it.isCurrentLocation) refreshCurrent(it) else it }
                    ?: run {
                        if (!hasLocationPermission) {
                            message = "출발지를 입력하거나 위치 권한을 허용하세요"; return@launch
                        }
                        val loc = LocationHelper.current(ctx)
                            ?: run { message = "현재 위치를 가져올 수 없습니다"; return@launch }
                        Place("현재 위치", "", loc.latitude, loc.longitude, true).also {
                            start = it; lastLocation = it
                        }
                    }
                val st = settings
                results = coroutineScope {
                    NavApp.entries.map { app -> async { compareOne(app, st, s, g) } }.awaitAll()
                }
                comparedAt = System.currentTimeMillis()
                val best = fastest()
                if (best == null) {
                    message = "경로를 가져오지 못했습니다. 각 앱의 오류 메시지를 확인하세요."
                } else if (st.autoLaunch) {
                    startCountdown(best.app)
                }
            } finally {
                comparing = false
            }
        }
    }

    /** 현재 위치 출발이면 비교 직전에 위치를 갱신 (실패 시 기존 값 사용) */
    private suspend fun refreshCurrent(old: Place): Place {
        val loc = runCatching { LocationHelper.current(ctx) }.getOrNull() ?: return old
        return old.copy(lat = loc.latitude, lng = loc.longitude).also { start = it; lastLocation = it }
    }

    private suspend fun compareOne(app: NavApp, st: Settings, s: Place, g: Place): AppComparison {
        val installed = NavLauncher.isInstalled(ctx, app)
        if (!st.hasKey(app)) {
            return AppComparison(app, installed, null, "API 키 미설정", null, null)
        }
        suspend fun call(avoidToll: Boolean): Result<RouteInfo> = runCatching {
            when (app) {
                NavApp.TMAP -> TmapRouteApi.route(st.tmapKey, s, g, avoidToll)
                NavApp.NAVER -> NaverRouteApi.route(st.naverId, st.naverSecret, s, g, avoidToll)
                NavApp.KAKAO -> KakaoRouteApi.route(st.kakaoKey, s, g, avoidToll)
            }
        }
        return coroutineScope {
            val mainD = async { call(false) }
            // 무료도로 비교: 설정에서 켰거나, 기준이 무료도로일 때
            val needFree = st.compareFree || st.criterion == Criterion.FREE
            val freeD = if (needFree) async { call(true) } else null
            val main = mainD.await()
            val free = freeD?.await()
            AppComparison(
                app = app,
                installed = installed,
                main = main.getOrNull(),
                mainError = main.exceptionOrNull()?.message,
                free = free?.getOrNull(),
                freeError = free?.exceptionOrNull()?.message,
            )
        }
    }

    fun routeFor(c: AppComparison): RouteInfo? =
        if (settings.criterion == Criterion.FREE) c.free else c.main

    /** 기준(기본/무료도로)에 따라, 설치된 앱 중 소요시간이 가장 짧은 앱 */
    fun fastest(): AppComparison? =
        results.filter { it.installed && routeFor(it) != null }
            .minByOrNull { routeFor(it)!!.durationSec }

    fun launchNav(app: NavApp) {
        cancelCountdown()
        val s = start ?: return
        val g = goal ?: return
        if (settings.criterion == Criterion.FREE) {
            message = "${app.label}에서 경로 옵션을 '무료도로'로 선택하세요"
        }
        NavLauncher.launch(ctx, app, s, g)
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
