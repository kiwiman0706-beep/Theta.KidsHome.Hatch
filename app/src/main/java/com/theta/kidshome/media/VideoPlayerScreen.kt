package com.theta.kidshome.media

import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.annotation.OptIn
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import com.theta.kidshome.data.Launcher
import com.theta.kidshome.data.MediaEntry
import com.theta.kidshome.data.Positions
import com.theta.kidshome.data.Store
import com.theta.kidshome.ui.DotText
import com.theta.kidshome.ui.RetroButton
import com.theta.kidshome.ui.RetroIconButton
import com.theta.kidshome.ui.RetroWindow
import com.theta.kidshome.ui.themeById
import kotlinx.coroutines.delay

private const val VLC = "org.videolan.vlc"

/**
 * ホームの中で動画を再生する（Media3 / ExoPlayer）。同じたな（フォルダ）の動画を続けて再生する。
 * 前に見たところから続きを再生し、どうがロックが始まったら止めてホームに戻る。
 */
@OptIn(UnstableApi::class)
@Composable
fun VideoPlayerScreen(list: List<MediaEntry>, startIndex: Int, onBack: () -> Unit, onLocked: () -> Unit) {
    val context = LocalContext.current
    val theme = themeById(Store.config.currentProfile.themeId)
    var error by remember { mutableStateOf<String?>(null) }
    var index by remember { mutableStateOf(startIndex) }

    val player = remember {
        AudioPlayer.pause()
        ExoPlayer.Builder(context).build().apply {
            setMediaItems(
                list.map { e ->
                    MediaItem.Builder().setUri(e.uri).setMediaId(e.id.toString())
                        .setMediaMetadata(MediaMetadata.Builder().setTitle(e.title).build()).build()
                },
                startIndex,
                list.getOrNull(startIndex)?.let { Positions.get(context, it.uri) } ?: 0L,
            )
            prepare()
            playWhenReady = true
        }
    }

    fun save() {
        list.getOrNull(player.currentMediaItemIndex)?.let { Positions.set(context, it.uri, player.currentPosition, player.duration) }
    }

    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onPlayerError(e: PlaybackException) {
                error = "この どうがは ここでは みられないよ"
            }

            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                // 前の動画は見終わったので、次は最初から
                list.getOrNull(index)?.let { Positions.set(context, it.uri, 0, 0) }
                index = player.currentMediaItemIndex
                list.getOrNull(index)?.let { player.seekTo(Positions.get(context, it.uri)) }
            }
        }
        player.addListener(listener)
        onDispose {
            save()
            player.removeListener(listener)
            player.release()
        }
    }

    // 5 秒ごとに見たところを覚える。どうがロックが始まったら止める
    LaunchedEffect(Unit) {
        while (true) {
            delay(1000)
            if (Store.config.isVideoLocked()) {
                player.pause()
                save()
                onLocked()
                break
            }
            if (player.isPlaying && System.currentTimeMillis() / 1000 % 5 == 0L) save()
        }
    }

    BackHandler(onBack = onBack)
    Box(Modifier.fillMaxSize().background(Color.Black)) {
        AndroidView(
            factory = { ctx ->
                PlayerView(ctx).apply {
                    this.player = player
                    useController = true
                    controllerShowTimeoutMs = 3000
                    setShowNextButton(true)
                    setShowPreviousButton(true)
                    keepScreenOn = true
                }
            },
            modifier = Modifier.fillMaxSize(),
        )
        Box(Modifier.windowInsetsPadding(WindowInsets.safeDrawing).padding(12.dp)) {
            RetroIconButton("🏠", theme, size = 56.dp, onClick = onBack)
        }
        error?.let { msg ->
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                RetroWindow(theme, title = "おしらせ", titleColor = Color(0xFFFFB3CF)) {
                    Column(Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        DotText(msg, theme, 20)
                        Row(Modifier.padding(top = 16.dp)) {
                            RetroButton("もどる", theme, theme.titleBars[1], onClick = onBack)
                            val item = list.getOrNull(index)
                            if (item != null && Launcher.isInstalled(context, VLC)) {
                                RetroButton("VLC で みる", theme, theme.titleBars[2], modifier = Modifier.padding(start = 12.dp)) {
                                    runCatching {
                                        context.startActivity(
                                            Intent(Intent.ACTION_VIEW).setDataAndType(item.uri, "video/*").setPackage(VLC)
                                                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
