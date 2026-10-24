package com.theta.kidshome

import android.content.Intent
import android.os.Bundle
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
import com.theta.kidshome.data.Store
import com.theta.kidshome.ui.DrawingScreen
import com.theta.kidshome.ui.HomeScreen
import com.theta.kidshome.ui.ParentScreen

private enum class Screen { HOME, DRAWING, PARENT }

class MainActivity : ComponentActivity() {
    private var screen by mutableStateOf(Screen.HOME)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Store.init(this)
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
                        )
                    }
                    Screen.DRAWING -> DrawingScreen(onBack = { screen = Screen.HOME })
                    Screen.PARENT -> ParentScreen(onClose = { screen = Screen.HOME })
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        // ホームボタンでいつでもホームに戻る
        if (intent.hasCategory(Intent.CATEGORY_HOME)) screen = Screen.HOME
    }

    override fun onStop() {
        super.onStop()
        // おうちのひとメニューを開いたまま離れたら、次は暗証番号を聞く
        if (screen == Screen.PARENT) screen = Screen.HOME
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) hideSystemBars()
    }

    private fun hideSystemBars() {
        WindowCompat.getInsetsController(window, window.decorView).apply {
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            hide(WindowInsetsCompat.Type.systemBars())
        }
    }
}
