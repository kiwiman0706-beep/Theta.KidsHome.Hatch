package com.theta.kidshome.ui

import android.content.Intent
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
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
    val config = Store.config
    var editingProfileId by remember { mutableStateOf(config.currentProfileId) }
    val profile = config.profiles.firstOrNull { it.id == editingProfileId } ?: config.profiles.first()
    var changePin by remember { mutableStateOf(false) }
    val context = LocalContext.current

    ParentTheme {
        Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
            Column(
                Modifier.fillMaxSize()
                    .windowInsetsPadding(WindowInsets.safeDrawing)
                    .verticalScroll(rememberScrollState())
                    .padding(20.dp)
                    .widthIn(max = 900.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("🔧 おうちのひと メニュー", fontSize = 26.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.weight(1f))
                    Button(onClick = onClose) { Text("ホームにもどる") }
                }
                Spacer(Modifier.height(16.dp))

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

                Section("⚙️ そのほか") {
                    FlowRow8 {
                        OutlinedButton(onClick = { changePin = true }) { Text("暗証番号を変える") }
                        OutlinedButton(onClick = {
                            context.startActivity(Intent(Settings.ACTION_HOME_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                        }) { Text("ホームアプリを切り替える") }
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
        if (changePin) PinGate(PinMode.CHANGE, onSuccess = { changePin = false }, onDismiss = { changePin = false })
    }
}

@Composable
private fun Section(title: String, content: @Composable () -> Unit) {
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
private fun FlowRow8(content: @Composable () -> Unit) {
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
        val base = if (it.isVideoLocked()) it.videoLockUntil else System.currentTimeMillis()
        it.copy(videoLockUntil = base + minutes * 60_000L)
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
                Store.update { it.copy(videoLockUntil = morning) }
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

    Spacer(Modifier.height(16.dp))
    Text("ホームのボタン（上から順に並びます）", fontSize = 13.sp, color = Color.Gray)
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
