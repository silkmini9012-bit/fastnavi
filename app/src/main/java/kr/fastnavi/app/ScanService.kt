package kr.fastnavi.app

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.graphics.Rect
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.view.accessibility.AccessibilityWindowInfo
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/** 접근성 서비스 연결 상태를 앱 어디서나 볼 수 있게 */
object ScanBus {
    var service by mutableStateOf<ScanService?>(null)

    fun isEnabledInSettings(context: Context): Boolean {
        val enabled = Settings.Secure.getString(
            context.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
        ).orEmpty()
        return enabled.contains("${context.packageName}/", ignoreCase = true)
    }
}

/**
 * 세 내비 앱의 결과 화면 글자를 읽고, 필요하면 '안내 시작' 버튼을 눌러주는 접근성 서비스.
 * 화면 내용은 기기 밖으로 보내지 않고 비교에만 사용한다.
 */
class ScanService : AccessibilityService() {

    override fun onServiceConnected() {
        super.onServiceConnected()
        ScanBus.service = this
    }

    override fun onUnbind(intent: Intent?): Boolean {
        if (ScanBus.service === this) ScanBus.service = null
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        if (ScanBus.service === this) ScanBus.service = null
        super.onDestroy()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {}
    override fun onInterrupt() {}

    fun foregroundPackage(): String? =
        runCatching { rootInActiveWindow?.packageName?.toString() }.getOrNull()

    private fun roots(pkg: String): List<AccessibilityNodeInfo> {
        val list = mutableListOf<AccessibilityNodeInfo>()
        runCatching {
            windows.filter { it.type == AccessibilityWindowInfo.TYPE_APPLICATION }
                .mapNotNull { it.root }
                .filter { it.packageName?.toString() == pkg }
                .forEach { list += it }
        }
        if (list.isEmpty()) {
            runCatching { rootInActiveWindow }.getOrNull()
                ?.takeIf { it.packageName?.toString() == pkg }?.let { list += it }
        }
        return list
    }

    /** 화면에 보이는 글자(텍스트 + 음성안내용 설명)를 화면 순서대로 */
    fun collectTexts(pkg: String): List<String> {
        val out = mutableListOf<String>()
        roots(pkg).forEach { walk(it, out, 0) }
        return out
    }

    private fun walk(n: AccessibilityNodeInfo?, out: MutableList<String>, depth: Int) {
        if (n == null || depth > 80 || out.size > 2000) return
        if (n.isVisibleToUser) {
            val t = n.text?.toString()?.trim().orEmpty()
            val d = n.contentDescription?.toString()?.trim().orEmpty()
            if (t.isNotEmpty()) out += t
            if (d.isNotEmpty() && d != t) out += d
        }
        for (i in 0 until n.childCount) walk(n.getChild(i), out, depth + 1)
    }

    /** 글자가 regex 와 맞는 버튼을 찾아 누름 */
    fun clickMatching(pkg: String, regex: Regex): Boolean {
        for (r in roots(pkg)) {
            val node = find(r, regex, 0) ?: continue
            var c: AccessibilityNodeInfo? = node
            while (c != null && !c.isClickable) c = c.parent
            if (c?.performAction(AccessibilityNodeInfo.ACTION_CLICK) == true) return true
        }
        return false
    }

    private fun find(n: AccessibilityNodeInfo?, regex: Regex, depth: Int): AccessibilityNodeInfo? {
        if (n == null || depth > 80) return null
        if (n.isVisibleToUser) {
            val t = n.text?.toString().orEmpty()
            val d = n.contentDescription?.toString().orEmpty()
            if (regex.matches(t.trim()) || regex.matches(d.trim())) return n
        }
        for (i in 0 until n.childCount) find(n.getChild(i), regex, depth + 1)?.let { return it }
        return null
    }

    // ───────────── 경로 카드 넘기기 (무료도로 등 다른 경로 읽기용) ─────────────

    private val durRe = Regex("\\d+[\\s¦]*분")

    private fun hasDuration(n: AccessibilityNodeInfo) =
        n.isVisibleToUser && (durRe.containsMatchIn(n.text ?: "") || durRe.containsMatchIn(n.contentDescription ?: ""))

    /** 소요시간 글자를 품고 있는 가장 안쪽의 스크롤 가능한 목록(= 경로 카드 목록) */
    private fun cardList(n: AccessibilityNodeInfo?, depth: Int): Pair<Boolean, AccessibilityNodeInfo?> {
        if (n == null || depth > 80) return false to null
        var has = hasDuration(n)
        var found: AccessibilityNodeInfo? = null
        for (i in 0 until n.childCount) {
            val (h, f) = cardList(n.getChild(i), depth + 1)
            if (h) has = true
            if (found == null && f != null) found = f
        }
        if (found == null && has && n.isScrollable) found = n
        return has to found
    }

    /** 화면 아래쪽(카드 영역)에 있는 소요시간 글자 위치 */
    private fun lowestDurationBounds(pkg: String): Rect? {
        var best: Rect? = null
        fun walk(n: AccessibilityNodeInfo?, d: Int) {
            if (n == null || d > 80) return
            if (hasDuration(n)) {
                val r = Rect(); n.getBoundsInScreen(r)
                if (best == null || r.top > best!!.top) best = r
            }
            for (i in 0 until n.childCount) walk(n.getChild(i), d + 1)
        }
        roots(pkg).forEach { walk(it, 0) }
        return best
    }

    /** 다음(forward=true) / 이전 경로 카드로 넘김 */
    suspend fun moveRouteCard(pkg: String, forward: Boolean): Boolean {
        val action = if (forward) AccessibilityNodeInfo.ACTION_SCROLL_FORWARD else AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD
        for (r in roots(pkg)) {
            val list = cardList(r, 0).second
            if (list != null && list.performAction(action)) return true
        }
        // 스크롤 동작을 지원하지 않으면 카드 높이에서 손가락으로 밀기
        val b = lowestDurationBounds(pkg) ?: return false
        val w = resources.displayMetrics.widthPixels.toFloat()
        val y = b.centerY().toFloat()
        return if (forward) swipe(w * 0.85f, y, w * 0.15f, y) else swipe(w * 0.15f, y, w * 0.85f, y)
    }

    private suspend fun swipe(x1: Float, y1: Float, x2: Float, y2: Float): Boolean =
        suspendCancellableCoroutine { cont ->
            val path = Path().apply { moveTo(x1, y1); lineTo(x2, y2) }
            val g = GestureDescription.Builder()
                .addStroke(GestureDescription.StrokeDescription(path, 0, 220))
                .build()
            val ok = dispatchGesture(g, object : GestureResultCallback() {
                override fun onCompleted(gestureDescription: GestureDescription?) { if (cont.isActive) cont.resume(true) }
                override fun onCancelled(gestureDescription: GestureDescription?) { if (cont.isActive) cont.resume(false) }
            }, null)
            if (!ok && cont.isActive) cont.resume(false)
        }
}
