package com.theta.kidshome.data

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * ベータ テスト（おや の スマホ で アプリ を ためす）：どうがロック・夜 の くらさ・おうちのひと かくにん・
 * 辞書 の レベル・カテゴリー の 見せる 子 を ぜんぶ はずして うごかす。おやモード の スマホ から だけ 入れる。
 */
object Beta {
    var on by mutableStateOf(false)
        private set

    private fun prefs(c: Context) = c.getSharedPreferences("family", Context.MODE_PRIVATE)

    fun load(c: Context) { on = prefs(c).getBoolean("beta", false) }

    fun set(c: Context, v: Boolean) { prefs(c).edit().putBoolean("beta", v).apply(); on = v }
}
