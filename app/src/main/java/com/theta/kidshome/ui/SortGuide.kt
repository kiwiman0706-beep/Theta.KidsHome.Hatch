package com.theta.kidshome.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
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
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.theta.kidshome.data.AppCategory
import com.theta.kidshome.data.AppConfig
import com.theta.kidshome.data.Categories
import com.theta.kidshome.data.InstalledApp
import com.theta.kidshome.data.Launcher
import com.theta.kidshome.data.Store

/** だれに見せるかの短い説明 */
fun visibleText(config: AppConfig, c: AppCategory): String {
    val names = config.profiles.filter { it.id in c.visibleTo }.map { it.name }
    return when {
        names.isEmpty() -> "🙈 だれにも見せない"
        names.size == config.profiles.size -> "👀 ぜんいんに見せる"
        else -> "👀 " + names.joinToString("・") + " に見せる"
    }
}

/**
 * 仕分けガイド（Theta Home Hatch と同じ流れ）
 * 1. カテゴリーを用意（はじめてのときだけ）
 * 2. アプリが 1 つずつ大きく出るので、振り分け先を押すと次へ
 */
@Composable
fun SortGuideDialog(redoAll: Boolean = false, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val config = Store.config
    var step by remember { mutableIntStateOf(if (config.categories.isEmpty()) 1 else 2) }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(shape = RoundedCornerShape(28.dp), modifier = Modifier.padding(24.dp).width(640.dp).heightIn(max = 800.dp)) {
            Column(Modifier.padding(24.dp)) {
                if (step == 1) CategorySetup(onNext = { step = 2 }, onDismiss = onDismiss)
                else AssignApps(redoAll, onDismiss)
            }
        }
    }
}

@Composable
private fun CategorySetup(onNext: () -> Unit, onDismiss: () -> Unit) {
    val config = Store.config
    var picked by remember { mutableStateOf(Categories.defaults.toSet()) }
    val custom = remember { mutableStateListOf<String>() }
    var newName by remember { mutableStateOf("") }
    Text("1. カテゴリーを用意", fontSize = 22.sp, fontWeight = FontWeight.Bold)
    Text(
        "アプリを入れる箱です。見せるカテゴリーは、子どものホームにタブとして出ます。「おうちのひと」は子どもに見せない箱です。あとから変えられます。",
        fontSize = 14.sp, color = Color.Gray, modifier = Modifier.padding(vertical = 8.dp),
    )
    FlowRow8 {
        Categories.presets.forEach { p ->
            FilterChip(
                selected = p.name in picked,
                onClick = { picked = if (p.name in picked) picked - p.name else picked + p.name },
                label = { Text("${p.emoji} ${p.name}${if (!p.shown) "（見せない）" else ""}", fontSize = 16.sp) },
            )
        }
        custom.forEach { n -> FilterChip(selected = true, onClick = { custom.remove(n) }, label = { Text("⭐ $n") }) }
    }
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 8.dp)) {
        OutlinedTextField(newName, { newName = it }, singleLine = true, label = { Text("じぶんで作る（例：がっこう）") }, modifier = Modifier.weight(1f))
        TextButton(enabled = newName.isNotBlank(), onClick = { custom += newName.trim(); newName = "" }) { Text("足す") }
    }
    Row(Modifier.fillMaxWidth().padding(top = 16.dp), horizontalArrangement = Arrangement.End) {
        TextButton(onClick = onDismiss) { Text("やめる") }
        Button(enabled = picked.isNotEmpty() || custom.isNotEmpty(), onClick = {
            val all = config.profiles.map { it.id }.toSet()
            val cats = Categories.presets.filter { it.name in picked }.map { Categories.fromPreset(it, all) } +
                custom.map { AppCategory(name = it, emoji = "⭐", visibleTo = all) }
            Store.update { it.copy(categories = it.categories + cats) }
            onNext()
        }) { Text("つぎへ（振り分け）") }
    }
}

@Composable
private fun ColumnScope.AssignApps(redoAll: Boolean, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val queue = remember {
        val all = Launcher.listApps(context)
        if (redoAll) all else all.filter { it.packageName !in Store.config.appCategory }
    }
    var index by remember { mutableIntStateOf(0) }
    val history = remember { mutableStateListOf<Pair<Int, String?>>() }
    var adding by remember { mutableStateOf(false) }
    var newName by remember { mutableStateOf("") }
    val config = Store.config

    Text("2. 振り分け", fontSize = 22.sp, fontWeight = FontWeight.Bold)
    if (index >= queue.size) {
        Text(if (queue.isEmpty()) "未分類のアプリはありません 🎉" else "ぜんぶ振り分けました 🎉", fontSize = 18.sp, modifier = Modifier.padding(vertical = 24.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) { Button(onClick = onDismiss) { Text("おわる") } }
        return
    }
    val app: InstalledApp = queue[index]
    val suggestion = remember(app.packageName) { Categories.suggest(context, app.packageName) }
    fun assign(categoryId: String?) {
        history += index to config.appCategory[app.packageName]
        Store.update { c ->
            c.copy(appCategory = if (categoryId == null) c.appCategory - app.packageName else c.appCategory + (app.packageName to categoryId))
        }
        index++
    }

    Text("${index + 1} / ${queue.size}", color = Color.Gray)
    Column(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState())) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 16.dp)) {
            val icon = remember(app.packageName) { Launcher.icon(context, app.packageName) }
            if (icon != null) Image(icon, null, Modifier.size(72.dp))
            Column(Modifier.padding(start = 16.dp)) {
                Text(app.label, fontSize = 24.sp, fontWeight = FontWeight.Bold)
                Text(app.packageName, fontSize = 12.sp, color = Color.Gray)
            }
        }
        Text("どこに入れる？", fontSize = 14.sp, color = Color.Gray)
        Spacer(Modifier.height(6.dp))
        config.categories.forEach { cat ->
            val isSuggested = cat.name == suggestion
            OutlinedButton(
                onClick = { assign(cat.id) },
                border = BorderStroke(if (isSuggested) 3.dp else 1.dp, if (isSuggested) MaterialTheme.colorScheme.primary else Color.LightGray),
                modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp),
            ) {
                Text("${cat.emoji} ${cat.name}", fontSize = 18.sp, modifier = Modifier.weight(1f))
                Text(visibleText(config, cat) + if (isSuggested) "　おすすめ" else "", fontSize = 12.sp, color = Color.Gray)
            }
        }
        OutlinedButton(onClick = { assign(Categories.HIDDEN) }, modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
            Text("🙈 かくす（どの子にも出さない）", fontSize = 18.sp, modifier = Modifier.weight(1f))
        }
        OutlinedButton(
            onClick = { Launcher.uninstall(context, app.packageName); index++ },
            border = BorderStroke(1.dp, Color(0xFFD03B5C)),
            modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp),
        ) {
            val sys = remember(app.packageName) { Launcher.isSystem(context, app.packageName) }
            Text(if (sys) "🗑 いらない（もとから の アプリ：アプリ情報 で 無効にする）" else "🗑 アンインストール", fontSize = 18.sp, color = Color(0xFFD03B5C), modifier = Modifier.weight(1f))
        }
        if (adding) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(newName, { newName = it }, singleLine = true, label = { Text("新しいカテゴリー") }, modifier = Modifier.weight(1f))
                TextButton(enabled = newName.isNotBlank(), onClick = {
                    val cat = AppCategory(name = newName.trim(), emoji = "⭐", visibleTo = config.profiles.map { it.id }.toSet())
                    Store.update { it.copy(categories = it.categories + cat) }
                    newName = ""
                    adding = false
                    assign(cat.id)
                }) { Text("作って入れる") }
            }
        } else {
            TextButton(onClick = { adding = true }) { Text("＋ 新しいカテゴリー") }
        }
    }
    Row(Modifier.fillMaxWidth().padding(top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        TextButton(enabled = history.isNotEmpty(), onClick = {
            val (i, prev) = history.removeAt(history.lastIndex)
            val pkg = queue[i].packageName
            Store.update { c -> c.copy(appCategory = if (prev == null) c.appCategory - pkg else c.appCategory + (pkg to prev)) }
            index = i
        }) { Text("もどす") }
        TextButton(onClick = { index++ }) { Text("あとで") }
        Spacer(Modifier.weight(1f))
        Button(onClick = onDismiss) { Text("ここで終わる") }
    }
}

/** 1 つのアプリのカテゴリーを変える */
@Composable
fun AppCategoryDialog(app: InstalledApp, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val config = Store.config
    val current = config.appCategory[app.packageName]
    fun set(id: String?) {
        Store.update { c -> c.copy(appCategory = if (id == null) c.appCategory - app.packageName else c.appCategory + (app.packageName to id)) }
        onDismiss()
    }
    DialogFrame(title = app.label, onDismiss = onDismiss, actions = { TextButton(onClick = onDismiss) { Text("とじる") } }) {
        Button(onClick = { Launcher.launchPackage(context, app.packageName); onDismiss() }, modifier = Modifier.fillMaxWidth()) { Text("ひらく") }
        OutlinedButton(onClick = { Launcher.uninstall(context, app.packageName); onDismiss() }, modifier = Modifier.fillMaxWidth()) {
            Text(if (Launcher.isSystem(context, app.packageName)) "🗑 無効にする（アプリ情報）" else "🗑 アンインストール", color = Color(0xFFD03B5C))
        }
        Spacer(Modifier.height(12.dp))
        Text("カテゴリー", fontSize = 13.sp, color = Color.Gray)
        config.categories.forEach { cat ->
            FilterChip(
                selected = current == cat.id, onClick = { set(cat.id) },
                label = { Text("${cat.emoji} ${cat.name}　${visibleText(config, cat)}") },
                modifier = Modifier.fillMaxWidth(),
            )
        }
        FilterChip(selected = current == Categories.HIDDEN, onClick = { set(Categories.HIDDEN) }, label = { Text("🙈 かくす") }, modifier = Modifier.fillMaxWidth())
        FilterChip(selected = current == null, onClick = { set(null) }, label = { Text("未分類（出さない）") }, modifier = Modifier.fillMaxWidth())
    }
}

/** せってい：カテゴリーごとに、だれに見せるか */
@Composable
fun CategorySection(config: AppConfig) {
    var guide by remember { mutableStateOf(false) }
    var redo by remember { mutableStateOf(false) }
    var newName by remember { mutableStateOf("") }
    val unsorted = unsortedCount(config)
    Section("🗂 アプリの仕分け（見せる・かくす）") {
        Text(
            "入っているアプリをカテゴリーに振り分けて、カテゴリーごとに見せる子を決めます。見せるカテゴリーは子どものホームにタブで出ます。" +
                "未分類と「かくす」のアプリは出ません。",
            fontSize = 14.sp,
        )
        Spacer(Modifier.height(8.dp))
        FlowRow8 {
            Button(onClick = { redo = false; guide = true }) { Text(if (unsorted > 0) "仕分けガイド（未分類 $unsorted）" else "仕分けガイド") }
            OutlinedButton(onClick = { redo = true; guide = true }) { Text("ぜんぶ振り分け直す") }
        }
        config.categories.forEachIndexed { i, cat ->
            val count = config.appCategory.values.count { it == cat.id }
            Surface(shape = RoundedCornerShape(16.dp), color = Color(0xFFFAF6F8), modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
                Column(Modifier.padding(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("${cat.emoji} ${cat.name}", fontSize = 18.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                        Text("${count}こ", color = Color.Gray)
                        TextButton(enabled = i > 0, onClick = {
                            Store.update { c -> c.copy(categories = c.categories.toMutableList().apply { add(i - 1, removeAt(i)) }) }
                        }) { Text("▲") }
                        TextButton(onClick = {
                            Store.update { c -> c.copy(categories = c.categories - cat, appCategory = c.appCategory.filterValues { it != cat.id }) }
                        }) { Text("削除", color = Color(0xFFD03B5C)) }
                    }
                    FlowRow8 {
                        config.profiles.forEach { p ->
                            val on = p.id in cat.visibleTo
                            FilterChip(
                                selected = on,
                                onClick = {
                                    Store.update { c ->
                                        c.copy(categories = c.categories.map {
                                            if (it.id == cat.id) it.copy(visibleTo = if (on) it.visibleTo - p.id else it.visibleTo + p.id) else it
                                        })
                                    }
                                },
                                label = { Text("${themeById(p.themeId).mascot} ${p.name}に見せる") },
                            )
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("どうが扱い", fontSize = 13.sp)
                            Switch(checked = cat.isVideo, onCheckedChange = { v ->
                                Store.update { c -> c.copy(categories = c.categories.map { if (it.id == cat.id) it.copy(isVideo = v) else it }) }
                            })
                        }
                    }
                }
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 8.dp)) {
            OutlinedTextField(newName, { newName = it }, singleLine = true, label = { Text("カテゴリーを足す") }, modifier = Modifier.weight(1f))
            TextButton(enabled = newName.isNotBlank(), onClick = {
                Store.update { it.copy(categories = it.categories + AppCategory(name = newName.trim(), emoji = "⭐", visibleTo = it.profiles.map { p -> p.id }.toSet())) }
                newName = ""
            }) { Text("足す") }
        }
    }
    if (guide) SortGuideDialog(redo) { guide = false }
}

@Composable
fun unsortedCount(config: AppConfig): Int {
    val context = LocalContext.current
    val apps = remember(config.appCategory) { Launcher.listApps(context) }
    return apps.count { it.packageName !in config.appCategory }
}
