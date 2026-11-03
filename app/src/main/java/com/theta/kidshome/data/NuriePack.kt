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
 * 🖍 ぬりえ パック（購入 した ぬりえ帳：非公開 リポジトリ の Release「nurie-pack.zip」）。
 * 本（books）ごと に ページ。公開 リポジトリ・APK には 入れない。
 */
object NuriePack {
    const val ASSET = "nurie-pack.zip"
    private const val MAX_BYTES = 300L * 1024 * 1024

    data class Book(val id: String, val name: String, val pages: List<String>)

    var books by mutableStateOf<List<Book>>(emptyList())
        private set
    var status by mutableStateOf<String?>(null)
    var busy by mutableStateOf(false)
    var version by mutableIntStateOf(0)
        private set
    private val scope = MainScope()

    fun dir(c: Context) = File(c.filesDir, "packs/nurie")
    private fun prefs(c: Context) = c.getSharedPreferences("nuriepack", Context.MODE_PRIVATE)
    val installed get() = books.isNotEmpty()

    fun parse(json: String): List<Book> {
        val a = JSONObject(json).getJSONArray("books")
        return (0 until a.length()).map { a.getJSONObject(it) }.map { o ->
            val pages = o.getJSONArray("pages").let { pa -> (0 until pa.length()).map { pa.getJSONObject(it).getString("file") } }
            Book(o.getString("id"), o.getString("name"), pages)
        }
    }

    fun load(c: Context) {
        val f = File(dir(c), "nurie.json")
        books = if (f.exists()) runCatching { parse(f.readText()) }.getOrDefault(emptyList()) else emptyList()
        version++
    }

    fun file(c: Context, book: String, page: String) = File(File(dir(c), book), page)

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
        return "ぬりえ を いれました（$tag・${books.size} さつ）"
    }

    fun importZip(c: Context, uri: Uri) {
        val app = c.applicationContext
        busy = true; status = "よみこんで います…"
        scope.launch {
            val r = runCatching { withContext(Dispatchers.IO) { app.contentResolver.openInputStream(uri)?.use { install(app, it) } ?: error("ひらけません") } }
            busy = false
            status = r.fold({ "ぬりえ を いれました（zip・${books.size} さつ）" }, { "よみこめません でした（${it.message}）" })
            if (r.isSuccess) prefs(app).edit().putString("tag", "zip").apply()
        }
    }

    private fun install(c: Context, input: InputStream) {
        val tmp = File(c.filesDir, "packs/.nurie-new").apply { deleteRecursively(); mkdirs() }
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
        val root = listOf(tmp, File(tmp, "nurie")).firstOrNull { File(it, "nurie.json").exists() } ?: error("nurie.json が ありません")
        val s = parse(File(root, "nurie.json").readText())
        val target = dir(c)
        target.deleteRecursively(); target.parentFile?.mkdirs()
        if (!root.renameTo(target)) error("いれかえ できません でした")
        tmp.deleteRecursively()
        scope.launch { books = s; version++ }
    }

    fun remove(c: Context) {
        dir(c).deleteRecursively(); prefs(c).edit().remove("tag").apply(); books = emptyList(); version++
    }
}
