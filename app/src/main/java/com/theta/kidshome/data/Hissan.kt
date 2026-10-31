package com.theta.kidshome.data

/**
 * ひっさん（たし算・ひき算・かけ算・わり算）の 書きかた を、ます目 の 表 に する。
 * 小学校 の 書きかた：くりあがり は 小さく 上 に、くりさがり は 数字 を 消して 上 に 小さく、
 * わり算 は 「わる数 ) わられる数」で 商 を 上 に。
 */
object Hissan {
    enum class Kind { NORMAL, SMALL, STRUCK, OP, ANSWER }

    data class Cell(val text: String, val kind: Kind = Kind.NORMAL)

    /** 1 行。rule：この 行 の 上 に 引く 線 の 列 の はんい */
    data class Row(val cells: List<Cell?>, val small: Boolean = false, val rule: IntRange? = null)

    data class Sheet(val cols: Int, val rows: List<Row>, val answer: String, val note: String? = null)

    private class Grid(val cols: Int) {
        val rows = mutableListOf<Row>()
        fun row(small: Boolean = false, rule: IntRange? = null, fill: (Array<Cell?>) -> Unit): Int {
            val a = arrayOfNulls<Cell>(cols); fill(a); rows += Row(a.toList(), small, rule); return rows.lastIndex
        }
        /** 右はし を end 列 に そろえて 数字 を 書く */
        fun put(a: Array<Cell?>, n: String, end: Int, kind: Kind = Kind.NORMAL) {
            n.forEachIndexed { i, ch -> val c = end - (n.length - 1 - i); if (c in 0 until cols) a[c] = Cell(ch.toString(), kind) }
        }
    }

    fun calc(a: Long, op: Char, b: Long): Sheet? = when (op) {
        '+' -> add(a, b)
        '-' -> sub(a, b)
        '×' -> mul(a, b)
        '÷' -> if (b == 0L) null else div(a, b)
        else -> null
    }

    fun add(a: Long, b: Long): Sheet {
        val r = a + b
        val sa = a.toString(); val sb = b.toString(); val sr = r.toString()
        val w = maxOf(maxOf(sa.length, sb.length) + 1, sr.length)
        val g = Grid(w)
        // くりあがり
        var carry = 0
        val carries = IntArray(w)
        for (i in 0 until w) {
            val da = sa.getOrNull(sa.length - 1 - i)?.digitToInt() ?: 0
            val db = sb.getOrNull(sb.length - 1 - i)?.digitToInt() ?: 0
            val s = da + db + carry
            carry = s / 10
            if (carry > 0 && i + 1 < w) carries[w - 2 - i] = carry
        }
        if (carries.any { it > 0 }) g.row(small = true) { row -> carries.forEachIndexed { c, v -> if (v > 0) row[c] = Cell("$v", Kind.SMALL) } }
        g.row { g.put(it, sa, w - 1) }
        g.row { it[0] = Cell("+", Kind.OP); g.put(it, sb, w - 1) }
        g.row(rule = 0 until w) { g.put(it, sr, w - 1, Kind.ANSWER) }
        return Sheet(w, g.rows, sr)
    }

    fun sub(a: Long, b: Long): Sheet {
        if (a < b) {
            val s = sub(b, a)
            return s.copy(answer = "-" + s.answer, note = "ひく かず の ほう が 大きい ので、$b − $a を して マイナス を つけます")
        }
        val r = a - b
        val sa = a.toString(); val sb = b.toString(); val sr = r.toString()
        val w = maxOf(sa.length, sb.length) + 1
        val g = Grid(w)
        val off = w - sa.length
        val digits = sa.map { it.digitToInt() }.toIntArray()
        val now = digits.copyOf()
        val changed = BooleanArray(sa.length)
        for (i in sa.indices.reversed()) {
            val db = sb.getOrNull(sb.length - (sa.length - i))?.digitToInt() ?: 0
            if (now[i] < db) {
                var k = i - 1
                while (k >= 0 && now[k] == 0) k--
                // k は かならず ある（a ≥ b）
                now[k]--; changed[k] = true
                for (j in k + 1 until i) { now[j] = 9; changed[j] = true }
                now[i] += 10; changed[i] = true
            }
        }
        if (changed.any { it }) g.row(small = true) { row -> sa.indices.forEach { i -> if (changed[i]) row[off + i] = Cell("${now[i]}", Kind.SMALL) } }
        g.row { row -> sa.forEachIndexed { i, ch -> row[off + i] = Cell(ch.toString(), if (changed[i]) Kind.STRUCK else Kind.NORMAL) } }
        g.row { it[0] = Cell("−", Kind.OP); g.put(it, sb, w - 1) }
        g.row(rule = 0 until w) { g.put(it, sr, w - 1, Kind.ANSWER) }
        return Sheet(w, g.rows, sr)
    }

    fun mul(a: Long, b: Long): Sheet {
        val r = a * b
        val sa = a.toString(); val sb = b.toString(); val sr = r.toString()
        val parts = sb.reversed().mapIndexed { j, d -> (a * d.digitToInt()).toString() to j }
        val w = maxOf(maxOf(sa.length, sb.length) + 1, sr.length, parts.maxOf { it.first.length + it.second })
        val g = Grid(w)
        g.row { g.put(it, sa, w - 1) }
        g.row { it[0] = Cell("×", Kind.OP); g.put(it, sb, w - 1) }
        if (sb.length == 1) {
            g.row(rule = 0 until w) { g.put(it, sr, w - 1, Kind.ANSWER) }
        } else {
            parts.forEachIndexed { k, (p, j) -> g.row(rule = if (k == 0) 0 until w else null) { g.put(it, p, w - 1 - j) } }
            g.row(rule = 0 until w) { g.put(it, sr, w - 1, Kind.ANSWER) }
        }
        return Sheet(w, g.rows, sr)
    }

    fun div(a: Long, b: Long): Sheet {
        val sa = a.toString(); val sb = b.toString()
        val off = sb.length + 1
        val w = off + sa.length
        val g = Grid(w)
        val quot = arrayOfNulls<Cell>(w)
        g.rows += Row(emptyList()) // 商 の 行（あと で 入れる）
        val work = g.row(rule = off until w) { row ->
            g.put(row, sb, sb.length - 1); row[sb.length] = Cell(")", Kind.OP); g.put(row, sa, w - 1)
        }
        var workRow = work
        var cur = 0L
        var started = false
        for (i in sa.indices) {
            val col = off + i
            cur = cur * 10 + sa[i].digitToInt()
            if (workRow != work) {
                // おろす
                val cells = g.rows[workRow].cells.toMutableList(); cells[col] = Cell(sa[i].toString())
                g.rows[workRow] = g.rows[workRow].copy(cells = cells)
            }
            val qd = cur / b
            if (!started && qd == 0L && i < sa.lastIndex) continue
            started = true
            quot[col] = Cell("$qd", Kind.ANSWER)
            if (qd == 0L) continue
            val prod = (qd * b).toString()
            g.row { g.put(it, prod, col) }
            cur -= qd * b
            val left = g.rows[workRow].cells.indexOfFirst { it != null }.coerceAtLeast(off)
            val start = minOf(left, col - prod.length + 1)
            workRow = g.row(rule = start..col) { row -> if (cur != 0L || i == sa.lastIndex) g.put(row, cur.toString(), col) }
        }
        g.rows[0] = Row(quot.toList())
        val q = a / b; val rem = a % b
        val ans = if (rem == 0L) "$q" else "$q あまり $rem"
        return Sheet(w, g.rows, ans)
    }
}
