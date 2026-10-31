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
import kotlinx.coroutines.delay
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.Canvas
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
import com.theta.kidshome.data.GuidePack
import com.theta.kidshome.data.QrSetup
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

// しらせ を うける 関数 は、JS から よばれる メソッド と ちがう 名前 に する
// （おなじ 名前 だと メソッド が じぶん を よびつづけて、しらせ が とどかない）
/**
 * ガイド役：3D キャラ を パソコン で パラパラ漫画 に した 絵 を ならべて 見せる（WebView・3D は つかわない）。
 * ふだん は「いき・まばたき」を くりかえし、タップ で 手を ふる。服・かみ の 色 は マスク に 色 を かけて ぬる。
 */
@Composable
fun GuideSprite(profileId: String, modifier: Modifier = Modifier, onTap: () -> Unit = {}) {
    val context = LocalContext.current
    val sp = GuidePack.sprites ?: return
    val m = GuidePack.manifest ?: return
    val chosen = remember(profileId, GuidePack.outfitVersion) { GuidePack.outfit(context, profileId) }
    val variant = remember(sp, chosen) { GuidePack.variantFor(sp, m, chosen) }
    val tints = remember(m, chosen) { GuidePack.tintsFor(m, chosen) }
    var anim by remember { mutableStateOf("idle") }
    var frame by remember { mutableIntStateOf(0) }
    LaunchedEffect(anim, sp) {
        val a = sp.anims[anim] ?: return@LaunchedEffect
        frame = 0
        while (true) {
            delay((a.seconds * 1000 / a.frames).toLong())
            if (frame + 1 >= a.frames) { if (anim == "tap") { anim = "idle"; break } else frame = 0 } else frame++
        }
    }
    val f = frame.coerceAtMost((sp.anims[anim]?.frames ?: 1) - 1)
    val base = remember(variant, anim, f) { GuidePack.frame(context, "${variant}/${anim}_%02d.webp".format(f), mask = false) }
    val masks = remember(variant, anim, f, tints) { tints.mapNotNull { (k, c) -> GuidePack.frame(context, "${variant}/mask_${k}_${anim}_%02d.png".format(f), mask = true)?.let { it to c } } }
    Canvas(
        modifier.pointerInput(Unit) { detectTapGestures { anim = "tap"; onTap() } },
    ) {
        val b = base ?: return@Canvas
        // たて・よこ を くずさず、下 に そろえて まんなか に
        val k = minOf(size.width / b.width, size.height / b.height)
        val w = b.width * k; val h = b.height * k
        val off = androidx.compose.ui.unit.IntOffset(((size.width - w) / 2).toInt(), (size.height - h).toInt())
        val dst = androidx.compose.ui.unit.IntSize(w.toInt(), h.toInt())
        drawImage(b, dstOffset = off, dstSize = dst, filterQuality = androidx.compose.ui.graphics.FilterQuality.Medium)
        masks.forEach { (mask, color) ->
            drawImage(
                mask, dstOffset = off, dstSize = dst,
                colorFilter = androidx.compose.ui.graphics.ColorFilter.tint(color, androidx.compose.ui.graphics.BlendMode.SrcIn),
                blendMode = androidx.compose.ui.graphics.BlendMode.Multiply,
            )
        }
    }
}

/** きせかえ：左に 大きく ガイド、右に スロットごとの アイテム */
@Composable
fun Wardrobe(theme: KidTheme, profileId: String) {
    val context = LocalContext.current
    val m = GuidePack.manifest ?: return
    val chosen = remember(profileId, GuidePack.outfitVersion) { GuidePack.outfit(context, profileId).toSet() }
    Row(Modifier.fillMaxSize().padding(12.dp)) {
        GuideSprite(profileId, Modifier.width(300.dp).fillMaxHeight())
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
            "へや の マスコット を、3D キャラ の パラパラ漫画 に できます（3D は パソコン で 絵 に して おく ので どの 端末 でも 出ます）。" +
                "じぶん の モデル は アプリに 入れず、非公開リポジトリ の Release（${GuidePack.ASSET}）から " +
                "読み取り専用トークン で とってくる か、zip を じかに 読みこみます。トークン は このタブレット だけに 保存します。",
            fontSize = 14.sp,
        )
        val m = GuidePack.manifest
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 6.dp)) {
            Text("へや に ガイド役 を だす（オフ なら えもじ の マスコット）", modifier = Modifier.weight(1f))
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
            if (m != null && !GuidePack.builtin) TextButton(onClick = { GuidePack.remove(context) }) { Text("はずして デフォルトに もどす", color = Color(0xFFD03B5C)) }
        }
        if (m != null && GuidePack.sprites == null) Text("⚠️ この パック には パラパラ漫画（sprites/）が ありません。非公開リポジトリ で 作りなおしてください", fontSize = 13.sp, color = Color(0xFFD03B5C))
        Text("新しい版 は 1 日 1 かい たしかめます。パック の 作りかた は 非公開リポジトリ の README に あります。", fontSize = 12.sp, color = Color.Gray)
    }
}
