package com.theta.kidshome.data

/**
 * 辞書の 本文（Markdown の 一部）を、表示用の かたまり に わける（Android に 依存しない）。
 * [[名前]] [[名前|表示]] リンク、![[x.png]] 図、![[x.png|14]] 外字、**強調**、<sub> <sup>、先頭 "- " の 入れ子。
 */
object DictText {
    sealed class Span {
        data class Plain(val text: String) : Span()
        data class Bold(val text: String) : Span()
        data class Link(val target: String, val label: String) : Span()
        /** 外字（行の 中に 小さく） */
        data class Gaiji(val name: String, val size: Int) : Span()
        data class Small(val text: String, val sup: Boolean) : Span()
        /** 行の 中の 図（大きさ 指定 なし）→ 表示では 行の あとに 出す */
        data class Media(val name: String) : Span()
    }

    sealed class Block {
        data class Para(val spans: List<Span>, val depth: Int = 0, val bullet: Boolean = false) : Block()
        data class Media(val name: String) : Block()
    }

    private val token = Regex("""!\[\[([^\]|]+)(?:\|(\d+))?\]\]|\[\[([^\]|]+)(?:\|([^\]]+))?\]\]|\*\*(.+?)\*\*|<sub>(.*?)</sub>|<sup>(.*?)</sup>""")
    private val mediaLine = Regex("""^\s*!\[\[([^\]|]+)\]\]\s*$""")

    fun spans(line: String): List<Span> {
        val out = mutableListOf<Span>()
        var i = 0
        for (m in token.findAll(line)) {
            if (m.range.first > i) out += Span.Plain(line.substring(i, m.range.first))
            val g = m.groups
            out += when {
                g[1] != null -> g[2]?.let { Span.Gaiji(g[1]!!.value, it.value.toInt()) } ?: Span.Media(g[1]!!.value)
                g[3] != null -> Span.Link(g[3]!!.value, g[4]?.value ?: displayName(g[3]!!.value))
                g[5] != null -> Span.Bold(g[5]!!.value)
                g[6] != null -> Span.Small(g[6]!!.value, sup = false)
                else -> Span.Small(g[7]!!.value, sup = true)
            }
            i = m.range.last + 1
        }
        if (i < line.length) out += Span.Plain(line.substring(i))
        return out
    }

    fun blocks(body: String): List<Block> {
        val out = mutableListOf<Block>()
        for (raw in body.split('\n')) {
            if (raw.isBlank()) continue
            val ml = mediaLine.find(raw)
            if (ml != null) { out += Block.Media(ml.groupValues[1]); continue }
            val lead = raw.length - raw.trimStart().length
            val t = raw.trimStart()
            val bullet = t.startsWith("- ")
            val sp = spans(if (bullet) t.substring(2) else t)
            out += Block.Para(sp.filter { it !is Span.Media }, depth = if (bullet) lead / 2 + 1 else 0, bullet = bullet)
            // 行の 中に あった 図 は、その 行の あと に
            sp.filterIsInstance<Span.Media>().forEach { out += Block.Media(it.name) }
        }
        return out
    }

    /** [[でん‐き【電気】]] の ように 表示名 が ないときは そのまま（ファイル名の (2) は とる） */
    fun displayName(name: String) = name.replace(Regex(""" \(\d+\)$"""), "")

    /** 見出し語の 代用記号（広辞苑 ―、研究社・コンサイス 〜）を 見出し に もどす */
    fun expand(text: String, word: String?): String =
        if (word.isNullOrBlank()) text else text.replace("―", word).replace("〜", word)

    /** 読み上げ・コピー用 の 文字だけ */
    fun plain(body: String, word: String?): String = blocks(body).filterIsInstance<Block.Para>().joinToString("\n") { p ->
        p.spans.joinToString("") { s ->
            when (s) {
                is Span.Plain -> expand(s.text, word)
                is Span.Bold -> s.text
                is Span.Link -> s.label
                is Span.Small -> s.text
                else -> ""
            }
        }
    }

    /** 検索の 入力 を そろえる（カタカナ → ひらがな、英字 は 小文字、空白・‐ を とる） */
    fun normalize(q: String): String = buildString {
        q.trim().lowercase().forEach { c ->
            when {
                c in 'ァ'..'ヶ' -> append(c - 0x60)
                c == ' ' || c == '　' || c == '‐' || c == '-' && length == 0 -> {}
                else -> append(c)
            }
        }
    }
}
