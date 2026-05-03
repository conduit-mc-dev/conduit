package dev.conduit.desktop.ui.instance

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import dev.conduit.desktop.ui.theme.*

@Composable
fun PlayersTab(
    playerCount: Int,
    maxPlayers: Int,
    playerNames: List<String>,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Background)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        // Stats card
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .background(Surface)
                .border(1.dp, Border, RoundedCornerShape(10.dp))
                .padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(32.dp),
        ) {
            Column {
                Text("Online", style = MaterialTheme.typography.labelMedium, color = TextSecondary)
                Text(
                    "$playerCount",
                    style = MaterialTheme.typography.headlineLarge,
                    color = StateRunning,
                )
            }
            Column {
                Text(
                    "Max Players",
                    style = MaterialTheme.typography.labelMedium,
                    color = TextSecondary,
                )
                Text(
                    "$maxPlayers",
                    style = MaterialTheme.typography.headlineLarge,
                    color = TextMuted,
                )
            }
        }

        // Player list
        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(playerNames) { name ->
                PlayerCard(name)
            }
            if (playerNames.isEmpty()) {
                item {
                    Text(
                        "No players online.",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextMuted,
                        modifier = Modifier.padding(vertical = 24.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun PlayerCard(name: String) {
    val colors = listOf(AccentBlue, AccentPurple, StateRunning, StateInstalling, StateStopping)
    val initialColor = colors[name.hashCode().and(0x7FFFFFFF) % colors.size]

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(Surface)
            .border(1.dp, Border, RoundedCornerShape(10.dp))
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            modifier = Modifier
                .size(32.dp)
                .clip(CircleShape)
                .background(initialColor.copy(alpha = 0.2f)),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                name.firstOrNull()?.uppercase() ?: "?",
                style = MaterialTheme.typography.titleSmall,
                color = initialColor,
            )
        }
        Text(
            name,
            style = MaterialTheme.typography.bodyMedium,
            color = TextPrimary,
            modifier = Modifier.weight(1f),
        )
        Box(
            Modifier
                .size(6.dp)
                .clip(CircleShape)
                .background(StateRunning),
        )
    }
}
