package com.theta.kidshome.ui

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
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
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.theta.kidshome.data.Weather
import kotlinx.coroutines.delay
import java.util.Calendar
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/** へや の 中の もの。押すと その きのう */
enum class RoomThing {
    WINDOW, CLOCK, CALENDAR, MAP, KUKU, TASU, TIMETABLE, CORK,
    EASEL, LAPTOP, NOTEBOOK, PAD, PAPER, GLOBE, PIANO, PHONE,
    TV, RADIO, SHELF, TOYBOX, PUZZLE,
}

/** 部屋の絵は 1600×1000 の 目盛りで かく */
private const val RW = 1600f
private const val RH = 1000f
private const val FLOOR = 700f

/** 部屋に置く 1 こ ぶんの もの */
private class Spot(val thing: RoomThing, val x: Float, val y: Float, val w: Float, val h: Float, val label: String)

private val spots = listOf(
    Spot(RoomThing.WINDOW, 50f, 40f, 350f, 320f, "まど"),
    Spot(RoomThing.CLOCK, 440f, 60f, 150f, 150f, "とけい"),
    Spot(RoomThing.CALENDAR, 625f, 45f, 170f, 215f, ""),
    Spot(RoomThing.MAP, 830f, 60f, 200f, 165f, "ちず"),
    Spot(RoomThing.CORK, 1065f, 35f, 340f, 260f, ""),
    Spot(RoomThing.KUKU, 1085f, 70f, 95f, 120f, "くく"),
    Spot(RoomThing.TASU, 1190f, 70f, 95f, 120f, "たしざん"),
    Spot(RoomThing.TIMETABLE, 1295f, 62f, 92f, 200f, "じかんわり"),
    Spot(RoomThing.RADIO, 1440f, 145f, 135f, 85f, "おんがく"),
    Spot(RoomThing.SHELF, 1430f, 230f, 155f, 640f, "ほんだな"),
    Spot(RoomThing.EASEL, 45f, 370f, 260f, 520f, "おえかき"),
    Spot(RoomThing.LAPTOP, 335f, 448f, 150f, 118f, "タイピング"),
    Spot(RoomThing.NOTEBOOK, 495f, 496f, 115f, 70f, "もじ"),
    Spot(RoomThing.PAD, 618f, 462f, 92f, 104f, "くるくる"),
    Spot(RoomThing.PAPER, 716f, 500f, 84f, 66f, "わーぷろ"),
    Spot(RoomThing.GLOBE, 803f, 420f, 80f, 146f, "ちず"),
    Spot(RoomThing.PIANO, 905f, 430f, 250f, 400f, "がっき"),
    Spot(RoomThing.PHONE, 1075f, 365f, 72f, 62f, "でんわ"),
    Spot(RoomThing.TV, 1170f, 325f, 240f, 195f, "テレビ"),
    Spot(RoomThing.TOYBOX, 1180f, 845f, 235f, 145f, "おもちゃばこ"),
    Spot(RoomThing.PUZZLE, 370f, 895f, 440f, 100f, "パズル"),
)

/**
 * へや の ホーム（Microsoft Bob みたいに、部屋の中の ものが アプリ）。
 * まど＝てんき、とけい・カレンダー、つくえ の上に ノート・パソコン、イーゼル＝おえかき、ピアノ、テレビ、ほんだな、おもちゃばこ…
 * available に ないものは 置かない（おうちのひとが はずした きのう）。
 */
@Composable
fun RoomView(
    theme: KidTheme,
    now: Long,
    available: Set<RoomThing>,
    locked: Boolean,
    lockText: String?,
    tvEmoji: String,
    mascotLine: String,
    playful: Boolean,
    modifier: Modifier = Modifier,
    onMascot: (Offset) -> Unit,
    onThing: (RoomThing) -> Unit,
) {
    val cal = Calendar.getInstance().apply { timeInMillis = now }
    val hour = cal.get(Calendar.HOUR_OF_DAY)
    val night = hour >= 19 || hour < 6
    BoxWithConstraints(modifier, contentAlignment = Alignment.Center) {
        val u = minOf(maxWidth / RW, maxHeight / RH)
        Box(Modifier.size(u * RW, u * RH).clip(RoundedCornerShape(u * 28f)).border(3.dp, theme.outline, RoundedCornerShape(u * 28f))) {
            Canvas(Modifier.fillMaxSize()) { room(theme) }
            spots.filter { it.thing == RoomThing.CORK || it.thing in available }.forEach { s ->
                Thing(s, u, theme, clickable = s.thing in available, onClick = { onThing(s.thing) }) {
                    when (s.thing) {
                        RoomThing.WINDOW -> window(theme, hour)
                        RoomThing.CLOCK -> clock(theme, cal)
                        RoomThing.CALENDAR -> paper(theme, theme.titleBars[0])
                        RoomThing.MAP -> paper(theme, theme.titleBars[1 % theme.titleBars.size])
                        RoomThing.CORK -> cork(theme)
                        RoomThing.KUKU, RoomThing.TASU -> card(theme, if (s.thing == RoomThing.KUKU) theme.titleBars[2 % theme.titleBars.size] else theme.titleBars[3 % theme.titleBars.size])
                        RoomThing.TIMETABLE -> card(theme, theme.titleBars[0])
                        RoomThing.RADIO -> radio(theme)
                        RoomThing.SHELF -> shelf(theme)
                        RoomThing.EASEL -> easel(theme)
                        RoomThing.LAPTOP -> laptop(theme)
                        RoomThing.NOTEBOOK -> notebook(theme)
                        RoomThing.PAD -> pad(theme)
                        RoomThing.PAPER -> papers(theme)
                        RoomThing.GLOBE -> globe(theme)
                        RoomThing.PIANO -> piano(theme)
                        RoomThing.PHONE -> phone(theme)
                        RoomThing.TV -> tv(theme)
                        RoomThing.TOYBOX -> toybox(theme)
                        RoomThing.PUZZLE -> puzzle(theme)
                    }
                }
                // もの の 上に のせる 字
                Overlay(s, u) {
                    when (s.thing) {
                        RoomThing.CALENDAR -> Column(Modifier.fillMaxSize().padding(top = u * 50f), horizontalAlignment = Alignment.CenterHorizontally) {
                            DotText("${cal.get(Calendar.MONTH) + 1}がつ", theme, (u.value * 26).toInt(), theme.outline)
                            DotText("${cal.get(Calendar.DAY_OF_MONTH)}", theme, (u.value * 70).toInt(), theme.outline)
                            DotText(listOf("にち", "げつ", "か", "すい", "もく", "きん", "ど")[cal.get(Calendar.DAY_OF_WEEK) - 1] + "ようび", theme, (u.value * 20).toInt(), theme.outline)
                        }
                        RoomThing.MAP -> Text("🗾", fontSize = (u.value * 96).sp, modifier = Modifier.align(Alignment.Center))
                        RoomThing.KUKU -> CardText("9×9", "くく", theme, u)
                        RoomThing.TASU -> CardText("1+1", "たしざん", theme, u)
                        RoomThing.TIMETABLE -> CardText("📒", "じかん\nわり", theme, u)
                        RoomThing.NOTEBOOK -> Text("あ", fontSize = (u.value * 34).sp, color = theme.outline, modifier = Modifier.align(Alignment.Center).offset(x = u * 22f))
                        RoomThing.TV -> Text(if (locked) "🔒" else tvEmoji, fontSize = (u.value * 64).sp, modifier = Modifier.align(Alignment.Center).offset(y = -u * 6f))
                        RoomThing.TOYBOX -> Text("🧸⚽🪀", fontSize = (u.value * 40).sp, modifier = Modifier.align(Alignment.TopCenter).offset(y = -u * 26f))
                        else -> {}
                    }
                }
            }
            // テレビの ロック、まど の てんき
            if (locked && lockText != null && RoomThing.TV in available) Tag(lockText, 1290f, 518f, u, theme, theme.titleBars[0])
            Weather.forecast?.let { f -> if (RoomThing.WINDOW in available) Tag("${Weather.emoji(f.code, f.isDay)} ${f.temp.toInt()}℃", 225f, 300f, u, theme, Color.White) }
            // マスコット（あとで ガイド役 に）
            Mascot(theme, mascotLine, u, playful, onMascot)
            // よる は へや を すこし くらく
            if (night) Box(Modifier.fillMaxSize().background(Color(0xFF1B1F4A).copy(alpha = 0.22f)))
        }
    }
}

@Composable
private fun Thing(s: Spot, u: Dp, theme: KidTheme, clickable: Boolean, onClick: () -> Unit, draw: DrawScope.() -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val focused by interaction.collectIsFocusedAsState()
    val k by animateFloatAsState(if (pressed) 0.93f else if (focused) 1.06f else 1f, label = "press")
    Box(
        Modifier.offset(u * s.x, u * s.y).size(u * s.w, u * s.h).scale(k)
            .then(if (clickable) Modifier.clickable(interactionSource = interaction, indication = null, onClick = onClick) else Modifier),
    ) {
        Canvas(Modifier.fillMaxSize()) {
            withTransform({ scale(size.width / s.w, size.height / s.h, pivot = Offset.Zero) }) { draw() }
            if (focused) drawRoundRect(theme.accent, style = Stroke(6.dp.toPx()), cornerRadius = CornerRadius(12.dp.toPx()))
        }
    }
    if (s.label.isNotEmpty() && clickable && s.thing !in setOf(RoomThing.KUKU, RoomThing.TASU, RoomThing.TIMETABLE)) {
        // ゆか の いちばん 下の ものは 中に、ほかは 下に
        val inside = s.thing == RoomThing.TOYBOX || s.thing == RoomThing.PUZZLE
        val lw = maxOf(s.w, 200f)
        Box(Modifier.offset(u * (s.x + s.w / 2 - lw / 2), u * (if (inside) s.y + s.h - 42f else s.y + s.h - 4f)).size(u * lw, u * 40f), contentAlignment = Alignment.TopCenter) {
            Text(
                s.label, fontFamily = DotFont, fontSize = (u.value * 19).sp, color = theme.outline, maxLines = 1, softWrap = false,
                modifier = Modifier.clip(RoundedCornerShape(50)).background(Color.White.copy(alpha = 0.8f)).padding(horizontal = u * 10f),
            )
        }
    }
}

@Composable
private fun Overlay(s: Spot, u: Dp, content: @Composable androidx.compose.foundation.layout.BoxScope.() -> Unit) {
    Box(Modifier.offset(u * s.x, u * s.y).size(u * s.w, u * s.h), content = content)
}

@Composable
private fun androidx.compose.foundation.layout.BoxScope.CardText(big: String, small: String, theme: KidTheme, u: Dp) {
    Column(Modifier.align(Alignment.Center).padding(top = u * 12f), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(big, fontFamily = DotFont, fontSize = (u.value * 30).sp, color = theme.outline)
        Text(small, fontFamily = DotFont, fontSize = (u.value * 17).sp, lineHeight = (u.value * 19).sp, color = theme.outline, textAlign = TextAlign.Center)
    }
}

@Composable
private fun Tag(text: String, cx: Float, y: Float, u: Dp, theme: KidTheme, bg: Color) {
    Box(Modifier.offset(u * (cx - 150f), u * y).size(u * 300f, u * 50f), contentAlignment = Alignment.TopCenter) {
        Text(
            text, fontFamily = DotFont, fontSize = (u.value * 22).sp, color = theme.outline,
            modifier = Modifier.clip(RoundedCornerShape(50)).background(bg).border(2.dp, theme.outline, RoundedCornerShape(50)).padding(horizontal = u * 14f, vertical = u * 2f),
        )
    }
}

@Composable
private fun Mascot(theme: KidTheme, line: String, u: Dp, playful: Boolean, onTap: (Offset) -> Unit) {
    val idle = rememberInfiniteTransition(label = "mascot")
    val bob by idle.animateFloat(0f, -10f, infiniteRepeatable(tween(900), RepeatMode.Reverse), label = "bob")
    var shown by remember(line) { mutableStateOf(true) }
    LaunchedEffect(line) { delay(9000); shown = false }
    val x = 730f; val y = 790f
    // かげ
    Box(Modifier.offset(u * (x + 40f), u * (y + 200f)).size(u * 120f, u * 22f).clip(RoundedCornerShape(50)).background(Color.Black.copy(alpha = 0.12f)))
    var center by remember { mutableStateOf(Offset.Zero) }
    Box(
        Modifier.offset(u * x, u * (y + (if (playful) bob else 0f))).size(u * 200f, u * 210f)
            .onGloballyPositioned { center = it.boundsInRoot().center }
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {
                shown = true
                onTap(center)
            },
        contentAlignment = Alignment.BottomCenter,
    ) { Text(theme.mascot, fontSize = (u.value * 130).sp, maxLines = 1, softWrap = false) }
    if (shown && line.isNotBlank()) {
        Box(Modifier.offset(u * (x - 40f), u * (y - 120f)).size(u * 330f, u * 110f), contentAlignment = Alignment.BottomCenter) {
            Box(
                Modifier.wrapContentSize().clip(RoundedCornerShape(u * 22f)).background(theme.bubbles[0])
                    .border(2.5.dp, theme.outline, RoundedCornerShape(u * 22f)).padding(horizontal = u * 18f, vertical = u * 8f),
            ) { Text(line, fontFamily = DotFont, fontSize = (u.value * 24).sp, color = theme.outline, textAlign = TextAlign.Center) }
        }
    }
}

// ---------------------------------------------------------------- え（目盛りは その もの の 中の 座標）

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

private fun DrawScope.room(t: KidTheme) {
    val k = size.width / RW
    withTransform({ scale(k, k, pivot = Offset.Zero) }) {
        // かべ（もようつき）
        drawRect(t.bgTop, size = Size(RW, FLOOR))
        for (x in 0..32) for (y in 0..14) {
            if ((x + y) % 2 == 0) drawCircle(t.grid.copy(alpha = 0.55f), 5f, Offset(x * 50f + 25f, y * 50f + 25f))
        }
        // ゆか（いた）
        drawRect(Wood, Offset(0f, FLOOR), Size(RW, RH - FLOOR))
        for (i in 0..6) drawLine(WoodDark.copy(alpha = 0.55f), Offset(0f, FLOOR + 30f + i * 48f), Offset(RW, FLOOR + 30f + i * 48f), 3f)
        for (i in 0..20) {
            val row = i % 6
            drawLine(WoodDark.copy(alpha = 0.4f), Offset((i * 173f + row * 61f) % RW, FLOOR + 30f + row * 48f), Offset((i * 173f + row * 61f) % RW, FLOOR + 78f + row * 48f), 3f)
        }
        // はばき
        drawRect(Color.White, Offset(0f, FLOOR - 16f), Size(RW, 22f))
        drawLine(t.outline, Offset(0f, FLOOR - 16f), Offset(RW, FLOOR - 16f), 4f)
        drawLine(t.outline, Offset(0f, FLOOR + 6f), Offset(RW, FLOOR + 6f), 4f)
        // つくえ（もの は 上に のる）
        val o = t.outline
        box(Wood, 315f, 562f, 580f, 44f, o, 10f)
        box(lerp(Wood, Color.White, 0.25f), 330f, 606f, 230f, 130f, o, 8f)
        dot(o, 445f, 671f, 7f)
        box(Wood, 330f, 736f, 30f, 150f, o, 6f)
        box(Wood, 850f, 606f, 30f, 280f, o, 6f)
        // テレビ台
        box(WoodDark, 1158f, 520f, 264f, 340f, o, 12f)
        box(Wood, 1172f, 560f, 114f, 280f, o, 8f)
        box(Wood, 1294f, 560f, 114f, 280f, o, 8f)
        dot(o, 1272f, 700f, 6f); dot(o, 1308f, 700f, 6f)
        // ラグ
        drawOval(lerp(t.titleBars[0], Color.White, 0.3f), Offset(360f, 885f), Size(460f, 112f))
        drawOval(o, Offset(360f, 885f), Size(460f, 112f), style = Stroke(5f))
        drawOval(Color.White.copy(alpha = 0.5f), Offset(390f, 900f), Size(400f, 82f), style = Stroke(4f))
    }
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

private fun DrawScope.paper(t: KidTheme, top: Color) {
    val w = size.width; val h = size.height
    box(Color.White, 6f, 14f, w - 12f, h - 20f, t.outline, 8f)
    drawRect(top, Offset(8f, 16f), Size(w - 16f, 30f))
    drawLine(t.outline, Offset(8f, 46f), Offset(w - 8f, 46f), 4f)
    dot(Color(0xFFE85D75), w / 2, 14f, 8f, t.outline, 3f)
}

private fun DrawScope.cork(t: KidTheme) {
    box(WoodDark, 0f, 0f, 340f, 260f, t.outline, 12f)
    drawRoundRect(Color(0xFFE6BC86), Offset(14f, 14f), Size(312f, 232f), CornerRadius(6f))
    for (i in 0 until 90) dot(Color(0xFFCF9F66), 20f + (i * 53f) % 300f, 20f + (i * 31f) % 220f, 3f)
}

private fun DrawScope.card(t: KidTheme, c: Color) {
    val w = size.width; val h = size.height
    box(lerp(c, Color.White, 0.35f), 3f, 8f, w - 6f, h - 12f, t.outline, 8f)
    dot(Color(0xFF5FA8FF), w / 2, 10f, 7f, t.outline, 3f)
}

private fun DrawScope.radio(t: KidTheme) {
    val o = t.outline
    drawLine(o, Offset(100f, 20f), Offset(125f, 0f), 4f)
    box(t.titleBars[2 % t.titleBars.size], 5f, 18f, 125f, 64f, o, 14f)
    dot(Color.White, 38f, 50f, 20f, o, 4f); dot(o, 38f, 50f, 7f)
    box(Color.White, 70f, 34f, 48f, 16f, o, 4f)
    dot(o, 82f, 64f, 5f); dot(o, 104f, 64f, 5f)
}

private fun DrawScope.shelf(t: KidTheme) {
    val o = t.outline
    box(WoodDark, 0f, 0f, 155f, 640f, o, 10f)
    val shelves = listOf(20f, 170f, 320f, 470f)
    val cols = listOf(Color(0xFFFF8FB1), Color(0xFF6FB7FF), Color(0xFFFFD84D), Color(0xFF8BD17C), Color(0xFFB79CFF), Color(0xFFFFB067))
    shelves.forEachIndexed { si, sy ->
        drawRect(Wood, Offset(12f, sy), Size(131f, 140f))
        var x = 16f
        var i = si
        while (x < 130f) {
            val bw = 16f + (i * 7 % 3) * 5f
            val bh = 100f + (i * 13 % 4) * 10f
            if (x + bw > 140f) break
            box(cols[i % cols.size], x, sy + 140f - bh, bw, bh, o, 4f, 3f)
            x += bw + 2f; i++
        }
        drawLine(o, Offset(10f, sy + 140f), Offset(145f, sy + 140f), 5f)
    }
}

private fun DrawScope.easel(t: KidTheme) {
    val o = t.outline
    drawLine(WoodDark, Offset(130f, 10f), Offset(130f, 500f), 14f)
    drawLine(WoodDark, Offset(120f, 60f), Offset(30f, 510f), 14f)
    drawLine(WoodDark, Offset(140f, 60f), Offset(230f, 510f), 14f)
    box(Color.White, 20f, 40f, 220f, 230f, o, 8f)
    // え
    listOf(Color(0xFFFF8FB1), Color(0xFFFFD84D), Color(0xFF8BD17C), Color(0xFF6FB7FF)).forEachIndexed { i, c ->
        drawArc(c, 180f, 180f, false, Offset(50f + i * 16f, 110f + i * 16f), Size(160f - i * 32f, 160f - i * 32f), style = Stroke(14f))
    }
    dot(Color(0xFFFFD84D), 195f, 85f, 18f)
    drawLine(o, Offset(30f, 230f), Offset(230f, 230f), 4f)
    box(Wood, 10f, 270f, 240f, 22f, o, 6f)
    // パレット
    drawOval(Color(0xFFF7E3C4), Offset(60f, 330f), Size(150f, 90f))
    drawOval(o, Offset(60f, 330f), Size(150f, 90f), style = Stroke(4f))
    listOf(Color(0xFFE85D75), Color(0xFF3D7BE0), Color(0xFFFFD84D), Color(0xFF2E9E5B)).forEachIndexed { i, c -> dot(c, 90f + i * 28f, 360f + (i % 2) * 22f, 10f) }
}

private fun DrawScope.laptop(t: KidTheme) {
    val o = t.outline
    box(Color(0xFFE9EEF5), 18f, 6f, 114f, 80f, o, 8f)
    drawRoundRect(Color(0xFF9ED7FF), Offset(28f, 15f), Size(94f, 60f), CornerRadius(4f))
    for (i in 0 until 3) drawLine(Color.White, Offset(40f, 30f + i * 15f), Offset(80f + i * 10f, 30f + i * 15f), 5f)
    val p = Path().apply { moveTo(10f, 88f); lineTo(140f, 88f); lineTo(150f, 112f); lineTo(0f, 112f); close() }
    drawPath(p, Color(0xFFD5DCE6)); drawPath(p, o, style = Stroke(4f))
    for (r in 0 until 2) for (c in 0 until 8) drawRect(o.copy(alpha = 0.35f), Offset(20f + c * 14f, 93f + r * 8f), Size(10f, 5f))
}

private fun DrawScope.notebook(t: KidTheme) {
    val o = t.outline
    box(Color.White, 4f, 6f, 54f, 60f, o, 4f)
    box(Color.White, 56f, 6f, 54f, 60f, o, 4f)
    for (i in 0 until 4) drawLine(Color(0xFFBFD7F5), Offset(10f, 18f + i * 12f), Offset(52f, 18f + i * 12f), 2f)
    drawLine(Color(0xFFE85D75), Offset(100f, 2f), Offset(76f, 54f), 7f)
}

private fun DrawScope.pad(t: KidTheme) {
    val o = t.outline
    box(Color(0xFF4A4F63), 8f, 4f, 76f, 92f, o, 10f)
    drawRoundRect(Color.White, Offset(16f, 12f), Size(60f, 70f), CornerRadius(4f))
    drawArc(t.accent, 0f, 300f, false, Offset(26f, 22f), Size(40f, 40f), style = Stroke(6f))
    drawArc(Color(0xFF6FB7FF), 90f, 250f, false, Offset(36f, 32f), Size(20f, 20f), style = Stroke(5f))
}

private fun DrawScope.papers(t: KidTheme) {
    val o = t.outline
    box(Color(0xFFF6F2EA), 12f, 10f, 64f, 52f, o, 4f)
    box(Color.White, 4f, 4f, 64f, 52f, o, 4f)
    for (i in 0 until 3) drawLine(o.copy(alpha = 0.4f), Offset(12f, 18f + i * 11f), Offset(56f, 18f + i * 11f), 3f)
    drawLine(Color(0xFFFFC94D), Offset(50f, 60f), Offset(82f, 30f), 8f)
}

private fun DrawScope.globe(t: KidTheme) {
    val o = t.outline
    drawArc(WoodDark, 200f, 140f, false, Offset(4f, 6f), Size(72f, 92f), style = Stroke(5f))
    dot(Color(0xFF6FB7FF), 40f, 52f, 32f, o, 4f)
    drawOval(Color(0xFF8BD17C), Offset(24f, 32f), Size(22f, 16f))
    drawOval(Color(0xFF8BD17C), Offset(44f, 56f), Size(18f, 22f))
    drawLine(o, Offset(40f, 86f), Offset(40f, 120f), 6f)
    box(WoodDark, 14f, 118f, 52f, 22f, o, 8f)
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

private fun DrawScope.puzzle(t: KidTheme) {
    val o = t.outline
    val cols = listOf(Color(0xFFFF8FB1), Color(0xFF6FB7FF), Color(0xFFFFD84D), Color(0xFF8BD17C))
    listOf(Triple(90f, 30f, -12f), Triple(170f, 40f, 8f), Triple(250f, 26f, 20f), Triple(320f, 44f, -6f)).forEachIndexed { i, (x, y, r) ->
        rotate(r, Offset(x + 25f, y + 25f)) {
            box(cols[i], x, y, 50f, 50f, o, 6f, 4f)
            dot(cols[i], x + 50f, y + 25f, 10f, o, 4f)
            drawRect(cols[i], Offset(x + 44f, y + 17f), Size(6f, 16f))
        }
    }
}
