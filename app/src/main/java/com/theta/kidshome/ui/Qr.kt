package com.theta.kidshome.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color as AColor
import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanOptions
import com.theta.kidshome.data.Events
import com.theta.kidshome.data.Gemini
import com.theta.kidshome.data.QrSetup
import com.theta.kidshome.data.SheetSync
import com.theta.kidshome.data.Store
import androidx.activity.compose.rememberLauncherForActivityResult

/** QR コードを カメラで読む。呼ぶと読み取り画面が開き、読めたら onResult */
@Composable
fun rememberQrScanner(prompt: String = "QR コードを 枠の中に うつしてください", onResult: (String) -> Unit): () -> Unit {
    val launcher = rememberLauncherForActivityResult(ScanContract()) { r -> r.contents?.let(onResult) }
    return {
        launcher.launch(
            ScanOptions()
                .setDesiredBarcodeFormats(ScanOptions.QR_CODE)
                .setPrompt(prompt)
                .setBeepEnabled(false)
                .setOrientationLocked(false),
        )
    }
}

fun qrBitmap(text: String, size: Int = 512): Bitmap {
    val m = QRCodeWriter().encode(
        text, BarcodeFormat.QR_CODE, size, size,
        mapOf(EncodeHintType.MARGIN to 2, EncodeHintType.CHARACTER_SET to "UTF-8", EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.M),
    )
    val px = IntArray(m.width * m.height) { if (m[it % m.width, it / m.width]) AColor.BLACK else AColor.WHITE }
    return Bitmap.createBitmap(px, m.width, m.height, Bitmap.Config.ARGB_8888)
}

@Composable
fun QrImage(text: String, size: Dp = 260.dp) {
    val bmp = remember(text) { qrBitmap(text) }
    Image(bmp.asImageBitmap(), contentDescription = "QR コード", filterQuality = FilterQuality.None, modifier = Modifier.size(size))
}

/** QR を大きく見せるダイアログ */
@Composable
fun QrDialog(title: String, text: String, note: String? = null, showText: Boolean = true, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                QrImage(text, 300.dp)
                if (showText) Text(text, fontSize = 12.sp, fontFamily = FontFamily.Monospace, color = Color.Gray, modifier = Modifier.padding(top = 8.dp))
                note?.let { Text(it, fontSize = 13.sp, modifier = Modifier.padding(top = 8.dp)) }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("とじる") } },
    )
}

/** 読み取った設定を このタブレットに入れる。入れたものの説明（何もなければ null） */
fun applyQrSetup(context: Context, raw: String): String? {
    val s = QrSetup.parse(raw)
    if (s.isEmpty) {
        Toast.makeText(context, "設定の QR コードではありませんでした", Toast.LENGTH_LONG).show()
        return null
    }
    s.geminiKey?.let { Gemini.setApiKey(context, it) }
    s.geminiModel?.let { Gemini.setModel(context, it) }
    if (s.sheetUrl != null || s.calendarUrls.isNotEmpty()) {
        Store.update { c ->
            c.copy(
                sheetUrl = s.sheetUrl ?: c.sheetUrl,
                calendarUrls = (c.calendarUrls + s.calendarUrls).distinct(),
            )
        }
    }
    if (s.sheetUrl != null) SheetSync.sync(context, force = true)
    if (s.calendarUrls.isNotEmpty()) Events.refresh(context, force = true)
    val msg = "📷 ${s.summary()} を設定しました"
    Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
    return msg
}

// ---------------------------------------------------------------- おやメニュー

/** 別のタブレットに このホームを入れる（いつでも最新版が落ちてくる リンク） */
const val LATEST_APK_URL = "https://github.com/${com.theta.kidshome.update.SelfUpdate.OWNER}/${com.theta.kidshome.update.SelfUpdate.REPO}/releases/latest/download/KidsHome.apk"

@Composable
fun QrSection(config: com.theta.kidshome.data.AppConfig) {
    val context = androidx.compose.ui.platform.LocalContext.current
    var show by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf<Pair<String, String>?>(null) }
    var withKey by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }
    val scan = rememberQrScanner { applyQrSetup(context, it) }
    Section("📷 QR コード で せってい・ほかのタブレットへ") {
        Text(
            "スマホの画面やパソコンに出した QR コードを、このタブレットのカメラで読むと設定が入ります。" +
                "読めるのは：スプレッドシートのリンク・Gemini の API キー（AIza…）・カレンダーの iCal アドレス・ほかのキッズホームの「設定を渡す QR」。",
            fontSize = 14.sp,
        )
        FlowRow8 {
            androidx.compose.material3.Button(onClick = scan) { Text("📷 QR コードを読む") }
            androidx.compose.material3.OutlinedButton(onClick = {
                show = "📲 別のタブレットに入れる" to LATEST_APK_URL
            }) { Text("📲 アプリのダウンロード QR") }
            androidx.compose.material3.OutlinedButton(onClick = {
                val key = Gemini.apiKey(context).takeIf { withKey && QrSetup.isGeminiKey(it) }
                val setup = QrSetup.Setup(
                    sheetUrl = config.sheetUrl,
                    geminiKey = key,
                    geminiModel = key?.let { Gemini.model(context) },
                    calendarUrls = config.calendarUrls,
                )
                if (setup.isEmpty) Toast.makeText(context, "渡せる設定がまだありません", Toast.LENGTH_LONG).show()
                else show = "🔁 設定を渡す（${setup.summary()}）" to QrSetup.encode(setup)
            }) { Text("🔁 この設定を QR で渡す") }
        }
        androidx.compose.foundation.layout.Row(verticalAlignment = Alignment.CenterVertically) {
            Text("渡す QR に Gemini キーも入れる", fontSize = 14.sp, modifier = Modifier.weight(1f))
            androidx.compose.material3.Switch(checked = withKey, onCheckedChange = { withKey = it })
        }
        Text(
            "アプリのダウンロード QR は、新しいタブレットのカメラで読むと いつでも最新版の APK が落ちてきます" +
                "（「提供元不明のアプリ」の許可が要ります）。入れたあと おやメニュー →「📷 QR コードを読む」で、" +
                "このタブレットの「設定を渡す QR」を読めば、スプレッドシートやカレンダーが そのまま入ります。",
            fontSize = 12.sp, color = Color.Gray,
        )
    }
    show?.let { (title, text) ->
        val secret = text.contains("gemini=") || text.contains("ical=") || text.contains("/ical/")
        QrDialog(
            title, text,
            note = when {
                text == LATEST_APK_URL -> "新しいタブレットのカメラで読んで、ダウンロードした APK を開いてください。"
                secret -> "⚠️ ひみつの アドレス・キーが入っています。読むのは自分のタブレットだけに。"
                else -> "新しいタブレットの キッズホーム → おやメニュー →「📷 QR コードを読む」で読んでください。"
            },
            showText = !secret && text.length < 200,
        ) { show = null }
    }
}

// ---------------------------------------------------------------- いつもの ホーム

fun isDefaultHome(context: Context): Boolean {
    val rm = context.getSystemService(android.app.role.RoleManager::class.java)
    if (rm != null && rm.isRoleAvailable(android.app.role.RoleManager.ROLE_HOME)) return rm.isRoleHeld(android.app.role.RoleManager.ROLE_HOME)
    val r = context.packageManager.resolveActivity(
        android.content.Intent(android.content.Intent.ACTION_MAIN).addCategory(android.content.Intent.CATEGORY_HOME),
        android.content.pm.PackageManager.MATCH_DEFAULT_ONLY,
    )
    return r?.activityInfo?.packageName == context.packageName
}

/** おやメニューの いちばん上：キッズホームを「いつもの ホーム」に */
@Composable
fun DefaultHomeSection() {
    val context = androidx.compose.ui.platform.LocalContext.current
    var isDefault by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(isDefaultHome(context)) }
    fun openSettings() = runCatching {
        context.startActivity(android.content.Intent(android.provider.Settings.ACTION_HOME_SETTINGS).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK))
    }.onFailure {
        context.startActivity(android.content.Intent(android.provider.Settings.ACTION_SETTINGS).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK))
    }
    val ask = rememberLauncherForActivityResult(androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult()) {
        isDefault = isDefaultHome(context)
        // 前に「いいえ」を選んでいると ダイアログが出ないので、設定画面へ
        if (!isDefault) openSettings()
    }
    androidx.compose.runtime.LaunchedEffect(Unit) {
        // 設定画面から もどってきたとき
        while (true) { kotlinx.coroutines.delay(1500); isDefault = isDefaultHome(context) }
    }
    Section("🏠 ホーム") {
        val style = com.theta.kidshome.data.Store.config.homeStyle
        Text("ホームの かたち", fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
        FlowRow8 {
            listOf("room" to "🛏️ へや（部屋の中の ものを押す）", "tabs" to "🗂️ タブ（ボタンを ならべる）").forEach { (id, label) ->
                androidx.compose.material3.FilterChip(
                    selected = (style != "tabs") == (id == "room"),
                    onClick = { com.theta.kidshome.data.Store.update { it.copy(homeStyle = id) } },
                    label = { Text(label) },
                )
            }
        }
        Text("へや：つくえ＝もじ・タイピング・くるくる・わーぷろ、イーゼル＝おえかき、テレビ＝どうが、おもちゃばこ＝アプリ…。この子の ボタンに ない ものは 置きません。", fontSize = 12.sp, color = Color.Gray)
        androidx.compose.foundation.layout.Spacer(Modifier.padding(top = 10.dp))
        Text("いつもの ホーム", fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
        if (isDefault) {
            Text("✅ キッズホームが いつもの ホーム です（ホームボタンで ここに もどります）", fontSize = 15.sp)
            androidx.compose.material3.TextButton(onClick = { openSettings() }) { Text("ほかのホームに もどす（Android の設定）") }
        } else {
            Text("いまは ほかのホームアプリが「いつもの ホーム」です。ホームボタンで キッズホームに もどるように しましょう。", fontSize = 15.sp)
            FlowRow8 {
                androidx.compose.material3.Button(onClick = {
                    val rm = context.getSystemService(android.app.role.RoleManager::class.java)
                    if (rm != null && rm.isRoleAvailable(android.app.role.RoleManager.ROLE_HOME)) ask.launch(rm.createRequestRoleIntent(android.app.role.RoleManager.ROLE_HOME))
                    else openSettings()
                }) { Text("🏠 キッズホームを いつもの ホームにする") }
                androidx.compose.material3.OutlinedButton(onClick = { openSettings() }) { Text("Android の設定で えらぶ") }
            }
        }
    }
}
