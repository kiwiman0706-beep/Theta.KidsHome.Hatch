package com.theta.kidshome.ui

import android.graphics.Bitmap
import android.graphics.ImageDecoder
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.theta.kidshome.data.Nurie
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** 🖍 ぬりえ を えらぶ（内蔵・足した もの・画像 から つくる） */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun NurieDialog(theme: KidTheme, onDismiss: () -> Unit, onPick: (Bitmap) -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var ver by remember { mutableIntStateOf(0) }
    val items = remember(ver) { Nurie.list(context) }
    var busy by remember { mutableStateOf(false) }
    val pick = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) scope.launch {
            busy = true
            withContext(Dispatchers.IO) {
                runCatching {
                    val src = ImageDecoder.decodeBitmap(ImageDecoder.createSource(context.contentResolver, uri)) { d, _, _ -> d.allocator = ImageDecoder.ALLOCATOR_SOFTWARE }
                    Nurie.addFromImage(context, src)
                }
            }
            busy = false; ver++
        }
    }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        RetroWindow(theme, Modifier.width(860.dp).height(620.dp), title = "🖍 ぬりえ を えらぶ", titleColor = theme.titleBars[2]) {
            Column(Modifier.fillMaxSize().padding(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    DotText("えらんで 🪣 バケツ で ぬってね（ペン でも ぬれるよ）", theme, 15, modifier = Modifier.weight(1f))
                    RetroButton(if (busy) "…" else "➕ がぞう から つくる", theme, theme.titleBars[0], fontSize = 15) {
                        if (!busy) pick.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                    }
                    Spacer(Modifier.width(6.dp))
                    RetroButton("とじる", theme, Color.White, fontSize = 15) { onDismiss() }
                }
                LazyVerticalGrid(GridCells.Adaptive(180.dp), Modifier.fillMaxSize().padding(top = 8.dp)) {
                    items(items, key = { it.id }) { item ->
                        val thumb by produceState<ImageBitmap?>(null, item.id) {
                            value = withContext(Dispatchers.Default) {
                                Nurie.bitmap(context, item.id)?.let { b ->
                                    val s = 360f / maxOf(b.width, b.height)
                                    val small = Bitmap.createScaledBitmap(b, (b.width * s).toInt().coerceAtLeast(1), (b.height * s).toInt().coerceAtLeast(1), true)
                                    Bitmap.createBitmap(small.width, small.height, Bitmap.Config.ARGB_8888).also { o ->
                                        android.graphics.Canvas(o).apply { drawColor(android.graphics.Color.WHITE); drawBitmap(small, 0f, 0f, null) }
                                    }.asImageBitmap()
                                }
                            }
                        }
                        Column(
                            Modifier.padding(6.dp).clip(RoundedCornerShape(14.dp)).background(Color.White)
                                .border(3.dp, theme.outline, RoundedCornerShape(14.dp))
                                .combinedClickable(
                                    onClick = { scope.launch { withContext(Dispatchers.Default) { Nurie.bitmap(context, item.id) }?.let(onPick) } },
                                    onLongClick = { if (item.id.startsWith("f:")) { Nurie.delete(context, item.id); ver++ } },
                                ).padding(6.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            Box(Modifier.fillMaxWidth().aspectRatio(16f / 11f), contentAlignment = Alignment.Center) {
                                thumb?.let { Image(it, null, Modifier.fillMaxSize(), contentScale = ContentScale.Fit) }
                            }
                            Text(item.name, fontSize = 15.sp, color = theme.outline)
                        }
                    }
                }
            }
        }
    }
}
