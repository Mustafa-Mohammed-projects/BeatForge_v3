package com.beatforge.studio.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.beatforge.studio.StudioViewModel
import com.beatforge.studio.audio.MAX_TRACKS
import com.beatforge.studio.audio.PlaybackState
import com.beatforge.studio.audio.Project

@Composable
fun MixerTab(vm: StudioViewModel, p: Project, playback: PlaybackState) {
    Column(Modifier.fillMaxSize()) {
        LazyRow(
            Modifier.weight(1f).fillMaxWidth(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            itemsIndexed(p.tracks, key = { _, t -> t.id }) { i, t ->
                val accent = BF.TrackColors[t.colorIndex % BF.TrackColors.size]
                Card(Modifier.width(96.dp).fillMaxHeight(), corner = 22.dp) {
                    Column(
                        Modifier.padding(vertical = 10.dp, horizontal = 8.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            t.name, color = accent, fontWeight = FontWeight.Black, fontSize = 12.sp,
                            maxLines = 1, textAlign = TextAlign.Center
                        )
                        Spacer(Modifier.height(6.dp))

                        Text("PAN", color = BF.TextFaint, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                        NeonSlider(
                            t.pan, { v -> vm.tweak { it.updateTrack(i) { tr -> tr.copy(pan = if (kotlin.math.abs(v) < 0.08f) 0f else v) } } },
                            Modifier.fillMaxWidth(), range = -1f..1f, accent = accent, center = true
                        )
                        Text(
                            when {
                                t.pan < -0.05f -> "L${(-t.pan * 100).toInt()}"
                                t.pan > 0.05f -> "R${(t.pan * 100).toInt()}"
                                else -> "C"
                            },
                            color = BF.TextDim, fontSize = 10.sp
                        )

                        Spacer(Modifier.height(4.dp))
                        VerticalFader(
                            value = t.volume,
                            level = playback.trackLevels.getOrElse(i) { 0f },
                            onChange = { v -> vm.tweak { it.updateTrack(i) { tr -> tr.copy(volume = v) } } },
                            accent = accent,
                            modifier = Modifier.weight(1f)
                        )
                        Text("${(t.volume * 100).toInt()}", color = BF.Text, fontWeight = FontWeight.Bold, fontSize = 12.sp)

                        Spacer(Modifier.height(6.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            SmallToggle("M", t.mute, BF.Red) { vm.tweak { it.updateTrack(i) { tr -> tr.copy(mute = !tr.mute) } } }
                            SmallToggle("S", t.solo, BF.Amber) { vm.tweak { it.updateTrack(i) { tr -> tr.copy(solo = !tr.solo) } } }
                        }
                        Spacer(Modifier.height(6.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            PitchButton("−") { vm.tweak { it.updateTrack(i) { tr -> tr.copy(pitch = (tr.pitch - 1).coerceAtLeast(-12)) } } }
                            Text(
                                if (t.pitch > 0) "+${t.pitch}" else "${t.pitch}",
                                color = BF.TextDim, fontSize = 11.sp, textAlign = TextAlign.Center,
                                modifier = Modifier.width(26.dp)
                            )
                            PitchButton("+") { vm.tweak { it.updateTrack(i) { tr -> tr.copy(pitch = (tr.pitch + 1).coerceAtMost(12)) } } }
                        }
                        Text("PITCH", color = BF.TextFaint, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                        if (p.tracks.size > 1) {
                            Spacer(Modifier.height(4.dp))
                            Icon(
                                Icons.Rounded.Delete, "Remove", tint = BF.TextFaint,
                                modifier = Modifier.size(20.dp).clickable { vm.edit { pr -> pr.removeTrack(i) } }
                            )
                        }
                    }
                }
            }
        }

        Card(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp), corner = 20.dp) {
            Row(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("MASTER", color = BF.Cyan, fontWeight = FontWeight.Black, fontSize = 12.sp, letterSpacing = 1.sp)
                Spacer(Modifier.width(12.dp))
                NeonSlider(
                    p.masterVolume, { v -> vm.tweak { it.copy(masterVolume = v) } },
                    Modifier.weight(1f), accent = BF.Cyan
                )
                Spacer(Modifier.width(12.dp))
                Text("${(p.masterVolume * 100).toInt()}", color = BF.Text, fontWeight = FontWeight.Bold)
            }
        }
        // `MAX_TRACKS` is referenced to keep the strip count in sync with the engine's meter array.
        Spacer(Modifier.height(if (p.tracks.size >= MAX_TRACKS) 4.dp else 0.dp))
    }
}

@Composable
private fun SmallToggle(label: String, on: Boolean, color: Color, onClick: () -> Unit) {
    Box(
        Modifier
            .size(34.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(if (on) color else BF.Surface3)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(label, color = if (on) Color.Black else BF.TextDim, fontWeight = FontWeight.Black, fontSize = 13.sp)
    }
}

@Composable
private fun PitchButton(label: String, onClick: () -> Unit) {
    Box(
        Modifier
            .size(26.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(BF.Surface3)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) { Text(label, color = BF.Text, fontWeight = FontWeight.Bold, fontSize = 14.sp) }
}
