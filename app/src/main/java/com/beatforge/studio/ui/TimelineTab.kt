package com.beatforge.studio.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.beatforge.studio.StudioViewModel
import com.beatforge.studio.audio.AudioUtil
import com.beatforge.studio.audio.PATTERN_COUNT
import com.beatforge.studio.audio.PlaybackState
import com.beatforge.studio.audio.Project
import com.beatforge.studio.audio.STEPS

private val BAR_W = 64.dp
private val LABEL_W = 70.dp

@Composable
fun TimelineTab(vm: StudioViewModel, p: Project, playback: PlaybackState) {
    val hScroll = rememberScrollState()
    val bars = p.arrangement.size
    val playBar = if (playback.playing && playback.step >= 0 && p.songMode)
        (playback.step / STEPS) % bars else -1
    val playFrac = if (playBar >= 0) ((playback.step % STEPS) / STEPS.toFloat()) else 0f

    Column(Modifier.fillMaxHeight().verticalScroll(rememberScrollState()).padding(12.dp)) {
        Card(Modifier.fillMaxWidth()) {
            Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Song mode", color = BF.Text, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    Text(
                        if (p.songMode) "Plays your arrangement and vocal clips"
                        else "Off — only the selected pattern loops",
                        color = BF.TextDim, fontSize = 12.sp
                    )
                }
                Switch(
                    checked = p.songMode,
                    onCheckedChange = { vm.setSongMode(it) },
                    colors = SwitchDefaults.colors(
                        checkedTrackColor = BF.Cyan, checkedThumbColor = Color.White,
                        uncheckedTrackColor = BF.Surface3, uncheckedThumbColor = BF.TextDim,
                        uncheckedBorderColor = BF.Outline
                    )
                )
            }
        }

        Spacer(Modifier.height(16.dp))
        SectionTitle("Arrangement") {
            Row(verticalAlignment = Alignment.CenterVertically) {
                MiniButton("−") { vm.edit { it.resizeArrangement(bars - 1) } }
                Text("$bars bars", color = BF.Text, fontWeight = FontWeight.Bold, fontSize = 13.sp,
                    modifier = Modifier.padding(horizontal = 10.dp))
                MiniButton("+") { vm.edit { it.resizeArrangement(bars + 1) } }
            }
        }
        Spacer(Modifier.height(10.dp))

        Card(Modifier.fillMaxWidth(), corner = 20.dp) {
            Column(Modifier.padding(vertical = 12.dp)) {
                Row {
                    Box(Modifier.width(LABEL_W))
                    Row(Modifier.horizontalScroll(hScroll)) {
                        for (b in 0 until bars) {
                            Text(
                                "${b + 1}",
                                color = if (b == playBar) BF.Pink else BF.TextFaint,
                                fontWeight = FontWeight.Bold, fontSize = 11.sp,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.width(BAR_W)
                            )
                        }
                    }
                }
                Spacer(Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Pattern", color = BF.TextDim, fontSize = 12.sp, fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.width(LABEL_W).padding(start = 12.dp))
                    Box(Modifier.horizontalScroll(hScroll)) {
                        Row {
                            for (b in 0 until bars) {
                                val pat = p.arrangement[b]
                                val active = b == playBar
                                Box(
                                    Modifier
                                        .padding(2.dp)
                                        .width(BAR_W - 4.dp)
                                        .height(54.dp)
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(patternColor(pat).copy(alpha = if (active) 1f else 0.7f))
                                        .then(if (active) Modifier.border(2.dp, Color.White, RoundedCornerShape(12.dp)) else Modifier)
                                        .clickable { vm.edit { it.setArrangementBar(b, (pat + 1) % PATTERN_COUNT) } },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(PATTERN_NAMES[pat], color = Color.White, fontWeight = FontWeight.Black, fontSize = 20.sp)
                                }
                            }
                        }
                        if (playBar >= 0) {
                            Box(
                                Modifier
                                    .offset(x = BAR_W * (playBar + playFrac))
                                    .width(2.dp)
                                    .height(54.dp)
                                    .background(Color.White)
                            )
                        }
                    }
                }
                Text(
                    "Tap a bar to cycle its pattern (A–D).",
                    color = BF.TextFaint, fontSize = 11.sp,
                    modifier = Modifier.padding(start = 12.dp, top = 8.dp)
                )

                if (p.clips.isNotEmpty()) {
                    Spacer(Modifier.height(14.dp))
                    p.clips.forEach { clip ->
                        val span = p.clipBars(clip)
                        Row(Modifier.padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                clip.name, color = BF.Cyan, fontSize = 11.sp, fontWeight = FontWeight.Bold,
                                maxLines = 1, modifier = Modifier.width(LABEL_W).padding(start = 12.dp)
                            )
                            Box(Modifier.horizontalScroll(hScroll)) {
                                Box(Modifier.width(BAR_W * maxOf(bars, clip.startBar + span)).height(38.dp)) {
                                    val peaks = remember(clip.id, clip.samples.size) { AudioUtil.peaks(clip.samples, 80) }
                                    Box(
                                        Modifier
                                            .offset(x = BAR_W * clip.startBar)
                                            .width(BAR_W * span - 4.dp)
                                            .height(38.dp)
                                            .clip(RoundedCornerShape(10.dp))
                                            .background(BF.Cyan.copy(alpha = if (clip.muted) 0.08f else 0.2f))
                                            .border(1.dp, BF.Cyan.copy(alpha = if (clip.muted) 0.3f else 0.9f), RoundedCornerShape(10.dp))
                                    ) {
                                        Waveform(
                                            peaks, Modifier.fillMaxWidth().height(38.dp).padding(4.dp),
                                            color = BF.Cyan, trimStart = clip.trimStart, trimEnd = clip.trimEnd
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(10.dp))
        Text(
            "Record or import vocals in the Vocals tab, then place them on the timeline there.",
            color = BF.TextFaint, fontSize = 12.sp
        )
        Spacer(Modifier.height(24.dp))
    }
}

fun patternColor(i: Int): Color = when (i) {
    0 -> BF.Violet
    1 -> BF.Pink
    2 -> BF.Orange
    else -> BF.Teal
}

@Composable
fun MiniButton(label: String, onClick: () -> Unit) {
    Box(
        Modifier
            .size(32.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(BF.Surface3)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) { Text(label, color = BF.Text, fontWeight = FontWeight.Bold, fontSize = 16.sp) }
}
