package dev.conduit.desktop.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SportsEsports
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.conduit.desktop.navigation.AppMode
import dev.conduit.desktop.ui.theme.*

@Composable
fun NavigationRail(
    currentMode: AppMode,
    onModeChange: (AppMode) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.width(72.dp).fillMaxHeight()
            .background(Brush.verticalGradient(listOf(NavRailBgTop, NavRailBgBottom)))
            .drawBehind {
                drawLine(NavRailBorder, Offset(size.width, 0f), Offset(size.width, size.height), 1.dp.toPx())
            }
            .padding(vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier.size(44.dp).clip(RoundedCornerShape(13.dp))
                .background(Brush.linearGradient(listOf(AccentBlue, AccentPurple))),
            contentAlignment = Alignment.Center,
        ) {
            Text("C", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight(800))
        }
        Spacer(Modifier.height(20.dp))
        NavItem(Icons.Default.Dns, "Servers", currentMode == AppMode.MANAGE) { onModeChange(AppMode.MANAGE) }
        Spacer(Modifier.height(6.dp))
        NavItem(Icons.Default.SportsEsports, "Launch", currentMode == AppMode.LAUNCHER) { onModeChange(AppMode.LAUNCHER) }
        Spacer(Modifier.height(6.dp))
        NavItem(Icons.Default.Settings, "Settings", currentMode == AppMode.SETTINGS) { onModeChange(AppMode.SETTINGS) }
        Spacer(Modifier.weight(1f))
    }
}

@Composable
private fun NavItem(icon: ImageVector, label: String, isSelected: Boolean, onClick: () -> Unit) {
    val interactionSource = remember { MutableInteractionSource() }
    val isHovered by interactionSource.collectIsHoveredAsState()
    val bgColor = when {
        isSelected -> NavRailSelectedBg
        isHovered -> Color.White.copy(alpha = 0.05f)
        else -> Color.Transparent
    }
    Column(
        modifier = Modifier.width(56.dp).clip(RoundedCornerShape(14.dp)).background(bgColor)
            .clickable(interactionSource = interactionSource, indication = null, onClick = onClick)
            .padding(top = 10.dp, bottom = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(icon, contentDescription = label, tint = if (isSelected) AccentBlue else NavRailUnselected, modifier = Modifier.size(22.dp))
        Spacer(Modifier.height(4.dp))
        Text(
            label, fontSize = 9.sp, letterSpacing = 0.3.sp,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
            color = if (isSelected) AccentBlue else NavRailUnselected,
        )
    }
}
