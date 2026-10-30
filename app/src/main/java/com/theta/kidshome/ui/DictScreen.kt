package com.theta.kidshome.ui

import android.content.Intent
import android.graphics.BitmapFactory
import android.speech.tts.TextToSpeech
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.foundation.text.appendInlineContent
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.Placeholder
import androidx.compose.ui.text.PlaceholderVerticalAlign
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.BaselineShift
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withLink
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.theta.kidshome.data.Dict
import com.theta.kidshome.data.DictText
import com.theta.kidshome.data.Moji
import com.theta.kidshome.data.Store
import kotlinx.coroutines.delay
import java.io.File
import java.util.Locale

/** 辞書の なかま（タブ） */
private enum class DictShelf(val emoji: String, val label: String, val codes: Set<String>) {
    KOKUGO("あ", "こくご", setOf("kokugo", "koujien", "furoku")),
    EIGO("A", "えいご", setOf("crcen", "eiwa", "waei")),
    HYAKKA("🌍", "ひゃっか", setOf("encarta")),
    KARADA("🩺", "からだ", setOf("promedica")),
}

private fun gradeLabel(g: Int) = if (g >= 7) "中学" else "小$g"

/**
 * じしょ・ひゃっかじてん：読み で ひく。こくご・えいご・ひゃっか。
 * 「もっと くわしく」で 広辞苑 や 研究社 も。漢字 は 学年 つき、リンク で とんで、図 も 出る。
 */
@Composable
fun DictScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val profile = Store.config.currentProfile
    val theme = themeById(profile.themeId)
    val grade = remember(profile) { Moji.gradeOf(profile) }
    LaunchedEffect(Unit) { Dict.open(context) }
    val maxLevel = Store.config.dictLevel
    var more by remember { mutableStateOf(false) }
    var shelf by remember { mutableStateOf(DictShelf.KOKUGO) }
    var query by remember { mutableStateOf("") }
    var hits by remember { mutableStateOf<List<Dict.Hit>>(emptyList()) }
    val history = remember { mutableStateListOf<Long>() }
    val currentId = history.lastOrNull()
    val current = remember(currentId, Dict.books) { currentId?.let { Dict.entry(it) } }
    BackHandler { if (history.isNotEmpty()) history.removeAt(history.lastIndex) else onBack() }

    val books = Dict.books.filter { it.level <= maxLevel && (it.level <= 1 || more) }
    val shelves = DictShelf.entries.filter { s -> books.any { it.code in s.codes } }
    if (shelves.isNotEmpty() && shelf !in shelves) shelf = shelves.first()
    val codes = books.filter { it.code in shelf.codes }.map { it.code }.toSet()

    LaunchedEffect(query, codes) {
        delay(180)
        hits = if (query.isBlank()) emptyList() else Dict.search(query, codes).ifEmpty { Dict.searchText(query, codes) }
    }

    // よみあげ
    var tts by remember { mutableStateOf<TextToSpeech?>(null) }
    DisposableEffect(Unit) {
        var t: TextToSpeech? = null
        t = TextToSpeech(context.applicationContext) { if (it == TextToSpeech.SUCCESS) tts = t }
        onDispose { t.stop(); t.shutdown() }
    }

    Column(Modifier.fillMaxSize().background(theme.bgBottom).windowInsetsPadding(WindowInsets.safeDrawing).padding(14.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            RetroIconButton("🏠", theme, onClick = onBack)
            DotText("じしょ・ひゃっかじてん", theme, 26, theme.text, modifier = Modifier.padding(start = 10.dp))
            Spacer(Modifier.width(12.dp))
            Row(Modifier.weight(1f).horizontalScroll(rememberScrollState())) {
                shelves.forEach { s ->
                    RetroButton("${s.emoji} ${s.label}", theme, if (s == shelf) theme.titleBars[0] else Color.White, fontSize = 17, modifier = Modifier.padding(end = 6.dp)) { shelf = s }
                }
                if (maxLevel >= 2) RetroButton(if (more) "🔎 もっと くわしく ✓" else "🔎 もっと くわしく", theme, if (more) theme.titleBars[1] else Color.White, fontSize = 15) { more = !more }
            }
        }
        Spacer(Modifier.height10())
        if (!Dict.ready) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                RetroWindow(theme, title = "おしらせ", titleColor = theme.titleBars[3 % theme.titleBars.size]) {
                    Column(Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("📚", fontSize = 64.sp)
                        DotText("おうちのひと に じしょ を\nいれて もらってね", theme, 20)
                    }
                }
            }
            return@Column
        }
        Row(Modifier.weight(1f)) {
            // さがす
            Column(Modifier.width(380.dp).fillMaxHeight()) {
                OutlinedTextField(
                    query, { query = it }, singleLine = true,
                    label = { Text(if (shelf == DictShelf.EIGO) "えいご か よみ を いれてね" else "よみ（ひらがな）か かんじ") },
                    textStyle = androidx.compose.ui.text.TextStyle(fontSize = 24.sp),
                    trailingIcon = { if (query.isNotEmpty()) Text("✖️", fontSize = 20.sp, modifier = Modifier.clickable { query = "" }.padding(8.dp)) },
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height10())
                if (query.isBlank()) {
                    val kanji = remember(grade, Dict.books) { Dict.kanjiOfDay(grade) }
                    if (kanji.isNotEmpty()) {
                        DotText("きょう の かんじ（${gradeLabel(grade)} まで）", theme, 18, theme.text)
                        LazyVerticalGrid(GridCells.Adaptive(84.dp), Modifier.weight(1f).padding(top = 6.dp)) {
                            items(kanji) { h ->
                                val k = Regex("【(.+?)】").find(h.heading)?.groupValues?.get(1) ?: h.heading
                                Column(
                                    Modifier.padding(4.dp).clip(RoundedCornerShape(14.dp)).background(Color.White)
                                        .border(2.dp, theme.outline, RoundedCornerShape(14.dp)).clickable { history += h.id }.padding(6.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                ) {
                                    Text(k, fontSize = 34.sp, color = theme.outline)
                                    Text(gradeLabel(h.grade ?: 0), fontSize = 12.sp, color = theme.outline.copy(alpha = 0.6f))
                                }
                            }
                        }
                    }
                } else {
                    LazyColumn(Modifier.weight(1f)) {
                        if (hits.isEmpty()) item { DotText("みつからないよ", theme, 18, theme.text, modifier = Modifier.padding(12.dp)) }
                        items(hits, key = { it.id }) { h ->
                            Row(
                                Modifier.fillMaxWidth().padding(vertical = 3.dp).clip(RoundedCornerShape(12.dp))
                                    .background(if (h.id == current?.id) theme.titleBars[0] else Color.White)
                                    .border(1.5.dp, theme.outline.copy(alpha = 0.5f), RoundedCornerShape(12.dp))
                                    .clickable { history.clear(); history += h.id }.padding(horizontal = 12.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(h.heading, fontSize = 19.sp, color = theme.outline, maxLines = 1, modifier = Modifier.weight(1f))
                                h.grade?.let { g -> Chip(gradeLabel(g), if (g <= grade) Color(0xFFB8F0A8) else Color(0xFFEEEEEE)) }
                                Text(" " + (Dict.books.firstOrNull { it.code == h.code }?.title?.take(6) ?: ""), fontSize = 11.sp, color = Color.Gray)
                            }
                        }
                    }
                }
            }
            Spacer(Modifier.width(14.dp))
            // よむ
            RetroWindow(theme, Modifier.weight(1f).fillMaxHeight(), title = current?.book?.title ?: "📖", titleColor = theme.titleBars[1 % theme.titleBars.size]) {
                if (current == null) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { DotText("しらべたい ことば を いれてね", theme, 20) }
                } else EntryView(theme, current, grade, tts, canBack = history.size > 1, onBack = { history.removeAt(history.lastIndex) }) { target ->
                    Dict.resolve(target, current)?.let { history += it.id }
                }
            }
        }
    }
}

private fun Modifier.height10() = this.padding(top = 10.dp)

@Composable
private fun Chip(text: String, color: Color) {
    Text(text, fontSize = 12.sp, modifier = Modifier.clip(RoundedCornerShape(50)).background(color).padding(horizontal = 8.dp, vertical = 2.dp))
}

@Composable
private fun EntryView(theme: KidTheme, e: Dict.Entry, grade: Int, tts: TextToSpeech?, canBack: Boolean, onBack: () -> Unit, onLink: (String) -> Unit) {
    val context = LocalContext.current
    val blocks = remember(e.id) { DictText.blocks(e.body) }
    Column(Modifier.fillMaxSize().padding(14.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (canBack) RetroIconButton("⬅️", theme, size = 46.dp, onClick = onBack)
            Text(e.heading, fontSize = 30.sp, fontWeight = FontWeight.Bold, color = theme.outline, modifier = Modifier.weight(1f).padding(horizontal = 8.dp))
            e.grade?.let { g -> Chip(if (g <= grade) "${gradeLabel(g)} で ならう" else "${gradeLabel(g)} で ならう（まだ）", if (g <= grade) Color(0xFFB8F0A8) else Color(0xFFEEEEEE)) }
            Spacer(Modifier.width(8.dp))
            if (tts != null) RetroIconButton("🔊", theme, size = 46.dp) {
                val english = e.book.code in setOf("crcen", "eiwa") && e.reading.firstOrNull()?.isLetter() == true && e.reading.first() < 'Ā'
                tts.stop()
                if (english) { tts.language = Locale.US; tts.speak(e.reading, TextToSpeech.QUEUE_ADD, null, "w") }
                tts.language = Locale.JAPAN
                tts.speak(DictText.plain(e.body, e.word).take(600), TextToSpeech.QUEUE_ADD, null, "b")
            }
        }
        LazyColumn(Modifier.weight(1f).padding(top = 8.dp)) {
            items(blocks.size) { i ->
                when (val b = blocks[i]) {
                    is DictText.Block.Media -> MediaBlock(e.book.vault, b.name)
                    is DictText.Block.Para -> Para(theme, b, e, onLink)
                }
            }
        }
    }
}

@Composable
private fun Para(theme: KidTheme, p: DictText.Block.Para, e: Dict.Entry, onLink: (String) -> Unit) {
    val context = LocalContext.current
    val gaiji = p.spans.filterIsInstance<DictText.Span.Gaiji>()
    val text = buildAnnotatedString {
        if (p.bullet) append("・")
        p.spans.forEach { s ->
            when (s) {
                is DictText.Span.Plain -> append(DictText.expand(s.text, e.word))
                is DictText.Span.Bold -> withStyle(SpanStyle(fontWeight = FontWeight.Bold, background = theme.titleBars[0].copy(alpha = 0.5f))) { append(s.text) }
                is DictText.Span.Small -> withStyle(SpanStyle(fontSize = 0.7.em, baselineShift = if (s.sup) BaselineShift.Superscript else BaselineShift.Subscript)) { append(s.text) }
                is DictText.Span.Link -> withLink(
                    LinkAnnotation.Clickable(s.target, TextLinkStyles(SpanStyle(color = Color(0xFF2F6FD6), textDecoration = TextDecoration.Underline))) { onLink(s.target) },
                ) { append(s.label) }
                is DictText.Span.Gaiji -> appendInlineContent(s.name, "〓")
                is DictText.Span.Media -> {}
            }
        }
    }
    val inline = gaiji.associate { g ->
        g.name to InlineTextContent(Placeholder(1.em, 1.em, PlaceholderVerticalAlign.TextCenter)) {
            val bmp = remember(g.name) { (Dict.media(context, e.book.vault, g.name)?.second as? ByteArray)?.let { BitmapFactory.decodeByteArray(it, 0, it.size)?.asImageBitmap() } }
            if (bmp != null) Image(bmp, null, Modifier.fillMaxSize()) else Text("〓")
        }
    }
    Text(
        text, inlineContent = inline, fontSize = 20.sp, lineHeight = 32.sp, color = theme.outline,
        modifier = Modifier.padding(start = (p.depth * 18).dp, bottom = 6.dp),
    )
}

@Composable
private fun MediaBlock(vault: String, name: String) {
    val context = LocalContext.current
    val m = remember(vault, name) { Dict.media(context, vault, name) }
    if (m == null) { Text("（$name が ありません）", fontSize = 13.sp, color = Color.Gray); return }
    val (mime, data) = m
    if (mime.startsWith("image/")) {
        val bmp = remember(vault, name) {
            when (data) {
                is ByteArray -> BitmapFactory.decodeByteArray(data, 0, data.size)
                is File -> BitmapFactory.decodeFile(data.path)
                else -> null
            }?.asImageBitmap()
        }
        if (bmp != null) Image(bmp, name, Modifier.padding(vertical = 8.dp).heightIn(max = 420.dp).background(Color.White))
    } else {
        // 音・どうが・PDF は ほかの アプリ で
        RetroButton(if (mime.startsWith("audio/")) "🔊 おと を きく" else if (mime.startsWith("video/")) "🎬 どうが を みる" else "📄 ひらく", themeById(Store.config.currentProfile.themeId), Color.White, fontSize = 16, modifier = Modifier.padding(vertical = 6.dp)) {
            runCatching {
                val dir = File(context.cacheDir, "export").apply { mkdirs() }
                val f = when (data) {
                    is File -> data.copyTo(File(dir, name), overwrite = true)
                    is ByteArray -> File(dir, name).apply { writeBytes(data) }
                    else -> return@runCatching
                }
                val uri = androidx.core.content.FileProvider.getUriForFile(context, "${context.packageName}.files", f)
                context.startActivity(Intent(Intent.ACTION_VIEW).setDataAndType(uri, mime).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK))
            }
        }
    }
}

// ---------------------------------------------------------------- おやメニュー

@Composable
fun DictSection(config: com.theta.kidshome.data.AppConfig) {
    val context = LocalContext.current
    LaunchedEffect(Unit) { Dict.open(context) }
    val pick = androidx.activity.compose.rememberLauncherForActivityResult(androidx.activity.result.contract.ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let { Dict.import(context, it) }
    }
    Section("📖 じしょ・ひゃっかじてん") {
        Text(
            "パソコンで 辞書の Vault を 1 つの ファイル（${Dict.FILE}）に まとめて、タブレットに 入れます。" +
                "まとめかた は 非公開リポジトリ の dict/README.md。中身は 家の 中だけ で つかってください。",
            fontSize = 14.sp,
        )
        val f = Dict.file(context)
        Text(
            if (Dict.ready) "✅ ${Dict.books.joinToString("・") { "${it.title}（${it.entries}）" }}　${f.length() shr 20}MB　${Dict.built ?: ""}"
            else "まだ ありません",
            fontSize = 14.sp, modifier = Modifier.padding(top = 6.dp),
        )
        Dict.importing?.let { Text(it, fontSize = 13.sp, color = Color.Gray) }
        FlowRow8 {
            androidx.compose.material3.Button(enabled = Dict.importing?.startsWith("コピー中") != true, onClick = { pick.launch(arrayOf("*/*")) }) { Text("ファイル を えらんで 入れる") }
            if (Dict.ready) androidx.compose.material3.TextButton(onClick = { Dict.remove(context) }) { Text("けす", color = Color(0xFFD03B5C)) }
        }
        Text("USB で コピー するなら：タブレットの「Android/data/${context.packageName}/files/dict/」に ${Dict.FILE}（と、あれば kidshome-dict-media フォルダ）を おく。", fontSize = 12.sp, color = Color.Gray)
        Text("子どもに 見せる 辞書", fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 8.dp))
        FlowRow8 {
            listOf(1 to "こども向け だけ（コンサイス・エンカルタ）", 2 to "「もっと くわしく」で 広辞苑・研究社 も", 3 to "おとな向け（付属資料・医学）も").forEach { (lv, label) ->
                androidx.compose.material3.FilterChip(config.dictLevel == lv, { Store.update { it.copy(dictLevel = lv) } }, label = { Text(label) })
            }
        }
    }
}
