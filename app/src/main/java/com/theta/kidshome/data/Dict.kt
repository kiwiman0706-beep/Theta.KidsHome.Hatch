package com.theta.kidshome.data

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/**
 * じしょ・ひゃっかじてん：パソコンで 作った SQLite（非公開リポジトリ の tools/build_dict_db.py）を よむ。
 * 置き場所：Android/data/com.theta.kidshome/files/dict/kidshome-dict.sqlite（USB で コピー できる）。
 * 大きい 添付 は 同じ ところ の kidshome-dict-media/ に。
 */
object Dict {
    const val FILE = "kidshome-dict.sqlite"
    data class Book(val id: Long, val code: String, val title: String, val level: Int, val vault: String, val entries: Int)
    data class Hit(val id: Long, val code: String, val heading: String, val reading: String, val grade: Int?)
    data class Entry(
        val id: Long, val book: Book, val name: String, val heading: String, val reading: String,
        val kanji: String?, val grade: Int?, val body: String,
    ) {
        /** ― や 〜 を おきかえる 見出し語 */
        val word get() = kanji ?: reading.takeIf { it.isNotBlank() } ?: heading
    }

    private var db: SQLiteDatabase? = null
    var books by mutableStateOf<List<Book>>(emptyList())
        private set
    var built by mutableStateOf<String?>(null)
        private set
    var hasFts = false
        private set
    var importing by mutableStateOf<String?>(null)
        private set
    private val scope = MainScope()

    fun dir(c: Context): File = (c.getExternalFilesDir("dict") ?: File(c.filesDir, "dict")).apply { mkdirs() }
    fun file(c: Context) = File(dir(c), FILE)

    val ready get() = db != null && books.isNotEmpty()

    /** ひらく（なければ なにもしない） */
    fun open(c: Context): Boolean {
        if (db != null) return true
        val f = file(c)
        if (!f.exists()) return false
        return runCatching {
            val d = SQLiteDatabase.openDatabase(f.path, null, SQLiteDatabase.OPEN_READONLY or SQLiteDatabase.NO_LOCALIZED_COLLATORS)
            val list = mutableListOf<Book>()
            d.rawQuery("SELECT id, code, title, level, vault, entries FROM dict ORDER BY id", null).use { cu ->
                while (cu.moveToNext()) list += Book(cu.getLong(0), cu.getString(1), cu.getString(2), cu.getInt(3), cu.getString(4), cu.getInt(5))
            }
            built = runCatching { d.rawQuery("SELECT value FROM meta WHERE key='built'", null).use { if (it.moveToFirst()) it.getString(0) else null } }.getOrNull()
            hasFts = runCatching { d.rawQuery("SELECT value FROM meta WHERE key='fts'", null).use { it.moveToFirst() && it.getString(0) == "1" } }.getOrDefault(false)
            db = d
            books = list
            true
        }.getOrDefault(false)
    }

    fun close() {
        db?.close(); db = null; books = emptyList()
    }

    private fun book(id: Long) = books.firstOrNull { it.id == id }

    /** 読み・見出し・漢字 で さがす（前方一致。ちょうど の ものが 先） */
    fun search(q: String, codes: Set<String>, limit: Int = 80): List<Hit> {
        val d = db ?: return emptyList()
        val n = DictText.normalize(q)
        if (n.isEmpty()) return emptyList()
        val ids = books.filter { it.code in codes }.map { it.id }
        if (ids.isEmpty()) return emptyList()
        val inIds = ids.joinToString(",")
        val hi = n + "￿"
        val sql = """
            SELECT e.id, e.dict_id, e.heading, e.reading, e.grade FROM entry e
            WHERE e.dict_id IN ($inIds) AND (
              (e.reading >= ? AND e.reading < ?) OR (e.name >= ? AND e.name < ?) OR (e.kanji >= ? AND e.kanji < ?)
            )
            ORDER BY (e.reading = ? OR e.kanji = ? OR e.name = ?) DESC, length(e.reading), e.dict_id
            LIMIT $limit
        """.trimIndent()
        val out = mutableListOf<Hit>()
        d.rawQuery(sql, arrayOf(n, hi, q.trim(), q.trim() + "￿", q.trim(), q.trim() + "￿", n, q.trim(), q.trim())).use { c ->
            while (c.moveToNext()) {
                val b = book(c.getLong(1)) ?: continue
                out += Hit(c.getLong(0), b.code, c.getString(2), c.getString(3).orEmpty(), if (c.isNull(4)) null else c.getInt(4))
            }
        }
        return out
    }

    /** 本文 も ふくめて さがす（DB に 全文検索 が あるとき） */
    fun searchText(q: String, codes: Set<String>, limit: Int = 60): List<Hit> {
        val d = db ?: return emptyList()
        if (!hasFts || q.trim().length < 3) return emptyList()
        val ids = books.filter { it.code in codes }.map { it.id }.joinToString(",").ifEmpty { return emptyList() }
        val out = mutableListOf<Hit>()
        runCatching {
            d.rawQuery(
                "SELECT e.id, e.dict_id, e.heading, e.reading, e.grade FROM entry_fts f JOIN entry e ON e.id = f.rowid WHERE entry_fts MATCH ? AND e.dict_id IN ($ids) LIMIT $limit",
                arrayOf("\"" + q.trim().replace("\"", "") + "\""),
            ).use { c ->
                while (c.moveToNext()) {
                    val b = book(c.getLong(1)) ?: continue
                    out += Hit(c.getLong(0), b.code, c.getString(2), c.getString(3).orEmpty(), if (c.isNull(4)) null else c.getInt(4))
                }
            }
        }
        return out
    }

    fun entry(id: Long): Entry? {
        val d = db ?: return null
        return d.rawQuery("SELECT id, dict_id, name, heading, reading, kanji, grade, body FROM entry WHERE id=?", arrayOf(id.toString())).use { c ->
            if (!c.moveToFirst()) null
            else Entry(
                c.getLong(0), book(c.getLong(1)) ?: return null, c.getString(2), c.getString(3), c.getString(4).orEmpty(),
                c.getString(5), if (c.isNull(6)) null else c.getInt(6), c.getString(7).orEmpty(),
            )
        }
    }

    /** リンク [[名前]] を さがす（おなじ 辞書 → おなじ 保管庫 → ぜんぶ） */
    fun resolve(name: String, from: Entry?): Entry? {
        val d = db ?: return null
        val order = buildList {
            from?.let { add(it.book.id) }
            from?.let { f -> books.filter { it.vault == f.book.vault && it.id != f.book.id }.forEach { add(it.id) } }
            books.filter { b -> none { it == b.id } }.forEach { add(it.id) }
        }
        for (id in order) {
            val found = d.rawQuery("SELECT id FROM entry WHERE dict_id=? AND name=?", arrayOf(id.toString(), name)).use { if (it.moveToFirst()) it.getLong(0) else null }
            if (found != null) return entry(found)
        }
        return null
    }

    /** 図・外字・音（DB の 中 か、となり の フォルダ） */
    fun media(c: Context, vault: String, name: String): Pair<String, Any>? {
        val d = db ?: return null
        return d.rawQuery("SELECT mime, data, file FROM media WHERE vault=? AND name=?", arrayOf(vault, name)).use { cu ->
            if (!cu.moveToFirst()) null
            else if (!cu.isNull(1)) cu.getString(0) to cu.getBlob(1)
            else cu.getString(2)?.let { f -> File(dir(c), "kidshome-dict-media/$f").takeIf { it.exists() }?.let { cu.getString(0) to it } }
        }
    }

    /** きょう の かんじ：その子 の 学年 までに ならう 漢字 から */
    fun kanjiOfDay(grade: Int, n: Int = 12): List<Hit> {
        val d = db ?: return emptyList()
        val k = books.firstOrNull { it.code == "kokugo" } ?: return emptyList()
        val seed = (System.currentTimeMillis() / 86_400_000L) % 9973
        val out = mutableListOf<Hit>()
        d.rawQuery(
            "SELECT id, heading, reading, grade FROM entry WHERE dict_id=? AND grade IS NOT NULL AND grade<=? ORDER BY (id * 7919 + ?) % 10007 LIMIT $n",
            arrayOf(k.id.toString(), grade.coerceIn(1, 7).toString(), seed.toString()),
        ).use { c -> while (c.moveToNext()) out += Hit(c.getLong(0), "kokugo", c.getString(1), c.getString(2).orEmpty(), c.getInt(3)) }
        return out
    }

    /** ファイル を えらんで コピー（大きいので うらで） */
    fun import(c: Context, uri: Uri) {
        val app = c.applicationContext
        importing = "コピー中…"
        scope.launch {
            val r = runCatching {
                withContext(Dispatchers.IO) {
                    val tmp = File(dir(app), "$FILE.part")
                    val total = runCatching { app.contentResolver.openAssetFileDescriptor(uri, "r")?.use { it.length } }.getOrNull() ?: -1L
                    app.contentResolver.openInputStream(uri)!!.use { input ->
                        tmp.outputStream().use { o ->
                            val buf = ByteArray(1 shl 20)
                            var done = 0L; var shown = -1
                            while (true) {
                                val n = input.read(buf); if (n < 0) break
                                o.write(buf, 0, n); done += n
                                val pct = if (total > 0) (done * 100 / total).toInt() else (done shr 20).toInt()
                                if (pct != shown) { shown = pct; withContext(Dispatchers.Main) { importing = if (total > 0) "コピー中… $pct%" else "コピー中… ${done shr 20}MB" } }
                            }
                        }
                    }
                    // SQLite か たしかめて から いれかえ
                    val head = tmp.inputStream().use { val b = ByteArray(16); it.read(b); String(b, Charsets.US_ASCII) }
                    if (!head.startsWith("SQLite format 3")) { tmp.delete(); error("SQLite の ファイル では ありません") }
                    withContext(Dispatchers.Main) { close() }
                    file(app).delete()
                    if (!tmp.renameTo(file(app))) error("いれかえ できませんでした")
                }
            }
            importing = r.fold({ null }, { "コピー できませんでした（${it.message}）" })
            if (r.isSuccess) open(app)
        }
    }

    fun remove(c: Context) {
        close()
        file(c).delete()
    }
}
