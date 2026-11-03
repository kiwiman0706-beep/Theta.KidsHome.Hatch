package com.theta.kidshome.ui

import android.media.AudioManager
import android.media.ToneGenerator
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.theta.kidshome.data.Store
import com.theta.kidshome.media.Kit
import com.theta.kidshome.media.Synth
import com.theta.kidshome.media.Voice
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin
import kotlin.random.Random

// ======================================================================== え（ベクター）

private val Ink = Color(0xFF4A3530)
private val Blush = Color(0xFFFF9EB5)

/** にっこり かお。mood 0 にっこり／1 め を とじて にっこり／2 もぐもぐ／3 おくち あーん */
private fun DrawScope.face(c: Offset, r: Float, mood: Int = 0) {
    val ex = r * 0.34f; val ey = -r * 0.08f
    if (mood == 1) {
        listOf(-1, 1).forEach { s -> drawArc(Ink, 200f, 140f, false, Offset(c.x + s * ex - r * 0.1f, c.y + ey - r * 0.06f), Size(r * 0.2f, r * 0.16f), style = Stroke(r * 0.05f, cap = StrokeCap.Round)) }
    } else listOf(-1, 1).forEach { s ->
        drawCircle(Ink, r * 0.08f, Offset(c.x + s * ex, c.y + ey))
        drawCircle(Color.White, r * 0.025f, Offset(c.x + s * ex + r * 0.025f, c.y + ey - r * 0.03f))
    }
    listOf(-1, 1).forEach { s -> drawOval(Blush.copy(alpha = 0.8f), Offset(c.x + s * r * 0.52f - r * 0.13f, c.y + r * 0.1f), Size(r * 0.26f, r * 0.15f)) }
    when (mood) {
        2 -> {
            val p = Path(); val w = r * 0.42f
            p.moveTo(c.x - w / 2, c.y + r * 0.3f)
            for (i in 1..8) p.lineTo(c.x - w / 2 + w * i / 8f, c.y + r * 0.3f + (if (i % 2 == 0) -1 else 1) * r * 0.05f)
            drawPath(p, Ink, style = Stroke(r * 0.045f, cap = StrokeCap.Round))
        }
        3 -> drawOval(Color(0xFFB8384E), Offset(c.x - r * 0.13f, c.y + r * 0.18f), Size(r * 0.26f, r * 0.24f))
        else -> drawArc(Ink, 20f, 140f, false, Offset(c.x - r * 0.18f, c.y + r * 0.05f), Size(r * 0.36f, r * 0.28f), style = Stroke(r * 0.05f, cap = StrokeCap.Round))
    }
}

private fun DrawScope.ball(c: Offset, r: Float, color: Color, mood: Int = 0) {
    drawCircle(color, r, c)
    drawCircle(Color.White.copy(alpha = 0.45f), r * 0.18f, Offset(c.x - r * 0.45f, c.y - r * 0.45f))
    face(c, r, mood)
}

private fun DrawScope.leaf(c: Offset, w: Float, angle: Float) = rotate(angle, c) { drawOval(Color(0xFF6BBE45), Offset(c.x - w / 2, c.y - w / 4), Size(w, w / 2)) }

private fun DrawScope.apple(c: Offset, r: Float, mood: Int = 0) {
    drawLine(Color(0xFF7A4B2A), Offset(c.x, c.y - r * 0.8f), Offset(c.x + r * 0.1f, c.y - r * 1.2f), r * 0.1f, StrokeCap.Round)
    leaf(Offset(c.x + r * 0.35f, c.y - r * 1.05f), r * 0.6f, -20f)
    drawCircle(Color(0xFFE8473F), r * 0.62f, Offset(c.x - r * 0.33f, c.y - r * 0.1f))
    drawCircle(Color(0xFFE8473F), r * 0.62f, Offset(c.x + r * 0.33f, c.y - r * 0.1f))
    drawCircle(Color(0xFFE8473F), r * 0.8f, Offset(c.x, c.y + r * 0.1f))
    drawCircle(Color.White.copy(alpha = 0.4f), r * 0.15f, Offset(c.x - r * 0.45f, c.y - r * 0.35f))
    face(Offset(c.x, c.y + r * 0.08f), r * 0.85f, mood)
}

private fun DrawScope.orange(c: Offset, r: Float, mood: Int = 0) {
    drawCircle(Color(0xFFFF9A2E), r, c)
    drawCircle(Color(0xFFFFB45C), r * 0.85f, Offset(c.x - r * 0.08f, c.y - r * 0.08f))
    leaf(Offset(c.x + r * 0.2f, c.y - r * 0.95f), r * 0.55f, -25f)
    drawCircle(Color(0xFF6E8E2F), r * 0.08f, Offset(c.x, c.y - r * 0.95f))
    face(c, r * 0.9f, mood)
}

private fun DrawScope.grape(c: Offset, r: Float, mood: Int = 0) {
    val g = Color(0xFF8E5BD6)
    drawLine(Color(0xFF7A4B2A), Offset(c.x, c.y - r * 1.05f), Offset(c.x, c.y - r * 0.7f), r * 0.1f, StrokeCap.Round)
    leaf(Offset(c.x + r * 0.35f, c.y - r * 0.95f), r * 0.55f, -10f)
    listOf(-0.5f to -0.45f, 0.5f to -0.45f, 0f to -0.5f, -0.3f to 0.15f, 0.3f to 0.15f, 0f to 0.7f).forEach { (dx, dy) ->
        drawCircle(g, r * 0.42f, Offset(c.x + dx * r, c.y + dy * r))
        drawCircle(Color.White.copy(alpha = 0.35f), r * 0.1f, Offset(c.x + dx * r - r * 0.15f, c.y + dy * r - r * 0.15f))
    }
    face(Offset(c.x, c.y - r * 0.05f), r * 0.75f, mood)
}

private fun DrawScope.onigiri(c: Offset, r: Float) {
    val p = Path().apply { moveTo(c.x, c.y - r); quadraticTo(c.x + r * 1.15f, c.y + r * 0.7f, c.x + r * 0.2f, c.y + r * 0.8f); lineTo(c.x - r * 0.2f, c.y + r * 0.8f); quadraticTo(c.x - r * 1.15f, c.y + r * 0.7f, c.x, c.y - r); close() }
    drawPath(p, Color.White); drawPath(p, Ink.copy(alpha = 0.3f), style = Stroke(r * 0.05f))
    drawRoundRect(Color(0xFF2E3B2E), Offset(c.x - r * 0.38f, c.y + r * 0.2f), Size(r * 0.76f, r * 0.62f), CornerRadius(r * 0.08f))
    face(Offset(c.x, c.y - r * 0.15f), r * 0.6f)
}

private fun DrawScope.strawberry(c: Offset, r: Float) {
    val p = Path().apply { moveTo(c.x - r, c.y - r * 0.4f); cubicTo(c.x - r, c.y + r * 0.6f, c.x - r * 0.2f, c.y + r, c.x, c.y + r * 1.1f); cubicTo(c.x + r * 0.2f, c.y + r, c.x + r, c.y + r * 0.6f, c.x + r, c.y - r * 0.4f); quadraticTo(c.x, c.y - r * 0.8f, c.x - r, c.y - r * 0.4f); close() }
    drawPath(p, Color(0xFFF2405A))
    for (i in 0 until 7) { val a = i * 0.9f; drawCircle(Color(0xFFFFE07A), r * 0.05f, Offset(c.x + cos(a) * r * 0.55f, c.y + 0.4f * r + sin(a) * r * 0.35f)) }
    for (i in -2..2) leaf(Offset(c.x + i * r * 0.28f, c.y - r * 0.6f), r * 0.5f, i * 25f)
    face(Offset(c.x, c.y - r * 0.05f), r * 0.7f)
}

private fun DrawScope.donut(c: Offset, r: Float) {
    drawCircle(Color(0xFFE6B072), r, c)
    drawCircle(Color(0xFFFF9EC4), r * 0.88f, Offset(c.x, c.y - r * 0.05f))
    listOf(Color(0xFF7FD3FF), Color(0xFFFFE07A), Color.White).forEachIndexed { i, col -> for (k in 0 until 4) { val a = k * 1.6f + i; rotate(a * 40f, Offset(c.x + cos(a) * r * 0.6f, c.y + sin(a) * r * 0.6f)) { drawRoundRect(col, Offset(c.x + cos(a) * r * 0.6f - r * 0.08f, c.y + sin(a) * r * 0.6f - r * 0.025f), Size(r * 0.16f, r * 0.05f), CornerRadius(r)) } } }
    drawCircle(Color(0xFFFFF6EC), r * 0.25f, c)
}

/** どうぶつ の かお（0 ねこ 1 いぬ 2 うさぎ 3 くま 4 ぶた 5 かえる） */
private fun DrawScope.animal(kind: Int, c: Offset, r: Float, mood: Int = 0) {
    val col = listOf(Color(0xFFFFD08A), Color(0xFFE9C59A), Color.White, Color(0xFFB98A62), Color(0xFFFFB6C8), Color(0xFF8ED36B))[kind]
    when (kind) {
        0 -> listOf(-1, 1).forEach { s -> drawPath(Path().apply { moveTo(c.x + s * r * 0.85f, c.y - r * 0.2f); lineTo(c.x + s * r * 0.7f, c.y - r * 1.15f); lineTo(c.x + s * r * 0.15f, c.y - r * 0.8f); close() }, col) }
        1 -> listOf(-1, 1).forEach { s -> rotate(s * 20f, Offset(c.x + s * r * 0.85f, c.y - r * 0.3f)) { drawOval(Color(0xFF9C6B45), Offset(c.x + s * r * 0.85f - r * 0.25f, c.y - r * 0.75f), Size(r * 0.5f, r * 0.95f)) } }
        2 -> listOf(-1, 1).forEach { s -> drawOval(col, Offset(c.x + s * r * 0.38f - r * 0.2f, c.y - r * 1.75f), Size(r * 0.4f, r * 1.2f)); drawOval(Blush, Offset(c.x + s * r * 0.38f - r * 0.1f, c.y - r * 1.6f), Size(r * 0.2f, r * 0.9f)) }
        3 -> listOf(-1, 1).forEach { s -> drawCircle(col, r * 0.32f, Offset(c.x + s * r * 0.7f, c.y - r * 0.75f)); drawCircle(Color(0xFF8A5E3E), r * 0.16f, Offset(c.x + s * r * 0.7f, c.y - r * 0.75f)) }
        4 -> listOf(-1, 1).forEach { s -> drawPath(Path().apply { moveTo(c.x + s * r * 0.85f, c.y - r * 0.35f); lineTo(c.x + s * r * 0.75f, c.y - r * 1.0f); lineTo(c.x + s * r * 0.3f, c.y - r * 0.85f); close() }, Color(0xFFFF97B4)) }
    }
    drawCircle(col, r, c)
    if (kind == 5) listOf(-1, 1).forEach { s ->
        drawCircle(col, r * 0.36f, Offset(c.x + s * r * 0.45f, c.y - r * 0.8f)); drawCircle(Color.White, r * 0.24f, Offset(c.x + s * r * 0.45f, c.y - r * 0.8f))
        drawCircle(Ink, r * 0.11f, Offset(c.x + s * r * 0.45f, c.y - r * 0.78f)); drawCircle(Color.White, r * 0.035f, Offset(c.x + s * r * 0.45f + r * 0.03f, c.y - r * 0.82f))
    }
    if (kind == 2) drawCircle(Ink.copy(alpha = 0.15f), r, c, style = Stroke(r * 0.03f))
    if (kind == 4) { drawOval(Color(0xFFFF97B4), Offset(c.x - r * 0.25f, c.y + r * 0.05f), Size(r * 0.5f, r * 0.34f)); listOf(-1, 1).forEach { s -> drawCircle(Ink, r * 0.05f, Offset(c.x + s * r * 0.1f, c.y + r * 0.22f)) } }
    if (kind == 1 || kind == 3) drawOval(Color.White.copy(alpha = 0.6f), Offset(c.x - r * 0.3f, c.y + r * 0.05f), Size(r * 0.6f, r * 0.42f))
    if (kind == 5) {
        drawArc(Ink, 20f, 140f, false, Offset(c.x - r * 0.35f, c.y), Size(r * 0.7f, r * 0.4f), style = Stroke(r * 0.05f, cap = StrokeCap.Round))
        listOf(-1, 1).forEach { s -> drawOval(Blush.copy(alpha = 0.8f), Offset(c.x + s * r * 0.55f - r * 0.13f, c.y + r * 0.05f), Size(r * 0.26f, r * 0.15f)) }
    } else face(c, r, mood)
    if (kind == 1 || kind == 3) drawOval(Ink, Offset(c.x - r * 0.09f, c.y + r * 0.05f), Size(r * 0.18f, r * 0.12f))
}

/** のりもの（0 パトカー 1 しょうぼうしゃ 2 きゅうきゅうしゃ 3 バス 4 くるま） */
private fun DrawScope.vehicle(kind: Int, x: Float, ground: Float, w: Float, light: Boolean) {
    val h = w * 0.42f; val top = ground - h - w * 0.08f
    val body = listOf(Color.White, Color(0xFFE53935), Color.White, Color(0xFFFFC94D), Color(0xFF6EB8FF))[kind]
    if (kind == 0) drawRoundRect(Color(0xFF2F2F3A), Offset(x, top + h * 0.45f), Size(w, h * 0.55f), CornerRadius(w * 0.06f))
    drawRoundRect(body, Offset(x, top + h * 0.3f), Size(w, h * (if (kind == 0) 0.25f else 0.7f)), CornerRadius(w * 0.06f))
    val cab = if (kind == 3) Offset(x + w * 0.04f, top) to Size(w * 0.92f, h * 0.45f) else Offset(x + w * 0.22f, top) to Size(w * 0.55f, h * 0.4f)
    drawRoundRect(if (kind == 0) Color.White else body, cab.first, cab.second, CornerRadius(w * 0.08f))
    val wins = if (kind == 3) 5 else 2
    for (i in 0 until wins) {
        val ww = cab.second.width / wins
        drawRoundRect(Color(0xFFBFE6FF), Offset(cab.first.x + i * ww + ww * 0.12f, top + h * 0.08f), Size(ww * 0.76f, h * 0.24f), CornerRadius(w * 0.03f))
    }
    if (kind == 2) drawRect(Color(0xFFE53935), Offset(x, top + h * 0.6f), Size(w, h * 0.08f))
    if (kind == 1) for (i in 0..5) drawLine(Color(0xFFDDDDDD), Offset(x + w * 0.1f + i * w * 0.12f, top - h * 0.12f), Offset(x + w * 0.1f + i * w * 0.12f, top + h * 0.02f), w * 0.015f)
    if (kind == 1) drawRect(Color(0xFFDDDDDD), Offset(x + w * 0.05f, top - h * 0.14f), Size(w * 0.75f, h * 0.05f))
    if (kind <= 2) drawRoundRect(if (light) Color(0xFFFF2D2D) else Color(0xFF9A2020), Offset(x + w * 0.44f, top - h * 0.14f), Size(w * 0.12f, h * 0.14f), CornerRadius(w * 0.03f))
    face(Offset(x + w * 0.88f, top + h * 0.62f), h * 0.32f)
    listOf(0.22f, 0.78f).forEach { k ->
        drawCircle(Color(0xFF333340), w * 0.11f, Offset(x + w * k, ground - w * 0.02f))
        drawCircle(Color(0xFFBBBBC8), w * 0.05f, Offset(x + w * k, ground - w * 0.02f))
    }
}

private fun DrawScope.sparkle(c: Offset, r: Float, color: Color = Color(0xFFFFE07A)) {
    drawLine(color, Offset(c.x - r, c.y), Offset(c.x + r, c.y), r * 0.25f, StrokeCap.Round)
    drawLine(color, Offset(c.x, c.y - r), Offset(c.x, c.y + r), r * 0.25f, StrokeCap.Round)
}

// ======================================================================== ゲーム

private class Game(val title: String, val bg: Color, val icon: DrawScope.() -> Unit, val play: @Composable (Sounds) -> Unit)

/** おと：ポン（がっき の たのしい 音）・でんわ の ピッポッパ・よみあげ */
private class Sounds(val pop: (Int) -> Unit, val dtmf: (Int) -> Unit, val say: (String) -> Unit)

@Composable
private fun rememberTime(): Float {
    var t by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(Unit) { val s = withFrameNanos { it }; while (true) withFrameNanos { t = (it - s) / 1e9f } }
    return t
}

private val Pastels = listOf(Color(0xFFFF8FB1), Color(0xFF7FC8FF), Color(0xFFFFD25A), Color(0xFF8ED36B), Color(0xFFB79CFF), Color(0xFFFFA96B))

private class Bubble(var x: Float, var y: Float, val r: Float, val color: Color, val speed: Float, var popped: Float = -1f)

@Composable
private fun Bubbles(s: Sounds) {
    val t = rememberTime()
    val list = remember { mutableStateListOf<Bubble>() }
    var size by remember { mutableStateOf(Size.Zero) }
    var last by remember { mutableFloatStateOf(0f) }
    fun spawn(w: Float, h: Float, anyY: Boolean) = Bubble(Random.nextFloat() * w, if (anyY) Random.nextFloat() * h else h + 80f, 40f + Random.nextFloat() * 50f, Pastels.random(), 40f + Random.nextFloat() * 60f)
    Canvas(Modifier.fillMaxSize().pointerInput(Unit) {
        detectTapGestures { o ->
            list.firstOrNull { it.popped < 0 && hypot(it.x - o.x, it.y - o.y) < it.r * 1.1f }?.let { it.popped = t; s.pop(Random.nextInt(12)) }
        }
    }) {
        size = this.size
        val dt = (t - last).coerceIn(0f, 0.1f); last = t
        if (list.isEmpty()) repeat(14) { list += spawn(size.width, size.height, true) }
        list.toList().forEach { b ->
            if (b.popped >= 0) {
                val k = (t - b.popped) / 0.35f
                if (k > 1f) { list.remove(b); list += spawn(size.width, size.height, false); return@forEach }
                for (i in 0 until 8) { val a = i * PI.toFloat() / 4; drawCircle(b.color, b.r * 0.15f * (1 - k), Offset(b.x + cos(a) * b.r * (0.6f + k), b.y + sin(a) * b.r * (0.6f + k))) }
                return@forEach
            }
            b.y -= b.speed * dt
            if (b.y < -b.r) b.y = size.height + b.r
            ball(Offset(b.x + sin(t * 1.3f + b.r) * 10f, b.y), b.r, b.color)
        }
    }
}

@Composable
private fun FruitTree(s: Sounds) {
    val t = rememberTime()
    var kind by remember { mutableIntStateOf(0) }
    val spots = listOf(-0.55f to -0.3f, 0f to -0.48f, 0.55f to -0.3f, -0.3f to 0.02f, 0.3f to 0.02f, 0f to -0.16f)
    val falls = remember { mutableStateListOf<Float>().apply { repeat(6) { add(-1f) } } }
    var ground by remember { mutableFloatStateOf(0f) }
    var center by remember { mutableStateOf(Offset.Zero) }
    var r by remember { mutableFloatStateOf(1f) }
    Canvas(Modifier.fillMaxSize().pointerInput(Unit) {
        detectTapGestures { o ->
            val hit = spots.indexOfFirst { (dx, dy) -> falls[spots.indexOf(dx to dy)] < 0 && hypot(center.x + dx * r * 2.2f - o.x, center.y + dy * r * 2.2f - o.y) < r * 0.55f }
            if (hit >= 0) { falls[hit] = t; s.pop(3); s.say(listOf("りんご", "みかん", "ぶどう")[kind]) }
            else if (o.y > center.y + r * 0.6f && abs(o.x - center.x) < r * 0.5f) {
                // みき を おすと ぜんぶ もどる・くだもの が かわる
                for (i in falls.indices) falls[i] = -1f
                kind = (kind + 1) % 3; s.pop(6)
            }
        }
    }) {
        r = minOf(size.width, size.height) * 0.26f
        center = Offset(size.width / 2, size.height * 0.46f)
        ground = size.height * 0.88f
        drawRect(Color(0xFF9BE070), Offset(0f, ground), Size(size.width, size.height - ground))
        drawRoundRect(Color(0xFF9C6B45), Offset(center.x - r * 0.28f, center.y + r * 0.3f), Size(r * 0.56f, ground - center.y - r * 0.3f), CornerRadius(r * 0.1f))
        face(Offset(center.x, center.y + r * 1.15f), r * 0.32f)
        listOf(0f to -0.5f, -0.6f to -0.15f, 0.6f to -0.15f, -0.3f to 0.25f, 0.3f to 0.25f, 0f to 0f).forEach { (dx, dy) -> drawCircle(Color(0xFF69C04A), r * 0.75f, Offset(center.x + dx * r * 1.6f, center.y + dy * r * 1.6f)) }
        spots.forEachIndexed { i, (dx, dy) ->
            var c = Offset(center.x + dx * r * 2.2f, center.y + dy * r * 2.2f)
            val f = falls[i]
            if (f >= 0) {
                val k = t - f
                val fallTo = ground - r * 0.35f
                val y = c.y + 900f * k * k
                c = Offset(c.x + (i - 2.5f) * 30f * minOf(k, 1f), minOf(y, fallTo) - if (y > fallTo) abs(sin((k - 0.4f) * 8f)) * 30f * maxOf(0f, 1.5f - k) else 0f)
            }
            val sw = sin(t * 2f + i) * 4f
            when (kind) { 0 -> apple(Offset(c.x + sw, c.y), r * 0.38f, if (f >= 0) 1 else 0); 1 -> orange(Offset(c.x + sw, c.y), r * 0.36f, if (f >= 0) 1 else 0); else -> grape(Offset(c.x + sw, c.y), r * 0.34f, if (f >= 0) 1 else 0) }
        }
    }
}

@Composable
private fun Phone(s: Sounds) {
    val t = rememberTime()
    var digits by remember { mutableStateOf("") }
    var ringing by remember { mutableStateOf(false) }
    val nums = listOf("ぜろ", "いち", "に", "さん", "よん", "ご", "ろく", "なな", "はち", "きゅう")
    Row(Modifier.fillMaxSize().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
        Canvas(Modifier.weight(1f).fillMaxHeight()) {
            val r = minOf(size.width, size.height) * 0.3f
            val c = Offset(size.width / 2, size.height / 2)
            rotate(if (ringing) sin(t * 25f) * 8f else 0f, c) {
                drawRoundRect(Color(0xFFFF8FB1), Offset(c.x - r, c.y - r * 1.4f), Size(r * 2, r * 2.8f), CornerRadius(r * 0.3f))
                drawRoundRect(Color(0xFFFFF4F8), Offset(c.x - r * 0.8f, c.y - r * 1.2f), Size(r * 1.6f, r * 1.4f), CornerRadius(r * 0.15f))
                face(Offset(c.x, c.y - r * 0.5f), r * 0.55f, if (ringing) 3 else 0)
            }
        }
        Column(Modifier.weight(1.2f), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(digits.takeLast(11).ifEmpty { " " }, fontSize = 44.sp, fontWeight = FontWeight.Bold, color = Ink)
            listOf(listOf(1, 2, 3), listOf(4, 5, 6), listOf(7, 8, 9), listOf(-1, 0, -2)).forEach { row ->
                Row(Modifier.padding(4.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    row.forEach { n ->
                        val label = when (n) { -1 -> "📞"; -2 -> "✖"; else -> "$n" }
                        Box(
                            Modifier.width(110.dp).height(78.dp).clip(RoundedCornerShape(40.dp))
                                .background(when (n) { -1 -> Color(0xFF8ED36B); -2 -> Color(0xFFFF8F8F); else -> Color.White }).border(3.dp, Ink.copy(alpha = 0.3f), RoundedCornerShape(40.dp))
                                .clickable {
                                    when (n) {
                                        -1 -> { ringing = true; s.say("もしもし？ だあれ？") }
                                        -2 -> { ringing = false; digits = "" }
                                        else -> { digits += n; s.dtmf(n); s.say(nums[n]) }
                                    }
                                },
                            contentAlignment = Alignment.Center,
                        ) { Text(label, fontSize = 34.sp, fontWeight = FontWeight.Bold, color = Ink) }
                    }
                }
            }
        }
    }
}

@Composable
private fun Egg(s: Sounds) {
    val t = rememberTime()
    var stage by remember { mutableIntStateOf(0) }
    var since by remember { mutableFloatStateOf(0f) }
    if (stage == 1 && t - since > 2.5f) { stage = 2; s.say("できた！ めだまやき"); s.pop(10) }
    Canvas(Modifier.fillMaxSize().pointerInput(Unit) {
        detectTapGestures {
            when (stage) { 0 -> { stage = 1; since = t; s.pop(4); s.say("ぱかっ。じゅ〜") }; 2 -> { stage = 0; s.pop(7) } }
        }
    }) {
        val r = minOf(size.width, size.height) * 0.32f
        val c = Offset(size.width / 2, size.height * 0.55f)
        drawRoundRect(Color(0xFF8A8A96), Offset(c.x + r * 0.9f, c.y - r * 0.08f), Size(r * 1.2f, r * 0.16f), CornerRadius(r * 0.08f))
        drawCircle(Color(0xFF5E5E6A), r * 1.05f, c)
        drawCircle(Color(0xFF7A7A86), r * 0.92f, c)
        when (stage) {
            0 -> {
                val e = Offset(c.x, c.y - r * 1.5f + sin(t * 3) * 8f)
                drawOval(Color(0xFFFFF4E6), Offset(e.x - r * 0.42f, e.y - r * 0.55f), Size(r * 0.84f, r * 1.1f))
                face(e, r * 0.4f)
            }
            else -> {
                listOf(0f to 0f, 0.3f to 0.15f, -0.25f to 0.2f, 0.1f to -0.3f, -0.3f to -0.15f).forEach { (dx, dy) -> drawCircle(Color.White, r * 0.42f, Offset(c.x + dx * r, c.y + dy * r)) }
                drawCircle(Color(0xFFFFB627), r * 0.3f, c)
                if (stage == 2) { face(c, r * 0.3f, 1); for (i in 0 until 5) sparkle(Offset(c.x + cos(i * 1.3f + t) * r * 1.1f, c.y + sin(i * 1.3f + t) * r * 1.1f), r * 0.08f) }
                else for (i in 0 until 6) { val k = ((t - since) * 1.5f + i / 6f) % 1f; drawCircle(Color.White.copy(alpha = 0.5f * (1 - k)), r * 0.08f, Offset(c.x + (i - 2.5f) * r * 0.25f, c.y - r * 0.4f - k * r)) }
            }
        }
    }
}

@Composable
private fun Mogumogu(s: Sounds) {
    val t = rememberTime()
    var food by remember { mutableIntStateOf(-1) }
    var since by remember { mutableFloatStateOf(0f) }
    var size by remember { mutableStateOf(Size.Zero) }
    val foods: List<DrawScope.(Offset, Float) -> Unit> = listOf({ c, r -> apple(c, r) }, { c, r -> onigiri(c, r) }, { c, r -> strawberry(c, r) }, { c, r -> donut(c, r) })
    val names = listOf("りんご", "おにぎり", "いちご", "ドーナツ")
    val k = t - since
    if (food >= 0 && k > 3f) food = -1
    Canvas(Modifier.fillMaxSize().pointerInput(Unit) {
        detectTapGestures { o ->
            if (food >= 0 || o.y < size.height * 0.78f) return@detectTapGestures
            val i = (o.x / (size.width / 4)).toInt().coerceIn(0, 3)
            food = i; since = t; s.say("${names[i]}、あーん"); s.pop(1)
        }
    }) {
        size = this.size
        val r = size.height * 0.3f
        val c = Offset(size.width / 2, size.height * 0.38f)
        drawRoundRect(Color(0xFF7FC8FF), Offset(c.x - r * 0.7f, c.y + r * 0.85f), Size(r * 1.4f, r), CornerRadius(r * 0.4f))
        drawCircle(Color(0xFFFFE0C4), r, c)
        val hair = Color(0xFF7A4B2A)
        drawArc(hair, 180f, 180f, true, Offset(c.x - r * 1.04f, c.y - r * 1.06f), Size(r * 2.08f, r * 1.3f))
        for (i in -3..3) drawCircle(hair, r * 0.2f, Offset(c.x + i * r * 0.28f, c.y - r * 0.42f + abs(i) * abs(i) * r * 0.035f))
        listOf(-1, 1).forEach { s -> drawOval(hair, Offset(c.x + s * r * 0.92f - r * 0.18f, c.y - r * 0.5f), Size(r * 0.36f, r * 0.8f)) }
        val mood = when { food < 0 -> 0; k < 0.8f -> 3; k < 2.2f -> 2; else -> 1 }
        face(Offset(c.x, c.y + r * 0.15f), r, mood)
        if (food >= 0 && k < 0.8f) {
            val start = Offset(size.width / 8 * (food * 2 + 1), size.height * 0.88f)
            val end = Offset(c.x, c.y + r * 0.5f)
            val p = k / 0.8f
            foods[food](this, Offset(start.x + (end.x - start.x) * p, start.y + (end.y - start.y) * p - sin(p * PI.toFloat()) * r * 0.5f), r * 0.25f * (1 - p * 0.6f))
        }
        drawRect(Color(0xFFFFF0D6), Offset(0f, size.height * 0.78f), Size(size.width, size.height * 0.22f))
        for (i in 0 until 4) foods[i](this, Offset(size.width / 8 * (i * 2 + 1), size.height * 0.89f), size.height * 0.07f)
    }
    if (food >= 0 && k in 0.8f..0.85f) LaunchedEffect(since) { s.say("もぐもぐ") }
    if (food >= 0 && k in 2.2f..2.25f) LaunchedEffect(since + 1) { s.say("おいしい！") }
}

private class Seed(val ang: Float, var x: Float = 0f, var y: Float = 0f, var vx: Float = 0f, var vy: Float = 0f, var flying: Boolean = false)

@Composable
private fun Dandelion(s: Sounds) {
    val t = rememberTime()
    val seeds = remember { mutableStateListOf<Seed>().apply { repeat(24) { add(Seed(it * 15f)) } } }
    var gone by remember { mutableFloatStateOf(-1f) }
    var last by remember { mutableFloatStateOf(0f) }
    if (gone >= 0 && t - gone > 4f) { seeds.clear(); repeat(24) { seeds += Seed(it * 15f) }; gone = -1f }
    Canvas(Modifier.fillMaxSize().pointerInput(Unit) {
        detectTapGestures {
            if (seeds.none { !it.flying }) return@detectTapGestures
            seeds.filter { !it.flying }.shuffled().take(8).forEach { sd -> sd.flying = true; sd.vx = 60f + Random.nextFloat() * 160f; sd.vy = -60f - Random.nextFloat() * 120f }
            s.say("ふ〜"); s.pop(9)
            if (seeds.all { it.flying }) gone = t
        }
    }) {
        val dt = (t - last).coerceIn(0f, 0.1f); last = t
        val head = Offset(size.width * 0.4f, size.height * 0.35f)
        val r = size.height * 0.18f
        drawRect(Color(0xFF9BE070), Offset(0f, size.height * 0.85f), Size(size.width, size.height * 0.15f))
        drawLine(Color(0xFF5DAA3A), head, Offset(head.x, size.height * 0.86f), r * 0.06f)
        leaf(Offset(head.x - r * 0.5f, size.height * 0.8f), r * 0.9f, -30f); leaf(Offset(head.x + r * 0.5f, size.height * 0.78f), r * 0.9f, 30f)
        drawCircle(Color(0xFFD9C9A0), r * 0.12f, head)
        seeds.forEach { sd ->
            if (!sd.flying) { sd.x = head.x + cos(sd.ang * PI.toFloat() / 180) * r; sd.y = head.y + sin(sd.ang * PI.toFloat() / 180) * r }
            else { sd.x += sd.vx * dt; sd.y += sd.vy * dt; sd.vy += (sin(t * 2 + sd.ang) * 20f) * dt }
            val base = if (sd.flying) Offset(sd.x, sd.y + r * 0.25f) else head
            drawLine(Color(0xFFCFC6B0), base, Offset(sd.x, sd.y), r * 0.015f)
            drawCircle(Color.White, r * 0.11f, Offset(sd.x, sd.y))
            drawCircle(Color(0xFFE8E2D4), r * 0.11f, Offset(sd.x, sd.y), style = Stroke(r * 0.015f))
        }
        val f = Offset(size.width * 0.72f, size.height * 0.75f)
        for (i in 0 until 12) rotate(i * 30f, f) { drawOval(Color(0xFFFFD43B), Offset(f.x - r * 0.08f, f.y - r * 0.38f), Size(r * 0.16f, r * 0.3f)) }
        drawCircle(Color(0xFFFFB627), r * 0.18f, f); face(f, r * 0.18f)
    }
}

private class Spark(var x: Float, var y: Float, val vx: Float, val vy: Float, val color: Color, val born: Float)

@Composable
private fun Fireworks(s: Sounds) {
    val t = rememberTime()
    val sparks = remember { mutableStateListOf<Spark>() }
    var last by remember { mutableFloatStateOf(0f) }
    Canvas(Modifier.fillMaxSize().pointerInput(Unit) {
        detectTapGestures { o ->
            val col = Pastels.random()
            repeat(36) { i -> val a = i * 10f * PI.toFloat() / 180; val sp = 200f + Random.nextFloat() * 120f; sparks += Spark(o.x, o.y, cos(a) * sp, sin(a) * sp, if (i % 3 == 0) Color.White else col, t) }
            s.pop(listOf(0, 2, 5).random())
        }
    }) {
        val dt = (t - last).coerceIn(0f, 0.1f); last = t
        drawRect(Color(0xFF1B2050))
        for (i in 0 until 30) drawCircle(Color.White.copy(alpha = 0.5f + 0.5f * sin(t * 2 + i)), 2f, Offset((i * 137 % 1000) / 1000f * size.width, (i * 71 % 600) / 1000f * size.height))
        sparks.toList().forEach { p ->
            val age = t - p.born
            if (age > 1.6f) { sparks.remove(p); return@forEach }
            p.x += p.vx * dt * (1.6f - age) / 1.6f; p.y += (p.vy * (1.6f - age) / 1.6f + 120f * age) * dt
            drawCircle(p.color.copy(alpha = 1f - age / 1.6f), 7f, Offset(p.x, p.y))
        }
        if (sparks.isEmpty()) drawCircle(Color.White.copy(alpha = 0.6f), 30f + sin(t * 4) * 6f, Offset(size.width / 2, size.height / 2), style = Stroke(4f))
    }
}

@Composable
private fun Vehicles(s: Sounds) {
    val t = rememberTime()
    var kind by remember { mutableIntStateOf(0) }
    val sounds = listOf("ウーウー！", "ウーウー カンカン！", "ピーポー ピーポー", "ブッブー！ はっしゃ しまーす", "プップー！")
    val names = listOf("パトカー", "しょうぼうしゃ", "きゅうきゅうしゃ", "バス", "くるま")
    var size by remember { mutableStateOf(Size.Zero) }
    Box(Modifier.fillMaxSize()) {
        Canvas(Modifier.fillMaxSize().pointerInput(Unit) { detectTapGestures { s.say(sounds[kind]); s.pop(11) } }) {
            size = this.size
            drawRect(Color(0xFFBFE6FF))
            listOf(0.1f to 0.35f, 0.25f to 0.25f, 0.62f to 0.3f, 0.8f to 0.38f).forEach { (x, hh) -> drawRect(listOf(Color(0xFFFFC8D8), Color(0xFFD8C8FF), Color(0xFFFFE6A8))[(x * 10).toInt() % 3], Offset(x * size.width, size.height * (0.72f - hh)), Size(size.width * 0.13f, size.height * hh)) }
            drawRect(Color(0xFF6E6E7A), Offset(0f, size.height * 0.72f), Size(size.width, size.height * 0.28f))
            for (i in 0 until 8) drawRect(Color.White, Offset((i * 0.14f + (t * 0.05f) % 0.14f) * size.width, size.height * 0.85f), Size(size.width * 0.07f, 8f))
            val w = size.width * 0.36f
            val x = ((t * 0.1f + 0.45f) % 1.45f - 0.4f) * size.width
            vehicle(kind, x, size.height * 0.8f, w, (t * 3).toInt() % 2 == 0)
        }
        Row(Modifier.align(Alignment.BottomCenter).padding(12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            names.forEachIndexed { i, n ->
                Box(
                    Modifier.clip(RoundedCornerShape(16.dp)).background(if (i == kind) Color(0xFFFFE07A) else Color.White).border(3.dp, Ink.copy(alpha = 0.3f), RoundedCornerShape(16.dp))
                        .clickable { kind = i; s.say(n) }.padding(horizontal = 14.dp, vertical = 8.dp),
                ) { Text(n, fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Ink) }
            }
        }
    }
}

@Composable
private fun Toaster(s: Sounds) {
    val t = rememberTime()
    var stage by remember { mutableIntStateOf(0) }
    var since by remember { mutableFloatStateOf(0f) }
    if (stage == 1 && t - since > 1.6f) { stage = 2; since = t; s.pop(7); s.say("チン！ やけたよ") }
    Canvas(Modifier.fillMaxSize().pointerInput(Unit) {
        detectTapGestures { if (stage == 0) { stage = 1; since = t; s.pop(4) } else if (stage == 2) { stage = 0; s.say("いただきます！") } }
    }) {
        val w = minOf(size.width * 0.5f, size.height * 0.75f)
        val c = Offset(size.width / 2, size.height * 0.62f)
        val toastY = when (stage) { 0 -> c.y - w * 0.42f; 1 -> c.y - w * 0.2f; else -> c.y - w * 0.75f - maxOf(0f, sin((t - since) * 10f)) * w * 0.15f * maxOf(0f, 1 - (t - since)) }
        drawRoundRect(Color(0xFFD9A15A), Offset(c.x - w * 0.32f, toastY - w * 0.18f), Size(w * 0.64f, w * 0.5f), CornerRadius(w * 0.12f))
        drawRoundRect(if (stage == 2) Color(0xFFF2C27A) else Color(0xFFFFEBC8), Offset(c.x - w * 0.27f, toastY - w * 0.13f), Size(w * 0.54f, w * 0.42f), CornerRadius(w * 0.1f))
        if (stage == 2) face(Offset(c.x, toastY + w * 0.05f), w * 0.15f, 1)
        drawRoundRect(Color(0xFFFF8F6B), Offset(c.x - w / 2, c.y - w * 0.32f), Size(w, w * 0.62f), CornerRadius(w * 0.12f))
        drawRoundRect(Color(0xFF6B4A3A), Offset(c.x - w * 0.34f, c.y - w * 0.34f), Size(w * 0.68f, w * 0.05f), CornerRadius(w * 0.02f))
        drawRoundRect(Color(0xFFBBBBC8), Offset(c.x + w / 2, c.y - w * 0.2f + (if (stage == 1) w * 0.2f else 0f)), Size(w * 0.12f, w * 0.06f), CornerRadius(w * 0.02f))
        face(Offset(c.x, c.y), w * 0.25f, if (stage == 1) 1 else 0)
        if (stage == 1) for (i in 0 until 3) sparkle(Offset(c.x + (i - 1) * w * 0.3f, c.y - w * 0.5f - ((t * 2 + i) % 1f) * w * 0.2f), w * 0.03f, Color(0xFFFFA96B))
    }
}

@Composable
private fun Animals(s: Sounds) {
    val t = rememberTime()
    val cries = listOf("にゃー", "わんわん", "ぴょん ぴょん", "がおー", "ぶーぶー", "けろけろ")
    val names = listOf("ねこ", "いぬ", "うさぎ", "くま", "ぶた", "かえる")
    val hops = remember { mutableStateListOf<Float>().apply { repeat(6) { add(-9f) } } }
    var size by remember { mutableStateOf(Size.Zero) }
    Canvas(Modifier.fillMaxSize().pointerInput(Unit) {
        detectTapGestures { o ->
            val i = ((o.y / (size.height / 2)).toInt().coerceIn(0, 1)) * 3 + (o.x / (size.width / 3)).toInt().coerceIn(0, 2)
            hops[i] = t; s.say("${names[i]}、${cries[i]}")
        }
    }) {
        size = this.size
        for (i in 0 until 6) {
            val c = Offset(size.width / 3 * (i % 3 + 0.5f), size.height / 2 * (i / 3 + 0.62f))
            val k = t - hops[i]
            val jump = if (k in 0f..0.6f) sin(k / 0.6f * PI.toFloat()) * size.height * 0.08f else 0f
            animal(i, Offset(c.x, c.y - jump), minOf(size.width / 3, size.height / 2) * 0.28f, if (k in 0f..0.6f) 1 else 0)
        }
    }
}

@Composable
private fun Numbers(s: Sounds) {
    var n by remember { mutableIntStateOf(0) }
    val read = listOf("", "いち", "に", "さん", "よん", "ご", "ろく", "なな", "はち", "きゅう", "じゅう")
    Box(Modifier.fillMaxSize().pointerInput(Unit) { detectTapGestures { n = if (n >= 10) 1 else n + 1; s.say(read[n]); s.pop(n % 12) } }, contentAlignment = Alignment.Center) {
        if (n == 0) Text("タッチ してね", fontSize = 40.sp, color = Ink, fontWeight = FontWeight.Bold)
        else Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("$n", fontSize = 140.sp, fontWeight = FontWeight.Bold, color = Pastels[n % Pastels.size])
            Canvas(Modifier.fillMaxWidth(0.8f).height(130.dp)) {
                val r = minOf(size.width / 10.5f, size.height / 2.2f) * 0.45f
                for (i in 0 until n) ball(Offset(size.width / 2 + (i % 5 - 2) * r * 2.3f, size.height / 2 + (i / 5 - 0.5f) * r * 2.3f * (if (n > 5) 1f else 0f)), r, Pastels[i % Pastels.size])
            }
        }
    }
}

private val GAMES = listOf(
    Game("ぷちぷち", Color(0xFFFFE4EE), { ball(center, size.minDimension * 0.35f, Pastels[0]) }) { Bubbles(it) },
    Game("くだもの の き", Color(0xFFDDF3FF), { apple(center, size.minDimension * 0.3f) }) { FruitTree(it) },
    Game("でんわ", Color(0xFFFFF4D6), { drawRoundRect(Color(0xFFFF8FB1), Offset(size.width * 0.3f, size.height * 0.15f), Size(size.width * 0.4f, size.height * 0.7f), CornerRadius(20f)); face(Offset(size.width / 2, size.height * 0.4f), size.minDimension * 0.15f) }) { Phone(it) },
    Game("めだまやき", Color(0xFFFFE8E0), { drawCircle(Color.White, size.minDimension * 0.32f, center); drawCircle(Color(0xFFFFB627), size.minDimension * 0.15f, center); face(center, size.minDimension * 0.15f, 1) }) { Egg(it) },
    Game("もぐもぐ", Color(0xFFFFF0D6), { drawCircle(Color(0xFFFFE0C4), size.minDimension * 0.35f, center); face(center, size.minDimension * 0.35f, 2) }) { Mogumogu(it) },
    Game("たんぽぽ", Color(0xFFE8F8FF), { for (i in 0 until 12) { val a = i * 30f * PI.toFloat() / 180; drawCircle(Color.White, size.minDimension * 0.06f, Offset(center.x + cos(a) * size.minDimension * 0.28f, center.y + sin(a) * size.minDimension * 0.28f)) } }) { Dandelion(it) },
    Game("はなび", Color(0xFF2A2F66), { for (i in 0 until 12) { val a = i * 30f * PI.toFloat() / 180; drawCircle(Pastels[i % 6], size.minDimension * 0.05f, Offset(center.x + cos(a) * size.minDimension * 0.3f, center.y + sin(a) * size.minDimension * 0.3f)) } }) { Fireworks(it) },
    Game("のりもの", Color(0xFFDDF3FF), { vehicle(0, size.width * 0.1f, size.height * 0.75f, size.width * 0.8f, true) }) { Vehicles(it) },
    Game("トースター", Color(0xFFE6F6D8), { drawRoundRect(Color(0xFFFF8F6B), Offset(size.width * 0.2f, size.height * 0.35f), Size(size.width * 0.6f, size.height * 0.45f), CornerRadius(24f)); face(Offset(center.x, size.height * 0.58f), size.minDimension * 0.15f) }) { Toaster(it) },
    Game("どうぶつ", Color(0xFFFFF4E0), { animal(0, Offset(center.x, center.y + size.minDimension * 0.08f), size.minDimension * 0.28f) }) { Animals(it) },
    Game("かず", Color(0xFFEFE8FF), { ball(Offset(center.x - size.minDimension * 0.2f, center.y), size.minDimension * 0.15f, Pastels[1]); ball(Offset(center.x + size.minDimension * 0.2f, center.y), size.minDimension * 0.15f, Pastels[2]) }) { Numbers(it) },
)

/** タッチ で あそぼ：さわる と うごく・なる・しゃべる。えらぶ で 一覧、ちぇんじ で つぎ */
@Composable
fun TouchPlayScreen(onBack: () -> Unit) {
    BackHandler(onBack = onBack)
    val context = LocalContext.current
    val theme = themeById(Store.config.currentProfile.themeId)
    val speak = rememberSpeaker()
    val synth = remember { Synth(context) }
    val tone = remember { runCatching { ToneGenerator(AudioManager.STREAM_MUSIC, 70) }.getOrNull() }
    LaunchedEffect(Unit) { runCatching { synth.prepare(Voice.PIANO, Kit.FUN) } }
    DisposableEffect(Unit) { onDispose { synth.release(); tone?.release() } }
    val sounds = remember {
        Sounds(
            pop = { runCatching { synth.playPad(Kit.FUN, it % 12) } },
            dtmf = { n -> tone?.startTone(ToneGenerator.TONE_DTMF_0 + n, 160) },
            say = { speak(it) },
        )
    }
    var cur by remember { mutableIntStateOf(-1) }
    Column(Modifier.fillMaxSize().background(if (cur >= 0) GAMES[cur].bg else theme.bgBottom).windowInsetsPadding(WindowInsets.safeDrawing).padding(10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            RetroIconButton("🏠", theme, onClick = onBack)
            DotText(if (cur >= 0) GAMES[cur].title else "タッチ で あそぼ", theme, 26, theme.text, modifier = Modifier.padding(start = 10.dp).weight(1f))
            if (cur >= 0) {
                RetroButton("🔄 ちぇんじ", theme, Color(0xFFFF8F8F), fontSize = 18, modifier = Modifier.padding(end = 8.dp)) { cur = (cur + 1) % GAMES.size }
                RetroButton("▦ えらぶ", theme, Color(0xFF7FC8FF), fontSize = 18) { cur = -1 }
            }
        }
        Spacer(Modifier.height(8.dp))
        Box(Modifier.weight(1f).fillMaxWidth().clip(RoundedCornerShape(20.dp))) {
            if (cur < 0) {
                LazyVerticalGrid(GridCells.Adaptive(170.dp), Modifier.fillMaxSize()) {
                    itemsIndexed(GAMES) { i, g ->
                        Column(
                            Modifier.padding(8.dp).clip(RoundedCornerShape(20.dp)).background(g.bg).border(3.dp, Ink.copy(alpha = 0.2f), RoundedCornerShape(20.dp)).clickable { cur = i }.padding(8.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            Canvas(Modifier.fillMaxWidth().aspectRatio(1.2f)) { g.icon(this) }
                            Text(g.title, fontSize = 18.sp, fontWeight = FontWeight.Bold, color = if (g.bg.luminanceLow()) Color.White else Ink)
                        }
                    }
                }
            } else Box(Modifier.fillMaxSize()) { GAMES[cur].play(sounds) }
        }
    }
}

private fun Color.luminanceLow() = (red * 0.3f + green * 0.59f + blue * 0.11f) < 0.4f
