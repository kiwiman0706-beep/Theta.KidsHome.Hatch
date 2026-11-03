package com.theta.kidshome.ui

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.FileProvider
import com.theta.kidshome.data.AppConfig
import com.theta.kidshome.data.Points
import com.theta.kidshome.data.Store
import kotlinx.coroutines.delay
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** ホーム の みだし に 出す「🪙 123」。タップ で つうちょう */
@Composable
fun PointsChip(theme: KidTheme, onClick: () -> Unit) {
    val v = Points.version
    val n = remember(v, Store.config.currentProfileId) { Points.balance() }
    val shape = RoundedCornerShape(50)
    Box(
        Modifier.padding(horizontal = 4.dp).clip(shape).background(Color(0xFFFFE9A8)).border(2.5.dp, theme.outline, shape)
            .clickable(onClick = onClick).padding(horizontal = 12.dp, vertical = 4.dp),
    ) { DotText("🪙 $n", theme, 18) }
}

/** もらった とき に みぎ うえ に「+1 🪙」 */
@Composable
fun PointsToast() {
    val g = Points.lastGain
    var show by remember { mutableStateOf(false) }
    LaunchedEffect(g) { if (g != null) { show = true; delay(1800); show = false } }
    Box(Modifier.fillMaxSize().padding(top = 70.dp, end = 24.dp), contentAlignment = Alignment.TopEnd) {
        AnimatedVisibility(show && g != null, enter = fadeIn() + slideInVertically { -it }, exit = fadeOut()) {
            val gg = g ?: return@AnimatedVisibility
            Box(
                Modifier.clip(RoundedCornerShape(18.dp)).background(if (gg.n > 0) Color(0xFFFFD84D) else Color(0xFFDDDDDD))
                    .border(3.dp, Color(0xFF4A3B2A), RoundedCornerShape(18.dp)).padding(horizontal = 16.dp, vertical = 8.dp),
            ) {
                Text(if (gg.n > 0) "🪙 +${gg.n}　${gg.why}" else gg.why, fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Color(0xFF4A3B2A))
            }
        }
    }
}

/** つうちょう：のこり・きょう・りれき、ごほうび しへい を よみこむ */
@Composable
fun PointsDialog(theme: KidTheme, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val v = Points.version
    val bal = remember(v) { Points.balance() }
    val hist = remember(v) { Points.history().take(40) }
    var scan by remember { mutableStateOf(false) }
    var bill by remember { mutableStateOf<Points.Bill?>(null) }
    var gate by remember { mutableStateOf(false) }
    var msg by remember { mutableStateOf<String?>(null) }
    val fmt = remember { SimpleDateFormat("M/d H:mm", Locale.JAPAN) }
    var tab by remember { mutableIntStateOf(0) }
    var buy by remember { mutableStateOf<com.theta.kidshome.data.StampItem?>(null) }

    fun onCode(code: String) {
        val b = Points.decodeBill(code)
        if (b == null) { msg = "これ は ごほうび しへい では ない みたい"; return }
        scan = false
        if (Points.billUsed(context, b)) { msg = "この しへい は もう つかったよ"; return }
        bill = b; msg = null
    }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        RetroWindow(theme, Modifier.width(720.dp).height(520.dp), title = "🪙 ポイント つうちょう", titleColor = theme.titleBars[0]) {
            Row(Modifier.fillMaxSize().padding(14.dp)) {
                Column(Modifier.weight(1f).fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
                    DotText("${Store.config.currentProfile.name} の ポイント", theme, 18)
                    DotText("🪙 $bal", theme, 54, theme.text)
                    val cap = Points.dailyCap()
                    DotText("きょう もらった：${Points.todayEarned()}" + if (cap > 0) " ／ $cap" else "", theme, 15, theme.outline.copy(alpha = 0.75f))
                    Spacer(Modifier.height(10.dp))
                    when {
                        bill != null -> {
                            val b = bill!!
                            DotText("🎉 ${b.value} ポイント の しへい！", theme, 22, theme.text)
                            DotText("おうちのひと に 暗証番号 を いれて もらってね", theme, 15)
                            RetroButton("🔑 おうちのひと", theme, theme.titleBars[1], fontSize = 18, modifier = Modifier.padding(top = 8.dp)) { gate = true }
                            TextButton(onClick = { bill = null }) { Text("やめる") }
                        }
                        scan -> {
                            FrontScanner(Modifier.size(260.dp, 190.dp).clip(RoundedCornerShape(16.dp)).border(4.dp, Color(0xFFFFB800), RoundedCornerShape(16.dp))) { onCode(it) }
                            DotText(if (cameraOnTop(context)) "↑ うえ の カメラ に しへい の QR を かざしてね" else "↓ した の カメラ に しへい の QR を かざしてね", theme, 14)
                            TextButton(onClick = { scan = false }) { Text("やめる") }
                        }
                        else -> RetroButton("📷 ごほうび しへい を よみこむ", theme, theme.titleBars[2], fontSize = 17) { scan = true; msg = null }
                    }
                    msg?.let { DotText(it, theme, 15, Color(0xFFD03B5C), modifier = Modifier.padding(top = 6.dp)) }
                    Spacer(Modifier.weight(1f))
                    DotText("字 を 1 つ かく・もんだい を 1 つ とく と 🪙 1", theme, 13, theme.outline.copy(alpha = 0.7f))
                    DotText("👗 きせかえ は 1 こ ${Points.DOLL_COST} ポイント", theme, 13, theme.outline.copy(alpha = 0.7f))
                }
                Column(Modifier.weight(1f).fillMaxSize().padding(start = 12.dp).clip(RoundedCornerShape(12.dp)).background(Color.White).padding(10.dp)) {
                    Row {
                        RetroButton("🎁 こうかん", theme, if (tab == 0) theme.titleBars[1] else Color.White, fontSize = 15, modifier = Modifier.padding(end = 6.dp)) { tab = 0 }
                        RetroButton("📒 りれき", theme, if (tab == 1) theme.titleBars[1] else Color.White, fontSize = 15) { tab = 1 }
                    }
                    if (tab == 0) {
                        val stamps = remember(v, com.theta.kidshome.data.StampLibrary.version) { com.theta.kidshome.data.StampLibrary.list().filter { it.reward } }
                        if (stamps.isEmpty()) DotText("まだ ごほうび スタンプ は ないよ", theme, 14, modifier = Modifier.padding(top = 10.dp))
                        androidx.compose.foundation.lazy.grid.LazyVerticalGrid(androidx.compose.foundation.lazy.grid.GridCells.Adaptive(96.dp), Modifier.fillMaxSize().padding(top = 6.dp)) {
                            items(stamps.size) { i ->
                                val st = stamps[i]
                                val own = com.theta.kidshome.data.StampLibrary.usable(st)
                                Column(Modifier.padding(3.dp).clip(RoundedCornerShape(10.dp)).background(Color(0xFFFFF6E0)).clickable { if (!own) buy = st }.padding(4.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                    StampThumb(theme, st.id, false, 70.dp) { if (!own) buy = st }
                                    if (own) {
                                        Text("🏠 うかべる", fontSize = 12.sp, modifier = Modifier.clickable { floatOnHome(st.id); msg = "ホーム に うかべたよ" })
                                    } else Text("🪙 ${st.price}", fontSize = 14.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    } else LazyColumn(Modifier.fillMaxSize()) {
                        items(hist) { e ->
                            Row(Modifier.fillMaxWidth().padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
                                Text(fmt.format(Date(e.time)), fontSize = 12.sp, color = Color.Gray, modifier = Modifier.width(76.dp))
                                Text(e.why, fontSize = 14.sp, modifier = Modifier.weight(1f), maxLines = 1)
                                Text(if (e.n > 0) "+${e.n}" else "${e.n}", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = if (e.n > 0) Color(0xFF2E9E6A) else Color(0xFFD03B5C))
                            }
                        }
                    }
                }
            }
        }
    }
    buy?.let { st ->
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { buy = null },
            title = { Text("🪙 ${st.price} ポイント で こうかん する？") },
            text = { Text(if (bal >= st.price) "「${st.name}」が つかえる よう に なるよ（いま ${bal}）" else "ポイント が たりないよ（いま ${bal}、あと ${st.price - bal}）") },
            confirmButton = { if (bal >= st.price) TextButton(onClick = { if (com.theta.kidshome.data.StampLibrary.buy(st)) msg = "🎉 「${st.name}」を ゲット！"; buy = null }) { Text("こうかん！") } },
            dismissButton = { TextButton(onClick = { buy = null }) { Text("やめる") } },
        )
    }
    if (gate) ParentGate(
        onSuccess = {
            gate = false
            val b = bill
            if (b != null) msg = if (Points.redeem(context, Store.config.currentProfileId, b)) null.also { speakOnce(context, "${b.value}ポイント もらったよ") } else "この しへい は もう つかったよ"
            bill = null
        },
        onDismiss = { gate = false },
        pinOnly = true, title = "🔑 ごほうび しへい",
    )
}

private fun speakOnce(context: Context, text: String) {
    var tts: android.speech.tts.TextToSpeech? = null
    tts = android.speech.tts.TextToSpeech(context.applicationContext) { if (it == 0) tts?.speak(text, android.speech.tts.TextToSpeech.QUEUE_FLUSH, null, "p") }
}

// ---------------------------------------------------------------- おやモード

/** ポイント の せってい・暗証番号・ごほうび しへい を つくる */
@Composable
fun PointsSection(config: AppConfig) {
    val context = LocalContext.current
    val v = Points.version
    var pin1 by remember { mutableStateOf("") }
    var pin2 by remember { mutableStateOf("") }
    var pinMsg by remember { mutableStateOf<String?>(null) }
    var value by remember { mutableIntStateOf(10) }
    var count by remember { mutableIntStateOf(10) }
    Section("🪙 ポイント・ごほうび しへい") {
        Text("字 を 1 つ かく・もんだい を 1 つ とく と 1 ポイント（おなじ もの は 1 日 1 かい）。きせかえ は 1 こ ${Points.DOLL_COST} ポイント。", fontSize = 13.sp)
        Text("1 日 に もらえる 上限", fontSize = 13.sp)
        Row {
            listOf(30, 50, 100, 0).forEach { n ->
                FilterChip(Points.dailyCap(context) == n, { Points.setDailyCap(context, n) }, label = { Text(if (n == 0) "なし" else "$n") }, modifier = Modifier.padding(end = 6.dp))
            }
        }
        config.profiles.forEach { pr ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("${pr.name}：🪙 ${remember(v) { Points.balance(context, pr.id) }}", modifier = Modifier.weight(1f), fontSize = 14.sp)
                listOf(1, 5, 10).forEach { n -> TextButton(onClick = { Points.give(context, pr.id, n, "おうちのひと から") }) { Text("+$n") } }
            }
        }

        Text("🔑 暗証番号（ごほうび しへい を つかう とき に いれる。4〜8 けた の 数字）", fontSize = 14.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 8.dp))
        Text(if (Points.hasPin(context)) "せってい ずみ" else "まだ です（ない あいだ は むずかしい かけ算 で かくにん）", fontSize = 13.sp)
        Row(verticalAlignment = Alignment.CenterVertically) {
            val ko = KeyboardOptions(keyboardType = KeyboardType.NumberPassword)
            OutlinedTextField(pin1, { pin1 = it.filter(Char::isDigit).take(8) }, label = { Text("あたらしい 番号") }, visualTransformation = PasswordVisualTransformation(), keyboardOptions = ko, singleLine = true, modifier = Modifier.width(170.dp))
            Spacer(Modifier.width(8.dp))
            OutlinedTextField(pin2, { pin2 = it.filter(Char::isDigit).take(8) }, label = { Text("もう いちど") }, visualTransformation = PasswordVisualTransformation(), keyboardOptions = ko, singleLine = true, modifier = Modifier.width(170.dp))
            Spacer(Modifier.width(8.dp))
            Button(onClick = {
                pinMsg = when {
                    pin1.length < 4 -> "4 けた いじょう に して ください"
                    pin1 != pin2 -> "2 つ が ちがいます"
                    else -> { Points.setPin(context, pin1); pin1 = ""; pin2 = ""; "きめました" }
                }
            }) { Text("きめる") }
            if (Points.hasPin(context)) TextButton(onClick = { Points.clearPin(context); pinMsg = "けしました" }) { Text("けす", color = Color(0xFFD03B5C)) }
        }
        pinMsg?.let { Text(it, fontSize = 13.sp) }
        if (Points.hasPin(context)) Row(verticalAlignment = Alignment.CenterVertically) {
            Text("おやモード の 入り口 も 暗証番号 に する（かけ算 の かわり）", modifier = Modifier.weight(1f), fontSize = 14.sp)
            Switch(Points.pinForGate(context), { Points.setPinForGate(context, it) })
        }

        Text("💴 ごほうび しへい（QR）を つくる", fontSize = 14.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 8.dp))
        Text("印刷 して おうちのひと が もって おく。子ども が「🪙」→「しへい を よみこむ」で カメラ に かざし、暗証番号 で もらえる。1 まい 1 かい（つかった ばんごう は おぼえる）。", fontSize = 13.sp)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Points.BILL_VALUES.forEach { n -> FilterChip(value == n, { value = n }, label = { Text("$n") }, modifier = Modifier.padding(end = 4.dp)) }
            Text("　まい：", fontSize = 13.sp)
            listOf(4, 10, 20).forEach { n -> FilterChip(count == n, { count = n }, label = { Text("$n") }, modifier = Modifier.padding(end = 4.dp)) }
        }
        Row {
            Button(onClick = { androidx.print.PrintHelper(context).apply { scaleMode = androidx.print.PrintHelper.SCALE_MODE_FIT }.printBitmap("ごほうび しへい", billSheet(value, count)) }) { Text("🖨 印刷") }
            Spacer(Modifier.width(8.dp))
            OutlinedButton(onClick = { shareBitmap(context, billSheet(value, count), "bills.png", "ごほうび しへい") }) { Text("📤 共有") }
        }
    }
}

internal fun shareBitmap(context: Context, bmp: Bitmap, name: String, title: String) {
    val dir = File(context.cacheDir, "export").apply { mkdirs() }
    val f = File(dir, name)
    f.outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
    val uri = FileProvider.getUriForFile(context, context.packageName + ".files", f)
    context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType("image/png").putExtra(Intent.EXTRA_STREAM, uri).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION), title))
}

private val BILL_COLORS = mapOf(1 to 0xFFB8E3FF, 5 to 0xFFB8F0A8, 10 to 0xFFFFE29A, 50 to 0xFFFFC2A8, 100 to 0xFFFFB3CF)

/** しへい 1 まい（よこ 1100 × たて 540） */
fun billBitmap(b: Points.Bill): Bitmap {
    val w = 1100; val h = 540
    val out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
    val cv = Canvas(out)
    val p = Paint(Paint.ANTI_ALIAS_FLAG)
    cv.drawColor(android.graphics.Color.WHITE)
    p.color = (BILL_COLORS[b.value] ?: 0xFFE0E0E0).toInt()
    cv.drawRoundRect(RectF(12f, 12f, w - 12f, h - 12f), 40f, 40f, p)
    p.style = Paint.Style.STROKE; p.strokeWidth = 8f; p.color = 0xFF4A3B2A.toInt()
    cv.drawRoundRect(RectF(12f, 12f, w - 12f, h - 12f), 40f, 40f, p)
    p.strokeWidth = 3f
    cv.drawRoundRect(RectF(36f, 36f, w - 36f, h - 36f), 28f, 28f, p)
    p.style = Paint.Style.FILL
    p.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
    p.textSize = 46f; cv.drawText("キッズホーム ぎんこう", 70f, 110f, p)
    p.textSize = 30f; cv.drawText("ごほうび しへい", 70f, 160f, p)
    p.textSize = 150f; cv.drawText("${b.value}", 70f, 340f, p)
    val vw = p.measureText("${b.value}")
    p.textSize = 54f; cv.drawText("ポイント", 90f + vw, 330f, p)
    p.textSize = 72f; cv.drawText("🪙", 70f, 450f, p)
    p.textSize = 24f; p.typeface = Typeface.MONOSPACE; cv.drawText("No. ${b.serial}", 170f, 440f, p)
    val qr = qrBitmap(Points.encodeBill(b), 400)
    cv.drawBitmap(qr, null, RectF(w - 460f, 70f, w - 60f, 470f), null)
    return out
}

/** A4（300dpi）に 2 れつ ならべる */
fun billSheet(value: Int, count: Int): Bitmap {
    val w = 2480; val h = 3508
    val pages = (count + 9) / 10
    val out = Bitmap.createBitmap(w, h * pages, Bitmap.Config.ARGB_8888)
    val cv = Canvas(out); cv.drawColor(android.graphics.Color.WHITE)
    repeat(count) { i ->
        val page = i / 10; val k = i % 10
        val x = 100f + (k % 2) * 1160f; val y = page * h + 120f + (k / 2) * 640f
        cv.drawBitmap(billBitmap(Points.Bill(value, Points.newSerial())), x, y, null)
    }
    return out
}
