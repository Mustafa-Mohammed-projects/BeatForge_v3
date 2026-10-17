package com.beatforge.studio

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.beatforge.studio.ui.BF
import com.beatforge.studio.ui.BeatForgeTheme
import com.beatforge.studio.ui.HomeScreen
import com.beatforge.studio.ui.SplashScreen
import com.beatforge.studio.ui.StudioScreen
import kotlinx.coroutines.delay

class MainActivity : ComponentActivity() {
    private val vm: StudioViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            BeatForgeTheme {
                Box(Modifier.fillMaxSize().background(BF.Bg)) {
                    AppRoot(vm)
                }
            }
        }
    }

    override fun onStop() {
        vm.stop()
        vm.saveNow()
        super.onStop()
    }
}

private const val SPLASH_MILLIS = 3000L

@Composable
private fun AppRoot(vm: StudioViewModel) {
    var splashDone by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        if (!splashDone) {
            delay(SPLASH_MILLIS)
            splashDone = true
        }
    }

    val project by vm.project.collectAsStateWithLifecycle()

    Crossfade(targetState = splashDone, animationSpec = tween(600), label = "splash") { done ->
        if (!done) {
            SplashScreen()
        } else {
            AnimatedContent(
                targetState = project != null,
                transitionSpec = {
                    if (targetState) {
                        (slideInHorizontally(tween(350)) { it / 4 } + fadeIn(tween(350))) togetherWith
                            fadeOut(tween(200))
                    } else {
                        fadeIn(tween(300)) togetherWith
                            (slideOutHorizontally(tween(300)) { it / 4 } + fadeOut(tween(250)))
                    }
                },
                label = "root"
            ) { inStudio ->
                if (inStudio) StudioScreen(vm) else HomeScreen(vm)
            }
        }
    }
}
