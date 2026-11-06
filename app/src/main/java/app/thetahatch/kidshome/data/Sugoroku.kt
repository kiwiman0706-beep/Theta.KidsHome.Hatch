package app.thetahatch.kidshome.data

import kotlin.random.Random

/** 🎲 すごろく の きまり（画面 なし で テスト できる ぶぶん） */
object Sugoroku {
    enum class Kind(val emoji: String, val label: String) {
        NORMAL("", ""), QUIZ("❓", "もんだい"), GO("⏩", "2 すすむ"), BACK("🔙", "2 もどる"), REST("💤", "1 かい やすみ"), STAR("⭐", "ポイント")
    }

    const val SIZE = 30
    const val COLS = 6
    const val ROWS = 5

    /** マス を つくる（0 = スタート、SIZE-1 = ゴール） */
    fun board(seed: Int): List<Kind> {
        val r = Random(seed)
        return List(SIZE) { i ->
            when {
                i == 0 || i == SIZE - 1 -> Kind.NORMAL
                i <= 2 -> if (r.nextInt(3) == 0) Kind.QUIZ else Kind.NORMAL
                else -> when (r.nextInt(100)) {
                    in 0..44 -> Kind.NORMAL
                    in 45..66 -> Kind.QUIZ
                    in 67..76 -> Kind.GO
                    in 77..86 -> Kind.BACK
                    in 87..92 -> Kind.REST
                    else -> Kind.STAR
                }
            }
        }
    }

    /** マス の いち（ひだり した から へび の よう に）：col, row（0 が うえ） */
    fun cell(i: Int): Pair<Int, Int> {
        val rowFromBottom = i / COLS
        val col = if (rowFromBottom % 2 == 0) i % COLS else COLS - 1 - i % COLS
        return col to (ROWS - 1 - rowFromBottom)
    }

    /** すすむ・もどる（0〜ゴール に おさめる） */
    fun move(pos: Int, steps: Int) = (pos + steps).coerceIn(0, SIZE - 1)

    data class Quiz(val text: String, val answer: Int, val choices: List<Int>)

    /** level 0：10 まで の たしざん、1：20 まで の たしざん・ひきざん、2：くく */
    fun quiz(level: Int, r: Random = Random.Default): Quiz {
        val (text, ans) = when (level) {
            0 -> { val a = r.nextInt(1, 10); val b = r.nextInt(1, 11 - a); "$a ＋ $b" to a + b }
            1 -> if (r.nextBoolean()) { val a = r.nextInt(2, 19); val b = r.nextInt(1, 21 - a); "$a ＋ $b" to a + b }
                 else { val a = r.nextInt(5, 21); val b = r.nextInt(1, a); "$a － $b" to a - b }
            else -> { val a = r.nextInt(2, 10); val b = r.nextInt(2, 10); "$a × $b" to a * b }
        }
        val wrong = generateSequence { ans + r.nextInt(-5, 6) }.filter { it != ans && it >= 0 }.distinct().take(2).toList()
        return Quiz("$text ＝ ？", ans, (wrong + ans).shuffled(r))
    }
}
