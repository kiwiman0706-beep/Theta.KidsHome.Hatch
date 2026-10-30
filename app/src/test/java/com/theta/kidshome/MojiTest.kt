package com.theta.kidshome

import com.theta.kidshome.data.Moji
import com.theta.kidshome.data.Profile
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class MojiTest {
    @Test
    fun kyoikuKanjiCountsMatchTheOfficialTable() {
        val f = listOf("src/main/assets/moji/kyoiku_kanji.json", "app/src/main/assets/moji/kyoiku_kanji.json").map(::File).first { it.exists() }
        val g = JSONObject(f.readText()).getJSONArray("grades")
        assertEquals(listOf(80, 160, 200, 202, 193, 191), (0 until g.length()).map { g.getString(it).length })
        assertEquals(1026, (0 until g.length()).joinToString("") { g.getString(it) }.toSet().size)
    }

    @Test
    fun gradeGoesUpEveryApril() {
        val now = Moji.schoolYear()
        assertEquals(1, Moji.gradeOf(Profile(name = "a", themeId = "usagi", tiles = emptyList(), grade = 1, gradeYear = now)))
        assertEquals(3, Moji.gradeOf(Profile(name = "a", themeId = "usagi", tiles = emptyList(), grade = 1, gradeYear = now - 2)))
        assertEquals(6, Moji.gradeOf(Profile(name = "a", themeId = "usagi", tiles = emptyList(), grade = 5, gradeYear = now - 4)))
        assertEquals(0, Moji.gradeOf(Profile(name = "a", themeId = "usagi", tiles = emptyList())))
    }

    @Test
    fun svgHasOnePathPerStroke() {
        val svg = Moji.svg(listOf(listOf(floatArrayOf(0.1f, 0.1f, 0.5f), floatArrayOf(0.9f, 0.9f, 0.5f)), listOf(floatArrayOf(0.5f, 0.5f, 0.5f))))
        assertEquals(2, Regex("<path").findAll(svg).count())
        assertTrue(svg.startsWith("<svg"))
    }
}
