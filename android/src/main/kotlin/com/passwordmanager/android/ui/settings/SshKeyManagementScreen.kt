package com.passwordmanager.android.ui.settings

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FileOpen
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.passwordmanager.android.R

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SshKeyManagementScreen(
    onBack: () -> Unit,
    viewModel: SshKeyManagementViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current

    LaunchedEffect(Unit) { viewModel.load() }

    var showGenerateDialog by remember { mutableStateOf(false) }
    var showImportDialog by remember { mutableStateOf(false) }
    var importName by remember { mutableStateOf("") }
    var importContent by remember { mutableStateOf("") }
    var importMode by remember { mutableStateOf("file") } // "file" or "content"
    var showDeleteDialog by remember { mutableStateOf<String?>(null) }

    // File picker for SSH key import
    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null && importName.isNotBlank()) {
            val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
            if (bytes != null) {
                viewModel.importKeyFromBytes(importName, bytes)
                importName = ""
                showImportDialog = false
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.ssh_key_management_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                    }
                }
            )
        },
        floatingActionButton = {
            Column(horizontalAlignment = Alignment.End) {
                SmallFloatingActionButton(
                    onClick = { showImportDialog = true }
                ) {
                    Icon(Icons.Default.FileOpen, contentDescription = stringResource(R.string.ssh_key_import))
                }
                Spacer(modifier = Modifier.height(12.dp))
                FloatingActionButton(
                    onClick = { showGenerateDialog = true }
                ) {
                    Icon(Icons.Default.Add, contentDescription = stringResource(R.string.ssh_key_generate))
                }
            }
        }
    ) { padding ->
        if (state.keys.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        stringResource(R.string.ssh_key_empty),
                        style = MaterialTheme.typography.titleMedium
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        stringResource(R.string.ssh_key_empty_subtitle),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(vertical = 16.dp)
            ) {
                items(state.keys, key = { it.id }) { key ->
                    SshKeyCard(
                        key = key,
                        onViewPublicKey = { viewModel.viewPublicKey(key.id) },
                        onDelete = { showDeleteDialog = key.id }
                    )
                }
            }
        }
    }

    // Error dialog
    state.error?.let { errorKey ->
        val errorMsg = when (errorKey) {
            "ssh_key_name_required" -> stringResource(R.string.ssh_key_name_required)
            "ssh_key_generate_error" -> stringResource(R.string.ssh_key_generate_error)
            "ssh_key_invalid" -> stringResource(R.string.ssh_key_invalid)
            "ssh_key_import_content_error" -> stringResource(R.string.ssh_key_import_content_error)
            else -> errorKey
        }
        AlertDialog(
            onDismissRequest = { viewModel.clearError() },
            title = { Text(stringResource(R.string.common_error)) },
            text = { Text(errorMsg) },
            confirmButton = {
                TextButton(onClick = { viewModel.clearError() }) {
                    Text(stringResource(R.string.common_ok))
                }
            }
        )
    }

    // View public key dialog
    state.showPublicKey?.let { publicKey ->
        AlertDialog(
            onDismissRequest = { viewModel.dismissPublicKey() },
            title = { Text(stringResource(R.string.ssh_key_public_key)) },
            text = {
                SelectionContainer {
                    Text(
                        text = publicKey,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    clipboardManager.setText(AnnotatedString(publicKey))
                    viewModel.dismissPublicKey()
                }) {
                    Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(stringResource(R.string.entry_copy))
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.dismissPublicKey() }) {
                    Text(stringResource(R.string.common_ok))
                }
            }
        )
    }

    // Generate key dialog
    if (showGenerateDialog) {
        GenerateSshKeyDialog(
            onDismiss = { showGenerateDialog = false },
            onGenerate = { name, type ->
                viewModel.generateKey(name, type)
                showGenerateDialog = false
            }
        )
    }

    // Import key dialog
    if (showImportDialog) {
        AlertDialog(
            onDismissRequest = { showImportDialog = false; importName = ""; importContent = ""; importMode = "file" },
            title = { Text(stringResource(R.string.ssh_key_import)) },
            text = {
                Column {
                    Text(
                        stringResource(R.string.ssh_key_import_hint),
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    OutlinedTextField(
                        value = importName,
                        onValueChange = { importName = it },
                        label = { Text(stringResource(R.string.ssh_key_name)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Row {
                        FilterChip(
                            selected = importMode == "file",
                            onClick = { importMode = "file" },
                            label = { Text(stringResource(R.string.ssh_key_select_file)) }
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        FilterChip(
                            selected = importMode == "content",
                            onClick = { importMode = "content" },
                            label = { Text(stringResource(R.string.ssh_key_paste_content)) }
                        )
                    }
                    if (importMode == "content") {
                        Spacer(modifier = Modifier.height(12.dp))
                        OutlinedTextField(
                            value = importContent,
                            onValueChange = { importContent = it },
                            label = { Text(stringResource(R.string.ssh_key_content_hint)) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 120.dp),
                            minLines = 5,
                            maxLines = 10
                        )
                    }
                }
            },
            confirmButton = {
                if (importMode == "file") {
                    TextButton(
                        onClick = { filePickerLauncher.launch("*/*") },
                        enabled = importName.isNotBlank()
                    ) {
                        Text(stringResource(R.string.ssh_key_select_file))
                    }
                } else {
                    TextButton(
                        onClick = {
                            viewModel.importKeyFromContent(importName, importContent)
                            importName = ""
                            importContent = ""
                            importMode = "file"
                            showImportDialog = false
                        },
                        enabled = importName.isNotBlank() && importContent.isNotBlank()
                    ) {
                        Text(stringResource(R.string.common_save))
                    }
                }
            },
            dismissButton = {
                TextButton(onClick = { showImportDialog = false; importName = ""; importContent = ""; importMode = "file" }) {
                    Text(stringResource(R.string.common_cancel))
                }
            }
        )
    }

    // Delete confirmation
    showDeleteDialog?.let { keyId ->
        val keyName = state.keys.find { it.id == keyId }?.name ?: ""
        AlertDialog(
            onDismissRequest = { showDeleteDialog = null },
            title = { Text(stringResource(R.string.ssh_key_delete_confirm)) },
            text = { Text(keyName) },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.deleteKey(keyId)
                    showDeleteDialog = null
                }) {
                    Text(stringResource(R.string.common_delete))
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = null }) {
                    Text(stringResource(R.string.common_cancel))
                }
            }
        )
    }
}

@Composable
private fun SshKeyCard(
    key: SshKeyMeta,
    onViewPublicKey: () -> Unit,
    onDelete: () -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(key.name, style = MaterialTheme.typography.titleMedium)
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                "${key.keyType} — ${key.fingerprint}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (key.createdAt.isNotBlank()) {
                Text(
                    key.createdAt,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            Row {
                TextButton(onClick = onViewPublicKey) {
                    Icon(Icons.Default.Visibility, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(stringResource(R.string.ssh_key_view_public))
                }
                Spacer(modifier = Modifier.weight(1f))
                IconButton(onClick = onDelete) {
                    Icon(
                        Icons.Default.Delete,
                        contentDescription = stringResource(R.string.common_delete),
                        tint = MaterialTheme.colorScheme.error
                    )
                }
            }
        }
    }
}

@Composable
private fun GenerateSshKeyDialog(
    onDismiss: () -> Unit,
    onGenerate: (name: String, type: String) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var selectedType by remember { mutableStateOf("ED25519") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.ssh_key_generate)) },
        text = {
            Column {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(stringResource(R.string.ssh_key_name)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(stringResource(R.string.ssh_key_type), style = MaterialTheme.typography.bodyMedium)
                Spacer(modifier = Modifier.height(8.dp))
                Row {
                    FilterChip(
                        selected = selectedType == "ED25519",
                        onClick = { selectedType = "ED25519" },
                        label = { Text("ED25519") }
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    FilterChip(
                        selected = selectedType == "RSA",
                        onClick = { selectedType = "RSA" },
                        label = { Text("RSA 4096") }
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onGenerate(name, selectedType) },
                enabled = name.isNotBlank()
            ) {
                Text(stringResource(R.string.ssh_key_generate))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.common_cancel))
            }
        }
    )
}
