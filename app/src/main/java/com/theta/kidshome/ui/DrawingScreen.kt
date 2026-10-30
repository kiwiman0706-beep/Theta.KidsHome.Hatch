package com.theta.kidshome.ui

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.ui.input.pointer.PointerType
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.theta.kidshome.data.Brush
import com.theta.kidshome.data.Gemini
import com.theta.kidshome.data.Mark
import com.theta.kidshome.data.Painter
import com.theta.kidshome.data.Paintings
import com.theta.kidshome.data.Stamp
import com.theta.kidshome.data.Store
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.random.Random
import com.theta.kidshome.data.Stroke as PaintStroke

/** ホームに戻っても絵が消えないように、アプリが動いている間は保持しておく */
private object Drawing {
    var id = Paintings.newId()
    val marks = mutableStateListOf<Mark>()
    var background by mutableStateOf<Bitmap?>(null)
}

private val palette = listOf(
    0xFF222222, 0xFF7A7A7A, 0xFFFF4D6D, 0xFFFF8FC7, 0xFFFF9F1C, 0xFFFFD60A, 0xFFB5E550,
    0xFF6BCB77, 0xFF2EC4B6, 0xFF4D96FF, 0xFF3A47C9, 0xFF9B5DE5, 0xFF8B5A2B, 0xFFF3C9A6, 0xFFFFFFFF,
).map { Color(it) }
private val stamps = listOf("⭐", "💗", "🌸", "🐰", "🐱", "🦄", "🌈", "🍓", "🎀", "🦋", "🍭", "👑", "🐶", "🚗", "🌳", "☀️")

/**
 * おえかき：画材（ペン・いろえんぴつ・クレヨン・すいさい・あぶらえ・スプレー・ゆび・にじ）、スタンプ、
 * 写真の上に描く、写真を Gemini で ぬりえ・お絵かき風 にしてから描く。
 * 描いたものは 自動でほぞん して、📂 から開いて つづきを描ける。
 */
@OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)
@Composable
fun DrawingScreen(onBack: () -> Unit) {
    val theme = themeById(Store.config.currentProfile.themeId)
    val context = LocalContext.current
    val density = LocalDensity.current
    val scope = rememberCoroutineScope()
    val widths = with(density) { listOf(5.dp.toPx(), 14.dp.toPx(), 30.dp.toPx()) }
    val stampSize = with(density) { 64.dp.toPx() }

    var brush by remember { mutableStateOf(Brush.CRAYON) }
    var stampMode by remember { mutableStateOf(false) }
    var color by remember { mutableStateOf(palette[2]) }
    var widthIndex by remember { mutableIntStateOf(1) }
    // えもじ か "img:<ライブラリの id>"
    var stamp by remember { mutableStateOf(stamps.first()) }
    var showMaker by remember { mutableStateOf(false) }
    fun stampMark(x: Float, y: Float): Stamp {
        val scale = listOf(0.7f, 1.2f, 2.0f)[widthIndex]
        return if (stamp.startsWith("img:")) Stamp(x, y, "", stampSize * scale, stamp.removePrefix("img:")) else Stamp(x, y, stamp, stampSize * scale)
    }
    var canvasSize by remember { mutableStateOf(IntSize.Zero) }
    var askClear by remember { mutableStateOf(false) }
    var showOpen by remember { mutableStateOf(false) }
    var showMagic by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf<String?>(null) }
    val current = remember { mutableStateListOf<Offset>() }
    var fingerColor by remember { mutableStateOf(Color.Gray) }
    // ペンモード（スタイラスだけで描く・筆圧）
    var penMode by remember { mutableStateOf(PenPrefs.on(context)) }
    val pressures = remember { mutableStateListOf<Float>() }
    var usePressure by remember { mutableStateOf(false) }
    var eraserTip by remember { mutableStateOf(false) }

    // 描き終わった分は 1 まいの絵にしておく（毎回 ぜんぶ描き直さない）
    var committed by remember { mutableStateOf<Bitmap?>(null) }
    var version by remember { mutableIntStateOf(0) }
    fun rebuild() {
        if (canvasSize.width <= 0) return
        committed = Painter.render(canvasSize.width, canvasSize.height, Drawing.background, Drawing.marks)
        version++
    }
    fun commit(m: Mark) {
        Drawing.marks += m
        val b = committed
        if (b == null || b.width != canvasSize.width) rebuild() else { Painter.draw(android.graphics.Canvas(b), m); version++ }
    }
    fun saveNow() {
        val b = committed ?: return
        Paintings.save(context, Drawing.id, Drawing.marks.toList(), Drawing.background, b)
    }
    fun newDrawing(bg: Bitmap? = null) {
        saveNow()
        Drawing.id = Paintings.newId()
        Drawing.marks.clear()
        Drawing.background = bg
        rebuild()
    }

    DisposableEffect(Unit) { onDispose { saveNow() } }
    BackHandler { saveNow(); onBack() }

    val pickPhoto = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) scope.launch {
            val bmp = withContext(Dispatchers.IO) { loadPhoto(context, uri) }
            if (bmp != null) newDrawing(bmp) else Toast.makeText(context, "しゃしんを ひらけなかったよ", Toast.LENGTH_SHORT).show()
        }
    }

    fun line(points: List<Offset>) = PaintStroke(
        points = FloatArray(points.size * 2) { i -> if (i % 2 == 0) points[i / 2].x else points[i / 2].y },
        color = if (brush == Brush.FINGER) fingerColor.toArgb() else color.toArgb(),
        width = widths[widthIndex], brush = if (eraserTip) Brush.ERASER else brush, seed = Random.nextInt(),
        pressures = if (usePressure && pressures.size == points.size) pressures.map { it.coerceIn(0f, 1f) }.toFloatArray() else null,
    )

    Box(Modifier.fillMaxSize()) {
        RetroBackground(theme)
        Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing).padding(16.dp)) {
            // 上のボタン
            Row(verticalAlignment = Alignment.CenterVertically) {
                RetroIconButton("🏠", theme) { saveNow(); onBack() }
                DotText("おえかき", theme, 28, theme.text, modifier = Modifier.padding(start = 12.dp))
                busy?.let { DotText("　$it", theme, 16, theme.text) }
                Spacer(Modifier.weight(1f))
                RetroIconButton("↩️", theme) { if (Drawing.marks.isNotEmpty()) { Drawing.marks.removeAt(Drawing.marks.lastIndex); rebuild() } }
                RetroIconButton("🗑️", theme) { if (Drawing.marks.isNotEmpty()) askClear = true }
                RetroIconButton("📄", theme) { newDrawing() }
                RetroIconButton("📂", theme) { saveNow(); showOpen = true }
                RetroIconButton("🖼️", theme) { pickPhoto.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }
                if (Drawing.background != null && Gemini.apiKey(context).isNotBlank()) {
                    RetroIconButton("✨", theme) { showMagic = true }
                }
                RetroIconButton("🖊️", theme, penMode) {
                    penMode = !penMode
                    PenPrefs.set(context, penMode)
                    Toast.makeText(context, if (penMode) "ペンモード：ペンだけで かけるよ（て は さわっても だいじょうぶ）" else "ゆびでも かけるよ", Toast.LENGTH_LONG).show()
                }
                RetroIconButton("🖨️", theme) { committed?.let { printBitmap(context, it) } }
                RetroIconButton("💾", theme) {
                    val ok = committed?.let { savePhoto(context, it) } == true
                    Toast.makeText(context, if (ok) "「しゃしん」に ほぞん したよ！" else "ほぞん できなかったよ", Toast.LENGTH_SHORT).show()
                }
                RetroIconButton("📌", theme) {
                    val ok = Drawing.marks.isNotEmpty() && committed?.let { pin(context, it) } == true
                    Toast.makeText(context, if (ok) "ホームに はったよ！" else "なにか かいてから はってね", Toast.LENGTH_SHORT).show()
                }
            }
            Spacer(Modifier.height(10.dp))

            RetroWindow(
                theme, Modifier.weight(1f).fillMaxWidth().padding(end = 6.dp, bottom = 6.dp),
                title = if (stampMode) "スタンプ" else "${brush.emoji} ${brush.label}", titleColor = theme.titleBars[2],
            ) {
                Row(Modifier.weight(1f)) {
                    // 画材（左のたて列）
                    Column(
                        Modifier.fillMaxHeight().background(theme.bubbles[0]).verticalScroll(rememberScrollState()).padding(6.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Brush.entries.forEach { b -> RetroIconButton(b.emoji, theme, !stampMode && brush == b, 50.dp) { brush = b; stampMode = false } }
                        RetroIconButton("🌟", theme, stampMode, 50.dp) { stampMode = true }
                        Spacer(Modifier.height(8.dp))
                        widths.forEachIndexed { i, w ->
                            val selected = widthIndex == i
                            Box(
                                Modifier.padding(5.dp).size(50.dp).clip(RoundedCornerShape(14.dp))
                                    .background(if (selected) theme.titleBars[0] else Color.White)
                                    .border(3.dp, theme.outline, RoundedCornerShape(14.dp))
                                    .clickable { widthIndex = i },
                                contentAlignment = Alignment.Center,
                            ) {
                                Box(Modifier.size(with(density) { w.toDp() }.coerceAtMost(36.dp)).clip(CircleShape).background(theme.outline))
                            }
                        }
                    }
                    Box(Modifier.fillMaxHeight().width(3.dp).background(theme.outline))
                    // キャンバス
                    Box(
                        Modifier.weight(1f).fillMaxHeight()
                            .background(Color.White)
                            .onSizeChanged { if (it != canvasSize) { canvasSize = it; rebuild() } }
                            .pointerInput(penMode) {
                                awaitEachGesture {
                                    val down = awaitFirstDown()
                                    val stylus = down.type == PointerType.Stylus || down.type == PointerType.Eraser
                                    // ペンモード：手のひら・指は描かない
                                    if (penMode && !stylus) return@awaitEachGesture
                                    down.consume()
                                    if (stampMode) {
                                        commit(stampMark(down.position.x, down.position.y))
                                        return@awaitEachGesture
                                    }
                                    // ペンのおしり（けしごむ側）なら けしごむ
                                    eraserTip = down.type == PointerType.Eraser
                                    if (brush == Brush.FINGER) fingerColor = pickColor(committed, down.position)
                                    current.clear(); pressures.clear()
                                    current += down.position
                                    pressures += down.pressure
                                    usePressure = stylus
                                    while (true) {
                                        val ev = awaitPointerEvent()
                                        val ch = ev.changes.firstOrNull { it.id == down.id } ?: break
                                        if (!ch.pressed) break
                                        ch.historical.forEach { h -> current += h.position; pressures += ch.pressure }
                                        current += ch.position
                                        pressures += ch.pressure
                                        ch.consume()
                                    }
                                    commit(line(current.toList()))
                                    current.clear(); pressures.clear()
                                    eraserTip = false
                                }
                            }
                    ) {
                        Canvas(Modifier.fillMaxSize()) {
                            version.let { } // 描き直しの合図
                            drawIntoCanvas { c ->
                                committed?.let { c.nativeCanvas.drawBitmap(it, 0f, 0f, null) }
                                if (current.isNotEmpty()) Painter.draw(c.nativeCanvas, line(current.toList()).copy(seed = 1))
                            }
                        }
                    }
                }
                Box(Modifier.fillMaxWidth().height(3.dp).background(theme.outline))
                // いろ・スタンプ（下の列）
                Row(
                    Modifier.fillMaxWidth().background(theme.bubbles[0]).horizontalScroll(rememberScrollState()).padding(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (stampMode) {
                        RetroIconButton("➕", theme, size = 50.dp) { showMaker = true }
                        val lib = remember(com.theta.kidshome.data.StampLibrary.version) { com.theta.kidshome.data.StampLibrary.list() }
                        lib.forEach { item -> StampThumb(theme, item.id, stamp == "img:${item.id}") { stamp = "img:${item.id}" } }
                        if (stamp.startsWith("img:")) {
                            RetroIconButton("🏠", theme, size = 50.dp) {
                                floatOnHome(stamp.removePrefix("img:"))
                                Toast.makeText(context, "ホームに うかべたよ！", Toast.LENGTH_SHORT).show()
                            }
                        }
                        Spacer(Modifier.width(10.dp))
                        stamps.forEach { s -> RetroIconButton(s, theme, stamp == s, 50.dp) { stamp = s } }
                    } else if (brush == Brush.FINGER) {
                        DotText("☝️ ゆびで さわった いろを のばすよ", theme, 18)
                    } else {
                        palette.forEach { c ->
                            val selected = color == c
                            Box(
                                Modifier.padding(4.dp)
                                    .offset(y = if (selected) (-4).dp else 0.dp)
                                    .size(46.dp).clip(RoundedCornerShape(10.dp)).background(c)
                                    .border(if (selected) 5.dp else 3.dp, theme.outline, RoundedCornerShape(10.dp))
                                    .clickable { color = c; if (brush == Brush.ERASER) brush = Brush.CRAYON }
                            )
                        }
                    }
                }
            }
        }
    }

    if (askClear) {
        Dialog(onDismissRequest = { askClear = false }) {
            RetroWindow(theme, Modifier.width(340.dp), title = "かくにん", titleColor = Color(0xFFFFB3CF)) {
                Column(Modifier.fillMaxWidth().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    DotText("かいたものを ぜんぶ けす？", theme, 20)
                    Spacer(Modifier.height(20.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        RetroButton("けす", theme, theme.titleBars[0]) { Drawing.marks.clear(); rebuild(); askClear = false }
                        RetroButton("やめる", theme, Color.White) { askClear = false }
                    }
                }
            }
        }
    }

    if (showOpen) OpenDialog(theme, onDismiss = { showOpen = false }) { id ->
        val (marks, bg) = Paintings.load(context, id)
        Drawing.id = id
        Drawing.marks.clear(); Drawing.marks.addAll(marks)
        Drawing.background = bg
        rebuild()
        showOpen = false
    }

    if (showMaker) StampMakerDialog(theme, onDismiss = { showMaker = false }) { stamp = "img:${it.id}" }

    if (showMagic) {
        Dialog(onDismissRequest = { showMagic = false }) {
            RetroWindow(theme, Modifier.width(420.dp), title = "✨ まほう", titleColor = theme.titleBars[1]) {
                Column(Modifier.padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    DotText("しゃしんを どんな え に する？", theme, 18)
                    Spacer(Modifier.height(12.dp))
                    Gemini.Style.entries.forEach { st ->
                        RetroButton("${st.emoji} ${st.label}", theme, theme.titleBars[st.ordinal % theme.titleBars.size], modifier = Modifier.padding(4.dp)) {
                            showMagic = false
                            val photo = Drawing.background ?: return@RetroButton
                            busy = "まほう ちゅう…"
                            scope.launch {
                                runCatching { Gemini.transform(context, photo, st) }
                                    .onSuccess { newDrawing(it) }
                                    .onFailure { Toast.makeText(context, it.message ?: "できなかったよ", Toast.LENGTH_LONG).show() }
                                busy = null
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun OpenDialog(theme: KidTheme, onDismiss: () -> Unit, onOpen: (String) -> Unit) {
    val context = LocalContext.current
    var list by remember { mutableStateOf(Paintings.list(context)) }
    Dialog(onDismissRequest = onDismiss) {
        RetroWindow(theme, Modifier.width(620.dp).heightIn(max = 640.dp), title = "かいた え", titleColor = theme.titleBars[1]) {
            if (list.isEmpty()) DotText("まだ ないよ", theme, 18, modifier = Modifier.padding(20.dp))
            LazyVerticalGrid(GridCells.Adaptive(170.dp), Modifier.padding(10.dp)) {
                items(list, key = { it.id }) { info ->
                    val thumb = remember(info.id) { Paintings.thumb(context, info.id)?.asImageBitmap() }
                    Box(Modifier.padding(6.dp)) {
                        Box(
                            Modifier.fillMaxWidth().height(120.dp).clip(RoundedCornerShape(10.dp)).background(Color.White)
                                .border(3.dp, theme.outline, RoundedCornerShape(10.dp)).clickable { onOpen(info.id) }
                        ) { if (thumb != null) Image(thumb, null, Modifier.fillMaxSize(), contentScale = ContentScale.Fit) }
                        Box(Modifier.align(Alignment.TopEnd)) {
                            RetroIconButton("🗑️", theme, size = 34.dp) { Paintings.delete(context, info.id); list = Paintings.list(context) }
                        }
                    }
                }
            }
        }
    }
}

/** ペンモードを おぼえておく */
private object PenPrefs {
    fun on(c: Context) = c.getSharedPreferences("drawing", Context.MODE_PRIVATE).getBoolean("pen", false)
    fun set(c: Context, v: Boolean) = c.getSharedPreferences("drawing", Context.MODE_PRIVATE).edit().putBoolean("pen", v).apply()
}

/** 印刷（ぬりえ を紙に出す など） */
fun printBitmap(context: Context, bitmap: Bitmap, name: String = "おえかき") {
    runCatching {
        androidx.print.PrintHelper(context).apply { scaleMode = androidx.print.PrintHelper.SCALE_MODE_FIT }.printBitmap(name, bitmap)
    }.onFailure { Toast.makeText(context, "いんさつ できなかったよ", Toast.LENGTH_SHORT).show() }
}

/** ゆび：さわった所の色 */
private fun pickColor(b: Bitmap?, p: Offset): Color {
    b ?: return Color.Gray
    val x = p.x.toInt().coerceIn(0, b.width - 1)
    val y = p.y.toInt().coerceIn(0, b.height - 1)
    return Color(b.getPixel(x, y))
}

private fun loadPhoto(context: Context, uri: Uri): Bitmap? = runCatching {
    ImageDecoder.decodeBitmap(ImageDecoder.createSource(context.contentResolver, uri)) { d, info, _ ->
        d.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
        val s = 2000f / maxOf(info.size.width, info.size.height)
        if (s < 1f) d.setTargetSize((info.size.width * s).toInt(), (info.size.height * s).toInt())
    }
}.getOrNull()

/** 絵を小さくして、ホームの ふせん にする */
private fun pin(context: Context, full: Bitmap): Boolean = runCatching {
    val scale = 480f / maxOf(full.width, full.height)
    val small = Bitmap.createScaledBitmap(full, (full.width * scale).toInt().coerceAtLeast(1), (full.height * scale).toInt().coerceAtLeast(1), true)
    val name = "drawing_${System.currentTimeMillis()}.png"
    val dir = java.io.File(context.filesDir, "stickers").apply { mkdirs() }
    java.io.File(dir, name).outputStream().use { small.compress(Bitmap.CompressFormat.PNG, 100, it) }
    Store.addSticker(com.theta.kidshome.data.Sticker(image = name, color = 0xFFFFFFFF.toInt()))
    true
}.getOrDefault(false)

private fun savePhoto(context: Context, bitmap: Bitmap): Boolean = runCatching {
    val values = ContentValues().apply {
        put(MediaStore.Images.Media.DISPLAY_NAME, "oekaki_${System.currentTimeMillis()}.png")
        put(MediaStore.Images.Media.MIME_TYPE, "image/png")
        put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/KidsHome")
    }
    val resolver = context.contentResolver
    val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values) ?: return false
    resolver.openOutputStream(uri)?.use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) } ?: return false
    true
}.getOrDefault(false)
