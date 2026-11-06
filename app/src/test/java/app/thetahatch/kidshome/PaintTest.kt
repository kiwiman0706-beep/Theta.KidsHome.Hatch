package app.thetahatch.kidshome

import app.thetahatch.kidshome.data.Brush
import app.thetahatch.kidshome.data.Paintings
import app.thetahatch.kidshome.data.Stamp
import app.thetahatch.kidshome.data.Stroke
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
        // かたむけた スタンプ（ふるい 絵 は r が ない → 0）
        val rot = Stamp(10f, 20f, "", 64f, "abc", 45f)
        assertEquals(rot, Paintings.fromJson(Paintings.toJson(rot)))
    }
}
