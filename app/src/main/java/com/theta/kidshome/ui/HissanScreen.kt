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
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.theta.kidshome.data.Hissan
import com.theta.kidshome.data.Store

/**
 * ひっさん でんたく：ふつう の でんたく で 計算 すると、よこ に ひっさん の 書きかた が 出る。
 * 「ひとつずつ」で 1 だん ずつ 出して、じぶん で 考えながら すすめられる。
 */
@Composable
fun HissanScreen(onBack: () -> Unit) {
    BackHandler(onBack = onBack)
    val theme = themeById(Store.config.currentProfile.themeId)
    var a by remember { mutableStateOf("") }
    var op by remember { mutableStateOf<Char?>(null) }
    var b by remember { mutableStateOf("") }
    var sheet by remember { mutableStateOf<Hissan.Sheet?>(null) }
    var message by remember { mutableStateOf<String?>(null) }
    var step by remember { mutableStateOf(false) }
    var shown by remember { mutableIntStateOf(0) }

    fun clear() { a = ""; op = null; b = ""; sheet = null; message = null }
    fun key(k: String) {
        message = null
        when (k) {
            "C" -> clear()
            "⌫" -> when { b.isNotEmpty() -> b = b.dropLast(1); op != null -> op = null; else -> a = a.dropLast(1) }
            "+", "−", "×", "÷" -> {
                if (sheet != null) { a = sheet!!.answer.substringBefore(" ").takeIf { !it.startsWith("-") } ?: ""; b = ""; sheet = null }
                if (a.isNotEmpty() && b.isEmpty()) op = if (k == "−") '-' else k[0]
            }
            "=" -> {
                val x = a.toLongOrNull(); val y = b.toLongOrNull(); val o = op
                if (x == null || y == null || o == null) return
                val s = Hissan.calc(x, o, y)
                if (s == null) { message = "0 で わる こと は できないよ"; return }
                sheet = s; shown = if (step) 2 else Int.MAX_VALUE
            }
            else -> {
                if (sheet != null) clear()
                if (op == null) { if (a.length < 6) a = (a + k).trimStart('0').ifEmpty { "0" } }
                else if (b.length < (if (op == '÷') 4 else 6)) b = (b + k).trimStart('0').ifEmpty { "0" }
            }
        }
    }
    val opText = when (op) { '-' -> "−"; null -> ""; else -> op.toString() }

    Column(Modifier.fillMaxSize().background(theme.bgBottom).windowInsetsPadding(WindowInsets.safeDrawing).padding(14.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            RetroIconButton("🏠", theme, onClick = onBack)
            DotText("ひっさん でんたく", theme, 28, theme.text, modifier = Modifier.padding(start = 10.dp))
            Spacer(Modifier.weight(1f))
            RetroButton("👀 ぜんぶ", theme, if (!step) theme.titleBars[0] else Color.White, fontSize = 17, modifier = Modifier.padding(end = 6.dp)) { step = false; shown = Int.MAX_VALUE }
            RetroButton("👣 ひとつずつ", theme, if (step) theme.titleBars[0] else Color.White, fontSize = 17) { step = true; shown = 2 }
        }
        Spacer(Modifier.height(10.dp))
        Row(Modifier.weight(1f)) {
            // でんたく
            Column(Modifier.width(360.dp).fillMaxHeight()) {
                Box(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(Color(0xFFE8F2E0))
                        .border(3.dp, theme.outline, RoundedCornerShape(14.dp)).padding(horizontal = 14.dp, vertical = 10.dp),
                    contentAlignment = Alignment.CenterEnd,
                ) {
                    Column(horizontalAlignment = Alignment.End) {
                        Text("$a $opText $b".trim().ifEmpty { "0" }, fontSize = 30.sp, color = theme.outline, fontWeight = FontWeight.Bold, maxLines = 1)
                        Text(sheet?.let { "= ${it.answer}" } ?: message ?: " ", fontSize = if (message != null) 18.sp else 34.sp, color = theme.accent, fontWeight = FontWeight.Bold, maxLines = 1)
                    }
                }
                Spacer(Modifier.height(10.dp))
                val keys = listOf(listOf("7", "8", "9", "÷"), listOf("4", "5", "6", "×"), listOf("1", "2", "3", "−"), listOf("C", "0", "⌫", "+"))
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    keys.forEach { row ->
                        Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            row.forEach { k ->
                                val isOp = k in listOf("÷", "×", "−", "+")
                                CalcKey(k, theme, if (isOp) theme.titleBars[1] else if (k == "C" || k == "⌫") theme.bubbles[0] else Color.White, Modifier.weight(1f)) { key(k) }
                            }
                        }
                    }
                    Row(Modifier.weight(1f)) { CalcKey("=", theme, theme.titleBars[0], Modifier.weight(1f)) { key("=") } }
                }
            }
            Spacer(Modifier.width(16.dp))
            // ひっさん
            RetroWindow(theme, Modifier.weight(1f).fillMaxHeight().padding(end = 6.dp, bottom = 6.dp), title = "ひっさん", titleColor = theme.titleBars[2]) {
                val s = sheet
                if (s == null) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        DotText("けいさん すると ここ に ひっさん が でるよ", theme, 20, theme.outline.copy(alpha = 0.6f))
                    }
                } else {
                    val order = remember(s) { revealOrder(s) }
                    Column(Modifier.fillMaxSize().padding(12.dp)) {
                        s.note?.let { Text(it, fontSize = 14.sp, color = theme.outline) }
                        SheetView(s, theme, visible = order.take(shown).toSet(), modifier = Modifier.weight(1f).fillMaxWidth())
                        if (step) Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                            if (shown < order.size) RetroButton("つぎ ▶", theme, theme.titleBars[3], fontSize = 22) { shown++ }
                            else DotText("こたえ ${s.answer}", theme, 24, theme.text)
                        }
                    }
                }
            }
        }
    }
}

/** ひとつずつ の じゅん：もんだい → くりあがり など → けいさん → こたえ（わり算 の 商 は さいご） */
private fun revealOrder(s: Hissan.Sheet): List<Int> {
    val idx = s.rows.indices
    fun answer(i: Int) = s.rows[i].cells.any { it?.kind == Hissan.Kind.ANSWER }
    val problem = idx.filter { !s.rows[it].small && !answer(it) }
    val small = idx.filter { s.rows[it].small }
    val rest = idx.filter { it !in problem && it !in small }
    return problem + small + rest
}

@Composable
private fun CalcKey(k: String, theme: KidTheme, color: Color, modifier: Modifier, onClick: () -> Unit) {
    Box(
        modifier.fillMaxHeight().clip(RoundedCornerShape(14.dp)).background(color)
            .border(3.dp, theme.outline, RoundedCornerShape(14.dp)).clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Text(k, fontSize = 32.sp, color = theme.outline, fontWeight = FontWeight.Bold) }
}

@Composable
private fun SheetView(s: Hissan.Sheet, theme: KidTheme, visible: Set<Int>, modifier: Modifier) {
    BoxWithConstraints(modifier, contentAlignment = Alignment.Center) {
        val units = s.rows.sumOf { if (it.small) 0.55 else 1.0 }.toFloat()
        val cell: Dp = minOf(maxWidth / s.cols, maxHeight / units, 84.dp)
        val ink = theme.outline
        Column {
            s.rows.forEachIndexed { ri, r ->
                val h = if (r.small) cell * 0.55f else cell
                val show = ri in visible
                Row(
                    Modifier.height(h).drawBehind {
                        val rule = r.rule ?: return@drawBehind
                        if (!show) return@drawBehind
                        val w = cell.toPx()
                        drawLine(ink, Offset(rule.first * w + 2, 1.5.dp.toPx()), Offset((rule.last + 1) * w - 2, 1.5.dp.toPx()), 3.dp.toPx())
                    }
                ) {
                    (0 until s.cols).forEach { ci ->
                        val c = r.cells.getOrNull(ci)
                        Box(Modifier.size(cell, h), contentAlignment = Alignment.Center) {
                            if (show && c?.text == ")") {
                                // わり算 の かっこ：上 の 線 から つながる まがった 線
                                Canvas(Modifier.fillMaxSize()) {
                                    drawArc(
                                        ink, startAngle = -80f, sweepAngle = 160f, useCenter = false,
                                        topLeft = Offset(-size.width * 0.55f, -size.height * 0.02f), size = androidx.compose.ui.geometry.Size(size.width * 1.1f, size.height * 1.04f),
                                        style = androidx.compose.ui.graphics.drawscope.Stroke(3.dp.toPx()),
                                    )
                                }
                            } else if (show && c != null) {
                                val color = when (c.kind) {
                                    Hissan.Kind.ANSWER -> theme.accent
                                    Hissan.Kind.SMALL -> Color(0xFFE0457B)
                                    else -> ink
                                }
                                val fs = when {
                                    r.small -> cell.value * 0.4f
                                    c.kind == Hissan.Kind.OP -> cell.value * 0.55f
                                    else -> cell.value * 0.68f
                                }
                                Text(
                                    c.text, fontSize = fs.sp, color = color, fontWeight = FontWeight.Bold,
                                    modifier = if (c.kind == Hissan.Kind.STRUCK) Modifier.drawBehind {
                                        drawLine(Color(0xFFE0457B), Offset(size.width * 0.1f, size.height * 0.8f), Offset(size.width * 0.9f, size.height * 0.2f), 2.5.dp.toPx())
                                    } else Modifier,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
