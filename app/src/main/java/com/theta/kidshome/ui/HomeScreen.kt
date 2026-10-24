package com.theta.kidshome.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.theta.kidshome.data.AppConfig
import com.theta.kidshome.data.Launcher
import com.theta.kidshome.data.Store
import com.theta.kidshome.data.Tile
import com.theta.kidshome.data.TileType
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.Calendar
import kotlin.math.PI
import kotlin.math.sin
import kotlin.random.Random

@Composable
fun HomeScreen(onOpenDrawing: () -> Unit, onOpenParent: () -> Unit) {
    val config = Store.config
    val profile = config.currentProfile
    val theme = themeById(profile.themeId)
    val context = LocalContext.current

    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            now = System.currentTimeMillis()
            delay(1000)
        }
    }
    val locked = config.isVideoLocked(now)
    var showPin by remember { mutableStateOf(false) }
    var showLockedDialog by remember { mutableStateOf(false) }

    Box(
        Modifier.fillMaxSize()
            .background(Brush.verticalGradient(listOf(theme.bgTop, theme.bgBottom)))
    ) {
        FloatingDecorations(theme)
        Column(
            Modifier.fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .padding(horizontal = 20.dp, vertical = 12.dp)
        ) {
            TopBar(config, theme, now, onLongPressGear = { showPin = true })
            if (locked) LockBanner(config, theme, now)
            Spacer(Modifier.height(12.dp))
            if (profile.tiles.isEmpty()) {
                EmptyState(theme)
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(150.dp),
                    horizontalArrangement = Arrangement.spacedBy(18.dp),
                    verticalArrangement = Arrangement.spacedBy(18.dp),
                    contentPadding = PaddingValues(bottom = 24.dp, top = 4.dp),
                    modifier = Modifier.fillMaxSize(),
                ) {
                    items(profile.tiles, key = { it.id }) { tile ->
                        val index = profile.tiles.indexOf(tile)
                        TileButton(
                            tile = tile,
                            theme = theme,
                            bubble = theme.bubbles[index % theme.bubbles.size],
                            locked = locked && tile.isVideo,
                        ) {
                            when {
                                tile.isVideo && locked -> showLockedDialog = true
                                tile.type == TileType.DRAWING -> onOpenDrawing()
                                else -> Launcher.launch(context, tile)
                            }
                        }
                    }
                }
            }
        }
        if (showPin) PinGate(onSuccess = { showPin = false; onOpenParent() }, onDismiss = { showPin = false })
        if (showLockedDialog) LockedDialog(config, theme, now) { showLockedDialog = false }
    }
}

// ---------------------------------------------------------------- 上のバー

@Composable
private fun TopBar(config: AppConfig, theme: KidTheme, now: Long, onLongPressGear: () -> Unit) {
    val cal = Calendar.getInstance().apply { timeInMillis = now }
    val hour = cal.get(Calendar.HOUR_OF_DAY)
    val greeting = when (hour) {
        in 4..9 -> "おはよう"
        in 10..16 -> "こんにちは"
        else -> "こんばんは"
    }
    val week = listOf("にち", "げつ", "か", "すい", "もく", "きん", "ど")[cal.get(Calendar.DAY_OF_WEEK) - 1]

    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        BouncingMascot(theme.mascot)
        Column(Modifier.padding(start = 8.dp)) {
            Text(
                "$greeting、${config.currentProfile.name}！",
                fontSize = 24.sp, fontWeight = FontWeight.Bold, color = theme.text,
                maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
            Text(
                "%d:%02d   %dがつ%dにち（%s）".format(
                    hour, cal.get(Calendar.MINUTE),
                    cal.get(Calendar.MONTH) + 1, cal.get(Calendar.DAY_OF_MONTH), week,
                ),
                fontSize = 18.sp, color = theme.text.copy(alpha = 0.8f),
            )
        }
        Spacer(Modifier.weight(1f))
        if (config.profiles.size > 1) {
            config.profiles.forEach { p ->
                ProfileAvatar(
                    name = p.name,
                    theme = themeById(p.themeId),
                    selected = p.id == config.currentProfileId,
                    textColor = theme.text,
                ) { Store.update { it.copy(currentProfileId = p.id) } }
            }
        }
        Spacer(Modifier.width(8.dp))
        HoldButton(theme, onLongPressGear)
    }
}

@Composable
private fun BouncingMascot(mascot: String) {
    val t = rememberInfiniteTransition(label = "mascot")
    val y by t.animateFloat(
        0f, -8f, infiniteRepeatable(tween(900), RepeatMode.Reverse), label = "y",
    )
    Text(mascot, fontSize = 52.sp, modifier = Modifier.offset(y = y.dp))
}

@Composable
private fun ProfileAvatar(
    name: String, theme: KidTheme, selected: Boolean, textColor: Color, onClick: () -> Unit,
) {
    val scale by animateFloatAsState(if (selected) 1.1f else 0.9f, label = "avatar")
    Column(
        Modifier.padding(horizontal = 6.dp).scale(scale)
            .clip(RoundedCornerShape(20.dp)).clickable(onClick = onClick).padding(4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier.size(60.dp).clip(CircleShape)
                .background(Brush.verticalGradient(listOf(theme.bgTop, theme.bgBottom)))
                .border(if (selected) 4.dp else 2.dp, if (selected) theme.accent else Color.White, CircleShape),
            contentAlignment = Alignment.Center,
        ) { Text(theme.mascot, fontSize = 32.sp) }
        Text(
            name, fontSize = 13.sp, color = textColor, maxLines = 1,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
        )
    }
}

/** 2秒ながおしで開く、おうちのひとボタン(子どもがうっかり押さないように) */
@Composable
private fun HoldButton(theme: KidTheme, onTriggered: () -> Unit) {
    var progress by remember { mutableFloatStateOf(0f) }
    val scope = rememberCoroutineScope()
    Box(
        Modifier.size(52.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.5f))
            .pointerInput(Unit) {
                awaitEachGesture {
                    awaitFirstDown()
                    val job = scope.launch {
                        val start = System.currentTimeMillis()
                        while (true) {
                            progress = ((System.currentTimeMillis() - start) / 2000f).coerceAtMost(1f)
                            if (progress >= 1f) {
                                progress = 0f
                                onTriggered()
                                break
                            }
                            delay(16)
                        }
                    }
                    waitForUpOrCancellation()
                    job.cancel()
                    progress = 0f
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        if (progress > 0f) {
            CircularProgressIndicator(
                progress = { progress }, color = theme.accent, strokeWidth = 4.dp,
                modifier = Modifier.size(48.dp),
            )
        }
        Text("⚙️", fontSize = 24.sp, modifier = Modifier.alpha(0.6f))
    }
}

// ---------------------------------------------------------------- ロック表示

private fun remainingText(until: Long, now: Long): String {
    val min = ((until - now + 59_999) / 60_000).toInt()
    return if (min >= 60) "${min / 60}じかん${if (min % 60 > 0) " ${min % 60}ふん" else ""}" else "${min}ふん"
}

@Composable
private fun LockBanner(config: AppConfig, theme: KidTheme, now: Long) {
    Surface(
        shape = RoundedCornerShape(24.dp),
        color = theme.accent,
        modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
    ) {
        Row(Modifier.padding(horizontal = 20.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(config.lockReason.emoji, fontSize = 32.sp)
            Text(
                "${config.lockReason.title}！ どうがは あと ${remainingText(config.videoLockUntil, now)} まってね",
                fontSize = 20.sp, fontWeight = FontWeight.Bold, color = theme.onAccent,
                modifier = Modifier.padding(start = 12.dp),
            )
        }
    }
}

@Composable
private fun LockedDialog(config: AppConfig, theme: KidTheme, now: Long, onDismiss: () -> Unit) {
    Dialog(onDismissRequest = onDismiss) {
        Surface(shape = RoundedCornerShape(36.dp), color = Color.White) {
            Column(Modifier.padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text("${theme.mascot}${config.lockReason.emoji}", fontSize = 64.sp)
                Text(
                    "いまは ${config.lockReason.title} だよ！",
                    fontSize = 24.sp, fontWeight = FontWeight.Bold, color = Color(0xFF555555),
                )
                Text(
                    "どうがは あと ${remainingText(config.videoLockUntil, now)} で みられるよ",
                    fontSize = 18.sp, color = Color(0xFF777777), textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = 8.dp),
                )
                Spacer(Modifier.height(20.dp))
                Box(
                    Modifier.clip(RoundedCornerShape(50)).background(theme.accent)
                        .clickable(onClick = onDismiss).padding(horizontal = 40.dp, vertical = 14.dp)
                ) { Text("わかった！", fontSize = 22.sp, color = theme.onAccent, fontWeight = FontWeight.Bold) }
            }
        }
    }
}

// ---------------------------------------------------------------- タイル

@Composable
private fun TileButton(tile: Tile, theme: KidTheme, bubble: Color, locked: Boolean, onClick: () -> Unit) {
    val context = LocalContext.current
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.9f else 1f, label = "press")
    val icon = remember(tile.packageName, tile.type) {
        if (tile.type == TileType.APP && tile.packageName != null) Launcher.icon(context, tile.packageName) else null
    }
    val service = remember(tile.url) { tile.url?.let { Launcher.detectService(it) } }

    Box(
        Modifier.aspectRatio(0.9f).scale(scale)
            .shadow(if (theme.tileBg.alpha > 0.5f) 6.dp else 0.dp, RoundedCornerShape(32.dp))
            .clip(RoundedCornerShape(32.dp))
            .background(theme.tileBg)
            .clickable(interactionSource = interaction, indication = null, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            Modifier.padding(12.dp).alpha(if (locked) 0.35f else 1f),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(
                Modifier.size(88.dp).clip(CircleShape).background(bubble),
                contentAlignment = Alignment.Center,
            ) {
                if (icon != null) {
                    Image(icon, contentDescription = null, modifier = Modifier.size(68.dp))
                } else {
                    Text(tile.emoji, fontSize = 48.sp)
                }
            }
            Spacer(Modifier.height(10.dp))
            Text(
                tile.label, fontSize = 18.sp, fontWeight = FontWeight.Bold, color = theme.text,
                textAlign = TextAlign.Center, maxLines = 2, overflow = TextOverflow.Ellipsis,
                lineHeight = 22.sp,
            )
            if (service != null) {
                Text(service.name, fontSize = 12.sp, color = theme.text.copy(alpha = 0.6f))
            }
        }
        if (locked) Text("🔒", fontSize = 56.sp)
    }
}

@Composable
private fun EmptyState(theme: KidTheme) {
    Column(
        Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(theme.mascot, fontSize = 96.sp)
        Text(
            "おうちのひとに あそぶものを\nついか してもらってね",
            fontSize = 22.sp, color = theme.text, textAlign = TextAlign.Center,
        )
    }
}

// ---------------------------------------------------------------- 背景のふわふわ

@Composable
private fun FloatingDecorations(theme: KidTheme) {
    val t = rememberInfiniteTransition(label = "deco")
    val phase by t.animateFloat(
        0f, 1f, infiniteRepeatable(tween(8000, easing = LinearEasing)), label = "phase",
    )
    val items = remember(theme.id) {
        val r = Random(theme.id.hashCode())
        List(14) {
            Deco(theme.decorations[it % theme.decorations.size], r.nextFloat(), r.nextFloat(), 22 + r.nextInt(26), r.nextFloat())
        }
    }
    BoxWithConstraints(Modifier.fillMaxSize()) {
        items.forEach { d ->
            val wave = sin((phase + d.phase) * 2 * PI).toFloat()
            Text(
                d.emoji,
                fontSize = d.size.sp,
                modifier = Modifier
                    .offset(x = maxWidth * d.x - 20.dp, y = maxHeight * d.y + (wave * 14).dp)
                    .alpha(0.28f),
            )
        }
    }
}

private data class Deco(val emoji: String, val x: Float, val y: Float, val size: Int, val phase: Float)
