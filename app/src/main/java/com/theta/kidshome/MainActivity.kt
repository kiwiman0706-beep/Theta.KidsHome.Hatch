package com.theta.kidshome

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.Crossfade
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.theta.kidshome.data.MediaEntry
import com.theta.kidshome.data.Store
import com.theta.kidshome.media.VideoPlayerScreen
import com.theta.kidshome.data.Weather
import com.theta.kidshome.data.Widgets
import com.theta.kidshome.remote.Remote
import com.theta.kidshome.ui.RemoteDialog
import com.theta.kidshome.ui.themeById
import com.theta.kidshome.rescue.Rescue
import com.theta.kidshome.rescue.RescueActivity
import com.theta.kidshome.ui.DrawingScreen
import com.theta.kidshome.ui.HomeScreen
import com.theta.kidshome.ui.ParentScreen
import com.theta.kidshome.update.SelfUpdate

private enum class Screen { HOME, DRAWING, PARENT, PLAYER, INSTRUMENT }

class MainActivity : ComponentActivity() {
    private var screen by mutableStateOf(Screen.HOME)
    private var playList by mutableStateOf<List<MediaEntry>>(emptyList())
    private var playIndex = 0

    /** レスキュー画面へ回したとき（このアクティビティは何もしないで閉じる） */
    private var rescued = false
    private val healthy = Handler(Looper.getMainLooper())
    private val markHealthy = Runnable { Rescue.onHomeHealthy(this) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // 落ちる・固まるのが続いていたら、ホームの代わりにレスキュー画面
        val safe = intent?.getBooleanExtra(Rescue.EXTRA_SAFE, false) == true
        if (Rescue.onHomeStart(this) && !safe) {
            rescued = true
            startActivity(Intent(this, RescueActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            finish()
            return
        }
        Store.init(this)
        if (safe) Store.safeMode = true
        enableEdgeToEdge()
        hideSystemBars()
        setContent {
            Crossfade(screen, label = "screen") { s ->
                when (s) {
                    Screen.HOME -> {
                        // ホームでは戻るボタンを無効にする
                        BackHandler {}
                        HomeScreen(
                            onOpenDrawing = { screen = Screen.DRAWING },
                            onOpenParent = { screen = Screen.PARENT },
                            onOpenInstrument = { screen = Screen.INSTRUMENT },
                            onPlayVideo = { list, i -> playList = list; playIndex = i; screen = Screen.PLAYER },
                        )
                    }
                    Screen.DRAWING -> DrawingScreen(onBack = { screen = Screen.HOME })
                    Screen.PARENT -> ParentScreen(onClose = { screen = Screen.HOME })
                    Screen.INSTRUMENT -> com.theta.kidshome.media.InstrumentScreen(onBack = { screen = Screen.HOME })
                    Screen.PLAYER -> VideoPlayerScreen(playList, playIndex, onBack = { screen = Screen.HOME }, onLocked = { screen = Screen.HOME })
                }
            }
            // おうちのひとからの呼びかけは、どの画面の上にも出す
            RemoteDialog(themeById(Store.config.currentProfile.themeId))
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        // ホームボタンでいつでもホームに戻る
        if (intent.hasCategory(Intent.CATEGORY_HOME) || intent.getBooleanExtra(EXTRA_REMOTE, false)) screen = Screen.HOME
    }

    override fun onResume() {
        super.onResume()
        if (rescued) return
        // 8 秒動けば「無事に起動した」
        healthy.removeCallbacks(markHealthy)
        healthy.postDelayed(markHealthy, 8000)
        if (Store.config.autoUpdate) SelfUpdate.check(this)
        Weather.refresh(this)
        Remote.sync(this)
        com.theta.kidshome.data.SheetSync.sync(this)
        com.theta.kidshome.data.MediaLibrary.refresh(this)
        com.theta.kidshome.data.Events.refresh(this)
    }

    override fun onStart() {
        super.onStart()
        if (!rescued && !Store.safeMode) Widgets.start()
    }

    override fun onStop() {
        if (!rescued) {
            healthy.removeCallbacks(markHealthy)
            // 普通に裏へ回った（落ちた・固まったのではない）
            Rescue.onHomeHealthy(this)
            // おうちのひとメニューを開いたまま離れたら、次はまたかけ算を聞く
            if (screen == Screen.PARENT) screen = Screen.HOME
            Widgets.stop()
        }
        super.onStop()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus && !rescued) hideSystemBars()
    }

    companion object {
        const val EXTRA_REMOTE = "remote"
    }

    private fun hideSystemBars() {
        WindowCompat.getInsetsController(window, window.decorView).apply {
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            hide(WindowInsetsCompat.Type.systemBars())
        }
    }
}
