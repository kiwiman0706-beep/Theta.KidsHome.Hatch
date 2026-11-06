package app.thetahatch.kidshome.data

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
/** えもじ か、スタンプライブラリの画像（image に id） */
/** rot は かたむき（ど）。ふるい 絵 には ない（0） */
data class Stamp(val x: Float, val y: Float, val emoji: String, val size: Float, val image: String? = null, val rot: Float = 0f) : Mark
/** 🪣 バケツ：ぬりえ の 線（なければ まわり の 色）で かこまれた ところ を ぬる */
data class Fill(val x: Float, val y: Float, val color: Int) : Mark

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
            is Fill -> {} // バケツ は render／fill で（ビットマップ が いる）
            is Stamp -> {
                c.save()
                if (m.rot != 0f) c.rotate(m.rot, m.x, m.y)
                val img = m.image?.let { StampLibrary.bitmap(it) }
                if (img != null) {
                    val s = m.size * 1.6f / maxOf(img.width, img.height)
                    val w = img.width * s; val h = img.height * s
                    c.drawBitmap(img, null, RectF(m.x - w / 2, m.y - h / 2, m.x + w / 2, m.y + h / 2), Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG))
                } else {
                    val p = Paint(Paint.ANTI_ALIAS_FLAG).apply { textSize = m.size; textAlign = Paint.Align.CENTER }
                    c.drawText(m.emoji, m.x, m.y + m.size / 3, p)
                }
                c.restore()
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

    fun render(width: Int, height: Int, bg: Bitmap?, marks: List<Mark>, lineFit: Bitmap? = null): Bitmap {
        val b = Bitmap.createBitmap(width.coerceAtLeast(1), height.coerceAtLeast(1), Bitmap.Config.ARGB_8888)
        val c = Canvas(b)
        drawBackground(c, bg)
        marks.forEach { if (it is Fill) fill(b, lineFit, it) else draw(c, it) }
        return b
    }

    /** ぬりえ の 線 を 紙 の 大きさ に あわせた もの（すけた 画像） */
    fun fitLine(line: Bitmap?, width: Int, height: Int): Bitmap? {
        line ?: return null
        val out = Bitmap.createBitmap(width.coerceAtLeast(1), height.coerceAtLeast(1), Bitmap.Config.ARGB_8888)
        val s = minOf(width.toFloat() / line.width, height.toFloat() / line.height)
        val w = line.width * s; val h = line.height * s
        Canvas(out).drawBitmap(line, null, RectF((width - w) / 2, (height - h) / 2, (width + w) / 2, (height + h) / 2), Paint(Paint.FILTER_BITMAP_FLAG))
        return out
    }

    /**
     * バケツ。ぬりえ の 線 が あれば 線 を かべ に、なければ おした ところ と ちかい 色 の ひろがり を ぬる。
     * 線 の した に すきま が できない よう に 2 ドット ふくらませる。
     */
    fun fill(target: Bitmap, lineFit: Bitmap?, m: Fill) {
        val w = target.width; val h = target.height
        val x0 = m.x.toInt(); val y0 = m.y.toInt()
        if (x0 !in 0 until w || y0 !in 0 until h) return
        val px = IntArray(w * h); target.getPixels(px, 0, w, 0, 0, w, h)
        val wall = BooleanArray(w * h)
        if (lineFit != null && lineFit.width == w && lineFit.height == h) {
            val lp = IntArray(w * h); lineFit.getPixels(lp, 0, w, 0, 0, w, h)
            for (i in lp.indices) wall[i] = (lp[i] ushr 24) > 90
        } else {
            val seed = px[y0 * w + x0]
            for (i in px.indices) {
                val c = px[i]
                wall[i] = Math.abs(Color.red(c) - Color.red(seed)) + Math.abs(Color.green(c) - Color.green(seed)) + Math.abs(Color.blue(c) - Color.blue(seed)) > 60
            }
        }
        if (wall[y0 * w + x0]) return
        val inR = BooleanArray(w * h)
        val stack = IntArray(w * h); var sp = 0
        stack[sp++] = y0 * w + x0; inR[y0 * w + x0] = true
        while (sp > 0) {
            val i = stack[--sp]; val x = i % w
            if (x > 0 && !inR[i - 1] && !wall[i - 1]) { inR[i - 1] = true; stack[sp++] = i - 1 }
            if (x < w - 1 && !inR[i + 1] && !wall[i + 1]) { inR[i + 1] = true; stack[sp++] = i + 1 }
            if (i >= w && !inR[i - w] && !wall[i - w]) { inR[i - w] = true; stack[sp++] = i - w }
            if (i + w < w * h && !inR[i + w] && !wall[i + w]) { inR[i + w] = true; stack[sp++] = i + w }
        }
        // 線 の した へ 2 ドット
        val grow = inR.copyOf()
        repeat(2) {
            val cur = grow.copyOf()
            for (i in cur.indices) if (!cur[i]) {
                val x = i % w
                if ((x > 0 && cur[i - 1]) || (x < w - 1 && cur[i + 1]) || (i >= w && cur[i - w]) || (i + w < w * h && cur[i + w])) grow[i] = true
            }
        }
        for (i in grow.indices) if (grow[i]) px[i] = m.color
        target.setPixels(px, 0, w, 0, 0, w, h)
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

    fun save(c: Context, id: String, marks: List<Mark>, bg: Bitmap?, thumb: Bitmap, line: Bitmap? = null) {
        if (marks.isEmpty() && bg == null && line == null) return
        val dir = File(root(c), id).apply { mkdirs() }
        File(dir, "marks.json").writeText(JSONArray(marks.map { toJson(it) }).toString())
        val bgFile = File(dir, "bg.png")
        if (bg != null) { if (!bgFile.exists()) bgFile.outputStream().use { bg.compress(Bitmap.CompressFormat.PNG, 100, it) } } else bgFile.delete()
        val lineFile = File(dir, "line.png")
        if (line != null) { if (!lineFile.exists()) lineFile.outputStream().use { line.compress(Bitmap.CompressFormat.PNG, 100, it) } } else lineFile.delete()
        val s = 360f / maxOf(thumb.width, thumb.height)
        File(dir, "thumb.png").outputStream().use {
            Bitmap.createScaledBitmap(thumb, (thumb.width * s).toInt().coerceAtLeast(1), (thumb.height * s).toInt().coerceAtLeast(1), true)
                .compress(Bitmap.CompressFormat.PNG, 90, it)
        }
        list(c).drop(40).forEach { it.dir.deleteRecursively() }
    }

    /** 背景を変えたときは、古い背景ファイルを消す（次の save で書き直す） */
    fun dropBackground(c: Context, id: String) { File(File(root(c), id), "bg.png").delete(); File(File(root(c), id), "line.png").delete() }

    /** ぬりえ の 線（ない とき は null） */
    fun line(c: Context, id: String): Bitmap? = File(File(root(c), id), "line.png").takeIf { it.exists() }?.let { BitmapFactory.decodeFile(it.path) }

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
        is Fill -> JSONObject().put("t", "f").put("x", m.x.toDouble()).put("y", m.y.toDouble()).put("c", m.color)
        is Stamp -> JSONObject().put("t", "e").put("x", m.x.toDouble()).put("y", m.y.toDouble()).put("e", m.emoji).put("size", m.size.toDouble()).putOpt("img", m.image).apply { if (m.rot != 0f) put("r", m.rot.toDouble()) }
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
        "f" -> Fill(o.getDouble("x").toFloat(), o.getDouble("y").toFloat(), o.getInt("c"))
        "e" -> Stamp(o.getDouble("x").toFloat(), o.getDouble("y").toFloat(), o.getString("e"), o.getDouble("size").toFloat(), if (o.has("img")) o.getString("img") else null, o.optDouble("r", 0.0).toFloat())
        else -> null
    }
}
