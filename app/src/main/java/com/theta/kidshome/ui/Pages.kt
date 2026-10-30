package com.theta.kidshome.ui

import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import com.theta.kidshome.data.Events
import com.theta.kidshome.data.Launcher
import com.theta.kidshome.data.Media
import com.theta.kidshome.media.AudioPlayer
import com.theta.kidshome.data.Store
import com.theta.kidshome.data.Weather
import com.theta.kidshome.data.Widgets
import com.theta.kidshome.remote.Remote
import kotlinx.coroutines.delay
import java.util.Calendar
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

private val WeekNames = listOf("にち", "げつ", "か", "すい", "もく", "きん", "ど")
private val Sunday = Color(0xFFE5486E)
private val Saturday = Color(0xFF3F86D6)
private val Rainbow = listOf(
    Color(0xFFFF6B8B), Color(0xFFFF9F43), Color(0xFFF7C531), Color(0xFF6BCB77),
    Color(0xFF4DA3FF), Color(0xFF9B7BFF),
)

// ---------------------------------------------------------------- とけい・カレンダー・てんき のタブ

@Composable
fun ClockPage(theme: KidTheme, now: Long, wide: Boolean) {
    val context = LocalContext.current
    LaunchedEffect(Store.config.weather) { Weather.refresh(context) }
    if (wide) {
        Row(Modifier.fillMaxSize().padding(end = 6.dp, bottom = 8.dp), horizontalArrangement = Arrangement.spacedBy(22.dp)) {
            AnalogClockWindow(theme, now, Modifier.weight(1f))
            Column(Modifier.weight(1f).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(20.dp)) {
                CalendarWindow(theme, now)
                Row(horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                    WeatherWindow(theme, Modifier.weight(1.4f))
                    BatteryWindow(theme, Modifier.weight(1f))
                }
            }
        }
    } else {
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(end = 6.dp, bottom = 8.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            AnalogClockWindow(theme, now, Modifier.fillMaxWidth())
            CalendarWindow(theme, now)
            Row(horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                WeatherWindow(theme, Modifier.weight(1.4f))
                BatteryWindow(theme, Modifier.weight(1f))
            }
        }
    }
}

@Composable
fun AnalogClockWindow(theme: KidTheme, now: Long, modifier: Modifier = Modifier) {
    val measurer = rememberTextMeasurer()
    val cal = Calendar.getInstance().apply { timeInMillis = now }
    val h = cal.get(Calendar.HOUR_OF_DAY)
    val m = cal.get(Calendar.MINUTE)
    val s = cal.get(Calendar.SECOND)
    val hour12 = h % 12
    val ampm = if (h < 12) "ごぜん" else "ごご"
    RetroWindow(theme, modifier, title = "とけい", titleColor = theme.titleBars[1]) {
        Column(Modifier.fillMaxWidth().padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            DotText("$ampm ${if (hour12 == 0) 12 else hour12}じ ${m}ふん", theme, 24)
            Canvas(Modifier.fillMaxWidth().aspectRatio(1f).padding(8.dp)) {
                val r = min(size.width, size.height) / 2
                val c = Offset(size.width / 2, size.height / 2)
                val line = 5.dp.toPx()
                drawCircle(theme.bubbles[0], r, c)
                drawCircle(theme.outline, r - line / 2, c, style = Stroke(line))
                // めもり
                for (i in 0 until 60) {
                    val a = Math.toRadians(i * 6.0 - 90)
                    val len = if (i % 5 == 0) r * 0.07f else r * 0.03f
                    val outer = r * 0.93f
                    drawLine(
                        theme.outline.copy(alpha = if (i % 5 == 0) 0.9f else 0.35f),
                        Offset(c.x + cos(a).toFloat() * outer, c.y + sin(a).toFloat() * outer),
                        Offset(c.x + cos(a).toFloat() * (outer - len), c.y + sin(a).toFloat() * (outer - len)),
                        strokeWidth = if (i % 5 == 0) 3.dp.toPx() else 1.5.dp.toPx(),
                        cap = StrokeCap.Round,
                    )
                }
                // 1〜12 の数字（にじいろ）と、外側に小さく「ふん」
                for (n in 1..12) {
                    val a = Math.toRadians(n * 30.0 - 90)
                    val big = measurer.measure(n.toString(), TextStyle(fontFamily = DotFont, fontSize = (r / 7).toSp(), color = Rainbow[n % Rainbow.size]))
                    val rr = r * 0.72f
                    drawText(big, topLeft = Offset(c.x + cos(a).toFloat() * rr - big.size.width / 2, c.y + sin(a).toFloat() * rr - big.size.height / 2))
                    val small = measurer.measure((n * 5).toString(), TextStyle(fontFamily = DotFont, fontSize = (r / 16).toSp(), color = theme.outline.copy(alpha = 0.55f)))
                    val sr = r * 0.53f
                    drawText(small, topLeft = Offset(c.x + cos(a).toFloat() * sr - small.size.width / 2, c.y + sin(a).toFloat() * sr - small.size.height / 2))
                }
                // はり
                val hourAngle = (hour12 + m / 60f) * 30f
                val minAngle = (m + s / 60f) * 6f
                rotate(hourAngle, c) {
                    drawLine(theme.outline, c, Offset(c.x, c.y - r * 0.42f), strokeWidth = r * 0.07f, cap = StrokeCap.Round)
                }
                rotate(minAngle, c) {
                    drawLine(theme.accent, c, Offset(c.x, c.y - r * 0.68f), strokeWidth = r * 0.045f, cap = StrokeCap.Round)
                }
                rotate(s * 6f, c) {
                    drawLine(Color(0xFFFF6B8B), Offset(c.x, c.y + r * 0.12f), Offset(c.x, c.y - r * 0.8f), strokeWidth = r * 0.015f, cap = StrokeCap.Round)
                }
                drawCircle(theme.outline, r * 0.06f, c)
                drawCircle(Color(0xFFFFE29A), r * 0.03f, c)
            }
        }
    }
}

@Composable
fun CalendarWindow(theme: KidTheme, now: Long, modifier: Modifier = Modifier, compact: Boolean = false, initialWeek: Boolean = false) {
    val context = LocalContext.current
    LaunchedEffect(Unit) { Events.refresh(context) }
    var week by remember { mutableStateOf(initialWeek) }
    val today = Calendar.getInstance().apply { timeInMillis = now }
    val month = today.get(Calendar.MONTH)
    val scale = if (compact) 0.8f else 1f
    fun sz(v: Int) = (v * scale).toInt()
    RetroWindow(theme, modifier.fillMaxWidth(), title = "カレンダー", titleColor = theme.titleBars[3]) {
        Column(Modifier.fillMaxWidth().padding(if (compact) 10.dp else 14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(bottom = 6.dp)) {
                DotText("${month + 1}がつ", theme, sz(22), modifier = Modifier.weight(1f))
                listOf(false to "つき", true to "しゅう").forEach { (w, label) ->
                    val on = week == w
                    Box(
                        Modifier.padding(start = 4.dp).clip(RoundedCornerShape(10.dp))
                            .background(if (on) theme.titleBars[0] else Color.White)
                            .border(2.dp, theme.outline, RoundedCornerShape(10.dp))
                            .clickable { week = w }.padding(horizontal = 10.dp, vertical = 2.dp)
                    ) { DotText(label, theme, sz(14)) }
                }
            }
            Row(Modifier.fillMaxWidth()) {
                WeekNames.forEachIndexed { i, w ->
                    Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                        DotText(w, theme, sz(14), when (i) { 0 -> Sunday; 6 -> Saturday; else -> theme.outline })
                    }
                }
            }
            if (week) WeekRow(theme, today, compact) else MonthGrid(theme, today, compact)
        }
    }
}

private fun dayStart(c: Calendar): Long = (c.clone() as Calendar).apply {
    set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
}.timeInMillis

@Composable
private fun MonthGrid(theme: KidTheme, today: Calendar, compact: Boolean) {
    val first = (today.clone() as Calendar).apply { set(Calendar.DAY_OF_MONTH, 1) }
    val offset = first.get(Calendar.DAY_OF_WEEK) - 1
    val days = today.getActualMaximum(Calendar.DAY_OF_MONTH)
    val cells = (0 until offset).map { 0 } + (1..days).toList()
    val cell = if (compact) 30.dp else 38.dp
    cells.chunked(7).forEach { week ->
        Row(Modifier.fillMaxWidth().padding(top = 2.dp)) {
            (0 until 7).forEach { i ->
                val d = week.getOrNull(i) ?: 0
                val isToday = d == today.get(Calendar.DAY_OF_MONTH)
                val hasEvent = d > 0 && Events.list.isNotEmpty() &&
                    Events.on(dayStart((first.clone() as Calendar).apply { set(Calendar.DAY_OF_MONTH, d) })).isNotEmpty()
                Box(Modifier.weight(1f).height(cell), contentAlignment = Alignment.Center) {
                    if (d > 0) {
                        if (isToday) {
                            Box(Modifier.size(cell - 4.dp).clip(CircleShape).background(theme.accent).border(2.5.dp, theme.outline, CircleShape))
                        }
                        DotText(
                            d.toString(), theme, if (compact) 14 else 18,
                            when {
                                isToday -> Color.White
                                i == 0 -> Sunday
                                i == 6 -> Saturday
                                else -> theme.outline
                            },
                        )
                        if (hasEvent) {
                            Box(Modifier.align(Alignment.BottomCenter).padding(bottom = 1.dp).size(5.dp).clip(CircleShape).background(Color(0xFFFF6B8B)))
                        }
                    }
                }
            }
        }
    }
}

/** 今週（にちようび はじまり）。予定があれば、その日の下に出す */
@Composable
private fun WeekRow(theme: KidTheme, today: Calendar, compact: Boolean) {
    val sunday = (today.clone() as Calendar).apply { add(Calendar.DAY_OF_MONTH, -(get(Calendar.DAY_OF_WEEK) - 1)) }
    Row(Modifier.fillMaxWidth().padding(top = 4.dp)) {
        (0 until 7).forEach { i ->
            val day = (sunday.clone() as Calendar).apply { add(Calendar.DAY_OF_MONTH, i) }
            val isToday = day.get(Calendar.DAY_OF_YEAR) == today.get(Calendar.DAY_OF_YEAR)
            val events = Events.on(dayStart(day))
            Column(
                Modifier.weight(1f).padding(1.dp).clip(RoundedCornerShape(8.dp))
                    .background(if (isToday) theme.bubbles[0] else Color.Transparent)
                    .heightIn(min = if (compact) 64.dp else 96.dp)
                    .padding(2.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                DotText(
                    day.get(Calendar.DAY_OF_MONTH).toString(), theme, if (compact) 16 else 20,
                    when { i == 0 -> Sunday; i == 6 -> Saturday; else -> theme.outline },
                )
                events.take(if (compact) 2 else 4).forEach { e ->
                    Box(
                        Modifier.fillMaxWidth().padding(top = 2.dp).clip(RoundedCornerShape(4.dp))
                            .background(Color(e.color).copy(alpha = 0.25f)).padding(horizontal = 2.dp)
                    ) {
                        Text(e.title, fontFamily = DotFont, fontSize = if (compact) 9.sp else 11.sp, color = theme.outline, maxLines = 2, lineHeight = 12.sp)
                    }
                }
            }
        }
    }
}

@Composable
fun WeatherWindow(theme: KidTheme, modifier: Modifier = Modifier) {
    val place = Store.config.weather
    val f = Weather.forecast
    RetroWindow(theme, modifier, title = "てんき", titleColor = theme.titleBars[2]) {
        Column(Modifier.fillMaxWidth().padding(14.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            when {
                place == null -> DotText("おうちのひとに ばしょを\nきめて もらってね", theme, 15)
                f == null -> DotText(if (Weather.error != null) "てんきが わからないよ" else "しらべているよ…", theme, 15)
                else -> {
                    val today = f.days.firstOrNull()
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(Weather.emoji(f.code, f.isDay), fontSize = 56.sp)
                        Column(Modifier.padding(start = 10.dp)) {
                            DotText(Weather.label(f.code), theme, 18)
                            DotText("いま ${f.temp.toInt()}℃", theme, 24)
                        }
                    }
                    if (today != null) {
                        DotText("きょう ${today.max.toInt()}℃ / ${today.min.toInt()}℃  ☂${today.rain}%", theme, 14)
                    }
                    f.days.getOrNull(1)?.let { t ->
                        DotText("あした ${Weather.emoji(t.code)} ${t.max.toInt()}℃ / ${t.min.toInt()}℃", theme, 14, modifier = Modifier.padding(top = 4.dp))
                    }
                    DotText(place.name, theme, 11, theme.outline.copy(alpha = 0.6f), modifier = Modifier.padding(top = 4.dp))
                }
            }
        }
    }
}

@Composable
fun BatteryWindow(theme: KidTheme, modifier: Modifier = Modifier) {
    val (pct, charging) = rememberBattery()
    val fill = when {
        pct < 0 -> Color(0xFFCCCCCC)
        pct <= 20 -> Color(0xFFFF7FA8)
        pct <= 50 -> Color(0xFFFFD25A)
        else -> Color(0xFF7DD87D)
    }
    RetroWindow(theme, modifier, title = "でんち", titleColor = theme.titleBars[0]) {
        Column(Modifier.fillMaxWidth().padding(14.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Row(
                    Modifier.clip(RoundedCornerShape(6.dp)).border(3.dp, theme.outline, RoundedCornerShape(6.dp)).padding(4.dp),
                    horizontalArrangement = Arrangement.spacedBy(3.dp),
                ) {
                    val filled = if (pct < 0) 0 else (pct + 9) / 20
                    repeat(5) { i ->
                        Box(Modifier.size(width = 12.dp, height = 24.dp).background(if (i < filled) fill else Color(0xFFF2EEF0)))
                    }
                }
                Box(Modifier.size(width = 5.dp, height = 12.dp).background(theme.outline))
            }
            DotText(if (pct < 0) "--%" else "$pct%", theme, 26, modifier = Modifier.padding(top = 6.dp))
            DotText(
                when {
                    charging -> "⚡じゅうでんちゅう"
                    pct in 0..20 -> "じゅうでん してね"
                    else -> " "
                },
                theme, 13,
            )
        }
    }
}

/** でんちの のこり（%、じゅうでんちゅうか）。取れないときは -1 */
@Composable
fun rememberBattery(): Pair<Int, Boolean> {
    val context = LocalContext.current
    val battery by produceState(-1 to false) {
        while (true) {
            value = runCatching {
                val i = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
                val level = i?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
                val scale = i?.getIntExtra(BatteryManager.EXTRA_SCALE, 100) ?: 100
                val status = i?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
                (if (level >= 0) level * 100 / scale else -1) to
                    (status == BatteryManager.BATTERY_STATUS_CHARGING || status == BatteryManager.BATTERY_STATUS_FULL)
            }.getOrDefault(-1 to false)
            delay(30_000)
        }
    }
    return battery
}

// ---------------------------------------------------------------- おんがく

@Composable
fun MusicWindow(theme: KidTheme, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    var playing by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        while (true) {
            playing = AudioPlayer.playing || Media.isPlaying(context)
            delay(1000)
        }
    }
    val pkg = Store.config.musicPackage
    val wave = rememberInfiniteTransition(label = "note")
    val y by wave.animateFloat(0f, -6f, infiniteRepeatable(tween(400), RepeatMode.Reverse), label = "y")
    RetroWindow(theme, modifier, title = "おんがく", titleColor = theme.titleBars[3]) {
        Column(Modifier.fillMaxWidth().padding(10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("🎵", fontSize = 22.sp, modifier = Modifier.offset(y = if (playing) y.dp else 0.dp))
                DotText(
                    AudioPlayer.current?.title ?: if (playing) "ながれているよ" else "とまっているよ",
                    theme, 14, modifier = Modifier.padding(start = 4.dp),
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                val own = AudioPlayer.current != null
                RetroIconButton("⏮", theme, size = 48.dp) { if (own) AudioPlayer.previous() else Media.previous(context) }
                RetroIconButton(if (playing) "⏸" else "▶️", theme, selected = playing, size = 56.dp) {
                    if (own) AudioPlayer.toggle() else Media.playPause(context)
                }
                RetroIconButton("⏭", theme, size = 48.dp) { if (own) AudioPlayer.next() else Media.next(context) }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                RetroIconButton("🔉", theme, size = 40.dp) { Media.volume(context, false) }
                RetroIconButton("🔊", theme, size = 40.dp) { Media.volume(context, true) }
                if (pkg != null) {
                    val icon = remember(pkg) { Launcher.icon(context, pkg) }
                    Box(
                        Modifier.padding(5.dp).size(40.dp).clip(RoundedCornerShape(12.dp)).background(Color.White)
                            .border(3.dp, theme.outline, RoundedCornerShape(12.dp))
                            .clickable { Launcher.launchPackage(context, pkg) },
                        contentAlignment = Alignment.Center,
                    ) {
                        if (icon != null) Image(icon, "おんがくアプリ", Modifier.size(28.dp))
                        else Text("🎧", fontSize = 20.sp)
                    }
                }
            }
        }
    }
}

// ---------------------------------------------------------------- ウィジェット のタブ

@Composable
fun WidgetPage(theme: KidTheme) {
    val ids = Store.config.widgetIds
    if (ids.isEmpty()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            RetroWindow(theme, title = "おしらせ", titleColor = theme.titleBars[3]) {
                Column(Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("🧩", fontSize = 56.sp)
                    DotText("おうちのひとに ウィジェットを\nついか してもらってね", theme, 18)
                }
            }
        }
        return
    }
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(end = 6.dp, bottom = 12.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        ids.forEachIndexed { i, id ->
            val info = remember(id) { Widgets.info(id) }
            val density = LocalDensity.current.density
            val height = remember(id) { ((info?.minHeight ?: 0) / density).toInt().coerceIn(120, 420) }
            RetroWindow(
                theme, Modifier.fillMaxWidth(),
                title = info?.let { Widgets.label(it) } ?: "ウィジェット",
                titleColor = theme.titleBars[i % theme.titleBars.size],
            ) {
                AndroidView(
                    factory = { ctx -> Widgets.view(ctx, id) ?: android.widget.TextView(ctx).apply { text = "ウィジェットを表示できません" } },
                    update = { v -> if (v is android.appwidget.AppWidgetHostView) Widgets.resize(v, 600, height) },
                    modifier = Modifier.fillMaxWidth().height(height.dp).padding(6.dp),
                )
            }
        }
    }
}

// ---------------------------------------------------------------- おうちのひとからの呼びかけ

@Composable
fun RemoteDialog(theme: KidTheme) {
    val msg = Remote.message ?: return
    val t = rememberInfiniteTransition(label = "call")
    val y by t.animateFloat(0f, -16f, infiniteRepeatable(tween(500), RepeatMode.Reverse), label = "y")
    Dialog(onDismissRequest = {}) {
        RetroWindow(theme, Modifier.width(460.dp), title = "おうちのひとから", titleColor = Color(0xFFFFB3CF)) {
            Column(Modifier.fillMaxWidth().padding(28.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(theme.mascot, fontSize = 72.sp, modifier = Modifier.offset(y = y.dp))
                    Text("📣", fontSize = 48.sp)
                }
                DotText(msg.text, theme, 30, modifier = Modifier.padding(vertical = 12.dp))
                if (msg.lockMinutes != null) DotText("（どうがは ${msg.lockMinutes}ふん おやすみ）", theme, 16)
                RetroButton("わかった！", theme, theme.titleBars[1], fontSize = 24, modifier = Modifier.padding(top = 16.dp)) {
                    Remote.message = null
                }
            }
        }
    }
}
