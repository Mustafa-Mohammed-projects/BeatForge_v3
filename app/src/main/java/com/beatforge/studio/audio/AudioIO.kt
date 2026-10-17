package com.beatforge.studio.audio

import android.content.Context
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaRecorder
import android.net.Uri
import java.io.File
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.abs
import kotlin.math.max

/** Microphone recording into a mono float buffer (44.1 kHz). Requires RECORD_AUDIO permission. */
class VocalRecorder {
    @Volatile private var recording = false
    @Volatile var level = 0f
        private set
    private var thread: Thread? = null
    private val chunks = ArrayList<FloatArray>()

    val isRecording: Boolean get() = recording

    @Suppress("MissingPermission")
    fun start(): Boolean {
        if (recording) return true
        val minBuf = AudioRecord.getMinBufferSize(
            SAMPLE_RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT
        )
        if (minBuf <= 0) return false
        val rec = try {
            AudioRecord(
                MediaRecorder.AudioSource.MIC, SAMPLE_RATE,
                AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, minBuf * 2
            )
        } catch (e: Exception) {
            return false
        }
        if (rec.state != AudioRecord.STATE_INITIALIZED) {
            rec.release()
            return false
        }
        chunks.clear()
        recording = true
        thread = Thread({
            val shorts = ShortArray(2048)
            try {
                rec.startRecording()
                while (recording) {
                    val n = rec.read(shorts, 0, shorts.size)
                    if (n <= 0) continue
                    val f = FloatArray(n)
                    var peak = 0f
                    for (i in 0 until n) {
                        f[i] = shorts[i] / 32768f
                        peak = max(peak, abs(f[i]))
                    }
                    level = peak
                    synchronized(chunks) { chunks.add(f) }
                }
            } catch (_: Exception) {
            } finally {
                try {
                    rec.stop()
                } catch (_: Exception) {
                }
                rec.release()
                level = 0f
            }
        }, "BeatForge-Rec").also { it.start() }
        return true
    }

    /** Stops and returns the recorded audio, or null if it was empty. */
    fun stop(): FloatArray? {
        recording = false
        try {
            thread?.join(1000)
        } catch (_: InterruptedException) {
        }
        thread = null
        val all = synchronized(chunks) {
            val total = chunks.sumOf { it.size }
            val out = FloatArray(total)
            var o = 0
            for (c in chunks) {
                System.arraycopy(c, 0, out, o, c.size)
                o += c.size
            }
            chunks.clear()
            out
        }
        return if (all.size < SAMPLE_RATE / 10) null else AudioUtil.trimSilence(all)
    }
}

object AudioUtil {
    fun trimSilence(data: FloatArray, threshold: Float = 0.01f): FloatArray {
        var s = 0
        var e = data.size
        while (s < e && abs(data[s]) < threshold) s++
        while (e > s && abs(data[e - 1]) < threshold) e--
        val pad = SAMPLE_RATE / 50
        s = max(0, s - pad)
        e = minOf(data.size, e + pad)
        return if (e - s < 64) data else data.copyOfRange(s, e)
    }

    fun peaks(data: FloatArray, buckets: Int): FloatArray {
        val out = FloatArray(buckets)
        if (data.isEmpty()) return out
        val per = max(1, data.size / buckets)
        for (b in 0 until buckets) {
            var m = 0f
            val from = b * per
            val to = minOf(data.size, from + per)
            for (i in from until to) m = max(m, abs(data[i]))
            out[b] = m
        }
        return out
    }

    fun normalize(data: FloatArray, target: Float = 0.95f): FloatArray {
        var peak = 0f
        for (v in data) peak = max(peak, abs(v))
        if (peak < 1e-4f) return data
        val g = target / peak
        return FloatArray(data.size) { data[it] * g }
    }

    fun resampleLinear(data: FloatArray, srcRate: Int, dstRate: Int): FloatArray {
        if (srcRate == dstRate || data.isEmpty()) return data
        val ratio = srcRate.toDouble() / dstRate
        val n = (data.size / ratio).toInt()
        val out = FloatArray(n)
        for (i in 0 until n) {
            val pos = i * ratio
            val ip = pos.toInt()
            val frac = (pos - ip).toFloat()
            val a = data[minOf(ip, data.size - 1)]
            val b = data[minOf(ip + 1, data.size - 1)]
            out[i] = a + (b - a) * frac
        }
        return out
    }
}

/** Decodes any audio file Android supports (mp3, wav, m4a, ogg, flac) to mono 44.1 kHz floats. */
object AudioDecoder {
    private const val MAX_SECONDS = 600

    fun decode(context: Context, uri: Uri): FloatArray? {
        val extractor = MediaExtractor()
        var codec: MediaCodec? = null
        try {
            context.contentResolver.openFileDescriptor(uri, "r")?.use { extractor.setDataSource(it.fileDescriptor) }
                ?: return null
            var trackIdx = -1
            var format: MediaFormat? = null
            for (i in 0 until extractor.trackCount) {
                val f = extractor.getTrackFormat(i)
                if (f.getString(MediaFormat.KEY_MIME)?.startsWith("audio/") == true) {
                    trackIdx = i
                    format = f
                    break
                }
            }
            if (trackIdx < 0 || format == null) return null
            extractor.selectTrack(trackIdx)

            var channels = format.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
            var rate = format.getInteger(MediaFormat.KEY_SAMPLE_RATE)
            codec = MediaCodec.createDecoderByType(format.getString(MediaFormat.KEY_MIME)!!)
            codec.configure(format, null, null, 0)
            codec.start()

            var out = FloatArray(rate * 10)
            var count = 0
            val info = MediaCodec.BufferInfo()
            var inputDone = false
            var outputDone = false
            while (!outputDone) {
                if (!inputDone) {
                    val ii = codec.dequeueInputBuffer(10_000)
                    if (ii >= 0) {
                        val buf = codec.getInputBuffer(ii)!!
                        val size = extractor.readSampleData(buf, 0)
                        if (size < 0) {
                            codec.queueInputBuffer(ii, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                            inputDone = true
                        } else {
                            codec.queueInputBuffer(ii, 0, size, extractor.sampleTime, 0)
                            extractor.advance()
                        }
                    }
                }
                val oi = codec.dequeueOutputBuffer(info, 10_000)
                when {
                    oi >= 0 -> {
                        val buf = codec.getOutputBuffer(oi)!!
                        buf.order(ByteOrder.LITTLE_ENDIAN)
                        buf.position(info.offset)
                        buf.limit(info.offset + info.size)
                        val shorts = buf.asShortBuffer()
                        val frames = shorts.remaining() / max(1, channels)
                        if (count + frames > out.size) {
                            out = out.copyOf(max(out.size * 2, count + frames))
                        }
                        for (f in 0 until frames) {
                            var sum = 0f
                            for (c in 0 until channels) sum += shorts.get() / 32768f
                            out[count++] = sum / channels
                        }
                        codec.releaseOutputBuffer(oi, false)
                        if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) outputDone = true
                        if (count > rate * MAX_SECONDS) outputDone = true
                    }
                    oi == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                        val nf = codec.outputFormat
                        channels = nf.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
                        rate = nf.getInteger(MediaFormat.KEY_SAMPLE_RATE)
                    }
                }
            }
            if (count == 0) return null
            return AudioUtil.resampleLinear(out.copyOf(count), rate, SAMPLE_RATE)
        } catch (e: Exception) {
            return null
        } finally {
            try {
                codec?.stop()
            } catch (_: Exception) {
            }
            codec?.release()
            extractor.release()
        }
    }
}

object WavWriter {
    /** Writes interleaved stereo float data (-1..1) as a 16-bit PCM WAV file. */
    fun write(file: File, stereo: FloatArray, frames: Int) {
        file.parentFile?.mkdirs()
        RandomAccessFile(file, "rw").use { raf ->
            raf.setLength(0)
            val dataBytes = frames * 2 * 2
            val header = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN)
            header.put("RIFF".toByteArray())
            header.putInt(36 + dataBytes)
            header.put("WAVE".toByteArray())
            header.put("fmt ".toByteArray())
            header.putInt(16)
            header.putShort(1)
            header.putShort(2)
            header.putInt(SAMPLE_RATE)
            header.putInt(SAMPLE_RATE * 4)
            header.putShort(4)
            header.putShort(16)
            header.put("data".toByteArray())
            header.putInt(dataBytes)
            raf.write(header.array())

            val chunkFrames = 8192
            val bb = ByteBuffer.allocate(chunkFrames * 4).order(ByteOrder.LITTLE_ENDIAN)
            var f = 0
            while (f < frames) {
                val n = minOf(chunkFrames, frames - f)
                bb.clear()
                for (i in 0 until n * 2) {
                    val v = (stereo[f * 2 + i].coerceIn(-1f, 1f) * 32767f).toInt()
                    bb.putShort(v.toShort())
                }
                raf.write(bb.array(), 0, n * 4)
                f += n
            }
        }
    }
}

/** Renders the whole project offline (faster than realtime) and writes it to a WAV. */
object Exporter {
    fun exportWav(project: Project, file: File, songMode: Boolean, loops: Int = 1): File {
        val p = project.copy(songMode = songMode)
        val bars = (if (songMode) p.arrangement.size else 1) * if (songMode) 1 else loops.coerceAtLeast(1)
        val totalSteps = bars * STEPS
        val stepLen = 60.0 / p.bpm / 4.0 * SAMPLE_RATE
        val tailFrames = (SAMPLE_RATE * 1.5).toInt()
        val totalFrames = (totalSteps * stepLen).toInt() + tailFrames

        val mixer = PatternMixer()
        mixer.stepLimit = totalSteps
        val out = FloatArray(totalFrames * 2)
        val block = FloatArray(2048 * 2)
        var done = 0
        while (done < totalFrames) {
            val n = minOf(2048, totalFrames - done)
            mixer.render(p, block, n, true)
            System.arraycopy(block, 0, out, done * 2, n * 2)
            done += n
        }
        WavWriter.write(file, out, totalFrames)
        return file
    }
}
