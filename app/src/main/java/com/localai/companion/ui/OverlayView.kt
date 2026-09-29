package com.localai.companion.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.localai.companion.engine.CompanionEngine

@Composable
fun OverlayView() {
    val state by CompanionEngine.appState.collectAsState()

    CompanionTheme {
        Row(
            modifier = Modifier
                .background(
                    SurfaceGraphite.copy(alpha = 0.95f),
                    CircleShape
                )
                .padding(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier.size(48.dp)
            ) {
                LiveOrb(state = state)
            }

            Spacer(
                modifier = Modifier.width(12.dp)
            )

            IconButton(
                onClick = {
                    CompanionEngine.onStopLiveRequested?.invoke()
                },
                modifier = Modifier
                    .size(40.dp)
                    .background(
                        Color.DarkGray.copy(alpha = 0.5f),
                        CircleShape
                    )
            ) {
                Icon(
                    imageVector = Icons.Default.Stop,
                    contentDescription = "Stop Live",
                    tint = ErrorRed
                )
            }
        }
    }
}
