package com.iranmusicstudio

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import androidx.media3.common.util.UnstableApi
import androidx.media3.effect.CanvasOverlay
import kotlin.math.*

@UnstableApi
@UnstableApi
class StudioSpectrumOverlay(
    private val spectrumFrames: Array<FloatArray>,
    private val style: Int
) : CanvasOverlay(true) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val gold = 0xFFFFD477.toInt()
    private val softGold = 0xFFE6B85D.toInt()

    override fun onDraw(canvas: Canvas, presentationTimeUs: Long) {
        if (spectrumFrames.isEmpty()) return
        val frameIndex = (presentationTimeUs / 1_000_000.0 * 30.0).toInt()
            .coerceIn(0, spectrumFrames.lastIndex)
        val values = spectrumFrames[frameIndex]
        val w = canvas.width.toFloat()
        val h = canvas.height.toFloat()
        val panelHeight = h * 0.19f
        val panel = RectF(w * 0.12f, h * 0.77f, w * 0.88f, h * 0.965f)
        paint.style = Paint.Style.FILL
        paint.color = 0x66060A12
        canvas.drawRoundRect(panel, h * 0.025f, h * 0.025f, paint)
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = max(1f, h * 0.0015f)
        paint.color = 0x668D6B35
        canvas.drawRoundRect(panel, h * 0.025f, h * 0.025f, paint)
        paint.style = Paint.Style.FILL

        when (style.coerceIn(0, 5)) {
            0, 5 -> drawBars(canvas, values, w, h, style == 5)
            1 -> drawCircular(canvas, values, w, h, false)
            2 -> drawCircular(canvas, values, w, h, true)
            3 -> drawWave(canvas, values, w, h)
            4 -> drawDots(canvas, values, w, h)
        }
    }

    private fun drawBars(c: Canvas, v: FloatArray, w: Float, h: Float, mirrored: Boolean) {
        val left = w * 0.16f
        val right = w * 0.84f
        val base = h * 0.925f
        val gap = w * 0.0022f
        val bw = (right - left - gap * (v.size - 1)) / v.size
        for (i in v.indices) {
            val x = left + i * (bw + gap)
            val barH = h * (0.025f + v[i] * 0.105f)
            paint.color = if (i % 6 == 0) gold else softGold
            c.drawRoundRect(x, base - barH, x + bw, base, bw / 2f, bw / 2f, paint)
            if (mirrored) c.drawRoundRect(x, base + h * 0.006f, x + bw,
                base + h * 0.006f + barH * 0.22f, bw / 2f, bw / 2f, paint)
        }
    }

    private fun drawCircular(c: Canvas, v: FloatArray, w: Float, h: Float, radial: Boolean) {
        val cx = w * 0.5f
        val cy = h * 0.865f
        val radius = if (radial) h * 0.045f else h * 0.065f
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = max(1f, h * 0.002f)
        paint.color = 0x668D6B35
        c.drawCircle(cx, cy, radius, paint)
        paint.strokeCap = Paint.Cap.ROUND
        paint.strokeWidth = max(2f, h * 0.004f)
        for (i in v.indices) {
            val a = Math.toRadians(i * 360.0 / v.size - 90.0)
            val inner = radius + h * if (radial) 0.018f else 0.012f
            val outer = inner + h * (0.012f + v[i] * if (radial) 0.055f else 0.035f)
            paint.color = if (i % 4 == 0) gold else softGold
            c.drawLine(cx + cos(a).toFloat() * inner, cy + sin(a).toFloat() * inner,
                cx + cos(a).toFloat() * outer, cy + sin(a).toFloat() * outer, paint)
        }
        paint.style = Paint.Style.FILL
    }

    private fun drawWave(c: Canvas, v: FloatArray, w: Float, h: Float) {
        val path = Path()
        for (i in v.indices) {
            val x = w * 0.16f + i * (w * 0.68f / (v.size - 1))
            val y = h * 0.87f - (v[i] - 0.12f) * h * 0.10f
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        paint.style = Paint.Style.STROKE
        paint.strokeCap = Paint.Cap.ROUND
        paint.strokeJoin = Paint.Join.ROUND
        paint.strokeWidth = h * 0.009f
        paint.color = 0x55E6B85D
        c.drawPath(path, paint)
        paint.strokeWidth = h * 0.0028f
        paint.color = gold
        c.drawPath(path, paint)
        paint.style = Paint.Style.FILL
    }

    private fun drawDots(c: Canvas, v: FloatArray, w: Float, h: Float) {
        val left = w * 0.17f
        val step = w * 0.66f / v.size
        val cy = h * 0.865f
        for (i in v.indices) {
            val rows = (v[i] * 7f).toInt().coerceIn(1, 7)
            for (j in 0 until rows) {
                paint.color = if (j == rows - 1) gold else softGold
                val x = left + i * step
                val y = cy - j * h * 0.012f
                c.drawCircle(x, y, h * 0.003f, paint)
            }
        }
    }
}
