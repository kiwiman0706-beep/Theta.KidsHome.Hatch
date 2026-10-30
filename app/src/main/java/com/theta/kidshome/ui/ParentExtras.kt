package com.theta.kidshome.ui

import android.Manifest
import android.appwidget.AppWidgetProviderInfo
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.graphics.drawable.toBitmap
import com.theta.kidshome.data.AppConfig
import com.theta.kidshome.data.Launcher
import com.theta.kidshome.data.Events
import com.theta.kidshome.data.MediaLibrary
import com.theta.kidshome.data.SheetSync
import com.theta.kidshome.data.Store
import com.theta.kidshome.data.Weather
import com.theta.kidshome.data.WeatherPlace
import com.theta.kidshome.data.Widgets
import com.theta.kidshome.remote.Remote
import kotlinx.coroutines.launch

// ---------------------------------------------------------------- 呼びかけ

@Composable
fun RemoteSection(config: AppConfig) {
    val context = LocalContext.current
    val notifPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    var remoteQr by remember { mutableStateOf(false) }
    Section("📣 おうちのひとからの呼びかけ") {
        Text(
            "スマホのブラウザで下のページを開いて文字を送ると、動画を見ていてもキッズホームが前に出て、マスコットと一緒に表示されます。",
            fontSize = 14.sp,
        )
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 8.dp)) {
            Text("呼びかけを受け取る", fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            Switch(checked = config.remoteOn, onCheckedChange = { on ->
                Store.update { it.copy(remoteOn = on, remoteTopic = it.remoteTopic ?: Remote.newTopic()) }
                if (on && Build.VERSION.SDK_INT >= 33) notifPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                Remote.sync(context)
            })
        }
        val topic = config.remoteTopic
        if (config.remoteOn && topic != null) {
            val url = Remote.pageUrl(topic)
            Surface(shape = RoundedCornerShape(12.dp), color = Color(0xFFF6F1F3), modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp)) {
                    Text("送るページ", fontSize = 12.sp, color = Color.Gray)
                    Text(url, fontSize = 16.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
                }
            }
            Spacer(Modifier.height(8.dp))
            FlowRow8 {
                Button(onClick = {
                    val send = Intent(Intent.ACTION_SEND).setType("text/plain")
                        .putExtra(Intent.EXTRA_TEXT, "キッズホームへの呼びかけ：$url")
                    context.startActivity(Intent.createChooser(send, "送るページを自分のスマホへ").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                }) { Text("このリンクを自分のスマホへ送る") }
                OutlinedButton(onClick = { remoteQr = true }) { Text("📱 QR で見せる") }
                if (!Settings.canDrawOverlays(context)) {
                    Button(onClick = {
                        context.startActivity(
                            Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:${context.packageName}"))
                                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        )
                    }) { Text("「他のアプリの上に重ねて表示」を許可") }
                }
                OutlinedButton(onClick = { Remote.apply(context, "そろそろ おわりだよ") }) { Text("テスト表示") }
                OutlinedButton(onClick = {
                    Store.update { it.copy(remoteTopic = Remote.newTopic()) }
                    context.stopService(Intent(context, com.theta.kidshome.remote.RemoteService::class.java))
                    Remote.sync(context)
                }) { Text("ページを作り直す") }
            }
            Text(
                "送り方の例：\n" +
                    "・そろそろ おわりだよ　… 呼びかけだけ\n" +
                    "・/lock 30 ごはんだよ　… どうがを30分ロックして呼びかけ\n" +
                    "・/unlock　… ロック解除\n" +
                    "動画を見ている最中でも前に出すには「他のアプリの上に重ねて表示」の許可が必要です（無いときは通知になります）。" +
                    "ntfy.sh という公開の中継サービスを使います。ページのアドレスを知っている人は誰でも送れるので、人に教えないでください。",
                fontSize = 13.sp, color = Color.Gray, modifier = Modifier.padding(top = 8.dp),
            )
            if (remoteQr) QrDialog("📱 呼びかけのページ", url, note = "スマホのカメラで読んで、ホーム画面に追加しておくと便利です。") { remoteQr = false }
        }
    }
}

// ---------------------------------------------------------------- 天気

@Composable
fun WeatherSection(config: AppConfig) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var query by remember { mutableStateOf("") }
    var results by remember { mutableStateOf<List<WeatherPlace>>(emptyList()) }
    var searching by remember { mutableStateOf(false) }
    fun choose(p: WeatherPlace?) {
        Store.update { it.copy(weather = p) }
        Weather.clear()
        Weather.refresh(context, force = true)
        results = emptyList()
    }
    val locationPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        if (ok) choose(WeatherPlace("いまいる ところ", 35.68, 139.76, useLocation = true))
        else Toast.makeText(context, "位置の許可がないので、地名で選んでください", Toast.LENGTH_LONG).show()
    }
    Section("🌤 てんき（「とけい」タブ）") {
        Text("いまの場所：${config.weather?.name ?: "未設定（天気を出しません）"}", fontSize = 14.sp)
        Spacer(Modifier.height(8.dp))
        FlowRow8 {
            Button(onClick = { locationPermission.launch(Manifest.permission.ACCESS_COARSE_LOCATION) }) { Text("📍 いまいる場所（車で移動してもOK）") }
            if (config.weather != null) OutlinedButton(onClick = { choose(null) }) { Text("天気を出さない") }
        }
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 8.dp)) {
            OutlinedTextField(
                value = query, onValueChange = { query = it }, singleLine = true,
                label = { Text("地名でさがす（例：横浜）") }, modifier = Modifier.weight(1f),
            )
            TextButton(enabled = query.isNotBlank() && !searching, onClick = {
                searching = true
                scope.launch {
                    results = runCatching { Weather.search(query) }.getOrDefault(emptyList())
                    if (results.isEmpty()) Toast.makeText(context, "見つかりませんでした", Toast.LENGTH_SHORT).show()
                    searching = false
                }
            }) { Text("さがす") }
        }
        results.forEach { p ->
            TextButton(onClick = { choose(p) }) { Text(p.name) }
        }
    }
}

// ---------------------------------------------------------------- おんがく

@Composable
fun MusicSection(config: AppConfig) {
    val context = LocalContext.current
    var picking by remember { mutableStateOf(false) }
    Section("🎵 おんがく（ホームの再生ボタン）") {
        Text(
            "⏮ ⏯ ⏭ は、いま音を出しているアプリ（Spotify・YouTube Music・Amazon Music など）を操作します。" +
                "「ひらく」ボタンで起動するアプリも選べます。",
            fontSize = 14.sp,
        )
        Spacer(Modifier.height(8.dp))
        val pkg = config.musicPackage
        FlowRow8 {
            if (pkg != null) {
                val icon = remember(pkg) { Launcher.icon(context, pkg) }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (icon != null) Image(icon, null, Modifier.size(32.dp))
                    Text(" ひらくアプリ：" + Launcher.appLabel(context, pkg))
                }
            }
            Button(onClick = { picking = true }) { Text(if (pkg == null) "ひらくアプリを選ぶ" else "変える") }
            if (pkg != null) OutlinedButton(onClick = { Store.update { it.copy(musicPackage = null) } }) { Text("なしにする") }
        }
    }
    if (picking) {
        AppPickerDialog(emptySet(), onDismiss = { picking = false }) { apps ->
            Store.update { it.copy(musicPackage = apps.firstOrNull()?.packageName) }
            picking = false
        }
    }
}

// ---------------------------------------------------------------- ウィジェット

@Composable
fun WidgetSection(config: AppConfig) {
    val context = LocalContext.current
    var picking by remember { mutableStateOf(false) }
    var pendingId by remember { mutableIntStateOf(-1) }

    fun add(id: Int) {
        Store.update { it.copy(widgetIds = it.widgetIds + id) }
        Toast.makeText(context, "「ウィジェット」タブに置きました", Toast.LENGTH_SHORT).show()
    }
    val configure = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { r ->
        val id = pendingId
        pendingId = -1
        if (id < 0) return@rememberLauncherForActivityResult
        // 設定画面で「キャンセル」されても、置けるものは置く（Android 12 以降は設定が任意のものが多い）
        if (r.resultCode == android.app.Activity.RESULT_OK || Build.VERSION.SDK_INT >= 31) add(id) else Widgets.release(id)
    }
    fun configureOrAdd(id: Int) {
        val intent = Widgets.configureIntent(id)
        if (intent == null) {
            add(id)
            return
        }
        pendingId = id
        if (runCatching { configure.launch(intent) }.isFailure) {
            pendingId = -1
            add(id)
        }
    }
    val bind = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { r ->
        val id = pendingId
        if (id < 0) return@rememberLauncherForActivityResult
        if (r.resultCode == android.app.Activity.RESULT_OK) configureOrAdd(id)
        else {
            pendingId = -1
            Widgets.release(id)
            Toast.makeText(context, "ウィジェットの許可がありませんでした", Toast.LENGTH_LONG).show()
        }
    }
    fun pick(info: AppWidgetProviderInfo) {
        picking = false
        val id = runCatching { Widgets.allocate() }.getOrElse {
            Toast.makeText(context, "ウィジェットを用意できませんでした", Toast.LENGTH_LONG).show()
            return
        }
        if (runCatching { Widgets.bindIfAllowed(id, info) }.getOrDefault(false)) configureOrAdd(id)
        else {
            pendingId = id
            bind.launch(Widgets.bindIntent(id, info))
        }
    }

    Section("🧩 ウィジェット（「ウィジェット」タブ）") {
        if (config.widgetIds.isEmpty()) Text("まだありません。置くと、ホームに「ウィジェット」タブが出ます。", fontSize = 14.sp, color = Color.Gray)
        config.widgetIds.forEachIndexed { i, id ->
            val info = remember(id) { Widgets.info(id) }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(info?.let { Widgets.label(it) } ?: "（見つかりません）", modifier = Modifier.weight(1f))
                TextButton(enabled = i > 0, onClick = {
                    Store.update { c -> c.copy(widgetIds = c.widgetIds.toMutableList().apply { add(i - 1, removeAt(i)) }) }
                }) { Text("▲") }
                TextButton(onClick = {
                    Store.update { c -> c.copy(widgetIds = c.widgetIds - id) }
                    Widgets.release(id)
                }) { Text("外す", color = Color(0xFFD03B5C)) }
            }
        }
        Button(onClick = { picking = true }, modifier = Modifier.padding(top = 8.dp)) { Text("＋ ウィジェットを置く") }
    }

    if (picking) WidgetPickerDialog(onDismiss = { picking = false }, onPick = ::pick)
}

@Composable
private fun WidgetPickerDialog(onDismiss: () -> Unit, onPick: (AppWidgetProviderInfo) -> Unit) {
    val context = LocalContext.current
    val providers = remember { runCatching { Widgets.providers() }.getOrDefault(emptyList()) }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(shape = RoundedCornerShape(28.dp), modifier = Modifier.padding(24.dp).width(560.dp).heightIn(max = 760.dp)) {
            Column(Modifier.padding(24.dp)) {
                Text("ウィジェットを選ぶ", fontSize = 22.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(8.dp))
                LazyColumn(Modifier.weight(1f, fill = false)) {
                    items(providers) { info ->
                        val icon = remember(info) {
                            runCatching { info.loadIcon(context, 0)?.toBitmap(96, 96)?.asImageBitmap() }.getOrNull()
                        }
                        Row(
                            Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable { onPick(info) }.padding(8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            if (icon != null) Image(icon, null, Modifier.size(36.dp)) else Spacer(Modifier.size(36.dp))
                            Column(Modifier.padding(start = 12.dp)) {
                                Text(Widgets.label(info), fontSize = 16.sp)
                                Text(
                                    Launcher.appLabel(context, info.provider.packageName),
                                    fontSize = 12.sp, color = MaterialTheme.colorScheme.primary,
                                )
                            }
                        }
                    }
                }
                Row(Modifier.fillMaxWidth().padding(top = 8.dp)) {
                    Spacer(Modifier.weight(1f))
                    TextButton(onClick = onDismiss) { Text("やめる") }
                }
            }
        }
    }
}

// ---------------------------------------------------------------- スプレッドシート

@Composable
fun SheetSection(config: AppConfig) {
    val context = LocalContext.current
    val st = SheetSync.state
    var url by remember(config.sheetUrl) { mutableStateOf(config.sheetUrl.orEmpty()) }
    val scanSheet = rememberQrScanner("スプレッドシートのリンクの QR コードを うつしてください") { v ->
        if (com.theta.kidshome.data.QrSetup.isSheet(v.trim())) {
            url = v.trim()
            Store.update { it.copy(sheetUrl = v.trim()) }
            SheetSync.sync(context, force = true)
            Toast.makeText(context, "📷 スプレッドシートを設定しました", Toast.LENGTH_SHORT).show()
        } else applyQrSetup(context, v)
    }
    Section("📝 親のスマホから設定（Google スプレッドシート）") {
        Text(
            "スプレッドシートに書いた こども・ボタン・カテゴリー・アプリの仕分け・せってい を、このタブレットが読み込みます（15分ごと と ホームに戻ったとき）。" +
                "シートは「リンクを知っている全員」が閲覧できるように共有してください。書き換えられるのは持ち主だけです。",
            fontSize = 14.sp,
        )
        Spacer(Modifier.height(8.dp))
        Text("はじめかた", fontWeight = FontWeight.Bold)
        Text(
            "1. 下の「いまの設定を書き出す」→ Google ドライブに保存\n" +
                "2. ドライブで開いて「Google スプレッドシートとして保存」\n" +
                "3. 共有 →「リンクを知っている全員」（閲覧者）→ リンクをコピー\n" +
                "4. そのリンクをここに貼る（スマホで共有 → QR コードを出して「📷 QR」で読んでも OK）",
            fontSize = 13.sp, color = Color.Gray,
        )
        Spacer(Modifier.height(8.dp))
        FlowRow8 {
            OutlinedButton(onClick = { exportSheet(context, config) }) { Text("いまの設定を書き出す（.xlsx）") }
        }
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 8.dp)) {
            OutlinedTextField(
                value = url, onValueChange = { url = it }, singleLine = true,
                label = { Text("スプレッドシートのリンク") }, modifier = Modifier.weight(1f),
            )
            TextButton(onClick = scanSheet) { Text("📷 QR") }
            TextButton(onClick = {
                val v = url.trim().ifEmpty { null }
                if (v != null && SheetSync.sheetId(v) == null) {
                    Toast.makeText(context, "スプレッドシートのリンクではありません", Toast.LENGTH_LONG).show()
                } else {
                    Store.update { it.copy(sheetUrl = v) }
                    SheetSync.sync(context, force = true)
                }
            }) { Text(if (url.isBlank()) "同期をやめる" else "保存して同期") }
        }
        if (config.sheetUrl != null) {
            Text(
                when {
                    st.syncing -> "読み込み中…"
                    st.error != null -> "⚠️ ${st.error}"
                    st.syncedAt > 0 -> "✅ ${java.text.SimpleDateFormat("H:mm", java.util.Locale.JAPAN).format(java.util.Date(st.syncedAt))} に読み込みました"
                    else -> " "
                },
                fontSize = 14.sp, modifier = Modifier.padding(top = 6.dp),
            )
            st.note?.let { Text(it, fontSize = 12.sp, color = Color.Gray) }
            Row(verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = { SheetSync.sync(context, force = true) }) { Text("今すぐ読み込む") }
                TextButton(onClick = {
                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(config.sheetUrl)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                }) { Text("シートを開く ↗") }
            }
            Text(
                "同期中は、タブレットで変えたボタン・仕分けは次の読み込みでシートの内容に戻ります。変えるときはシートで。" +
                    "ウィジェット・おんがく・呼びかけ・どうがロックはタブレットだけの設定です。",
                fontSize = 12.sp, color = Color.Gray,
            )
        }
    }
}

private fun exportSheet(context: android.content.Context, config: AppConfig) {
    runCatching {
        val dir = java.io.File(context.cacheDir, "export").apply { mkdirs() }
        val file = java.io.File(dir, "キッズホームの設定.xlsx")
        SheetSync.writeXlsx(SheetSync.tables(context, config), file)
        val uri = androidx.core.content.FileProvider.getUriForFile(context, "${context.packageName}.files", file)
        val send = Intent(Intent.ACTION_SEND)
            .setType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")
            .putExtra(Intent.EXTRA_STREAM, uri)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        context.startActivity(Intent.createChooser(send, "ドライブに保存").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }.onFailure { Toast.makeText(context, "書き出せませんでした（${it.message}）", Toast.LENGTH_LONG).show() }
}

// ---------------------------------------------------------------- 端末の動画・音楽・おはなし

@Composable
fun MediaSection(config: AppConfig) {
    val context = LocalContext.current
    var granted by remember { mutableStateOf(MediaLibrary.hasPermission(context)) }
    var calendar by remember { mutableStateOf(Events.hasPermission(context)) }
    val ask = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        granted = MediaLibrary.hasPermission(context)
        if (granted) MediaLibrary.refresh(context, force = true)
    }
    val askCalendar = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        calendar = ok
        if (ok) Events.refresh(context, force = true)
    }
    Section("📼 端末の動画・音楽・おはなし") {
        Text(
            "本体と SD カードに入っている動画・音楽・オーディオブック（.m4b など）を、サムネイルで選んでホームの中で再生します。" +
                "動画は「どうがロック」の対象です。見たところ・聞いたところは覚えて、次は続きから。",
            fontSize = 14.sp,
        )
        Spacer(Modifier.height(8.dp))
        if (!granted) {
            Button(onClick = { ask.launch(MediaLibrary.permissions) }) { Text("動画と音楽へのアクセスを許可") }
        } else {
            Text(
                "見つかったもの：動画 ${MediaLibrary.videos.size}・音楽 ${MediaLibrary.music.size}・おはなし ${MediaLibrary.books.size}",
                fontSize = 14.sp, fontWeight = FontWeight.Bold,
            )
            listOf(
                Triple("📼 ビデオ のタブ", config.showVideos) { v: Boolean -> Store.update { it.copy(showVideos = v) } },
                Triple("🎵 おんがく のタブ", config.showMusic) { v: Boolean -> Store.update { it.copy(showMusic = v) } },
                Triple("🎧 おはなし のタブ（オーディオブック）", config.showBooks) { v: Boolean -> Store.update { it.copy(showBooks = v) } },
            ).forEach { (label, on, set) ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(label, modifier = Modifier.weight(1f))
                    Switch(checked = on, onCheckedChange = set)
                }
            }
            val folders = (MediaLibrary.videos + MediaLibrary.music + MediaLibrary.books).groupBy { it.folder }
            if (folders.isNotEmpty()) {
                Text("フォルダごとに 見せる・かくす（カメラで撮った動画などはかくす）", fontSize = 13.sp, color = Color.Gray, modifier = Modifier.padding(top = 8.dp))
                FlowRow8 {
                    folders.toSortedMap().forEach { (folder, list) ->
                        val hidden = folder in config.hiddenFolders
                        androidx.compose.material3.FilterChip(
                            selected = !hidden,
                            onClick = { Store.update { it.copy(hiddenFolders = if (hidden) it.hiddenFolders - folder else it.hiddenFolders + folder) } },
                            label = { Text("${if (hidden) "🙈" else "👀"} $folder（${list.size}）") },
                        )
                    }
                }
            }
            TextButton(onClick = { MediaLibrary.refresh(context, force = true) }) { Text("読み直す") }
            Text(
                "オーディオブックは、.m4b のファイル、または「Audiobooks」「オーディオブック」「おはなし」という名前のフォルダに入れた音声です。" +
                    "再生できない形式の動画は、VLC が入っていれば「VLC で みる」が出ます。",
                fontSize = 12.sp, color = Color.Gray,
            )
        }
        Spacer(Modifier.height(12.dp))
        Text("📅 カレンダーの予定", fontWeight = FontWeight.Bold)
        Text("端末のカレンダー（子どもの Google アカウントなど）の予定を、ホームのカレンダーに出します。", fontSize = 14.sp)
        if (!calendar) {
            OutlinedButton(onClick = { askCalendar.launch(Manifest.permission.READ_CALENDAR) }) { Text("この端末のカレンダーを表示する") }
        } else {
            Text("✅ この端末のカレンダーを表示しています", fontSize = 14.sp)
        }
        Spacer(Modifier.height(8.dp))
        Text("Google カレンダー（ログインなし）", fontWeight = FontWeight.Bold)
        Text(
            "パソコンの Google カレンダー → 設定 → 見せたいカレンダー →「iCal 形式の非公開アドレス」をコピーして貼ります。" +
                "家族のカレンダーを、子どものタブレットに Google アカウントを入れずに出せます（読むだけ）。このアドレスは人に教えないでください。",
            fontSize = 13.sp, color = Color.Gray,
        )
        config.calendarUrls.forEachIndexed { i, u ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("📅 " + u.take(48) + if (u.length > 48) "…" else "", fontSize = 13.sp, modifier = Modifier.weight(1f))
                TextButton(onClick = {
                    Store.update { c -> c.copy(calendarUrls = c.calendarUrls.filterIndexed { j, _ -> j != i }) }
                    Events.refresh(context, force = true)
                }) { Text("外す", color = Color(0xFFD03B5C)) }
            }
        }
        var ical by remember { mutableStateOf("") }
        val scanIcal = rememberQrScanner("iCal アドレスの QR コードを うつしてください") { applyQrSetup(context, it) }
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(ical, { ical = it }, singleLine = true, label = { Text("https://calendar.google.com/calendar/ical/…/basic.ics") }, modifier = Modifier.weight(1f))
            TextButton(enabled = ical.trim().let { it.startsWith("http") || it.startsWith("webcal") }, onClick = {
                Store.update { it.copy(calendarUrls = it.calendarUrls + ical.trim()) }
                ical = ""
                Events.refresh(context, force = true)
            }) { Text("足す") }
            TextButton(onClick = scanIcal) { Text("📷 QR") }
        }
        Events.error?.let { Text("⚠️ 読めないカレンダーがあります（$it）", fontSize = 12.sp, color = Color(0xFFD03B5C)) }
    }
}

// ---------------------------------------------------------------- Hatch シリーズ

@Composable
fun HatchSection() {
    val context = LocalContext.current
    androidx.compose.runtime.LaunchedEffect(Unit) { com.theta.kidshome.update.Hatch.apps.forEach { com.theta.kidshome.update.Hatch.check(it) } }
    Section("🥚 Hatch シリーズのアプリ") {
        com.theta.kidshome.update.Hatch.apps.forEach { app ->
            val st = com.theta.kidshome.update.Hatch.states[app.packageName] ?: com.theta.kidshome.update.Hatch.State()
            val installed = com.theta.kidshome.update.Hatch.installedVersion(context, app)
            val latest = st.latest?.version
            Surface(shape = RoundedCornerShape(16.dp), color = Color(0xFFFAF6F8), modifier = Modifier.fillMaxWidth().padding(top = 6.dp)) {
                Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(app.emoji, fontSize = 32.sp)
                    Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                        Text(app.name, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                        Text(app.description, fontSize = 13.sp, color = Color.Gray)
                        Text(
                            buildString {
                                append(if (installed != null) "入っている版：$installed" else "まだ入っていません")
                                when {
                                    st.progress != null -> append("　ダウンロード中 ${(st.progress * 100).toInt()}%")
                                    st.checking -> append("　確かめています…")
                                    latest != null -> append("　最新：$latest")
                                    st.error != null -> append("　（${st.error}）")
                                }
                            },
                            fontSize = 13.sp,
                        )
                    }
                    val newer = latest != null && (installed == null || com.theta.kidshome.update.isNewer(latest, installed))
                    if (newer) {
                        Button(enabled = st.progress == null, onClick = { com.theta.kidshome.update.Hatch.install(context, app) }) {
                            Text(if (installed == null) "インストール" else "アップデート")
                        }
                    }
                    if (installed != null) {
                        TextButton(onClick = { Launcher.launchPackage(context, app.packageName) }) { Text("ひらく") }
                    }
                }
            }
        }
        Text(
            "入れたあと、子どものホームに出すには「ボタン」の「アプリを追加」か、アプリの仕分けで見せるカテゴリーに入れてください。",
            fontSize = 12.sp, color = Color.Gray, modifier = Modifier.padding(top = 8.dp),
        )
    }
}

// ---------------------------------------------------------------- ピアノの れんしゅう曲

@Composable
fun SongSection(config: AppConfig) {
    var title by remember { mutableStateOf("") }
    var notes by remember { mutableStateOf("") }
    Section("🎹 ピアノの れんしゅう曲") {
        Text(
            "「がっき」→「🎓 れんしゅう」で、次に押す けん が光り、押すまで待ってくれます。入っている曲：" +
                com.theta.kidshome.media.Songs.builtIn.joinToString("・") { it.title } + "。",
            fontSize = 14.sp,
        )
        config.songs.forEachIndexed { i, s ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("🎵 ${s.title}（${com.theta.kidshome.media.Songs.parse(s.notes).size}おと）", modifier = Modifier.weight(1f))
                TextButton(onClick = { Store.update { c -> c.copy(songs = c.songs.filterIndexed { j, _ -> j != i }) } }) {
                    Text("削除", color = Color(0xFFD03B5C))
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        Text("曲を足す（ドレミで書く）", fontWeight = FontWeight.Bold)
        OutlinedTextField(title, { title = it }, singleLine = true, label = { Text("曲の名前") }, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(notes, { notes = it }, label = { Text("例：ドドソソララソー ファファミミレレドー") }, modifier = Modifier.fillMaxWidth().padding(top = 6.dp))
        val parsed = com.theta.kidshome.media.Songs.parse(notes)
        Text(
            "ド レ ミ ファ ソ ラ シ。うしろに ＋ で1つ上の高さ（＋＋で2つ上）、♯ で半音上、ー で のばす。いま ${parsed.size} おと。",
            fontSize = 12.sp, color = Color.Gray,
        )
        Button(
            enabled = title.isNotBlank() && parsed.isNotEmpty(),
            onClick = {
                Store.update { it.copy(songs = it.songs + com.theta.kidshome.media.Song(title.trim(), notes.trim())) }
                title = ""; notes = ""
            },
            modifier = Modifier.padding(top = 6.dp),
        ) { Text("足す") }
    }
}

// ---------------------------------------------------------------- Gemini（写真を お絵かき風 に）

@Composable
fun GeminiSection() {
    val context = LocalContext.current
    var key by remember { mutableStateOf(com.theta.kidshome.data.Gemini.apiKey(context)) }
    var model by remember { mutableStateOf(com.theta.kidshome.data.Gemini.model(context)) }
    var shown by remember { mutableStateOf(false) }
    val scanKey = rememberQrScanner("API キーの QR コードを うつしてください") { v ->
        if (com.theta.kidshome.data.QrSetup.isGeminiKey(v.trim())) {
            key = v.trim()
            com.theta.kidshome.data.Gemini.setApiKey(context, key)
            Toast.makeText(context, "📷 Gemini キーを保存しました", Toast.LENGTH_SHORT).show()
        } else if (applyQrSetup(context, v) != null) {
            key = com.theta.kidshome.data.Gemini.apiKey(context)
            model = com.theta.kidshome.data.Gemini.model(context)
        }
    }
    Section("✨ 写真を お絵かき風 に（Gemini）") {
        Text(
            "おえかき で 🖼️ 写真を開いたあと ✨ を押すと、写真を ぬりえ・クレヨン・すいさい・まんが の絵にしてから描けます。" +
                "Google AI Studio で作った自分の API キーを使います（画像の生成は有料のプロジェクトのキーが必要なことがあります）。" +
                "写真は Google に送られます。キーはこのタブレットだけに保存し、スプレッドシート・書き出しには入れません。" +
                "長いキーは、パソコンで QR コードにして「📷 QR で読む」と楽です。",
            fontSize = 14.sp,
        )
        OutlinedTextField(
            key, { key = it }, singleLine = true, label = { Text("Gemini API キー") },
            visualTransformation = if (shown) androidx.compose.ui.text.input.VisualTransformation.None else androidx.compose.ui.text.input.PasswordVisualTransformation(),
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
        )
        OutlinedTextField(model, { model = it }, singleLine = true, label = { Text("モデル") }, modifier = Modifier.fillMaxWidth().padding(top = 6.dp))
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 6.dp)) {
            Button(onClick = {
                com.theta.kidshome.data.Gemini.setApiKey(context, key)
                com.theta.kidshome.data.Gemini.setModel(context, model)
                Toast.makeText(context, "保存しました", Toast.LENGTH_SHORT).show()
            }) { Text("保存") }
            TextButton(onClick = scanKey) { Text("📷 QR で読む") }
            TextButton(onClick = { shown = !shown }) { Text(if (shown) "かくす" else "見る") }
            TextButton(onClick = { model = com.theta.kidshome.data.Gemini.DEFAULT_MODEL }) { Text("モデルを はじめに戻す") }
        }
    }
}
