package com.theta.kidshome.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import com.theta.kidshome.data.Store
import kotlin.math.abs
import kotlin.random.Random

/** そろばん 1 けた：上 の 玉（5）と 下 の 玉（1 × 4） */
data class Rod(val five: Boolean = false, val ones: Int = 0) {
    val value get() = (if (five) 5 else 0) + ones
}

object Soroban {
    const val RODS = 9

    fun value(rods: List<Rod>): Long = rods.fold(0L) { acc, r -> acc * 10 + r.value }

    /** 下 の 玉 j（0 が はり に いちばん 近い）を さわった とき */
    fun touchOne(r: Rod, j: Int): Rod = if (j < r.ones) r.copy(ones = j) else r.copy(ones = j + 1)
}

/**
 * バーチャル そろばん：玉 を タップ で はじく。上 の 玉 は 5、下 の 玉 は 1。
 * 🎲 もんだい：かず を おく・たしざん・ひきざん（こたえ の かず に なったら せいかい）。
 */
@Composable
fun SorobanScreen(onBack: () -> Unit) {
    BackHandler(onBack = onBack)
    val theme = themeById(Store.config.currentProfile.themeId)
    val rods = remember { mutableStateListOf<Rod>().apply { repeat(Soroban.RODS) { add(Rod()) } } }
    var showNumber by remember { mutableStateOf(true) }
    var digits by remember { mutableIntStateOf(1) }
    var quiz by remember { mutableStateOf<Pair<String, Long>?>(null) }
    var cheer by remember { mutableStateOf<String?>(null) }
    val value = Soroban.value(rods)

    fun set(n: Long) {
        var x = n
        for (i in rods.indices.reversed()) { val d = (x % 10).toInt(); rods[i] = Rod(d >= 5, d % 5); x /= 10 }
    }
    fun check() {
        val q = quiz ?: return
        if (Soroban.value(rods) == q.second) { cheer = listOf("せいかい！", "すごい！", "ばっちり！", "よくできました！").random(); quiz = null }
    }
    fun newQuiz(kind: Int) {
        cheer = null
        val max = listOf(9L, 99L, 999L)[digits - 1]
        when (kind) {
            0 -> { val n = Random.nextLong(1, max + 1); set(0); quiz = "$n を おこう" to n }
            1 -> { val a = Random.nextLong(1, max + 1); val b = Random.nextLong(1, max + 1); set(a); quiz = "$a ＋ $b は？（$a が おいて あるよ）" to a + b }
            else -> { val a = Random.nextLong(2, max + 1); val b = Random.nextLong(1, a); set(a); quiz = "$a − $b は？（$a が おいて あるよ）" to a - b }
        }
    }

    Column(Modifier.fillMaxSize().background(theme.bgBottom).windowInsetsPadding(WindowInsets.safeDrawing).padding(14.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            RetroIconButton("🏠", theme, onClick = onBack)
            DotText("そろばん", theme, 28, theme.text, modifier = Modifier.padding(start = 10.dp))
            Spacer(Modifier.width(16.dp))
            listOf(1, 2, 3).forEach { d ->
                RetroButton("$d けた", theme, if (digits == d) theme.titleBars[1] else Color.White, fontSize = 16, modifier = Modifier.padding(end = 6.dp)) { digits = d }
            }
            Spacer(Modifier.weight(1f))
            RetroButton("🎲 おく", theme, theme.titleBars[2], fontSize = 16, modifier = Modifier.padding(end = 6.dp)) { newQuiz(0) }
            RetroButton("🎲 ＋", theme, theme.titleBars[2], fontSize = 16, modifier = Modifier.padding(end = 6.dp)) { newQuiz(1) }
            RetroButton("🎲 −", theme, theme.titleBars[2], fontSize = 16, modifier = Modifier.padding(end = 6.dp)) { newQuiz(2) }
            RetroButton(if (showNumber) "👀 かず" else "🙈 かず", theme, if (showNumber) theme.titleBars[0] else Color.White, fontSize = 16, modifier = Modifier.padding(end = 6.dp)) { showNumber = !showNumber }
            RetroButton("🧹 ごはさん", theme, theme.bubbles[0], fontSize = 16) { set(0); cheer = null }
        }
        Spacer(Modifier.height(10.dp))
        Row(Modifier.fillMaxWidth().height(56.dp), verticalAlignment = Alignment.CenterVertically) {
            quiz?.let { DotText(it.first, theme, 26, theme.text) }
            cheer?.let { DotText("${theme.mascot} $it", theme, 26, theme.text) }
            Spacer(Modifier.weight(1f))
            if (showNumber) DotText("%,d".format(value), theme, 40, theme.text)
        }
        Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
            Abacus(rods, Modifier.fillMaxSize()) { i, r -> rods[i] = r; check() }
        }
    }
}

@Composable
private fun Abacus(rods: List<Rod>, modifier: Modifier, onChange: (Int, Rod) -> Unit) {
    val wood = Color(0xFF8A5A33); val woodDark = Color(0xFF5E3A1E)
    val bead = Color(0xFF3B2A20); val beadHi = Color(0xFF7A5A44)
    Canvas(
        modifier.pointerInput(rods.size) {
            detectTapGestures { o ->
                val g = geom(size.width.toFloat(), size.height.toFloat(), rods.size)
                val i = ((o.x - g.left) / g.step).toInt().coerceIn(0, rods.lastIndex)
                if (abs(o.x - g.rodX(i)) > g.step * 0.6f) return@detectTapGestures
                val r = rods[i]
                if (o.y < g.beam) onChange(i, r.copy(five = !r.five))
                else {
                    val j = (0 until 4).minBy { abs(g.oneY(r, it) - o.y) }
                    onChange(i, Soroban.touchOne(r, j))
                }
            }
        }
    ) {
        val g = geom(size.width, size.height, rods.size)
        // わく
        drawRoundRect(woodDark, Offset(g.left - g.frame, g.top - g.frame), Size(g.step * rods.size + g.frame * 2, g.bottom - g.top + g.frame * 2), CornerRadius(18f))
        drawRoundRect(Color(0xFFF6EAD8), Offset(g.left, g.top), Size(g.step * rods.size, g.bottom - g.top), CornerRadius(6f))
        // はり と 定位点
        drawRect(wood, Offset(g.left, g.beam - g.beamH / 2), Size(g.step * rods.size, g.beamH))
        rods.indices.forEach { i ->
            val x = g.rodX(i)
            drawLine(Color(0xFF9C8A70), Offset(x, g.top), Offset(x, g.bottom), g.step * 0.06f)
            if ((rods.size - 1 - i) % 3 == 0) drawCircle(Color.White, g.beamH * 0.22f, Offset(x, g.beam))
        }
        fun beadAt(x: Float, y: Float, on: Boolean) {
            val w = g.step * 0.86f; val h = g.bh * 0.92f
            drawRoundRect(
                Brush.verticalGradient(listOf(beadHi, bead, bead, beadHi), startY = y - h / 2, endY = y + h / 2),
                Offset(x - w / 2, y - h / 2), Size(w, h), CornerRadius(h / 2),
            )
            if (on) drawCircle(Color(0xFFFFD36B), h * 0.12f, Offset(x, y))
        }
        rods.forEachIndexed { i, r ->
            val x = g.rodX(i)
            beadAt(x, g.fiveY(r), r.five)
            (0 until 4).forEach { j -> beadAt(x, g.oneY(r, j), j < r.ones) }
        }
    }
}

private class Geom(val left: Float, val top: Float, val bottom: Float, val step: Float, val bh: Float, val beam: Float, val beamH: Float, val frame: Float) {
    fun rodX(i: Int) = left + step * (i + 0.5f)
    fun fiveY(r: Rod) = if (r.five) beam - beamH / 2 - bh / 2 else top + bh / 2
    fun oneY(r: Rod, j: Int) = if (j < r.ones) beam + beamH / 2 + bh / 2 + j * bh else bottom - (3 - j) * bh - bh / 2
}

private fun geom(w: Float, h: Float, n: Int): Geom {
    val frame = minOf(w, h) * 0.04f
    // 玉 の 高さ：上 2 こ ぶん、下 5 こ ぶん（4 こ と すきま）＋ はり
    val bh = minOf((h - frame * 2) / 8.2f, (w - frame * 2) / n * 0.62f)
    val step = minOf((w - frame * 2) / n, bh / 0.62f)
    val total = bh * 8.2f
    val top = (h - total) / 2
    val beamH = bh * 0.5f
    val beam = top + bh * 2 + beamH / 2
    return Geom((w - step * n) / 2, top, top + total, step, bh, beam, beamH, frame)
}
