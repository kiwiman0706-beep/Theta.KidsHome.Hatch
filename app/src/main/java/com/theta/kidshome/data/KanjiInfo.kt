package com.theta.kidshome.data

import android.content.Context
import android.graphics.Path
import androidx.core.graphics.PathParser
import org.json.JSONObject

/**
 * もじ れんしゅう の よこ に 出す「かくすう・よみ・ことば・かきじゅん」。
 * assets/moji/kanji_info.json（tools/build_kanji_info.py で 作る。KANJIDIC2・JMdict は EDRDG、書き順 は KanjiVG、どちらも CC BY-SA）
 */
object KanjiInfo {
    data class Info(val strokes: Int?, val on: List<String>, val kun: List<String>, val words: List<Pair<String, String>>, val paths: List<String>)

    private var all: JSONObject? = null
    private val cache = HashMap<String, Info?>()
    private val pathCache = HashMap<String, List<Path>>()

    fun load(c: Context) {
        if (all != null) return
        all = runCatching { JSONObject(c.assets.open("moji/kanji_info.json").bufferedReader().readText()) }.getOrDefault(JSONObject())
    }

    fun get(ch: String): Info? = cache.getOrPut(ch) {
        val o = all?.optJSONObject(ch) ?: return@getOrPut null
        fun list(k: String) = o.optJSONArray(k)?.let { a -> (0 until a.length()).map { a.getString(it) } }.orEmpty()
        Info(
            strokes = if (o.has("s") && !o.isNull("s")) o.getInt("s") else null,
            on = list("on"), kun = list("kun"),
            words = o.optJSONArray("w")?.let { a -> (0 until a.length()).map { a.getJSONArray(it).let { w -> w.getString(0) to w.getString(1) } } }.orEmpty(),
            paths = list("p"),
        )
    }

    /** 書き順 の 線（109×109 の 座標） */
    fun paths(ch: String): List<Path> = pathCache.getOrPut(ch) {
        get(ch)?.paths?.mapNotNull { runCatching { PathParser.createPathFromPathData(it) }.getOrNull() }.orEmpty()
    }

    const val BOX = 109f
}
