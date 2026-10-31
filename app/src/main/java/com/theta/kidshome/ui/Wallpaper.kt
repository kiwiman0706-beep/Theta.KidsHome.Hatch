package com.theta.kidshome.ui

import android.app.WallpaperManager
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Shader
import android.graphics.Typeface
import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.theta.kidshome.data.AppConfig
import com.theta.kidshome.data.Store
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.random.Random

/**
 * かべがみ・ロック画面 を ホーム の テーマ に そろえる。
 * 方眼 の 背景・まる・テーマ の かざり・マスコット、ロック画面 には 名前 も（車 で どっち の タブレット か わかる）。
 */
object ThemeWallpaper {
    fun make(theme: KidTheme, name: String, lock: Boolean, size: Int): Bitmap {
        val b = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val c = Canvas(b)
        val s = size / 1000f
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        p.shader = LinearGradient(0f, 0f, 0f, size.toFloat(), theme.bgTop.toArgb(), theme.bgBottom.toArgb(), Shader.TileMode.CLAMP)
        c.drawRect(0f, 0f, size.toFloat(), size.toFloat(), p)
        p.shader = null
        // 方眼
        p.color = theme.grid.toArgb(); p.strokeWidth = 3 * s
        var x = 0f
        while (x < size) { c.drawLine(x, 0f, x, size.toFloat(), p); c.drawLine(0f, x, size.toFloat(), x, p); x += 50 * s }
        // まる と かざり（いつも おなじ 並び）
        val rnd = Random(theme.id.hashCode())
        repeat(14) { i ->
            p.color = theme.bubbles[i % theme.bubbles.size].toArgb()
            c.drawCircle(rnd.nextFloat() * size, rnd.nextFloat() * size, (40 + rnd.nextFloat() * 90) * s, p)
        }
        val emoji = Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER }
        repeat(26) { i ->
            emoji.textSize = (44 + rnd.nextFloat() * 40) * s
            val cx = rnd.nextFloat() * size; val cy = rnd.nextFloat() * size
            // まんなか（時計・名前・マスコット）は あける
            if (cx in 250 * s..750 * s && cy in 150 * s..850 * s) return@repeat
            c.drawText(theme.decorations[i % theme.decorations.size], cx, cy, emoji)
        }
        emoji.textSize = 260 * s
        c.drawText(theme.mascot, size / 2f, if (lock) 720 * s else 600 * s, emoji)
        if (lock && name.isNotBlank()) {
            val t = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                textAlign = Paint.Align.CENTER; textSize = 84 * s; typeface = Typeface.DEFAULT_BOLD; color = theme.text.toArgb()
            }
            val o = Paint(t).apply { style = Paint.Style.STROKE; strokeWidth = 14 * s; color = android.graphics.Color.WHITE }
            c.drawText(name, size / 2f, 860 * s, o)
            c.drawText(name, size / 2f, 860 * s, t)
        }
        return b
    }

    /** どちら向き でも 切れない よう、長い ほう の 辺 の 正方形 */
    private fun side(context: Context): Int = context.resources.displayMetrics.let { maxOf(it.widthPixels, it.heightPixels) }.coerceIn(1024, 2560)

    fun apply(context: Context, theme: KidTheme, name: String) {
        val wm = WallpaperManager.getInstance(context)
        val n = side(context)
        wm.setBitmap(make(theme, name, lock = false, size = n), null, true, WallpaperManager.FLAG_SYSTEM)
        wm.setBitmap(make(theme, name, lock = true, size = n), null, true, WallpaperManager.FLAG_LOCK)
    }
}

/** おやメニュー：この タブレット は だれ の？ と、かべがみ・ロック画面 */
@Composable
fun ThisTabletSection(config: AppConfig) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val profile = config.currentProfile
    val theme = themeById(profile.themeId)
    var busy by remember { mutableStateOf(false) }
    Section("📱 この タブレット") {
        if (config.profiles.size > 1) {
            Text("だれ の タブレット？（1 人 1 台 なので ホーム には きりかえ を 出しません）", fontSize = 14.sp)
            Row {
                config.profiles.forEach { p ->
                    FilterChip(p.id == config.currentProfileId, { Store.update { it.copy(currentProfileId = p.id) } },
                        label = { Text("${themeById(p.themeId).mascot} ${p.name}") }, modifier = Modifier.padding(end = 8.dp))
                }
            }
            Spacer(Modifier.height(10.dp))
        }
        Text("かべがみ と ロック画面 を「${theme.name}」の せかい に そろえます（ロック画面 には ${profile.name} の なまえ）。", fontSize = 14.sp)
        val preview by produceState<ImageBitmap?>(null, theme.id, profile.name) {
            value = withContext(Dispatchers.Default) { ThemeWallpaper.make(theme, profile.name, lock = true, size = 360).asImageBitmap() }
        }
        Row(Modifier.padding(top = 8.dp)) {
            preview?.let { Image(it, null, Modifier.size(120.dp).clip(RoundedCornerShape(12.dp))) }
            Button(
                enabled = !busy, modifier = Modifier.padding(start = 12.dp),
                onClick = {
                    busy = true
                    scope.launch {
                        val r = withContext(Dispatchers.IO) { runCatching { ThemeWallpaper.apply(context, theme, profile.name) } }
                        busy = false
                        Toast.makeText(context, r.fold({ "🖼 かべがみ と ロック画面 を かえました" }, { "かえられませんでした（${it.message}）" }), Toast.LENGTH_LONG).show()
                    }
                },
            ) { Text(if (busy) "かえています…" else "🖼 かべがみ・ロック画面 を そろえる") }
        }
    }
}
