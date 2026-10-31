package com.theta.kidshome

import com.theta.kidshome.media.Kit
import com.theta.kidshome.media.Tones
import com.theta.kidshome.media.Voice
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class ToneTest {
    private fun check(name: String, data: FloatArray) {
        val peak = data.maxOf { abs(it) }
        assertTrue("$name is silent ($peak)", peak > 0.15f)
        assertTrue("$name clips ($peak)", peak <= 1.0f)
        assertTrue("$name has NaN", data.none { it.isNaN() })
        // 終わりは無音（プチッとしない）
        assertTrue("$name ends loud", abs(data.last()) < 0.01f)
    }

    @Test
    fun voicesAreAudibleAndDoNotClip() {
        Voice.entries.forEach { v -> for (k in 0 until 25) check("$v/$k", Tones.note(v, 60 + k)) }
    }

    @Test
    fun padsAreAudibleAndDoNotClip() {
        Kit.entries.filter { it != Kit.MINE }.forEach { kit -> for (p in kit.pads.indices) check("$kit/$p", Tones.drum(kit, p)) }
    }
}
