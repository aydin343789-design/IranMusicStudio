package com.iranmusicstudio

import android.Manifest
import android.content.ContentValues
import android.content.pm.PackageManager
import android.graphics.Canvas
import android.graphics.Paint
import android.media.MediaMetadataRetriever
import android.media.audiofx.Visualizer
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.ImageView
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.graphics.drawable.GradientDrawable
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.transformer.Composition
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.EditedMediaItemSequence
import androidx.media3.transformer.ExportException
import androidx.media3.transformer.ExportResult
import androidx.media3.transformer.Transformer
import androidx.media3.ui.PlayerView
import java.io.File
import java.io.FileInputStream

@UnstableApi
class MainActivity : ComponentActivity() {
    private var audioPlayer: ExoPlayer? = null
    private var videoPlayer: ExoPlayer? = null
    private var visualizer: Visualizer? = null
    private var selectedAudio: Uri? = null
    private var selectedBackground: Uri? = null
    private var transformer: Transformer? = null
    private lateinit var spectrum: SpectrumView
    private lateinit var songLabel: TextView
    private lateinit var videoLabel: TextView
    private lateinit var status: TextView
    private var visualStyle = 0

    private val pickAudio = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let {
            keepPermission(it)
            selectedAudio = it
            songLabel.text = "♫  فایل موسیقی انتخاب شد"
            audioPlayer?.setMediaItem(MediaItem.fromUri(it))
            audioPlayer?.prepare()
            status.text = "آهنگ آماده است؛ برای شنیدن، پخش موسیقی را بزن."
        }
    }
    private val pickBackground = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let {
            keepPermission(it)
            selectedBackground = it
            videoLabel.text = if (contentResolver.getType(it)?.startsWith("image/") == true)
                "▣  تصویر پس‌زمینه انتخاب شد" else "▣  ویدئوی پس‌زمینه انتخاب شد"
            videoPlayer?.setMediaItem(MediaItem.fromUri(it))
            videoPlayer?.prepare()
            videoPlayer?.playWhenReady = false
            status.text = "پس‌زمینه آمادهٔ پیش‌نمایش است."
        }
    }
    private val askAudioPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) attachVisualizer()
            else status.text = "مجوز اکولایزر داده نشد؛ پخش موسیقی همچنان فعال است."
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = android.graphics.Color.rgb(8, 13, 25)
        window.navigationBarColor = android.graphics.Color.rgb(8, 13, 25)
        buildUi()
        audioPlayer = ExoPlayer.Builder(this).build()
        videoPlayer = ExoPlayer.Builder(this).build()
        findViewById<PlayerView>(AUDIO_PLAYER_ID).player = audioPlayer
        findViewById<PlayerView>(VIDEO_PLAYER_ID).player = videoPlayer
        audioPlayer?.addListener(object : Player.Listener {
            override fun onPlaybackStateChanged(state: Int) {
                if (state == Player.STATE_READY) checkPermissionAndAttach()
                if (state == Player.STATE_ENDED) status.text = "پخش موسیقی تمام شد."
            }
        })
    }

    private fun buildUi() {
        val page = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(18, 24, 18, 28)
            setBackgroundColor(android.graphics.Color.rgb(8, 13, 25))
        }
        val brandHeader = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(14, 14, 14, 14)
            background = roundedPanel(0xFF111722.toInt(), 0xFF80602F.toInt(), 22f)
        }
        brandHeader.addView(ImageView(this).apply {
            setImageResource(R.drawable.pahlavi_logo)
            scaleType = ImageView.ScaleType.FIT_CENTER
        }, LinearLayout.LayoutParams(82, 82))
        val brandTitles = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(14, 0, 0, 0)
            addView(TextView(this@MainActivity).apply {
                text = "استودیو پهلوی"
                textSize = 24f
                setTextColor(0xFFE7C27A.toInt())
                typeface = android.graphics.Typeface.create("sans-serif", android.graphics.Typeface.BOLD)
            }, matchWrap())
            addView(TextView(this@MainActivity).apply {
                text = "PAHLAVI STUDIO  •  MUSIC & VIDEO"
                textSize = 10f
                letterSpacing = 0.12f
                setTextColor(0xFFB8A27C.toInt())
                setPadding(0, 4, 0, 0)
            }, matchWrap())
            addView(TextView(this@MainActivity).apply {
                text = "ساخت موزیک‌ویدئو با جلوه‌های صوتی"
                textSize = 12f
                setTextColor(0xFFD4D8E0.toInt())
                setPadding(0, 7, 0, 0)
            }, matchWrap())
        }
        brandHeader.addView(brandTitles, LinearLayout.LayoutParams(0, -2, 1f))
        page.addView(brandHeader, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = 18 })

        page.addView(sectionTitle("۱  ·  انتخاب موسیقی"), matchWrap())
        songLabel = info("هنوز آهنگی انتخاب نشده")
        page.addView(songLabel, matchWrap())
        page.addView(actionButton("انتخاب فایل موسیقی") {
            pickAudio.launch(arrayOf("audio/*"))
        }, matchWrap())

        page.addView(sectionTitle("۲  ·  تصویر یا ویدئوی پس‌زمینه"), matchWrap())
        videoLabel = info("هنوز پس‌زمینه‌ای انتخاب نشده")
        page.addView(videoLabel, matchWrap())
        page.addView(actionButton("انتخاب تصویر / ویدئو") {
            pickBackground.launch(arrayOf("image/*", "video/*"))
        }, matchWrap())

        page.addView(sectionTitle("پیش‌نمایش پس‌زمینه"), matchWrap())
        page.addView(PlayerView(this).apply {
            id = VIDEO_PLAYER_ID
            useController = true
            setBackgroundColor(android.graphics.Color.rgb(18, 25, 40))
        }, LinearLayout.LayoutParams(-1, 210))

        page.addView(actionButton("▶  پخش / توقف ویدئو") {
            val p = videoPlayer
            if (p?.isPlaying == true) p.pause() else p?.play()
        }, matchWrap())

        page.addView(sectionTitle("۳  ·  انتخاب طرح اکولایزر"), matchWrap())
        val styles = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            listOf("خطی", "دایره‌ای", "شعاعی", "موجی", "نقطه‌ای", "طیفی").forEachIndexed { index, label ->
                addView(styleButton(label, index), LinearLayout.LayoutParams(76, -2).apply { marginEnd = 7 })
            }
        }
        page.addView(HorizontalScrollView(this).apply {
            isHorizontalScrollBarEnabled = false
            addView(styles)
        }, matchWrap())
        spectrum = SpectrumView(this)
        page.addView(spectrum, LinearLayout.LayoutParams(-1, 176))

        page.addView(sectionTitle("پخش موسیقی"), matchWrap())
        page.addView(PlayerView(this).apply {
            id = AUDIO_PLAYER_ID
            useController = true
            setBackgroundColor(android.graphics.Color.rgb(18, 25, 40))
        }, LinearLayout.LayoutParams(-1, 68))

        page.addView(sectionTitle("۴  ·  ساخت موزیک‌ویدئو"), matchWrap())
        page.addView(actionButton("✦  ترکیب و ساخت فایل MP4") { exportVideo() }, matchWrap())
        status = info("برای شروع، موسیقی و پس‌زمینه را انتخاب کن.")
        page.addView(status, matchWrap())
        page.addView(TextView(this).apply {
            text = "فایل نهایی در پوشه Movies/PahlaviStudio ذخیره می‌شود. شش طرح اکولایزر زنده در پیش‌نمایش قابل انتخاب‌اند؛ لایه‌گذاری اکولایزر روی MP4 هنوز در حال پیاده‌سازی است."
            textSize = 11f
            setTextColor(android.graphics.Color.rgb(165, 174, 190))
            setPadding(4, 14, 4, 4)
        }, matchWrap())

        val scroll = ScrollView(this).apply {
            isFillViewport = true
            setBackgroundColor(android.graphics.Color.rgb(8, 13, 25))
            addView(page)
        }
        setContentView(scroll)
    }

    private fun exportVideo() {
        val audio = selectedAudio
        val background = selectedBackground
        if (audio == null || background == null) {
            status.text = "برای ساخت ویدئو، ابتدا موسیقی و پس‌زمینه را انتخاب کن."
            return
        }
        if (transformer != null) {
            status.text = "ساخت ویدئو در حال انجام است؛ لطفاً صبر کن."
            return
        }
        val durationMs = try {
            MediaMetadataRetriever().use { retriever ->
                retriever.setDataSource(this, audio)
                retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLong()
            }
        } catch (_: Exception) { null }
        if (durationMs == null || durationMs <= 0L) {
            status.text = "مدت آهنگ خوانده نشد؛ لطفاً فایل دیگری انتخاب کن."
            return
        }

        val isImage = contentResolver.getType(background)?.startsWith("image/") == true
        val videoItem = if (isImage) {
            val image = MediaItem.Builder().setUri(background).setImageDurationMs(durationMs).build()
            EditedMediaItem.Builder(image).setFrameRate(30).build()
        } else {
            EditedMediaItem.Builder(MediaItem.fromUri(background)).setRemoveAudio(true).build()
        }
        val audioItem = EditedMediaItem.Builder(MediaItem.fromUri(audio)).build()
        val videoSequenceBuilder = EditedMediaItemSequence.Builder()
            .addItems(listOf(videoItem))
        if (!isImage) videoSequenceBuilder.setIsLooping(true)
        val videoSequence = videoSequenceBuilder.build()
        val audioSequence = EditedMediaItemSequence.Builder()
            .addItems(listOf(audioItem))
            .build()
        val composition = Composition.Builder(videoSequence, audioSequence).build()
        val output = File(cacheDir, "iran_studio_${System.currentTimeMillis()}.mp4")

        status.text = if (isImage) "در حال ساخت ویدئو؛ تصویر تا پایان آهنگ نمایش داده می‌شود…" else "در حال ساخت ویدئو؛ کلیپ کوتاه در صورت نیاز تکرار و انتهای کلیپ بلند با پایان آهنگ هماهنگ می‌شود…"
        val engine = Transformer.Builder(this)
            .setVideoMimeType(MimeTypes.VIDEO_H264)
            .addListener(object : Transformer.Listener {
                override fun onCompleted(composition: Composition, exportResult: ExportResult) {
                    transformer = null
                    try {
                        saveToMovies(output)
                        status.text = "✓ موزیک‌ویدئو در Movies/PahlaviStudio ذخیره شد."
                    } catch (e: Exception) {
                        status.text = "خروجی ساخته شد اما ذخیره‌سازی ناموفق بود: ${e.message}"
                    }
                }
                override fun onError(
                    composition: Composition,
                    exportResult: ExportResult,
                    exportException: ExportException
                ) {
                    transformer = null
                    output.delete()
                    status.text = "ساخت MP4 ناموفق بود: ${exportException.message ?: "خطای ناشناخته"}"
                }
            }).build()
        transformer = engine
        try {
            engine.start(composition, output.absolutePath)
        } catch (e: Exception) {
            transformer = null
            output.delete()
            status.text = "شروع خروجی‌گیری ممکن نشد: ${e.message}"
        }
    }

    private fun saveToMovies(file: File) {
        if (!file.exists() || file.length() == 0L) error("فایل خروجی خالی است")
        val name = "PahlaviStudio_${System.currentTimeMillis()}.mp4"
        if (Build.VERSION.SDK_INT >= 29) {
            val values = ContentValues().apply {
                put(MediaStore.Video.Media.DISPLAY_NAME, name)
                put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
                put(MediaStore.Video.Media.RELATIVE_PATH, Environment.DIRECTORY_MOVIES + "/PahlaviStudio")
                put(MediaStore.Video.Media.IS_PENDING, 1)
            }
            val uri = contentResolver.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, values)
                ?: error("ساخت فایل مقصد ممکن نشد")
            try {
                contentResolver.openOutputStream(uri)?.use { out ->
                    FileInputStream(file).use { input -> input.copyTo(out) }
                } ?: error("فایل مقصد باز نشد")
                contentResolver.update(uri, ContentValues().apply {
                    put(MediaStore.Video.Media.IS_PENDING, 0)
                }, null, null)
                file.delete()
            } catch (e: Exception) {
                contentResolver.delete(uri, null, null)
                throw e
            }
        } else {
            val folder = getExternalFilesDir(Environment.DIRECTORY_MOVIES) ?: filesDir
            file.copyTo(File(folder, name), overwrite = true)
            file.delete()
        }
    }

    private fun checkPermissionAndAttach() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) ==
            PackageManager.PERMISSION_GRANTED) attachVisualizer()
        else askAudioPermission.launch(Manifest.permission.RECORD_AUDIO)
    }

    private fun attachVisualizer() {
        val id = audioPlayer?.audioSessionId ?: 0
        if (id <= 0) return
        try {
            releaseVisualizer()
            visualizer = Visualizer(id).apply {
                captureSize = Visualizer.getCaptureSizeRange()[1]
                setDataCaptureListener(object : Visualizer.OnDataCaptureListener {
                    override fun onWaveFormDataCapture(v: Visualizer?, data: ByteArray?, rate: Int) = Unit
                    override fun onFftDataCapture(v: Visualizer?, data: ByteArray?, rate: Int) {
                        if (data != null) spectrum.updateFft(data)
                    }
                }, Visualizer.getMaxCaptureRate() / 2, false, true)
                enabled = true
            }
        } catch (_: Exception) { status.text = "اکولایزر زنده در این دستگاه در دسترس نیست." }
    }

    private fun releaseVisualizer() {
        try { visualizer?.enabled = false; visualizer?.release() } catch (_: Exception) { }
        visualizer = null
    }
    private fun keepPermission(uri: Uri) {
        try { contentResolver.takePersistableUriPermission(uri, android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION) }
        catch (_: Exception) { }
    }
    private fun sectionTitle(value: String) = TextView(this).apply {
        text = value
        textSize = 15f
        setTextColor(android.graphics.Color.rgb(231, 194, 120))
        setPadding(2, 20, 2, 6)
    }
    private fun info(value: String) = TextView(this).apply {
        text = value
        textSize = 12f
        setTextColor(android.graphics.Color.WHITE)
        setPadding(5, 8, 5, 8)
    }
    private fun roundedPanel(fill: Int, stroke: Int, radius: Float) = GradientDrawable().apply {
        setColor(fill)
        cornerRadius = radius
        setStroke(1, stroke)
    }
    private fun actionButton(value: String, action: () -> Unit) = Button(this).apply {
        text = value
        textSize = 13f
        isAllCaps = false
        setTextColor(0xFFFFE7B5.toInt())
        background = roundedPanel(0xFF20202A.toInt(), 0xFFB58A45.toInt(), 18f)
        setPadding(18, 12, 18, 12)
        setOnClickListener { action() }
    }
    private fun styleButton(value: String, style: Int) = Button(this).apply {
        text = value
        textSize = 11f
        isAllCaps = false
        setTextColor(0xFFFFE8BE.toInt())
        background = roundedPanel(if (visualStyle == style) 0xFF49371F.toInt() else 0xFF171E2A.toInt(),
            if (visualStyle == style) 0xFFE0B35E.toInt() else 0xFF465064.toInt(), 14f)
        setPadding(5, 10, 5, 10)
        setOnClickListener {
            visualStyle = style
            spectrum.setStyle(style)
            status.text = "طرح اکولایزر «$value» انتخاب شد."
            (parent as? LinearLayout)?.let { row ->
                for (i in 0 until row.childCount) {
                    val child = row.getChildAt(i) as? Button
                    child?.background = roundedPanel(
                        if (i == style) 0xFF49371F.toInt() else 0xFF171E2A.toInt(),
                        if (i == style) 0xFFE0B35E.toInt() else 0xFF465064.toInt(), 14f)
                }
            }
        }
    }
    private fun matchWrap() = LinearLayout.LayoutParams(-1, -2)

    override fun onDestroy() {
        try { transformer?.cancel() } catch (_: Exception) { }
        transformer = null
        releaseVisualizer()
        audioPlayer?.release()
        videoPlayer?.release()
        audioPlayer = null
        videoPlayer = null
        super.onDestroy()
    }
    companion object {
        private const val AUDIO_PLAYER_ID = 0x1A2026
        private const val VIDEO_PLAYER_ID = 0x1A2027
    }
}

class SpectrumView(context: android.content.Context) : View(context) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private var levels = FloatArray(48) { 0.025f }
    private var style = 0
    fun setStyle(value: Int) { style = value.coerceIn(0, 5); invalidate() }

    fun updateFft(fft: ByteArray) {
        val targets = FloatArray(levels.size)
        val maxBin = (fft.size / 2 - 1).coerceAtLeast(2)
        for (i in targets.indices) {
            val low = (kotlin.math.exp(kotlin.math.ln(2.0) + (i.toDouble() / targets.size) *
                kotlin.math.ln(maxBin.toDouble() / 2.0))).toInt().coerceIn(1, maxBin)
            val high = (kotlin.math.exp(kotlin.math.ln(2.0) + ((i + 1).toDouble() / targets.size) *
                kotlin.math.ln(maxBin.toDouble() / 2.0))).toInt().coerceIn(low + 1, maxBin + 1)
            var peak = 0.0
            for (bin in low until high) {
                val index = bin * 2
                if (index + 1 < fft.size) {
                    val re = fft[index].toInt(); val im = fft[index + 1].toInt()
                    peak = maxOf(peak, kotlin.math.sqrt((re * re + im * im).toDouble()))
                }
            }
            targets[i] = ((kotlin.math.ln(1.0 + peak) / kotlin.math.ln(129.0)) * 0.96 + 0.025)
                .toFloat().coerceIn(0.025f, 1f)
        }
        post {
            for (i in levels.indices) {
                val factor = if (targets[i] > levels[i]) 0.34f else 0.12f
                levels[i] += (targets[i] - levels[i]) * factor
            }
            invalidate()
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        canvas.drawColor(0xFF0B111B.toInt())
        val gold = 0xFFE4B85F.toInt(); val pale = 0xFFFFE6A8.toInt()
        val cx = width / 2f; val cy = height / 2f
        val side = minOf(width, height).toFloat()
        paint.style = Paint.Style.STROKE; paint.strokeWidth = 1f; paint.color = 0xFF293241.toInt()
        canvas.drawLine(18f, height - 16f, width - 18f, height - 16f, paint)
        paint.style = Paint.Style.FILL
        when (style) {
            0, 5 -> {
                val gap = if (style == 0) 3f else 2f
                val bw = ((width - 36f - gap * (levels.size - 1)) / levels.size).coerceAtLeast(1f)
                val base = height - 17f
                levels.forEachIndexed { i, v ->
                    val h = (v * height * if (style == 0) 0.70f else 0.82f).coerceAtLeast(2.5f)
                    val x = 18f + i * (bw + gap)
                    paint.color = if (i % 6 == 0) pale else gold
                    canvas.drawRoundRect(x, base - h, x + bw, base, bw / 2, bw / 2, paint)
                }
            }
            1, 2 -> {
                val radius = if (style == 1) side * 0.25f else side * 0.10f
                paint.style = Paint.Style.STROKE; paint.strokeCap = Paint.Cap.ROUND
                paint.strokeWidth = if (style == 1) 3f else 3.4f
                if (style == 1) {
                    paint.strokeWidth = 1.2f; paint.color = 0x665C4A2D
                    canvas.drawCircle(cx, cy, radius, paint)
                }
                levels.forEachIndexed { i, v ->
                    val angle = Math.toRadians(i * 360.0 / levels.size - 90.0)
                    val inner = radius + if (style == 1) 5f else 8f
                    val outer = inner + (if (style == 1) 5f else 8f) + v * side * if (style == 1) 0.16f else 0.29f
                    paint.color = if (i % 4 == 0) pale else gold
                    canvas.drawLine(cx + kotlin.math.cos(angle).toFloat() * inner,
                        cy + kotlin.math.sin(angle).toFloat() * inner,
                        cx + kotlin.math.cos(angle).toFloat() * outer,
                        cy + kotlin.math.sin(angle).toFloat() * outer, paint)
                }
                paint.style = Paint.Style.FILL
                if (style == 1) { paint.color = pale; canvas.drawCircle(cx, cy, 2.5f, paint) }
            }
            3 -> {
                val path = android.graphics.Path()
                levels.forEachIndexed { i, v ->
                    val x = 18f + i * (width - 36f) / (levels.size - 1)
                    val y = cy - (v - 0.08f) * height * 0.65f
                    if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
                }
                paint.style = Paint.Style.STROKE; paint.strokeWidth = 2.2f
                paint.strokeCap = Paint.Cap.ROUND; paint.strokeJoin = Paint.Join.ROUND
                paint.color = 0x44E4B85F; paint.strokeWidth = 7f; canvas.drawPath(path, paint)
                paint.color = pale; paint.strokeWidth = 2.2f; canvas.drawPath(path, paint)
            }
            4 -> {
                val cols = 24; val step = (width - 40f) / cols
                for (i in 0 until cols) {
                    val v = levels[i * levels.size / cols]
                    val x = 20f + i * step + step / 2f
                    val rows = (v * 9f).toInt().coerceIn(1, 9)
                    for (j in 0 until rows) {
                        val y = cy - (j + 1) * 7f / 1.6f
                        paint.color = if (j == rows - 1) pale else gold
                        canvas.drawCircle(x, y, 2.1f, paint)
                        canvas.drawCircle(x, height - y, 2.1f, paint)
                    }
                }
            }
        }
        paint.style = Paint.Style.FILL; paint.strokeCap = Paint.Cap.BUTT
    }
}
