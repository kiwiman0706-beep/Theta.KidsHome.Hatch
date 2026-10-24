package com.theta.kidshome.data

import android.appwidget.AppWidgetHost
import android.appwidget.AppWidgetHostView
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.util.SizeF

/**
 * Android のウィジェットを「ウィジェット」タブに置くためのホスト（Theta Home Hatch と同じしくみ）。
 * 置くときは「割り当ての許可」と、必要ならそのウィジェット自身の設定画面を通る。
 */
object Widgets {
    private const val HOST_ID = 7105
    private lateinit var app: Context
    private val host by lazy { AppWidgetHost(app, HOST_ID) }
    val manager: AppWidgetManager by lazy { AppWidgetManager.getInstance(app) }
    private val views = HashMap<Int, AppWidgetHostView>()

    fun init(context: Context) {
        if (!::app.isInitialized) app = context.applicationContext
    }

    fun providers(): List<AppWidgetProviderInfo> =
        manager.installedProviders.sortedBy { it.loadLabel(app.packageManager).toString() }

    fun label(info: AppWidgetProviderInfo): String = info.loadLabel(app.packageManager).toString()

    fun info(id: Int): AppWidgetProviderInfo? = manager.getAppWidgetInfo(id)

    fun allocate(): Int = host.allocateAppWidgetId()

    fun bindIfAllowed(id: Int, info: AppWidgetProviderInfo): Boolean = manager.bindAppWidgetIdIfAllowed(id, info.provider)

    fun bindIntent(id: Int, info: AppWidgetProviderInfo): Intent =
        Intent(AppWidgetManager.ACTION_APPWIDGET_BIND)
            .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id)
            .putExtra(AppWidgetManager.EXTRA_APPWIDGET_PROVIDER, info.provider)

    fun configureIntent(id: Int): Intent? = info(id)?.configure?.let {
        Intent(AppWidgetManager.ACTION_APPWIDGET_CONFIGURE).setComponent(it).putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id)
    }

    fun release(id: Int) {
        views.remove(id)
        runCatching { host.deleteAppWidgetId(id) }
    }

    /** 描き直すたびに作り直さないよう、ビューを使い回す */
    fun view(context: Context, id: Int): AppWidgetHostView? {
        views[id]?.let { v ->
            (v.parent as? android.view.ViewGroup)?.removeView(v)
            return v
        }
        val info = info(id) ?: return null
        return runCatching { host.createView(context, id, info) }.getOrNull()?.also { views[id] = it }
    }

    fun resize(v: AppWidgetHostView, wDp: Int, hDp: Int) {
        if (Build.VERSION.SDK_INT >= 31) {
            v.updateAppWidgetSize(Bundle(), listOf(SizeF(wDp.toFloat(), hDp.toFloat())))
        } else {
            @Suppress("DEPRECATION")
            v.updateAppWidgetSize(null, wDp, hDp, wDp, hDp)
        }
    }

    fun start() = runCatching { if (::app.isInitialized) host.startListening() }

    fun stop() = runCatching { if (::app.isInitialized) host.stopListening() }
}
