package com.theta.kidshome.media

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/**
 * ループ：テンポ（BPM）・1 しょうせつ の はく・しょうせつ の かず を きめて、そのながさ を くりかえす。
 * 「どれを いつ 押したか」を 16 ぶんおんぷ の マス（step）で おぼえる（音そのものではない）。
 * ⏺ を押すと 1 しょうせつ カウント してから 録りはじめ、もう一度 ⏺ で 録りおわり（くりかえしは つづく）。
 * ドラム は マス を タップ しても 置ける。
 */
class Looper(
    private val scope: CoroutineScope,
    private val play: (Hit) -> Unit,
    private val click: (accent: Boolean) -> Unit,
) {
    /** 鳴らしたもの 1 つ。step は ループの 先頭からの 16 ぶん の かず（0 ≦ step < steps） */
    data class Hit(
        val step: Float, val pad: Int? = null, val kit: Kit? = null, val key: Int? = null, val voice: Voice? = null,
        val octave: Int = 0, val layer: Int = 0, val id: Long = 0,
    )

    companion object {
        const val STEPS_PER_BEAT = 4
        val BPMS = 50..200
        val BEATS = listOf(2, 3, 4, 6)
        val BARS = listOf(1, 2, 4, 8)
    }

    var bpm by mutableIntStateOf(96)
    var beats by mutableIntStateOf(4)
        private set
    var bars by mutableIntStateOf(2)
        private set
    var metronome by mutableStateOf(true)
    /** ぴったり マス に あわせる（16 ぶん） */
    var quantize by mutableStateOf(true)

    var playing by mutableStateOf(false)
        private set
    var recording by mutableStateOf(false)
        private set
    /** いまの ばしょ（step）。カウント中 は マイナス */
    var position by mutableFloatStateOf(0f)
        private set

    val hits = mutableStateListOf<Hit>()
    private var layer = 0
    private var nextId = 1L
    /** この 1 しゅう で 録った もの（すぐ もう一度 鳴らさない） */
    private val fresh = HashSet<Long>()
    private var job: Job? = null

    val steps get() = bars * beats * STEPS_PER_BEAT
    val stepsPerBar get() = beats * STEPS_PER_BEAT
    val countIn get() = position < 0

    fun changeBeats(b: Int) { beats = b; trim() }
    fun changeBars(b: Int) { bars = b; trim() }
    /** ループを みじかく したら はみだした ものは けす */
    private fun trim() {
        hits.removeAll { it.step >= steps }
        if (position >= steps) position = 0f
    }

    fun togglePlay() {
        if (playing) stop() else start(countIn = false)
    }

    fun stop() {
        job?.cancel()
        playing = false
        recording = false
        position = 0f
    }

    /** ⏺：とまっていたら カウント して 録りはじめ、録っていたら おわり、ながれていたら 録りはじめ */
    fun pressRecord() {
        when {
            recording -> recording = false
            playing -> { layer++; recording = true; fresh.clear() }
            else -> { layer++; recording = true; fresh.clear(); start(countIn = true) }
        }
    }

    /** いま 鳴らした ものを、録っている あいだ だけ おぼえる */
    fun record(hit: Hit) {
        if (!recording || !playing) return
        var p = position
        if (p < -0.5f) return
        if (p < 0) p = 0f
        val step = if (quantize) (p.roundToInt() % steps).toFloat() else p % steps
        val h = hit.copy(step = step, layer = layer, id = nextId++)
        // おなじ マス に おなじ もの が あれば かさねない
        if (hits.none { same(it, h) && it.step == h.step }) {
            hits += h
            fresh += h.id
        }
    }

    /** ドラム の マス を タップ：あれば けす、なければ おく */
    fun toggleStep(kit: Kit, pad: Int, step: Int): Boolean {
        val found = hits.firstOrNull { it.pad == pad && it.kit == kit && it.step.roundToInt() == step }
        return if (found != null) { hits.remove(found); false } else { hits += Hit(step.toFloat(), pad = pad, kit = kit, id = nextId++); true }
    }

    /** さいごに 録った ぶん を けす */
    fun undo() {
        val last = hits.maxOfOrNull { it.layer } ?: return
        if (last == 0) hits.removeAll { it.layer == 0 } else hits.removeAll { it.layer == last }
    }

    fun clear() {
        stop()
        hits.clear()
        layer = 0
    }

    private fun same(a: Hit, b: Hit) = a.pad == b.pad && a.kit == b.kit && a.key == b.key && a.voice == b.voice && a.octave == b.octave

    private fun start(countIn: Boolean) {
        job?.cancel()
        playing = true
        position = if (countIn) -stepsPerBar.toFloat() else 0f
        job = scope.launch {
            var last = System.nanoTime()
            // さいしょ の マス（カウント の 1 か、ループ の 0）
            val p0 = position
            tick(p0 - 0.0001f, p0 + 0.0001f)
            position = p0 + 0.0001f
            while (isActive) {
                delay(4)
                val now = System.nanoTime()
                val stepMs = 60_000.0 / bpm / STEPS_PER_BEAT
                val adv = ((now - last) / 1_000_000.0 / stepMs).toFloat()
                last = now
                val prev = position
                var cur = prev + adv
                if (cur >= steps) {
                    tick(prev, steps.toFloat())
                    cur -= steps
                    fresh.clear()
                    tick(-0.0001f, cur)
                } else tick(prev, cur)
                position = cur
            }
        }
    }

    /** prev ≦ step < cur に ある もの を 鳴らす（メトロノーム も） */
    private fun tick(prev: Float, cur: Float) {
        // はく の さかいめ（カウント中 は かならず 鳴らす）
        var b = kotlin.math.ceil(prev / STEPS_PER_BEAT).toInt()
        while (b * STEPS_PER_BEAT < cur) {
            val s = b * STEPS_PER_BEAT
            if (s >= prev && (metronome || s < 0)) click(Math.floorMod(s, stepsPerBar) == 0)
            b++
        }
        if (cur <= 0) return
        hits.filter { it.step >= maxOf(prev, 0f) && it.step < cur && it.id !in fresh }.forEach(play)
    }
}
