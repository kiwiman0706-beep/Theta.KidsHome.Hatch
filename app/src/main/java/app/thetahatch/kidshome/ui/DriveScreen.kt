package app.thetahatch.kidshome.ui

import android.Manifest
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Looper
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.thetahatch.kidshome.data.DriveMap
import app.thetahatch.kidshome.data.Japan
import app.thetahatch.kidshome.data.Landmark
import app.thetahatch.kidshome.data.Landmarks
import app.thetahatch.kidshome.data.Store
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.hypot

/** きた みち（画面 を とじて も アプリ が うごいて いる あいだ は おぼえて おく） */
private object Trail {
    val pts = ArrayList<Pair<Double, Double>>()
    fun add(lat: Double, lon: Double) {
        val last = pts.lastOrNull()
        if (last == null || DriveMap.distKm(last.first, last.second, lat, lon) > 0.15) pts += lat to lon
        if (pts.size > 4000) pts.subList(0, 1000).clear()
    }
}

/** いつも の はんい（南・西・北・東）：大阪・兵庫 の 南 と 淡路島（洲本 の ホテル ニュー アワジ）まで */
private val HOME_BOX = doubleArrayOf(34.28, 134.80, 34.98, 135.72)

/** 路線 の ふだ 用 の みじかい 名前：「阪神高速3号神戸線」→「3号神戸線」、「中国自動車道」→「中国道」 */
private fun shortName(n: String) = n.removePrefix("阪神高速").removePrefix("道路").replace("高速道路", "").replace("自動車道", "道").ifBlank { n }

private val ROUTE_COLORS = listOf(
    Color(0xFF2E7D32), Color(0xFFE65100), Color(0xFF1565C0), Color(0xFFC2185B), Color(0xFF6A1B9A),
    Color(0xFF00838F), Color(0xFFAD1457), Color(0xFF558B2F), Color(0xFF4527A0), Color(0xFFEF6C00),
)
private val PREF_COLORS = mapOf(
    24 to Color(0xFFFFF3C4), 25 to Color(0xFFD7F0FF), 26 to Color(0xFFFFE0EC), 27 to Color(0xFFE6F7D9),
    28 to Color(0xFFFFE8CC), 29 to Color(0xFFEDE3FF), 30 to Color(0xFFDDF5EE),
)

/**
 * 🚗 いま どこ？：近畿 の 高速道路 の 路線図 に、いま いる ところ を 出す。
 * 「いま ○○道」「つぎ は ○○ IC」、おや が 登録 した めじるし（写真）が 近づく と 大きく 出る。
 * ネット は いらない（地図 は アプリ の 中、場所 は GPS）。
 */
@android.annotation.SuppressLint("MissingPermission")
@Composable
fun DriveScreen(onBack: () -> Unit) {
    BackHandler(onBack = onBack)
    val context = LocalContext.current
    val theme = themeById(Store.config.currentProfile.themeId)
    val density = LocalDensity.current
    val view = LocalView.current
    DisposableEffect(Unit) { view.keepScreenOn = true; onDispose { view.keepScreenOn = false } }
    remember { Landmarks.init(context) }
    val data by produceState<DriveMap.Data?>(null) { value = withContext(Dispatchers.IO) { DriveMap.load(context) } }

    fun hasLoc() = context.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
        context.checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
    var granted by remember { mutableStateOf(hasLoc()) }
    val ask = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { granted = hasLoc() }

    var fix by remember { mutableStateOf<Location?>(null) }
    var heading by remember { mutableStateOf<Float?>(null) }
    var prev by remember { mutableStateOf<Location?>(null) }
    DisposableEffect(granted) {
        val lm = context.getSystemService(LocationManager::class.java)
        val listener = LocationListener { loc ->
            val cur = fix
            // GPS を さき に（10 びょう いない の GPS が あれば ネットワーク の は つかわない）
            if (loc.provider != LocationManager.GPS_PROVIDER && cur?.provider == LocationManager.GPS_PROVIDER && loc.time - cur.time < 10_000) return@LocationListener
            fix = loc
            Trail.add(loc.latitude, loc.longitude)
            if (loc.hasBearing() && loc.speed > 2f) heading = loc.bearing
            else {
                val p = prev
                if (p == null) prev = loc
                else if (p.distanceTo(loc) > 80f) { heading = p.bearingTo(loc).let { if (it < 0) it + 360f else it }; prev = loc }
            }
        }
        if (granted && lm != null) {
            listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER).forEach { p ->
                runCatching {
                    if (lm.isProviderEnabled(p)) {
                        lm.requestLocationUpdates(p, 1000L, 5f, listener, Looper.getMainLooper())
                        if (fix == null) lm.getLastKnownLocation(p)?.let { fix = it }
                    }
                }
            }
        }
        onDispose { runCatching { lm?.removeUpdates(listener) } }
    }

    // 地図 の 見え方：まんなか（km、y は 下 むき）と 大きさ（1km が なん px か）
    var cx by remember { mutableFloatStateOf(Float.NaN) }
    var cy by remember { mutableFloatStateOf(0f) }
    var scale by remember { mutableFloatStateOf(1f) }
    var follow by remember { mutableStateOf(true) }
    var canvas by remember { mutableStateOf(IntSize.Zero) }
    var picked by remember { mutableStateOf<Any?>(null) }

    val d = data
    val photos by produceState<Map<String, ImageBitmap>>(emptyMap(), Landmarks.list, Landmarks.version) {
        value = withContext(Dispatchers.IO) { Landmarks.list.mapNotNull { l -> Landmarks.photo(l)?.let { l.id to it.asImageBitmap() } }.toMap() }
    }
    val where = remember(d, fix, heading) { if (d != null && fix != null) DriveMap.where(d, fix!!.latitude, fix!!.longitude, heading) else null }

    if (d == null) {
        Box(Modifier.fillMaxSize().background(theme.bgBottom), contentAlignment = Alignment.Center) {
            DotText("ちず を よみこみ ちゅう…", theme, 24, theme.text)
        }
        return
    }
    // 原点（データ の まんなか）
    val ox = remember(d) { DriveMap.kmX((d.bbox[1] + d.bbox[3]) / 2) }
    val oy = remember(d) { DriveMap.kmY((d.bbox[0] + d.bbox[2]) / 2) }
    fun lx(lon: Double) = (DriveMap.kmX(lon) - ox).toFloat()
    fun ly(lat: Double) = (oy - DriveMap.kmY(lat)).toFloat()
    val kinki = remember(d) {
        val xs = d.prefs.filter { it.kinki }.flatMap { p -> p.polys.flatMap { f -> (1 until f.size step 2).map { lx(f[it].toDouble()) } } }
        val ys = d.prefs.filter { it.kinki }.flatMap { p -> p.polys.flatMap { f -> (0 until f.size step 2).map { ly(f[it].toDouble()) } } }
        floatArrayOf(xs.min(), ys.min(), xs.max(), ys.max())
    }
    fun fitAll() {
        if (canvas == IntSize.Zero) return
        cx = (kinki[0] + kinki[2]) / 2; cy = (kinki[1] + kinki[3]) / 2
        scale = minOf(canvas.width / (kinki[2] - kinki[0]), canvas.height / (kinki[3] - kinki[1])) * 0.95f
    }
    // いつも の はんい：大阪・兵庫（神戸・三田・宝塚）〜 淡路島 の 洲本 まで
    fun fitHome() {
        if (canvas == IntSize.Zero) return
        val x0 = lx(HOME_BOX[1]); val x1 = lx(HOME_BOX[3]); val y0 = ly(HOME_BOX[2]); val y1 = ly(HOME_BOX[0])
        cx = (x0 + x1) / 2; cy = (y0 + y1) / 2
        scale = minOf(canvas.width / (x1 - x0), canvas.height / (y1 - y0))
    }
    LaunchedEffect(canvas) { if (cx.isNaN()) fitHome() }
    // ついて いく：大きさ は そのまま、車 が 画面 の はし に 近づいたら まんなか へ
    LaunchedEffect(fix, follow, canvas) {
        val f = fix ?: return@LaunchedEffect
        if (!follow || canvas == IntSize.Zero || cx.isNaN()) return@LaunchedEffect
        val X = canvas.width / 2f + (lx(f.longitude) - cx) * scale
        val Y = canvas.height / 2f + (ly(f.latitude) - cy) * scale
        val mx = canvas.width * 0.18f; val my = canvas.height * 0.22f
        if (X < mx || X > canvas.width - mx || Y < my || Y > canvas.height - my) { cx = lx(f.longitude); cy = ly(f.latitude) }
    }

    // 路線 と 県 の 線（km の 座標 で 1 回 だけ 作る）
    val prefPaths = remember(d) {
        d.prefs.map { p ->
            p to Path().apply {
                p.polys.forEach { f ->
                    moveTo(lx(f[1].toDouble()), ly(f[0].toDouble()))
                    var k = 2
                    while (k < f.size) { lineTo(lx(f[k + 1].toDouble()), ly(f[k].toDouble())); k += 2 }
                    close()
                }
            }
        }
    }
    val routePaths = remember(d) {
        var c = 0
        d.routes.map { r ->
            val color = ROUTE_COLORS[c++ % ROUTE_COLORS.size]
            Triple(r, color, Path().apply {
                r.lines.forEach { f ->
                    moveTo(lx(f[1].toDouble()), ly(f[0].toDouble()))
                    var k = 2
                    while (k < f.size) { lineTo(lx(f[k + 1].toDouble()), ly(f[k].toDouble())); k += 2 }
                }
            })
        }
    }
    val routeColor = remember(routePaths) { routePaths.associate { it.first to it.second } }
    val prefNames = remember(d) {
        d.prefs.filter { it.kinki }.map { p ->
            val f = p.polys.maxBy { it.size }
            val n = f.size / 2
            Triple(Japan.PREFS.firstOrNull { it.code == p.code }, (0 until n).sumOf { lx(f[it * 2 + 1].toDouble()).toDouble() } / n, (0 until n).sumOf { ly(f[it * 2].toDouble()).toDouble() } / n)
        }
    }
    val landmarkAt = remember(Landmarks.list) { Landmarks.list.groupBy { it.point } }

    // 📐 模式図（IC と IC の あいだ を たて・よこ・ななめ だけ で つなぐ。SA の 案内図 の よう に）か、🗺️ 本物 の 形 か
    val drivePrefs = remember { context.getSharedPreferences("drive", android.content.Context.MODE_PRIVATE) }
    var schema by remember { mutableStateOf(drivePrefs.getBoolean("schema", true)) }
    fun nodeX(i: Int) = lx(d.points[i].lon)
    fun nodeY(i: Int) = ly(d.points[i].lat)
    val schemaPaths = remember(d) {
        var c = 0
        d.routes.map { r ->
            val color = ROUTE_COLORS[c++ % ROUTE_COLORS.size]
            Triple(r, color, Path())
        }.also { list ->
            // つながり（IC と IC の あいだ）ごと に 折れ線。IC の ない 短い 路線 は 本物 の 形 の まま
            d.edges.filter { it.route >= 0 }.forEach { e ->
                val b = DriveMap.bend(nodeX(e.a), nodeY(e.a), nodeX(e.b), nodeY(e.b))
                list[e.route].third.apply { moveTo(b[0], b[1]); lineTo(b[2], b[3]); lineTo(b[4], b[5]); lineTo(b[6], b[7]) }
            }
            val has = d.edges.filter { it.route >= 0 }.map { it.route }.toSet()
            list.forEachIndexed { i, (r, _, path) ->
                if (i !in has) r.lines.forEach { f ->
                    path.moveTo(lx(f[1].toDouble()), ly(f[0].toDouble()))
                    var k = 2
                    while (k < f.size) { path.lineTo(lx(f[k + 1].toDouble()), ly(f[k].toDouble())); k += 2 }
                }
            }
        }
    }

    // 路線 の 名前 の ふだ を おく 場所 の こうほ（本物 の 形 用、模式図 用）
    val routeTags = remember(d) {
        d.routes.indices.associateWith { ri ->
            val r = d.routes[ri]
            val real = r.lines.sortedByDescending { it.size }.map { f -> val m = (f.size / 4) * 2; lx(f[m + 1].toDouble()) to ly(f[m].toDouble()) }
            val sch = d.edges.filter { it.route == ri }.sortedByDescending { it.km }.map { e ->
                val b = DriveMap.bend(nodeX(e.a), nodeY(e.a), nodeX(e.b), nodeY(e.b)); ((b[2] + b[4]) / 2) to ((b[3] + b[5]) / 2)
            }
            real to sch
        }.toList()
    }

    // 🏁 いく ところ：高速 だけ で いちばん 近そう な みち を ぬる
    remember { app.thetahatch.kidshome.data.Atlas.init(context) }
    var dest by remember { mutableStateOf<Int?>(null) }
    var destName by remember { mutableStateOf("") }
    val start = remember(d, where, fix) {
        val f = fix
        if (f != null) DriveMap.startPoint(d, where, f.latitude, f.longitude)
        else app.thetahatch.kidshome.data.Atlas.home?.let { DriveMap.startPoint(d, null, it.lat, it.lon) }  // GPS が まだ なら おうち から
    }
    val trip = remember(d, start, dest) { val a = start; val b = dest; if (a != null && b != null) DriveMap.trip(d, a, b) else null }
    val tripPath = remember(trip, schema) {
        val t = trip ?: return@remember null
        Path().apply {
            var cur = t.from
            moveTo(nodeX(cur), nodeY(cur))
            t.edges.forEach { e ->
                val next = if (e.a == cur) e.b else e.a
                if (schema || e.route < 0 || e.line < 0) {
                    val b = DriveMap.bend(nodeX(cur), nodeY(cur), nodeX(next), nodeY(next))
                    if (schema && e.route >= 0) { lineTo(b[2], b[3]); lineTo(b[4], b[5]) }
                    lineTo(nodeX(next), nodeY(next))
                } else {
                    val f = DriveMap.cut(d.routes[e.route].lines[e.line], e.ka, e.kb)
                    val pts = (0 until f.size / 2).map { lx(f[it * 2 + 1].toDouble()) to ly(f[it * 2].toDouble()) }
                    // cut は 線 の むき なので、いま の 点 に 近い ほう から
                    val ord = if (pts.isNotEmpty() && hypot(pts.first().first - nodeX(cur), pts.first().second - nodeY(cur)) > hypot(pts.last().first - nodeX(cur), pts.last().second - nodeY(cur))) pts.reversed() else pts
                    ord.forEach { lineTo(it.first, it.second) }
                    lineTo(nodeX(next), nodeY(next))
                }
                cur = next
            }
        }
    }
    // 模式図 の とき は 車 を 路線 の 上 に のせる
    val carSpot = remember(d, fix, schema) {
        val f = fix ?: return@remember null
        if (!schema) return@remember null
        val sp = DriveMap.spot(d, f.latitude, f.longitude) ?: return@remember null
        val sq = d.routes[sp.route].seqs[sp.seq]
        DriveMap.along(DriveMap.bend(nodeX(sq.pts[sp.k]), nodeY(sq.pts[sp.k]), nodeX(sq.pts[sp.k + 1]), nodeY(sq.pts[sp.k + 1])), sp.t)
    }

    Box(Modifier.fillMaxSize().background(Color(0xFFBFE3F5))) {
        Canvas(
            Modifier.fillMaxSize()
                .pointerInputTransform { centroid, pan, zoom ->
                    follow = false
                    val w = canvas.width / 2f; val h = canvas.height / 2f
                    val wx = cx + (centroid.x - w) / scale; val wy = cy + (centroid.y - h) / scale
                    val s2 = (scale * zoom).coerceIn(1f, 400f)
                    cx = wx - (centroid.x - w) / s2 - pan.x / s2
                    cy = wy - (centroid.y - h) / s2 - pan.y / s2
                    scale = s2
                }
                .pointerInputTap { pos ->
                    // さわった ところ の めじるし・IC を 出す
                    val w = canvas.width / 2f; val h = canvas.height / 2f
                    fun sx(lon: Double) = w + (lx(lon) - cx) * scale
                    fun sy(lat: Double) = h + (ly(lat) - cy) * scale
                    val r = with(density) { 36.dp.toPx() }
                    picked = Landmarks.list.minByOrNull { hypot(sx(it.lon) - pos.x, sy(it.lat) - pos.y) }?.takeIf { hypot(sx(it.lon) - pos.x, sy(it.lat) - pos.y) < r }
                        ?: d.points.minByOrNull { hypot(sx(it.lon) - pos.x, sy(it.lat) - pos.y) }?.takeIf { scale > 3 && hypot(sx(it.lon) - pos.x, sy(it.lat) - pos.y) < r * 0.7f }
                },
        ) {
            if (size.width.toInt() != canvas.width || size.height.toInt() != canvas.height) canvas = IntSize(size.width.toInt(), size.height.toInt())
            if (cx.isNaN()) return@Canvas
            val w = size.width / 2f; val h = size.height / 2f
            fun sx(lon: Double) = w + (lx(lon) - cx) * scale
            fun sy(lat: Double) = h + (ly(lat) - cy) * scale
            val px = 1f / scale
            withTransform({ translate(w - cx * scale, h - cy * scale); scale(scale, scale, Offset.Zero) }) {
                prefPaths.forEach { (p, path) ->
                    drawPath(path, if (p.kinki) PREF_COLORS[p.code] ?: Color(0xFFF5F5F5) else Color(0xFFEDEDED), alpha = if (schema) 0.55f else 1f)
                }
                prefPaths.forEach { (p, path) ->
                    drawPath(path, Color.White, style = Stroke(width = (if (p.kinki) 3.dp else 1.5.dp).toPx() * px, join = StrokeJoin.Round))
                }
                // 🏁 いく みち（下 に ふとく 黄色）
                tripPath?.let { drawPath(it, Color(0xEEFFD600), style = Stroke(width = 16.dp.toPx() * px, cap = StrokeCap.Round, join = StrokeJoin.Round)) }
                val lines = if (schema) schemaPaths else routePaths
                val wd = 5.dp.toPx() * px
                lines.forEach { (_, _, path) -> drawPath(path, Color.White, style = Stroke(width = wd + 3.dp.toPx() * px, cap = StrokeCap.Round, join = StrokeJoin.Round)) }
                lines.forEach { (_, color, path) -> drawPath(path, color, style = Stroke(width = wd, cap = StrokeCap.Round, join = StrokeJoin.Round)) }
                // きた みち（本物 の 形 の とき だけ）
                if (!schema && Trail.pts.size > 1) {
                    val trail = Path().apply {
                        moveTo(lx(Trail.pts[0].second), ly(Trail.pts[0].first))
                        Trail.pts.drop(1).forEach { lineTo(lx(it.second), ly(it.first)) }
                    }
                    drawPath(trail, Color(0xFFFF1744), style = Stroke(width = 4.dp.toPx() * px, cap = StrokeCap.Round, pathEffect = PathEffect.dashPathEffect(floatArrayOf(8.dp.toPx() * px, 6.dp.toPx() * px))))
                }
            }
            val nc = drawContext.canvas.nativeCanvas
            val fill = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply { textAlign = android.graphics.Paint.Align.CENTER; typeface = android.graphics.Typeface.DEFAULT_BOLD }
            val edge = android.graphics.Paint(fill).apply { style = android.graphics.Paint.Style.STROKE; color = android.graphics.Color.WHITE; strokeWidth = 4.dp.toPx() }
            // 字 は かさならない よう に：だいじ な もの から 置いて、もう 置いた 字 と かさなる もの は 出さない
            val taken = ArrayList<android.graphics.RectF>()
            fun label(text: String, x: Float, y: Float, sizeDp: Float, color: Color, force: Boolean = true): Boolean {
                fill.textSize = sizeDp.dp.toPx(); edge.textSize = fill.textSize
                val tw = fill.measureText(text)
                val r = android.graphics.RectF(x - tw / 2 - 2, y - fill.textSize, x + tw / 2 + 2, y + fill.textSize * 0.25f)
                if (!force && taken.any { android.graphics.RectF.intersects(it, r) }) return false
                taken += r
                fill.color = color.toArgb()
                nc.drawText(text, x, y, edge); nc.drawText(text, x, y, fill)
                return true
            }
            // めじるし の 写真 の ところ は あけて おく
            Landmarks.list.forEach { l ->
                val X = sx(l.lon); val Y = sy(l.lat); val r = 30.dp.toPx()
                taken += android.graphics.RectF(X - r, Y - r * 2 - 40.dp.toPx(), X + r, Y)
            }
            // IC・JCT・SA・PA の 点
            val shown = ArrayList<DriveMap.Point>()
            if (scale > 5) d.points.forEach { p ->
                if (p.kind != "ic" && p.kind != "jct" && scale < 14) return@forEach
                val X = sx(p.lon); val Y = sy(p.lat)
                if (X < -50 || Y < -50 || X > size.width + 50 || Y > size.height + 50) return@forEach
                val c = p.routes.firstOrNull()?.let { routeColor[d.routes[it]] } ?: Color.Gray
                when (p.kind) {
                    "jct" -> { drawCircle(Color.White, 4.dp.toPx(), Offset(X, Y)); drawCircle(Color.DarkGray, 4.dp.toPx(), Offset(X, Y), style = Stroke(2.dp.toPx())) }
                    "ic" -> { drawCircle(Color.White, 6.dp.toPx(), Offset(X, Y)); drawCircle(c, 6.dp.toPx(), Offset(X, Y), style = Stroke(3.dp.toPx())) }
                    else -> label("🅿️", X, Y + 5.dp.toPx(), 12f, Color.Black, force = false)
                }
                shown += p
            }
            // 名前：つぎ の IC → めじるし の IC → IC → JCT → SA・PA の じゅん
            where?.next?.let { n -> label(n.name, sx(n.lon), sy(n.lat) - 10.dp.toPx(), 16f, Color(0xFFD50000)) }
            if (scale > 8) shown.sortedBy { p ->
                when { p.name in landmarkAt -> 0; p.kind == "ic" -> 1; p.kind == "jct" -> 2; else -> 3 }
            }.forEach { p ->
                if (p == where?.next) return@forEach
                if (p.kind != "ic" && scale < 16) return@forEach
                label(p.name, sx(p.lon), sy(p.lat) - 10.dp.toPx(), 12f, Color(0xFF333333), force = false)
            }
            // 路線 の 名前 の ふだ（あいて いる ところ に、1 路線 3 まい まで）
            if (scale > 6) routeTags.forEach { (ri, spots) ->
                val r = d.routes[ri]
                val color = routeColor[r] ?: Color.Gray
                var n = 0
                for ((x, y) in (if (schema) spots.second else spots.first)) {
                    if (n >= 3) break
                    val X = w + (x - cx) * scale; val Y = h + (y - cy) * scale
                    if (X < 30 || Y < 30 || X > size.width - 30 || Y > size.height - 30) continue
                    val name = shortName(r.name)
                    fill.textSize = 12.dp.toPx()
                    val tw = fill.measureText(name)
                    val rect = android.graphics.RectF(X - tw / 2 - 6.dp.toPx(), Y - 9.dp.toPx(), X + tw / 2 + 6.dp.toPx(), Y + 9.dp.toPx())
                    if (taken.any { android.graphics.RectF.intersects(it, rect) }) continue
                    taken += android.graphics.RectF(rect.left - 40.dp.toPx(), rect.top - 20.dp.toPx(), rect.right + 40.dp.toPx(), rect.bottom + 20.dp.toPx())
                    val bg = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply { this.color = color.toArgb() }
                    nc.drawRoundRect(rect, 6.dp.toPx(), 6.dp.toPx(), bg)
                    fill.color = android.graphics.Color.WHITE
                    nc.drawText(name, X, Y + 4.dp.toPx(), fill)
                    n++
                }
            }
            // 県 の 名前（うすく、あいて いる ところ だけ）
            if (scale < 30) prefNames.forEach { (p, x, y) ->
                if (p != null) {
                    val X = w + (x.toFloat() - cx) * scale; val Y = h + (y.toFloat() - cy) * scale
                    if (label(p.name, X, Y, 18f, Color(0x99555555), force = false)) label(p.kana, X, Y + 16.dp.toPx(), 11f, Color(0x99555555), force = false)
                }
            }
            // めじるし（写真）
            Landmarks.list.forEach { l ->
                val X = sx(l.lon); val Y = sy(l.lat)
                val r = 26.dp.toPx()
                val c = Offset(X, Y - r - 10.dp.toPx())
                drawLine(Color(0xFF333333), Offset(X, Y), Offset(c.x, c.y + r), 2.dp.toPx())
                drawCircle(Color.White, r + 3.dp.toPx(), c)
                val img = photos[l.id]
                if (img != null) {
                    val clip = Path().apply { addOval(androidx.compose.ui.geometry.Rect(c, r)) }
                    drawContext.canvas.save()
                    drawContext.canvas.clipPath(clip)
                    drawImage(img, srcOffset = IntOffset.Zero, srcSize = IntSize(img.width, img.height), dstOffset = IntOffset((c.x - r).toInt(), (c.y - r).toInt()), dstSize = IntSize((r * 2).toInt(), (r * 2).toInt()))
                    drawContext.canvas.restore()
                } else label(l.emoji, c.x, c.y + 10.dp.toPx(), 26f, Color.Black)
                drawCircle(Color(0xFFFF6F00), r + 3.dp.toPx(), c, style = Stroke(3.dp.toPx()))
                label(l.name, c.x, c.y - r - 8.dp.toPx(), 14f, Color(0xFF4E342E))
            }
            // 🏁
            dest?.let { i ->
                val p = d.points[i]
                label("🏁", sx(p.lon) + 14.dp.toPx(), sy(p.lat) - 4.dp.toPx(), 30f, Color.Black)
            }
            // いま いる ところ
            fix?.let { f ->
                val X = carSpot?.let { w + (it.first - cx) * scale } ?: sx(f.longitude)
                val Y = carSpot?.let { h + (it.second - cy) * scale } ?: sy(f.latitude)
                drawCircle(Color(0x332962FF), 24.dp.toPx(), Offset(X, Y))
                heading?.let { hd ->
                    rotate(hd, Offset(X, Y)) {
                        val a = Path().apply {
                            moveTo(X, Y - 26.dp.toPx()); lineTo(X - 10.dp.toPx(), Y - 8.dp.toPx()); lineTo(X + 10.dp.toPx(), Y - 8.dp.toPx()); close()
                        }
                        drawPath(a, Color(0xFF2962FF))
                    }
                }
                drawCircle(Color.White, 11.dp.toPx(), Offset(X, Y))
                drawCircle(Color(0xFF2962FF), 8.dp.toPx(), Offset(X, Y))
                label("🚗", X, Y - 14.dp.toPx(), 26f, Color.Black)
            }
            fill.textAlign = android.graphics.Paint.Align.RIGHT
            fill.textSize = 10.dp.toPx(); fill.color = android.graphics.Color.DKGRAY
            nc.drawText("© OpenStreetMap contributors・Natural Earth", size.width - 8.dp.toPx(), size.height - 8.dp.toPx(), fill)
        }

        // 上：もどる と いま の ようす
        Column(Modifier.windowInsetsPadding(WindowInsets.safeDrawing).padding(12.dp)) {
            Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                RetroIconButton("🏠", theme, onClick = onBack)
                RetroWindow(theme, Modifier.widthIn(max = 560.dp), title = "🚗 いま どこ？") {
                    Column(Modifier.padding(12.dp)) {
                        val f = fix
                        when {
                            !granted -> {
                                Text("いま どこ に いる か を 出す には、場所（GPS）を つかって いい か きいて ね", fontSize = 15.sp)
                                Button(onClick = { ask.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)) }) { Text("📍 場所 を つかう") }
                            }
                            f == null -> Text("🛰 GPS で さがして いる よ…（まど の ちかく だと はやい）", fontSize = 16.sp)
                            else -> {
                                val w = where
                                val pref = w?.pref?.let { c -> Japan.PREFS.firstOrNull { it.code == c } }
                                Text(
                                    listOfNotNull(pref?.let { "📍 ${it.name}（${it.kana}）" }, if (f.hasSpeed()) "⏱ じそく ${(f.speed * 3.6f).toInt()} km" else null).joinToString("　"),
                                    fontSize = 16.sp,
                                )
                                if (w?.route != null) {
                                    Text("🛣 いま：${w.route.name}${if (w.route.ref.isNotBlank()) "（${w.route.ref}）" else ""}", fontSize = 20.sp, color = routeColor[w.route] ?: theme.outline)
                                } else Text("🛣 いま は ふつう の みち", fontSize = 16.sp)
                                if (w?.next != null) Text("➡️ つぎ：${w.next.name}　あと ${km(w.nextKm)}", fontSize = 20.sp, color = Color(0xFFD50000))
                                else if (w?.nearest != null) Text("🔎 いちばん ちかい IC：${w.nearest.name}（${km(w.nearestKm)}）", fontSize = 16.sp)
                            }
                        }
                        if (dest != null) {
                            val t = trip
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    when {
                                        t != null -> "🏁 $destName まで だいたい ${km(t.km)}（高速）"
                                        start == null -> "🏁 $destName：いま の 場所 が わかると みち を 出す よ"
                                        else -> "🏁 $destName：高速 で いける みち が 見つからない"
                                    },
                                    fontSize = 17.sp, modifier = Modifier.weight(1f, fill = false),
                                )
                                androidx.compose.material3.TextButton(onClick = { dest = null }) { Text("✖") }
                            }
                            t?.let { Text(it.steps(d).joinToString(" → "), fontSize = 15.sp, color = Color(0xFF6D4C41)) }
                        }
                    }
                }
            }
        }

        // めじるし が ちかい とき：大きく
        val near = remember(where, fix, Landmarks.list) {
            val f = fix ?: return@remember null
            Landmarks.list.map { it to DriveMap.distKm(f.latitude, f.longitude, it.lat, it.lon) }
                .filter { (l, km) -> km < 15 || (where?.next?.name == l.point && km < 40) }
                .minByOrNull { it.second }
        }
        near?.let { (l, kmTo) ->
            LandmarkCard(l, photos[l.id], "もうすぐ！ ${l.point}（あと ${km(kmTo)}）", Modifier.align(Alignment.BottomStart).padding(16.dp))
        }

        // さわった もの
        when (val p = picked) {
            is Landmark -> Column(Modifier.align(Alignment.BottomCenter).padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                LandmarkCard(p, photos[p.id], "${p.point} が いちばん ちかい", Modifier) { picked = null }
                Button(onClick = {
                    dest = d.points.indices.minByOrNull { DriveMap.distKm(p.lat, p.lon, d.points[it].lat, d.points[it].lon) + (if (d.points[it].name == p.point) 0.0 else 0.5) }
                    destName = p.name; picked = null
                }, modifier = Modifier.padding(top = 8.dp)) { Text("🏁 ここ へ いく みち", fontSize = 18.sp) }
            }
            is DriveMap.Point -> RetroWindow(theme, Modifier.align(Alignment.BottomCenter).padding(16.dp), title = when (p.kind) { "jct" -> "🔀 ジャンクション"; "sa" -> "🅿️ サービスエリア"; "pa" -> "🅿️ パーキングエリア"; else -> "🚪 インターチェンジ" }, onClick = { picked = null }) {
                Column(Modifier.padding(14.dp)) {
                    DotText(p.name, theme, 24, theme.outline)
                    Text(p.routes.joinToString("・") { d.routes[it].name }, fontSize = 14.sp)
                    fix?.let { Text("ここ から ${km(DriveMap.distKm(it.latitude, it.longitude, p.lat, p.lon))}（まっすぐ）", fontSize = 14.sp) }
                    Button(onClick = { dest = d.points.indexOf(p); destName = p.name; picked = null }, modifier = Modifier.padding(top = 6.dp)) { Text("🏁 ここ へ いく みち") }
                }
            }
        }

        // 右下：じぶん・ぜんぶ
        Column(Modifier.align(Alignment.BottomEnd).padding(16.dp, 16.dp, 16.dp, 30.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            RetroIconButton("🚗", theme, selected = follow) { follow = true; fix?.let { cx = lx(it.longitude); cy = ly(it.latitude); if (scale < 8) fitHome(); cx = lx(it.longitude); cy = ly(it.latitude) } }
            RetroIconButton("🗺️", theme) { follow = false; fitHome() }
            RetroIconButton("🌐", theme) { follow = false; fitAll() }
            RetroIconButton(if (schema) "📐" else "🌏", theme, selected = schema) { schema = !schema; drivePrefs.edit().putBoolean("schema", schema).apply() }
        }
    }
}

private fun km(v: Double) = if (v < 1) "${(v * 1000).toInt() / 100 * 100}m" else if (v < 10) "%.1fkm".format(v) else "${v.toInt()}km"

@Composable
private fun LandmarkCard(l: Landmark, img: ImageBitmap?, sub: String, modifier: Modifier, onClick: (() -> Unit)? = null) {
    val theme = themeById(Store.config.currentProfile.themeId)
    RetroWindow(theme, modifier, title = "${l.emoji} めじるし", onClick = onClick) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(120.dp).clip(RoundedCornerShape(16.dp)).border(3.dp, Color(0xFFFF6F00), RoundedCornerShape(16.dp)), contentAlignment = Alignment.Center) {
                if (img != null) Image(img, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop) else Text(l.emoji, fontSize = 56.sp)
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.widthIn(max = 320.dp)) {
                DotText(l.name, theme, 24, theme.outline)
                Text(sub, fontSize = 16.sp, color = Color(0xFFD50000))
            }
        }
    }
}

private fun Modifier.pointerInputTransform(on: (Offset, Offset, Float) -> Unit) =
    this.then(Modifier.pointerInput("transform") { detectTransformGestures { centroid, pan, zoom, _ -> on(centroid, pan, zoom) } })

private fun Modifier.pointerInputTap(on: (Offset) -> Unit) =
    this.then(Modifier.pointerInput("tap") { detectTapGestures(onTap = on) })

// ---------------------------------------------------------------- 🚗 めじるし の 登録（おやモード）

@android.annotation.SuppressLint("MissingPermission")
@Composable
fun DriveSection() {
    val context = LocalContext.current
    remember { Landmarks.init(context) }
    val data by produceState<DriveMap.Data?>(null) { value = withContext(Dispatchers.IO) { DriveMap.load(context) } }
    var name by remember { mutableStateOf("") }
    var emoji by remember { mutableStateOf("📍") }
    var query by remember { mutableStateOf("") }
    var point by remember { mutableStateOf<DriveMap.Point?>(null) }
    var photo by remember { mutableStateOf<android.net.Uri?>(null) }
    var here by remember { mutableStateOf<Location?>(null) }
    val pick = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { photo = it }
    val askLoc = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        val lm = context.getSystemService(LocationManager::class.java)
        here = runCatching { listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER).mapNotNull { lm?.getLastKnownLocation(it) }.maxByOrNull { it.time } }.getOrNull()
    }
    Section("🚗 いま どこ？ の めじるし") {
        Text(
            "ドライブ の とき、近畿 の 高速道路 の 路線図 に いま の 場所 を 出します（GPS。ネット は いりません）。" +
                "よく いく ところ を「めじるし」に すると、地図 に 写真 で 出て、その IC が 近づく と 大きく 出ます。",
            fontSize = 14.sp,
        )
        val d = data
        if (d == null) { Text("（地図 データ が まだ ありません）", fontSize = 13.sp, color = Color.Gray); return@Section }
        Landmarks.list.forEach { l ->
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 4.dp)) {
                val img by produceState<ImageBitmap?>(null, l.id, Landmarks.version) { value = withContext(Dispatchers.IO) { Landmarks.photo(l)?.asImageBitmap() } }
                Box(Modifier.size(48.dp).clip(RoundedCornerShape(10.dp)).background(Color(0xFFF2EEF5)), contentAlignment = Alignment.Center) {
                    img?.let { Image(it, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop) } ?: Text(l.emoji, fontSize = 24.sp)
                }
                Column(Modifier.padding(start = 10.dp).weight(1f)) {
                    Text("${l.emoji} ${l.name}", fontSize = 15.sp)
                    Text("いちばん ちかい：${l.point}", fontSize = 12.sp, color = Color.Gray)
                }
                androidx.compose.material3.TextButton(onClick = { Landmarks.remove(l) }) { Text("けす") }
            }
        }
        Spacer(Modifier.size(8.dp))
        Text("➕ めじるし を ふやす", fontSize = 15.sp)
        androidx.compose.material3.OutlinedTextField(name, { name = it }, label = { Text("なまえ（例：ありま グランドホテル）") }, singleLine = true)
        FlowRow8 {
            listOf("📍", "🏨", "♨️", "🛍️", "🏠", "👵", "🎢", "🦁", "🐟", "🏯", "⛩️", "🏖️", "🍜", "⚽").forEach { e ->
                androidx.compose.material3.FilterChip(selected = emoji == e, onClick = { emoji = e }, label = { Text(e, fontSize = 18.sp) })
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            androidx.compose.material3.OutlinedTextField(query, { query = it; point = null }, label = { Text("いちばん ちかい IC（例：西宮北）") }, singleLine = true, modifier = Modifier.weight(1f))
            androidx.compose.material3.OutlinedButton(
                onClick = { askLoc.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)) },
                modifier = Modifier.padding(start = 8.dp),
            ) { Text("📍 いま の 近く") }
        }
        val cands = remember(query, here, d) {
            val h = here
            when {
                h != null && query.isBlank() -> d.points.filter { it.kind == "ic" }.sortedBy { DriveMap.distKm(h.latitude, h.longitude, it.lat, it.lon) }.take(8)
                query.isNotBlank() -> d.points.filter { it.name.contains(query.trim()) }.take(12)
                else -> emptyList()
            }
        }
        FlowRow8 {
            cands.forEach { p ->
                androidx.compose.material3.FilterChip(
                    selected = point == p, onClick = { point = p },
                    label = { Text(p.name + (here?.let { "（${km(DriveMap.distKm(it.latitude, it.longitude, p.lat, p.lon))}）" } ?: "") + "・" + p.routes.joinToString("・") { d.routes[it].name }) },
                )
            }
        }
        if (query.isNotBlank() && cands.isEmpty()) Text("見つかりません（「IC」を つけず に、名前 の 一部 で）", fontSize = 12.sp, color = Color.Gray)
        Row(verticalAlignment = Alignment.CenterVertically) {
            androidx.compose.material3.OutlinedButton(onClick = { pick.launch(androidx.activity.result.PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }) {
                Text(if (photo == null) "🖼 写真 を えらぶ" else "🖼 写真 を かえる")
            }
            if (photo != null) Text("✅ 写真 あり", fontSize = 13.sp, modifier = Modifier.padding(start = 8.dp))
        }
        Button(
            enabled = name.isNotBlank() && point != null,
            onClick = {
                val p = point ?: return@Button
                Landmarks.add(context, Landmark(name = name.trim(), point = p.name, lat = p.lat, lon = p.lon, emoji = emoji), photo)
                name = ""; query = ""; point = null; photo = null; emoji = "📍"
            },
        ) { Text("とうろく") }
        Text(d.attribution + "（${d.made}）", fontSize = 11.sp, color = Color.Gray)
    }
}
