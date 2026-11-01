package com.theta.kidshome.ui

import android.speech.tts.TextToSpeech
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.theta.kidshome.data.Almanac
import kotlinx.coroutines.delay
import java.util.Calendar
import java.util.Locale

/** にほんご の よみあげ（ひらがな を そのまま 読む） */
@Composable
fun rememberSpeaker(): (String) -> Unit {
    val context = LocalContext.current
    var tts by remember { mutableStateOf<TextToSpeech?>(null) }
    DisposableEffect(Unit) {
        var t: TextToSpeech? = null
        t = TextToSpeech(context.applicationContext) { if (it == TextToSpeech.SUCCESS) { t?.language = Locale.JAPANESE; tts = t } }
        onDispose { t?.stop(); t?.shutdown() }
    }
    return { text -> tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "kh") }
}

// ---------------------------------------------------------------- とけい

/** みじかい はり ＝ なんじ、ながい はり ＝ なんぷん */
@Composable
fun ClockLegend(theme: KidTheme) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.width(26.dp).height(7.dp).clip(RoundedCornerShape(4.dp)).background(theme.outline))
            DotText(" みじかい はり ＝ なんじ", theme, 14)
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.width(38.dp).height(5.dp).clip(RoundedCornerShape(3.dp)).background(theme.accent))
            DotText(" ながい はり ＝ なんぷん", theme, 14)
        }
    }
    DotText("ながい はり が さす 小さい 数字 が 「ふん」だよ", theme, 12, theme.outline.copy(alpha = 0.6f))
}

/** とけい を 見て「なんじ なんぷん？」を えらぶ。ちょうど・はん → 5 ふん → 1 ぷん */
@Composable
fun ClockQuizDialog(theme: KidTheme, onDismiss: () -> Unit) {
    val speak = rememberSpeaker()
    val measurer = rememberTextMeasurer()
    var level by remember { mutableIntStateOf(0) }
    fun make(): Pair<Int, Int> {
        val h = (1..12).random()
        val m = when (level) { 0 -> listOf(0, 30).random(); 1 -> (0..11).random() * 5; else -> (0..59).random() }
        return h to m
    }
    var q by remember { mutableStateOf(make()) }
    var choices by remember { mutableStateOf(listOf<Pair<Int, Int>>()) }
    var result by remember { mutableStateOf<Boolean?>(null) }
    var score by remember { mutableIntStateOf(0) }
    fun next() {
        val a = make()
        val wrong = generateSequence { make() }.filter { it != a }.distinct().take(2).toList() +
            listOf(((a.first % 12) + 1) to a.second) // となり の 時（みじかい はり の まちがい）
        q = a
        choices = (listOf(a) + wrong.filter { it != a }.distinct().take(2)).shuffled()
        result = null
    }
    LaunchedEffect(level) { next() }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        RetroWindow(theme, Modifier.padding(24.dp).width(720.dp), title = "とけい の よみかた", titleColor = theme.titleBars[2]) {
            Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Canvas(Modifier.size(300.dp)) { clockFace(theme, measurer, q.first % 12, q.second, null) }
                    ClockLegend(theme)
                }
                Column(Modifier.padding(start = 20.dp).weight(1f), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row {
                        listOf("ちょうど・はん", "5 ふん", "1 ぷん").forEachIndexed { i, l ->
                            RetroButton(l, theme, if (level == i) theme.titleBars[1] else Color.White, fontSize = 14, modifier = Modifier.padding(end = 6.dp)) { level = i; score = 0 }
                        }
                    }
                    DotText("なんじ なんぷん？", theme, 26)
                    choices.forEach { c ->
                        val label = Almanac.readTime(c.first, c.second)
                        RetroButton("${c.first}:%02d  $label".format(c.second), theme, Color.White, fontSize = 18, modifier = Modifier.fillMaxWidth()) {
                            if (result != null) return@RetroButton
                            result = c == q
                            if (c == q) score++
                            speak(Almanac.readTime(q.first, q.second))
                        }
                    }
                    result?.let { ok ->
                        DotText(if (ok) "⭕ せいかい！" else "❌ こたえ は ${Almanac.readTime(q.first, q.second)}", theme, 22, if (ok) Color(0xFF2E9E6A) else Color(0xFFD03B5C))
                        RetroButton("つぎ ▶", theme, theme.titleBars[3], fontSize = 20) { next() }
                    }
                    DotText("⭐ $score", theme, 18)
                }
            }
        }
    }
}

// ---------------------------------------------------------------- じゅうにし

private val EtoColors = listOf(
    0xFFFFE0EA, 0xFFFFF3D6, 0xFFE3F6DA, 0xFFFFE4EC, 0xFFDDEFFC, 0xFFE6F6E4,
    0xFFDDF0FC, 0xFFEFE4FA, 0xFFFFE9DC, 0xFFFFF6DA, 0xFFDDEFFC, 0xFFFFEADF,
).map { Color(it) }
private val EtoInk = listOf(
    0xFFE04878, 0xFFF08A1C, 0xFF2E9E4A, 0xFFE04878, 0xFF2A6FD0, 0xFF2E9E4A,
    0xFF2A6FD0, 0xFF8E4CC2, 0xFFE8603C, 0xFFE89A1C, 0xFF2A6FD0, 0xFFE8603C,
).map { Color(it) }

/** じゅうにし：どうぶつ と いっしょ に おぼえよう。タップ で 読む、🔊 で じゅんばん に */
@Composable
fun JunishiDialog(theme: KidTheme, year: Int, onDismiss: () -> Unit) {
    val speak = rememberSpeaker()
    val now = Almanac.eto(year)
    var playing by remember { mutableIntStateOf(-1) }
    LaunchedEffect(playing >= 0) {
        if (playing < 0) return@LaunchedEffect
        for (i in Almanac.ETO.indices) { playing = i; speak(Almanac.ETO[i].kana); delay(900) }
        playing = -1
    }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        RetroWindow(theme, Modifier.padding(16.dp).fillMaxWidth(), title = "じゅうにし", titleColor = theme.titleBars[0], onClick = onDismiss) {
            Column(Modifier.padding(14.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                DotText("ことし（${year}ねん）は ${now.emoji} ${now.kana}（${now.animal}）どし", theme, 22)
                Spacer(Modifier.height(8.dp))
                Almanac.ETO.chunked(6).forEachIndexed { row, list ->
                    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        list.forEachIndexed { col, e ->
                            val i = row * 6 + col
                            val on = e == now || playing == i
                            Column(
                                Modifier.weight(1f).aspectRatio(0.78f).clip(RoundedCornerShape(16.dp)).background(EtoColors[i])
                                    .border(if (on) 4.dp else 2.dp, if (on) EtoInk[i] else theme.outline.copy(alpha = 0.25f), RoundedCornerShape(16.dp))
                                    .clickable { speak(if (e.kana == e.animal) e.kana else "${e.kana}、${e.animal}") }
                                    .padding(6.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                            ) {
                                Box(Modifier.align(Alignment.Start).size(26.dp).clip(CircleShape).background(EtoInk[i]), contentAlignment = Alignment.Center) {
                                    Text("${i + 1}", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                                }
                                Text(e.emoji, fontSize = 52.sp)
                                Text(e.kana, fontSize = 30.sp, fontWeight = FontWeight.Bold, color = EtoInk[i])
                                Text(e.kanji, fontSize = 20.sp, fontWeight = FontWeight.Bold, color = theme.outline)
                                if (e.kana != e.animal) Text(e.animal, fontSize = 13.sp, color = theme.outline)
                                if (e == now) Text("ことし", fontSize = 12.sp, color = EtoInk[i], fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
                Row(Modifier.padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    RetroButton("🔊 こえ に だして よんで みよう", theme, theme.titleBars[1], fontSize = 18) { if (playing < 0) playing = 0 }
                    DotText("  " + Almanac.ETO.joinToString("・") { it.kana }, theme, 18)
                }
            }
        }
    }
}

// ---------------------------------------------------------------- カレンダー と つき

/** こんげつ：むかし の よびかた・誕生石・誕生花・季節・節気・干支・祝日 */
@Composable
fun MonthInfo(theme: KidTheme, today: Calendar, compact: Boolean = false) {
    val y = today.get(Calendar.YEAR); val m = today.get(Calendar.MONTH) + 1; val d = today.get(Calendar.DAY_OF_MONTH)
    val info = Almanac.MONTHS[m - 1]
    val (season, se) = Almanac.season(m)
    val sekki = Almanac.sekki(m, d)
    val eto = Almanac.eto(y)
    var junishi by remember { mutableStateOf(false) }
    val fs = if (compact) 12 else 14
    Column(Modifier.fillMaxWidth().padding(top = 8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Chip(theme, "${m}がつ ＝ ${info.old}（${info.oldKana}）", fs)
            Chip(theme, "$se $season・${sekki.first}（${sekki.second}）", fs)
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.padding(end = 4.dp).size(14.dp).clip(CircleShape).background(Color(info.stoneColor)).border(1.dp, theme.outline, CircleShape))
            DotText("たんじょうせき：${info.stone}", theme, fs)
            Spacer(Modifier.width(12.dp))
            DotText("たんじょうか：${info.flowerEmoji} ${info.flower}", theme, fs)
        }
        Box(Modifier.clip(RoundedCornerShape(8.dp)).clickable { junishi = true }) {
            DotText("${eto.emoji} ことし は ${eto.kana}（${eto.kanji}・${eto.animal}）どし ▶ じゅうにし", theme, fs)
        }
        val hol = Almanac.holidays(y).filter { it.month == m }
        if (hol.isNotEmpty()) DotText("🎌 " + hol.joinToString("　") { "${it.day}にち ${it.kana}" }, theme, fs, Color(0xFFD03B5C))
        if (!compact) DotText(info.season, theme, fs, theme.outline.copy(alpha = 0.8f))
    }
    if (junishi) JunishiDialog(theme, y) { junishi = false }
}

@Composable
private fun Chip(theme: KidTheme, text: String, fs: Int) {
    Box(Modifier.padding(end = 6.dp).clip(RoundedCornerShape(10.dp)).background(theme.bubbles[1]).padding(horizontal = 8.dp, vertical = 2.dp)) {
        DotText(text, theme, fs)
    }
}

/** つき の かたち（月齢） */
@Composable
fun MoonRow(theme: KidTheme, now: Long, fs: Int = 14) {
    val moon = Almanac.moon(now)
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 4.dp)) {
        Text(moon.emoji, fontSize = (fs * 2).sp)
        Column(Modifier.padding(start = 6.dp)) {
            DotText("こんや の つき：${moon.name}", theme, fs)
            DotText(
                if (moon.name == "まんげつ") "げつれい ${"%.0f".format(moon.age)}" else "げつれい ${"%.0f".format(moon.age)}・まんげつ まで あと ${moon.toFull}にち",
                theme, fs - 2, theme.outline.copy(alpha = 0.7f),
            )
        }
    }
}
