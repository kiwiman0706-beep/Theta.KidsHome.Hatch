package com.theta.kidshome.ui

import android.Manifest
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Environment
import android.provider.MediaStore
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import com.theta.kidshome.data.StampLibrary
import com.theta.kidshome.data.Store
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.math.cos
import kotlin.math.sin

/**
 * 📸 プリクラ：インカメラ で 3・2・1 → らくがき（フレーム・スタンプ・ペン・ひづけ）→ instax mini Link・ほぞん・いんさつ。
 * でき あがり は たて 3：よこ… ではなく よこ 3：たて 4（instax mini の しゃしん と おなじ 形）。
 */
private const val OUT_W = 1200
private const val OUT_H = 1600

private sealed interface PDeco
/** x, y, size は 0〜1（はば を 1 と して） */
private data class DStamp(val x: Float, val y: Float, val size: Float, val piece: String) : PDeco
private data class DPen(val pts: List<Offset>, val color: Int, val width: Float) : PDeco

private val FRAMES = listOf("なし", "しろ", "ハート", "ほし", "リボン", "にじ")
private val PENS = listOf(0xFFFF4FA3, 0xFFFFD84D, 0xFF4FC3FF, 0xFF7CFF8A, 0xFFB36BFF, 0xFFFFFFFF, 0xFF222222).map { it.toInt() }
private val EMOJI = listOf("💖", "⭐", "✨", "🎀", "🌸", "👑", "🐰", "🐱", "🍓", "🌈", "🦄", "💎")

@Composable
fun PurikuraScreen(onBack: () -> Unit) {
    BackHandler(onBack = onBack)
    val context = LocalContext.current
    val theme = themeById(Store.config.currentProfile.themeId)
    var granted by remember { mutableStateOf(context.checkSelfPermission(Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) }
    val ask = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted = it }
    LaunchedEffect(Unit) { if (!granted) ask.launch(Manifest.permission.CAMERA) }
    var shots by remember { mutableStateOf<List<Bitmap>?>(null) }
    var four by remember { mutableStateOf(false) }
    Box(Modifier.fillMaxSize().background(Color(0xFFFFE6F2)).windowInsetsPadding(WindowInsets.safeDrawing).padding(12.dp)) {
        val s = shots
        when {
            !granted -> Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
                RetroIconButton("🏠", theme, onClick = onBack)
                DotText("📷 カメラ を つかって いい か きいて ね", theme, 22)
                RetroButton("もう いちど", theme, theme.titleBars[0]) { ask.launch(Manifest.permission.CAMERA) }
            }
            s == null -> Shoot(theme, four, { four = it }, onBack) { shots = it }
            else -> Decorate(theme, s, four, onRetake = { shots = null }, onBack = onBack)
        }
    }
}

// ---------------------------------------------------------------- とる

@Composable
private fun Shoot(theme: KidTheme, four: Boolean, setFour: (Boolean) -> Unit, onBack: () -> Unit, onDone: (List<Bitmap>) -> Unit) {
    val context = LocalContext.current
    val owner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()
    val speak = rememberSpeaker()
    var capture by remember { mutableStateOf<ImageCapture?>(null) }
    var count by remember { mutableIntStateOf(0) }
    var flash by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    val got = remember { mutableStateListOf<Bitmap>() }
    val preview = remember { PreviewView(context).apply { scaleType = PreviewView.ScaleType.FILL_CENTER; implementationMode = PreviewView.ImplementationMode.COMPATIBLE } }
    DisposableEffect(owner) {
        val future = ProcessCameraProvider.getInstance(context)
        future.addListener({
            runCatching {
                val provider = future.get()
                provider.unbindAll()
                val p = Preview.Builder().build().also { it.setSurfaceProvider(preview.surfaceProvider) }
                val cap = ImageCapture.Builder().setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY).build()
                val sel = if (provider.hasCamera(CameraSelector.DEFAULT_FRONT_CAMERA)) CameraSelector.DEFAULT_FRONT_CAMERA else CameraSelector.DEFAULT_BACK_CAMERA
                provider.bindToLifecycle(owner, sel, p, cap)
                capture = cap
            }.onFailure { Toast.makeText(context, "カメラ が ひらけません（${it.message}）", Toast.LENGTH_LONG).show() }
        }, ContextCompat.getMainExecutor(context))
        onDispose { runCatching { future.get().unbindAll() } }
    }
    val tone = remember { runCatching { ToneGenerator(AudioManager.STREAM_MUSIC, 80) }.getOrNull() }

    /** now：「はい チーズ」と きこえた とき は 1 まい め を すぐ とる */
    fun start(now: Boolean = false) {
        val cap = capture ?: return
        if (busy) return
        busy = true; got.clear()
        scope.launch {
            val n = if (four) 4 else 1
            repeat(n) { k ->
                if (now && k == 0) { delay(150) } else {
                    speak(if (k == 0) "いくよ！" else "つぎ！")
                    for (i in 3 downTo 1) { count = i; tone?.startTone(ToneGenerator.TONE_PROP_BEEP, 120); delay(800) }
                    count = 0; speak("はい、チーズ")
                    delay(350)
                }
                val b = runCatching { cap.shot(context) }.getOrNull()
                flash = true; tone?.startTone(ToneGenerator.TONE_PROP_ACK, 150); delay(180); flash = false
                if (b != null) got += b
                delay(500)
            }
            busy = false
            if (got.isNotEmpty()) onDone(got.toList())
        }
    }

    // 🎤「はい チーズ」で シャッター（とって いる あいだ は きかない：じぶん の こえ を ひろわない ため）
    val heard = CheeseListener(enabled = !busy && capture != null) { start(now = true) }

    Row(Modifier.fillMaxSize()) {
        Box(Modifier.fillMaxHeight().aspectRatio(3f / 4f).clip(RoundedCornerShape(20.dp)).border(5.dp, Color(0xFFFF4FA3), RoundedCornerShape(20.dp))) {
            AndroidView({ preview }, Modifier.fillMaxSize())
            if (four) Box(Modifier.fillMaxSize().padding(16.dp), contentAlignment = Alignment.TopEnd) {
                DotText("${got.size + (if (busy) 1 else 0)} / 4", theme, 26, Color.White)
            }
            if (count > 0) Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("$count", fontSize = 160.sp, fontWeight = FontWeight.Bold, color = Color.White)
            }
            if (flash) Box(Modifier.fillMaxSize().background(Color.White))
        }
        Column(Modifier.weight(1f).fillMaxHeight().padding(start = 16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                RetroIconButton("🏠", theme, onClick = onBack)
                DotText("📸 プリクラ", theme, 30, theme.text, modifier = Modifier.padding(start = 10.dp))
            }
            DotText("↑ うえ の カメラ を 見て ね", theme, 18, modifier = Modifier.padding(top = 12.dp))
            DotText(if (heard) "🎤「はい チーズ！」で も とれるよ" else "🎤 こえ で とる に は マイク を ゆるして ね", theme, 16, if (heard) Color(0xFFFF4FA3) else theme.outline, modifier = Modifier.padding(top = 6.dp))
            Row(Modifier.padding(top = 16.dp)) {
                RetroButton("1 まい", theme, if (!four) theme.titleBars[1] else Color.White, fontSize = 20, modifier = Modifier.padding(end = 8.dp)) { if (!busy) setFour(false) }
                RetroButton("4 まい", theme, if (four) theme.titleBars[1] else Color.White, fontSize = 20) { if (!busy) setFour(true) }
            }
            Spacer(Modifier.weight(1f))
            Box(
                Modifier.size(170.dp).clip(CircleShape).background(if (busy) Color(0xFFDDDDDD) else Color(0xFFFF4FA3))
                    .border(6.dp, Color.White, CircleShape).clickable(enabled = !busy && capture != null) { start() },
                contentAlignment = Alignment.Center,
            ) { Text(if (busy) "📸" else "とる！", fontSize = 40.sp, fontWeight = FontWeight.Bold, color = Color.White) }
            Spacer(Modifier.weight(1f))
        }
    }
}

/**
 * 「チーズ」が きこえたら onCheese。きいて いる あいだ は true を かえす。
 * Android の 音声認識 を くりかえし つかう（とぎれたら また はじめる）。
 */
@Composable
private fun CheeseListener(enabled: Boolean, onCheese: () -> Unit): Boolean {
    val context = LocalContext.current
    var mic by remember { mutableStateOf(context.checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) }
    val ask = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { mic = it }
    LaunchedEffect(Unit) { if (!mic) ask.launch(Manifest.permission.RECORD_AUDIO) }
    val available = remember { android.speech.SpeechRecognizer.isRecognitionAvailable(context) }
    val cb = androidx.compose.runtime.rememberUpdatedState(onCheese)
    var listening by remember { mutableStateOf(false) }
    DisposableEffect(mic, enabled, available) {
        if (!mic || !enabled || !available) { listening = false; return@DisposableEffect onDispose { } }
        val sr = android.speech.SpeechRecognizer.createSpeechRecognizer(context)
        val intent = Intent(android.speech.RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
            .putExtra(android.speech.RecognizerIntent.EXTRA_LANGUAGE_MODEL, android.speech.RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            .putExtra(android.speech.RecognizerIntent.EXTRA_LANGUAGE, "ja-JP")
            .putExtra(android.speech.RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            .putExtra(android.speech.RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
        var alive = true; var fired = false
        val handler = android.os.Handler(android.os.Looper.getMainLooper())
        fun again() { if (alive && !fired) handler.postDelayed({ if (alive) runCatching { sr.startListening(intent) } }, 300) }
        fun check(b: android.os.Bundle?): Boolean {
            val words = b?.getStringArrayList(android.speech.SpeechRecognizer.RESULTS_RECOGNITION).orEmpty()
            if (!fired && words.any { w -> listOf("チーズ", "ちーず", "cheese", "Cheese").any { it in w } }) {
                fired = true; runCatching { sr.cancel() }; cb.value(); return true
            }
            return false
        }
        sr.setRecognitionListener(object : android.speech.RecognitionListener {
            override fun onReadyForSpeech(params: android.os.Bundle?) { listening = true }
            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(rmsdB: Float) {}
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEndOfSpeech() {}
            override fun onError(error: Int) { again() }
            override fun onResults(results: android.os.Bundle?) { if (!check(results)) again() }
            override fun onPartialResults(partialResults: android.os.Bundle?) { check(partialResults) }
            override fun onEvent(eventType: Int, params: android.os.Bundle?) {}
        })
        runCatching { sr.startListening(intent) }
        onDispose { alive = false; listening = false; handler.removeCallbacksAndMessages(null); runCatching { sr.cancel(); sr.destroy() } }
    }
    return mic && available
}

/** 1 まい とる（かがみ の よう に、むき を なおす） */
private suspend fun ImageCapture.shot(context: Context): Bitmap = suspendCancellableCoroutine { cont ->
    takePicture(ContextCompat.getMainExecutor(context), object : ImageCapture.OnImageCapturedCallback() {
        override fun onCaptureSuccess(image: ImageProxy) {
            val raw = image.toBitmap(); val rot = image.imageInfo.rotationDegrees
            image.close()
            val m = Matrix().apply { postRotate(rot.toFloat()); postScale(-1f, 1f) }
            val big = Bitmap.createBitmap(raw, 0, 0, raw.width, raw.height, m, true)
            val s = minOf(1f, 1600f / maxOf(big.width, big.height))
            cont.resume(if (s < 1f) Bitmap.createScaledBitmap(big, (big.width * s).toInt(), (big.height * s).toInt(), true) else big)
        }
        override fun onError(exception: ImageCaptureException) { cont.resumeWithException(exception) }
    })
}

// ---------------------------------------------------------------- らくがき

@Composable
private fun Decorate(theme: KidTheme, shots: List<Bitmap>, four: Boolean, onRetake: () -> Unit, onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var pretty by remember { mutableStateOf(true) }
    var frame by remember { mutableIntStateOf(2) }
    var frameColor by remember { mutableIntStateOf(PENS[0]) }
    var date by remember { mutableStateOf(true) }
    val decos = remember { mutableStateListOf<PDeco>() }
    var tool by remember { mutableStateOf("pen") } // pen / stamp
    var pen by remember { mutableIntStateOf(PENS[0]) }
    var piece by remember { mutableStateOf(EMOJI[0]) }
    var stampSize by remember { mutableFloatStateOf(0.16f) }
    val live = remember { mutableStateListOf<Offset>() }
    var box by remember { mutableStateOf(IntSize.Zero) }
    val base = remember(shots, four, pretty) { composePhotos(shots, four, pretty, 600, 800) }
    val previewBmp = remember(base, frame, frameColor, date, decos.size) { renderPurikura(base, frame, frameColor, date, decos.toList(), 600, 800).asImageBitmap() }
    val mine = remember(StampLibrary.version) { StampLibrary.mine() }

    fun final(): Bitmap = renderPurikura(composePhotos(shots, four, pretty, OUT_W, OUT_H), frame, frameColor, date, decos.toList(), OUT_W, OUT_H)

    Row(Modifier.fillMaxSize()) {
        Box(
            Modifier.fillMaxHeight().aspectRatio(3f / 4f).clip(RoundedCornerShape(12.dp)).onSizeChanged { box = it }
                .pointerInput(tool, pen, piece, stampSize) {
                    awaitEachGesture {
                        val down = awaitFirstDown()
                        fun norm(o: Offset) = Offset(o.x / size.width, o.y / size.width) // はば を 1 に
                        if (tool == "stamp") { decos += DStamp(down.position.x / size.width, down.position.y / size.width, stampSize, piece); return@awaitEachGesture }
                        live.clear(); live += norm(down.position)
                        while (true) {
                            val ev = awaitPointerEvent()
                            val ch = ev.changes.firstOrNull { it.id == down.id } ?: break
                            if (!ch.pressed) break
                            live += norm(ch.position); ch.consume()
                        }
                        if (live.size > 1) decos += DPen(live.toList(), pen, 0.018f)
                        live.clear()
                    }
                },
        ) {
            Image(previewBmp, null, Modifier.fillMaxSize(), contentScale = ContentScale.FillBounds)
            androidx.compose.foundation.Canvas(Modifier.fillMaxSize()) {
                if (live.size > 1) {
                    val w = size.width
                    val path = androidx.compose.ui.graphics.Path().apply { moveTo(live[0].x * w, live[0].y * w); live.drop(1).forEach { lineTo(it.x * w, it.y * w) } }
                    drawPath(path, Color(pen).copy(alpha = 0.55f), style = Stroke(0.018f * w * 1.8f, cap = StrokeCap.Round, join = StrokeJoin.Round))
                    drawPath(path, Color.White, style = Stroke(0.018f * w * 0.45f, cap = StrokeCap.Round, join = StrokeJoin.Round))
                }
            }
        }
        Column(Modifier.weight(1f).fillMaxHeight().padding(start = 12.dp).verticalScroll(rememberScrollState())) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                RetroIconButton("🏠", theme, size = 48.dp, onClick = onBack)
                RetroButton("🔁 とりなおす", theme, Color.White, fontSize = 15, modifier = Modifier.padding(start = 6.dp)) { onRetake() }
                RetroButton("↩️", theme, Color.White, fontSize = 15, modifier = Modifier.padding(start = 6.dp)) { if (decos.isNotEmpty()) decos.removeAt(decos.lastIndex) }
                RetroButton("🧽", theme, Color.White, fontSize = 15, modifier = Modifier.padding(start = 6.dp)) { decos.clear() }
            }
            DotText("フレーム", theme, 16, modifier = Modifier.padding(top = 8.dp))
            Row(Modifier.horizontalScroll(rememberScrollState())) {
                FRAMES.forEachIndexed { i, f -> RetroButton(f, theme, if (frame == i) theme.titleBars[1] else Color.White, fontSize = 14, modifier = Modifier.padding(end = 4.dp)) { frame = i } }
            }
            Row(Modifier.padding(top = 4.dp)) {
                PENS.forEach { c -> Box(Modifier.padding(3.dp).size(34.dp).clip(CircleShape).background(Color(c)).border(if (frameColor == c) 4.dp else 2.dp, theme.outline, CircleShape).clickable { frameColor = c }) }
            }
            Row(Modifier.padding(top = 6.dp)) {
                RetroButton(if (pretty) "✨ きらきら ON" else "✨ きらきら", theme, if (pretty) theme.titleBars[2] else Color.White, fontSize = 14, modifier = Modifier.padding(end = 6.dp)) { pretty = !pretty }
                RetroButton(if (date) "📅 ひづけ ON" else "📅 ひづけ", theme, if (date) theme.titleBars[2] else Color.White, fontSize = 14) { date = !date }
            }
            DotText("らくがき", theme, 16, modifier = Modifier.padding(top = 8.dp))
            Row {
                RetroButton("🖊 ペン", theme, if (tool == "pen") theme.titleBars[0] else Color.White, fontSize = 15, modifier = Modifier.padding(end = 6.dp)) { tool = "pen" }
                RetroButton("🌟 スタンプ", theme, if (tool == "stamp") theme.titleBars[0] else Color.White, fontSize = 15) { tool = "stamp" }
            }
            if (tool == "pen") Row(Modifier.padding(top = 4.dp)) {
                PENS.forEach { c -> Box(Modifier.padding(3.dp).size(38.dp).clip(CircleShape).background(Color(c)).border(if (pen == c) 4.dp else 2.dp, theme.outline, CircleShape).clickable { pen = c }) }
            } else {
                Row(Modifier.padding(top = 4.dp).horizontalScroll(rememberScrollState())) {
                    mine.forEach { s -> StampThumb(theme, s.id, piece == "img:${s.id}", 52.dp) { piece = "img:${s.id}" } }
                    EMOJI.forEach { e -> RetroIconButton(e, theme, piece == e, 48.dp) { piece = e } }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    DotText("おおきさ", theme, 14)
                    RetroButton("－", theme, Color.White, fontSize = 16, modifier = Modifier.padding(start = 6.dp)) { stampSize = (stampSize - 0.04f).coerceAtLeast(0.06f) }
                    RetroButton("＋", theme, Color.White, fontSize = 16, modifier = Modifier.padding(start = 6.dp)) { stampSize = (stampSize + 0.04f).coerceAtMost(0.4f) }
                }
            }
            DotText("できた！", theme, 16, modifier = Modifier.padding(top = 10.dp))
            Row {
                RetroButton("🖨 instax", theme, Color(0xFFFFB3D9), fontSize = 16, modifier = Modifier.padding(end = 6.dp)) {
                    scope.launch { val b = withContext(Dispatchers.Default) { final() }; sendToInstax(context, b) }
                }
                RetroButton("💾 ほぞん", theme, Color.White, fontSize = 16, modifier = Modifier.padding(end = 6.dp)) {
                    scope.launch {
                        val ok = withContext(Dispatchers.IO) { savePurikura(context, final()) }
                        Toast.makeText(context, if (ok) "「しゃしん」に いれたよ！" else "いれられなかったよ", Toast.LENGTH_SHORT).show()
                    }
                }
                RetroButton("🖨 いんさつ", theme, Color.White, fontSize = 16) {
                    scope.launch { val b = withContext(Dispatchers.Default) { final() }; androidx.print.PrintHelper(context).apply { scaleMode = androidx.print.PrintHelper.SCALE_MODE_FIT }.printBitmap("プリクラ", b) }
                }
            }
            // 🌟 スタンプ に する（しかく：かどまる＋しろ ふち／きりぬき：Gemini で ひと だけ）
            var stampBusy by remember { mutableStateOf<String?>(null) }
            Row(Modifier.padding(top = 6.dp)) {
                RetroButton("🌟 スタンプ に する", theme, theme.titleBars[3], fontSize = 15, modifier = Modifier.padding(end = 6.dp)) {
                    if (stampBusy != null) return@RetroButton
                    stampBusy = "つくって いるよ…"
                    scope.launch {
                        runCatching { withContext(Dispatchers.Default) { StampLibrary.add(StampLibrary.finish(roundPhoto(final()), true), "プリクラ") } }
                        stampBusy = null
                        Toast.makeText(context, "スタンプ に なったよ！ おえかき・すごろく・チャット で つかえるよ", Toast.LENGTH_LONG).show()
                    }
                }
                if (com.theta.kidshome.data.Gemini.apiKey(context).isNotBlank()) RetroButton("✨ きりぬき スタンプ", theme, theme.titleBars[2], fontSize = 15) {
                    if (stampBusy != null) return@RetroButton
                    stampBusy = "きりぬいて いるよ…"
                    scope.launch {
                        val r = runCatching {
                            val src = withContext(Dispatchers.Default) { composePhotos(shots.take(1), false, pretty, 900, 1200) }
                            val cut = StampLibrary.chromaKey(com.theta.kidshome.data.Gemini.cutout(context, src))
                            withContext(Dispatchers.Default) { StampLibrary.add(StampLibrary.finish(cut, true), "プリクラ") }
                        }
                        stampBusy = null
                        Toast.makeText(context, if (r.isSuccess) "きりぬき スタンプ が できたよ！" else "できなかったよ（${r.exceptionOrNull()?.message}）", Toast.LENGTH_LONG).show()
                    }
                }
            }
            stampBusy?.let { DotText(it, theme, 14) }
        }
    }
}

// ---------------------------------------------------------------- 絵 を つくる

/** かど を まるく した しゃしん（スタンプ 用。まわり は すける） */
private fun roundPhoto(src: Bitmap): Bitmap {
    val s = 600f / maxOf(src.width, src.height)
    val w = (src.width * s).toInt(); val h = (src.height * s).toInt()
    val out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
    val c = Canvas(out)
    c.clipPath(Path().apply { addRoundRect(RectF(0f, 0f, w.toFloat(), h.toFloat()), w * 0.08f, w * 0.08f, Path.Direction.CW) })
    c.drawBitmap(src, null, RectF(0f, 0f, w.toFloat(), h.toFloat()), Paint(Paint.FILTER_BITMAP_FLAG))
    return out
}

/** しゃしん を ならべる（1 まい：いっぱい、4 まい：2×2）。きらきら：すこし あかるく・あざやか に */
private fun composePhotos(shots: List<Bitmap>, four: Boolean, pretty: Boolean, w: Int, h: Int): Bitmap {
    val out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
    val c = Canvas(out); c.drawColor(android.graphics.Color.WHITE)
    val p = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG)
    if (pretty) p.colorFilter = ColorMatrixColorFilter(ColorMatrix().apply {
        setSaturation(1.18f)
        postConcat(ColorMatrix(floatArrayOf(1.06f, 0f, 0f, 0f, 22f, 0f, 1.04f, 0f, 0f, 18f, 0f, 0f, 1.04f, 0f, 20f, 0f, 0f, 0f, 1f, 0f)))
    })
    fun put(b: Bitmap, r: RectF) {
        val sr = r.width() / r.height(); val br = b.width.toFloat() / b.height
        val src = if (br > sr) { val cw = (b.height * sr).toInt(); android.graphics.Rect((b.width - cw) / 2, 0, (b.width + cw) / 2, b.height) }
                  else { val ch = (b.width / sr).toInt(); android.graphics.Rect(0, (b.height - ch) / 2, b.width, (b.height + ch) / 2) }
        c.drawBitmap(b, src, r, p)
    }
    if (!four || shots.size < 2) put(shots.first(), RectF(0f, 0f, w.toFloat(), h.toFloat()))
    else {
        val m = w * 0.075f; val g = w * 0.025f
        val cw = (w - m * 2 - g) / 2; val ch = (h - m * 2 - g) / 2
        shots.take(4).forEachIndexed { i, b ->
            val x = m + (i % 2) * (cw + g); val y = m + (i / 2) * (ch + g)
            put(b, RectF(x, y, x + cw, y + ch))
        }
    }
    return out
}

private fun heart(x: Float, y: Float, s: Float) = Path().apply {
    moveTo(x, y + s * 0.35f)
    cubicTo(x - s * 0.9f, y - s * 0.35f, x - s * 0.35f, y - s * 0.95f, x, y - s * 0.4f)
    cubicTo(x + s * 0.35f, y - s * 0.95f, x + s * 0.9f, y - s * 0.35f, x, y + s * 0.35f); close()
}

private fun star(x: Float, y: Float, r: Float) = Path().apply {
    for (k in 0 until 10) {
        val a = -Math.PI / 2 + Math.PI * k / 5; val rr = if (k % 2 == 0) r else r * 0.45f
        val px = x + rr * cos(a).toFloat(); val py = y + rr * sin(a).toFloat()
        if (k == 0) moveTo(px, py) else lineTo(px, py)
    }
    close()
}

/** フレーム・らくがき・ひづけ を かさねる */
private fun renderPurikura(base: Bitmap, frame: Int, frameColor: Int, date: Boolean, decos: List<PDeco>, w: Int, h: Int): Bitmap {
    val out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
    val c = Canvas(out)
    c.drawBitmap(base, null, RectF(0f, 0f, w.toFloat(), h.toFloat()), Paint(Paint.FILTER_BITMAP_FLAG))
    val p = Paint(Paint.ANTI_ALIAS_FLAG)
    val band = w * 0.06f
    fun bandPath() = Path().apply { fillType = Path.FillType.EVEN_ODD; addRect(0f, 0f, w.toFloat(), h.toFloat(), Path.Direction.CW); addRoundRect(RectF(band, band, w - band, h - band), band, band, Path.Direction.CW) }
    when (FRAMES.getOrNull(frame)) {
        "しろ" -> { p.color = android.graphics.Color.WHITE; c.drawPath(bandPath(), p) }
        "ハート", "ほし", "リボン" -> {
            p.color = frameColor; c.drawPath(bandPath(), p)
            p.color = if (frameColor == android.graphics.Color.WHITE) 0xFFFF4FA3.toInt() else android.graphics.Color.WHITE
            val n = 7
            for (k in 0..n) {
                val x = band / 2 + (w - band) * k / n; val y = band / 2 + (h - band) * k / n
                val s = band * 0.7f
                listOf(x to band / 2, x to h - band / 2, band / 2 to y, w - band / 2 to y).forEach { (px, py) ->
                    when (FRAMES[frame]) { "ほし" -> c.drawPath(star(px, py, s * 0.6f), p); "ハート" -> c.drawPath(heart(px, py, s * 0.75f), p); else -> c.drawCircle(px, py, s * 0.22f, p) }
                }
            }
            if (FRAMES[frame] == "リボン") {
                p.color = frameColor
                val cx = w / 2f; val cy = band * 1.1f; val r = band * 1.6f
                c.drawPath(Path().apply { moveTo(cx, cy); lineTo(cx - r * 1.4f, cy - r * 0.7f); lineTo(cx - r * 1.4f, cy + r * 0.7f); close() }, p)
                c.drawPath(Path().apply { moveTo(cx, cy); lineTo(cx + r * 1.4f, cy - r * 0.7f); lineTo(cx + r * 1.4f, cy + r * 0.7f); close() }, p)
                c.drawCircle(cx, cy, r * 0.38f, p)
                p.style = Paint.Style.STROKE; p.strokeWidth = band * 0.12f; p.color = android.graphics.Color.WHITE
                c.drawCircle(cx, cy, r * 0.38f, p); p.style = Paint.Style.FILL
            }
        }
        "にじ" -> {
            val cols = listOf(0xFFFF5A5A, 0xFFFFA64D, 0xFFFFE04D, 0xFF6BD96B, 0xFF5AB4FF, 0xFFB07CFF)
            cols.forEachIndexed { i, col ->
                val inset = band * i / cols.size
                p.color = col.toInt(); p.style = Paint.Style.STROKE; p.strokeWidth = band / cols.size + 1
                c.drawRect(RectF(inset + p.strokeWidth / 2, inset + p.strokeWidth / 2, w - inset - p.strokeWidth / 2, h - inset - p.strokeWidth / 2), p)
            }
            p.style = Paint.Style.FILL
        }
        else -> {}
    }
    decos.forEach { d ->
        when (d) {
            is DPen -> {
                val path = Path().apply { d.pts.forEachIndexed { i, o -> if (i == 0) moveTo(o.x * w, o.y * w) else lineTo(o.x * w, o.y * w) } }
                val sp = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND; strokeJoin = Paint.Join.ROUND }
                sp.color = d.color; sp.alpha = 150; sp.strokeWidth = d.width * w * 1.8f; c.drawPath(path, sp)
                sp.color = android.graphics.Color.WHITE; sp.strokeWidth = d.width * w * 0.45f; c.drawPath(path, sp)
            }
            is DStamp -> {
                val size = d.size * w; val x = d.x * w; val y = d.y * w
                if (d.piece.startsWith("img:")) {
                    StampLibrary.bitmap(d.piece.removePrefix("img:"))?.let { b ->
                        val s = size / maxOf(b.width, b.height)
                        c.drawBitmap(b, null, RectF(x - b.width * s / 2, y - b.height * s / 2, x + b.width * s / 2, y + b.height * s / 2), Paint(Paint.FILTER_BITMAP_FLAG))
                    }
                } else {
                    val tp = Paint(Paint.ANTI_ALIAS_FLAG).apply { textSize = size; textAlign = Paint.Align.CENTER }
                    c.drawText(d.piece, x, y + size / 3, tp)
                }
            }
        }
    }
    if (date) {
        val t = SimpleDateFormat("yyyy.MM.dd", Locale.JAPAN).format(Date())
        val tp = Paint(Paint.ANTI_ALIAS_FLAG).apply { textSize = w * 0.06f; typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD); textAlign = Paint.Align.RIGHT }
        val x = w - band * 1.3f; val y = h - band * 1.3f
        tp.style = Paint.Style.STROKE; tp.strokeWidth = w * 0.012f; tp.color = android.graphics.Color.WHITE; c.drawText(t, x, y, tp)
        tp.style = Paint.Style.FILL; tp.color = if (frameColor == android.graphics.Color.WHITE) 0xFFFF4FA3.toInt() else frameColor; c.drawText(t, x, y, tp)
    }
    return out
}

// ---------------------------------------------------------------- だす

private fun savePurikura(context: Context, b: Bitmap): Boolean = runCatching {
    val v = ContentValues().apply {
        put(MediaStore.Images.Media.DISPLAY_NAME, "purikura_${System.currentTimeMillis()}.jpg")
        put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
        put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/KidsHome")
    }
    val uri = context.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, v) ?: return false
    context.contentResolver.openOutputStream(uri)?.use { b.compress(Bitmap.CompressFormat.JPEG, 95, it) } ?: return false
    true
}.getOrDefault(false)

/**
 * instax mini Link の アプリ へ わたす（アプリ で プリンター を えらんで いんさつ）。
 * アプリ が ない とき は ふつう の「きょうゆう」。
 */
private fun sendToInstax(context: Context, b: Bitmap) {
    val dir = File(context.cacheDir, "export").apply { mkdirs() }
    val f = File(dir, "purikura.jpg")
    f.outputStream().use { b.compress(Bitmap.CompressFormat.JPEG, 95, it) }
    val uri = FileProvider.getUriForFile(context, context.packageName + ".files", f)
    val send = Intent(Intent.ACTION_SEND).setType("image/jpeg").putExtra(Intent.EXTRA_STREAM, uri).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    @Suppress("DEPRECATION")
    val instax = context.packageManager.queryIntentActivities(send, 0).firstOrNull { it.activityInfo.packageName.contains("instax", ignoreCase = true) }
    if (instax != null) {
        context.startActivity(Intent(send).setClassName(instax.activityInfo.packageName, instax.activityInfo.name).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    } else {
        Toast.makeText(context, "instax の アプリ が 見つからないので、えらんで ね", Toast.LENGTH_LONG).show()
        context.startActivity(Intent.createChooser(send, "プリクラ を おくる").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
}
