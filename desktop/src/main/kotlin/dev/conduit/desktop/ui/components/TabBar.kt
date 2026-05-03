package dev.conduit.desktop.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.conduit.desktop.ui.theme.*

data class TabItem(val id: String, val label: String, val count: Int? = null)

@Composable
fun TabBar(tabs: List<TabItem>, selectedTabId: String, onTabSelected: (String) -> Unit, modifier: Modifier = Modifier) {
    Row(modifier = modifier.fillMaxWidth().background(Surface)
        .drawBehind {
            drawLine(Border, Offset(0f, size.height), Offset(size.width, size.height), 1.dp.toPx())
        }
        .padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
        tabs.forEach { tab ->
            val interactionSource = remember { MutableInteractionSource() }
            val isHovered by interactionSource.collectIsHoveredAsState()
            val isSelected = tab.id == selectedTabId
            val textColor by animateColorAsState(
                targetValue = when { isSelected -> AccentBlue; isHovered -> TextPrimary; else -> TextSecondary },
                animationSpec = tween(200), label = "tabColor",
            )
            Column(
                modifier = Modifier.clickable(interactionSource = interactionSource, indication = null) { onTabSelected(tab.id) }
                    .hoverable(interactionSource)
                    .drawBehind {
                        if (isSelected) {
                            drawLine(AccentBlue, Offset(0f, size.height), Offset(size.width, size.height), 2.dp.toPx())
                        }
                    }
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(tab.label, style = MaterialTheme.typography.bodySmall.copy(
                        fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal
                    ), color = textColor)
                    if (tab.count != null) {
                        Spacer(Modifier.width(4.dp))
                        Text("(${tab.count})", style = MaterialTheme.typography.bodySmall, color = TextMuted)
                    }
                }
                Spacer(Modifier.height(2.dp))
            }
        }
    }
}
