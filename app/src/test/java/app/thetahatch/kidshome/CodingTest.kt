package app.thetahatch.kidshome

import app.thetahatch.kidshome.data.Coding
import app.thetahatch.kidshome.data.Coding.Blk
import app.thetahatch.kidshome.data.Coding.Dir.*
import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.File

class CodingTest {
    private var id = 0L
    private fun m(vararg d: Coding.Dir) = d.map { Blk.Move(id++, it) }
    private fun rep(n: Int, vararg b: Blk) = Blk.Repeat(id++, n, b.toList())
    private fun until(d: Coding.Dir) = Blk.Until(id++, d)
    private fun iff(go: Coding.Dir, turn: Coding.Dir) = Blk.IfWall(id++, go, turn)

    private val levels = Coding.parse(File("src/main/assets/content/coding.json").readText())

    /** どの ステージ も 「best」まい で とける */
    private val answers: List<List<Blk>> by lazy {
        listOf(
            m(RIGHT, RIGHT, RIGHT),
            m(DOWN, DOWN, DOWN),
            m(RIGHT, RIGHT, RIGHT, RIGHT, DOWN, DOWN),
            m(UP, RIGHT, RIGHT, RIGHT, RIGHT, DOWN),
            m(RIGHT, DOWN, RIGHT, DOWN, RIGHT, DOWN, RIGHT),
            m(RIGHT, RIGHT, RIGHT, DOWN, DOWN, LEFT, LEFT, DOWN, DOWN, RIGHT, RIGHT, RIGHT),
            listOf(rep(7, *m(RIGHT).toTypedArray())),
            listOf(rep(5, *m(DOWN, RIGHT).toTypedArray())),
            listOf(rep(4, *m(RIGHT).toTypedArray()), rep(3, *m(DOWN).toTypedArray()), rep(4, *m(LEFT).toTypedArray())),
            listOf(rep(6, *m(RIGHT).toTypedArray()), rep(2, *m(DOWN).toTypedArray()), rep(6, *m(LEFT).toTypedArray())),
            listOf(rep(4, *m(RIGHT, UP).toTypedArray())) + m(RIGHT),
            listOf(rep(4, *m(RIGHT).toTypedArray()), rep(3, *m(DOWN).toTypedArray()), rep(3, *m(RIGHT).toTypedArray())),
            listOf(until(RIGHT)),
            listOf(until(RIGHT), until(DOWN)),
            listOf(until(RIGHT), until(DOWN), until(LEFT), until(DOWN), until(RIGHT)),
            listOf(rep(4, until(RIGHT), *m(DOWN).toTypedArray()), until(RIGHT)),
            listOf(rep(9, iff(RIGHT, DOWN))),
            listOf(rep(8, iff(DOWN, RIGHT))),
            listOf(until(RIGHT), rep(8, iff(DOWN, RIGHT))),
            listOf(until(RIGHT), until(DOWN), until(LEFT), until(DOWN), until(RIGHT), until(UP), until(RIGHT), until(DOWN)),
        )
    }

    @Test fun everyLevelSolvable() {
        assertEquals(20, levels.size)
        levels.forEachIndexed { i, lv ->
            val r = Coding.run(lv, answers[i])
            assertEquals("level ${i + 1}", Coding.Outcome.GOAL, r.outcome)
            assertEquals("level ${i + 1} best", lv.best, Coding.count(answers[i]))
            assertEquals(3, Coding.stars(lv, answers[i]))
        }
    }

    @Test fun bumpAndShort() {
        val lv = levels[0]
        assertEquals(Coding.Outcome.BUMP, Coding.run(lv, m(DOWN)).outcome)
        assertEquals(Coding.Outcome.SHORT, Coding.run(lv, m(RIGHT)).outcome)
        val r = Coding.run(lv, m(RIGHT, RIGHT, RIGHT, RIGHT))
        assertEquals(Coding.Outcome.GOAL, r.outcome) // ゴール で とまる
        assertEquals(3, r.frames.size)
    }
}
