package com.theta.kidshome.ui

import android.graphics.BitmapFactory
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.theta.kidshome.data.DollPack
import com.theta.kidshome.data.Store
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** へや の ガイド役（きせかえ ドール）：ふわふわ、タップ で ぴょん */
@Composable
fun DollGuide(profile: String, modifier: Modifier, onTap: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val img by produceState<ImageBitmap?>(null, profile, DollPack.version, DollPack.pack) {
        value = withContext(Dispatchers.IO) {
            DollPack.compose(context, DollPack.look(context, profile).filterKeys { it != "bg" })?.asImageBitmap()
        }
    }
    val bob by rememberInfiniteTransition(label = "bob").animateFloat(0f, 1f, infiniteRepeatable(tween(1600), RepeatMode.Reverse), label = "b")
    val jump = remember { Animatable(0f) }
    Box(modifier.clickable {
        onTap()
        scope.launch { jump.animateTo(1f, tween(180)); jump.animateTo(0f, tween(260)) }
    }) {
        val i = img ?: return@Box
        Canvas(Modifier.fillMaxSize().graphicsLayer { translationY = -bob * 6.dp.toPx() - jump.value * 40.dp.toPx(); scaleY = 1f + bob * 0.01f }) {
            // ひと の いる ところ だけ（400×600 の うち）
            val src = IntOffset(75, 60); val srcSize = IntSize(250, 530)
            val k = minOf(size.width / srcSize.width, size.height / srcSize.height)
            val w = srcSize.width * k; val h = srcSize.height * k
            drawImage(i, src, srcSize, IntOffset(((size.width - w) / 2).toInt(), (size.height - h).toInt()), IntSize(w.toInt(), h.toInt()))
        }
    }
}

/** きせかえ（へや の 👗 から）：左 に すがた、右 に スロット と アイテム。🔒 は 🎟 で あける */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DollDressUp(theme: KidTheme, profile: String) {
    val context = LocalContext.current
    val p = DollPack.pack ?: return
    val v = DollPack.version
    val look = remember(profile, v) { DollPack.look(context, profile) }
    val slots = remember(v) { p.slots.filter { DollPack.visible(context, it) } }
    var slotId by remember { mutableStateOf(slots.firstOrNull { it.id == "hair" }?.id ?: slots.first().id) }
    val slot = slots.firstOrNull { it.id == slotId } ?: slots.first()
    val tickets = remember(profile, v) { DollPack.tickets(context, profile) }
    var ask by remember { mutableStateOf<DollPack.Item?>(null) }
    var msg by remember { mutableStateOf<String?>(null) }
    val img by produceState<ImageBitmap?>(null, look) { value = withContext(Dispatchers.IO) { DollPack.compose(context, look)?.asImageBitmap() } }

    Row(Modifier.fillMaxSize().padding(8.dp)) {
        Column(Modifier.width(300.dp).fillMaxHeight(), horizontalAlignment = Alignment.CenterHorizontally) {
            Box(Modifier.weight(1f).aspectRatio(2f / 3f).clip(RoundedCornerShape(16.dp)).background(Color(0xFFFFF4FA))) {
                img?.let { Image(it, null, Modifier.fillMaxSize(), contentScale = ContentScale.Fit) }
            }
            DotText("🎟 ${tickets} まい", theme, 22, theme.text)
            DotText("もじ れんしゅう で ${DollPack.perTicket(context)} もじ かくと 🎟 1 まい", theme, 12, theme.outline.copy(alpha = 0.7f))
            Row(Modifier.padding(top = 4.dp)) {
                RetroButton("🎲", theme, theme.titleBars[2], fontSize = 18, modifier = Modifier.padding(end = 6.dp)) { DollPack.random(context, profile) }
                val g = DollPack.guideOn(context, profile)
                RetroButton(if (g) "⭐ ガイド" else "☆ ガイド", theme, if (g) theme.titleBars[1] else Color.White, fontSize = 16) { DollPack.setGuide(context, profile, !g) }
            }
            msg?.let { DotText(it, theme, 13, Color(0xFFD03B5C)) }
        }
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f).fillMaxHeight()) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                slots.forEach { s ->
                    val on = s.id == slot.id
                    Box(
                        Modifier.clip(RoundedCornerShape(10.dp)).background(if (on) theme.titleBars[0] else Color.White)
                            .border(2.dp, theme.outline.copy(alpha = if (on) 1f else 0.3f), RoundedCornerShape(10.dp)).clickable { slotId = s.id; msg = null }
                            .padding(horizontal = 8.dp, vertical = 3.dp),
                    ) { Text("${s.emoji} ${s.label}", fontSize = 13.sp, color = theme.outline) }
                }
            }
            val total = slot.items.size
            val open = slot.items.count { DollPack.unlocked(context, profile, slot, it) }
            DotText("${slot.emoji} ${slot.label}　あけた ${open} / ${total}", theme, 14, modifier = Modifier.padding(vertical = 4.dp))
            LazyVerticalGrid(GridCells.Adaptive(92.dp), Modifier.weight(1f)) {
                items(slot.items, key = { it.id }) { item ->
                    val unlocked = DollPack.unlocked(context, profile, slot, item)
                    val worn = item.id in look[slot.id].orEmpty()
                    Box(
                        Modifier.padding(3.dp).aspectRatio(1f).clip(RoundedCornerShape(12.dp)).background(if (worn) theme.titleBars[1] else Color.White)
                            .border(if (worn) 3.dp else 1.dp, if (worn) theme.outline else theme.outline.copy(alpha = 0.25f), RoundedCornerShape(12.dp))
                            .clickable {
                                msg = null
                                if (unlocked) DollPack.toggle(context, profile, slot, item)
                                else if (tickets > 0) ask = item
                                else msg = "🎟 が ないよ。もじ れんしゅう で もらおう"
                            },
                        contentAlignment = Alignment.Center,
                    ) {
                        val t by produceState<ImageBitmap?>(null, item.thumb) {
                            value = withContext(Dispatchers.IO) { runCatching { BitmapFactory.decodeFile(DollPack.file(context, item.thumb).path)?.asImageBitmap() }.getOrNull() }
                        }
                        t?.let { Image(it, null, Modifier.fillMaxSize().padding(6.dp).alpha(if (unlocked) 1f else 0.35f), contentScale = ContentScale.Fit) }
                        if (!unlocked) Text("🔒", fontSize = 26.sp)
                    }
                }
            }
        }
    }
    ask?.let { item ->
        AlertDialog(
            onDismissRequest = { ask = null },
            title = { Text("🎟 1 まい で あける？") },
            text = { Text("のこり ${tickets} まい") },
            confirmButton = {
                TextButton(onClick = {
                    if (DollPack.unlock(context, profile, item)) DollPack.toggle(context, profile, slot, item)
                    ask = null
                }) { Text("あける！") }
            },
            dismissButton = { TextButton(onClick = { ask = null }) { Text("やめる") } },
        )
    }
}

/** おやメニュー：きせかえ ドール */
@Composable
fun DollSection() {
    val context = LocalContext.current
    val config = Store.config
    val pick = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> if (uri != null) DollPack.importZip(context, uri) }
    val v = DollPack.version
    Section("👗 きせかえ ドール（ごほうび）") {
        Text(
            "購入した ドール素材（非公開リポジトリ の Release「doll-pack.zip」）を、ガイド役 と おなじ トークン で とってきます。" +
                "からだ と かお は はじめ から、かみがた・ふく・アクセ は 🎟 で あけます（もじ れんしゅう で きまった 数 を かくと 1 まい）。",
            fontSize = 13.sp,
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            Button(enabled = !DollPack.busy, onClick = { DollPack.update(context, force = true) }) { Text("📥 とってくる") }
            OutlinedButton(onClick = { pick.launch(arrayOf("application/zip", "application/octet-stream")) }, modifier = Modifier.padding(start = 8.dp)) { Text("zip から") }
            if (DollPack.installed) TextButton(onClick = { DollPack.remove(context) }) { Text("はずす", color = Color(0xFFD03B5C)) }
        }
        DollPack.status?.let { Text(it, fontSize = 13.sp) }
        val p = DollPack.pack ?: return@Section
        Text("はいって いる：${p.slots.sumOf { it.items.size }} こ（${DollPack.installedTag(context)}）", fontSize = 13.sp, fontWeight = FontWeight.Bold)
        Text("🎟 1 まい に かく 字 の 数", fontSize = 13.sp)
        Row {
            listOf(3, 5, 10).forEach { n -> FilterChip(DollPack.perTicket(context) == n, { DollPack.setPerTicket(context, n) }, label = { Text("$n もじ") }, modifier = Modifier.padding(end = 6.dp)) }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("ぜんぶ あける（ごほうび なし）", modifier = Modifier.weight(1f), fontSize = 14.sp)
            Switch(DollPack.allOpen(context), { DollPack.setAllOpen(context, it) })
        }
        config.profiles.forEach { pr ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("${pr.name}：🎟 ${DollPack.tickets(context, pr.id)} まい", modifier = Modifier.weight(1f), fontSize = 14.sp)
                TextButton(onClick = { DollPack.addBonus(context, pr.id, 1) }) { Text("🎟 ＋1 おまけ") }
                Text("ガイド", fontSize = 13.sp); Switch(DollPack.guideOn(context, pr.id), { DollPack.setGuide(context, pr.id, it) })
            }
        }
        Text("見せる もの（ぶき・したぎ は はじめ かくして あります）", fontSize = 13.sp, modifier = Modifier.padding(top = 6.dp))
        p.slots.forEach { s ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("${s.emoji} ${s.label}（${s.items.size}）", modifier = Modifier.weight(1f), fontSize = 13.sp)
                Switch(DollPack.visible(context, s), { DollPack.setVisible(context, s, it) })
            }
        }
        Unit.also { v }
    }
}
