package com.theta.kidshome.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import com.theta.kidshome.R

/** ドット文字（DotGothic16, SIL Open Font License） */
val DotFont = FontFamily(Font(R.font.dotgothic16))

/**
 * キャラクターごとの見た目。
 * パステルの方眼紙に、太いフチ線のレトロなウィンドウを並べる。
 */
data class KidTheme(
    val id: String,
    val name: String,
    val mascot: String,
    val bgTop: Color,
    val bgBottom: Color,
    /** 背景の方眼の線 */
    val grid: Color,
    /** ウィンドウのフチ線・ウィンドウの中の文字 */
    val outline: Color,
    /** 背景に直接のせる文字 */
    val text: Color,
    val accent: Color,
    /** ウィンドウのタイトルバーの色（順番に使う） */
    val titleBars: List<Color>,
    /** アイコンの下に敷く丸の色 */
    val bubbles: List<Color>,
    val decorations: List<String>,
    /** マスコットのせりふ */
    val lines: List<String>,
)

private val commonLines = listOf("なにして あそぶ？", "きょうも げんき？", "いっしょに あそぼ！", "えらいね！", "だいすき！")

val KidThemes = listOf(
    KidTheme(
        id = "usagi", name = "うさぎ", mascot = "🐰",
        bgTop = Color(0xFFFFD6E4), bgBottom = Color(0xFFFFF4F8), grid = Color(0x66FFFFFF),
        outline = Color(0xFF7A3E55), text = Color(0xFF7A3E55), accent = Color(0xFFFF7FA8),
        titleBars = listOf(Color(0xFFFFB3CF), Color(0xFFC9F0A8), Color(0xFFB8D8FF), Color(0xFFFFE29A)),
        bubbles = listOf(Color(0xFFFFE0EA), Color(0xFFFFF0C9), Color(0xFFE3F4FF), Color(0xFFEDE4FF)),
        decorations = listOf("🌸", "💗", "🍓", "✨", "🎀"),
        lines = commonLines + listOf("ぴょん ぴょん！", "にんじん たべたい"),
    ),
    KidTheme(
        id = "neko", name = "ねこ", mascot = "🐱",
        bgTop = Color(0xFFE4DAFF), bgBottom = Color(0xFFF8F4FF), grid = Color(0x66FFFFFF),
        outline = Color(0xFF4B3A7A), text = Color(0xFF4B3A7A), accent = Color(0xFF9B7BFF),
        titleBars = listOf(Color(0xFFCBB8FF), Color(0xFFFFB3CF), Color(0xFFB8E8F0), Color(0xFFFFE29A)),
        bubbles = listOf(Color(0xFFEDE4FF), Color(0xFFFFE0EA), Color(0xFFDFF7EE), Color(0xFFFFF0C9)),
        decorations = listOf("🐾", "🎀", "⭐", "💜", "🐟"),
        lines = commonLines + listOf("にゃーん", "ごろごろ…"),
    ),
    KidTheme(
        id = "kuma", name = "くま", mascot = "🧸",
        bgTop = Color(0xFFFFE6C7), bgBottom = Color(0xFFFFF8EE), grid = Color(0x77FFFFFF),
        outline = Color(0xFF6B4426), text = Color(0xFF6B4426), accent = Color(0xFFD9925B),
        titleBars = listOf(Color(0xFFF5C99B), Color(0xFFFFE29A), Color(0xFFC9E8A8), Color(0xFFFFB8B8)),
        bubbles = listOf(Color(0xFFFFEBD2), Color(0xFFFFF5C2), Color(0xFFE6F3DA), Color(0xFFFFE0EA)),
        decorations = listOf("🍯", "🍪", "🌼", "🤎", "🍎"),
        lines = commonLines + listOf("はちみつ だいすき", "ぎゅーっ！"),
    ),
    KidTheme(
        id = "dino", name = "きょうりゅう", mascot = "🦕",
        bgTop = Color(0xFFD3F2CC), bgBottom = Color(0xFFF4FFF0), grid = Color(0x77FFFFFF),
        outline = Color(0xFF2F5E33), text = Color(0xFF2F5E33), accent = Color(0xFF5DBB63),
        titleBars = listOf(Color(0xFFA8E0A0), Color(0xFFFFE29A), Color(0xFFB8D8FF), Color(0xFFFFC4A8)),
        bubbles = listOf(Color(0xFFDDF5D8), Color(0xFFFFF0C9), Color(0xFFD9F0FF), Color(0xFFFFE3D3)),
        decorations = listOf("🌿", "🥚", "🌴", "🦖", "🌋"),
        lines = commonLines + listOf("がおー！", "のっし のっし"),
    ),
    KidTheme(
        id = "unicorn", name = "ユニコーン", mascot = "🦄",
        bgTop = Color(0xFFFFDDF2), bgBottom = Color(0xFFDDEEFF), grid = Color(0x77FFFFFF),
        outline = Color(0xFF5A3D7A), text = Color(0xFF5A3D7A), accent = Color(0xFFC77DFF),
        titleBars = listOf(Color(0xFFE2B8FF), Color(0xFFFFB8DE), Color(0xFFB8E0FF), Color(0xFFFFF0A0)),
        bubbles = listOf(Color(0xFFFFE1F4), Color(0xFFE1F0FF), Color(0xFFFFF5CC), Color(0xFFE5FFE9)),
        decorations = listOf("🌈", "⭐", "☁️", "💫", "🦋"),
        lines = commonLines + listOf("キラキラ〜", "まほう かけちゃう！"),
    ),
    KidTheme(
        id = "umi", name = "うみ", mascot = "🐬",
        bgTop = Color(0xFFC9EDFF), bgBottom = Color(0xFFEFFBFF), grid = Color(0x77FFFFFF),
        outline = Color(0xFF1F4E6B), text = Color(0xFF1F4E6B), accent = Color(0xFF3BA7E0),
        titleBars = listOf(Color(0xFFA8D8F5), Color(0xFFB8F0E0), Color(0xFFFFE29A), Color(0xFFFFC4D8)),
        bubbles = listOf(Color(0xFFD4F1FF), Color(0xFFFFF0C9), Color(0xFFDDF7F0), Color(0xFFFFE0EA)),
        decorations = listOf("🐠", "🫧", "🐚", "🌊", "🐳"),
        lines = commonLines + listOf("きゅいきゅい！", "およぐの だいすき"),
    ),
    KidTheme(
        id = "hoshi", name = "おほしさま", mascot = "🌙",
        bgTop = Color(0xFF2B2F6B), bgBottom = Color(0xFF5A4A9A), grid = Color(0x22FFFFFF),
        outline = Color(0xFF23265A), text = Color(0xFFFFFFFF), accent = Color(0xFFFFD95A),
        titleBars = listOf(Color(0xFFB8A8FF), Color(0xFFFFD95A), Color(0xFFFFB8D9), Color(0xFFA8E0F5)),
        bubbles = listOf(Color(0xFFEDE8FF), Color(0xFFFFF4C4), Color(0xFFFFE4F1), Color(0xFFE0F4FF)),
        decorations = listOf("⭐", "✨", "🌟", "🪐", "☁️"),
        lines = commonLines + listOf("おほしさま きれいだね", "ねむねむ…"),
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
