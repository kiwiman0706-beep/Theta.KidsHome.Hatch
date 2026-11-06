package app.thetahatch.kidshome.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import app.thetahatch.kidshome.data.Points
import app.thetahatch.kidshome.data.StampLibrary
import app.thetahatch.kidshome.data.Store
import app.thetahatch.kidshome.data.Sugoroku
import app.thetahatch.kidshome.data.Sugoroku.Kind
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.random.Random

/** コマ：スタンプ（img:id）か 絵文字 */
private data class Player(val name: String, val piece: String, val cpu: Boolean)

private val PIECE_EMOJI = listOf("🐰", "🐻", "🐱", "🐶", "🐼", "🦄", "🐸", "🚗")
private val PLAYER_COLORS = listOf(Color(0xFFFF8FB1), Color(0xFF7FC8FF), Color(0xFFFFD25A), Color(0xFF8EE08A))
private val KIND_COLORS = mapOf(
    Kind.NORMAL to Color(0xFFFFFFFF), Kind.QUIZ to Color(0xFFFFF0B3), Kind.GO to Color(0xFFCFF5C8),
    Kind.BACK to Color(0xFFFFD6D6), Kind.REST to Color(0xFFE3DAFF), Kind.STAR to Color(0xFFFFE3A8),
)

@Composable
fun SugorokuScreen(onBack: () -> Unit) {
    BackHandler(onBack = onBack)
    val theme = themeById(Store.config.currentProfile.themeId)
    var players by remember { mutableStateOf<List<Player>?>(null) }
    var level by remember { mutableIntStateOf(0) }
    Box(Modifier.fillMaxSize().background(theme.bgBottom).windowInsetsPadding(WindowInsets.safeDrawing).padding(12.dp)) {
        val ps = players
        if (ps == null) Setup(theme, level, { level = it }, onBack) { players = it }
        else Game(theme, ps, level, onQuit = { players = null })
    }
}

// ---------------------------------------------------------------- じゅんび

@Composable
private fun Setup(theme: KidTheme, level: Int, setLevel: (Int) -> Unit, onBack: () -> Unit, onStart: (List<Player>) -> Unit) {
    val me = Store.config.currentProfile.name
    val mine = remember(StampLibrary.version) { StampLibrary.mine() }
    val list = remember {
        mutableStateListOf(
            Player(me, mine.firstOrNull()?.let { "img:${it.id}" } ?: PIECE_EMOJI[0], false),
            Player("コンピューター", PIECE_EMOJI[1], true),
        )
    }
    var editing by remember { mutableIntStateOf(0) }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            RetroIconButton("🏠", theme, onClick = onBack)
            DotText("🎲 すごろく", theme, 28, theme.text, modifier = Modifier.padding(start = 10.dp))
        }
        DotText("だれ が あそぶ？（コマ を おして えらぶ）", theme, 18, modifier = Modifier.padding(top = 10.dp))
        Row(Modifier.padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            list.forEachIndexed { i, p ->
                Column(
                    Modifier.padding(end = 10.dp).clip(RoundedCornerShape(16.dp)).background(if (editing == i) PLAYER_COLORS[i] else Color.White)
                        .border(3.dp, theme.outline, RoundedCornerShape(16.dp)).clickable { editing = i }.padding(10.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Piece(p.piece, 64.dp)
                    DotText(p.name, theme, 15)
                    RetroButton(if (p.cpu) "🤖 コンピューター" else "🧒 ひと", theme, Color.White, fontSize = 13) {
                        list[i] = p.copy(cpu = !p.cpu, name = if (!p.cpu) "コンピューター" else if (i == 0) me else "${i + 1}P")
                    }
                }
            }
            if (list.size < 4) RetroButton("＋", theme, theme.titleBars[0], fontSize = 22) { list += Player("${list.size + 1}P", PIECE_EMOJI[list.size], false) }
            if (list.size > 1) RetroButton("－", theme, Color.White, fontSize = 22, modifier = Modifier.padding(start = 6.dp)) { list.removeAt(list.size - 1); editing = 0 }
        }
        DotText("${list[editing.coerceAtMost(list.size - 1)].name} の コマ", theme, 16, modifier = Modifier.padding(top = 10.dp))
        run {
            val e = editing.coerceAtMost(list.size - 1)
            StampPicker(theme, list[e].piece.removePrefix("img:"), Modifier.fillMaxWidth(), 60.dp) { list[e] = list[e].copy(piece = "img:$it") }
        }
        Row(Modifier.horizontalScroll(rememberScrollState()).padding(vertical = 4.dp)) {
            val e = editing.coerceAtMost(list.size - 1)
            PIECE_EMOJI.forEach { em -> Box(Modifier.padding(4.dp).clickable { list[e] = list[e].copy(piece = em) }) { Piece(em, 60.dp) } }
        }
        DotText("❓ マス の もんだい", theme, 16, modifier = Modifier.padding(top = 6.dp))
        Row {
            listOf("10 まで の たしざん", "20 まで の たしざん・ひきざん", "くく").forEachIndexed { i, l ->
                RetroButton(l, theme, if (level == i) theme.titleBars[2] else Color.White, fontSize = 15, modifier = Modifier.padding(end = 6.dp)) { setLevel(i) }
            }
        }
        RetroButton("▶ はじめる", theme, theme.titleBars[1], fontSize = 24, modifier = Modifier.padding(top = 16.dp)) { onStart(list.toList()) }
    }
}

@Composable
private fun Piece(piece: String, size: Dp) {
    if (piece.startsWith("img:")) {
        val img = remember(piece) { StampLibrary.bitmap(piece.removePrefix("img:"))?.asImageBitmap() }
        if (img != null) { Image(img, null, Modifier.size(size)); return }
    }
    Box(Modifier.size(size), contentAlignment = Alignment.Center) { Text(if (piece.startsWith("img:")) "❔" else piece, fontSize = (size.value * 0.62f).sp) }
}

// ---------------------------------------------------------------- あそぶ

@Composable
private fun Game(theme: KidTheme, players: List<Player>, level: Int, onQuit: () -> Unit) {
    val speak = rememberSpeaker()
    val scope = rememberCoroutineScope()
    val board = remember { Sugoroku.board(Random.nextInt()) }
    val pos = remember { mutableStateListOf(*Array(players.size) { 0 }) }
    val rest = remember { mutableStateListOf(*Array(players.size) { false }) }
    var turn by remember { mutableIntStateOf(0) }
    var dice by remember { mutableIntStateOf(1) }
    var busy by remember { mutableStateOf(false) }
    var msg by remember { mutableStateOf("${players[0].name} の ばん。🎲 を おしてね") }
    var quiz by remember { mutableStateOf<Sugoroku.Quiz?>(null) }
    var winner by remember { mutableStateOf<Int?>(null) }
    var pending: (suspend (Boolean) -> Unit)? by remember { mutableStateOf(null) }

    suspend fun walk(p: Int, steps: Int) {
        val dir = if (steps >= 0) 1 else -1
        repeat(kotlin.math.abs(steps)) {
            if (dir > 0 && pos[p] >= Sugoroku.SIZE - 1) return
            pos[p] = Sugoroku.move(pos[p], dir); delay(260)
        }
    }

    suspend fun nextTurn() {
        if (winner != null) return
        var t = (turn + 1) % players.size
        if (rest[t]) { rest[t] = false; msg = "${players[t].name} は やすみ 💤"; speak("${players[t].name} は やすみ"); delay(1200); t = (t + 1) % players.size }
        turn = t
        msg = "${players[t].name} の ばん" + if (players[t].cpu) "" else "。🎲 を おしてね"
        busy = false
    }

    suspend fun land(p: Int) {
        if (pos[p] >= Sugoroku.SIZE - 1) {
            winner = p; msg = "🎉 ${players[p].name} の かち！"
            speak("ゴール！ ${players[p].name} の かち")
            if (!players[p].cpu) Points.earn(5, "すごろく ゴール", "sugoroku:${System.currentTimeMillis() / 1_800_000}")
            return
        }
        when (board[pos[p]]) {
            Kind.GO -> { msg = "⏩ 2 すすむ！"; speak("ふたつ すすむ"); delay(500); walk(p, 2); land(p); return }
            Kind.BACK -> { msg = "🔙 2 もどる…"; speak("ふたつ もどる"); delay(500); walk(p, -2) }
            Kind.REST -> { msg = "💤 つぎ は やすみ"; speak("いっかい やすみ"); rest[p] = true; delay(700) }
            Kind.STAR -> {
                msg = "⭐ キラキラ マス！"
                if (!players[p].cpu) Points.earn(1, "すごろく ⭐", "sugoroku-star:${pos[p]}:${System.currentTimeMillis() / 1_800_000}")
                speak("キラキラ"); delay(700)
            }
            Kind.QUIZ -> {
                val q = Sugoroku.quiz(level)
                if (players[p].cpu) {
                    val ok = Random.nextInt(10) < 7
                    msg = "❓ ${q.text}　🤖 " + if (ok) "せいかい！ 2 すすむ" else "ざんねん"
                    delay(1300); if (ok) { walk(p, 2); land(p); return }
                } else {
                    quiz = q; msg = "❓ もんだい！ こたえる と 2 すすむ"
                    speak(q.text.replace("＝ ？", "は").replace("＋", "たす").replace("－", "ひく").replace("×", "かける"))
                    pending = { ok -> if (ok) { walk(p, 2); land(p) } else nextTurn() }
                    return
                }
            }
            Kind.NORMAL -> {}
        }
        nextTurn()
    }

    fun roll() {
        if (busy || winner != null || quiz != null) return
        busy = true
        scope.launch {
            repeat(8) { dice = Random.nextInt(1, 7); delay(70) }
            dice = Random.nextInt(1, 7)
            speak("$dice")
            msg = "${players[turn].name}：$dice"
            delay(400)
            walk(turn, dice)
            land(turn)
        }
    }

    // コンピューター は じぶん で ふる
    LaunchedEffect(turn, busy, winner) { if (winner == null && !busy && players[turn].cpu) { delay(900); roll() } }

    Row(Modifier.fillMaxSize()) {
        // ばん
        BoxWithConstraints(Modifier.weight(1f).fillMaxHeight().clip(RoundedCornerShape(18.dp)).background(Color(0xFFBFE8D2)).border(3.dp, theme.outline, RoundedCornerShape(18.dp)).padding(8.dp)) {
            val cw = maxWidth / Sugoroku.COLS; val ch = maxHeight / Sugoroku.ROWS
            // みち
            Canvas(Modifier.fillMaxSize()) {
                val w = size.width / Sugoroku.COLS; val h = size.height / Sugoroku.ROWS
                for (i in 0 until Sugoroku.SIZE - 1) {
                    val (c1, r1) = Sugoroku.cell(i); val (c2, r2) = Sugoroku.cell(i + 1)
                    drawLine(Color(0xFF8A6B4A), Offset((c1 + .5f) * w, (r1 + .5f) * h), Offset((c2 + .5f) * w, (r2 + .5f) * h), 14f, StrokeCap.Round)
                }
            }
            board.forEachIndexed { i, k ->
                val (c, r) = Sugoroku.cell(i)
                Box(Modifier.offset(cw * c, ch * r).size(cw, ch).padding(5.dp), contentAlignment = Alignment.Center) {
                    Column(
                        Modifier.fillMaxSize().clip(RoundedCornerShape(12.dp))
                            .background(if (i == 0) Color(0xFFB3E5FF) else if (i == Sugoroku.SIZE - 1) Color(0xFFFFC2D6) else KIND_COLORS.getValue(k))
                            .border(2.dp, theme.outline.copy(alpha = 0.6f), RoundedCornerShape(12.dp)),
                        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center,
                    ) {
                        when (i) {
                            0 -> Text("スタート", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = theme.outline)
                            Sugoroku.SIZE - 1 -> Text("🏁 ゴール", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = theme.outline)
                            else -> {
                                if (k.emoji.isNotEmpty()) Text(k.emoji, fontSize = 22.sp)
                                Text(if (k.label.isNotEmpty()) k.label else "$i", fontSize = 11.sp, color = theme.outline.copy(alpha = 0.7f))
                            }
                        }
                    }
                }
            }
            // コマ
            players.forEachIndexed { p, pl ->
                val (c, r) = Sugoroku.cell(pos[p])
                val dx = if (p % 2 == 0) (-10).dp else 10.dp; val dy = if (p < 2) (-8).dp else 8.dp
                val x by animateDpAsState(cw * c + cw / 2 - 26.dp + dx, tween(220), label = "x")
                val y by animateDpAsState(ch * r + ch / 2 - 26.dp + dy, tween(220), label = "y")
                Box(Modifier.offset(x, y).size(52.dp).clip(CircleShape).background(PLAYER_COLORS[p].copy(alpha = 0.55f)).border(if (turn == p) 3.dp else 0.dp, theme.outline, CircleShape), contentAlignment = Alignment.Center) {
                    Piece(pl.piece, 46.dp)
                }
            }
        }
        // よこ
        Column(Modifier.width(250.dp).fillMaxHeight().padding(start = 10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                RetroIconButton("⬅️", theme, size = 44.dp, onClick = onQuit)
                DotText("すごろく", theme, 22, theme.text, modifier = Modifier.padding(start = 6.dp))
            }
            players.forEachIndexed { p, pl ->
                Row(
                    Modifier.fillMaxWidth().padding(top = 4.dp).clip(RoundedCornerShape(12.dp)).background(if (turn == p) PLAYER_COLORS[p] else Color.White.copy(alpha = 0.7f)).padding(4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Piece(pl.piece, 34.dp)
                    Text(pl.name + if (rest[p]) " 💤" else "", fontSize = 15.sp, modifier = Modifier.weight(1f).padding(start = 4.dp), maxLines = 1)
                    Text(if (pos[p] >= Sugoroku.SIZE - 1) "🏁" else "あと ${Sugoroku.SIZE - 1 - pos[p]}", fontSize = 13.sp)
                }
            }
            Spacer(Modifier.height(10.dp))
            val human = winner == null && !players[turn].cpu && !busy
            Box(
                Modifier.size(120.dp).clip(RoundedCornerShape(24.dp)).background(if (human) Color.White else Color(0xFFEEEEEE))
                    .border(4.dp, theme.outline, RoundedCornerShape(24.dp)).clickable(enabled = human) { roll() },
                contentAlignment = Alignment.Center,
            ) { DiceFace(dice, theme) }
            DotText(if (human) "おして ふる！" else " ", theme, 14)
            Text(msg, fontSize = 18.sp, fontWeight = FontWeight.Bold, color = theme.outline, modifier = Modifier.padding(top = 8.dp))
            if (winner != null) RetroButton("🔁 もういちど", theme, theme.titleBars[1], fontSize = 18, modifier = Modifier.padding(top = 10.dp)) { onQuit() }
        }
    }

    quiz?.let { q ->
        Dialog(onDismissRequest = {}) {
            RetroWindow(theme, Modifier.width(420.dp), title = "❓ もんだい", titleColor = theme.titleBars[2]) {
                Column(Modifier.fillMaxWidth().padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    DotText(q.text, theme, 40, theme.text)
                    Row(Modifier.padding(top = 14.dp)) {
                        q.choices.forEach { c ->
                            RetroButton("$c", theme, Color.White, fontSize = 30, modifier = Modifier.padding(horizontal = 6.dp)) {
                                val ok = c == q.answer
                                quiz = null
                                msg = if (ok) "⭕ せいかい！ 2 すすむ" else "❌ こたえ は ${q.answer}"
                                speak(if (ok) "せいかい" else "ざんねん、こたえ は ${q.answer}")
                                if (ok) Points.earn(1, "すごろく の もんだい", "sugoroku-q:${q.text}")
                                val next = pending; pending = null
                                scope.launch { delay(700); next?.invoke(ok) }
                            }
                        }
                    }
                }
            }
        }
    }
}

/** サイコロ の め（てん で かく） */
@Composable
private fun DiceFace(n: Int, theme: KidTheme) {
    Canvas(Modifier.size(90.dp)) {
        val s = size.width; val r = s * 0.09f
        val a = s * 0.22f; val m = s * 0.5f; val b = s * 0.78f
        val dots = when (n) {
            1 -> listOf(m to m); 2 -> listOf(a to a, b to b); 3 -> listOf(a to a, m to m, b to b)
            4 -> listOf(a to a, b to a, a to b, b to b); 5 -> listOf(a to a, b to a, m to m, a to b, b to b)
            else -> listOf(a to a, b to a, a to m, b to m, a to b, b to b)
        }
        dots.forEach { (x, y) -> drawCircle(if (n == 1) Color(0xFFD03B5C) else theme.outline, if (n == 1) r * 1.5f else r, Offset(x, y)) }
    }
}
