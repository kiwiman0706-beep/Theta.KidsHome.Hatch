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
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.runtime.rememberUpdatedState
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
    // ひらがな で 出す（まだ 漢字 が よめない とき）
    // ひらがな が さいしょ（えらんだ ほう を おぼえる）
    val kanaPrefs = androidx.compose.ui.platform.LocalContext.current.getSharedPreferences("japan", android.content.Context.MODE_PRIVATE)
    var kana by remember { mutableStateOf(kanaPrefs.getBoolean("kana", true)) }
    var yuruBook by remember { mutableStateOf(false) }
    val memory = mode == -2
    val visited = remember(JapanPhotos.version) { JapanPhotos.visited(context) }
    val targets = remember(mode) { if (mode < 0) (1..47).toList() else Japan.REGIONS[mode].codes.toList() }
    var queue by remember(mode) { mutableStateOf(targets.shuffled()) }
    val tray = if (memory) emptyList() else queue.filter { it !in placed }.take(6)
    val done = !memory && targets.all { it in placed }
    androidx.compose.runtime.LaunchedEffect(done) { if (done) com.theta.kidshome.data.Points.earn(if (mode < 0) 20 else 5, "にほん パズル", "japan:$mode") }

    fun start(m: Int) { mode = m; placed = emptySet(); info = null }

    Column(Modifier.fillMaxSize().background(theme.bgBottom).windowInsetsPadding(WindowInsets.safeDrawing).padding(14.dp)) {
        if (yuruBook) YuruBook(theme, kana) { yuruBook = false }
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
            RetroButton("🧸", theme, Color.White, fontSize = 15, modifier = Modifier.padding(end = 6.dp)) { yuruBook = true }
            RetroButton(if (kana) "あ ひらがな" else "漢 かんじ", theme, if (kana) theme.titleBars[2] else Color.White, fontSize = 15, modifier = Modifier.padding(end = 6.dp)) { kana = !kana; kanaPrefs.edit().putBoolean("kana", kana).apply() }
            RetroButton(if (names) "🏷 なまえ" else "🙈 なまえ", theme, if (names) theme.titleBars[1] else Color.White, fontSize = 15) { names = !names }
        }
        Spacer(Modifier.height(8.dp))
        Box(Modifier.weight(1f).fillMaxWidth()) {
            Board(
                map, mode, targets, if (memory) visited else placed, tray, names, kana, theme, Modifier.fillMaxSize(),
                tappable = if (memory) targets.toSet() else placed, marks = visited,
                onPlace = { code -> placed = placed + code; info = code },
                onTap = { code -> info = code },
            )
            info?.let { code ->
                val east = mode in 0..3
                InfoCard(
                    Japan.pref(code), RegionColors[Japan.pref(code).region], theme, kana,
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
    map: Japan.Map, mode: Int, targets: List<Int>, placed: Set<Int>, tray: List<Int>, names: Boolean, kana: Boolean, theme: KidTheme,
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
    // ゆび 2 本 で 大きく・小さく、1 本 で うごかす（地図 の ところ だけ）
    var zoom by remember(mode) { mutableStateOf(1f) }
    var pan by remember(mode) { mutableStateOf(Offset.Zero) }
    val density = LocalDensity.current
    val lift = with(density) { 36.dp.toPx() }
    val snap = with(density) { 40.dp.toPx() }
    val minLabel = with(density) { 11.dp.toPx() }
    val maxLabel = with(density) { 26.dp.toPx() }

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
        val s0 = minOf(mapW / box.width, h / box.height)
        val ox0 = (mapW - box.width * s0) / 2 - box.left * s0
        val oy0 = (h - box.height * s0) / 2 - box.top * s0
        val s = s0 * zoom
        val view = View(s, ox0 * zoom + pan.x, oy0 * zoom + pan.y)
        val viewNow = rememberUpdatedState(view)
        /** c を 中心 に f ばい（はみだし すぎない よう に） */
        fun zoomBy(f: Float, c: Offset, move: Offset = Offset.Zero) {
            val nz = (zoom * f).coerceIn(1f, 8f)
            var np = c + move - (c - pan) * (nz / zoom)
            np = Offset(np.x.coerceIn(mapW * (1 - nz) - mapW * 0.3f, mapW * 0.3f), np.y.coerceIn(h * (1 - nz) - h * 0.3f, h * 0.3f))
            zoom = nz; pan = if (nz == 1f) Offset.Zero else np
        }
        // トレー の ます（2 れつ × 3 だん）
        val slots = (0 until 6).map { i ->
            val cw = trayW / 2; val ch = h / 3
            Rect(mapW + (i % 2) * cw, (i / 2) * ch, mapW + (i % 2 + 1) * cw, (i / 2 + 1) * ch)
        }
        fun slotScale(code: Int, slot: Rect): Float {
            val sh = shapes.getValue(code)
            return minOf(s0 * 2.5f, slot.width * 0.8f / (sh.maxX - sh.minX), slot.height * 0.7f / (sh.maxY - sh.minY))
        }

        Canvas(
            Modifier.fillMaxSize().pointerInput(mode, tray, placed, tappable, s0, mapW) {
                awaitEachGesture {
                    val down = awaitFirstDown()
                    val i = slots.indexOfFirst { it.contains(down.position) }
                    val code = tray.getOrNull(i)
                    if (code == null) {
                        if (down.position.x > mapW) return@awaitEachGesture
                        // 地図：2 本 で ピンチ、1 本 で うごかす、うごかさ ず に はなせば タップ
                        var moved = false
                        var total = Offset.Zero
                        while (true) {
                            val ev = awaitPointerEvent()
                            val pressed = ev.changes.filter { it.pressed }
                            if (pressed.isEmpty()) break
                            if (pressed.size >= 2) {
                                moved = true
                                zoomBy(ev.calculateZoom(), ev.calculateCentroid(useCurrent = true), ev.calculatePan())
                            } else {
                                val d = ev.calculatePan()
                                total += d
                                if (moved || total.getDistance() > viewConfiguration.touchSlop) {
                                    moved = true
                                    if (zoom > 1f) zoomBy(1f, Offset.Zero, d)
                                }
                            }
                            ev.changes.forEach { it.consume() }
                        }
                        if (!moved) {
                            // タップ：はめた 県 の カード
                            val m = viewNow.value.toMap(down.position)
                            tappable.firstOrNull { c -> shapes.getValue(c).polys.any { inside(it, m.x - off(c).x, m.y - off(c).y) } }?.let(onTap)
                        }
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
                        val v = viewNow.value
                        val goal = v.toScreen(sh.cx + off(code).x, sh.cy + off(code).y)
                        val piece = Offset(at.x, at.y - lift)
                        if (hypot(piece.x - goal.x, piece.y - goal.y) < maxOf(snap, (sh.maxX - sh.minX) * v.s * 0.25f)) onPlace(code)
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
                    label(shortName(Japan.pref(code), kana), o, (s * 13f).coerceIn(minLabel, maxLabel), outline.copy(alpha = if (code in placed) 1f else 0.45f))
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
                if (names) label(if (kana) Japan.pref(code).kana else Japan.pref(code).name, Offset(c.x, slot.bottom - 18f), 30f, outline)
            }
            // もって いる ピース（地図 と おなじ 大きさ、ゆび の すこし 上）
            drag?.let { (code, at) ->
                val sh = shapes.getValue(code)
                piece(paths.getValue(code), at.x - sh.cx * s, at.y - lift - sh.cy * s, s, RegionColors[Japan.pref(code).region].copy(alpha = 0.92f), outline, false)
            }
        }
        // 大きく・小さく・もどす
        Column(Modifier.align(Alignment.TopStart).padding(start = with(density) { (mapW - 8f).toDp() } - 64.dp, top = 8.dp)) {
            RetroIconButton("➕", theme, size = 52.dp) { zoomBy(1.6f, Offset(mapW / 2, h / 2)) }
            Spacer(Modifier.height(6.dp))
            RetroIconButton("➖", theme, size = 52.dp) { zoomBy(1 / 1.6f, Offset(mapW / 2, h / 2)) }
            if (zoom > 1f) {
                Spacer(Modifier.height(6.dp))
                RetroIconButton("🔄", theme, size = 52.dp) { zoom = 1f; pan = Offset.Zero }
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
private fun InfoCard(p: Japan.Pref, color: Color, theme: KidTheme, kana: Boolean, modifier: Modifier, onClose: () -> Unit) {
    RetroWindow(theme, modifier.width(400.dp), title = Japan.REGIONS[p.region].name + " ちほう", titleColor = color, onClick = onClose) {
        Column(Modifier.padding(14.dp).verticalScroll(rememberScrollState())) {
            Row(verticalAlignment = Alignment.Bottom) {
                Text(if (kana) p.kana else p.name, fontSize = 34.sp, fontWeight = FontWeight.Bold, color = theme.outline)
                if (!kana) Text("（${p.kana}）", fontSize = 18.sp, color = theme.outline.copy(alpha = 0.75f), modifier = Modifier.padding(start = 4.dp, bottom = 4.dp))
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
            com.theta.kidshome.data.Yuru.of(LocalContext.current, p.code).forEach { y -> YuruBox(y, theme, kana) }
            Photos(p, theme)
            Text("（タップ で とじる）", fontSize = 12.sp, color = theme.outline.copy(alpha = 0.5f), modifier = Modifier.padding(top = 6.dp))
        }
    }
}

/** ゆるキャラ の しょうかい（絵 は 入れない。モチーフ の 絵文字 と おはなし） */
@Composable
internal fun YuruBox(y: com.theta.kidshome.data.Yuru.Chara, theme: KidTheme, kana: Boolean) {
    Column(Modifier.padding(top = 10.dp).fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Color(0xFFFFF4D6)).padding(10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            // ほんもの の 絵（パック か おうちのひと が 足した もの）。なければ 絵文字
            val ctx = LocalContext.current
            val pic by produceState<ImageBitmap?>(null, y.pref, com.theta.kidshome.data.YuruPack.version) {
                value = withContext(Dispatchers.IO) { com.theta.kidshome.data.YuruPack.image(ctx, y.pref)?.let { android.graphics.BitmapFactory.decodeFile(it.path)?.asImageBitmap() } }
            }
            pic?.let { Image(it, y.name, Modifier.size(110.dp).clip(RoundedCornerShape(10.dp)), contentScale = ContentScale.Fit) } ?: Text(y.emoji, fontSize = 34.sp)
            Column(Modifier.padding(start = 8.dp)) {
                Text("🧸 ゆるキャラ：" + if (kana) y.kana else y.name, fontSize = 18.sp, fontWeight = FontWeight.Bold, color = theme.outline)
                Text(y.who, fontSize = 13.sp, color = theme.outline.copy(alpha = 0.7f))
            }
        }
        Text(y.story, fontSize = 15.sp, color = theme.outline, lineHeight = 22.sp, modifier = Modifier.padding(top = 4.dp))
    }
}

/** 🧸 ゆるキャラ ずかん：ぜんぶ を ならべて 見る */
@Composable
private fun YuruBook(theme: KidTheme, kana: Boolean, onClose: () -> Unit) {
    val list = com.theta.kidshome.data.Yuru.all(LocalContext.current)
    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        RetroWindow(theme, Modifier.width(760.dp).fillMaxHeight(0.92f), title = "🧸 ゆるキャラ ずかん（${list.size}）", titleColor = theme.titleBars[1], onClick = null) {
            Column(Modifier.fillMaxSize().padding(12.dp).verticalScroll(rememberScrollState())) {
                Text("けん や まち を しょうかい する キャラクター。ほんもの の 絵 は おうちのひと と しらべて みよう！", fontSize = 14.sp, color = theme.outline)
                list.forEach { y ->
                    Text(Japan.pref(y.pref).let { if (kana) it.kana else it.name }, fontSize = 13.sp, color = theme.outline.copy(alpha = 0.6f), modifier = Modifier.padding(top = 8.dp))
                    YuruBox(y, theme, kana)
                }
                RetroButton("とじる", theme, Color.White, fontSize = 16, modifier = Modifier.padding(top = 10.dp)) { onClose() }
            }
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

/** 地図 に 書く みじかい 名前（県・府・都 を とる。北海道 は そのまま） */
private fun shortName(p: Japan.Pref, kana: Boolean): String =
    if (kana) p.kana.removeSuffix("けん").removeSuffix("ふ").let { if (it == "とうきょうと") "とうきょう" else it }
    else p.name.removeSuffix("県").removeSuffix("府").let { if (it == "東京都") "東京" else it }

// ---------------------------------------------------------------- 🧸 ゆるキャラ の 絵（おやモード）

@Composable
fun YuruPicSection() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val v = com.theta.kidshome.data.YuruPack.version
    var target by remember { mutableStateOf<Int?>(null) }
    var open by remember { mutableStateOf(false) }
    val pickZip = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> if (uri != null) com.theta.kidshome.data.YuruPack.importZip(context, uri) }
    val pickImg = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        val pref = target
        if (uri != null && pref != null) scope.launch {
            withContext(Dispatchers.IO) {
                runCatching {
                    val b = android.graphics.ImageDecoder.decodeBitmap(android.graphics.ImageDecoder.createSource(context.contentResolver, uri)) { d, _, _ -> d.allocator = android.graphics.ImageDecoder.ALLOCATOR_SOFTWARE }
                    com.theta.kidshome.data.YuruPack.setOwn(context, pref, b)
                }
            }
        }
    }
    Section("🧸 ゆるキャラ の 絵（非公開）") {
        androidx.compose.material3.Text("ゆるキャラ の 絵 は 県・市 の 著作物 なので アプリ には 入って いません。家 の 中 だけ で つかう 絵 を 入れる と、にほん パズル の カード・ずかん に 出ます（ない ところ は 絵文字）。" +
            "まとめて：非公開 リポジトリ の yuru パック（43.png の よう に 都道府県 番号）を「📥 とってくる」。1 たい ずつ：下 の 📷 で ギャラリー から。", fontSize = 13.sp)
        Row {
            androidx.compose.material3.Button(enabled = !com.theta.kidshome.data.YuruPack.busy, onClick = { com.theta.kidshome.data.YuruPack.update(context) }) { androidx.compose.material3.Text("📥 とってくる") }
            Spacer(Modifier.width(8.dp))
            androidx.compose.material3.OutlinedButton(onClick = { pickZip.launch(arrayOf("application/zip", "application/octet-stream")) }) { androidx.compose.material3.Text("zip から") }
            Spacer(Modifier.width(8.dp))
            androidx.compose.material3.OutlinedButton(onClick = { open = !open }) { androidx.compose.material3.Text(if (open) "とじる" else "📷 1 たい ずつ") }
        }
        com.theta.kidshome.data.YuruPack.status?.let { androidx.compose.material3.Text(it, fontSize = 13.sp) }
        val list = remember { com.theta.kidshome.data.Yuru.all(context) }
        androidx.compose.material3.Text("絵 が ある：${remember(v) { list.count { com.theta.kidshome.data.YuruPack.image(context, it.pref) != null } }} / ${list.size}", fontSize = 13.sp)
        if (open) list.forEach { y ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                val has = remember(v) { com.theta.kidshome.data.YuruPack.image(context, y.pref) != null }
                androidx.compose.material3.Text("${Japan.pref(y.pref).name}　${y.name}" + if (has) "　✅" else "", fontSize = 14.sp, modifier = Modifier.weight(1f))
                androidx.compose.material3.TextButton(onClick = { target = y.pref; pickImg.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }) { androidx.compose.material3.Text("📷") }
                if (has) androidx.compose.material3.TextButton(onClick = { com.theta.kidshome.data.YuruPack.clearOwn(context, y.pref) }) { androidx.compose.material3.Text("けす") }
            }
        }
    }
}
