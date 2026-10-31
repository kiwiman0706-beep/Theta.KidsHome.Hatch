package com.theta.kidshome.data

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.graphics.drawable.toBitmap

data class InstalledApp(val label: String, val packageName: String)

/** URL から判別できる動画サービス */
data class VideoService(val name: String, val packageName: String, val emoji: String)

private val services = listOf(
    Regex("netflix\\.com") to VideoService("Netflix", "com.netflix.mediaclient", "🎬"),
    Regex("primevideo\\.com|amazon\\.(co\\.jp|com)/(gp/video|.*dp/)|amzn\\.(to|asia)") to
        VideoService("Prime Video", "com.amazon.avod.thirdpartyclient", "📺"),
    Regex("youtubekids\\.com") to VideoService("YouTube Kids", "com.google.android.apps.youtube.kids", "▶️"),
    Regex("youtube\\.com|youtu\\.be") to VideoService("YouTube", "com.google.android.youtube", "▶️"),
    Regex("disneyplus\\.com") to VideoService("Disney+", "com.disney.disneyplus", "🏰"),
    Regex("tver\\.jp") to VideoService("TVer", "jp.hamitv.hamiand1", "📡"),
    Regex("unext\\.jp|video\\.unext") to VideoService("U-NEXT", "jp.unext.mediaplayer", "🎞️"),
)

object Launcher {
    /** よく使う動画アプリ。アプリとして追加したときに自動で「どうが」扱いにする */
    val videoPackages = services.map { it.second.packageName }.toSet() + setOf(
        "com.google.android.apps.youtube.kids",
        "jp.happyon.android", // Hulu
        "com.dazn",
        "jp.co.abematv", // ABEMA (旧)
        "tv.abema",
    )

    fun detectService(url: String): VideoService? =
        services.firstOrNull { it.first.containsMatchIn(url) }?.second

    /** 共有テキストから URL だけを取り出す */
    fun extractUrl(text: String): String? =
        Regex("https?://\\S+").find(text)?.value?.trimEnd(')', '」', '。', '"', '\'')

    fun listApps(context: Context): List<InstalledApp> {
        val pm = context.packageManager
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        return pm.queryIntentActivities(intent, 0)
            .map { InstalledApp(it.loadLabel(pm).toString(), it.activityInfo.packageName) }
            .filter { it.packageName != context.packageName }
            .distinctBy { it.packageName }
            .sortedBy { it.label }
    }

    fun appLabel(context: Context, packageName: String): String = runCatching {
        val pm = context.packageManager
        pm.getApplicationLabel(pm.getApplicationInfo(packageName, 0)).toString()
    }.getOrDefault(packageName)

    fun isInstalled(context: Context, packageName: String) =
        context.packageManager.getLaunchIntentForPackage(packageName) != null

    private val iconCache = HashMap<String, ImageBitmap?>()

    fun icon(context: Context, packageName: String): ImageBitmap? = iconCache.getOrPut(packageName) {
        runCatching {
            context.packageManager.getApplicationIcon(packageName).toBitmap(192, 192).asImageBitmap()
        }.getOrNull()
    }

    fun launch(context: Context, tile: Tile) {
        val ok = when (tile.type) {
            TileType.APP -> launchApp(context, tile.packageName ?: return)
            TileType.VIDEO -> openVideo(context, tile.url ?: return, tile.packageName)
            TileType.CALL -> openVideo(context, tile.url ?: return, null)
            TileType.DRAWING, TileType.INSTRUMENT, TileType.EDUPAD, TileType.WORDPRO, TileType.PUZZLE, TileType.TYPING, TileType.MOJI, TileType.KUKU, TileType.MAP, TileType.TASU, TileType.DICT, TileType.SOROBAN, TileType.HISSAN, TileType.JAPAN -> true
        }
        if (!ok) Toast.makeText(context, "ひらけなかったよ…\nおうちのひとに きいてね", Toast.LENGTH_LONG).show()
    }

    /** もとから 入っている アプリ（けせない。とめる だけ） */
    fun isSystem(context: Context, packageName: String): Boolean = runCatching {
        context.packageManager.getApplicationInfo(packageName, 0).flags and android.content.pm.ApplicationInfo.FLAG_SYSTEM != 0
    }.getOrDefault(false)

    /** アンインストール の かくにん を 出す。もとから の アプリ は「アプリ情報」（無効にする）を ひらく */
    fun uninstall(context: Context, packageName: String) {
        val uri = Uri.parse("package:$packageName")
        val i = if (isSystem(context, packageName)) Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS, uri) else Intent(Intent.ACTION_DELETE, uri)
        runCatching { context.startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
            .onFailure { Toast.makeText(context, "ひらけませんでした", Toast.LENGTH_SHORT).show() }
    }

    fun launchPackage(context: Context, packageName: String) {
        if (!launchApp(context, packageName)) Toast.makeText(context, "ひらけなかったよ…", Toast.LENGTH_SHORT).show()
    }

    private fun launchApp(context: Context, packageName: String): Boolean {
        val intent = context.packageManager.getLaunchIntentForPackage(packageName) ?: return false
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return start(context, intent)
    }

    private fun openVideo(context: Context, url: String, packageName: String?): Boolean {
        val uri = Uri.parse(url)
        val base = Intent(Intent.ACTION_VIEW, uri).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        // まず動画アプリで直接開き、だめならOSにおまかせ(ブラウザなど)
        if (packageName != null && start(context, Intent(base).setPackage(packageName))) return true
        return start(context, base)
    }

    private fun start(context: Context, intent: Intent): Boolean = try {
        context.startActivity(intent)
        true
    } catch (e: ActivityNotFoundException) {
        false
    } catch (e: SecurityException) {
        false
    }
}
