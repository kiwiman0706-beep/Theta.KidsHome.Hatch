package com.theta.kidshome.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val WindowShape = RoundedCornerShape(18.dp)
private val dotColors = listOf(Color(0xFFFFE29A), Color(0xFFB8F0A8), Color(0xFFFFB3CF))

/** シールのような、ずらした影 */
fun Modifier.stickerShadow(color: Color, offset: Dp = 5.dp, radius: Dp = 18.dp) = drawBehind {
    val o = offset.toPx()
    drawRoundRect(color, topLeft = Offset(o, o), size = size, cornerRadius = CornerRadius(radius.toPx()))
}

/**
 * レトロなパソコンのウィンドウ。タイトルバーに小さな丸が3つ。
 * onClick を渡すと、押したときに少し沈むボタンになる。
 */
@Composable
fun RetroWindow(
    theme: KidTheme,
    modifier: Modifier = Modifier,
    title: String? = null,
    titleColor: Color = theme.titleBars.first(),
    body: Color = Color(0xFFFFFDFE),
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    // テレビのリモコン（十字キー）で選んでいるとき
    val focused by interaction.collectIsFocusedAsState()
    val scale by animateFloatAsState(if (pressed) 0.94f else if (focused) 1.05f else 1f, label = "press")
    Column(
        modifier
            .scale(scale)
            .stickerShadow(theme.outline.copy(alpha = 0.85f), if (pressed) 2.dp else 5.dp)
            .clip(WindowShape)
            .background(body)
            .border(if (focused) 6.dp else 3.dp, if (focused) theme.accent else theme.outline, WindowShape)
            .then(
                if (onClick != null) Modifier.clickable(interactionSource = interaction, indication = null, onClick = onClick)
                else Modifier
            ),
    ) {
        if (title != null) {
            Row(
                Modifier.fillMaxWidth().background(titleColor)
                    .drawBehind {
                        val w = 3.dp.toPx()
                        drawLine(theme.outline, Offset(0f, size.height - w / 2), Offset(size.width, size.height - w / 2), w)
                    }
                    .padding(start = 14.dp, end = 10.dp, top = 6.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    title, fontFamily = DotFont, fontSize = 15.sp, color = theme.outline,
                    maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f),
                )
                dotColors.forEach { c ->
                    Box(
                        Modifier.padding(start = 5.dp).size(11.dp).clip(CircleShape)
                            .background(c).border(2.dp, theme.outline, CircleShape)
                    )
                }
            }
        }
        content()
    }
}

/** ドット文字のボタン */
@Composable
fun RetroButton(
    text: String,
    theme: KidTheme,
    color: Color = theme.titleBars.first(),
    fontSize: Int = 20,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val focused by interaction.collectIsFocusedAsState()
    val shape = RoundedCornerShape(12.dp)
    Box(
        modifier
            .offset(x = if (pressed) 3.dp else 0.dp, y = if (pressed) 3.dp else 0.dp)
            .stickerShadow(theme.outline, if (pressed) 0.dp else 3.dp, 12.dp)
            .clip(shape)
            .background(color)
            .border(if (focused) 5.dp else 3.dp, if (focused) theme.accent else theme.outline, shape)
            .clickable(interactionSource = interaction, indication = null, onClick = onClick)
            .padding(horizontal = 22.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, fontFamily = DotFont, fontSize = fontSize.sp, color = theme.outline)
    }
}

/** 正方形のアイコンボタン（えもじ） */
@Composable
fun RetroIconButton(
    emoji: String,
    theme: KidTheme,
    selected: Boolean = false,
    size: Dp = 56.dp,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(14.dp)
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    Box(
        Modifier.padding(5.dp)
            .offset(x = if (selected) 3.dp else 0.dp, y = if (selected) 3.dp else 0.dp)
            .stickerShadow(theme.outline, if (selected) 0.dp else 3.dp, 14.dp)
            .size(size).clip(shape)
            .background(if (selected) theme.titleBars.first() else Color.White)
            .border(if (focused) 5.dp else 3.dp, if (focused) theme.accent else theme.outline, shape)
            .clickable(interactionSource = interaction, indication = null, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Text(emoji, fontSize = (size.value * 0.48f).sp) }
}

/** 方眼紙のグラデーション背景 */
@Composable
fun RetroBackground(theme: KidTheme, modifier: Modifier = Modifier) {
    Canvas(modifier.fillMaxSize().background(Brush.verticalGradient(listOf(theme.bgTop, theme.bgBottom)))) {
        val step = 36.dp.toPx()
        val w = 1.5.dp.toPx()
        var x = step
        while (x < size.width) { drawLine(theme.grid, Offset(x, 0f), Offset(x, size.height), w); x += step }
        var y = step
        while (y < size.height) { drawLine(theme.grid, Offset(0f, y), Offset(size.width, y), w); y += step }
    }
}

/** ゲームのXPバーのような、すすみぐあいの棒 */
@Composable
fun RetroProgress(fraction: Float, theme: KidTheme, fill: Color, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(50)
    Box(
        modifier.height(22.dp).clip(shape).background(Color.White).border(3.dp, theme.outline, shape).padding(4.dp)
    ) {
        Box(Modifier.fillMaxWidth(fraction.coerceIn(0.03f, 1f)).height(14.dp).clip(shape).background(fill))
    }
}

/** ウィンドウの中のちいさな見出し（ドット文字） */
@Composable
fun DotText(text: String, theme: KidTheme, size: Int = 18, color: Color = theme.outline, modifier: Modifier = Modifier) {
    Text(text, fontFamily = DotFont, fontSize = size.sp, color = color, modifier = modifier, lineHeight = (size * 1.3).sp)
}

@Composable
fun VSpace(h: Int) = Spacer(Modifier.height(h.dp))

@Composable
fun HSpace(w: Int) = Spacer(Modifier.width(w.dp))
