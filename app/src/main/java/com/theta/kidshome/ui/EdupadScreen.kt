package com.theta.kidshome.ui

import android.annotation.SuppressLint
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.print.PrintAttributes
import android.print.PrintManager
import android.provider.MediaStore
import android.util.Base64
import android.webkit.JavascriptInterface
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.FileProvider
import androidx.webkit.WebViewAssetLoader
import com.theta.kidshome.data.Store
import java.io.File

/** 内蔵の くるくる思考パッド（assets/edupad、オフラインで動く Web アプリ） */
private const val EDUPAD_URL = "https://appassets.androidplatform.net/assets/edupad/index.html"

private fun assetLoader(context: Context) = WebViewAssetLoader.Builder()
    .addPathHandler("/assets/", WebViewAssetLoader.AssetsPathHandler(context))
    .build()

private class LocalClient(private val loader: WebViewAssetLoader, private val onFinished: (WebView) -> Unit = {}) : WebViewClient() {
    override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse? =
        loader.shouldInterceptRequest(request.url)

    // 内蔵のページ以外（外のサイト）へは行かせない
    override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean =
        request.url.host != "appassets.androidplatform.net"

    override fun onPageFinished(view: WebView, url: String?) = onFinished(view)
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun EdupadScreen(onBack: () -> Unit) {
    BackHandler(onBack = onBack)
    val context = LocalContext.current
    val theme = themeById(Store.config.currentProfile.themeId)
    val pendingFiles = remember { arrayOfNulls<ValueCallback<Array<Uri>>>(1) }
    val pick = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        pendingFiles[0]?.onReceiveValue(uris.toTypedArray())
        pendingFiles[0] = null
    }

    val webView = remember {
        WebView(context).apply {
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.allowFileAccess = false
            settings.mediaPlaybackRequiresUserGesture = false
            webViewClient = LocalClient(assetLoader(context))
            webChromeClient = object : WebChromeClient() {
                override fun onShowFileChooser(view: WebView, callback: ValueCallback<Array<Uri>>, params: FileChooserParams): Boolean {
                    pendingFiles[0]?.onReceiveValue(null)
                    pendingFiles[0] = callback
                    val types = params.acceptTypes.filter { it.isNotBlank() }.ifEmpty { listOf("*/*") }
                    runCatching { pick.launch(types.toTypedArray()) }.onFailure {
                        pendingFiles[0] = null
                        callback.onReceiveValue(null)
                    }
                    return true
                }
            }
            addJavascriptInterface(EdupadBridge(context), "KidsHome")
            loadUrl(EDUPAD_URL)
        }
    }
    DisposableEffect(Unit) {
        onDispose {
            pendingFiles[0]?.onReceiveValue(null)
            webView.destroy()
        }
    }

    Box(Modifier.fillMaxSize()) {
        AndroidView(factory = { webView }, modifier = Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing))
        // もどるボタン（左下。アプリの見出しにかぶらない場所）
        Box(Modifier.align(Alignment.BottomStart).windowInsetsPadding(WindowInsets.safeDrawing).padding(12.dp)) {
            RetroIconButton("🏠", theme, size = 52.dp, onClick = onBack)
        }
    }
}

/** Web アプリからの 共有・保存・印刷 を受ける（kidshome-bridge.js から呼ばれる） */
private class EdupadBridge(context: Context) {
    private val app = context.applicationContext
    private val activity = context
    private val main = Handler(Looper.getMainLooper())
    private var printing: WebView? = null

    @JavascriptInterface
    fun share(base64: String, name: String, mime: String, title: String) {
        val file = runCatching {
            val dir = File(app.cacheDir, "export").apply { mkdirs() }
            File(dir, name.replace('/', '_')).apply { writeBytes(Base64.decode(base64, Base64.DEFAULT)) }
        }.getOrNull() ?: return
        main.post {
            val uri = FileProvider.getUriForFile(app, "${app.packageName}.files", file)
            val send = Intent(Intent.ACTION_SEND).setType(mime).putExtra(Intent.EXTRA_STREAM, uri)
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            runCatching { activity.startActivity(Intent.createChooser(send, title.ifBlank { name })) }
        }
    }

    @JavascriptInterface
    fun save(base64: String, name: String, mime: String) {
        val ok = runCatching {
            val image = mime.startsWith("image/")
            val values = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, name)
                put(MediaStore.MediaColumns.MIME_TYPE, mime)
                put(
                    MediaStore.MediaColumns.RELATIVE_PATH,
                    (if (image) Environment.DIRECTORY_PICTURES else Environment.DIRECTORY_DOWNLOADS) + "/KidsHome",
                )
            }
            val collection = if (image) MediaStore.Images.Media.EXTERNAL_CONTENT_URI else MediaStore.Downloads.EXTERNAL_CONTENT_URI
            val uri = app.contentResolver.insert(collection, values) ?: error("insert")
            app.contentResolver.openOutputStream(uri)?.use { it.write(Base64.decode(base64, Base64.DEFAULT)) } ?: error("open")
            image
        }
        main.post {
            Toast.makeText(
                app,
                when (ok.getOrNull()) {
                    true -> "「写真」に保存しました"
                    false -> "「ダウンロード」に保存しました"
                    null -> "保存できませんでした"
                },
                Toast.LENGTH_SHORT,
            ).show()
        }
    }

    /** 印刷：そのページだけを別の WebView で開いて、Android の印刷へ */
    @SuppressLint("SetJavaScriptEnabled")
    @JavascriptInterface
    fun print(url: String) {
        main.post {
            val pm = activity.getSystemService(PrintManager::class.java) ?: return@post
            val w = WebView(activity)
            w.settings.javaScriptEnabled = true
            w.webViewClient = LocalClient(assetLoader(app)) { view ->
                runCatching {
                    pm.print("くるくる思考パッド", view.createPrintDocumentAdapter("kurukuru"), PrintAttributes.Builder().setMediaSize(PrintAttributes.MediaSize.ISO_A4).build())
                }
            }
            printing = w
            w.loadUrl(url)
        }
    }
}
