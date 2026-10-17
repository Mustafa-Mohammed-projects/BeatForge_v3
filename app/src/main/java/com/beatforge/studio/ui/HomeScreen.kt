package com.beatforge.studio.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.beatforge.studio.StudioViewModel
import com.beatforge.studio.audio.ProjectInfo
import com.beatforge.studio.audio.Template
import com.beatforge.studio.audio.Templates
import java.text.DateFormat
import java.util.Date

private val templateBrushes = listOf(
    Brush.linearGradient(listOf(Color(0xFFFB923C), Color(0xFFFF2E93))),
    Brush.linearGradient(listOf(Color(0xFF8B5CF6), Color(0xFF22D3EE))),
    Brush.linearGradient(listOf(Color(0xFFEF4444), Color(0xFF7C3AED))),
    Brush.linearGradient(listOf(Color(0xFF2DD4BF), Color(0xFFA3E635))),
    Brush.linearGradient(listOf(Color(0xFF2E2E5C), Color(0xFF12122A)))
)

@Composable
fun HomeScreen(vm: StudioViewModel) {
    val projects by vm.projects.collectAsStateWithLifecycle()
    val busy by vm.busy.collectAsStateWithLifecycle()
    val message by vm.message.collectAsStateWithLifecycle()
    val snack = remember { SnackbarHostState() }
    var toDelete by remember { mutableStateOf<ProjectInfo?>(null) }

    LaunchedEffect(message) {
        message?.let {
            snack.showSnackbar(it)
            vm.consumeMessage()
        }
    }

    Box(Modifier.fillMaxSize().background(brush = BF.Screen)) {
        LazyColumn(
            Modifier.fillMaxSize().statusBarsPadding(),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 20.dp, bottom = 120.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item { Header() }
            item {
                SectionTitle("Start a new beat")
                Spacer(Modifier.height(10.dp))
                LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    items(Templates.all.size) { i ->
                        TemplateCard(Templates.all[i], templateBrushes[i % templateBrushes.size]) {
                            vm.newProject(Templates.all[i])
                        }
                    }
                }
                Spacer(Modifier.height(14.dp))
            }
            item { SectionTitle("Your projects") }
            if (projects.isEmpty()) {
                item {
                    Card(Modifier.fillMaxWidth()) {
                        EmptyHint(
                            "No beats yet",
                            "Pick a style above and your first beat is ready to play in one tap."
                        )
                    }
                }
            } else {
                items(projects, key = { it.id }) { info ->
                    ProjectRow(info, onOpen = { vm.openProject(info.id) }, onDelete = { toDelete = info })
                }
            }
        }

        SnackbarHost(
            snack,
            Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(16.dp)
        )

        AnimatedVisibility(busy != null, Modifier.align(Alignment.Center)) {
            BusyCard(busy ?: "")
        }
    }

    toDelete?.let { info ->
        AlertDialog(
            onDismissRequest = { toDelete = null },
            containerColor = BF.Surface2,
            title = { Text("Delete “${info.name}”?", color = BF.Text) },
            text = { Text("The beat and all its recordings will be removed from this device.", color = BF.TextDim) },
            confirmButton = {
                TextButton(onClick = {
                    vm.deleteProject(info.id)
                    toDelete = null
                }) { Text("Delete", color = BF.Red) }
            },
            dismissButton = {
                TextButton(onClick = { toDelete = null }) { Text("Cancel", color = BF.TextDim) }
            }
        )
    }
}

@Composable
private fun Header() {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier.size(46.dp).clip(RoundedCornerShape(14.dp)).background(brush = BF.Brand),
            contentAlignment = Alignment.Center
        ) { Icon(Icons.Rounded.GraphicEq, null, tint = Color.White, modifier = Modifier.size(26.dp)) }
        Spacer(Modifier.width(12.dp))
        Column {
            Text("BeatForge", color = BF.Text, fontWeight = FontWeight.Black, fontSize = 26.sp)
            Text("Make beats. Record vocals. Mix & export.", color = BF.TextDim, fontSize = 13.sp)
        }
    }
    Spacer(Modifier.height(10.dp))
}

@Composable
private fun TemplateCard(t: Template, brush: Brush, onClick: () -> Unit) {
    Box(
        Modifier
            .width(168.dp)
            .height(126.dp)
            .clip(RoundedCornerShape(22.dp))
            .background(brush = brush)
            .clickable(onClick = onClick)
            .padding(16.dp)
    ) {
        Column(Modifier.align(Alignment.BottomStart)) {
            Text(t.name, color = Color.White, fontWeight = FontWeight.Black, fontSize = 21.sp)
            Text(t.tagline, color = Color.White.copy(alpha = 0.85f), fontSize = 12.sp, maxLines = 2)
            if (t.bpm > 0 && t.name != "Blank") {
                Spacer(Modifier.height(4.dp))
                Text("${t.bpm} BPM", color = Color.White.copy(alpha = 0.9f), fontWeight = FontWeight.Bold, fontSize = 11.sp)
            }
        }
        Icon(
            if (t.name == "Blank") Icons.Rounded.Add else Icons.Rounded.AutoAwesome,
            null,
            tint = Color.White.copy(alpha = 0.9f),
            modifier = Modifier.align(Alignment.TopEnd).size(24.dp)
        )
    }
}

@Composable
private fun ProjectRow(info: ProjectInfo, onOpen: () -> Unit, onDelete: () -> Unit) {
    Card(Modifier.fillMaxWidth(), onClick = onOpen) {
        Row(
            Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                Modifier.size(48.dp).clip(CircleShape).background(BF.Surface3),
                contentAlignment = Alignment.Center
            ) { Icon(Icons.Rounded.MusicNote, null, tint = BF.Cyan) }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(info.name, color = BF.Text, fontWeight = FontWeight.Bold, fontSize = 16.sp, maxLines = 1)
                Text(
                    "${info.bpm} BPM · ${DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(info.modified))}",
                    color = BF.TextDim, fontSize = 12.sp
                )
            }
            RoundIconButton(Icons.Rounded.Delete, "Delete", size = 40.dp, tint = BF.TextDim, onClick = onDelete)
        }
    }
}

@Composable
fun BusyCard(text: String) {
    Card(color = BF.Surface2) {
        Row(
            Modifier.padding(horizontal = 22.dp, vertical = 18.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            CircularProgressIndicator(color = BF.Pink, strokeWidth = 3.dp, modifier = Modifier.size(24.dp))
            Spacer(Modifier.width(14.dp))
            Text(text, color = BF.Text, fontWeight = FontWeight.SemiBold)
        }
    }
}
