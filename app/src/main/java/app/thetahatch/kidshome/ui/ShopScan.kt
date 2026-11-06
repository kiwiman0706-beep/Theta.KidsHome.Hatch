package app.thetahatch.kidshome.ui

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.view.Surface
import android.view.WindowManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.key.utf16CodePoint
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.google.zxing.BarcodeFormat
import com.journeyapps.barcodescanner.BarcodeView
import com.journeyapps.barcodescanner.DefaultDecoderFactory
import com.journeyapps.barcodescanner.camera.CameraSettings

/**
 * レジ の「ピッ」：前 の カメラ（インカメラ）で QR を 読みつづける。
 * 画面 の カメラ の すぐ そば に 小さな まど と「ここ に かざして ピッ」を 出す。
 * 外付け の バーコードリーダー（キーボード として うつ）も scannerKeys で うけとる。
 */

/** インカメラ が 画面 の うえ に ある か（よこ に して カメラ が うえ の とき 0／90、さかさま なら した） */
fun cameraOnTop(context: Context): Boolean {
    val override = context.getSharedPreferences("shop", Context.MODE_PRIVATE).getString("cameraEdge", "auto")
    if (override == "top") return true
    if (override == "bottom") return false
    @Suppress("DEPRECATION")
    val rot = (context.getSystemService(Context.WINDOW_SERVICE) as WindowManager).defaultDisplay.rotation
    return rot == Surface.ROTATION_0 || rot == Surface.ROTATION_90
}

private fun cameraId(front: Boolean): Int {
    @Suppress("DEPRECATION")
    for (i in 0 until android.hardware.Camera.getNumberOfCameras()) {
        val info = android.hardware.Camera.CameraInfo()
        android.hardware.Camera.getCameraInfo(i, info)
        val want = if (front) android.hardware.Camera.CameraInfo.CAMERA_FACING_FRONT else android.hardware.Camera.CameraInfo.CAMERA_FACING_BACK
        if (info.facing == want) return i
    }
    return -1
}

/** インカメラ で QR を 読む 小さな まど（おなじ もの は 1.5 びょう あける） */
@Composable
fun FrontScanner(modifier: Modifier, front: Boolean = true, onCode: (String) -> Unit) {
    val context = LocalContext.current
    val owner = LocalLifecycleOwner.current
    var granted by remember { mutableStateOf(context.checkSelfPermission(Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) }
    val ask = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted = it }
    LaunchedEffect(Unit) { if (!granted) ask.launch(Manifest.permission.CAMERA) }
    val cb = rememberUpdatedState(onCode)
    var last by remember { mutableStateOf("") }
    var lastAt by remember { mutableLongStateOf(0L) }
    if (!granted) { Box(modifier.background(Color(0xFF333344)), contentAlignment = Alignment.Center) { Text("📷？", fontSize = 28.sp, color = Color.White) }; return }
    var view by remember(front) { mutableStateOf<BarcodeView?>(null) }
    androidx.compose.runtime.key(front) { AndroidView(
        factory = { ctx ->
            BarcodeView(ctx).apply {
                cameraSettings = CameraSettings().apply { val id = cameraId(front); if (id >= 0) requestedCameraId = id }
                decoderFactory = DefaultDecoderFactory(listOf(BarcodeFormat.QR_CODE))
                decodeContinuous { r ->
                    val t = r.text ?: return@decodeContinuous
                    val now = System.currentTimeMillis()
                    if (t == last && now - lastAt < 1500) return@decodeContinuous
                    last = t; lastAt = now
                    cb.value(t)
                }
                resume()
                view = this
            }
        },
        modifier = modifier.graphicsLayer { scaleX = if (front) -1f else 1f }, // インカメラ は かがみ の よう に
    ) }
    DisposableEffect(owner, view) {
        val obs = LifecycleEventObserver { _, e ->
            when (e) { Lifecycle.Event.ON_RESUME -> view?.resume(); Lifecycle.Event.ON_PAUSE -> view?.pause(); else -> {} }
        }
        owner.lifecycle.addObserver(obs)
        onDispose { owner.lifecycle.removeObserver(obs); view?.pause() }
    }
}

/** ほか の タブレット の 画面 の QR を 読む：そと の カメラ が さいしょ（🔄 で きりかえ） */
@Composable
fun QrScanBox(modifier: Modifier, onCode: (String) -> Unit) {
    var front by remember { mutableStateOf(false) }
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        FrontScanner(modifier, front, onCode)
        androidx.compose.material3.TextButton(onClick = { front = !front }) {
            Text(if (front) "🔄 そと の カメラ に する" else "🔄 うち の カメラ に する（いま：うしろ）", fontSize = 13.sp)
        }
    }
}

/** カメラ の そば に 出す「ここ で ピッ」。onTop で うえ か した */
@Composable
fun PipWindow(onTop: Boolean, labelTurn: Float = 0f, onCode: (String) -> Unit) {
    val blink by rememberInfiniteTransition(label = "pip").animateFloat(0.3f, 1f, infiniteRepeatable(tween(700), RepeatMode.Reverse), label = "a")
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        if (!onTop) PipLabel(blink, false, labelTurn)
        FrontScanner(
            Modifier.padding(4.dp).size(150.dp, 112.dp).clip(RoundedCornerShape(16.dp))
                .border(5.dp, Color(0xFFFF5A8A).copy(alpha = blink), RoundedCornerShape(16.dp)),
            onCode = onCode,
        )
        if (onTop) PipLabel(blink, true, labelTurn)
    }
}

@Composable
private fun PipLabel(blink: Float, onTop: Boolean, turn: Float) {
    Box(
        Modifier.graphicsLayer { rotationZ = turn }.clip(RoundedCornerShape(14.dp)).background(Color(0xFFFF5A8A).copy(alpha = 0.6f + blink * 0.4f))
            .padding(horizontal = 12.dp, vertical = 4.dp),
    ) { Text(if (onTop) "↑ カメラ に QR を かざして ピッ！" else "↓ カメラ に QR を かざして ピッ！", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color.White) }
}

/** 外付け バーコードリーダー：キー を ためて Enter で 1 つ */
@Composable
fun Modifier.scannerKeys(onCode: (String) -> Unit): Modifier {
    val focus = remember { FocusRequester() }
    val buf = remember { StringBuilder() }
    val cb = rememberUpdatedState(onCode)
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
    return this.focusRequester(focus).focusable().onPreviewKeyEvent { e ->
        if (e.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
        if (e.key == Key.Enter || e.key == Key.NumPadEnter) {
            val s = buf.toString(); buf.clear()
            if (s.isNotBlank()) cb.value(s)
            return@onPreviewKeyEvent s.isNotBlank()
        }
        val ch = e.utf16CodePoint
        if (ch in 0x21..0x7E) { buf.append(ch.toChar()); return@onPreviewKeyEvent true }
        false
    }
}
