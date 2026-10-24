package com.theta.kidshome.data

import android.content.Context
import android.content.pm.ApplicationInfo
import java.util.UUID

/**
 * アプリの仕分け先（Theta Home Hatch の仕分けを、子ども向けにしたもの）。
 * visibleTo に入っている子のホームに、このカテゴリーのタブが出る。だれにも見せないカテゴリーは「かくす」と同じ。
 */
data class AppCategory(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val emoji: String,
    val visibleTo: Set<String> = emptySet(),
    /** true なら、どうがロック中は押せない */
    val isVideo: Boolean = false,
)

object Categories {
    /** 「かくす」：どの子のホームにも出さない（仕分けていないアプリも出さない） */
    const val HIDDEN = "hidden"

    data class Preset(val name: String, val emoji: String, val isVideo: Boolean = false, val shown: Boolean = true)

    val presets = listOf(
        Preset("べんきょう", "📚"),
        Preset("あそび", "🎮"),
        Preset("どうが", "🎬", isVideo = true),
        Preset("おんがく", "🎵"),
        Preset("えほん", "📖"),
        Preset("つくる", "🎨"),
        Preset("しゃしん", "📷"),
        Preset("おうちのひと", "🔧", shown = false),
    )

    /** はじめから選んでおく候補 */
    val defaults = listOf("べんきょう", "あそび", "どうが", "おんがく", "おうちのひと")

    fun fromPreset(p: Preset, profileIds: Set<String>) =
        AppCategory(name = p.name, emoji = p.emoji, visibleTo = if (p.shown) profileIds else emptySet(), isVideo = p.isVideo)

    /** アプリの申告や名前からの、おすすめのカテゴリー名。分からなければ null */
    fun suggest(context: Context, packageName: String): String? {
        val pkg = packageName.lowercase()
        if (packageName in Launcher.videoPackages) return "どうが"
        if (pkg.contains("settings") || pkg.contains("installer") || pkg.contains("vending") || pkg.contains("chrome") ||
            pkg.contains("browser") || pkg.contains("filemanager") || pkg.contains("files") || pkg.contains("mail") ||
            pkg.contains("gm") && pkg.startsWith("com.google.android.gm")
        ) return "おうちのひと"
        if (pkg.contains("camera") || pkg.contains("photos") || pkg.contains("gallery")) return "しゃしん"
        if (pkg.contains("music") || pkg.contains("spotify")) return "おんがく"
        if (pkg.contains("book") || pkg.contains("ehon") || pkg.contains("kindle")) return "えほん"
        if (pkg.contains("study") || pkg.contains("learn") || pkg.contains("kids") || pkg.contains("benesse") ||
            pkg.contains("shimajiro") || pkg.contains("hiragana") || pkg.contains("kanji") || pkg.contains("math")
        ) return "べんきょう"
        val info = runCatching { context.packageManager.getApplicationInfo(packageName, 0) }.getOrNull() ?: return null
        return when (info.category) {
            ApplicationInfo.CATEGORY_GAME -> "あそび"
            ApplicationInfo.CATEGORY_VIDEO -> "どうが"
            ApplicationInfo.CATEGORY_AUDIO -> "おんがく"
            ApplicationInfo.CATEGORY_IMAGE -> "しゃしん"
            ApplicationInfo.CATEGORY_PRODUCTIVITY, ApplicationInfo.CATEGORY_SOCIAL, ApplicationInfo.CATEGORY_NEWS,
            ApplicationInfo.CATEGORY_MAPS, ApplicationInfo.CATEGORY_ACCESSIBILITY -> "おうちのひと"
            else -> null
        }
    }
}
