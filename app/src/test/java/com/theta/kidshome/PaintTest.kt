package com.theta.kidshome

import com.theta.kidshome.data.Brush
import com.theta.kidshome.data.Paintings
import com.theta.kidshome.data.Stamp
import com.theta.kidshome.data.Stroke
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PaintTest {
    @Test
    fun marksRoundTrip() {
        val s = Stroke(floatArrayOf(1.25f, 2f, 30f, 40.5f), 0xFFFF0000.toInt(), 14f, Brush.CRAYON, 42)
        val back = Paintings.fromJson(Paintings.toJson(s)) as Stroke
        assertTrue(back.points.contentEquals(floatArrayOf(1.3f, 2f, 30f, 40.5f)) || back.points.contentEquals(s.points))
        assertEquals(Brush.CRAYON, back.brush)
        assertEquals(42, back.seed)
        val st = Stamp(10f, 20f, "⭐", 64f)
        assertEquals(st, Paintings.fromJson(Paintings.toJson(st)))
    }
}
