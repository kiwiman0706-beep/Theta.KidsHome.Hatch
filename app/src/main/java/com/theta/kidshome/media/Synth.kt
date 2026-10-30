package com.theta.kidshome.media

import android.content.Context
import android.media.AudioAttributes
import android.media.SoundPool
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.DataOutputStream
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.pow
import kotlin.math.sin
import kotlin.random.Random

/** けんばんの音色 */
enum class Voice(val emoji: String, val label: String) {
    PIANO("🎹", "ピアノ"),
    MUSICBOX("🎁", "オルゴール"),
    ORGAN("⛪", "オルガン"),
    XYLO("🪵", "もっきん"),
    SYNTH("🤖", "シンセ"),
}

/** ドラムパッドのセット（8 こ ずつ） */
enum class Kit(val emoji: String, val label: String, val pads: List<Pair<String, String>>) {
    DRUMS(
        "🥁", "ドラム",
        listOf("🦶" to "ドン", "🥁" to "タン", "🎩" to "チッ", "👏" to "パン", "🔵" to "トン", "🟣" to "ボン", "💥" to "シャーン", "🐮" to "カン"),
    ),
    ELECTRO(
        "🎛", "でんし",
        listOf("💣" to "ズン", "⚡" to "パシ", "✨" to "ツッ", "🙌" to "パチ", "🌀" to "ピュン", "🔔" to "ポーン", "🌊" to "ザー", "🎯" to "ピッ"),
    ),
    FUN(
        "🎉", "たのしい",
        listOf("🫧" to "ぷよん", "👾" to "ピコ", "🚀" to "ビーム", "🎈" to "ポン", "🐸" to "ケロ", "🐤" to "ピヨ", "🌈" to "キラ", "😴" to "ブー"),
    ),
}

/**
 * がっきの音を、その場で計算して作る（音のファイルはいらない）。
 * 作った音は一時ファイル（WAV）にして SoundPool に読み込み、すぐ鳴らせるようにする。
 */
class Synth(private val context: Context) {
    companion object {
        const val RATE = 22050
        /** けんばん：ド（C4）から 2 オクターブ上のド（C6）まで */
        const val KEYS = 25
        const val BASE_MIDI = 60
    }

    private val pool = SoundPool.Builder()
        .setMaxStreams(16)
        .setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_GAME).setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build())
        .build()
    private val voiceIds = HashMap<Voice, IntArray>()
    private val kitIds = HashMap<Kit, IntArray>()
    private var pending = 0
    var ready by mutableStateOf(false)
        private set

    init {
        pool.setOnLoadCompleteListener { _, _, _ ->
            pending--
            if (pending <= 0) ready = true
        }
    }

    /** 音色・セットの音をまだ作っていなければ作る（数百ミリ秒） */
    suspend fun prepare(voice: Voice, kit: Kit) {
        val needVoice = voice !in voiceIds
        val needKit = kit !in kitIds
        if (!needVoice && !needKit) return
        ready = false
        val dir = File(context.cacheDir, "instrument").apply { mkdirs() }
        val files = withContext(Dispatchers.Default) {
            val v = if (needVoice) (0 until KEYS).map { k -> wav(File(dir, "${voice.name}_$k.wav"), Tones.note(voice, BASE_MIDI + k)) } else emptyList()
            val d = if (needKit) (0 until 8).map { p -> wav(File(dir, "${kit.name}_$p.wav"), Tones.drum(kit, p)) } else emptyList()
            v to d
        }
        pending += files.first.size + files.second.size
        if (needVoice) voiceIds[voice] = files.first.map { pool.load(it.path, 1) }.toIntArray()
        if (needKit) kitIds[kit] = files.second.map { pool.load(it.path, 1) }.toIntArray()
    }

    /** octave：-1・0・+1（再生の速さで 1 オクターブずらす） */
    fun playKey(voice: Voice, key: Int, octave: Int = 0) {
        val id = voiceIds[voice]?.getOrNull(key) ?: return
        val rate = when (octave) { -1 -> 0.5f; 1 -> 2f; else -> 1f }
        pool.play(id, 0.9f, 0.9f, 1, 0, rate)
    }

    fun playPad(kit: Kit, pad: Int) {
        val id = kitIds[kit]?.getOrNull(pad) ?: return
        pool.play(id, 1f, 1f, 1, 0, 1f)
    }

    fun release() = pool.release()

    private fun wav(file: File, data: FloatArray): File {
        val pcm = ByteBuffer.allocate(data.size * 2).order(ByteOrder.LITTLE_ENDIAN)
        data.forEach { pcm.putShort((it.coerceIn(-1f, 1f) * 32767).toInt().toShort()) }
        DataOutputStream(file.outputStream().buffered()).use { o ->
            fun i32(v: Int) = o.write(ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putInt(v).array())
            fun i16(v: Int) = o.write(ByteBuffer.allocate(2).order(ByteOrder.LITTLE_ENDIAN).putShort(v.toShort()).array())
            o.writeBytes("RIFF"); i32(36 + data.size * 2); o.writeBytes("WAVE")
            o.writeBytes("fmt "); i32(16); i16(1); i16(1); i32(RATE); i32(RATE * 2); i16(2); i16(16)
            o.writeBytes("data"); i32(data.size * 2)
            o.write(pcm.array())
        }
        return file
    }
}

/** 音色の計算（Android に依存しない） */
object Tones {

    private fun freq(midi: Int) = 440.0 * 2.0.pow((midi - 69) / 12.0)

    private fun render(seconds: Double, f: (t: Double, i: Int) -> Double): FloatArray {
        val n = (seconds * Synth.RATE).toInt()
        val out = FloatArray(n) { i ->
            val t = i.toDouble() / Synth.RATE
            // 出だしと終わりの「プチッ」を消す
            val fadeIn = (t / 0.004).coerceAtMost(1.0)
            val fadeOut = ((seconds - t) / 0.03).coerceIn(0.0, 1.0)
            (f(t, i) * fadeIn * fadeOut).toFloat()
        }
        // 音が割れないように、いちばん大きいところを 0.95 までにそろえる
        val peak = out.maxOf { abs(it) }
        if (peak > 0.95f) for (i in out.indices) out[i] = out[i] / peak * 0.95f
        return out
    }

    private fun s(f: Double, t: Double) = sin(2 * PI * f * t)

    fun note(voice: Voice, midi: Int): FloatArray {
        val f = freq(midi)
        return when (voice) {
            Voice.PIANO -> render(1.4) { t, _ ->
                0.5 * (s(f, t) + 0.45 * s(2 * f, t) * exp(-1.5 * t) + 0.2 * s(3 * f, t) * exp(-2.5 * t) + 0.08 * s(4 * f, t) * exp(-4.0 * t)) *
                    exp(-2.8 * t)
            }
            Voice.MUSICBOX -> render(1.6) { t, _ ->
                0.5 * (s(f * 2, t) + 0.35 * s(f * 2 * 2.76, t) * exp(-6.0 * t) + 0.15 * s(f * 2 * 5.4, t) * exp(-10.0 * t)) * exp(-3.2 * t)
            }
            Voice.ORGAN -> render(0.9) { t, _ ->
                val env = if (t < 0.7) 1.0 else exp(-(t - 0.7) * 20)
                0.32 * (s(f, t) + 0.6 * s(2 * f, t) + 0.35 * s(3 * f, t) + 0.2 * s(4 * f, t) + 0.12 * s(6 * f, t)) * env *
                    (1 + 0.05 * s(5.5, t))
            }
            Voice.XYLO -> render(0.7) { t, _ ->
                0.6 * (s(f * 2, t) + 0.3 * s(f * 2 * 3.93, t) * exp(-20.0 * t)) * exp(-7.0 * t)
            }
            Voice.SYNTH -> render(0.9) { t, _ ->
                var v = 0.0
                for (h in 1..9 step 2) v += s(f * h, t) / h * exp(-(h - 1) * 1.2 * t)
                0.45 * v * exp(-2.2 * t)
            }
        }
    }

    fun drum(kit: Kit, pad: Int): FloatArray {
        val rnd = Random(kit.ordinal * 31 + pad)
        fun noise() = rnd.nextDouble() * 2 - 1
        var last = 0.0
        fun hiNoise(): Double { val n = noise(); val v = n - last; last = n; return v * 0.6 }
        fun sweep(f0: Double, f1: Double, k: Double, t: Double): Double {
            // 周波数が f0 から f1 へ落ちていく音（位相は積分）
            val ph = f1 * t + (f0 - f1) * (1 - exp(-k * t)) / k
            return sin(2 * PI * ph)
        }
        return when (kit) {
            Kit.DRUMS -> when (pad) {
                0 -> render(0.45) { t, _ -> 0.95 * sweep(150.0, 50.0, 25.0, t) * exp(-7.0 * t) }
                1 -> render(0.3) { t, _ -> (0.55 * noise() * exp(-14.0 * t) + 0.45 * s(190.0, t) * exp(-20.0 * t)) }
                2 -> render(0.12) { t, _ -> 0.7 * hiNoise() * exp(-45.0 * t) }
                3 -> render(0.3) { t, _ -> 0.7 * noise() * (clap(t, 60.0) + 0.6 * exp(-12.0 * t)) }
                4 -> render(0.4) { t, _ -> 0.8 * sweep(260.0, 180.0, 12.0, t) * exp(-8.0 * t) }
                5 -> render(0.5) { t, _ -> 0.85 * sweep(160.0, 95.0, 10.0, t) * exp(-6.0 * t) }
                6 -> render(1.6) { t, _ -> 0.45 * hiNoise() * exp(-2.4 * t) + 0.1 * noise() * exp(-6.0 * t) }
                else -> render(0.35) { t, _ -> 0.35 * (sq(540.0, t) + sq(800.0, t)) * exp(-11.0 * t) }
            }
            Kit.ELECTRO -> when (pad) {
                0 -> render(0.8) { t, _ -> 0.95 * sweep(120.0, 42.0, 9.0, t) * exp(-3.5 * t) }
                1 -> render(0.25) { t, _ -> 0.6 * noise() * exp(-18.0 * t) + 0.3 * s(330.0, t) * exp(-30.0 * t) }
                2 -> render(0.08) { t, _ -> 0.6 * hiNoise() * exp(-70.0 * t) }
                3 -> render(0.25) { t, _ -> 0.65 * hiNoise() * (clap(t, 80.0) + 0.5 * exp(-15.0 * t)) }
                4 -> render(0.35) { t, _ -> 0.6 * sweep(1600.0, 200.0, 14.0, t) * exp(-6.0 * t) }
                5 -> render(1.0) { t, _ -> 0.5 * (s(880.0, t) + 0.5 * s(1320.0, t)) * exp(-4.0 * t) }
                6 -> render(1.0) { t, _ -> 0.4 * noise() * (if (t < 0.5) t * 2 else exp(-(t - 0.5) * 8)) }
                else -> render(0.1) { t, _ -> 0.5 * sq(1760.0, t) * exp(-30.0 * t) }
            }
            Kit.FUN -> when (pad) {
                0 -> render(0.45) { t, _ -> 0.7 * sweep(180.0, 520.0, 8.0, t) * exp(-5.0 * t) }
                1 -> render(0.18) { t, _ -> 0.45 * sq(if (t < 0.06) 988.0 else 1319.0, t) * exp(-8.0 * t) }
                2 -> render(0.6) { t, _ -> 0.4 * sq(1800.0 * exp(-4.0 * t) + 200, t) * exp(-3.0 * t) }
                3 -> render(0.15) { t, _ -> 0.9 * sweep(900.0, 300.0, 40.0, t) * exp(-25.0 * t) }
                4 -> render(0.35) { t, _ -> 0.5 * sq(140.0 + 60 * abs(sin(2 * PI * 18 * t)), t) * exp(-6.0 * t) }
                5 -> render(0.3) { t, _ -> 0.6 * sweep(2600.0, 3600.0, 30.0, t % 0.15) * exp(-8.0 * (t % 0.15)) }
                6 -> render(1.2) { t, _ -> 0.3 * (s(1568.0, t) * exp(-3.0 * t) + s(2093.0, t) * exp(-3.0 * maxOf(0.0, t - 0.1)) * (if (t > 0.1) 1.0 else 0.0) + s(2637.0, t) * exp(-3.0 * maxOf(0.0, t - 0.2)) * (if (t > 0.2) 1.0 else 0.0)) }
                else -> render(0.7) { t, _ -> 0.45 * sq(90.0 + 20 * sin(2 * PI * 7 * t), t) * exp(-3.0 * t) }
            }
        }
    }

    /** 手びょうし：短い音が 3 回 */
    private fun clap(t: Double, k: Double) = if (t < 0.036) exp(-k * (t % 0.012)) else 0.0

    /** やわらかい矩形波 */
    private fun sq(f: Double, t: Double): Double {
        var v = 0.0
        for (h in 1..7 step 2) v += s(f * h, t) / h
        return v
    }
}
