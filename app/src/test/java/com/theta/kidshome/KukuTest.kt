package com.theta.kidshome

import com.theta.kidshome.data.Kuku
import org.junit.Assert.assertEquals
import org.junit.Test

class KukuTest {
    @Test fun answersMatchNumbers() {
        for (a in 1..9) for (b in 1..9) {
            val l = Kuku.line(a, b)
            assertEquals(a, l.a); assertEquals(b, l.b)
            assertEquals("$a×$b", Kuku.numberReading(a * b), l.answer)
        }
    }

    @Test fun readings() {
        assertEquals("さざんが く", Kuku.line(3, 3).text)
        assertEquals("ハッパ ロクジュウシ", Kuku.line(8, 8).speech)
        assertEquals(9, Kuku.column(4).size)
        assertEquals("しちし にじゅうはち", Kuku.column(4)[6].text)
    }
}
