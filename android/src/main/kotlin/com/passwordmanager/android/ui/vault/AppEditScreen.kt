package com.passwordmanager.android.ui.vault

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.key
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.imePadding
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.hilt.navigation.compose.hiltViewModel
import com.passwordmanager.android.R
import com.passwordmanager.android.ui.components.PasswordField

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppEditScreen(
    entryId: String?,
    onBack: () -> Unit,
    onSaved: () -> Unit,
    viewModel: AppEditViewModel = hiltViewModel()
) {
    LaunchedEffect(entryId) { viewModel.loadEntry(entryId) }

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
                    key(state.favorite) {
                        IconButton(onClick = { viewModel.toggleFavorite() }) {
                            Icon(
                                imageVector = if (state.favorite) Icons.Default.Star else Icons.Default.StarBorder,
                                contentDescription = stringResource(R.string.entry_toggle_favorite),
                                tint = if (state.favorite) Color(0xFFFFC107) else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
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
                .imePadding()
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

            // PIN (masked) + generate button
            PasswordField(
                value = state.pin,
                onValueChange = { viewModel.updatePin(it) },
                label = stringResource(R.string.entry_pin),
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(4.dp))

            OutlinedButton(
                onClick = { viewModel.generatePin() },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(stringResource(R.string.entry_generate))
            }

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
