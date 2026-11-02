package com.theta.kidshome.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Rect
import android.net.Uri
import android.util.LruCache
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
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

/**
 * きせかえ ドール（購入素材）。非公開リポジトリ の Release「doll-pack.zip」を ガイド役 と おなじ トークン で とってくる。
 * 公開 リポジトリ・APK には 入れない。filesDir/packs/doll に doll.json と img/・thumb/。
 * 衣装 は ごほうび（🎟）で あける：もじ れんしゅう 5 もじ で 1 まい（おや が かえられる）。
 */
object DollPack {
    const val ASSET = "doll-pack.zip"
    private const val MAX_BYTES = 200L * 1024 * 1024

    data class Layer(val src: String, val z: Int)
    data class Item(val id: String, val label: String, val layers: List<Layer>, val thumb: String)
    data class Slot(val id: String, val label: String, val emoji: String, val mode: String, val hidden: Boolean, val excludes: List<String>, val items: List<Item>) {
        val multi get() = mode == "multi"
        val required get() = mode == "one"
    }
    data class Pack(val version: String, val slots: List<Slot>, val always: List<Layer>, val defaults: Map<String, String>)

    var pack by mutableStateOf<Pack?>(null)
        private set
    var status by mutableStateOf<String?>(null)
        private set
    var busy by mutableStateOf(false)
        private set
    /** きせかえ・あけた もの が かわった とき */
    var version by mutableIntStateOf(0)
        private set
    private val scope = MainScope()

    val installed get() = pack != null

    fun dir(c: Context) = File(c.filesDir, "packs/doll")
    private fun prefs(c: Context) = c.getSharedPreferences("doll", Context.MODE_PRIVATE)

    fun parse(json: String): Pack {
        val o = JSONObject(json)
        fun layers(a: JSONArray) = (0 until a.length()).map { a.getJSONObject(it).let { l -> Layer(l.getString("src"), l.getInt("z")) } }
        val slots = o.getJSONArray("slots").let { a ->
            (0 until a.length()).map { i ->
                val s = a.getJSONObject(i)
                Slot(
                    s.getString("id"), s.getString("label"), s.getString("emoji"), s.getString("mode"), s.optBoolean("hidden"),
                    s.optJSONArray("excludes")?.let { e -> (0 until e.length()).map { e.getString(it) } }.orEmpty(),
                    s.getJSONArray("items").let { it2 ->
                        (0 until it2.length()).map { j -> it2.getJSONObject(j).let { t -> Item(t.getString("id"), t.optString("label"), layers(t.getJSONArray("layers")), t.getString("thumb")) } }
                    },
                )
            }
        }
        val d = o.optJSONObject("defaults")
        return Pack(o.optString("version"), slots, layers(o.optJSONArray("always") ?: JSONArray()), d?.keys()?.asSequence()?.associateWith { d.getString(it) }.orEmpty())
    }

    fun load(c: Context) {
        if (pack != null) return
        pack = runCatching { parse(File(dir(c), "doll.json").readText()) }.getOrNull()
    }

    fun file(c: Context, path: String) = File(dir(c), path)

    // ---------------------------------------------------------------- きせかえ（子ども ごと）

    /** スロット → えらんだ アイテム（multi は いくつ でも） */
    fun look(c: Context, profile: String): Map<String, List<String>> {
        val p = pack ?: return emptyMap()
        val saved = prefs(c).getString("look:$profile", null)
        if (saved == null) return p.defaults.mapValues { listOf(it.value) }
        return runCatching {
            val o = JSONObject(saved)
            o.keys().asSequence().associateWith { k -> o.getJSONArray(k).let { a -> (0 until a.length()).map { a.getString(it) } } }
        }.getOrDefault(emptyMap())
    }

    private fun saveLook(c: Context, profile: String, look: Map<String, List<String>>) {
        val o = JSONObject(); look.forEach { (k, v) -> if (v.isNotEmpty()) o.put(k, JSONArray(v)) }
        prefs(c).edit().putString("look:$profile", o.toString()).apply()
        version++
    }

    /** item を きる／ぬぐ */
    fun toggle(c: Context, profile: String, slot: Slot, item: Item) {
        val cur = look(c, profile).toMutableMap()
        val now = cur[slot.id].orEmpty()
        cur[slot.id] = when {
            item.id in now -> if (slot.required) now else now - item.id
            slot.multi -> now + item.id
            else -> listOf(item.id)
        }
        if (item.id !in now) slot.excludes.forEach { cur.remove(it) }
        saveLook(c, profile, cur)
    }

    /** あけた もの から でたらめ に */
    fun random(c: Context, profile: String) {
        val p = pack ?: return
        val look = HashMap<String, List<String>>()
        p.slots.filter { visible(c, it) }.forEach { s ->
            val open = s.items.filter { unlocked(c, profile, s, it) }
            if (open.isEmpty()) return@forEach
            if (s.required || Math.random() < 0.6) look[s.id] = listOf(open.random().id)
        }
        if ("dress" in look) { look.remove("top"); look.remove("bottom") } else if ("top" in look || "bottom" in look) look.remove("dress")
        saveLook(c, profile, look)
    }

    fun layers(look: Map<String, List<String>>): List<Layer> {
        val p = pack ?: return emptyList()
        val byId = p.slots.flatMap { s -> s.items.map { it.id to it } }.toMap()
        return (p.always + look.values.flatten().mapNotNull { byId[it] }.flatMap { it.layers }).sortedBy { it.z }
    }

    private val cache = object : LruCache<String, Bitmap>(48 * 1024 * 1024) {
        override fun sizeOf(key: String, value: Bitmap) = value.byteCount
    }

    /** かさねた 1 まい の 絵（よこ 400・たて 600） */
    fun compose(c: Context, look: Map<String, List<String>>, half: Boolean = true): Bitmap? {
        val ls = layers(look)
        if (ls.isEmpty()) return null
        val key = (if (half) "h:" else "f:") + ls.joinToString(",") { it.src }
        cache.get(key)?.let { return it }
        val w = if (half) 400 else 800; val h = if (half) 600 else 1200
        val out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val cv = Canvas(out)
        val dst = Rect(0, 0, w, h)
        ls.forEach { l ->
            val b = BitmapFactory.decodeFile(file(c, l.src).path, BitmapFactory.Options().apply { inSampleSize = if (half) 2 else 1 }) ?: return@forEach
            cv.drawBitmap(b, null, dst, null)
            b.recycle()
        }
        cache.put(key, out)
        return out
    }

    // ---------------------------------------------------------------- ごほうび（🎟）

    /** はじめ から つかえる スロット（からだ と かお）と はじめ の かっこう */
    private val FREE_SLOTS = setOf("body", "eyes", "pupils", "lashes", "brows", "nose", "mouth", "ears")

    fun perTicket(c: Context) = prefs(c).getInt("perTicket", 5)
    fun setPerTicket(c: Context, n: Int) { prefs(c).edit().putInt("perTicket", n).apply(); version++ }
    fun allOpen(c: Context) = prefs(c).getBoolean("allOpen", false)
    fun setAllOpen(c: Context, v: Boolean) { prefs(c).edit().putBoolean("allOpen", v).apply(); version++ }

    /** かくした スロット（ぶき・したぎ は はじめ から かくす。おや が かえる） */
    fun visible(c: Context, s: Slot) = prefs(c).getBoolean("show:${s.id}", !s.hidden)
    fun setVisible(c: Context, s: Slot, v: Boolean) { prefs(c).edit().putBoolean("show:${s.id}", v).apply(); version++ }

    private fun opened(c: Context, profile: String): Set<String> = prefs(c).getStringSet("open:$profile", emptySet()).orEmpty()

    /** スロット ごと に はじめ から あける 数（おや が 0／3／5／10 に） */
    fun freePerSlot(c: Context) = prefs(c).getInt("freePerSlot", 3)
    fun setFreePerSlot(c: Context, n: Int) { prefs(c).edit().putInt("freePerSlot", n).apply(); version++ }

    fun unlocked(c: Context, profile: String, slot: Slot, item: Item): Boolean =
        allOpen(c) || slot.id in FREE_SLOTS || slot.items.indexOf(item) < freePerSlot(c) ||
            pack?.defaults?.values?.contains(item.id) == true || item.id in opened(c, profile)

    /** はじめ に もらえる 🎟 */
    private const val STARTER = 3

    /** もらった チケット（もじ れんしゅう で かいた 字 の 数 ÷ perTicket） */
    fun earned(c: Context, profile: String) = STARTER + Moji.writtenChars(c, profile).size / perTicket(c) + prefs(c).getInt("bonus:$profile", 0)

    fun tickets(c: Context, profile: String) = (earned(c, profile) - opened(c, profile).size).coerceAtLeast(0)

    /** 🎟 1 まい で あける */
    fun unlock(c: Context, profile: String, item: Item): Boolean {
        if (tickets(c, profile) <= 0) return false
        prefs(c).edit().putStringSet("open:$profile", opened(c, profile) + item.id).apply()
        version++
        return true
    }

    /** おや から の おまけ */
    fun addBonus(c: Context, profile: String, n: Int) { prefs(c).edit().putInt("bonus:$profile", prefs(c).getInt("bonus:$profile", 0) + n).apply(); version++ }

    // ---------------------------------------------------------------- ガイド役 に する

    fun guideOn(c: Context, profile: String) = installed && prefs(c).getBoolean("guide:$profile", true)
    fun setGuide(c: Context, profile: String, v: Boolean) { prefs(c).edit().putBoolean("guide:$profile", v).apply(); version++ }

    // ---------------------------------------------------------------- とってくる

    fun installedTag(c: Context) = prefs(c).getString("tag", "").orEmpty()

    fun update(c: Context, force: Boolean = false) {
        val app = c.applicationContext
        val repo = GuidePack.repo(app); val token = GuidePack.token(app)
        if (busy || repo.isBlank() || token.isBlank()) return
        if (!force && System.currentTimeMillis() - prefs(app).getLong("checked", 0) < 24 * 3600_000L) return
        busy = true; status = "しらべています…"
        scope.launch {
            val r = runCatching { withContext(Dispatchers.IO) { fetch(app, repo, token) } }
            busy = false
            prefs(app).edit().putLong("checked", System.currentTimeMillis()).apply()
            status = r.fold({ it }, { "とれませんでした（${it.message}）" })
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
            in 300..399 -> (URL(a.getHeaderField("Location") ?: error("ダウンロード先が ない")).openConnection() as HttpURLConnection)
                .apply { connectTimeout = 15_000; readTimeout = 180_000 }.also { conns += it }.let { if (it.responseCode != 200) error("HTTP ${it.responseCode}"); it.inputStream }
            200 -> a.inputStream
            else -> error("HTTP ${a.responseCode}")
        }
        try { stream.use { install(c, it) } } finally { conns.forEach { it.disconnect() } }
        prefs(c).edit().putString("tag", tag).apply()
        return "きせかえ ドール を いれました（$tag）"
    }

    fun importZip(c: Context, uri: Uri) {
        val app = c.applicationContext
        busy = true; status = "よみこんで います…"
        scope.launch {
            val r = runCatching { withContext(Dispatchers.IO) { app.contentResolver.openInputStream(uri)?.use { install(app, it) } ?: error("ひらけません") } }
            busy = false
            status = r.fold({ "きせかえ ドール を いれました（zip）" }, { "よみこめませんでした（${it.message}）" })
            if (r.isSuccess) prefs(app).edit().putString("tag", "zip").apply()
        }
    }

    private fun install(c: Context, input: InputStream) {
        val tmp = File(c.filesDir, "packs/.doll-new").apply { deleteRecursively(); mkdirs() }
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
                    while (true) { val n = z.read(buf); if (n < 0) break; total += n; if (total > MAX_BYTES) error("パックが 大きすぎます"); o.write(buf, 0, n) }
                }
            }
        }
        val root = listOf(tmp, File(tmp, "doll")).firstOrNull { File(it, "doll.json").exists() } ?: error("doll.json が ありません")
        val p = parse(File(root, "doll.json").readText())
        val target = dir(c)
        target.deleteRecursively(); target.parentFile?.mkdirs()
        if (!root.renameTo(target)) error("いれかえ できませんでした")
        tmp.deleteRecursively()
        cache.evictAll()
        scope.launch { pack = p; version++ }
    }

    fun remove(c: Context) {
        dir(c).deleteRecursively(); prefs(c).edit().remove("tag").apply(); pack = null; cache.evictAll(); version++
    }
}

/** 非公開リポジトリ の Release から、その 名前 の ファイル が ある いちばん 新しい もの を さがす（ガイド と ドール が べつ の Release でも） */
object PackRelease {
    fun find(repo: String, token: String, asset: String): Pair<String, String> {
        val conn = (URL("https://api.github.com/repos/$repo/releases?per_page=30").openConnection() as HttpURLConnection).apply {
            connectTimeout = 15_000; readTimeout = 60_000
            setRequestProperty("Authorization", "Bearer $token"); setRequestProperty("Accept", "application/vnd.github+json")
            setRequestProperty("X-GitHub-Api-Version", "2022-11-28"); setRequestProperty("User-Agent", "KidsHome")
        }
        val body = try {
            when (conn.responseCode) {
                200 -> conn.inputStream.bufferedReader().readText()
                401 -> error("トークンが ちがうか、きげんぎれ")
                404 -> error("リポジトリ が みつからない（トークンの けんげん も たしかめて）")
                else -> error("HTTP ${conn.responseCode}")
            }
        } finally { conn.disconnect() }
        val rels = JSONArray(body)
        for (i in 0 until rels.length()) {
            val r = rels.getJSONObject(i)
            if (r.optBoolean("draft")) continue
            val assets = r.optJSONArray("assets") ?: continue
            for (j in 0 until assets.length()) {
                val a = assets.getJSONObject(j)
                if (a.optString("name") == asset) return r.optString("tag_name") to a.getString("url")
            }
        }
        error("Release に $asset が ありません")
    }
}
