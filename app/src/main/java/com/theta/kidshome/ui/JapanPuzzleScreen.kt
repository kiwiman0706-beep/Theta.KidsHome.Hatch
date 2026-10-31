package com.theta.kidshome.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.theta.kidshome.data.Japan
import com.theta.kidshome.data.JapanPhotos
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import com.theta.kidshome.data.Store
import kotlin.math.hypot

private val RegionColors = listOf(
    Color(0xFFB8D8FF), Color(0xFFC9F0A8), Color(0xFFFFE29A), Color(0xFFFFC8A8),
    Color(0xFFFFB3CF), Color(0xFFCBB8FF), Color(0xFFA8E8D8), Color(0xFFF5C99B),
)

/**
 * 都道府県パズル：右 の ピース を ゆび で 地図 の ただしい ところ へ。
 * ハマったら 名前・県庁・名物（えもじ）・とくちょう の カード が でる。地方 ごと か ぜんぶ。
 */
@Composable
fun JapanPuzzleScreen(onBack: () -> Unit) {
    BackHandler(onBack = onBack)
    val context = LocalContext.current
    val theme = themeById(Store.config.currentProfile.themeId)
    val map = remember { Japan.map(context) }
    var mode by remember { mutableStateOf(-1) } // -1 = ぜんぶ、0.. = 地方、-2 = おもいで（いった ところ）
    var placed by remember { mutableStateOf(setOf<Int>()) }
    var info by remember { mutableStateOf<Int?>(null) }
    var names by remember { mutableStateOf(false) }
    val memory = mode == -2
    val visited = remember(JapanPhotos.version) { JapanPhotos.visited(context) }
    val targets = remember(mode) { if (mode < 0) (1..47).toList() else Japan.REGIONS[mode].codes.toList() }
    var queue by remember(mode) { mutableStateOf(targets.shuffled()) }
    val tray = if (memory) emptyList() else queue.filter { it !in placed }.take(6)
    val done = !memory && targets.all { it in placed }

    fun start(m: Int) { mode = m; placed = emptySet(); info = null }

    Column(Modifier.fillMaxSize().background(theme.bgBottom).windowInsetsPadding(WindowInsets.safeDrawing).padding(14.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            RetroIconButton("🏠", theme, onClick = onBack)
            DotText("にっぽん パズル", theme, 28, theme.text, modifier = Modifier.padding(start = 10.dp))
            Spacer(Modifier.width(12.dp))
            Row(Modifier.weight(1f).horizontalScroll(rememberScrollState())) {
                RetroButton("📷 おもいで", theme, if (memory) theme.titleBars[3] else Color.White, fontSize = 15, modifier = Modifier.padding(end = 6.dp)) { start(-2) }
                RetroButton("🗾 ぜんぶ", theme, if (mode == -1) theme.titleBars[0] else Color.White, fontSize = 15, modifier = Modifier.padding(end = 6.dp)) { start(-1) }
                Japan.REGIONS.forEachIndexed { i, r ->
                    RetroButton(r.name, theme, if (mode == i) RegionColors[i] else Color.White, fontSize = 15, modifier = Modifier.padding(end = 6.dp)) { start(i) }
                }
            }
            DotText(if (memory) "いった ところ ${visited.size}" else "${targets.count { it in placed }} / ${targets.size}", theme, 18, theme.text, modifier = Modifier.padding(horizontal = 8.dp))
            RetroButton(if (names) "🏷 なまえ" else "🙈 なまえ", theme, if (names) theme.titleBars[1] else Color.White, fontSize = 15) { names = !names }
        }
        Spacer(Modifier.height(8.dp))
        Box(Modifier.weight(1f).fillMaxWidth()) {
            Board(
                map, mode, targets, if (memory) visited else placed, tray, names, theme, Modifier.fillMaxSize(),
                tappable = if (memory) targets.toSet() else placed, marks = visited,
                onPlace = { code -> placed = placed + code; info = code },
                onTap = { code -> info = code },
            )
            info?.let { code ->
                val east = mode in 0..3
                InfoCard(
                    Japan.pref(code), RegionColors[Japan.pref(code).region], theme,
                    Modifier.align(if (east) Alignment.BottomStart else Alignment.BottomEnd).padding(start = 8.dp, bottom = 8.dp, end = if (east || memory) 8.dp else 308.dp),
                ) { info = null }
            }
            if (memory && info == null) DotText(
                if (visited.isEmpty()) "けん を タップ して、あそび に いった ときの しゃしん を いれてね" else "📷 の けん を タップ すると しゃしん が 見られるよ",
                theme, 18, theme.text, modifier = Modifier.align(Alignment.TopStart).padding(12.dp),
            )
            if (done) Column(Modifier.align(Alignment.TopStart).padding(8.dp)) {
                DotText("${theme.mascot} 🏅 ぜんぶ できた！", theme, 26, theme.text)
                RetroButton("🔁 もういちど", theme, theme.titleBars[2], fontSize = 18) { placed = emptySet(); queue = targets.shuffled(); info = null }
            }
        }
    }
}

private class View(val s: Float, val ox: Float, val oy: Float) {
    fun toScreen(x: Float, y: Float) = Offset(ox + x * s, oy + y * s)
    fun toMap(o: Offset) = Offset((o.x - ox) / s, (o.y - oy) / s)
}

private fun inside(poly: FloatArray, x: Float, y: Float): Boolean {
    var c = false
    var j = poly.size - 2
    var i = 0
    while (i < poly.size) {
        val xi = poly[i]; val yi = poly[i + 1]; val xj = poly[j]; val yj = poly[j + 1]
        if ((yi > y) != (yj > y) && x < (xj - xi) * (y - yi) / (yj - yi) + xi) c = !c
        j = i; i += 2
    }
    return c
}

@Composable
private fun Board(
    map: Japan.Map, mode: Int, targets: List<Int>, placed: Set<Int>, tray: List<Int>, names: Boolean, theme: KidTheme,
    modifier: Modifier, tappable: Set<Int>, marks: Set<Int>, onPlace: (Int) -> Unit, onTap: (Int) -> Unit,
) {
    val shapes = remember(map) { map.shapes.associateBy { it.code } }
    val paths = remember(map) {
        map.shapes.associate { sh ->
            sh.code to Path().apply {
                sh.polys.forEach { p -> moveTo(p[0], p[1]); for (i in 2 until p.size step 2) lineTo(p[i], p[i + 1]); close() }
            }
        }
    }
    val okinawaIn = 47 in targets
    // 九州・沖縄 の とき は 沖縄 の わく を 九州 の 左下 に よせる（北 の わく の まま だと 九州 が 小さく なる）
    val shift = remember(mode) {
        if (mode < 0 || !okinawaIn) Offset.Zero else {
            val ky = (40..46).map { shapes.getValue(it) }
            Offset(ky.minOf { it.minX } - map.inset[2] * 0.6f - map.inset[0], ky.maxOf { it.maxY } - map.inset[3] - map.inset[1])
        }
    }
    fun off(code: Int) = if (code == 47) shift else Offset.Zero
    var drag by remember { mutableStateOf<Pair<Int, Offset>?>(null) }
    val density = LocalDensity.current
    val lift = with(density) { 36.dp.toPx() }
    val snap = with(density) { 40.dp.toPx() }
    val minLabel = with(density) { 11.dp.toPx() }
    val maxLabel = with(density) { 20.dp.toPx() }

    BoxWithConstraints(modifier) {
        val w = constraints.maxWidth.toFloat(); val h = constraints.maxHeight.toFloat()
        val trayW = if (mode == -2) 0f else with(density) { 300.dp.toPx() }.coerceAtMost(w * 0.32f)
        val mapW = w - trayW
        // 見る はんい（地図 の 単位）
        val box = remember(mode) {
            val sh = targets.map { shapes.getValue(it) }
            var r = Rect(sh.minOf { it.minX + off(it.code).x }, sh.minOf { it.minY + off(it.code).y }, sh.maxOf { it.maxX + off(it.code).x }, sh.maxOf { it.maxY + off(it.code).y })
            if (okinawaIn) {
                val ix = map.inset[0] + shift.x; val iy = map.inset[1] + shift.y
                r = Rect(minOf(r.left, ix), minOf(r.top, iy), maxOf(r.right, ix + map.inset[2]), maxOf(r.bottom, iy + map.inset[3]))
            }
            val m = maxOf(r.width, r.height) * 0.08f
            Rect(r.left - m, r.top - m, r.right + m, r.bottom + m)
        }
        val s = minOf(mapW / box.width, h / box.height)
        val view = View(s, (mapW - box.width * s) / 2 - box.left * s, (h - box.height * s) / 2 - box.top * s)
        // トレー の ます（2 れつ × 3 だん）
        val slots = (0 until 6).map { i ->
            val cw = trayW / 2; val ch = h / 3
            Rect(mapW + (i % 2) * cw, (i / 2) * ch, mapW + (i % 2 + 1) * cw, (i / 2 + 1) * ch)
        }
        fun slotScale(code: Int, slot: Rect): Float {
            val sh = shapes.getValue(code)
            return minOf(s * 2.5f, slot.width * 0.8f / (sh.maxX - sh.minX), slot.height * 0.7f / (sh.maxY - sh.minY))
        }

        Canvas(
            Modifier.fillMaxSize().pointerInput(mode, tray, placed, tappable, s) {
                awaitEachGesture {
                    val down = awaitFirstDown()
                    val i = slots.indexOfFirst { it.contains(down.position) }
                    val code = tray.getOrNull(i)
                    if (code == null) {
                        // 地図 を タップ：はめた 県 の カード
                        val m = view.toMap(down.position)
                        tappable.firstOrNull { c -> shapes.getValue(c).polys.any { inside(it, m.x - off(c).x, m.y - off(c).y) } }?.let(onTap)
                        return@awaitEachGesture
                    }
                    down.consume()
                    drag = code to down.position
                    while (true) {
                        val ev = awaitPointerEvent()
                        val c = ev.changes.firstOrNull { it.id == down.id } ?: break
                        if (!c.pressed) break
                        drag = code to c.position
                        c.consume()
                    }
                    val at = drag?.second
                    drag = null
                    if (at != null) {
                        val sh = shapes.getValue(code)
                        val goal = view.toScreen(sh.cx + off(code).x, sh.cy + off(code).y)
                        val piece = Offset(at.x, at.y - lift)
                        if (hypot(piece.x - goal.x, piece.y - goal.y) < maxOf(snap, (sh.maxX - sh.minX) * s * 0.25f)) onPlace(code)
                    }
                }
            }
        ) {
            // うみ
            drawRoundRect(Color(0xFFDDF0FA), Offset.Zero, Size(mapW - 8f, h), CornerRadius(24f))
            if (okinawaIn || mode < 0) {
                val tl = view.toScreen(map.inset[0] + shift.x, map.inset[1] + shift.y)
                drawRect(Color(0xFF8AA8B8), tl, Size(map.inset[2] * s, map.inset[3] * s), style = Stroke(2f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 8f))))
            }
            val outline = theme.outline
            clipRect(0f, 0f, mapW - 8f, h) {
            map.shapes.forEach { sh ->
                val code = sh.code
                val path = paths.getValue(code)
                val target = code in targets
                val fill = when {
                    code in placed -> RegionColors[Japan.pref(code).region]
                    target -> Color.White
                    else -> Color(0xFFE6E6E6)
                }
                val o0 = view.toScreen(off(code).x, off(code).y)
                piece(path, o0.x, o0.y, s, fill, if (target) outline.copy(alpha = if (code in placed) 0.9f else 0.35f) else Color(0xFFBBBBBB), dashed = target && code !in placed)
                if (code in placed && code in marks) {
                    val o = view.toScreen(sh.cx + off(code).x, sh.cy + off(code).y)
                    label("📷", Offset(o.x, o.y - (s * 13f).coerceIn(minLabel, maxLabel) * 1.1f), (s * 13f).coerceIn(minLabel, maxLabel), Color.Black)
                }
                if (code in placed || (names && target)) {
                    val o = view.toScreen(sh.cx + off(code).x, sh.cy + off(code).y)
                    label(Japan.pref(code).name.removeSuffix("県").removeSuffix("府").let { if (it == "東京都") "東京" else it }, o, (s * 13f).coerceIn(minLabel, maxLabel), outline.copy(alpha = if (code in placed) 1f else 0.45f))
                }
            }
            }
            // トレー
            if (trayW > 0f) drawRoundRect(Color.White.copy(alpha = 0.7f), Offset(mapW + 4f, 0f), Size(trayW - 4f, h), CornerRadius(24f))
            tray.forEachIndexed { i, code ->
                if (drag?.first == code) return@forEachIndexed
                val slot = slots[i]
                val sh = shapes.getValue(code)
                val k = slotScale(code, slot)
                val c = slot.center
                piece(paths.getValue(code), c.x - (sh.minX + sh.maxX) / 2 * k, c.y - (sh.minY + sh.maxY) / 2 * k, k, RegionColors[Japan.pref(code).region], outline, false)
                if (names) label(Japan.pref(code).name, Offset(c.x, slot.bottom - 18f), 30f, outline)
            }
            // もって いる ピース（地図 と おなじ 大きさ、ゆび の すこし 上）
            drag?.let { (code, at) ->
                val sh = shapes.getValue(code)
                piece(paths.getValue(code), at.x - sh.cx * s, at.y - lift - sh.cy * s, s, RegionColors[Japan.pref(code).region].copy(alpha = 0.92f), outline, false)
            }
        }
    }
}

private fun DrawScope.piece(path: Path, tx: Float, ty: Float, k: Float, fill: Color, line: Color, dashed: Boolean) {
    withTransform({ translate(tx, ty); scale(k, k, Offset.Zero) }) {
        drawPath(path, fill)
        drawPath(path, line, style = Stroke(width = 2.2f / k, pathEffect = if (dashed) PathEffect.dashPathEffect(floatArrayOf(6f / k, 5f / k)) else null))
    }
}

private fun DrawScope.label(text: String, at: Offset, size: Float, color: Color) {
    drawIntoCanvas { cv ->
        val p = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
            textSize = size; textAlign = android.graphics.Paint.Align.CENTER; this.color = color.toArgb(); isFakeBoldText = true
        }
        val o = android.graphics.Paint(p).apply { style = android.graphics.Paint.Style.STROKE; strokeWidth = size * 0.18f; this.color = android.graphics.Color.WHITE }
        cv.nativeCanvas.drawText(text, at.x, at.y + size * 0.35f, o)
        cv.nativeCanvas.drawText(text, at.x, at.y + size * 0.35f, p)
    }
}

@Composable
private fun InfoCard(p: Japan.Pref, color: Color, theme: KidTheme, modifier: Modifier, onClose: () -> Unit) {
    RetroWindow(theme, modifier.width(400.dp), title = Japan.REGIONS[p.region].name + " ちほう", titleColor = color, onClick = onClose) {
        Column(Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.Bottom) {
                Text(p.name, fontSize = 34.sp, fontWeight = FontWeight.Bold, color = theme.outline)
                Text("（${p.kana}）", fontSize = 18.sp, color = theme.outline.copy(alpha = 0.75f), modifier = Modifier.padding(start = 4.dp, bottom = 4.dp))
            }
            Text("🏛 けんちょう：${p.capital}", fontSize = 16.sp, color = theme.outline)
            Row(Modifier.padding(vertical = 8.dp)) {
                p.goods.forEach { (e, n) ->
                    Column(Modifier.padding(end = 14.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(e, fontSize = 52.sp)
                        Text(n, fontSize = 14.sp, color = theme.outline)
                    }
                }
            }
            Text(p.about, fontSize = 17.sp, color = theme.outline, lineHeight = 24.sp)
            Photos(p, theme)
            Text("（タップ で とじる）", fontSize = 12.sp, color = theme.outline.copy(alpha = 0.5f), modifier = Modifier.padding(top = 6.dp))
        }
    }
}

/** あそび に いった ときの しゃしん：ならべて 見る・たす・おおきく 見る */
@Composable
private fun Photos(p: Japan.Pref, theme: KidTheme) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val files = remember(p.code, JapanPhotos.version) { JapanPhotos.list(context, p.code) }
    var open by remember { mutableStateOf<Int?>(null) }
    var busy by remember { mutableStateOf(false) }
    val pick = rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia(20)) { uris ->
        if (uris.isNotEmpty()) {
            busy = true
            scope.launch { withContext(Dispatchers.IO) { JapanPhotos.add(context, p.code, uris) }; busy = false }
        }
    }
    Row(Modifier.padding(top = 10.dp).horizontalScroll(rememberScrollState()), verticalAlignment = Alignment.CenterVertically) {
        files.forEachIndexed { i, f ->
            val bmp by produceState<ImageBitmap?>(null, f.path) { value = withContext(Dispatchers.IO) { JapanPhotos.thumb(f)?.asImageBitmap() } }
            Box(
                Modifier.padding(end = 6.dp).size(84.dp).clip(RoundedCornerShape(10.dp)).background(Color(0xFFEEEEEE)).clickable { open = i },
            ) { bmp?.let { Image(it, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop) } }
        }
        RetroButton(if (busy) "…" else if (files.isEmpty()) "📷 しゃしん を いれる" else "📷 ＋", theme, theme.titleBars[1], fontSize = 15) {
            if (!busy) pick.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
        }
    }
    open?.let { start -> PhotoViewer(files, start, theme) { open = null } }
}

@Composable
private fun PhotoViewer(files: List<File>, start: Int, theme: KidTheme, onClose: () -> Unit) {
    var i by remember { mutableIntStateOf(start) }
    var ask by remember { mutableStateOf(false) }
    val f = files.getOrNull(i) ?: run { onClose(); return }
    val bmp by produceState<ImageBitmap?>(null, f.path) { value = withContext(Dispatchers.IO) { JapanPhotos.full(f)?.asImageBitmap() } }
    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.92f)).clickable(onClick = onClose)) {
            bmp?.let { Image(it, null, Modifier.fillMaxSize().padding(24.dp), contentScale = ContentScale.Fit) }
            Row(Modifier.align(Alignment.BottomCenter).padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                RetroIconButton("◀", theme) { i = (i - 1 + files.size) % files.size }
                DotText("${i + 1} / ${files.size}", theme, 20, Color.White, modifier = Modifier.padding(horizontal = 14.dp))
                RetroIconButton("▶", theme) { i = (i + 1) % files.size }
                Spacer(Modifier.width(24.dp))
                RetroIconButton("🗑", theme) { ask = true }
            }
        }
    }
    if (ask) AlertDialog(
        onDismissRequest = { ask = false },
        title = { Text("この しゃしん を けす？") },
        confirmButton = { TextButton(onClick = { ask = false; JapanPhotos.delete(f); if (files.size <= 1) onClose() else i = i.coerceAtMost(files.size - 2) }) { Text("けす") } },
        dismissButton = { TextButton(onClick = { ask = false }) { Text("やめる") } },
    )
}
