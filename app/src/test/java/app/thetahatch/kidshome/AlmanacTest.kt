package app.thetahatch.kidshome

import app.thetahatch.kidshome.data.Almanac
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar
import java.util.TimeZone

class AlmanacTest {
    private fun names(y: Int) = Almanac.holidays(y).map { "%d/%d %s".format(it.month, it.day, it.name) }

    @Test fun holidays2025() {
        val h = names(2025)
        assertTrue(h.toString(), "1/13 成人の日" in h)
        assertTrue("2/24 振替休日" in h)        // 2/23 が 日曜
        assertTrue("3/20 春分の日" in h)
        assertTrue("5/6 振替休日" in h)         // 5/4 が 日曜
        assertTrue("9/15 敬老の日" in h)
        assertTrue("9/23 秋分の日" in h)
        assertTrue("11/24 振替休日" in h)       // 11/23 が 日曜
        assertEquals(19, h.size)
    }

    @Test fun silverWeek2026() {
        // 2026：敬老の日 9/21、秋分 9/23 → 9/22 国民の休日
        val h = names(2026)
        assertTrue(h.toString(), "9/22 国民の休日" in h)
    }

    @Test fun moon() {
        val c = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply { clear(); set(2024, Calendar.JANUARY, 25, 18, 0) } // 満月
        assertEquals("まんげつ", Almanac.moon(c.timeInMillis).name)
        c.set(2024, Calendar.FEBRUARY, 9, 23, 0) // 新月
        assertEquals("しんげつ", Almanac.moon(c.timeInMillis).name)
    }

    @Test fun clock() {
        assertEquals("さんじ じゅうごふん", Almanac.readTime(15, 15))
        assertEquals("しちじ さんじゅっぷん（しちじ はん）", Almanac.readTime(7, 30))
        assertEquals("よじ いっぷん", Almanac.readTime(4, 1))
        assertEquals("くじ ちょうど", Almanac.readTime(9, 0))
        assertEquals("じゅうにじ ごじゅうはっぷん", Almanac.readTime(0, 58))
        assertEquals("ろくじ よんじゅっぷん", Almanac.readTime(18, 40))
    }

    @Test fun eto() {
        assertEquals("うま", Almanac.eto(2026).animal)
        assertEquals("へび", Almanac.eto(2025).animal)
        assertEquals("ねずみ", Almanac.eto(2020).animal)
    }

    @Test fun sekki() {
        assertEquals("立冬", Almanac.sekki(11, 8).first)
        assertEquals("冬至", Almanac.sekki(1, 2).first)
    }
}
