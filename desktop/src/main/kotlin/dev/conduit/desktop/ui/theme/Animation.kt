package dev.conduit.desktop.ui.theme

import androidx.compose.animation.core.EaseInOut
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue

object AnimationSpecs {
    const val BREATHING_DURATION_MS = 1500
    const val TAB_FADE_DURATION_MS = 200
}

@Composable
fun rememberBreathingAlpha(): Float {
    val transition = rememberInfiniteTransition(label = "breathing")
    val alpha by transition.animateFloat(
        initialValue = 0.3f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(
                durationMillis = AnimationSpecs.BREATHING_DURATION_MS,
                easing = EaseInOut,
            ),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "breathingAlpha",
    )
    return alpha
}
