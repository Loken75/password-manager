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
import androidx.compose.ui.graphics.Color
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import com.passwordmanager.android.R
import com.passwordmanager.android.ui.components.BentoDashboard
import com.passwordmanager.android.ui.components.ConfirmDialog
import com.passwordmanager.android.ui.components.DateFilterChip
import com.passwordmanager.android.ui.components.EntryCard
import com.passwordmanager.android.ui.components.FilterSection
import com.passwordmanager.android.ui.components.FilterSheet
import com.passwordmanager.android.ui.components.VaultPageSelector
import com.passwordmanager.android.ui.sync.ConflictResolutionScreen
import com.passwordmanager.vault.SortField

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun VaultListScreen(
    onEntryClick: (String) -> Unit,
    onNewEntry: () -> Unit,
    onSelectPage: (Int) -> Unit = {},
    isCurrentPage: Boolean = true,
    viewModel: VaultListViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    // Dialog states
    var entryToDelete by remember { mutableStateOf<String?>(null) }
    var showBulkCategoryDialog by remember { mutableStateOf(false) }
    var showBulkDeleteDialog by remember { mutableStateOf(false) }

    // Snackbar feedback for vault-wide actions (import/export/sync, password copied).
    // Gated by isCurrentPage so the shared ViewModel's message is consumed only once.
    VaultActionsMessageEffect(viewModel, snackbarHostState, active = isCurrentPage)

    // Refresh when returning from edit/detail
    LaunchedEffect(Unit) { viewModel.refreshEntries() }

    var sortMenuExpanded by remember { mutableStateOf(false) }
    var showFilterSheet by remember { mutableStateOf(false) }

    val activeFilterCount = listOf(
        state.selectedCategories.isNotEmpty(),
        state.favoritesOnly,
        state.selectedStrengths.isNotEmpty(),
        state.createdSince != null,
        state.modifiedSince != null,
        state.createdOn != null,
        state.modifiedOn != null
    ).count { it }

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
                    title = { VaultPageSelector(selectedIndex = 0, onSelect = onSelectPage) },
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
                                // Direction toggle (keeps the menu open so the field can be picked next)
                                DropdownMenuItem(
                                    leadingIcon = {
                                        Icon(
                                            if (state.sortDescending) Icons.Default.ArrowDownward else Icons.Default.ArrowUpward,
                                            contentDescription = null
                                        )
                                    },
                                    text = {
                                        Text(stringResource(
                                            if (state.sortDescending) R.string.menu_sort_descending
                                            else R.string.menu_sort_ascending
                                        ))
                                    },
                                    onClick = { viewModel.toggleSortDirection() }
                                )
                                HorizontalDivider()
                                listOf(
                                    SortField.TITLE to R.string.menu_sort_name,
                                    SortField.USERNAME to R.string.menu_sort_username,
                                    SortField.EMAIL to R.string.menu_sort_email,
                                    SortField.URL to R.string.menu_sort_url,
                                    SortField.CREATED to R.string.menu_sort_created,
                                    SortField.DATE to R.string.menu_sort_modified,
                                    SortField.CATEGORY to R.string.menu_sort_category,
                                    SortField.STRENGTH to R.string.menu_sort_strength
                                ).forEach { (field, labelRes) ->
                                    DropdownMenuItem(
                                        text = { Text(stringResource(labelRes)) },
                                        trailingIcon = {
                                            if (state.sortField == field) {
                                                Icon(Icons.Default.Check, contentDescription = null)
                                            }
                                        },
                                        onClick = {
                                            viewModel.setSortField(field)
                                            sortMenuExpanded = false
                                        }
                                    )
                                }
                            }
                        }

                        // Filter sheet trigger (badge shows the number of active filters)
                        BadgedBox(
                            badge = {
                                if (activeFilterCount > 0) {
                                    Badge { Text(activeFilterCount.toString()) }
                                }
                            }
                        ) {
                            IconButton(onClick = { showFilterSheet = true }) {
                                Icon(Icons.Default.FilterList, contentDescription = stringResource(R.string.vault_filter))
                            }
                        }

                        // Overflow menu (import/export/sync) — shared with Applications
                        VaultActionsMenu(viewModel = viewModel)
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
            // Bulk "Actions..." button in selection mode
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
                                leadingIcon = { Icon(Icons.Default.Label, contentDescription = null) },
                                text = { Text(stringResource(R.string.vault_change_category)) },
                                onClick = { showActionsMenu = false; showBulkCategoryDialog = true }
                            )
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
            // Bento dashboard — vault-wide stats (full list, so the score matches the Audit
            // screen regardless of the active filter/search). Hidden only in selection mode.
            if (!state.isSelectionMode && state.allEntries.isNotEmpty()) {
                BentoDashboard(state.allEntries)
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
                    items(state.entries, key = { "${it.id}_${it.isFavorite}_${state.refreshToken}" }) { entry ->
                        val domain = entry.url?.let {
                            com.passwordmanager.util.FaviconService.extractDomain(it)
                        }
                        val favicon = domain?.let { state.favicons[it] }
                        EntryCard(
                            entry = entry,
                            onClick = { onEntryClick(entry.id) },
                            onDelete = { entryToDelete = entry.id },
                            onCopyPassword = { viewModel.copyPasswordForEntry(entry.id) },
                            modifier = Modifier.animateItem(),
                            isSelected = entry.id in state.selectedEntryIds,
                            isSelectionMode = state.isSelectionMode,
                            onLongClick = { viewModel.toggleSelection(entry.id) },
                            onToggleFavorite = { viewModel.toggleFavorite(entry.id) },
                            favicon = favicon
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

    // Sync merge conflict resolution (full-screen dialog)
    if (state.passwordConflicts.isNotEmpty()) {
        ConflictResolutionScreen(
            conflicts = state.passwordConflicts,
            onResolve = { resolutions -> viewModel.resolvePasswordConflicts(resolutions) },
            onBack = { viewModel.dismissConflicts() }
        )
    }

    // SFTP host-key confirmation (first use or changed key)
    state.hostKeyPrompt?.let { prompt ->
        ConfirmDialog(
            title = stringResource(
                if (prompt.changed) R.string.sftp_hostkey_changed_title else R.string.sftp_hostkey_title
            ),
            message = stringResource(
                if (prompt.changed) R.string.sftp_hostkey_changed_message else R.string.sftp_hostkey_message,
                "${prompt.host}:${prompt.port}", prompt.keyType, prompt.fingerprint
            ),
            confirmText = stringResource(
                if (prompt.changed) R.string.sftp_hostkey_trust_changed else R.string.sftp_hostkey_trust
            ),
            onConfirm = { viewModel.confirmHostKey() },
            onDismiss = { viewModel.dismissHostKeyPrompt() }
        )
    }

    // Filters bottom sheet — applied live; the CTA reflects the running result count
    if (showFilterSheet) {
        FilterSheet(
            resultCount = state.entries.size,
            activeFilterCount = activeFilterCount,
            onReset = { viewModel.clearAllFilters() },
            onDismiss = { showFilterSheet = false }
        ) {
            FilterSection(stringResource(R.string.entry_favorite)) {
                FilterChip(
                    selected = state.favoritesOnly,
                    onClick = { viewModel.toggleFavoritesFilter() },
                    label = { Text(stringResource(R.string.filter_favorites_only)) },
                    leadingIcon = {
                        Icon(
                            if (state.favoritesOnly) Icons.Default.Star else Icons.Default.StarBorder,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                )
            }

            if (state.categories.isNotEmpty()) {
                FilterSection(stringResource(R.string.entry_category)) {
                    // Multi-select: no category selected = all shown.
                    state.categories.forEach { category ->
                        FilterChip(
                            selected = category in state.selectedCategories,
                            onClick = { viewModel.toggleCategory(category) },
                            label = { Text(category) }
                        )
                    }
                }
            }

            FilterSection(stringResource(R.string.filter_strength)) {
                val strengths = com.passwordmanager.crypto.PasswordStrengthAnalyzer.Strength.values()
                // Multi-select: no strength selected = all shown.
                strengths.forEach { strength ->
                    FilterChip(
                        selected = strength in state.selectedStrengths,
                        onClick = { viewModel.toggleStrength(strength) },
                        label = {
                            Text(
                                when (strength) {
                                    com.passwordmanager.crypto.PasswordStrengthAnalyzer.Strength.WEAK -> stringResource(R.string.strength_weak)
                                    com.passwordmanager.crypto.PasswordStrengthAnalyzer.Strength.MEDIUM -> stringResource(R.string.strength_medium)
                                    com.passwordmanager.crypto.PasswordStrengthAnalyzer.Strength.STRONG -> stringResource(R.string.strength_strong)
                                    com.passwordmanager.crypto.PasswordStrengthAnalyzer.Strength.VERY_STRONG -> stringResource(R.string.strength_very_strong)
                                }
                            )
                        }
                    )
                }
            }

            FilterSection(stringResource(R.string.filter_dates)) {
                DateFilterChip(stringResource(R.string.filter_created_on), state.createdOn, viewModel::setCreatedOn)
                DateFilterChip(stringResource(R.string.filter_created_since), state.createdSince, viewModel::setCreatedSince)
                DateFilterChip(stringResource(R.string.filter_modified_on), state.modifiedOn, viewModel::setModifiedOn)
                DateFilterChip(stringResource(R.string.filter_modified_since), state.modifiedSince, viewModel::setModifiedSince)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SearchBar(
    query: String,
    onQueryChange: (String) -> Unit,
    onClose: () -> Unit
) {
    // Focus the field as soon as the search bar appears so the keyboard shows immediately.
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) { focusRequester.requestFocus() }
    TopAppBar(
        title = {
            OutlinedTextField(
                value = query,
                onValueChange = onQueryChange,
                placeholder = { Text(stringResource(R.string.vault_search)) },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(focusRequester)
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
