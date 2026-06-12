package com.keshav.expensetracker.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

@Composable
fun FilterSection(
        availableSenders: Set<String>,
        selectedSenders: Set<String>,
        onToggleSender: (String) -> Unit,
        availableMerchants: Set<String>,
        selectedMerchants: Set<String>,
        onToggleMerchant: (String) -> Unit,
        onClearFilters: () -> Unit
) {
        Column(modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 4.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                                "Filters",
                                style = MaterialTheme.typography.titleMedium,
                                modifier = Modifier.weight(1f)
                        )
                        if (selectedSenders.isNotEmpty() || selectedMerchants.isNotEmpty()) {
                                TextButton(
                                        onClick = onClearFilters,
                                        colors =
                                                ButtonDefaults.textButtonColors(
                                                        contentColor =
                                                                MaterialTheme.colorScheme.error
                                                )
                                ) {
                                        Icon(
                                                Icons.Default.Clear,
                                                contentDescription = null,
                                                modifier = Modifier.size(16.dp)
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("Clear All")
                                }
                        }
                }

                Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                        FilterChipDropdown(
                                label = "Senders",
                                items = availableSenders.toList().sorted(),
                                selectedItems = selectedSenders,
                                onToggleItem = onToggleSender,
                                modifier = Modifier.weight(1f)
                        )
                        FilterChipDropdown(
                                label = "Merchants",
                                items = availableMerchants.toList().sorted(),
                                selectedItems = selectedMerchants,
                                onToggleItem = onToggleMerchant,
                                modifier = Modifier.weight(1f)
                        )
                }
        }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FilterChipDropdown(
        label: String,
        items: List<String>,
        selectedItems: Set<String>,
        onToggleItem: (String) -> Unit,
        modifier: Modifier = Modifier
) {
        var showDialog by remember { mutableStateOf(false) }

        OutlinedCard(
                modifier = modifier.clickable { showDialog = true },
                colors =
                        CardDefaults.outlinedCardColors(
                                containerColor = MaterialTheme.colorScheme.surface
                        )
        ) {
                Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                ) {
                        Column(modifier = Modifier.weight(1f)) {
                                Text(
                                        label,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.primary
                                )
                                if (selectedItems.isEmpty()) {
                                        Text(
                                                text = "All",
                                                style = MaterialTheme.typography.bodySmall,
                                                fontWeight = FontWeight.SemiBold,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                        )
                                } else {
                                        Row(
                                                modifier =
                                                        Modifier.fillMaxWidth().padding(top = 4.dp),
                                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                                        ) {
                                                selectedItems.take(2).forEach { item ->
                                                        Surface(
                                                                modifier = Modifier.width(60.dp),
                                                                shape =
                                                                        MaterialTheme.shapes
                                                                                .extraSmall,
                                                                color =
                                                                        MaterialTheme.colorScheme
                                                                                .primaryContainer,
                                                                contentColor =
                                                                        MaterialTheme.colorScheme
                                                                                .onPrimaryContainer
                                                        ) {
                                                                Text(
                                                                        text = item,
                                                                        style =
                                                                                MaterialTheme
                                                                                        .typography
                                                                                        .labelSmall,
                                                                        maxLines = 1,
                                                                        overflow =
                                                                                TextOverflow
                                                                                        .Ellipsis,
                                                                        modifier =
                                                                                Modifier.padding(
                                                                                        horizontal =
                                                                                                4.dp,
                                                                                        vertical =
                                                                                                2.dp
                                                                                )
                                                                )
                                                        }
                                                }
                                                if (selectedItems.size > 2) {
                                                        Text(
                                                                text = "+${selectedItems.size - 2}",
                                                                style =
                                                                        MaterialTheme.typography
                                                                                .labelSmall,
                                                                color =
                                                                        MaterialTheme.colorScheme
                                                                                .outline,
                                                                modifier =
                                                                        Modifier.align(
                                                                                Alignment
                                                                                        .CenterVertically
                                                                        )
                                                        )
                                                }
                                        }
                                }
                        }
                        Icon(
                                Icons.Default.Search,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp),
                                tint = MaterialTheme.colorScheme.outline
                        )
                }
        }

        if (showDialog) {
                var searchQuery by remember { mutableStateOf("") }
                val filteredList =
                        remember(searchQuery, items, selectedItems) {
                                items
                                        .filter { it.contains(searchQuery, ignoreCase = true) }
                                        .sortedWith(
                                                compareByDescending<String> {
                                                        selectedItems.contains(it)
                                                }
                                                        .thenBy { it }
                                        )
                        }

                AlertDialog(
                        onDismissRequest = { showDialog = false },
                        title = { Text("Select $label") },
                        text = {
                                Column(modifier = Modifier.fillMaxWidth().heightIn(max = 400.dp)) {
                                        CompactTextField(
                                                value = searchQuery,
                                                onValueChange = { searchQuery = it },
                                                placeholder = "Search $label...",
                                                modifier =
                                                        Modifier.fillMaxWidth()
                                                                .padding(bottom = 8.dp)
                                        )

                                        if (filteredList.isEmpty()) {
                                                Box(
                                                        modifier =
                                                                Modifier.fillMaxWidth()
                                                                        .height(100.dp),
                                                        contentAlignment = Alignment.Center
                                                ) {
                                                        Text(
                                                                "No items found",
                                                                color =
                                                                        MaterialTheme.colorScheme
                                                                                .outline
                                                        )
                                                }
                                        } else {
                                                LazyColumn(
                                                        modifier = Modifier.weight(1f, fill = false)
                                                ) {
                                                        items(filteredList) { item ->
                                                                Row(
                                                                        modifier =
                                                                                Modifier.fillMaxWidth()
                                                                                        .clickable {
                                                                                                onToggleItem(
                                                                                                        item
                                                                                                )
                                                                                        }
                                                                                        .padding(
                                                                                                vertical =
                                                                                                        4.dp
                                                                                        ),
                                                                        verticalAlignment =
                                                                                Alignment
                                                                                        .CenterVertically
                                                                ) {
                                                                        Checkbox(
                                                                                checked =
                                                                                        selectedItems
                                                                                                .contains(
                                                                                                        item
                                                                                                ),
                                                                                onCheckedChange = {
                                                                                        onToggleItem(
                                                                                                item
                                                                                        )
                                                                                }
                                                                        )
                                                                        Text(
                                                                                item,
                                                                                style =
                                                                                        MaterialTheme
                                                                                                .typography
                                                                                                .bodyMedium
                                                                        )
                                                                }
                                                        }
                                                }
                                        }
                                }
                        },
                        confirmButton = {
                                Button(onClick = { showDialog = false }) { Text("Done") }
                        }
                )
        }
}
