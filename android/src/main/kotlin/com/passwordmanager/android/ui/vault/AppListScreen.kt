package com.passwordmanager.android.ui.vault

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.hilt.navigation.compose.hiltViewModel
import com.passwordmanager.android.R
import com.passwordmanager.android.ui.components.AppEntryCard
import com.passwordmanager.android.ui.components.ConfirmDialog
import com.passwordmanager.vault.SortField

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppListScreen(
    onEntryClick: (String) -> Unit,
    onNewEntry: () -> Unit,
    onLock: () -> Unit,
    viewModel: AppListViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    var entryToDelete by remember { mutableStateOf<String?>(null) }
    var showBulkDeleteDialog by remember { mutableStateOf(false) }

    val pinCopiedStr = stringResource(R.string.entry_pin_copied)

    // Handle messages
    LaunchedEffect(state.message) {
        state.message?.let { msg ->
            val text = when (msg) {
                "pin_copied" -> pinCopiedStr
                else -> msg
            }
            snackbarHostState.showSnackbar(text)
            viewModel.clearMessage()
        }
    }

    // Refresh when returning from edit/detail
    LaunchedEffect(Unit) { viewModel.refreshEntries() }

    var sortMenuExpanded by remember { mutableStateOf(false) }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            if (state.isSelectionMode) {
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
                AppSearchBar(
                    query = state.searchQuery,
                    onQueryChange = { viewModel.setSearchQuery(it) },
                    onClose = { viewModel.toggleSearch() }
                )
            } else {
                TopAppBar(
                    title = {
                        Text(
                            "${stringResource(R.string.tab_applications)} — ${state.entries.size} ${stringResource(R.string.vault_entries)}"
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
                                    text = { Text(stringResource(R.string.menu_sort_date)) },
                                    onClick = {
                                        viewModel.setSortField(SortField.DATE)
                                        sortMenuExpanded = false
                                    }
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
            AnimatedVisibility(
                visible = state.isSelectionMode && state.selectedEntryIds.isNotEmpty(),
                enter = slideInVertically { it },
                exit = slideOutVertically { it }
            ) {
                var showActionsMenu by remember { mutableStateOf(false) }
                Surface(tonalElevation = 3.dp) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Button(onClick = { showActionsMenu = true }) {
                            Text(stringResource(R.string.vault_bulk_actions))
                        }
                        DropdownMenu(
                            expanded = showActionsMenu,
                            onDismissRequest = { showActionsMenu = false }
                        ) {
                            DropdownMenuItem(
                                leadingIcon = { Icon(Icons.Default.SwapHoriz, contentDescription = null) },
                                text = { Text(stringResource(R.string.vault_toggle_favorites)) },
                                onClick = { showActionsMenu = false; viewModel.bulkToggleFavorite() }
                            )
                            HorizontalDivider()
                            DropdownMenuItem(
                                leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
                                text = { Text(stringResource(R.string.common_delete), color = MaterialTheme.colorScheme.error) },
                                onClick = { showActionsMenu = false; showBulkDeleteDialog = true }
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
            // Favorites filter chip
            Row(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
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
                                imageVector = Icons.Default.Apps,
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
                    items(state.entries, key = { "${it.id}_${it.isFavorite}_${state.refreshToken}" }) { entry ->
                        AppEntryCard(
                            entry = entry,
                            onClick = { onEntryClick(entry.id) },
                            onDelete = { entryToDelete = entry.id },
                            onCopyPin = { viewModel.copyPinForEntry(entry.id) },
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
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AppSearchBar(
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

