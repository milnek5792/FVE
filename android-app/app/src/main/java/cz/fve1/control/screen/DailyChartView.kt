package cz.fve1.control.screen

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.util.AttributeSet
import android.view.View
import androidx.core.content.ContextCompat
import cz.fve1.control.R
import cz.fve1.control.data.ChartPoint
import java.util.Calendar
import kotlin.math.max

class DailyChartView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : View(context, attrs) {
    private var points: List<ChartPoint> = emptyList()

    private val axisPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ContextCompat.getColor(context, R.color.chart_axis)
        strokeWidth = 1f * resources.displayMetrics.density
        style = Paint.Style.STROKE
    }
    private val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ContextCompat.getColor(context, R.color.chart_line)
        strokeWidth = 2f * resources.displayMetrics.density
        style = Paint.Style.STROKE
        strokeJoin = Paint.Join.ROUND
        strokeCap = Paint.Cap.ROUND
    }
    private val dotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ContextCompat.getColor(context, R.color.chart_dot)
        style = Paint.Style.FILL
    }
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ContextCompat.getColor(context, R.color.muted)
        textSize = 9f * resources.displayMetrics.scaledDensity
    }

    fun setPoints(newPoints: List<ChartPoint>) {
        points = newPoints
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0f || h <= 0f) return

        val baselineY = h - 20f * resources.displayMetrics.density
        canvas.drawLine(0f, baselineY, w, baselineY, axisPaint)

        if (points.isEmpty()) {
            canvas.drawText("Žádná data pro dnešní den", 10f, h / 2f, textPaint)
            return
        }

        val minT = points.minOf { it.t }.toFloat()
        val maxT = points.maxOf { it.t }.toFloat()
        val maxV = max(points.maxOf { it.v }, 1f)
        val innerTop = 10f * resources.displayMetrics.density
        val innerBottom = baselineY - 5f * resources.displayMetrics.density
        val usable = innerBottom - innerTop
        val span = max(maxT - minT, 1f)

        var prevX = 0f
        var prevY = 0f
        points.forEachIndexed { idx, p ->
            val x = ((p.t - minT) / span) * (w - 10f) + 5f
            val y = innerBottom - (p.v / maxV) * usable
            if (idx > 0) canvas.drawLine(prevX, prevY, x, y, linePaint)
            canvas.drawCircle(x, y, 2f * resources.displayMetrics.density, dotPaint)
            prevX = x
            prevY = y
        }

        canvas.drawText("Max: ${maxV.toInt()} W", 6f, innerTop + 10f, textPaint)
        canvas.drawText(fmtTime(points.first().t), 6f, h - 6f, textPaint)
        val end = fmtTime(points.last().t)
        val endW = textPaint.measureText(end)
        canvas.drawText(end, w - endW - 6f, h - 6f, textPaint)
    }

    private fun fmtTime(ms: Long): String {
        val c = Calendar.getInstance().apply { timeInMillis = ms }
        return "%02d:%02d".format(c.get(Calendar.HOUR_OF_DAY), c.get(Calendar.MINUTE))
    }
}
