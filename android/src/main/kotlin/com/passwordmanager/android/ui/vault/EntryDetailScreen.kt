package com.passwordmanager.android.ui.vault

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.passwordmanager.android.R
import com.passwordmanager.android.ui.components.ConfirmDialog
import com.passwordmanager.android.ui.components.PasswordStrengthBar
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EntryDetailScreen(
    entryId: String,
    onBack: () -> Unit,
    onEdit: () -> Unit,
    onDeleted: () -> Unit,
    viewModel: EntryDetailViewModel = viewModel()
) {
    LaunchedEffect(entryId) { viewModel.loadEntry(entryId) }

    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val entry = state.entry
    val context = LocalContext.current
    var showDeleteConfirm by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(entry?.title ?: "") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                    }
                },
                actions = {
                    IconButton(onClick = onEdit) {
                        Icon(Icons.Default.Edit, contentDescription = stringResource(R.string.vault_edit_entry))
                    }
                    IconButton(onClick = { showDeleteConfirm = true }) {
                        Icon(Icons.Default.Delete, contentDescription = stringResource(R.string.vault_delete_entry))
                    }
                }
            )
        }
    ) { padding ->
        if (entry == null) {
            Box(modifier = Modifier.fillMaxSize().padding(padding))
            return@Scaffold
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState())
        ) {
            // Username
            DetailRow(
                label = stringResource(R.string.entry_username),
                value = entry.username ?: "",
                onCopy = { copyToClipboard(context, entry.username ?: "", 30) }
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Password
            DetailRow(
                label = stringResource(R.string.entry_password),
                value = if (state.passwordVisible) entry.password?.let { String(it) } ?: ""
                    else "\u2022".repeat(12),
                onCopy = {
                    entry.password?.let { copyToClipboard(context, String(it), 30) }
                },
                trailing = {
                    IconButton(onClick = { viewModel.togglePasswordVisibility() }) {
                        Icon(
                            imageVector = if (state.passwordVisible) Icons.Default.VisibilityOff
                                else Icons.Default.Visibility,
                            contentDescription = stringResource(R.string.entry_show_password)
                        )
                    }
                }
            )

            entry.password?.let {
                Spacer(modifier = Modifier.height(8.dp))
                PasswordStrengthBar(password = it)
            }

            Spacer(modifier = Modifier.height(16.dp))

            // URL
            if (!entry.url.isNullOrBlank()) {
                DetailRow(
                    label = stringResource(R.string.entry_url),
                    value = entry.url
                )
                Spacer(modifier = Modifier.height(16.dp))
            }

            // Category
            if (!entry.category.isNullOrBlank()) {
                DetailRow(
                    label = stringResource(R.string.entry_category),
                    value = entry.category
                )
                Spacer(modifier = Modifier.height(16.dp))
            }

            // Tags
            if (!entry.tags.isNullOrEmpty()) {
                DetailRow(
                    label = stringResource(R.string.entry_tags),
                    value = entry.tags.joinToString(", ")
                )
                Spacer(modifier = Modifier.height(16.dp))
            }

            // Notes
            if (!entry.notes.isNullOrBlank()) {
                Text(
                    text = stringResource(R.string.entry_notes),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = entry.notes,
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.padding(top = 4.dp)
                )
                Spacer(modifier = Modifier.height(16.dp))
            }

            HorizontalDivider()
            Spacer(modifier = Modifier.height(8.dp))

            // Timestamps
            DetailRow(
                label = stringResource(R.string.entry_created),
                value = entry.createdAt ?: ""
            )
            Spacer(modifier = Modifier.height(8.dp))
            DetailRow(
                label = stringResource(R.string.entry_updated),
                value = entry.updatedAt ?: ""
            )
        }
    }

    if (showDeleteConfirm) {
        ConfirmDialog(
            title = stringResource(R.string.vault_delete_entry),
            message = stringResource(R.string.vault_delete_confirm),
            confirmText = stringResource(R.string.common_delete),
            onConfirm = {
                showDeleteConfirm = false
                if (viewModel.deleteEntry(entryId)) onDeleted()
            },
            onDismiss = { showDeleteConfirm = false }
        )
    }
}

@Composable
private fun DetailRow(
    label: String,
    value: String,
    onCopy: (() -> Unit)? = null,
    trailing: @Composable (() -> Unit)? = null
) {
    Column {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Row(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = value,
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.weight(1f).padding(top = 2.dp)
            )
            trailing?.invoke()
            if (onCopy != null) {
                IconButton(onClick = onCopy) {
                    Icon(
                        Icons.Default.ContentCopy,
                        contentDescription = stringResource(R.string.entry_copy),
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }
    }
}

private fun copyToClipboard(context: Context, text: String, clearAfterSeconds: Int) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    clipboard.setPrimaryClip(ClipData.newPlainText("", text))

    CoroutineScope(Dispatchers.Main).launch {
        delay(clearAfterSeconds * 1000L)
        clipboard.setPrimaryClip(ClipData.newPlainText("", ""))
    }
}
