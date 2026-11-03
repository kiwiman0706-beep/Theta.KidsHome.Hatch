package com.theta.kidshome.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import java.io.File
import java.util.UUID
import kotlin.math.cos
import kotlin.math.sin

/**
 * 🖍 ぬりえ の 線画。
 * - 内蔵：アプリ の 中 で 線 を ひいた もの（著作権 の しんぱい なし）
 * - assets/nurie の png：あとから リポジトリ に 足す 素材（黒 い 線・白 か すけた 背景）
 * - 画像 から：写真・ぬりえ の 画像 を えらぶ と 黒 い 線 だけ に して 保存（filesDir/nurie）
 */
object Nurie {
    /** group：""＝内蔵、"self"＝じぶん で 足した、ほか は ぬりえ パック の 本 の id */
    data class Item(val id: String, val name: String, val group: String = "")

    const val W = 1600
    const val H = 1100

    private fun pen() = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE; strokeWidth = 11f; color = Color.BLACK
        strokeCap = Paint.Cap.ROUND; strokeJoin = Paint.Join.ROUND
    }

    private val builtins: List<Pair<Item, (Canvas, Paint) -> Unit>> = listOf(
        Item("b:apple", "りんご") to { c, p ->
            val body = Path().apply {
                moveTo(800f, 330f)
                cubicTo(650f, 220f, 430f, 300f, 450f, 560f); cubicTo(470f, 820f, 650f, 930f, 800f, 860f)
                cubicTo(950f, 930f, 1130f, 820f, 1150f, 560f); cubicTo(1170f, 300f, 950f, 220f, 800f, 330f); close()
            }
            c.drawPath(body, p)
            c.drawPath(Path().apply { moveTo(800f, 340f); quadTo(790f, 260f, 830f, 190f) }, p)
            c.drawPath(Path().apply { moveTo(830f, 240f); quadTo(920f, 150f, 1020f, 210f); quadTo(930f, 300f, 830f, 240f); close() }, p)
            c.drawOval(RectF(560f, 430f, 640f, 560f), p) // ひかり
        },
        Item("b:fish", "さかな") to { c, p ->
            c.drawOval(RectF(350f, 330f, 1100f, 780f), p)
            c.drawPath(Path().apply { moveTo(1080f, 555f); lineTo(1350f, 360f); lineTo(1320f, 555f); lineTo(1350f, 750f); close() }, p)
            c.drawCircle(520f, 500f, 40f, p); c.drawCircle(520f, 500f, 12f, p)
            c.drawPath(Path().apply { moveTo(680f, 340f); quadTo(760f, 200f, 900f, 350f) }, p)
            c.drawPath(Path().apply { moveTo(650f, 400f); quadTo(700f, 555f, 650f, 710f) }, p)
            c.drawCircle(260f, 380f, 30f, p); c.drawCircle(210f, 270f, 45f, p); c.drawCircle(290f, 180f, 25f, p)
            c.drawPath(Path().apply { moveTo(150f, 960f); quadTo(400f, 880f, 650f, 960f); quadTo(900f, 1040f, 1150f, 960f); quadTo(1350f, 900f, 1480f, 960f) }, p)
        },
        Item("b:house", "おうち") to { c, p ->
            c.drawRect(RectF(450f, 520f, 1050f, 950f), p)
            c.drawPath(Path().apply { moveTo(380f, 540f); lineTo(750f, 240f); lineTo(1120f, 540f); close() }, p)
            c.drawRect(RectF(920f, 260f, 1000f, 420f), p)
            c.drawRect(RectF(690f, 720f, 820f, 950f), p); c.drawCircle(795f, 840f, 10f, p)
            c.drawRect(RectF(520f, 600f, 640f, 710f), p); c.drawLine(580f, 600f, 580f, 710f, p); c.drawLine(520f, 655f, 640f, 655f, p)
            c.drawRect(RectF(870f, 600f, 990f, 710f), p); c.drawLine(930f, 600f, 930f, 710f, p); c.drawLine(870f, 655f, 990f, 655f, p)
            c.drawCircle(1330f, 220f, 100f, p)
            for (k in 0 until 8) { val a = Math.PI * 2 * k / 8; c.drawLine(1330f + 130f * cos(a).toFloat(), 220f + 130f * sin(a).toFloat(), 1330f + 180f * cos(a).toFloat(), 220f + 180f * sin(a).toFloat(), p) }
            c.drawLine(100f, 950f, 1500f, 950f, p)
            c.drawPath(Path().apply { moveTo(160f, 950f); quadTo(200f, 800f, 260f, 950f) }, p)
            c.drawCircle(1250f, 720f, 110f, p); c.drawRect(RectF(1225f, 830f, 1275f, 950f), p)
        },
        Item("b:cat", "ねこ") to { c, p ->
            c.drawOval(RectF(560f, 560f, 1040f, 1000f), p)
            c.drawCircle(800f, 400f, 230f, p)
            c.drawPath(Path().apply { moveTo(610f, 290f); lineTo(600f, 120f); lineTo(740f, 190f) }, p)
            c.drawPath(Path().apply { moveTo(990f, 290f); lineTo(1000f, 120f); lineTo(860f, 190f) }, p)
            c.drawOval(RectF(690f, 330f, 750f, 420f), p); c.drawOval(RectF(850f, 330f, 910f, 420f), p)
            c.drawPath(Path().apply { moveTo(780f, 460f); lineTo(820f, 460f); lineTo(800f, 485f); close() }, p)
            c.drawPath(Path().apply { moveTo(800f, 485f); quadTo(770f, 530f, 740f, 505f); moveTo(800f, 485f); quadTo(830f, 530f, 860f, 505f) }, p)
            c.drawLine(560f, 450f, 690f, 470f, p); c.drawLine(560f, 510f, 690f, 495f, p); c.drawLine(1040f, 450f, 910f, 470f, p); c.drawLine(1040f, 510f, 910f, 495f, p)
            c.drawPath(Path().apply { moveTo(1030f, 850f); cubicTo(1250f, 850f, 1250f, 600f, 1150f, 560f) }, p)
            c.drawOval(RectF(640f, 920f, 760f, 1010f), p); c.drawOval(RectF(840f, 920f, 960f, 1010f), p)
        },
        Item("b:flower", "おはな") to { c, p ->
            for (k in 0 until 6) { val a = Math.PI * 2 * k / 6; c.drawCircle(800f + 150f * cos(a).toFloat(), 330f + 150f * sin(a).toFloat(), 95f, p) }
            c.drawCircle(800f, 330f, 90f, p)
            c.drawPath(Path().apply { moveTo(800f, 480f); lineTo(800f, 820f) }, p)
            c.drawPath(Path().apply { moveTo(800f, 680f); quadTo(650f, 560f, 560f, 640f); quadTo(660f, 720f, 800f, 680f); close() }, p)
            c.drawPath(Path().apply { moveTo(800f, 620f); quadTo(950f, 500f, 1040f, 580f); quadTo(940f, 660f, 800f, 620f); close() }, p)
            c.drawPath(Path().apply { moveTo(600f, 820f); lineTo(1000f, 820f); lineTo(940f, 1020f); lineTo(660f, 1020f); close() }, p)
            c.drawLine(620f, 880f, 980f, 880f, p)
        },
        Item("b:car", "くるま") to { c, p ->
            c.drawRoundRect(RectF(250f, 520f, 1350f, 820f), 60f, 60f, p)
            c.drawPath(Path().apply { moveTo(450f, 520f); lineTo(560f, 330f); lineTo(1000f, 330f); lineTo(1130f, 520f) }, p)
            c.drawPath(Path().apply { moveTo(520f, 510f); lineTo(600f, 370f); lineTo(770f, 370f); lineTo(770f, 510f); close() }, p)
            c.drawPath(Path().apply { moveTo(820f, 510f); lineTo(820f, 370f); lineTo(980f, 370f); lineTo(1070f, 510f); close() }, p)
            c.drawCircle(500f, 830f, 120f, p); c.drawCircle(500f, 830f, 50f, p)
            c.drawCircle(1100f, 830f, 120f, p); c.drawCircle(1100f, 830f, 50f, p)
            c.drawOval(RectF(1270f, 570f, 1340f, 640f), p); c.drawLine(780f, 600f, 840f, 600f, p)
            c.drawLine(100f, 950f, 1500f, 950f, p)
        },
        Item("b:butterfly", "ちょうちょ") to { c, p ->
            c.drawOval(RectF(770f, 300f, 830f, 800f), p); c.drawCircle(800f, 260f, 50f, p)
            c.drawPath(Path().apply { moveTo(785f, 220f); quadTo(740f, 120f, 680f, 110f); moveTo(815f, 220f); quadTo(860f, 120f, 920f, 110f) }, p)
            c.drawPath(Path().apply { moveTo(770f, 400f); cubicTo(600f, 150f, 300f, 200f, 360f, 450f); cubicTo(400f, 580f, 650f, 560f, 770f, 520f); close() }, p)
            c.drawPath(Path().apply { moveTo(830f, 400f); cubicTo(1000f, 150f, 1300f, 200f, 1240f, 450f); cubicTo(1200f, 580f, 950f, 560f, 830f, 520f); close() }, p)
            c.drawPath(Path().apply { moveTo(770f, 560f); cubicTo(600f, 560f, 420f, 700f, 520f, 820f); cubicTo(620f, 900f, 740f, 760f, 770f, 680f); close() }, p)
            c.drawPath(Path().apply { moveTo(830f, 560f); cubicTo(1000f, 560f, 1180f, 700f, 1080f, 820f); cubicTo(980f, 900f, 860f, 760f, 830f, 680f); close() }, p)
            c.drawCircle(520f, 380f, 60f, p); c.drawCircle(1080f, 380f, 60f, p); c.drawCircle(600f, 720f, 40f, p); c.drawCircle(1000f, 720f, 40f, p)
        },
        Item("b:rocket", "ロケット") to { c, p ->
            c.drawPath(Path().apply { moveTo(800f, 120f); cubicTo(950f, 250f, 960f, 500f, 920f, 760f); lineTo(680f, 760f); cubicTo(640f, 500f, 650f, 250f, 800f, 120f); close() }, p)
            c.drawCircle(800f, 420f, 80f, p); c.drawCircle(800f, 420f, 50f, p)
            c.drawPath(Path().apply { moveTo(690f, 600f); lineTo(560f, 820f); lineTo(690f, 760f) }, p)
            c.drawPath(Path().apply { moveTo(910f, 600f); lineTo(1040f, 820f); lineTo(910f, 760f) }, p)
            c.drawPath(Path().apply { moveTo(720f, 760f); quadTo(800f, 1040f, 880f, 760f) }, p)
            c.drawLine(690f, 250f, 910f, 250f, p)
            fun star(x: Float, y: Float, r: Float) = Path().apply {
                for (k in 0 until 10) { val a = -Math.PI / 2 + Math.PI * k / 5; val rr = if (k % 2 == 0) r else r * 0.45f
                    val px = x + rr * cos(a).toFloat(); val py = y + rr * sin(a).toFloat(); if (k == 0) moveTo(px, py) else lineTo(px, py) }; close() }
            c.drawPath(star(300f, 250f, 80f), p); c.drawPath(star(1300f, 400f, 70f), p); c.drawPath(star(400f, 850f, 60f), p); c.drawPath(star(1250f, 900f, 90f), p)
            c.drawCircle(1200f, 180f, 70f, p)
        },
        Item("b:icecream", "アイス") to { c, p ->
            c.drawPath(Path().apply { moveTo(630f, 560f); lineTo(800f, 1030f); lineTo(970f, 560f) }, p)
            c.drawLine(680f, 640f, 900f, 760f, p); c.drawLine(730f, 800f, 860f, 870f, p); c.drawLine(920f, 640f, 700f, 760f, p); c.drawLine(870f, 800f, 740f, 870f, p)
            c.drawCircle(700f, 500f, 140f, p); c.drawCircle(900f, 500f, 140f, p); c.drawCircle(800f, 330f, 140f, p)
            c.drawCircle(800f, 150f, 45f, p); c.drawPath(Path().apply { moveTo(810f, 110f); quadTo(830f, 60f, 880f, 50f) }, p)
            c.drawLine(600f, 570f, 1000f, 570f, p)
        },
        Item("b:bear", "くま") to { c, p ->
            c.drawCircle(560f, 250f, 100f, p); c.drawCircle(560f, 250f, 50f, p)
            c.drawCircle(1040f, 250f, 100f, p); c.drawCircle(1040f, 250f, 50f, p)
            c.drawCircle(800f, 480f, 300f, p)
            c.drawOval(RectF(660f, 520f, 940f, 720f), p)
            c.drawOval(RectF(760f, 540f, 840f, 600f), p)
            c.drawPath(Path().apply { moveTo(800f, 600f); lineTo(800f, 650f); moveTo(800f, 650f); quadTo(760f, 690f, 730f, 660f); moveTo(800f, 650f); quadTo(840f, 690f, 870f, 660f) }, p)
            c.drawCircle(690f, 420f, 30f, p); c.drawCircle(910f, 420f, 30f, p)
            c.drawOval(RectF(580f, 520f, 640f, 560f), p); c.drawOval(RectF(960f, 520f, 1020f, 560f), p)
            c.drawPath(Path().apply { moveTo(640f, 730f); quadTo(560f, 1050f, 800f, 1050f); quadTo(1040f, 1050f, 960f, 730f) }, p)
        },
    )

    private fun dir(c: Context) = File(c.filesDir, "nurie").apply { mkdirs() }

    /** えらべる もの：内蔵・assets・じぶん で 足した もの */
    fun list(c: Context): List<Item> =
        builtins.map { it.first } +
            (runCatching { c.assets.list("nurie")?.filter { it.endsWith(".png") } }.getOrNull().orEmpty().sorted().map { Item("a:$it", it.removeSuffix(".png")) }) +
            (dir(c).listFiles()?.filter { it.extension == "png" }?.sortedBy { it.lastModified() }?.map { Item("f:${it.name}", "じぶん の", "self") }.orEmpty()) +
            NuriePack.books.flatMap { b -> b.pages.mapIndexed { k, f -> Item("p:${b.id}/$f", "${b.name} ${k + 1}", b.id) } }

    /** 線画（黒 い 線、ほか は すけて いる） */
    fun bitmap(c: Context, id: String): Bitmap? = when {
        id.startsWith("b:") -> builtins.firstOrNull { it.first.id == id }?.let { (_, draw) ->
            Bitmap.createBitmap(W, H, Bitmap.Config.ARGB_8888).also { draw(Canvas(it), pen()) }
        }
        id.startsWith("a:") -> runCatching { c.assets.open("nurie/" + id.removePrefix("a:")).use { BitmapFactory.decodeStream(it) } }.getOrNull()?.let { toLine(it) }
        id.startsWith("f:") -> File(dir(c), id.removePrefix("f:")).takeIf { it.exists() }?.let { BitmapFactory.decodeFile(it.path) }
        id.startsWith("p:") -> id.removePrefix("p:").split('/', limit = 2).let { (b, f) -> NuriePack.file(c, b, f) }
            .takeIf { it.exists() }?.let { BitmapFactory.decodeFile(it.path) }?.let { toLine(it, thicken = true) }
        else -> null
    }

    /** 画像 を 黒 い 線 だけ に する（くらい ところ → 黒、あかるい ところ → すける） */
    /** thicken：ほそい 線 を 1 ドット ふとく（ちいさく して も バケツ が もれない よう に） */
    fun toLine(src: Bitmap, threshold: Int = 110, thicken: Boolean = false): Bitmap {
        val s = minOf(1f, 1800f / maxOf(src.width, src.height))
        val b = if (s < 1f) Bitmap.createScaledBitmap(src, (src.width * s).toInt(), (src.height * s).toInt(), true) else src
        val w = b.width; val h = b.height
        val px = IntArray(w * h); b.getPixels(px, 0, w, 0, 0, w, h)
        for (i in px.indices) {
            val c = px[i]
            val a = c ushr 24
            val lum = (Color.red(c) * 30 + Color.green(c) * 59 + Color.blue(c) * 11) / 100
            px[i] = if (a > 100 && lum < threshold) Color.BLACK else Color.TRANSPARENT
        }
        if (thicken) {
            val src2 = px.copyOf()
            for (i in px.indices) if (src2[i] == Color.TRANSPARENT) {
                val x = i % w
                if ((x > 0 && src2[i - 1] == Color.BLACK) || (x < w - 1 && src2[i + 1] == Color.BLACK) || (i >= w && src2[i - w] == Color.BLACK) || (i + w < px.size && src2[i + w] == Color.BLACK)) px[i] = Color.BLACK
            }
        }
        return Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888).also { it.setPixels(px, 0, w, 0, 0, w, h) }
    }

    fun addFromImage(c: Context, src: Bitmap): Item {
        val name = "${UUID.randomUUID()}.png"
        File(dir(c), name).outputStream().use { toLine(src).compress(Bitmap.CompressFormat.PNG, 100, it) }
        return Item("f:$name", "じぶん の")
    }

    fun delete(c: Context, id: String) { if (id.startsWith("f:")) File(dir(c), id.removePrefix("f:")).delete() }
}
