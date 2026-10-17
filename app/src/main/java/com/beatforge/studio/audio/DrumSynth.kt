package com.beatforge.studio.audio

import java.util.Random
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.sin
import kotlin.math.tanh

/** Generates all built-in drum sounds procedurally, so the app needs no audio assets. */
object DrumSynth {
    private val cache = HashMap<DrumKind, FloatArray>()
    private const val TWO_PI = 2.0 * PI

    @Synchronized
    fun sample(kind: DrumKind): FloatArray = cache.getOrPut(kind) { normalize(render(kind)) }

    fun sampleFor(track: Track): FloatArray =
        track.customSample ?: sample(track.kind ?: DrumKind.PERC)

    private fun render(kind: DrumKind): FloatArray = when (kind) {
        DrumKind.KICK -> kick()
        DrumKind.SNARE -> snare()
        DrumKind.CLAP -> clap()
        DrumKind.HAT -> hat(0.12f, 38.0)
        DrumKind.OPEN_HAT -> hat(0.55f, 8.0)
        DrumKind.TOM -> tom()
        DrumKind.BASS808 -> bass808()
        DrumKind.PERC -> perc()
        DrumKind.RIM -> rim()
    }

    private fun buf(seconds: Float) = FloatArray((seconds * SAMPLE_RATE).toInt())

    private fun kick(): FloatArray {
        val out = buf(0.45f)
        var phase = 0.0
        for (i in out.indices) {
            val t = i.toDouble() / SAMPLE_RATE
            val freq = 48.0 + 135.0 * exp(-t * 30.0)
            phase += TWO_PI * freq / SAMPLE_RATE
            val body = sin(phase) * exp(-t * 7.0)
            val click = if (t < 0.003) (1.0 - t / 0.003) * 0.5 else 0.0
            out[i] = tanh((body + click) * 1.7).toFloat()
        }
        return out
    }

    private fun snare(): FloatArray {
        val out = buf(0.3f)
        val rnd = Random(7)
        var prev = 0f
        var phase = 0.0
        for (i in out.indices) {
            val t = i.toDouble() / SAMPLE_RATE
            val n = rnd.nextFloat() * 2f - 1f
            val hp = n - prev * 0.55f
            prev = n
            phase += TWO_PI * (180.0 + 60.0 * exp(-t * 40.0)) / SAMPLE_RATE
            val tone = sin(phase) * exp(-t * 20.0) * 0.55
            val noise = hp * exp(-t * 18.0) * 0.9
            out[i] = tanh(tone + noise).toFloat()
        }
        return out
    }

    private fun clap(): FloatArray {
        val out = buf(0.35f)
        val rnd = Random(21)
        var prev = 0f
        val bursts = doubleArrayOf(0.0, 0.011, 0.023, 0.036)
        for (i in out.indices) {
            val t = i.toDouble() / SAMPLE_RATE
            val n = rnd.nextFloat() * 2f - 1f
            val hp = n - prev * 0.7f
            prev = n
            var env = 0.0
            for (b in bursts) {
                if (t >= b) env = max(env, exp(-(t - b) * 110.0) * (if (b == 0.036) 1.0 else 0.8))
            }
            val tail = if (t >= 0.036) exp(-(t - 0.036) * 16.0) * 0.55 else 0.0
            out[i] = (hp * max(env, tail)).toFloat()
        }
        return out
    }

    private val metalFreqs = doubleArrayOf(205.3, 304.4, 369.6, 522.7, 540.0, 800.0)

    private fun hat(length: Float, decay: Double): FloatArray {
        val out = buf(length)
        val phases = DoubleArray(metalFreqs.size)
        var prev = 0f
        for (i in out.indices) {
            val t = i.toDouble() / SAMPLE_RATE
            var s = 0f
            for (k in metalFreqs.indices) {
                phases[k] += TWO_PI * metalFreqs[k] * 2.0 / SAMPLE_RATE
                s += if (sin(phases[k]) > 0) 1f else -1f
            }
            s /= metalFreqs.size
            val hp = s - prev * 0.9f
            prev = s
            out[i] = (hp * exp(-t * decay)).toFloat()
        }
        return out
    }

    private fun tom(): FloatArray {
        val out = buf(0.45f)
        var phase = 0.0
        for (i in out.indices) {
            val t = i.toDouble() / SAMPLE_RATE
            phase += TWO_PI * (100.0 + 90.0 * exp(-t * 22.0)) / SAMPLE_RATE
            out[i] = (sin(phase) * exp(-t * 8.0)).toFloat()
        }
        return out
    }

    private fun bass808(): FloatArray {
        val out = buf(1.1f)
        var phase = 0.0
        for (i in out.indices) {
            val t = i.toDouble() / SAMPLE_RATE
            val freq = 41.2 + 55.0 * exp(-t * 38.0)
            phase += TWO_PI * freq / SAMPLE_RATE
            val amp = exp(-t * 2.4)
            out[i] = tanh(sin(phase) * amp * 2.0).toFloat()
        }
        return out
    }

    private fun perc(): FloatArray {
        val out = buf(0.22f)
        for (i in out.indices) {
            val t = i.toDouble() / SAMPLE_RATE
            val mod = sin(TWO_PI * 420.0 * t) * 2.5
            out[i] = (sin(TWO_PI * 880.0 * t + mod) * exp(-t * 28.0)).toFloat()
        }
        return out
    }

    private fun rim(): FloatArray {
        val out = buf(0.1f)
        val rnd = Random(3)
        for (i in out.indices) {
            val t = i.toDouble() / SAMPLE_RATE
            val tone = sin(TWO_PI * 1750.0 * t) * exp(-t * 70.0)
            val noise = (rnd.nextFloat() * 2f - 1f) * exp(-t * 130.0) * 0.5
            out[i] = (tone + noise).toFloat()
        }
        return out
    }

    private fun normalize(data: FloatArray): FloatArray {
        var peak = 0f
        for (v in data) peak = max(peak, abs(v))
        if (peak > 0f) {
            val g = 0.9f / peak
            for (i in data.indices) data[i] *= g
        }
        val fade = minOf(96, data.size)
        for (i in 0 until fade) data[data.size - 1 - i] *= i / fade.toFloat()
        return data
    }
}
