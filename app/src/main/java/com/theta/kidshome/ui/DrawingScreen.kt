package com.theta.kidshome.ui

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Paint
import android.graphics.Path
import android.os.Environment
import android.provider.MediaStore
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.theta.kidshome.data.Store

// ---------------------------------------------------------------- データ

sealed interface Mark
data class Line(val points: List<Offset>, val color: Int, val width: Float, val rainbow: Boolean) : Mark
data class StampMark(val x: Float, val y: Float, val emoji: String, val size: Float) : Mark

/** ホームに戻っても絵が消えないように、アプリが動いている間は保持しておく */
private object Drawing {
    val marks = mutableStateListOf<Mark>()
}

private enum class Tool { PEN, RAINBOW, ERASER, STAMP }

private val palette = listOf(
    Color(0xFF222222), Color(0xFFFF4D6D), Color(0xFFFF9F1C), Color(0xFFFFD60A),
    Color(0xFF6BCB77), Color(0xFF4D96FF), Color(0xFF9B5DE5), Color(0xFFFF8FC7),
    Color(0xFF8B5A2B), Color(0xFFFFFFFF),
)
private val stamps = listOf("⭐", "💗", "🌸", "🐰", "🐱", "🦄", "🌈", "🍓", "🎀", "🦋", "🍭", "👑")
private const val PAPER = android.graphics.Color.WHITE

// ---------------------------------------------------------------- 画面

@Composable
fun DrawingScreen(onBack: () -> Unit) {
    BackHandler(onBack = onBack)
    val theme = themeById(Store.config.currentProfile.themeId)
    val context = LocalContext.current
    val density = LocalDensity.current
    val widths = with(density) { listOf(6.dp.toPx(), 16.dp.toPx(), 34.dp.toPx()) }
    val stampSize = with(density) { 64.dp.toPx() }

    var tool by remember { mutableStateOf(Tool.PEN) }
    var color by remember { mutableStateOf(palette[1]) }
    var widthIndex by remember { mutableIntStateOf(1) }
    var stamp by remember { mutableStateOf(stamps.first()) }
    var canvasSize by remember { mutableStateOf(IntSize.Zero) }
    var askClear by remember { mutableStateOf(false) }
    val current = remember { mutableStateListOf<Offset>() }

    fun currentLine(points: List<Offset>) = Line(
        points = points,
        color = if (tool == Tool.ERASER) PAPER else color.toArgb(),
        width = widths[widthIndex] * if (tool == Tool.ERASER) 1.6f else 1f,
        rainbow = tool == Tool.RAINBOW,
    )

    Column(
        Modifier.fillMaxSize()
            .background(Brush.verticalGradient(listOf(theme.bgTop, theme.bgBottom)))
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .padding(12.dp)
    ) {
        // 上のボタン
        Row(verticalAlignment = Alignment.CenterVertically) {
            ToolButton("🏠", false, theme, onClick = onBack)
            Text(
                "おえかき", fontSize = 26.sp, fontWeight = FontWeight.Bold, color = theme.text,
                modifier = Modifier.padding(start = 12.dp),
            )
            Spacer(Modifier.weight(1f))
            ToolButton("↩️", false, theme) { Drawing.marks.removeLastOrNull() }
            ToolButton("🗑️", false, theme) { if (Drawing.marks.isNotEmpty()) askClear = true }
            ToolButton("💾", false, theme) {
                val ok = canvasSize.width > 0 && saveDrawing(context, canvasSize, Drawing.marks.toList())
                Toast.makeText(context, if (ok) "ほぞん したよ！" else "ほぞん できなかったよ", Toast.LENGTH_SHORT).show()
            }
        }
        Spacer(Modifier.height(10.dp))

        // キャンバス
        Box(
            Modifier.weight(1f).fillMaxWidth()
                .shadow(6.dp, RoundedCornerShape(28.dp))
                .clip(RoundedCornerShape(28.dp))
                .background(Color.White)
                .onSizeChanged { canvasSize = it }
                .pointerInput(Unit) {
                    detectTapGestures { pos ->
                        Drawing.marks += if (tool == Tool.STAMP) {
                            StampMark(pos.x, pos.y, stamp, stampSize)
                        } else {
                            currentLine(listOf(pos))
                        }
                    }
                }
                .pointerInput(Unit) {
                    detectDragGestures(
                        onDragStart = { pos ->
                            if (tool == Tool.STAMP) {
                                Drawing.marks += StampMark(pos.x, pos.y, stamp, stampSize)
                            } else {
                                current.clear()
                                current += pos
                            }
                        },
                        onDrag = { change, _ ->
                            if (tool != Tool.STAMP) current += change.position
                        },
                        onDragEnd = {
                            if (current.isNotEmpty()) Drawing.marks += currentLine(current.toList())
                            current.clear()
                        },
                        onDragCancel = { current.clear() },
                    )
                }
        ) {
            Canvas(Modifier.fillMaxSize()) {
                drawIntoCanvas { c ->
                    renderMarks(c.nativeCanvas, Drawing.marks)
                    if (current.isNotEmpty()) renderMarks(c.nativeCanvas, listOf(currentLine(current.toList())))
                }
            }
        }
        Spacer(Modifier.height(10.dp))

        // 道具
        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ToolButton("✏️", tool == Tool.PEN, theme) { tool = Tool.PEN }
            ToolButton("🌈", tool == Tool.RAINBOW, theme) { tool = Tool.RAINBOW }
            ToolButton("🧽", tool == Tool.ERASER, theme) { tool = Tool.ERASER }
            ToolButton("🌟", tool == Tool.STAMP, theme) { tool = Tool.STAMP }
            Spacer(Modifier.width(16.dp))
            if (tool == Tool.STAMP) {
                stamps.forEach { s -> ToolButton(s, stamp == s, theme) { stamp = s } }
            } else {
                widths.forEachIndexed { i, w ->
                    val selected = widthIndex == i
                    Box(
                        Modifier.padding(4.dp).size(56.dp).clip(CircleShape)
                            .background(if (selected) theme.accent.copy(alpha = 0.3f) else Color.White)
                            .clickable { widthIndex = i },
                        contentAlignment = Alignment.Center,
                    ) {
                        Box(
                            Modifier.size(with(density) { w.toDp() }.coerceAtMost(40.dp))
                                .clip(CircleShape).background(Color(0xFF555555))
                        )
                    }
                }
                Spacer(Modifier.width(16.dp))
                palette.forEach { c ->
                    val selected = color == c && tool == Tool.PEN
                    Box(
                        Modifier.padding(4.dp).size(if (selected) 56.dp else 48.dp)
                            .clip(CircleShape).background(c)
                            .border(if (selected) 5.dp else 2.dp, if (selected) theme.accent else Color(0x22000000), CircleShape)
                            .clickable { color = c; tool = Tool.PEN }
                    )
                }
            }
        }
    }

    if (askClear) {
        Dialog(onDismissRequest = { askClear = false }) {
            Surface(shape = RoundedCornerShape(32.dp), color = Color.White) {
                Column(Modifier.padding(28.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("ぜんぶ けす？", fontSize = 26.sp, fontWeight = FontWeight.Bold, color = Color(0xFF555555))
                    Spacer(Modifier.height(20.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                        ToolButton("⭕", false, theme) { Drawing.marks.clear(); askClear = false }
                        ToolButton("❌", false, theme) { askClear = false }
                    }
                }
            }
        }
    }
}

@Composable
private fun ToolButton(emoji: String, selected: Boolean, theme: KidTheme, onClick: () -> Unit) {
    Box(
        Modifier.padding(4.dp).size(60.dp).clip(CircleShape)
            .background(if (selected) theme.accent else Color.White)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Text(emoji, fontSize = 28.sp) }
}

// ---------------------------------------------------------------- 描画と保存

private fun renderMarks(canvas: android.graphics.Canvas, marks: List<Mark>) {
    val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    for (m in marks) when (m) {
        is Line -> {
            paint.strokeWidth = m.width
            if (m.points.size == 1) {
                paint.style = Paint.Style.FILL
                paint.color = if (m.rainbow) android.graphics.Color.HSVToColor(floatArrayOf(0f, 0.7f, 1f)) else m.color
                canvas.drawCircle(m.points[0].x, m.points[0].y, m.width / 2, paint)
                continue
            }
            paint.style = Paint.Style.STROKE
            if (m.rainbow) {
                for (i in 1 until m.points.size) {
                    paint.color = android.graphics.Color.HSVToColor(floatArrayOf((i * 6f) % 360f, 0.7f, 1f))
                    val a = m.points[i - 1]
                    val b = m.points[i]
                    canvas.drawLine(a.x, a.y, b.x, b.y, paint)
                }
            } else {
                paint.color = m.color
                val path = Path().apply {
                    moveTo(m.points[0].x, m.points[0].y)
                    for (i in 1 until m.points.size) lineTo(m.points[i].x, m.points[i].y)
                }
                canvas.drawPath(path, paint)
            }
        }
        is StampMark -> {
            paint.style = Paint.Style.FILL
            paint.textSize = m.size
            paint.textAlign = Paint.Align.CENTER
            canvas.drawText(m.emoji, m.x, m.y + m.size / 3, paint)
        }
    }
}

private fun saveDrawing(context: Context, size: IntSize, marks: List<Mark>): Boolean = runCatching {
    val bitmap = Bitmap.createBitmap(size.width, size.height, Bitmap.Config.ARGB_8888)
    val canvas = android.graphics.Canvas(bitmap)
    canvas.drawColor(PAPER)
    renderMarks(canvas, marks)
    val values = ContentValues().apply {
        put(MediaStore.Images.Media.DISPLAY_NAME, "oekaki_${System.currentTimeMillis()}.png")
        put(MediaStore.Images.Media.MIME_TYPE, "image/png")
        put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/KidsHome")
    }
    val resolver = context.contentResolver
    val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values) ?: return false
    resolver.openOutputStream(uri)?.use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) } ?: return false
    true
}.getOrDefault(false)
