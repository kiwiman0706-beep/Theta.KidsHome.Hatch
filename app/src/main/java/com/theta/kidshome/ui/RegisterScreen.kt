package com.theta.kidshome.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.theta.kidshome.data.Money
import com.theta.kidshome.data.Store
import kotlin.random.Random

private val Lcd = Color(0xFF203A2A)
private val LcdText = Color(0xFF9CF5A8)

/**
 * おみせやさん ごっこ：レジ で ごうけい、お金 を だして はらう／おつり を わたす。
 *  - 🧒 おきゃくさん：しょうひん を えらんで、おさいふ から お金 を トレー に だす
 *  - 🧑‍🍳 おみせのひと：やってきた おきゃくさん に、レジ の お金 から おつり を わたす
 *  レベル：ぴったり（10 円 きざみ）・おつり（1 円 まで）・ぜいこみ（10%）
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun RegisterScreen(onBack: () -> Unit) {
    BackHandler(onBack = onBack)
    val context = LocalContext.current
    val theme = themeById(Store.config.currentProfile.themeId)
    val speak = rememberSpeaker()
    val shops = remember { Money.shops(context) }
    var shopIdx by remember { mutableIntStateOf(0) }
    var cashier by remember { mutableStateOf(false) }
    var level by remember { mutableIntStateOf(0) }
    val shop = shops.getOrNull(shopIdx)
    var basket by remember { mutableStateOf(listOf<Money.Item>()) }
    var paying by remember { mutableStateOf(false) } // ごうけい が 出た あと
    var tray by remember { mutableStateOf(mapOf<Int, Int>()) }
    var paid by remember { mutableIntStateOf(0) } // おきゃくさん が だした お金（おみせのひと モード）
    var guest by remember { mutableStateOf("🧑") }
    var message by remember { mutableStateOf<String?>(null) }
    var done by remember { mutableStateOf(false) }
    var stars by remember { mutableIntStateOf(0) }

    fun price(i: Money.Item) = Money.price(i.price, level)
    val subtotal = basket.sumOf { price(it) }
    val total = Money.total(basket.map { price(it) }, level)
    val traySum = Money.sum(tray)

    fun reset() { basket = emptyList(); paying = false; tray = emptyMap(); paid = 0; message = null; done = false }

    /** おみせのひと：あたらしい おきゃくさん */
    fun nextGuest() {
        reset()
        val items = shop?.items.orEmpty().filter { Money.price(it.price, level) < 1000 }
        basket = List(Random.nextInt(1, 4)) { items.random() }
        guest = listOf("🧑", "👩", "👴", "👵", "👦", "👧", "🐻", "🐰", "🐱", "🦊").random()
        val t = Money.total(basket.map { Money.price(it.price, level) }, level)
        paid = Money.customerPays(t, Random.nextInt(0, 4)).let { if (level == 0) maxOf(it, t) else it }
        paying = true
        speak("いらっしゃいませ。ごうけい ${t}えん です")
    }

    fun setMode(c: Boolean) { cashier = c; if (c) nextGuest() else reset() }

    Column(Modifier.fillMaxSize().background(theme.bgBottom).windowInsetsPadding(WindowInsets.safeDrawing).padding(12.dp)) {
        // みだし
        Row(verticalAlignment = Alignment.CenterVertically) {
            RetroIconButton("🏠", theme, onClick = onBack)
            DotText("おみせやさん", theme, 26, theme.text, modifier = Modifier.padding(start = 10.dp))
            Spacer(Modifier.width(10.dp))
            Row(Modifier.weight(1f).horizontalScroll(rememberScrollState())) {
                shops.forEachIndexed { i, s ->
                    RetroButton("${s.emoji} ${s.name}", theme, if (i == shopIdx) theme.titleBars[0] else Color.White, fontSize = 15, modifier = Modifier.padding(end = 6.dp)) {
                        shopIdx = i; if (cashier) nextGuest() else reset()
                    }
                }
            }
            RetroButton("🧒 おきゃくさん", theme, if (!cashier) theme.titleBars[1] else Color.White, fontSize = 15, modifier = Modifier.padding(end = 6.dp)) { setMode(false) }
            RetroButton("🧑‍🍳 おみせのひと", theme, if (cashier) theme.titleBars[1] else Color.White, fontSize = 15) { setMode(true) }
        }
        Row(Modifier.padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            listOf("ぴったり", "おつり", "ぜいこみ").forEachIndexed { i, l ->
                RetroButton(l, theme, if (level == i) theme.titleBars[2] else Color.White, fontSize = 14, modifier = Modifier.padding(end = 6.dp)) {
                    level = i; if (cashier) nextGuest() else reset()
                }
            }
            Spacer(Modifier.weight(1f))
            DotText("⭐ $stars", theme, 20, theme.text)
        }
        Row(Modifier.weight(1f)) {
            // ---------------- しょうひん（おきゃくさん）／おきゃくさん（おみせのひと）
            RetroWindow(theme, Modifier.weight(1.15f).fillMaxHeight().padding(end = 8.dp, bottom = 6.dp), title = if (cashier) "おきゃくさん" else "${shop?.name ?: ""} の しょうひん", titleColor = theme.titleBars[3]) {
                if (!cashier) {
                    LazyVerticalGrid(GridCells.Adaptive(104.dp), Modifier.padding(6.dp)) {
                        items(shop?.items.orEmpty()) { it ->
                            Column(
                                Modifier.padding(4.dp).clip(RoundedCornerShape(12.dp)).background(Color.White)
                                    .border(2.dp, theme.outline.copy(alpha = 0.25f), RoundedCornerShape(12.dp))
                                    .clickable(enabled = !paying) { basket = basket + it; message = null }.padding(6.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                            ) {
                                Text(it.emoji, fontSize = 40.sp)
                                Text(it.name, fontSize = 13.sp, color = theme.outline, maxLines = 1)
                                PriceTag("${price(it)}えん" + if (level >= 2) "＋ぜい" else "")
                            }
                        }
                    }
                } else {
                    Column(Modifier.fillMaxSize().padding(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(guest, fontSize = 90.sp)
                        DotText("これ を ください", theme, 20)
                        FlowRow(Modifier.padding(top = 6.dp), horizontalArrangement = Arrangement.Center) {
                            basket.forEach { Text(it.emoji, fontSize = 44.sp, modifier = Modifier.padding(4.dp)) }
                        }
                        Spacer(Modifier.height(10.dp))
                        DotText("${paid}えん で はらいます", theme, 22, theme.text)
                        MoneyRow(Money.breakdown(paid), 48.dp)
                    }
                }
            }
            // ---------------- レジ
            Column(Modifier.weight(1f).fillMaxHeight().padding(end = 8.dp)) {
                Column(
                    Modifier.fillMaxWidth().weight(1f).clip(RoundedCornerShape(16.dp)).background(Color(0xFF6F7C8A))
                        .border(3.dp, theme.outline, RoundedCornerShape(16.dp)).padding(10.dp),
                ) {
                    Column(Modifier.fillMaxWidth().weight(1f).clip(RoundedCornerShape(10.dp)).background(Lcd).padding(10.dp).verticalScroll(rememberScrollState())) {
                        basket.groupBy { it }.forEach { (it, list) ->
                            LcdLine("${it.emoji} ${it.name}" + if (list.size > 1) " ×${list.size}" else "", "${price(it) * list.size}")
                        }
                        if (basket.isEmpty()) Text(if (cashier) "" else "しょうひん を おしてね", color = LcdText.copy(alpha = 0.6f), fontSize = 16.sp)
                        if (paying || cashier) {
                            Box(Modifier.fillMaxWidth().padding(vertical = 4.dp).height(2.dp).background(LcdText.copy(alpha = 0.5f)))
                            if (level >= 2) { LcdLine("しょうけい", "$subtotal"); LcdLine("しょうひぜい 10%", "${total - subtotal}") }
                            LcdLine("ごうけい", "$total", big = true)
                            if (cashier) { LcdLine("おあずかり", "$paid"); if (done) LcdLine("おつり", "${paid - total}", big = true) }
                            else if (done) { LcdLine("おあずかり", "$traySum"); LcdLine("おつり", "${traySum - total}", big = true) }
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    // ボタン
                    if (!cashier) {
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            if (!paying) {
                                RegKey("↩️", Color(0xFFFFE29A), Modifier.weight(1f)) { if (basket.isNotEmpty()) basket = basket.dropLast(1) }
                                RegKey("ごうけい", Color(0xFFB8F0A8), Modifier.weight(2f)) {
                                    if (basket.isNotEmpty()) { paying = true; speak("ごうけい ${total}えん です") }
                                }
                            } else if (!done) {
                                RegKey("はらう", Color(0xFFB8D8FF), Modifier.weight(2f)) {
                                    when {
                                        traySum < total -> { message = "あと ${total - traySum}えん たりないよ"; speak(message!!) }
                                        else -> {
                                            done = true; stars++
                                            val change = traySum - total
                                            message = if (change == 0) "ぴったり！ ありがとうございました" else "おつり は ${change}えん です"
                                            speak(message!!)
                                        }
                                    }
                                }
                            } else {
                                RegKey("つぎ の かいもの", Color(0xFFFFB3CF), Modifier.weight(2f)) { reset() }
                            }
                        }
                    } else {
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            if (!done) RegKey("おつり を わたす", Color(0xFFB8D8FF), Modifier.weight(2f)) {
                                val change = paid - total
                                when {
                                    traySum == change -> { done = true; stars++; message = "せいかい！ おつり ${change}えん"; speak("ありがとうございました") }
                                    traySum < change -> { message = "おつり が あと ${change - traySum}えん たりないよ"; speak(message!!) }
                                    else -> { message = "おつり が ${traySum - change}えん おおいよ"; speak(message!!) }
                                }
                            } else RegKey("つぎ の おきゃくさん", Color(0xFFFFB3CF), Modifier.weight(2f)) { nextGuest() }
                            RegKey("💡", Color(0xFFFFE29A), Modifier.weight(0.7f)) {
                                val change = paid - total
                                message = if (change == 0) "おつり は ないよ（ぴったり）" else "おつり ${paid} − ${total} ＝ ${change}えん"
                            }
                        }
                    }
                }
                message?.let { DotText(it, theme, 18, theme.text, modifier = Modifier.padding(top = 6.dp)) }
                // おつり の だしかた（おきゃくさん モード で おつり が ある とき）
                if (!cashier && done && traySum > total) MoneyRow(Money.breakdown(traySum - total), 40.dp)
            }
            // ---------------- お金
            RetroWindow(
                theme, Modifier.weight(1f).fillMaxHeight().padding(bottom = 6.dp),
                title = if (cashier) "レジ の お金（おつり を だす）" else "おさいふ（はらう お金 を だす）", titleColor = theme.titleBars[1],
            ) {
                Column(Modifier.fillMaxSize().padding(8.dp)) {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Money.DENOMS.forEach { d ->
                            Box(Modifier.clickable(enabled = paying && !done) { tray = tray + (d to (tray[d] ?: 0) + 1); message = null }) { Cash(d, 62.dp) }
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    DotText("トレー：${traySum}えん（おす と もどす）", theme, 16)
                    Box(
                        Modifier.fillMaxWidth().weight(1f).clip(RoundedCornerShape(14.dp)).background(Color(0xFF3B5B8A).copy(alpha = 0.18f))
                            .border(2.dp, theme.outline.copy(alpha = 0.3f), RoundedCornerShape(14.dp)).padding(8.dp),
                    ) {
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Money.DENOMS.forEach { d ->
                                repeat(tray[d] ?: 0) {
                                    Box(Modifier.clickable(enabled = !done) {
                                        val n = (tray[d] ?: 0) - 1
                                        tray = if (n <= 0) tray - d else tray + (d to n)
                                    }) { Cash(d, 46.dp) }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun LcdLine(left: String, right: String, big: Boolean = false) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
        Text(left, color = LcdText, fontSize = if (big) 22.sp else 16.sp, fontWeight = if (big) FontWeight.Bold else FontWeight.Normal, modifier = Modifier.weight(1f), maxLines = 1)
        Text("¥$right", color = LcdText, fontSize = if (big) 30.sp else 18.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun RegKey(label: String, color: Color, modifier: Modifier, onClick: () -> Unit) {
    Box(
        modifier.height(56.dp).clip(RoundedCornerShape(12.dp)).background(color).border(2.dp, Color(0xFF3A3A4A), RoundedCornerShape(12.dp)).clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Text(label, fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Color(0xFF3A3A4A)) }
}

@Composable
private fun PriceTag(text: String) {
    Box(Modifier.padding(top = 2.dp).clip(RoundedCornerShape(6.dp)).background(Color(0xFFFFF0A8)).padding(horizontal = 6.dp, vertical = 1.dp)) {
        Text(text, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = Color(0xFFB0302E))
    }
}

@Composable
private fun MoneyRow(m: Map<Int, Int>, size: Dp) {
    Row(Modifier.horizontalScroll(rememberScrollState()).padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        m.forEach { (d, n) ->
            Cash(d, size)
            if (n > 1) Text("×$n", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Color(0xFF555555), modifier = Modifier.padding(start = 2.dp))
            Spacer(Modifier.width(8.dp))
        }
    }
}

/** 硬貨 と お札（本物 に にせた 色・あな） */
@Composable
fun Cash(d: Int, size: Dp) {
    if (d >= 1000) {
        Box(Modifier.size(size * 1.6f, size), contentAlignment = Alignment.CenterStart) {
            Canvas(Modifier.fillMaxSize()) {
                val r = CornerRadius(8f)
                drawRoundRect(Color(0xFFD6E6D0), cornerRadius = r)
                drawRoundRect(Color(0xFF6B8E6B), cornerRadius = r, style = Stroke(3f))
                drawCircle(Color(0xFFB8CDB0), this.size.height * 0.3f, Offset(this.size.width * 0.72f, this.size.height / 2))
            }
            Text("  $d", fontSize = (size.value * 0.32f).sp, fontWeight = FontWeight.Bold, color = Color(0xFF2E4A2E))
        }
        return
    }
    val k = when (d) { 500 -> 1.0f; 100 -> 0.88f; 50 -> 0.82f; 10 -> 0.9f; 5 -> 0.85f; else -> 0.78f }
    val (c1, c2) = when (d) {
        500 -> Color(0xFFF2D27A) to Color(0xFFC9A23A)
        100, 50 -> Color(0xFFEDEDED) to Color(0xFFAFAFAF)
        10 -> Color(0xFFD99A6C) to Color(0xFFA8643A)
        5 -> Color(0xFFF0D070) to Color(0xFFC8A040)
        else -> Color(0xFFF7F7F7) to Color(0xFFCCCCCC)
    }
    Box(Modifier.size(size), contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(size * k)) {
            val r = this.size.minDimension / 2
            drawCircle(Brush.radialGradient(listOf(c1, c2), center = Offset(r * 0.7f, r * 0.7f), radius = r * 1.4f), r)
            drawCircle(c2, r * 0.92f, style = Stroke(r * 0.06f))
            if (d == 5 || d == 50) drawCircle(Color.White, r * 0.22f)
        }
        Text(
            "$d", fontSize = (size.value * k * (if (d >= 100) 0.3f else 0.34f)).sp, fontWeight = FontWeight.Bold,
            color = Color(0xFF4A3A2A), modifier = if (d == 5 || d == 50) Modifier.padding(top = (size.value * 0.5f).dp) else Modifier,
        )
    }
}
