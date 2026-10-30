package com.theta.kidshome.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material3.Text
import com.theta.kidshome.data.Store
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.hypot
import kotlin.math.roundToInt
import kotlin.random.Random

/** パズルのむずかしさ（よこ × たて） */
private val levels = listOf(2 to 2, 3 to 2, 3 to 3, 4 to 3, 4 to 4, 6 to 4)

/** 1 まいの ピース */
private class Piece(val col: Int, val row: Int, val image: ImageBitmap) {
    var pos by mutableStateOf(Offset.Zero)
    var turns by mutableIntStateOf(0)
    var placed by mutableStateOf(false)
}

/**
 * しゃしんパズル：写真・えほんの絵・おえかき をピースにして、正しい場所に はめる。
 * 近くに置くと ぴたっ と はまる。「くるくる」をオンにすると、ピースが回っていて、タップで回して合わせる。
 */
@Composable
fun PuzzleScreen(onBack: () -> Unit) {
    BackHandler(onBack = onBack)
    val context = LocalContext.current
    val theme = themeById(Store.config.currentProfile.themeId)
    val scope = rememberCoroutineScope()
    var source by remember { mutableStateOf<Bitmap?>(null) }
    var level by remember { mutableIntStateOf(1) }
    var rotate by remember { mutableStateOf(false) }
    var hint by remember { mutableStateOf(true) }
    var choosing by remember { mutableStateOf(true) }

    val pick = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) scope.launch { source = withContext(Dispatchers.IO) { loadBitmap(context, uri) }; choosing = false }
    }

    Box(Modifier.fillMaxSize()) {
        RetroBackground(theme)
        Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing).padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                RetroIconButton("🏠", theme, onClick = onBack)
                DotText("パズル", theme, 28, theme.text, modifier = Modifier.padding(start = 10.dp))
                Spacer(Modifier.weight(1f))
                if (!choosing) {
                    levels.forEachIndexed { i, (c, r) ->
                        RetroButton("${c * r}", theme, if (level == i) theme.titleBars[0] else Color.White, fontSize = 16, modifier = Modifier.padding(start = 6.dp)) { level = i }
                    }
                    RetroIconButton("🔄", theme, rotate, 46.dp) { rotate = !rotate }
                    RetroIconButton("👻", theme, hint, 46.dp) { hint = !hint }
                    RetroIconButton("🖼️", theme, size = 46.dp) { choosing = true }
                }
            }
            Spacer(Modifier.height(10.dp))
            val src = source
            if (choosing || src == null) {
                ChooseImage(
                    theme,
                    onPhoto = { pick.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                    onScene = { name -> scope.launch { source = withContext(Dispatchers.IO) { loadAsset(context, name) }; choosing = false } },
                )
            } else {
                Board(theme, src, levels[level], rotate, hint)
            }
        }
    }
}

@Composable
private fun ChooseImage(theme: KidTheme, onPhoto: () -> Unit, onScene: (String) -> Unit) {
    RetroWindow(theme, Modifier.fillMaxSize().padding(end = 6.dp, bottom = 6.dp), title = "どの え で あそぶ？", titleColor = theme.titleBars[1]) {
        Column(Modifier.padding(16.dp)) {
            RetroButton("📷 しゃしん を えらぶ", theme, theme.titleBars[0], onClick = onPhoto)
            Spacer(Modifier.height(16.dp))
            DotText("えほん の え", theme, 18)
            Row(Modifier.horizontalScroll(rememberScrollState()).padding(top = 8.dp)) {
                (1..12).forEach { i ->
                    val name = "edupad/scenes/scene%02d.jpg".format(i)
                    val context = LocalContext.current
                    val thumb = remember(name) { runCatching { context.assets.open(name).use { BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = 4 }) }?.asImageBitmap() }.getOrNull() }
                    Box(
                        Modifier.padding(end = 10.dp).size(180.dp, 120.dp).clip(RoundedCornerShape(12.dp))
                            .border(3.dp, theme.outline, RoundedCornerShape(12.dp)).clickable { onScene(name) }
                    ) { if (thumb != null) Image(thumb, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop) }
                }
            }
        }
    }
}

@Composable
private fun Board(theme: KidTheme, src: Bitmap, level: Pair<Int, Int>, rotate: Boolean, hint: Boolean) {
    val (cols, rows) = level
    val full = remember(src) { src.asImageBitmap() }
    val pieces = remember(src, level, rotate) {
        val w = src.width / cols
        val h = src.height / rows
        mutableStateListOf<Piece>().apply {
            for (r in 0 until rows) for (c in 0 until cols) {
                this += Piece(c, r, Bitmap.createBitmap(src, c * w, r * h, w, h).asImageBitmap()).also {
                    if (rotate) it.turns = Random.nextInt(1, 4)
                }
            }
            shuffle()
        }
    }
    val done = pieces.isNotEmpty() && pieces.all { it.placed }
    val density = LocalDensity.current

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val aspect = src.width.toFloat() / src.height
        val areaW = constraints.maxWidth.toFloat()
        val areaH = constraints.maxHeight.toFloat()
        val boardW = minOf(areaW * 0.62f, areaH * aspect)
        val boardH = boardW / aspect
        val cellW = boardW / cols
        val cellH = boardH / rows
        val trayX = boardW + with(density) { 20.dp.toPx() }
        val trayW = (areaW - trayX - cellW).coerceAtLeast(cellW * 0.2f)

        // はじめは ピースを右の「おきば」に ばらばらに置く
        LaunchedEffect(pieces, areaW, areaH) {
            val rnd = Random(pieces.size)
            pieces.forEach { p ->
                if (!p.placed) p.pos = Offset(trayX + rnd.nextFloat() * trayW, rnd.nextFloat() * (areaH - cellH).coerceAtLeast(1f))
            }
        }

        // ばん（うすい見本）
        Box(
            Modifier.size(with(density) { boardW.toDp() }, with(density) { boardH.toDp() })
                .background(Color.White).border(4.dp, theme.outline)
        ) {
            if (hint) Image(full, null, Modifier.fillMaxSize().alpha(0.22f), contentScale = ContentScale.FillBounds)
        }
        // おきば
        Box(
            Modifier.offset { IntOffset(trayX.roundToInt() - 10, 0) }
                .size(with(density) { (areaW - trayX + 10).toDp() }, with(density) { areaH.toDp() })
                .clip(RoundedCornerShape(20.dp)).background(theme.bubbles[0].copy(alpha = 0.6f))
                .border(3.dp, theme.outline.copy(alpha = 0.4f), RoundedCornerShape(20.dp))
        )

        // ピース（はまったものを下に、いま動かしているものを いちばん上に）
        pieces.sortedBy { !it.placed }.forEach { p ->
            androidx.compose.runtime.key(p) {
                val scale by animateFloatAsState(if (p.placed) 1f else 0.96f, label = "piece")
                Image(
                    p.image, null,
                    contentScale = ContentScale.FillBounds,
                    modifier = Modifier
                        .offset { IntOffset(p.pos.x.roundToInt(), p.pos.y.roundToInt()) }
                        .size(with(density) { cellW.toDp() }, with(density) { cellH.toDp() })
                        .scale(scale)
                        .rotate(p.turns * 90f)
                        .border(if (p.placed) 0.dp else 2.dp, theme.outline)
                        .then(
                            if (p.placed) Modifier else Modifier
                                .pointerInput(p) {
                                    detectDragGestures(
                                        onDragStart = { pieces.remove(p); pieces.add(p) },
                                        onDrag = { change, d -> change.consume(); p.pos += d },
                                        onDragEnd = {
                                            val target = Offset(p.col * cellW, p.row * cellH)
                                            if (p.turns % 4 == 0 && hypot(p.pos.x - target.x, p.pos.y - target.y) < minOf(cellW, cellH) * 0.35f) {
                                                p.pos = target
                                                p.placed = true
                                            }
                                        },
                                    )
                                }
                                .pointerInput(p, rotate) { if (rotate) detectTapGestures { p.turns = (p.turns + 1) % 4 } }
                        ),
                )
            }
        }

        if (done) {
            Box(Modifier.size(with(density) { boardW.toDp() }, with(density) { boardH.toDp() }), contentAlignment = Alignment.Center) {
                RetroWindow(theme, title = "できた！", titleColor = theme.titleBars[1]) {
                    Column(Modifier.padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("🎉🧩🎉", fontSize = 48.sp)
                        DotText("ぜんぶ はまったよ！", theme, 24)
                    }
                }
            }
        }
    }
}

private fun fit(b: Bitmap, max: Int = 1600): Bitmap {
    val s = max.toFloat() / maxOf(b.width, b.height)
    return if (s >= 1f) b else Bitmap.createScaledBitmap(b, (b.width * s).toInt(), (b.height * s).toInt(), true)
}

private fun loadBitmap(context: Context, uri: Uri): Bitmap? = runCatching {
    fit(ImageDecoder.decodeBitmap(ImageDecoder.createSource(context.contentResolver, uri)) { d, info, _ ->
        d.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
        val s = 1600f / maxOf(info.size.width, info.size.height)
        if (s < 1f) d.setTargetSize((info.size.width * s).toInt(), (info.size.height * s).toInt())
    })
}.getOrNull()

private fun loadAsset(context: Context, name: String): Bitmap? =
    runCatching { context.assets.open(name).use { BitmapFactory.decodeStream(it) }?.let { fit(it) } }.getOrNull()
