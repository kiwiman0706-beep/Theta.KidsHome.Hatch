package com.theta.kidshome.ui

import android.graphics.Paint
import android.graphics.Path
import android.graphics.PathMeasure
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.theta.kidshome.data.Dict
import com.theta.kidshome.data.KanjiInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * もじ れんしゅう の よこ：おてほん（書き順 の アニメ と 番号）・かくすう・よみ・ことば・いみ。
 * いみ は 学年 つき の こくご 辞書 が タブレット に あれば。
 */
@Composable
fun KanjiSide(theme: KidTheme, ch: String, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val loaded by produceState(false) {
        withContext(Dispatchers.IO) { KanjiInfo.load(context) }
        runCatching { Dict.open(context) }
        value = true
    }
    val info = remember(ch, loaded) { if (loaded) KanjiInfo.get(ch) else null }
    val paths = remember(ch, loaded) { if (loaded) KanjiInfo.paths(ch) else emptyList() }
    val meaning by produceState<String?>(null, ch, loaded, Dict.books) {
        value = if (!loaded) null else withContext(Dispatchers.IO) { runCatching { Dict.kanjiMeaning(ch) }.getOrNull() }
    }
    var replay by remember { mutableIntStateOf(0) }
    val anim = remember(ch) { Animatable(0f) }
    LaunchedEffect(ch, paths.size, replay) {
        if (paths.isEmpty()) return@LaunchedEffect
        anim.snapTo(0f)
        anim.animateTo(paths.size.toFloat(), tween(durationMillis = 650 * paths.size, easing = LinearEasing))
    }
    val kanji = ch.codePointAt(0) >= 0x4E00

    Column(modifier.width(250.dp).fillMaxHeight().verticalScroll(rememberScrollState()).padding(end = 14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            DotText("おてほん", theme, 16, theme.text, modifier = Modifier.weight(1f))
            info?.strokes?.let { DotText("$it かく", theme, 16, theme.text) }
        }
        Box(
            Modifier.fillMaxWidth().aspectRatio(1f).clip(RoundedCornerShape(12.dp)).background(Color.White)
                .border(3.dp, theme.outline, RoundedCornerShape(12.dp))
        ) {
            Canvas(Modifier.fillMaxSize()) {
                val dash = PathEffect.dashPathEffect(floatArrayOf(8f, 7f))
                val lc = theme.outline.copy(alpha = 0.25f)
                drawLine(lc, Offset(size.width / 2, 0f), Offset(size.width / 2, size.height), 2f, pathEffect = dash)
                drawLine(lc, Offset(0f, size.height / 2), Offset(size.width, size.height / 2), 2f, pathEffect = dash)
                drawIntoCanvas { cv ->
                    val c = cv.nativeCanvas
                    if (paths.isEmpty()) {
                        val p = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                            color = android.graphics.Color.rgb(40, 40, 50); textSize = size.width * 0.78f
                            textAlign = Paint.Align.CENTER; typeface = android.graphics.Typeface.SERIF
                        }
                        val fm = p.fontMetrics
                        c.drawText(ch, size.width / 2, size.height / 2 - (fm.ascent + fm.descent) / 2, p)
                        return@drawIntoCanvas
                    }
                    val k = size.width / KanjiInfo.BOX
                    c.save(); c.scale(k, k)
                    val line = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                        style = Paint.Style.STROKE; strokeWidth = 4.2f; strokeCap = Paint.Cap.ROUND; strokeJoin = Paint.Join.ROUND
                    }
                    val t = anim.value
                    // うすい ぜんたい
                    line.color = android.graphics.Color.argb(28, 0, 0, 0)
                    paths.forEach { c.drawPath(it, line) }
                    paths.forEachIndexed { i, p ->
                        when {
                            i + 1 <= t -> { line.color = android.graphics.Color.rgb(35, 35, 45); c.drawPath(p, line) }
                            i < t -> {
                                val m = PathMeasure(p, false)
                                val part = Path()
                                m.getSegment(0f, m.length * (t - i), part, true)
                                line.color = theme.accent.toArgb(); c.drawPath(part, line)
                            }
                        }
                    }
                    // 番号（かき はじめ の ところ）
                    val num = Paint(Paint.ANTI_ALIAS_FLAG).apply { textSize = if (paths.size > 12) 6.5f else 8.5f; color = android.graphics.Color.rgb(220, 60, 90); textAlign = Paint.Align.CENTER; isFakeBoldText = true }
                    val pos = FloatArray(2)
                    paths.forEachIndexed { i, p ->
                        if (i >= t) return@forEachIndexed
                        PathMeasure(p, false).getPosTan(0f, pos, null)
                        c.drawText("${i + 1}", (pos[0] - 5f).coerceIn(5f, 104f), (pos[1] - 2f).coerceIn(9f, 107f), num)
                    }
                    c.restore()
                }
            }
        }
        if (paths.isNotEmpty()) RetroButton("▶ かきじゅん", theme, theme.titleBars[3], fontSize = 16, modifier = Modifier.fillMaxWidth()) { replay++ }

        if (kanji && info != null) {
            if (info.on.isNotEmpty()) Reading(theme, "おん", info.on.joinToString("・"))
            if (info.kun.isNotEmpty()) Reading(theme, "くん", info.kun.joinToString("・") { it.replace("(", "（").replace(")", "）") })
            if (info.words.isNotEmpty()) {
                DotText("ことば", theme, 15, theme.outline)
                info.words.forEach { (w, r) ->
                    Row(verticalAlignment = Alignment.Bottom) {
                        Text(w, fontSize = 22.sp, color = theme.outline)
                        Text("（$r）", fontSize = 14.sp, color = theme.outline.copy(alpha = 0.7f), modifier = Modifier.padding(start = 2.dp, bottom = 2.dp))
                    }
                }
            }
        }
        meaning?.let {
            DotText("いみ", theme, 15, theme.outline)
            Text(it, fontSize = 14.sp, color = theme.outline, lineHeight = 20.sp)
        }
    }
}

@Composable
private fun Reading(theme: KidTheme, label: String, text: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.clip(RoundedCornerShape(8.dp)).background(theme.titleBars[1]).padding(horizontal = 8.dp, vertical = 2.dp)) {
            Text(label, fontSize = 13.sp, color = theme.outline)
        }
        Text(text, fontSize = 18.sp, color = theme.outline, modifier = Modifier.padding(start = 8.dp))
    }
}
