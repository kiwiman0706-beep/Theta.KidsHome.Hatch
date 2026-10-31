package com.theta.kidshome.media

/** れんしゅうの曲。notes はドレミで書く（下の parse を参照） */
data class Song(val title: String, val notes: String)

/** 1 つの音：けんばんの番号（0 = まんなかのド、24 = 2 つ上のド）と長さ（はく） */
data class Note(val key: Int, val beats: Int, val name: String)

object Songs {
    /** 作者の没後 70 年以上たった曲・むかしの民謡だけ（著作権の心配がないもの） */
    val builtIn = listOf(
        Song("きらきらぼし", "ドドソソララソー ファファミミレレドー ソソファファミミレー ソソファファミミレー ドドソソララソー ファファミミレレドー"),
        Song("メリーさんのひつじ", "ミレドレミミミー レレレー ミソソー ミレドレミミミー レレミレドー"),
        Song("かえるのうた", "ドレミファミレドー ミファソラソファミー ドー ドー ドー ドー ドドレレミミファファミーレードー"),
        Song("ちょうちょう", "ソミミー ファレレー ドレミファソソソー ソミミミファレレレ ドミソソミミミー"),
        Song("よろこびのうた", "ミミファソ ソファミレ ドドレミ ミーレレー ミミファソ ソファミレ ドドレミ レードドー"),
        Song("ロンドンばし", "ソーラソファミファソー レミファー ミファソー ソーラソファミファソー レーソーミドー"),
    )

    private val names = listOf("ド" to 0, "レ" to 2, "ミ" to 4, "ファ" to 5, "ソ" to 7, "ラ" to 9, "シ" to 11)

    /**
     * ドレミ書きを音にする。
     * - ド レ ミ ファ ソ ラ シ（カタカナ）。ひらがな（ど れ み ふぁ そ ら し）も可
     * - すぐ後ろに ＋ で 1 つ上のオクターブ（＋＋ で 2 つ上）、♯ か # で半音上
     * - ー で 1 はく のばす。空白・、・ は読みとばす
     */
    fun parse(text: String): List<Note> {
        val t = text.map { c -> if (c in 'ぁ'..'ゖ') c + 0x60 else c }.joinToString("")
            .replace('＋', '+').replace('＃', '#').replace('♯', '#')
        val out = mutableListOf<Note>()
        var i = 0
        while (i < t.length) {
            val hit = names.firstOrNull { t.startsWith(it.first, i) }
            if (hit != null) {
                i += hit.first.length
                var key = hit.second
                var label = hit.first
                while (i < t.length && (t[i] == '+' || t[i] == '#')) {
                    if (t[i] == '+') key += 12 else { key += 1; label += "♯" }
                    i++
                }
                if (key in 0..24) out += Note(key, 1, label)
                continue
            }
            if ((t[i] == 'ー' || t[i] == '〜') && out.isNotEmpty()) {
                val last = out.removeAt(out.lastIndex)
                out += last.copy(beats = last.beats + 1)
            }
            i++
        }
        return out
    }

    private val keyNames = listOf("ド", "ド♯", "レ", "レ♯", "ミ", "ファ", "ファ♯", "ソ", "ソ♯", "ラ", "ラ♯", "シ")

    /** 音 を ドレミ書き に もどす（parse の はんたい）。8 はく ごと に すきま */
    fun format(notes: List<Pair<Int, Int>>): String = buildString {
        var beat = 0
        notes.forEach { (key, beats) ->
            if (beat >= 8) { append(' '); beat = 0 }
            append(keyNames[key % 12]).append("＋".repeat(key / 12)).append("ー".repeat((beats - 1).coerceAtLeast(0)))
            beat += beats
        }
    }

    /**
     * ひいた 時間 から 長さ（はく）を きめる：よく ある 間（ま）を 1 はく に して、2・3・4 はく に まるめる。
     * keys と times（ミリ秒）は おなじ かず。さいご の 音 は 1 はく（つぎ が ないので）
     */
    fun fromTaps(keys: List<Int>, times: List<Long>): List<Pair<Int, Int>> {
        if (keys.isEmpty()) return emptyList()
        val gaps = times.zipWithNext { a, b -> (b - a).coerceAtLeast(1) }
        // みじかい ほう から 4 わり の ところ を 1 はく に（はやい 音 が 多い 曲 でも くずれにくい）
        val unit = gaps.sorted().let { if (it.isEmpty()) 1L else it[(it.size * 0.4).toInt().coerceAtMost(it.lastIndex)] }.coerceAtLeast(120)
        return keys.mapIndexed { i, k ->
            val beats = gaps.getOrNull(i)?.let { g -> (g.toDouble() / unit).let { kotlin.math.round(it).toInt() }.coerceIn(1, 4) } ?: 1
            k to beats
        }
    }
}
