package com.theta.kidshome

import com.theta.kidshome.media.Songs
import org.junit.Assert.assertEquals
import org.junit.Test

class SongRecordTest {
    @Test fun formatRoundTrip() {
        val notes = listOf(0 to 1, 2 to 1, 4 to 2, 6 to 1, 12 to 1, 24 to 3)
        val text = Songs.format(notes)
        assertEquals("ドレミーファ♯ド＋ド＋＋ーー", text)
        assertEquals(notes, Songs.parse(text).map { it.key to it.beats })
    }

    @Test fun tapsToBeats() {
        // きらきらぼし の はじめ：ド ド ソ ソ ラ ラ ソー（500ms で 1 はく、すこし ずれる）
        val keys = listOf(0, 0, 7, 7, 9, 9, 7, 5)
        val times = listOf(0L, 510, 990, 1500, 2020, 2490, 3000, 4010)
        assertEquals(listOf(1, 1, 1, 1, 1, 1, 2, 1), Songs.fromTaps(keys, times).map { it.second })
        assertEquals("ドドソソララソー ファ", Songs.format(Songs.fromTaps(keys, times)))
    }
}
