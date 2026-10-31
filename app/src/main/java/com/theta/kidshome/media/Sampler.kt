package com.theta.kidshome.media

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.setValue
import java.io.File
import kotlin.math.abs

/**
 * サンプラー：マイクの音を録って、パッドに割り当てる（「じぶんの おと」のセット）。
 * パッドを押している間だけ録音（いちばん長くて 4 びょう）。最初の無音は切って、音の大きさをそろえる。
 */
object Sampler {
    const val MAX_SECONDS = 4
    private val main by lazy { android.os.Handler(android.os.Looper.getMainLooper()) }

    /** いま の 音 の 大きさ（0〜1）と、録った びょう（画面 に 出す） */
    var level by mutableFloatStateOf(0f)
        private set
    var elapsed by mutableFloatStateOf(0f)
        private set

    fun file(context: Context, pad: Int) = File(File(context.filesDir, "samples").apply { mkdirs() }, "pad_$pad.wav")

    fun hasPermission(context: Context) =
        context.checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED

    @Volatile private var recording = false
    private var thread: Thread? = null

    val isRecording get() = recording

    @SuppressLint("MissingPermission")
    fun start(context: Context, pad: Int, synth: Synth, onDone: (Boolean) -> Unit) {
        if (recording || !hasPermission(context)) return
        val rate = Synth.RATE
        val min = AudioRecord.getMinBufferSize(rate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
        val rec = runCatching {
            AudioRecord(MediaRecorder.AudioSource.MIC, rate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, maxOf(min, rate))
        }.getOrNull() ?: return
        if (rec.state != AudioRecord.STATE_INITIALIZED) { rec.release(); return }
        recording = true
        val target = file(context, pad)
        thread = Thread({
            val samples = ShortArray(rate * MAX_SECONDS)
            var n = 0
            rec.startRecording()
            val buf = ShortArray(1024)
            while (recording && n < samples.size) {
                val got = rec.read(buf, 0, minOf(buf.size, samples.size - n))
                if (got <= 0) break
                System.arraycopy(buf, 0, samples, n, got)
                n += got
                var peak = 0
                for (j in 0 until got) peak = maxOf(peak, abs(buf[j].toInt()))
                val lv = (peak / 20000f).coerceAtMost(1f); val el = n.toFloat() / rate
                main.post { level = lv; elapsed = el }
            }
            runCatching { rec.stop() }
            rec.release()
            recording = false
            val data = shape(samples, n)
            if (data.isNotEmpty()) {
                synth.wav(target, data)
                main.post {
                    level = 0f
                    synth.reloadSample(pad)
                    onDone(true)
                }
            } else main.post { level = 0f; onDone(false) }
        }, "kidshome-sampler").apply { start() }
    }

    fun stop() {
        recording = false
    }

    /** 最初の無音を切り、終わりをなめらかにし、大きさをそろえる */
    fun shape(samples: ShortArray, n: Int): FloatArray {
        if (n <= 0) return FloatArray(0)
        var start = 0
        while (start < n && abs(samples[start].toInt()) < 500) start++
        start = (start - Synth.RATE / 100).coerceAtLeast(0)
        val len = n - start
        if (len < Synth.RATE / 40) return FloatArray(0)
        val out = FloatArray(len) { samples[start + it] / 32768f }
        val peak = out.maxOf { abs(it) }.coerceAtLeast(0.01f)
        val fade = (Synth.RATE / 50).coerceAtMost(len)
        for (i in out.indices) {
            var v = out[i] / peak * 0.9f
            if (i > len - fade) v *= (len - i).toFloat() / fade
            out[i] = v
        }
        return out
    }
}
