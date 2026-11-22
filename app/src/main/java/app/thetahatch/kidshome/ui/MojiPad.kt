package app.thetahatch.kidshome.ui

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.view.MotionEvent
import android.view.View
import androidx.input.motionprediction.MotionEventPredictor
import app.thetahatch.kidshome.data.Moji
import app.thetahatch.kidshome.data.MojiStroke

/**
 * もじ れんしゅう の 書く ところ（ペン の おくれ を へらす ため Compose で なく ふつう の View）。
 * - 書き おわった 線 は 1 まい の 絵 に して おいて、いま 書いて いる 線 だけ を 描く
 * - タッチ を まとめず すぐ うけとる（requestUnbufferedDispatch）
 * - ペン の すこし 先 を 予測 して 描く（MotionEventPredictor。指 を はなしたら 消える）
 */
@SuppressLint("ViewConstructor")
class MojiPadView(context: Context) : View(context) {
    /** ペン だけ で 書く（指 は 無視） */
    var penOnly = false
    var gridColor = 0x40000000
    var inkColor = android.graphics.Color.rgb(30, 30, 40)
    /** 書き おわった 線（そと から もらう） */
    var strokes: List<MojiStroke> = emptyList()
        set(v) { if (v == field) return; field = v; dirty = true; invalidate() }
    var onStroke: (MojiStroke) -> Unit = {}

    private val cur = ArrayList<FloatArray>()
    private var predicted: List<FloatArray> = emptyList()
    private var pointer = -1
    private var stylus = false
    private val predictor = MotionEventPredictor.newInstance(this)
    private var cache: Bitmap? = null
    private var dirty = true
    private val gridPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = 3f; pathEffect = DashPathEffect(floatArrayOf(14f, 12f), 0f) }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) { dirty = true }

    private fun pt(x: Float, y: Float, p: Float): FloatArray {
        val sz = width.toFloat().coerceAtLeast(1f)
        return floatArrayOf((x / sz).coerceIn(0f, 1f), (y / sz).coerceIn(0f, 1f), if (stylus) p.coerceIn(0f, 1f) else 0.5f)
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(e: MotionEvent): Boolean {
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                val tool = e.getToolType(0)
                stylus = tool == MotionEvent.TOOL_TYPE_STYLUS || tool == MotionEvent.TOOL_TYPE_ERASER
                if (penOnly && !stylus) return false
                requestUnbufferedDispatch(e)
                parent?.requestDisallowInterceptTouchEvent(true)
                pointer = e.getPointerId(0)
                predictor.record(e)
                cur.clear(); predicted = emptyList()
                cur += pt(e.x, e.y, e.pressure)
                invalidate()
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                val i = e.findPointerIndex(pointer)
                if (i < 0) return true
                predictor.record(e)
                for (h in 0 until e.historySize) cur += pt(e.getHistoricalX(i, h), e.getHistoricalY(i, h), e.getHistoricalPressure(i, h))
                cur += pt(e.getX(i), e.getY(i), e.getPressure(i))
                predicted = runCatching {
                    predictor.predict()?.let { p ->
                        val j = p.findPointerIndex(pointer).coerceAtLeast(0)
                        val out = ArrayList<FloatArray>()
                        for (h in 0 until p.historySize) out += pt(p.getHistoricalX(j, h), p.getHistoricalY(j, h), p.getHistoricalPressure(j, h))
                        out += pt(p.getX(j), p.getY(j), p.getPressure(j))
                        p.recycle()
                        out
                    }
                }.getOrNull().orEmpty()
                invalidate()
                return true
            }
            MotionEvent.ACTION_UP -> {
                if (pointer < 0) return true
                predictor.record(e)
                val i = e.findPointerIndex(pointer)
                if (i >= 0) cur += pt(e.getX(i), e.getY(i), e.getPressure(i))
                val s = cur.toList()
                // すぐ 絵 に 足す（そと から strokes が もどって くる まで ちらつかない よう に）
                cache?.let { b -> Moji.draw(Canvas(b), listOf(s), 0f, 0f, width.toFloat(), inkColor) }
                cur.clear(); predicted = emptyList(); pointer = -1
                invalidate()
                onStroke(s)
                return true
            }
            MotionEvent.ACTION_CANCEL -> {
                cur.clear(); predicted = emptyList(); pointer = -1
                invalidate()
                return true
            }
        }
        return true
    }

    override fun onDraw(canvas: Canvas) {
        if (width <= 0 || height <= 0) return
        if (dirty || cache == null || cache!!.width != width || cache!!.height != height) {
            val b = cache?.takeIf { it.width == width && it.height == height } ?: Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).also { cache = it }
            b.eraseColor(android.graphics.Color.TRANSPARENT)
            val c = Canvas(b)
            gridPaint.color = gridColor
            c.drawLine(width / 2f, 0f, width / 2f, height.toFloat(), gridPaint)
            c.drawLine(0f, height / 2f, width.toFloat(), height / 2f, gridPaint)
            Moji.draw(c, strokes, 0f, 0f, width.toFloat(), inkColor)
            dirty = false
        }
        canvas.drawBitmap(cache!!, 0f, 0f, null)
        if (cur.isNotEmpty()) {
            val live = if (predicted.isEmpty()) cur else cur + predicted
            Moji.draw(canvas, listOf(live), 0f, 0f, width.toFloat(), inkColor)
        }
    }
}
