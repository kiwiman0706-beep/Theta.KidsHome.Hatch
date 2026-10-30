package com.theta.kidshome.update

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.widget.Toast
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

data class Release(val tag: String, val apkName: String, val apkUrl: String, val sumsUrl: String?) {
    /** "v0.2.0-alpha" → "0.2.0-alpha" */
    val version: String get() = tag.removePrefix("v")
}

/** 数字の部分だけで比べる。"0.3.0-beta" > "0.2.0"、"0.2.0-alpha" と "0.2.0" は同じ。 */
fun isNewer(candidate: String, current: String): Boolean {
    fun parts(v: String) = v.substringBefore('-').split('.').map { it.toIntOrNull() ?: 0 }
    val a = parts(candidate)
    val b = parts(current)
    for (i in 0 until maxOf(a.size, b.size)) {
        val x = a.getOrElse(i) { 0 }
        val y = b.getOrElse(i) { 0 }
        if (x != y) return x > y
    }
    return false
}

data class UpdateState(
    val latest: Release? = null,
    val newer: Boolean = false,
    val checking: Boolean = false,
    /** 取得中なら 0〜1 */
    val progress: Float? = null,
    val error: String? = null,
    val checkedAt: Long = 0L,
)

/**
 * 自動更新（Theta Home Hatch と同じしくみ）。
 * GitHub Releases の新しい版を確かめ → APK を取得 → SHA-256 を照合 → Android のインストール画面へ。
 */
object SelfUpdate {
    const val OWNER = "kiwiman0706-beep"
    const val REPO = "Theta.KidsHome.Hatch"
    const val RELEASES_URL = "https://github.com/$OWNER/$REPO/releases"
    private const val ASSET_PREFIX = "KidsHome-"

    var state by mutableStateOf(UpdateState())
        private set

    private val scope = MainScope()

    fun currentVersion(context: Context): String =
        runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }.getOrNull() ?: "0"

    /** 新しい版があるか確かめる。force でなければ 1 時間に 1 回まで。 */
    fun check(context: Context, force: Boolean = false) {
        if (state.checking) return
        val prefs = context.getSharedPreferences("update", Context.MODE_PRIVATE)
        val last = prefs.getLong("lastCheck", 0L)
        if (!force && System.currentTimeMillis() - last < 3600_000) return
        val current = currentVersion(context)
        state = state.copy(checking = true, error = null)
        scope.launch {
            val r = runCatching { latest() }
            val now = System.currentTimeMillis()
            prefs.edit().putLong("lastCheck", now).apply()
            val rel = r.getOrNull()
            state = UpdateState(
                latest = rel,
                newer = rel != null && isNewer(rel.version, current),
                error = r.exceptionOrNull()?.message,
                checkedAt = now,
            )
        }
    }

    /** 取得して照合し、インストールへ。 */
    fun downloadAndInstall(context: Context) {
        val rel = state.latest ?: return
        if (state.progress != null) return
        val app = context.applicationContext
        permissionIntent(app)?.let {
            Toast.makeText(app, "「この提供元のアプリを許可」をオンにしてから、もう一度押してください", Toast.LENGTH_LONG).show()
            app.startActivity(it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            return
        }
        state = state.copy(progress = 0f, error = null)
        scope.launch {
            val result = runCatching { download(app, rel) { p -> scope.launch { state = state.copy(progress = p) } } }
            state = state.copy(progress = null, error = result.exceptionOrNull()?.message)
            result.getOrNull()?.let { runCatching { install(app, it) }.onFailure { e -> state = state.copy(error = e.message) } }
        }
    }

    private suspend fun latest(): Release? = latestOf(REPO) { it.startsWith(ASSET_PREFIX) }

    /** そのリポジトリの Releases で、いちばん新しい APK（下書きは除く） */
    suspend fun latestOf(repo: String, isApk: (String) -> Boolean = { true }): Release? = withContext(Dispatchers.IO) {
        val list = JSONArray(get("https://api.github.com/repos/$OWNER/$repo/releases?per_page=10") ?: return@withContext null)
        for (i in 0 until list.length()) {
            val r = list.getJSONObject(i)
            if (r.optBoolean("draft")) continue
            val assets = r.optJSONArray("assets") ?: continue
            var apk: Pair<String, String>? = null
            var sums: String? = null
            for (j in 0 until assets.length()) {
                val a = assets.getJSONObject(j)
                val name = a.optString("name")
                if (apk == null && name.endsWith(".apk") && isApk(name)) apk = name to a.optString("browser_download_url")
                if (name.contains("SHA256", ignoreCase = true)) sums = a.optString("browser_download_url")
            }
            if (apk != null) return@withContext Release(r.optString("tag_name"), apk.first, apk.second, sums)
        }
        null
    }

    suspend fun download(context: Context, release: Release, onProgress: (Float) -> Unit): File =
        withContext(Dispatchers.IO) {
            val dir = File(context.cacheDir, "update").apply { mkdirs() }
            dir.listFiles()?.forEach { it.delete() }
            val out = File(dir, release.apkName.replace('/', '_'))
            val conn = open(release.apkUrl)
            val total = conn.contentLengthLong
            val digest = MessageDigest.getInstance("SHA-256")
            conn.inputStream.use { input ->
                out.outputStream().use { o ->
                    val buf = ByteArray(64 * 1024)
                    var done = 0L
                    var shown = 0
                    while (true) {
                        val n = input.read(buf)
                        if (n < 0) break
                        o.write(buf, 0, n)
                        digest.update(buf, 0, n)
                        done += n
                        val pct = if (total > 0) (done * 100 / total).toInt() else 0
                        if (pct > shown) { shown = pct; onProgress(pct / 100f) }
                    }
                }
            }
            val hash = digest.digest().joinToString("") { "%02x".format(it) }
            val expected = release.sumsUrl?.let { get(it) }?.lineSequence()
                ?.map { it.trim().split(Regex("\\s+")) }
                ?.firstOrNull { it.size >= 2 && it.last().removePrefix("*").endsWith(release.apkName) }
                ?.first()?.lowercase()
            if (expected != null && expected != hash) {
                out.delete()
                throw IOException("SHA-256 が一致しません")
            }
            out
        }

    fun permissionIntent(context: Context): Intent? =
        if (context.packageManager.canRequestPackageInstalls()) null
        else Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${context.packageName}"))

    fun install(context: Context, apk: File) {
        val installer = context.packageManager.packageInstaller
        val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL)
        // 自分自身の更新なら、Android 12 以降は確認を省ける場合がある
        if (Build.VERSION.SDK_INT >= 31) params.setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_NOT_REQUIRED)
        val id = installer.createSession(params)
        installer.openSession(id).use { session ->
            session.openWrite("base.apk", 0, apk.length()).use { o ->
                apk.inputStream().use { it.copyTo(o) }
                session.fsync(o)
            }
            val pi = PendingIntent.getBroadcast(
                context, id,
                Intent(context, InstallResultReceiver::class.java),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE,
            )
            session.commit(pi.intentSender)
        }
    }

    private fun open(url: String): HttpURLConnection {
        val conn = URL(url).openConnection() as HttpURLConnection
        conn.connectTimeout = 15_000
        conn.readTimeout = 30_000
        conn.setRequestProperty("Accept", "application/vnd.github+json")
        conn.setRequestProperty("User-Agent", "KidsHome")
        conn.instanceFollowRedirects = true
        if (conn.responseCode !in 200..299) throw IOException("HTTP ${conn.responseCode}")
        return conn
    }

    private fun get(url: String): String? = runCatching { open(url).inputStream.bufferedReader().use { it.readText() } }.getOrNull()
}

/** PackageInstaller の結果。確認が必要なときは確認画面を開く。 */
class InstallResultReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)) {
            PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                @Suppress("DEPRECATION")
                val confirm = intent.getParcelableExtra<Intent>(Intent.EXTRA_INTENT) ?: return
                context.startActivity(confirm.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            }
            PackageInstaller.STATUS_SUCCESS -> Toast.makeText(context, "インストールしました", Toast.LENGTH_SHORT).show()
            else -> {
                val msg = intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE) ?: ""
                Toast.makeText(context, "インストールできませんでした $msg", Toast.LENGTH_LONG).show()
            }
        }
    }
}
