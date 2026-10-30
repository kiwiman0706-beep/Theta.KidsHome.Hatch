package com.theta.kidshome.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.theta.kidshome.data.Events
import com.theta.kidshome.data.GuidePack
import com.theta.kidshome.data.Profile
import com.theta.kidshome.data.Store
import com.theta.kidshome.data.Tile
import com.theta.kidshome.data.Weather
import kotlinx.coroutines.delay
import java.util.Calendar
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

/** 本棚の 1 だん／ショーウインドウの 1 だん。open があれば だん を押すと それを ひらく（おはなし など） */
class Shelf(val key: String, val emoji: String, val title: String, val tiles: List<Tile>, val open: (() -> Unit)? = null)

/** 部屋の絵は 1600×1000 の 目盛りで かく */
private const val RW = 1600f
private const val RH = 1000f
private const val FLOOR = 700f

private class R(val x: Float, val y: Float, val w: Float, val h: Float)

private val BOARD = R(40f, 40f, 660f, 440f)
private val WINDOW = R(790f, 45f, 300f, 274f)
private val PLATE = R(775f, 340f, 280f, 100f)
private val SWITCH = R(1100f, 360f, 44f, 64f)
private val SHELF = R(1160f, 35f, 410f, 910f)
private val GEAR = R(1470f, 18f, 100f, 100f)
private val APPS = R(1160f, 905f, 410f, 82f)
private val TV = R(60f, 545f, 320f, 260f)
private val RADIO = R(385f, 705f, 120f, 80f)
private val EASEL = R(515f, 470f, 200f, 400f)
private val PHONE = R(1072f, 362f, 82f, 70f)
private val PIANO = R(880f, 430f, 270f, 400f)
private val TOYBOX = R(860f, 640f, 270f, 330f)
private val MASCOT = R(705f, 720f, 190f, 230f)
private val GUIDE = R(700f, 520f, 200f, 470f)

private enum class Slide { CLOCK, CALENDAR, TIMETABLE }

/**
 * へや の ホーム：部屋に おくのは 6 つだけ（けいじばん・まど・ほんだな・テレビ・おもちゃばこ・でんわ）。
 * 本棚 と おもちゃばこ は、押すと 小さな まど で なかみ（だん ごと）を ひらく。
 */
@Composable
fun RoomView(
    theme: KidTheme,
    now: Long,
    profile: Profile,
    shelves: List<Shelf>,
    toys: List<Shelf>,
    calls: List<Tile>,
    hasTv: Boolean,
    locked: Boolean,
    lockText: String?,
    tvEmoji: String,
    mascotLine: String,
    playful: Boolean,
    updateBadge: Boolean,
    modifier: Modifier = Modifier,
    tileView: @Composable (Tile, Int) -> Unit,
    onGate: () -> Unit,
    onMascot: (Offset) -> Unit,
    onBoard: (String) -> Unit,
    onTv: () -> Unit,
    onRadio: () -> Unit,
    onCall: () -> Unit,
    onPiano: (() -> Unit)? = null,
    /** イーゼル：かいた 絵 を ひらく（null なら あたらしい 絵） */
    onEasel: ((String?) -> Unit)? = null,
    apps: List<Shelf> = emptyList(),
    initialPopup: String? = null,
) {
    val cal = Calendar.getInstance().apply { timeInMillis = now }
    val hour = cal.get(Calendar.HOUR_OF_DAY)
    val night = Store.config.nightDim && (hour >= 19 || hour < 6)
    var popup by remember { mutableStateOf(initialPopup) } // "shelf" / "toys"
    var popupKey by remember { mutableStateOf(shelves.firstOrNull { it.tiles.isNotEmpty() }?.key) }
    BoxWithConstraints(modifier, contentAlignment = Alignment.Center) {
        val u = minOf(maxWidth / RW, maxHeight / RH)
        val ox = (maxWidth - u * RW) / 2
        val oy = (maxHeight - u * RH) / 2
        Box(Modifier.size(u * RW, u * RH).clip(RoundedCornerShape(u * 28f)).border(3.dp, theme.outline, RoundedCornerShape(u * 28f))) {
            Canvas(Modifier.fillMaxSize()) { roomBack(theme) }

            // けいじばん（スライド）
            Board(theme, now, profile, u, onBoard)
            // まど
            Thing(WINDOW, u, 350f, 320f, onClick = { onBoard("clock") }) { window(theme, hour) }
            Weather.forecast?.let { f -> Tag("${Weather.emoji(f.code, f.isDay)} ${f.temp.toInt()}℃", WINDOW.x + WINDOW.w / 2, WINDOW.y + WINDOW.h - 80f, u, theme, Color.White) }
            // なふだ（子どもの きりかえ）と でんきの スイッチ（おうちのひと）
            NamePlate(theme, profile, u)
            // ほんだな
            if (shelves.isNotEmpty()) Bookshelf(theme, shelves, u) { s ->
                if (s.open != null && s.tiles.isEmpty()) s.open.invoke() else { popup = "shelf"; popupKey = s.key }
            }
            // テレビ・ラジオ
            if (hasTv) Thing(TV, u, 240f, 195f, onClick = onTv) { tv(theme) }
            if (hasTv) Overlay(TV, u) {
                Text(if (locked) "🔒" else tvEmoji, fontSize = (u.value * 90).sp, modifier = Modifier.align(Alignment.Center).offset(y = -u * 10f))
            }
            if (locked && lockText != null && hasTv) Tag(lockText, TV.x + TV.w / 2, TV.y + TV.h - 12f, u, theme, theme.titleBars[0])
            Thing(RADIO, u, 135f, 85f, onClick = onRadio) { radio(theme) }
            // でんわ
            // イーゼル：いままで かいた 絵 を ときどき かえて かざる
            onEasel?.let { open -> Easel(theme, u, open) }
            // ピアノ（がっき）：かべぎわ に。でんわ は ピアノ の うえ
            onPiano?.let { Thing(PIANO, u, 250f, 400f, onClick = it) { piano(theme) } }
            if (calls.isNotEmpty()) Thing(PHONE, u, 72f, 62f, onClick = onCall) { phone(theme) }
            // おもちゃばこ
            if (toys.isNotEmpty()) {
                Thing(TOYBOX, u, 270f, 330f, onClick = { popup = "toys"; popupKey = toys.first().key }) { toyChest(theme) }
                Overlay(TOYBOX, u) {
                    Text("🧸🪀⚽", fontSize = (u.value * 54).sp, modifier = Modifier.align(Alignment.TopCenter).offset(y = u * 92f))
                }
            }
            if (GuidePack.installed) {
                // 3D が うごくまで（うごかないとき も）えもじ の マスコット
                if (!GuidePack.viewReady) {
                    Mascot(theme, "", u, playful, onMascot)
                    GuidePack.viewStatus?.takeIf { !it.startsWith("よみこみ中") }?.let { msg ->
                        Box(Modifier.offset(u * GUIDE.x, u * (GUIDE.y + GUIDE.h - 60f)).size(u * GUIDE.w, u * 60f)) {
                            Text("⚠️ 3D：$msg", fontSize = 10.sp, color = Color(0xFFD03B5C), maxLines = 3)
                        }
                    }
                }
                Bubble(theme, mascotLine, GUIDE.x + GUIDE.w / 2, GUIDE.y - 10f, u)
                Box(
                    Modifier.offset(u * (GUIDE.x + GUIDE.w - 40f), u * (GUIDE.y + GUIDE.h - 110f)).size(u * 84f).clip(CircleShape)
                        .background(Color.White).border(2.dp, theme.outline, CircleShape).clickable { popup = "wardrobe" },
                    contentAlignment = Alignment.Center,
                ) { Text("👗", fontSize = (u.value * 44).sp) }
            } else Mascot(theme, mascotLine, u, playful, onMascot)
            if (night) Box(Modifier.fillMaxSize().background(Color(0xFF1B1F4A).copy(alpha = 0.22f)))

        }
        // 3D の ガイド は、まるく きりとった へや の そと に おく（WebView は きりとり の 中だと 出ないことが ある）
        if (GuidePack.installed && popup == null) {
            var center by remember { mutableStateOf(Offset.Zero) }
            Box(
                Modifier.align(Alignment.TopStart).offset(ox + u * GUIDE.x, oy + u * GUIDE.y).size(u * GUIDE.w, u * GUIDE.h)
                    .onGloballyPositioned { center = it.boundsInRoot().center },
            ) { GuideView(profile.id, Modifier.fillMaxSize(), onTap = { onMascot(center) }) }
        }
        // 小さな まど（本棚・ショーウインドウ・きせかえ）
        if (popup != null) Box(Modifier.align(Alignment.TopStart).offset(ox, oy).size(u * RW, u * RH)) {
            when (popup) {
                "shelf" -> Popup(theme, "📚 ほんだな", u, onClose = { popup = null }) {
                    ShelfPicker(theme, shelves, popupKey, u, onPick = { popupKey = it.key }, tileView = tileView)
                }
                "toys" -> Popup(theme, "🧸 おもちゃばこ", u, onClose = { popup = null }, glass = true) {
                    Showcase(theme, toys, u, tileView)
                }
                "apps" -> Popup(theme, "📱 アプリ", u, onClose = { popup = null }) { Showcase(theme, apps, u, tileView) }
                "wardrobe" -> Popup(theme, "👗 きせかえ", u, onClose = { popup = null }) { Wardrobe(theme, profile.id) }
            }
        }
    }
}

// ---------------------------------------------------------------- へや の もの

@Composable
private fun Thing(r: R, u: Dp, artW: Float, artH: Float, onClick: () -> Unit, draw: DrawScope.() -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val focused by interaction.collectIsFocusedAsState()
    val k by animateFloatAsState(if (pressed) 0.94f else if (focused) 1.05f else 1f, label = "press")
    Box(
        Modifier.offset(u * r.x, u * r.y).size(u * r.w, u * r.h).scale(k)
            .clickable(interactionSource = interaction, indication = null, onClick = onClick),
    ) {
        Canvas(Modifier.fillMaxSize()) {
            withTransform({ scale(size.width / artW, size.height / artH, pivot = Offset.Zero) }) { draw() }
            if (focused) drawRoundRect(Color(0xFFFFB300), style = Stroke(6.dp.toPx()), cornerRadius = CornerRadius(12.dp.toPx()))
        }
    }
}

@Composable
private fun Overlay(r: R, u: Dp, content: @Composable BoxScope.() -> Unit) {
    Box(Modifier.offset(u * r.x, u * r.y).size(u * r.w, u * r.h), content = content)
}

@Composable
private fun Tag(text: String, cx: Float, y: Float, u: Dp, theme: KidTheme, bg: Color) {
    Box(Modifier.offset(u * (cx - 160f), u * y).size(u * 320f, u * 60f), contentAlignment = Alignment.TopCenter) {
        Text(
            text, fontFamily = DotFont, fontSize = (u.value * 28).sp, color = theme.outline, maxLines = 1, softWrap = false,
            modifier = Modifier.clip(RoundedCornerShape(50)).background(bg).border(2.dp, theme.outline, RoundedCornerShape(50)).padding(horizontal = u * 16f, vertical = u * 2f),
        )
    }
}

@Composable
private fun NamePlate(theme: KidTheme, profile: Profile, u: Dp) {
    val profiles = Store.config.profiles
    Box(Modifier.offset(u * PLATE.x, u * PLATE.y).size(u * PLATE.w, u * PLATE.h)) {
        Canvas(Modifier.fillMaxSize()) {
            val k = size.width / PLATE.w
            withTransform({ scale(k, k, pivot = Offset.Zero) }) {
                drawLine(theme.outline, Offset(60f, 0f), Offset(160f, -40f), 4f)
                drawLine(theme.outline, Offset(260f, 0f), Offset(160f, -40f), 4f)
                box(Color(0xFFF2D3A6), 0f, 0f, PLATE.w, PLATE.h, theme.outline, 20f)
            }
        }
        Row(Modifier.fillMaxSize().padding(horizontal = u * 14f), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(profile.name, fontFamily = DotFont, fontSize = (u.value * 24).sp, color = theme.outline, maxLines = 1, softWrap = false)
                Text("の へや", fontFamily = DotFont, fontSize = (u.value * 20).sp, color = theme.outline)
            }
        }
    }
}

/** でんきの スイッチ：2 びょう ながおし で おうちのひと */
@Composable
private fun LightSwitch(theme: KidTheme, u: Dp, badge: Boolean, onTriggered: () -> Unit) {
    var progress by remember { mutableStateOf(0f) }
    var holding by remember { mutableStateOf(false) }
    LaunchedEffect(holding) {
        if (!holding) { progress = 0f; return@LaunchedEffect }
        val start = System.currentTimeMillis()
        while (holding) {
            progress = ((System.currentTimeMillis() - start) / 2000f).coerceAtMost(1f)
            if (progress >= 1f) { holding = false; onTriggered(); break }
            delay(30)
        }
    }
    Box(
        Modifier.size(u * SWITCH.w, u * SWITCH.h).clip(RoundedCornerShape(u * 8f)).background(Color.White)
            .border(2.dp, theme.outline, RoundedCornerShape(u * 8f))
            .pointerInputHold { holding = it },
        contentAlignment = Alignment.Center,
    ) {
        Box(Modifier.size(u * 18f, u * 34f).clip(RoundedCornerShape(u * 5f)).background(lerp(Color(0xFFE0E0E0), theme.accent, progress)))
        if (badge) Box(Modifier.align(Alignment.TopEnd).size(u * 14f).clip(CircleShape).background(Color(0xFFE85D75)))
    }
}

private fun Modifier.pointerInputHold(onHold: (Boolean) -> Unit) = this.then(
    Modifier.pointerInput(Unit) {
        detectTapGestures(onPress = { onHold(true); tryAwaitRelease(); onHold(false) })
    },
)

// ---------------------------------------------------------------- けいじばん

@Composable
private fun Board(theme: KidTheme, now: Long, profile: Profile, u: Dp, onOpen: (String) -> Unit) {
    val slides = buildList { add(Slide.CLOCK); add(Slide.CALENDAR); if (profile.hasTimetable) add(Slide.TIMETABLE) }
    var index by remember { mutableIntStateOf(0) }
    var auto by remember { mutableIntStateOf(0) }
    LaunchedEffect(index, auto, slides.size) {
        delay(10_000)
        index = (index + 1) % slides.size
    }
    val slide = slides[index.coerceIn(0, slides.lastIndex)]
    Box(Modifier.offset(u * BOARD.x, u * BOARD.y).size(u * BOARD.w, u * BOARD.h)) {
        Canvas(Modifier.fillMaxSize()) {
            val k = size.width / BOARD.w
            withTransform({ scale(k, k, pivot = Offset.Zero) }) { corkBoard(theme) }
        }
        // かみ（スライド）
        Box(
            Modifier.padding(start = u * 70f, end = u * 70f, top = u * 34f, bottom = u * 60f).fillMaxSize()
                .clip(RoundedCornerShape(u * 10f)).background(Color.White).border(3.dp, theme.outline, RoundedCornerShape(u * 10f))
                .pointerInput(slides.size) {
                    var dx = 0f
                    detectHorizontalDragGestures(onDragStart = { dx = 0f }, onDragEnd = {
                        if (abs(dx) > 60) { index = (index + (if (dx < 0) 1 else -1) + slides.size) % slides.size; auto++ }
                    }) { _, d -> dx += d }
                }
                .clickable { onOpen(if (slide == Slide.TIMETABLE) "timetable" else "clock") },
        ) {
            AnimatedContent(slide, transitionSpec = { fadeIn(tween(400)) togetherWith fadeOut(tween(400)) }, label = "slide") { s ->
                when (s) {
                    Slide.CLOCK -> ClockSlide(theme, now, u)
                    Slide.CALENDAR -> CalendarSlide(theme, now, u)
                    Slide.TIMETABLE -> TimetableSlide(theme, profile, now, u)
                }
            }
            // ピン
            Box(Modifier.align(Alignment.TopCenter).offset(y = -u * 10f).size(u * 22f).clip(CircleShape).background(Color(0xFFE85D75)).border(2.dp, theme.outline, CircleShape))
        }
        // ◀ ▶ と ●○○
        listOf(-1 to Alignment.CenterStart, 1 to Alignment.CenterEnd).forEach { (d, a) ->
            Box(
                Modifier.align(a).padding(horizontal = u * 12f).size(u * 50f).clip(CircleShape).background(Color.White.copy(alpha = 0.85f))
                    .border(2.dp, theme.outline, CircleShape).clickable { index = (index + d + slides.size) % slides.size; auto++ },
                contentAlignment = Alignment.Center,
            ) { Text(if (d < 0) "◀" else "▶", fontSize = (u.value * 22).sp, color = theme.outline) }
        }
        Row(Modifier.align(Alignment.BottomCenter).padding(bottom = u * 18f), horizontalArrangement = Arrangement.spacedBy(u * 12f)) {
            slides.indices.forEach { i ->
                Box(Modifier.size(u * 18f).clip(CircleShape).background(if (i == index) theme.outline else Color.White).border(2.dp, theme.outline, CircleShape))
            }
        }
    }
}

@Composable
private fun ClockSlide(theme: KidTheme, now: Long, u: Dp) {
    val cal = Calendar.getInstance().apply { timeInMillis = now }
    val f = Weather.forecast
    Row(Modifier.fillMaxSize().padding(u * 20f), verticalAlignment = Alignment.CenterVertically) {
        Canvas(Modifier.size(u * 210f)) {
            val k = size.width / 150f
            withTransform({ scale(k, k, pivot = Offset.Zero) }) { clock(theme, cal) }
        }
        Column(Modifier.weight(1f).padding(start = u * 24f), horizontalAlignment = Alignment.CenterHorizontally) {
            Text("%d:%02d".format(cal.get(Calendar.HOUR_OF_DAY), cal.get(Calendar.MINUTE)), fontFamily = DotFont, fontSize = (u.value * 64).sp, color = theme.outline)
            if (f != null) {
                Text(Weather.emoji(f.code, f.isDay), fontSize = (u.value * 56).sp)
                Text(Weather.label(f.code), fontFamily = DotFont, fontSize = (u.value * 20).sp, color = theme.outline, textAlign = TextAlign.Center, maxLines = 1, softWrap = false)
                Text("いま ${f.temp.toInt()}℃", fontFamily = DotFont, fontSize = (u.value * 30).sp, color = theme.outline)
                f.days.firstOrNull()?.let { d -> Text("${d.max.toInt()}℃ / ${d.min.toInt()}℃  ☂${d.rain}%", fontFamily = DotFont, fontSize = (u.value * 22).sp, color = theme.outline.copy(alpha = 0.7f)) }
            }
        }
    }
}

@Composable
private fun CalendarSlide(theme: KidTheme, now: Long, u: Dp) {
    val today = Calendar.getInstance().apply { timeInMillis = now }
    val start = (today.clone() as Calendar).apply {
        set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        add(Calendar.DAY_OF_MONTH, -(get(Calendar.DAY_OF_WEEK) - 1))
    }
    val names = listOf("にち", "げつ", "か", "すい", "もく", "きん", "ど")
    Column(Modifier.fillMaxSize().padding(u * 18f), horizontalAlignment = Alignment.CenterHorizontally) {
        Text("${today.get(Calendar.MONTH) + 1}がつ ${today.get(Calendar.DAY_OF_MONTH)}にち ${names[today.get(Calendar.DAY_OF_WEEK) - 1]}ようび", fontFamily = DotFont, fontSize = (u.value * 34).sp, color = theme.outline)
        Row(Modifier.fillMaxWidth().padding(top = u * 10f), horizontalArrangement = Arrangement.SpaceEvenly) {
            for (i in 0 until 7) {
                val d = (start.clone() as Calendar).apply { add(Calendar.DAY_OF_MONTH, i) }
                val isToday = d.get(Calendar.DAY_OF_YEAR) == today.get(Calendar.DAY_OF_YEAR)
                val ev = Events.on(d.timeInMillis)
                Column(
                    Modifier.width(u * 66f).clip(RoundedCornerShape(u * 10f)).background(if (isToday) theme.titleBars[0] else Color.Transparent).padding(vertical = u * 4f),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(names[i], fontFamily = DotFont, fontSize = (u.value * 18).sp, color = if (i == 0) Color(0xFFE85D75) else if (i == 6) Color(0xFF3D7BE0) else theme.outline)
                    Text("${d.get(Calendar.DAY_OF_MONTH)}", fontFamily = DotFont, fontSize = (u.value * 34).sp, color = theme.outline)
                    Row { ev.take(3).forEach { e -> Box(Modifier.padding(u * 1f).size(u * 9f).clip(CircleShape).background(Color(e.color))) } }
                }
            }
        }
        val todayStart = (today.clone() as Calendar).apply { set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0) }.timeInMillis
        val list = (Events.on(todayStart).map { "きょう" to it } + Events.on(todayStart + 86_400_000L).map { "あした" to it }).take(3)
        Column(Modifier.fillMaxWidth().padding(top = u * 10f)) {
            if (list.isEmpty()) Text("よていは ないよ", fontFamily = DotFont, fontSize = (u.value * 24).sp, color = theme.outline.copy(alpha = 0.6f), modifier = Modifier.align(Alignment.CenterHorizontally))
            list.forEach { (day, e) ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(u * 16f).clip(CircleShape).background(Color(e.color)))
                    Text(" $day  ${e.title}", fontFamily = DotFont, fontSize = (u.value * 24).sp, color = theme.outline, maxLines = 1)
                }
            }
        }
    }
}

@Composable
private fun TimetableSlide(theme: KidTheme, profile: Profile, now: Long, u: Dp) {
    val (day, tomorrow) = timetableDay(now)
    val list = profile.timetable.getOrNull(day).orEmpty().filter { it.isNotBlank() }
    val bring = profile.belongings.getOrNull(day).orEmpty()
    Column(Modifier.fillMaxSize().padding(u * 18f), horizontalAlignment = Alignment.CenterHorizontally) {
        Text("${if (tomorrow) "あした" else "きょう"}の じかんわり（${SchoolDays.getOrElse(day) { "" }}）", fontFamily = DotFont, fontSize = (u.value * 30).sp, color = theme.outline)
        Row(Modifier.fillMaxWidth().padding(top = u * 12f), horizontalArrangement = Arrangement.SpaceEvenly) {
            list.take(7).forEachIndexed { i, s ->
                val (emoji, color) = subjectStyle(s)
                Column(
                    Modifier.width(u * 74f).clip(RoundedCornerShape(u * 10f)).background(color).border(2.dp, theme.outline.copy(alpha = 0.4f), RoundedCornerShape(u * 10f)).padding(vertical = u * 6f),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text("${i + 1}", fontFamily = DotFont, fontSize = (u.value * 16).sp, color = theme.outline.copy(alpha = 0.6f))
                    Text(emoji, fontSize = (u.value * 34).sp)
                    Text(s, fontFamily = DotFont, fontSize = (u.value * 15).sp, color = theme.outline, maxLines = 1)
                }
            }
        }
        if (bring.isNotBlank()) Text("もちもの：$bring", fontFamily = DotFont, fontSize = (u.value * 24).sp, color = theme.outline, modifier = Modifier.padding(top = u * 12f))
    }
}

// ---------------------------------------------------------------- ほんだな

@Composable
private fun Bookshelf(theme: KidTheme, shelves: List<Shelf>, u: Dp, onShelf: (Shelf) -> Unit) {
    val o = theme.outline
    Box(Modifier.offset(u * SHELF.x, u * SHELF.y).size(u * SHELF.w, u * SHELF.h)) {
        Canvas(Modifier.fillMaxSize()) {
            val k = size.width / SHELF.w
            withTransform({ scale(k, k, pivot = Offset.Zero) }) { box(WoodDark, 0f, 0f, SHELF.w, SHELF.h, o, 14f) }
        }
        Column(Modifier.fillMaxSize().padding(u * 16f), verticalArrangement = Arrangement.spacedBy(u * 10f)) {
            shelves.forEachIndexed { i, s ->
                val interaction = remember { MutableInteractionSource() }
                val pressed by interaction.collectIsPressedAsState()
                Box(
                    Modifier.weight(1f).fillMaxWidth().scale(if (pressed) 0.95f else 1f).clip(RoundedCornerShape(u * 8f)).background(Wood)
                        .clickable(interactionSource = interaction, indication = null) { onShelf(s) },
                ) {
                    // 本（なかみ の えもじ を せびょうし に）
                    Row(Modifier.align(Alignment.BottomStart).padding(start = u * 8f, bottom = u * 8f), verticalAlignment = Alignment.Bottom) {
                        val books = s.tiles.map { it.emoji }.ifEmpty { listOf(s.emoji) }
                        books.take(5).forEachIndexed { j, e ->
                            Box(
                                Modifier.padding(end = u * 4f).size(u * 54f, u * (96f + (j % 3) * 12f)).clip(RoundedCornerShape(u * 6f))
                                    .background(BookColors[(i * 3 + j) % BookColors.size]).border(2.dp, o, RoundedCornerShape(u * 6f)),
                                contentAlignment = Alignment.Center,
                            ) { Text(e, fontSize = (u.value * 30).sp) }
                        }
                    }
                    // なふだ
                    Text(
                        "${s.emoji} ${s.title}", fontFamily = DotFont, fontSize = (u.value * 24).sp, color = o, maxLines = 1, softWrap = false,
                        modifier = Modifier.align(Alignment.TopEnd).padding(u * 6f).clip(RoundedCornerShape(u * 8f)).background(Color.White).border(2.dp, o, RoundedCornerShape(u * 8f)).padding(horizontal = u * 10f),
                    )
                    Box(Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(u * 8f).background(WoodDark))
                }
            }
        }
    }
}

private val BookColors = listOf(Color(0xFFFF8FB1), Color(0xFF6FB7FF), Color(0xFFFFD84D), Color(0xFF8BD17C), Color(0xFFB79CFF), Color(0xFFFFB067))

@Composable
private fun ShelfPicker(theme: KidTheme, shelves: List<Shelf>, key: String?, u: Dp, onPick: (Shelf) -> Unit, tileView: @Composable (Tile, Int) -> Unit) {
    val cur = shelves.firstOrNull { it.key == key } ?: shelves.first()
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp, vertical = 8.dp)) {
            shelves.forEach { s ->
                RetroButton("${s.emoji} ${s.title}", theme, if (s.key == cur.key) theme.titleBars[1 % theme.titleBars.size] else Color.White, fontSize = 18, modifier = Modifier.padding(end = 8.dp)) {
                    if (s.open != null && s.tiles.isEmpty()) s.open.invoke() else onPick(s)
                }
            }
        }
        Row(Modifier.fillMaxSize().horizontalScroll(rememberScrollState()).padding(16.dp), horizontalArrangement = Arrangement.spacedBy(20.dp), verticalAlignment = Alignment.CenterVertically) {
            cur.tiles.forEachIndexed { i, t -> Box(Modifier.width(200.dp)) { tileView(t, i) } }
        }
    }
}

// ---------------------------------------------------------------- おもちゃばこ（ショーウインドウ）

@Composable
private fun Showcase(theme: KidTheme, toys: List<Shelf>, u: Dp, tileView: @Composable (Tile, Int) -> Unit) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp)) {
        toys.forEach { s ->
            Text(
                "${s.emoji} ${s.title}", fontFamily = DotFont, fontSize = 20.sp, color = theme.outline,
                modifier = Modifier.padding(top = 10.dp, bottom = 6.dp).clip(RoundedCornerShape(8.dp)).background(Color.White).border(2.dp, theme.outline, RoundedCornerShape(8.dp)).padding(horizontal = 12.dp, vertical = 2.dp),
            )
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                s.tiles.forEachIndexed { i, t -> Box(Modifier.width(180.dp)) { tileView(t, i) } }
            }
            // ガラスの たな
            Box(Modifier.fillMaxWidth().padding(top = 6.dp).height(14.dp).clip(RoundedCornerShape(4.dp)).background(WoodDark).border(2.dp, theme.outline, RoundedCornerShape(4.dp)))
        }
    }
}

@Composable
private fun Popup(theme: KidTheme, title: String, u: Dp, onClose: () -> Unit, glass: Boolean = false, content: @Composable () -> Unit) {
    Box(
        Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.28f)).pointerInput(Unit) { detectTapGestures { onClose() } },
        contentAlignment = Alignment.Center,
    ) {
        RetroWindow(
            theme, Modifier.size(u * 1250f, u * 820f).pointerInput(Unit) { detectTapGestures { } },
            title = title, titleColor = theme.titleBars[0],
            body = if (glass) Color(0xFFEAF6FF) else Color(0xFFFFFDFE),
        ) {
            Box(Modifier.fillMaxSize()) {
                if (glass) Canvas(Modifier.fillMaxSize()) {
                    // ガラスの ひかり
                    for (i in 0 until 3) drawLine(Color.White.copy(alpha = 0.7f), Offset(size.width * (0.1f + i * 0.08f), 0f), Offset(size.width * (0.0f + i * 0.08f), size.height * 0.4f), 14f)
                }
                content()
                Box(Modifier.align(Alignment.TopEnd).padding(8.dp)) { RetroIconButton("✖️", theme, size = 52.dp, onClick = onClose) }
            }
        }
    }
}

@Composable
private fun Mascot(theme: KidTheme, line: String, u: Dp, playful: Boolean, onTap: (Offset) -> Unit) {
    val idle = rememberInfiniteTransition(label = "mascot")
    val bob by idle.animateFloat(0f, -10f, infiniteRepeatable(tween(900), RepeatMode.Reverse), label = "bob")
    var shown by remember(line) { mutableStateOf(true) }
    LaunchedEffect(line) { delay(9000); shown = false }
    val x = MASCOT.x; val y = MASCOT.y
    Box(Modifier.offset(u * (x + 40f), u * (y + 214f)).size(u * 120f, u * 22f).clip(RoundedCornerShape(50)).background(Color.Black.copy(alpha = 0.12f)))
    var center by remember { mutableStateOf(Offset.Zero) }
    Box(
        Modifier.offset(u * x, u * (y + (if (playful) bob else 0f))).size(u * MASCOT.w, u * MASCOT.h)
            .onGloballyPositioned { center = it.boundsInRoot().center }
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {
                shown = true
                onTap(center)
            },
        contentAlignment = Alignment.BottomCenter,
    ) { Text(theme.mascot, fontSize = (u.value * 140).sp, maxLines = 1, softWrap = false) }
    if (shown && line.isNotBlank()) {
        Box(Modifier.offset(u * (x - 80f), u * (y - 110f)).size(u * 360f, u * 110f), contentAlignment = Alignment.BottomCenter) {
            Box(
                Modifier.wrapContentSize().clip(RoundedCornerShape(u * 22f)).background(theme.bubbles[0])
                    .border(2.5.dp, theme.outline, RoundedCornerShape(u * 22f)).padding(horizontal = u * 18f, vertical = u * 8f),
            ) { Text(line, fontFamily = DotFont, fontSize = (u.value * 28).sp, color = theme.outline, textAlign = TextAlign.Center) }
        }
    }
}

// ---------------------------------------------------------------- へや の え

private fun DrawScope.roomBack(t: KidTheme) {
    val k = size.width / RW
    withTransform({ scale(k, k, pivot = Offset.Zero) }) {
        drawRect(t.bgTop, size = Size(RW, FLOOR))
        for (x in 0..32) for (y in 0..14) {
            if ((x + y) % 2 == 0) drawCircle(t.grid.copy(alpha = 0.55f), 5f, Offset(x * 50f + 25f, y * 50f + 25f))
        }
        drawRect(Wood, Offset(0f, FLOOR), Size(RW, RH - FLOOR))
        for (i in 0..6) drawLine(WoodDark.copy(alpha = 0.55f), Offset(0f, FLOOR + 30f + i * 48f), Offset(RW, FLOOR + 30f + i * 48f), 3f)
        for (i in 0..20) {
            val row = i % 6
            drawLine(WoodDark.copy(alpha = 0.4f), Offset((i * 173f + row * 61f) % RW, FLOOR + 30f + row * 48f), Offset((i * 173f + row * 61f) % RW, FLOOR + 78f + row * 48f), 3f)
        }
        drawRect(Color.White, Offset(0f, FLOOR - 16f), Size(RW, 22f))
        drawLine(t.outline, Offset(0f, FLOOR - 16f), Offset(RW, FLOOR - 16f), 4f)
        drawLine(t.outline, Offset(0f, FLOOR + 6f), Offset(RW, FLOOR + 6f), 4f)
        val o = t.outline
        // テレビ台
        box(WoodDark, 40f, 790f, 470f, 170f, o, 12f)
        box(Wood, 56f, 812f, 213f, 130f, o, 8f)
        box(Wood, 281f, 812f, 213f, 130f, o, 8f)
        dot(o, 255f, 877f, 7f); dot(o, 295f, 877f, 7f)
        // ラグ
        drawOval(lerp(t.titleBars[0], Color.White, 0.3f), Offset(600f, 900f), Size(560f, 90f))
        drawOval(o, Offset(600f, 900f), Size(560f, 90f), style = Stroke(5f))
    }
}

private fun DrawScope.corkBoard(t: KidTheme) {
    box(WoodDark, 0f, 0f, BOARD.w, BOARD.h, t.outline, 16f)
    drawRoundRect(Color(0xFFE6BC86), Offset(16f, 16f), Size(BOARD.w - 32f, BOARD.h - 32f), CornerRadius(8f))
    for (i in 0 until 140) drawCircle(Color(0xFFCF9F66), 3f, Offset(24f + (i * 53f) % (BOARD.w - 48f), 24f + (i * 31f) % (BOARD.h - 48f)))
}

/** ふた の あいた おもちゃばこ（270×330） */
private fun DrawScope.toyChest(t: KidTheme) {
    val o = t.outline
    val c = t.titleBars[2 % t.titleBars.size]
    // ふた（うしろに ひらいている）
    val lid = Path().apply { moveTo(20f, 160f); lineTo(40f, 40f); lineTo(250f, 40f); lineTo(250f, 160f); close() }
    drawPath(lid, lerp(c, Color.Black, 0.1f)); drawPath(lid, o, style = Stroke(5f))
    box(c, 0f, 150f, 270f, 175f, o, 14f)
    drawRoundRect(Color.White.copy(alpha = 0.5f), Offset(24f, 190f), Size(222f, 14f), CornerRadius(7f))
    listOf(60f, 135f, 210f).forEach { dot(Color(0xFFFFD84D), it, 268f, 16f, o, 3f) }
}


private val Wood = Color(0xFFF2D3A6)
private val WoodDark = Color(0xFFD9A46B)

private fun DrawScope.box(c: Color, x: Float, y: Float, w: Float, h: Float, line: Color, r: Float = 14f, sw: Float = 5f) {
    drawRoundRect(c, Offset(x, y), Size(w, h), CornerRadius(r))
    drawRoundRect(line, Offset(x, y), Size(w, h), CornerRadius(r), style = Stroke(sw))
}

private fun DrawScope.dot(c: Color, x: Float, y: Float, r: Float, line: Color? = null, sw: Float = 4f) {
    drawCircle(c, r, Offset(x, y))
    if (line != null) drawCircle(line, r, Offset(x, y), style = Stroke(sw))
}


private fun DrawScope.window(t: KidTheme, hour: Int) {
    val o = t.outline
    val f = Weather.forecast
    val code = f?.code ?: 0
    val sky = when {
        hour in 6..8 -> listOf(Color(0xFFFFC9A8), Color(0xFFBFE3FF))
        hour in 9..15 -> listOf(Color(0xFF8FD0FF), Color(0xFFD8F0FF))
        hour in 16..18 -> listOf(Color(0xFFFF9E7A), Color(0xFFC9A6E8))
        else -> listOf(Color(0xFF1F2A5C), Color(0xFF3C4A8C))
    }.let { if (code >= 51 && hour in 6..18) it.map { c -> lerp(c, Color(0xFF9AA4B5), 0.5f) } else it }
    // わく と そら
    box(Color.White, 25f, 10f, 300f, 270f, o, 16f)
    drawRoundRect(Brush.verticalGradient(sky, 22f, 270f), Offset(42f, 26f), Size(266f, 238f), CornerRadius(8f))
    if (hour in 6..18) {
        if (code < 3) {
            dot(Color(0xFFFFD84D), 240f, 85f, 34f)
            for (i in 0 until 8) {
                val a = i * PI / 4
                drawLine(Color(0xFFFFD84D), Offset(240f + cos(a).toFloat() * 44f, 85f + sin(a).toFloat() * 44f), Offset(240f + cos(a).toFloat() * 58f, 85f + sin(a).toFloat() * 58f), 6f)
            }
        }
    } else {
        dot(Color(0xFFFFF3B0), 235f, 80f, 28f)
        dot(sky[0], 248f, 70f, 24f)
        listOf(80f to 60f, 130f to 110f, 180f to 50f, 100f to 170f, 270f to 160f).forEach { (x, y) -> dot(Color.White, x, y, 3f) }
    }
    if (code >= 2) {
        val cloud = if (code >= 51) Color(0xFFE3E6EE) else Color.White
        listOf(110f to 120f, 200f to 160f).forEach { (x, y) ->
            dot(cloud, x, y, 26f); dot(cloud, x + 28f, y - 10f, 30f); dot(cloud, x + 58f, y, 24f)
        }
        if (code in 51..67 || code in 80..82 || code >= 95) for (i in 0 until 14) {
            val x = 60f + (i * 37f) % 240f; val y = 170f + (i * 23f) % 80f
            drawLine(Color(0xFF5FA8FF), Offset(x, y), Offset(x - 6f, y + 18f), 4f)
        }
        if (code in 71..77 || code in 85..86) for (i in 0 until 16) dot(Color.White, 60f + (i * 41f) % 240f, 150f + (i * 29f) % 110f, 5f)
    }
    // まどわく の じゅうじ
    drawLine(o, Offset(175f, 26f), Offset(175f, 264f), 6f)
    drawLine(o, Offset(42f, 145f), Offset(308f, 145f), 6f)
    drawRoundRect(o, Offset(42f, 26f), Size(266f, 238f), CornerRadius(8f), style = Stroke(4f))
    // カーテン
    val cur = t.titleBars[0]
    listOf(0f, 290f).forEach { x ->
        val p = Path().apply {
            moveTo(x, 0f); lineTo(x + 60f, 0f)
            cubicTo(x + 50f, 120f, x + 70f, 200f, x + (if (x == 0f) 70f else 0f), 300f)
            lineTo(x + (if (x == 0f) 0f else 60f), 300f); close()
        }
        drawPath(p, cur); drawPath(p, o, style = Stroke(4f))
    }
    // まどべ
    box(Wood, 15f, 278f, 320f, 22f, o, 6f)
}

private fun DrawScope.clock(t: KidTheme, cal: Calendar) {
    val o = t.outline
    dot(t.titleBars[1 % t.titleBars.size], 75f, 75f, 72f, o, 5f)
    dot(Color.White, 75f, 75f, 56f, o, 3f)
    for (i in 0 until 12) {
        val a = i * PI / 6
        dot(o, 75f + sin(a).toFloat() * 46f, 75f - cos(a).toFloat() * 46f, if (i % 3 == 0) 5f else 3f)
    }
    val h = (cal.get(Calendar.HOUR) + cal.get(Calendar.MINUTE) / 60f) * 30f
    val m = cal.get(Calendar.MINUTE) * 6f + cal.get(Calendar.SECOND) / 10f
    rotate(h, Offset(75f, 75f)) { drawLine(o, Offset(75f, 75f), Offset(75f, 45f), 8f, cap = androidx.compose.ui.graphics.StrokeCap.Round) }
    rotate(m, Offset(75f, 75f)) { drawLine(t.accent, Offset(75f, 75f), Offset(75f, 30f), 5f, cap = androidx.compose.ui.graphics.StrokeCap.Round) }
    dot(o, 75f, 75f, 6f)
}




private fun DrawScope.radio(t: KidTheme) {
    val o = t.outline
    drawLine(o, Offset(100f, 20f), Offset(125f, 0f), 4f)
    box(t.titleBars[2 % t.titleBars.size], 5f, 18f, 125f, 64f, o, 14f)
    dot(Color.White, 38f, 50f, 20f, o, 4f); dot(o, 38f, 50f, 7f)
    box(Color.White, 70f, 34f, 48f, 16f, o, 4f)
    dot(o, 82f, 64f, 5f); dot(o, 104f, 64f, 5f)
}









/** 260×520 の イーゼル（キャンバス は 20,40 から 220×230） */
private fun DrawScope.easel(t: KidTheme) {
    val o = t.outline
    drawLine(WoodDark, Offset(130f, 10f), Offset(130f, 510f), 14f)
    drawLine(WoodDark, Offset(120f, 60f), Offset(30f, 515f), 14f)
    drawLine(WoodDark, Offset(140f, 60f), Offset(230f, 515f), 14f)
    box(Color.White, 20f, 40f, 220f, 230f, o, 8f)
    box(Wood, 10f, 270f, 240f, 22f, o, 6f)
    drawOval(Color(0xFFF7E3C4), Offset(60f, 330f), Size(150f, 90f))
    drawOval(o, Offset(60f, 330f), Size(150f, 90f), style = Stroke(4f))
    listOf(Color(0xFFE85D75), Color(0xFF3D7BE0), Color(0xFFFFD84D), Color(0xFF2E9E5B)).forEachIndexed { i, c -> dot(c, 90f + i * 28f, 360f + (i % 2) * 22f, 10f) }
}

@Composable
private fun Easel(theme: KidTheme, u: Dp, onOpen: (String?) -> Unit) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val list = remember { com.theta.kidshome.data.Paintings.list(context) }
    var pick by remember { mutableStateOf(list.randomOrNull()?.id) }
    // 20 びょう ごと に べつの 絵
    LaunchedEffect(list) { while (list.size > 1) { delay(20_000); pick = list.filter { it.id != pick }.randomOrNull()?.id ?: pick } }
    Thing(EASEL, u, 260f, 520f, onClick = { onOpen(pick) }) { easel(theme) }
    val k = EASEL.w / 260f
    Box(Modifier.offset(u * (EASEL.x + 23f * k), u * (EASEL.y + 43f * k)).size(u * (214f * k), u * (224f * k))) {
        androidx.compose.animation.Crossfade(pick, label = "easel") { id ->
            val bmp = remember(id) { id?.let { com.theta.kidshome.data.Paintings.thumb(context, it)?.asImageBitmap() } }
            if (bmp != null) androidx.compose.foundation.Image(bmp, "え", Modifier.fillMaxSize().clickable { onOpen(id) }, contentScale = androidx.compose.ui.layout.ContentScale.Crop)
            else Canvas(Modifier.fillMaxSize().clickable { onOpen(null) }) {
                // まだ 絵 が ない とき：にじ
                val w = size.width
                listOf(Color(0xFFFF8FB1), Color(0xFFFFD84D), Color(0xFF8BD17C), Color(0xFF6FB7FF)).forEachIndexed { i, c ->
                    drawArc(c, 180f, 180f, false, Offset(w * (0.12f + i * 0.07f), w * (0.3f + i * 0.07f)), Size(w * (0.76f - i * 0.14f), w * (0.76f - i * 0.14f)), style = Stroke(w * 0.06f))
                }
            }
        }
    }
}

private fun DrawScope.piano(t: KidTheme) {
    val o = t.outline
    val body = t.titleBars[1 % t.titleBars.size]
    box(body, 0f, 0f, 250f, 330f, o, 14f)
    box(Color.White, 60f, 22f, 130f, 80f, o, 6f)
    for (i in 0 until 3) drawLine(o.copy(alpha = 0.5f), Offset(70f, 40f + i * 18f), Offset(180f, 40f + i * 18f), 2f)
    dot(o, 100f, 58f, 7f); dot(o, 140f, 76f, 7f)
    box(Color.White, 10f, 150f, 230f, 60f, o, 4f)
    for (i in 1 until 10) drawLine(o, Offset(10f + i * 23f, 150f), Offset(10f + i * 23f, 210f), 2f)
    listOf(1, 2, 4, 5, 6, 8, 9).forEach { i -> drawRect(o, Offset(10f + i * 23f - 7f, 150f), Size(14f, 34f)) }
    box(lerp(body, Color.Black, 0.12f), 0f, 210f, 250f, 26f, o, 6f)
    box(body, 18f, 236f, 26f, 160f, o, 6f)
    box(body, 206f, 236f, 26f, 160f, o, 6f)
}

private fun DrawScope.phone(t: KidTheme) {
    val o = t.outline
    box(Color(0xFFFF8FB1), 6f, 22f, 60f, 38f, o, 10f)
    dot(Color.White, 36f, 41f, 11f, o, 3f)
    drawRoundRect(Color(0xFFFF8FB1), Offset(2f, 4f), Size(68f, 18f), CornerRadius(9f))
    drawRoundRect(o, Offset(2f, 4f), Size(68f, 18f), CornerRadius(9f), style = Stroke(4f))
}

private fun DrawScope.tv(t: KidTheme) {
    val o = t.outline
    drawLine(o, Offset(110f, 20f), Offset(70f, 0f), 4f)
    drawLine(o, Offset(130f, 20f), Offset(170f, 0f), 4f)
    box(Color(0xFF555A70), 0f, 18f, 240f, 172f, o, 18f)
    drawRoundRect(Brush.verticalGradient(listOf(Color(0xFF9BE0FF), Color(0xFFD7F2FF))), Offset(16f, 32f), Size(208f, 132f), CornerRadius(12f))
    dot(Color(0xFFFF8FB1), 214f, 176f, 5f)
}

private fun DrawScope.toybox(t: KidTheme) {
    val o = t.outline
    box(t.titleBars[2 % t.titleBars.size], 0f, 30f, 235f, 112f, o, 12f)
    drawRoundRect(Color.White.copy(alpha = 0.5f), Offset(20f, 60f), Size(195f, 12f), CornerRadius(6f))
    listOf(50f, 117f, 185f).forEach { dot(Color(0xFFFFD84D), it, 105f, 12f, o, 3f) }
}


@Composable
private fun Bubble(theme: KidTheme, line: String, cx: Float, bottom: Float, u: Dp) {
    var shown by remember(line) { mutableStateOf(true) }
    LaunchedEffect(line) { delay(9000); shown = false }
    if (!shown || line.isBlank()) return
    Box(Modifier.offset(u * (cx - 200f), u * (bottom - 110f)).size(u * 400f, u * 110f), contentAlignment = Alignment.BottomCenter) {
        Box(
            Modifier.wrapContentSize().clip(RoundedCornerShape(u * 22f)).background(theme.bubbles[0])
                .border(2.5.dp, theme.outline, RoundedCornerShape(u * 22f)).padding(horizontal = u * 18f, vertical = u * 8f),
        ) { Text(line, fontFamily = DotFont, fontSize = (u.value * 28).sp, color = theme.outline, textAlign = TextAlign.Center) }
    }
}
