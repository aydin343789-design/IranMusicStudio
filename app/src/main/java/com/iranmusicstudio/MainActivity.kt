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
        val styles = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        styles.addView(styleButton("میله‌ای", 0), LinearLayout.LayoutParams(0, -2, 1f))
        styles.addView(styleButton("موجی", 1), LinearLayout.LayoutParams(0, -2, 1f))
        styles.addView(styleButton("ضربانی", 2), LinearLayout.LayoutParams(0, -2, 1f))
        page.addView(styles, matchWrap())
        spectrum = SpectrumView(this)
        page.addView(spectrum, LinearLayout.LayoutParams(-1, 150))

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
            text = "فایل نهایی در پوشه Movies/PahlaviStudio ذخیره می‌شود. اکولایزر این نسخه در پیش‌نمایش زنده فعال است؛ رندر اکولایزر داخل MP4 را در مرحلهٔ بعد به موتور خروجی اضافه می‌کنیم."
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
    private var levels = FloatArray(44) { 0.025f }
    private var style = 0

    fun setStyle(value: Int) { style = value; invalidate() }

    fun updateFft(fft: ByteArray) {
        val targets = FloatArray(levels.size)
        val maxBin = (fft.size / 2 - 1).coerceAtLeast(2)
        for (i in targets.indices) {
            val low = (kotlin.math.exp(kotlin.math.ln(2.0) +
                (i.toDouble() / targets.size) * kotlin.math.ln(maxBin.toDouble() / 2.0))).toInt().coerceIn(1, maxBin)
            val high = (kotlin.math.exp(kotlin.math.ln(2.0) +
                ((i + 1).toDouble() / targets.size) * kotlin.math.ln(maxBin.toDouble() / 2.0))).toInt()
                .coerceIn(low + 1, maxBin + 1)
            var peak = 0.0
            for (bin in low until high) {
                val index = bin * 2
                if (index + 1 < fft.size) {
                    val re = fft[index].toInt()
                    val im = fft[index + 1].toInt()
                    val magnitude = kotlin.math.sqrt((re * re + im * im).toDouble())
                    if (magnitude > peak) peak = magnitude
                }
            }
            targets[i] = ((kotlin.math.ln(1.0 + peak) / kotlin.math.ln(129.0)) * 0.96 + 0.025)
                .toFloat().coerceIn(0.025f, 1f)
        }
        post {
            for (i in levels.indices) {
                val factor = if (targets[i] > levels[i]) 0.42f else 0.16f
                levels[i] += (targets[i] - levels[i]) * factor
            }
            invalidate()
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        canvas.drawColor(0xFF0B111B.toInt())
        val gold = 0xFFE4B85F.toInt()
        val paleGold = 0xFFFFE6A8.toInt()
        val centerY = height * 0.53f
        paint.color = 0xFF293241.toInt()
        paint.strokeWidth = 1f
        canvas.drawLine(14f, centerY, width - 14f, centerY, paint)

        val count = levels.size
        val gap = 4f
        val barWidth = ((width - 36f - gap * (count - 1)) / count).coerceAtLeast(1f)
        val maxHeight = height * 0.72f

        if (style == 1) {
            val path = android.graphics.Path()
            for (i in levels.indices) {
                val x = 18f + i * (barWidth + gap) + barWidth / 2f
                val y = centerY - levels[i] * maxHeight * 0.48f
                if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
            }
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = 2.4f
            paint.strokeCap = Paint.Cap.ROUND
            paint.color = paleGold
            canvas.drawPath(path, paint)
            paint.style = Paint.Style.FILL
            for (i in levels.indices step 4) {
                val x = 18f + i * (barWidth + gap) + barWidth / 2f
                val y = centerY - levels[i] * maxHeight * 0.48f
                paint.color = gold
                canvas.drawCircle(x, y, 2.2f, paint)
            }
            return
        }

        levels.forEachIndexed { i, level ->
            val x = 18f + i * (barWidth + gap)
            val h = (level * maxHeight).coerceAtLeast(3f)
            paint.color = if (i % 4 == 0) paleGold else gold
            if (style == 0) {
                canvas.drawRoundRect(x, centerY - h, x + barWidth, centerY, barWidth / 2, barWidth / 2, paint)
            } else {
                val radius = (barWidth * 0.46f).coerceAtLeast(1.2f)
                canvas.drawCircle(x + barWidth / 2, centerY - h * 0.58f, radius, paint)
                canvas.drawCircle(x + barWidth / 2, centerY + h * 0.58f, radius, paint)
            }
        }
    }
}
