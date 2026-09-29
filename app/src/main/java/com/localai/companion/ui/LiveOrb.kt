package com.localai.companion.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import com.localai.companion.engine.AppState

@Composable
fun LiveOrb(state: AppState) {
    val transition = rememberInfiniteTransition(label = "orb_transition")

    val targetColor = when (state) {
        AppState.IDLE -> Color.DarkGray
        AppState.LISTENING -> CyanAccent
        AppState.PROCESSING -> PurpleAccent
        AppState.SPEAKING -> CyanAccent
        AppState.ERROR -> ErrorRed
        AppState.OFFLINE -> Color.DarkGray
    }

    val color by animateColorAsState(
        targetValue = targetColor,
        animationSpec = tween(500),
        label = "color_anim"
    )

    val scale by transition.animateFloat(
        initialValue = 0.85f,
        targetValue = if (
            state == AppState.LISTENING ||
            state == AppState.SPEAKING
        ) {
            1.15f
        } else {
            1.0f
        },
        animationSpec = infiniteRepeatable(
            animation = tween(
                if (state == AppState.PROCESSING) {
                    400
                } else {
                    1200
                },
                easing = FastOutSlowInEasing
            ),
            repeatMode = RepeatMode.Reverse
        ),
        label = "scale_anim"
    )

    Canvas(
        modifier = Modifier.fillMaxSize()
    ) {
        val radius =
            (size.minDimension / 2) * scale

        drawCircle(
            color = color.copy(alpha = 0.15f),
            radius = radius
        )

        drawCircle(
            color = color,
            radius = size.minDimension / 2.5f,
            style = Stroke(width = 4.dp.toPx())
        )
    }
}
