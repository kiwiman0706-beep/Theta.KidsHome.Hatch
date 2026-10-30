package com.theta.kidshome.ui

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.theta.kidshome.data.AppConfig
import com.theta.kidshome.data.Gemini
import com.theta.kidshome.data.StampItem
import com.theta.kidshome.data.StampLibrary
import com.theta.kidshome.data.Sticker
import com.theta.kidshome.data.Store
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** すけたところが分かるように、こうし もよう を下に敷く */
@Composable
fun Checker(modifier: Modifier) {
    Canvas(modifier) {
        val s = 14.dp.toPx()
        var y = 0f
        var row = 0
        while (y < size.height) {
            var x = if (row % 2 == 0) 0f else s
            while (x < size.width) { drawRect(Color(0xFFEDEDED), Offset(x, y), Size(s, s)); x += s * 2 }
            y += s; row++
        }
    }
}

/**
 * シールを つくる：写真・画像を えらぶ → ✨ Gemini で型どり か ✂️ まわりの色を消す → ライブラリへ。
 */
@Composable
fun StampMakerDialog(theme: KidTheme, onDismiss: () -> Unit, onAdded: (StampItem) -> Unit = {}) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var original by remember { mutableStateOf<Bitmap?>(null) }
    var cut by remember { mutableStateOf<Bitmap?>(null) }
    var border by remember { mutableStateOf(true) }
    var name by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf<String?>(null) }
    val result = remember(cut, border) { cut?.let { StampLibrary.finish(it, border) } }
    val pick = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) scope.launch {
            original = withContext(Dispatchers.IO) { loadImage(context, uri) }
            cut = null
        }
    }
    fun process(label: String, f: suspend (Bitmap) -> Bitmap) {
        val src = original ?: return
        busy = label
        scope.launch {
            runCatching { withContext(Dispatchers.Default) { f(src) } }
                .onSuccess { cut = it }
                .onFailure { Toast.makeText(context, it.message ?: "できなかったよ", Toast.LENGTH_LONG).show() }
            busy = null
        }
    }

    Dialog(onDismissRequest = onDismiss) {
        RetroWindow(theme, Modifier.width(560.dp).heightIn(max = 760.dp), title = "シールを つくる", titleColor = theme.titleBars[1]) {
            Column(Modifier.padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Preview(theme, original, "1. しゃしん")
                    Preview(theme, result, "2. シール", checker = true)
                }
                busy?.let { DotText("$it…", theme, 16, modifier = Modifier.padding(top = 8.dp)) }
                Spacer(Modifier.height(10.dp))
                RetroButton("📷 しゃしん・がぞう を えらぶ", theme, theme.titleBars[0], fontSize = 17) {
                    pick.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                }
                if (original != null) {
                    Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (Gemini.apiKey(context).isNotBlank()) {
                            RetroButton("✨ かたどり", theme, theme.titleBars[2], fontSize = 16) {
                                process("かたどり ちゅう") { StampLibrary.chromaKey(Gemini.cutout(context, it)) }
                            }
                        }
                        RetroButton("✂️ まわりを けす", theme, theme.titleBars[3], fontSize = 16) {
                            process("きりぬき ちゅう") { StampLibrary.removeBackground(it) }
                        }
                        RetroButton("⬜ そのまま", theme, Color.White, fontSize = 16) { process("じゅんび ちゅう") { it } }
                    }
                }
                if (result != null) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 8.dp)) {
                        RetroIconButton("🤍", theme, border, 44.dp) { border = !border }
                        DotText(if (border) "しろい ふち あり" else "ふち なし", theme, 14)
                    }
                    OutlinedTextField(name, { name = it }, singleLine = true, label = { Text("なまえ") }, modifier = Modifier.fillMaxWidth().padding(top = 4.dp))
                    RetroButton("📥 ライブラリに いれる", theme, theme.titleBars[0], modifier = Modifier.padding(top = 10.dp)) {
                        val item = StampLibrary.add(result, name)
                        Toast.makeText(context, "シールが できたよ！", Toast.LENGTH_SHORT).show()
                        onAdded(item)
                        onDismiss()
                    }
                }
                if (Gemini.apiKey(context).isBlank()) {
                    DotText("✨ かたどり は おうちのひと が Gemini の キーを いれると つかえるよ", theme, 12, theme.outline.copy(alpha = 0.6f), modifier = Modifier.padding(top = 8.dp))
                }
            }
        }
    }
}

@Composable
private fun Preview(theme: KidTheme, b: Bitmap?, label: String, checker: Boolean = false) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        DotText(label, theme, 14)
        Box(
            Modifier.size(220.dp).clip(RoundedCornerShape(12.dp)).background(Color.White).border(3.dp, theme.outline, RoundedCornerShape(12.dp)),
            contentAlignment = Alignment.Center,
        ) {
            if (checker) Checker(Modifier.fillMaxSize())
            if (b != null) Image(b.asImageBitmap(), null, Modifier.fillMaxSize().padding(8.dp), contentScale = ContentScale.Fit)
        }
    }
}

/** おえかき などで使う、ライブラリの シール 1 まい */
@Composable
fun StampThumb(theme: KidTheme, id: String, selected: Boolean, size: androidx.compose.ui.unit.Dp = 50.dp, onClick: () -> Unit) {
    val img = remember(id, StampLibrary.version) { StampLibrary.bitmap(id)?.asImageBitmap() }
    Box(
        Modifier.padding(5.dp).size(size).clip(RoundedCornerShape(14.dp))
            .background(if (selected) theme.titleBars[0] else Color.White)
            .border(if (selected) 4.dp else 3.dp, theme.outline, RoundedCornerShape(14.dp))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { if (img != null) Image(img, null, Modifier.fillMaxSize().padding(4.dp), contentScale = ContentScale.Fit) }
}

/** ホームに シールを うかべる（いまの子） */
fun floatOnHome(id: String) = Store.addSticker(Sticker(stamp = id))

// ---------------------------------------------------------------- おうちのひと

@Composable
fun StampLibrarySection(config: AppConfig) {
    val context = LocalContext.current
    var making by remember { mutableStateOf(false) }
    val items = remember(StampLibrary.version) { StampLibrary.list() }
    val import = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            val n = runCatching { StampLibrary.importZip(context, uri) }.getOrDefault(-1)
            Toast.makeText(context, if (n >= 0) "${n}まい 読み込みました" else "読み込めませんでした", Toast.LENGTH_SHORT).show()
        }
    }
    Section("🌟 スタンプ・シール ライブラリ") {
        Text(
            "写真や画像からシールを作って、おえかき のスタンプや、ホームに うかべる シールに使います。" +
                "✨ かたどり は Gemini で主役だけを切り抜きます（写真は Google に送られます）。✂️ は白い紙などの まわりの色を消します。" +
                "市販のシールの写真から作ったものは、家の中だけで使ってください（書き出した ZIP を人に配らない）。",
            fontSize = 14.sp,
        )
        FlowRow8 {
            Button(onClick = { making = true }) { Text("＋ シールを つくる") }
            OutlinedButton(enabled = items.isNotEmpty(), onClick = { exportStamps(context) }) { Text("ZIP で書き出す") }
            OutlinedButton(onClick = { import.launch(arrayOf("application/zip", "application/octet-stream")) }) { Text("ZIP を読み込む") }
        }
        if (items.isNotEmpty()) {
            LazyVerticalGrid(GridCells.Adaptive(120.dp), Modifier.heightIn(max = 460.dp).padding(top = 8.dp)) {
                items(items, key = { it.id }) { s ->
                    val img = remember(s.id) { StampLibrary.bitmap(s.id)?.asImageBitmap() }
                    Column(Modifier.padding(4.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Box(Modifier.size(100.dp).clip(RoundedCornerShape(10.dp)).border(1.dp, Color.LightGray, RoundedCornerShape(10.dp))) {
                            Checker(Modifier.fillMaxSize())
                            if (img != null) Image(img, null, Modifier.fillMaxSize().padding(4.dp), contentScale = ContentScale.Fit)
                        }
                        Text(s.name, fontSize = 12.sp, maxLines = 1)
                        Row {
                            TextButton(onClick = { floatOnHome(s.id); Toast.makeText(context, "${config.currentProfile.name}の ホームに うかべました", Toast.LENGTH_SHORT).show() }) { Text("🏠") }
                            TextButton(onClick = { StampLibrary.delete(s.id) }) { Text("削除", color = Color(0xFFD03B5C)) }
                        }
                    }
                }
            }
        }
    }
    if (making) StampMakerDialog(themeById(config.currentProfile.themeId), onDismiss = { making = false })
}

private fun exportStamps(context: Context) {
    runCatching {
        val dir = java.io.File(context.cacheDir, "export").apply { mkdirs() }
        val file = java.io.File(dir, "シール.zip")
        StampLibrary.exportZip(file)
        val uri = androidx.core.content.FileProvider.getUriForFile(context, "${context.packageName}.files", file)
        val send = Intent(Intent.ACTION_SEND).setType("application/zip").putExtra(Intent.EXTRA_STREAM, uri).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        context.startActivity(Intent.createChooser(send, "書き出す").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }.onFailure { Toast.makeText(context, "書き出せませんでした", Toast.LENGTH_SHORT).show() }
}

private fun loadImage(context: Context, uri: Uri): Bitmap? = runCatching {
    ImageDecoder.decodeBitmap(ImageDecoder.createSource(context.contentResolver, uri)) { d, info, _ ->
        d.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
        val s = 1400f / maxOf(info.size.width, info.size.height)
        if (s < 1f) d.setTargetSize((info.size.width * s).toInt(), (info.size.height * s).toInt())
    }
}.getOrNull()
