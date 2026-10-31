package com.theta.kidshome.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.net.Uri
import android.util.LruCache
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import java.io.File

/**
 * にっぽん パズル の「あそび に いった ところ」の しゃしん。県 ごと に アプリ の 中 に 小さく して コピー する。
 * filesDir/japan/<県番号>/<時刻>.jpg
 */
object JapanPhotos {
    /** ふえた・へった とき に 画面 を かきなおす ため */
    var version by mutableIntStateOf(0)
        private set

    private fun dir(c: Context, code: Int) = File(c.filesDir, "japan/$code")

    fun list(c: Context, code: Int): List<File> =
        dir(c, code).listFiles { f -> f.name.endsWith(".jpg") }?.sortedBy { it.name }.orEmpty()

    /** しゃしん が ある 県 */
    fun visited(c: Context): Set<Int> =
        File(c.filesDir, "japan").listFiles()?.mapNotNull { d -> d.name.toIntOrNull()?.takeIf { list(c, it).isNotEmpty() } }?.toSet().orEmpty()

    /** えらんだ しゃしん を 長い 辺 1600 に して 入れる（よこ・たて の むき は そのまま）。入った 数 */
    fun add(c: Context, code: Int, uris: List<Uri>): Int {
        val d = dir(c, code).apply { mkdirs() }
        var n = 0
        uris.forEachIndexed { i, uri ->
            runCatching {
                val bmp = ImageDecoder.decodeBitmap(ImageDecoder.createSource(c.contentResolver, uri)) { dec, info, _ ->
                    dec.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                    val s = 1600f / maxOf(info.size.width, info.size.height)
                    if (s < 1f) dec.setTargetSize((info.size.width * s).toInt(), (info.size.height * s).toInt())
                }
                File(d, "${System.currentTimeMillis()}_$i.jpg").outputStream().use { bmp.compress(Bitmap.CompressFormat.JPEG, 88, it) }
                n++
            }
        }
        version++
        return n
    }

    fun delete(f: File) {
        f.delete(); thumbs.remove(f.path); version++
    }

    private val thumbs = object : LruCache<String, Bitmap>(16 * 1024 * 1024) {
        override fun sizeOf(key: String, value: Bitmap) = value.byteCount
    }

    fun thumb(f: File, size: Int = 360): Bitmap? = thumbs.get(f.path) ?: runCatching {
        val o = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(f.path, o)
        var s = 1
        while (maxOf(o.outWidth, o.outHeight) / (s * 2) >= size) s *= 2
        BitmapFactory.decodeFile(f.path, BitmapFactory.Options().apply { inSampleSize = s })
    }.getOrNull()?.also { thumbs.put(f.path, it) }

    fun full(f: File): Bitmap? = runCatching { BitmapFactory.decodeFile(f.path) }.getOrNull()
}
