package app.thetahatch.kidshome.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.thetahatch.kidshome.data.Coding
import app.thetahatch.kidshome.data.Coding.Blk
import app.thetahatch.kidshome.data.Coding.Dir
import app.thetahatch.kidshome.data.Store
import kotlinx.coroutines.delay

private val CardMove = Color(0xFFB8D8FF)
private val CardRepeat = Color(0xFFFFD58A)
private val CardUntil = Color(0xFFC9F0A8)
private val CardIf = Color(0xFFE4CCFF)

/**
 * うごかして ゴール（プログラミング）：カード を ならべて ▶。ロボット が その とおり に うごく。
 * ぶつかったら どの カード で ぶつかったか が 赤 く なる ので、なおして もう いちど（デバッグ）。
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CodingScreen(onBack: () -> Unit) {
    BackHandler(onBack = onBack)
    val context = LocalContext.current
    val config = Store.config
    val theme = themeById(config.currentProfile.themeId)
    val speak = rememberSpeaker()
    val levels = remember { Coding.levels(context) }
    var li by remember { mutableIntStateOf(((0 until levels.size).firstOrNull { Coding.stars(context, config.currentProfileId, it) == 0 } ?: 0)) }
    val level = levels.getOrNull(li) ?: return
    var prog by remember(li) { mutableStateOf(listOf<Blk>()) }
    var selected by remember(li) { mutableStateOf<Long?>(null) }
    var nextId by remember { mutableLongStateOf(1L) }
    var pos by remember(li) { mutableStateOf(level.start) }
    var active by remember(li) { mutableStateOf<Long?>(null) }
    var bumped by remember(li) { mutableStateOf<Long?>(null) }
    var running by remember { mutableStateOf(false) }
    var result by remember(li) { mutableStateOf<Coding.Outcome?>(null) }
    var gotStars by remember(li) { mutableIntStateOf(0) }
    var showIntro by remember(li) { mutableStateOf(level.intro != null) }
    var starVersion by remember { mutableIntStateOf(0) }

    fun newId() = nextId.also { nextId++ }
    fun reset() { pos = level.start; active = null; bumped = null; result = null }

    /** 一覧 の 中 の id の ブロック を f で かえる（null なら けす） */
    fun edit(list: List<Blk>, id: Long, f: (Blk) -> Blk?): List<Blk> = list.mapNotNull { b ->
        when {
            b.id == id -> f(b)
            b is Blk.Repeat -> b.copy(body = edit(b.body, id, f))
            else -> b
        }
    }
    fun find(list: List<Blk>, id: Long?): Blk? = list.firstNotNullOfOrNull { b -> if (b.id == id) b else (b as? Blk.Repeat)?.let { find(it.body, id) } }

    fun add(b: Blk) {
        if (running) return
        reset()
        val target = find(prog, selected) as? Blk.Repeat
        prog = if (target != null && b !is Blk.Repeat) edit(prog, target.id) { (it as Blk.Repeat).copy(body = it.body + b) } else prog + b
        if (b is Blk.Repeat) selected = b.id
    }

    LaunchedEffect(running) {
        if (!running) return@LaunchedEffect
        reset()
        val r = Coding.run(level, prog)
        for (f in r.frames) {
            active = f.block
            if (f.bump) { bumped = f.block; break }
            pos = f.r to f.c
            delay(420)
        }
        result = r.outcome
        when (r.outcome) {
            Coding.Outcome.GOAL -> {
                gotStars = Coding.stars(level, prog)
                Coding.save(context, config.currentProfileId, li, gotStars); starVersion++
                app.thetahatch.kidshome.data.Points.earn(gotStars, "うごかして ゴール ${li + 1}", "coding:$li:$gotStars")
                speak(if (gotStars == 3) "やったね！ さいこう！" else "ゴール！ もっと すくない カード でも できるかな")
            }
            Coding.Outcome.BUMP -> speak("ごつん！ あかい カード の ところ で ぶつかったよ")
            Coding.Outcome.SHORT -> speak("とどかなかった。カード を たそう")
            Coding.Outcome.LONG -> speak("ながすぎる よ")
        }
        running = false
    }

    Column(Modifier.fillMaxSize().background(theme.bgBottom).windowInsetsPadding(WindowInsets.safeDrawing).padding(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            RetroIconButton("🏠", theme, onClick = onBack)
            DotText("うごかして ゴール", theme, 26, theme.text, modifier = Modifier.padding(start = 10.dp))
            Spacer(Modifier.width(10.dp))
            Row(Modifier.weight(1f).horizontalScroll(rememberScrollState())) {
                levels.indices.forEach { i ->
                    val s = remember(starVersion, i) { Coding.stars(context, config.currentProfileId, i) }
                    Column(
                        Modifier.padding(end = 4.dp).clip(RoundedCornerShape(10.dp)).background(if (i == li) theme.titleBars[0] else Color.White)
                            .border(2.dp, theme.outline.copy(alpha = 0.4f), RoundedCornerShape(10.dp)).clickable(enabled = !running) { li = i }.padding(horizontal = 8.dp, vertical = 2.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text("${i + 1}", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = theme.outline)
                        Text(if (s > 0) "⭐".repeat(s) else "・", fontSize = 9.sp)
                    }
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        Row(Modifier.weight(1f)) {
            // ---------------- マップ
            BoxWithConstraints(Modifier.weight(1f).fillMaxHeight(), contentAlignment = Alignment.Center) {
                val cell = minOf(maxWidth / level.cols, maxHeight / level.rows, 110.dp)
                val ax by animateFloatAsState(pos.second.toFloat(), tween(380), label = "x")
                val ay by animateFloatAsState(pos.first.toFloat(), tween(380), label = "y")
                Box(Modifier.size(cell * level.cols, cell * level.rows)) {
                    Canvas(Modifier.fillMaxSize()) {
                        val c = size.width / level.cols
                        for (r in 0 until level.rows) for (col in 0 until level.cols) {
                            val wall = level.at(r, col) == '#'
                            drawRoundRect(
                                if (wall) Color(0xFFB9D99A) else if ((r + col) % 2 == 0) Color(0xFFFFF6DC) else Color(0xFFFFEDC2),
                                Offset(col * c + 2, r * c + 2), Size(c - 4, c - 4), CornerRadius(c * 0.15f),
                            )
                        }
                    }
                    for (r in 0 until level.rows) for (col in 0 until level.cols) {
                        val ch = level.at(r, col)
                        if (ch == '#' || ch == 'G') Box(Modifier.offset(cell * col, cell * r).size(cell), contentAlignment = Alignment.Center) {
                            Text(if (ch == '#') "🌳" else "💎", fontSize = (cell.value * 0.55f).sp)
                        }
                    }
                    Box(Modifier.offset(cell * ax, cell * ay).size(cell), contentAlignment = Alignment.Center) {
                        Text(if (bumped != null) "😵" else if (result == Coding.Outcome.GOAL) "🥳" else "🤖", fontSize = (cell.value * 0.62f).sp)
                        if (bumped != null) Text("💥", fontSize = (cell.value * 0.4f).sp, modifier = Modifier.align(Alignment.TopEnd))
                    }
                }
            }
            Spacer(Modifier.width(12.dp))
            // ---------------- プログラム
            Column(Modifier.weight(1.15f).fillMaxHeight()) {
                val msg = when (result) {
                    Coding.Outcome.GOAL -> "🎉 ゴール！ " + "⭐".repeat(gotStars) + if (gotStars < 3) "（${level.best} まい で できる かな？）" else ""
                    Coding.Outcome.BUMP -> "💥 ごつん！ あかい カード の ところ で ぶつかったよ。なおして もう いちど"
                    Coding.Outcome.SHORT -> "🤔 とどかなかった… カード を たそう"
                    Coding.Outcome.LONG -> "😵 ながすぎる よ"
                    null -> if (showIntro) level.intro!! else "💡 " + level.hint
                }
                Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(Color.White).border(2.dp, theme.outline.copy(alpha = 0.3f), RoundedCornerShape(14.dp)).clickable { showIntro = false }.padding(10.dp)) {
                    Text("${theme.mascot} $msg", fontSize = 16.sp, color = theme.outline, lineHeight = 22.sp)
                }
                Spacer(Modifier.height(8.dp))
                RetroWindow(theme, Modifier.fillMaxWidth().weight(1f), title = "プログラム（${Coding.count(prog)} まい）", titleColor = theme.titleBars[2]) {
                    Column(Modifier.fillMaxSize().padding(8.dp).verticalScroll(rememberScrollState())) {
                        if (prog.isEmpty()) Text("した の カード を おして ならべよう", fontSize = 15.sp, color = theme.outline.copy(alpha = 0.6f))
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            prog.forEach { b ->
                                ProgCard(b, selected, active, bumped,
                                    onSelect = { id -> selected = if (selected == id) null else id },
                                    onChange = { id, nb -> prog = edit(prog, id) { nb }; reset() },
                                )
                            }
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
                // パレット
                Row(Modifier.horizontalScroll(rememberScrollState()), verticalAlignment = Alignment.CenterVertically) {
                    Dir.entries.forEach { d -> PaletteCard(d.arrow, CardMove) { add(Blk.Move(newId(), d)) } }
                    if ("repeat" in level.cards) PaletteCard("🔁", CardRepeat) { add(Blk.Repeat(newId(), 2, emptyList())) }
                    if ("until" in level.cards) PaletteCard("⏩→", CardUntil) { add(Blk.Until(newId(), Dir.RIGHT)) }
                    if ("if" in level.cards) PaletteCard("❓→↓", CardIf) { add(Blk.IfWall(newId(), Dir.RIGHT, Dir.DOWN)) }
                }
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    RetroButton(if (running) "…" else "▶ うごかす", theme, theme.titleBars[1], fontSize = 20, modifier = Modifier.weight(2f)) {
                        if (!running && prog.isNotEmpty()) { selected = null; running = true }
                    }
                    RetroButton("🗑 1 まい", theme, Color.White, fontSize = 16, modifier = Modifier.weight(1f)) {
                        if (running) return@RetroButton
                        val id = selected ?: lastId(prog)
                        if (id != null) { prog = edit(prog, id) { null }; selected = null; reset() }
                    }
                    RetroButton("🧹 ぜんぶ", theme, Color.White, fontSize = 16, modifier = Modifier.weight(1f)) { if (!running) { prog = emptyList(); selected = null; reset() } }
                    if (result == Coding.Outcome.GOAL && li + 1 < levels.size) RetroButton("つぎ ▶", theme, theme.titleBars[3], fontSize = 18, modifier = Modifier.weight(1f)) { li++ }
                }
            }
        }
    }
}

/** さいご の カード（くりかえし の 中 も） */
private fun lastId(list: List<Blk>): Long? {
    val b = list.lastOrNull() ?: return null
    return if (b is Blk.Repeat && b.body.isNotEmpty()) lastId(b.body) else b.id
}

@Composable
private fun PaletteCard(label: String, color: Color, onClick: () -> Unit) {
    Box(
        Modifier.padding(end = 8.dp).size(width = 74.dp, height = 62.dp).clip(RoundedCornerShape(12.dp)).background(color)
            .border(2.dp, Color(0xFF55456A), RoundedCornerShape(12.dp)).clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Text(label, fontSize = 26.sp, fontWeight = FontWeight.Bold, color = Color(0xFF3A2E4A)) }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ProgCard(b: Blk, selected: Long?, active: Long?, bumped: Long?, onSelect: (Long) -> Unit, onChange: (Long, Blk) -> Unit) {
    val sel = b.id == selected
    val border = when { b.id == bumped -> Color(0xFFE53950); b.id == active -> Color(0xFFFF9F1C); sel -> Color(0xFF3D7BE0); else -> Color(0xFF55456A) }
    val width = if (b.id == bumped || b.id == active || sel) 4.dp else 2.dp
    val shape = RoundedCornerShape(12.dp)
    when (b) {
        is Blk.Move -> Box(
            Modifier.size(58.dp).clip(shape).background(if (b.id == bumped) Color(0xFFFFC2CB) else CardMove).border(width, border, shape).clickable { onSelect(b.id) },
            contentAlignment = Alignment.Center,
        ) { Text(b.dir.arrow, fontSize = 30.sp, fontWeight = FontWeight.Bold, color = Color(0xFF2A3A5A)) }
        is Blk.Until -> Column(
            Modifier.clip(shape).background(if (b.id == bumped) Color(0xFFFFC2CB) else CardUntil).border(width, border, shape).clickable { onSelect(b.id) }.padding(6.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text("⏩ ${b.dir.arrow}", fontSize = 24.sp, fontWeight = FontWeight.Bold)
            Text("かべ まで", fontSize = 11.sp)
            if (sel) SmallArrow(b.dir) { onChange(b.id, b.copy(dir = b.dir.next())) }
        }
        is Blk.IfWall -> Column(
            Modifier.clip(shape).background(if (b.id == bumped) Color(0xFFFFC2CB) else CardIf).border(width, border, shape).clickable { onSelect(b.id) }.padding(6.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text("❓ ${b.go.arrow}", fontSize = 22.sp, fontWeight = FontWeight.Bold)
            Text("かべ なら ${b.turn.arrow}", fontSize = 12.sp, fontWeight = FontWeight.Bold)
            if (sel) Row {
                SmallArrow(b.go) { onChange(b.id, b.copy(go = b.go.next())) }
                Spacer(Modifier.width(4.dp))
                SmallArrow(b.turn) { onChange(b.id, b.copy(turn = b.turn.next())) }
            }
        }
        is Blk.Repeat -> Column(
            Modifier.clip(shape).background(CardRepeat.copy(alpha = 0.55f)).border(width, border, shape).padding(6.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.clickable { onSelect(b.id) }) {
                Text("🔁", fontSize = 22.sp)
                Box(
                    Modifier.padding(start = 4.dp).clip(RoundedCornerShape(8.dp)).background(Color.White).clickable {
                        if (!sel) onSelect(b.id) else onChange(b.id, b.copy(times = if (b.times >= 12) 2 else b.times + 1))
                    }.padding(horizontal = 8.dp),
                ) { Text("${b.times} かい", fontSize = 18.sp, fontWeight = FontWeight.Bold) }
                if (sel) Text("  ← ここ に 入る", fontSize = 11.sp, color = Color(0xFF3D7BE0))
            }
            FlowRow(Modifier.padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(4.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                b.body.forEach { c -> ProgCard(c, selected, active, bumped, onSelect, onChange) }
                if (b.body.isEmpty()) Box(Modifier.size(58.dp).clip(shape).border(2.dp, Color(0x6655456A), shape), contentAlignment = Alignment.Center) { Text("＋", fontSize = 24.sp, color = Color(0x8855456A)) }
            }
        }
    }
}

@Composable
private fun SmallArrow(d: Dir, onClick: () -> Unit) {
    Box(
        Modifier.padding(top = 4.dp).size(30.dp).clip(RoundedCornerShape(8.dp)).background(Color.White).border(1.dp, Color(0xFF55456A), RoundedCornerShape(8.dp)).clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Text(d.arrow, fontSize = 16.sp, fontWeight = FontWeight.Bold) }
}
