package com.theta.kidshome.ui

import android.content.res.Configuration
import android.media.AudioManager
import android.media.ToneGenerator
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.key.utf16CodePoint
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.theta.kidshome.data.Store
import com.theta.kidshome.data.TypingMatcher
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** れんしゅうの メニュー */
private data class Lesson(val emoji: String, val title: String, val items: List<String>, val shuffle: Boolean = true)

private val lessons = listOf(
    Lesson("🅰️", "ローマじ 1もじ", listOf("あ", "い", "う", "え", "お", "か", "き", "く", "け", "こ", "さ", "し", "す", "せ", "そ", "た", "ち", "つ", "て", "と", "な", "に", "ぬ", "ね", "の", "は", "ひ", "ふ", "へ", "ほ", "ま", "み", "む", "め", "も", "や", "ゆ", "よ", "ら", "り", "る", "れ", "ろ", "わ", "を", "ん")),
    Lesson("🏠", "ホームポジション", listOf("fff jjj", "ddd kkk", "sss lll", "aaa ;;;", "fj fj dk dk", "sl sl a; a;", "asdf jkl;", "fdsa ;lkj", "jf kd ls ;a", "asdf jkl; asdf"), shuffle = false),
    Lesson("🔤", "あいうえお", listOf("あいうえお", "かきくけこ", "さしすせそ", "たちつてと", "なにぬねの", "はひふへほ", "まみむめも", "やゆよ", "らりるれろ", "わをん"), shuffle = false),
    Lesson("🐶", "どうぶつ", listOf("いぬ", "ねこ", "うさぎ", "ぞう", "きりん", "らいおん", "ぱんだ", "こあら", "ぺんぎん", "かめ", "ひよこ", "りす", "くま", "さる", "かば", "きつね", "たぬき", "しまうま")),
    Lesson("🍓", "たべもの", listOf("りんご", "いちご", "ばなな", "すいか", "めろん", "ぶどう", "おにぎり", "らーめん", "かれー", "ぱん", "けーき", "ぷりん", "おすし", "たまご", "ぎゅうにゅう", "ちょこれーと")),
    Lesson("🚃", "のりもの", listOf("でんしゃ", "ばす", "くるま", "しんかんせん", "ひこうき", "ふね", "じてんしゃ", "しょうぼうしゃ", "きゅうきゅうしゃ", "ろけっと", "たくしー", "とらっく")),
    Lesson("💬", "みじかい ぶん", listOf("おはよう。", "きょうは いい てんき。", "がっこうに いきます。", "あそびに いこう！", "ありがとう。", "おやすみなさい。", "ごはんを たべた。", "えを かくのが すき。")),
)

/** キーをおす ゆび（0〜3 ひだり こゆび〜ひとさしゆび、4〜7 みぎ ひとさしゆび〜こゆび、8 おやゆび） */
private val fingerOf = buildMap {
    "1qaz" .forEach { put(it, 0) }; "2wsx".forEach { put(it, 1) }; "3edc".forEach { put(it, 2) }; "45rtfgvb".forEach { put(it, 3) }
    "67yuhjnm".forEach { put(it, 4) }; "8ik,".forEach { put(it, 5) }; "9ol.".forEach { put(it, 6) }; "0p;/-@:[]".forEach { put(it, 7) }; put(' ', 8)
}
private val fingerColors = listOf(
    Color(0xFFFFB3B3), Color(0xFFFFD6A0), Color(0xFFFFF0A0), Color(0xFFC9F0A8),
    Color(0xFFB8E8F0), Color(0xFFB8D4FF), Color(0xFFD9C4FF), Color(0xFFFFC4E8), Color(0xFFE6E6E6),
)
private val fingerNames = listOf(
    "ひだりの こゆび", "ひだりの くすりゆび", "ひだりの なかゆび", "ひだりの ひとさしゆび",
    "みぎの ひとさしゆび", "みぎの なかゆび", "みぎの くすりゆび", "みぎの こゆび", "おやゆび（スペース）",
)
private val rows = listOf("1234567890-", "qwertyuiop@", "asdfghjkl;:", "zxcvbnm,./")

/**
 * タイピング：物理キーボードをつないで、ローマ字で ことば を打つ（特打みたいに）。
 * つぎに押すキーが光り、押す ゆび を色で教える。まちがえても すすまないで まってくれる。
 */
@Composable
fun TypingScreen(onBack: () -> Unit) {
    BackHandler(onBack = onBack)
    val theme = themeById(Store.config.currentProfile.themeId)
    val hasKeyboard = LocalConfiguration.current.keyboard == Configuration.KEYBOARD_QWERTY
    var lesson by remember { mutableStateOf(lessons[0]) }
    var chart by remember { mutableStateOf(false) }
    var items by remember { mutableStateOf(listOf<String>()) }
    var index by remember { mutableIntStateOf(0) }
    var matcher by remember { mutableStateOf(TypingMatcher("")) }
    var tick by remember { mutableIntStateOf(0) }
    var mistakes by remember { mutableIntStateOf(0) }
    var startedAt by remember { mutableLongStateOf(0L) }
    var finishedAt by remember { mutableLongStateOf(0L) }
    var typedCount by remember { mutableIntStateOf(0) }
    val shake = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    val focus = remember { FocusRequester() }
    val tone = remember { runCatching { ToneGenerator(AudioManager.STREAM_MUSIC, 35) }.getOrNull() }
    DisposableEffect(Unit) { onDispose { tone?.release() } }

    fun start(l: Lesson) {
        lesson = l
        items = (if (l.shuffle) l.items.shuffled() else l.items).take(10)
        index = 0
        matcher = TypingMatcher(items[0])
        mistakes = 0; typedCount = 0; startedAt = 0L; finishedAt = 0L
        tick++
    }
    LaunchedEffect(Unit) { start(lessons[0]); focus.requestFocus() }

    fun press(c: Char) {
        if (finishedAt > 0 || items.isEmpty()) return
        if (startedAt == 0L) startedAt = System.currentTimeMillis()
        if (matcher.type(c)) {
            typedCount++
            if (matcher.done) {
                if (index + 1 >= items.size) {
                    finishedAt = System.currentTimeMillis()
                } else {
                    index++
                    matcher = TypingMatcher(items[index])
                }
            }
        } else {
            mistakes++
            tone?.startTone(ToneGenerator.TONE_PROP_NACK, 120)
            scope.launch { for (x in listOf(14f, -14f, 8f, -8f, 0f)) shake.animateTo(x, tween(40)) }
        }
        tick++
    }

    Box(
        Modifier.fillMaxSize()
            .focusRequester(focus)
            .onPreviewKeyEvent { e ->
                if (e.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                val cp = e.utf16CodePoint
                if (cp <= 0) return@onPreviewKeyEvent false
                press(cp.toChar())
                true
            }
            .focusable()
    ) {
        RetroBackground(theme)
        Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing).padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                RetroIconButton("🏠", theme, onClick = onBack)
                DotText("タイピング", theme, 28, theme.text, modifier = Modifier.padding(start = 10.dp))
                Spacer(Modifier.width(16.dp))
                Row(Modifier.horizontalScroll(rememberScrollState())) {
                    RetroButton("📖 ローマじ ひょう", theme, if (chart) theme.titleBars[0] else Color.White, fontSize = 15, modifier = Modifier.padding(end = 6.dp)) { chart = !chart }
                    lessons.forEach { l ->
                        RetroButton("${l.emoji} ${l.title}", theme, if (lesson == l) theme.titleBars[0] else Color.White, fontSize = 15, modifier = Modifier.padding(end = 6.dp)) {
                            chart = false; start(l); focus.requestFocus()
                        }
                    }
                }
            }
            if (!hasKeyboard) {
                DotText("⌨️ キーボードを つなぐと、もっと たのしいよ（したの キーを さわっても できるよ）", theme, 14, theme.text, modifier = Modifier.padding(top = 6.dp))
            }
            Spacer(Modifier.height(10.dp))

            if (chart) {
                RomajiChart(theme, Modifier.weight(1f).fillMaxWidth().padding(end = 6.dp))
                return@Column
            }
            // もんだい
            RetroWindow(theme, Modifier.fillMaxWidth().padding(end = 6.dp), title = "${lesson.emoji} ${lesson.title}　${minOf(index + 1, items.size)} / ${items.size}", titleColor = theme.titleBars[1]) {
                tick.let { }
                if (finishedAt > 0) {
                    Result(theme, (finishedAt - startedAt).coerceAtLeast(1), typedCount, mistakes) { start(lesson); focus.requestFocus() }
                } else {
                    Column(Modifier.fillMaxWidth().padding(16.dp).offset(x = shake.value.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        DotText(matcher.word, theme, 52)
                        val guide = matcher.guide()
                        val done = matcher.typed.length
                        Text(
                            buildAnnotatedString {
                                withStyle(SpanStyle(color = theme.accent)) { append(guide.take(done)) }
                                withStyle(SpanStyle(color = theme.outline.copy(alpha = 0.45f))) { append(guide.drop(done)) }
                            },
                            fontFamily = DotFont, fontSize = 34.sp, letterSpacing = 3.sp,
                        )
                        val next = matcher.nextKey()
                        if (next != null) {
                            DotText("つぎは「${if (next == ' ') "スペース" else next.toString()}」 ${fingerNames[fingerOf[next] ?: 8]}", theme, 18, modifier = Modifier.padding(top = 6.dp))
                        }
                        DotText("まちがい ${mistakes}こ", theme, 14, theme.outline.copy(alpha = 0.6f))
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
            // キーボードの絵（つぎのキーが光る）
            KeyboardMap(theme, if (finishedAt > 0) null else matcher.nextKey(), Modifier.weight(1f).fillMaxWidth().padding(end = 6.dp)) { press(it); focus.requestFocus() }
        }
    }
}

@Composable
private fun Result(theme: KidTheme, ms: Long, typed: Int, mistakes: Int, onAgain: () -> Unit) {
    val perMin = (typed * 60_000L / ms).toInt()
    val accuracy = if (typed + mistakes == 0) 100 else typed * 100 / (typed + mistakes)
    val stars = when {
        accuracy >= 95 && perMin >= 60 -> 3
        accuracy >= 85 -> 2
        else -> 1
    }
    Column(Modifier.fillMaxWidth().padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text("⭐".repeat(stars) + "☆".repeat(3 - stars), fontSize = 52.sp)
        DotText("${theme.mascot} できた！", theme, 30)
        DotText("じかん ${ms / 1000}びょう　1ぷんで ${perMin}もじ　せいかく ${accuracy}%", theme, 18, modifier = Modifier.padding(top = 6.dp))
        RetroButton("もういちど", theme, theme.titleBars[0], modifier = Modifier.padding(top = 12.dp), onClick = onAgain)
    }
}

@Composable
private fun KeyboardMap(theme: KidTheme, next: Char?, modifier: Modifier, onKey: (Char) -> Unit) {
    val blink = rememberInfiniteTransition(label = "key")
    val glow by blink.animateFloat(0.4f, 1f, infiniteRepeatable(tween(420), RepeatMode.Reverse), label = "g")
    RetroWindow(theme, modifier, title = "キーボード", titleColor = theme.titleBars[2]) {
        Column(Modifier.fillMaxSize().padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            rows.forEachIndexed { r, keys ->
                Row(Modifier.weight(1f).fillMaxWidth().padding(start = (r * 18).dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    keys.forEach { k -> Key(theme, k.toString(), k, k == next, glow, Modifier.weight(1f), onKey) }
                    if (r == 0) Spacer(Modifier.weight(0.5f))
                }
            }
            Row(Modifier.weight(1f).fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                Key(theme, "スペース", ' ', next == ' ', glow, Modifier.width(360.dp), onKey)
            }
        }
    }
}

@Composable
private fun Key(theme: KidTheme, label: String, c: Char, lit: Boolean, glow: Float, modifier: Modifier, onKey: (Char) -> Unit) {
    val base = fingerColors[fingerOf[c] ?: 8]
    val shape = RoundedCornerShape(10.dp)
    Box(
        modifier.fillMaxSize().clip(shape)
            .background(if (lit) Color(0xFFFFE066).copy(alpha = glow) else base)
            .border(if (lit) 4.dp else 2.dp, if (lit) theme.accent else theme.outline, shape)
            .clickable { onKey(c) },
        contentAlignment = Alignment.Center,
    ) {
        Text(label.uppercase(), fontFamily = DotFont, fontSize = 20.sp, color = theme.outline)
        // F と J の でっぱり
        if (c == 'f' || c == 'j') Box(Modifier.align(Alignment.BottomCenter).padding(bottom = 6.dp).size(width = 16.dp, height = 3.dp).background(theme.outline))
    }
}

/** ローマじ ひょう：子音（たて）× 母音（よこ）。さわると大きく出る */
@Composable
private fun RomajiChart(theme: KidTheme, modifier: Modifier) {
    val consonants = listOf("" to "あいうえお", "k" to "かきくけこ", "s" to "さしすせそ", "t" to "たちつてと", "n" to "なにぬねの",
        "h" to "はひふへほ", "m" to "まみむめも", "y" to "や　ゆ　よ", "r" to "らりるれろ", "w" to "わ　　　を",
        "g" to "がぎぐげご", "z" to "ざじずぜぞ", "d" to "だぢづでど", "b" to "ばびぶべぼ", "p" to "ぱぴぷぺぽ")
    val vowels = listOf("a", "i", "u", "e", "o")
    var picked by remember { mutableStateOf<String?>(null) }
    RetroWindow(theme, modifier, title = "ローマじ ひょう（こえ の もと＋あいうえお）", titleColor = theme.titleBars[3]) {
        Row(Modifier.fillMaxSize().padding(10.dp)) {
            Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
                Row {
                    Box(Modifier.width(56.dp))
                    vowels.forEach { v -> Box(Modifier.weight(1f).padding(2.dp), contentAlignment = Alignment.Center) { DotText(v.uppercase(), theme, 22, theme.accent) } }
                }
                consonants.forEach { (c, kana) ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.width(56.dp), contentAlignment = Alignment.Center) { DotText(c.uppercase().ifEmpty { "－" }, theme, 22, theme.accent) }
                        kana.forEachIndexed { i, k ->
                            val kk = k.toString()
                            val roma = if (kk.isBlank()) "" else com.theta.kidshome.data.Romaji.units(kk).first().spellings.first()
                            Box(
                                Modifier.weight(1f).padding(2.dp).height(54.dp).clip(RoundedCornerShape(10.dp))
                                    .background(if (kk.isBlank()) Color.Transparent else fingerColors[i % fingerColors.size].copy(alpha = 0.6f))
                                    .then(if (kk.isBlank()) Modifier else Modifier.clickable { picked = kk }),
                                contentAlignment = Alignment.Center,
                            ) {
                                if (kk.isNotBlank()) Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    DotText(kk, theme, 22)
                                    DotText(roma, theme, 13, theme.outline.copy(alpha = 0.7f))
                                }
                            }
                        }
                    }
                }
            }
            // えらんだ もじ を おおきく
            Column(Modifier.width(240.dp).padding(start = 12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                val k = picked
                if (k == null) {
                    DotText("もじ を さわってね", theme, 18)
                    DotText("こえ の もと（K S T…）と\nあいうえお（A I U E O）を\nあわせて うつよ", theme, 15, modifier = Modifier.padding(top = 10.dp))
                } else {
                    val sp = com.theta.kidshome.data.Romaji.units(k).first().spellings
                    DotText(k, theme, 96)
                    DotText(sp.first().uppercase(), theme, 52, theme.accent)
                    if (sp.size > 1) DotText("（" + sp.drop(1).joinToString("・") + " でも OK）", theme, 14)
                    val first = sp.first()
                    if (first.length >= 2) DotText("${first.dropLast(1).uppercase()} ＋ ${first.last().uppercase()}", theme, 26, modifier = Modifier.padding(top = 8.dp))
                }
            }
        }
    }
}
