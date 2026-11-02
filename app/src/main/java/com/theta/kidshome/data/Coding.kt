package com.theta.kidshome.data

import android.content.Context
import org.json.JSONObject

/**
 * プログラミング「うごかして ゴール」：カード を ならべて ロボット を ゴール へ。
 *  じゅんばん（↑↓←→）→ くりかえし（🔁）→ かべ まで（⏩）→ もし かべ なら（❓）
 */
object Coding {
    enum class Dir(val dr: Int, val dc: Int, val arrow: String) {
        UP(-1, 0, "↑"), DOWN(1, 0, "↓"), LEFT(0, -1, "←"), RIGHT(0, 1, "→");
        fun next() = entries[(ordinal + 1) % entries.size]
    }

    sealed class Blk {
        abstract val id: Long
        data class Move(override val id: Long, val dir: Dir) : Blk()
        data class Repeat(override val id: Long, val times: Int, val body: List<Blk>) : Blk()
        /** かべ（はし）に あたる まで すすむ */
        data class Until(override val id: Long, val dir: Dir) : Blk()
        /** go に すすむ。かべ なら turn に すすむ */
        data class IfWall(override val id: Long, val go: Dir, val turn: Dir) : Blk()
    }

    /** カード の 数（くりかえし は 中身 も） */
    fun count(p: List<Blk>): Int = p.sumOf { if (it is Blk.Repeat) 1 + count(it.body) else 1 }

    data class Level(val map: List<String>, val cards: Set<String>, val best: Int, val hint: String, val intro: String?) {
        val rows get() = map.size
        val cols get() = map.maxOf { it.length }
        fun at(r: Int, c: Int): Char = map.getOrNull(r)?.getOrNull(c) ?: '#'
        fun free(r: Int, c: Int) = r in 0 until rows && c in 0 until cols && at(r, c) != '#'
        val start: Pair<Int, Int> get() = find('S')
        val goal: Pair<Int, Int> get() = find('G')
        private fun find(ch: Char): Pair<Int, Int> {
            map.forEachIndexed { r, row -> val c = row.indexOf(ch); if (c >= 0) return r to c }
            return 0 to 0
        }
    }

    enum class Outcome { GOAL, BUMP, SHORT, LONG }

    /** 1 コマ：いる ところ・うごかした カード・ぶつかった か */
    data class Frame(val r: Int, val c: Int, val block: Long, val bump: Boolean = false)

    data class Result(val frames: List<Frame>, val outcome: Outcome)

    private class Stop(val outcome: Outcome) : RuntimeException()

    fun run(level: Level, program: List<Blk>, maxFrames: Int = 300): Result {
        var (r, c) = level.start
        val frames = ArrayList<Frame>()
        fun move(d: Dir, id: Long) {
            val nr = r + d.dr; val nc = c + d.dc
            if (!level.free(nr, nc)) { frames += Frame(r, c, id, bump = true); throw Stop(Outcome.BUMP) }
            r = nr; c = nc
            frames += Frame(r, c, id)
            if (r to c == level.goal) throw Stop(Outcome.GOAL)
            if (frames.size >= maxFrames) throw Stop(Outcome.LONG)
        }
        fun exec(list: List<Blk>) {
            for (b in list) when (b) {
                is Blk.Move -> move(b.dir, b.id)
                is Blk.Repeat -> repeat(b.times) { exec(b.body) }
                is Blk.Until -> while (level.free(r + b.dir.dr, c + b.dir.dc)) move(b.dir, b.id)
                is Blk.IfWall -> move(if (level.free(r + b.go.dr, c + b.go.dc)) b.go else b.turn, b.id)
            }
        }
        return try { exec(program); Result(frames, Outcome.SHORT) } catch (s: Stop) { Result(frames, s.outcome) }
    }

    /** ほし：いちばん すくない カード なら 3 */
    fun stars(level: Level, program: List<Blk>): Int {
        val n = count(program)
        return when { n <= level.best -> 3; n <= level.best + 2 -> 2; else -> 1 }
    }

    // ---------------------------------------------------------------- ステージ（assets/content/coding.json）

    fun parse(json: String): List<Level> {
        val a = JSONObject(json).getJSONArray("levels")
        return (0 until a.length()).map { i ->
            val o = a.getJSONObject(i)
            Level(
                map = o.getJSONArray("map").let { m -> (0 until m.length()).map { m.getString(it) } },
                cards = o.getJSONArray("cards").let { m -> (0 until m.length()).map { m.getString(it) }.toSet() },
                best = o.getInt("best"),
                hint = o.optString("hint"),
                intro = o.optString("intro").takeIf { it.isNotBlank() },
            )
        }
    }

    private var levels: List<Level>? = null
    fun levels(c: Context): List<Level> = levels ?: runCatching { parse(c.assets.open("content/coding.json").bufferedReader().readText()) }.getOrDefault(emptyList()).also { levels = it }

    // ---------------------------------------------------------------- きろく

    private fun prefs(c: Context) = c.getSharedPreferences("coding", Context.MODE_PRIVATE)
    fun stars(c: Context, profile: String, i: Int) = prefs(c).getInt("$profile:$i", 0)
    fun save(c: Context, profile: String, i: Int, s: Int) { if (s > stars(c, profile, i)) prefs(c).edit().putInt("$profile:$i", s).apply() }
}
