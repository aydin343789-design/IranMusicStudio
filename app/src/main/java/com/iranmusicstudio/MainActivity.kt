package com.iranmusicstudio

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Canvas
import android.graphics.Paint
import android.media.audiofx.Visualizer
import android.net.Uri
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView

class MainActivity : ComponentActivity() {
    private var player: ExoPlayer? = null
    private var visualizer: Visualizer? = null
    private lateinit var spectrum: SpectrumView
    private lateinit var songLabel: TextView
    private lateinit var backgroundLabel: TextView
    private lateinit var statusLabel: TextView

    private val pickAudio = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let { keepPermission(it); loadAudio(it) }
    }
    private val pickBackground = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let {
            keepPermission(it)
            backgroundLabel.text = "پس‌زمینه انتخاب شد"
        }
    }
    private val askAudioPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) attachVisualizer()
            else statusLabel.text = "مجوز تحلیل صدا داده نشد؛ پخش آهنگ همچنان فعال است."
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = android.graphics.Color.rgb(10, 14, 24)
        window.navigationBarColor = android.graphics.Color.rgb(10, 14, 24)
        buildUi()
        player = ExoPlayer.Builder(this).build()
        findViewById<PlayerView>(PLAYER_ID).player = player
        player?.addListener(object : Player.Listener {
            override fun onPlaybackStateChanged(state: Int) {
                if (state == Player.STATE_READY) {
                    statusLabel.text = "آهنگ آماده پخش است."
                    checkPermissionAndAttach()
                }
                if (state == Player.STATE_ENDED) statusLabel.text = "پخش آهنگ تمام شد."
            }
        })
    }

    private fun buildUi() {
        val page = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(22, 28, 22, 20)
            setBackgroundColor(android.graphics.Color.rgb(10, 14, 24))
        }
        page.addView(TextView(this).apply {
            text = "IRAN MUSIC STUDIO"
            textSize = 23f
            gravity = Gravity.CENTER
            setTextColor(android.graphics.Color.rgb(224, 190, 120))
        }, matchWrap())
        page.addView(TextView(this).apply {
            text = "استودیوی ساخت موزیک‌ویدئوی میهنی"
            textSize = 13f
            gravity = Gravity.CENTER
            setTextColor(android.graphics.Color.LTGRAY)
            setPadding(0, 8, 0, 20)
        }, matchWrap())

        songLabel = label("هنوز آهنگی انتخاب نشده است")
        page.addView(songLabel, matchWrap())
        page.addView(button("انتخاب آهنگ · MP3 / WAV / M4A") {
            pickAudio.launch(arrayOf("audio/*"))
        }, matchWrap())

        backgroundLabel = label("پس‌زمینه: انتخاب نشده")
        page.addView(backgroundLabel, matchWrap())
        page.addView(button("انتخاب تصویر یا ویدئوی پس‌زمینه") {
            pickBackground.launch(arrayOf("image/*", "video/*"))
        }, matchWrap())

        page.addView(label("پیش‌نمایش اکولایزر زنده"), matchWrap())
        spectrum = SpectrumView(this)
        page.addView(spectrum, LinearLayout.LayoutParams(-1, 190))

        page.addView(PlayerView(this).apply {
            id = PLAYER_ID
            useController = true
            setBackgroundColor(android.graphics.Color.rgb(20, 25, 37))
        }, LinearLayout.LayoutParams(-1, 76))

        statusLabel = label("برای شروع، یک آهنگ انتخاب کن.")
        page.addView(statusLabel, matchWrap())
        page.addView(TextView(this).apply {
            text = "نسخه 0.1: انتخاب و پخش موزیک و تحلیل زنده صدا. خروجی‌گیری MP4 در مرحله بعد اضافه می‌شود."
            textSize = 12f
            setTextColor(android.graphics.Color.LTGRAY)
            setPadding(4, 14, 4, 4)
        }, matchWrap())
        setContentView(page)
    }

    private fun loadAudio(uri: Uri) {
        releaseVisualizer()
        songLabel.text = "فایل موسیقی انتخاب شد"
        player?.apply {
            setMediaItem(MediaItem.fromUri(uri))
            prepare()
            playWhenReady = false
        }
        statusLabel.text = "برای پخش از کنترل پایین استفاده کن."
    }

    private fun checkPermissionAndAttach() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
            == PackageManager.PERMISSION_GRANTED) attachVisualizer()
        else askAudioPermission.launch(Manifest.permission.RECORD_AUDIO)
    }

    private fun attachVisualizer() {
        val sessionId = player?.audioSessionId ?: 0
        if (sessionId <= 0) return
        try {
            releaseVisualizer()
            visualizer = Visualizer(sessionId).apply {
                captureSize = Visualizer.getCaptureSizeRange()[1]
                setDataCaptureListener(object : Visualizer.OnDataCaptureListener {
                    override fun onWaveFormDataCapture(v: Visualizer?, data: ByteArray?, rate: Int) = Unit
                    override fun onFftDataCapture(v: Visualizer?, data: ByteArray?, rate: Int) {
                        if (data != null) spectrum.updateFft(data)
                    }
                }, Visualizer.getMaxCaptureRate() / 2, false, true)
                enabled = true
            }
            statusLabel.text = "تحلیل زنده فرکانس فعال است."
        } catch (e: Exception) {
            statusLabel.text = "تحلیل زنده در این دستگاه در دسترس نیست."
        }
    }

    private fun releaseVisualizer() {
        try { visualizer?.enabled = false; visualizer?.release() } catch (_: Exception) { }
        visualizer = null
        if (::spectrum.isInitialized) spectrum.setBars(FloatArray(32))
    }

    private fun keepPermission(uri: Uri) {
        try {
            contentResolver.takePersistableUriPermission(
                uri, android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION
            )
        } catch (_: Exception) { }
    }

    private fun label(value: String) = TextView(this).apply {
        text = value
        textSize = 13f
        setTextColor(android.graphics.Color.WHITE)
        setPadding(4, 12, 4, 8)
    }
    private fun button(value: String, click: () -> Unit) = Button(this).apply {
        text = value
        textSize = 13f
        setTextColor(android.graphics.Color.rgb(15, 18, 26))
        setBackgroundColor(android.graphics.Color.rgb(210, 174, 104))
        setOnClickListener { click() }
    }
    private fun matchWrap() = LinearLayout.LayoutParams(-1, -2)

    override fun onDestroy() {
        releaseVisualizer()
        player?.release()
        player = null
        super.onDestroy()
    }
    companion object { private const val PLAYER_ID = 0x1A2026 }
}

class SpectrumView(context: android.content.Context) : View(context) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private var bars = FloatArray(32)

    fun updateFft(fft: ByteArray) {
        val next = FloatArray(bars.size)
        for (i in next.indices) {
            val idx = 2 + i * 3
            if (idx + 1 < fft.size) {
                val re = fft[idx].toInt()
                val im = fft[idx + 1].toInt()
                val mag = kotlin.math.sqrt((re * re + im * im).toDouble()).toFloat()
                next[i] = (mag / 22f).coerceIn(0.06f, 1f)
            }
        }
        post { bars = next; invalidate() }
    }
    fun setBars(values: FloatArray) { bars = values; invalidate() }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        canvas.drawColor(android.graphics.Color.rgb(17, 23, 35))
        val gap = 7f
        val barWidth = (width - gap * (bars.size + 1)) / bars.size
        val base = height - 18f
        bars.forEachIndexed { i, value ->
            val h = (height - 42f) * value.coerceIn(0.04f, 1f)
            paint.color = when (i % 3) {
                0 -> android.graphics.Color.rgb(35, 150, 95)
                1 -> android.graphics.Color.WHITE
                else -> android.graphics.Color.rgb(190, 55, 65)
            }
            val left = gap + i * (barWidth + gap)
            canvas.drawRoundRect(left, base - h, left + barWidth, base, 5f, 5f, paint)
        }
        paint.color = android.graphics.Color.rgb(224, 184, 105)
        paint.strokeWidth = 2f
        canvas.drawLine(12f, base + 5f, width - 12f, base + 5f, paint)
    }
}
