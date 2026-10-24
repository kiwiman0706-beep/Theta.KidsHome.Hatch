package com.theta.kidshome.remote

import android.app.Notification
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import com.theta.kidshome.R
import com.theta.kidshome.data.Store
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * おうちのひとからの呼びかけを待ち受ける（ntfy の JSON ストリームを読み続ける）。
 * 子どもが動画アプリを見ている間も届くように、常駐（フォアグラウンドサービス）にする。
 */
class RemoteService : Service() {
    @Volatile private var running = false
    @Volatile private var conn: HttpURLConnection? = null
    private var thread: Thread? = null
    private val main = Handler(Looper.getMainLooper())

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Store.init(this)
        Remote.ensureChannel(this)
        val n = Notification.Builder(this, CHANNEL_WAIT)
            .setSmallIcon(R.drawable.ic_rescue)
            .setContentTitle("キッズホーム")
            .setContentText("おうちのひとからの呼びかけを待っています")
            .setOngoing(true)
            .build()
        if (Build.VERSION.SDK_INT >= 34) startForeground(1, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        else startForeground(1, n)

        val topic = Store.config.remoteTopic
        if (!Store.config.remoteOn || topic == null) {
            stopSelf()
            return START_NOT_STICKY
        }
        if (!running) {
            running = true
            thread = Thread({ loop(topic) }, "kidshome-remote").apply { isDaemon = true; start() }
        }
        return START_STICKY
    }

    override fun onDestroy() {
        running = false
        runCatching { conn?.disconnect() }
        super.onDestroy()
    }

    private fun loop(topic: String) {
        val prefs = getSharedPreferences("remote", MODE_PRIVATE)
        var backoff = 2_000L
        while (running) {
            try {
                // 前回の続きから（取りこぼさない）。はじめてなら今から
                val since = prefs.getString("lastId:$topic", null) ?: (System.currentTimeMillis() / 1000).toString()
                val c = URL("${Remote.SERVER}/$topic/json?since=$since").openConnection() as HttpURLConnection
                conn = c
                c.connectTimeout = 15_000
                c.readTimeout = 90_000 // ntfy は 45 秒ごとに keepalive を送る
                c.setRequestProperty("User-Agent", "KidsHome")
                if (c.responseCode / 100 != 2) throw java.io.IOException("HTTP ${c.responseCode}")
                backoff = 2_000L
                c.inputStream.bufferedReader().useLines { lines ->
                    for (line in lines) {
                        if (!running) break
                        val o = runCatching { JSONObject(line) }.getOrNull() ?: continue
                        if (o.optString("event") != "message") continue
                        prefs.edit().putString("lastId:$topic", o.optString("id")).apply()
                        // つながっていない間にたまった古い呼びかけ（10 分より前）は出さない
                        val age = System.currentTimeMillis() - o.optLong("time") * 1000
                        val text = o.optString("message")
                        if (age < 10 * 60_000 && text.isNotBlank()) main.post { Remote.apply(this, text) }
                    }
                }
            } catch (e: Exception) {
                if (!running) break
                runCatching { Thread.sleep(backoff) }
                backoff = (backoff * 2).coerceAtMost(60_000L)
            }
        }
    }

    companion object {
        const val CHANNEL_WAIT = "remote_wait"
    }
}
