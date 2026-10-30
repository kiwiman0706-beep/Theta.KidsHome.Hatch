package com.theta.kidshome.data

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import java.util.TimeZone

/**
 * iCal（.ics）の予定を読む。Google カレンダーの「iCal 形式の非公開 URL」を想定。
 * くりかえし（RRULE の DAILY・WEEKLY・MONTHLY・YEARLY、INTERVAL・COUNT・UNTIL・BYDAY）と EXDATE に対応。
 * 終日の予定は CalendarContract と同じく UTC の 0 時で持つ。
 */
object Ical {
    private data class Raw(
        val summary: String, val start: Long, val end: Long, val allDay: Boolean,
        val tz: TimeZone, val rrule: Map<String, String>?, val exdates: Set<Long>,
    )

    fun parse(text: String, from: Long, to: Long, color: Int = 0xFF4DA3FF.toInt()): List<CalEvent> {
        // 折り返し行をつなぐ
        val lines = text.replace("\r\n", "\n").replace("\n ", "").replace("\n\t", "").split('\n')
        val out = mutableListOf<CalEvent>()
        var cur: MutableMap<String, MutableList<Pair<String, String>>>? = null
        for (line in lines) {
            when {
                line == "BEGIN:VEVENT" -> cur = mutableMapOf()
                line == "END:VEVENT" -> {
                    cur?.let { toRaw(it) }?.let { out += expand(it, from, to, color) }
                    cur = null
                }
                cur != null -> {
                    val colon = line.indexOf(':')
                    if (colon <= 0) continue
                    val head = line.substring(0, colon)
                    val name = head.substringBefore(';').uppercase()
                    val params = head.substringAfter(';', "")
                    cur.getOrPut(name) { mutableListOf() } += params to line.substring(colon + 1)
                }
            }
        }
        return out.sortedBy { it.begin }
    }

    private fun unescape(s: String) = s.replace("\\n", " ").replace("\\,", ",").replace("\\;", ";").replace("\\\\", "\\")

    private fun toRaw(m: Map<String, List<Pair<String, String>>>): Raw? {
        if (m["STATUS"]?.firstOrNull()?.second == "CANCELLED") return null
        val (sp, sv) = m["DTSTART"]?.firstOrNull() ?: return null
        val allDay = sp.contains("VALUE=DATE") || sv.length == 8
        val tz = Regex("TZID=([^;]+)").find(sp)?.groupValues?.get(1)?.let { TimeZone.getTimeZone(it) } ?: TimeZone.getDefault()
        val start = time(sv, allDay, tz) ?: return null
        val end = m["DTEND"]?.firstOrNull()?.let { (p, v) -> time(v, allDay, Regex("TZID=([^;]+)").find(p)?.groupValues?.get(1)?.let { TimeZone.getTimeZone(it) } ?: tz) }
            ?: (start + if (allDay) 86_400_000L else 3_600_000L)
        val rrule = m["RRULE"]?.firstOrNull()?.second?.split(';')?.mapNotNull { kv ->
            kv.split('=', limit = 2).takeIf { it.size == 2 }?.let { it[0].uppercase() to it[1] }
        }?.toMap()
        val ex = m["EXDATE"].orEmpty().flatMap { (p, v) ->
            val exTz = Regex("TZID=([^;]+)").find(p)?.groupValues?.get(1)?.let { TimeZone.getTimeZone(it) } ?: tz
            v.split(',').mapNotNull { time(it, allDay, exTz) }
        }.toSet()
        return Raw(unescape(m["SUMMARY"]?.firstOrNull()?.second ?: "（よてい）"), start, end, allDay, tz, rrule, ex)
    }

    /** 20261030T090000Z・20261030T090000（TZID）・20261030（終日：UTC の 0 時） */
    fun time(v: String, allDay: Boolean, tz: TimeZone): Long? = runCatching {
        val s = v.trim()
        when {
            s.length == 8 -> fmt("yyyyMMdd", TimeZone.getTimeZone("UTC")).parse(s)!!.time
            s.endsWith("Z") -> fmt("yyyyMMdd'T'HHmmss'Z'", TimeZone.getTimeZone("UTC")).parse(s)!!.time
            else -> fmt("yyyyMMdd'T'HHmmss", if (allDay) TimeZone.getTimeZone("UTC") else tz).parse(s)!!.time
        }
    }.getOrNull()

    private fun fmt(p: String, tz: TimeZone) = SimpleDateFormat(p, Locale.US).apply { timeZone = tz }

    private val days = mapOf("SU" to Calendar.SUNDAY, "MO" to Calendar.MONDAY, "TU" to Calendar.TUESDAY, "WE" to Calendar.WEDNESDAY,
        "TH" to Calendar.THURSDAY, "FR" to Calendar.FRIDAY, "SA" to Calendar.SATURDAY)

    private fun expand(r: Raw, from: Long, to: Long, color: Int): List<CalEvent> {
        val dur = r.end - r.start
        fun ev(s: Long) = CalEvent(r.summary, s, s + dur, r.allDay, color)
        val rule = r.rrule ?: return if (r.start < to && r.end > from) listOf(ev(r.start)) else emptyList()
        val freq = rule["FREQ"] ?: return emptyList()
        val interval = rule["INTERVAL"]?.toIntOrNull()?.coerceAtLeast(1) ?: 1
        val count = rule["COUNT"]?.toIntOrNull()
        val until = rule["UNTIL"]?.let { time(it, it.length == 8, r.tz) }
        val byDay = rule["BYDAY"]?.split(',')?.mapNotNull { days[it.takeLast(2)] }?.toSet().orEmpty()
        val zone = if (r.allDay) TimeZone.getTimeZone("UTC") else r.tz
        val cal = Calendar.getInstance(zone).apply { timeInMillis = r.start }
        val out = mutableListOf<CalEvent>()
        var n = 0
        var guard = 0
        fun take(t: Long): Boolean {
            if (until != null && t > until) return false
            if (count != null && n >= count) return false
            n++
            if (t !in r.exdates && t < to && t + dur > from) out += ev(t)
            return true
        }
        while (guard++ < 3000 && cal.timeInMillis < to) {
            if (freq == "WEEKLY" && byDay.isNotEmpty()) {
                // その週の、えらばれた ようび ぜんぶ
                val week = (cal.clone() as Calendar).apply { set(Calendar.DAY_OF_WEEK, firstDayOfWeek) }
                var stop = false
                for (i in 0 until 7) {
                    val d = (week.clone() as Calendar).apply { add(Calendar.DAY_OF_MONTH, i) }
                    if (d.get(Calendar.DAY_OF_WEEK) in byDay && d.timeInMillis >= r.start) {
                        if (!take(d.timeInMillis)) { stop = true; break }
                    }
                }
                if (stop) break
                cal.add(Calendar.WEEK_OF_YEAR, interval)
            } else {
                if (!take(cal.timeInMillis)) break
                when (freq) {
                    "DAILY" -> cal.add(Calendar.DAY_OF_MONTH, interval)
                    "WEEKLY" -> cal.add(Calendar.WEEK_OF_YEAR, interval)
                    "MONTHLY" -> cal.add(Calendar.MONTH, interval)
                    "YEARLY" -> cal.add(Calendar.YEAR, interval)
                    else -> break
                }
            }
        }
        return out
    }
}
