package com.theta.kidshome.ui

import android.content.Intent
import android.provider.Settings
import androidx.activity.compose.BackHandler
import android.app.StatusBarManager
import android.content.ComponentName
import android.net.Uri
import android.os.Build
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.GridItemSpan
import com.theta.kidshome.data.Categories
import com.theta.kidshome.data.InstalledApp
import androidx.compose.material3.Switch
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.ui.text.style.TextAlign
import com.theta.kidshome.R
import com.theta.kidshome.rescue.Rescue
import com.theta.kidshome.rescue.RescueActivity
import com.theta.kidshome.rescue.RescueTile
import com.theta.kidshome.update.SelfUpdate
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.theta.kidshome.data.AppConfig
import com.theta.kidshome.data.Launcher
import com.theta.kidshome.data.LockReason
import com.theta.kidshome.data.Profile
import com.theta.kidshome.data.Store
import com.theta.kidshome.data.Tile
import com.theta.kidshome.data.TileType
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

@Composable
fun ParentScreen(onClose: () -> Unit) {
    BackHandler(onBack = onClose)
    var tab by remember { mutableIntStateOf(0) }

    ParentTheme {
        Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
            Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing).padding(20.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("🔧 おうちのひと", fontSize = 26.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.width(20.dp))
                    FilterChip(selected = tab == 0, onClick = { tab = 0 }, label = { Text("📱 すべてのアプリ", fontSize = 16.sp) })
                    Spacer(Modifier.width(8.dp))
                    FilterChip(selected = tab == 1, onClick = { tab = 1 }, label = { Text("⚙️ せってい", fontSize = 16.sp) })
                    Spacer(Modifier.weight(1f))
                    Button(onClick = onClose) { Text("ホームにもどる") }
                }
                Spacer(Modifier.height(16.dp))
                if (tab == 0) AllApps(Modifier.weight(1f)) else SettingsTab(Modifier.weight(1f))
            }
        }
    }
}

/** 端末に入っているアプリをぜんぶ出す（おうちのひと用）。カテゴリーごとに並べ、押すと ひらく・仕分け */
@Composable
private fun AllApps(modifier: Modifier) {
    val context = LocalContext.current
    val config = Store.config
    val apps = remember { Launcher.listApps(context) }
    var query by remember { mutableStateOf("") }
    var selected by remember { mutableStateOf<InstalledApp?>(null) }
    var guide by remember { mutableStateOf(false) }
    val shown = apps.filter { query.isBlank() || it.label.contains(query, ignoreCase = true) }
    val groups: List<Pair<String, List<InstalledApp>>> = buildList {
        config.categories.forEach { cat ->
            add("${cat.emoji} ${cat.name}　${visibleText(config, cat)}" to shown.filter { config.appCategory[it.packageName] == cat.id })
        }
        add("🙈 かくす" to shown.filter { config.appCategory[it.packageName] == Categories.HIDDEN })
        add("❓ 未分類（子どもには出ません）" to shown.filter { it.packageName !in config.appCategory || config.categoryOf(it.packageName) == null && config.appCategory[it.packageName] != Categories.HIDDEN })
    }.filter { it.second.isNotEmpty() }
    val unsorted = apps.count { it.packageName !in config.appCategory }

    Column(modifier) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = query, onValueChange = { query = it }, singleLine = true,
                label = { Text("さがす") }, modifier = Modifier.widthIn(max = 480.dp).weight(1f, fill = false),
            )
            Spacer(Modifier.width(12.dp))
            Button(onClick = { guide = true }) { Text(if (unsorted > 0) "仕分け $unsorted" else "仕分け") }
        }
        Text("アプリを押すと「ひらく」か「カテゴリーを変える」を選べます。", fontSize = 13.sp, color = Color.Gray, modifier = Modifier.padding(vertical = 8.dp))
        LazyVerticalGrid(GridCells.Adaptive(104.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            groups.forEach { (title, list) ->
                item(span = { GridItemSpan(maxLineSpan) }, key = "h:$title") {
                    Text(title, fontSize = 16.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 12.dp, bottom = 4.dp))
                }
                items(list, key = { title + it.packageName }) { app ->
                    val icon = remember(app.packageName) { Launcher.icon(context, app.packageName) }
                    Column(
                        Modifier.clip(RoundedCornerShape(16.dp)).clickable { selected = app }.padding(8.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        if (icon != null) Image(icon, null, Modifier.size(56.dp)) else Spacer(Modifier.size(56.dp))
                        Text(app.label, fontSize = 12.sp, maxLines = 2, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 4.dp))
                    }
                }
            }
        }
    }
    selected?.let { AppCategoryDialog(it) { selected = null } }
    if (guide) SortGuideDialog { guide = false }
}

@Composable
private fun SettingsTab(modifier: Modifier) {
    val config = Store.config
    var editingProfileId by remember { mutableStateOf(config.currentProfileId) }
    val profile = config.profiles.firstOrNull { it.id == editingProfileId } ?: config.profiles.first()
    val context = LocalContext.current

    Column(modifier.verticalScroll(rememberScrollState()).widthIn(max = 900.dp)) {
        UpdateSection(config)

        QrSection(config)

        SheetSection(config)

        MediaSection(config)

        SongSection(config)

        MojiExportSection(config)

        HatchSection()

        GeminiSection()

        StampLibrarySection(config)

        CategorySection(config)

        VideoLockSection(config)

        Section("👧 こどもごとの設定") {
            ProfileTabs(config, profile.id) { editingProfileId = it }
            Spacer(Modifier.height(12.dp))
            ProfileEditor(profile, canDelete = config.profiles.size > 1) {
                Store.update { c ->
                    val rest = c.profiles.filter { it.id != profile.id }
                    c.copy(
                        profiles = rest,
                        currentProfileId = if (c.currentProfileId == profile.id) rest.first().id else c.currentProfileId,
                    )
                }
                editingProfileId = Store.config.profiles.first().id
            }
        }

        RemoteSection(config)

        WeatherSection(config)

        MusicSection(config)

        WidgetSection(config)

        Section("🔑 おうちのひと かくにん") {
            Text("⚙️ を2秒ながおし → かけ算 に答えると、このメニューに入れます。", fontSize = 14.sp)
            Spacer(Modifier.height(8.dp))
            FlowRow8 {
                FilterChip(selected = !config.hardGate, onClick = { Store.update { it.copy(hardGate = false) } }, label = { Text("ふつう（23 × 7）") })
                FilterChip(selected = config.hardGate, onClick = { Store.update { it.copy(hardGate = true) } }, label = { Text("むずかしい（23 × 47）") })
            }
            Text("お子さんが九九を覚えたら「むずかしい」に。", fontSize = 13.sp, color = Color.Gray, modifier = Modifier.padding(top = 8.dp))
        }

        Section("🛟 もしものとき（レスキュー）") {
            Text(
                "キッズホームが続けて落ちる・固まると、自動でレスキュー画面になります（別のホームを選ぶ／セーフモード／最新版を入れる）。" +
                    "通知パネルのクイック設定にタイルを足しておくと、いつでも開けます（開くときもかけ算を聞きます）。",
                fontSize = 14.sp,
            )
            Spacer(Modifier.height(8.dp))
            FlowRow8 {
                if (Build.VERSION.SDK_INT >= 33) {
                    OutlinedButton(onClick = { addRescueTile(context) }) { Text("クイック設定にタイルを足す") }
                }
                OutlinedButton(onClick = {
                    context.startActivity(Intent(context, RescueActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                }) { Text("レスキュー画面を開く") }
                OutlinedButton(onClick = { Rescue.chooseHome(context) }) { Text("ホームアプリを切り替える") }
                OutlinedButton(onClick = {
                    context.startActivity(Intent(Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                }) { Text("Android の設定を開く") }
            }
            Text(
                "ヒント: Netflix や Prime Video で作品を開き「共有」→「キッズホームに登録」を選ぶと、そのままお気に入り登録できます。",
                fontSize = 13.sp, color = Color.Gray, modifier = Modifier.padding(top = 12.dp),
            )
        }
    }
}

private fun addRescueTile(context: android.content.Context) {
    if (Build.VERSION.SDK_INT < 33) return
    val sbm = context.getSystemService(StatusBarManager::class.java) ?: return
    sbm.requestAddTileService(
        ComponentName(context, RescueTile::class.java),
        "キッズホームのレスキュー",
        android.graphics.drawable.Icon.createWithResource(context, R.drawable.ic_rescue),
        context.mainExecutor,
    ) { }
}

@Composable
private fun UpdateSection(config: AppConfig) {
    val context = LocalContext.current
    val st = SelfUpdate.state
    val current = remember { SelfUpdate.currentVersion(context) }
    var apkQr by remember { mutableStateOf(false) }
    Section("🆕 アップデート") {
        Text("いまの版：$current", fontSize = 14.sp)
        Text(
            when {
                st.checking -> "確かめています…"
                st.progress != null -> "ダウンロード中 ${(st.progress * 100).toInt()}%"
                st.newer && st.latest != null -> "新しい版 ${st.latest.version} があります"
                st.error != null -> "確かめられませんでした（${st.error}）"
                st.checkedAt > 0 -> "最新です"
                else -> " "
            },
            fontSize = 16.sp, fontWeight = FontWeight.Bold,
            color = if (st.newer) MaterialTheme.colorScheme.primary else Color.Gray,
            modifier = Modifier.padding(vertical = 6.dp),
        )
        FlowRow8 {
            if (st.newer) {
                Button(onClick = { SelfUpdate.downloadAndInstall(context) }, enabled = st.progress == null) { Text("アップデートする") }
            }
            OutlinedButton(onClick = { SelfUpdate.check(context, force = true) }) { Text("今すぐ確かめる") }
            OutlinedButton(onClick = {
                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(SelfUpdate.RELEASES_URL)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            }) { Text("更新履歴 ↗") }
            OutlinedButton(onClick = { apkQr = true }) { Text("📲 別のタブレットに入れる（QR）") }
        }
        if (apkQr) QrDialog(
            "📲 別のタブレットに入れる", LATEST_APK_URL,
            note = "新しいタブレットのカメラで読むと、最新版の APK がダウンロードされます。開いてインストールしてください。",
        ) { apkQr = false }
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 8.dp)) {
            Text("自動で確かめる（1時間に1回まで）", modifier = Modifier.weight(1f))
            Switch(checked = config.autoUpdate, onCheckedChange = { v -> Store.update { it.copy(autoUpdate = v) } })
        }
    }
}

@Composable
internal fun Section(title: String, content: @Composable () -> Unit) {
    Card(
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(2.dp),
        modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
    ) {
        Column(Modifier.padding(20.dp)) {
            Text(title, fontSize = 20.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(12.dp))
            content()
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun FlowRow8(content: @Composable () -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        content()
    }
}

// ---------------------------------------------------------------- どうがロック

@Composable
private fun VideoLockSection(config: AppConfig) {
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            now = System.currentTimeMillis()
            delay(1000)
        }
    }
    val locked = config.isVideoLocked(now)

    fun lockFor(minutes: Int) = Store.update {
        // ロック中に押したら延長、そうでなければ今から
        val now = System.currentTimeMillis()
        if (it.isVideoLocked()) it.copy(videoLockUntil = it.videoLockUntil + minutes * 60_000L)
        else it.copy(videoLockFrom = now, videoLockUntil = now + minutes * 60_000L)
    }

    Section("🔒 どうがロック") {
        Text(
            if (locked) {
                val left = (config.videoLockUntil - now + 59_999) / 60_000
                "ロック中：${SimpleDateFormat("H:mm", Locale.JAPAN).format(Date(config.videoLockUntil))} まで（あと ${left} 分）"
            } else {
                "いまは どうがを見られます"
            },
            fontSize = 16.sp,
            color = if (locked) MaterialTheme.colorScheme.primary else Color.Gray,
            fontWeight = FontWeight.Bold,
        )
        Spacer(Modifier.height(8.dp))
        Text("表示する理由", fontSize = 13.sp, color = Color.Gray)
        FlowRow8 {
            LockReason.entries.forEach { r ->
                FilterChip(
                    selected = config.lockReason == r,
                    onClick = { Store.update { it.copy(lockReason = r) } },
                    label = { Text("${r.emoji} ${r.title}") },
                )
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(if (locked) "延長する" else "ロックする", fontSize = 13.sp, color = Color.Gray)
        FlowRow8 {
            listOf(15, 30, 60, 120).forEach { m ->
                Button(onClick = { lockFor(m) }) { Text(if (m >= 60) "${m / 60}時間" else "${m}分") }
            }
            OutlinedButton(onClick = {
                val morning = Calendar.getInstance().apply {
                    add(Calendar.DAY_OF_MONTH, 1)
                    set(Calendar.HOUR_OF_DAY, 6); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0)
                }.timeInMillis
                Store.update { it.copy(videoLockFrom = System.currentTimeMillis(), videoLockUntil = morning) }
            }) { Text("あしたの朝まで") }
            if (locked) {
                OutlinedButton(onClick = { Store.update { it.copy(videoLockUntil = 0L) } }) { Text("ロック解除") }
            }
        }
        Text(
            "「どうが」マークのボタン（動画リンク・動画アプリ）だけが押せなくなります。おえかきや学習アプリはそのまま使えます。",
            fontSize = 13.sp, color = Color.Gray, modifier = Modifier.padding(top = 8.dp),
        )
    }
}

// ---------------------------------------------------------------- プロフィール

@Composable
private fun ProfileTabs(config: AppConfig, selectedId: String, onSelect: (String) -> Unit) {
    FlowRow8 {
        config.profiles.forEach { p ->
            FilterChip(
                selected = p.id == selectedId,
                onClick = { onSelect(p.id) },
                label = { Text("${themeById(p.themeId).mascot} ${p.name}", fontSize = 16.sp) },
            )
        }
        if (config.profiles.size < 4) {
            TextButton(onClick = {
                val p = Profile(name = "こども${config.profiles.size + 1}", themeId = KidThemes[config.profiles.size % KidThemes.size].id, tiles = listOf(AppConfig.drawingTile()))
                Store.update { it.copy(profiles = it.profiles + p) }
                onSelect(p.id)
            }) { Text("＋ こどもを追加") }
        }
    }
}

@Composable
private fun ProfileEditor(profile: Profile, canDelete: Boolean, onDelete: () -> Unit) {
    var showAppPicker by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<Tile?>(null) }
    var addingVideo by remember { mutableStateOf(false) }
    var addingCall by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }

    // 名前は入力中に毎回保存すると重いので、ローカルに持って確定時に保存
    var name by remember(profile.id) { mutableStateOf(profile.name) }
    OutlinedTextField(
        value = name,
        onValueChange = {
            name = it
            if (it.isNotBlank()) Store.updateProfile(profile.id) { p -> p.copy(name = it.trim()) }
        },
        singleLine = true,
        label = { Text("なまえ") },
        modifier = Modifier.fillMaxWidth(),
    )
    Spacer(Modifier.height(12.dp))
    Text("テーマ（キャラクター）", fontSize = 13.sp, color = Color.Gray)
    ThemePicker(profile.themeId) { id -> Store.updateProfile(profile.id) { it.copy(themeId = id) } }

    GradeEditor(profile)

    TimetableEditor(profile)

    Spacer(Modifier.height(16.dp))
    Text("ボタン（上から順に並びます。どうがリンクはサービスごとのタブに入ります）", fontSize = 13.sp, color = Color.Gray)
    if (profile.tiles.isEmpty()) {
        Text("まだありません", color = Color.Gray, modifier = Modifier.padding(8.dp))
    }
    profile.tiles.forEachIndexed { i, tile ->
        TileRow(
            tile = tile,
            onUp = if (i > 0) ({ move(profile, i, i - 1) }) else null,
            onDown = if (i < profile.tiles.lastIndex) ({ move(profile, i, i + 1) }) else null,
            onEdit = { editing = tile },
            onDelete = { Store.updateProfile(profile.id) { p -> p.copy(tiles = p.tiles.filter { it.id != tile.id }) } },
        )
    }
    Spacer(Modifier.height(12.dp))
    FlowRow8 {
        Button(onClick = { addingVideo = true }) { Text("🎬 どうがリンクを追加") }
        Button(onClick = { showAppPicker = true }) { Text("📱 アプリを追加") }
        OutlinedButton(onClick = { addingCall = true }) { Text("📞 でんわボタンを追加") }
        if (profile.tiles.none { it.type == TileType.EDUPAD }) {
            OutlinedButton(onClick = { Store.addTiles(profile.id, listOf(AppConfig.edupadTile())) }) { Text("🧠 くるくる思考パッドを追加") }
        }
        if (profile.tiles.none { it.type == TileType.MAP }) {
            OutlinedButton(onClick = { Store.addTiles(profile.id, listOf(AppConfig.mapTile())) }) { Text("🗺️ ちずを追加") }
        }
        if (profile.tiles.none { it.type == TileType.TASU }) {
            OutlinedButton(onClick = { Store.addTiles(profile.id, listOf(AppConfig.tasuTile())) }) { Text("➕ たしざん の ひょうを追加") }
        }
        if (profile.tiles.none { it.type == TileType.KUKU }) {
            OutlinedButton(onClick = { Store.addTiles(profile.id, listOf(AppConfig.kukuTile())) }) { Text("✖️ くくの ひょうを追加") }
        }
        if (profile.tiles.none { it.type == TileType.MOJI }) {
            OutlinedButton(onClick = { Store.addTiles(profile.id, listOf(AppConfig.mojiTile())) }) { Text("✍️ もじ れんしゅうを追加") }
        }
        if (profile.tiles.none { it.type == TileType.TYPING }) {
            OutlinedButton(onClick = { Store.addTiles(profile.id, listOf(AppConfig.typingTile())) }) { Text("⌨️ タイピングを追加") }
        }
        if (profile.tiles.none { it.type == TileType.PUZZLE }) {
            OutlinedButton(onClick = { Store.addTiles(profile.id, listOf(AppConfig.puzzleTile())) }) { Text("🧩 パズルを追加") }
        }
        if (profile.tiles.none { it.type == TileType.WORDPRO }) {
            OutlinedButton(onClick = { Store.addTiles(profile.id, listOf(AppConfig.wordproTile())) }) { Text("📝 わーぷろを追加") }
        }
        if (profile.tiles.none { it.type == TileType.INSTRUMENT }) {
            OutlinedButton(onClick = { Store.addTiles(profile.id, listOf(AppConfig.instrumentTile())) }) { Text("🎹 がっきを追加") }
        }
        if (profile.tiles.none { it.type == TileType.DRAWING }) {
            OutlinedButton(onClick = { Store.addTiles(profile.id, listOf(AppConfig.drawingTile())) }) { Text("🎨 おえかきを追加") }
        }
        if (canDelete) {
            TextButton(onClick = { confirmDelete = true }) { Text("このこどもを削除", color = Color(0xFFD03B5C)) }
        }
    }

    if (showAppPicker) {
        AppPickerDialog(
            alreadyAdded = profile.tiles.mapNotNull { it.packageName.takeIf { _ -> it.type == TileType.APP } }.toSet(),
            onDismiss = { showAppPicker = false },
            onAdd = { apps ->
                Store.addTiles(profile.id, apps.map { appTile(it) })
                showAppPicker = false
            },
        )
    }
    if (addingVideo) {
        TileEditDialog(null, onDismiss = { addingVideo = false }) { tile ->
            Store.addTiles(profile.id, listOf(tile))
            addingVideo = false
        }
    }
    if (addingCall) {
        TileEditDialog(null, onDismiss = { addingCall = false }, newType = TileType.CALL) { tile ->
            Store.addTiles(profile.id, listOf(tile))
            addingCall = false
        }
    }
    editing?.let { t ->
        TileEditDialog(t, onDismiss = { editing = null }) { updated ->
            Store.updateProfile(profile.id) { p -> p.copy(tiles = p.tiles.map { if (it.id == updated.id) updated else it }) }
            editing = null
        }
    }
    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("「${profile.name}」を削除しますか？") },
            text = { Text("ボタンの設定も消えます。") },
            confirmButton = { TextButton(onClick = { confirmDelete = false; onDelete() }) { Text("削除") } },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("やめる") } },
        )
    }
}

private fun move(profile: Profile, from: Int, to: Int) = Store.updateProfile(profile.id) { p ->
    val list = p.tiles.toMutableList()
    list.add(to, list.removeAt(from))
    p.copy(tiles = list)
}

@Composable
private fun TileRow(tile: Tile, onUp: (() -> Unit)?, onDown: (() -> Unit)?, onEdit: () -> Unit, onDelete: () -> Unit) {
    val context = LocalContext.current
    val icon = remember(tile.packageName) {
        if (tile.type == TileType.APP && tile.packageName != null) Launcher.icon(context, tile.packageName) else null
    }
    val missing = remember(tile.packageName) {
        tile.type == TileType.APP && tile.packageName != null && !Launcher.isInstalled(context, tile.packageName)
    }
    Row(
        Modifier.fillMaxWidth().padding(vertical = 4.dp).clip(RoundedCornerShape(16.dp))
            .background(Color(0xFFFAF6F8)).padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(44.dp).clip(CircleShape).background(Color.White), contentAlignment = Alignment.Center) {
            if (icon != null) Image(icon, null, Modifier.size(34.dp)) else Text(tile.emoji, fontSize = 24.sp)
        }
        Column(Modifier.weight(1f).padding(start = 12.dp)) {
            Text(tile.label, fontSize = 16.sp, fontWeight = FontWeight.Bold)
            val sub = buildList {
                when (tile.type) {
                    TileType.APP -> add("アプリ")
                    TileType.VIDEO -> add(tile.url?.let { Launcher.detectService(it)?.name } ?: "どうがリンク")
                    TileType.DRAWING -> add("おえかき（内蔵）")
                    TileType.CALL -> add("でんわ（ロック中も使える）")
                    TileType.INSTRUMENT -> add("がっき（内蔵）")
                    TileType.EDUPAD -> add("くるくる思考パッド（内蔵）")
                    TileType.WORDPRO -> add("わーぷろ（内蔵）")
                    TileType.PUZZLE -> add("パズル（内蔵）")
                    TileType.TYPING -> add("タイピング（内蔵）")
                    TileType.MOJI -> add("もじ れんしゅう（内蔵）")
                    TileType.KUKU -> add("くくの ひょう（内蔵）")
                    TileType.MAP -> add("ちず（内蔵）")
                    TileType.TASU -> add("たしざん の ひょう（内蔵）")
                }
                if (tile.isVideo) add("🔒ロック対象")
                if (missing) add("⚠️ アプリが見つかりません")
            }.joinToString(" ・ ")
            Text(sub, fontSize = 12.sp, color = Color.Gray)
        }
        TextButton(onClick = { onUp?.invoke() }, enabled = onUp != null) { Text("▲") }
        TextButton(onClick = { onDown?.invoke() }, enabled = onDown != null) { Text("▼") }
        TextButton(onClick = onEdit) { Text("編集") }
        TextButton(onClick = onDelete) { Text("削除", color = Color(0xFFD03B5C)) }
    }
}
