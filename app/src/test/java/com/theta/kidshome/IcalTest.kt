package com.theta.kidshome

import com.theta.kidshome.data.Ical
import com.theta.kidshome.media.Sampler
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.TimeZone

class IcalTest {
    private val ics = """
BEGIN:VCALENDAR
BEGIN:VEVENT
DTSTART;TZID=Asia/Tokyo:20261030T090000
DTEND;TZID=Asia/Tokyo:20261030T100000
SUMMARY:えんそく
END:VEVENT
BEGIN:VEVENT
DTSTART;VALUE=DATE:20261103
DTEND;VALUE=DATE:20261104
SUMMARY:ぶんかのひ
END:VEVENT
BEGIN:VEVENT
DTSTART:20261005T060000Z
DTEND:20261005T070000Z
RRULE:FREQ=WEEKLY;BYDAY=MO,WE;UNTIL=20261031T000000Z
EXDATE:20261007T060000Z
SUMMARY:ピアノ\, ならいごと
END:VEVENT
BEGIN:VEVENT
DTSTART:20261001T000000Z
STATUS:CANCELLED
SUMMARY:なし
END:VEVENT
END:VCALENDAR
""".trimIndent()

    @Test
    fun parsesEventsAndRecurrence() {
        val from = Ical.time("20261001T000000Z", false, TimeZone.getDefault())!!
        val to = Ical.time("20261110T000000Z", false, TimeZone.getDefault())!!
        val ev = Ical.parse(ics, from, to)
        assertEquals(Ical.time("20261030T000000Z", false, TimeZone.getDefault())!!, ev.first { it.title == "えんそく" }.begin)
        assertTrue(ev.first { it.title == "ぶんかのひ" }.allDay)
        // 10 月の月・水（5,7,12,14,19,21,26,28）から 7 日を除いて 7 回
        assertEquals(7, ev.count { it.title == "ピアノ, ならいごと" })
        assertTrue(ev.none { it.title == "なし" })
    }

    @Test
    fun samplerTrimsSilenceAndNormalizes() {
        val n = 22050
        val pcm = ShortArray(n) { i -> if (i < 5000) 10 else (8000 * kotlin.math.sin(i / 10.0)).toInt().toShort() }
        val out = Sampler.shape(pcm, n)
        assertTrue(out.size < n - 4000)
        assertTrue(out.maxOf { kotlin.math.abs(it) } in 0.85f..0.91f)
    }
}
