package app.thetahatch.kidshome.data

import android.Manifest
import android.content.ContentUris
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.util.LruCache
import android.util.Size
import org.json.JSONObject
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class MediaKind { VIDEO, MUSIC, BOOK }

/** べんきょう の シリーズ（KidsHome/どうが・おんがく・おはなし の すぐ 下） */
const val STUDY_SHELF = "★べんきょう"

/** 端末（本体・SD カード）の動画・音楽・オーディオブック 1 つ */
data class MediaEntry(
    val id: Long,
    val uri: Uri,
    val kind: MediaKind,
    val title: String,
    /** 入っているフォルダの名前（動画は「たな」、音楽はアルバムの代わりにも使う） */
    val folder: String,
    val album: String,
    val artist: String,
    val durationMs: Long,
    val track: Int = 0,
    /** ひだり の 一覧 の まとまり（KidsHome/どうが/<ここ>/…。そう で なければ folder） */
    val shelf: String = folder,
    /** SD・本体 の なか の 場所（Movies/…/、とちゅう の フォルダ で ならべる） */
    val path: String = "",
    /** ひだり の 一覧 の フォルダ の 場所（アイコン を さがす） */
    val shelfPath: String = path,
) {
    /** どうが・おんがく・おはなし の すぐ 下 の「★べんきょう」の 中：ふつう の シリーズ と して 出し、どうが ロック でも 見られる */
    val study: Boolean get() = shelf == STUDY_SHELF || path.split('/').any { it == STUDY_SHELF }
}

/**
 * 端末の動画・音楽を MediaStore から読む。SD カードも含む（Android 10 以降の「外部ストレージ」全体）。
 * オーディオブック：MediaStore の IS_AUDIOBOOK、拡張子 .m4b、フォルダ名に audiobook／オーディオブック／おはなし。
 */
object MediaLibrary {
    var videos by mutableStateOf<List<MediaEntry>>(emptyList())
        private set
    var music by mutableStateOf<List<MediaEntry>>(emptyList())
        private set
    var books by mutableStateOf<List<MediaEntry>>(emptyList())
        private set
    private var loading = false
    private var loadedAt = 0L
    private val scope = MainScope()

    val permissions: Array<String>
        get() = if (Build.VERSION.SDK_INT >= 33) {
            arrayOf(Manifest.permission.READ_MEDIA_VIDEO, Manifest.permission.READ_MEDIA_AUDIO)
        } else {
            arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
        }

    fun hasPermission(context: Context) =
        permissions.all { context.checkSelfPermission(it) == PackageManager.PERMISSION_GRANTED }

    /** フォルダ アイコン（folder.jpg など）を 読む ため の 画像 の 許可 */
    val imagePermission: String
        get() = if (Build.VERSION.SDK_INT >= 33) Manifest.permission.READ_MEDIA_IMAGES else Manifest.permission.READ_EXTERNAL_STORAGE

    fun hasImagePermission(context: Context) = context.checkSelfPermission(imagePermission) == PackageManager.PERMISSION_GRANTED

    /** フォルダ の 場所（RELATIVE_PATH）→ その フォルダ の アイコン 画像 */
    var icons by mutableStateOf<kotlin.collections.Map<String, Uri>>(emptyMap())
        private set

    /** アイコン に する ファイル名（上 ほど 優先） */
    val ICON_NAMES = listOf("folder.png", "folder.jpg", "folder.jpeg", "folder.webp", "icon.png", "icon.jpg", "icon.jpeg", "icon.webp", "cover.png", "cover.jpg", "cover.jpeg", "cover.webp")

    fun iconFor(path: String): Uri? = icons[path]

    // ---------------------------------------------------------------- 📁 えらんだ フォルダ（ファミリーリンク でも 読める）

    private fun mediaPrefs(c: Context) = c.getSharedPreferences("media", Context.MODE_PRIVATE)

    /** 親モード で えらんだ SD の フォルダ（KidsHome か SD の いちばん うえ）。メディア の 権限 が いらない */
    fun treeUri(c: Context): Uri? = mediaPrefs(c).getString("tree", null)?.let(Uri::parse)
        ?.takeIf { u -> c.contentResolver.persistedUriPermissions.any { it.uri == u && it.isReadPermission } }

    fun setTree(c: Context, uri: Uri?) {
        val old = mediaPrefs(c).getString("tree", null)?.let(Uri::parse)
        if (old != null && old != uri) runCatching { c.contentResolver.releasePersistableUriPermission(old, android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION) }
        if (uri != null) c.contentResolver.takePersistableUriPermission(uri, android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
        mediaPrefs(c).edit().putString("tree", uri?.toString()).apply()
        refresh(c, force = true)
    }

    /** 読める か（フォルダ を えらんだ か、メディア の 権限 が ある か） */
    fun canRead(c: Context) = treeUri(c) != null || hasPermission(c)

    private val VIDEO_EXT = setOf("mp4", "m4v", "mkv", "webm", "3gp", "mov", "ts")
    private val AUDIO_EXT = setOf("mp3", "m4a", "aac", "ogg", "oga", "opus", "flac", "wav", "m4b")

    private class Scan(
        val videos: List<MediaEntry>, val audio: List<MediaEntry>, val icons: kotlin.collections.Map<String, Uri>,
        /** ファイル の uri → パソコン で 作った サムネイル（.kidshome/thumbs） */
        val thumbs: kotlin.collections.Map<String, Uri>,
        /** 長さ が まだ わからない もの（uri と おぼえる key） */
        val missing: List<Pair<Uri, String>>,
    )

    /** パソコン で 作った サムネイル（ファイル の uri → 画像 の uri） */
    @Volatile private var filling = false
    @Volatile private var pcThumbs: kotlin.collections.Map<String, Uri> = emptyMap()

    private fun durPrefs(c: Context) = c.getSharedPreferences("mediadur", Context.MODE_PRIVATE)

    /**
     * えらんだ フォルダ を たどって 動画・音声・アイコン を あつめる。
     * 長さ は .kidshome/index.json（パソコン の tools/make_kidshome_cache.py）→ まえ に しらべた もの → わからない ものは あと で（missing）。
     */
    private fun scanTree(c: Context, tree: Uri): Scan {
        val cr = c.contentResolver
        val cols = arrayOf(
            android.provider.DocumentsContract.Document.COLUMN_DOCUMENT_ID, android.provider.DocumentsContract.Document.COLUMN_DISPLAY_NAME,
            android.provider.DocumentsContract.Document.COLUMN_MIME_TYPE, android.provider.DocumentsContract.Document.COLUMN_SIZE,
        )
        fun list(docId: String): List<Array<Any?>> {
            val rows = ArrayList<Array<Any?>>()
            cr.query(android.provider.DocumentsContract.buildChildDocumentsUriUsingTree(tree, docId), cols, null, null, null)?.use { q ->
                while (q.moveToNext()) rows += arrayOf(q.getString(0), q.getString(1), q.getString(2), q.getLong(3))
            }
            return rows.sortedBy { it[1] as String? }
        }
        val isDir = { mime: String -> mime == android.provider.DocumentsContract.Document.MIME_TYPE_DIR }
        val dur = durPrefs(c)
        val videos = ArrayList<MediaEntry>(); val audio = ArrayList<MediaEntry>()
        val icons = HashMap<String, Pair<Int, Uri>>()
        val thumbs = HashMap<String, Uri>(); val missing = ArrayList<Pair<Uri, String>>()
        // パソコン で 作った キャッシュ：base（KidsHome の 場所）から の 相対パス → 大きさ・長さ・サムネイル名
        var cacheBase: String? = null
        var index: JSONObject? = null
        val thumbFiles = HashMap<String, Uri>()

        fun readCache(docId: String, base: String) {
            list(docId).forEach { r ->
                val id = r[0] as String; val name = (r[1] as String?).orEmpty()
                when {
                    name == "index.json" -> runCatching {
                        index = cr.openInputStream(android.provider.DocumentsContract.buildDocumentUriUsingTree(tree, id))!!
                            .use { JSONObject(it.bufferedReader().readText()) }.optJSONObject("files")
                        cacheBase = base
                    }
                    name == "thumbs" && isDir((r[2] as String?).orEmpty()) -> list(id).forEach { t ->
                        thumbFiles[(t[1] as String?).orEmpty()] = android.provider.DocumentsContract.buildDocumentUriUsingTree(tree, t[0] as String)
                    }
                }
            }
        }

        val rootId = android.provider.DocumentsContract.getTreeDocumentId(tree)
        val rootName = cr.query(android.provider.DocumentsContract.buildDocumentUriUsingTree(tree, rootId), arrayOf(cols[1]), null, null, null)
            ?.use { if (it.moveToFirst()) it.getString(0) else null } ?: "KidsHome"
        fun walk(docId: String, path: String, depth: Int) {
            if (depth > 8) return
            val folder = path.trimEnd('/').substringAfterLast('/')
            list(docId).forEach { r ->
                val id = r[0] as String; val name = (r[1] as String?).orEmpty(); val mime = (r[2] as String?).orEmpty(); val size = r[3] as Long
                if (name == ".kidshome" && isDir(mime)) { readCache(id, path); return@forEach }
                if (name.startsWith(".")) return@forEach
                if (isDir(mime)) { walk(id, "$path$name/", depth + 1); return@forEach }
                val uri = android.provider.DocumentsContract.buildDocumentUriUsingTree(tree, id)
                val lower = name.lowercase(); val ext = lower.substringAfterLast('.', "")
                val rank = ICON_NAMES.indexOf(lower)
                if (rank >= 0) { val cur = icons[path]; if (cur == null || rank < cur.first) icons[path] = rank to uri; return@forEach }
                if (ext !in VIDEO_EXT && ext !in AUDIO_EXT) return@forEach
                // 長さ：パソコン の キャッシュ（大きさ が おなじ とき）→ まえ に しらべた もの → あと で
                val key = "$id:$size"
                val rel = cacheBase?.let { b -> (path + name).takeIf { it.startsWith(b) }?.removePrefix(b) }
                val pc = rel?.let { index?.optJSONObject(it) }?.takeIf { it.optLong("size", -1) == size }
                pc?.optString("thumb")?.takeIf { it.isNotBlank() }?.let { t -> thumbFiles[t]?.let { thumbs[uri.toString()] = it } }
                val ms = pc?.optLong("dur", -1)?.takeIf { it >= 0 } ?: dur.getLong(key, -1L).takeIf { it >= 0 }
                if (ms == null) missing += uri to key
                val stable = java.util.UUID.nameUUIDFromBytes(id.toByteArray()).mostSignificantBits
                val title = clean(name.substringBeforeLast('.'))
                if (ext in VIDEO_EXT) videos += MediaEntry(
                    id = stable, uri = uri, kind = MediaKind.VIDEO, title = title, folder = folder.ifEmpty { "どうが" }, album = "", artist = "",
                    durationMs = ms ?: 0L, shelf = shelfOf(path) ?: folder.ifEmpty { "どうが" }, path = path, shelfPath = shelfPathOf(path),
                ) else {
                    val isBook = ext == "m4b" || isBookFolder(folder) || isBookPath(path)
                    audio += MediaEntry(
                        id = stable, uri = uri, kind = if (isBook) MediaKind.BOOK else MediaKind.MUSIC, title = title,
                        folder = folder.ifEmpty { "おんがく" }, album = folder.ifEmpty { "おんがく" }, artist = "",
                        durationMs = ms ?: 0L, track = Regex("^(\\d{1,3})").find(name)?.groupValues?.get(1)?.toIntOrNull() ?: 0,
                        shelf = shelfOf(path) ?: folder.ifEmpty { "おんがく" }, path = path, shelfPath = shelfPathOf(path),
                    )
                }
            }
        }
        walk(rootId, "$rootName/", 0)
        return Scan(
            videos.sortedWith(compareBy({ it.shelf }, { it.path })),
            audio.sortedWith(compareBy({ it.shelf }, { it.album }, { it.track }, { it.title })),
            icons.mapValues { it.value.second }, thumbs, missing,
        )
    }

    /** 長さ が わからない もの を うら で しらべて、わかった ぶん から 一覧 に 入れる */
    private suspend fun fillDurations(c: Context, missing: List<Pair<Uri, String>>) {
        val dur = durPrefs(c)
        val found = HashMap<String, Long>()
        suspend fun publish() {
            if (found.isEmpty()) return
            val f = HashMap(found); found.clear()
            withContext(Dispatchers.Main) {
                fun upd(l: List<MediaEntry>) = l.map { e -> f[e.uri.toString()]?.let { e.copy(durationMs = it) } ?: e }
                videos = upd(videos); music = upd(music); books = upd(books)
            }
        }
        missing.forEachIndexed { i, (uri, key) ->
            val d = withContext(Dispatchers.IO) {
                runCatching {
                    android.media.MediaMetadataRetriever().run {
                        try { setDataSource(c, uri); extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L } finally { release() }
                    }
                }.getOrDefault(0L)
            }
            dur.edit().putLong(key, d).apply()
            found[uri.toString()] = d
            if (i % 10 == 9) publish()
        }
        publish()
    }

    /** 5 分に 1 回まで読み直す（新しく入れた動画を拾うため） */
    fun refresh(context: Context, force: Boolean = false) {
        if (loading || !canRead(context)) return
        if (!force && System.currentTimeMillis() - loadedAt < 5 * 60_000) return
        loading = true
        val app = context.applicationContext
        scope.launch {
            val tree = treeUri(app)
            var missing: List<Pair<Uri, String>> = emptyList()
            if (tree != null) runCatching {
                val sc = withContext(Dispatchers.IO) { scanTree(app, tree) }
                icons = sc.icons; pcThumbs = sc.thumbs; videos = sc.videos
                music = sc.audio.filter { it.kind == MediaKind.MUSIC }
                books = sc.audio.filter { it.kind == MediaKind.BOOK }
                missing = sc.missing
            } else runCatching {
                val v = withContext(Dispatchers.IO) { queryVideos(app) }
                val a = withContext(Dispatchers.IO) { queryAudio(app) }
                icons = if (hasImagePermission(app)) withContext(Dispatchers.IO) { runCatching { queryIcons(app) }.getOrDefault(emptyMap()) } else emptyMap()
                videos = v
                music = a.filter { it.kind == MediaKind.MUSIC }
                books = a.filter { it.kind == MediaKind.BOOK }
            }
            loadedAt = System.currentTimeMillis()
            loading = false
            // 一覧 は もう 出て いる。長さ は うら で
            if (missing.isNotEmpty() && !filling) { filling = true; runCatching { fillDurations(app, missing) }; filling = false }
        }
    }

    private fun queryIcons(context: Context): kotlin.collections.Map<String, Uri> {
        val base = MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
        val cols = arrayOf(MediaStore.Images.Media._ID, MediaStore.Images.Media.DISPLAY_NAME, MediaStore.Images.Media.RELATIVE_PATH)
        val sel = "lower(${MediaStore.Images.Media.DISPLAY_NAME}) IN (" + ICON_NAMES.joinToString(",") { "?" } + ")"
        val found = mutableListOf<Triple<String, Int, Uri>>()
        context.contentResolver.query(base, cols, sel, ICON_NAMES.toTypedArray(), null)?.use { c ->
            while (c.moveToNext()) {
                val rank = ICON_NAMES.indexOf(c.getString(1).orEmpty().lowercase())
                found += Triple(c.getString(2).orEmpty(), rank, ContentUris.withAppendedId(base, c.getLong(0)))
            }
        }
        return found.groupBy { it.first }.mapValues { (_, l) -> l.minBy { it.second }.third }
    }

    private fun queryVideos(context: Context): List<MediaEntry> {
        val base = MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
        val cols = arrayOf(
            MediaStore.Video.Media._ID, MediaStore.Video.Media.TITLE, MediaStore.Video.Media.DISPLAY_NAME,
            MediaStore.Video.Media.BUCKET_DISPLAY_NAME, MediaStore.Video.Media.DURATION, MediaStore.Video.Media.RELATIVE_PATH,
        )
        val out = mutableListOf<MediaEntry>()
        context.contentResolver.query(base, cols, null, null, "${MediaStore.Video.Media.DISPLAY_NAME} ASC")?.use { c ->
            while (c.moveToNext()) {
                val id = c.getLong(0)
                val name = c.getString(2).orEmpty()
                out += MediaEntry(
                    id = id, uri = ContentUris.withAppendedId(base, id), kind = MediaKind.VIDEO,
                    title = clean(c.getString(1)?.takeIf { it.isNotBlank() } ?: name.substringBeforeLast('.')),
                    folder = c.getString(3) ?: "どうが", album = "", artist = "", durationMs = c.getLong(4),
                    shelf = shelfOf(c.getString(5).orEmpty()) ?: c.getString(3) ?: "どうが", path = c.getString(5).orEmpty(),
                    shelfPath = shelfPathOf(c.getString(5).orEmpty()),
                )
            }
        }
        // ならび は ファイル名 の じゅん（DISPLAY_NAME で もらった じゅん を くずさない）
        return out.sortedWith(compareBy({ it.shelf }, { it.path }))
    }

    private fun queryAudio(context: Context): List<MediaEntry> {
        val base = MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
        val cols = arrayOf(
            MediaStore.Audio.Media._ID, MediaStore.Audio.Media.TITLE, MediaStore.Audio.Media.DISPLAY_NAME,
            MediaStore.Audio.Media.BUCKET_DISPLAY_NAME, MediaStore.Audio.Media.DURATION, MediaStore.Audio.Media.ALBUM,
            MediaStore.Audio.Media.ARTIST, MediaStore.Audio.Media.TRACK, MediaStore.Audio.Media.IS_MUSIC,
            MediaStore.Audio.Media.IS_AUDIOBOOK, MediaStore.Audio.Media.RELATIVE_PATH,
        )
        val out = mutableListOf<MediaEntry>()
        context.contentResolver.query(base, cols, null, null, "${MediaStore.Audio.Media.DISPLAY_NAME} ASC")?.use { c ->
            while (c.moveToNext()) {
                val id = c.getLong(0)
                val name = c.getString(2).orEmpty()
                val folder = c.getString(3).orEmpty()
                val duration = c.getLong(4)
                val path = c.getString(10).orEmpty()
                val isBook = c.getInt(9) == 1 || name.lowercase().endsWith(".m4b") || isBookFolder(folder) || isBookPath(path)
                val isMusic = c.getInt(8) == 1
                // 着信音・通知音・短い効果音は出さない
                if (!isBook && (!isMusic || duration in 1 until 30_000)) continue
                out += MediaEntry(
                    id = id, uri = ContentUris.withAppendedId(base, id),
                    kind = if (isBook) MediaKind.BOOK else MediaKind.MUSIC,
                    title = clean(c.getString(1)?.takeIf { it.isNotBlank() } ?: name.substringBeforeLast('.')),
                    folder = folder.ifEmpty { "おんがく" },
                    album = c.getString(5)?.takeIf { it.isNotBlank() && it != "<unknown>" } ?: folder.ifEmpty { "おんがく" },
                    artist = c.getString(6)?.takeIf { it != "<unknown>" }.orEmpty(),
                    durationMs = duration,
                    track = c.getInt(7) % 1000,
                    shelf = shelfOf(path) ?: c.getString(6)?.takeIf { it.isNotBlank() && it != "<unknown>" } ?: folder.ifEmpty { "おんがく" },
                    path = path,
                    shelfPath = shelfPathOf(path),
                )
            }
        }
        return out.sortedWith(compareBy({ it.shelf }, { it.album }, { it.track }))
    }

    /** SD の きまり：KidsHome/どうが・おんがく・おはなし の すぐ 下 の フォルダ が ひだり の 一覧 */
    private val ROOTS = setOf("どうが", "おんがく", "おはなし", "videos", "music", "audiobooks", "books")

    /** 「01 アンパンマン」「03_かえるのうた」の 先頭 の ばんごう は ならび用 なので 見せない */
    fun clean(title: String): String = title.replace(Regex("^\\d{1,3}[ 　._\\-]+"), "").ifBlank { title }

    fun shelfOf(path: String): String? {
        val seg = path.split('/').filter { it.isNotBlank() }
        val i = seg.indexOfFirst { it.lowercase() in ROOTS }
        return seg.getOrNull(i + 1)?.takeIf { i >= 0 }
    }

    /** KidsHome/どうが/アンパンマン/シーズン1/ → KidsHome/どうが/アンパンマン/（きまり の 形 で なければ その まま） */
    fun shelfPathOf(path: String): String {
        val seg = path.split('/').filter { it.isNotBlank() }
        val i = seg.indexOfFirst { it.lowercase() in ROOTS }
        return if (i >= 0 && i + 1 < seg.size) seg.take(i + 2).joinToString("/") + "/" else path
    }

    private fun isBookPath(path: String) = path.split('/').any { it == "おはなし" || it.equals("audiobooks", true) }

    fun isBookFolder(folder: String): Boolean {
        val f = folder.lowercase()
        return f.contains("audiobook") || f.contains("オーディオブック") || f.contains("おはなし") || f.contains("朗読")
    }

    // ---------------------------------------------------------------- サムネイル

    private val thumbs = object : LruCache<String, Bitmap>(24 * 1024 * 1024) {
        override fun sizeOf(key: String, value: Bitmap) = value.byteCount
    }
    private val missing = HashSet<String>()

    /** 動画のコマ・音楽のジャケット。無ければ null（主スレッドで呼ばない） */
    fun thumbnail(context: Context, uri: Uri, w: Int = 384, h: Int = 216): Bitmap? {
        val key = "$uri@$w"
        thumbs.get(key)?.let { return it }
        if (key in missing) return null
        // パソコン で 作った サムネイル が あれば それ
        pcThumbs[uri.toString()]?.let { t -> fileThumb(context, t, w, h)?.let { thumbs.put(key, it); return it } }
        val b = runCatching { context.contentResolver.loadThumbnail(uri, Size(w, h), null) }.getOrNull()
            ?: if (uri.authority != MediaStore.AUTHORITY) fileThumb(context, uri, w, h) else null
        if (b != null) thumbs.put(key, b) else missing += key
        return b
    }
}

/** えらんだ フォルダ の ファイル（document の uri）の サムネイル：画像 は ちぢめて、動画 は 1 コマ、音声 は 埋め込み ジャケット */
private fun fileThumb(context: Context, uri: Uri, w: Int, h: Int): Bitmap? = runCatching {
    val name = uri.lastPathSegment.orEmpty().lowercase()
    if (name.endsWith(".jpg") || name.endsWith(".jpeg") || name.endsWith(".png") || name.endsWith(".webp")) {
        return@runCatching android.graphics.ImageDecoder.decodeBitmap(android.graphics.ImageDecoder.createSource(context.contentResolver, uri)) { d, info, _ ->
            val s = maxOf(info.size.width / w.toFloat(), info.size.height / h.toFloat(), 1f)
            d.setTargetSize((info.size.width / s).toInt().coerceAtLeast(1), (info.size.height / s).toInt().coerceAtLeast(1))
            d.allocator = android.graphics.ImageDecoder.ALLOCATOR_SOFTWARE
        }
    }
    android.media.MediaMetadataRetriever().run {
        try {
            setDataSource(context, uri)
            embeddedPicture?.let { bytes ->
                BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.let { Bitmap.createScaledBitmap(it, w, (w * it.height / it.width.coerceAtLeast(1)).coerceAtLeast(1), true) }
            } ?: (if (Build.VERSION.SDK_INT >= 27) getScaledFrameAtTime(3_000_000, android.media.MediaMetadataRetriever.OPTION_CLOSEST_SYNC, w, h) else getFrameAtTime(3_000_000))
        } finally { release() }
    }
}.getOrNull()

/** 続きから再生するための、再生位置の記録 */
object Positions {
    private fun prefs(context: Context) = context.getSharedPreferences("positions", Context.MODE_PRIVATE)

    fun get(context: Context, uri: Uri): Long = prefs(context).getLong(uri.toString(), 0L)

    fun set(context: Context, uri: Uri, positionMs: Long, durationMs: Long) {
        // 最後まで見た（聞いた）ら、次は最初から
        val v = if (durationMs > 0 && positionMs > durationMs - 15_000) 0L else positionMs
        prefs(context).edit().putLong(uri.toString(), v).apply()
    }

    fun progress(context: Context, e: MediaEntry): Float =
        if (e.durationMs <= 0) 0f else (get(context, e.uri).toFloat() / e.durationMs).coerceIn(0f, 1f)
}
