package com.theta.kidshome.ui

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.FileProvider
import com.theta.kidshome.data.Gemini
import com.theta.kidshome.data.ShopItems
import com.theta.kidshome.tepra.Tepra
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/** おやメニュー：おもちゃ の しょうひん に はる QR ラベル */
@Composable
fun ShopLabelSection() {
    val context = LocalContext.current
    var open by remember { mutableStateOf(false) }
    var edge by remember { mutableStateOf(context.getSharedPreferences("shop", Context.MODE_PRIVATE).getString("cameraEdge", "auto")!!) }
    LaunchedEffect(Unit) { ShopItems.load(context) }
    Section("🏷 おみせ の しょうひん ラベル（QR）") {
        Text(
            "おもちゃ の 食材 など を カメラ で とると、Gemini が 名前 と ねだん（10 円 きざみ）を 考えます。OK で ラベル が できて、テプラ SR5900P で 印刷（または 共有）。" +
                "レジ の「📷 ピッ」で インカメラ に かざすか、外付け の 2 次元 バーコードリーダー で 読むと、名前 と ねだん が 入ります。",
            fontSize = 13.sp,
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            Button(onClick = { open = true }) { Text("🏷 ラベル を つくる（${ShopItems.items.size}）") }
            Spacer(Modifier.width(12.dp))
            Text(if (Tepra.available) "🖨 テプラ：${Tepra.printerName(context) ?: "まだ えらんで いません"}" else "この 版 は テプラ なし（共有 で 印刷）", fontSize = 13.sp)
        }
        Text("インカメラ の いち（レジ の「ここ で ピッ」）", fontSize = 13.sp)
        Row {
            listOf("auto" to "じどう", "top" to "うえ", "bottom" to "した").forEach { (k, l) ->
                FilterChip(edge == k, { edge = k; context.getSharedPreferences("shop", Context.MODE_PRIVATE).edit().putString("cameraEdge", k).apply() }, label = { Text(l) }, modifier = Modifier.padding(end = 6.dp))
            }
        }
    }
    if (open) ShopLabelMaker { open = false }
}

/** 共有（テプラ アプリ・プリンター など）で ラベル の 絵 を わたす */
private fun share(context: Context, bmp: Bitmap) {
    val dir = File(context.cacheDir, "export").apply { mkdirs() }
    val f = File(dir, "label.png")
    f.outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
    val uri = FileProvider.getUriForFile(context, context.packageName + ".files", f)
    context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType("image/png").putExtra(Intent.EXTRA_STREAM, uri).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION), "ラベル を 印刷"))
}

/** ぜんぶ の ラベル を 1 まい に ならべる（シール用紙 など） */
private fun sheet(items: List<ShopItems.Item>): Bitmap {
    val labels = items.map { ShopItems.label(it, 180) }
    val w = 1240; val gap = 30
    var x = gap; var y = gap; var rowH = 0
    val pos = labels.map { b ->
        if (x + b.width > w - gap) { x = gap; y += rowH + gap; rowH = 0 }
        val p = x to y; x += b.width + gap; rowH = maxOf(rowH, b.height); p
    }
    val out = Bitmap.createBitmap(w, maxOf(y + rowH + gap, 200), Bitmap.Config.ARGB_8888)
    Canvas(out).apply { drawColor(android.graphics.Color.WHITE); labels.forEachIndexed { i, b -> drawBitmap(b, pos[i].first.toFloat(), pos[i].second.toFloat(), null) } }
    return out
}

@Composable
private fun ShopLabelMaker(onClose: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var photo by remember { mutableStateOf<Bitmap?>(null) }
    var busy by remember { mutableStateOf<String?>(null) }
    var name by remember { mutableStateOf("") }
    var price by remember { mutableStateOf("") }
    var shop by remember { mutableStateOf("other") }
    var editing by remember { mutableStateOf<ShopItems.Item?>(null) }
    var status by remember { mutableStateOf<String?>(null) }
    var autoPrint by remember { mutableStateOf(Tepra.available && Tepra.printer(context) != null) }
    var searching by remember { mutableStateOf(false) }
    val found = remember { mutableStateListOf<Map<String, String>>() }

    fun printOrShare(item: ShopItems.Item) {
        val printer = Tepra.printer(context)
        if (Tepra.available && printer != null) {
            status = "🖨 ${item.name}：テプラ へ…"
            Tepra.impl.print(context, printer, 1, { h, _ -> ShopItems.label(item, h) }) { m, _, _ -> scope.launch { status = "🖨 ${item.name}：$m" } }
        } else share(context, ShopItems.label(item, 240))
    }

    val camera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicturePreview()) { bmp ->
        if (bmp == null) return@rememberLauncherForActivityResult
        photo = bmp; editing = null; name = ""; price = ""; shop = "other"
        if (Gemini.apiKey(context).isBlank()) { status = "Gemini の キー が ない ので 名前 と ねだん を 入れて ください"; return@rememberLauncherForActivityResult }
        busy = "🤔 なに か な…"
        scope.launch {
            val r = runCatching { ShopItems.recognize(context, bmp) }
            busy = null
            r.onSuccess { name = it.name; price = it.price.toString(); shop = it.shop; status = null }
                .onFailure { status = "わかりませんでした（${it.message}）。入れて ください" }
        }
    }

    fun ok(again: Boolean) {
        val p = price.filter { it.isDigit() }.toIntOrNull() ?: return
        if (name.isBlank()) return
        val item = editing?.let { e -> e.copy(name = name.trim(), price = p, shop = shop).also { ShopItems.update(context, it) } }
            ?: ShopItems.add(context, name, p, shop, photo)
        if (autoPrint || !Tepra.available) printOrShare(item)
        photo = null; editing = null; name = ""; price = ""
        if (again) camera.launch(null)
    }

    DisposableEffect(Unit) { onDispose { Tepra.impl.stopDiscover() } }

    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(Modifier.fillMaxSize().background(Color(0xFFFFF8F0)).padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("🏷 しょうひん ラベル", fontSize = 24.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                if (Tepra.available) {
                    Text("🖨 ${Tepra.printerName(context) ?: "テプラ 未設定"}", fontSize = 14.sp)
                    TextButton(onClick = {
                        found.clear(); searching = true
                        if (!Tepra.impl.startDiscover(context) { m -> scope.launch { if (found.none { it["host"] == m["host"] }) found += m } }) status = "さがせません（Wi-Fi を たしかめて）"
                    }) { Text("さがす") }
                    Text("OK で 印刷", fontSize = 13.sp); Switch(autoPrint, { autoPrint = it })
                }
                TextButton(onClick = { if (ShopItems.items.isNotEmpty()) share(context, sheet(ShopItems.items)) }) { Text("📤 ぜんぶ を 1 まい に") }
                TextButton(onClick = onClose) { Text("とじる") }
            }
            if (searching) Row(Modifier.horizontalScroll(rememberScrollState()), verticalAlignment = Alignment.CenterVertically) {
                Text(if (found.isEmpty()) "さがして います…（おなじ Wi-Fi に SR5900P）" else "えらんで ください：", fontSize = 13.sp)
                found.forEach { m ->
                    OutlinedButton(onClick = { Tepra.savePrinter(context, m); Tepra.impl.stopDiscover(); searching = false; autoPrint = true; status = "🖨 ${m["name"]} を つかいます" }, modifier = Modifier.padding(start = 6.dp)) {
                        Text("${m["name"]}  ${m["host"]}")
                    }
                }
            }
            status?.let { Text(it, fontSize = 14.sp, color = Color(0xFF7A3E55)) }
            Spacer(Modifier.height(8.dp))
            Row(Modifier.weight(1f)) {
                // とる・なおす
                Column(Modifier.width(420.dp).padding(end = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { camera.launch(null) }, modifier = Modifier.fillMaxWidth().height(64.dp)) { Text("📷 しょうひん を とる", fontSize = 20.sp) }
                    if (photo != null || editing != null) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            val th by produceState<ImageBitmap?>(photo?.asImageBitmap(), editing) {
                                editing?.let { e -> value = withContext(Dispatchers.IO) { BitmapFactory.decodeFile(ShopItems.photo(context, e.id).path)?.asImageBitmap() } }
                            }
                            th?.let { Image(it, null, Modifier.size(120.dp).clip(RoundedCornerShape(12.dp)), contentScale = ContentScale.Crop) }
                            Text(busy ?: "", fontSize = 18.sp, modifier = Modifier.padding(start = 12.dp))
                        }
                        OutlinedTextField(name, { name = it }, singleLine = true, label = { Text("なまえ（ひらがな）") }, modifier = Modifier.fillMaxWidth())
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            OutlinedTextField(price, { price = it.filter { c -> c.isDigit() }.take(5) }, singleLine = true, label = { Text("ねだん（えん）") },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.width(160.dp))
                            listOf(-10, 10, 100).forEach { d ->
                                TextButton(onClick = { price = ((price.toIntOrNull() ?: 0) + d).coerceAtLeast(10).toString() }) { Text(if (d > 0) "+$d" else "$d") }
                            }
                        }
                        Row(Modifier.horizontalScroll(rememberScrollState())) {
                            ShopItems.SHOPS.forEach { (k, l) -> FilterChip(shop == k, { shop = k }, label = { Text(l) }, modifier = Modifier.padding(end = 6.dp)) }
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(enabled = busy == null && name.isNotBlank() && price.isNotBlank(), onClick = { ok(again = editing == null) }) {
                                Text(if (editing == null) "✅ OK（つぎ を とる）" else "✅ なおす")
                            }
                            OutlinedButton(onClick = { photo = null; editing = null; name = ""; price = "" }) { Text("やめる") }
                        }
                        if (editing == null) Text("OK で ラベル が できて ${if (autoPrint) "印刷" else "共有"} し、すぐ つぎ の 写真 に なります", fontSize = 12.sp, color = Color.Gray)
                    }
                }
                // つくった もの
                LazyVerticalGrid(GridCells.Adaptive(150.dp), Modifier.weight(1f)) {
                    items(ShopItems.items, key = { it.id }) { item ->
                        val th by produceState<ImageBitmap?>(null, item.id) { value = withContext(Dispatchers.IO) { BitmapFactory.decodeFile(ShopItems.photo(context, item.id).path)?.asImageBitmap() } }
                        Column(
                            Modifier.padding(4.dp).clip(RoundedCornerShape(12.dp)).background(Color.White).border(1.dp, Color(0x22000000), RoundedCornerShape(12.dp)).padding(6.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            Box(Modifier.size(80.dp).clip(RoundedCornerShape(8.dp)).background(Color(0xFFF2EEF5))) { th?.let { Image(it, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop) } }
                            Text(item.name, fontSize = 15.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                            Text("${item.price}えん", fontSize = 14.sp)
                            Row {
                                TextButton(onClick = { printOrShare(item) }) { Text(if (Tepra.available) "🖨" else "📤") }
                                TextButton(onClick = { editing = item; photo = null; name = item.name; price = item.price.toString(); shop = item.shop }) { Text("✏️") }
                                TextButton(onClick = { ShopItems.remove(context, item) }) { Text("🗑") }
                            }
                        }
                    }
                }
            }
            if (Tepra.available) Text("Powered by KING JIM TEPRA-Print SDK", fontSize = 10.sp, color = Color.Gray)
        }
    }
}
