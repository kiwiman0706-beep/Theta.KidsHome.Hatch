package com.theta.kidshome.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
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
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.theta.kidshome.data.InstalledApp
import com.theta.kidshome.data.Launcher
import com.theta.kidshome.data.Tile
import com.theta.kidshome.data.TileType

val TileEmojis = listOf(
    "🎬", "📺", "▶️", "🐷", "🚂", "🚒", "🚓", "👸", "🧚", "🦸", "🐶", "🐱",
    "🐻", "🐼", "🐰", "🦁", "🐸", "🦖", "🐠", "🐣", "🦄", "🌈", "🎵", "🚀",
    "🍭", "🎈", "🧸", "🏰", "⭐", "💗", "📚", "✏️", "🔢", "🔤", "🧩", "🎨",
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun EmojiPicker(selected: String, onSelect: (String) -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        TileEmojis.forEach { e ->
            Box(
                Modifier.size(44.dp).clip(CircleShape)
                    .background(if (e == selected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent)
                    .clickable { onSelect(e) },
                contentAlignment = Alignment.Center,
            ) { Text(e, fontSize = 24.sp) }
        }
    }
}

/** 動画リンク(と、タイルの名前・絵文字)の入力欄。共有からの登録画面でも使う */
@Composable
fun VideoFields(
    url: String, onUrl: (String) -> Unit,
    label: String, onLabel: (String) -> Unit,
    emoji: String, onEmoji: (String) -> Unit,
) {
    val service = Launcher.detectService(url)
    OutlinedTextField(
        value = url, onValueChange = onUrl, singleLine = true,
        label = { Text("作品のURL（アプリの「共有」→「リンクをコピー」）") },
        modifier = Modifier.fillMaxWidth(),
    )
    Text(
        when {
            url.isBlank() -> "例: https://www.netflix.com/title/80117470"
            service != null -> "✅ ${service.name} アプリで開きます"
            else -> "ℹ️ 対応アプリを自動で選びます（ブラウザで開くこともあります）"
        },
        fontSize = 13.sp, color = Color.Gray, modifier = Modifier.padding(top = 4.dp, bottom = 8.dp),
    )
    OutlinedTextField(
        value = label, onValueChange = onLabel, singleLine = true,
        label = { Text("ボタンの名前（ひらがながおすすめ）") },
        modifier = Modifier.fillMaxWidth(),
    )
    Spacer(Modifier.height(8.dp))
    Text("えもじ", fontSize = 13.sp, color = Color.Gray)
    EmojiPicker(emoji, onEmoji)
}

fun newVideoTile(url: String, label: String, emoji: String) = Tile(
    type = TileType.VIDEO,
    label = label.ifBlank { "どうが" },
    url = url.trim(),
    packageName = Launcher.detectService(url)?.packageName,
    emoji = emoji,
    isVideo = true,
)

@Composable
fun DialogFrame(title: String, onDismiss: () -> Unit, actions: @Composable () -> Unit, content: @Composable () -> Unit) {
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(
            shape = RoundedCornerShape(28.dp),
            modifier = Modifier.padding(24.dp).width(560.dp).heightIn(max = 720.dp),
        ) {
            Column(Modifier.padding(24.dp)) {
                Text(title, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(12.dp))
                Column(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState())) { content() }
                Row(Modifier.fillMaxWidth().padding(top = 12.dp), horizontalArrangement = Arrangement.End) { actions() }
            }
        }
    }
}

/** 動画の追加・タイルの編集 */
@Composable
fun TileEditDialog(initial: Tile?, onDismiss: () -> Unit, onSave: (Tile) -> Unit) {
    val isVideoTile = initial == null || initial.type == TileType.VIDEO
    var url by remember { mutableStateOf(initial?.url ?: "") }
    var label by remember { mutableStateOf(initial?.label ?: "") }
    var emoji by remember { mutableStateOf(initial?.emoji ?: "🎬") }
    var isVideo by remember { mutableStateOf(initial?.isVideo ?: true) }

    DialogFrame(
        title = if (initial == null) "どうがを追加" else "ボタンを編集",
        onDismiss = onDismiss,
        actions = {
            TextButton(onClick = onDismiss) { Text("キャンセル") }
            TextButton(
                enabled = !isVideoTile || url.isNotBlank(),
                onClick = {
                    val tile = if (isVideoTile) {
                        newVideoTile(url, label, emoji).let { if (initial != null) it.copy(id = initial.id) else it }
                    } else {
                        initial!!.copy(label = label.ifBlank { initial.label }, emoji = emoji, isVideo = isVideo)
                    }
                    onSave(tile)
                },
            ) { Text("保存") }
        },
    ) {
        if (isVideoTile) {
            VideoFields(url, { url = it }, label, { label = it }, emoji, { emoji = it })
        } else {
            OutlinedTextField(
                value = label, onValueChange = { label = it }, singleLine = true,
                label = { Text("ボタンの名前") }, modifier = Modifier.fillMaxWidth(),
            )
            if (initial?.type == TileType.APP) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 12.dp)) {
                    Column(Modifier.weight(1f)) {
                        Text("どうが として扱う", fontWeight = FontWeight.Bold)
                        Text("オンにすると「どうがロック」中は使えなくなります", fontSize = 13.sp, color = Color.Gray)
                    }
                    Switch(checked = isVideo, onCheckedChange = { isVideo = it })
                }
            } else {
                Spacer(Modifier.height(8.dp))
                Text("えもじ", fontSize = 13.sp, color = Color.Gray)
                EmojiPicker(emoji) { emoji = it }
            }
        }
    }
}

/** インストール済みアプリから選んで追加 */
@Composable
fun AppPickerDialog(alreadyAdded: Set<String>, onDismiss: () -> Unit, onAdd: (List<InstalledApp>) -> Unit) {
    val context = LocalContext.current
    val apps = remember { Launcher.listApps(context) }
    var query by remember { mutableStateOf("") }
    var checked by remember { mutableStateOf(setOf<String>()) }
    val shown = apps.filter { query.isBlank() || it.label.contains(query, ignoreCase = true) }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(shape = RoundedCornerShape(28.dp), modifier = Modifier.padding(24.dp).width(560.dp).heightIn(max = 760.dp)) {
            Column(Modifier.padding(24.dp)) {
                Text("アプリを追加", fontSize = 22.sp, fontWeight = FontWeight.Bold)
                OutlinedTextField(
                    value = query, onValueChange = { query = it }, singleLine = true,
                    label = { Text("さがす") }, modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                )
                LazyColumn(Modifier.weight(1f, fill = false)) {
                    items(shown, key = { it.packageName }) { app ->
                        val added = app.packageName in alreadyAdded
                        val isChecked = app.packageName in checked
                        Row(
                            Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
                                .clickable(enabled = !added) {
                                    checked = if (isChecked) checked - app.packageName else checked + app.packageName
                                }
                                .padding(vertical = 6.dp, horizontal = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            val icon = remember(app.packageName) { Launcher.icon(context, app.packageName) }
                            if (icon != null) Image(icon, null, Modifier.size(40.dp)) else Spacer(Modifier.size(40.dp))
                            Column(Modifier.weight(1f).padding(start = 12.dp)) {
                                Text(app.label, fontSize = 16.sp)
                                if (app.packageName in Launcher.videoPackages) {
                                    Text("どうがアプリ", fontSize = 12.sp, color = MaterialTheme.colorScheme.primary)
                                }
                            }
                            if (added) Text("追加済み", fontSize = 13.sp, color = Color.Gray)
                            else Checkbox(checked = isChecked, onCheckedChange = null)
                        }
                    }
                }
                Row(Modifier.fillMaxWidth().padding(top = 12.dp), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onDismiss) { Text("キャンセル") }
                    TextButton(
                        enabled = checked.isNotEmpty(),
                        onClick = { onAdd(apps.filter { it.packageName in checked }) },
                    ) { Text("${checked.size}こ 追加") }
                }
            }
        }
    }
}

fun appTile(app: InstalledApp) = Tile(
    type = TileType.APP,
    label = app.label,
    packageName = app.packageName,
    emoji = "⭐",
    isVideo = app.packageName in Launcher.videoPackages,
)

/** テーマ選択(キャラクター一覧) */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ThemePicker(selectedId: String, onSelect: (String) -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        KidThemes.forEach { t ->
            val selected = t.id == selectedId
            Column(
                Modifier.clip(RoundedCornerShape(20.dp))
                    .background(androidx.compose.ui.graphics.Brush.verticalGradient(listOf(t.bgTop, t.bgBottom)))
                    .border(if (selected) 4.dp else 1.dp, if (selected) t.accent else Color(0x22000000), RoundedCornerShape(20.dp))
                    .clickable { onSelect(t.id) }
                    .padding(horizontal = 14.dp, vertical = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(t.mascot, fontSize = 32.sp)
                Text(t.name, fontSize = 13.sp, color = t.text, fontWeight = FontWeight.Bold)
            }
        }
    }
}
