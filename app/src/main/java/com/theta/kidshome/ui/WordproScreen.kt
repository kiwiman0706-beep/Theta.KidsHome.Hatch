package com.theta.kidshome.ui

import android.content.Context
import android.content.Intent
import android.print.PrintAttributes
import android.print.PrintManager
import android.speech.RecognizerIntent
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.theta.kidshome.data.Sticker
import com.theta.kidshome.data.Store
import kotlinx.coroutines.delay
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

/** わーぷろ の 1 まい */
data class Doc(
    val id: String = UUID.randomUUID().toString(),
    val text: String = "",
    val size: Int = 28,
    val color: Int = 0xFF333333.toInt(),
    /** むじ・せん・ます */
    val paper: Int = 1,
    val dot: Boolean = true,
    val updated: Long = System.currentTimeMillis(),
) {
    val title get() = text.lineSequence().firstOrNull { it.isNotBlank() }?.take(16) ?: "（まっしろ）"
}

/** わーぷろ の ほぞん（子ごと、30 まいまで） */
object Docs {
    private fun prefs(c: Context) = c.getSharedPreferences("docs", Context.MODE_PRIVATE)
    private fun key() = "docs:" + Store.config.currentProfileId

    fun list(c: Context): List<Doc> = runCatching {
        val a = JSONArray(prefs(c).getString(key(), "[]"))
        (0 until a.length()).map { a.getJSONObject(it) }.map {
            Doc(it.getString("id"), it.optString("text"), it.optInt("size", 28), it.optInt("color"), it.optInt("paper", 1), it.optBoolean("dot", true), it.optLong("updated"))
        }
    }.getOrDefault(emptyList())

    fun save(c: Context, d: Doc) {
        if (d.text.isBlank()) return
        val all = (listOf(d) + list(c).filter { it.id != d.id }).take(30)
        prefs(c).edit().putString(key(), JSONArray(all.map {
            JSONObject().put("id", it.id).put("text", it.text).put("size", it.size).put("color", it.color)
                .put("paper", it.paper).put("dot", it.dot).put("updated", it.updated)
        }).toString()).apply()
    }

    fun delete(c: Context, id: String) {
        val all = list(c).filter { it.id != id }
        prefs(c).edit().putString(key(), JSONArray(all.map { JSONObject().put("id", it.id).put("text", it.text).put("size", it.size).put("color", it.color).put("paper", it.paper).put("dot", it.dot).put("updated", it.updated) }).toString()).apply()
    }
}

private val inkColors = listOf(0xFF333333, 0xFFE5486E, 0xFFFF8A3D, 0xFF2E9E5B, 0xFF3F86D6, 0xFF8A5CD6).map { Color(it) }
private val stickerColors = listOf(0xFFFFF3A0, 0xFFFFD6E4, 0xFFD4F1FF, 0xFFDDF5D8).map { it.toInt() }
private val emojiStamps = listOf("😀", "🥰", "😢", "😡", "⭐", "💗", "🌸", "🐶", "🐱", "🍓", "🎂", "☀️", "🌈", "🎉", "👍", "✏️")

/**
 * わーぷろ：大きな文字で書いて、色・大きさ・紙をえらぶ。🎤 で こえ入力、📌 で ホームに ふせん、🖨️ で印刷。
 * 書いたものは自動でほぞん（その子ごと）。
 */
@Composable
fun WordproScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val theme = themeById(Store.config.currentProfile.themeId)
    var doc by remember { mutableStateOf(Doc()) }
    var showList by remember { mutableStateOf(false) }
    BackHandler { Docs.save(context, doc); onBack() }

    // 書くたびに すこし待ってから ほぞん
    LaunchedEffect(doc) {
        delay(800)
        Docs.save(context, doc)
    }
    val speech = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { r ->
        val said = r.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()
        if (!said.isNullOrBlank()) doc = doc.copy(text = doc.text + said, updated = System.currentTimeMillis())
    }
    fun edit(f: (Doc) -> Doc) { doc = f(doc).copy(updated = System.currentTimeMillis()) }

    Box(Modifier.fillMaxSize()) {
        RetroBackground(theme)
        Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing).padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                RetroIconButton("🏠", theme) { Docs.save(context, doc); onBack() }
                DotText("わーぷろ", theme, 28, theme.text, modifier = Modifier.padding(start = 10.dp))
                Spacer(Modifier.weight(1f))
                RetroIconButton("📄", theme) { Docs.save(context, doc); doc = Doc(size = doc.size, paper = doc.paper, dot = doc.dot) }
                RetroIconButton("📂", theme) { Docs.save(context, doc); showList = true }
                RetroIconButton("🎤", theme) {
                    val i = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
                        .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                        .putExtra(RecognizerIntent.EXTRA_LANGUAGE, "ja-JP")
                        .putExtra(RecognizerIntent.EXTRA_PROMPT, "おはなし してね")
                    runCatching { speech.launch(i) }.onFailure { Toast.makeText(context, "こえ入力が つかえないよ", Toast.LENGTH_SHORT).show() }
                }
                RetroIconButton("📌", theme) {
                    if (doc.text.isBlank()) Toast.makeText(context, "なにか かいてから はってね", Toast.LENGTH_SHORT).show()
                    else {
                        Store.addSticker(Sticker(text = doc.text.take(80), color = stickerColors[(doc.text.length) % stickerColors.size]))
                        Toast.makeText(context, "ホームに はったよ！", Toast.LENGTH_SHORT).show()
                    }
                }
                RetroIconButton("🖨️", theme) { printDoc(context, doc) }
            }
            Spacer(Modifier.height(8.dp))
            // 文字の大きさ・色・紙・字の形
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), verticalAlignment = Alignment.CenterVertically) {
                RetroIconButton("🔡", theme, size = 46.dp) { edit { it.copy(size = (it.size - 6).coerceAtLeast(16)) } }
                DotText("${doc.size}", theme, 16, theme.text)
                RetroIconButton("🔠", theme, size = 46.dp) { edit { it.copy(size = (it.size + 6).coerceAtMost(72)) } }
                Spacer(Modifier.width(10.dp))
                inkColors.forEach { c ->
                    Box(
                        Modifier.padding(4.dp).size(if (doc.color == c.toArgb()) 44.dp else 36.dp).clip(CircleShape).background(c)
                            .border(if (doc.color == c.toArgb()) 4.dp else 2.dp, theme.outline, CircleShape)
                            .clickable { edit { it.copy(color = c.toArgb()) } }
                    )
                }
                Spacer(Modifier.width(10.dp))
                listOf("⬜" to 0, "📃" to 1, "🔲" to 2).forEach { (e, p) -> RetroIconButton(e, theme, doc.paper == p, 46.dp) { edit { it.copy(paper = p) } } }
                RetroIconButton("👾", theme, doc.dot, 46.dp) { edit { it.copy(dot = !it.dot) } }
                Spacer(Modifier.width(10.dp))
                emojiStamps.forEach { e -> RetroIconButton(e, theme, size = 42.dp) { edit { it.copy(text = it.text + e) } } }
            }
            Spacer(Modifier.height(10.dp))
            RetroWindow(theme, Modifier.weight(1f).fillMaxWidth().padding(end = 6.dp, bottom = 6.dp), title = doc.title, titleColor = theme.titleBars[3]) {
                val lineH = (doc.size * 1.6f)
                val lineColor = theme.outline.copy(alpha = 0.18f)
                BasicTextField(
                    value = doc.text,
                    onValueChange = { v -> edit { it.copy(text = v) } },
                    textStyle = TextStyle(
                        fontSize = doc.size.sp, lineHeight = lineH.sp, color = Color(doc.color),
                        fontFamily = if (doc.dot) DotFont else FontFamily.Default,
                    ),
                    cursorBrush = SolidColor(theme.accent),
                    modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState())
                        .heightIn(min = 400.dp)
                        .drawBehind {
                            val step = lineH.sp.toPx()
                            val pad = 16.dp.toPx()
                            if (doc.paper == 1) {
                                var y = pad + step
                                while (y < size.height) { drawLine(lineColor, Offset(0f, y), Offset(size.width, y), 2f); y += step }
                            } else if (doc.paper == 2) {
                                var y = pad
                                while (y < size.height) { drawLine(lineColor, Offset(0f, y), Offset(size.width, y), 2f); y += step }
                                var x = pad
                                while (x < size.width) { drawLine(lineColor, Offset(x, 0f), Offset(x, size.height), 2f); x += step }
                            }
                        }
                        .padding(16.dp),
                )
            }
        }
    }

    if (showList) {
        val docs = remember { Docs.list(context) }
        Dialog(onDismissRequest = { showList = false }) {
            RetroWindow(theme, Modifier.width(460.dp).heightIn(max = 620.dp), title = "かいたもの", titleColor = theme.titleBars[1]) {
                Column(Modifier.padding(12.dp).verticalScroll(rememberScrollState())) {
                    if (docs.isEmpty()) DotText("まだ ないよ", theme, 18)
                    docs.forEach { d ->
                        Row(
                            Modifier.fillMaxWidth().padding(vertical = 3.dp).clip(RoundedCornerShape(10.dp))
                                .background(theme.bubbles[0]).clickable { doc = d; showList = false }.padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            DotText("📝 ${d.title}", theme, 17, modifier = Modifier.weight(1f))
                            DotText(SimpleDateFormat("M/d H:mm", Locale.JAPAN).format(Date(d.updated)), theme, 12, theme.outline.copy(alpha = 0.6f))
                            RetroIconButton("🗑️", theme, size = 36.dp) { Docs.delete(context, d.id); showList = false }
                        }
                    }
                }
            }
        }
    }
}

/** 印刷：文字を HTML にして Android の印刷へ */
private fun printDoc(context: Context, doc: Doc) {
    val pm = context.getSystemService(PrintManager::class.java) ?: return
    val esc = doc.text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\n", "<br>")
    val color = String.format("#%06X", 0xFFFFFF and doc.color)
    val html = """<html><head><meta charset="utf-8"><style>
        @font-face { font-family: dot; src: url('file:///android_res/font/dotgothic16.ttf'); }
        body { font-family: ${if (doc.dot) "dot," else ""} sans-serif; font-size: ${doc.size}px; line-height: 1.6; color: $color; margin: 24px; }
        </style></head><body>$esc</body></html>"""
    val w = WebView(context)
    w.webViewClient = object : WebViewClient() {
        override fun onPageFinished(view: WebView, url: String?) {
            pm.print("わーぷろ", view.createPrintDocumentAdapter("wordpro"), PrintAttributes.Builder().setMediaSize(PrintAttributes.MediaSize.ISO_A4).build())
        }
    }
    w.loadDataWithBaseURL("file:///android_res/", html, "text/html", "utf-8", null)
}
