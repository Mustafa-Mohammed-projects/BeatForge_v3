package com.beatforge.studio.audio

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.os.Process
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.TimeUnit
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock
import kotlin.math.max
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class PlaybackState(
    val playing: Boolean = false,
    /** Global step counter that is currently audible (-1 when stopped). */
    val step: Int = -1,
    val peakL: Float = 0f,
    val peakR: Float = 0f,
    val trackLevels: FloatArray = FloatArray(MAX_TRACKS + 1)
)

/**
 * Low-latency live playback. A dedicated thread renders small blocks with [PatternMixer]
 * and streams them to an AudioTrack. The thread sleeps by itself when nothing is playing.
 */
class SequencerEngine {
    private companion object {
        const val BLOCK = 512
        const val IDLE_BLOCKS = 200
    }

    private val mixer = PatternMixer()

    @Volatile private var project: Project? = null
    @Volatile private var playing = false
    @Volatile private var resetRequested = false
    @Volatile private var stopRequested = false
    @Volatile private var running = false

    private val previews = ConcurrentLinkedQueue<Int>()
    private val lock = ReentrantLock()
    private val cond = lock.newCondition()
    private var thread: Thread? = null

    private val _state = MutableStateFlow(PlaybackState())
    val state: StateFlow<PlaybackState> = _state.asStateFlow()

    fun start() {
        if (running) return
        running = true
        thread = Thread({ loop() }, "BeatForge-Audio").also { it.start() }
    }

    fun shutdown() {
        running = false
        wake()
        try {
            thread?.join(1500)
        } catch (_: InterruptedException) {
        }
        thread = null
    }

    fun setProject(p: Project) {
        project = p
    }

    fun play() {
        resetRequested = true
        playing = true
        wake()
    }

    fun stop() {
        playing = false
        stopRequested = true
        wake()
    }

    fun preview(trackIndex: Int) {
        previews.add(trackIndex)
        wake()
    }

    private fun wake() = lock.withLock { cond.signalAll() }

    private fun hasWork() = playing || stopRequested || previews.isNotEmpty()

    private fun loop() {
        Process.setThreadPriority(Process.THREAD_PRIORITY_URGENT_AUDIO)
        val track: AudioTrack
        try {
            val minBuf = AudioTrack.getMinBufferSize(
                SAMPLE_RATE, AudioFormat.CHANNEL_OUT_STEREO, AudioFormat.ENCODING_PCM_FLOAT
            )
            track = AudioTrack.Builder()
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                        .build()
                )
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setEncoding(AudioFormat.ENCODING_PCM_FLOAT)
                        .setSampleRate(SAMPLE_RATE)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_STEREO)
                        .build()
                )
                .setBufferSizeInBytes(max(minBuf, BLOCK * 2 * 4 * 3))
                .setTransferMode(AudioTrack.MODE_STREAM)
                .setPerformanceMode(AudioTrack.PERFORMANCE_MODE_LOW_LATENCY)
                .build()
        } catch (e: Exception) {
            running = false
            return
        }

        val buffer = FloatArray(BLOCK * 2)
        val history = ArrayDeque<LongArray>()
        var written = 0L
        var headBase = 0L
        var idle = 0
        var publishCounter = 0

        try {
            track.play()
            while (running) {
                val p = project
                if (p == null) {
                    Thread.sleep(20)
                    continue
                }

                if (idle > IDLE_BLOCKS) {
                    track.pause()
                    track.flush()
                    lock.withLock {
                        while (running && !hasWork()) cond.await(500, TimeUnit.MILLISECONDS)
                    }
                    if (!running) break
                    track.play()
                    headBase = track.playbackHeadPosition.toLong()
                    written = 0
                    history.clear()
                    idle = 0
                }

                if (stopRequested) {
                    stopRequested = false
                    mixer.clearVoices()
                }
                if (resetRequested) {
                    resetRequested = false
                    mixer.reset()
                    history.clear()
                    written = 0
                    headBase = track.playbackHeadPosition.toLong()
                }
                while (true) {
                    val ti = previews.poll() ?: break
                    p.tracks.getOrNull(ti)?.let { mixer.triggerPreview(it, ti) }
                }

                val isPlaying = playing
                mixer.render(p, buffer, BLOCK, isPlaying)
                history.addLast(longArrayOf(written, mixer.lastTriggeredStep.toLong()))
                track.write(buffer, 0, buffer.size, AudioTrack.WRITE_BLOCKING)
                written += BLOCK

                val head = track.playbackHeadPosition.toLong() - headBase
                while (history.size > 1 && history[1][0] <= head) history.removeFirst()
                val audibleStep = if (isPlaying && history.isNotEmpty()) history.first()[1].toInt() else -1

                idle = if (isPlaying || mixer.voiceCount > 0 || previews.isNotEmpty()) 0 else idle + 1

                if (++publishCounter % 2 == 0) {
                    _state.value = PlaybackState(
                        playing = isPlaying,
                        step = audibleStep,
                        peakL = mixer.peakL,
                        peakR = mixer.peakR,
                        trackLevels = mixer.trackLevels.copyOf()
                    )
                }
            }
        } catch (e: Exception) {
            // Audio device vanished (e.g. headphones unplugged mid-write); the engine can be restarted.
        } finally {
            try {
                track.stop()
            } catch (_: Exception) {
            }
            track.release()
            running = false
            _state.value = PlaybackState()
        }
    }
}
