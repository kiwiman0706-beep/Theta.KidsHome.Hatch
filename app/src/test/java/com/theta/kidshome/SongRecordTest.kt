package com.theta.kidshome

import com.theta.kidshome.media.Songs
import org.junit.Assert.assertEquals
import org.junit.Test

class SongRecordTest {
    @Test fun formatRoundTrip() {
        val notes = listOf(0 to 1f, 2 to 0.5f, 4 to 1.5f, 6 to 0.5f, -1 to 0.5f, 12 to 2f, 24 to 3.5f)
        val text = Songs.format(notes)
        assertEquals("ドレ_ミ・ファ♯_ん_ド＋ード＋＋ーー・", text)
        assertEquals(notes, Songs.parse(text).map { it.key to it.beats })
    }

    @Test fun eighthNotesWithTempo() {
        // ♩=120（1 はく 500ms）：タタ タ ー（8分 8分 4分 2分）
        val keys = listOf(0, 2, 4, 5)
        val downs = listOf(0L, 260, 490, 1010)
        val ups = listOf<Long?>(200, 450, 950, 1990)
        assertEquals(listOf(0 to 0.5f, 2 to 0.5f, 4 to 1f, 5 to 2f), Songs.fromTaps(keys, downs, ups, bpm = 120))
    }

    @Test fun restsWhenReleasedEarly() {
        // ド を すぐ はなして 1 はく あけて レ
        val r = Songs.fromTaps(listOf(0, 2), listOf(0L, 1000), listOf(240L, 1500), bpm = 120)
        assertEquals(listOf(0 to 0.5f, -1 to 1.5f, 2 to 1f), r)
        assertEquals("ド_ん・レ", Songs.format(r))
    }

    @Test fun tapsWithoutTempo() {
        val keys = listOf(0, 0, 7, 7, 9, 9, 7, 5)
        val times = listOf(0L, 510, 990, 1500, 2020, 2490, 3000, 4010)
        assertEquals("ドドソソララソー ファ", Songs.format(Songs.fromTaps(keys, times)))
    }
}
