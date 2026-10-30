package com.theta.kidshome.data

import android.Manifest
import android.content.ContentUris
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.util.LruCache
import android.util.Size
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class MediaKind { VIDEO, MUSIC, BOOK }

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
)

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

    /** 5 分に 1 回まで読み直す（新しく入れた動画を拾うため） */
    fun refresh(context: Context, force: Boolean = false) {
        if (loading || !hasPermission(context)) return
        if (!force && System.currentTimeMillis() - loadedAt < 5 * 60_000) return
        loading = true
        val app = context.applicationContext
        scope.launch {
            runCatching {
                val v = withContext(Dispatchers.IO) { queryVideos(app) }
                val a = withContext(Dispatchers.IO) { queryAudio(app) }
                videos = v
                music = a.filter { it.kind == MediaKind.MUSIC }
                books = a.filter { it.kind == MediaKind.BOOK }
            }
            loadedAt = System.currentTimeMillis()
            loading = false
        }
    }

    private fun queryVideos(context: Context): List<MediaEntry> {
        val base = MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
        val cols = arrayOf(
            MediaStore.Video.Media._ID, MediaStore.Video.Media.TITLE, MediaStore.Video.Media.DISPLAY_NAME,
            MediaStore.Video.Media.BUCKET_DISPLAY_NAME, MediaStore.Video.Media.DURATION,
        )
        val out = mutableListOf<MediaEntry>()
        context.contentResolver.query(base, cols, null, null, "${MediaStore.Video.Media.DISPLAY_NAME} ASC")?.use { c ->
            while (c.moveToNext()) {
                val id = c.getLong(0)
                val name = c.getString(2).orEmpty()
                out += MediaEntry(
                    id = id, uri = ContentUris.withAppendedId(base, id), kind = MediaKind.VIDEO,
                    title = c.getString(1)?.takeIf { it.isNotBlank() } ?: name.substringBeforeLast('.'),
                    folder = c.getString(3) ?: "どうが", album = "", artist = "", durationMs = c.getLong(4),
                )
            }
        }
        return out
    }

    private fun queryAudio(context: Context): List<MediaEntry> {
        val base = MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
        val cols = arrayOf(
            MediaStore.Audio.Media._ID, MediaStore.Audio.Media.TITLE, MediaStore.Audio.Media.DISPLAY_NAME,
            MediaStore.Audio.Media.BUCKET_DISPLAY_NAME, MediaStore.Audio.Media.DURATION, MediaStore.Audio.Media.ALBUM,
            MediaStore.Audio.Media.ARTIST, MediaStore.Audio.Media.TRACK, MediaStore.Audio.Media.IS_MUSIC,
            MediaStore.Audio.Media.IS_AUDIOBOOK,
        )
        val out = mutableListOf<MediaEntry>()
        context.contentResolver.query(base, cols, null, null, null)?.use { c ->
            while (c.moveToNext()) {
                val id = c.getLong(0)
                val name = c.getString(2).orEmpty()
                val folder = c.getString(3).orEmpty()
                val duration = c.getLong(4)
                val isBook = c.getInt(9) == 1 || name.lowercase().endsWith(".m4b") || isBookFolder(folder)
                val isMusic = c.getInt(8) == 1
                // 着信音・通知音・短い効果音は出さない
                if (!isBook && (!isMusic || duration in 1 until 30_000)) continue
                out += MediaEntry(
                    id = id, uri = ContentUris.withAppendedId(base, id),
                    kind = if (isBook) MediaKind.BOOK else MediaKind.MUSIC,
                    title = c.getString(1)?.takeIf { it.isNotBlank() } ?: name.substringBeforeLast('.'),
                    folder = folder.ifEmpty { "おんがく" },
                    album = c.getString(5)?.takeIf { it.isNotBlank() && it != "<unknown>" } ?: folder.ifEmpty { "おんがく" },
                    artist = c.getString(6)?.takeIf { it != "<unknown>" }.orEmpty(),
                    durationMs = duration,
                    track = c.getInt(7) % 1000,
                )
            }
        }
        return out.sortedWith(compareBy({ it.album }, { it.track }, { it.title }))
    }

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
        val b = runCatching { context.contentResolver.loadThumbnail(uri, Size(w, h), null) }.getOrNull()
        if (b != null) thumbs.put(key, b) else missing += key
        return b
    }
}

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
