package com.theta.kidshome.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/** キャラクターごとの見た目 */
data class KidTheme(
    val id: String,
    val name: String,
    val mascot: String,
    val bgTop: Color,
    val bgBottom: Color,
    val accent: Color,
    val text: Color,
    val tileBg: Color,
    val bubbles: List<Color>,
    val decorations: List<String>,
    /** accent の上に乗せる文字色 */
    val onAccent: Color = Color.White,
)

val KidThemes = listOf(
    KidTheme(
        id = "usagi", name = "うさぎ", mascot = "🐰",
        bgTop = Color(0xFFFFD6E4), bgBottom = Color(0xFFFFF4F8),
        accent = Color(0xFFFF7FA8), text = Color(0xFF7A3E55), tileBg = Color(0xF2FFFFFF),
        bubbles = listOf(Color(0xFFFFE0EA), Color(0xFFFFF0C9), Color(0xFFE3F4FF), Color(0xFFEDE4FF)),
        decorations = listOf("🌸", "💗", "🍓", "✨", "🎀"),
    ),
    KidTheme(
        id = "neko", name = "ねこ", mascot = "🐱",
        bgTop = Color(0xFFE4DAFF), bgBottom = Color(0xFFF8F4FF),
        accent = Color(0xFF9B7BFF), text = Color(0xFF4B3A7A), tileBg = Color(0xF2FFFFFF),
        bubbles = listOf(Color(0xFFEDE4FF), Color(0xFFFFE0EA), Color(0xFFDFF7EE), Color(0xFFFFF0C9)),
        decorations = listOf("🐾", "🎀", "⭐", "💜", "🐟"),
    ),
    KidTheme(
        id = "kuma", name = "くま", mascot = "🧸",
        bgTop = Color(0xFFFFE6C7), bgBottom = Color(0xFFFFF8EE),
        accent = Color(0xFFD9925B), text = Color(0xFF6B4426), tileBg = Color(0xF2FFFFFF),
        bubbles = listOf(Color(0xFFFFEBD2), Color(0xFFFFF5C2), Color(0xFFE6F3DA), Color(0xFFFFE0EA)),
        decorations = listOf("🍯", "🍪", "🌼", "🤎", "🍎"),
    ),
    KidTheme(
        id = "dino", name = "きょうりゅう", mascot = "🦕",
        bgTop = Color(0xFFD3F2CC), bgBottom = Color(0xFFF4FFF0),
        accent = Color(0xFF5DBB63), text = Color(0xFF2F5E33), tileBg = Color(0xF2FFFFFF),
        bubbles = listOf(Color(0xFFDDF5D8), Color(0xFFFFF0C9), Color(0xFFD9F0FF), Color(0xFFFFE3D3)),
        decorations = listOf("🌿", "🥚", "🌴", "🦖", "🌋"),
    ),
    KidTheme(
        id = "unicorn", name = "ユニコーン", mascot = "🦄",
        bgTop = Color(0xFFFFDDF2), bgBottom = Color(0xFFDDEEFF),
        accent = Color(0xFFC77DFF), text = Color(0xFF5A3D7A), tileBg = Color(0xF2FFFFFF),
        bubbles = listOf(Color(0xFFFFE1F4), Color(0xFFE1F0FF), Color(0xFFFFF5CC), Color(0xFFE5FFE9)),
        decorations = listOf("🌈", "⭐", "☁️", "💫", "🦋"),
    ),
    KidTheme(
        id = "umi", name = "うみ", mascot = "🐬",
        bgTop = Color(0xFFC9EDFF), bgBottom = Color(0xFFEFFBFF),
        accent = Color(0xFF3BA7E0), text = Color(0xFF1F4E6B), tileBg = Color(0xF2FFFFFF),
        bubbles = listOf(Color(0xFFD4F1FF), Color(0xFFFFF0C9), Color(0xFFDDF7F0), Color(0xFFFFE0EA)),
        decorations = listOf("🐠", "🫧", "🐚", "🌊", "🐳"),
    ),
    KidTheme(
        id = "hoshi", name = "おほしさま", mascot = "🌙",
        bgTop = Color(0xFF2B2F6B), bgBottom = Color(0xFF5A4A9A),
        accent = Color(0xFFFFD95A), text = Color(0xFFFFFFFF), tileBg = Color(0x33FFFFFF),
        bubbles = listOf(Color(0x55FFFFFF), Color(0x55FFD95A), Color(0x55B8A8FF), Color(0x55FFB8D9)),
        decorations = listOf("⭐", "✨", "🌟", "🪐", "☁️"),
        onAccent = Color(0xFF2B2F6B),
    ),
)

fun themeById(id: String) = KidThemes.firstOrNull { it.id == id } ?: KidThemes.first()

/** おうちのひとメニュー用の落ち着いた配色 */
@Composable
fun ParentTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = lightColorScheme(
            primary = Color(0xFFE0608A),
            secondary = Color(0xFF8A6FE0),
            primaryContainer = Color(0xFFFFD9E4),
            secondaryContainer = Color(0xFFE9E0FF),
            background = Color(0xFFFFFBFC),
            surface = Color(0xFFFFFBFC),
        ),
        content = content,
    )
}
