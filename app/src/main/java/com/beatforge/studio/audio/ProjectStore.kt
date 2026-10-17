package com.beatforge.studio.audio

import android.content.Context
import java.io.File
import org.json.JSONArray
import org.json.JSONObject

data class ProjectInfo(val id: String, val name: String, val bpm: Int, val modified: Long)

/** Saves projects as JSON (pattern data) plus raw float32 files for recorded/imported audio. */
class ProjectStore(context: Context) {
    private val root = File(context.filesDir, "projects").apply { mkdirs() }

    private fun dir(id: String) = File(root, id).apply { mkdirs() }

    fun list(): List<ProjectInfo> =
        (root.listFiles() ?: emptyArray()).mapNotNull { d ->
            val f = File(d, "project.json")
            if (!f.exists()) return@mapNotNull null
            try {
                val o = JSONObject(f.readText())
                ProjectInfo(d.name, o.optString("name", "Untitled"), o.optInt("bpm", 90), f.lastModified())
            } catch (_: Exception) {
                null
            }
        }.sortedByDescending { it.modified }

    fun delete(id: String) {
        File(root, id).deleteRecursively()
    }

    fun save(p: Project) {
        val d = dir(p.id)
        val o = JSONObject()
        o.put("name", p.name)
        o.put("bpm", p.bpm)
        o.put("swing", p.swing.toDouble())
        o.put("master", p.masterVolume.toDouble())
        o.put("activePattern", p.activePattern)
        o.put("songMode", p.songMode)
        o.put("nextId", p.nextId)
        o.put("arrangement", JSONArray(p.arrangement))

        val tracks = JSONArray()
        for (t in p.tracks) {
            val to = JSONObject()
            to.put("id", t.id)
            to.put("name", t.name)
            to.put("kind", t.kind?.name ?: "")
            to.put("color", t.colorIndex)
            to.put("vol", t.volume.toDouble())
            to.put("pan", t.pan.toDouble())
            to.put("pitch", t.pitch)
            to.put("mute", t.mute)
            to.put("solo", t.solo)
            if (t.customSample != null) {
                writeFloats(File(d, "track_${t.id}.pcm"), t.customSample)
                to.put("sample", true)
            }
            tracks.put(to)
        }
        o.put("tracks", tracks)

        val pats = JSONArray()
        for (pat in p.patterns) {
            val rows = JSONArray()
            for (row in pat) rows.put(row.joinToString("") { if (it) "x" else "." })
            pats.put(rows)
        }
        o.put("patterns", pats)

        val clips = JSONArray()
        for (c in p.clips) {
            val co = JSONObject()
            co.put("id", c.id)
            co.put("name", c.name)
            co.put("startBar", c.startBar)
            co.put("gain", c.gain.toDouble())
            co.put("trimStart", c.trimStart.toDouble())
            co.put("trimEnd", c.trimEnd.toDouble())
            co.put("muted", c.muted)
            writeFloats(File(d, "clip_${c.id}.pcm"), c.samples)
            clips.put(co)
        }
        o.put("clips", clips)

        File(d, "project.json").writeText(o.toString())

        // remove audio files that no longer belong to the project
        val keep = HashSet<String>()
        p.tracks.filter { it.customSample != null }.forEach { keep.add("track_${it.id}.pcm") }
        p.clips.forEach { keep.add("clip_${it.id}.pcm") }
        d.listFiles { f -> f.name.endsWith(".pcm") }?.forEach { if (it.name !in keep) it.delete() }
    }

    fun load(id: String): Project? {
        val d = File(root, id)
        val f = File(d, "project.json")
        if (!f.exists()) return null
        return try {
            val o = JSONObject(f.readText())
            val tracks = o.getJSONArray("tracks").let { arr ->
                (0 until arr.length()).map { i ->
                    val to = arr.getJSONObject(i)
                    val kind = to.optString("kind").takeIf { it.isNotEmpty() }?.let {
                        runCatching { DrumKind.valueOf(it) }.getOrNull()
                    }
                    val tid = to.getInt("id")
                    Track(
                        id = tid,
                        name = to.getString("name"),
                        kind = kind,
                        customSample = if (to.optBoolean("sample")) readFloats(File(d, "track_$tid.pcm")) else null,
                        colorIndex = to.optInt("color"),
                        volume = to.optDouble("vol", 0.8).toFloat(),
                        pan = to.optDouble("pan", 0.0).toFloat(),
                        pitch = to.optInt("pitch"),
                        mute = to.optBoolean("mute"),
                        solo = to.optBoolean("solo")
                    )
                }
            }
            val patterns = o.getJSONArray("patterns").let { arr ->
                (0 until arr.length()).map { pi ->
                    val rows = arr.getJSONArray(pi)
                    (0 until rows.length()).map { ri ->
                        val s = rows.getString(ri)
                        List(STEPS) { s.getOrNull(it) == 'x' }
                    }
                }
            }
            val clips = o.optJSONArray("clips")?.let { arr ->
                (0 until arr.length()).mapNotNull { i ->
                    val co = arr.getJSONObject(i)
                    val cid = co.getInt("id")
                    val data = readFloats(File(d, "clip_$cid.pcm")) ?: return@mapNotNull null
                    VocalClip(
                        id = cid,
                        name = co.optString("name", "Clip"),
                        samples = data,
                        startBar = co.optInt("startBar"),
                        gain = co.optDouble("gain", 1.0).toFloat(),
                        trimStart = co.optDouble("trimStart", 0.0).toFloat(),
                        trimEnd = co.optDouble("trimEnd", 1.0).toFloat(),
                        muted = co.optBoolean("muted")
                    )
                }
            } ?: emptyList()
            val arrJson = o.optJSONArray("arrangement")
            val arrangement = if (arrJson != null) (0 until arrJson.length()).map { arrJson.getInt(it) } else listOf(0)
            Project(
                id = id,
                name = o.optString("name", "Untitled"),
                bpm = o.optInt("bpm", 90),
                swing = o.optDouble("swing", 0.0).toFloat(),
                masterVolume = o.optDouble("master", 0.85).toFloat(),
                tracks = tracks,
                patterns = patterns,
                activePattern = o.optInt("activePattern").coerceIn(0, patterns.lastIndex.coerceAtLeast(0)),
                arrangement = arrangement,
                clips = clips,
                songMode = o.optBoolean("songMode"),
                nextId = o.optInt("nextId", 100)
            )
        } catch (_: Exception) {
            null
        }
    }

    private fun writeFloats(file: File, data: FloatArray) {
        val bb = java.nio.ByteBuffer.allocate(data.size * 4).order(java.nio.ByteOrder.LITTLE_ENDIAN)
        bb.asFloatBuffer().put(data)
        file.writeBytes(bb.array())
    }

    private fun readFloats(file: File): FloatArray? {
        if (!file.exists()) return null
        val bytes = file.readBytes()
        val fb = java.nio.ByteBuffer.wrap(bytes).order(java.nio.ByteOrder.LITTLE_ENDIAN).asFloatBuffer()
        return FloatArray(fb.remaining()).also { fb.get(it) }
    }
}
