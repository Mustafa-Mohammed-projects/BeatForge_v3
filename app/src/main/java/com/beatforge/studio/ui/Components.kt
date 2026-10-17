package com.beatforge.studio.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.max

@Composable
fun Card(
    modifier: Modifier = Modifier,
    corner: Dp = 20.dp,
    color: Color = BF.Surface,
    border: Color = BF.Outline.copy(alpha = 0.6f),
    onClick: (() -> Unit)? = null,
    content: @Composable () -> Unit
) {
    val shape = RoundedCornerShape(corner)
    Box(
        modifier
            .clip(shape)
            .background(color)
            .border(1.dp, border, shape)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
    ) { content() }
}

@Composable
fun GradientButton(
    text: String,
    icon: ImageVector? = null,
    modifier: Modifier = Modifier,
    brush: Brush = BF.BrandHorizontal,
    onClick: () -> Unit
) {
    Row(
        modifier
            .height(52.dp)
            .clip(RoundedCornerShape(26.dp))
            .background(brush)
            .clickable(onClick = onClick)
            .padding(horizontal = 24.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = androidx.compose.foundation.layout.Arrangement.Center
    ) {
        if (icon != null) {
            Icon(icon, null, tint = Color.White, modifier = Modifier.size(22.dp))
            Spacer(Modifier.width(8.dp))
        }
        Text(text, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 15.sp)
    }
}

@Composable
fun Chip(
    text: String,
    selected: Boolean,
    modifier: Modifier = Modifier,
    accent: Color = BF.Violet,
    onClick: () -> Unit
) {
    val bg = if (selected) accent.copy(alpha = 0.22f) else BF.Surface2
    val stroke = if (selected) accent else BF.Outline
    Box(
        modifier
            .height(36.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(bg)
            .border(1.dp, stroke, RoundedCornerShape(18.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text,
            color = if (selected) Color.White else BF.TextDim,
            fontWeight = FontWeight.SemiBold,
            fontSize = 13.sp
        )
    }
}

@Composable
fun RoundIconButton(
    icon: ImageVector,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    size: Dp = 44.dp,
    tint: Color = BF.Text,
    background: Color = BF.Surface2,
    enabled: Boolean = true,
    onClick: () -> Unit
) {
    Box(
        modifier
            .size(size)
            .clip(CircleShape)
            .background(background)
            .then(if (enabled) Modifier.clickable(onClick = onClick) else Modifier),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            icon, contentDescription,
            tint = if (enabled) tint else BF.TextFaint,
            modifier = Modifier.size(size * 0.5f)
        )
    }
}

/** Horizontal slider drawn from scratch: bigger touch target, gradient fill, no Material thumb clutter. */
@Composable
fun NeonSlider(
    value: Float,
    onChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
    range: ClosedFloatingPointRange<Float> = 0f..1f,
    accent: Color = BF.Violet,
    center: Boolean = false
) {
    var width by remember { mutableFloatStateOf(1f) }
    val span = range.endInclusive - range.start
    fun toValue(x: Float) = (range.start + (x / max(1f, width)).coerceIn(0f, 1f) * span)
    val frac = ((value - range.start) / span).coerceIn(0f, 1f)

    Box(
        modifier
            .height(36.dp)
            .pointerInput(range) {
                detectTapGestures { onChange(toValue(it.x)) }
            }
            .pointerInput(range) {
                detectDragGestures { change, _ ->
                    change.consume()
                    onChange(toValue(change.position.x))
                }
            },
        contentAlignment = Alignment.CenterStart
    ) {
        Canvas(Modifier.fillMaxSize()) {
            width = size.width
            val h = 6.dp.toPx()
            val y = size.height / 2 - h / 2
            drawRoundRect(BF.Surface3, Offset(0f, y), Size(size.width, h), CornerRadius(h / 2))
            val x = size.width * frac
            val from = if (center) size.width / 2 else 0f
            val left = minOf(from, x)
            val right = maxOf(from, x)
            drawRoundRect(
                Brush.horizontalGradient(listOf(accent.copy(alpha = 0.6f), accent), left, right),
                Offset(left, y), Size(right - left, h), CornerRadius(h / 2)
            )
            drawCircle(Color.White, 9.dp.toPx(), Offset(x.coerceIn(9.dp.toPx(), size.width - 9.dp.toPx()), size.height / 2))
            drawCircle(accent, 5.dp.toPx(), Offset(x.coerceIn(9.dp.toPx(), size.width - 9.dp.toPx()), size.height / 2))
        }
    }
}

/** Vertical fader with a live level meter behind it, used on the mixer channel strips. */
@Composable
fun VerticalFader(
    value: Float,
    level: Float,
    onChange: (Float) -> Unit,
    accent: Color,
    modifier: Modifier = Modifier
) {
    var height by remember { mutableFloatStateOf(1f) }
    fun toValue(y: Float) = (1f - (y / max(1f, height))).coerceIn(0f, 1f)
    val animLevel by animateFloatAsState(level.coerceIn(0f, 1f), spring(stiffness = 900f), label = "lvl")
    Box(
        modifier
            .width(54.dp)
            .pointerInput(Unit) { detectTapGestures { onChange(toValue(it.y)) } }
            .pointerInput(Unit) {
                detectDragGestures { change, _ ->
                    change.consume()
                    onChange(toValue(change.position.y))
                }
            }
    ) {
        Canvas(Modifier.fillMaxSize()) {
            height = size.height
            val trackW = 10.dp.toPx()
            val cx = size.width / 2
            val left = cx - trackW / 2
            drawRoundRect(BF.Surface3, Offset(left, 0f), Size(trackW, size.height), CornerRadius(trackW / 2))
            val lh = size.height * animLevel
            drawRoundRect(
                Brush.verticalGradient(listOf(accent, accent.copy(alpha = 0.25f)), size.height - lh, size.height),
                Offset(left, size.height - lh), Size(trackW, lh), CornerRadius(trackW / 2)
            )
            val ty = size.height * (1f - value)
            val capW = 38.dp.toPx()
            val capH = 22.dp.toPx()
            val top = (ty - capH / 2).coerceIn(0f, size.height - capH)
            drawRoundRect(Color(0xFF3A3A78), Offset(cx - capW / 2, top), Size(capW, capH), CornerRadius(8.dp.toPx()))
            drawRoundRect(
                accent, Offset(cx - capW / 2 + 6.dp.toPx(), top + capH / 2 - 1.5f.dp.toPx()),
                Size(capW - 12.dp.toPx(), 3.dp.toPx()), CornerRadius(2.dp.toPx())
            )
        }
    }
}

@Composable
fun LevelMeter(left: Float, right: Float, modifier: Modifier = Modifier) {
    val l by animateFloatAsState(left.coerceIn(0f, 1f), spring(stiffness = 1200f), label = "ml")
    val r by animateFloatAsState(right.coerceIn(0f, 1f), spring(stiffness = 1200f), label = "mr")
    Canvas(modifier) {
        val gap = 3.dp.toPx()
        val barH = (size.height - gap) / 2
        fun bar(top: Float, v: Float) {
            drawRoundRect(BF.Surface3, Offset(0f, top), Size(size.width, barH), CornerRadius(barH / 2))
            val w = size.width * v
            if (w > 0f) {
                drawRoundRect(
                    Brush.horizontalGradient(
                        listOf(BF.Lime, BF.Amber, BF.Red),
                        0f, size.width
                    ),
                    Offset(0f, top), Size(w, barH), CornerRadius(barH / 2)
                )
            }
        }
        bar(0f, l)
        bar(barH + gap, r)
    }
}

/** Draws a waveform from precomputed peaks. [trimStart]/[trimEnd] dim the cut-away parts. */
@Composable
fun Waveform(
    peaks: FloatArray,
    modifier: Modifier = Modifier,
    color: Color = BF.Cyan,
    trimStart: Float = 0f,
    trimEnd: Float = 1f,
    playhead: Float = -1f
) {
    Canvas(modifier) {
        val n = peaks.size
        if (n == 0) return@Canvas
        val barW = size.width / n
        val mid = size.height / 2
        for (i in 0 until n) {
            val f = i / n.toFloat()
            val h = max(2.dp.toPx(), peaks[i] * size.height * 0.95f)
            val inside = f in trimStart..trimEnd
            drawLine(
                color.copy(alpha = if (inside) 0.95f else 0.18f),
                Offset(i * barW + barW / 2, mid - h / 2),
                Offset(i * barW + barW / 2, mid + h / 2),
                strokeWidth = max(1.5f, barW * 0.6f),
                cap = StrokeCap.Round
            )
        }
        if (playhead in 0f..1f) {
            drawLine(Color.White, Offset(size.width * playhead, 0f), Offset(size.width * playhead, size.height), 2.dp.toPx())
        }
    }
}

@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier, trailing: @Composable () -> Unit = {}) {
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(
            text.uppercase(),
            color = BF.TextDim,
            fontWeight = FontWeight.Bold,
            fontSize = 12.sp,
            letterSpacing = 1.2.sp,
            modifier = Modifier.weight(1f)
        )
        trailing()
    }
}

@Composable
fun EmptyHint(title: String, body: String, modifier: Modifier = Modifier) {
    Column(
        modifier.fillMaxWidth().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(title, color = BF.Text, fontWeight = FontWeight.Bold, fontSize = 17.sp, textAlign = TextAlign.Center)
        Spacer(Modifier.height(6.dp))
        Text(body, color = BF.TextDim, fontSize = 13.sp, textAlign = TextAlign.Center)
    }
}

fun Modifier.fillHeight(): Modifier = this.fillMaxHeight()
