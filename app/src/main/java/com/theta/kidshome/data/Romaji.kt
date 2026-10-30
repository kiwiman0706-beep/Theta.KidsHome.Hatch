package com.theta.kidshome.data

/**
 * ひらがな → ローマ字入力の判定（タイピング練習用）。
 * 「し」は shi／si、「つ」は tsu／tu、「ん」は nn／n（つぎが母音・や行・な行でなければ n 1 回でよい）、
 * 「っ」はつぎの子音を重ねる か xtu／ltu、など よくある書き方をぜんぶ受け付ける。
 */
object Romaji {
    private val table: Map<String, List<String>> = buildMap {
        fun p(k: String, vararg r: String) = put(k, r.toList())
        p("あ", "a"); p("い", "i"); p("う", "u", "wu"); p("え", "e"); p("お", "o")
        p("か", "ka", "ca"); p("き", "ki"); p("く", "ku", "cu"); p("け", "ke"); p("こ", "ko", "co")
        p("さ", "sa"); p("し", "shi", "si", "ci"); p("す", "su"); p("せ", "se", "ce"); p("そ", "so")
        p("た", "ta"); p("ち", "chi", "ti"); p("つ", "tsu", "tu"); p("て", "te"); p("と", "to")
        p("な", "na"); p("に", "ni"); p("ぬ", "nu"); p("ね", "ne"); p("の", "no")
        p("は", "ha"); p("ひ", "hi"); p("ふ", "fu", "hu"); p("へ", "he"); p("ほ", "ho")
        p("ま", "ma"); p("み", "mi"); p("む", "mu"); p("め", "me"); p("も", "mo")
        p("や", "ya"); p("ゆ", "yu"); p("よ", "yo")
        p("ら", "ra"); p("り", "ri"); p("る", "ru"); p("れ", "re"); p("ろ", "ro")
        p("わ", "wa"); p("を", "wo"); p("ん", "nn", "n'", "xn")
        p("が", "ga"); p("ぎ", "gi"); p("ぐ", "gu"); p("げ", "ge"); p("ご", "go")
        p("ざ", "za"); p("じ", "ji", "zi"); p("ず", "zu"); p("ぜ", "ze"); p("ぞ", "zo")
        p("だ", "da"); p("ぢ", "di"); p("づ", "du"); p("で", "de"); p("ど", "do")
        p("ば", "ba"); p("び", "bi"); p("ぶ", "bu"); p("べ", "be"); p("ぼ", "bo")
        p("ぱ", "pa"); p("ぴ", "pi"); p("ぷ", "pu"); p("ぺ", "pe"); p("ぽ", "po")
        p("ぁ", "xa", "la"); p("ぃ", "xi", "li"); p("ぅ", "xu", "lu"); p("ぇ", "xe", "le"); p("ぉ", "xo", "lo")
        p("ゃ", "xya", "lya"); p("ゅ", "xyu", "lyu"); p("ょ", "xyo", "lyo"); p("っ", "xtu", "ltu", "xtsu")
        p("きゃ", "kya"); p("きゅ", "kyu"); p("きょ", "kyo")
        p("しゃ", "sha", "sya"); p("しゅ", "shu", "syu"); p("しょ", "sho", "syo"); p("しぇ", "she", "sye")
        p("ちゃ", "cha", "tya", "cya"); p("ちゅ", "chu", "tyu", "cyu"); p("ちょ", "cho", "tyo", "cyo"); p("ちぇ", "che", "tye")
        p("にゃ", "nya"); p("にゅ", "nyu"); p("にょ", "nyo")
        p("ひゃ", "hya"); p("ひゅ", "hyu"); p("ひょ", "hyo")
        p("みゃ", "mya"); p("みゅ", "myu"); p("みょ", "myo")
        p("りゃ", "rya"); p("りゅ", "ryu"); p("りょ", "ryo")
        p("ぎゃ", "gya"); p("ぎゅ", "gyu"); p("ぎょ", "gyo")
        p("じゃ", "ja", "zya", "jya"); p("じゅ", "ju", "zyu", "jyu"); p("じょ", "jo", "zyo", "jyo"); p("じぇ", "je", "zye")
        p("びゃ", "bya"); p("びゅ", "byu"); p("びょ", "byo")
        p("ぴゃ", "pya"); p("ぴゅ", "pyu"); p("ぴょ", "pyo")
        p("ふぁ", "fa"); p("ふぃ", "fi"); p("ふぇ", "fe"); p("ふぉ", "fo"); p("てぃ", "thi"); p("でぃ", "dhi")
        p("ー", "-"); p("、", ","); p("。", "."); p("　", " "); p(" ", " ")
        p("？", "?"); p("！", "!")
        for (c in 'a'..'z') p(c.toString(), c.toString())
        for (c in '0'..'9') p(c.toString(), c.toString())
        p(";", ";")
    }

    /** ことばを、入力の ひとまとまり（ひらがな と 受け付けるローマ字）に分ける */
    data class Unit(val kana: String, val spellings: List<String>)

    fun units(word: String): List<Unit> {
        val out = mutableListOf<Unit>()
        var i = 0
        val w = word.map { if (it in 'ァ'..'ヶ') it - 0x60 else it }.joinToString("")
        while (i < w.length) {
            val two = if (i + 1 < w.length) w.substring(i, i + 2) else null
            val key = if (two != null && table.containsKey(two)) two else w[i].toString()
            out += Unit(key, table[key] ?: listOf(key))
            i += key.length
        }
        // 「ん」：つぎが 母音・や行・な行・おわり でなければ n 1 回でよい
        // 「っ」：つぎの子音を重ねる書き方も受け付ける
        for (k in out.indices) {
            val next = out.getOrNull(k + 1)
            if (out[k].kana == "ん" && next != null && next.spellings.none { it[0] in "aiueoyn'" }) {
                out[k] = out[k].copy(spellings = out[k].spellings + "n")
            }
            if (out[k].kana == "っ" && next != null) {
                val doubled = next.spellings.mapNotNull { s -> s.firstOrNull()?.takeIf { it !in "aiueon" }?.toString() }.distinct()
                out[k] = out[k].copy(spellings = doubled + out[k].spellings)
            }
        }
        return out
    }

    /** いちばんふつうの書き方（お手本として出す） */
    fun guide(units: List<Unit>) = units.joinToString("") { it.spellings.first() }
}

/**
 * 入力の すすみぐあい。1 文字ずつ type() に渡すと、合っていれば true。
 * 合っていない文字は受け付けず（すすまない）、まちがいとして数える。
 */
class TypingMatcher(val word: String) {
    val units = Romaji.units(word)
    var unitIndex = 0
        private set
    var buffer = ""
        private set
    /** 打ちおわった ローマ字（画面に出す） */
    var typed = ""
        private set
    val done get() = unitIndex >= units.size

    /** いまの お手本（打ちおわった分 ＋ のこりの いちばんふつうの書き方） */
    fun guide(): String {
        val cur = units.getOrNull(unitIndex) ?: return typed
        val restOfUnit = cur.spellings.firstOrNull { it.startsWith(buffer) }?.drop(buffer.length) ?: ""
        return typed + restOfUnit + units.drop(unitIndex + 1).joinToString("") { it.spellings.first() }
    }

    /** つぎに押すキー */
    fun nextKey(): Char? {
        val cur = units.getOrNull(unitIndex) ?: return null
        return cur.spellings.firstOrNull { it.startsWith(buffer) }?.getOrNull(buffer.length)
    }

    fun type(c: Char): Boolean {
        val ch = c.lowercaseChar()
        val cur = units.getOrNull(unitIndex) ?: return false
        val b = buffer + ch
        val candidates = cur.spellings.filter { it.startsWith(b) }
        if (candidates.isEmpty()) {
            // 「n」で ん を終えて、つぎの文字の 1 文字目だった場合
            if (buffer == "n" && cur.kana == "ん" && "n" in cur.spellings) {
                unitIndex++; typed += "n"; buffer = ""
                return type(c)
            }
            return false
        }
        buffer = b
        typed += ch
        // 書き方が決まったら（ほかに長い書き方が残っていなければ）つぎの文字へ
        if (b in candidates && candidates.all { it.length == b.length }) {
            unitIndex++
            buffer = ""
        }
        return true
    }
}
