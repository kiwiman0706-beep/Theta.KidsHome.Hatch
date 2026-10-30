package com.theta.kidshome.media

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * かんたんループ：たたいた パッド・けんばん を覚えて、くりかえし鳴らす（音そのものではなく「どれを いつ押したか」）。
 * ⏺ 1 回目：録りはじめ → ⏺ 2 回目：そこまでの長さで ループ開始 → ⏺：かさねどり（もう一度 ⏺ で おわり）
 */
class Looper(private val scope: CoroutineScope, private val play: (Hit) -> Unit) {
    /** 鳴らしたもの 1 つ */
    data class Hit(val at: Long, val pad: Int? = null, val kit: Kit? = null, val key: Int? = null, val voice: Voice? = null, val octave: Int = 0)

    enum class Mode { EMPTY, FIRST, PLAYING, OVERDUB, PAUSED }

    var mode by mutableStateOf(Mode.EMPTY)
        private set
    /** ループのどこを鳴らしているか（0〜1） */
    var progress by mutableFloatStateOf(0f)
        private set

    private val hits = mutableListOf<Hit>()
    private var startedAt = 0L
    private var length = 0L
    private var job: Job? = null

    private fun now() = System.nanoTime() / 1_000_000

    /** 鳴らしたときに呼ぶ（録っている間だけ覚える） */
    fun record(hit: Hit) {
        when (mode) {
            Mode.FIRST -> hits += hit.copy(at = now() - startedAt)
            Mode.OVERDUB -> hits += hit.copy(at = (now() - startedAt) % length)
            else -> {}
        }
    }

    fun pressRecord() {
        when (mode) {
            Mode.EMPTY -> { hits.clear(); startedAt = now(); mode = Mode.FIRST }
            Mode.FIRST -> {
                length = (now() - startedAt).coerceAtLeast(500)
                startedAt = now()
                mode = Mode.PLAYING
                loop()
            }
            Mode.PLAYING -> mode = Mode.OVERDUB
            Mode.OVERDUB -> mode = Mode.PLAYING
            Mode.PAUSED -> { startedAt = now(); mode = Mode.OVERDUB; loop() }
        }
    }

    fun togglePlay() {
        when (mode) {
            Mode.PLAYING, Mode.OVERDUB -> { job?.cancel(); mode = Mode.PAUSED; progress = 0f }
            Mode.PAUSED -> { startedAt = now(); mode = Mode.PLAYING; loop() }
            else -> {}
        }
    }

    fun clear() {
        job?.cancel()
        hits.clear()
        mode = Mode.EMPTY
        progress = 0f
    }

    private fun loop() {
        job?.cancel()
        job = scope.launch {
            var last = -1L
            while (isActive) {
                val pos = (now() - startedAt) % length
                // 前回から今回までの間にある音を鳴らす（ループの終わりをまたぐときも）
                val due = if (pos >= last) hits.filter { it.at > last && it.at <= pos }
                else hits.filter { it.at > last || it.at <= pos }
                due.forEach(play)
                last = pos
                progress = pos.toFloat() / length
                delay(5)
            }
        }
    }
}
