package dev.conduit.desktop.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.unit.dp
import dev.conduit.desktop.ui.theme.*

@Composable
fun SearchBar(
    query: String,
    onQueryChange: (String) -> Unit,
    placeholder: String = "Search servers...",
    modifier: Modifier = Modifier,
) {
    var isFocused by remember { mutableStateOf(false) }
    val borderColor = if (isFocused) AccentBlue.copy(alpha = 0.3f) else Border
    val iconTint = if (isFocused) AccentBlue else TextMuted

    Row(
        modifier = modifier.fillMaxWidth().clip(RoundedCornerShape(6.dp)).background(Surface)
            .border(1.dp, borderColor, RoundedCornerShape(6.dp))
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Default.Search, contentDescription = null, tint = iconTint, modifier = Modifier.size(14.dp))
        Spacer(Modifier.width(6.dp))
        Box(modifier = Modifier.weight(1f)) {
            if (query.isEmpty() && !isFocused) Text(placeholder, color = TextMuted, style = MaterialTheme.typography.bodySmall)
            BasicTextField(
                value = query, onValueChange = onQueryChange,
                textStyle = MaterialTheme.typography.bodySmall.copy(color = TextPrimary),
                singleLine = true,
                modifier = Modifier.fillMaxWidth().onFocusChanged { isFocused = it.isFocused },
            )
        }
        if (query.isNotEmpty()) {
            IconButton(onClick = { onQueryChange("") }, modifier = Modifier.size(16.dp)) {
                Icon(Icons.Default.Close, contentDescription = "Clear", tint = TextMuted, modifier = Modifier.size(12.dp))
            }
        }
    }
}
