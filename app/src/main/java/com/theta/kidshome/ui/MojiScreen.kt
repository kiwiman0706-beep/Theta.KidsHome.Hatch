package com.theta.kidshome.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.PointerType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.theta.kidshome.data.Moji
import com.theta.kidshome.data.MojiSet
import com.theta.kidshome.data.MojiStroke
import com.theta.kidshome.data.Store

/**
 * もじ れんしゅう：マスに ペン（ゆびでも）で書く。書いた字は 1 字ずつ保存され、
 * いつのまにか その子の「手書きフォントの素材」が集まる（おうちのひと メニューから書き出し）。
 * 学年が上がると、その学年の漢字が ふえる。
 */
@OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)
@Composable
fun MojiScreen(onBack: () -> Unit) {
    BackHandler(onBack = onBack)
    val context = LocalContext.current
    val profile = Store.config.currentProfile
    val theme = themeById(profile.themeId)
    val sets = remember(profile.id, profile.grade, profile.gradeYear) { Moji.sets(context, profile) }
    var set by remember { mutableStateOf(sets.first()) }
    val chars = remember(set) { set.chars.codePoints().toArray().map { String(Character.toChars(it)) } }
    var written by remember { mutableStateOf(Moji.written(context, profile.id)) }
    var index by remember(set) { mutableIntStateOf(chars.indexOfFirst { it !in written }.coerceAtLeast(0)) }
    val ch = chars.getOrElse(index) { chars.first() }
    val strokes = remember(ch) { mutableStateListOf<MojiStroke>().apply { written[ch]?.let { addAll(it) } } }
    val current = remember { mutableStateListOf<FloatArray>() }
    var guide by remember { mutableStateOf(true) }
    var penMode by remember { mutableStateOf(context.getSharedPreferences("drawing", 0).getBoolean("pen", false)) }
    var cheer by remember { mutableStateOf<String?>(null) }

    fun save() {
        if (strokes.isEmpty()) return
        Moji.save(context, profile.id, ch, strokes.toList())
        written = written + (ch to strokes.toList())
        val done = chars.count { it in written }
        cheer = if (done == chars.size) "🏅 ${set.title} ぜんぶ かけた！" else listOf("じょうず！", "すてき！", "いいね！", "かっこいい！").random()
        // つぎの まだ書いていない字へ
        val next = (1..chars.size).map { (index + it) % chars.size }.firstOrNull { chars[it] !in written }
        if (next != null) index = next
    }

    Column(Modifier.fillMaxSize().background(theme.bgBottom).windowInsetsPadding(WindowInsets.safeDrawing).padding(14.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            RetroIconButton("🏠", theme, onClick = onBack)
            DotText("もじ れんしゅう", theme, 28, theme.text, modifier = Modifier.padding(start = 10.dp))
            Spacer(Modifier.width(12.dp))
            Row(Modifier.weight(1f).horizontalScroll(rememberScrollState())) {
                sets.forEach { s ->
                    val n = s.chars.codePoints().toArray().count { String(Character.toChars(it)) in written }
                    RetroButton("${s.emoji} ${s.title} $n", theme, if (s == set) theme.titleBars[0] else Color.White, fontSize = 15, modifier = Modifier.padding(end = 6.dp)) { set = s }
                }
            }
        }
        Spacer(Modifier.height(10.dp))
        Row(Modifier.weight(1f)) {
            // 字の いちらん（書いた字は その子の字で）
            RetroWindow(
                theme, Modifier.width(330.dp).fillMaxHeight().padding(end = 6.dp, bottom = 6.dp),
                title = "${set.title}　${chars.count { it in written }} / ${chars.size}", titleColor = theme.titleBars[1],
            ) {
                LazyVerticalGrid(GridCells.Adaptive(50.dp), Modifier.padding(6.dp)) {
                    items(chars.size) { i ->
                        val c = chars[i]
                        val w = written[c]
                        Box(
                            Modifier.padding(2.dp).aspectRatio(1f).clip(RoundedCornerShape(6.dp))
                                .background(if (i == index) theme.titleBars[0] else if (w != null) Color.White else Color(0xFFF4F0F2))
                                .border(if (i == index) 3.dp else 1.dp, theme.outline.copy(alpha = if (i == index) 1f else 0.3f), RoundedCornerShape(6.dp))
                                .clickable { index = i },
                            contentAlignment = Alignment.Center,
                        ) {
                            if (w != null) Canvas(Modifier.fillMaxSize()) { drawIntoCanvas { Moji.draw(it.nativeCanvas, w, 0f, 0f, size.width) } }
                            else Text(c, fontSize = 22.sp, color = theme.outline.copy(alpha = 0.45f))
                        }
                    }
                }
            }
            Spacer(Modifier.width(16.dp))
            // かく ところ
            Column(Modifier.weight(1f).fillMaxHeight(), horizontalAlignment = Alignment.CenterHorizontally) {
                Box(
                    Modifier.weight(1f).aspectRatio(1f).clip(RoundedCornerShape(16.dp)).background(Color.White)
                        .border(4.dp, theme.outline, RoundedCornerShape(16.dp))
                        .pointerInput(ch, penMode) {
                            awaitEachGesture {
                                val down = awaitFirstDown()
                                val stylus = down.type == PointerType.Stylus || down.type == PointerType.Eraser
                                if (penMode && !stylus) return@awaitEachGesture
                                down.consume()
                                val sz = size.width.toFloat()
                                fun pt(o: Offset, pr: Float) = floatArrayOf((o.x / sz).coerceIn(0f, 1f), (o.y / sz).coerceIn(0f, 1f), if (stylus) pr.coerceIn(0f, 1f) else 0.5f)
                                current.clear()
                                current += pt(down.position, down.pressure)
                                while (true) {
                                    val ev = awaitPointerEvent()
                                    val c = ev.changes.firstOrNull { it.id == down.id } ?: break
                                    if (!c.pressed) break
                                    c.historical.forEach { h -> current += pt(h.position, c.pressure) }
                                    current += pt(c.position, c.pressure)
                                    c.consume()
                                }
                                strokes += current.toList()
                                current.clear()
                                cheer = null
                            }
                        },
                ) {
                    Canvas(Modifier.fillMaxSize()) {
                        // 田 の 点線
                        val dash = PathEffect.dashPathEffect(floatArrayOf(14f, 12f))
                        val lc = theme.outline.copy(alpha = 0.25f)
                        drawLine(lc, Offset(size.width / 2, 0f), Offset(size.width / 2, size.height), 3f, pathEffect = dash)
                        drawLine(lc, Offset(0f, size.height / 2), Offset(size.width, size.height / 2), 3f, pathEffect = dash)
                        drawIntoCanvas { cv ->
                            if (guide) {
                                val p = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                                    color = android.graphics.Color.argb(40, 0, 0, 0)
                                    textSize = size.width * 0.78f
                                    textAlign = android.graphics.Paint.Align.CENTER
                                    typeface = android.graphics.Typeface.SERIF
                                }
                                val fm = p.fontMetrics
                                cv.nativeCanvas.drawText(ch, size.width / 2, size.height / 2 - (fm.ascent + fm.descent) / 2, p)
                            }
                            Moji.draw(cv.nativeCanvas, strokes.toList() + listOfNotNull(current.toList().takeIf { it.isNotEmpty() }), 0f, 0f, size.width, android.graphics.Color.rgb(30, 30, 40))
                        }
                    }
                }
                Spacer(Modifier.height(10.dp))
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    RetroIconButton("⬅️", theme) { index = (index - 1 + chars.size) % chars.size }
                    RetroIconButton("↩️", theme) { if (strokes.isNotEmpty()) strokes.removeAt(strokes.lastIndex) }
                    RetroIconButton("🗑️", theme) { strokes.clear() }
                    RetroIconButton("👻", theme, guide) { guide = !guide }
                    RetroIconButton("🖊️", theme, penMode) {
                        penMode = !penMode
                        context.getSharedPreferences("drawing", 0).edit().putBoolean("pen", penMode).apply()
                    }
                    RetroButton("⭐ できた！", theme, theme.titleBars[2], fontSize = 22) { save() }
                    RetroIconButton("➡️", theme) { index = (index + 1) % chars.size }
                }
                cheer?.let { DotText("${theme.mascot} $it", theme, 22, theme.text, modifier = Modifier.padding(top = 6.dp)) }
            }
        }
    }
}

/** おうちのひと：学年（4 月に 自動で 1 つ上がる） */
@Composable
fun GradeEditor(profile: com.theta.kidshome.data.Profile) {
    val g = Moji.gradeOf(profile)
    Text("がくねん（もじ れんしゅう の漢字が この学年まで ふえます。4 月に 自動で 1 つ上がります）", fontSize = 13.sp, color = Color.Gray, modifier = Modifier.padding(top = 12.dp))
    FlowRow8 {
        (0..6).forEach { n ->
            androidx.compose.material3.FilterChip(
                selected = g == n,
                onClick = { Store.updateProfile(profile.id) { it.copy(grade = n, gradeYear = Moji.schoolYear()) } },
                label = { Text(if (n == 0) "小学校まえ" else "${n}年生") },
            )
        }
    }
}

/** おうちのひと：書いた字を フォントの素材として書き出す */
@Composable
fun MojiExportSection(config: com.theta.kidshome.data.AppConfig) {
    val context = LocalContext.current
    Section("✍️ 手書きフォントの素材（もじ れんしゅう）") {
        Text(
            "「もじ れんしゅう」で書いた字が 1 字ずつ保存されています。おれん字 の用紙と同じ形の PDF（そのまま取り込める想定）か、" +
                "画像（PNG）・SVG・線のデータ（JSON）の ZIP で書き出せます。",
            fontSize = 14.sp,
        )
        config.profiles.forEach { p ->
            val n = remember(p.id) { Moji.writtenChars(context, p.id).size }
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 6.dp)) {
                Text("${themeById(p.themeId).mascot} ${p.name}：${n}字", modifier = Modifier.weight(1f))
                androidx.compose.material3.OutlinedButton(enabled = n > 0, onClick = { shareExport(context, p, pdf = true) }) { Text("おれん字 PDF") }
                Spacer(Modifier.width(8.dp))
                androidx.compose.material3.OutlinedButton(enabled = n > 0, onClick = { shareExport(context, p, pdf = false) }) { Text("ZIP") }
            }
        }
    }
}

private fun shareExport(context: android.content.Context, p: com.theta.kidshome.data.Profile, pdf: Boolean) {
    runCatching {
        val dir = java.io.File(context.cacheDir, "export").apply { mkdirs() }
        val file = java.io.File(dir, "${p.name}_てがき.${if (pdf) "pdf" else "zip"}")
        if (pdf) Moji.exportOrenji(context, p.id, file) else Moji.exportZip(context, p.id, p.name, file)
        val uri = androidx.core.content.FileProvider.getUriForFile(context, "${context.packageName}.files", file)
        val send = android.content.Intent(android.content.Intent.ACTION_SEND)
            .setType(if (pdf) "application/pdf" else "application/zip")
            .putExtra(android.content.Intent.EXTRA_STREAM, uri)
            .addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
        context.startActivity(android.content.Intent.createChooser(send, "書き出す").addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK))
    }.onFailure { android.widget.Toast.makeText(context, "書き出せませんでした（${it.message}）", android.widget.Toast.LENGTH_LONG).show() }
}
