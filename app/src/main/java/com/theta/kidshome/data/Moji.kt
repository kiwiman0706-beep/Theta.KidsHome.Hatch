package com.theta.kidshome.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.Calendar
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.math.max

/** 1 画（点の並び：x・y はマスの中で 0〜1、p は筆圧 0〜1） */
typealias MojiStroke = List<FloatArray>

/** れんしゅうする 文字のまとまり */
data class MojiSet(val id: String, val emoji: String, val title: String, val chars: String, val grade: Int = 0)

/**
 * 字の練習：書いた字は 1 字ずつ保存されて、いつのまにか「手書きフォントの素材」が集まる。
 * 学年が上がると、その学年で習う漢字（学年別漢字配当表）が増える。
 * 書き出し：おれん字 の用紙と同じ形の PDF、または 画像（PNG）＋ 線のデータ（JSON・SVG）の ZIP。
 */
object Moji {
    private const val HIRAGANA = "あいうえおかきくけこさしすせそたちつてとなにぬねのはひふへほまみむめもやゆよらりるれろわをんがぎぐげござじずぜぞだぢづでどばびぶべぼぱぴぷぺぽぁぃぅぇぉっゃゅょ"
    private const val KATAKANA = "アイウエオカキクケコサシスセソタチツテトナニヌネノハヒフヘホマミムメモヤユヨラリルレロワヲンガギグゲゴザジズゼゾダヂヅデドバビブベボパピプペポァィゥェォッャュョー"
    private const val NUMBERS = "０１２３４５６７８９ＡＢＣＤＥＦＧＨＩＪＫＬＭＮＯＰＱＲＳＴＵＶＷＸＹＺａｂｃｄｅｆｇｈｉｊｋｌｍｎｏｐｑｒｓｔｕｖｗｘｙｚ？！"

    private var grades: List<String> = emptyList()
    private var sheets: List<Pair<String, String>> = emptyList()

    fun load(context: Context) {
        if (grades.isNotEmpty()) return
        runCatching {
            val g = JSONObject(context.assets.open("moji/kyoiku_kanji.json").bufferedReader().readText()).getJSONArray("grades")
            grades = (0 until g.length()).map { g.getString(it) }
            val o = JSONObject(context.assets.open("moji/oren_sheets.json").bufferedReader().readText()).getJSONArray("sheets")
            sheets = (0 until o.length()).map { o.getJSONObject(it) }.map { it.getString("name") to it.getString("chars") }
        }
    }

    /** いまの学年（4 月に 1 つ上がる）。0 = 小学校まえ */
    fun schoolYear(now: Long = System.currentTimeMillis()): Int {
        val c = Calendar.getInstance().apply { timeInMillis = now }
        return c.get(Calendar.YEAR) - if (c.get(Calendar.MONTH) < Calendar.APRIL) 1 else 0
    }

    fun gradeOf(p: Profile): Int {
        val up = if (p.gradeYear == 0) 0 else (schoolYear() - p.gradeYear).coerceAtLeast(0)
        return (p.grade + up).coerceIn(0, 6)
    }

    /** れんしゅう できる まとまり：かな・すうじ と 1〜6 ねんせい の 漢字（学年別漢字配当表） */
    fun sets(context: Context, p: Profile): List<MojiSet> {
        load(context)
        val g = gradeOf(p)
        return buildList {
            add(MojiSet("hira", "あ", "ひらがな", HIRAGANA))
            add(MojiSet("kata", "ア", "カタカナ", KATAKANA))
            add(MojiSet("num", "１", "すうじ・ABC", NUMBERS))
            // 1〜6 ねんせい ぜんぶ えらべる（フォント の 素材 に。まだ ならって いない 学年 は「これから」）
            grades.forEachIndexed { i, chars ->
                add(MojiSet("g${i + 1}", "${i + 1}", "${i + 1}ねんせい の かんじ" + if (i + 1 > g) "（これから）" else "", chars, i + 1))
            }
        }
    }

    // ---------------------------------------------------------------- ほぞん

    private fun dir(c: Context, profileId: String) = File(c.filesDir, "moji/$profileId").apply { mkdirs() }
    private fun file(c: Context, profileId: String, ch: String) = File(dir(c, profileId), ch.codePoints().toArray().joinToString("_") { "%04x".format(it) } + ".json")

    fun save(c: Context, profileId: String, ch: String, strokes: List<MojiStroke>) {
        val a = JSONArray(strokes.map { s -> JSONArray(s.map { p -> JSONArray(listOf(r(p[0]), r(p[1]), r(p.getOrElse(2) { 0.5f }))) }) })
        file(c, profileId, ch).writeText(JSONObject().put("char", ch).put("strokes", a).put("at", System.currentTimeMillis()).toString())
    }

    private fun r(v: Float) = Math.round(v * 1000) / 1000.0

    fun load(c: Context, profileId: String, ch: String): List<MojiStroke>? = runCatching {
        val f = file(c, profileId, ch)
        if (!f.exists()) return null
        parse(JSONObject(f.readText()).getJSONArray("strokes"))
    }.getOrNull()

    fun parse(a: JSONArray): List<MojiStroke> = (0 until a.length()).map { i ->
        val s = a.getJSONArray(i)
        (0 until s.length()).map { j -> val p = s.getJSONArray(j); floatArrayOf(p.getDouble(0).toFloat(), p.getDouble(1).toFloat(), p.optDouble(2, 0.5).toFloat()) }
    }

    fun delete(c: Context, profileId: String, ch: String) = file(c, profileId, ch).delete()

    /** 書いた字（文字 → 線） */
    fun written(c: Context, profileId: String): Map<String, List<MojiStroke>> =
        dir(c, profileId).listFiles()?.mapNotNull { f ->
            runCatching { val o = JSONObject(f.readText()); o.getString("char") to parse(o.getJSONArray("strokes")) }.getOrNull()
        }?.toMap().orEmpty()

    fun writtenChars(c: Context, profileId: String): Set<String> = written(c, profileId).keys

    // ---------------------------------------------------------------- かく

    /** 筆圧で太さが変わる線（おれん字 てがき と同じ太さの決め方） */
    fun widthUnit(pr: Float, pen: Float = 0.06f) = max(pen * 0.28f, pen * (0.32f + 1.25f * pr))

    fun draw(canvas: Canvas, strokes: List<MojiStroke>, x: Float, y: Float, size: Float, color: Int = Color.BLACK) {
        val p = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = color; strokeCap = Paint.Cap.ROUND; strokeJoin = Paint.Join.ROUND }
        for (s in strokes) {
            if (s.size == 1) {
                p.style = Paint.Style.FILL
                canvas.drawCircle(x + s[0][0] * size, y + s[0][1] * size, size * widthUnit(s[0][2]) * 0.55f, p)
                continue
            }
            p.style = Paint.Style.STROKE
            for (i in 1 until s.size) {
                val a = s[i - 1]; val b = s[i]
                p.strokeWidth = size * widthUnit((a[2] + b[2]) / 2)
                canvas.drawLine(x + a[0] * size, y + a[1] * size, x + b[0] * size, y + b[1] * size, p)
            }
        }
    }

    fun svg(strokes: List<MojiStroke>, size: Int = 1000): String = buildString {
        append("""<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 $size $size" width="$size" height="$size">""")
        for (s in strokes) {
            if (s.isEmpty()) continue
            val w = size * widthUnit(s.map { it[2] }.average().toFloat())
            append("""<path fill="none" stroke="#000" stroke-linecap="round" stroke-linejoin="round" stroke-width="${"%.1f".format(w)}" d="""")
            s.forEachIndexed { i, p -> append(if (i == 0) "M" else " L").append("%.1f %.1f".format(p[0] * size, p[1] * size)) }
            if (s.size == 1) append(" l0.1 0")
            append("\"/>")
        }
        append("</svg>")
    }

    // ---------------------------------------------------------------- 書き出し

    /** おれん字 の用紙（A4 横・20×10 マス）と同じ形の PDF。書いた字のあるシートだけ */
    fun exportOrenji(c: Context, profileId: String, out: File): Int {
        load(c)
        val w = written(c, profileId)
        val pdf = PdfDocument()
        var pages = 0
        val PT_W = 841.92f; val PT_H = 595.32f
        val COLX0 = 41.11f; val COLPITCH = 36.851f; val ROWTOP0 = 62.37f; val ROWPITCH = 48.19f; val CELL = 36.85f
        val GRID_TOP = 51.03f; val GRID_BOT = 532.95f; val GRID_L = 41.11f; val GRID_R = 778.14f
        for ((_, chars) in sheets) {
            val list = chars.codePoints().toArray().map { String(Character.toChars(it)) }
            if (list.none { it in w }) continue
            pages++
            val page = pdf.startPage(PdfDocument.PageInfo.Builder(PT_W.toInt() + 1, PT_H.toInt() + 1, pages).create())
            val cv = page.canvas
            val line = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = 0.6f; color = Color.BLACK }
            cv.drawColor(Color.WHITE)
            cv.drawRoundRect(RectF(32.6f, 42.5f, 786.65f, 541.45f), 9f, 9f, line)
            cv.drawRoundRect(RectF(GRID_L, GRID_TOP, GRID_R, GRID_BOT), 6f, 6f, line)
            line.strokeWidth = 0.5f
            for (col in 0..20) cv.drawLine(COLX0 + col * COLPITCH, GRID_TOP, COLX0 + col * COLPITCH, GRID_BOT, line)
            cv.drawLine(GRID_L, GRID_TOP, GRID_R, GRID_TOP, line)
            for (row in 0 until 10) {
                val yt = ROWTOP0 + row * ROWPITCH
                cv.drawLine(GRID_L, yt, GRID_R, yt, line)
                cv.drawLine(GRID_L, yt + CELL, GRID_R, yt + CELL, line)
            }
            val text = Paint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 11f; typeface = Typeface.SERIF; color = Color.BLACK }
            cv.drawText("※", 32.6f, 29.2f, text)
            line.strokeWidth = 0.6f
            cv.drawRect(616.6f, 14.2f, 786.6f, 34.0f, line)
            cv.drawLine(644.9f, 14.2f, 644.9f, 34.0f, line)
            text.textSize = 10f
            cv.drawText("メモ", 619f, 28.5f, text)
            list.take(200).forEachIndexed { i, ch ->
                val s = w[ch] ?: return@forEachIndexed
                draw(cv, s, COLX0 + (i % 20) * COLPITCH, ROWTOP0 + (i / 20) * ROWPITCH, COLPITCH)
            }
            pdf.finishPage(page)
        }
        out.outputStream().use { pdf.writeTo(it) }
        pdf.close()
        return pages
    }

    /** 画像（PNG 512px・すけ）＋ SVG ＋ 線のデータ（JSON）をまとめた ZIP。ほかのフォント作りにも使える */
    fun exportZip(c: Context, profileId: String, name: String, out: File): Int {
        val w = written(c, profileId)
        ZipOutputStream(out.outputStream()).use { z ->
            fun put(path: String, bytes: ByteArray) { z.putNextEntry(ZipEntry(path)); z.write(bytes); z.closeEntry() }
            val all = JSONObject()
            w.forEach { (ch, strokes) ->
                val code = ch.codePoints().toArray().joinToString("_") { "U+%04X".format(it) }
                val bmp = Bitmap.createBitmap(512, 512, Bitmap.Config.ARGB_8888)
                draw(Canvas(bmp), strokes, 0f, 0f, 512f)
                val png = java.io.ByteArrayOutputStream().also { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }.toByteArray()
                put("png/${code}_$ch.png", png)
                put("svg/${code}_$ch.svg", svg(strokes).toByteArray())
                all.put(ch, JSONArray(strokes.map { s -> JSONArray(s.map { p -> JSONArray(listOf(r(p[0]), r(p[1]), r(p[2]))) }) }))
            }
            put("strokes.json", JSONObject().put("name", name).put("format", "x,y,pressure in 0..1 per cell").put("chars", all).toString().toByteArray())
            put("README.txt", ("$name の手書き文字（${w.size}字）\npng/ 512px 透過、svg/ 線、strokes.json 線のデータ（おれん字 てがき と同じ形式）\n").toByteArray())
        }
        return w.size
    }
}
