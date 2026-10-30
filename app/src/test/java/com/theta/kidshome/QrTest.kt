package com.theta.kidshome

import com.theta.kidshome.data.QrSetup
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class QrTest {
    private val sheet = "https://docs.google.com/spreadsheets/d/1AbC_dEf-123/edit?usp=sharing"
    private val key = "AIzaSyD-abcdefghijklmnopqrstuvwxyz01234"
    private val ical = "https://calendar.google.com/calendar/ical/abc%40group.calendar.google.com/private-xyz/basic.ics"

    @Test fun single() {
        assertEquals(sheet, QrSetup.parse(" $sheet\n").sheetUrl)
        assertEquals(key, QrSetup.parse(key).geminiKey)
        assertEquals(listOf(ical), QrSetup.parse(ical).calendarUrls)
        assertEquals(listOf("webcal://example.com/cal"), QrSetup.parse("webcal://example.com/cal").calendarUrls)
        assertTrue(QrSetup.parse("https://example.com/").isEmpty)
        assertTrue(QrSetup.parse("AIza short").isEmpty)
    }

    @Test fun roundTrip() {
        val s = QrSetup.Setup(sheet, key, "gemini-3.1-flash-image", listOf(ical, "webcal://x.example/a"))
        val back = QrSetup.parse(QrSetup.encode(s))
        assertEquals(s, back)
        assertEquals("スプレッドシート・Gemini キー・Gemini モデル・カレンダー 2こ", back.summary())
    }

    @Test fun guidePack() {
        val tok = "github_pat_11ABCDEFG0123456789_abcdefghijklmnopqrstuvwxyz"
        val s = QrSetup.parse("kidshome:setup?pack=kiwiman0706-beep%2FTheta.Kidshome.Hatch.Closed&packtoken=$tok")
        assertEquals("kiwiman0706-beep/Theta.Kidshome.Hatch.Closed", s.packRepo)
        assertEquals(tok, s.packToken)
        assertEquals(s, QrSetup.parse(QrSetup.encode(s)))
        assertEquals(tok, QrSetup.parse(tok).packToken)
        assertNull(QrSetup.parse("kidshome:setup?pack=..%2F..%2Fx%20y").packRepo)
    }

    @Test fun badValuesDropped() {
        val s = QrSetup.parse("kidshome:setup?sheet=https%3A%2F%2Fevil.example%2F&gemini=nope&model=a%20b&ical=javascript%3Ax")
        assertTrue(s.isEmpty)
        assertNull(s.sheetUrl)
    }
}
