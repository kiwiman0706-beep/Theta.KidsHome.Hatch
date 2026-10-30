package com.theta.kidshome.media

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.theta.kidshome.MainActivity
import com.theta.kidshome.data.MediaEntry
import com.theta.kidshome.data.MediaKind
import com.theta.kidshome.data.Positions

/**
 * 音楽・オーディオブックのプレイヤー（アプリに 1 つ）。
 * 再生中は [PlaybackService] が通知とメディアセッションを出すので、ほかのアプリに行っても鳴り続け、
 * ホームの ⏮ ⏯ ⏭ や車のハンドルのボタンでも操作できる。
 * オーディオブックは、どこまで聞いたかを 5 秒ごとに覚えて、次は続きから。
 */
object AudioPlayer {
    private var player: ExoPlayer? = null
    private lateinit var app: Context
    private var queue: List<MediaEntry> = emptyList()

    var current by mutableStateOf<MediaEntry?>(null)
        private set
    var playing by mutableStateOf(false)
        private set
    var speed by mutableFloatStateOf(1f)
        private set

    private val handler = Handler(Looper.getMainLooper())
    private val saver = object : Runnable {
        override fun run() {
            saveBookPosition()
            handler.postDelayed(this, 5_000)
        }
    }

    fun player(context: Context): ExoPlayer {
        player?.let { return it }
        app = context.applicationContext
        val p = ExoPlayer.Builder(app)
            .setAudioAttributes(
                AudioAttributes.Builder().setUsage(C.USAGE_MEDIA).setContentType(C.AUDIO_CONTENT_TYPE_MUSIC).build(),
                /* handleAudioFocus = */ true,
            )
            .setHandleAudioBecomingNoisy(true)
            .build()
        p.addListener(object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                playing = isPlaying
                handler.removeCallbacks(saver)
                if (isPlaying) handler.postDelayed(saver, 5_000) else saveBookPosition()
            }

            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                current = queue.getOrNull(p.currentMediaItemIndex)
            }
        })
        player = p
        return p
    }

    /** list の index 番目から再生。オーディオブックは続きから */
    fun play(context: Context, list: List<MediaEntry>, index: Int) {
        val p = player(context)
        saveBookPosition()
        queue = list
        val start = list.getOrNull(index) ?: return
        val items = list.map { e ->
            MediaItem.Builder()
                .setUri(e.uri)
                .setMediaId(e.id.toString())
                .setMediaMetadata(
                    MediaMetadata.Builder().setTitle(e.title).setArtist(e.artist).setAlbumTitle(e.album).build()
                )
                .build()
        }
        val resume = if (start.kind == MediaKind.BOOK) Positions.get(app, start.uri) else 0L
        p.setAudioAttributes(
            AudioAttributes.Builder().setUsage(C.USAGE_MEDIA)
                .setContentType(if (start.kind == MediaKind.BOOK) C.AUDIO_CONTENT_TYPE_SPEECH else C.AUDIO_CONTENT_TYPE_MUSIC).build(),
            true,
        )
        p.setMediaItems(items, index, resume)
        p.playbackParameters = PlaybackParameters(if (start.kind == MediaKind.BOOK) speed else 1f)
        p.prepare()
        p.play()
        current = start
        // 通知とメディアセッション（裏でも鳴り続ける）
        runCatching { app.startService(Intent(app, PlaybackService::class.java)) }
    }

    fun toggle() {
        val p = player ?: return
        if (p.isPlaying) p.pause() else p.play()
    }

    fun pause() = player?.pause()

    fun next() = player?.let { if (it.hasNextMediaItem()) it.seekToNextMediaItem() }

    fun previous() = player?.let { if (it.currentPosition > 5_000 || !it.hasPreviousMediaItem()) it.seekTo(0) else it.seekToPreviousMediaItem() }

    fun seekBy(ms: Long) = player?.let { it.seekTo((it.currentPosition + ms).coerceIn(0, maxOf(0, it.duration))) }

    fun seekTo(fraction: Float) = player?.let { if (it.duration > 0) it.seekTo((it.duration * fraction).toLong()) }

    fun changeSpeed(s: Float) {
        speed = s
        player?.playbackParameters = PlaybackParameters(s)
    }

    fun position(): Long = player?.currentPosition ?: 0L
    fun duration(): Long = player?.duration?.takeIf { it > 0 } ?: current?.durationMs ?: 0L

    private fun saveBookPosition() {
        val p = player ?: return
        val e = current ?: return
        if (e.kind == MediaKind.BOOK) Positions.set(app, e.uri, p.currentPosition, p.duration)
    }

    fun release() {
        saveBookPosition()
        handler.removeCallbacks(saver)
        player?.release()
        player = null
        current = null
        playing = false
    }
}

/** 音楽・オーディオブックを裏でも鳴らすためのサービス（通知・ロック画面・車のボタン） */
class PlaybackService : MediaSessionService() {
    private var session: MediaSession? = null

    override fun onCreate() {
        super.onCreate()
        val open = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        session = MediaSession.Builder(this, AudioPlayer.player(this)).setSessionActivity(open).build().also { addSession(it) }
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = session

    override fun onTaskRemoved(rootIntent: Intent?) {
        val p = session?.player
        if (p == null || !p.playWhenReady || p.mediaItemCount == 0) stopSelf()
    }

    override fun onDestroy() {
        session?.release()
        session = null
        AudioPlayer.release()
        super.onDestroy()
    }
}
