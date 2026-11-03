package com.theta.kidshome

import com.theta.kidshome.data.Sugoroku
import com.theta.kidshome.data.Yuru
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import kotlin.random.Random

class SugorokuTest {
    @Test fun cellsAreUniqueAndSnake() {
        val cells = (0 until Sugoroku.SIZE).map { Sugoroku.cell(it) }
        assertEquals(Sugoroku.SIZE, cells.toSet().size)
        assertEquals(0 to Sugoroku.ROWS - 1, cells[0])
        // となり どうし は 1 マス だけ はなれて いる
        cells.zipWithNext().forEach { (a, b) -> assertEquals(1, kotlin.math.abs(a.first - b.first) + kotlin.math.abs(a.second - b.second)) }
    }

    @Test fun boardStartGoal() {
        repeat(50) { seed ->
            val b = Sugoroku.board(seed)
            assertEquals(Sugoroku.Kind.NORMAL, b.first()); assertEquals(Sugoroku.Kind.NORMAL, b.last())
        }
        assertEquals(Sugoroku.SIZE - 1, Sugoroku.move(27, 6)); assertEquals(0, Sugoroku.move(1, -2))
    }

    @Test fun quizzes() {
        val r = Random(1)
        repeat(300) {
            for (lv in 0..2) {
                val q = Sugoroku.quiz(lv, r)
                assertEquals(3, q.choices.toSet().size)
                assertTrue(q.answer in q.choices)
                assertTrue(q.choices.all { it >= 0 })
                if (lv == 0) assertTrue(q.answer <= 10)
                if (lv == 1) assertTrue(q.answer in 0..20)
            }
        }
    }

    @Test fun yuruData() {
        val list = Yuru.parse(File("src/main/assets/content/yuruchara.json").readText())
        assertTrue(list.size >= 20)
        list.forEach { assertTrue(it.pref in 1..47); assertTrue(it.story.length > 20); assertTrue(it.name.isNotBlank()) }
    }
}
