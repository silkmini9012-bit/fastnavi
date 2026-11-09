package kr.fastnavi.app

import java.util.Calendar

/**
 * 내비 앱 결과 화면에서 읽은 글자들(화면 순서대로)로 경로 후보를 추출.
 * "32분", "1시간 5분" 이 나올 때마다 새 경로 후보로 보고,
 * 그 뒤에 나오는 도착시각 / 통행료 / 무료 / 거리를 그 후보에 붙인다.
 */
object RouteParser {

    data class Option(
        val durationMin: Int,
        val arrivalMillis: Long? = null,
        val toll: Int? = null,
        val distanceM: Int? = null,
    )

    private enum class T { DUR, ARR, TOLL, FREE, DIST }
    private data class Tok(val pos: Int, val end: Int, val type: T, val a: Int, val b: Int = 0, val label: String = "")

    private val DUR = Regex(
        "(?<!\\d)(?:(\\d{1,2})\\s*시간\\s*)?(\\d{1,3})\\s*분(?!\\s*(?:후|전|마다|간격|이내|이상|거리|정도\\s*후))" +
            "|(?<!\\d)(\\d{1,2})\\s*시간(?!\\s*(?:\\d|후|전|이내|이상))",
    )
    private val ARR_AMPM = Regex("(오전|오후)\\s*(\\d{1,2})\\s*[:시]\\s*(\\d{2})")
    private val ARR_24 = Regex("(?<![\\d:])(\\d{1,2}):(\\d{2})\\s*도착")
    private val TOLL = Regex("통행료\\s*[:：]?\\s*(?:약\\s*)?([\\d,]{1,9})\\s*원")
    private val FREE = Regex("통행료\\s*(?:없음|무료|0\\s*원)|무료\\s*도로|(?<![가-힣])무료(?![가-힣])")
    private val DIST = Regex("(?<![\\d.])(\\d{1,4}(?:\\.\\d{1,2})?)\\s*(km|㎞)")

    fun parse(texts: List<String>, now: Long = System.currentTimeMillis()): List<Option> {
        val s = texts.joinToString(" ¦ ")
        val toks = mutableListOf<Tok>()
        DUR.findAll(s).forEach { m ->
            val h = m.groupValues[1].toIntOrNull() ?: 0
            val min = m.groupValues[2].toIntOrNull()
            val onlyH = m.groupValues[3].toIntOrNull()
            val total = if (min != null) h * 60 + min else (onlyH ?: 0) * 60
            // 대중교통/도보/자전거 탭의 시간은 제외
            val before = s.substring(maxOf(0, m.range.first - 8), m.range.first)
            val otherMode = Regex("대중교통|도보|자전거|버스|지하철|기차|항공").containsMatchIn(before)
            if (total in 1..1440 && !otherMode) toks += Tok(m.range.first, m.range.last, T.DUR, total)
        }
        ARR_AMPM.findAll(s).forEach { m ->
            var hh = m.groupValues[2].toInt() % 12
            if (m.groupValues[1] == "오후") hh += 12
            toks += Tok(m.range.first, m.range.last, T.ARR, hh, m.groupValues[3].toInt())
        }
        ARR_24.findAll(s).forEach { m ->
            toks += Tok(m.range.first, m.range.last, T.ARR, m.groupValues[1].toInt(), m.groupValues[2].toInt())
        }
        TOLL.findAll(s).forEach { m ->
            m.groupValues[1].replace(",", "").toIntOrNull()?.let { toks += Tok(m.range.first, m.range.last, T.TOLL, it) }
        }
        FREE.findAll(s).forEach { m -> toks += Tok(m.range.first, m.range.last, T.FREE, 0) }
        DIST.findAll(s).forEach { m ->
            val km = m.groupValues[1].toDoubleOrNull() ?: return@forEach
            toks += Tok(m.range.first, m.range.last, T.DIST, (km * 1000).toInt())
        }
        // 겹치는 토큰 제거(앞에서 시작하는 것 우선): 예) "오후 4시 58분"의 "58분"
        toks.sortWith(compareBy<Tok> { it.pos }.thenByDescending { it.end })
        val clean = mutableListOf<Tok>()
        var lastEnd = -1
        for (t in toks) if (t.pos > lastEnd) { clean += t; lastEnd = t.end }

        val opts = mutableListOf<Option>()
        var pending = Option(0)
        fun attach(f: (Option) -> Option?) {
            val cur = opts.lastOrNull()
            val upd = cur?.let(f)
            if (cur != null && upd != null) opts[opts.size - 1] = upd
            else f(pending)?.let { pending = it }
        }
        for (t in clean) when (t.type) {
            T.DUR -> {
                opts += Option(t.a, pending.arrivalMillis, pending.toll, pending.distanceM)
                pending = Option(0)
            }
            T.ARR -> attach { o -> if (o.arrivalMillis == null) o.copy(arrivalMillis = clock(t.a, t.b, now)) else null }
            T.TOLL -> attach { o -> if (o.toll == null || o.toll == 0) o.copy(toll = t.a) else null }
            T.FREE -> attach { o -> if (o.toll == null) o.copy(toll = 0) else null }
            T.DIST -> attach { o -> if (o.distanceM == null) o.copy(distanceM = t.a) else null }
        }
        // 같은 시간이 연달아 나오면(지도 라벨 + 목록) 하나로 합침
        val merged = mutableListOf<Option>()
        for (o in opts) {
            val p = merged.lastOrNull()
            if (p != null && p.durationMin == o.durationMin) {
                merged[merged.size - 1] = p.copy(
                    arrivalMillis = p.arrivalMillis ?: o.arrivalMillis,
                    toll = p.toll ?: o.toll,
                    distanceM = p.distanceM ?: o.distanceM,
                )
            } else merged += o
        }
        return merged
    }

    /** 오늘 hh:mm 의 시각. 이미 한참 지났으면 내일로 */
    private fun clock(hh: Int, mm: Int, now: Long): Long {
        val c = Calendar.getInstance().apply {
            timeInMillis = now
            set(Calendar.HOUR_OF_DAY, hh); set(Calendar.MINUTE, mm)
            set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        }
        if (c.timeInMillis < now - 30 * 60_000) c.add(Calendar.DAY_OF_MONTH, 1)
        return c.timeInMillis
    }
}
