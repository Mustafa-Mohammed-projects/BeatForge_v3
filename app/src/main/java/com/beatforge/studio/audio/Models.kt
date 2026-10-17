package com.beatforge.studio.audio

const val SAMPLE_RATE = 44100
const val STEPS = 16
const val PATTERN_COUNT = 4
const val MAX_TRACKS = 16

enum class DrumKind(val label: String) {
    KICK("Kick"),
    SNARE("Snare"),
    CLAP("Clap"),
    HAT("Hi-Hat"),
    OPEN_HAT("Open Hat"),
    TOM("Tom"),
    BASS808("808"),
    PERC("Perc"),
    RIM("Rim")
}

/** One row of the step sequencer. Either a built-in synthesized drum or an imported / recorded sample. */
data class Track(
    val id: Int,
    val name: String,
    val kind: DrumKind?,
    val customSample: FloatArray? = null,
    val colorIndex: Int = 0,
    val volume: Float = 0.8f,
    val pan: Float = 0f,
    val pitch: Int = 0,
    val mute: Boolean = false,
    val solo: Boolean = false
)

/** A recorded or imported audio clip (vocals, loops, full songs) placed on the song timeline. */
data class VocalClip(
    val id: Int,
    val name: String,
    val samples: FloatArray,
    val startBar: Int = 0,
    val gain: Float = 1f,
    val trimStart: Float = 0f,
    val trimEnd: Float = 1f,
    val muted: Boolean = false
) {
    val startIndex: Int get() = (samples.size * trimStart).toInt().coerceIn(0, samples.size)
    val endIndex: Int get() = (samples.size * trimEnd).toInt().coerceIn(startIndex, samples.size)
    val durationSec: Float get() = (endIndex - startIndex).toFloat() / SAMPLE_RATE
}

typealias Pattern = List<List<Boolean>>

data class Project(
    val id: String,
    val name: String = "Untitled Beat",
    val bpm: Int = 90,
    val swing: Float = 0f,
    val masterVolume: Float = 0.85f,
    val tracks: List<Track>,
    val patterns: List<Pattern>,
    val activePattern: Int = 0,
    val arrangement: List<Int> = listOf(0, 0, 1, 0),
    val clips: List<VocalClip> = emptyList(),
    val songMode: Boolean = false,
    val nextId: Int = 100
) {
    val barSeconds: Float get() = 4f * 60f / bpm

    fun clipBars(clip: VocalClip): Int =
        kotlin.math.ceil(clip.durationSec / barSeconds).toInt().coerceAtLeast(1)

    fun toggleStep(track: Int, step: Int, patternIndex: Int = activePattern): Project {
        val pat = patterns.getOrNull(patternIndex) ?: return this
        val row = pat.getOrNull(track) ?: return this
        if (step !in row.indices) return this
        val newRow = row.toMutableList().also { it[step] = !it[step] }
        val newPat = pat.toMutableList().also { it[track] = newRow }
        return copy(patterns = patterns.toMutableList().also { it[patternIndex] = newPat })
    }

    fun setStep(track: Int, step: Int, on: Boolean, patternIndex: Int = activePattern): Project {
        val row = patterns.getOrNull(patternIndex)?.getOrNull(track) ?: return this
        if (step !in row.indices || row[step] == on) return this
        return toggleStep(track, step, patternIndex)
    }

    fun clearPattern(patternIndex: Int = activePattern): Project {
        if (patternIndex !in patterns.indices) return this
        val cleared = tracks.map { List(STEPS) { false } }
        return copy(patterns = patterns.toMutableList().also { it[patternIndex] = cleared })
    }

    fun copyPattern(from: Int, to: Int): Project {
        if (from !in patterns.indices || to !in patterns.indices) return this
        return copy(patterns = patterns.toMutableList().also { it[to] = it[from] })
    }

    fun updateTrack(index: Int, transform: (Track) -> Track): Project {
        if (index !in tracks.indices) return this
        return copy(tracks = tracks.toMutableList().also { it[index] = transform(it[index]) })
    }

    fun addTrack(name: String, kind: DrumKind?, sample: FloatArray?): Project {
        if (tracks.size >= MAX_TRACKS) return this
        val track = Track(
            id = nextId,
            name = name,
            kind = kind,
            customSample = sample,
            colorIndex = tracks.size % 9
        )
        return copy(
            tracks = tracks + track,
            patterns = patterns.map { it + listOf(List(STEPS) { false }) },
            nextId = nextId + 1
        )
    }

    fun removeTrack(index: Int): Project {
        if (index !in tracks.indices || tracks.size <= 1) return this
        return copy(
            tracks = tracks.filterIndexed { i, _ -> i != index },
            patterns = patterns.map { p -> p.filterIndexed { i, _ -> i != index } }
        )
    }

    fun setArrangementBar(bar: Int, pattern: Int): Project {
        if (bar !in arrangement.indices) return this
        return copy(arrangement = arrangement.toMutableList().also { it[bar] = pattern })
    }

    fun resizeArrangement(bars: Int): Project {
        val n = bars.coerceIn(1, 32)
        val list = if (n <= arrangement.size) arrangement.take(n)
        else arrangement + List(n - arrangement.size) { arrangement.lastOrNull() ?: 0 }
        return copy(arrangement = list)
    }

    fun addClip(name: String, samples: FloatArray, startBar: Int = 0): Project =
        copy(
            clips = clips + VocalClip(id = nextId, name = name, samples = samples, startBar = startBar),
            nextId = nextId + 1
        )

    fun updateClip(id: Int, transform: (VocalClip) -> VocalClip): Project =
        copy(clips = clips.map { if (it.id == id) transform(it) else it })

    fun removeClip(id: Int): Project = copy(clips = clips.filterNot { it.id == id })
}
