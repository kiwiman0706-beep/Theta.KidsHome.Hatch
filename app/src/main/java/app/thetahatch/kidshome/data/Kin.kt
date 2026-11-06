package app.thetahatch.kidshome.data

/**
 * かけいず（家系図）と ともだち の たんじょうび。
 * Kin は 計算 だけ（Android に よらない）。ほぞん は KinStore。
 */
data class Person(
    val id: String,
    val name: String,
    val kana: String = "",
    /** "m" おとこ・"f" おんな・"" わからない */
    val sex: String = "",
    val birthYear: Int = 0,
    val birthMonth: Int = 0,
    val birthDay: Int = 0,
    val father: String? = null,
    val mother: String? = null,
    val spouse: String? = null,
    /** ともだち（家系図 には 入れない、たんじょうび だけ） */
    val friend: Boolean = false,
    /** この 子（プロフィール）本人 */
    val profileId: String? = null,
) {
    val hasBirthday get() = birthMonth in 1..12 && birthDay in 1..31
    val male get() = sex == "m"
    val female get() = sex == "f"
    /** よびかた（かな が あれば かな） */
    val call get() = kana.ifBlank { name }
}

object Kin {
    data class Relation(
        /** 1 ことば で（おばさん・いとこ …）。わからない とき は null */
        val term: String?,
        /** たどりかた（わたし の おとうさん の いもうと） */
        val chain: String,
    ) {
        val text get() = term ?: chain
    }

    private enum class Step { P, C, S }

    private fun byId(people: List<Person>) = people.associateBy { it.id }

    private fun neighbors(p: Person, all: Map<String, Person>): List<Pair<Step, Person>> = buildList {
        listOfNotNull(p.father, p.mother).mapNotNull { all[it] }.forEach { add(Step.P to it) }
        all.values.filter { it.father == p.id || it.mother == p.id }.forEach { add(Step.C to it) }
        val sp = p.spouse?.let { all[it] } ?: all.values.firstOrNull { it.spouse == p.id }
        sp?.let { add(Step.S to it) }
    }

    /** いちばん みじかい たどりかた（P 親・C 子・S つれあい） */
    private fun path(people: List<Person>, from: String, to: String): List<Pair<Step, Person>>? {
        val all = byId(people)
        val start = all[from] ?: return null
        if (from == to) return emptyList()
        val prev = HashMap<String, Pair<String, Step>>()
        val queue = ArrayDeque(listOf(start))
        val seen = hashSetOf(from)
        while (queue.isNotEmpty()) {
            val cur = queue.removeFirst()
            for ((step, n) in neighbors(cur, all).sortedBy { it.first.ordinal }) {
                if (!seen.add(n.id)) continue
                prev[n.id] = cur.id to step
                if (n.id == to) {
                    val out = ArrayList<Pair<Step, Person>>()
                    var id = to
                    while (id != from) { val (p, s) = prev.getValue(id); out += s to all.getValue(id); id = p }
                    return out.reversed()
                }
                queue += n
            }
        }
        return null
    }

    /** a は b より 年上？（わからない とき null） */
    private fun older(a: Person, b: Person): Boolean? {
        if (a.birthYear == 0 || b.birthYear == 0) return null
        val ka = a.birthYear * 10000 + a.birthMonth * 100 + a.birthDay
        val kb = b.birthYear * 10000 + b.birthMonth * 100 + b.birthDay
        return if (ka == kb) null else ka < kb
    }

    private fun mf(p: Person, m: String, f: String, any: String) = when { p.male -> m; p.female -> f; else -> any }

    /** 兄弟姉妹：base から 見た who */
    private fun sibling(base: Person, who: Person): String = when (older(who, base)) {
        true -> mf(who, "おにいさん", "おねえさん", "きょうだい")
        false -> mf(who, "おとうと", "いもうと", "きょうだい")
        null -> "きょうだい"
    }

    /** from から 見る と to は なに？ */
    fun relation(people: List<Person>, from: String, to: String): Relation? {
        val all = byId(people)
        val me = all[from] ?: return null
        val steps = path(people, from, to) ?: return null
        if (steps.isEmpty()) return Relation("じぶん", "じぶん")
        val t = steps.last().second
        val sig = steps.joinToString("") { it.first.name }
        val term = when (sig) {
            "P" -> mf(t, "おとうさん", "おかあさん", "おや")
            "PP" -> mf(t, "おじいさん", "おばあさん", "そふぼ")
            "PPP" -> mf(t, "ひいおじいさん", "ひいおばあさん", "ひいそふぼ")
            "C" -> mf(t, "むすこ", "むすめ", "こども")
            "CC" -> "まご"
            "CCC" -> "ひまご"
            "PC" -> sibling(me, t)
            "PPC", "PPCS" -> mf(t, "おじさん", "おばさん", "おじさん・おばさん")
            "PPPC" -> mf(t, "おおおじさん", "おおおばさん", "おおおじ・おおおば")
            "PPCC" -> "いとこ"
            "PCC" -> mf(t, "おい", "めい", "おい・めい")
            "S" -> mf(t, "だんなさん", "おくさん", "つれあい")
            "SP" -> "ぎり の " + mf(t, "おとうさん", "おかあさん", "おや")
            "SPC" -> "ぎり の " + sibling(all[steps[0].second.id] ?: me, t)
            "PCS" -> "ぎり の " + mf(t, "おにいさん・おとうと", "おねえさん・いもうと", "きょうだい")
            "CS" -> mf(t, "むこ（むすめ の だんなさん）", "よめ（むすこ の おくさん）", "こども の つれあい")
            else -> null
        }
        return Relation(term, chain(me, steps))
    }

    /** たどりかた の 文：おや → こ は きょうだい に まとめる */
    private fun chain(me: Person, steps: List<Pair<Step, Person>>): String {
        val words = ArrayList<String>()
        var base = me
        var i = 0
        while (i < steps.size) {
            val (s, p) = steps[i]
            if (s == Step.P && i + 1 < steps.size && steps[i + 1].first == Step.C) {
                val sib = steps[i + 1].second
                words += sibling(base, sib); base = sib; i += 2; continue
            }
            words += when (s) {
                Step.P -> mf(p, "おとうさん", "おかあさん", "おや")
                Step.C -> mf(p, "むすこ", "むすめ", "こども")
                Step.S -> mf(p, "だんなさん", "おくさん", "つれあい")
            }
            base = p; i++
        }
        return (listOf(me.call) + words).joinToString(" の ")
    }

    // ---------------------------------------------------------------- たんじょうび

    fun age(p: Person, y: Int, m: Int, d: Int): Int? = when {
        p.birthYear == 0 -> null
        !p.hasBirthday -> y - p.birthYear // 月日 が わからない とき は だいたい
        else -> y - p.birthYear - (if (m * 100 + d < p.birthMonth * 100 + p.birthDay) 1 else 0)
    }

    /** つぎ の たんじょうび まで の 日数（今日 なら 0） */
    fun daysUntil(p: Person, today: java.util.Calendar): Int? {
        if (!p.hasBirthday) return null
        val t = (today.clone() as java.util.Calendar).apply {
            set(java.util.Calendar.HOUR_OF_DAY, 0); set(java.util.Calendar.MINUTE, 0); set(java.util.Calendar.SECOND, 0); set(java.util.Calendar.MILLISECOND, 0)
        }
        val b = (t.clone() as java.util.Calendar).apply { set(java.util.Calendar.MONTH, p.birthMonth - 1); set(java.util.Calendar.DAY_OF_MONTH, p.birthDay) }
        if (b.before(t)) b.add(java.util.Calendar.YEAR, 1)
        return ((b.timeInMillis - t.timeInMillis + 3_600_000L) / 86_400_000L).toInt()
    }

    // ---------------------------------------------------------------- ならべかた

    /** 世代（基準 の 人 = 0、親 は -1、子 は +1）。つながって いない 人 は null */
    fun generations(people: List<Person>, root: String): Map<String, Int> {
        val all = byId(people)
        val gen = HashMap<String, Int>()
        val start = all[root] ?: return gen
        gen[root] = 0
        val q = ArrayDeque(listOf(start))
        while (q.isNotEmpty()) {
            val cur = q.removeFirst()
            for ((s, n) in neighbors(cur, all)) {
                if (n.id in gen) continue
                gen[n.id] = gen.getValue(cur.id) + when (s) { Step.P -> -1; Step.C -> 1; Step.S -> 0 }
                q += n
            }
        }
        return gen
    }
}
