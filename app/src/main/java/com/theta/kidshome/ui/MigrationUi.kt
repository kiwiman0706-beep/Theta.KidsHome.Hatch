package com.theta.kidshome.ui

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.theta.kidshome.data.Migration
import com.theta.kidshome.update.SelfUpdate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** 📦 新しい キッズホーム へ 引っ越し（書き出し・読み込み） */
@Composable
fun MigrationSection() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf<String?>(null) }
    var msg by remember { mutableStateOf<String?>(null) }
    var pending by remember { mutableStateOf<Uri?>(null) }
    fun mb(b: Long) = "%.1f MB".format(b / 1024.0 / 1024.0)

    val save = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { uri ->
        if (uri != null) scope.launch {
            busy = "書きだして います…"
            msg = runCatching {
                val s = withContext(Dispatchers.IO) { context.contentResolver.openOutputStream(uri)!!.use { Migration.export(context, it) } }
                "書きだしました：設定 ${s.prefs}・ファイル ${s.files}（${mb(s.bytes)}）"
            }.getOrElse { "書きだせません でした（${it.message}）" }
            busy = null
        }
    }
    val open = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> if (uri != null) pending = uri }

    Section("📦 新しい キッズホーム へ 引っ越し") {
        val mig = SelfUpdate.state.migrate
        if (mig != null) Text(
            "🆕 新しい アプリ「Theta KidsHome Hatch」（${mig.version}）が 出ました。この アプリ には 上書き できない ので、下 の 手順 で 移って ください。",
            fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color(0xFFD03B5C),
        )
        Text(
            "アプリ の 名前（ID）と 署名 が かわる ので、新しい アプリ を 入れて、ここ で 書きだした ファイル を 読みこみます。\n" +
                "1.「💾 書きだす」（SD か ダウンロード に）\n2.「🌐 新しい アプリ を 入れる」\n3. 新しい アプリ の 親モード「📦 引っ越し」→「📂 読みこむ」\n" +
                "4. 新しい アプリ を「既定 の ホーム」に えらぶ → この アプリ を 消す\n" +
                "入る もの：設定・子ども・ポイント・絵・スタンプ・字・家系図・写真・チャット・レジ の ラベル。入らない もの：Gemini キー・トークン（入れなおす）、おや の スマホ との つながり（つなぎなおす）、パック（📥 で 取りなおす）、辞書（SD から）。",
            fontSize = 13.sp,
        )
        FlowRow8 {
            Button(enabled = busy == null, onClick = {
                save.launch("kidshome-hikkoshi-" + SimpleDateFormat("yyyyMMdd", Locale.JAPAN).format(Date()) + ".zip")
            }) { Text("💾 書きだす") }
            OutlinedButton(onClick = {
                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(SelfUpdate.LATEST_URL)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            }) { Text("🌐 新しい アプリ を 入れる") }
            OutlinedButton(enabled = busy == null, onClick = { open.launch(arrayOf("application/zip", "application/octet-stream")) }) { Text("📂 読みこむ") }
        }
        busy?.let { Text(it, fontSize = 13.sp) }
        msg?.let { Text(it, fontSize = 14.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 4.dp)) }
    }

    pending?.let { uri ->
        AlertDialog(
            onDismissRequest = { pending = null },
            title = { Text("引っ越し の ファイル を 読みこみますか？") },
            text = { Text("いま の 設定・絵・ポイント など は、ファイル の 中身 で うわがき されます。おわったら アプリ を 開きなおします。") },
            confirmButton = {
                TextButton(onClick = {
                    pending = null
                    scope.launch {
                        busy = "読みこんで います…"
                        val r = runCatching { withContext(Dispatchers.IO) { context.contentResolver.openInputStream(uri)!!.use { Migration.import(context, it) } } }
                        busy = null
                        r.onSuccess { s ->
                            msg = "読みこみました：設定 ${s.prefs}・ファイル ${s.files}（${mb(s.bytes)}）。開きなおします…"
                            kotlinx.coroutines.delay(1500)
                            // 読みこんだ 設定 で はじめから
                            context.packageManager.getLaunchIntentForPackage(context.packageName)?.let {
                                context.startActivity(it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK))
                            }
                            Runtime.getRuntime().exit(0)
                        }.onFailure { msg = "読みこめません でした（${it.message}）" }
                    }
                }) { Text("読みこむ") }
            },
            dismissButton = { TextButton(onClick = { pending = null }) { Text("やめる") } },
        )
    }
}
