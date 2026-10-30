package com.theta.kidshome

import com.theta.kidshome.data.DictText
import com.theta.kidshome.data.DictText.Block
import com.theta.kidshome.data.DictText.Span
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DictTextTest {
    @Test fun spans() {
        val s = DictText.spans("[音]デン(小2) → [[でん‐き【電気】|でんき]] と [[わ【和】]]、**名** H<sub>2</sub>O ![[kenkyusha_na13f.png|14]]")
        assertEquals(Span.Plain("[音]デン(小2) → "), s[0])
        assertEquals(Span.Link("でん‐き【電気】", "でんき"), s[1])
        assertEquals(Span.Link("わ【和】", "わ【和】"), s[3])
        assertTrue(s.contains(Span.Bold("名")))
        assertTrue(s.contains(Span.Small("2", sup = false)))
        assertEquals(Span.Gaiji("kenkyusha_na13f.png", 14), s.last())
    }

    @Test fun blocks() {
        val b = DictText.blocks("(1)柿の木。\n![[koujien_fig_0024.png]]\n- to apple\n  - りんごの\n本文 ![[x.png]] つづき")
        assertEquals(Block.Media("koujien_fig_0024.png"), b[1])
        assertEquals(1, (b[2] as Block.Para).depth)
        assertEquals(2, (b[3] as Block.Para).depth)
        assertEquals(Block.Media("x.png"), b[5])
    }

    @Test fun normalize() {
        assertEquals("あいんしゅたいん", DictText.normalize(" アインシュタイン "))
        assertEquals("apple", DictText.normalize("Apple"))
        assertEquals("電気", DictText.expand("〜", "電気"))
        assertEquals("名前", DictText.displayName("名前 (2)"))
    }
}
