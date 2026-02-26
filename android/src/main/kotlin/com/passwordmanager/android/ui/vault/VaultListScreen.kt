package com.passwordmanager.android.ui.vault

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.ui.graphics.Color
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.hilt.navigation.compose.hiltViewModel
import com.passwordmanager.android.R
import com.passwordmanager.android.ui.components.ConfirmDialog
import com.passwordmanager.android.ui.components.EntryCard
import com.passwordmanager.android.ui.components.ExportDialog
import com.passwordmanager.android.ui.components.ImportDialog
import com.passwordmanager.android.update.AndroidUpdateManager
import com.passwordmanager.update.UpdateInfo
import com.passwordmanager.vault.SortField

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VaultListScreen(
    onEntryClick: (String) -> Unit,
    onNewEntry: () -> Unit,
    onLock: () -> Unit,
    viewModel: VaultListViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    // Dialog states
    var showImportDialog by remember { mutableStateOf(false) }
    var showExportDialog by remember { mutableStateOf(false) }
    var entryToDelete by remember { mutableStateOf<String?>(null) }
    var showBulkCategoryDialog by remember { mutableStateOf(false) }
    var showBulkDeleteDialog by remember { mutableStateOf(false) }

    // For encrypted import: store password until file is picked
    var pendingEncPassword by remember { mutableStateOf<CharArray?>(null) }
    var pendingEncImportUri by remember { mutableStateOf<android.net.Uri?>(null) }

    // Import/Export launchers
    val importCsvLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri -> uri?.let { viewModel.importCsv(it) } }

    val importJsonLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri -> uri?.let { viewModel.importJson(it) } }

    val importEncLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri -> uri?.let { pendingEncImportUri = it } }

    val exportCsvLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("text/csv")
    ) { uri -> uri?.let { viewModel.exportCsv(it) } }

    val exportJsonLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri -> uri?.let { viewModel.exportJson(it) } }

    val exportBackupLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/octet-stream")
    ) { uri -> uri?.let { viewModel.exportBackup(it) } }

    // When both URI and password are available, perform import
    LaunchedEffect(pendingEncImportUri, pendingEncPassword) {
        val uri = pendingEncImportUri
        val pwd = pendingEncPassword
        if (uri != null && pwd != null) {
            viewModel.importEncryptedVault(uri, pwd)
            pendingEncImportUri = null
            pendingEncPassword = null
        }
    }

    // Message strings for snackbar
    val importSuccessTemplate = stringResource(R.string.import_success)
    val importErrorStr = stringResource(R.string.import_error)
    val exportSuccessStr = stringResource(R.string.export_success)
    val exportErrorStr = stringResource(R.string.export_error)
    val passwordCopiedStr = stringResource(R.string.password_copied)
    val syncSuccessStr = stringResource(R.string.sync_success)
    val syncErrorStr = stringResource(R.string.sync_error)

    // Handle messages
    LaunchedEffect(state.message) {
        state.message?.let { msg ->
            val text = when {
                msg.startsWith("import_success:") -> {
                    val count = msg.substringAfter(":").toIntOrNull() ?: 0
                    importSuccessTemplate.replace("%1\$d", count.toString())
                }
                msg == "import_error" -> importErrorStr
                msg == "export_success" -> exportSuccessStr
                msg == "export_error" -> exportErrorStr
                msg == "password_copied" -> passwordCopiedStr
                msg == "sync_success" -> syncSuccessStr
                msg == "sync_error" -> syncErrorStr
                else -> msg
            }
            snackbarHostState.showSnackbar(text)
            viewModel.clearMessage()
        }
    }

    // Refresh when returning from edit/detail
    LaunchedEffect(Unit) { viewModel.refreshEntries() }

    // Update check
    val context = androidx.compose.ui.platform.LocalContext.current
    var updateInfo by remember { mutableStateOf<UpdateInfo?>(null) }
    LaunchedEffect(Unit) {
        updateInfo = AndroidUpdateManager.checkForUpdate()
    }
    if (updateInfo != null) {
        AlertDialog(
            onDismissRequest = { updateInfo = null },
            title = { Text(stringResource(R.string.update_check)) },
            text = {
                Text(
                    stringResource(R.string.update_available)
                        .replace("%1\$s", updateInfo!!.version)
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    AndroidUpdateManager.openReleasePage(context, updateInfo!!)
                    updateInfo = null
                }) {
                    Text(stringResource(R.string.update_download))
                }
            },
            dismissButton = {
                TextButton(onClick = { updateInfo = null }) {
                    Text(stringResource(R.string.update_dismiss))
                }
            }
        )
    }

    var menuExpanded by remember { mutableStateOf(false) }
    var sortMenuExpanded by remember { mutableStateOf(false) }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            if (state.isSelectionMode) {
                // Selection mode top bar
                TopAppBar(
                    title = {
                        Text(
                            stringResource(R.string.vault_selected_count)
                                .replace("%1\$d", state.selectedEntryIds.size.toString())
                        )
                    },
                    navigationIcon = {
                        IconButton(onClick = { viewModel.clearSelection() }) {
                            Icon(Icons.Default.Close, contentDescription = stringResource(R.string.common_cancel))
                        }
                    },
                    actions = {
                        IconButton(onClick = { viewModel.selectAll() }) {
                            Icon(Icons.Default.SelectAll, contentDescription = stringResource(R.string.vault_select_all))
                        }
                    }
                )
            } else if (state.isSearchActive) {
                SearchBar(
                    query = state.searchQuery,
                    onQueryChange = { viewModel.setSearchQuery(it) },
                    onClose = { viewModel.toggleSearch() }
                )
            } else {
                TopAppBar(
                    title = {
                        Text(
                            "${stringResource(R.string.app_title)} — ${state.entries.size} ${stringResource(R.string.vault_entries)}"
                        )
                    },
                    actions = {
                        IconButton(onClick = { viewModel.toggleSearch() }) {
                            Icon(Icons.Default.Search, contentDescription = stringResource(R.string.vault_search))
                        }

                        // Sort menu
                        Box {
                            IconButton(onClick = { sortMenuExpanded = true }) {
                                Icon(Icons.Default.Sort, contentDescription = stringResource(R.string.vault_sort))
                            }
                            DropdownMenu(
                                expanded = sortMenuExpanded,
                                onDismissRequest = { sortMenuExpanded = false }
                            ) {
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.menu_sort_name)) },
                                    onClick = {
                                        viewModel.setSortField(SortField.TITLE)
                                        sortMenuExpanded = false
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.menu_sort_username)) },
                                    onClick = {
                                        viewModel.setSortField(SortField.USERNAME)
                                        sortMenuExpanded = false
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.menu_sort_email)) },
                                    onClick = {
                                        viewModel.setSortField(SortField.EMAIL)
                                        sortMenuExpanded = false
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.menu_sort_pseudo)) },
                                    onClick = {
                                        viewModel.setSortField(SortField.PSEUDO)
                                        sortMenuExpanded = false
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.menu_sort_url)) },
                                    onClick = {
                                        viewModel.setSortField(SortField.URL)
                                        sortMenuExpanded = false
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.menu_sort_date)) },
                                    onClick = {
                                        viewModel.setSortField(SortField.DATE)
                                        sortMenuExpanded = false
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.menu_sort_category)) },
                                    onClick = {
                                        viewModel.setSortField(SortField.CATEGORY)
                                        sortMenuExpanded = false
                                    }
                                )
                            }
                        }

                        // Overflow menu
                        Box {
                            IconButton(onClick = { menuExpanded = true }) {
                                Icon(Icons.Default.MoreVert, contentDescription = stringResource(R.string.vault_menu))
                            }
                            DropdownMenu(
                                expanded = menuExpanded,
                                onDismissRequest = { menuExpanded = false }
                            ) {
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.menu_import)) },
                                    onClick = {
                                        menuExpanded = false
                                        showImportDialog = true
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.menu_export)) },
                                    onClick = {
                                        menuExpanded = false
                                        showExportDialog = true
                                    }
                                )
                                HorizontalDivider()
                                DropdownMenuItem(
                                    leadingIcon = { Icon(Icons.Default.Sync, contentDescription = null) },
                                    text = { Text(stringResource(R.string.menu_sync_now)) },
                                    onClick = {
                                        menuExpanded = false
                                        viewModel.syncNow()
                                    }
                                )
                                HorizontalDivider()
                                DropdownMenuItem(
                                    leadingIcon = { Icon(Icons.Default.Lock, contentDescription = null) },
                                    text = { Text(stringResource(R.string.menu_lock)) },
                                    onClick = { menuExpanded = false; onLock() }
                                )
                            }
                        }
                    }
                )
            }
        },
        floatingActionButton = {
            if (!state.isSelectionMode) {
                FloatingActionButton(onClick = onNewEntry) {
                    Icon(Icons.Default.Add, contentDescription = stringResource(R.string.vault_new_entry))
                }
            }
        },
        bottomBar = {
            // Bulk change category button in selection mode
            AnimatedVisibility(
                visible = state.isSelectionMode && state.selectedEntryIds.isNotEmpty(),
                enter = slideInVertically { it },
                exit = slideOutVertically { it }
            ) {
                Surface(tonalElevation = 3.dp) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Button(
                            onClick = { showBulkCategoryDialog = true },
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Default.Label, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                stringResource(R.string.vault_change_category),
                                maxLines = 1,
                                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                            )
                        }
                        Button(
                            onClick = { showBulkDeleteDialog = true },
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.error
                            )
                        ) {
                            Icon(Icons.Default.Delete, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                stringResource(R.string.common_delete),
                                maxLines = 1,
                                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // Category dropdown filter
            if (state.categories.isNotEmpty()) {
                var categoryExpanded by remember { mutableStateOf(false) }
                val selectedLabel = state.selectedCategory
                    ?: stringResource(R.string.category_all)

                Box(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                    ExposedDropdownMenuBox(
                        expanded = categoryExpanded,
                        onExpandedChange = { categoryExpanded = it }
                    ) {
                        OutlinedTextField(
                            value = selectedLabel,
                            onValueChange = {},
                            readOnly = true,
                            label = { Text(stringResource(R.string.entry_category)) },
                            trailingIcon = {
                                ExposedDropdownMenuDefaults.TrailingIcon(expanded = categoryExpanded)
                            },
                            modifier = Modifier.fillMaxWidth().menuAnchor(),
                            singleLine = true
                        )
                        ExposedDropdownMenu(
                            expanded = categoryExpanded,
                            onDismissRequest = { categoryExpanded = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.category_all)) },
                                onClick = {
                                    viewModel.selectCategory(null)
                                    categoryExpanded = false
                                }
                            )
                            state.categories.forEach { category ->
                                DropdownMenuItem(
                                    text = { Text(category) },
                                    onClick = {
                                        viewModel.selectCategory(category)
                                        categoryExpanded = false
                                    }
                                )
                            }
                        }
                    }
                }
            }

            // Favorites filter chip
            Row(
                modifier = Modifier.padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterChip(
                    selected = state.favoritesOnly,
                    onClick = { viewModel.toggleFavoritesFilter() },
                    label = { Text(stringResource(R.string.entry_favorite)) },
                    leadingIcon = {
                        Icon(
                            if (state.favoritesOnly) Icons.Default.Star else Icons.Default.StarBorder,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                )
            }
            Spacer(modifier = Modifier.height(8.dp))

            // Entry list or empty state
            if (state.entries.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        if (state.isSearchActive || state.searchQuery.isNotBlank()) {
                            Icon(
                                imageVector = Icons.Default.SearchOff,
                                contentDescription = null,
                                modifier = Modifier.size(64.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                text = stringResource(R.string.vault_search_empty),
                                style = MaterialTheme.typography.titleMedium,
                                textAlign = TextAlign.Center
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = stringResource(R.string.vault_search_empty_subtitle),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Default.LockOpen,
                                contentDescription = null,
                                modifier = Modifier.size(64.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                text = stringResource(R.string.vault_empty),
                                style = MaterialTheme.typography.titleMedium,
                                textAlign = TextAlign.Center
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = stringResource(R.string.vault_empty_subtitle),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(state.entries, key = { it.id }) { entry ->
                        EntryCard(
                            entry = entry,
                            onClick = { onEntryClick(entry.id) },
                            onDelete = { entryToDelete = entry.id },
                            onCopyPassword = { viewModel.copyPasswordForEntry(entry.id) },
                            modifier = Modifier.animateItem(),
                            isSelected = entry.id in state.selectedEntryIds,
                            isSelectionMode = state.isSelectionMode,
                            onLongClick = { viewModel.toggleSelection(entry.id) },
                            onToggleFavorite = { viewModel.toggleFavorite(entry.id) }
                        )
                    }
                }
            }
        }
    }

    // Import dialog
    if (showImportDialog) {
        ImportDialog(
            onDismiss = { showImportDialog = false },
            onImportCsv = { importCsvLauncher.launch(arrayOf("text/*")) },
            onImportJson = { importJsonLauncher.launch(arrayOf("application/json", "text/*")) },
            onImportEncrypted = { password ->
                pendingEncPassword = password
                importEncLauncher.launch(arrayOf("application/octet-stream", "*/*"))
            }
        )
    }

    // Export dialog
    if (showExportDialog) {
        ExportDialog(
            onDismiss = { showExportDialog = false },
            onExportCsv = { exportCsvLauncher.launch("vault_export.csv") },
            onExportJson = { exportJsonLauncher.launch("vault_export.json") },
            onExportEncrypted = { exportBackupLauncher.launch("vault_backup.enc") }
        )
    }

    // Delete confirmation dialog
    entryToDelete?.let { entryId ->
        ConfirmDialog(
            title = stringResource(R.string.vault_delete_entry),
            message = stringResource(R.string.vault_delete_confirm),
            confirmText = stringResource(R.string.common_delete),
            onConfirm = {
                viewModel.deleteEntry(entryId)
                entryToDelete = null
            },
            onDismiss = { entryToDelete = null }
        )
    }

    // Bulk delete confirmation dialog
    if (showBulkDeleteDialog) {
        ConfirmDialog(
            title = stringResource(R.string.vault_delete_entry),
            message = stringResource(R.string.vault_delete_selected_confirm)
                .replace("%1\$d", state.selectedEntryIds.size.toString()),
            confirmText = stringResource(R.string.common_delete),
            onConfirm = {
                viewModel.bulkDelete()
                showBulkDeleteDialog = false
            },
            onDismiss = { showBulkDeleteDialog = false }
        )
    }

    // Bulk change category dialog
    if (showBulkCategoryDialog) {
        BulkCategoryDialog(
            categories = state.categories,
            onDismiss = { showBulkCategoryDialog = false },
            onConfirm = { category ->
                viewModel.bulkChangeCategory(category)
                showBulkCategoryDialog = false
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SearchBar(
    query: String,
    onQueryChange: (String) -> Unit,
    onClose: () -> Unit
) {
    TopAppBar(
        title = {
            OutlinedTextField(
                value = query,
                onValueChange = onQueryChange,
                placeholder = { Text(stringResource(R.string.vault_search)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
        },
        navigationIcon = {
            IconButton(onClick = onClose) {
                Icon(Icons.Default.Close, contentDescription = stringResource(R.string.common_cancel))
            }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BulkCategoryDialog(
    categories: List<String>,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    var selectedCategory by remember { mutableStateOf(categories.firstOrNull() ?: "") }
    var expanded by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.vault_change_category)) },
        text = {
            ExposedDropdownMenuBox(
                expanded = expanded,
                onExpandedChange = { expanded = it }
            ) {
                OutlinedTextField(
                    value = selectedCategory,
                    onValueChange = { selectedCategory = it },
                    label = { Text(stringResource(R.string.entry_category)) },
                    trailingIcon = {
                        ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded)
                    },
                    modifier = Modifier.fillMaxWidth().menuAnchor(),
                    singleLine = true
                )
                ExposedDropdownMenu(
                    expanded = expanded,
                    onDismissRequest = { expanded = false }
                ) {
                    categories.forEach { cat ->
                        DropdownMenuItem(
                            text = { Text(cat) },
                            onClick = {
                                selectedCategory = cat
                                expanded = false
                            }
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(selectedCategory) },
                enabled = selectedCategory.isNotBlank()
            ) {
                Text(stringResource(R.string.common_save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.common_cancel))
            }
        }
    )
}
