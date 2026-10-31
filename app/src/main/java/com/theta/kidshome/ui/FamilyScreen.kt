package com.theta.kidshome.ui

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.theta.kidshome.remote.Family
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.json.JSONObject

/**
 * おやモード（おや の スマホ）：つないだ 子ども の タブレット に メッセージ・どうがロック・曲・せってい を おくる。
 * タブレット の おやメニュー「📣 呼びかけ」→「📱 おや の スマホ と つなぐ」の QR を 読んで つなぐ。
 */
@Composable
fun FamilyScreen(onExit: () -> Unit) {
    val context = LocalContext.current
    LaunchedEffect(Unit) { Family.loadDevices(context) }
    val scan = rememberQrScanner("タブレット の「おや の スマホ と つなぐ」QR を うつしてください") { v ->
        val d = Family.pair(context, v.trim())
        Toast.makeText(context, if (d != null) "📱 ${d.name} と つながりました" else "つなぐ QR では ありません", Toast.LENGTH_LONG).show()
    }
    Column(Modifier.fillMaxSize().background(Color(0xFFFFF5F8)).windowInsetsPadding(WindowInsets.safeDrawing).verticalScroll(rememberScrollState()).padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("📱 おやモード", fontSize = 26.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            TextButton(onClick = onExit) { Text("キッズホーム に もどす") }
        }
        Text("子ども の タブレット に、メッセージ・どうが の おやすみ・ピアノ の 曲・せってい を おくれます。", fontSize = 14.sp, color = Color.Gray)
        Spacer(Modifier.height(10.dp))
        if (Family.devices.isEmpty()) Text(
            "まだ つながって いません。タブレット の おやメニュー →「📣 おうちのひと から の 呼びかけ」→「📱 おや の スマホ と つなぐ」で 出る QR を 読んでください。",
            fontSize = 15.sp,
        )
        if (Family.devices.size >= 2) AllCard(Family.devices)
        Family.devices.forEach { d -> DeviceCard(d) }
        Spacer(Modifier.height(8.dp))
        Button(onClick = scan, modifier = Modifier.fillMaxWidth()) { Text("📷 タブレット の QR を 読んで つなぐ") }
    }
}

/** つないだ タブレット みんな に まとめて おくる */
@Composable
private fun AllCard(list: List<Family.Device>) {
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf<String?>(null) }
    var text by remember { mutableStateOf("") }
    var recording by remember { mutableStateOf(false) }
    var songNotes by remember { mutableStateOf("") }
    var songTitle by remember { mutableStateOf("") }

    fun send(o: JSONObject, done: String) {
        busy = "おくっています…"
        scope.launch {
            val failed = list.filter { d -> runCatching { Family.send(d, JSONObject(o.toString())) }.isFailure }
            busy = if (failed.isEmpty()) "✅ みんな に $done" else "✅ $done（${failed.joinToString("・") { it.name }} には おくれませんでした）"
        }
    }

    Card(colors = CardDefaults.cardColors(containerColor = Color(0xFFFFF0C8)), modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("👨‍👩‍👧‍👧 みんな に（${list.size} だい）", fontSize = 20.sp, fontWeight = FontWeight.Bold)
            busy?.let { Text(it, fontSize = 13.sp) }
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(text, { text = it }, singleLine = true, label = { Text("メッセージ") }, modifier = Modifier.weight(1f))
                Button(enabled = text.isNotBlank(), onClick = { send(JSONObject().put("c", "msg").put("text", text.trim()), "メッセージ を おくりました"); text = "" }, modifier = Modifier.padding(start = 6.dp)) { Text("💬") }
            }
            FlowRow8 {
                listOf(15, 30, 60, 120).forEach { m -> OutlinedButton(onClick = { send(JSONObject().put("c", "lock").put("min", m).put("text", text.trim()), "$m ふん おやすみ に しました") }) { Text("🔒 $m ふん") } }
                OutlinedButton(onClick = { send(JSONObject().put("c", "unlock"), "おやすみ を といた") }) { Text("🔓 といて いいよ") }
            }
            OutlinedTextField(songTitle, { songTitle = it }, singleLine = true, label = { Text("曲 の 名前") }, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(songNotes, { songNotes = it }, label = { Text("ドレミ（ド_レ_ミー ん ソ・ …）") }, modifier = Modifier.fillMaxWidth())
            FlowRow8 {
                OutlinedButton(onClick = { recording = true }) { Text("🎹 ひいて きろく") }
                Button(
                    enabled = songTitle.isNotBlank() && com.theta.kidshome.media.Songs.parse(songNotes).isNotEmpty(),
                    onClick = { send(JSONObject().put("c", "song").put("title", songTitle.trim()).put("notes", songNotes.trim()), "「${songTitle.trim()}」を おくりました"); songTitle = ""; songNotes = "" },
                ) { Text("🎵 みんな に おくる") }
            }
        }
    }
    if (recording) SongRecorder(onDismiss = { recording = false }) { t -> songNotes = t; recording = false }
}

@Composable
private fun DeviceCard(d: Family.Device) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var status by remember(d.topic) { mutableStateOf<JSONObject?>(null) }
    var busy by remember { mutableStateOf<String?>(null) }
    var text by remember { mutableStateOf("") }
    var recording by remember { mutableStateOf(false) }
    var songNotes by remember { mutableStateOf("") }
    var songTitle by remember { mutableStateOf("") }
    var naming by remember { mutableStateOf<String?>(null) }

    fun send(o: JSONObject, done: String) {
        busy = "おくっています…"
        scope.launch {
            val r = runCatching { Family.send(d, o) }
            busy = r.fold({ "✅ $done" }, { "おくれませんでした（${it.message}）" })
            if (r.isSuccess) { delay(4000); status = runCatching { Family.lastStatus(d) }.getOrNull() ?: status }
        }
    }
    LaunchedEffect(d.topic) { status = runCatching { Family.lastStatus(d) }.getOrNull() }

    Card(colors = CardDefaults.cardColors(containerColor = Color.White), modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("🧸 ${d.name}", fontSize = 20.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                TextButton(onClick = { naming = d.name }) { Text("✏️") }
                TextButton(onClick = { send(JSONObject().put("c", "status"), "ようす を ききました") }) { Text("🔄 ようす") }
            }
            naming?.let { n ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(n, { naming = it }, singleLine = true, label = { Text("よびな（〇〇 の タブレット）") }, modifier = Modifier.weight(1f))
                    Button(onClick = { Family.rename(context, d, n); naming = null }, modifier = Modifier.padding(start = 6.dp)) { Text("OK") }
                }
            }
            val s = status
            Text(
                if (s == null) "ようす：まだ わかりません（🔄 で きく）"
                else buildString {
                    append("🔋 ${s.optInt("battery")}%${if (s.optBoolean("charging")) "⚡" else ""}　👧 ${s.optString("profile")}")
                    val lock = s.optLong("lockedUntil")
                    if (lock > System.currentTimeMillis()) append("　🔒 どうが ${((lock - System.currentTimeMillis()) / 60000) + 1} ふん")
                    append("　v${s.optString("version")}　（${java.text.SimpleDateFormat("H:mm", java.util.Locale.JAPAN).format(java.util.Date(s.optLong("ts")))}）")
                },
                fontSize = 14.sp, color = Color.Gray,
            )
            busy?.let { Text(it, fontSize = 13.sp) }

            // メッセージ
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(text, { text = it }, singleLine = true, label = { Text("メッセージ（そろそろ ごはん だよ）") }, modifier = Modifier.weight(1f))
                Button(enabled = text.isNotBlank(), onClick = { send(JSONObject().put("c", "msg").put("text", text.trim()), "メッセージ を おくりました"); text = "" }, modifier = Modifier.padding(start = 6.dp)) { Text("💬") }
            }
            // どうが
            Text("どうが を おやすみ", fontWeight = FontWeight.Bold)
            FlowRow8 {
                listOf(15, 30, 60, 120).forEach { m -> OutlinedButton(onClick = { send(JSONObject().put("c", "lock").put("min", m).put("text", text.trim()), "$m ふん おやすみ に しました") }) { Text("🔒 $m ふん") } }
                OutlinedButton(onClick = { send(JSONObject().put("c", "unlock"), "おやすみ を といた") }) { Text("🔓 といて いいよ") }
            }
            // 曲
            Text("ピアノ の 曲 を おくる", fontWeight = FontWeight.Bold)
            OutlinedTextField(songTitle, { songTitle = it }, singleLine = true, label = { Text("曲 の 名前") }, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(songNotes, { songNotes = it }, label = { Text("ドレミ（ド_レ_ミー ん ソ・ …）") }, modifier = Modifier.fillMaxWidth())
            FlowRow8 {
                OutlinedButton(onClick = { recording = true }) { Text("🎹 ひいて きろく") }
                Button(
                    enabled = songTitle.isNotBlank() && com.theta.kidshome.media.Songs.parse(songNotes).isNotEmpty(),
                    onClick = { send(JSONObject().put("c", "song").put("title", songTitle.trim()).put("notes", songNotes.trim()), "「${songTitle.trim()}」を おくりました"); songTitle = ""; songNotes = "" },
                ) { Text("🎵 おくる") }
            }
            s?.optJSONArray("songs")?.let { a -> if (a.length() > 0) Text("タブレット の 曲：" + (0 until a.length()).joinToString("・") { a.getString(it) }, fontSize = 12.sp, color = Color.Gray) }
            // せってい
            Text("せってい", fontWeight = FontWeight.Bold)
            @Composable fun toggle(label: String, key: String, cur: Boolean) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(label, modifier = Modifier.weight(1f), fontSize = 14.sp)
                    Switch(cur, { v -> send(JSONObject().put("c", "set").put(key, v), "$label を ${if (v) "オン" else "オフ"} に しました") })
                }
            }
            toggle("ガイド役 を だす", "guide", s?.optBoolean("guide", true) ?: true)
            toggle("よる は へや を くらく", "nightDim", s?.optBoolean("nightDim") ?: false)
            toggle("じどう アップデート", "autoUpdate", s?.optBoolean("autoUpdate", true) ?: true)
            FlowRow8 {
                listOf(3 to "じしょ ぜんぶ", 2 to "医学 かくす", 1 to "こども向け だけ").forEach { (lv, l) ->
                    FilterChip(s?.optInt("dictLevel", 3) == lv, { send(JSONObject().put("c", "set").put("dictLevel", lv), l) }, label = { Text(l) })
                }
            }
            TextButton(onClick = { Family.forget(context, d) }) { Text("この タブレット と の つながり を けす", color = Color(0xFFD03B5C)) }
        }
    }
    if (recording) SongRecorder(onDismiss = { recording = false }) { t -> songNotes = t; recording = false }
}
