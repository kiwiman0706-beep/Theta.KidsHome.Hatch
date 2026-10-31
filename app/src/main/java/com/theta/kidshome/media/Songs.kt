package com.theta.kidshome.media

/** れんしゅうの曲。notes はドレミで書く（下の parse を参照） */
data class Song(val title: String, val notes: String)

/** 1 つの音：けんばんの番号（0 = まんなかのド、24 = 2 つ上のド、-1 = やすみ）と長さ（はく。0.5 = 8分音符） */
data class Note(val key: Int, val beats: Float, val name: String) {
    val isRest get() = key < 0
    /** 表示用：ド ドー ド_ ド・ ん */
    val label get() = name + Songs.lengthMarks(beats)
}

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
     * - ド レ ミ ファ ソ ラ シ（カタカナ）。ひらがな（ど れ み ふぁ そ ら し）も可。ん は やすみ
     * - すぐ後ろに ＋ で 1 つ上のオクターブ（＋＋ で 2 つ上）、♯ か # で半音上
     * - ＿（_）で 半分（8分音符）、・ で 1.5 ばい（付点）、ー で 1 はく のばす
     * - 空白・、 は読みとばす
     */
    fun parse(text: String): List<Note> {
        val t = text.map { c -> if (c in 'ぁ'..'ゖ') c + 0x60 else c }.joinToString("")
            .replace('＋', '+').replace('＃', '#').replace('♯', '#').replace('＿', '_')
        val out = mutableListOf<Note>()
        var i = 0
        var base = 1f
        while (i < t.length) {
            val hit = names.firstOrNull { t.startsWith(it.first, i) }
            if (hit != null) {
                i += hit.first.length
                var key = hit.second
                var label = hit.first
                while (i < t.length && (t[i] == '+' || t[i] == '#')) {
                    if (t[i] == '+') { key += 12; label += "＋" } else { key += 1; label += "♯" }
                    i++
                }
                base = 1f
                if (key in 0..24) out += Note(key, 1f, label)
                continue
            }
            val last = out.lastOrNull()
            when {
                t[i] == 'ン' -> { base = 1f; out += Note(-1, 1f, "ん") }
                t[i] == '_' && last != null -> { base = 0.5f; out[out.lastIndex] = last.copy(beats = last.beats - 0.5f) }
                t[i] == '・' && last != null -> out[out.lastIndex] = last.copy(beats = last.beats + base / 2)
                (t[i] == 'ー' || t[i] == '〜') && last != null -> out[out.lastIndex] = last.copy(beats = last.beats + 1f)
            }
            i++
        }
        return out
    }

    /** 長さ の しるし（1 はく は なし）：0.5 → _、0.75 → _・、1.5 → ・、2 → ー、2.5 → ー・ */
    fun lengthMarks(beats: Float): String = when {
        beats < 1f -> "_" + if (beats >= 0.75f) "・" else ""
        else -> "ー".repeat(beats.toInt() - 1) + if (beats - beats.toInt() >= 0.5f) "・" else ""
    }

    private val keyNames = listOf("ド", "ド♯", "レ", "レ♯", "ミ", "ファ", "ファ♯", "ソ", "ソ♯", "ラ", "ラ♯", "シ")

    /** 音 を ドレミ書き に もどす（parse の はんたい）。key が -1 は やすみ。8 はく ごと に すきま */
    fun format(notes: List<Pair<Int, Float>>): String = buildString {
        var beat = 0f
        notes.forEach { (key, beats) ->
            if (beat >= 8f) { append(' '); beat = 0f }
            if (key < 0) append("ん") else append(keyNames[key % 12]).append("＋".repeat(key / 12))
            append(lengthMarks(beats))
            beat += beats
        }
    }

    /**
     * ひいた 時間 から 長さ を きめる（0.5 はく きざみ）。
     * downs：押した 時刻、ups：はなした 時刻（わからなければ null）、bpm：はやさ（null なら よく ある 間 を 1 はく に）。
     * つぎ の 音 まで に 0.5 はく 以上 はなして いれば、そこ は やすみ（ん）に する。
     */
    fun fromTaps(keys: List<Int>, downs: List<Long>, ups: List<Long?> = emptyList(), bpm: Int? = null): List<Pair<Int, Float>> {
        if (keys.isEmpty()) return emptyList()
        val gaps = downs.zipWithNext { a, b -> (b - a).coerceAtLeast(1) }
        val beatMs = bpm?.let { 60_000.0 / it }
            ?: gaps.sorted().let { if (it.isEmpty()) 500.0 else it[(it.size * 0.4).toInt().coerceAtMost(it.lastIndex)].toDouble() }.coerceAtLeast(120.0)
        fun halves(ms: Double) = kotlin.math.round(ms / beatMs * 2).toInt()
        val out = mutableListOf<Pair<Int, Float>>()
        keys.forEachIndexed { i, k ->
            val up = ups.getOrNull(i)
            val next = downs.getOrNull(i + 1)
            if (next == null) {
                val h = up?.let { halves((it - downs[i]).toDouble()) } ?: 2
                out += k to h.coerceIn(1, 8) / 2f
                return@forEachIndexed
            }
            val total = halves((next - downs[i]).toDouble()).coerceAtLeast(1)
            val held = up?.takeIf { it < next }?.let { halves((it - downs[i]).toDouble()).coerceAtLeast(1) }
            if (held != null && total - held >= 1) {
                out += k to held.coerceAtMost(8) / 2f
                out += -1 to (total - held).coerceAtMost(8) / 2f
            } else out += k to total.coerceAtMost(8) / 2f
        }
        return out
    }
}
