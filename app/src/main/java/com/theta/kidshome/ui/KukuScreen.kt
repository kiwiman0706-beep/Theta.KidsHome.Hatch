package com.theta.kidshome.ui

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.animateFloatAsState
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
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.theta.kidshome.data.Kuku
import com.theta.kidshome.data.Store
import java.util.Locale

/** 九九の 読み上げ（端末の 音声読み上げ を使う） */
private class KukuVoice(context: Context) {
    var ready by mutableStateOf<Boolean?>(null)
        private set
    /** いま読んでいる 「a,b」 */
    var speaking by mutableStateOf<Pair<Int, Int>?>(null)
        private set
    private val main = Handler(Looper.getMainLooper())
    private var last: String? = null
    private val tts: TextToSpeech = TextToSpeech(context.applicationContext) { status ->
        main.post { ready = status == TextToSpeech.SUCCESS && setJapanese() }
    }

    private fun setJapanese() = tts.setLanguage(Locale.JAPAN) >= TextToSpeech.LANG_AVAILABLE

    init {
        tts.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(id: String) {
                val p = id.split(',').mapNotNull { it.toIntOrNull() }
                if (p.size == 2) main.post { speaking = p[0] to p[1] }
            }
            override fun onDone(id: String) { if (id == last) main.post { speaking = null } }
            @Deprecated("") override fun onError(id: String) { main.post { speaking = null } }
            override fun onStop(id: String, interrupted: Boolean) { main.post { speaking = null } }
        })
    }

    /** text を空にすると、その段は「さざんが」まで（もんだい） */
    fun say(lines: List<Kuku.Line>, slow: Boolean, questionOnly: Boolean = false) {
        if (ready != true) return
        tts.stop()
        tts.setSpeechRate(if (slow) 0.7f else 1.0f)
        lines.forEachIndexed { i, l ->
            val id = "${l.a},${l.b}"
            if (i > 0) tts.playSilentUtterance(if (slow) 450 else 250, TextToSpeech.QUEUE_ADD, "gap$i")
            tts.speak(if (questionOnly) Kuku.katakana(l.question) else l.speech, TextToSpeech.QUEUE_ADD, null, id)
            last = id
        }
    }

    fun stop() { tts.stop(); speaking = null }
    fun shutdown() { tts.stop(); tts.shutdown() }
}

/**
 * くくのひょう：9×9 の ます。
 * だん（よこ）や たて を押すと 読み上げ、ます を押すと その九九。
 * 「かくす」にすると 答えが かくれて、押すと ひらく。
 */
@Composable
fun KukuScreen(startHidden: Boolean = false, onBack: () -> Unit) {
    BackHandler(onBack = onBack)
    val context = LocalContext.current
    val theme = themeById(Store.config.currentProfile.themeId)
    val voice = remember { KukuVoice(context) }
    DisposableEffect(Unit) { onDispose { voice.shutdown() } }
    var hide by remember { mutableStateOf(startHidden) }
    var opened by remember { mutableStateOf(setOf<Pair<Int, Int>>()) }
    var selected by remember { mutableStateOf(3 to 4) }
    var quiz by remember { mutableStateOf<Pair<Int, Int>?>(null) }
    var slow by remember { mutableStateOf(false) }
    var cheer by remember { mutableStateOf<String?>(null) }
    val speaking = voice.speaking
    val danColors = remember(theme) { (0 until 9).map { lerp(theme.titleBars[it % theme.titleBars.size], Color.White, 0.55f) } }

    fun visible(a: Int, b: Int) = !hide || (a to b) in opened || speaking == (a to b)

    fun tapCell(a: Int, b: Int) {
        selected = a to b
        cheer = null
        if (hide) {
            if (quiz == (a to b)) cheer = listOf("せいかい！", "すごい！", "よくできました！", "ばっちり！").random()
            opened = opened + (a to b)
            quiz = null
        }
        voice.say(listOf(Kuku.line(a, b)), slow)
    }

    fun newQuiz() {
        val rest = (1..9).flatMap { a -> (1..9).map { b -> a to b } }.filter { it !in opened }
        if (rest.isEmpty()) { cheer = "🏅 ぜんぶ ひらいた！"; return }
        val q = rest.random()
        quiz = q; selected = q; cheer = null
        voice.say(listOf(Kuku.line(q.first, q.second)), slow, questionOnly = true)
    }

    Column(Modifier.fillMaxSize().background(theme.bgBottom).windowInsetsPadding(WindowInsets.safeDrawing).padding(14.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            RetroIconButton("🏠", theme, onClick = onBack)
            DotText("くくの ひょう", theme, 28, theme.text, modifier = Modifier.padding(start = 10.dp))
            Spacer(Modifier.weight(1f))
            if (hide) DotText("ひらいた ${opened.size} / 81", theme, 18, theme.text, modifier = Modifier.padding(end = 10.dp))
            RetroButton("👀 みる", theme, if (!hide) theme.titleBars[0] else Color.White, fontSize = 17, modifier = Modifier.padding(end = 6.dp)) { hide = false; quiz = null }
            RetroButton("🙈 かくす", theme, if (hide) theme.titleBars[0] else Color.White, fontSize = 17, modifier = Modifier.padding(end = 6.dp)) {
                if (hide) opened = emptySet() // もういちど 押すと ぜんぶ かくしなおす
                hide = true; quiz = null; cheer = null
            }
            RetroIconButton("🐢", theme, slow) { slow = !slow }
            Spacer(Modifier.width(6.dp))
            RetroIconButton("⏹️", theme) { voice.stop() }
        }
        Spacer(Modifier.height(10.dp))
        Row(Modifier.weight(1f)) {
            // ひょう
            BoxWithConstraints(Modifier.weight(1f).fillMaxHeight(), contentAlignment = Alignment.TopCenter) {
                val cell = minOf(maxWidth / 10, maxHeight / 10)
                Column {
                    Row {
                        Box(Modifier.size(cell).padding(2.dp), contentAlignment = Alignment.Center) { Text("×", fontSize = (cell.value * 0.4f).sp, color = theme.outline) }
                        for (b in 1..9) {
                            Header("$b", cell, theme, Color.White, on = selected.second == b || speaking?.second == b) {
                                selected = selected.first to b; cheer = null
                                voice.say(Kuku.column(b), slow)
                            }
                        }
                    }
                    for (a in 1..9) Row {
                        Header("${a}の\nだん", cell, theme, danColors[a - 1], on = selected.first == a || speaking?.first == a, small = true) {
                            selected = a to selected.second; cheer = null
                            voice.say(Kuku.dan(a), slow)
                        }
                        for (b in 1..9) {
                            val on = speaking == (a to b) || (speaking == null && selected == (a to b))
                            val cross = selected.first == a || selected.second == b
                            val pop by animateFloatAsState(if (speaking == (a to b)) 1.12f else 1f, label = "pop")
                            Box(
                                Modifier.size(cell).padding(2.dp).scale(pop).clip(RoundedCornerShape(10.dp))
                                    .background(
                                        when {
                                            on -> theme.titleBars[0]
                                            !visible(a, b) -> lerp(danColors[a - 1], theme.outline, 0.12f)
                                            cross -> danColors[a - 1]
                                            else -> lerp(danColors[a - 1], Color.White, 0.6f)
                                        },
                                    )
                                    .border(if (on || quiz == (a to b)) 3.dp else 1.dp, theme.outline.copy(alpha = if (on || quiz == (a to b)) 1f else 0.25f), RoundedCornerShape(10.dp))
                                    .clickable { tapCell(a, b) },
                                contentAlignment = Alignment.Center,
                            ) {
                                if (visible(a, b)) Text("${a * b}", fontSize = (cell.value * 0.4f).sp, fontWeight = FontWeight.Bold, color = theme.outline)
                                else Text(if (quiz == (a to b)) "？" else "★", fontSize = (cell.value * (if (quiz == (a to b)) 0.4f else 0.22f)).sp, color = Color.White)
                            }
                        }
                    }
                }
            }
            Spacer(Modifier.width(14.dp))
            // おおきく
            val (a, b) = speaking ?: selected
            val line = Kuku.line(a, b)
            val show = visible(a, b)
            RetroWindow(theme, Modifier.width(330.dp).fillMaxHeight(), title = "${a}の だん", titleColor = theme.titleBars[(a - 1) % theme.titleBars.size]) {
                Column(Modifier.fillMaxSize().padding(14.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    DotText("$a × $b = ${if (show) "${a * b}" else "？"}", theme, 40, theme.outline)
                    DotText(if (show) line.text else "${line.question} …", theme, 22, theme.outline.copy(alpha = 0.75f), modifier = Modifier.padding(top = 4.dp))
                    Spacer(Modifier.height(10.dp))
                    // a が b こぶん
                    Text("$a が $b こぶん", fontSize = 16.sp, color = theme.outline)
                    Dots(a, b, if (show) theme.titleBars[(a - 1) % theme.titleBars.size] else theme.outline.copy(alpha = 0.18f), theme.outline, Modifier.weight(1f).fillMaxWidth().padding(vertical = 8.dp))
                    cheer?.let { DotText(it, theme, 24, theme.accent) }
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(top = 6.dp)) {
                        RetroButton("🔊 よむ", theme, Color.White, fontSize = 16) { voice.say(listOf(line), slow) }
                        if (hide) RetroButton("🎲 もんだい", theme, theme.titleBars[1 % theme.titleBars.size], fontSize = 16) { newQuiz() }
                    }
                    when (voice.ready) {
                        false -> Text("読み上げが使えません（設定 → 音声読み上げ で 日本語を入れてください）", fontSize = 12.sp, color = Color(0xFFD03B5C), modifier = Modifier.padding(top = 6.dp))
                        else -> Text(if (hide) "★ を押すと こたえが ひらくよ" else "「〇のだん」や うえの すうじ を押すと よみあげるよ", fontSize = 13.sp, color = theme.outline.copy(alpha = 0.7f), modifier = Modifier.padding(top = 6.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun Header(text: String, cell: androidx.compose.ui.unit.Dp, theme: KidTheme, color: Color, on: Boolean, small: Boolean = false, onClick: () -> Unit) {
    Box(
        Modifier.size(cell).padding(2.dp).clip(RoundedCornerShape(10.dp))
            .background(if (on) theme.titleBars[0] else color)
            .border(2.dp, theme.outline, RoundedCornerShape(10.dp))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text, fontFamily = DotFont, fontSize = (cell.value * (if (small) 0.22f else 0.4f)).sp,
            lineHeight = (cell.value * (if (small) 0.26f else 0.45f)).sp, color = theme.outline,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        )
    }
}

/** a こ の まるが b れつ */
@Composable
private fun Dots(a: Int, b: Int, fill: Color, line: Color, modifier: Modifier) {
    Canvas(modifier) {
        val step = minOf(size.width / (a + 0.5f), size.height / (b + 0.5f), 54.dp.toPx())
        val r = step * 0.36f
        val x0 = (size.width - step * a) / 2 + step / 2
        val y0 = (size.height - step * b) / 2 + step / 2
        for (row in 0 until b) for (col in 0 until a) {
            val c = Offset(x0 + col * step, y0 + row * step)
            drawCircle(fill, r, c)
            drawCircle(line, r, c, style = androidx.compose.ui.graphics.drawscope.Stroke(1.5f))
        }
    }
}
