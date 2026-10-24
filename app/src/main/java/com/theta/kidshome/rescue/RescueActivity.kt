package com.theta.kidshome.rescue

import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.text.InputType
import android.view.Gravity
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import com.theta.kidshome.MainActivity
import kotlin.random.Random

/**
 * レスキュー画面。キッズホームが落ち続ける・固まったとき、クイック設定のタイルから開く。
 * 本体とは別のプロセスで、Compose も使わず Android の素の部品だけで描く（本体が壊れていても開けるように）。
 * 子どもが別のホームへ逃げないよう、最初にかけ算を聞く。
 */
class RescueActivity : Activity() {
    private val pink = Color.rgb(0xFF, 0x7F, 0xA8)
    private val ink = Color.rgb(0x7A, 0x3E, 0x55)
    private val paper = Color.rgb(0xFF, 0xF4, 0xF8)
    private lateinit var col: LinearLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        col = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(px(24), px(48), px(24), px(32))
        }
        setContentView(ScrollView(this).apply { setBackgroundColor(paper); addView(col) })
        showGate()
    }

    private fun px(v: Int) = (v * resources.displayMetrics.density).toInt()

    private fun text(s: String, size: Float, color: Int = ink, bold: Boolean = false) = TextView(this).apply {
        text = s
        textSize = size
        setTextColor(color)
        if (bold) typeface = Typeface.DEFAULT_BOLD
        setPadding(0, px(6), 0, px(6))
        col.addView(this)
    }

    private fun button(label: String, primary: Boolean = false, onClick: () -> Unit) = Button(this).apply {
        text = label
        isAllCaps = false
        textSize = 16f
        setTextColor(if (primary) Color.WHITE else ink)
        background = GradientDrawable().apply {
            cornerRadius = px(14).toFloat()
            setColor(if (primary) pink else Color.WHITE)
            setStroke(px(3), ink)
        }
        gravity = Gravity.CENTER
        minHeight = px(56)
        setPadding(px(12), px(10), px(12), px(10))
        setOnClickListener { onClick() }
        col.addView(this, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { topMargin = px(12) })
    }

    private fun header() {
        text("🛟 RESCUE", 13f, pink, bold = true)
        text("キッズホームのレスキュー", 26f, bold = true)
    }

    /** おうちのひと かくにん（2けた×1けた） */
    private fun showGate() {
        col.removeAllViews()
        header()
        val a = Random.nextInt(12, 99).let { if (it % 10 == 0) it + 1 else it }
        val b = Random.nextInt(3, 10)
        text("おうちのひとが こたえてね", 15f)
        text("$a × $b = ?", 32f, bold = true)
        val input = EditText(this).apply {
            inputType = InputType.TYPE_CLASS_NUMBER
            textSize = 24f
            setTextColor(ink)
            col.addView(this)
        }
        button("OK", primary = true) {
            if (input.text.toString().trim().toIntOrNull() == a * b) showMenu() else showGate()
        }
        button("とじる") { finish() }
    }

    private fun showMenu() {
        col.removeAllViews()
        header()
        val trouble = Rescue.recentTrouble(this)
        text(
            if (trouble >= Rescue.LIMIT) "キッズホームが続けて止まったので、ホームの代わりにこの画面を出しています。"
            else "キッズホームがおかしいときに、ここから逃げられます。",
            15f,
        )
        Rescue.lastTrouble(this)?.let { text("最後の不具合：$it", 12f, Color.GRAY) }

        button("別のホームアプリを選ぶ", primary = true) { Rescue.chooseHome(this) }
        text("端末に最初から入っているホームを選べば、いつもどおり使えます。戻すときはキッズホームを選び直します。", 12f, Color.GRAY)
        button("セーフモードで開く（アニメーションを止める）") { reopen(safe = true) }
        button("普通に開き直す") { reopen(safe = false) }
        button("最新版を入れる（Releases をブラウザで）") { open(Rescue.RELEASES) }
        button("Android の設定を開く") { runCatching { startActivity(Intent(Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) } }
        text("通知パネルのクイック設定に「キッズホームのレスキュー」タイルを足しておくと、ホームが固まっていてもここを開けます。", 12f, Color.GRAY)
    }

    private fun reopen(safe: Boolean) {
        Rescue.reset(this)
        startActivity(
            Intent(this, MainActivity::class.java).putExtra(Rescue.EXTRA_SAFE, safe)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        )
        finish()
    }

    private fun open(url: String) {
        runCatching { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
    }
}
