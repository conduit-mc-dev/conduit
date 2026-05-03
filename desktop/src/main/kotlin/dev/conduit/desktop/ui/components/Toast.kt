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
import dev.conduit.desktop.ui.theme.*
import kotlinx.coroutines.delay

enum class ToastType { Success, Error, Warning }
data class ToastMessage(val type: ToastType, val text: String, val id: Long = System.currentTimeMillis())

@Composable
fun ToastHost(currentToast: ToastMessage?, onDismiss: () -> Unit, modifier: Modifier = Modifier) {
    LaunchedEffect(currentToast) {
        if (currentToast != null) { delay(3000); onDismiss() }
    }
    Box(modifier = modifier.fillMaxSize().padding(bottom = 48.dp, end = 24.dp), contentAlignment = Alignment.BottomEnd) {
        AnimatedVisibility(
            visible = currentToast != null,
            enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
            exit = slideOutVertically(targetOffsetY = { it }) + fadeOut(),
        ) {
            currentToast?.let { toast ->
                val borderColor = when (toast.type) { ToastType.Success -> StateRunning; ToastType.Error -> StateCrashed; ToastType.Warning -> StateInstalling }
                val icon = when (toast.type) { ToastType.Success -> Icons.Default.CheckCircle; ToastType.Error -> Icons.Default.Error; ToastType.Warning -> Icons.Default.Warning }
                Row(
                    modifier = Modifier.clip(RoundedCornerShape(10.dp)).background(Surface)
                        .border(1.dp, borderColor, RoundedCornerShape(10.dp))
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(icon, contentDescription = null, tint = borderColor, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(10.dp))
                    Text(toast.text, style = MaterialTheme.typography.bodySmall, color = TextPrimary)
                    Spacer(Modifier.width(12.dp))
                    IconButton(onClick = onDismiss, modifier = Modifier.size(16.dp)) {
                        Icon(Icons.Default.Close, contentDescription = "Dismiss", tint = TextMuted, modifier = Modifier.size(12.dp))
                    }
                }
            }
        }
    }
}
