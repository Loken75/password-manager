package com.passwordmanager.android.ui.vault

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.hilt.navigation.compose.hiltViewModel
import com.passwordmanager.android.R
import com.passwordmanager.android.ui.components.PasswordField
import com.passwordmanager.android.ui.components.PasswordStrengthBar

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EntryEditScreen(
    entryId: String?,
    onBack: () -> Unit,
    onSaved: () -> Unit,
    onNavigateToGenerator: () -> Unit,
    generatedPassword: String? = null,
    viewModel: EntryEditViewModel = hiltViewModel()
) {
    LaunchedEffect(entryId) { viewModel.loadEntry(entryId) }

    // Receive generated password from generator screen
    LaunchedEffect(generatedPassword) {
        generatedPassword?.let { viewModel.setGeneratedPassword(it) }
    }

    val state by viewModel.uiState.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        if (state.isNew) stringResource(R.string.vault_new_entry)
                        else stringResource(R.string.vault_edit_entry)
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                    }
                },
                actions = {
                    TextButton(onClick = {
                        if (viewModel.save()) onSaved()
                    }) {
                        Text(stringResource(R.string.common_save))
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState())
        ) {
            // Title
            OutlinedTextField(
                value = state.title,
                onValueChange = { viewModel.updateTitle(it) },
                label = { Text(stringResource(R.string.entry_title)) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                isError = state.error == "title_required"
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Username
            OutlinedTextField(
                value = state.username,
                onValueChange = { viewModel.updateUsername(it) },
                label = { Text(stringResource(R.string.entry_username)) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Password + Generate button
            PasswordField(
                value = state.password,
                onValueChange = { viewModel.updatePassword(it) },
                label = stringResource(R.string.entry_password),
                modifier = Modifier.fillMaxWidth()
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                TextButton(onClick = onNavigateToGenerator) {
                    Text(stringResource(R.string.entry_generate))
                }
            }

            if (state.password.isNotBlank()) {
                PasswordStrengthBar(password = state.password.toCharArray())
                Spacer(modifier = Modifier.height(12.dp))
            }

            // URL
            OutlinedTextField(
                value = state.url,
                onValueChange = { viewModel.updateUrl(it) },
                label = { Text(stringResource(R.string.entry_url)) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Category dropdown
            var categoryExpanded by remember { mutableStateOf(false) }
            ExposedDropdownMenuBox(
                expanded = categoryExpanded,
                onExpandedChange = { categoryExpanded = it }
            ) {
                OutlinedTextField(
                    value = state.category,
                    onValueChange = { viewModel.updateCategory(it) },
                    label = { Text(stringResource(R.string.entry_category)) },
                    modifier = Modifier.fillMaxWidth().menuAnchor(),
                    trailingIcon = {
                        Icon(Icons.Default.ArrowDropDown, contentDescription = null)
                    },
                    singleLine = true
                )
                ExposedDropdownMenu(
                    expanded = categoryExpanded,
                    onDismissRequest = { categoryExpanded = false }
                ) {
                    state.categories.forEach { cat ->
                        DropdownMenuItem(
                            text = { Text(cat) },
                            onClick = {
                                viewModel.updateCategory(cat)
                                categoryExpanded = false
                            }
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Tags
            OutlinedTextField(
                value = state.tags,
                onValueChange = { viewModel.updateTags(it) },
                label = { Text(stringResource(R.string.entry_tags)) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Notes
            OutlinedTextField(
                value = state.notes,
                onValueChange = { viewModel.updateNotes(it) },
                label = { Text(stringResource(R.string.entry_notes)) },
                modifier = Modifier.fillMaxWidth(),
                minLines = 3,
                maxLines = 6
            )
        }
    }
}
