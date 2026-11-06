package app.thetahatch.kidshome.ui

import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import java.util.Calendar
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * へや の 行事 の かざり と、つき ごと の 花 の 花びん。
 * 部屋 の 目盛り（1600×1000）で かく。かべ の うえ に かざり の ひも、まど と けいじばん の あいだ（x 705〜790）に 小もの。
 */
object RoomSeason {
    val EVENTS = listOf(
        "newyear" to "🎍 おしょうがつ", "setsubun" to "👹 せつぶん", "hina" to "🎎 ひなまつり", "kodomo" to "🎏 こどもの日",
        "tanabata" to "🎋 たなばた", "summer" to "🎐 なつやすみ", "tsukimi" to "🎑 おつきみ", "halloween" to "🎃 ハロウィン", "christmas" to "🎄 クリスマス",
    )

    /** きょう の 行事（なければ null） */
    fun eventOf(cal: Calendar): String? {
        val m = cal.get(Calendar.MONTH) + 1; val d = cal.get(Calendar.DAY_OF_MONTH)
        fun inRange(m1: Int, d1: Int, m2: Int, d2: Int) = (m * 100 + d) in (m1 * 100 + d1)..(m2 * 100 + d2)
        return when {
            inRange(1, 1, 1, 10) -> "newyear"
            inRange(1, 25, 2, 3) -> "setsubun"
            inRange(2, 18, 3, 3) -> "hina"
            inRange(4, 20, 5, 5) -> "kodomo"
            inRange(6, 25, 7, 7) -> "tanabata"
            inRange(7, 20, 8, 31) -> "summer"
            inRange(9, 1, 9, 30) -> "tsukimi"
            inRange(10, 10, 10, 31) -> "halloween"
            inRange(12, 1, 12, 25) -> "christmas"
            else -> null
        }
    }

    /** つき ごと の 花（花びん） */
    val FLOWERS = listOf(
        "すいせん", "うめ", "もも", "さくら", "カーネーション", "あじさい",
        "あさがお", "ひまわり", "コスモス", "きんもくせい", "きく", "ポインセチア",
    )
}

private fun DrawScope.star(cx: Float, cy: Float, r: Float, c: Color) {
    val p = Path()
    for (k in 0 until 10) {
        val a = -PI / 2 + PI * k / 5; val rr = if (k % 2 == 0) r else r * 0.45f
        val x = cx + rr * cos(a).toFloat(); val y = cy + rr * sin(a).toFloat()
        if (k == 0) p.moveTo(x, y) else p.lineTo(x, y)
    }
    p.close(); drawPath(p, c)
}

/** たるんだ ひも に そって おく（t：0〜1） */
private fun sag(x0: Float, x1: Float, y: Float, dip: Float, t: Float) = Offset(x0 + (x1 - x0) * t, y + dip * sin(PI * t).toFloat())

/** ひも と 三角 の はた */
private fun DrawScope.bunting(colors: List<Color>, o: Color, x0: Float = 20f, x1: Float = 1140f, y: Float = 14f, n: Int = 16) {
    val p = Path().apply { moveTo(x0, y); for (i in 1..40) { val q = sag(x0, x1, y, 34f, i / 40f); lineTo(q.x, q.y) } }
    drawPath(p, o, style = Stroke(4f))
    for (i in 0 until n) {
        val a = sag(x0, x1, y, 34f, (i + 0.15f) / n); val b = sag(x0, x1, y, 34f, (i + 0.85f) / n)
        val tip = Offset((a.x + b.x) / 2, (a.y + b.y) / 2 + 46f)
        val f = Path().apply { moveTo(a.x, a.y); lineTo(b.x, b.y); lineTo(tip.x, tip.y); close() }
        drawPath(f, colors[i % colors.size]); drawPath(f, o, style = Stroke(3f))
    }
}

/** 行事 の かざり（家具 の あと に かく） */
fun DrawScope.seasonDecor(ev: String, o: Color) {
    val cx = 748f // まど と けいじばん の あいだ
    when (ev) {
        "newyear" -> {
            bunting(listOf(Color(0xFFE53935), Color.White), o)
            // しめかざり
            drawCircle(Color(0xFFE8C66A), 34f, Offset(cx, 110f), style = Stroke(12f))
            drawCircle(o, 40f, Offset(cx, 110f), style = Stroke(2f))
            for (k in 0..1) { val x = cx - 14f + k * 28f
                val p = Path().apply { moveTo(x, 140f); lineTo(x + 10f, 160f); lineTo(x - 4f, 172f); lineTo(x + 8f, 196f) }
                drawPath(p, Color.White, style = Stroke(9f)); drawPath(p, o, style = Stroke(2f)) }
            drawCircle(Color(0xFFFF9800), 14f, Offset(cx, 82f)); drawCircle(Color(0xFF43A047), 6f, Offset(cx, 68f))
            // かがみもち
            drawOval(Color.White, Offset(cx - 38f, 400f), Size(76f, 44f)); drawOval(o, Offset(cx - 38f, 400f), Size(76f, 44f), style = Stroke(3f))
            drawOval(Color.White, Offset(cx - 28f, 372f), Size(56f, 36f)); drawOval(o, Offset(cx - 28f, 372f), Size(56f, 36f), style = Stroke(3f))
            drawCircle(Color(0xFFFF9800), 14f, Offset(cx, 364f))
            drawRect(Color(0xFFD9A46B), Offset(cx - 44f, 440f), Size(88f, 22f)); drawRect(o, Offset(cx - 44f, 440f), Size(88f, 22f), style = Stroke(3f))
        }
        "setsubun" -> {
            bunting(listOf(Color(0xFFFFD84D), Color(0xFF333333)), o)
            // おに の おめん
            drawCircle(Color(0xFFE53935), 40f, Offset(cx, 150f)); drawCircle(o, 40f, Offset(cx, 150f), style = Stroke(3f))
            listOf(-1f, 1f).forEach { s ->
                val h = Path().apply { moveTo(cx + s * 16f, 116f); lineTo(cx + s * 26f, 82f); lineTo(cx + s * 32f, 120f); close() }
                drawPath(h, Color(0xFFFFE9A8)); drawPath(h, o, style = Stroke(2f))
                drawCircle(Color.White, 9f, Offset(cx + s * 14f, 142f)); drawCircle(o, 4f, Offset(cx + s * 14f, 142f))
            }
            drawLine(o, Offset(cx - 14f, 170f), Offset(cx + 14f, 170f), 4f)
            listOf(-1f, 1f).forEach { s -> drawLine(Color.White, Offset(cx + s * 8f, 170f), Offset(cx + s * 8f, 180f), 4f) }
            // ます と まめ
            drawRect(Color(0xFFE2B57A), Offset(cx - 34f, 400f), Size(68f, 50f)); drawRect(o, Offset(cx - 34f, 400f), Size(68f, 50f), style = Stroke(3f))
            for (i in 0 until 7) drawCircle(Color(0xFFF2DFB0), 8f, Offset(cx - 24f + i * 8f, 398f - (i % 2) * 6f))
        }
        "hina" -> {
            bunting(listOf(Color(0xFFFF8FB1), Color.White, Color(0xFF7CC576)), o)
            // ぼんぼり
            drawLine(o, Offset(cx, 150f), Offset(cx, 260f), 5f)
            drawOval(Color(0xFFFFF1F6), Offset(cx - 28f, 100f), Size(56f, 70f)); drawOval(o, Offset(cx - 28f, 100f), Size(56f, 70f), style = Stroke(3f))
            drawCircle(Color(0xFFFF8FB1), 10f, Offset(cx, 135f))
            // ひしもち
            listOf(Color(0xFF7CC576), Color.White, Color(0xFFFF8FB1)).forEachIndexed { i, c ->
                val y = 440f - i * 22f
                val p = Path().apply { moveTo(cx - 40f, y); lineTo(cx, y - 14f); lineTo(cx + 40f, y); lineTo(cx, y + 14f); close() }
                drawPath(p, c); drawPath(p, o, style = Stroke(3f))
            }
        }
        "kodomo" -> {
            // こいのぼり（ひも に 3 びき）
            val p = Path().apply { moveTo(20f, 14f); for (i in 1..40) { val q = sag(20f, 1140f, 14f, 34f, i / 40f); lineTo(q.x, q.y) } }
            drawPath(p, o, style = Stroke(4f))
            listOf(Color(0xFF333A56), Color(0xFFE53935), Color(0xFF4A90E2), Color(0xFFFF8FB1), Color(0xFF7CC576)).forEachIndexed { i, c ->
                val a = sag(20f, 1140f, 14f, 34f, 0.1f + i * 0.2f)
                val body = Path().apply { moveTo(a.x - 50f, a.y + 8f); quadraticBezierTo(a.x, a.y - 14f, a.x + 50f, a.y + 8f); lineTo(a.x + 70f, a.y - 6f); lineTo(a.x + 64f, a.y + 22f); lineTo(a.x + 70f, a.y + 46f); lineTo(a.x + 50f, a.y + 30f); quadraticBezierTo(a.x, a.y + 54f, a.x - 50f, a.y + 30f); close() }
                drawPath(body, c); drawPath(body, o, style = Stroke(3f))
                drawCircle(Color.White, 9f, Offset(a.x - 32f, a.y + 18f)); drawCircle(o, 4f, Offset(a.x - 32f, a.y + 18f))
            }
            // かぶと
            val k = Path().apply { moveTo(cx - 40f, 440f); quadraticBezierTo(cx, 370f, cx + 40f, 440f); close() }
            drawPath(k, Color(0xFF4A5A8A)); drawPath(k, o, style = Stroke(3f))
            val horn = Path().apply { moveTo(cx - 30f, 400f); quadraticBezierTo(cx - 40f, 350f, cx - 14f, 330f); moveTo(cx + 30f, 400f); quadraticBezierTo(cx + 40f, 350f, cx + 14f, 330f) }
            drawPath(horn, Color(0xFFE8C66A), style = Stroke(8f))
            drawRect(Color(0xFFD9A46B), Offset(cx - 46f, 440f), Size(92f, 18f)); drawRect(o, Offset(cx - 46f, 440f), Size(92f, 18f), style = Stroke(3f))
        }
        "tanabata" -> {
            // あまのがわ（ほし の ひも）
            val p = Path().apply { moveTo(20f, 14f); for (i in 1..40) { val q = sag(20f, 1140f, 14f, 34f, i / 40f); lineTo(q.x, q.y) } }
            drawPath(p, Color(0xFF9FB8FF), style = Stroke(6f))
            for (i in 0 until 22) { val q = sag(20f, 1140f, 14f, 34f, (i + 0.5f) / 22f); star(q.x, q.y + 16f, 12f, listOf(Color(0xFFFFD84D), Color.White, Color(0xFFFFB3D9))[i % 3]) }
            // ささ と たんざく
            drawLine(Color(0xFF6FAF5A), Offset(cx, 470f), Offset(cx + 6f, 70f), 7f)
            for (i in 0 until 7) { val y = 100f + i * 50f; val s = if (i % 2 == 0) -1f else 1f
                val leaf = Path().apply { moveTo(cx + 3f, y); quadraticBezierTo(cx + s * 30f, y - 10f, cx + s * 44f, y + 6f); quadraticBezierTo(cx + s * 26f, y + 8f, cx + 3f, y); close() }
                drawPath(leaf, Color(0xFF7CC576)) }
            listOf(Color(0xFFFF8FB1), Color(0xFFFFD84D), Color(0xFF4FC3FF), Color(0xFFB36BFF)).forEachIndexed { i, c ->
                val x = cx + (if (i % 2 == 0) -30f else 18f); val y = 140f + i * 70f
                drawRect(c, Offset(x, y), Size(14f, 42f)); drawRect(o, Offset(x, y), Size(14f, 42f), style = Stroke(2f))
            }
        }
        "summer" -> {
            bunting(listOf(Color(0xFF4FC3FF), Color.White, Color(0xFF7FE0D0)), o)
            // ふうりん
            drawLine(o, Offset(cx, 60f), Offset(cx, 110f), 3f)
            val bell = Path().apply { moveTo(cx - 30f, 160f); quadraticBezierTo(cx - 30f, 106f, cx, 106f); quadraticBezierTo(cx + 30f, 106f, cx + 30f, 160f); close() }
            drawPath(bell, Color(0xFFBFEFFF).copy(alpha = 0.9f)); drawPath(bell, o, style = Stroke(3f))
            drawCircle(Color(0xFFFF6B6B), 6f, Offset(cx - 12f, 135f)); drawCircle(Color(0xFF4A90E2), 6f, Offset(cx + 10f, 128f))
            drawLine(o, Offset(cx, 160f), Offset(cx, 200f), 2f)
            drawRect(Color(0xFFFFB3D9), Offset(cx - 9f, 200f), Size(18f, 54f)); drawRect(o, Offset(cx - 9f, 200f), Size(18f, 54f), style = Stroke(2f))
            // うちわ
            drawLine(Color(0xFFD9A46B), Offset(cx, 460f), Offset(cx, 400f), 7f)
            drawCircle(Color(0xFFFF8FB1), 40f, Offset(cx, 370f)); drawCircle(o, 40f, Offset(cx, 370f), style = Stroke(3f))
            drawCircle(Color(0xFFFF5A5A), 16f, Offset(cx - 8f, 364f))
        }
        "tsukimi" -> {
            val p = Path().apply { moveTo(20f, 14f); for (i in 1..40) { val q = sag(20f, 1140f, 14f, 34f, i / 40f); lineTo(q.x, q.y) } }
            drawPath(p, o, style = Stroke(3f))
            for (i in 0 until 12) { val q = sag(20f, 1140f, 14f, 34f, (i + 0.5f) / 12f)
                if (i % 2 == 0) { drawCircle(Color(0xFFFFE27A), 16f, Offset(q.x, q.y + 20f)); drawCircle(o, 16f, Offset(q.x, q.y + 20f), style = Stroke(2f)) }
                else star(q.x, q.y + 20f, 12f, Color(0xFFFFF3B0)) }
            // すすき
            for (k in -1..1) {
                drawLine(Color(0xFFB9A26A), Offset(cx + k * 10f, 400f), Offset(cx + k * 34f, 140f + kotlin.math.abs(k) * 30f), 4f)
                drawOval(Color(0xFFE8D7A8), Offset(cx + k * 34f - 12f, 100f + kotlin.math.abs(k) * 30f), Size(24f, 70f))
            }
            // つきみ だんご
            drawRect(Color(0xFFD9A46B), Offset(cx - 44f, 440f), Size(88f, 22f)); drawRect(o, Offset(cx - 44f, 440f), Size(88f, 22f), style = Stroke(3f))
            listOf(Offset(cx - 26f, 424f), Offset(cx, 424f), Offset(cx + 26f, 424f), Offset(cx - 13f, 400f), Offset(cx + 13f, 400f), Offset(cx, 376f)).forEach {
                drawCircle(Color.White, 14f, it); drawCircle(o, 14f, it, style = Stroke(2f))
            }
        }
        "halloween" -> {
            bunting(listOf(Color(0xFFFF8A1F), Color(0xFF6B3FA0), Color(0xFF333333)), o)
            // おばけ
            val g = Path().apply { moveTo(cx - 30f, 220f); quadraticBezierTo(cx - 30f, 140f, cx, 140f); quadraticBezierTo(cx + 30f, 140f, cx + 30f, 220f)
                lineTo(cx + 18f, 210f); lineTo(cx + 6f, 222f); lineTo(cx - 6f, 210f); lineTo(cx - 18f, 222f); close() }
            drawPath(g, Color.White); drawPath(g, o, style = Stroke(3f))
            drawCircle(o, 5f, Offset(cx - 10f, 175f)); drawCircle(o, 5f, Offset(cx + 10f, 175f))
            // かぼちゃ
            drawOval(Color(0xFFFF8A1F), Offset(cx - 42f, 390f), Size(84f, 66f)); drawOval(o, Offset(cx - 42f, 390f), Size(84f, 66f), style = Stroke(3f))
            drawLine(o, Offset(cx, 392f), Offset(cx, 454f), 2f)
            drawRect(Color(0xFF5A8A3A), Offset(cx - 5f, 376f), Size(10f, 16f))
            listOf(-1f, 1f).forEach { s -> val e = Path().apply { moveTo(cx + s * 22f, 408f); lineTo(cx + s * 12f, 424f); lineTo(cx + s * 30f, 424f); close() }; drawPath(e, Color(0xFF3A2A10)) }
            drawPath(Path().apply { moveTo(cx - 20f, 436f); lineTo(cx - 10f, 444f); lineTo(cx, 436f); lineTo(cx + 10f, 444f); lineTo(cx + 20f, 436f) }, Color(0xFF3A2A10), style = Stroke(4f))
            // こうもり
            listOf(Offset(300f, 110f), Offset(980f, 100f)).forEach { b ->
                val bat = Path().apply { moveTo(b.x, b.y); quadraticBezierTo(b.x - 20f, b.y - 18f, b.x - 40f, b.y - 4f); quadraticBezierTo(b.x - 26f, b.y + 2f, b.x - 22f, b.y + 12f); quadraticBezierTo(b.x - 10f, b.y + 4f, b.x, b.y + 12f)
                    quadraticBezierTo(b.x + 10f, b.y + 4f, b.x + 22f, b.y + 12f); quadraticBezierTo(b.x + 26f, b.y + 2f, b.x + 40f, b.y - 4f); quadraticBezierTo(b.x + 20f, b.y - 18f, b.x, b.y); close() }
                drawPath(bat, Color(0xFF333333))
            }
        }
        "christmas" -> {
            // ガーランド と ライト
            val p = Path().apply { moveTo(20f, 14f); for (i in 1..40) { val q = sag(20f, 1140f, 14f, 34f, i / 40f); lineTo(q.x, q.y) } }
            drawPath(p, Color(0xFF2E7D32), style = Stroke(16f))
            for (i in 0 until 24) { val q = sag(20f, 1140f, 14f, 34f, (i + 0.5f) / 24f)
                val c = listOf(Color(0xFFFF5252), Color(0xFFFFD84D), Color(0xFF4FC3FF), Color(0xFFFF8FB1))[i % 4]
                drawOval(c, Offset(q.x - 7f, q.y + 6f), Size(14f, 20f)); drawOval(o, Offset(q.x - 7f, q.y + 6f), Size(14f, 20f), style = Stroke(2f)) }
            // ツリー
            listOf(0, 1, 2).forEach { i ->
                val top = 130f + i * 80f; val w = 34f + i * 10f
                val t = Path().apply { moveTo(cx, top); lineTo(cx + w, top + 110f); lineTo(cx - w, top + 110f); close() }
                drawPath(t, Color(0xFF43A047)); drawPath(t, o, style = Stroke(3f))
            }
            drawRect(Color(0xFF8B5A2B), Offset(cx - 10f, 400f), Size(20f, 30f))
            star(cx, 124f, 22f, Color(0xFFFFD84D))
            listOf(Offset(cx - 18f, 210f), Offset(cx + 20f, 240f), Offset(cx - 30f, 300f), Offset(cx + 26f, 330f), Offset(cx - 6f, 370f)).forEachIndexed { i, b ->
                drawCircle(listOf(Color(0xFFFF5252), Color(0xFF4FC3FF), Color(0xFFFFD84D))[i % 3], 8f, b)
            }
            // プレゼント
            drawRect(Color(0xFFFF5252), Offset(cx - 40f, 430f), Size(40f, 34f)); drawRect(o, Offset(cx - 40f, 430f), Size(40f, 34f), style = Stroke(2f))
            drawLine(Color(0xFFFFD84D), Offset(cx - 20f, 430f), Offset(cx - 20f, 464f), 6f)
            drawRect(Color(0xFF4FC3FF), Offset(cx + 4f, 440f), Size(36f, 24f)); drawRect(o, Offset(cx + 4f, 440f), Size(36f, 24f), style = Stroke(2f))
        }
    }
}

/** 花びん（ローカル 160×240。花 は つき で かわる） */
fun DrawScope.flowerVase(month: Int, o: Color) {
    val stem = Color(0xFF5E9E4A); val branch = Color(0xFF7A4E2D)
    fun petals(cx: Float, cy: Float, r: Float, n: Int, c: Color, center: Color, pr: Float = r * 0.55f) {
        for (k in 0 until n) { val a = 2 * PI * k / n; drawCircle(c, pr, Offset(cx + r * cos(a).toFloat(), cy + r * sin(a).toFloat())) }
        drawCircle(center, r * 0.5f, Offset(cx, cy))
    }
    fun stems(tips: List<Offset>, c: Color = stem, w: Float = 4f) = tips.forEach { drawLine(c, Offset(80f, 165f), it, w) }
    when (month) {
        1 -> { // すいせん
            stems(listOf(Offset(50f, 70f), Offset(85f, 50f), Offset(115f, 80f)))
            listOf(Offset(50f, 70f), Offset(85f, 50f), Offset(115f, 80f)).forEach { petals(it.x, it.y, 13f, 6, Color.White, Color(0xFFFFC23D)) }
            drawLine(stem, Offset(80f, 165f), Offset(60f, 40f), 6f)
        }
        2, 3 -> { // うめ・もも（えだ に 小さい 花）
            val c = if (month == 2) Color(0xFFE5395B) else Color(0xFFFF9EC2)
            stems(listOf(Offset(30f, 40f), Offset(90f, 20f), Offset(130f, 60f)), c = branch, w = 5f)
            listOf(Offset(40f, 60f), Offset(30f, 40f), Offset(60f, 90f), Offset(90f, 20f), Offset(85f, 60f), Offset(130f, 60f), Offset(110f, 95f)).forEach { petals(it.x, it.y, 7f, 5, c, Color(0xFFFFE27A), 6f) }
        }
        4 -> { // さくら
            stems(listOf(Offset(25f, 50f), Offset(80f, 15f), Offset(135f, 45f)), c = branch, w = 5f)
            listOf(Offset(25f, 50f), Offset(45f, 80f), Offset(70f, 40f), Offset(80f, 15f), Offset(100f, 70f), Offset(135f, 45f), Offset(120f, 95f)).forEach { petals(it.x, it.y, 9f, 5, Color(0xFFFFD1DF), Color(0xFFFF8FB1), 7f) }
        }
        5 -> { // カーネーション
            stems(listOf(Offset(50f, 60f), Offset(85f, 40f), Offset(118f, 66f)))
            listOf(Offset(50f, 60f), Offset(85f, 40f), Offset(118f, 66f)).forEach { p ->
                for (k in 0 until 7) { val a = 2 * PI * k / 7; drawCircle(Color(0xFFE53950), 11f, Offset(p.x + 9f * cos(a).toFloat(), p.y + 7f * sin(a).toFloat())) }
                drawCircle(Color(0xFFFF6F86), 10f, p)
                drawOval(Color(0xFF5E9E4A), Offset(p.x - 7f, p.y + 12f), Size(14f, 18f))
            }
        }
        6 -> { // あじさい
            stems(listOf(Offset(55f, 70f), Offset(108f, 65f)))
            listOf(Offset(55f, 70f) to Color(0xFF7FA8FF), Offset(108f, 65f) to Color(0xFFB79CFF)).forEach { (p, c) ->
                drawCircle(c.copy(alpha = 0.5f), 38f, p)
                for (k in 0 until 16) { val a = 2.4 * k; val r = 8f + (k % 4) * 8f; petals(p.x + r * cos(a).toFloat(), p.y + r * sin(a).toFloat(), 5f, 4, c, Color.White, 4f) }
            }
            drawOval(Color(0xFF5E9E4A), Offset(20f, 110f), Size(50f, 26f)); drawOval(Color(0xFF5E9E4A), Offset(95f, 108f), Size(50f, 26f))
        }
        7 -> { // あさがお
            val vine = androidx.compose.ui.graphics.Path().apply { moveTo(80f, 165f); cubicTo(20f, 130f, 140f, 90f, 60f, 60f); cubicTo(20f, 40f, 110f, 10f, 120f, 40f) }
            drawPath(vine, stem, style = Stroke(4f))
            listOf(Offset(45f, 105f) to Color(0xFF5A7BFF), Offset(115f, 75f) to Color(0xFFB04FE0), Offset(70f, 35f) to Color(0xFFFF6FA8)).forEach { (p, c) ->
                drawCircle(c, 20f, p); drawCircle(Color.White, 7f, p)
                for (k in 0 until 5) { val a = 2 * PI * k / 5; drawLine(Color.White.copy(alpha = 0.8f), p, Offset(p.x + 18f * cos(a).toFloat(), p.y + 18f * sin(a).toFloat()), 2f) }
            }
        }
        8 -> { // ひまわり
            stems(listOf(Offset(55f, 60f), Offset(110f, 45f)), w = 6f)
            listOf(Offset(55f, 60f), Offset(110f, 45f)).forEach { p ->
                for (k in 0 until 14) { val a = 2 * PI * k / 14; drawOval(Color(0xFFFFC107), Offset(p.x + 24f * cos(a).toFloat() - 8f, p.y + 24f * sin(a).toFloat() - 8f), Size(16f, 16f)) }
                drawCircle(Color(0xFF7A4E2D), 17f, p)
            }
            drawOval(stem, Offset(25f, 110f), Size(46f, 24f)); drawOval(stem, Offset(92f, 100f), Size(46f, 24f))
        }
        9 -> { // コスモス
            val tips = listOf(Offset(35f, 55f), Offset(70f, 25f), Offset(105f, 45f), Offset(135f, 80f), Offset(55f, 90f))
            stems(tips, w = 2.5f)
            tips.forEachIndexed { i, p -> petals(p.x, p.y, 11f, 8, if (i % 2 == 0) Color(0xFFFF8FB1) else Color(0xFFFFFFFF), Color(0xFFFFC23D), 7f) }
        }
        10 -> { // きんもくせい
            stems(listOf(Offset(30f, 45f), Offset(85f, 20f), Offset(135f, 55f)), c = branch, w = 5f)
            listOf(Offset(40f, 70f), Offset(60f, 40f), Offset(110f, 40f), Offset(125f, 85f), Offset(80f, 70f)).forEach { drawOval(Color(0xFF3E7A3A), Offset(it.x - 14f, it.y - 6f), Size(28f, 12f)) }
            for (k in 0 until 26) { val x = 25f + (k * 37f) % 115f; val y = 25f + (k * 23f) % 85f; drawCircle(Color(0xFFFF9A2E), 4f, Offset(x, y)) }
        }
        11 -> { // きく
            stems(listOf(Offset(50f, 60f), Offset(85f, 38f), Offset(118f, 64f)))
            listOf(Offset(50f, 60f) to Color(0xFFFFD84D), Offset(85f, 38f) to Color.White, Offset(118f, 64f) to Color(0xFFE6A0FF)).forEach { (p, c) ->
                for (ring in 0..1) for (k in 0 until 16) { val a = 2 * PI * k / 16 + ring * 0.2; val r = 24f - ring * 10f
                    drawLine(c, p, Offset(p.x + r * cos(a).toFloat(), p.y + r * sin(a).toFloat()), 6f) }
                drawCircle(lerpColor(c), 4f, p)
            }
        }
        else -> { // ポインセチア
            stems(listOf(Offset(80f, 70f)), w = 5f)
            for (k in 0 until 6) { val a = 2 * PI * k / 6
                val tip = Offset(80f + 50f * cos(a).toFloat(), 70f + 40f * sin(a).toFloat())
                drawLine(Color(0xFF2E7D32), Offset(80f, 70f), Offset(80f + 56f * cos(a + 0.5).toFloat(), 70f + 46f * sin(a + 0.5).toFloat()), 14f)
                drawLine(Color(0xFFD32F2F), Offset(80f, 70f), tip, 18f) }
            for (k in 0 until 5) drawCircle(Color(0xFFFFD84D), 4f, Offset(74f + (k % 3) * 6f, 66f + (k / 3) * 6f))
        }
    }
    // 花びん
    val v = androidx.compose.ui.graphics.Path().apply {
        moveTo(62f, 150f); lineTo(98f, 150f); quadraticBezierTo(130f, 190f, 112f, 236f); lineTo(48f, 236f); quadraticBezierTo(30f, 190f, 62f, 150f); close()
    }
    drawPath(v, Color(0xFF8FC9E8)); drawPath(v, o, style = Stroke(4f))
    drawRoundRect(Color.White.copy(alpha = 0.55f), Offset(56f, 172f), Size(8f, 40f), CornerRadius(4f))
}

private fun lerpColor(c: Color) = androidx.compose.ui.graphics.lerp(c, Color(0xFF8A6A20), 0.5f)
