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
        assertEquals(2, n.last().beats)
    }

    @Test
    fun octaveSharpAndHiragana() {
        assertEquals(listOf(12, 24, 6, 5, 0), Songs.parse("ド+ ド++ ファ# ふぁ ど").map { it.key })
        assertEquals("ファ♯", Songs.parse("ファ＃").single().name)
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
