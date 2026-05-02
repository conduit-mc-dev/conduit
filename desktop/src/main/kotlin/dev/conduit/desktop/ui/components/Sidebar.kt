package dev.conduit.desktop.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SportsEsports
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.conduit.desktop.navigation.AppMode

@Composable
fun Sidebar(
    currentMode: AppMode,
    appIcon: Painter,
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
        Image(
            painter = appIcon,
            contentDescription = "Conduit MC",
            modifier = Modifier
                .size(36.dp)
                .clip(RoundedCornerShape(10.dp)),
            contentScale = ContentScale.Fit,
        )

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
            icon = Icons.Default.Dns,
            label = "管理",
            isActive = currentMode == AppMode.MANAGE,
            onClick = { onModeChange(AppMode.MANAGE) },
        )

        Spacer(Modifier.height(6.dp))

        // Launcher mode
        ModeIcon(
            icon = Icons.Default.SportsEsports,
            label = "启动器",
            isActive = currentMode == AppMode.LAUNCHER,
            onClick = { onModeChange(AppMode.LAUNCHER) },
        )

        Spacer(Modifier.weight(1f))

        // Settings
        ModeIcon(
            icon = Icons.Default.Settings,
            label = "设置",
            isActive = currentMode == AppMode.SETTINGS,
            onClick = { onModeChange(AppMode.SETTINGS) },
        )
    }
}

@Composable
private fun ModeIcon(
    icon: ImageVector,
    label: String,
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
            .clip(RoundedCornerShape(16.dp))
            .background(bgColor)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            modifier = Modifier.size(22.dp),
            tint = if (isActive) {
                MaterialTheme.colorScheme.onSurface
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
            },
        )
    }
}
