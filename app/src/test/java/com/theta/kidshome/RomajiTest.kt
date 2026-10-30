package com.theta.kidshome

import com.theta.kidshome.data.TypingMatcher
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RomajiTest {
    private fun typeAll(word: String, keys: String): TypingMatcher {
        val m = TypingMatcher(word)
        keys.forEach { assertTrue("$word: '$it' in $keys", m.type(it)) }
        return m
    }

    @Test
    fun acceptsCommonSpellings() {
        assertTrue(typeAll("しんかんせん", "shinkansenn").done)
        assertTrue(typeAll("しんかんせん", "sinnkannsenn").done)
        assertTrue(typeAll("きって", "kitte").done)
        assertTrue(typeAll("きって", "kixtute").done)
        assertTrue(typeAll("ちょうちょ", "tyoutyo").done)
        assertTrue(typeAll("ちょうちょ", "chouchou".take(4) + "cho").done)
        assertTrue(typeAll("ふうせん", "huusenn").done)
        assertTrue(typeAll("ほんや", "honnya").done)
        assertTrue(typeAll("らーめん", "ra-menn").done)
    }

    @Test
    fun rejectsWrongKeysAndGuides() {
        val m = TypingMatcher("いぬ")
        assertFalse(m.type('u'))
        assertEquals('i', m.nextKey())
        assertTrue(m.type('i'))
        assertEquals("inu", m.guide())
        assertEquals('n', m.nextKey())
        // 最後の ん は nn まで待つ
        val n = TypingMatcher("ぱん")
        "pan".forEach { assertTrue(n.type(it)) }
        assertFalse(n.done)
        assertTrue(n.type('n'))
        assertTrue(n.done)
    }
}
