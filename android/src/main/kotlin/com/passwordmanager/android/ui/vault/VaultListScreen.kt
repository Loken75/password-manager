package com.passwordmanager.android.ui.vault

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.hilt.navigation.compose.hiltViewModel
import com.passwordmanager.android.R
import com.passwordmanager.android.ui.components.ConfirmDialog
import com.passwordmanager.android.ui.components.EntryCard
import com.passwordmanager.vault.SortField

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VaultListScreen(
    onEntryClick: (String) -> Unit,
    onNewEntry: () -> Unit,
    onSettings: () -> Unit,
    onSecurityAudit: () -> Unit,
    onGenerator: () -> Unit,
    onLock: () -> Unit,
    viewModel: VaultListViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    // Import/Export launchers
    val importCsvLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri -> uri?.let { viewModel.importCsv(it) } }

    val importJsonLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri -> uri?.let { viewModel.importJson(it) } }

    val exportCsvLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("text/csv")
    ) { uri -> uri?.let { viewModel.exportCsv(it) } }

    val exportJsonLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri -> uri?.let { viewModel.exportJson(it) } }

    val exportBackupLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/octet-stream")
    ) { uri -> uri?.let { viewModel.exportBackup(it) } }

    // Export warning dialog
    var showExportWarning by remember { mutableStateOf<String?>(null) }

    // Message strings for snackbar
    val importSuccessTemplate = stringResource(R.string.import_success)
    val importErrorStr = stringResource(R.string.import_error)
    val exportSuccessStr = stringResource(R.string.export_success)
    val exportErrorStr = stringResource(R.string.export_error)

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
                else -> msg
            }
            snackbarHostState.showSnackbar(text)
            viewModel.clearMessage()
        }
    }

    // Refresh when returning from edit/detail
    LaunchedEffect(Unit) { viewModel.refreshEntries() }

    var menuExpanded by remember { mutableStateOf(false) }
    var sortMenuExpanded by remember { mutableStateOf(false) }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            if (state.isSearchActive) {
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
                                Icon(Icons.Default.Sort, contentDescription = "Sort")
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
                                Icon(Icons.Default.MoreVert, contentDescription = "Menu")
                            }
                            DropdownMenu(
                                expanded = menuExpanded,
                                onDismissRequest = { menuExpanded = false }
                            ) {
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.menu_import_csv)) },
                                    onClick = {
                                        menuExpanded = false
                                        importCsvLauncher.launch(arrayOf("text/*"))
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.menu_import_json)) },
                                    onClick = {
                                        menuExpanded = false
                                        importJsonLauncher.launch(arrayOf("application/json", "text/*"))
                                    }
                                )
                                HorizontalDivider()
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.menu_export_csv)) },
                                    onClick = {
                                        menuExpanded = false
                                        showExportWarning = "csv"
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.menu_export_json)) },
                                    onClick = {
                                        menuExpanded = false
                                        showExportWarning = "json"
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.menu_export_backup)) },
                                    onClick = {
                                        menuExpanded = false
                                        exportBackupLauncher.launch("vault_backup.enc")
                                    }
                                )
                                HorizontalDivider()
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.menu_security_audit)) },
                                    onClick = { menuExpanded = false; onSecurityAudit() }
                                )
                                DropdownMenuItem(
                                    leadingIcon = { Icon(Icons.Default.Key, contentDescription = null) },
                                    text = { Text(stringResource(R.string.generator_title)) },
                                    onClick = { menuExpanded = false; onGenerator() }
                                )
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.menu_settings)) },
                                    onClick = { menuExpanded = false; onSettings() }
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
            FloatingActionButton(onClick = onNewEntry) {
                Icon(Icons.Default.Add, contentDescription = stringResource(R.string.vault_new_entry))
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // Category filter chips
            if (state.categories.isNotEmpty()) {
                Row(
                    modifier = Modifier
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilterChip(
                        selected = state.selectedCategory == null,
                        onClick = { viewModel.selectCategory(null) },
                        label = { Text(stringResource(R.string.category_all)) }
                    )
                    state.categories.forEach { category ->
                        FilterChip(
                            selected = state.selectedCategory == category,
                            onClick = { viewModel.selectCategory(category) },
                            label = { Text(category) }
                        )
                    }
                }
            }

            // Entry list
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(state.entries, key = { it.id }) { entry ->
                    EntryCard(
                        entry = entry,
                        onClick = { onEntryClick(entry.id) }
                    )
                }
            }
        }
    }

    // Export warning dialog (for unencrypted exports)
    showExportWarning?.let { type ->
        ConfirmDialog(
            title = stringResource(R.string.export_title),
            message = stringResource(R.string.export_warning),
            onConfirm = {
                showExportWarning = null
                when (type) {
                    "csv" -> exportCsvLauncher.launch("vault_export.csv")
                    "json" -> exportJsonLauncher.launch("vault_export.json")
                }
            },
            onDismiss = { showExportWarning = null }
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
