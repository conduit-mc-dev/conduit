package dev.conduit.desktop.ui.instance

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.*
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.conduit.desktop.ui.components.ActionButton
import dev.conduit.desktop.ui.components.ButtonVariant
import dev.conduit.desktop.ui.theme.*

@Composable
fun ConsoleTab(
    lines: List<String>,
    commandInput: String,
    onCommandChange: (String) -> Unit,
    onSendCommand: () -> Unit,
    isRunning: Boolean,
    modifier: Modifier = Modifier,
) {
    val listState = rememberLazyListState()
    LaunchedEffect(lines.size) {
        if (lines.isNotEmpty()) {
            listState.animateScrollToItem(lines.size - 1)
        }
    }

    Column(modifier = modifier.fillMaxSize()) {
        LazyColumn(
            state = listState,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .background(Background)
                .padding(horizontal = 12.dp, vertical = 8.dp),
        ) {
            items(lines) { line ->
                Text(
                    text = colorizeLogLine(line),
                    fontFamily = MonoFontFamily,
                    style = MaterialTheme.typography.bodySmall.copy(
                        lineHeight = (MaterialTheme.typography.bodySmall.fontSize.value * 1.7).sp,
                    ),
                    color = TextSecondary,
                )
            }
            if (lines.isEmpty()) {
                item {
                    Text(
                        "No console output yet.",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextMuted,
                        modifier = Modifier.padding(16.dp),
                    )
                }
            }
        }

        HorizontalDivider(color = Border)

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(Surface)
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            OutlinedTextField(
                value = commandInput,
                onValueChange = onCommandChange,
                placeholder = { Text("Type command...", color = TextMuted) },
                enabled = isRunning,
                singleLine = true,
                textStyle = MaterialTheme.typography.bodySmall.copy(
                    fontFamily = MonoFontFamily,
                    color = TextPrimary,
                ),
                modifier = Modifier
                    .weight(1f)
                    .onKeyEvent {
                        if (it.key == Key.Enter && it.type == KeyEventType.KeyUp) {
                            onSendCommand()
                            true
                        } else {
                            false
                        }
                    },
                shape = MaterialTheme.shapes.small,
            )
            Spacer(Modifier.width(8.dp))
            ActionButton(
                "Send",
                ButtonVariant.Primary,
                onClick = onSendCommand,
                enabled = isRunning,
            )
        }
    }
}

private fun colorizeLogLine(line: String) = buildAnnotatedString {
    when {
        line.contains("ERROR", ignoreCase = true) || line.contains("Exception") ->
            withStyle(SpanStyle(color = StateCrashed)) { append(line) }

        line.contains("WARN", ignoreCase = true) ->
            withStyle(SpanStyle(color = StateInstalling)) { append(line) }

        line.contains("Done") || line.contains("joined") ->
            withStyle(SpanStyle(color = StateRunning)) { append(line) }

        line.startsWith(">") ->
            withStyle(SpanStyle(color = AccentBlue)) { append(line) }

        else -> append(line)
    }
}
