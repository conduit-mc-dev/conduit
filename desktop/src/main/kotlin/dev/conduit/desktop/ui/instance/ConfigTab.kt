package dev.conduit.desktop.ui.instance

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import dev.conduit.desktop.ui.components.*
import dev.conduit.desktop.ui.theme.*
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

@Composable
fun ConfigTab(
    instanceId: String,
    daemonId: String,
    viewModel: ConfigTabViewModel = koinViewModel { parametersOf(instanceId, daemonId) },
) {
    val state by viewModel.state.collectAsState()
    var editingKey by remember { mutableStateOf<String?>(null) }

    Column(modifier = Modifier.fillMaxSize().background(Background).padding(16.dp)) {
        // Toolbar
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (state.modifiedCount > 0) {
                Text(
                    "${state.modifiedCount} unsaved",
                    style = MaterialTheme.typography.bodySmall,
                    color = StateInstalling,
                )
            }
            Spacer(Modifier.weight(1f))
            ActionButton(
                "Revert All",
                ButtonVariant.Secondary,
                onClick = viewModel::revertAll,
                enabled = state.modifiedCount > 0,
            )
            ActionButton(
                "Save",
                ButtonVariant.Primary,
                onClick = viewModel::save,
                enabled = state.modifiedCount > 0 && !state.isSaving,
            )
        }
        Spacer(Modifier.height(12.dp))

        // Search
        SearchBar(
            query = state.searchQuery,
            onQueryChange = viewModel::updateSearch,
            placeholder = "Filter properties...",
        )
        Spacer(Modifier.height(12.dp))

        // Property list
        LazyColumn(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            items(state.filteredProperties, key = { it.key }) { prop ->
                val borderColor = if (prop.isModified) AccentBlue else Border
                Row(
                    modifier = Modifier.fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(Surface)
                        .border(1.dp, borderColor, RoundedCornerShape(8.dp))
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        prop.key,
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary,
                        modifier = Modifier.widthIn(max = 200.dp),
                    )
                    Spacer(Modifier.width(12.dp))

                    if (editingKey == prop.key) {
                        OutlinedTextField(
                            value = prop.currentValue,
                            onValueChange = { viewModel.updateProperty(prop.key, it) },
                            singleLine = true,
                            textStyle = MaterialTheme.typography.bodySmall.copy(
                                color = if (prop.isModified) AccentBlue else TextPrimary,
                            ),
                            modifier = Modifier.weight(1f),
                            shape = MaterialTheme.shapes.small,
                        )
                    } else {
                        Text(
                            if (prop.isModified) prop.originalValue else prop.currentValue,
                            style = MaterialTheme.typography.bodySmall.copy(
                                textDecoration = if (prop.isModified) TextDecoration.LineThrough else null,
                            ),
                            color = if (prop.isModified) TextMuted else TextPrimary,
                            modifier = Modifier.weight(1f),
                        )
                        if (prop.isModified) {
                            Text(
                                prop.currentValue,
                                style = MaterialTheme.typography.bodySmall,
                                color = AccentBlue,
                                modifier = Modifier.padding(start = 8.dp),
                            )
                        }
                    }

                    IconButton(
                        onClick = {
                            editingKey = if (editingKey == prop.key) null else prop.key
                        },
                        modifier = Modifier.size(24.dp),
                    ) {
                        Icon(
                            Icons.Default.Edit,
                            contentDescription = "Edit",
                            tint = TextMuted,
                            modifier = Modifier.size(14.dp),
                        )
                    }
                }
            }
        }
    }
}
