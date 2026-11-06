package app.thetahatch.kidshome

import app.thetahatch.kidshome.data.SongSync
import app.thetahatch.kidshome.data.SongSync.Entry
import org.junit.Assert.assertEquals
import org.junit.Test

class SongSyncTest {
    @Test fun mergeKeepsNewest() {
        val a = mapOf("かえる" to Entry("ドレミ", 10, false), "ぞう" to Entry("ソミ", 10, false))
        val b = mapOf("かえる" to Entry("ドレミ", 20, true), "はと" to Entry("ララ", 5, false), "ぞう" to Entry("ソソ", 5, false))
        val m = SongSync.merge(a, b)
        assertEquals(true, m.getValue("かえる").deleted)   // あと で けした
        assertEquals("ソミ", m.getValue("ぞう").notes)      // こちら が あたらしい
        assertEquals("ララ", m.getValue("はと").notes)      // むこう で 足した
    }
}
