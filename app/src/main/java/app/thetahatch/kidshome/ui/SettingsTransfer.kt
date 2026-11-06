package app.thetahatch.kidshome.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.thetahatch.kidshome.data.SettingsBundle
import kotlinx.coroutines.delay
import org.json.JSONObject

/** せってい を まとめて：ファイル・QR（なんまい か）で ほか の タブレット へ */
@Composable
fun SettingsTransferSection() {
    val context = LocalContext.current
    var withGemini by remember { mutableStateOf(false) }
    var msg by remember { mutableStateOf<String?>(null) }
    var parts by remember { mutableStateOf<List<String>>(emptyList()) }
    var page by remember { mutableIntStateOf(0) }
    var auto by remember { mutableStateOf(true) }
    var scanning by remember { mutableStateOf(false) }
    val collector = remember { SettingsBundle.Collector() }
    var progress by remember { mutableStateOf("") }
    var pending by remember { mutableStateOf<JSONObject?>(null) }

    val save = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) msg = runCatching {
            context.contentResolver.openOutputStream(uri)?.use { it.write(SettingsBundle.export(context, withGemini).toString(2).toByteArray()) }
            "書きだしました"
        }.getOrElse { "書きだせません でした（${it.message}）" }
    }
    val open = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) runCatching { JSONObject(context.contentResolver.openInputStream(uri)!!.bufferedReader().readText()) }
            .onSuccess { pending = it }.onFailure { msg = "読めません でした（${it.message}）" }
    }
    LaunchedEffect(parts, auto) {
        while (auto && parts.size > 1) { delay(1500); page = (page + 1) % parts.size }
    }

    Section("📦 せってい を まとめて 書きだす・うつす") {
        Text(
            "こども・タイル・カテゴリー・せいげん・ポイント の きまり・暗証番号・チャット の あいことば・テプラ など を まとめて うつせます。" +
                "ポイント の 残高・りれき、GitHub トークン は 入りません。Gemini キー は スイッチ を いれた とき だけ。",
            fontSize = 13.sp,
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Gemini キー も 入れる", fontSize = 14.sp, modifier = Modifier.padding(end = 8.dp))
            Switch(withGemini, { withGemini = it })
        }
        FlowRow8 {
            Button(onClick = { save.launch("kidshome-settings.json") }) { Text("💾 ファイル に 書きだす") }
            OutlinedButton(onClick = { open.launch(arrayOf("application/json", "text/plain", "application/octet-stream")) }) { Text("📂 ファイル から 読みこむ") }
            Button(onClick = { parts = SettingsBundle.qrParts(SettingsBundle.export(context, withGemini)); page = 0; scanning = false }) { Text("🔁 QR で ほか の タブレット へ") }
            OutlinedButton(onClick = { scanning = !scanning; parts = emptyList(); progress = "" }) { Text(if (scanning) "やめる" else "📷 QR を 読みこむ") }
        }
        if (parts.isNotEmpty()) {
            Text("うけとる タブレット で「📷 QR を 読みこむ」→ うしろ の カメラ で この 画面 を うつす（${parts.size} まい を じゅんばん に 出します）", fontSize = 13.sp)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Image(remember(page, parts) { qrBitmap(parts[page], 600).asImageBitmap() }, null, Modifier.size(300.dp))
                Column(Modifier.padding(start = 12.dp)) {
                    Text("${page + 1} / ${parts.size}", fontSize = 22.sp, fontWeight = FontWeight.Bold)
                    Row {
                        TextButton(onClick = { auto = false; page = (page - 1 + parts.size) % parts.size }) { Text("◀") }
                        TextButton(onClick = { auto = !auto }) { Text(if (auto) "⏸" else "▶ じどう") }
                        TextButton(onClick = { auto = false; page = (page + 1) % parts.size }) { Text("▶") }
                    }
                    TextButton(onClick = { parts = emptyList() }) { Text("とじる") }
                }
            }
        }
        if (scanning) Row(verticalAlignment = Alignment.CenterVertically) {
            QrScanBox(Modifier.size(260.dp, 195.dp).clip(RoundedCornerShape(12.dp))) { t ->
                if (collector.add(t)) {
                    progress = "${collector.count} / ${collector.total} まい"
                    if (collector.done) {
                        runCatching { collector.result() }.onSuccess { pending = it; scanning = false }.onFailure { progress = "読めません でした。もう いちど" }
                    }
                }
            }
            Spacer(Modifier.width(12.dp))
            Text(progress.ifBlank { "QR を かざして ください" }, fontSize = 18.sp)
        }
        msg?.let { Text(it, fontSize = 14.sp, fontWeight = FontWeight.Bold) }
    }

    pending?.let { o ->
        AlertDialog(
            onDismissRequest = { pending = null },
            title = { Text("この せってい を 入れますか？") },
            text = { Text("いま の せってい は うわがき されます（この タブレット の こども・ふせん・ポイント は そのまま）。") },
            confirmButton = { TextButton(onClick = { msg = runCatching { SettingsBundle.import(context, o) }.getOrElse { "入れられません でした（${it.message}）" }; pending = null }) { Text("入れる") } },
            dismissButton = { TextButton(onClick = { pending = null }) { Text("やめる") } },
        )
    }
}
