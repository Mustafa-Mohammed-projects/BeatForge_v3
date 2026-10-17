package com.beatforge.studio.ui

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.DeleteSweep
import androidx.compose.material.icons.rounded.FileOpen
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.beatforge.studio.StudioViewModel
import com.beatforge.studio.audio.DrumKind
import com.beatforge.studio.audio.PATTERN_COUNT
import com.beatforge.studio.audio.PlaybackState
import com.beatforge.studio.audio.Project
import com.beatforge.studio.audio.STEPS

fun displayName(context: Context, uri: Uri): String {
    try {
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
            if (c.moveToFirst()) return c.getString(0) ?: "Sample"
        }
    } catch (_: Exception) {
    }
    return uri.lastPathSegment ?: "Sample"
}

val PATTERN_NAMES = listOf("A", "B", "C", "D")

@Composable
fun BeatTab(vm: StudioViewModel, p: Project, playback: PlaybackState) {
    val context = LocalContext.current
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) vm.importAudio(uri, displayName(context, uri), asClip = false)
    }
    var addMenu by remember { mutableStateOf(false) }
    val playingStep = if (playback.playing && playback.step >= 0) playback.step else -1
    val barIndex = if (playingStep >= 0) playingStep / STEPS else -1
    val stepInBar = if (playingStep >= 0) playingStep % STEPS else -1

    // While playing in song mode the pattern shown follows the arrangement.
    val shownPattern = if (p.songMode && barIndex >= 0 && p.arrangement.isNotEmpty())
        p.arrangement[barIndex % p.arrangement.size].coerceIn(0, p.patterns.lastIndex)
    else p.activePattern

    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            repeat(PATTERN_COUNT) { i ->
                Chip(
                    PATTERN_NAMES[i], shownPattern == i, accent = BF.Pink,
                    modifier = Modifier.padding(end = 8.dp)
                ) { vm.tweak { it.copy(activePattern = i) } }
            }
            Spacer(Modifier.weight(1f))
            RoundIconButton(Icons.Rounded.ContentCopy, "Copy to next pattern", size = 38.dp) {
                val to = (p.activePattern + 1) % PATTERN_COUNT
                vm.edit { it.copyPattern(p.activePattern, to) }
                vm.tweak { it.copy(activePattern = to) }
            }
            Spacer(Modifier.width(8.dp))
            RoundIconButton(Icons.Rounded.DeleteSweep, "Clear pattern", size = 38.dp) {
                vm.edit { it.clearPattern() }
            }
        }

        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 12.dp, end = 12.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            item { StepRuler(stepInBar) }
            itemsIndexed(p.tracks, key = { _, t -> t.id }) { ti, track ->
                val accent = BF.TrackColors[track.colorIndex % BF.TrackColors.size]
                val row = p.patterns.getOrNull(shownPattern)?.getOrNull(ti) ?: List(STEPS) { false }
                val pulse = playback.trackLevels.getOrElse(ti) { 0f }
                SequencerRow(
                    name = track.name,
                    accent = accent,
                    muted = track.mute,
                    pulse = pulse,
                    row = row,
                    playingStep = stepInBar,
                    onPreview = { vm.previewTrack(ti) },
                    onStep = { s, on -> vm.edit { it.setStep(ti, s, on, shownPattern) } },
                    onToggleStep = { s ->
                        vm.edit { it.toggleStep(ti, s, shownPattern) }
                        if (!row[s]) vm.previewTrack(ti)
                    }
                )
            }
            item {
                Box {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .height(52.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .border(1.dp, BF.Outline, RoundedCornerShape(16.dp))
                            .clickable { addMenu = true },
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Icon(Icons.Rounded.Add, null, tint = BF.TextDim)
                        Spacer(Modifier.width(8.dp))
                        Text("Add sound", color = BF.TextDim, fontWeight = FontWeight.SemiBold)
                    }
                    DropdownMenu(addMenu, { addMenu = false }, containerColor = BF.Surface2) {
                        DrumKind.entries.forEach { kind ->
                            DropdownMenuItem(
                                text = { Text(kind.label, color = BF.Text) },
                                onClick = {
                                    addMenu = false
                                    vm.addDrum(kind)
                                }
                            )
                        }
                        DropdownMenuItem(
                            text = { Text("Import sample from files…", color = BF.Cyan) },
                            leadingIcon = { Icon(Icons.Rounded.FileOpen, null, tint = BF.Cyan) },
                            onClick = {
                                addMenu = false
                                picker.launch(arrayOf("audio/*"))
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun StepRuler(current: Int) {
    Row(Modifier.fillMaxWidth().padding(start = 84.dp), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
        repeat(STEPS) { i ->
            val active = i == current
            val c by animateColorAsState(
                if (active) BF.Pink else if (i % 4 == 0) BF.TextDim else BF.TextFaint,
                tween(60), label = "ruler"
            )
            Text(
                "${i + 1}",
                color = c,
                fontSize = 9.sp,
                fontWeight = if (i % 4 == 0 || active) FontWeight.Bold else FontWeight.Normal,
                modifier = Modifier.weight(1f),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
        }
    }
}

@Composable
private fun SequencerRow(
    name: String,
    accent: Color,
    muted: Boolean,
    pulse: Float,
    row: List<Boolean>,
    playingStep: Int,
    onPreview: () -> Unit,
    onStep: (Int, Boolean) -> Unit,
    onToggleStep: (Int) -> Unit
) {
    val glow by animateFloatAsState(pulse.coerceIn(0f, 1f), tween(60), label = "glow")
    Row(Modifier.fillMaxWidth().height(46.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier
                .width(78.dp)
                .fillMaxSize()
                .clip(RoundedCornerShape(12.dp))
                .background(accent.copy(alpha = 0.14f + 0.5f * glow))
                .clickable(onClick = onPreview)
                .padding(horizontal = 8.dp),
            contentAlignment = Alignment.CenterStart
        ) {
            Text(
                name, color = if (muted) BF.TextFaint else Color.White,
                fontWeight = FontWeight.Bold, fontSize = 12.sp, maxLines = 1
            )
        }
        Spacer(Modifier.width(6.dp))
        StepGrid(row, accent, playingStep, onStep, onToggleStep, Modifier.weight(1f))
    }
}

/** 16 pads. Tap toggles one; dragging across pads "paints" them on or off in one gesture. */
@Composable
private fun StepGrid(
    row: List<Boolean>,
    accent: Color,
    playingStep: Int,
    onStep: (Int, Boolean) -> Unit,
    onToggleStep: (Int) -> Unit,
    modifier: Modifier
) {
    BoxWithConstraints(modifier.fillMaxSize()) {
        val gap = 3.dp
        val cell = (maxWidth - gap * (STEPS - 1)) / STEPS
        var paintValue by remember { mutableStateOf<Boolean?>(null) }
        var lastCell by remember { mutableStateOf(-1) }
        val currentRow by androidx.compose.runtime.rememberUpdatedState(row)
        val stepCallback by androidx.compose.runtime.rememberUpdatedState(onStep)
        val toggleCallback by androidx.compose.runtime.rememberUpdatedState(onToggleStep)

        fun cellAt(x: Float, widthPx: Float): Int =
            ((x / widthPx) * STEPS).toInt().coerceIn(0, STEPS - 1)

        Box(
            Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    androidx.compose.foundation.gestures.detectTapGestures { o ->
                        toggleCallback(cellAt(o.x, size.width.toFloat()))
                    }
                }
                .pointerInput(Unit) {
                    detectDragGestures(
                        onDragStart = { o: Offset ->
                            val c = cellAt(o.x, size.width.toFloat())
                            val target = !currentRow[c]
                            paintValue = target
                            lastCell = c
                            stepCallback(c, target)
                        },
                        onDragEnd = { paintValue = null; lastCell = -1 },
                        onDragCancel = { paintValue = null; lastCell = -1 },
                        onDrag = { change, _ ->
                            change.consume()
                            val c = cellAt(change.position.x, size.width.toFloat())
                            val v = paintValue
                            if (v != null && c != lastCell) {
                                lastCell = c
                                stepCallback(c, v)
                            }
                        }
                    )
                }
        ) {
            Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(gap)) {
                for (i in 0 until STEPS) {
                    Pad(
                        on = row.getOrElse(i) { false },
                        accent = accent,
                        beatStart = i % 4 == 0,
                        playing = i == playingStep,
                        modifier = Modifier.width(cell).fillMaxSize()
                    )
                }
            }
        }
    }
}

@Composable
private fun Pad(on: Boolean, accent: Color, beatStart: Boolean, playing: Boolean, modifier: Modifier) {
    val bg by animateColorAsState(
        when {
            on -> accent
            beatStart -> BF.Surface3
            else -> BF.Surface2
        }, tween(90), label = "pad"
    )
    val s by animateFloatAsState(if (playing && on) 1.12f else 1f, tween(70), label = "padScale")
    Box(
        modifier
            .scale(s)
            .clip(RoundedCornerShape(7.dp))
            .background(bg)
            .then(
                if (playing) Modifier.border(1.5.dp, Color.White.copy(alpha = if (on) 0.9f else 0.5f), RoundedCornerShape(7.dp))
                else Modifier
            )
    )
}
