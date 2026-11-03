package com.theta.kidshome.ui

import android.media.AudioManager
import android.media.ToneGenerator
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.layout.layout
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.theta.kidshome.data.Money

/** ふたりで：つくえ に おいて、上 は おきゃくさん（さかさま）、下 は てんいんさん */
private enum class Phase { SHOP, SCAN, PAY, CHANGE, DONE }

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun RegisterDuo(theme: KidTheme, shop: Money.Shop, level: Int, onExit: () -> Unit) {
    val speak = rememberSpeaker()
    val beep = remember { runCatching { ToneGenerator(AudioManager.STREAM_MUSIC, 80) }.getOrNull() }
    DisposableEffect(Unit) { onDispose { beep?.release() } }
    var phase by remember { mutableStateOf(Phase.SHOP) }
    var basket by remember { mutableStateOf(listOf<Money.Item>()) }
    var scanned by remember { mutableStateOf(listOf<Int>()) } // かご の なんばんめ を ピッ した か
    var wallet by remember { mutableStateOf(mapOf<Int, Int>()) } // おきゃくさん が だした お金
    var change by remember { mutableStateOf(mapOf<Int, Int>()) } // てんいんさん が だす おつり
    var custMsg by remember { mutableStateOf<String?>(null) }
    var cashMsg by remember { mutableStateOf<String?>(null) }
    var stars by remember { mutableIntStateOf(0) }

    fun price(i: Money.Item) = if (i.real) i.price else Money.price(i.price, level)
    var pip by remember { mutableStateOf(false) }
    /** てんいんさん が ラベル を ピッ：かご に 入れて ピッ ずみ に */
    fun scanned(code: String) {
        val it = com.theta.kidshome.data.ShopItems.decode(code) ?: return
        if (phase != Phase.SHOP && phase != Phase.SCAN) return
        basket = basket + Money.fromLabel(it)
        scanned = scanned + (basket.size - 1)
        phase = Phase.SCAN
        beep?.startTone(ToneGenerator.TONE_PROP_BEEP, 120)
        speak("${it.name}、${it.price}えん")
    }
    val scannedItems = scanned.map { basket[it] }
    val subtotal = scannedItems.sumOf { price(it) }
    val total = Money.total(scannedItems.map { price(it) }, level)
    val paid = Money.sum(wallet)
    fun again() { phase = Phase.SHOP; basket = emptyList(); scanned = emptyList(); wallet = emptyMap(); change = emptyMap(); custMsg = null; cashMsg = null }

    val display: @Composable () -> Unit = {
        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(Lcd).padding(8.dp)) {
            scannedItems.groupBy { it }.forEach { (it, l) -> LcdLine("${it.emoji} ${it.name}" + if (l.size > 1) " ×${l.size}" else "", "${price(it) * l.size}") }
            if (phase >= Phase.PAY) {
                if (level >= 2) LcdLine("ぜい 10%", "${total - subtotal}")
                LcdLine("ごうけい", "$total", big = true)
            }
            if (phase >= Phase.CHANGE) LcdLine("おあずかり", "$paid")
            if (phase == Phase.DONE) LcdLine("おつり", "${paid - total}", big = true)
            if (scannedItems.isEmpty()) Text(" ", color = LcdText)
        }
    }

    var side by rememberSaveableSide()
    // ふたり の いち：左右（みじかい 辺 に すわる）か 上下（ながい 辺 で むかいあう）
        // ======================== おきゃくさん（むこう がわ：さかさま）
        val customer: @Composable (Modifier) -> Unit = { m -> Box(m.background(Color(0xFFFFF1F6)).padding(8.dp)) {
            Row(Modifier.fillMaxSize()) {
                Column(Modifier.weight(1.2f).fillMaxHeight()) {
                    DotText("🧒 おきゃくさん ・ ${shop.emoji} ${shop.name}", theme, 18, theme.text)
                    if (phase == Phase.SHOP) {
                        LazyVerticalGrid(GridCells.Adaptive(84.dp), Modifier.weight(1f)) {
                            items(shop.items) { it ->
                                Column(
                                    Modifier.padding(3.dp).clip(RoundedCornerShape(10.dp)).background(Color.White).border(2.dp, theme.outline.copy(alpha = 0.25f), RoundedCornerShape(10.dp))
                                        .clickable { basket = basket + it }.padding(4.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                ) {
                                    Text(it.emoji, fontSize = 30.sp)
                                    PriceTag("${price(it)}えん")
                                }
                            }
                        }
                    } else {
                        Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
                            DotText(
                                when (phase) {
                                    Phase.SCAN -> "てんいんさん が ピッ と して いるよ"
                                    Phase.PAY -> "おさいふ から お金 を だして「はらう」"
                                    Phase.CHANGE -> "おつり を まって いてね"
                                    Phase.DONE -> "🛍 ありがとうございました！"
                                    else -> ""
                                }, theme, 18,
                            )
                            display()
                            if (phase == Phase.DONE && paid > total) {
                                DotText("もらった おつり：${paid - total}えん", theme, 16)
                                MoneyRow(change, 38.dp)
                            }
                        }
                    }
                }
                Spacer(Modifier.width(8.dp))
                Column(Modifier.weight(1f).fillMaxHeight()) {
                    DotText("🧺 かご：" + basket.joinToString("") { it.emoji }, theme, 16)
                    if (phase == Phase.SHOP) Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(top = 4.dp)) {
                        RegKey("↩️", Color(0xFFFFE29A), Modifier.weight(1f)) { if (basket.isNotEmpty()) basket = basket.dropLast(1) }
                        RegKey("レジ へ", Color(0xFFB8F0A8), Modifier.weight(2f)) { if (basket.isNotEmpty()) { phase = Phase.SCAN; speak("おねがいします") } }
                    }
                    if (phase == Phase.PAY) {
                        FlowRow(Modifier.padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            Money.DENOMS.forEach { d -> Box(Modifier.clickable { wallet = wallet + (d to (wallet[d] ?: 0) + 1); custMsg = null }) { Cash(d, 44.dp) } }
                        }
                        DotText("だす お金：${paid}えん（おす と もどす）", theme, 14)
                        FlowRow(Modifier.weight(1f).fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(Color(0x223B5B8A)).padding(4.dp)) {
                            Money.DENOMS.forEach { d -> repeat(wallet[d] ?: 0) { Box(Modifier.clickable { val n = (wallet[d] ?: 0) - 1; wallet = if (n <= 0) wallet - d else wallet + (d to n) }) { Cash(d, 34.dp) } } }
                        }
                        RegKey("はらう", Color(0xFFB8D8FF), Modifier.fillMaxWidth()) {
                            if (paid < total) { custMsg = "あと ${total - paid}えん たりないよ"; speak(custMsg!!) }
                            else { phase = Phase.CHANGE; cashMsg = null; speak("${paid}えん で おねがいします") }
                        }
                    }
                    custMsg?.let { DotText(it, theme, 16, Color(0xFFD03B5C)) }
                    if (phase == Phase.DONE) RegKey("もういちど", Color(0xFFFFB3CF), Modifier.fillMaxWidth().padding(top = 6.dp)) { again() }
                }
            }
        } }


        // ======================== てんいんさん（てまえ）
        val cashier: @Composable (Modifier) -> Unit = { m -> Box(m.background(Color(0xFFF1F8FF)).padding(8.dp)) {
            Row(Modifier.fillMaxSize()) {
                Column(Modifier.weight(1f).fillMaxHeight()) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        DotText("🧑‍🍳 てんいんさん", theme, 18, theme.text, modifier = Modifier.weight(1f))
                        DotText("⭐ $stars", theme, 16)
                        Spacer(Modifier.width(8.dp))
                        RetroButton(if (pip) "📷 ON" else "📷", theme, if (pip) Color(0xFFFF8FB1) else Color.White, fontSize = 13, modifier = Modifier.padding(end = 6.dp)) { pip = !pip }
                        RetroButton(if (side) "↕" else "↔", theme, Color.White, fontSize = 13, modifier = Modifier.padding(end = 6.dp)) { side = !side }
                        RetroButton("✖", theme, Color.White, fontSize = 13, onClick = onExit)
                    }
                    when (phase) {
                        Phase.SHOP -> DotText("いらっしゃいませ！ おきゃくさん が えらんで いるよ", theme, 18)
                        Phase.SCAN -> {
                            DotText("しょうひん を おして ピッ！（ふくろ に 入る）", theme, 16)
                            Row(Modifier.horizontalScroll(rememberScrollState()).padding(vertical = 4.dp)) {
                                basket.forEachIndexed { i, it ->
                                    if (i !in scanned) Box(
                                        Modifier.padding(end = 6.dp).clip(RoundedCornerShape(12.dp)).background(Color.White).border(2.dp, theme.outline.copy(alpha = 0.4f), RoundedCornerShape(12.dp))
                                            .clickable { scanned = scanned + i; beep?.startTone(ToneGenerator.TONE_PROP_BEEP, 120) }.padding(8.dp),
                                    ) { Text(it.emoji, fontSize = 40.sp) }
                                }
                            }
                        }
                        else -> {}
                    }
                    // ふくろ
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 2.dp)) {
                        Text("🛍", fontSize = 34.sp)
                        Text(scannedItems.joinToString("") { it.emoji }, fontSize = 22.sp)
                    }
                    if (phase == Phase.SCAN && scanned.size == basket.size) RegKey("ごうけい", Color(0xFFB8F0A8), Modifier.fillMaxWidth()) {
                        phase = Phase.PAY; speak("ごうけい ${total}えん です")
                    }
                    if (phase == Phase.CHANGE) {
                        DotText("おあずかり：${paid}えん", theme, 16)
                        MoneyRow(wallet, 34.dp)
                    }
                }
                Spacer(Modifier.width(8.dp))
                Column(Modifier.weight(1f).fillMaxHeight()) {
                    display()
                    if (phase == Phase.CHANGE) {
                        FlowRow(Modifier.padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            Money.DENOMS.forEach { d -> Box(Modifier.clickable { change = change + (d to (change[d] ?: 0) + 1); cashMsg = null }) { Cash(d, 44.dp) } }
                        }
                        DotText("おつり：${Money.sum(change)}えん（おす と もどす）", theme, 14)
                        FlowRow(Modifier.weight(1f).fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(Color(0x223B5B8A)).padding(4.dp)) {
                            Money.DENOMS.forEach { d -> repeat(change[d] ?: 0) { Box(Modifier.clickable { val n = (change[d] ?: 0) - 1; change = if (n <= 0) change - d else change + (d to n) }) { Cash(d, 34.dp) } } }
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            RegKey("おつり を わたす", Color(0xFFB8D8FF), Modifier.weight(3f)) {
                                val need = paid - total
                                val give = Money.sum(change)
                                when {
                                    give == need -> { phase = Phase.DONE; stars++; cashMsg = "せいかい！"; speak("ありがとうございました") }
                                    give < need -> { cashMsg = "おつり が あと ${need - give}えん たりないよ"; speak(cashMsg!!) }
                                    else -> { cashMsg = "おつり が ${give - need}えん おおいよ"; speak(cashMsg!!) }
                                }
                            }
                            RegKey("💡", Color(0xFFFFE29A), Modifier.weight(1f)) { cashMsg = "$paid − $total ＝ ${paid - total}えん" }
                        }
                    }
                    cashMsg?.let { DotText(it, theme, 16, if (it == "せいかい！") Color(0xFF2E9E6A) else Color(0xFFD03B5C)) }
                }
            }
        } }
        Box(Modifier.fillMaxSize().scannerKeys(::scanned)) {
            if (side) Row(Modifier.fillMaxSize()) {
                customer(Modifier.weight(1f).fillMaxHeight().turned(90))
                Box(Modifier.width(10.dp).fillMaxHeight().background(Color(0xFF8A5A33)))
                cashier(Modifier.weight(1f).fillMaxHeight().turned(-90))
            } else Column(Modifier.fillMaxSize()) {
                customer(Modifier.weight(1f).fillMaxWidth().rotate(180f))
                Box(Modifier.fillMaxWidth().height(10.dp).background(Color(0xFF8A5A33)))
                cashier(Modifier.weight(1f).fillMaxWidth())
            }
            // カメラ の そば に「ここ で ピッ」（てんいんさん むき）
            if (pip) {
                val ctx = androidx.compose.ui.platform.LocalContext.current
                val top = remember { cameraOnTop(ctx) }
                Box(Modifier.align(if (top) Alignment.TopCenter else Alignment.BottomCenter)) { PipWindow(top, labelTurn = 0f) { scanned(it) } }
            }
        }
}

@Composable
private fun rememberSaveableSide() = androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf(true) }

/** 90 ど まわして おく（たて と よこ を いれかえて はかる） */
private fun Modifier.turned(deg: Int) = this.layout { m, c ->
    val p = m.measure(androidx.compose.ui.unit.Constraints(minWidth = c.minHeight, maxWidth = c.maxHeight, minHeight = c.minWidth, maxHeight = c.maxWidth))
    layout(c.maxWidth, c.maxHeight) { p.placeWithLayer((c.maxWidth - p.width) / 2, (c.maxHeight - p.height) / 2) { rotationZ = deg.toFloat() } }
}
