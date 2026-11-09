package kr.fastnavi.app

import android.Manifest
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
    // 키가 하나도 없으면 첫 실행 시 설정 화면으로
    LaunchedEffect(Unit) {
        if (NavApp.entries.none { vm.settings.hasKey(it) }) screen = Screen.Settings
    }
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
            item { PlaceInputCard(vm, onSearch) }
            item { CriterionRow(vm) }
            item {
                Button(
                    onClick = {
                        if (vm.start == null && !hasPerm()) {
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
                        Text("세 앱 경로 조회 중…")
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
                        "※ 각 사의 경로 API 예상치(조회 시점 교통 반영)입니다. 앱 실행 후 재탐색 결과와 1~2분 차이가 날 수 있습니다.",
                        fontSize = 12.sp, color = Color.Gray,
                    )
                }
            }
        }
    }
}

@Composable
private fun PlaceInputCard(vm: MainViewModel, onSearch: (Target) -> Unit) {
    Card(colors = CardDefaults.cardColors(containerColor = Color.White), shape = RoundedCornerShape(14.dp)) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                PlaceRow("출발", vm.start?.name ?: "현재 위치 (자동)", vm.start == null, Color(0xFF0064FF)) {
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
                Text(Fmt.eta(r.durationSec, vm.comparedAt) + " 도착", color = Color.White, fontSize = 20.sp,
                    fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.height(4.dp))
            Text(
                "${Fmt.duration(r.durationSec)} · ${Fmt.distance(r.distanceM)} · " +
                    if (r.tollFare > 0) "통행료 ${Fmt.won(r.tollFare)}" else "통행료 없음",
                color = Color(0xFFDDE3EE),
            )
            // 2등과의 차이
            val second = vm.results.filter { it.app != best.app && it.installed }
                .mapNotNull { vm.routeFor(it) }.minByOrNull { it.durationSec }
            if (second != null) {
                val gap = second.durationSec - r.durationSec
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
private fun TollBadge(toll: Int) {
    val (bg, fg, text) = if (toll > 0) Triple(Color(0xFFFCE8E6), TollRed, "유료 ${Fmt.won(toll)}")
    else Triple(Color(0xFFE6F4EA), FreeGreen, "무료")
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
                r = c.main, err = c.mainError, comparedAt = vm.comparedAt,
                maxDur = maxDur, color = appColor, emphasized = !freeMode,
            )
            // 무료도로 경로
            if (c.free != null || c.freeError != null) {
                Spacer(Modifier.height(8.dp))
                RouteLine(
                    title = "무료도로",
                    r = c.free, err = c.freeError, comparedAt = vm.comparedAt,
                    maxDur = maxDur, color = appColor.copy(alpha = 0.55f), emphasized = freeMode,
                )
            }
            // 통행료 vs 시간 인사이트
            val m = c.main; val f = c.free
            if (m != null && f != null && m.tollFare > 0) {
                val saved = f.durationSec - m.durationSec
                Spacer(Modifier.height(6.dp))
                Text(
                    when {
                        saved >= 60 -> "💡 통행료 ${Fmt.won(m.tollFare - f.tollFare)} 내면 ${Fmt.duration(saved)} 빨라요"
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
            if (vm.routeFor(c) != null || c.main != null) {
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
    title: String, r: RouteInfo?, err: String?, comparedAt: Long,
    maxDur: Int, color: Color, emphasized: Boolean,
) {
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(title, fontSize = 12.sp, color = Color.Gray, modifier = Modifier.width(56.dp))
            if (r == null) {
                Text(err ?: "-", fontSize = 13.sp, color = TollRed, modifier = Modifier.weight(1f))
                return@Row
            }
            Text(
                Fmt.duration(r.durationSec),
                fontSize = if (emphasized) 20.sp else 16.sp,
                fontWeight = if (emphasized) FontWeight.Bold else FontWeight.Medium,
            )
            Spacer(Modifier.width(8.dp))
            Text("${Fmt.eta(r.durationSec, comparedAt)} 도착 · ${Fmt.distance(r.distanceM)}",
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

    val permLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { granted -> if (granted.values.any { it }) vm.useCurrentLocation(target, onBack) }

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
                onValueChange = { query = it },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("장소, 주소 검색 (예: 강남역, 판교 현대백화점)") },
                singleLine = true,
                trailingIcon = {
                    IconButton(onClick = { vm.search(query) }) { Icon(Icons.Default.Search, "검색") }
                },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { vm.search(query) }),
            )
            Spacer(Modifier.height(8.dp))
            if (vm.searching) LinearProgressIndicator(Modifier.fillMaxWidth())
            vm.searchError?.let { Text(it, color = TollRed, modifier = Modifier.padding(vertical = 8.dp)) }

            LazyColumn {
                item {
                    ResultRow(Icons.Default.LocationOn, "현재 위치", "GPS로 현재 위치 사용") {
                        val granted = ContextCompat.checkSelfPermission(
                            context, Manifest.permission.ACCESS_FINE_LOCATION,
                        ) == PackageManager.PERMISSION_GRANTED
                        if (granted) vm.useCurrentLocation(target, onBack)
                        else permLauncher.launch(
                            arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION),
                        )
                    }
                }
                if (vm.searchResults.isEmpty() && vm.recentGoals.isNotEmpty()) {
                    item { Text("최근 목적지", color = Color.Gray, fontSize = 13.sp, modifier = Modifier.padding(top = 12.dp)) }
                    items(vm.recentGoals) { p ->
                        ResultRow(Icons.Default.Place, p.name, p.address) { vm.setPlace(target, p); onBack() }
                    }
                }
                items(vm.searchResults) { p ->
                    ResultRow(Icons.Default.Place, p.name, p.address) { vm.setPlace(target, p); onBack() }
                }
            }
        }
    }
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
            SectionTitle("API 키")
            Text(
                "앱들이 내부 예상시간을 외부에 공개하지 않기 때문에, 각 사의 공식 경로 API로 같은 엔진의 예상 시간을 조회합니다. " +
                    "키가 없는 앱은 비교에서 빠집니다.",
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
