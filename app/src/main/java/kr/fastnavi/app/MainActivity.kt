package kr.fastnavi.app

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.provider.Settings as AndroidSettings
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat

private val Navy = Color(0xFF1B2B4B)
private val TollRed = Color(0xFFD93025)
private val FreeGreen = Color(0xFF188038)

class MainActivity : ComponentActivity() {
    private val vm: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme(colorScheme = lightColorScheme(primary = Navy, secondary = Color(0xFF0064FF))) {
                Surface(Modifier.fillMaxSize(), color = Color(0xFFF4F6FA)) { App(vm) }
            }
        }
    }
}

private sealed interface Screen {
    data object Main : Screen
    data object Settings : Screen
    data class Search(val target: Target) : Screen
}

@Composable
private fun App(vm: MainViewModel) {
    var screen by remember { mutableStateOf<Screen>(Screen.Main) }
    when (val s = screen) {
        Screen.Main -> MainScreen(
            vm,
            onSettings = { screen = Screen.Settings },
            onSearch = { vm.clearSearch(); screen = Screen.Search(it) },
        )
        Screen.Settings -> SettingsScreen(vm, onBack = { screen = Screen.Main })
        is Screen.Search -> SearchScreen(vm, s.target, onBack = { screen = Screen.Main })
    }
}

/* ───────────────────────────── 메인 ───────────────────────────── */

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MainScreen(vm: MainViewModel, onSettings: () -> Unit, onSearch: (Target) -> Unit) {
    val context = LocalContext.current
    val snackbar = remember { SnackbarHostState() }
    fun hasPerm() = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) ==
        PackageManager.PERMISSION_GRANTED ||
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) ==
        PackageManager.PERMISSION_GRANTED

    val permLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { vm.compare(hasPerm()) }

    LaunchedEffect(vm.message) {
        vm.message?.let { snackbar.showSnackbar(it); vm.message = null }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("빠른길 내비", fontWeight = FontWeight.Bold) },
                actions = {
                    IconButton(onClick = onSettings) { Icon(Icons.Default.Settings, "설정") }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Navy, titleContentColor = Color.White, actionIconContentColor = Color.White,
                ),
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
        containerColor = Color(0xFFF4F6FA),
    ) { pad ->
        LazyColumn(
            Modifier.fillMaxSize().padding(pad),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (vm.settings.mode == Mode.SCREEN && ScanBus.service == null) {
                item { SetupCard() }
            }
            item { PlaceInputCard(vm, onSearch) }
            if (vm.recentGoals.isNotEmpty()) item { QuickGoals(vm) }
            item { CriterionRow(vm) }
            item {
                Button(
                    onClick = {
                        if (vm.settings.mode == Mode.API && vm.start == null && !hasPerm()) {
                            permLauncher.launch(
                                arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION),
                            )
                        } else vm.compare(hasPerm())
                    },
                    enabled = !vm.comparing,
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                    shape = RoundedCornerShape(12.dp),
                ) {
                    if (vm.comparing) {
                        CircularProgressIndicator(Modifier.size(20.dp), color = Color.White, strokeWidth = 2.dp)
                        Spacer(Modifier.width(10.dp))
                        Text(vm.progress ?: "세 앱 경로 조회 중…")
                    } else {
                        Text("경로 비교하기", fontSize = 17.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }

            val best = vm.fastest()
            if (best != null) {
                item { BestCard(vm, best) }
            }
            if (vm.results.isNotEmpty()) {
                val sorted = vm.results.sortedWith(
                    compareBy<AppComparison> { vm.routeFor(it) == null }
                        .thenBy { vm.routeFor(it)?.durationSec ?: Int.MAX_VALUE },
                )
                val maxDur = sorted.mapNotNull { vm.routeFor(it)?.durationSec }.maxOrNull() ?: 1
                item {
                    Text(
                        "앱별 비교",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
                items(sorted, key = { it.app.name }) { c ->
                    AppCard(vm, c, isBest = c.app == best?.app, maxDur = maxDur)
                }
                item {
                    Text(
                        if (vm.settings.mode == Mode.SCREEN)
                            "※ 각 앱을 실제로 실행해 결과 화면에서 읽은 값입니다. 앱 업데이트로 화면이 바뀌면 일부를 못 읽을 수 있어요. 그럴 땐 '읽은 글자 보기'를 캡처해서 알려주세요."
                        else "※ 각 사 경로 API 예상치입니다. 앱 실행 후 재탐색 결과와 1~2분 차이가 날 수 있습니다.",
                        fontSize = 12.sp, color = Color.Gray,
                    )
                }
            }
        }
    }
}

@Composable
private fun SetupCard() {
    val context = LocalContext.current
    val enabledButNotRunning = ScanBus.isEnabledInSettings(context)
    Card(colors = CardDefaults.cardColors(containerColor = Color(0xFFFFF4E5)), shape = RoundedCornerShape(14.dp)) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("처음 한 번만: 접근성 권한 켜기", fontWeight = FontWeight.Bold, fontSize = 16.sp)
            Text(
                "티맵·네이버지도·카카오맵을 차례로 열어 결과 화면의 시간과 통행료를 읽으려면 이 권한이 필요해요. " +
                    "읽은 내용은 폰 밖으로 나가지 않습니다.",
                fontSize = 13.sp, color = Color(0xFF5F6368),
            )
            if (enabledButNotRunning) {
                Text("설정에는 켜져 있는데 연결이 안 됐어요. 접근성 설정에서 껐다가 다시 켜 주세요.",
                    fontSize = 13.sp, color = TollRed)
            }
            Text("① 아래 [접근성 설정 열기] → '설치된 앱'(또는 '설치된 서비스') → 빠른길 내비 → 사용 켜기", fontSize = 13.sp)
            Button(
                onClick = {
                    context.startActivity(Intent(AndroidSettings.ACTION_ACCESSIBILITY_SETTINGS))
                },
                modifier = Modifier.fillMaxWidth(),
            ) { Text("접근성 설정 열기") }
            Text(
                "② 켜지지 않고 '제한된 설정' 안내가 나오면: [앱 정보 열기] → 오른쪽 위 ⋮ → '제한된 설정 허용' → 다시 ①",
                fontSize = 13.sp,
            )
            OutlinedButton(
                onClick = {
                    context.startActivity(
                        Intent(AndroidSettings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}")),
                    )
                },
                modifier = Modifier.fillMaxWidth(),
            ) { Text("앱 정보 열기") }
        }
    }
}

@Composable
private fun PlaceInputCard(vm: MainViewModel, onSearch: (Target) -> Unit) {
    Card(colors = CardDefaults.cardColors(containerColor = Color.White), shape = RoundedCornerShape(14.dp)) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                PlaceRow("출발", vm.start?.name ?: "현재 위치", false, Color(0xFF0064FF)) {
                    onSearch(Target.START)
                }
                HorizontalDivider(Modifier.padding(vertical = 6.dp))
                PlaceRow("도착", vm.goal?.name ?: "어디로 갈까요?", vm.goal == null, TollRed) {
                    onSearch(Target.GOAL)
                }
            }
            TextButton(onClick = vm::swap) { Text("⇅", fontSize = 22.sp) }
        }
    }
}

@Composable
private fun PlaceRow(label: String, text: String, placeholder: Boolean, dot: Color, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).clickable(onClick = onClick).padding(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(10.dp).clip(CircleShape).background(dot))
        Spacer(Modifier.width(10.dp))
        Text(label, color = Color.Gray, fontSize = 13.sp, modifier = Modifier.width(34.dp))
        Text(
            text,
            fontSize = 16.sp,
            color = if (placeholder) Color.Gray else Color.Black,
            fontWeight = if (placeholder) FontWeight.Normal else FontWeight.SemiBold,
            maxLines = 1, overflow = TextOverflow.Ellipsis,
        )
    }
}

/** 즐겨찾기(★)와 최근 목적지 바로 선택 */
@Composable
private fun QuickGoals(vm: MainViewModel) {
    Row(
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        vm.recentGoals.take(10).forEach { p ->
            FilterChip(
                selected = vm.goal?.let { it.name == p.name && it.address == p.address } == true,
                onClick = { vm.setPlace(Target.GOAL, p) },
                label = { Text((if (p.pinned) "★ " else "") + p.name, maxLines = 1) },
            )
        }
    }
}

@Composable
private fun CriterionRow(vm: MainViewModel) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        Text("기준", color = Color.Gray, fontSize = 13.sp)
        Criterion.entries.forEach { c ->
            FilterChip(
                selected = vm.settings.criterion == c,
                onClick = { vm.saveSettings(vm.settings.copy(criterion = c)) },
                label = { Text(if (c == Criterion.DEFAULT) "가장 빠른 길" else "무료도로만") },
            )
        }
    }
}

@Composable
private fun BestCard(vm: MainViewModel, best: AppComparison) {
    val r = vm.routeFor(best) ?: return
    val appColor = Color(best.app.color)
    Card(
        colors = CardDefaults.cardColors(containerColor = Navy),
        shape = RoundedCornerShape(16.dp),
    ) {
        Column(Modifier.padding(18.dp)) {
            Text("가장 빨리 도착", color = Color(0xFFB8C4DC), fontSize = 13.sp)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(12.dp).clip(CircleShape).background(appColor))
                Spacer(Modifier.width(8.dp))
                Text(best.app.label, color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.weight(1f))
                Text(Fmt.clock(vm.etaOf(r)) + " 도착", color = Color.White, fontSize = 20.sp,
                    fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.height(4.dp))
            Text(
                listOfNotNull(
                    Fmt.duration(r.durationSec),
                    r.distanceM?.let { Fmt.distance(it) },
                    when {
                        r.tollFare == null -> "통행료 정보 없음"
                        r.tollFare > 0 -> "통행료 ${Fmt.won(r.tollFare)}"
                        else -> "통행료 없음"
                    },
                ).joinToString(" · "),
                color = Color(0xFFDDE3EE),
            )
            // 2등과의 차이
            val second = vm.results.filter { it.app != best.app && it.installed }
                .mapNotNull { vm.routeFor(it) }.minByOrNull { vm.etaOf(it) }
            if (second != null) {
                val gap = ((vm.etaOf(second) - vm.etaOf(r)) / 1000).toInt()
                Text(
                    if (gap >= 60) "다음으로 빠른 앱보다 ${Fmt.duration(gap)} 빨라요" else "다른 앱과 거의 같아요 (1분 이내)",
                    color = Color(0xFFB8C4DC), fontSize = 13.sp,
                )
            }
            Spacer(Modifier.height(12.dp))
            val cd = vm.countdown
            if (cd != null) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("${cd}초 후 ${best.app.label} 안내 시작…", color = Color.White, modifier = Modifier.weight(1f))
                    OutlinedButton(onClick = vm::cancelCountdown) { Text("취소", color = Color.White) }
                }
            } else {
                Button(
                    onClick = { vm.launchNav(best.app) },
                    modifier = Modifier.fillMaxWidth().height(50.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = Navy),
                    shape = RoundedCornerShape(12.dp),
                ) { Text("${best.app.label}로 안내 시작", fontWeight = FontWeight.Bold, fontSize = 16.sp) }
            }
        }
    }
}

@Composable
private fun TollBadge(toll: Int?) {
    val (bg, fg, text) = when {
        toll == null -> Triple(Color(0xFFF1F3F4), Color.Gray, "통행료 ?")
        toll > 0 -> Triple(Color(0xFFFCE8E6), TollRed, "유료 ${Fmt.won(toll)}")
        else -> Triple(Color(0xFFE6F4EA), FreeGreen, "무료")
    }
    Text(
        text, color = fg, fontSize = 13.sp, fontWeight = FontWeight.Bold,
        modifier = Modifier.clip(RoundedCornerShape(6.dp)).background(bg).padding(horizontal = 8.dp, vertical = 3.dp),
    )
}

@Composable
private fun AppCard(vm: MainViewModel, c: AppComparison, isBest: Boolean, maxDur: Int) {
    val appColor = Color(c.app.color)
    val freeMode = vm.settings.criterion == Criterion.FREE
    Card(
        colors = CardDefaults.cardColors(containerColor = Color.White),
        shape = RoundedCornerShape(14.dp),
        border = if (isBest) androidx.compose.foundation.BorderStroke(2.dp, appColor) else null,
    ) {
        Column(Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(12.dp).clip(CircleShape).background(appColor))
                Spacer(Modifier.width(8.dp))
                Text(c.app.label, fontWeight = FontWeight.Bold, fontSize = 17.sp)
                if (isBest) {
                    Spacer(Modifier.width(6.dp))
                    Text("가장 빠름", fontSize = 12.sp, color = Color.White, fontWeight = FontWeight.Bold,
                        modifier = Modifier.clip(RoundedCornerShape(6.dp)).background(appColor)
                            .padding(horizontal = 6.dp, vertical = 2.dp))
                }
                if (!c.installed) {
                    Spacer(Modifier.width(6.dp))
                    Text("미설치", fontSize = 12.sp, color = Color.Gray)
                }
            }
            Spacer(Modifier.height(8.dp))

            // 기본 경로
            RouteLine(
                title = "기본 경로",
                r = c.main, err = c.mainError, eta = c.main?.let { vm.etaOf(it) },
                maxDur = maxDur, color = appColor, emphasized = !freeMode,
            )
            // 무료도로 경로
            if (c.free != null || c.freeError != null) {
                Spacer(Modifier.height(8.dp))
                RouteLine(
                    title = "무료도로",
                    r = c.free, err = c.freeError, eta = c.free?.let { vm.etaOf(it) },
                    maxDur = maxDur, color = appColor.copy(alpha = 0.55f), emphasized = freeMode,
                )
            }
            // 통행료 vs 시간 인사이트
            val m = c.main; val f = c.free
            if (m != null && f != null && (m.tollFare ?: 0) > 0) {
                val saved = ((vm.etaOf(f) - vm.etaOf(m)) / 1000).toInt()
                Spacer(Modifier.height(6.dp))
                Text(
                    when {
                        saved >= 60 -> "💡 통행료 ${Fmt.won(m.tollFare ?: 0)} 내면 ${Fmt.duration(saved)} 빨라요" +
                            (if (m.distanceM != null && f.distanceM != null && f.distanceM != m.distanceM)
                                " (무료도로는 ${Fmt.distance(f.distanceM)}, ${if (f.distanceM > m.distanceM) "더 길어요" else "더 짧아요"})" else "")
                        else -> "💡 무료도로로 가도 시간 차이가 거의 없어요"
                    },
                    fontSize = 13.sp, color = Color(0xFF5F6368),
                )
            }
            m?.let { r ->
                val extra = listOfNotNull(
                    r.fuelPrice?.let { "유류비 약 ${Fmt.won(it)}" },
                    r.taxiFare?.let { "택시 약 ${Fmt.won(it)}" },
                )
                if (extra.isNotEmpty()) Text(extra.joinToString(" · "), fontSize = 12.sp, color = Color.Gray)
            }
            if (c.otherCount > 0) {
                Text("이 앱이 보여준 다른 경로 ${c.otherCount}개 중 가장 빠른 경로 기준", fontSize = 12.sp, color = Color.Gray)
            }
            if (c.rawText.isNotEmpty()) {
                var open by remember { mutableStateOf(false) }
                TextButton(onClick = { open = !open }) {
                    Text(if (open) "읽은 글자 닫기" else "읽은 글자 보기", fontSize = 12.sp)
                }
                if (open) {
                    Text(
                        c.rawText.take(80).joinToString(" | "),
                        fontSize = 11.sp, color = Color(0xFF5F6368),
                        modifier = Modifier.background(Color(0xFFF4F6FA)).padding(8.dp),
                    )
                }
            }
            if (c.installed && (vm.routeFor(c) != null || c.main != null || c.mainError != null)) {
                Spacer(Modifier.height(8.dp))
                OutlinedButton(
                    onClick = { vm.launchNav(c.app) },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp),
                ) { Text(if (c.installed) "${c.app.label}로 안내" else "${c.app.label} 설치하기") }
            }
        }
    }
}

@Composable
private fun RouteLine(
    title: String, r: RouteInfo?, err: String?, eta: Long?,
    maxDur: Int, color: Color, emphasized: Boolean,
) {
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(title, fontSize = 12.sp, color = Color.Gray, modifier = Modifier.width(56.dp))
            if (r == null) {
                Text(err ?: "-", fontSize = 13.sp, color = if (title == "무료도로") Color.Gray else TollRed, modifier = Modifier.weight(1f))
                return@Row
            }
            Text(
                Fmt.duration(r.durationSec),
                fontSize = if (emphasized) 20.sp else 16.sp,
                fontWeight = if (emphasized) FontWeight.Bold else FontWeight.Medium,
            )
            Spacer(Modifier.width(8.dp))
            Text(listOfNotNull(eta?.let { Fmt.clock(it) + " 도착" }, r.distanceM?.let { Fmt.distance(it) }).joinToString(" · "),
                fontSize = 13.sp, color = Color(0xFF5F6368), modifier = Modifier.weight(1f))
            TollBadge(r.tollFare)
        }
        if (r != null) {
            Spacer(Modifier.height(4.dp))
            LinearProgressIndicator(
                progress = { (r.durationSec.toFloat() / maxDur.coerceAtLeast(1)).coerceIn(0.05f, 1f) },
                modifier = Modifier.fillMaxWidth().padding(start = 56.dp).height(6.dp).clip(RoundedCornerShape(3.dp)),
                color = color,
                trackColor = Color(0xFFEDEFF3),
            )
        }
    }
}

/* ───────────────────────────── 장소 검색 ───────────────────────────── */

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SearchScreen(vm: MainViewModel, target: Target, onBack: () -> Unit) {
    val context = LocalContext.current
    var query by rememberSaveable { mutableStateOf("") }
    BackHandler(onBack = onBack)

    // 가까운 곳 우선 표시용 위치 (권한이 없으면 한 번 요청)
    val permLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { granted -> if (granted.values.any { it }) vm.refreshMyLocation() }
    LaunchedEffect(Unit) {
        val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED
        if (granted) vm.refreshMyLocation()
        else permLauncher.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
    }
    fun pick(p: Place) { vm.setPlace(target, p); onBack() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (target == Target.START) "출발지 검색" else "도착지 검색") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "뒤로") }
                },
            )
        },
    ) { pad ->
        Column(Modifier.fillMaxSize().padding(pad).padding(horizontal = 16.dp)) {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it; vm.searchAsYouType(it) },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("장소, 주소 (한 글자부터 검색)") },
                singleLine = true,
                trailingIcon = {
                    if (query.isNotEmpty()) {
                        IconButton(onClick = { query = ""; vm.clearSearch() }) { Icon(Icons.Default.Close, "지우기") }
                    } else Icon(Icons.Default.Search, null)
                },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { vm.search(query) }),
            )
            Spacer(Modifier.height(4.dp))
            if (vm.searching) LinearProgressIndicator(Modifier.fillMaxWidth())
            if (query.isNotBlank()) {
                vm.searchError?.let { Text(it, color = TollRed, fontSize = 13.sp, modifier = Modifier.padding(vertical = 8.dp)) }
            }

            LazyColumn {
                if (target == Target.START && query.isBlank()) item {
                    ResultRow(Icons.Default.LocationOn, "현재 위치", "내비 앱이 지금 위치에서 출발") {
                        vm.useCurrentLocation(Target.START, onBack)
                    }
                }
                if (query.isBlank()) {
                    if (vm.recentGoals.isNotEmpty()) {
                        item {
                            Text("즐겨찾기 · 최근 목적지  (★ 고정, ✕ 삭제)", color = Color.Gray, fontSize = 13.sp,
                                modifier = Modifier.padding(top = 12.dp, bottom = 4.dp))
                        }
                    }
                    items(vm.recentGoals, key = { "r_" + it.name + it.address }) { p ->
                        RecentRow(
                            p,
                            distance = vm.myLocation?.let { Fmt.meters(it, p) },
                            onClick = { pick(p) },
                            onPin = { vm.togglePin(p) },
                            onDelete = { vm.removeRecent(p) },
                        )
                    }
                } else {
                    items(vm.searchResults) { p ->
                        val d = vm.myLocation?.let { Fmt.meters(it, p) }
                        ResultRow(
                            Icons.Default.Place, p.name,
                            listOfNotNull(p.address.ifBlank { null }, d?.let { Fmt.distance(it) }).joinToString("  ·  "),
                        ) { pick(p) }
                    }
                }
            }
        }
    }
}

@Composable
private fun RecentRow(p: Place, distance: Int?, onClick: () -> Unit, onPin: () -> Unit, onDelete: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = onPin) {
            Icon(
                if (p.pinned) Icons.Filled.Star else Icons.Outlined.Star,
                if (p.pinned) "고정 해제" else "고정",
                tint = if (p.pinned) Color(0xFFFFB800) else Color(0xFFBDBDBD),
            )
        }
        Column(Modifier.weight(1f)) {
            Text(p.name, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            val sub = listOfNotNull(p.address.ifBlank { null }, distance?.let { Fmt.distance(it) }).joinToString("  ·  ")
            if (sub.isNotBlank()) Text(sub, fontSize = 13.sp, color = Color.Gray, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        IconButton(onClick = onDelete) { Icon(Icons.Default.Close, "삭제", tint = Color.Gray) }
    }
    HorizontalDivider(color = Color(0xFFEDEFF3))
}

@Composable
private fun ResultRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String, sub: String, onClick: () -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, tint = Color.Gray)
        Spacer(Modifier.width(12.dp))
        Column {
            Text(title, fontWeight = FontWeight.SemiBold)
            if (sub.isNotBlank()) Text(sub, fontSize = 13.sp, color = Color.Gray, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
    HorizontalDivider(color = Color(0xFFEDEFF3))
}

/* ───────────────────────────── 설정 ───────────────────────────── */

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SettingsScreen(vm: MainViewModel, onBack: () -> Unit) {
    var s by remember { mutableStateOf(vm.settings) }
    BackHandler(onBack = onBack)
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("설정") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "뒤로") }
                },
                actions = {
                    TextButton(onClick = { vm.saveSettings(s); onBack() }) { Text("저장", fontWeight = FontWeight.Bold) }
                },
            )
        },
    ) { pad ->
        Column(
            Modifier.fillMaxSize().padding(pad).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            SectionTitle("비교 방식")
            Mode.entries.forEach { m ->
                Row(
                    Modifier.fillMaxWidth().clickable { s = s.copy(mode = m) },
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    RadioButton(selected = s.mode == m, onClick = { s = s.copy(mode = m) })
                    Text(m.label)
                }
            }
            SectionTitle("API 키 (선택 사항)")
            Text(
                "'API로 조회' 방식을 고를 때만 필요합니다. 기본 방식(앱 화면 읽기)은 키 없이 동작해요.",
                fontSize = 13.sp, color = Color.Gray,
            )
            KeyField("TMAP 앱키 (SK open API)", s.tmapKey) { s = s.copy(tmapKey = it) }
            KeyField("네이버 클라우드 Client ID", s.naverId) { s = s.copy(naverId = it) }
            KeyField("네이버 클라우드 Client Secret", s.naverSecret) { s = s.copy(naverSecret = it) }
            KeyField("카카오 REST API 키 (경로 + 장소검색)", s.kakaoKey) { s = s.copy(kakaoKey = it) }

            SectionTitle("비교 옵션")
            SwitchRow("무료도로 경로도 함께 비교", "통행료를 내면 몇 분 빨라지는지 보여줍니다", s.compareFree) {
                s = s.copy(compareFree = it)
            }
            SwitchRow("가장 빠른 앱 자동 실행", "비교가 끝나면 3초 후 1등 앱으로 안내를 시작합니다", s.autoLaunch) {
                s = s.copy(autoLaunch = it)
            }
            Text("가장 빠른 앱 선택 기준", fontWeight = FontWeight.SemiBold)
            Criterion.entries.forEach { c ->
                Row(
                    Modifier.fillMaxWidth().clickable { s = s.copy(criterion = c) },
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    RadioButton(selected = s.criterion == c, onClick = { s = s.copy(criterion = c) })
                    Text(c.label)
                }
            }
            Spacer(Modifier.height(8.dp))
            Button(onClick = { vm.saveSettings(s); onBack() }, modifier = Modifier.fillMaxWidth()) { Text("저장") }

            SectionTitle("업데이트")
            val ctx = LocalContext.current
            val ver = remember { runCatching { ctx.packageManager.getPackageInfo(ctx.packageName, 0).versionName }.getOrNull() ?: "?" }
            Text("현재 버전 $ver · 새 버전은 지우지 않고 그대로 설치하면 업데이트돼요.", fontSize = 13.sp, color = Color.Gray)
            OutlinedButton(
                onClick = {
                    ctx.startActivity(
                        Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/silkmini9012-bit/fastnavi/releases/latest/download/FastNavi.apk")),
                    )
                },
                modifier = Modifier.fillMaxWidth(),
            ) { Text("최신 버전 받기") }
        }
    }
}

@Composable
private fun SectionTitle(t: String) =
    Text(t, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 8.dp))

@Composable
private fun KeyField(label: String, value: String, onChange: (String) -> Unit) {
    OutlinedTextField(
        value = value, onValueChange = onChange, label = { Text(label) },
        singleLine = true, modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun SwitchRow(title: String, sub: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, fontWeight = FontWeight.SemiBold)
            Text(sub, fontSize = 12.sp, color = Color.Gray)
        }
        Switch(checked = checked, onCheckedChange = onChange)
    }
}
