package com.beatforge.studio.audio

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.tanh

/**
 * The heart of the app: a sample-accurate step sequencer + voice mixer.
 * Used both by the live engine (AudioTrack) and by the offline exporter.
 * Not thread-safe: always call from one thread.
 */
class PatternMixer {
    private class Voice(
        val data: FloatArray,
        var pos: Double,
        val limit: Int,
        val rate: Double,
        val gainL: Float,
        val gainR: Float,
        var delay: Int,
        val track: Int
    )

    private val voices = ArrayList<Voice>(96)
    private var step = 0
    private var nextStepTime = 0.0

    /** Index MAX_TRACKS is used for recorded / imported clips. */
    val trackLevels = FloatArray(MAX_TRACKS + 1)
    var lastTriggeredStep = -1
        private set
    var peakL = 0f
        private set
    var peakR = 0f
        private set
    val voiceCount: Int get() = voices.size

    /** Offline renderer sets this so no step beyond the song end is scheduled. */
    var stepLimit = Int.MAX_VALUE

    fun reset() {
        voices.clear()
        step = 0
        nextStepTime = 0.0
        lastTriggeredStep = -1
        trackLevels.fill(0f)
    }

    fun clearVoices() = voices.clear()

    fun triggerPreview(track: Track, index: Int) {
        val (l, r) = gainsOf(track)
        val data = DrumSynth.sampleFor(track)
        addVoice(data, 0.0, data.size, rateOf(track), l, r, 0, index)
    }

    private fun gainsOf(t: Track): Pair<Float, Float> {
        val l = t.volume * (if (t.pan > 0f) 1f - t.pan else 1f)
        val r = t.volume * (if (t.pan < 0f) 1f + t.pan else 1f)
        return l to r
    }

    private fun rateOf(t: Track): Double = 2.0.pow(t.pitch / 12.0)

    private fun addVoice(
        data: FloatArray, pos: Double, limit: Int, rate: Double,
        gl: Float, gr: Float, delay: Int, track: Int
    ) {
        if (data.size < 2) return
        if (voices.size >= 96) voices.removeAt(0)
        voices.add(Voice(data, pos, limit, rate, gl, gr, delay, track))
    }

    private fun triggerStep(p: Project, s: Int, delay: Int) {
        val inBar = s % STEPS
        val barIdx = s / STEPS
        val songBar: Int
        val patternIndex: Int
        if (p.songMode && p.arrangement.isNotEmpty()) {
            songBar = barIdx % p.arrangement.size
            patternIndex = p.arrangement[songBar].coerceIn(0, p.patterns.lastIndex)
        } else {
            songBar = -1
            patternIndex = p.activePattern.coerceIn(0, p.patterns.lastIndex)
        }
        val pat = p.patterns.getOrNull(patternIndex) ?: return
        val anySolo = p.tracks.any { it.solo }
        for (ti in p.tracks.indices) {
            val tr = p.tracks[ti]
            if (pat.getOrNull(ti)?.getOrNull(inBar) != true) continue
            if (tr.mute || (anySolo && !tr.solo)) continue
            val data = DrumSynth.sampleFor(tr)
            val (gl, gr) = gainsOf(tr)
            addVoice(data, 0.0, data.size, rateOf(tr), gl, gr, delay, ti)
        }
        if (p.songMode && inBar == 0) {
            for (clip in p.clips) {
                if (clip.muted || clip.startBar != songBar) continue
                addVoice(
                    clip.samples, clip.startIndex.toDouble(), clip.endIndex, 1.0,
                    clip.gain, clip.gain, delay, MAX_TRACKS
                )
            }
        }
    }

    fun render(p: Project, out: FloatArray, frames: Int, playing: Boolean) {
        java.util.Arrays.fill(out, 0, frames * 2, 0f)
        if (playing) {
            val stepLen = 60.0 / max(40, p.bpm) / 4.0 * SAMPLE_RATE
            while (step < stepLimit) {
                val swingOff = if (step % 2 == 1) p.swing.toDouble() * 0.5 * stepLen else 0.0
                val t = nextStepTime + swingOff
                if (t >= frames) break
                triggerStep(p, step, if (t < 0.0) 0 else t.toInt())
                nextStepTime += stepLen
                step++
            }
            nextStepTime -= frames
            lastTriggeredStep = step - 1
        }

        for (i in trackLevels.indices) trackLevels[i] *= 0.92f

        var i = voices.size - 1
        while (i >= 0) {
            val v = voices[i]
            var f = v.delay
            var pos = v.pos
            val data = v.data
            val lastIdx = min(v.limit, data.size) - 1
            var peak = 0f
            var finished = false
            if (f < frames) {
                while (f < frames) {
                    val ip = pos.toInt()
                    if (ip >= lastIdx) {
                        finished = true
                        break
                    }
                    val frac = (pos - ip).toFloat()
                    val s = data[ip] + (data[ip + 1] - data[ip]) * frac
                    out[2 * f] += s * v.gainL
                    out[2 * f + 1] += s * v.gainR
                    val a = abs(s)
                    if (a > peak) peak = a
                    pos += v.rate
                    f++
                }
                v.delay = 0
            } else {
                v.delay -= frames
            }
            v.pos = pos
            val idx = if (v.track in 0 until MAX_TRACKS) v.track else MAX_TRACKS
            val level = peak * max(v.gainL, v.gainR)
            if (level > trackLevels[idx]) trackLevels[idx] = level
            if (finished) voices.removeAt(i)
            i--
        }

        val master = p.masterVolume
        var pl = 0f
        var pr = 0f
        for (k in 0 until frames) {
            val l = tanh(out[2 * k] * master)
            val r = tanh(out[2 * k + 1] * master)
            out[2 * k] = l
            out[2 * k + 1] = r
            if (abs(l) > pl) pl = abs(l)
            if (abs(r) > pr) pr = abs(r)
        }
        peakL = max(pl, peakL * 0.9f)
        peakR = max(pr, peakR * 0.9f)
    }
}
