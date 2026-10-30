package com.theta.kidshome.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.theta.kidshome.data.Store

/**
 * たしざん の ひょう：くくの ひょう と おなじ 形。
 * 「1〜9」と「11〜20」（おうよう）。答えを かくして 押すと ひらく・もんだい。
 * 右に 10 の まとまり（10 の わく）で 「10 と 3 で 13」。
 */
@Composable
fun TasuScreen(startHard: Boolean = false, startHidden: Boolean = false, onBack: () -> Unit) {
    BackHandler(onBack = onBack)
    val theme = themeById(Store.config.currentProfile.themeId)
    var hard by remember { mutableStateOf(startHard) }
    val rows = if (hard) (11..20).toList() else (1..9).toList()
    val cols = (1..9).toList()
    var hide by remember { mutableStateOf(startHidden) }
    var opened by remember(hard) { mutableStateOf(setOf<Pair<Int, Int>>()) }
    var selected by remember(hard) { mutableStateOf(rows[2] to 4) }
    var quiz by remember(hard) { mutableStateOf<Pair<Int, Int>?>(null) }
    var cheer by remember { mutableStateOf<String?>(null) }
    val rowColors = remember(theme) { (0 until 10).map { lerp(theme.titleBars[it % theme.titleBars.size], Color.White, 0.55f) } }
    val total = rows.size * cols.size

    fun visible(a: Int, b: Int) = !hide || (a to b) in opened

    fun tapCell(a: Int, b: Int) {
        selected = a to b
        cheer = null
        if (hide) {
            if (quiz == (a to b)) cheer = listOf("せいかい！", "すごい！", "よくできました！", "ばっちり！").random()
            opened = opened + (a to b)
            quiz = null
        }
    }

    fun newQuiz() {
        val rest = rows.flatMap { a -> cols.map { b -> a to b } }.filter { it !in opened }
        if (rest.isEmpty()) { cheer = "🏅 ぜんぶ ひらいた！"; return }
        val q = rest.random()
        quiz = q; selected = q; cheer = null
    }

    Column(Modifier.fillMaxSize().background(theme.bgBottom).windowInsetsPadding(WindowInsets.safeDrawing).padding(14.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            RetroIconButton("🏠", theme, onClick = onBack)
            DotText("たしざん の ひょう", theme, 28, theme.text, modifier = Modifier.padding(start = 10.dp))
            Spacer(Modifier.width(12.dp))
            RetroButton("1〜9", theme, if (!hard) theme.titleBars[1 % theme.titleBars.size] else Color.White, fontSize = 17, modifier = Modifier.padding(end = 6.dp)) { hard = false; cheer = null }
            RetroButton("11〜20", theme, if (hard) theme.titleBars[1 % theme.titleBars.size] else Color.White, fontSize = 17) { hard = true; cheer = null }
            Spacer(Modifier.weight(1f))
            if (hide) DotText("ひらいた ${opened.size} / $total", theme, 18, theme.text, modifier = Modifier.padding(end = 10.dp))
            RetroButton("👀 みる", theme, if (!hide) theme.titleBars[0] else Color.White, fontSize = 17, modifier = Modifier.padding(end = 6.dp)) { hide = false; quiz = null }
            RetroButton("🙈 かくす", theme, if (hide) theme.titleBars[0] else Color.White, fontSize = 17) {
                if (hide) opened = emptySet()
                hide = true; quiz = null; cheer = null
            }
        }
        Spacer(Modifier.height(10.dp))
        Row(Modifier.weight(1f)) {
            BoxWithConstraints(Modifier.weight(1f).fillMaxHeight(), contentAlignment = Alignment.TopCenter) {
                val cell = minOf(maxWidth / (cols.size + 1), maxHeight / (rows.size + 1))
                Column {
                    Row {
                        Box(Modifier.size(cell).padding(2.dp), contentAlignment = Alignment.Center) { Text("+", fontSize = (cell.value * 0.45f).sp, color = theme.outline) }
                        cols.forEach { b ->
                            Header("$b", cell, theme, Color.White, on = selected.second == b) { selected = selected.first to b; cheer = null }
                        }
                    }
                    rows.forEachIndexed { ri, a ->
                        Row {
                            Header("$a", cell, theme, rowColors[ri], on = selected.first == a) { selected = a to selected.second; cheer = null }
                            cols.forEach { b ->
                                val on = selected == (a to b)
                                val cross = selected.first == a || selected.second == b
                                val isQuiz = quiz == (a to b)
                                Box(
                                    Modifier.size(cell).padding(2.dp).clip(RoundedCornerShape(10.dp))
                                        .background(
                                            when {
                                                on -> theme.titleBars[0]
                                                !visible(a, b) -> lerp(rowColors[ri], theme.outline, 0.12f)
                                                cross -> rowColors[ri]
                                                else -> lerp(rowColors[ri], Color.White, 0.6f)
                                            },
                                        )
                                        .border(if (on || isQuiz) 3.dp else 1.dp, theme.outline.copy(alpha = if (on || isQuiz) 1f else 0.25f), RoundedCornerShape(10.dp))
                                        .clickable { tapCell(a, b) },
                                    contentAlignment = Alignment.Center,
                                ) {
                                    if (visible(a, b)) Text("${a + b}", fontSize = (cell.value * 0.4f).sp, fontWeight = FontWeight.Bold, color = theme.outline)
                                    else Text(if (isQuiz) "？" else "★", fontSize = (cell.value * (if (isQuiz) 0.4f else 0.22f)).sp, color = Color.White)
                                }
                            }
                        }
                    }
                }
            }
            Spacer(Modifier.width(14.dp))
            val (a, b) = selected
            val show = visible(a, b)
            val sum = a + b
            RetroWindow(theme, Modifier.width(330.dp).fillMaxHeight(), title = "$a に たす", titleColor = theme.titleBars[1 % theme.titleBars.size]) {
                Column(Modifier.fillMaxSize().padding(14.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    DotText("$a + $b = ${if (show) "$sum" else "？"}", theme, 40, theme.outline)
                    if (show && sum >= 10) DotText(
                        if (sum % 10 == 0) "10 が ${sum / 10} こ で $sum" else "${sum / 10 * 10} と ${sum % 10} で $sum",
                        theme, 20, theme.outline.copy(alpha = 0.75f), modifier = Modifier.padding(top = 4.dp),
                    )
                    Spacer(Modifier.height(8.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(14.dp).clip(RoundedCornerShape(7.dp)).background(Color(0xFFFF8FB1)))
                        Text(" $a  ", fontSize = 15.sp, color = theme.outline)
                        Box(Modifier.size(14.dp).clip(RoundedCornerShape(7.dp)).background(Color(0xFF6FB7FF)))
                        Text(" $b", fontSize = 15.sp, color = theme.outline)
                    }
                    TenFrames(a, if (show) b else 0, theme.outline, Modifier.weight(1f).fillMaxWidth().padding(vertical = 8.dp))
                    cheer?.let { DotText(it, theme, 24, theme.accent) }
                    if (hide) RetroButton("🎲 もんだい", theme, theme.titleBars[1 % theme.titleBars.size], fontSize = 16, modifier = Modifier.padding(top = 6.dp)) { newQuiz() }
                    Text(
                        if (hide) "★ を押すと こたえが ひらくよ" else "10 の わく が いっぱいに なったら 10！",
                        fontSize = 13.sp, color = theme.outline.copy(alpha = 0.7f), modifier = Modifier.padding(top = 6.dp),
                    )
                }
            }
        }
    }
}

/** 10 の わく（2 だん × 5）を ならべて、a こ（ピンク）と b こ（みずいろ）を じゅんに うめる */
@Composable
private fun TenFrames(a: Int, b: Int, line: Color, modifier: Modifier) {
    val frames = maxOf(1, (a + b + 9) / 10).coerceAtLeast((a + 9) / 10)
    Canvas(modifier) {
        val gap = 10.dp.toPx()
        val cellW = minOf(size.width / 5, (size.height - gap * (frames - 1)) / (frames * 2))
        val w = cellW * 5; val h = cellW * 2
        val x0 = (size.width - w) / 2
        val y0 = (size.height - (h * frames + gap * (frames - 1))) / 2
        for (f in 0 until frames) {
            val top = y0 + f * (h + gap)
            for (i in 0 until 10) {
                val n = f * 10 + i
                val cx = x0 + (i % 5) * cellW; val cy = top + (i / 5) * cellW
                drawRoundRect(Color.White, Offset(cx + 1, cy + 1), Size(cellW - 2, cellW - 2), CornerRadius(6f))
                drawRoundRect(line.copy(alpha = 0.35f), Offset(cx + 1, cy + 1), Size(cellW - 2, cellW - 2), CornerRadius(6f), style = Stroke(2f))
                val c = when {
                    n < a -> Color(0xFFFF8FB1)
                    n < a + b -> Color(0xFF6FB7FF)
                    else -> null
                }
                if (c != null) {
                    drawCircle(c, cellW * 0.36f, Offset(cx + cellW / 2, cy + cellW / 2))
                    drawCircle(line, cellW * 0.36f, Offset(cx + cellW / 2, cy + cellW / 2), style = Stroke(1.5f))
                }
            }
            drawRoundRect(line, Offset(x0, top), Size(w, h), CornerRadius(8f), style = Stroke(3f))
        }
    }
}
