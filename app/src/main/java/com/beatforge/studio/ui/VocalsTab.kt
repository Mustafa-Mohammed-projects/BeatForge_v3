package com.beatforge.studio.ui

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.FileOpen
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.beatforge.studio.StudioViewModel
import com.beatforge.studio.audio.AudioUtil
import com.beatforge.studio.audio.Project
import com.beatforge.studio.audio.VocalClip

@Composable
fun VocalsTab(vm: StudioViewModel, p: Project) {
    val context = LocalContext.current
    val recording by vm.recording.collectAsStateWithLifecycle()
    val level by vm.recLevel.collectAsStateWithLifecycle()
    var withBeat by remember { mutableStateOf(true) }

    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) vm.startRecording(withBeat) else vm.toast("Microphone permission is needed to record")
    }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) vm.importAudio(uri, displayName(context, uri), asClip = true)
    }

    fun toggleRecord() {
        if (recording) {
            vm.stopRecording()
        } else if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
            vm.startRecording(withBeat)
        } else {
            permission.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(12.dp)) {
        Card(Modifier.fillMaxWidth(), corner = 28.dp) {
            Column(
                Modifier.fillMaxWidth().padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                RecordButton(recording, level, ::toggleRecord)
                Spacer(Modifier.height(12.dp))
                Text(
                    if (recording) "Recording… tap to stop" else "Tap to record your vocals",
                    color = if (recording) BF.Pink else BF.Text, fontWeight = FontWeight.Bold, fontSize = 16.sp
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    "Use headphones so the beat doesn't leak into the mic.",
                    color = BF.TextDim, fontSize = 12.sp
                )
                Spacer(Modifier.height(14.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Chip("Play beat while recording", withBeat, accent = BF.Cyan, onClick = { if (!recording) withBeat = !withBeat })
                }
            }
        }

        Spacer(Modifier.height(14.dp))
        Row(
            Modifier
                .fillMaxWidth()
                .height(52.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(BF.Surface2)
                .clickable { picker.launch(arrayOf("audio/*")) },
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Icon(Icons.Rounded.FileOpen, null, tint = BF.Cyan)
            Spacer(Modifier.width(8.dp))
            Text("Import song, loop or acapella", color = BF.Text, fontWeight = FontWeight.SemiBold)
        }

        Spacer(Modifier.height(18.dp))
        SectionTitle("Clips (${p.clips.size})")
        Spacer(Modifier.height(8.dp))

        if (p.clips.isEmpty()) {
            Card(Modifier.fillMaxWidth()) {
                EmptyHint(
                    "No recordings yet",
                    "Record a take or import audio. It lands on the Song timeline where you can move and trim it."
                )
            }
        } else {
            p.clips.forEach { clip ->
                ClipCard(vm, p, clip)
                Spacer(Modifier.height(10.dp))
            }
        }
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun RecordButton(recording: Boolean, level: Float, onClick: () -> Unit) {
    val t = rememberInfiniteTransition(label = "rec")
    val pulse by t.animateFloat(
        0.9f, 1.15f,
        infiniteRepeatable(tween(650), RepeatMode.Reverse), label = "pulse"
    )
    val ring = if (recording) 1f + level * 0.5f else 1f
    Box(Modifier.size(132.dp), contentAlignment = Alignment.Center) {
        if (recording) {
            Box(
                Modifier
                    .size(120.dp)
                    .scale(ring * pulse)
                    .alpha(0.3f)
                    .clip(CircleShape)
                    .background(BF.Pink)
            )
        }
        Box(
            Modifier
                .size(92.dp)
                .clip(CircleShape)
                .background(if (recording) BF.Pink else BF.Brand)
                .clickable(onClick = onClick),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                if (recording) Icons.Rounded.Stop else Icons.Rounded.Mic,
                if (recording) "Stop recording" else "Record",
                tint = Color.White, modifier = Modifier.size(44.dp)
            )
        }
    }
}

@Composable
private fun ClipCard(vm: StudioViewModel, p: Project, clip: VocalClip) {
    val peaks = remember(clip.id, clip.samples.size) { AudioUtil.peaks(clip.samples, 120) }
    val maxStart = (p.arrangement.size - 1).coerceAtLeast(0)
    Card(Modifier.fillMaxWidth(), corner = 22.dp) {
        Column(Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(clip.name, color = BF.Text, fontWeight = FontWeight.Bold, fontSize = 16.sp, maxLines = 1)
                    Text("%.1f s · bar ${clip.startBar + 1}".format(clip.durationSec), color = BF.TextDim, fontSize = 12.sp)
                }
                Chip(if (clip.muted) "Muted" else "On", !clip.muted, accent = BF.Cyan, modifier = Modifier.padding(end = 8.dp)) {
                    vm.tweak { it.updateClip(clip.id) { c -> c.copy(muted = !c.muted) } }
                }
                RoundIconButton(Icons.Rounded.Delete, "Delete clip", size = 38.dp, tint = BF.TextDim) {
                    vm.edit { it.removeClip(clip.id) }
                }
            }
            Spacer(Modifier.height(10.dp))
            Waveform(
                peaks,
                Modifier.fillMaxWidth().height(64.dp).clip(RoundedCornerShape(12.dp)).background(BF.Surface2).padding(6.dp),
                color = BF.Cyan, trimStart = clip.trimStart, trimEnd = clip.trimEnd
            )
            Spacer(Modifier.height(6.dp))
            LabeledSlider("Start trim", clip.trimStart, 0f..0.95f) { v ->
                vm.tweak { it.updateClip(clip.id) { c -> c.copy(trimStart = v.coerceAtMost(c.trimEnd - 0.03f)) } }
            }
            LabeledSlider("End trim", clip.trimEnd, 0.05f..1f) { v ->
                vm.tweak { it.updateClip(clip.id) { c -> c.copy(trimEnd = v.coerceAtLeast(c.trimStart + 0.03f)) } }
            }
            LabeledSlider("Volume", clip.gain, 0f..2f) { v ->
                vm.tweak { it.updateClip(clip.id) { c -> c.copy(gain = v) } }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Starts at bar", color = BF.TextDim, fontSize = 12.sp, modifier = Modifier.weight(1f))
                MiniButton("−") { vm.edit { it.updateClip(clip.id) { c -> c.copy(startBar = (c.startBar - 1).coerceAtLeast(0)) } } }
                Text("${clip.startBar + 1}", color = BF.Text, fontWeight = FontWeight.Bold,
                    modifier = Modifier.width(40.dp), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                MiniButton("+") { vm.edit { it.updateClip(clip.id) { c -> c.copy(startBar = (c.startBar + 1).coerceAtMost(maxStart)) } } }
            }
        }
    }
}

@Composable
private fun LabeledSlider(label: String, value: Float, range: ClosedFloatingPointRange<Float>, onChange: (Float) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label, color = BF.TextDim, fontSize = 12.sp, modifier = Modifier.width(78.dp))
        NeonSlider(value, onChange, Modifier.weight(1f), range = range, accent = BF.Cyan)
    }
}
