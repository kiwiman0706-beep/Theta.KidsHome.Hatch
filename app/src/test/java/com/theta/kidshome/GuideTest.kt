package com.theta.kidshome

import com.theta.kidshome.data.GuidePack
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GuideTest {
    @Test fun manifest() {
        val m = GuidePack.parse(
            """{"name":"ガイドちゃん","version":"0.2.0","lines":{"tap":["えへへ"]},
               "slots":[{"id":"tops","name":"ふく","items":[{"id":"a","name":"いつもの"},{"id":"b","name":"ピンク","emoji":"🩷","thumbnail":"thumbs/b.png"}]},
                        {"id":"hat","name":"ぼうし","optional":true,"items":[]}]}""",
        )
        assertEquals("ガイドちゃん", m.name)
        assertEquals(listOf("えへへ"), m.tap)
        assertTrue(m.greet.isEmpty())
        assertEquals(2, m.slots.size)
        assertEquals("👗", m.slots[0].items[0].emoji)
        assertEquals("thumbs/b.png", m.slots[0].items[1].thumbnail)
        assertTrue(m.slots[1].optional)
    }
}
