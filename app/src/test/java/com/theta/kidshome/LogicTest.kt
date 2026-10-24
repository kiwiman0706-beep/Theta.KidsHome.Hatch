package com.theta.kidshome

import com.theta.kidshome.data.Launcher
import com.theta.kidshome.remote.Remote
import com.theta.kidshome.ui.GateQuestion
import com.theta.kidshome.update.isNewer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class LogicTest {
    @Test
    fun versions() {
        assertTrue(isNewer("0.3.0", "0.2.0"))
        assertTrue(isNewer("1.10.0", "1.9.2"))
        assertFalse(isNewer("0.2.0-alpha", "0.2.0"))
        assertFalse(isNewer("0.1.9", "0.2.0"))
    }

    @Test
    fun gateIsNotTimesTable() {
        val r = Random(1)
        repeat(500) {
            val easy = GateQuestion.next(false, r)
            assertTrue(easy.a in 12..98 && easy.a % 10 != 0 && easy.b in 3..9)
            val hard = GateQuestion.next(true, r)
            assertTrue(hard.b in 12..98 && hard.b % 10 != 0)
        }
    }

    @Test
    fun detectsServices() {
        assertEquals("com.netflix.mediaclient", Launcher.detectService("https://www.netflix.com/title/80117470")?.packageName)
        assertEquals("com.amazon.avod.thirdpartyclient", Launcher.detectService("https://www.amazon.co.jp/gp/video/detail/B0ABC")?.packageName)
        assertEquals("com.google.android.apps.youtube.kids", Launcher.detectService("https://www.youtubekids.com/watch?v=x")?.packageName)
        assertNull(Launcher.detectService("https://example.com/"))
        assertEquals(
            "https://www.netflix.com/title/81",
            Launcher.extractUrl("「ティンピン」をNetflixで見よう https://www.netflix.com/title/81"),
        )
    }

    @Test
    fun remoteCommands() {
        assertEquals("そろそろ おわりだよ", Remote.parse(" そろそろ おわりだよ ").message)
        val lock = Remote.parse("/lock 45 ごはんだよ")
        assertEquals(45, lock.lockMinutes)
        assertEquals("ごはんだよ", lock.message)
        assertEquals(30, Remote.parse("/LOCK").lockMinutes)
        assertEquals(30, Remote.parse("/ロック").lockMinutes)
        assertTrue(Remote.parse("/unlock").unlock)
        assertNull(Remote.parse("こんにちは").lockMinutes)
        assertTrue(Remote.newTopic().matches(Regex("kidshome-[a-z0-9]{16}")))
    }
}
