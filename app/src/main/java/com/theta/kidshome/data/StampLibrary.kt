package com.theta.kidshome.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.net.Uri
import android.util.LruCache
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.UUID
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import kotlin.math.abs

/** スタンプ・シール 1 まい（すけた PNG） */
data class StampItem(val id: String, val name: String, val created: Long, val price: Int = 0, val set: String = "") {
    /** ごほうび（ポイント で こうかん する まで つかえない） */
    val reward get() = price > 0
}

/**
 * スタンプ・シールの ライブラリ。おえかき・ホーム（うかぶ シール）など、どこからでも使う。
 * 写真や画像から切り抜いて作る（Gemini で型どり、または まわりの色を消す）。ZIP で書き出し・読み込み。
 */
object StampLibrary {
    private lateinit var app: Context
    /** ふえた・へった 合図（画面の描き直し用） */
    var version by mutableIntStateOf(0)
        private set
    private val cache = object : LruCache<String, Bitmap>(32 * 1024 * 1024) {
        override fun sizeOf(key: String, value: Bitmap) = value.byteCount
    }

    fun init(context: Context) {
        if (!::app.isInitialized) app = context.applicationContext
    }

    private fun dir() = File(app.filesDir, "stamps").apply { mkdirs() }
    private fun index() = File(dir(), "index.json")

    fun list(): List<StampItem> = runCatching {
        val a = JSONArray(index().readText())
        (0 until a.length()).map { a.getJSONObject(it) }.map { StampItem(it.getString("id"), it.optString("name"), it.optLong("created"), it.optInt("price", 0)) }
    }.getOrDefault(emptyList())

    private fun write(items: List<StampItem>) {
        index().writeText(JSONArray(items.map { JSONObject().put("id", it.id).put("name", it.name).put("created", it.created).put("price", it.price) }).toString())
        version++
    }

    /** パック を いれた・かった とき の 合図 */
    fun touch() { version++ }

    /** スタンプ パック（セット）の もの。id は「pk:セット/ファイル」 */
    fun packItems(): List<StampItem> = StampPack.sets.flatMap { s -> s.items.map { f -> StampItem("pk:${s.id}/$f", s.name, 0L, 0, s.id) } }

    fun bitmap(id: String): Bitmap? {
        cache.get(id)?.let { return it }
        if (id.startsWith("pk:")) {
            val (set, item) = id.removePrefix("pk:").split('/', limit = 2).let { it[0] to it.getOrElse(1) { "" } }
            val f = StampPack.file(app, set, item)
            if (!f.exists()) return null
            return BitmapFactory.decodeFile(f.path)?.also { cache.put(id, it) }
        }
        val f = File(dir(), "$id.png")
        if (!f.exists()) return null
        return BitmapFactory.decodeFile(f.path)?.also { cache.put(id, it) }
    }

    fun add(bitmap: Bitmap, name: String, price: Int = 0): StampItem {
        val item = StampItem(UUID.randomUUID().toString(), name.ifBlank { "シール" }, System.currentTimeMillis(), price)
        File(dir(), "${item.id}.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        write(listOf(item) + list())
        return item
    }

    fun setPrice(id: String, price: Int) = write(list().map { if (it.id == id) it.copy(price = price) else it })

    private fun ownPrefs() = app.getSharedPreferences("stamps", Context.MODE_PRIVATE)
    private fun owned(profile: String) = ownPrefs().getStringSet("own:$profile", emptySet()).orEmpty()
    /** この 子 が つかえる か（ふつう の シール は いつも、ごほうび は こうかん した もの だけ） */
    fun usable(s: StampItem, profile: String = Store.config.currentProfileId) =
        if (s.set.isNotEmpty()) StampPack.usable(app, s.set, profile) else !s.reward || s.id in owned(profile)
    /** この 子 が つかえる シール（じぶん の ＋ こうかん した セット） */
    fun mine(profile: String = Store.config.currentProfileId) = list().filter { usable(it, profile) } + packItems().filter { usable(it, profile) }
    /** 🪙 で こうかん */
    fun buy(s: StampItem, profile: String = Store.config.currentProfileId): Boolean {
        if (usable(s, profile)) return true
        if (!Points.spend(app, profile, s.price, "スタンプ：${s.name}")) return false
        ownPrefs().edit().putStringSet("own:$profile", owned(profile) + s.id).apply()
        version++
        return true
    }

    /**
     * イラスト が たくさん ならんだ 1 まい を、1 こ ずつ に きりわける（LINE スタンプ の よう に）。
     * まわり の 色（ふち の 色）を 背景 と して、つながった かたまり ごと に きりだし、背景 を けす。
     * gap：これ より ちかい かたまり は 1 つ に まとめる（はなれた 目 や 文字 を いっしょ に）。
     */
    fun splitSheet(src: Bitmap, gap: Float = 0.012f, tolerance: Int = 60): List<Bitmap> {
        val scale = minOf(1f, 900f / maxOf(src.width, src.height))
        val small = if (scale < 1f) Bitmap.createScaledBitmap(src, (src.width * scale).toInt().coerceAtLeast(1), (src.height * scale).toInt().coerceAtLeast(1), true) else src
        val w = small.width; val h = small.height
        val px = IntArray(w * h); small.getPixels(px, 0, w, 0, 0, w, h)
        // ふち の 色 の まんなか を 背景 に
        val border = ArrayList<Int>()
        for (x in 0 until w) { border += px[x]; border += px[(h - 1) * w + x] }
        for (y in 0 until h) { border += px[y * w]; border += px[y * w + w - 1] }
        fun ch(c: Int, sh: Int) = (c shr sh) and 0xFF
        val br = border.map { ch(it, 16) }.sorted()[border.size / 2]
        val bg = border.map { ch(it, 8) }.sorted()[border.size / 2]
        val bb = border.map { ch(it, 0) }.sorted()[border.size / 2]
        val fg = BooleanArray(w * h) { i ->
            val c = px[i]
            (c ushr 24) > 40 && abs(ch(c, 16) - br) + abs(ch(c, 8) - bg) + abs(ch(c, 0) - bb) > tolerance
        }
        // ちかい もの を つなげる（ふくらませる）
        val r = (maxOf(w, h) * gap).toInt().coerceAtLeast(1)
        val tmp = BooleanArray(w * h)
        for (y in 0 until h) { var last = -100000; for (x in 0 until w) { if (fg[y * w + x]) last = x; if (x - last <= r) tmp[y * w + x] = true }
            last = 100000; for (x in w - 1 downTo 0) { if (fg[y * w + x]) last = x; if (last - x <= r) tmp[y * w + x] = true } }
        val grown = BooleanArray(w * h)
        for (x in 0 until w) { var last = -100000; for (y in 0 until h) { if (tmp[y * w + x]) last = y; if (y - last <= r) grown[y * w + x] = true }
            last = 100000; for (y in h - 1 downTo 0) { if (tmp[y * w + x]) last = y; if (last - y <= r) grown[y * w + x] = true } }
        // かたまり ごと の わく
        val label = IntArray(w * h)
        val boxes = ArrayList<IntArray>() // l, t, r, b, 本当 の 画素 の 数
        val stack = ArrayDeque<Int>()
        for (start in 0 until w * h) {
            if (!grown[start] || label[start] != 0) continue
            val id = boxes.size + 1
            val box = intArrayOf(w, h, -1, -1, 0, id)
            label[start] = id; stack.addLast(start)
            while (stack.isNotEmpty()) {
                val i = stack.removeLast(); val x = i % w; val y = i / w
                if (fg[i]) { box[0] = minOf(box[0], x); box[1] = minOf(box[1], y); box[2] = maxOf(box[2], x); box[3] = maxOf(box[3], y); box[4]++ }
                if (x > 0 && grown[i - 1] && label[i - 1] == 0) { label[i - 1] = id; stack.addLast(i - 1) }
                if (x < w - 1 && grown[i + 1] && label[i + 1] == 0) { label[i + 1] = id; stack.addLast(i + 1) }
                if (y > 0 && grown[i - w] && label[i - w] == 0) { label[i - w] = id; stack.addLast(i - w) }
                if (y < h - 1 && grown[i + w] && label[i + w] == 0) { label[i + w] = id; stack.addLast(i + w) }
            }
            boxes += box
        }
        val minArea = w * h * 0.002
        val keep = boxes.filter { it[4] > minArea && it[2] > it[0] && it[3] > it[1] }
            .sortedWith(compareBy({ it[1] / (h / 8 + 1) }, { it[0] })) // うえ から、ひだり から
        return keep.map { b ->
            val pad = r
            val l = ((b[0] - pad).coerceAtLeast(0) / scale).toInt(); val t = ((b[1] - pad).coerceAtLeast(0) / scale).toInt()
            val rr = ((b[2] + pad + 1).coerceAtMost(w) / scale).toInt().coerceAtMost(src.width); val bt = ((b[3] + pad + 1).coerceAtMost(h) / scale).toInt().coerceAtMost(src.height)
            val crop = Bitmap.createBitmap(src, l, t, (rr - l).coerceAtLeast(1), (bt - t).coerceAtLeast(1))
            val out = removeBackground(crop, tolerance)
            // となり の イラスト が はみだして いたら けす
            val cw = out.width; val chh = out.height
            val op = IntArray(cw * chh); out.getPixels(op, 0, cw, 0, 0, cw, chh)
            for (y in 0 until chh) for (x in 0 until cw) {
                val sx = ((l + x) * scale).toInt().coerceIn(0, w - 1); val sy = ((t + y) * scale).toInt().coerceIn(0, h - 1)
                val lb = label[sy * w + sx]
                if (lb != 0 && lb != b[5]) op[y * cw + x] = Color.TRANSPARENT
            }
            out.setPixels(op, 0, cw, 0, 0, cw, chh)
            out
        }
    }

    fun rename(id: String, name: String) = write(list().map { if (it.id == id) it.copy(name = name) else it })

    fun delete(id: String) {
        File(dir(), "$id.png").delete()
        cache.remove(id)
        write(list().filter { it.id != id })
    }

    /** ZIP：index.json と PNG */
    fun exportZip(out: File): Int {
        val items = list()
        ZipOutputStream(out.outputStream()).use { z ->
            z.putNextEntry(ZipEntry("index.json")); z.write(index().readBytes()); z.closeEntry()
            items.forEach { s ->
                val f = File(dir(), "${s.id}.png")
                if (f.exists()) { z.putNextEntry(ZipEntry("${s.id}.png")); z.write(f.readBytes()); z.closeEntry() }
            }
        }
        return items.size
    }

    /** ZIP を読み込む（同じ id は上書きしない）。読み込んだ数 */
    fun importZip(context: Context, uri: Uri): Int {
        var idx: List<StampItem> = emptyList()
        val pngs = HashMap<String, ByteArray>()
        context.contentResolver.openInputStream(uri)?.use { input ->
            ZipInputStream(input).use { z ->
                while (true) {
                    val e = z.nextEntry ?: break
                    val name = File(e.name).name
                    val bytes = z.readBytes()
                    if (name == "index.json") idx = runCatching {
                        val a = JSONArray(String(bytes))
                        (0 until a.length()).map { a.getJSONObject(it) }.map { StampItem(it.getString("id"), it.optString("name"), it.optLong("created"), it.optInt("price", 0)) }
                    }.getOrDefault(emptyList())
                    else if (name.endsWith(".png") && name.length < 80) pngs[name.removeSuffix(".png")] = bytes
                }
            }
        }
        val have = list().map { it.id }.toSet()
        val added = idx.filter { it.id !in have && pngs.containsKey(it.id) && it.id.matches(Regex("[A-Za-z0-9-]+")) }
        added.forEach { File(dir(), "${it.id}.png").writeBytes(pngs.getValue(it.id)) }
        if (added.isNotEmpty()) write(added + list())
        return added.size
    }

    // ---------------------------------------------------------------- きりぬき

    /** keyColor に近い色を すけさせる（Gemini に みどりの背景で出してもらう） */
    fun chromaKey(src: Bitmap, keyColor: Int = Color.rgb(0, 255, 0), tolerance: Int = 110): Bitmap {
        val b = src.copy(Bitmap.Config.ARGB_8888, true)
        val px = IntArray(b.width * b.height)
        b.getPixels(px, 0, b.width, 0, 0, b.width, b.height)
        val kr = Color.red(keyColor); val kg = Color.green(keyColor); val kb = Color.blue(keyColor)
        for (i in px.indices) {
            val c = px[i]
            val d = abs(Color.red(c) - kr) + abs(Color.green(c) - kg) + abs(Color.blue(c) - kb)
            // みどりが強い ところも消す（ふちの にじみ）
            val greenish = Color.green(c) > 150 && Color.green(c) > Color.red(c) + 60 && Color.green(c) > Color.blue(c) + 60
            if (d < tolerance || greenish) px[i] = Color.TRANSPARENT
        }
        b.setPixels(px, 0, b.width, 0, 0, b.width, b.height)
        return b
    }

    /** 四すみから つながった「まわりの色」を消す（白い紙に貼ったシールの写真など） */
    fun removeBackground(src: Bitmap, tolerance: Int = 60): Bitmap {
        val b = src.copy(Bitmap.Config.ARGB_8888, true)
        val w = b.width; val h = b.height
        val px = IntArray(w * h)
        b.getPixels(px, 0, w, 0, 0, w, h)
        val seen = BooleanArray(w * h)
        val stack = ArrayDeque<Int>()
        for (start in listOf(0, w - 1, (h - 1) * w, h * w - 1)) {
            val base = px[start]
            stack.add(start)
            while (stack.isNotEmpty()) {
                val i = stack.removeLast()
                if (i < 0 || i >= px.size || seen[i]) continue
                val c = px[i]
                if (abs(Color.red(c) - Color.red(base)) + abs(Color.green(c) - Color.green(base)) + abs(Color.blue(c) - Color.blue(base)) > tolerance) continue
                seen[i] = true
                px[i] = Color.TRANSPARENT
                val x = i % w
                if (x > 0 && !seen[i - 1]) stack.add(i - 1)
                if (x < w - 1 && !seen[i + 1]) stack.add(i + 1)
                if (i - w >= 0 && !seen[i - w]) stack.add(i - w)
                if (i + w < px.size && !seen[i + w]) stack.add(i + w)
            }
        }
        b.setPixels(px, 0, w, 0, 0, w, h)
        return b
    }

    /** すけた まわりを切りつめて、白いフチ（シールの形）をつけて、大きさをそろえる */
    fun finish(cut: Bitmap, border: Boolean = true, max: Int = 512): Bitmap {
        val w = cut.width; val h = cut.height
        val px = IntArray(w * h)
        cut.getPixels(px, 0, w, 0, 0, w, h)
        var l = w; var t = h; var r = -1; var bm = -1
        for (y in 0 until h) for (x in 0 until w) if (Color.alpha(px[y * w + x]) > 20) {
            if (x < l) l = x; if (x > r) r = x; if (y < t) t = y; if (y > bm) bm = y
        }
        if (r < l || bm < t) return cut
        val trimmed = Bitmap.createBitmap(cut, l, t, r - l + 1, bm - t + 1)
        val s = (max * 0.86f) / maxOf(trimmed.width, trimmed.height)
        val scaled = Bitmap.createScaledBitmap(trimmed, (trimmed.width * s).toInt().coerceAtLeast(1), (trimmed.height * s).toInt().coerceAtLeast(1), true)
        val pad = (max * 0.05f).toInt()
        val out = Bitmap.createBitmap(scaled.width + pad * 2, scaled.height + pad * 2, Bitmap.Config.ARGB_8888)
        val c = Canvas(out)
        if (border) {
            // 白いフチ：形を少しずつ ずらして白で描く
            val white = scaled.extractAlpha()
            val p = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE }
            val shadow = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(60, 0, 0, 0) }
            val rr = pad * 0.8f
            for (k in 0 until 24) {
                val a = k * Math.PI * 2 / 24
                c.drawBitmap(white, pad + (Math.cos(a) * rr).toFloat() + 2, pad + (Math.sin(a) * rr).toFloat() + 3, shadow)
            }
            for (k in 0 until 24) {
                val a = k * Math.PI * 2 / 24
                c.drawBitmap(white, pad + (Math.cos(a) * rr).toFloat(), pad + (Math.sin(a) * rr).toFloat(), p)
            }
        }
        c.drawBitmap(scaled, pad.toFloat(), pad.toFloat(), null)
        return out
    }
}
