package dev.conduit.desktop.ui.components

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.TextUnitType
import dev.conduit.desktop.ui.theme.*

@Composable
fun ToastHost(toasts: List<ToastMessage>, onDismiss: (Long) -> Unit, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier.fillMaxSize().padding(bottom = 48.dp, end = 16.dp),
        contentAlignment = Alignment.BottomEnd,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            toasts.forEach { toast ->
                key(toast.id) {
                    val borderColor = when (toast.type) {
                        ToastType.Success -> StateRunning
                        ToastType.Error -> StateCrashed
                        ToastType.Warning -> StateInstalling
                    }
                    val icon = when (toast.type) {
                        ToastType.Success -> Icons.Default.CheckCircle
                        ToastType.Error -> Icons.Default.Error
                        ToastType.Warning -> Icons.Default.Warning
                    }
                    AnimatedVisibility(
                        visible = true,
                        enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
                        exit = slideOutVertically(targetOffsetY = { it }) + fadeOut(),
                    ) {
                        Row(
                            modifier = Modifier
                                .clip(RoundedCornerShape(10.dp))
                                .background(Surface)
                                .border(1.dp, borderColor.copy(alpha = 0.3f), RoundedCornerShape(10.dp))
                                .padding(horizontal = 16.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(icon, contentDescription = null, tint = borderColor, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(10.dp))
                            Text(
                                toast.text,
                                style = MaterialTheme.typography.bodySmall.copy(fontSize = TextUnit(12f, TextUnitType.Sp)),
                                color = borderColor,
                            )
                            Spacer(Modifier.width(12.dp))
                            IconButton(onClick = { onDismiss(toast.id) }, modifier = Modifier.size(16.dp)) {
                                Icon(Icons.Default.Close, contentDescription = "Dismiss", tint = TextMuted, modifier = Modifier.size(12.dp))
                            }
                        }
                    }
                }
            }
        }
    }
}
