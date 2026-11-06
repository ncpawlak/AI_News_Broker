package com.ainewsbroker.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ainewsbroker.data.QueryNormalizer
import com.ainewsbroker.model.Category
import com.ainewsbroker.model.RefreshIntervalOptions
import com.ainewsbroker.model.SearchQuery
import com.ainewsbroker.model.ThemeMode
import com.ainewsbroker.platform.supportsBackgroundRefresh
import com.ainewsbroker.ui.theme.categoryAccent

@Composable
fun SettingsScreen(viewModel: SettingsViewModel, onBack: () -> Unit) {
    val queries by viewModel.queries.collectAsStateWithLifecycle()
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    var editing by remember { mutableStateOf<SearchQuery?>(null) }
    var confirmReset by remember { mutableStateOf(false) }
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
                },
                scrollBehavior = scrollBehavior,
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { editing = viewModel.newQuery(viewModel.categories.first().id) },
                icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                text = { Text("Add search") },
            )
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.TopCenter) {
            LazyColumn(
                modifier = Modifier.widthIn(max = 760.dp).fillMaxSize(),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 112.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                item { SectionTitle("Appearance") }
                item {
                    SettingCard {
                        Text("Theme", style = MaterialTheme.typography.titleSmall)
                        Spacer(Modifier.height(10.dp))
                        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                            ThemeMode.entries.forEachIndexed { i, mode ->
                                SegmentedButton(
                                    selected = settings.themeMode == mode,
                                    onClick = { viewModel.setTheme(mode) },
                                    shape = SegmentedButtonDefaults.itemShape(i, ThemeMode.entries.size),
                                ) { Text(mode.name) }
                            }
                        }
                    }
                }
                item { SectionTitle("Refresh") }
                item {
                    SettingCard {
                        Text("Check for new articles every", style = MaterialTheme.typography.titleSmall)
                        Spacer(Modifier.height(10.dp))
                        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                            RefreshIntervalOptions.forEachIndexed { i, h ->
                                SegmentedButton(
                                    selected = settings.refreshHours == h,
                                    onClick = { viewModel.setRefreshHours(h) },
                                    shape = SegmentedButtonDefaults.itemShape(i, RefreshIntervalOptions.size),
                                ) { Text("${h}h") }
                            }
                        }
                        Spacer(Modifier.height(8.dp))
                        Text(
                            if (supportsBackgroundRefresh) "Runs in the background when you're online."
                            else "In the browser, refresh runs while this tab is open.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                viewModel.categories.forEach { category ->
                    val inCategory = queries.filter { it.categoryId == category.id }
                    item(key = "cat-${category.id}") { SectionTitle(category.name, categoryAccent(category.id)) }
                    if (inCategory.isEmpty()) {
                        item(key = "empty-${category.id}") {
                            Text(
                                "No searches. Tap “Add search” to create one.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 4.dp),
                            )
                        }
                    }
                    items(inCategory, key = { it.id }) { q ->
                        QueryRow(
                            query = q,
                            onToggle = { viewModel.setEnabled(q.id, it) },
                            onEdit = { editing = q },
                            modifier = Modifier.animateItem(),
                        )
                    }
                }
                item {
                    Spacer(Modifier.height(12.dp))
                    TextButton(onClick = { confirmReset = true }) { Text("Reset searches to defaults") }
                }
            }
        }
    }

    editing?.let { q ->
        QueryEditorDialog(
            initial = q,
            categories = viewModel.categories,
            isNew = queries.none { it.id == q.id },
            onDismiss = { editing = null },
            onSave = { viewModel.save(it); editing = null },
            onDelete = { viewModel.delete(q.id); editing = null },
        )
    }

    if (confirmReset) {
        AlertDialog(
            onDismissRequest = { confirmReset = false },
            title = { Text("Reset searches?") },
            text = { Text("This replaces your searches with the built-in defaults. Custom searches and their articles are removed.") },
            confirmButton = { TextButton(onClick = { viewModel.resetDefaults(); confirmReset = false }) { Text("Reset") } },
            dismissButton = { TextButton(onClick = { confirmReset = false }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun SectionTitle(text: String, accent: androidx.compose.ui.graphics.Color? = null) {
    Row(Modifier.padding(start = 4.dp, top = 16.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        if (accent != null) {
            Box(Modifier.size(10.dp).clip(CircleShape).background(accent))
            Spacer(Modifier.width(8.dp))
        }
        Text(text, style = MaterialTheme.typography.titleMedium, color = accent ?: MaterialTheme.colorScheme.primary)
    }
}

@Composable
private fun SettingCard(content: @Composable () -> Unit) {
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerLow).padding(18.dp),
    ) { content() }
}

@Composable
private fun QueryRow(query: SearchQuery, onToggle: (Boolean) -> Unit, onEdit: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerLow)
            .clickable(onClick = onEdit)
            .padding(start = 18.dp, end = 12.dp, top = 14.dp, bottom = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(query.label, style = MaterialTheme.typography.titleSmall)
            Spacer(Modifier.height(4.dp))
            Text(
                query.rawQuery,
                style = MaterialTheme.typography.bodySmall,
                fontFamily = FontFamily.Monospace,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(Modifier.width(12.dp))
        Switch(checked = query.enabled, onCheckedChange = onToggle)
    }
}

@Composable
private fun QueryEditorDialog(
    initial: SearchQuery,
    categories: List<Category>,
    isNew: Boolean,
    onDismiss: () -> Unit,
    onSave: (SearchQuery) -> Unit,
    onDelete: () -> Unit,
) {
    var label by remember { mutableStateOf(initial.label) }
    var raw by remember { mutableStateOf(initial.rawQuery) }
    var categoryId by remember { mutableStateOf(initial.categoryId) }
    val valid = label.isNotBlank() && raw.isNotBlank()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (isNew) "New search" else "Edit search") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                    categories.forEachIndexed { i, c ->
                        SegmentedButton(
                            selected = categoryId == c.id,
                            onClick = { categoryId = c.id },
                            shape = SegmentedButtonDefaults.itemShape(i, categories.size),
                        ) { Text(c.name, maxLines = 1) }
                    }
                }
                OutlinedTextField(
                    value = label,
                    onValueChange = { label = it },
                    label = { Text("Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = raw,
                    onValueChange = { raw = it },
                    label = { Text("Search query") },
                    supportingText = { Text("Google Alerts syntax: \"quotes\", OR, AND, (groups)") },
                    minLines = 3,
                    modifier = Modifier.fillMaxWidth(),
                )
                if (raw.isNotBlank()) {
                    Text(
                        "Sent to Google News as: ${QueryNormalizer.normalize(raw)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (!isNew) {
                    TextButton(onClick = onDelete) {
                        Icon(Icons.Filled.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                        Spacer(Modifier.width(6.dp))
                        Text("Delete search", color = MaterialTheme.colorScheme.error)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = valid,
                onClick = { onSave(initial.copy(label = label.trim(), rawQuery = raw.trim(), categoryId = categoryId)) },
            ) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
