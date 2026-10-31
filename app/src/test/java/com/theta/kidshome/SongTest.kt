package com.theta.kidshome

import com.theta.kidshome.media.Songs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SongTest {
    @Test
    fun parsesDoReMi() {
        val n = Songs.parse("ドドソソララソー")
        assertEquals(listOf(0, 0, 7, 7, 9, 9, 7), n.map { it.key })
        assertEquals(2f, n.last().beats)
    }

    @Test
    fun octaveSharpAndHiragana() {
        assertEquals(listOf(12, 24, 6, 5, 0), Songs.parse("ド+ ド++ ファ# ふぁ ど").map { it.key })
        assertEquals("ファ♯", Songs.parse("ファ＃").single().name)
        // はちぶ・ふてん・やすみ
        assertEquals(listOf(0.5f, 1.5f, 1f, 0.75f), Songs.parse("ド_ ミ・ ん ソ_・").map { it.beats })
        assertEquals(-1, Songs.parse("ん").single().key)
    }

    @Test
    fun builtInSongsAreInRange() {
        Songs.builtIn.forEach { s ->
            val notes = Songs.parse(s.notes)
            assertTrue(s.title, notes.size >= 10)
            assertTrue(s.title, notes.all { it.key in 0..24 })
        }
    }
}
