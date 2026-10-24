package com.theta.kidshome.data

import android.content.Context
import android.media.AudioManager
import android.os.SystemClock
import android.view.KeyEvent

/**
 * おんがくの操作。いま音を出しているアプリ（Spotify・YouTube Music など）にメディアキーを送る。
 * 特別な許可はいらない。
 */
object Media {
    private fun audio(context: Context) = context.getSystemService(AudioManager::class.java)

    fun isPlaying(context: Context): Boolean = runCatching { audio(context).isMusicActive }.getOrDefault(false)

    fun playPause(context: Context) = send(context, KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE)
    fun next(context: Context) = send(context, KeyEvent.KEYCODE_MEDIA_NEXT)
    fun previous(context: Context) = send(context, KeyEvent.KEYCODE_MEDIA_PREVIOUS)

    fun volume(context: Context, up: Boolean) = runCatching {
        audio(context).adjustStreamVolume(
            AudioManager.STREAM_MUSIC,
            if (up) AudioManager.ADJUST_RAISE else AudioManager.ADJUST_LOWER,
            AudioManager.FLAG_SHOW_UI,
        )
    }

    private fun send(context: Context, code: Int) = runCatching {
        val am = audio(context)
        val t = SystemClock.uptimeMillis()
        am.dispatchMediaKeyEvent(KeyEvent(t, t, KeyEvent.ACTION_DOWN, code, 0))
        am.dispatchMediaKeyEvent(KeyEvent(t, t, KeyEvent.ACTION_UP, code, 0))
    }
}
