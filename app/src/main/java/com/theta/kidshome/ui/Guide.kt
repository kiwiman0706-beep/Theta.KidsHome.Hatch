package com.theta.kidshome.ui

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.BitmapFactory
import android.os.Handler
import android.os.Looper
import android.webkit.JavascriptInterface
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.webkit.WebViewAssetLoader
import com.theta.kidshome.data.GuidePack
import com.theta.kidshome.data.QrSetup
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

// しらせ を うける 関数 は、JS から よばれる メソッド と ちがう 名前 に する
// （おなじ 名前 だと メソッド が じぶん を よびつづけて、しらせ が とどかない）
private class GuideBridge(private val tapped: () -> Unit, private val readied: (String) -> Unit, private val failed: (String) -> Unit) {
    private val main = Handler(Looper.getMainLooper())
    @JavascriptInterface fun onTap() { main.post { tapped() } }
    @JavascriptInterface fun onReady(json: String) { main.post { readied(json) } }
    @JavascriptInterface fun onError(msg: String) { main.post { failed(msg) } }
    @JavascriptInterface fun onStage(stage: String) { main.post { GuidePack.stage(stage) } }
    @JavascriptInterface fun onInfo(info: String) { main.post { GuidePack.viewInfo = info } }
}

/**
 * ガイド役（VRM）を 3D で うごかす。WebView の 中で three.js と three-vrm（アプリに 同梱）。
 * パック は filesDir/packs/guide/ から よむ（ネット は つかわない）。
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun GuideView(
    profileId: String,
    modifier: Modifier = Modifier,
    view: String = "full",
    onTap: () -> Unit = {},
    onReady: (List<String>) -> Unit = {},
    /** うしろ の 絵（PNG）。あれば WebView を すけさせず、この 絵 を 背景 に して 3D を かく */
    background: ByteArray? = null,
    /** 絵 が ない とき の 背景 の 色 */
    backgroundColor: Color = Color(0xFFFFFDFE),
) {
    val context = LocalContext.current
    val m = GuidePack.manifest ?: return
    val parts = remember(profileId, GuidePack.outfitVersion) { GuidePack.outfit(context, profileId) }
    val transparent = GuidePack.transparent
    val simpleMode = GuidePack.simple
    val web = remember(m, view, transparent, simpleMode, background?.contentHashCode()) {
        val loader = WebViewAssetLoader.Builder()
            .addPathHandler("/assets/", WebViewAssetLoader.AssetsPathHandler(context))
            .addPathHandler("/mem/") { _ -> background?.let { WebResourceResponse("image/png", null, java.io.ByteArrayInputStream(it)) } }
            .addPathHandler("/pack/", WebViewAssetLoader.InternalStoragePathHandler(context, File(context.filesDir, "packs")))
            .build()
        WebView(context).apply {
            // すけさせる と 出ない 端末 が ある ので、ふつう は 背景 を ぬった ページ に する
            setBackgroundColor(if (transparent) android.graphics.Color.TRANSPARENT else backgroundColor.toArgb())
            settings.javaScriptEnabled = true
            settings.allowFileAccess = false
            settings.allowContentAccess = false
            GuidePack.viewReady = false
            GuidePack.viewStatus = "よみこみ中…"
            webViewClient = object : WebViewClient() {
                override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse? =
                    loader.shouldInterceptRequest(request.url)
                        // アプリの中に ない もの（favicon など）は ネット に いかず「ない」と こたえる
                        ?: if (request.url.host == "appassets.androidplatform.net") WebResourceResponse("text/plain", "utf-8", 404, "Not Found", emptyMap(), java.io.ByteArrayInputStream(ByteArray(0))) else null
                override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest) = true
                override fun onReceivedError(view: WebView, request: WebResourceRequest, error: android.webkit.WebResourceError) {
                    if (!request.url.path.orEmpty().endsWith("favicon.ico")) GuidePack.reportError("よめない：${request.url.path}（${error.description}）")
                }
                override fun onReceivedHttpError(view: WebView, request: WebResourceRequest, response: WebResourceResponse) {
                    if (!request.url.path.orEmpty().endsWith("favicon.ico")) GuidePack.reportError("ない：${request.url.path}（${response.statusCode}）")
                }
            }
            // ページの エラー を おやメニュー に だす（なおすときの 手がかり）
            webChromeClient = object : android.webkit.WebChromeClient() {
                override fun onConsoleMessage(m: android.webkit.ConsoleMessage): Boolean {
                    if (m.messageLevel() == android.webkit.ConsoleMessage.MessageLevel.ERROR) {
                        GuidePack.reportError("${m.message()}（${m.sourceId().substringAfterLast('/')}:${m.lineNumber()}）")
                    }
                    return true
                }
            }
            addJavascriptInterface(GuideBridge({ onTap() }, { j ->
                GuidePack.viewReady = true
                GuidePack.viewStatus = null
                onReady(runCatching { JSONObject(j).getJSONArray("parts").let { a -> (0 until a.length()).map { a.getString(it) } } }.getOrDefault(emptyList()))
            }, { e -> GuidePack.reportError(e) }), "KidsHomeGuide")
            val p = android.net.Uri.encode(JSONArray(parts).toString())
            val bg = when {
                transparent -> ""
                background != null -> "&bgimg=" + android.net.Uri.encode("/mem/room.png?" + background.contentHashCode())
                else -> "&bg=" + "%06x".format(0xFFFFFF and backgroundColor.toArgb())
            }
            loadUrl("https://appassets.androidplatform.net/assets/guide/guide.html?pack=${GuidePack.packPath}&view=$view&parts=$p$bg${if (simpleMode) "&simple=1" else ""}")
        }
    }
    LaunchedEffect(parts) { web.evaluateJavascript("window.KH && KH.setParts(${JSONArray(parts)})", null) }
    DisposableEffect(web) { web.onResume(); onDispose { web.onPause(); web.destroy() } }
    AndroidView({ web }, modifier)
}

/** きせかえ：左に 大きく ガイド、右に スロットごとの アイテム */
@Composable
fun Wardrobe(theme: KidTheme, profileId: String) {
    val context = LocalContext.current
    val m = GuidePack.manifest ?: return
    val chosen = remember(profileId, GuidePack.outfitVersion) { GuidePack.outfit(context, profileId).toSet() }
    Row(Modifier.fillMaxSize().padding(12.dp)) {
        GuideView(profileId, Modifier.width(320.dp).fillMaxHeight())
        Column(Modifier.weight(1f).fillMaxHeight().verticalScroll(rememberScrollState()).padding(start = 12.dp)) {
            if (m.slots.isEmpty()) DotText("きせかえ は まだ ないよ", theme, 20)
            m.slots.forEach { slot ->
                DotText(slot.name, theme, 20, modifier = Modifier.padding(top = 8.dp, bottom = 6.dp))
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    val cur = slot.items.firstOrNull { it.id in chosen } ?: if (slot.optional) null else slot.items.firstOrNull()
                    if (slot.optional) ItemCard(theme, "🚫", "なし", null, cur == null) { GuidePack.choose(context, profileId, slot, null) }
                    slot.items.forEach { item ->
                        ItemCard(theme, item.emoji, item.name, item.thumbnail, cur?.id == item.id) { GuidePack.choose(context, profileId, slot, item) }
                    }
                }
            }
        }
    }
}

@Composable
private fun ItemCard(theme: KidTheme, emoji: String, name: String, thumb: String?, on: Boolean, onClick: () -> Unit) {
    val context = LocalContext.current
    val bmp = remember(thumb) { thumb?.let { t -> GuidePack.open(context, t)?.use { BitmapFactory.decodeStream(it) }?.asImageBitmap() } }
    Column(
        Modifier.width(120.dp).clip(RoundedCornerShape(16.dp)).background(if (on) theme.titleBars[0] else Color.White)
            .border(if (on) 4.dp else 2.dp, theme.outline, RoundedCornerShape(16.dp)).clickable(onClick = onClick).padding(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        if (bmp != null) Image(bmp, null, Modifier.size(90.dp)) else Text(emoji, fontSize = 52.sp)
        DotText(name, theme, 15)
    }
}

// ---------------------------------------------------------------- おやメニュー

@Composable
fun GuideSection() {
    val context = LocalContext.current
    var repo by remember { mutableStateOf(GuidePack.repo(context)) }
    var token by remember { mutableStateOf(GuidePack.token(context)) }
    var parts by remember { mutableStateOf<List<String>?>(null) }
    val pick = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> uri?.let { GuidePack.importZip(context, it) } }
    val scan = rememberQrScanner("パックの QR コードを うつしてください") { v ->
        val s = QrSetup.parse(v)
        if (s.packRepo != null || s.packToken != null) {
            GuidePack.setSource(context, s.packRepo, s.packToken)
            repo = GuidePack.repo(context); token = GuidePack.token(context)
            GuidePack.update(context, force = true)
        } else applyQrSetup(context, v)
    }
    Section("🧸 ガイド役（3D キャラ・きせかえ）") {
        Text(
            "へや の マスコット を、VRM の 3D キャラ に できます。モデル は アプリに 入れず、非公開リポジトリ の Release（${GuidePack.ASSET}）から " +
                "読み取り専用トークン で とってくる か、zip を じかに 読みこみます。トークン は このタブレット だけに 保存します。",
            fontSize = 14.sp,
        )
        val m = GuidePack.manifest
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 6.dp)) {
            Text("へや に 3D の ガイド役 を だす（ためし中。画面 が おかしく なったら オフ に）", modifier = Modifier.weight(1f))
            androidx.compose.material3.Switch(GuidePack.enabled, { GuidePack.setEnabled(context, it) })
        }
        Text(
            when {
                m == null -> "まだ ありません"
                GuidePack.builtin -> "いまは アプリに 入っている ガイドちゃん（きせかえ ${m.slots.sumOf { it.items.size }} こ）"
                else -> "✅ ${m.name}（${m.version}・${GuidePack.installedTag(context)}）きせかえ ${m.slots.sumOf { it.items.size }} こ"
            },
            fontSize = 15.sp,
        )
        GuidePack.status?.let { Text(it, fontSize = 13.sp, color = Color.Gray) }
        OutlinedTextField(repo, { repo = it }, singleLine = true, label = { Text("リポジトリ（例：kiwiman0706-beep/Theta.Kidshome.Hatch.Closed）") }, modifier = Modifier.fillMaxWidth().padding(top = 6.dp))
        OutlinedTextField(
            token, { token = it }, singleLine = true, label = { Text("読み取り専用トークン（github_pat_…）") },
            visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation(), modifier = Modifier.fillMaxWidth(),
        )
        FlowRow8 {
            Button(enabled = !GuidePack.busy, onClick = {
                GuidePack.setSource(context, repo, token)
                GuidePack.update(context, force = true)
            }) { Text(if (GuidePack.busy) "とってきています…" else "保存して いま とってくる") }
            OutlinedButton(onClick = scan) { Text("📷 QR で 読む") }
            OutlinedButton(enabled = !GuidePack.busy, onClick = { pick.launch(arrayOf("application/zip", "application/octet-stream")) }) { Text("zip を 読みこむ") }
            if (m != null) OutlinedButton(onClick = { parts = emptyList() }) { Text("パーツ名を 見る") }
            if (m != null && !GuidePack.builtin) TextButton(onClick = { GuidePack.remove(context) }) { Text("はずして デフォルトに もどす", color = Color(0xFFD03B5C)) }
        }
        GuidePack.viewStatus?.let { Text("3D の ようす：$it", fontSize = 13.sp, color = Color(0xFFD03B5C)) }
        if (GuidePack.viewReady) Text("3D の ようす：うごいています（${GuidePack.viewInfo ?: "…"}）", fontSize = 13.sp, color = Color.Gray)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("かんたん な ぬりかた（キャラ が うつらない とき。自動 でも きりかわります）", fontSize = 14.sp, modifier = Modifier.weight(1f))
            androidx.compose.material3.Switch(GuidePack.simple, { GuidePack.setSimple(context, it) })
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("3D を すけさせて かさねる（ためし。端末 に よっては 見えなくなる）", fontSize = 14.sp, modifier = Modifier.weight(1f))
            androidx.compose.material3.Switch(GuidePack.transparent, { GuidePack.setTransparent(context, it) })
        }
        Text("新しい版 は 1 日 1 かい たしかめます。パック の 作りかた は 非公開リポジトリ の README に あります。", fontSize = 12.sp, color = Color.Gray)
    }
    parts?.let { list ->
        AlertDialog(
            onDismissRequest = { parts = null },
            title = { Text("パーツ名（manifest の show / hide に かく）") },
            text = {
                Row(Modifier.heightIn(max = 420.dp)) {
                    GuideView("parts-check", Modifier.width(200.dp).fillMaxHeight(), onReady = { parts = it })
                    Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(start = 12.dp)) {
                        if (list.isEmpty()) Text("よみこみ中…")
                        list.forEach { Text(it, fontFamily = FontFamily.Monospace, fontSize = 14.sp) }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { parts = null }) { Text("とじる") } },
        )
    }
}
