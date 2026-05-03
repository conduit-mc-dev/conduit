package dev.conduit.desktop.ui.launch

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.SportsEsports
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import dev.conduit.desktop.ui.components.*
import dev.conduit.desktop.ui.theme.*

@Composable
fun LaunchEmptyScreen(onCreateInstance: () -> Unit, onConnectServer: () -> Unit, modifier: Modifier = Modifier) {
    Column(modifier = modifier.fillMaxSize().background(Background).padding(40.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Box(modifier = Modifier.size(80.dp).clip(RoundedCornerShape(20.dp)).background(Surface).border(1.dp, Border, RoundedCornerShape(20.dp)), contentAlignment = Alignment.Center) {
            Icon(Icons.Default.SportsEsports, contentDescription = null, tint = Elevated, modifier = Modifier.size(40.dp))
        }
        Spacer(Modifier.height(16.dp))
        Text("Welcome to Conduit", style = MaterialTheme.typography.headlineLarge, color = TextPrimary)
        Spacer(Modifier.height(8.dp))
        Text("Create a new Minecraft server or connect to an existing one to get started.",
            style = MaterialTheme.typography.bodyMedium, color = TextSecondary, textAlign = TextAlign.Center, modifier = Modifier.widthIn(max = 360.dp))
        Spacer(Modifier.height(16.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            ActionButton("Create Instance", ButtonVariant.Primary, onClick = onCreateInstance)
            ActionButton("Connect Server", ButtonVariant.Secondary, onClick = onConnectServer)
        }
        Spacer(Modifier.height(12.dp))
        Text("Quick start", style = MaterialTheme.typography.labelMedium, color = TextMuted)
        Spacer(Modifier.height(4.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("NeoForge", "Fabric", "Vanilla").forEach { chip ->
                Box(modifier = Modifier.clip(RoundedCornerShape(8.dp)).background(Surface).border(1.dp, Border, RoundedCornerShape(8.dp))
                    .clickable { onCreateInstance() }.padding(horizontal = 16.dp, vertical = 8.dp)) {
                    Text(chip, style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                }
            }
        }
    }
}
