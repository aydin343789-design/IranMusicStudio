package com.iranmusicstudio

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import androidx.media3.common.util.UnstableApi
import androidx.media3.effect.CanvasOverlay
import kotlin.math.*

/**
 * Transparent visualizer layer for the final video.
 * No backing panel, card, logo or opaque pixels are drawn: the source video
 * remains visible everywhere except for the actual spectrum strokes.
 */
@UnstableApi
class StudioSpectrumOverlay(
    private val spectrumFrames: Array<FloatArray>,
    private val style: Int
) : CanvasOverlay(true) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val gold = 0xFFFFD477.toInt()
    private val softGold = 0xFFE6B85D.toInt()
    private val paleGold = 0xFFFFE8B5.toInt()

    override fun onDraw(canvas: Canvas, presentationTimeUs: Long) {
        if (spectrumFrames.isEmpty()) return
        val frameIndex = (presentationTimeUs / 1_000_000.0 * 30.0).toInt()
            .coerceIn(0, spectrumFrames.lastIndex)
        val values = spectrumFrames[frameIndex]
        val w = canvas.width.toFloat()
        val h = canvas.height.toFloat()

        // Only draw the selected visualization. The overlay stays fully transparent elsewhere.
        when (style.coerceIn(0, 5)) {
            0 -> drawBars(canvas, values, w, h, false)
            1 -> drawCircular(canvas, values, w, h, false)
            2 -> drawCircular(canvas, values, w, h, true)
            3 -> drawWave(canvas, values, w, h)
            4 -> drawDots(canvas, values, w, h)
            5 -> drawBars(canvas, values, w, h, true)
        }
    }

    private fun drawBars(c: Canvas, v: FloatArray, w: Float, h: Float, mirrored: Boolean) {
        val left = w * 0.12f
        val right = w * 0.88f
        val base = h * 0.91f
        val gap = w * 0.0025f
        val bw = (right - left - gap * (v.size - 1)) / v.size
        paint.style = Paint.Style.FILL
        for (i in v.indices) {
            val level = v[i].coerceIn(0f, 1f)
            val x = left + i * (bw + gap)
            val barH = h * (0.012f + level * 0.105f)
            paint.color = if (i % 6 == 0) paleGold else softGold
            c.drawRoundRect(x, base - barH, x + bw, base, bw / 2f, bw / 2f, paint)
            if (mirrored) {
                c.drawRoundRect(x, base + h * 0.006f, x + bw,
                    base + h * 0.006f + barH * 0.72f, bw / 2f, bw / 2f, paint)
            }
        }
    }

    private fun drawCircular(c: Canvas, v: FloatArray, w: Float, h: Float, radial: Boolean) {
        val cx = w * 0.5f
        val cy = h * 0.80f
        val baseRadius = h * if (radial) 0.055f else 0.072f
        val minStroke = max(2.2f, h * 0.0024f)
        val maxExtension = h * if (radial) 0.105f else 0.075f

        // Delicate guide rings only; no filled disc or badge in the centre.
        paint.style = Paint.Style.STROKE
        paint.strokeCap = Paint.Cap.ROUND
        paint.strokeWidth = max(1f, h * 0.0012f)
        paint.color = 0x668D6B35
        c.drawCircle(cx, cy, baseRadius, paint)
        if (radial) {
            paint.color = 0x338D6B35
            c.drawCircle(cx, cy, baseRadius + maxExtension * 0.52f, paint)
        }

        for (i in v.indices) {
            val level = v[i].coerceIn(0f, 1f)
            val angle = Math.toRadians(i * 360.0 / v.size - 90.0)
            val inner = baseRadius + h * 0.008f
            val extension = h * 0.009f + level * maxExtension
            val outer = inner + extension
            paint.strokeWidth = minStroke + h * 0.0013f * level
            paint.color = when {
                i % 8 == 0 -> paleGold
                i % 3 == 0 -> gold
                else -> softGold
            }
            c.drawLine(
                cx + cos(angle).toFloat() * inner,
                cy + sin(angle).toFloat() * inner,
                cx + cos(angle).toFloat() * outer,
                cy + sin(angle).toFloat() * outer,
                paint
            )
        }

        // A small live bass pulse replaces the static centre emblem.
        val bass = v.take(min(6, v.size)).average().toFloat().coerceIn(0f, 1f)
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = max(1.4f, h * 0.0018f)
        paint.color = 0x99E6B85D.toInt()
        c.drawCircle(cx, cy, baseRadius * (0.43f + bass * 0.12f), paint)
        paint.style = Paint.Style.FILL
    }

    private fun drawWave(c: Canvas, v: FloatArray, w: Float, h: Float) {
        if (v.size < 2) return
        val path = Path()
        for (i in v.indices) {
            val x = w * 0.12f + i * (w * 0.76f / (v.size - 1))
            val y = h * 0.86f - v[i].coerceIn(0f, 1f) * h * 0.13f
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        paint.style = Paint.Style.STROKE
        paint.strokeCap = Paint.Cap.ROUND
        paint.strokeJoin = Paint.Join.ROUND
        paint.strokeWidth = h * 0.008f
        paint.color = 0x44E6B85D
        c.drawPath(path, paint)
        paint.strokeWidth = h * 0.0026f
        paint.color = paleGold
        c.drawPath(path, paint)
        paint.style = Paint.Style.FILL
    }

    private fun drawDots(c: Canvas, v: FloatArray, w: Float, h: Float) {
        val left = w * 0.14f
        val step = w * 0.72f / v.size
        val cy = h * 0.86f
        paint.style = Paint.Style.FILL
        for (i in v.indices) {
            val rows = (v[i].coerceIn(0f, 1f) * 9f).toInt().coerceIn(1, 9)
            for (j in 0 until rows) {
                paint.color = if (j == rows - 1) paleGold else softGold
                val x = left + i * step
                val y = cy - j * h * 0.010f
                c.drawCircle(x, y, max(1.8f, h * 0.0028f), paint)
            }
        }
    }
}
