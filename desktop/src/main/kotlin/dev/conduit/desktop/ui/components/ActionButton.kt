package dev.conduit.desktop.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.conduit.core.model.InstanceState
import dev.conduit.desktop.ui.theme.*
import androidx.compose.foundation.BorderStroke

enum class ButtonVariant { Success, Default, Muted, Danger, Attention }

@Composable
fun ActionButton(
    text: String,
    variant: ButtonVariant,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val shape = RoundedCornerShape(6.dp)
    val textStyle = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
    val borderWidth = 1.dp
    val disabledBorder = BorderStroke(borderWidth, SolidColor(ButtonDisabledBorder))

    when (variant) {
        ButtonVariant.Success -> OutlinedButton(
            onClick = onClick, enabled = enabled, modifier = modifier, shape = shape,
            border = if (enabled) BorderStroke(borderWidth, SolidColor(ButtonStartBorder)) else disabledBorder,
            colors = ButtonDefaults.outlinedButtonColors(
                contentColor = ButtonStart, disabledContentColor = ButtonDisabledText,
            ),
        ) { Text(text, style = textStyle) }

        ButtonVariant.Default -> OutlinedButton(
            onClick = onClick, enabled = enabled, modifier = modifier, shape = shape,
            border = if (enabled) BorderStroke(borderWidth, SolidColor(ButtonStopBorder)) else disabledBorder,
            colors = ButtonDefaults.outlinedButtonColors(
                contentColor = ButtonStopText, disabledContentColor = ButtonDisabledText,
            ),
        ) { Text(text, style = textStyle) }

        ButtonVariant.Muted -> OutlinedButton(
            onClick = onClick, enabled = enabled, modifier = modifier, shape = shape,
            border = if (enabled) BorderStroke(borderWidth, SolidColor(ButtonKillBorder)) else disabledBorder,
            colors = ButtonDefaults.outlinedButtonColors(
                contentColor = ButtonKillText, disabledContentColor = ButtonDisabledText,
            ),
        ) { Text(text, style = textStyle) }

        ButtonVariant.Danger -> OutlinedButton(
            onClick = onClick, enabled = enabled, modifier = modifier, shape = shape,
            border = if (enabled) BorderStroke(borderWidth, SolidColor(ButtonDangerBorder)) else disabledBorder,
            colors = ButtonDefaults.outlinedButtonColors(
                contentColor = ButtonDanger, disabledContentColor = ButtonDisabledText,
            ),
        ) { Text(text, style = textStyle) }

        ButtonVariant.Attention -> OutlinedButton(
            onClick = onClick, enabled = enabled, modifier = modifier, shape = shape,
            border = if (enabled) BorderStroke(borderWidth, SolidColor(ButtonWarningBorder)) else disabledBorder,
            colors = ButtonDefaults.outlinedButtonColors(
                contentColor = ButtonWarning, disabledContentColor = ButtonDisabledText,
            ),
        ) { Text(text, style = textStyle) }
    }
}

@Composable
fun InstanceActionButtons(
    state: InstanceState,
    isActionInProgress: Boolean,
    onStart: () -> Unit,
    onStop: () -> Unit,
    onRestart: () -> Unit,
    onKill: () -> Unit,
    onDelete: () -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(modifier = modifier, horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
        when (state) {
            InstanceState.STOPPED -> {
                ActionButton("Start", ButtonVariant.Success, onStart, enabled = !isActionInProgress)
                Spacer(Modifier.weight(1f))
                ActionButton("Delete", ButtonVariant.Danger, onDelete, enabled = !isActionInProgress)
            }
            InstanceState.STARTING -> {
                ActionButton("Stop", ButtonVariant.Default, onStop, enabled = false)
                ActionButton("Kill", ButtonVariant.Muted, onKill, enabled = false)
                Spacer(Modifier.weight(1f))
                ActionButton("Cancel", ButtonVariant.Attention, onCancel, enabled = !isActionInProgress)
            }
            InstanceState.RUNNING -> {
                ActionButton("Restart", ButtonVariant.Success, onRestart, enabled = !isActionInProgress)
                ActionButton("Stop", ButtonVariant.Default, onStop, enabled = !isActionInProgress)
                ActionButton("Kill", ButtonVariant.Muted, onKill, enabled = !isActionInProgress)
            }
            InstanceState.STOPPING -> {
                ActionButton("Stop", ButtonVariant.Default, onStop, enabled = false)
                ActionButton("Kill", ButtonVariant.Muted, onKill, enabled = !isActionInProgress)
            }
            InstanceState.CRASHED -> {
                ActionButton("Start", ButtonVariant.Success, onStart, enabled = !isActionInProgress)
                ActionButton("Kill", ButtonVariant.Muted, onKill, enabled = !isActionInProgress)
                Spacer(Modifier.weight(1f))
                ActionButton("Delete", ButtonVariant.Danger, onDelete, enabled = !isActionInProgress)
            }
            InstanceState.INITIALIZING -> {
                Spacer(Modifier.weight(1f))
                ActionButton("Cancel", ButtonVariant.Attention, onCancel, enabled = !isActionInProgress)
            }
        }
    }
}
