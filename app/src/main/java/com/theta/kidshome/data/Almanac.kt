package com.theta.kidshome.data

import java.util.Calendar
import kotlin.math.floor

/**
 * こよみ：祝日・月 の ようす（むかし の よびかた・誕生石・誕生花・季節）・二十四節気・月 の 満ち欠け・時計 の 読みかた。
 * ぜんぶ 計算 か 表 で、ネット は つかわない。
 */
object Almanac {
    data class Holiday(val month: Int, val day: Int, val name: String, val kana: String)

    // ---------------------------------------------------------------- 祝日

    private fun nthMonday(y: Int, m: Int, n: Int): Int {
        val c = Calendar.getInstance().apply { clear(); set(y, m - 1, 1) }
        val first = (Calendar.MONDAY - c.get(Calendar.DAY_OF_WEEK) + 7) % 7 + 1
        return first + (n - 1) * 7
    }

    /** 春分 の 日・秋分 の 日（1980〜2099 年 の 式） */
    fun vernal(y: Int) = floor(20.8431 + 0.242194 * (y - 1980) - floor((y - 1980) / 4.0)).toInt()
    fun autumnal(y: Int) = floor(23.2488 + 0.242194 * (y - 1980) - floor((y - 1980) / 4.0)).toInt()

    /** その 年 の 祝日（振替休日・国民の休日 も） */
    fun holidays(y: Int): List<Holiday> {
        val base = listOf(
            Holiday(1, 1, "元日", "がんじつ"),
            Holiday(1, nthMonday(y, 1, 2), "成人の日", "せいじんのひ"),
            Holiday(2, 11, "建国記念の日", "けんこくきねんのひ"),
            Holiday(2, 23, "天皇誕生日", "てんのうたんじょうび"),
            Holiday(3, vernal(y), "春分の日", "しゅんぶんのひ"),
            Holiday(4, 29, "昭和の日", "しょうわのひ"),
            Holiday(5, 3, "憲法記念日", "けんぽうきねんび"),
            Holiday(5, 4, "みどりの日", "みどりのひ"),
            Holiday(5, 5, "こどもの日", "こどものひ"),
            Holiday(7, nthMonday(y, 7, 3), "海の日", "うみのひ"),
            Holiday(8, 11, "山の日", "やまのひ"),
            Holiday(9, nthMonday(y, 9, 3), "敬老の日", "けいろうのひ"),
            Holiday(9, autumnal(y), "秋分の日", "しゅうぶんのひ"),
            Holiday(10, nthMonday(y, 10, 2), "スポーツの日", "スポーツのひ"),
            Holiday(11, 3, "文化の日", "ぶんかのひ"),
            Holiday(11, 23, "勤労感謝の日", "きんろうかんしゃのひ"),
        )
        fun cal(h: Holiday) = Calendar.getInstance().apply { clear(); set(y, h.month - 1, h.day) }
        val days = base.associateBy { cal(it).get(Calendar.DAY_OF_YEAR) }.toMutableMap()
        // 国民の休日：祝日 と 祝日 に はさまれた 日
        base.map { cal(it).get(Calendar.DAY_OF_YEAR) }.forEach { d ->
            if (d + 2 in days && d + 1 !in days) {
                val c = Calendar.getInstance().apply { clear(); set(Calendar.YEAR, y); set(Calendar.DAY_OF_YEAR, d + 1) }
                if (c.get(Calendar.DAY_OF_WEEK) != Calendar.SUNDAY) days[d + 1] = Holiday(c.get(Calendar.MONTH) + 1, c.get(Calendar.DAY_OF_MONTH), "国民の休日", "こくみんのきゅうじつ")
            }
        }
        // 振替休日：日曜 の 祝日 の あと の、さいしょ の 祝日 で ない 日
        days.keys.sorted().forEach { d ->
            val c = Calendar.getInstance().apply { clear(); set(Calendar.YEAR, y); set(Calendar.DAY_OF_YEAR, d) }
            if (c.get(Calendar.DAY_OF_WEEK) == Calendar.SUNDAY) {
                var n = d + 1
                while (n in days) n++
                val s = Calendar.getInstance().apply { clear(); set(Calendar.YEAR, y); set(Calendar.DAY_OF_YEAR, n) }
                days[n] = Holiday(s.get(Calendar.MONTH) + 1, s.get(Calendar.DAY_OF_MONTH), "振替休日", "ふりかえきゅうじつ")
            }
        }
        return days.toSortedMap().values.toList()
    }

    private val cache = HashMap<Int, List<Holiday>>()
    fun holiday(y: Int, m: Int, d: Int): Holiday? = cache.getOrPut(y) { holidays(y) }.firstOrNull { it.month == m && it.day == d }

    // ---------------------------------------------------------------- 月 の ようす

    data class Month(
        val old: String, val oldKana: String,
        val stone: String, val stoneColor: Long,
        val flower: String, val flowerEmoji: String,
        val season: String,
    )

    val MONTHS = listOf(
        Month("睦月", "むつき", "ガーネット", 0xFFA1203A, "すいせん", "🌼", "いちばん さむい ころ。お正月 に 雪あそび、たこあげ。"),
        Month("如月", "きさらぎ", "アメジスト", 0xFF8E5CC2, "うめ", "🌸", "せつぶん で 豆まき。うめ が さきはじめる。"),
        Month("弥生", "やよい", "アクアマリン", 0xFF6FC9D6, "もも", "🍑", "ひなまつり。だんだん あたたかく なる。つくし が でる。"),
        Month("卯月", "うづき", "ダイヤモンド", 0xFFB8D8F0, "さくら", "🌸", "さくら が さいて 入学式。おたまじゃくし が およぐ。"),
        Month("皐月", "さつき", "エメラルド", 0xFF2E9E6A, "すずらん", "💐", "こどもの日 に こいのぼり。みどり が きれい。"),
        Month("水無月", "みなづき", "しんじゅ", 0xFFE8E2D4, "あじさい", "💠", "つゆ で 雨 が おおい。かたつむり と あじさい。"),
        Month("文月", "ふみづき", "ルビー", 0xFFD0204A, "ゆり", "🪷", "七夕。つゆ が あけて なつ やすみ。せみ が なく。"),
        Month("葉月", "はづき", "ペリドット", 0xFF9CC23A, "ひまわり", "🌻", "いちばん あつい ころ。はなび・おぼん・すいかわり。"),
        Month("長月", "ながつき", "サファイア", 0xFF2A4FB0, "コスモス", "🌸", "おつきみ。すずむし が なく。すこし すずしく なる。"),
        Month("神無月", "かんなづき", "オパール", 0xFFE7D7E8, "きんもくせい", "🧡", "あき の みのり。いもほり・どんぐり・ハロウィン。"),
        Month("霜月", "しもつき", "トパーズ", 0xFFF2A93B, "きく", "🌼", "もみじ が あかく なる。七五三。"),
        Month("師走", "しわす", "ターコイズ", 0xFF2EB7B0, "ポインセチア", "🌺", "ふゆ。クリスマス と おおみそか。いちねん の おわり。"),
    )

    /** 二十四節気（だいたい の 日。年 で 1 日 くらい ずれる） */
    val SEKKI = listOf(
        Triple(1, 6, "小寒" to "しょうかん"), Triple(1, 20, "大寒" to "だいかん"), Triple(2, 4, "立春" to "りっしゅん"), Triple(2, 19, "雨水" to "うすい"),
        Triple(3, 6, "啓蟄" to "けいちつ"), Triple(3, 21, "春分" to "しゅんぶん"), Triple(4, 5, "清明" to "せいめい"), Triple(4, 20, "穀雨" to "こくう"),
        Triple(5, 6, "立夏" to "りっか"), Triple(5, 21, "小満" to "しょうまん"), Triple(6, 6, "芒種" to "ぼうしゅ"), Triple(6, 21, "夏至" to "げし"),
        Triple(7, 7, "小暑" to "しょうしょ"), Triple(7, 23, "大暑" to "たいしょ"), Triple(8, 7, "立秋" to "りっしゅう"), Triple(8, 23, "処暑" to "しょしょ"),
        Triple(9, 8, "白露" to "はくろ"), Triple(9, 23, "秋分" to "しゅうぶん"), Triple(10, 8, "寒露" to "かんろ"), Triple(10, 23, "霜降" to "そうこう"),
        Triple(11, 7, "立冬" to "りっとう"), Triple(11, 22, "小雪" to "しょうせつ"), Triple(12, 7, "大雪" to "たいせつ"), Triple(12, 22, "冬至" to "とうじ"),
    )

    /** いま の 節気（その 日 から つぎ の 節気 まで） */
    fun sekki(m: Int, d: Int): Pair<String, String> {
        val k = m * 100 + d
        return (SEKKI.lastOrNull { it.first * 100 + it.second <= k } ?: SEKKI.last()).third
    }

    fun season(m: Int) = when (m) { 3, 4, 5 -> "はる" to "🌸"; 6, 7, 8 -> "なつ" to "🌻"; 9, 10, 11 -> "あき" to "🍁"; else -> "ふゆ" to "⛄" }

    // ---------------------------------------------------------------- 干支

    data class Eto(val kanji: String, val kana: String, val animal: String, val emoji: String)

    val ETO = listOf(
        Eto("子", "ね", "ねずみ", "🐭"), Eto("丑", "うし", "うし", "🐮"), Eto("寅", "とら", "とら", "🐯"), Eto("卯", "う", "うさぎ", "🐰"),
        Eto("辰", "たつ", "りゅう", "🐲"), Eto("巳", "み", "へび", "🐍"), Eto("午", "うま", "うま", "🐴"), Eto("未", "ひつじ", "ひつじ", "🐑"),
        Eto("申", "さる", "さる", "🐵"), Eto("酉", "とり", "にわとり", "🐔"), Eto("戌", "いぬ", "いぬ", "🐶"), Eto("亥", "い", "いのしし", "🐗"),
    )

    /** 2020 年 は 子（ねずみ） */
    fun eto(y: Int) = ETO[((y - 2020) % 12 + 12) % 12]

    // ---------------------------------------------------------------- 月 の 満ち欠け

    private const val SYNODIC = 29.530588853
    /** 2000-01-06 18:14 UTC（新月） */
    private const val NEW_MOON_MS = 947_182_440_000L

    /** 月齢（0〜29.5） */
    fun moonAge(ms: Long): Double {
        val d = (ms - NEW_MOON_MS) / 86_400_000.0
        return ((d % SYNODIC) + SYNODIC) % SYNODIC
    }

    data class Moon(val emoji: String, val name: String, val age: Double, val toFull: Int, val toNew: Int)

    fun moon(ms: Long): Moon {
        val a = moonAge(ms)
        val (e, n) = when {
            a < 1.85 -> "🌑" to "しんげつ"
            a < 5.54 -> "🌒" to "みかづき"
            a < 9.23 -> "🌓" to "じょうげん の つき"
            a < 12.92 -> "🌔" to "じゅうさんや"
            a < 16.61 -> "🌕" to "まんげつ"
            a < 20.30 -> "🌖" to "ねまちづき"
            a < 23.99 -> "🌗" to "かげん の つき"
            a < 27.68 -> "🌘" to "ありあけ の つき"
            else -> "🌑" to "しんげつ"
        }
        val full = SYNODIC / 2
        val toFull = (((full - a) % SYNODIC + SYNODIC) % SYNODIC).let { Math.round(it).toInt() }
        val toNew = Math.round(SYNODIC - a).toInt() % 30
        return Moon(e, n, a, toFull, toNew)
    }

    // ---------------------------------------------------------------- 時計 の 読みかた

    private val HOURS = listOf("じゅうにじ", "いちじ", "にじ", "さんじ", "よじ", "ごじ", "ろくじ", "しちじ", "はちじ", "くじ", "じゅうじ", "じゅういちじ", "じゅうにじ")
    private val ONES = listOf("", "いっぷん", "にふん", "さんぷん", "よんぷん", "ごふん", "ろっぷん", "ななふん", "はっぷん", "きゅうふん")
    private val TENS = listOf("", "じゅう", "にじゅう", "さんじゅう", "よんじゅう", "ごじゅう")
    private val TENS_EXACT = listOf("", "じゅっぷん", "にじゅっぷん", "さんじゅっぷん", "よんじゅっぷん", "ごじゅっぷん")

    fun readMinute(m: Int): String = when {
        m == 0 -> "ちょうど"
        m % 10 == 0 -> TENS_EXACT[m / 10]
        else -> TENS[m / 10] + ONES[m % 10]
    }

    /** 3:15 → さんじ じゅうごふん、7:30 → しちじ さんじゅっぷん（しちじ はん） */
    fun readTime(h: Int, m: Int): String {
        val hh = HOURS[h % 12]
        val base = if (m == 0) "$hh ちょうど" else "$hh ${readMinute(m)}"
        return if (m == 30) "$base（$hh はん）" else base
    }
}
