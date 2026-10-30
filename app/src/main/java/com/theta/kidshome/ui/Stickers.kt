package com.theta.kidshome.ui

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.theta.kidshome.data.Sticker
import com.theta.kidshome.data.Store
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.roundToInt

/** ホームの上に はった ふせん。ゆびで うごかせて、タップで大きく見る・はがす */
@Composable
fun StickerLayer(theme: KidTheme, stickers: List<Sticker>, width: Dp, height: Dp) {
    var open by remember { mutableStateOf<Sticker?>(null) }
    stickers.forEach { s -> androidx.compose.runtime.key(s.id) { StickerNote(theme, s, width, height) { open = s } } }
    open?.let { s -> StickerDialog(theme, s) { open = null } }
}

@Composable
private fun stickerImage(s: Sticker): ImageBitmap? {
    val context = LocalContext.current
    val img by produceState<ImageBitmap?>(null, s.image) {
        value = s.image?.let { name ->
            withContext(Dispatchers.IO) { BitmapFactory.decodeFile(File(File(context.filesDir, "stickers"), name).path)?.asImageBitmap() }
        }
    }
    return img
}

@Composable
private fun StickerNote(theme: KidTheme, s: Sticker, width: Dp, height: Dp, onOpen: () -> Unit) {
    val density = LocalDensity.current
    val wPx = with(density) { width.toPx() }
    val hPx = with(density) { height.toPx() }
    var pos by remember(s.x, s.y) { mutableStateOf(Offset(s.x * wPx, s.y * hPx)) }
    val img = stickerImage(s)
    Box(
        Modifier
            .offset { IntOffset(pos.x.roundToInt(), pos.y.roundToInt()) }
            .rotate(s.rotation)
            .width(150.dp)
            .stickerShadow(theme.outline.copy(alpha = 0.35f), 4.dp, 4.dp)
            .background(Color(s.color))
            .border(2.dp, theme.outline.copy(alpha = 0.5f))
            .pointerInput(s.id) {
                detectDragGestures(
                    onDrag = { change, d -> change.consume(); pos += d },
                    onDragEnd = { Store.moveSticker(s.id, pos.x / wPx, pos.y / hPx) },
                )
            }
            .pointerInput(s.id) { detectTapGestures { onOpen() } }
            .padding(start = 8.dp, end = 8.dp, top = 14.dp, bottom = 8.dp),
    ) {
        if (img != null) {
            Image(img, null, Modifier.fillMaxWidth().heightIn(max = 140.dp), contentScale = ContentScale.Fit)
        } else {
            Text(s.text, fontFamily = DotFont, fontSize = 15.sp, color = theme.outline, maxLines = 6, overflow = TextOverflow.Ellipsis)
        }
        // マスキングテープ
        Box(
            Modifier.align(Alignment.TopCenter).offset(y = (-22).dp).size(width = 56.dp, height = 18.dp)
                .rotate(-4f).background(theme.titleBars[0].copy(alpha = 0.75f))
        )
    }
}

@Composable
private fun StickerDialog(theme: KidTheme, s: Sticker, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val img = stickerImage(s)
    Dialog(onDismissRequest = onDismiss) {
        RetroWindow(theme, Modifier.width(520.dp), title = "ふせん", titleColor = theme.titleBars[1]) {
            Column(Modifier.padding(18.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).background(Color(s.color)).padding(12.dp)) {
                    if (img != null) Image(img, null, Modifier.fillMaxWidth().heightIn(max = 420.dp), contentScale = ContentScale.Fit)
                    else DotText(s.text, theme, 22)
                }
                Row(Modifier.padding(top = 16.dp)) {
                    RetroButton("もどる", theme, theme.titleBars[1], onClick = onDismiss)
                    RetroButton("はがす", theme, Color.White, modifier = Modifier.padding(start = 12.dp)) {
                        Store.removeSticker(context, s.id)
                        onDismiss()
                    }
                }
            }
        }
    }
}
