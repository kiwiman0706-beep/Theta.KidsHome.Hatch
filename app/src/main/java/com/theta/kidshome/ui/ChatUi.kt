package com.theta.kidshome.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.theta.kidshome.data.Chat
import com.theta.kidshome.data.StampLibrary
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** ホーム の みだし の「💌」（きて いる 数 の しるし つき） */
@Composable
fun ChatChip(theme: KidTheme, onClick: () -> Unit) {
    if (!Chat.on()) return
    val n = Chat.unread
    val shape = RoundedCornerShape(50)
    Box {
        Box(
            Modifier.padding(horizontal = 4.dp).clip(shape).background(if (n > 0) Color(0xFFFFB3CF) else Color.White.copy(alpha = 0.85f))
                .border(2.5.dp, theme.outline, shape).clickable(onClick = onClick).padding(horizontal = 12.dp, vertical = 4.dp),
        ) { DotText("💌", theme, 18) }
        if (n > 0) Box(Modifier.align(Alignment.TopEnd).size(20.dp).clip(CircleShape).background(Color(0xFFD03B5C)), contentAlignment = Alignment.Center) {
            Text("$n", fontSize = 11.sp, color = Color.White, fontWeight = FontWeight.Bold)
        }
    }
}

/** とどいた とき に うえ に「💌 ○○ から」 */
@Composable
fun ChatToast() {
    val m = Chat.incoming
    var show by remember { mutableStateOf(false) }
    LaunchedEffect(m) { if (m != null) { show = true; delay(3500); show = false } }
    Box(Modifier.fillMaxSize().padding(top = 70.dp), contentAlignment = Alignment.TopCenter) {
        AnimatedVisibility(show && m != null, enter = fadeIn() + slideInVertically { -it }, exit = fadeOut()) {
            val mm = m ?: return@AnimatedVisibility
            val img = remember(mm.id) { Chat.image(mm)?.asImageBitmap() }
            Row(
                Modifier.clip(RoundedCornerShape(18.dp)).background(Color(0xFFFFE4EF)).border(3.dp, Color(0xFF4A3B2A), RoundedCornerShape(18.dp)).padding(10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("💌 ${mm.from} から　${mm.text}", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Color(0xFF4A3B2A))
                img?.let { Image(it, null, Modifier.size(56.dp).padding(start = 8.dp)) }
            }
        }
    }
}

/** かぞく の チャット：スタンプ と きまった ことば */
@Composable
fun ChatDialog(theme: KidTheme, onDismiss: () -> Unit) {
    val scope = rememberCoroutineScope()
    val v = Chat.version
    val msgs = remember(v) { Chat.messages() }
    val stamps = remember(StampLibrary.version) { StampLibrary.mine() }
    var phrase by remember { mutableStateOf<String?>(null) }
    var sending by remember { mutableStateOf(false) }
    val list = rememberLazyListState()
    val fmt = remember { SimpleDateFormat("M/d H:mm", Locale.JAPAN) }
    LaunchedEffect(Unit) { Chat.markRead(); Chat.poll() }
    LaunchedEffect(msgs.size) { if (msgs.isNotEmpty()) list.scrollToItem(msgs.size - 1); Chat.markRead() }
    fun send(stamp: String?) {
        if (sending || (stamp == null && phrase == null)) return
        sending = true
        scope.launch { Chat.send(phrase.orEmpty(), stamp); phrase = null; sending = false }
    }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        RetroWindow(theme, Modifier.width(820.dp).height(600.dp), title = "💌 かぞく の チャット", titleColor = theme.titleBars[3]) {
            Column(Modifier.fillMaxSize().padding(12.dp)) {
                LazyColumn(Modifier.weight(1f).fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Color(0xFFF4F7FB)).padding(8.dp), state = list) {
                    items(msgs, key = { it.id }) { m ->
                        val img = remember(m.id) { Chat.image(m)?.asImageBitmap() }
                        Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalArrangement = if (m.mine) Arrangement.End else Arrangement.Start) {
                            Column(horizontalAlignment = if (m.mine) Alignment.End else Alignment.Start) {
                                Text("${if (m.mine) "じぶん" else m.from}　${fmt.format(Date(m.time))}", fontSize = 11.sp, color = Color.Gray)
                                if (m.text.isNotBlank()) Box(
                                    Modifier.clip(RoundedCornerShape(14.dp)).background(if (m.mine) Color(0xFFB8F0A8) else Color.White)
                                        .border(2.dp, theme.outline.copy(alpha = 0.4f), RoundedCornerShape(14.dp)).padding(horizontal = 12.dp, vertical = 6.dp),
                                ) { Text(m.text, fontSize = 20.sp, color = theme.outline) }
                                img?.let { Image(it, null, Modifier.size(110.dp), contentScale = ContentScale.Fit) }
                            }
                        }
                    }
                }
                Chat.status?.let { Text(it, fontSize = 13.sp, color = Color(0xFFD03B5C)) }
                Row(Modifier.padding(top = 6.dp).horizontalScroll(rememberScrollState())) {
                    Chat.PHRASES.forEach { p ->
                        RetroButton(p, theme, if (phrase == p) theme.titleBars[1] else Color.White, fontSize = 15, modifier = Modifier.padding(end = 6.dp)) { phrase = if (phrase == p) null else p }
                    }
                }
                Row(Modifier.padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    if (stamps.isEmpty()) DotText("スタンプ は 🪙 の「こうかん」で ふやせるよ", theme, 13, modifier = Modifier.weight(1f))
                    else StampPicker(theme, null, Modifier.weight(1f), 60.dp) { send(it) }
                    RetroButton(if (sending) "…" else "📨 おくる", theme, theme.titleBars[0], fontSize = 17) { send(null) }
                }
                DotText("ことば を えらんで スタンプ を おす と いっしょ に おくれるよ", theme, 12, theme.outline.copy(alpha = 0.6f))
            }
        }
    }
}

// ---------------------------------------------------------------- おやモード

@Composable
fun ChatSection() {
    val context = LocalContext.current
    val v = Chat.version
    var code by remember(v) { mutableStateOf(Chat.secret(context)) }
    var server by remember(v) { mutableStateOf(Chat.server(context)) }
    var scan by remember { mutableStateOf(false) }
    var showQr by remember { mutableStateOf(false) }
    Section("💌 かぞく の チャット（ntfy）") {
        Text(
            "きょうだい の タブレット どうし で スタンプ と きまった ことば を おくりあえます。おなじ「かぞく の あいことば」を それぞれ の タブレット に 入れて ください。" +
                "中身 は あいことば で 暗号化 して ntfy に おくる ので、サーバー から は 読めません（とどいて から 3 時間 いない に ひらく と 受けとれます）。",
            fontSize = 13.sp,
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(code, { code = it.filter { c -> c.isLetterOrDigit() }.take(32) }, singleLine = true, label = { Text("かぞく の あいことば") }, modifier = Modifier.width(260.dp))
            Spacer(Modifier.width(8.dp))
            Button(onClick = { Chat.setSecret(context, code) }) { Text("きめる") }
            TextButton(onClick = { code = Chat.newSecret() }) { Text("🎲 あたらしく つくる") }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(server, { server = it }, singleLine = true, label = { Text("ntfy サーバー") }, modifier = Modifier.width(320.dp))
            Spacer(Modifier.width(8.dp))
            OutlinedButton(onClick = { Chat.setServer(context, server) }) { Text("ほぞん") }
        }
        Row {
            if (Chat.on(context)) OutlinedButton(onClick = { showQr = !showQr }) { Text(if (showQr) "QR を とじる" else "🔗 QR で ほか の タブレット へ") }
            Spacer(Modifier.width(8.dp))
            OutlinedButton(onClick = { scan = !scan }) { Text(if (scan) "やめる" else "📷 QR を よみこむ") }
        }
        if (showQr) Image(remember(v) { qrBitmap("KHCHAT1.${Chat.secret(context)}", 360).asImageBitmap() }, null, Modifier.size(200.dp))
        if (scan) QrScanBox(Modifier.size(240.dp, 180.dp).clip(RoundedCornerShape(12.dp))) { t ->
            if (t.startsWith("KHCHAT1.")) { Chat.setSecret(context, t.removePrefix("KHCHAT1.")); scan = false }
        }
        Text(if (Chat.on(context)) "つかえます" else "まだ です（あいことば を 8 もじ いじょう）", fontSize = 13.sp, fontWeight = FontWeight.Bold)
    }
}
