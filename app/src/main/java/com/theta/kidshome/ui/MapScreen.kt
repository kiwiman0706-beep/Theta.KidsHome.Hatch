package com.theta.kidshome.ui

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Paint
import android.graphics.drawable.BitmapDrawable
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.theta.kidshome.data.Atlas
import com.theta.kidshome.data.GeoLine
import com.theta.kidshome.data.LatLon
import com.theta.kidshome.data.LineKind
import com.theta.kidshome.data.Place
import com.theta.kidshome.data.Store
import com.theta.kidshome.data.Weather
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.osmdroid.config.Configuration
import org.osmdroid.events.MapEventsReceiver
import org.osmdroid.tileprovider.tilesource.ITileSource
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.tileprovider.tilesource.XYTileSource
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.CustomZoomButtonsController
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.MapEventsOverlay
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polyline
import java.io.File

private fun tileSource(style: String): ITileSource = when (style) {
    "osm" -> TileSourceFactory.MAPNIK
    "pale" -> XYTileSource("GSI_pale", 2, 18, 256, ".png", arrayOf("https://cyberjapandata.gsi.go.jp/xyz/pale/"), "地理院タイル")
    else -> XYTileSource("GSI_std", 2, 18, 256, ".png", arrayOf("https://cyberjapandata.gsi.go.jp/xyz/std/"), "地理院タイル")
}

private val styles = listOf("gsi" to "🗾 ちず", "pale" to "🌫️ うすい", "osm" to "🌍 OSM")

private fun LatLon.geo() = GeoPoint(lat, lon)

/** えもじ の ピン */
private fun pinIcon(context: Context, emoji: String, ring: Int, big: Boolean = false): BitmapDrawable {
    val d = context.resources.displayMetrics.density * (if (big) 1.3f else 1f)
    val r = 22 * d
    val w = (r * 2 + 6 * d).toInt(); val h = (r * 2 + 16 * d).toInt()
    val b = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
    val c = android.graphics.Canvas(b)
    val p = Paint(Paint.ANTI_ALIAS_FLAG)
    val cx = w / 2f; val cy = r + 3 * d
    p.color = ring
    val path = android.graphics.Path().apply { moveTo(cx - 9 * d, cy + r - 6 * d); lineTo(cx, h - 1f); lineTo(cx + 9 * d, cy + r - 6 * d); close() }
    c.drawPath(path, p)
    c.drawCircle(cx, cy, r, p)
    p.color = android.graphics.Color.WHITE
    c.drawCircle(cx, cy, r - 4 * d, p)
    p.textSize = r * 1.15f; p.textAlign = Paint.Align.CENTER
    val fm = p.fontMetrics
    c.drawText(emoji, cx, cy - (fm.ascent + fm.descent) / 2, p)
    return BitmapDrawable(context.resources, b)
}

/** 線の なまえ ふだ */
private fun labelIcon(context: Context, text: String, color: Int): BitmapDrawable {
    val d = context.resources.displayMetrics.density
    val p = Paint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 14 * d; typeface = android.graphics.Typeface.DEFAULT_BOLD }
    val tw = p.measureText(text)
    val b = Bitmap.createBitmap((tw + 20 * d).toInt(), (26 * d).toInt(), Bitmap.Config.ARGB_8888)
    val c = android.graphics.Canvas(b)
    val bg = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = color }
    c.drawRoundRect(0f, 0f, b.width.toFloat(), b.height.toFloat(), 12 * d, 12 * d, bg)
    p.color = android.graphics.Color.WHITE
    c.drawText(text, 10 * d, b.height / 2f - (p.fontMetrics.ascent + p.fontMetrics.descent) / 2, p)
    return BitmapDrawable(context.resources, b)
}

val lineColors = listOf(0xFF2E9E5B, 0xFFE85D75, 0xFF3D7BE0, 0xFFF29A2E, 0xFF8A5CD6, 0xFF1BA7B5).map { it.toInt() }

/**
 * ちず：おうち・がっこう・あそびに いく ところ を ピンで。
 * えらぶと おうちからの きょり と、車の みち（なぞるように かく）。高速道路・線路 を かさねて、ちりの かんかく を そだてる。
 * ✏️（おうちのひと かくにん のあと）で ばしょ・線 を ふやす。
 */
@Composable
fun MapScreen(onBack: () -> Unit) {
    BackHandler(onBack = onBack)
    val context = LocalContext.current
    val theme = themeById(Store.config.currentProfile.themeId)
    val scope = rememberCoroutineScope()
    var editing by remember { mutableStateOf(false) }
    var gate by remember { mutableStateOf(false) }
    var selected by remember { mutableStateOf<Place?>(null) }
    var editPlace by remember { mutableStateOf<Place?>(null) }
    var searching by remember { mutableStateOf(false) }
    var linesDialog by remember { mutableStateOf(false) }
    var here by remember { mutableStateOf<LatLon?>(null) }
    var routeFor by remember { mutableStateOf<Place?>(null) }
    var routeShown by remember { mutableStateOf(0) }

    val map = remember {
        Configuration.getInstance().apply {
            userAgentValue = context.packageName
            osmdroidBasePath = File(context.cacheDir, "osmdroid")
            osmdroidTileCache = File(context.cacheDir, "osmdroid/tiles")
        }
        MapView(context).apply {
            setMultiTouchControls(true)
            zoomController.setVisibility(CustomZoomButtonsController.Visibility.NEVER)
            minZoomLevel = 4.0; maxZoomLevel = 18.0
            isTilesScaledToDpi = true
            val h = Atlas.home
            controller.setZoom(if (h != null) 13.0 else 5.5)
            controller.setCenter(h?.at?.geo() ?: GeoPoint(36.3, 137.8))
        }
    }
    val routeLine = remember { Polyline(map).apply { outlinePaint.color = 0xFFFF4D8D.toInt(); outlinePaint.strokeWidth = 9 * context.resources.displayMetrics.density; outlinePaint.strokeCap = Paint.Cap.ROUND } }
    DisposableEffect(Unit) {
        map.onResume()
        onDispose { map.onPause(); map.onDetach() }
    }
    val askLocation = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        if (ok) scope.launch { Weather.locate(context)?.let { (la, lo) -> here = LatLon(la, lo); map.controller.animateTo(GeoPoint(la, lo), 14.0, 800L) } }
    }

    fun flyTo(p: LatLon, zoom: Double = 14.0) = map.controller.animateTo(p.geo(), zoom, 900L)

    // 車の みち を なぞるように かく
    LaunchedEffect(routeFor) {
        val r = routeFor?.route ?: run { routeShown = 0; return@LaunchedEffect }
        for (i in 2..r.size step maxOf(1, r.size / 60)) { routeShown = i; delay(30) }
        routeShown = r.size
    }

    fun rebuild(m: MapView) {
        m.setTileSource(tileSource(Atlas.style))
        m.overlays.clear()
        m.overlays += MapEventsOverlay(object : MapEventsReceiver {
            override fun singleTapConfirmedHelper(p: GeoPoint) = false.also { if (!editing) selected = null }
            override fun longPressHelper(p: GeoPoint): Boolean {
                if (!editing) return false
                editPlace = Place(name = "", lat = p.latitude, lon = p.longitude)
                return true
            }
        })
        val dens = context.resources.displayMetrics.density
        Atlas.lines.filter { it.visible }.forEach { l ->
            l.segments.forEach { seg ->
                m.overlays += Polyline(m).apply {
                    setPoints(seg.map { it.geo() })
                    outlinePaint.color = l.color; outlinePaint.strokeWidth = 6 * dens; outlinePaint.strokeCap = Paint.Cap.ROUND
                    setOnClickListener { _, _, _ -> Toast.makeText(context, "${l.kind.emoji} ${l.name}", Toast.LENGTH_SHORT).show(); true }
                }
            }
            l.segments.maxByOrNull { it.size }?.let { seg ->
                m.overlays += Marker(m).apply {
                    position = seg[seg.size / 2].geo(); icon = labelIcon(context, "${l.kind.emoji} ${l.name}", l.color)
                    setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER); setInfoWindow(null)
                    setOnMarkerClickListener { _, _ -> true }
                }
            }
        }
        routeFor?.let { p -> routeLine.setPoints(p.route.take(routeShown).map { it.geo() }); m.overlays += routeLine }
        Atlas.places.forEach { p ->
            m.overlays += Marker(m).apply {
                position = p.at.geo()
                icon = pinIcon(context, p.emoji, (if (p.home) theme.accent else theme.outline).toArgb(), big = selected?.id == p.id)
                setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM); setInfoWindow(null)
                setOnMarkerClickListener { _, _ ->
                    if (editing) editPlace = p else { selected = p; routeFor = null }
                    true
                }
            }
        }
        here?.let { h ->
            m.overlays += Marker(m).apply {
                position = h.geo(); icon = pinIcon(context, "🚗", 0xFF3D7BE0.toInt()); setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM); setInfoWindow(null)
            }
        }
        m.invalidate()
    }

    Column(Modifier.fillMaxSize().background(theme.bgBottom).windowInsetsPadding(WindowInsets.safeDrawing).padding(14.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            RetroIconButton("🏠", theme, onClick = onBack)
            DotText(if (editing) "ちず（へんしゅう）" else "ちず", theme, 28, theme.text, modifier = Modifier.padding(start = 4.dp))
            Spacer(Modifier.weight(1f))
            styles.forEach { (id, label) ->
                RetroButton(label, theme, if (Atlas.style == id) theme.titleBars[0] else Color.White, fontSize = 15) { Atlas.chooseStyle(id) }
            }
            RetroIconButton("🏡", theme) { Atlas.home?.let { flyTo(it.at, 14.0) } ?: Toast.makeText(context, "おうち が まだ ありません", Toast.LENGTH_SHORT).show() }
            RetroIconButton("📍", theme) {
                if (context.checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED) askLocation.launch(Manifest.permission.ACCESS_COARSE_LOCATION)
                else askLocation.launch(Manifest.permission.ACCESS_COARSE_LOCATION)
            }
            RetroIconButton("🗾", theme) { map.controller.animateTo(GeoPoint(36.3, 137.8), 5.5, 900L) }
            RetroIconButton("✏️", theme, editing) { if (editing) editing = false else gate = true }
        }
        Spacer(Modifier.height(10.dp))
        Row(Modifier.weight(1f)) {
            Box(Modifier.weight(1f).fillMaxHeight().clip(RoundedCornerShape(18.dp)).border(3.dp, theme.outline, RoundedCornerShape(18.dp))) {
                AndroidView({ map }, Modifier.fillMaxSize()) { m ->
                    // 読むことで、変わったら かきなおす
                    Atlas.places; Atlas.lines; Atlas.style; selected; here; routeShown; editing
                    rebuild(m)
                }
                Column(Modifier.align(Alignment.BottomEnd).padding(10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    RetroIconButton("➕", theme) { map.controller.zoomIn() }
                    RetroIconButton("➖", theme) { map.controller.zoomOut() }
                }
                Text(
                    if (Atlas.style == "osm") "© OpenStreetMap contributors" else "地理院タイル　みち © OpenStreetMap",
                    fontSize = 10.sp, color = Color.DarkGray,
                    modifier = Modifier.align(Alignment.BottomStart).background(Color.White.copy(alpha = 0.7f)).padding(horizontal = 4.dp),
                )
                if (editing) Text(
                    "ながおし で ばしょを ふやす・ピンを押すと なおす",
                    fontSize = 14.sp, color = Color.White,
                    modifier = Modifier.align(Alignment.TopCenter).padding(8.dp).clip(RoundedCornerShape(10.dp)).background(theme.outline.copy(alpha = 0.8f)).padding(horizontal = 10.dp, vertical = 4.dp),
                )
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.width(300.dp).fillMaxHeight()) {
                val sel = selected
                if (sel != null) {
                    PlaceCard(sel, theme, routeOn = routeFor?.id == sel.id) { routeFor = if (routeFor?.id == sel.id) null else sel; if (routeFor != null) flyToRoute(map, sel) }
                    Spacer(Modifier.height(10.dp))
                }
                RetroWindow(theme, Modifier.fillMaxWidth().weight(1f), title = "ばしょ ${Atlas.places.size}", titleColor = theme.titleBars[1 % theme.titleBars.size]) {
                    LazyColumn(Modifier.padding(6.dp)) {
                        val home = Atlas.home
                        items(Atlas.places.sortedBy { p -> if (p.home) -1.0 else home?.let { Atlas.km(it.at, p.at) } ?: 0.0 }, key = { it.id }) { p ->
                            Row(
                                Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp))
                                    .background(if (sel?.id == p.id) theme.titleBars[0] else Color.Transparent)
                                    .clickable { if (editing) editPlace = p else { selected = p; routeFor = null }; flyTo(p.at) }
                                    .padding(horizontal = 8.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(p.emoji, fontSize = 24.sp)
                                Text(p.name, fontSize = 17.sp, color = theme.outline, modifier = Modifier.weight(1f).padding(start = 8.dp), maxLines = 1)
                                if (!p.home && home != null) Text(Atlas.distanceText(Atlas.km(home.at, p.at)), fontSize = 12.sp, color = theme.outline.copy(alpha = 0.6f))
                            }
                        }
                        if (Atlas.places.isEmpty()) item {
                            Text("まだ ばしょが ありません。✏️ から おうちのひとが ふやせます", fontSize = 14.sp, color = theme.outline.copy(alpha = 0.7f), modifier = Modifier.padding(8.dp))
                        }
                    }
                }
                if (Atlas.lines.isNotEmpty()) {
                    Spacer(Modifier.height(10.dp))
                    RetroWindow(theme, Modifier.fillMaxWidth().heightIn(max = 200.dp), title = "せん", titleColor = theme.titleBars[2 % theme.titleBars.size]) {
                        LazyColumn(Modifier.padding(6.dp)) {
                            items(Atlas.lines, key = { it.id }) { l ->
                                Row(Modifier.fillMaxWidth().clickable { Atlas.updateLine(l.copy(visible = !l.visible)) }.padding(4.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Box(Modifier.size(width = 22.dp, height = 8.dp).clip(CircleShape).background(Color(l.color)))
                                    Text("${l.kind.emoji} ${l.name}", fontSize = 15.sp, color = theme.outline, modifier = Modifier.weight(1f).padding(start = 8.dp), maxLines = 1)
                                    Text(if (l.visible) "👀" else "🙈", fontSize = 18.sp)
                                }
                            }
                        }
                    }
                }
                if (editing) {
                    Spacer(Modifier.height(10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        RetroButton("🔍 さがす", theme, Color.White, fontSize = 16) { searching = true }
                        RetroButton("🛣️ せん", theme, Color.White, fontSize = 16) { linesDialog = true }
                    }
                }
            }
        }
    }

    if (gate) ParentGate(onSuccess = { gate = false; editing = true; selected = null }, onDismiss = { gate = false })
    editPlace?.let { p -> PlaceEditor(p, onDone = { editPlace = null }) }
    if (searching) SearchDialog(onPick = { f -> searching = false; flyTo(LatLon(f.lat, f.lon), 15.0); editPlace = Place(name = f.name, lat = f.lat, lon = f.lon) }, onDismiss = { searching = false })
    if (linesDialog) LinesDialog(onDismiss = { linesDialog = false })
}

private fun flyToRoute(map: MapView, p: Place) {
    if (p.route.size < 2) return
    val box = org.osmdroid.util.BoundingBox.fromGeoPoints(p.route.map { GeoPoint(it.lat, it.lon) })
    map.zoomToBoundingBox(box.increaseByScale(1.3f), true, 40)
}

@Composable
private fun PlaceCard(p: Place, theme: KidTheme, routeOn: Boolean, onRoute: () -> Unit) {
    val home = Atlas.home
    RetroWindow(theme, Modifier.fillMaxWidth(), title = p.name.ifBlank { "ばしょ" }, titleColor = theme.titleBars[0]) {
        Column(Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(p.emoji, fontSize = 44.sp)
                DotText(p.name, theme, 22, theme.outline, modifier = Modifier.padding(start = 8.dp))
            }
            if (p.memo.isNotBlank()) Text(p.memo, fontSize = 15.sp, color = theme.outline, modifier = Modifier.padding(top = 4.dp))
            if (home != null && !p.home) {
                Text("🏡 おうちから まっすぐ ${Atlas.distanceText(Atlas.km(home.at, p.at))}", fontSize = 15.sp, color = theme.outline, modifier = Modifier.padding(top = 6.dp))
                if (p.route.size >= 2) {
                    Text("🚗 みちのり ${Atlas.distanceText(p.routeKm)}・くるまで ${Atlas.timeText(p.routeMin)}", fontSize = 15.sp, color = theme.outline)
                    RetroButton(if (routeOn) "🚗 いきかた を けす" else "🚗 いきかた を みる", theme, if (routeOn) theme.titleBars[0] else Color.White, fontSize = 16, modifier = Modifier.padding(top = 8.dp), onClick = onRoute)
                }
            }
            if (p.home) DotText("ここが おうち！", theme, 16, theme.accent, modifier = Modifier.padding(top = 4.dp))
        }
    }
}

private val placeEmojis = listOf("🏡", "🏫", "🏥", "🛝", "🏞️", "🛒", "🍔", "👵", "🏊", "⛩️", "🚉", "🎡", "🐘", "🏖️", "⛰️", "🏯", "📚", "🎂")

@Composable
private fun PlaceEditor(p: Place, onDone: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var name by remember { mutableStateOf(p.name) }
    var emoji by remember { mutableStateOf(p.emoji) }
    var memo by remember { mutableStateOf(p.memo) }
    var home by remember { mutableStateOf(p.home) }
    var route by remember { mutableStateOf(Triple(p.route, p.routeKm, p.routeMin)) }
    var busy by remember { mutableStateOf(false) }
    val exists = Atlas.places.any { it.id == p.id }
    fun current() = p.copy(name = name.trim().ifBlank { "ばしょ" }, emoji = emoji.ifBlank { "📍" }, memo = memo.trim(), home = home, route = route.first, routeKm = route.second, routeMin = route.third)
    AlertDialog(
        onDismissRequest = onDone,
        title = { Text(if (exists) "ばしょを なおす" else "ばしょを ふやす") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                OutlinedTextField(name, { name = it }, singleLine = true, label = { Text("なまえ（ひらがな が おすすめ）") }, modifier = Modifier.fillMaxWidth())
                androidx.compose.foundation.lazy.LazyRow(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                    items(placeEmojis) { e ->
                        Text(e, fontSize = 26.sp, modifier = Modifier.clip(RoundedCornerShape(8.dp)).background(if (e == emoji) Color(0xFFFFD6E4) else Color.Transparent).clickable { emoji = e }.padding(4.dp))
                    }
                }
                OutlinedTextField(emoji, { emoji = it.take(4) }, singleLine = true, label = { Text("えもじ") }, modifier = Modifier.width(120.dp))
                OutlinedTextField(memo, { memo = it }, label = { Text("メモ（なにが ある？ だれと いく？）") }, modifier = Modifier.fillMaxWidth())
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("ここが おうち", modifier = Modifier.weight(1f)); Switch(home, { home = it })
                }
                val h = Atlas.home
                if (!home && h != null) {
                    Text(
                        if (route.first.size >= 2) "🚗 みち：${Atlas.distanceText(route.second)}・${Atlas.timeText(route.third)}" else "🚗 みち：まだ",
                        fontSize = 14.sp,
                    )
                    OutlinedButton(enabled = !busy, onClick = {
                        busy = true
                        scope.launch {
                            runCatching { Atlas.route(h.at, LatLon(p.lat, p.lon)) }
                                .onSuccess { route = it }
                                .onFailure { Toast.makeText(context, "みちを しらべられませんでした（${it.message}）", Toast.LENGTH_LONG).show() }
                            busy = false
                        }
                    }) { Text(if (busy) "しらべています…" else "🚗 おうちからの みちを しらべる") }
                } else if (!home) Text("先に「おうち」を ひとつ きめると、みちを しらべられます", fontSize = 12.sp, color = Color.Gray)
            }
        },
        confirmButton = { Button(onClick = { Atlas.upsert(current()); onDone() }) { Text("ほぞん") } },
        dismissButton = {
            Row {
                if (exists) TextButton(onClick = { Atlas.remove(p); onDone() }) { Text("けす", color = Color(0xFFD03B5C)) }
                TextButton(onClick = onDone) { Text("やめる") }
            }
        },
    )
}

@Composable
private fun SearchDialog(onPick: (Atlas.Found) -> Unit, onDismiss: () -> Unit) {
    val scope = rememberCoroutineScope()
    var q by remember { mutableStateOf("") }
    var results by remember { mutableStateOf<List<Atlas.Found>>(emptyList()) }
    var status by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("🔍 ばしょを さがす") },
        text = {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(q, { q = it }, singleLine = true, label = { Text("住所・地名・しせつの なまえ") }, modifier = Modifier.weight(1f))
                    TextButton(enabled = q.isNotBlank(), onClick = {
                        status = "さがしています…"
                        scope.launch {
                            runCatching { Atlas.search(q) }
                                .onSuccess { results = it; status = if (it.isEmpty()) "見つかりませんでした" else "" }
                                .onFailure { status = "さがせませんでした（${it.message}）" }
                        }
                    }) { Text("さがす") }
                }
                if (status.isNotEmpty()) Text(status, fontSize = 13.sp, color = Color.Gray)
                LazyColumn(Modifier.heightIn(max = 320.dp)) {
                    items(results) { f ->
                        Column(Modifier.fillMaxWidth().clickable { onPick(f) }.padding(vertical = 6.dp)) {
                            Text(f.name, fontWeight = FontWeight.Bold)
                            Text(f.detail, fontSize = 12.sp, color = Color.Gray, maxLines = 2)
                        }
                    }
                }
                Text("地図の上を ながおし しても ふやせます。検索は OpenStreetMap（Nominatim）です。", fontSize = 12.sp, color = Color.Gray)
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("とじる") } },
    )
}

@Composable
private fun LinesDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var kind by remember { mutableStateOf(LineKind.ROAD) }
    var name by remember { mutableStateOf("") }
    var color by remember { mutableStateOf(lineColors[0]) }
    var busy by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("🛣️ 高速道路・線路・川 を かさねる") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Atlas.lines.forEach { l ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(width = 22.dp, height = 8.dp).clip(CircleShape).background(Color(l.color)))
                        Text("${l.kind.emoji} ${l.name}", modifier = Modifier.weight(1f).padding(start = 8.dp))
                        Checkbox(l.visible, { v -> Atlas.updateLine(l.copy(visible = v)) })
                        TextButton(onClick = { Atlas.removeLine(l) }) { Text("けす", color = Color(0xFFD03B5C)) }
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    LineKind.entries.forEach { k -> FilterChip(kind == k, { kind = k }, label = { Text("${k.emoji} ${k.label}") }) }
                }
                OutlinedTextField(
                    name, { name = it }, singleLine = true, modifier = Modifier.fillMaxWidth(),
                    label = {
                        Text(
                            when (kind) {
                                LineKind.ROAD -> "なまえ（例：東名高速道路、首都高速、圏央道）"
                                LineKind.RAIL -> "なまえ（例：東海道新幹線、山手線）"
                                LineKind.RIVER -> "なまえ（例：多摩川）"
                            },
                        )
                    },
                )
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    lineColors.forEach { c ->
                        Box(Modifier.size(30.dp).clip(CircleShape).background(Color(c)).border(if (c == color) 3.dp else 0.dp, Color.Black, CircleShape).clickable { color = c })
                    }
                }
                Button(enabled = name.isNotBlank() && !busy, onClick = {
                    busy = true
                    scope.launch {
                        runCatching { Atlas.findLine(name, kind) }
                            .onSuccess { segs ->
                                if (segs.isEmpty()) Toast.makeText(context, "見つかりませんでした。正式な なまえで ためしてください", Toast.LENGTH_LONG).show()
                                else {
                                    Atlas.addLine(GeoLine(name = name.trim(), kind = kind, color = color, segments = segs))
                                    name = ""; color = lineColors[(lineColors.indexOf(color) + 1) % lineColors.size]
                                }
                            }
                            .onFailure { Toast.makeText(context, "さがせませんでした（${it.message}）", Toast.LENGTH_LONG).show() }
                        busy = false
                    }
                }) { Text(if (busy) "さがしています…（30びょう くらい）" else "🔍 さがして かさねる") }
                Text("OpenStreetMap の データ（Overpass）から、なまえに その文字が入っている 道・線路を あつめます。", fontSize = 12.sp, color = Color.Gray)
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("とじる") } },
    )
}
