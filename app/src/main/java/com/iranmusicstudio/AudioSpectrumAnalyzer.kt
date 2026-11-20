package com.iranmusicstudio

import android.content.Context
import android.media.AudioFormat
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import java.nio.ByteOrder
import kotlin.math.*

/**
 * Decodes audio incrementally and retains only a short PCM ring buffer plus
 * the compact 30 FPS spectrum envelope needed by the video overlay.
 */
object AudioSpectrumAnalyzer {
    fun analyze(context: Context, uri: Uri): Array<FloatArray> {
        val extractor = MediaExtractor()
        var decoder: MediaCodec? = null
        var sampleRate = 44100
        var channels = 2
        var pcmEncoding = AudioFormat.ENCODING_PCM_16BIT
        var accumulator: StreamingSpectrumAccumulator? = null

        try {
            extractor.setDataSource(context, uri, null)
            var track = -1
            for (i in 0 until extractor.trackCount) {
                val format = extractor.getTrackFormat(i)
                val mime = format.getString(MediaFormat.KEY_MIME) ?: continue
                if (mime.startsWith("audio/")) {
                    track = i
                    sampleRate = format.getInteger(MediaFormat.KEY_SAMPLE_RATE)
                    channels = format.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
                    break
                }
            }
            require(track >= 0) { "فایل انتخاب‌شده ترک صوتی ندارد." }
            extractor.selectTrack(track)

            val inputFormat = extractor.getTrackFormat(track)
            val mime = inputFormat.getString(MediaFormat.KEY_MIME)
                ?: error("قالب صوتی فایل شناسایی نشد.")
            decoder = MediaCodec.createDecoderByType(mime)
            decoder.configure(inputFormat, null, null, 0)
            decoder.start()

            val info = MediaCodec.BufferInfo()
            var inputDone = false
            var outputDone = false
            while (!outputDone) {
                if (!inputDone) {
                    val inputIndex = decoder.dequeueInputBuffer(10_000)
                    if (inputIndex >= 0) {
                        val input = decoder.getInputBuffer(inputIndex)
                            ?: error("بافر ورودی رمزگشا در دسترس نیست.")
                        input.clear()
                        val size = extractor.readSampleData(input, 0)
                        if (size < 0) {
                            decoder.queueInputBuffer(
                                inputIndex, 0, 0, 0L, MediaCodec.BUFFER_FLAG_END_OF_STREAM
                            )
                            inputDone = true
                        } else {
                            decoder.queueInputBuffer(inputIndex, 0, size, extractor.sampleTime, 0)
                            extractor.advance()
                        }
                    }
                }

                val outputIndex = decoder.dequeueOutputBuffer(info, 10_000)
                when {
                    outputIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                        val out = decoder.outputFormat
                        if (out.containsKey(MediaFormat.KEY_SAMPLE_RATE)) {
                            sampleRate = out.getInteger(MediaFormat.KEY_SAMPLE_RATE)
                        }
                        if (out.containsKey(MediaFormat.KEY_CHANNEL_COUNT)) {
                            channels = out.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
                        }
                        if (out.containsKey(MediaFormat.KEY_PCM_ENCODING)) {
                            pcmEncoding = out.getInteger(MediaFormat.KEY_PCM_ENCODING)
                        }
                    }
                    outputIndex >= 0 -> {
                        try {
                            val output = decoder.getOutputBuffer(outputIndex)
                            if (output != null && info.size > 0) {
                                val processor = accumulator ?: StreamingSpectrumAccumulator(sampleRate).also {
                                    accumulator = it
                                }
                                val data = output.duplicate().order(ByteOrder.LITTLE_ENDIAN)
                                data.position(info.offset)
                                data.limit(info.offset + info.size)
                                when (pcmEncoding) {
                                    AudioFormat.ENCODING_PCM_FLOAT -> {
                                        while (data.remaining() >= 4 * channels) {
                                            var sum = 0f
                                            repeat(channels) { sum += data.float }
                                            processor.add((sum / channels).coerceIn(-1f, 1f))
                                        }
                                    }
                                    AudioFormat.ENCODING_PCM_8BIT -> {
                                        while (data.remaining() >= channels) {
                                            var sum = 0f
                                            repeat(channels) {
                                                sum += ((data.get().toInt() and 0xFF) - 128) / 128f
                                            }
                                            processor.add(sum / channels)
                                        }
                                    }
                                    else -> {
                                        while (data.remaining() >= 2 * channels) {
                                            var sum = 0f
                                            repeat(channels) { sum += data.short / 32768f }
                                            processor.add(sum / channels)
                                        }
                                    }
                                }
                            }
                        } finally {
                            outputDone = (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0
                            decoder.releaseOutputBuffer(outputIndex, false)
                        }
                    }
                }
            }
        } finally {
            try { decoder?.stop() } catch (_: Exception) {}
            try { decoder?.release() } catch (_: Exception) {}
            extractor.release()
        }

        val frames = accumulator?.finish() ?: emptyArray()
        require(frames.isNotEmpty()) { "امکان تحلیل صدای این فایل وجود ندارد." }
        return frames
    }

    /**
     * Keeps a 4096-sample rolling window. FFT frames are emitted as soon as
     * enough PCM arrives, so decoded audio is never accumulated in RAM.
     */
    private class StreamingSpectrumAccumulator(sampleRate: Int) {
        private val fftSize = 2048
        private val ring = FloatArray(4096)
        private val hop = (sampleRate / 30).coerceAtLeast(1)
        private val frames = ArrayList<FloatArray>()
        private var totalSamples = 0L
        private var nextCenter = 0L
        private var finished = false

        fun add(sample: Float) {
            ring[(totalSamples % ring.size).toInt()] = sample
            totalSamples++
            while (nextCenter + fftSize / 2 < totalSamples) {
                frames.add(makeFrame(nextCenter))
                nextCenter += hop
            }
        }

        fun finish(): Array<FloatArray> {
            if (!finished) {
                while (nextCenter < totalSamples) {
                    frames.add(makeFrame(nextCenter))
                    nextCenter += hop
                }
                finished = true
            }
            return frames.toTypedArray()
        }

        private fun makeFrame(center: Long): FloatArray {
            val real = DoubleArray(fftSize)
            val imag = DoubleArray(fftSize)
            val first = center - fftSize / 2
            for (i in 0 until fftSize) {
                val absoluteIndex = first + i
                val sample = if (absoluteIndex < 0L || absoluteIndex >= totalSamples) {
                    0f
                } else if (totalSamples - absoluteIndex > ring.size) {
                    0f
                } else {
                    ring[(absoluteIndex % ring.size).toInt()]
                }
                val window = 0.5 - 0.5 * cos(2.0 * PI * i / (fftSize - 1))
                real[i] = sample * window
            }
            fft(real, imag)

            val bands = FloatArray(48)
            val maxBin = fftSize / 2 - 1
            for (b in bands.indices) {
                val low = (2.0 * (maxBin / 2.0).pow(b.toDouble() / bands.size))
                    .toInt().coerceIn(1, maxBin)
                val high = (2.0 * (maxBin / 2.0).pow((b + 1.0) / bands.size))
                    .toInt().coerceIn(low + 1, maxBin + 1)
                var peak = 0.0
                for (bin in low until high) peak = max(peak, hypot(real[bin], imag[bin]))
                bands[b] = (ln(1.0 + peak / (fftSize * 0.12)) / ln(12.0))
                    .toFloat().coerceIn(0.025f, 1f)
            }
            return bands
        }
    }

    private fun fft(real: DoubleArray, imag: DoubleArray) {
        val n = real.size
        var j = 0
        for (i in 1 until n) {
            var bit = n shr 1
            while ((j and bit) != 0) { j = j xor bit; bit = bit shr 1 }
            j = j xor bit
            if (i < j) {
                val r = real[i]; real[i] = real[j]; real[j] = r
                val im = imag[i]; imag[i] = imag[j]; imag[j] = im
            }
        }
        var len = 2
        while (len <= n) {
            val angle = -2.0 * PI / len
            val wLenR = cos(angle); val wLenI = sin(angle)
            var i = 0
            while (i < n) {
                var wr = 1.0; var wi = 0.0
                for (k in 0 until len / 2) {
                    val uR = real[i + k]; val uI = imag[i + k]
                    val vR = real[i + k + len / 2] * wr - imag[i + k + len / 2] * wi
                    val vI = real[i + k + len / 2] * wi + imag[i + k + len / 2] * wr
                    real[i + k] = uR + vR; imag[i + k] = uI + vI
                    real[i + k + len / 2] = uR - vR; imag[i + k + len / 2] = uI - vI
                    val next = wr * wLenR - wi * wLenI
                    wi = wr * wLenI + wi * wLenR; wr = next
                }
                i += len
            }
            len = len shl 1
        }
    }
}
