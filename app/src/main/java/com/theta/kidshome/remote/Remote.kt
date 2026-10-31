package com.theta.kidshome.remote

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.media.RingtoneManager
import android.provider.Settings
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.theta.kidshome.MainActivity
import com.theta.kidshome.R
import com.theta.kidshome.data.Store
import kotlin.random.Random

/** おうちのひとからの呼びかけ */
data class RemoteMessage(val text: String, val at: Long, val lockMinutes: Int? = null)

/** 呼びかけのテキストを読んだ結果 */
data class RemoteCommand(val message: String, val lockMinutes: Int? = null, val unlock: Boolean = false)

/**
 * おうちのひとからの呼びかけ（ntfy.sh 経由）。
 * スマホのブラウザで https://ntfy.sh/<トピック> を開いて文字を送ると、子どものタブレットにマスコットと一緒に出る。
 *   そろそろ おわりだよ         … 呼びかけだけ
 *   /lock 30 ごはんだよ          … どうがを 30 分ロックして呼びかけ
 *   /unlock                      … ロックを解除
 */
object Remote {
    const val SERVER = "https://ntfy.sh"
    const val CHANNEL = "remote"

    var message by mutableStateOf<RemoteMessage?>(null)

    fun newTopic(): String {
        val chars = "abcdefghijkmnpqrstuvwxyz23456789"
        return "kidshome-" + (1..16).map { chars[Random.nextInt(chars.length)] }.joinToString("")
    }

    fun pageUrl(topic: String) = "$SERVER/$topic"

    fun parse(raw: String): RemoteCommand {
        val text = raw.trim()
        val lower = text.lowercase()
        fun rest(prefix: String) = text.substring(prefix.length).trim()
        for (p in listOf("/unlock", "/かいじょ", "/解除")) {
            if (lower.startsWith(p)) return RemoteCommand(rest(p), unlock = true)
        }
        for (p in listOf("/lock", "/ろっく", "/ロック")) {
            if (lower.startsWith(p)) {
                val after = rest(p)
                val num = Regex("^(\\d{1,3})").find(after)?.value
                val minutes = num?.toIntOrNull()?.coerceIn(1, 600) ?: 30
                return RemoteCommand(after.removePrefix(num ?: "").trim(), lockMinutes = minutes)
            }
        }
        return RemoteCommand(text)
    }

    /** 届いた呼びかけを反映する（主スレッドで呼ぶ） */
    fun apply(context: Context, raw: String) {
        Store.init(context)
        // おや の スマホ（おやモード）から の しるし つき めいれい
        if (Family.isSealed(raw)) { Family.receive(context, raw); return }
        val cmd = parse(raw)
        val now = System.currentTimeMillis()
        when {
            cmd.unlock -> Store.update { it.copy(videoLockUntil = 0L) }
            cmd.lockMinutes != null -> Store.update { it.copy(videoLockFrom = now, videoLockUntil = now + cmd.lockMinutes * 60_000L) }
        }
        val text = cmd.message.ifBlank {
            when {
                cmd.unlock -> "どうがを みても いいよ！"
                cmd.lockMinutes != null -> "どうがは ちょっと おやすみ してね"
                else -> return
            }
        }
        message = RemoteMessage(text, now, cmd.lockMinutes)
        chime(context)
        bringToFront(context, text)
    }

    private fun chime(context: Context) = runCatching {
        RingtoneManager.getRingtone(context, RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION))?.play()
    }

    /** 動画を見ていても、キッズホームを前に出す（「他のアプリの上に重ねて表示」の許可が要る）。だめなら通知 */
    private fun bringToFront(context: Context, text: String) {
        val intent = Intent(context, MainActivity::class.java)
            .putExtra(MainActivity.EXTRA_REMOTE, true)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (Settings.canDrawOverlays(context) && runCatching { context.startActivity(intent) }.isSuccess) return
        val nm = context.getSystemService(NotificationManager::class.java) ?: return
        ensureChannel(context)
        val pi = PendingIntent.getActivity(context, 1, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val n = android.app.Notification.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_rescue)
            .setContentTitle("おうちのひとから")
            .setContentText(text)
            .setContentIntent(pi)
            .setAutoCancel(true)
            .build()
        runCatching { nm.notify(2, n) }
    }

    fun ensureChannel(context: Context) {
        val nm = context.getSystemService(NotificationManager::class.java) ?: return
        if (nm.getNotificationChannel(CHANNEL) == null) {
            nm.createNotificationChannel(NotificationChannel(CHANNEL, "おうちのひとからの呼びかけ", NotificationManager.IMPORTANCE_HIGH))
        }
        if (nm.getNotificationChannel(RemoteService.CHANNEL_WAIT) == null) {
            nm.createNotificationChannel(NotificationChannel(RemoteService.CHANNEL_WAIT, "呼びかけを待つ", NotificationManager.IMPORTANCE_MIN))
        }
    }

    /** 設定に合わせて、待ち受けを始める・止める */
    fun sync(context: Context) {
        val c = Store.config
        val intent = Intent(context, RemoteService::class.java)
        if (c.remoteOn && c.remoteTopic != null) {
            runCatching { context.startForegroundService(intent) }
        } else {
            context.stopService(intent)
        }
    }
}
