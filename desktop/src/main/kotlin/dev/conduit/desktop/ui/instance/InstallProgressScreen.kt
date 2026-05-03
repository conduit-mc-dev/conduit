package dev.conduit.desktop.ui.instance

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.unit.dp
import dev.conduit.desktop.ui.components.ActionButton
import dev.conduit.desktop.ui.components.ButtonVariant
import dev.conduit.desktop.ui.theme.*

@Composable
fun InstallProgressScreen(
    instanceId: String,
    daemonId: String,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Background)
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(24.dp),
    ) {
        Spacer(Modifier.height(48.dp))

        Column(
            modifier = Modifier
                .widthIn(max = 480.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(Surface)
                .border(1.dp, Border, RoundedCornerShape(14.dp))
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text("Installing Server", style = MaterialTheme.typography.headlineMedium, color = TextPrimary)

            Text(
                "0%",
                style = MaterialTheme.typography.headlineLarge.copy(
                    fontSize = MaterialTheme.typography.headlineLarge.fontSize * 2,
                ),
                color = StateInstalling,
            )

            // Progress bar
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(Elevated),
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(0f)
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(4.dp))
                        .background(
                            Brush.horizontalGradient(
                                listOf(ProgressInstallingStart, ProgressInstallingEnd),
                            ),
                        ),
                )
            }

            Text("Waiting for progress...", style = MaterialTheme.typography.bodySmall, color = TextMuted)
        }

        ActionButton("Cancel", ButtonVariant.Warning, onClick = { /* TODO */ })
    }
}
