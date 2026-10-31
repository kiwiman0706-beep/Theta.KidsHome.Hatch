package com.theta.kidshome.media

import androidx.activity.compose.BackHandler
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.animation.core.animateFloat
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import kotlinx.coroutines.Job
import com.theta.kidshome.ui.RetroButton
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.foundation.clickable
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.PointerId
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.theta.kidshome.data.Store
import com.theta.kidshome.ui.DotFont
import com.theta.kidshome.ui.DotText
import com.theta.kidshome.ui.KidTheme
import com.theta.kidshome.ui.RetroBackground
import com.theta.kidshome.ui.RetroIconButton
import com.theta.kidshome.ui.RetroWindow
import com.theta.kidshome.ui.themeById

private val WHITE = intArrayOf(0, 2, 4, 5, 7, 9, 11)
/** 黒けん：（左どなりの白けんの番号, 半音） */
private val BLACK = listOf(0 to 1, 1 to 3, 3 to 6, 4 to 8, 5 to 10)
private val NOTE_NAMES = listOf("ド", "レ", "ミ", "ファ", "ソ", "ラ", "シ")
private val NOTE_COLORS = listOf(
    Color(0xFFFF6B8B), Color(0xFFFF9F43), Color(0xFFF7C531), Color(0xFF6BCB77),
    Color(0xFF4DA3FF), Color(0xFF7B6BFF), Color(0xFFC77DFF),
)
private val PAD_COLORS = listOf(
    Color(0xFFFF8FAB), Color(0xFFFFB86B), Color(0xFFFFE066), Color(0xFF8FE3A0),
    Color(0xFF7FC8FF), Color(0xFFA99BFF), Color(0xFFFFA8E2), Color(0xFF9EE6E0),
    Color(0xFFFFC2C2), Color(0xFFD7F28B), Color(0xFFB8E2FF), Color(0xFFFFD1A8),
)

/**
 * がっき：上に ミニけんばん（2 オクターブ、ドレミの色つき）、下に ドラムパッド（8 こ）。
 * どちらも音色を変えられて、いくつ同時に押しても鳴る。
 */
@Composable
fun InstrumentScreen(onBack: () -> Unit, loopPreview: ((Looper) -> Unit)? = null) {
    BackHandler(onBack = onBack)
    val context = LocalContext.current
    val theme = themeById(Store.config.currentProfile.themeId)
    val synth = remember { Synth(context) }
    var voice by remember { mutableStateOf(Voice.PIANO) }
    var kit by remember { mutableStateOf(Kit.DRUMS) }
    var octave by remember { mutableIntStateOf(0) }
    var practice by remember { mutableStateOf(false) }
    val songs = Songs.builtIn + Store.config.songs
    var song by remember { mutableStateOf(songs.first()) }
    val notes = remember(song) { Songs.parse(song.notes) }
    var pos by remember { mutableIntStateOf(0) }
    var demo by remember { mutableStateOf<Job?>(null) }
    val scope = rememberCoroutineScope()
    val looper = remember {
        Looper(scope, play = { h ->
            if (h.key != null && h.voice != null) synth.playKey(h.voice, h.key, h.octave)
            else if (h.pad != null && h.kit != null) synth.playPad(h.kit, h.pad)
        }, click = { synth.click(it) })
    }
    var recordingPad by remember { mutableStateOf<Int?>(null) }
    var loopOpen by remember { mutableStateOf(loopPreview != null) }
    remember { loopPreview?.invoke(looper) }
    val micPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {}
    var sampleMsg by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(sampleMsg) { if (sampleMsg != null) { delay(3500); sampleMsg = null } }
    fun startSample(pad: Int) {
        if (!Sampler.hasPermission(context)) {
            sampleMsg = "🎤 マイク を つかって いいか きかれたら「ゆるす」→ もういちど ながおし"
            micPermission.launch(android.Manifest.permission.RECORD_AUDIO); return
        }
        recordingPad = pad
        Sampler.start(context, pad, synth) { ok ->
            if (recordingPad == pad) recordingPad = null
            sampleMsg = if (ok) "✅ パッド ${pad + 1} に いれたよ！ おしてみてね" else "🙉 おと が ちいさくて とれなかったよ。マイク に ちかづいて もういちど"
            // 入れた おと を すぐ きかせる（よみこみ を すこし まって）
            if (ok) scope.launch { delay(350); synth.playPad(Kit.MINE, pad) }
        }
    }

    LaunchedEffect(voice, kit) { synth.prepare(voice, kit) }
    DisposableEffect(Unit) {
        AudioPlayer.pause()
        onDispose {
            Sampler.stop()
            looper.clear()
            synth.release()
        }
    }

    Box(Modifier.fillMaxSize()) {
        RetroBackground(theme)
        Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing).padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                RetroIconButton("🏠", theme, onClick = onBack)
                DotText("がっき", theme, 28, theme.text, modifier = Modifier.padding(start = 10.dp))
                if (!synth.ready) DotText("　じゅんびちゅう…", theme, 16, theme.text)
                Spacer(Modifier.weight(1f))
                if (!loopOpen && looper.playing) DotText(if (looper.recording) "⏺ ろくおん中　" else "🔁 くりかえし中　", theme, 16, theme.text)
                RetroButton(if (loopOpen) "🔁 ループ を とじる" else "🔁 ループ", theme, if (loopOpen) theme.titleBars[1] else androidx.compose.ui.graphics.Color.White, fontSize = 16) { loopOpen = !loopOpen }
            }
            Spacer(Modifier.height(10.dp))
            if (loopOpen) {
                LoopPanel(theme, looper, kit, Modifier.weight(1.1f).fillMaxWidth().padding(end = 6.dp, bottom = 6.dp)) { k, p -> synth.playPad(k, p) }
                Spacer(Modifier.height(6.dp))
            }

            // ---- ドラムパッド（れんしゅう中は けんばん を大きく）
            if (!practice) {
            // パッド は 1 れつ（12 こ）。高さ を おさえて けんばん を 大きく
            RetroWindow(theme, Modifier.height(if (loopOpen) 120.dp else 150.dp).fillMaxWidth().padding(end = 6.dp, bottom = 6.dp)) {
                Row(Modifier.fillMaxSize().padding(6.dp), verticalAlignment = Alignment.CenterVertically) {
                    // セット：2 だん
                    Column(Modifier.padding(end = 6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Kit.entries.chunked(3).forEach { row ->
                            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) { row.forEach { k -> RetroIconButton(k.emoji, theme, kit == k, if (loopOpen) 44.dp else 50.dp) { kit = k } } }
                        }
                    }
                    Row(Modifier.weight(1f).fillMaxHeight(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        kit.pads.forEachIndexed { i, (emoji, label) ->
                            Pad(
                                theme, emoji, if (kit == Kit.MINE && recordingPad == i) "🔴" else label,
                                if (kit == Kit.MINE && recordingPad == i) Color(0xFFFF4D6D) else PAD_COLORS[i % PAD_COLORS.size],
                                Modifier.weight(1f).fillMaxHeight(),
                                onHoldStart = if (kit == Kit.MINE) ({ startSample(i) }) else null,
                                onHoldEnd = { if (recordingPad == i) Sampler.stop() },
                            ) {
                                synth.playPad(kit, i)
                                looper.record(Looper.Hit(0f, pad = i, kit = kit))
                            }
                        }
                    }
                }
            }
            if (kit == Kit.MINE && recordingPad == null && sampleMsg == null) DotText("🎤 じぶん の おと：パッド を ながおし → 赤く なったら こえ を だす → はなすと おわり（${Sampler.MAX_SECONDS} びょう まで）", theme, 14, theme.text)
            Spacer(Modifier.height(6.dp))
            }

            // ---- けんばん
            RetroWindow(
                theme, Modifier.weight(if (practice) 2.2f else 1.15f).fillMaxWidth().padding(end = 6.dp, bottom = 6.dp),
                title = if (practice) "れんしゅう　🎓 ${song.title}" else "けんばん　${voice.emoji} ${voice.label}",
                titleColor = theme.titleBars[0],
            ) {
                Row(
                    Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 6.dp, vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Voice.entries.forEach { v -> RetroIconButton(v.emoji, theme, voice == v, 46.dp) { voice = v } }
                    Spacer(Modifier.width(16.dp))
                    if (!practice) {
                        RetroIconButton("⬇️", theme, size = 46.dp) { octave = (octave - 1).coerceAtLeast(-1) }
                        DotText(when (octave) { -1 -> "ひくい"; 1 -> "たかい"; else -> "ふつう" }, theme, 16)
                        RetroIconButton("⬆️", theme, size = 46.dp) { octave = (octave + 1).coerceAtMost(1) }
                        Spacer(Modifier.width(16.dp))
                    }
                    RetroButton(if (practice) "おわる" else "🎓 れんしゅう", theme, theme.titleBars[1], fontSize = 16) {
                        practice = !practice
                        demo?.cancel()
                        pos = 0
                        octave = 0
                    }
                }
                if (practice) {
                    PracticeBar(
                        theme, songs, song, notes, pos, done = pos >= notes.size,
                        onSong = { song = it; pos = 0; demo?.cancel() },
                        onRestart = { pos = 0; demo?.cancel() },
                        onDemo = {
                            demo?.cancel()
                            demo = scope.launch {
                                pos = 0
                                for ((i, n) in notes.withIndex()) {
                                    pos = i
                                    synth.playKey(voice, n.key)
                                    delay(380L * n.beats)
                                }
                                pos = 0
                            }
                        },
                    )
                }
                Keyboard(
                    theme, Modifier.weight(1f).fillMaxWidth().padding(8.dp),
                    glow = if (practice) notes.getOrNull(pos)?.key else null,
                ) { key ->
                    synth.playKey(voice, key, octave)
                    looper.record(Looper.Hit(0f, key = key, voice = voice, octave = octave))
                    // 光っている けん を押したら つぎへ（まちがえても まってくれる）
                    if (practice && demo?.isActive != true && notes.getOrNull(pos)?.key == key) pos++
                }
            }
        }
        // ろくおん中 は 上 に 大きく 出す（音 の 大きさ と のこり びょう）
        val rp = recordingPad
        if (rp != null || sampleMsg != null) {
            Box(Modifier.align(Alignment.TopCenter).padding(top = 12.dp)) {
                Column(
                    Modifier.clip(RoundedCornerShape(20.dp)).background(if (rp != null) Color(0xFFFF4D6D) else Color(0xEE333344)).padding(horizontal = 24.dp, vertical = 10.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    if (rp != null) {
                        Text("🔴 ろくおん中… パッド ${rp + 1}　はなすと おわり（あと ${"%.1f".format((Sampler.MAX_SECONDS - Sampler.elapsed).coerceAtLeast(0f))} びょう）", color = Color.White, fontSize = 20.sp)
                        Box(Modifier.padding(top = 6.dp).width(320.dp).height(14.dp).clip(RoundedCornerShape(50)).background(Color.White.copy(alpha = 0.35f))) {
                            Box(Modifier.fillMaxWidth(Sampler.level.coerceIn(0.02f, 1f)).fillMaxHeight().background(Color.White))
                        }
                    } else Text(sampleMsg.orEmpty(), color = Color.White, fontSize = 18.sp)
                }
            }
        }
    }
}

@Composable
private fun Pad(
    theme: KidTheme, emoji: String, label: String, color: Color, modifier: Modifier,
    onHoldStart: (() -> Unit)? = null, onHoldEnd: () -> Unit = {}, onHit: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    var down by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(if (down) 0.9f else 1f, label = "pad")
    val shape = RoundedCornerShape(18.dp)
    Box(
        modifier.scale(scale).clip(shape)
            .background(if (down) Color.White else color)
            .border(3.dp, theme.outline, shape)
            // 指が触れた瞬間に鳴らす（はなすのを待たない）
            .pointerInput(Unit) {
                awaitEachGesture {
                    awaitFirstDown().consume()
                    onHit()
                    down = true
                    // ながおし（0.5 びょう）で録音（じぶんの おと のときだけ）
                    val hold = onHoldStart?.let { start -> scope.launch { delay(500); start() } }
                    waitForUpOrCancellation()
                    hold?.cancel()
                    onHoldEnd()
                    down = false
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(emoji, fontSize = 34.sp)
            Text(label, fontFamily = DotFont, fontSize = 16.sp, color = theme.outline)
        }
    }
}

/** 2 オクターブのけんばん。いくつ同時に押しても、指をすべらせても鳴る */
@Composable
internal fun Keyboard(theme: KidTheme, modifier: Modifier, glow: Int? = null, onKey: (Int) -> Unit) {
    val pressed = remember { mutableStateMapOf<PointerId, Int>() }
    val measurer = rememberTextMeasurer()
    val whiteCount = 15
    val blink = rememberInfiniteTransition(label = "glow")
    val glowAlpha by blink.animateFloat(0.35f, 1f, infiniteRepeatable(tween(450), RepeatMode.Reverse), label = "a")

    fun keyAt(o: Offset, size: Size): Int? {
        if (o.x < 0 || o.y < 0 || o.x > size.width || o.y > size.height) return null
        val w = size.width / whiteCount
        if (o.y < size.height * 0.6f) {
            for (oct in 0..1) for ((after, semi) in BLACK) {
                val cx = (oct * 7 + after + 1) * w
                if (o.x in cx - w * 0.3f..cx + w * 0.3f) return oct * 12 + semi
            }
        }
        val i = (o.x / w).toInt().coerceIn(0, whiteCount - 1)
        return if (i == 14) 24 else (i / 7) * 12 + WHITE[i % 7]
    }

    Canvas(
        modifier.pointerInput(Unit) {
            awaitPointerEventScope {
                while (true) {
                    val e = awaitPointerEvent()
                    val sz = Size(size.width.toFloat(), size.height.toFloat())
                    e.changes.forEach { c ->
                        if (c.pressed) {
                            val k = keyAt(c.position, sz)
                            if (k != null && pressed[c.id] != k) {
                                onKey(k)
                                pressed[c.id] = k
                            } else if (k == null) pressed.remove(c.id)
                            c.consume()
                        } else {
                            pressed.remove(c.id)
                        }
                    }
                }
            }
        }
    ) {
        val w = size.width / whiteCount
        val r = CornerRadius(10.dp.toPx())
        val line = 3.dp.toPx()
        val down = pressed.values.toSet()
        // 白けん
        for (i in 0 until whiteCount) {
            val semi = if (i == 14) 24 else (i / 7) * 12 + WHITE[i % 7]
            val rect = Rect(i * w + 2, 0f, (i + 1) * w - 2, size.height)
            val c = NOTE_COLORS[i % 7]
            drawRoundRect(if (semi in down) c else Color.White, rect.topLeft, rect.size, r)
            if (semi == glow && semi !in down) drawRoundRect(Color(0xFFFFE066).copy(alpha = glowAlpha), rect.topLeft, rect.size, r)
            drawRoundRect(theme.outline, rect.topLeft, rect.size, r, style = Stroke(line))
            // 下に ドレミ と色の丸
            drawCircle(c, w * 0.2f, Offset(rect.center.x, size.height - w * 1.0f))
            val label = measurer.measure(NOTE_NAMES[i % 7], TextStyle(fontFamily = DotFont, fontSize = (w * 0.26f).toSp(), color = theme.outline))
            drawText(label, topLeft = Offset(rect.center.x - label.size.width / 2, size.height - w * 0.42f - label.size.height / 2))
        }
        // 黒けん
        for (oct in 0..1) for ((after, semi) in BLACK) {
            val cx = (oct * 7 + after + 1) * w
            val rect = Rect(cx - w * 0.3f, 0f, cx + w * 0.3f, size.height * 0.6f)
            drawRoundRect(if (oct * 12 + semi in down) Color(0xFF8A7BFF) else Color(0xFF3A3150), rect.topLeft, rect.size, r)
            if (oct * 12 + semi == glow) drawRoundRect(Color(0xFFFFC94D).copy(alpha = glowAlpha), rect.topLeft, rect.size, r)
            drawRoundRect(theme.outline, rect.topLeft, rect.size, r, style = Stroke(line))
        }
    }
}

/** れんしゅう：曲えらび、ドレミの ならび（いまの音が大きく光る）、おてほん・はじめから */
@Composable
private fun PracticeBar(
    theme: KidTheme, songs: List<Song>, song: Song, notes: List<Note>, pos: Int, done: Boolean,
    onSong: (Song) -> Unit, onRestart: () -> Unit, onDemo: () -> Unit,
) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 8.dp)) {
        Row(Modifier.horizontalScroll(rememberScrollState()), verticalAlignment = Alignment.CenterVertically) {
            songs.forEach { s ->
                val on = s == song
                Box(
                    Modifier.padding(end = 6.dp, top = 2.dp, bottom = 2.dp).clip(RoundedCornerShape(12.dp))
                        .background(if (on) theme.titleBars[2] else Color.White)
                        .border(2.5.dp, theme.outline, RoundedCornerShape(12.dp))
                        .clickable { onSong(s) }.padding(horizontal = 12.dp, vertical = 4.dp)
                ) { DotText(s.title, theme, 15) }
            }
        }
        val scroll = rememberScrollState()
        LaunchedEffect(pos) { scroll.animateScrollTo((pos - 3).coerceAtLeast(0) * 120) }
        Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            RetroIconButton("▶️", theme, size = 44.dp, onClick = onDemo)
            RetroIconButton("↩️", theme, size = 44.dp, onClick = onRestart)
            if (done) {
                DotText("できた！ 🎉 すごい！", theme, 26, modifier = Modifier.padding(start = 12.dp))
            } else {
                Row(Modifier.weight(1f).horizontalScroll(scroll), verticalAlignment = Alignment.CenterVertically) {
                    notes.forEachIndexed { i, n ->
                        val c = NOTE_COLORS[WHITE.indexOfFirst { it == n.key % 12 }.coerceAtLeast(0)]
                        Box(
                            Modifier.padding(horizontal = 3.dp).clip(RoundedCornerShape(10.dp))
                                .background(if (i == pos) c else if (i < pos) Color(0xFFEDEDED) else Color.White)
                                .border(if (i == pos) 3.dp else 1.5.dp, theme.outline, RoundedCornerShape(10.dp))
                                .padding(horizontal = if (i == pos) 12.dp else 7.dp, vertical = 4.dp)
                        ) { DotText(n.name + "ー".repeat(n.beats - 1), theme, if (i == pos) 24 else 16, if (i < pos) Color.Gray else theme.outline) }
                    }
                }
            }
        }
    }
}

/** ループ：テンポ・はく・しょうせつ と、マス（タイムライン）。ドラム の マス は タップ で おける */
@Composable
private fun LoopPanel(theme: KidTheme, looper: Looper, kit: Kit, modifier: Modifier, preview: (Kit, Int) -> Unit) {
    val countIn = looper.playing && looper.countIn
    RetroWindow(
        theme, modifier,
        title = "🔁 ループ　${looper.bpm} BPM・${looper.beats} びょうし・${looper.bars} しょうせつ" + when {
            countIn -> "　…カウント"
            looper.recording -> "　⏺ ろくおん中"
            looper.playing -> "　▶ くりかえし中"
            else -> ""
        },
        titleColor = if (looper.recording) Color(0xFFFFB3C1) else theme.titleBars[1],
    ) {
        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            RetroIconButton(if (looper.playing) "⏹" else "▶️", theme, size = 46.dp) { looper.togglePlay() }
            RetroIconButton("⏺", theme, selected = looper.recording, size = 46.dp) { looper.pressRecord() }
            RetroIconButton("↩️", theme, size = 46.dp) { looper.undo() }
            RetroIconButton("🗑️", theme, size = 46.dp) { looper.clear() }
            Spacer(Modifier.width(8.dp))
            RetroIconButton("🔔", theme, selected = looper.metronome, size = 46.dp) { looper.metronome = !looper.metronome }
            RetroIconButton("🧲", theme, selected = looper.quantize, size = 46.dp) { looper.quantize = !looper.quantize }
            Spacer(Modifier.width(8.dp))
            DotText("はやさ", theme, 14)
            RetroIconButton("➖", theme, size = 40.dp) { looper.bpm = (looper.bpm - 2).coerceIn(Looper.BPMS) }
            DotText("${looper.bpm}", theme, 22)
            RetroIconButton("➕", theme, size = 40.dp) { looper.bpm = (looper.bpm + 2).coerceIn(Looper.BPMS) }
            Spacer(Modifier.width(8.dp))
            DotText("はく", theme, 14)
            Looper.BEATS.forEach { b -> RetroButton("$b", theme, if (looper.beats == b) theme.titleBars[0] else Color.White, fontSize = 15) { looper.changeBeats(b) } }
            Spacer(Modifier.width(8.dp))
            DotText("しょうせつ", theme, 14)
            Looper.BARS.forEach { b -> RetroButton("$b", theme, if (looper.bars == b) theme.titleBars[0] else Color.White, fontSize = 15) { looper.changeBars(b) } }
        }
        LoopGrid(theme, looper, kit, Modifier.weight(1f).fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp), preview)
        DotText(
            "🔔 メトロノーム　🧲 マスに ぴったり　⏺ で 1 しょうせつ かぞえてから ろくおん（もう一度 ⏺ で おわり）。ドラム の マス を タップ しても おける",
            theme, 12, theme.outline.copy(alpha = 0.7f), modifier = Modifier.padding(start = 10.dp, bottom = 4.dp),
        )
    }
}

@Composable
private fun LoopGrid(theme: KidTheme, looper: Looper, kit: Kit, modifier: Modifier, preview: (Kit, Int) -> Unit) {
    val measurer = rememberTextMeasurer()
    val steps = looper.steps
    val perBar = looper.stepsPerBar
    val rows = kit.pads.size
    val labelW = 40.dp
    Canvas(
        modifier.clip(RoundedCornerShape(10.dp)).background(Color.White).border(2.dp, theme.outline, RoundedCornerShape(10.dp))
            .pointerInput(kit, steps) {
                awaitEachGesture {
                    val down = awaitFirstDown()
                    val lw = labelW.toPx()
                    val rowH = size.height / (rows + 2f)
                    val col = ((down.position.x - lw) / ((size.width - lw) / steps)).toInt()
                    val row = (down.position.y / rowH).toInt()
                    if (col in 0 until steps && row in 0 until rows) {
                        if (looper.toggleStep(kit, row, col)) preview(kit, row)
                    }
                }
            },
    ) {
        val lw = labelW.toPx()
        val rowH = size.height / (rows + 2f)
        val cw = (size.width - lw) / steps
        // しょうせつ ごと に いろ を かえる
        for (bar in 0 until steps / perBar) {
            if (bar % 2 == 1) drawRect(theme.titleBars[0].copy(alpha = 0.18f), Offset(lw + bar * perBar * cw, 0f), Size(perBar * cw, size.height))
        }
        // よこ の せん と ラベル
        for (r in 0..rows) drawLine(theme.outline.copy(alpha = 0.15f), Offset(0f, r * rowH), Offset(size.width, r * rowH), 1.5f)
        kit.pads.forEachIndexed { r, (emoji, _) ->
            val t = measurer.measure(emoji, TextStyle(fontSize = (rowH * 0.55f / density).sp))
            drawText(t, topLeft = Offset((lw - t.size.width) / 2, r * rowH + (rowH - t.size.height) / 2))
        }
        val kt = measurer.measure("🎹", TextStyle(fontSize = (rowH * 0.7f / density).sp))
        drawText(kt, topLeft = Offset((lw - kt.size.width) / 2, rows * rowH + (rowH * 2 - kt.size.height) / 2))
        // たて の せん：マス・はく・しょうせつ
        for (c in 0..steps) {
            val x = lw + c * cw
            val (col, w) = when {
                c % perBar == 0 -> theme.outline to 3f
                c % Looper.STEPS_PER_BEAT == 0 -> theme.outline.copy(alpha = 0.45f) to 2f
                else -> theme.outline.copy(alpha = 0.12f) to 1f
            }
            drawLine(col, Offset(x, 0f), Offset(x, size.height), w)
        }
        // ならした もの
        looper.hits.forEach { h ->
            val x = lw + h.step * cw
            if (h.pad != null && h.kit != null) {
                val c = if (h.kit == kit) PAD_COLORS[h.pad % PAD_COLORS.size] else Color(0xFFBBBBBB)
                drawRoundRect(c, Offset(x + 1.5f, h.pad * rowH + 3f), Size(cw - 3f, rowH - 6f), CornerRadius(5f))
                drawRoundRect(theme.outline.copy(alpha = 0.6f), Offset(x + 1.5f, h.pad * rowH + 3f), Size(cw - 3f, rowH - 6f), CornerRadius(5f), style = Stroke(1.5f))
            } else if (h.key != null) {
                val top = rows * rowH
                val kh = rowH * 2
                val y = top + kh - (h.key + (h.octave + 1) * 12f) / (Synth.KEYS + 24f) * kh
                drawRoundRect(theme.accent, Offset(x + 1f, y - 4f), Size(cw * 1.6f, 8f), CornerRadius(4f))
            }
        }
        // いま の ばしょ
        val p = looper.position
        if (looper.playing && p >= 0) {
            val x = lw + p * cw
            drawLine(if (looper.recording) Color(0xFFFF4D6D) else theme.accent, Offset(x, 0f), Offset(x, size.height), 5f)
        } else if (looper.playing) {
            // カウント：のこり はく
            val left = kotlin.math.ceil(-p / Looper.STEPS_PER_BEAT).toInt()
            val t = measurer.measure("$left", TextStyle(fontSize = (size.height * 0.5f / density).sp, color = Color(0xFFFF4D6D), fontFamily = DotFont))
            drawText(t, topLeft = Offset((size.width - t.size.width) / 2, (size.height - t.size.height) / 2))
        }
    }
}
