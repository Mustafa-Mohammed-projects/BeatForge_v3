package com.beatforge.studio

import android.app.Application
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.beatforge.studio.audio.AudioDecoder
import com.beatforge.studio.audio.AudioUtil
import com.beatforge.studio.audio.DrumKind
import com.beatforge.studio.audio.Exporter
import com.beatforge.studio.audio.PlaybackState
import com.beatforge.studio.audio.Project
import com.beatforge.studio.audio.ProjectInfo
import com.beatforge.studio.audio.ProjectStore
import com.beatforge.studio.audio.SequencerEngine
import com.beatforge.studio.audio.Template
import com.beatforge.studio.audio.Templates
import com.beatforge.studio.audio.VocalRecorder
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class StudioViewModel(app: Application) : AndroidViewModel(app) {
    private val store = ProjectStore(app)
    val engine = SequencerEngine()
    private val recorder = VocalRecorder()

    private val _project = MutableStateFlow<Project?>(null)
    val project: StateFlow<Project?> = _project.asStateFlow()

    private val _projects = MutableStateFlow<List<ProjectInfo>>(emptyList())
    val projects: StateFlow<List<ProjectInfo>> = _projects.asStateFlow()

    private val _recording = MutableStateFlow(false)
    val recording: StateFlow<Boolean> = _recording.asStateFlow()

    private val _recLevel = MutableStateFlow(0f)
    val recLevel: StateFlow<Float> = _recLevel.asStateFlow()

    private val _busy = MutableStateFlow<String?>(null)
    val busy: StateFlow<String?> = _busy.asStateFlow()

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    val playback: StateFlow<PlaybackState> get() = engine.state

    private val undoStack = ArrayDeque<Project>()
    private val _canUndo = MutableStateFlow(false)
    val canUndo: StateFlow<Boolean> = _canUndo.asStateFlow()

    private var saveJob: Job? = null
    private var levelJob: Job? = null

    init {
        engine.start()
        refreshProjects()
    }

    fun refreshProjects() {
        viewModelScope.launch(Dispatchers.IO) { _projects.value = store.list() }
    }

    fun toast(text: String) {
        _message.value = text
    }

    fun consumeMessage() {
        _message.value = null
    }

    // ---------- project lifecycle ----------

    fun newProject(template: Template) {
        stop()
        undoStack.clear()
        _canUndo.value = false
        val p = Templates.create(template)
        setProject(p, pushUndo = false)
        saveNow()
    }

    fun openProject(id: String) {
        stop()
        viewModelScope.launch {
            _busy.value = "Loading…"
            val p = withContext(Dispatchers.IO) { store.load(id) }
            _busy.value = null
            if (p == null) {
                toast("Couldn't open project")
            } else {
                undoStack.clear()
                _canUndo.value = false
                setProject(p.copy(songMode = false), pushUndo = false)
            }
        }
    }

    fun deleteProject(id: String) {
        viewModelScope.launch(Dispatchers.IO) {
            store.delete(id)
            _projects.value = store.list()
        }
    }

    fun closeProject() {
        stop()
        saveNow()
        _project.value = null
    }

    private fun setProject(p: Project, pushUndo: Boolean = true) {
        if (pushUndo) {
            _project.value?.let {
                undoStack.addLast(it)
                if (undoStack.size > 40) undoStack.removeFirst()
                _canUndo.value = true
            }
        }
        _project.value = p
        engine.setProject(p)
        scheduleSave()
    }

    /** Edit without recording an undo step (used for continuous sliders). */
    fun tweak(transform: (Project) -> Project) {
        val p = _project.value ?: return
        val n = transform(p)
        _project.value = n
        engine.setProject(n)
        scheduleSave()
    }

    fun edit(transform: (Project) -> Project) {
        val p = _project.value ?: return
        val n = transform(p)
        if (n === p) return
        setProject(n)
    }

    fun undo() {
        val prev = undoStack.removeLastOrNull() ?: return
        _canUndo.value = undoStack.isNotEmpty()
        _project.value = prev
        engine.setProject(prev)
        scheduleSave()
    }

    private fun scheduleSave() {
        saveJob?.cancel()
        saveJob = viewModelScope.launch {
            delay(1200)
            saveNow()
        }
    }

    fun saveNow() {
        val p = _project.value ?: return
        viewModelScope.launch(Dispatchers.IO) {
            store.save(p)
            _projects.value = store.list()
        }
    }

    // ---------- transport ----------

    fun togglePlay() {
        if (engine.state.value.playing) stop() else play()
    }

    fun play() {
        stopRecordingIfNeeded()
        engine.play()
    }

    fun stop() {
        engine.stop()
    }

    fun previewTrack(index: Int) = engine.preview(index)

    fun setSongMode(on: Boolean) {
        val wasPlaying = engine.state.value.playing
        tweak { it.copy(songMode = on) }
        if (wasPlaying) engine.play()
    }

    // ---------- recording ----------

    fun startRecording(playBackingTrack: Boolean): Boolean {
        if (recorder.isRecording) return true
        if (!recorder.start()) {
            toast("Microphone unavailable")
            return false
        }
        _recording.value = true
        levelJob = viewModelScope.launch {
            while (recorder.isRecording) {
                _recLevel.value = recorder.level
                delay(50)
            }
            _recLevel.value = 0f
        }
        if (playBackingTrack) {
            setSongMode(false)
            engine.play()
        }
        return true
    }

    fun stopRecording(name: String? = null) {
        if (!recorder.isRecording) return
        engine.stop()
        viewModelScope.launch {
            val data = withContext(Dispatchers.IO) { recorder.stop() }
            _recording.value = false
            if (data == null) {
                toast("Nothing recorded")
                return@launch
            }
            val count = (_project.value?.clips?.size ?: 0) + 1
            edit { it.addClip(name ?: "Vocal $count", data, startBar = 0) }
            toast("Take saved to the timeline")
        }
    }

    private fun stopRecordingIfNeeded() {
        if (recorder.isRecording) stopRecording()
    }

    // ---------- import ----------

    fun importAudio(uri: Uri, displayName: String, asClip: Boolean) {
        viewModelScope.launch {
            _busy.value = "Importing $displayName…"
            val data = withContext(Dispatchers.Default) {
                AudioDecoder.decode(getApplication(), uri)?.let { AudioUtil.normalize(it) }
            }
            _busy.value = null
            if (data == null) {
                toast("Couldn't read that audio file")
                return@launch
            }
            val clean = displayName.substringBeforeLast('.').take(18)
            if (asClip) {
                edit { it.addClip(clean, data) }
                toast("Added to the timeline")
            } else {
                val oneShot = if (data.size > 44100 * 4) data.copyOf(44100 * 4) else data
                edit { it.addTrack(clean, null, oneShot) }
                toast("Added as a new drum pad")
            }
        }
    }

    fun addDrum(kind: DrumKind) = edit { it.addTrack(kind.label, kind, null) }

    // ---------- export ----------

    fun export(songMode: Boolean, loops: Int) {
        val p = _project.value ?: return
        stop()
        viewModelScope.launch {
            _busy.value = "Rendering…"
            val file = withContext(Dispatchers.Default) {
                val safe = p.name.replace(Regex("[^\\w\\- ]"), "_").trim().ifEmpty { "beat" }
                Exporter.exportWav(p, File(getApplication<Application>().cacheDir, "exports/$safe.wav"), songMode, loops)
            }
            _busy.value = null
            try {
                val ctx = getApplication<Application>()
                val uri = FileProvider.getUriForFile(ctx, "${ctx.packageName}.fileprovider", file)
                val send = Intent(Intent.ACTION_SEND).apply {
                    type = "audio/wav"
                    putExtra(Intent.EXTRA_STREAM, uri)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                val chooser = Intent.createChooser(send, "Share your beat").apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                ctx.startActivity(chooser)
            } catch (e: Exception) {
                toast("Export failed")
            }
        }
    }

    override fun onCleared() {
        saveNow()
        engine.shutdown()
        super.onCleared()
    }
}
