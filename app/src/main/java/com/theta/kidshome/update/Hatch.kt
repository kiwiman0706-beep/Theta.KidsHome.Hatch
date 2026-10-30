package com.theta.kidshome.update

import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.compose.runtime.mutableStateMapOf
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.launch

/** Theta Hatch シリーズのうち、子どもも使うアプリ（GitHub Releases から入れる） */
data class HatchApp(val name: String, val emoji: String, val description: String, val packageName: String, val repo: String)

object Hatch {
    val apps = listOf(
        HatchApp("おえかきハッチ", "🖍", "描いた絵がゲームで動く", "jp.theta.oekakihatch", "Theta.Oekaki.Hatch"),
    )

    data class State(val latest: Release? = null, val checking: Boolean = false, val progress: Float? = null, val error: String? = null)

    val states = mutableStateMapOf<String, State>()
    private val scope = MainScope()

    fun installedVersion(context: Context, app: HatchApp): String? =
        runCatching { context.packageManager.getPackageInfo(app.packageName, 0).versionName }.getOrNull()

    fun check(app: HatchApp) {
        val st = states[app.packageName] ?: State()
        if (st.checking) return
        states[app.packageName] = st.copy(checking = true, error = null)
        scope.launch {
            val r = runCatching { SelfUpdate.latestOf(app.repo) }
            states[app.packageName] = State(
                latest = r.getOrNull(),
                error = r.exceptionOrNull()?.message ?: if (r.getOrNull() == null) "見つかりませんでした" else null,
            )
        }
    }

    /** 取得して照合し、インストール画面へ */
    fun install(context: Context, app: HatchApp) {
        val rel = states[app.packageName]?.latest ?: return check(app)
        val ctx = context.applicationContext
        SelfUpdate.permissionIntent(ctx)?.let {
            Toast.makeText(ctx, "「この提供元のアプリを許可」をオンにしてから、もう一度押してください", Toast.LENGTH_LONG).show()
            ctx.startActivity(it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            return
        }
        states[app.packageName] = State(latest = rel, progress = 0f)
        scope.launch {
            val r = runCatching {
                SelfUpdate.download(ctx, rel) { p -> scope.launch { states[app.packageName] = State(latest = rel, progress = p) } }
            }
            states[app.packageName] = State(latest = rel, error = r.exceptionOrNull()?.message)
            r.getOrNull()?.let { file -> runCatching { SelfUpdate.install(ctx, file) }.onFailure { e -> states[app.packageName] = State(latest = rel, error = e.message) } }
        }
    }
}
