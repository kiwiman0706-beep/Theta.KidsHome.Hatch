package com.theta.kidshome.data

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.net.Uri
import android.os.Environment
import android.provider.OpenableColumns
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
 * 辞書は あとから ふやせる：dict フォルダ の *.sqlite を ぜんぶ よむ（1 ファイル に 1 冊 でも、まとめて でも）。
 * 置き場所：本体 か SD カード の Android/data/com.theta.kidshome/files/dict/（このアプリ専用 なので 許可 いらず）。
 * 大きい 添付 は おなじ フォルダ の <ファイル名>-media/ か kidshome-dict-media/ に。
 */
object Dict {
    const val FILE = "kidshome-dict.sqlite"

    /** 1 つ の DB ファイル */
    class Source(val file: File, val db: SQLiteDatabase, val built: String?, val fts: Boolean)

    data class Book(
        val key: String, val source: Source, val id: Long, val code: String, val title: String,
        val level: Int, val vault: String, val entries: Int, val tab: String,
    )
    data class Ref(val key: String, val id: Long)
    data class Hit(val ref: Ref, val book: Book, val heading: String, val reading: String, val grade: Int?)
    data class Entry(
        val ref: Ref, val book: Book, val name: String, val heading: String, val reading: String,
        val kanji: String?, val grade: Int?, val body: String,
    ) {
        /** ― や 〜 を おきかえる 見出し語 */
        val word get() = kanji ?: reading.takeIf { it.isNotBlank() } ?: heading
    }

    private var sources: List<Source> = emptyList()
    var books by mutableStateOf<List<Book>>(emptyList())
        private set
    var importing by mutableStateOf<String?>(null)
        private set
    private val scope = MainScope()

    /** 置ける ところ（SD カード を さき に）。どちらも このアプリ専用 */
    fun dirs(c: Context): List<File> {
        val ext = c.getExternalFilesDirs("dict").filterNotNull()
        val sd = ext.filter { runCatching { Environment.isExternalStorageRemovable(it) }.getOrDefault(false) }
        return (sd + ext.filterNot { it in sd } + File(c.filesDir, "dict")).distinct()
    }

    fun isSd(f: File) = runCatching { Environment.isExternalStorageRemovable(f) }.getOrDefault(false)

    /** あたらしく 入れる ところ（SD カード が あれば SD） */
    fun target(c: Context): File = dirs(c).first().apply { mkdirs() }

    fun files(c: Context): List<File> = dirs(c).flatMap { d ->
        d.mkdirs()
        d.listFiles { f -> f.isFile && f.name.endsWith(".sqlite") }?.toList().orEmpty()
    }

    val ready get() = books.isNotEmpty()

    /** 辞書 を さがして ひらく（ふえた ファイル も） */
    fun open(c: Context) {
        val have = sources.map { it.file.path }.toSet()
        val found = files(c)
        if (found.map { it.path }.toSet() == have) return
        close()
        val srcs = mutableListOf<Source>()
        val list = mutableListOf<Book>()
        for (f in found.sortedBy { it.name }) {
            runCatching {
                val d = SQLiteDatabase.openDatabase(f.path, null, SQLiteDatabase.OPEN_READONLY or SQLiteDatabase.NO_LOCALIZED_COLLATORS)
                fun meta(k: String) = runCatching { d.rawQuery("SELECT value FROM meta WHERE key=?", arrayOf(k)).use { if (it.moveToFirst()) it.getString(0) else null } }.getOrNull()
                val src = Source(f, d, meta("built"), meta("fts") == "1")
                val hasTab = runCatching { d.rawQuery("SELECT tab FROM dict LIMIT 1", null).close(); true }.getOrDefault(false)
                d.rawQuery("SELECT id, code, title, level, vault, entries${if (hasTab) ", tab" else ""} FROM dict ORDER BY id", null).use { cu ->
                    while (cu.moveToNext()) {
                        val code = cu.getString(1)
                        // おなじ 辞書 が 2 つ の ファイル に あれば さき の ほう
                        if (list.any { it.code == code }) continue
                        list += Book(
                            "${f.name}#${cu.getLong(0)}", src, cu.getLong(0), code, cu.getString(2), cu.getInt(3), cu.getString(4), cu.getInt(5),
                            (if (hasTab) cu.getString(6) else null) ?: tabOf(code),
                        )
                    }
                }
                srcs += src
            }
        }
        sources = srcs
        books = list
    }

    /** ふるい DB（tab が ない）用 */
    fun tabOf(code: String) = when (code) {
        "kokugo", "koujien", "furoku" -> "kokugo"
        "crcen", "eiwa", "waei" -> "eigo"
        "encarta" -> "hyakka"
        "promedica" -> "karada"
        else -> "other"
    }

    fun close() {
        sources.forEach { runCatching { it.db.close() } }
        sources = emptyList(); books = emptyList()
    }

    private fun book(key: String) = books.firstOrNull { it.key == key }

    private fun hits(b: Book, sql: String, args: Array<String>): List<Hit> {
        val out = mutableListOf<Hit>()
        runCatching {
            b.source.db.rawQuery(sql, args).use { c ->
                while (c.moveToNext()) out += Hit(Ref(b.key, c.getLong(0)), b, c.getString(1), c.getString(2).orEmpty(), if (c.isNull(3)) null else c.getInt(3))
            }
        }
        return out
    }

    /** 読み・見出し・漢字 で さがす（前方一致。ちょうど の ものが 先） */
    fun search(q: String, keys: Set<String>, limit: Int = 80): List<Hit> {
        val n = DictText.normalize(q)
        val t = q.trim()
        if (n.isEmpty()) return emptyList()
        val all = books.filter { it.key in keys }.flatMap { b ->
            hits(
                b,
                """SELECT id, heading, reading, grade FROM entry WHERE dict_id = ? AND (
                     (reading >= ? AND reading < ?) OR (name >= ? AND name < ?) OR (kanji >= ? AND kanji < ?))
                   ORDER BY (reading = ? OR kanji = ? OR name = ?) DESC, length(reading) LIMIT $limit""",
                arrayOf(b.id.toString(), n, n + "￿", t, t + "￿", t, t + "￿", n, t, t),
            )
        }
        return all.sortedWith(compareByDescending<Hit> { it.reading == n || it.heading.startsWith(t) }.thenBy { it.reading.length }.thenBy { it.book.level })
            .take(limit)
    }

    /** 本文 も ふくめて さがす（全文検索 が ある DB だけ） */
    fun searchText(q: String, keys: Set<String>, limit: Int = 60): List<Hit> {
        if (q.trim().length < 3) return emptyList()
        return books.filter { it.key in keys && it.source.fts }.flatMap { b ->
            hits(
                b,
                "SELECT e.id, e.heading, e.reading, e.grade FROM entry_fts f JOIN entry e ON e.id = f.rowid WHERE entry_fts MATCH ? AND e.dict_id = ? LIMIT $limit",
                arrayOf("\"" + q.trim().replace("\"", "") + "\"", b.id.toString()),
            )
        }.take(limit)
    }

    fun entry(ref: Ref): Entry? {
        val b = book(ref.key) ?: return null
        return runCatching {
            b.source.db.rawQuery("SELECT id, name, heading, reading, kanji, grade, body FROM entry WHERE id=?", arrayOf(ref.id.toString())).use { c ->
                if (!c.moveToFirst()) null
                else Entry(ref, b, c.getString(1), c.getString(2), c.getString(3).orEmpty(), c.getString(4), if (c.isNull(5)) null else c.getInt(5), c.getString(6).orEmpty())
            }
        }.getOrNull()
    }

    /** リンク [[名前]] を さがす（おなじ 辞書 → おなじ 保管庫 → ぜんぶ） */
    fun resolve(name: String, from: Entry?): Entry? {
        val order = buildList {
            from?.let { f -> add(f.book); books.filter { it.vault == f.book.vault && it != f.book }.forEach { add(it) } }
            books.filter { b -> none { it == b } }.forEach { add(it) }
        }
        for (b in order) {
            val id = runCatching {
                b.source.db.rawQuery("SELECT id FROM entry WHERE dict_id=? AND name=?", arrayOf(b.id.toString(), name)).use { if (it.moveToFirst()) it.getLong(0) else null }
            }.getOrNull()
            if (id != null) return entry(Ref(b.key, id))
        }
        return null
    }

    /** 図・外字・音（DB の 中 か、となり の フォルダ） */
    fun media(c: Context, book: Book, name: String): Pair<String, Any>? = runCatching {
        book.source.db.rawQuery("SELECT mime, data, file FROM media WHERE vault=? AND name=?", arrayOf(book.vault, name)).use { cu ->
            if (!cu.moveToFirst()) null
            else if (!cu.isNull(1)) cu.getString(0) to cu.getBlob(1)
            else cu.getString(2)?.let { rel ->
                val dir = book.source.file.parentFile
                val stem = book.source.file.name.removeSuffix(".sqlite")
                listOf(File(dir, "$stem-media/$rel"), File(dir, "kidshome-dict-media/$rel")).firstOrNull { it.exists() }?.let { cu.getString(0) to it }
            }
        }
    }.getOrNull()

    /** きょう の かんじ：その子 の 学年 までに ならう 漢字 から */
    fun kanjiOfDay(grade: Int, n: Int = 12): List<Hit> {
        val k = books.firstOrNull { it.code == "kokugo" } ?: return emptyList()
        val seed = (System.currentTimeMillis() / 86_400_000L) % 9973
        return hits(
            k,
            "SELECT id, heading, reading, grade FROM entry WHERE dict_id=? AND grade IS NOT NULL AND grade<=? ORDER BY (id * 7919 + ?) % 10007 LIMIT $n",
            arrayOf(k.id.toString(), grade.coerceIn(1, 7).toString(), seed.toString()),
        )
    }

    /** 漢字 1 字 の いみ（学年 つき の こくご 辞書 が あれば。もじ れんしゅう の よこ に 出す） */
    fun kanjiMeaning(ch: String, max: Int = 160): String? {
        val k = books.firstOrNull { it.code == "kokugo" } ?: return null
        val id = runCatching {
            k.source.db.rawQuery(
                "SELECT id FROM entry WHERE dict_id=? AND (heading=? OR kanji=?) ORDER BY grade IS NULL, length(body) DESC LIMIT 1",
                arrayOf(k.id.toString(), ch, ch),
            ).use { if (it.moveToFirst()) it.getLong(0) else null }
        }.getOrNull() ?: return null
        val e = entry(Ref(k.key, id)) ?: return null
        val t = DictText.plain(e.body, e.word).lines().map { it.trim() }.filter { it.isNotEmpty() }.joinToString("\n")
        return t.takeIf { it.isNotBlank() }?.let { if (it.length > max) it.take(max) + "…" else it }
    }

    /** ファイル を えらんで コピー（SD カード が あれば SD に。おなじ 名前 は いれかえ） */
    fun import(c: Context, uri: Uri) {
        val app = c.applicationContext
        importing = "コピー中…"
        scope.launch {
            val r = runCatching {
                withContext(Dispatchers.IO) {
                    val name = app.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { if (it.moveToFirst()) it.getString(0) else null }
                        ?.substringAfterLast('/')?.let { if (it.endsWith(".sqlite")) it else "$it.sqlite" } ?: FILE
                    val dir = target(app)
                    val tmp = File(dir, "$name.part")
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
                    val head = tmp.inputStream().use { val b = ByteArray(16); it.read(b); String(b, Charsets.US_ASCII) }
                    if (!head.startsWith("SQLite format 3")) { tmp.delete(); error("SQLite の ファイル では ありません") }
                    withContext(Dispatchers.Main) { close() }
                    val dest = File(dir, name)
                    dest.delete()
                    if (!tmp.renameTo(dest)) error("いれかえ できませんでした")
                }
            }
            importing = r.fold({ null }, { "コピー できませんでした（${it.message}）" })
            open(app)
        }
    }

    fun remove(c: Context, f: File) {
        close()
        f.delete()
        open(c)
    }
}
