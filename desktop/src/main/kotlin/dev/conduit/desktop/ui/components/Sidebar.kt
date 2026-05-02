package dev.conduit.desktop.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.conduit.desktop.navigation.AppMode

@Composable
fun Sidebar(
    currentMode: AppMode,
    onModeChange: (AppMode) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .width(48.dp)
            .fillMaxHeight()
            .background(Color(0xFF141218))
            .padding(vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // Logo
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(
                    Brush.linearGradient(
                        colors = listOf(Color(0xFF6366F1), Color(0xFFA855F7))
                    )
                ),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = "C",
                color = Color.White,
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp,
            )
        }

        Spacer(Modifier.height(8.dp))

        // Divider
        Box(
            modifier = Modifier
                .width(20.dp)
                .height(2.dp)
                .clip(CircleShape)
                .background(Color(0xFF49454F))
        )

        Spacer(Modifier.height(8.dp))

        // Manage mode
        ModeIcon(
            emoji = "🖥️",
            isActive = currentMode == AppMode.MANAGE,
            onClick = { onModeChange(AppMode.MANAGE) },
        )

        Spacer(Modifier.height(6.dp))

        // Launcher mode
        ModeIcon(
            emoji = "🎮",
            isActive = currentMode == AppMode.LAUNCHER,
            onClick = { onModeChange(AppMode.LAUNCHER) },
        )

        Spacer(Modifier.weight(1f))

        // Settings
        ModeIcon(
            emoji = "⚙",
            isActive = currentMode == AppMode.SETTINGS,
            onClick = { onModeChange(AppMode.SETTINGS) },
        )
    }
}

@Composable
private fun ModeIcon(
    emoji: String,
    isActive: Boolean,
    onClick: () -> Unit,
) {
    val bgColor = if (isActive) {
        MaterialTheme.colorScheme.surfaceContainer
    } else {
        Color.Transparent
    }

    Box(
        modifier = Modifier
            .size(40.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(bgColor)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = emoji,
            fontSize = 18.sp,
            color = if (isActive) {
                MaterialTheme.colorScheme.onSurface
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
            },
        )
    }
}
