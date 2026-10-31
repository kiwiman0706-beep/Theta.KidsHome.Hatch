package com.theta.kidshome

import com.theta.kidshome.data.Hissan
import org.junit.Assert.assertEquals
import org.junit.Test

class HissanTest {
    /** 表 を 文字 に（小さい 行 は 小文字 の 印 '^'、消す 数字 は '/'、線 は '-'） */
    private fun text(s: Hissan.Sheet): String = buildString {
        s.rows.forEach { r ->
            r.rule?.let { rule -> append((0 until s.cols).joinToString("") { if (it in rule) "-" else " " }.trimEnd()).append('\n') }
            append(r.cells.joinToString("") { c ->
                when {
                    c == null -> " "
                    c.kind == Hissan.Kind.STRUCK -> "/"
                    c.text.length > 1 -> "#"
                    else -> c.text
                }
            }.trimEnd().let { if (r.small) "$it ^" else it }).append('\n')
        }
    }

    @Test fun addWithCarry() {
        val s = Hissan.add(478, 256)
        assertEquals("734", s.answer)
        assertEquals(" 11 ^\n 478\n+256\n----\n 734\n", text(s))
    }

    @Test fun subWithBorrowAcrossZero() {
        val s = Hissan.sub(503, 7)
        assertEquals("496", s.answer)
        assertEquals(" 49# ^\n ///\n−  7\n----\n 496\n", text(s))
        assertEquals("-5", Hissan.sub(3, 8).answer)
    }

    @Test fun mulTwoDigits() {
        val s = Hissan.mul(23, 45)
        assertEquals("1035", s.answer)
        assertEquals("  23\n× 45\n----\n 115\n 92\n----\n1035\n", text(s))
    }

    @Test fun longDivision() {
        val s = Hissan.div(756, 3)
        assertEquals("252", s.answer)
        assertEquals("  252\n  ---\n3)756\n  6\n  -\n  15\n  15\n  --\n    6\n    6\n    -\n    0\n", text(s))
        assertEquals("4 あまり 1", Hissan.div(9, 2).answer)
        assertEquals("0 あまり 3", Hissan.div(3, 7).answer)
        assertEquals("10 あまり 1", Hissan.div(41, 4).answer)
    }
}
