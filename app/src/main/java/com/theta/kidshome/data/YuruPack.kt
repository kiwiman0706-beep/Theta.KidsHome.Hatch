package com.theta.kidshome.data

import android.content.Context
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.zip.ZipInputStream

/**
 * 🧸 ゆるキャラ の 絵（非公開 リポジトリ の Release「yuru-pack.zip」）。
 * 県・市 の 著作物 なので 公開 リポジトリ・APK には 入れない。ファイル は 都道府県 番号（43.webp など）。
 * タブレット で 1 たい ずつ 足した 絵（filesDir/yuru）が あれば そちら を さき に。
 */
object YuruPack {
    const val ASSET = "yuru-pack.zip"
    private const val MAX_BYTES = 300L * 1024 * 1024

    /** 都道府県 番号 → ファイル名 */
    var files by mutableStateOf<Map<Int, String>>(emptyMap())
        private set
    var status by mutableStateOf<String?>(null)
    var busy by mutableStateOf(false)
    var version by mutableIntStateOf(0)
        private set
    private val scope = MainScope()

    fun dir(c: Context) = File(c.filesDir, "packs/yuru")
    private fun prefs(c: Context) = c.getSharedPreferences("yurupack", Context.MODE_PRIVATE)
    val installed get() = files.isNotEmpty()

    fun parse(json: String): Map<Int, String> {
        val o = JSONObject(json).getJSONObject("files")
        return o.keys().asSequence().mapNotNull { k -> k.toIntOrNull()?.let { it to o.getString(k) } }.toMap()
    }

    fun load(c: Context) {
        val f = File(dir(c), "yuru.json")
        files = if (f.exists()) runCatching { parse(f.readText()) }.getOrDefault(emptyMap()) else emptyMap()
        version++
    }

    private fun own(c: Context, pref: Int) = File(c.filesDir, "yuru/$pref.png")

    /** この 県 の 絵（じぶん で 足した もの → パック） */
    fun image(c: Context, pref: Int): File? = own(c, pref).takeIf { it.exists() } ?: files[pref]?.let { File(dir(c), it) }?.takeIf { it.exists() }

    /** ギャラリー の 画像 を この 県 の 絵 に（ながい 辺 600） */
    fun setOwn(c: Context, pref: Int, src: android.graphics.Bitmap) {
        val s = minOf(1f, 600f / maxOf(src.width, src.height))
        val b = if (s < 1f) android.graphics.Bitmap.createScaledBitmap(src, (src.width * s).toInt(), (src.height * s).toInt(), true) else src
        own(c, pref).apply { parentFile?.mkdirs() }.outputStream().use { b.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
        version++
    }
    fun clearOwn(c: Context, pref: Int) { own(c, pref).delete(); version++ }

    fun installedTag(c: Context) = prefs(c).getString("tag", "").orEmpty()

    fun update(c: Context) {
        val app = c.applicationContext
        val repo = GuidePack.repo(app); val token = GuidePack.token(app)
        if (busy) return
        if (repo.isBlank() || token.isBlank()) { status = "非公開 リポジトリ の トークン を 先 に 入れて ください（ガイド役 と おなじ）"; return }
        busy = true; status = "しらべて います…"
        scope.launch {
            val r = runCatching { withContext(Dispatchers.IO) { fetch(app, repo, token) } }
            busy = false
            status = r.fold({ it }, { "とれません でした（${it.message}）" })
        }
    }

    private fun api(url: String, token: String, accept: String) = (URL(url).openConnection() as HttpURLConnection).apply {
        connectTimeout = 15_000; readTimeout = 60_000; instanceFollowRedirects = false
        setRequestProperty("Authorization", "Bearer $token"); setRequestProperty("Accept", accept)
        setRequestProperty("X-GitHub-Api-Version", "2022-11-28"); setRequestProperty("User-Agent", "KidsHome")
    }

    private fun fetch(c: Context, repo: String, token: String): String {
        val (tag, asset) = PackRelease.find(repo, token, ASSET)
        if (tag == installedTag(c) && installed) return "さいしん です（$tag）"
        val a = api(asset, token, "application/octet-stream")
        val conns = mutableListOf(a)
        val stream: InputStream = when (a.responseCode) {
            in 300..399 -> (URL(a.getHeaderField("Location") ?: error("ダウンロード先 が ない")).openConnection() as HttpURLConnection)
                .apply { connectTimeout = 15_000; readTimeout = 300_000 }.also { conns += it }.let { if (it.responseCode != 200) error("HTTP ${it.responseCode}"); it.inputStream }
            200 -> a.inputStream
            else -> error("HTTP ${a.responseCode}")
        }
        try { stream.use { install(c, it) } } finally { conns.forEach { it.disconnect() } }
        prefs(c).edit().putString("tag", tag).apply()
        return "ゆるキャラ の 絵 を いれました（$tag・${files.size} たい）"
    }

    fun importZip(c: Context, uri: Uri) {
        val app = c.applicationContext
        busy = true; status = "よみこんで います…"
        scope.launch {
            val r = runCatching { withContext(Dispatchers.IO) { app.contentResolver.openInputStream(uri)?.use { install(app, it) } ?: error("ひらけません") } }
            busy = false
            status = r.fold({ "ゆるキャラ の 絵 を いれました（zip・${files.size} たい）" }, { "よみこめません でした（${it.message}）" })
            if (r.isSuccess) prefs(app).edit().putString("tag", "zip").apply()
        }
    }

    private fun install(c: Context, input: InputStream) {
        val tmp = File(c.filesDir, "packs/.yuru-new").apply { deleteRecursively(); mkdirs() }
        var total = 0L
        ZipInputStream(input).use { z ->
            while (true) {
                val e = z.nextEntry ?: break
                if (e.isDirectory) continue
                val out = File(tmp, e.name.replace('\\', '/').removePrefix("./"))
                if (!out.canonicalPath.startsWith(tmp.canonicalPath + File.separator)) error("あやしい ファイル名")
                out.parentFile?.mkdirs()
                out.outputStream().use { o ->
                    val buf = ByteArray(64 * 1024)
                    while (true) { val n = z.read(buf); if (n < 0) break; total += n; if (total > MAX_BYTES) error("パック が 大きすぎます"); o.write(buf, 0, n) }
                }
            }
        }
        val root = listOf(tmp, File(tmp, "yuru")).firstOrNull { File(it, "yuru.json").exists() } ?: error("yuru.json が ありません")
        val s = parse(File(root, "yuru.json").readText())
        val target = dir(c)
        target.deleteRecursively(); target.parentFile?.mkdirs()
        if (!root.renameTo(target)) error("いれかえ できません でした")
        tmp.deleteRecursively()
        scope.launch { files = s; version++ }
    }

    fun remove(c: Context) {
        dir(c).deleteRecursively(); prefs(c).edit().remove("tag").apply(); files = emptyMap(); version++
    }
}
