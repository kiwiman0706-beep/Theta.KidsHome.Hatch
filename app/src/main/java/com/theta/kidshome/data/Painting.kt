package com.theta.kidshome.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.BlurMaskFilter
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.UUID
import kotlin.math.hypot
import kotlin.random.Random

/** 画材 */
enum class Brush(val emoji: String, val label: String) {
    PEN("🖊", "ペン"),
    PENCIL("✏️", "いろえんぴつ"),
    CRAYON("🖍", "クレヨン"),
    WATER("💧", "すいさい"),
    OIL("🖌", "あぶらえ"),
    SPRAY("💨", "スプレー"),
    FINGER("☝️", "ゆび"),
    RAINBOW("🌈", "にじ"),
    ERASER("🧽", "けしごむ"),
}

/** 絵の 1 かき（あとから開き直せるように、点の並びで持つ） */
sealed interface Mark
data class Stroke(
    val points: FloatArray, val color: Int, val width: Float, val brush: Brush, val seed: Int,
    /** ペンの筆圧（0〜1、点ごと）。指で描いたときは null */
    val pressures: FloatArray? = null,
) : Mark
data class Stamp(val x: Float, val y: Float, val emoji: String, val size: Float) : Mark

/**
 * 画材ごとの描き方。同じ seed なら毎回おなじ「かすれ」になる（開き直しても同じ絵）。
 */
object Painter {
    const val PAPER = Color.WHITE

    /** 背景（写真・ぬりえ）を、紙のまんなかに入るように置く */
    fun drawBackground(c: Canvas, bg: Bitmap?) {
        c.drawColor(PAPER)
        bg ?: return
        val s = minOf(c.width.toFloat() / bg.width, c.height.toFloat() / bg.height)
        val w = bg.width * s
        val h = bg.height * s
        c.drawBitmap(bg, null, RectF((c.width - w) / 2, (c.height - h) / 2, (c.width + w) / 2, (c.height + h) / 2), Paint(Paint.FILTER_BITMAP_FLAG))
    }

    fun draw(c: Canvas, m: Mark) {
        when (m) {
            is Stamp -> {
                val p = Paint(Paint.ANTI_ALIAS_FLAG).apply { textSize = m.size; textAlign = Paint.Align.CENTER }
                c.drawText(m.emoji, m.x, m.y + m.size / 3, p)
            }
            is Stroke -> stroke(c, m)
        }
    }

    private fun path(pts: FloatArray, dx: Float = 0f, dy: Float = 0f) = Path().apply {
        moveTo(pts[0] + dx, pts[1] + dy)
        var i = 2
        while (i < pts.size) { lineTo(pts[i] + dx, pts[i + 1] + dy); i += 2 }
    }

    /** 点のあいだを step ごとに うめた位置 */
    private inline fun along(pts: FloatArray, step: Float, f: (Float, Float) -> Unit) {
        f(pts[0], pts[1])
        var i = 2
        while (i < pts.size) {
            val x0 = pts[i - 2]; val y0 = pts[i - 1]; val x1 = pts[i]; val y1 = pts[i + 1]
            val d = hypot(x1 - x0, y1 - y0)
            val n = (d / step).toInt().coerceAtLeast(1)
            for (k in 1..n) f(x0 + (x1 - x0) * k / n, y0 + (y1 - y0) * k / n)
            i += 2
        }
    }

    private fun shade(color: Int, f: Float): Int {
        fun ch(v: Int) = (v * f).toInt().coerceIn(0, 255)
        return Color.argb(Color.alpha(color), ch(Color.red(color)), ch(Color.green(color)), ch(Color.blue(color)))
    }

    /** 筆圧で太さが変わる線（ペン・けしごむ・いろえんぴつ・にじ） */
    private fun pressureLine(c: Canvas, m: Stroke, p: Paint, colorAt: (Int) -> Int, scale: Float) {
        val pts = m.points
        val pr = m.pressures ?: return
        p.style = Paint.Style.STROKE
        var i = 2
        if (pts.size == 2) {
            p.style = Paint.Style.FILL; p.color = colorAt(0)
            c.drawCircle(pts[0], pts[1], m.width * scale * (0.35f + 0.9f * pr[0]) / 2, p)
            return
        }
        while (i < pts.size) {
            val k = i / 2
            p.color = colorAt(k)
            p.strokeWidth = m.width * scale * (0.35f + 0.9f * pr.getOrElse(k) { 0.5f })
            c.drawLine(pts[i - 2], pts[i - 1], pts[i], pts[i + 1], p)
            i += 2
        }
    }

    private fun stroke(c: Canvas, m: Stroke) {
        val pts = m.points
        if (pts.size < 2) return
        val pr = m.pressures
        if (pr != null && m.brush in setOf(Brush.PEN, Brush.ERASER, Brush.PENCIL, Brush.RAINBOW)) {
            val p = Paint(Paint.ANTI_ALIAS_FLAG).apply { strokeCap = Paint.Cap.ROUND; strokeJoin = Paint.Join.ROUND }
            when (m.brush) {
                Brush.ERASER -> pressureLine(c, m, p, { PAPER }, 1.6f)
                Brush.RAINBOW -> pressureLine(c, m, p, { k -> Color.HSVToColor(floatArrayOf((k * 6f) % 360f, 0.7f, 1f)) }, 1f)
                Brush.PENCIL -> { p.alpha = 160; pressureLine(c, m, p, { Color.argb(160, Color.red(m.color), Color.green(m.color), Color.blue(m.color)) }, 0.45f) }
                else -> pressureLine(c, m, p, { m.color }, 1f)
            }
            return
        }
        // ほかの画材は、筆圧の平均で太さを変える
        val w = m.width * (pr?.let { 0.5f + it.average().toFloat() } ?: 1f)
        val rnd = Random(m.seed)
        val p = Paint(Paint.ANTI_ALIAS_FLAG).apply { strokeCap = Paint.Cap.ROUND; strokeJoin = Paint.Join.ROUND; style = Paint.Style.STROKE }
        val single = pts.size == 2
        when (m.brush) {
            Brush.PEN, Brush.ERASER -> {
                p.color = if (m.brush == Brush.ERASER) PAPER else m.color
                p.strokeWidth = if (m.brush == Brush.ERASER) w * 1.6f else w
                if (single) { p.style = Paint.Style.FILL; c.drawCircle(pts[0], pts[1], p.strokeWidth / 2, p) } else c.drawPath(path(pts), p)
            }
            Brush.RAINBOW -> {
                p.strokeWidth = w
                var hue = 0f
                var i = 2
                if (single) { p.style = Paint.Style.FILL; p.color = Color.HSVToColor(floatArrayOf(0f, 0.7f, 1f)); c.drawCircle(pts[0], pts[1], w / 2, p) }
                while (i < pts.size) {
                    p.color = Color.HSVToColor(floatArrayOf(hue % 360f, 0.7f, 1f))
                    c.drawLine(pts[i - 2], pts[i - 1], pts[i], pts[i + 1], p)
                    hue += 6f; i += 2
                }
            }
            Brush.PENCIL -> {
                // 細かい つぶ を こまかく ならべる：紙の ざらざら が見える
                p.style = Paint.Style.FILL
                p.color = m.color
                val r = (w * 0.5f).coerceAtLeast(2f)
                along(pts, r * 0.15f) { x, y ->
                    repeat(3) {
                        p.alpha = 70 + rnd.nextInt(150)
                        c.drawCircle(x + (rnd.nextFloat() - 0.5f) * r, y + (rnd.nextFloat() - 0.5f) * r, r * (0.08f + rnd.nextFloat() * 0.1f), p)
                    }
                }
            }
            Brush.CRAYON -> {
                // ろう を こすりつけた感じ：つぶを みっちり、ところどころ 紙が見える
                p.style = Paint.Style.FILL
                p.color = m.color
                along(pts, w * 0.1f) { x, y ->
                    repeat(9) {
                        if (rnd.nextFloat() < 0.85f) {
                            p.alpha = 170 + rnd.nextInt(85)
                            c.drawCircle(x + (rnd.nextFloat() - 0.5f) * w * 0.9f, y + (rnd.nextFloat() - 0.5f) * w * 0.9f, w * (0.08f + rnd.nextFloat() * 0.1f), p)
                        }
                    }
                }
            }
            Brush.WATER -> {
                // うすい色を ぼかして かさねる（ふちが すこし こい）
                p.color = m.color
                p.strokeWidth = w * 2f
                p.alpha = 60
                p.maskFilter = BlurMaskFilter(w * 0.7f, BlurMaskFilter.Blur.NORMAL)
                c.drawPath(path(pts), p)
                p.maskFilter = null
                p.alpha = 30
                p.strokeWidth = w * 2.1f
                p.style = Paint.Style.STROKE
                c.drawPath(path(pts), p)
                if (single) { p.style = Paint.Style.FILL; p.alpha = 70; c.drawCircle(pts[0], pts[1], w, p) }
            }
            Brush.OIL -> {
                // こってり：太い下地に、明るい・暗い すじ を重ねる
                p.color = m.color
                p.strokeWidth = w * 1.5f
                c.drawPath(path(pts), p)
                p.strokeWidth = w * 0.3f
                for (k in -2..2) {
                    p.color = shade(m.color, 0.82f + rnd.nextFloat() * 0.36f)
                    c.drawPath(path(pts, k * w * 0.25f, k * w * 0.12f), p)
                }
            }
            Brush.SPRAY -> {
                p.style = Paint.Style.FILL
                p.color = m.color
                along(pts, w * 0.4f) { x, y ->
                    repeat(24) {
                        val a = rnd.nextFloat() * 6.2832f
                        val r = w * 1.3f * kotlin.math.sqrt(rnd.nextFloat())
                        p.alpha = 150 + rnd.nextInt(105)
                        c.drawCircle(x + kotlin.math.cos(a) * r, y + kotlin.math.sin(a) * r, 1.5f + w * 0.05f, p)
                    }
                }
            }
            Brush.FINGER -> {
                // ゆびで のばす：さわった所の色を、うすく ぼかして ひろげる
                p.color = m.color
                p.alpha = 90
                p.strokeWidth = w * 1.8f
                p.maskFilter = BlurMaskFilter(w * 0.9f, BlurMaskFilter.Blur.NORMAL)
                c.drawPath(path(pts), p)
            }
        }
    }

    fun render(width: Int, height: Int, bg: Bitmap?, marks: List<Mark>): Bitmap {
        val b = Bitmap.createBitmap(width.coerceAtLeast(1), height.coerceAtLeast(1), Bitmap.Config.ARGB_8888)
        val c = Canvas(b)
        drawBackground(c, bg)
        marks.forEach { draw(c, it) }
        return b
    }
}

/** 描いた絵の保存（あとから開いて、つづきを描ける）。子ごとに 40 まいまで */
object Paintings {
    data class Info(val id: String, val dir: File, val updated: Long)

    private fun root(c: Context) = File(c.filesDir, "drawings/" + Store.config.currentProfileId).apply { mkdirs() }

    fun list(c: Context): List<Info> =
        root(c).listFiles()?.filter { File(it, "marks.json").exists() }
            ?.map { Info(it.name, it, File(it, "marks.json").lastModified()) }
            ?.sortedByDescending { it.updated }.orEmpty()

    fun newId() = UUID.randomUUID().toString()

    fun save(c: Context, id: String, marks: List<Mark>, bg: Bitmap?, thumb: Bitmap) {
        if (marks.isEmpty() && bg == null) return
        val dir = File(root(c), id).apply { mkdirs() }
        File(dir, "marks.json").writeText(JSONArray(marks.map { toJson(it) }).toString())
        val bgFile = File(dir, "bg.png")
        if (bg != null) { if (!bgFile.exists()) bgFile.outputStream().use { bg.compress(Bitmap.CompressFormat.PNG, 100, it) } } else bgFile.delete()
        val s = 360f / maxOf(thumb.width, thumb.height)
        File(dir, "thumb.png").outputStream().use {
            Bitmap.createScaledBitmap(thumb, (thumb.width * s).toInt().coerceAtLeast(1), (thumb.height * s).toInt().coerceAtLeast(1), true)
                .compress(Bitmap.CompressFormat.PNG, 90, it)
        }
        list(c).drop(40).forEach { it.dir.deleteRecursively() }
    }

    /** 背景を変えたときは、古い背景ファイルを消す（次の save で書き直す） */
    fun dropBackground(c: Context, id: String) = File(File(root(c), id), "bg.png").delete()

    fun load(c: Context, id: String): Pair<List<Mark>, Bitmap?> {
        val dir = File(root(c), id)
        val marks = runCatching {
            val a = JSONArray(File(dir, "marks.json").readText())
            (0 until a.length()).mapNotNull { fromJson(a.getJSONObject(it)) }
        }.getOrDefault(emptyList())
        val bg = File(dir, "bg.png").takeIf { it.exists() }?.let { BitmapFactory.decodeFile(it.path) }
        return marks to bg
    }

    fun thumb(c: Context, id: String): Bitmap? = File(File(root(c), id), "thumb.png").takeIf { it.exists() }?.let { BitmapFactory.decodeFile(it.path) }

    fun delete(c: Context, id: String) = File(root(c), id).deleteRecursively()

    fun toJson(m: Mark): JSONObject = when (m) {
        is Stroke -> JSONObject().put("t", "s").put("c", m.color).put("w", m.width.toDouble()).put("b", m.brush.name).put("seed", m.seed)
            .put("p", JSONArray().apply { m.points.forEach { put(Math.round(it * 10) / 10.0) } })
            .apply { m.pressures?.let { pr -> put("pr", JSONArray().apply { pr.forEach { put(Math.round(it * 100) / 100.0) } }) } }
        is Stamp -> JSONObject().put("t", "e").put("x", m.x.toDouble()).put("y", m.y.toDouble()).put("e", m.emoji).put("size", m.size.toDouble())
    }

    fun fromJson(o: JSONObject): Mark? = when (o.optString("t")) {
        "s" -> {
            val a = o.getJSONArray("p")
            Stroke(
                FloatArray(a.length()) { a.getDouble(it).toFloat() }, o.getInt("c"), o.getDouble("w").toFloat(),
                runCatching { Brush.valueOf(o.getString("b")) }.getOrDefault(Brush.PEN), o.optInt("seed"),
                o.optJSONArray("pr")?.let { pr -> FloatArray(pr.length()) { pr.getDouble(it).toFloat() } },
            )
        }
        "e" -> Stamp(o.getDouble("x").toFloat(), o.getDouble("y").toFloat(), o.getString("e"), o.getDouble("size").toFloat())
        else -> null
    }
}
