package com.beatforge.studio.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.Undo
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.GridView
import androidx.compose.material.icons.rounded.IosShare
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.ViewTimeline
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.beatforge.studio.StudioViewModel

private enum class Tab(val label: String, val icon: ImageVector) {
    BEAT("Beat", Icons.Rounded.GridView),
    MIXER("Mixer", Icons.Rounded.Tune),
    TIMELINE("Song", Icons.Rounded.ViewTimeline),
    VOCALS("Vocals", Icons.Rounded.Mic)
}

@Composable
fun StudioScreen(vm: StudioViewModel) {
    val project by vm.project.collectAsStateWithLifecycle()
    val playback by vm.playback.collectAsStateWithLifecycle()
    val busy by vm.busy.collectAsStateWithLifecycle()
    val message by vm.message.collectAsStateWithLifecycle()
    val canUndo by vm.canUndo.collectAsStateWithLifecycle()
    val snack = remember { SnackbarHostState() }
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var showExport by remember { mutableStateOf(false) }

    val p = project ?: return

    BackHandler { vm.closeProject() }
    LaunchedEffect(message) {
        message?.let {
            snack.showSnackbar(it)
            vm.consumeMessage()
        }
    }

    Box(Modifier.fillMaxSize().background(BF.Screen)) {
        Column(Modifier.fillMaxSize().statusBarsPadding()) {
            TopBar(
                name = p.name,
                canUndo = canUndo,
                onBack = vm::closeProject,
                onRename = { n -> vm.tweak { it.copy(name = n.take(32)) } },
                onUndo = vm::undo,
                onExport = { showExport = true }
            )
            TransportBar(
                bpm = p.bpm,
                swing = p.swing,
                playing = playback.playing,
                songMode = p.songMode,
                meterL = playback.peakL,
                meterR = playback.peakR,
                onPlay = vm::togglePlay,
                onBpm = { b -> vm.tweak { it.copy(bpm = b.coerceIn(40, 220)) } },
                onSwing = { s -> vm.tweak { it.copy(swing = s) } }
            )
            Box(Modifier.weight(1f)) {
                AnimatedContent(
                    targetState = tab,
                    transitionSpec = {
                        (fadeIn(androidx.compose.animation.core.tween(220)) +
                            slideInVertically(androidx.compose.animation.core.tween(220)) { it / 24 }) togetherWith
                            fadeOut(androidx.compose.animation.core.tween(120))
                    },
                    label = "tab"
                ) { t ->
                    when (Tab.entries[t]) {
                        Tab.BEAT -> BeatTab(vm, p, playback)
                        Tab.MIXER -> MixerTab(vm, p, playback)
                        Tab.TIMELINE -> TimelineTab(vm, p, playback)
                        Tab.VOCALS -> VocalsTab(vm, p)
                    }
                }
                SnackbarHost(snack, Modifier.align(Alignment.BottomCenter).padding(12.dp))
            }
            BottomNav(tab) { tab = it }
        }

        AnimatedVisibility(
            busy != null,
            Modifier.align(Alignment.Center),
            enter = fadeIn(), exit = fadeOut()
        ) { BusyCard(busy ?: "") }
    }

    if (showExport) {
        ExportDialog(
            hasSong = p.arrangement.isNotEmpty(),
            onDismiss = { showExport = false },
            onExport = { song, loops ->
                showExport = false
                vm.export(song, loops)
            }
        )
    }
}

@Composable
private fun TopBar(
    name: String,
    canUndo: Boolean,
    onBack: () -> Unit,
    onRename: (String) -> Unit,
    onUndo: () -> Unit,
    onExport: () -> Unit
) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RoundIconButton(Icons.AutoMirrored.Rounded.ArrowBack, "Back", size = 42.dp, onClick = onBack)
        Spacer(Modifier.width(10.dp))
        BasicTextField(
            value = name,
            onValueChange = onRename,
            singleLine = true,
            textStyle = TextStyle(color = BF.Text, fontWeight = FontWeight.Bold, fontSize = 18.sp),
            cursorBrush = SolidColor(BF.Pink),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(),
            modifier = Modifier.weight(1f)
        )
        RoundIconButton(
            Icons.AutoMirrored.Rounded.Undo, "Undo", size = 42.dp,
            enabled = canUndo, tint = BF.Text, onClick = onUndo
        )
        Spacer(Modifier.width(8.dp))
        Row(
            Modifier
                .height(42.dp)
                .clip(RoundedCornerShape(21.dp))
                .background(BF.Brand)
                .clickable(onClick = onExport)
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Rounded.IosShare, null, tint = Color.White, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
            Text("Export", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
        }
    }
}

@Composable
private fun TransportBar(
    bpm: Int,
    swing: Float,
    playing: Boolean,
    songMode: Boolean,
    meterL: Float,
    meterR: Float,
    onPlay: () -> Unit,
    onBpm: (Int) -> Unit,
    onSwing: (Float) -> Unit
) {
    Card(Modifier.fillMaxWidth().padding(horizontal = 12.dp), corner = 24.dp) {
        Column(Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                val playBg by animateColorAsState(if (playing) BF.Pink else BF.Violet, label = "play")
                Box(
                    Modifier
                        .size(56.dp)
                        .clip(RoundedCornerShape(18.dp))
                        .background(playBg)
                        .clickable(onClick = onPlay),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        if (playing) Icons.Rounded.Stop else Icons.Rounded.PlayArrow,
                        if (playing) "Stop" else "Play",
                        tint = Color.White,
                        modifier = Modifier.size(34.dp)
                    )
                }
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    BpmStepper(bpm, onBpm)
                }
                Column(Modifier.width(96.dp)) {
                    Text(
                        if (songMode) "SONG MODE" else "LOOP MODE",
                        color = if (songMode) BF.Cyan else BF.TextDim,
                        fontWeight = FontWeight.Bold, fontSize = 11.sp, letterSpacing = 1.sp
                    )
                    Spacer(Modifier.height(6.dp))
                    LevelMeter(meterL, meterR, Modifier.fillMaxWidth().height(14.dp))
                }
            }
            Spacer(Modifier.height(4.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("SWING", color = BF.TextDim, fontWeight = FontWeight.Bold, fontSize = 11.sp, letterSpacing = 1.sp)
                Spacer(Modifier.width(10.dp))
                NeonSlider(swing, onSwing, Modifier.weight(1f), accent = BF.Cyan)
                Spacer(Modifier.width(10.dp))
                Text("${(swing * 100).toInt()}%", color = BF.Text, fontWeight = FontWeight.Bold, fontSize = 12.sp)
            }
        }
    }
}

@Composable
private fun BpmStepper(bpm: Int, onBpm: (Int) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        StepButton("−") { onBpm(bpm - 1) }
        Column(Modifier.width(78.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text("$bpm", color = BF.Text, fontWeight = FontWeight.Black, fontSize = 28.sp)
            Text("BPM", color = BF.TextDim, fontWeight = FontWeight.Bold, fontSize = 10.sp, letterSpacing = 1.sp)
        }
        StepButton("+") { onBpm(bpm + 1) }
    }
}

@Composable
private fun StepButton(label: String, onClick: () -> Unit) {
    Box(
        Modifier
            .size(38.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(BF.Surface3)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) { Text(label, color = BF.Text, fontWeight = FontWeight.Bold, fontSize = 20.sp) }
}

@Composable
private fun BottomNav(selected: Int, onSelect: (Int) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .background(BF.Surface)
            .navigationBarsPadding()
            .padding(horizontal = 8.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceEvenly
    ) {
        Tab.entries.forEachIndexed { i, t ->
            val active = i == selected
            val tint by animateColorAsState(if (active) Color.White else BF.TextDim, label = "tint")
            Column(
                Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(16.dp))
                    .background(if (active) BF.Violet.copy(alpha = 0.25f) else Color.Transparent)
                    .clickable { onSelect(i) }
                    .padding(vertical = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(t.icon, t.label, tint = tint, modifier = Modifier.size(24.dp))
                Text(t.label, color = tint, fontWeight = FontWeight.SemiBold, fontSize = 11.sp)
            }
        }
    }
}

@Composable
private fun ExportDialog(hasSong: Boolean, onDismiss: () -> Unit, onExport: (Boolean, Int) -> Unit) {
    var song by remember { mutableStateOf(false) }
    var loops by remember { mutableIntStateOf(4) }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = BF.Surface2,
        title = { Text("Export WAV", color = BF.Text, fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Render your beat to a high-quality stereo WAV and share it anywhere.", color = BF.TextDim, fontSize = 14.sp)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Chip("Loop", !song, onClick = { song = false })
                    if (hasSong) Chip("Full song", song, accent = BF.Cyan, onClick = { song = true })
                }
                if (!song) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(1, 2, 4, 8).forEach { n ->
                            Chip("$n bar${if (n > 1) "s" else ""}", loops == n, accent = BF.Pink, onClick = { loops = n })
                        }
                    }
                } else {
                    Text("Includes your pattern arrangement and every vocal clip.", color = BF.TextFaint, fontSize = 12.sp)
                }
            }
        },
        confirmButton = { TextButton(onClick = { onExport(song, loops) }) { Text("Render", color = BF.Pink, fontWeight = FontWeight.Bold) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel", color = BF.TextDim) } }
    )
}
