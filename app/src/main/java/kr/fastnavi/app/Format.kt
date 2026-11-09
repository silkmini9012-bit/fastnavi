package kr.fastnavi.app

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object Fmt {
    fun duration(sec: Int): String {
        val m = (sec + 30) / 60
        return if (m >= 60) "${m / 60}시간 ${m % 60}분" else "${m}분"
    }

    fun diff(sec: Int): String {
        val m = (kotlin.math.abs(sec) + 30) / 60
        return if (sec >= 0) "+${m}분" else "-${m}분"
    }

    fun eta(durationSec: Int, from: Long = System.currentTimeMillis()): String =
        SimpleDateFormat("a h:mm", Locale.KOREAN).format(Date(from + durationSec * 1000L))

    fun distance(m: Int): String =
        if (m >= 1000) String.format(Locale.KOREA, "%.1fkm", m / 1000.0) else "${m}m"

    fun won(v: Int): String = String.format(Locale.KOREA, "%,d원", v)
}
