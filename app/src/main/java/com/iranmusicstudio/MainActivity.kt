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
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
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
        page.addView(TextView(this).apply {
            text = "IRAN MUSIC STUDIO"
            textSize = 25f
            gravity = Gravity.CENTER
            letterSpacing = 0.12f
            setTextColor(android.graphics.Color.rgb(230, 190, 112))
            setPadding(0, 8, 0, 2)
        }, matchWrap())
        page.addView(TextView(this).apply {
            text = "استودیوی ساخت موزیک‌ویدئو"
            textSize = 14f
            gravity = Gravity.CENTER
            setTextColor(android.graphics.Color.LTGRAY)
            setPadding(0, 0, 0, 20)
        }, matchWrap())

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
            text = "فایل نهایی در پوشه Movies/IranMusicStudio ذخیره می‌شود. طرح اکولایزر فعلاً در پیش‌نمایش زنده است و در خروجی MP4 به مرحلهٔ بعد اضافه خواهد شد."
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
        var videoSequence = EditedMediaItemSequence.Builder()
            .addItems(listOf(videoItem))
            .build()
        if (!isImage) videoSequence = videoSequence.buildUpon().setIsLooping(true).build()
        val audioSequence = EditedMediaItemSequence.Builder()
            .addItems(listOf(audioItem))
            .build()
        val composition = Composition.Builder(videoSequence, audioSequence).build()
        val output = File(cacheDir, "iran_studio_${System.currentTimeMillis()}.mp4")

        status.text = "در حال ترکیب موسیقی و پس‌زمینه… لطفاً برنامه را نبند."
        val engine = Transformer.Builder(this)
            .setVideoMimeType(MimeTypes.VIDEO_H264)
            .addListener(object : Transformer.Listener {
                override fun onCompleted(composition: Composition, exportResult: ExportResult) {
                    transformer = null
                    try {
                        saveToMovies(output)
                        status.text = "✓ موزیک‌ویدئو با موفقیت در پوشه Movies/IranMusicStudio ذخیره شد."
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
        val name = "IranMusicStudio_${System.currentTimeMillis()}.mp4"
        if (Build.VERSION.SDK_INT >= 29) {
            val values = ContentValues().apply {
                put(MediaStore.Video.Media.DISPLAY_NAME, name)
                put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
                put(MediaStore.Video.Media.RELATIVE_PATH, Environment.DIRECTORY_MOVIES + "/IranMusicStudio")
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
    private fun actionButton(value: String, action: () -> Unit) = Button(this).apply {
        text = value
        textSize = 13f
        setTextColor(android.graphics.Color.rgb(13, 18, 29))
        setBackgroundColor(android.graphics.Color.rgb(220, 181, 105))
        setOnClickListener { action() }
    }
    private fun styleButton(value: String, style: Int) = Button(this).apply {
        text = value
        textSize = 11f
        setTextColor(android.graphics.Color.WHITE)
        setBackgroundColor(if (visualStyle == style) android.graphics.Color.rgb(65, 105, 91)
            else android.graphics.Color.rgb(34, 43, 60))
        setOnClickListener {
            visualStyle = style
            spectrum.setStyle(style)
            status.text = "طرح اکولایزر «$value» انتخاب شد."
            (parent as? LinearLayout)?.let { row ->
                for (i in 0 until row.childCount) {
                    val child = row.getChildAt(i) as? Button
                    child?.setBackgroundColor(if (i == style) android.graphics.Color.rgb(65, 105, 91)
                        else android.graphics.Color.rgb(34, 43, 60))
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
    private var bars = FloatArray(36)
    private var style = 0
    fun setStyle(value: Int) { style = value; invalidate() }
    fun updateFft(fft: ByteArray) {
        val values = FloatArray(bars.size)
        for (i in values.indices) {
            val index = 2 + i * 2
            if (index + 1 < fft.size) {
                val re = fft[index].toInt()
                val im = fft[index + 1].toInt()
                values[i] = (kotlin.math.sqrt((re * re + im * im).toDouble()).toFloat() / 24f).coerceIn(0.04f, 1f)
            }
        }
        post { bars = values; invalidate() }
    }
    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        canvas.drawColor(android.graphics.Color.rgb(17, 24, 39))
        val gap = 5f
        val w = (width - gap * (bars.size + 1)) / bars.size
        val base = height - 14f
        bars.forEachIndexed { i, value ->
            val v = value.coerceIn(0.04f, 1f)
            val h = (height - 34f) * v
            paint.color = when (i % 3) {
                0 -> android.graphics.Color.rgb(34, 155, 105)
                1 -> android.graphics.Color.WHITE
                else -> android.graphics.Color.rgb(190, 58, 70)
            }
            val x = gap + i * (w + gap)
            when (style) {
                0 -> canvas.drawRoundRect(x, base - h, x + w, base, 5f, 5f, paint)
                1 -> {
                    val center = height / 2f
                    canvas.drawRoundRect(x, center - h / 2, x + w, center + h / 2, 5f, 5f, paint)
                }
                else -> {
                    val pulse = h * (if (i % 2 == 0) 1f else 0.65f)
                    canvas.drawCircle(x + w / 2, base - pulse / 2, (w / 2).coerceAtLeast(2f), paint)
                }
            }
        }
    }
}
