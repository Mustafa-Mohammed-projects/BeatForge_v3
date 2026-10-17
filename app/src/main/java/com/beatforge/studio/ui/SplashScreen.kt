package com.beatforge.studio.ui

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.animation.core.Animatable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.beatforge.studio.R

/**
 * Shows res/drawable-nodpi/presplash.png full-screen for the first 3 seconds (the timing lives in
 * MainActivity). Replace that file with your own artwork; the screen slowly zooms in and a thin
 * progress bar fills while it is visible.
 */
@Composable
fun SplashScreen() {
    val zoom = remember { Animatable(1.0f) }
    val progress = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        zoom.animateTo(1.06f, tween(3000, easing = FastOutSlowInEasing))
    }
    LaunchedEffect(Unit) {
        progress.animateTo(1f, tween(2900, easing = FastOutSlowInEasing))
    }
    val pulse = rememberInfiniteTransition(label = "pulse").animateFloat(
        initialValue = 0.55f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(700), RepeatMode.Reverse),
        label = "pulseAlpha"
    )

    Box(Modifier.fillMaxSize().background(BF.Bg)) {
        Image(
            painter = painterResource(R.drawable.presplash),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize().scale(zoom.value)
        )
        Box(
            Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(horizontal = 56.dp, vertical = 40.dp)
                .fillMaxWidth()
                .height(4.dp)
                .alpha(pulse.value)
                .clip(RoundedCornerShape(50))
                .background(BF.Surface3.copy(alpha = 0.7f))
        ) {
            Box(
                Modifier
                    .fillMaxWidth(progress.value)
                    .height(4.dp)
                    .clip(RoundedCornerShape(50))
                    .background(BF.BrandHorizontal)
            )
        }
    }
}
