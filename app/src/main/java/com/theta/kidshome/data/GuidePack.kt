package com.theta.kidshome.data

import android.content.Context
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.zip.ZipInputStream

/** ガイド役 の きせかえ：スロット（ふく・かみ…）と アイテム */
data class GuideItem(val id: String, val name: String, val emoji: String, val thumbnail: String?)
data class GuideSlot(val id: String, val name: String, val optional: Boolean, val items: List<GuideItem>)
data class GuideManifest(
    val name: String,
    val version: String,
    val greet: List<String>,
    val tap: List<String>,
    val slots: List<GuideSlot>,
)

/**
 * ガイド役 の パック（VRM モデル・きせかえ・せりふ）。APK には 入れない。
 * 非公開リポジトリ の Release（guide-pack.zip）を 読み取り専用トークン で とってくる か、zip を じかに よみこむ。
 * filesDir/packs/guide/ に ひらく。トークン は このタブレット だけ（書き出し・シートには 入れない）。
 */
object GuidePack {
    const val ASSET = "guide-pack.zip"
    private const val MAX_BYTES = 200L * 1024 * 1024

    var manifest by mutableStateOf<GuideManifest?>(null)
        private set
    /** アプリに 入っている デフォルト の ガイド（パック が ないとき） */
    var builtin by mutableStateOf(false)
        private set
    /** おやメニュー で ガイド役 を つかうか（つかわない なら えもじ の マスコット） */
    var enabled by mutableStateOf(true)
        private set
    var busy by mutableStateOf(false)
        private set
    var status by mutableStateOf<String?>(null)
        private set
    /** 3D の ひょうじ の ようす："よみこみ中" / null（うごいている）/ エラー の なかみ */
    var viewStatus by mutableStateOf<String?>(null)
    var viewReady by mutableStateOf(false)

    /** どこまで すすんだか（エラー が なければ 出す） */
    fun stage(s: String) {
        if (viewReady) return
        val cur = viewStatus
        if (cur == null || cur.startsWith("よみこみ中")) viewStatus = "よみこみ中…（$s）"
    }

    /** さいしょ の エラー を のこす（あとの こまかい エラー で うわがき しない） */
    fun reportError(msg: String) {
        if (viewReady) return
        val cur = viewStatus
        if (cur == null || cur.startsWith("よみこみ中")) viewStatus = msg
        else if (!cur.contains(msg) && cur.length < 400) viewStatus = "$cur ／ $msg"
    }

    /** きせかえ を かえた 合図 */
    var outfitVersion by mutableStateOf(0)
        private set

    private val scope = MainScope()

    fun dir(c: Context) = File(c.filesDir, "packs/guide")
    private fun secrets(c: Context) = c.getSharedPreferences("secrets", Context.MODE_PRIVATE)
    private fun prefs(c: Context) = c.getSharedPreferences("guide", Context.MODE_PRIVATE)

    fun repo(c: Context): String = secrets(c).getString("packRepo", "").orEmpty()
    fun token(c: Context): String = secrets(c).getString("packToken", "").orEmpty()
    fun setSource(c: Context, repo: String?, token: String?) {
        secrets(c).edit().apply {
            repo?.let { putString("packRepo", it.trim().removePrefix("https://github.com/").trim('/')) }
            token?.let { putString("packToken", it.trim()) }
        }.apply()
    }
    fun installedTag(c: Context): String = prefs(c).getString("tag", "").orEmpty()

    fun init(c: Context) {
        enabled = prefs(c).getBoolean("enabled", true)
        load(c)
    }

    /** いれた パック、なければ アプリの デフォルト */
    private fun load(c: Context) {
        val own = runCatching { parse(File(dir(c), "manifest.json").readText()) }.getOrNull()
        builtin = own == null
        manifest = own ?: runCatching { parse(c.assets.open("guide/default/manifest.json").bufferedReader().readText()) }.getOrNull()
        outfitVersion++
    }

    fun setEnabled(c: Context, on: Boolean) {
        prefs(c).edit().putBoolean("enabled", on).apply()
        enabled = on
    }

    /** へや に ガイド を だすか */
    val installed get() = enabled && manifest != null

    /** WebView から みた パック の ばしょ */
    val packPath get() = if (builtin) "/assets/guide/default/" else "/pack/guide/"

    /** サムネイル（パック の 中 か アプリの 中） */
    fun open(c: Context, path: String): java.io.InputStream? = runCatching {
        if (builtin) c.assets.open("guide/default/$path") else File(dir(c), path).inputStream()
    }.getOrNull()

    fun parse(text: String): GuideManifest {
        val o = JSONObject(text)
        fun strings(a: JSONArray?) = a?.let { (0 until it.length()).map { i -> it.getString(i) } } ?: emptyList()
        val lines = o.optJSONObject("lines")
        return GuideManifest(
            name = o.optString("name", "ガイド"),
            version = o.optString("version", "0"),
            greet = strings(lines?.optJSONArray("greet")),
            tap = strings(lines?.optJSONArray("tap")),
            slots = o.optJSONArray("slots")?.let { a ->
                (0 until a.length()).map { a.getJSONObject(it) }.map { s ->
                    GuideSlot(
                        s.optString("id"), s.optString("name"), s.optBoolean("optional"),
                        s.optJSONArray("items")?.let { ia ->
                            (0 until ia.length()).map { ia.getJSONObject(it) }.map { i ->
                                GuideItem(i.optString("id"), i.optString("name"), i.optString("emoji", "👗"), i.optString("thumbnail").ifBlank { null })
                            }
                        } ?: emptyList(),
                    )
                }
            } ?: emptyList(),
        )
    }

    // ---------------------------------------------------------------- きせかえ（子ども ごと）

    /** その子が えらんだ アイテム の id（スロットごとに 1 こ） */
    fun outfit(c: Context, profileId: String): List<String> =
        runCatching { JSONArray(prefs(c).getString("outfit:$profileId", "[]")).let { a -> (0 until a.length()).map { a.getString(it) } } }.getOrDefault(emptyList())

    fun choose(c: Context, profileId: String, slot: GuideSlot, item: GuideItem?) {
        val others = outfit(c, profileId).filter { id -> slot.items.none { it.id == id } }
        prefs(c).edit().putString("outfit:$profileId", JSONArray(others + listOfNotNull(item?.id)).toString()).apply()
        outfitVersion++
    }

    // ---------------------------------------------------------------- とってくる

    /** 新しい版が あれば とってくる。force でなければ 1 日 1 かい まで */
    fun update(c: Context, force: Boolean = false) {
        val app = c.applicationContext
        if (busy || repo(app).isBlank() || token(app).isBlank()) return
        val last = prefs(app).getLong("checked", 0)
        if (!force && System.currentTimeMillis() - last < 24 * 3600_000L) return
        busy = true
        status = "しらべています…"
        scope.launch {
            val r = runCatching { withContext(Dispatchers.IO) { fetch(app) } }
            busy = false
            prefs(app).edit().putLong("checked", System.currentTimeMillis()).apply()
            status = r.fold({ it }, { "とれませんでした（${it.message}）" })
        }
    }

    private fun api(url: String, token: String, accept: String): HttpURLConnection =
        (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 15_000; readTimeout = 60_000
            instanceFollowRedirects = false
            setRequestProperty("Authorization", "Bearer $token")
            setRequestProperty("Accept", accept)
            setRequestProperty("X-GitHub-Api-Version", "2022-11-28")
            setRequestProperty("User-Agent", "KidsHome")
        }

    private fun fetch(c: Context): String {
        val repo = repo(c); val token = token(c)
        val rel = api("https://api.github.com/repos/$repo/releases/latest", token, "application/vnd.github+json")
        val body = try {
            when (rel.responseCode) {
                200 -> rel.inputStream.bufferedReader().readText()
                401 -> throw IllegalStateException("トークンが ちがうか、きげんぎれ")
                404 -> throw IllegalStateException("リポジトリ か Release が みつからない（トークンの けんげん も たしかめて）")
                else -> throw IllegalStateException("HTTP ${rel.responseCode}")
            }
        } finally { rel.disconnect() }
        val o = JSONObject(body)
        val tag = o.optString("tag_name")
        if (tag == installedTag(c) && !builtin) return "さいしん です（$tag）"
        val assets = o.optJSONArray("assets") ?: JSONArray()
        val asset = (0 until assets.length()).map { assets.getJSONObject(it) }.firstOrNull { it.optString("name") == ASSET }
            ?: throw IllegalStateException("Release に $ASSET が ありません")
        if (asset.optLong("size") > MAX_BYTES) throw IllegalStateException("パックが 大きすぎます")
        // GitHub は 保存場所へ とばす（そこへは トークン を つけない）
        val a = api(asset.getString("url"), token, "application/octet-stream")
        val stream: InputStream
        val conns = mutableListOf(a)
        if (a.responseCode in 300..399) {
            val loc = a.getHeaderField("Location") ?: throw IllegalStateException("ダウンロード先が ない")
            val d = (URL(loc).openConnection() as HttpURLConnection).apply { connectTimeout = 15_000; readTimeout = 120_000 }
            conns += d
            if (d.responseCode != 200) throw IllegalStateException("HTTP ${d.responseCode}")
            stream = d.inputStream
        } else if (a.responseCode == 200) stream = a.inputStream
        else throw IllegalStateException("HTTP ${a.responseCode}")
        try {
            stream.use { install(c, it) }
        } finally { conns.forEach { it.disconnect() } }
        prefs(c).edit().putString("tag", tag).apply()
        return "${manifest?.name ?: "ガイド"} を いれました（$tag）"
    }

    /** zip を じかに よみこむ（ドライブ・NAS から） */
    fun importZip(c: Context, uri: Uri) {
        val app = c.applicationContext
        busy = true
        scope.launch {
            val r = runCatching {
                withContext(Dispatchers.IO) { app.contentResolver.openInputStream(uri)?.use { install(app, it) } ?: error("ひらけません") }
            }
            busy = false
            status = r.fold({ "${manifest?.name ?: "ガイド"} を いれました（zip）" }, { "よみこめませんでした（${it.message}）" })
            if (r.isSuccess) prefs(app).edit().putString("tag", "zip").apply()
        }
    }

    /** zip を べつの ばしょ に ひらいて、manifest.json が あれば いれかえる */
    private fun install(c: Context, input: InputStream) {
        val tmp = File(c.filesDir, "packs/.guide-new").apply { deleteRecursively(); mkdirs() }
        var total = 0L
        ZipInputStream(input).use { z ->
            while (true) {
                val e = z.nextEntry ?: break
                if (e.isDirectory) continue
                // 「guide/」 の した に まとめた zip も そのまま よめるように
                val name = e.name.replace('\\', '/').removePrefix("./")
                val out = File(tmp, name)
                if (!out.canonicalPath.startsWith(tmp.canonicalPath + File.separator)) error("あやしい ファイル名：$name")
                out.parentFile?.mkdirs()
                out.outputStream().use { o ->
                    val buf = ByteArray(64 * 1024)
                    while (true) {
                        val n = z.read(buf); if (n < 0) break
                        total += n
                        if (total > MAX_BYTES) error("パックが 大きすぎます")
                        o.write(buf, 0, n)
                    }
                }
            }
        }
        val root = listOf(tmp, File(tmp, "guide")).firstOrNull { File(it, "manifest.json").exists() } ?: error("manifest.json が ありません")
        val m = parse(File(root, "manifest.json").readText())
        val target = dir(c)
        target.deleteRecursively()
        target.parentFile?.mkdirs()
        if (!root.renameTo(target)) error("いれかえ できませんでした")
        tmp.deleteRecursively()
        scope.launch { builtin = false; manifest = m; outfitVersion++ }
    }

    /** いれた パック を はずして デフォルト に もどす */
    fun remove(c: Context) {
        dir(c).deleteRecursively()
        prefs(c).edit().remove("tag").apply()
        status = null
        load(c)
    }
}
