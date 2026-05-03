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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
        HorizontalDivider(color = Border, modifier = Modifier.padding(top = 12.dp))
        Spacer(Modifier.height(8.dp))

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
                val borderColor = if (prop.isModified) AccentBlue.copy(alpha = 0.3f) else Border
                Row(
                    modifier = Modifier.fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(Surface)
                        .border(1.dp, borderColor, RoundedCornerShape(10.dp))
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        prop.key,
                        style = MaterialTheme.typography.bodySmall.copy(fontFamily = MonoFontFamily),
                        color = TextPrimary,
                        modifier = Modifier.widthIn(min = 160.dp, max = 240.dp),
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
                                fontFamily = MonoFontFamily,
                                textDecoration = if (prop.isModified) TextDecoration.LineThrough else null,
                            ),
                            color = if (prop.isModified) TextMuted else TextSecondary,
                            modifier = Modifier.widthIn(min = 160.dp, max = 240.dp),
                        )
                        if (prop.isModified) {
                            Text(" → ", style = MaterialTheme.typography.bodySmall, color = AccentBlue)
                            Text(
                                prop.currentValue,
                                style = MaterialTheme.typography.bodySmall.copy(fontSize = 13.sp, fontWeight = FontWeight.SemiBold, fontFamily = MonoFontFamily),
                                color = AccentBlue,
                            )
                        }
                    }

                    IconButton(
                        onClick = {
                            editingKey = if (editingKey == prop.key) null else prop.key
                        },
                        modifier = Modifier.size(28.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(Border),
                    ) {
                        Icon(
                            Icons.Default.Edit,
                            contentDescription = "Edit",
                            tint = TextMuted,
                            modifier = Modifier.size(12.dp),
                        )
                    }
                }
            }
        }
    }
}
