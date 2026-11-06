package app.thetahatch.kidshome

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import app.thetahatch.kidshome.data.MediaEntry
import app.thetahatch.kidshome.data.Store
import app.thetahatch.kidshome.media.VideoPlayerScreen
import app.thetahatch.kidshome.data.Weather
import app.thetahatch.kidshome.data.Widgets
import app.thetahatch.kidshome.remote.Remote
import app.thetahatch.kidshome.ui.RemoteDialog
import app.thetahatch.kidshome.ui.themeById
import app.thetahatch.kidshome.rescue.Rescue
import app.thetahatch.kidshome.rescue.RescueActivity
import app.thetahatch.kidshome.ui.DrawingScreen
import app.thetahatch.kidshome.ui.HomeScreen
import app.thetahatch.kidshome.ui.ParentScreen
import app.thetahatch.kidshome.update.SelfUpdate

private enum class Screen { HOME, DRAWING, PARENT, PLAYER, INSTRUMENT, EDUPAD, WORDPRO, PUZZLE, TYPING, MOJI, KUKU, MAP, TASU, DICT, SOROBAN, HISSAN, JAPAN, REGISTER, CODING, TOUCH, SUGOROKU, PURIKURA }

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
        app.thetahatch.kidshome.data.KinStore.load(this)
        app.thetahatch.kidshome.data.DollPack.load(this)
        app.thetahatch.kidshome.data.StampPack.load(this)
        app.thetahatch.kidshome.data.NuriePack.load(this)
        app.thetahatch.kidshome.data.YuruPack.load(this)
        app.thetahatch.kidshome.data.Points.init(this)
        app.thetahatch.kidshome.data.Chat.init(this)
        app.thetahatch.kidshome.data.SongSync.init(this)
        if (safe) Store.safeMode = true
        enableEdgeToEdge()
        hideSystemBars()
        // おやモード（おや の スマホ）：たて むき でも よい、子ども の ホーム は 出さない
        app.thetahatch.kidshome.data.Beta.load(this)
        if (app.thetahatch.kidshome.remote.Family.isParent(this) && !app.thetahatch.kidshome.data.Beta.on) {
            requestedOrientation = android.content.pm.ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
            setContent {
                app.thetahatch.kidshome.ui.FamilyScreen(
                    onExit = {
                        app.thetahatch.kidshome.remote.Family.setParent(this, false)
                        recreate()
                    },
                    onBeta = { app.thetahatch.kidshome.data.Beta.set(this, true); recreate() },
                )
            }
            return
        }
        setContent {
          androidx.compose.foundation.layout.Box(androidx.compose.ui.Modifier.fillMaxSize()) {
            Crossfade(screen, label = "screen") { s ->
                when (s) {
                    Screen.HOME -> {
                        // ホームでは戻るボタンを無効にする
                        BackHandler {}
                        HomeScreen(
                            onOpenDrawing = { screen = Screen.DRAWING },
                            onOpenParent = { screen = Screen.PARENT },
                            onOpenInstrument = { screen = Screen.INSTRUMENT },
                            onOpenEdupad = { screen = Screen.EDUPAD },
                            onOpenWordpro = { screen = Screen.WORDPRO },
                            onOpenPuzzle = { screen = Screen.PUZZLE },
                            onOpenTyping = { screen = Screen.TYPING },
                            onOpenMoji = { screen = Screen.MOJI },
                            onOpenKuku = { screen = Screen.KUKU },
                            onOpenMap = { screen = Screen.MAP },
                            onOpenTasu = { screen = Screen.TASU },
                            onOpenSoroban = { screen = Screen.SOROBAN },
                            onOpenHissan = { screen = Screen.HISSAN },
                            onOpenJapan = { screen = Screen.JAPAN },
                            onOpenRegister = { screen = Screen.REGISTER },
                            onOpenCoding = { screen = Screen.CODING },
                            onOpenTouch = { screen = Screen.TOUCH },
                            onOpenSugoroku = { screen = Screen.SUGOROKU },
                            onOpenPurikura = { screen = Screen.PURIKURA },
                            onOpenDict = { screen = Screen.DICT },
                            onPlayVideo = { list, i -> playList = list; playIndex = i; screen = Screen.PLAYER },
                        )
                    }
                    Screen.DRAWING -> DrawingScreen(onBack = { screen = Screen.HOME })
                    Screen.PARENT -> ParentScreen(onClose = { screen = Screen.HOME })
                    Screen.INSTRUMENT -> app.thetahatch.kidshome.media.InstrumentScreen(onBack = { screen = Screen.HOME })
                    Screen.EDUPAD -> app.thetahatch.kidshome.ui.EdupadScreen(onBack = { screen = Screen.HOME })
                    Screen.WORDPRO -> app.thetahatch.kidshome.ui.WordproScreen(onBack = { screen = Screen.HOME })
                    Screen.PUZZLE -> app.thetahatch.kidshome.ui.PuzzleScreen(onBack = { screen = Screen.HOME })
                    Screen.TYPING -> app.thetahatch.kidshome.ui.TypingScreen(onBack = { screen = Screen.HOME })
                    Screen.MOJI -> app.thetahatch.kidshome.ui.MojiScreen(onBack = { screen = Screen.HOME })
                    Screen.KUKU -> app.thetahatch.kidshome.ui.KukuScreen(onBack = { screen = Screen.HOME })
                    Screen.MAP -> app.thetahatch.kidshome.ui.MapScreen(onBack = { screen = Screen.HOME })
                    Screen.TASU -> app.thetahatch.kidshome.ui.TasuScreen(onBack = { screen = Screen.HOME })
                    Screen.DICT -> app.thetahatch.kidshome.ui.DictScreen(onBack = { screen = Screen.HOME })
                    Screen.SOROBAN -> app.thetahatch.kidshome.ui.SorobanScreen(onBack = { screen = Screen.HOME })
                    Screen.HISSAN -> app.thetahatch.kidshome.ui.HissanScreen(onBack = { screen = Screen.HOME })
                    Screen.JAPAN -> app.thetahatch.kidshome.ui.JapanPuzzleScreen(onBack = { screen = Screen.HOME })
                    Screen.REGISTER -> app.thetahatch.kidshome.ui.RegisterScreen(onBack = { screen = Screen.HOME })
                    Screen.CODING -> app.thetahatch.kidshome.ui.CodingScreen(onBack = { screen = Screen.HOME })
                    Screen.TOUCH -> app.thetahatch.kidshome.ui.TouchPlayScreen(onBack = { screen = Screen.HOME })
                    Screen.SUGOROKU -> app.thetahatch.kidshome.ui.SugorokuScreen(onBack = { screen = Screen.HOME })
                    Screen.PURIKURA -> app.thetahatch.kidshome.ui.PurikuraScreen(onBack = { screen = Screen.HOME })
                    Screen.PLAYER -> VideoPlayerScreen(playList, playIndex, onBack = { screen = Screen.HOME }, onLocked = { screen = Screen.HOME })
                }
            }
            // おうちのひとからの呼びかけは、どの画面の上にも出す
            RemoteDialog(themeById(Store.config.currentProfile.themeId))
            // はじめて の 起動：前 の キッズホーム（com.theta.kidshome）から 引っ越し？
            app.thetahatch.kidshome.ui.FirstRunMigration()
            // 🪙 +1
            app.thetahatch.kidshome.ui.PointsToast()
            app.thetahatch.kidshome.ui.EarnNav.open = { k -> screen = when (k) { "moji" -> Screen.MOJI; "kuku" -> Screen.KUKU; else -> Screen.TASU } }
            // 💌 かぞく の チャット：30 びょう ごと に とってくる
            app.thetahatch.kidshome.ui.ChatToast()
            // 🎹 足した 曲 が かわったら ほか の タブレット へ
            val songs = Store.config.songs
            androidx.compose.runtime.LaunchedEffect(songs) { app.thetahatch.kidshome.data.SongSync.localChanged(songs) }
            androidx.compose.runtime.LaunchedEffect(Unit) {
                while (true) { runCatching { app.thetahatch.kidshome.data.Chat.poll() }; kotlinx.coroutines.delay(30_000) }
            }
            // ベータ（おや の スマホ で ためし中）：いつでも おやモード に もどれる
            if (app.thetahatch.kidshome.data.Beta.on) {
                androidx.compose.material3.Button(
                    onClick = { app.thetahatch.kidshome.data.Beta.set(this@MainActivity, false); recreate() },
                    colors = androidx.compose.material3.ButtonDefaults.buttonColors(containerColor = androidx.compose.ui.graphics.Color(0xCC6A3FB5)),
                    modifier = androidx.compose.ui.Modifier.align(androidx.compose.ui.Alignment.BottomCenter).padding(bottom = 6.dp),
                ) { androidx.compose.material3.Text("🧪 ベータ（せいげん なし）・おやモード に もどる", fontSize = 12.sp) }
            }
          }
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
        app.thetahatch.kidshome.data.GuidePack.update(this)
        app.thetahatch.kidshome.data.DollPack.update(this)
        Weather.refresh(this)
        Remote.sync(this)
        app.thetahatch.kidshome.data.SheetSync.sync(this)
        app.thetahatch.kidshome.data.MediaLibrary.refresh(this)
        app.thetahatch.kidshome.data.Events.refresh(this)
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
