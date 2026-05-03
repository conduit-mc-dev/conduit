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

enum class ButtonVariant { Primary, Secondary, Danger, Warning }

@Composable
fun ActionButton(
    text: String,
    variant: ButtonVariant,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val shape = RoundedCornerShape(6.dp)
    when (variant) {
        ButtonVariant.Primary -> Button(
            onClick = onClick, enabled = enabled, modifier = modifier, shape = shape,
            colors = ButtonDefaults.buttonColors(
                containerColor = ButtonPrimary, contentColor = ButtonPrimaryText,
                disabledContainerColor = ButtonDisabled, disabledContentColor = ButtonDisabledText,
            ),
        ) { Text(text, style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp, fontWeight = FontWeight.SemiBold)) }

        ButtonVariant.Secondary -> Button(
            onClick = onClick, enabled = enabled, modifier = modifier, shape = shape,
            colors = ButtonDefaults.buttonColors(
                containerColor = ButtonSecondary, contentColor = ButtonSecondaryText,
                disabledContainerColor = ButtonDisabled, disabledContentColor = ButtonDisabledText,
            ),
        ) { Text(text, style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp, fontWeight = FontWeight.SemiBold)) }

        ButtonVariant.Danger -> OutlinedButton(
            onClick = onClick, enabled = enabled, modifier = modifier, shape = shape,
            border = ButtonDefaults.outlinedButtonBorder(enabled).copy(brush = SolidColor(ButtonDanger)),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = ButtonDanger, disabledContentColor = ButtonDisabledText),
        ) { Text(text, style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp, fontWeight = FontWeight.SemiBold)) }

        ButtonVariant.Warning -> Button(
            onClick = onClick, enabled = enabled, modifier = modifier, shape = shape,
            colors = ButtonDefaults.buttonColors(
                containerColor = ButtonWarning, contentColor = ButtonPrimaryText,
                disabledContainerColor = ButtonDisabled, disabledContentColor = ButtonDisabledText,
            ),
        ) { Text(text, style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp, fontWeight = FontWeight.SemiBold)) }
    }
}

@Composable
fun InstanceActionButtons(
    state: InstanceState,
    isActionInProgress: Boolean,
    onStart: () -> Unit,
    onStop: () -> Unit,
    onKill: () -> Unit,
    onDelete: () -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(modifier = modifier, horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
        when (state) {
            InstanceState.STOPPED -> {
                ActionButton("Start", ButtonVariant.Primary, onStart, enabled = !isActionInProgress)
                Spacer(Modifier.weight(1f))
                ActionButton("Delete", ButtonVariant.Danger, onDelete, enabled = !isActionInProgress)
            }
            InstanceState.STARTING -> {
                ActionButton("Stop", ButtonVariant.Primary, onStop, enabled = false)
                ActionButton("Kill", ButtonVariant.Secondary, onKill, enabled = false)
                Spacer(Modifier.weight(1f))
                ActionButton("Cancel", ButtonVariant.Warning, onCancel, enabled = !isActionInProgress)
            }
            InstanceState.RUNNING -> {
                ActionButton("Stop", ButtonVariant.Primary, onStop, enabled = !isActionInProgress)
                ActionButton("Kill", ButtonVariant.Secondary, onKill, enabled = !isActionInProgress)
            }
            InstanceState.STOPPING -> {
                ActionButton("Stop", ButtonVariant.Primary, onStop, enabled = false)
                ActionButton("Kill", ButtonVariant.Secondary, onKill, enabled = !isActionInProgress)
            }
            InstanceState.CRASHED -> {
                ActionButton("Start", ButtonVariant.Primary, onStart, enabled = !isActionInProgress)
                ActionButton("Kill", ButtonVariant.Secondary, onKill, enabled = !isActionInProgress)
                Spacer(Modifier.weight(1f))
                ActionButton("Delete", ButtonVariant.Danger, onDelete, enabled = !isActionInProgress)
            }
            InstanceState.INITIALIZING -> {
                Spacer(Modifier.weight(1f))
                ActionButton("Cancel", ButtonVariant.Warning, onCancel, enabled = !isActionInProgress)
            }
        }
    }
}
