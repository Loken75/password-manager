package com.passwordmanager.android.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.input.PasswordVisualTransformation
import com.passwordmanager.android.R

enum class ImportFormat { CSV, JSON, ENCRYPTED }
enum class ExportFormat { CSV, JSON, ENCRYPTED }

@Composable
fun ImportDialog(
    onDismiss: () -> Unit,
    onImportCsv: () -> Unit,
    onImportJson: () -> Unit,
    onImportEncrypted: (password: CharArray) -> Unit
) {
    var selectedFormat by remember { mutableStateOf(ImportFormat.CSV) }
    var encryptedPassword by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.import_title)) },
        text = {
            Column(modifier = Modifier.selectableGroup()) {
                ImportFormat.entries.forEach { format ->
                    val label = when (format) {
                        ImportFormat.CSV -> stringResource(R.string.import_format_csv)
                        ImportFormat.JSON -> stringResource(R.string.import_format_json)
                        ImportFormat.ENCRYPTED -> stringResource(R.string.import_format_encrypted)
                    }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .selectable(
                                selected = selectedFormat == format,
                                onClick = { selectedFormat = format },
                                role = Role.RadioButton
                            )
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = selectedFormat == format,
                            onClick = null
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(text = label, style = MaterialTheme.typography.bodyLarge)
                    }
                }

                if (selectedFormat == ImportFormat.ENCRYPTED) {
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = encryptedPassword,
                        onValueChange = { encryptedPassword = it },
                        label = { Text(stringResource(R.string.import_vault_password)) },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation()
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onDismiss()
                    when (selectedFormat) {
                        ImportFormat.CSV -> onImportCsv()
                        ImportFormat.JSON -> onImportJson()
                        ImportFormat.ENCRYPTED -> {
                            if (encryptedPassword.isNotBlank()) {
                                onImportEncrypted(encryptedPassword.toCharArray())
                            }
                        }
                    }
                },
                enabled = selectedFormat != ImportFormat.ENCRYPTED || encryptedPassword.isNotBlank()
            ) {
                Text(stringResource(R.string.import_title))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.common_cancel))
            }
        }
    )
}

@Composable
fun ExportDialog(
    onDismiss: () -> Unit,
    onExportCsv: () -> Unit,
    onExportJson: () -> Unit,
    onExportEncrypted: () -> Unit
) {
    var selectedFormat by remember { mutableStateOf(ExportFormat.CSV) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.export_title)) },
        text = {
            Column(modifier = Modifier.selectableGroup()) {
                ExportFormat.entries.forEach { format ->
                    val label = when (format) {
                        ExportFormat.CSV -> stringResource(R.string.export_format_csv)
                        ExportFormat.JSON -> stringResource(R.string.export_format_json)
                        ExportFormat.ENCRYPTED -> stringResource(R.string.export_format_encrypted)
                    }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .selectable(
                                selected = selectedFormat == format,
                                onClick = { selectedFormat = format },
                                role = Role.RadioButton
                            )
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = selectedFormat == format,
                            onClick = null
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(text = label, style = MaterialTheme.typography.bodyLarge)
                    }
                }

                if (selectedFormat != ExportFormat.ENCRYPTED) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = stringResource(R.string.export_unencrypted_warning),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onDismiss()
                    when (selectedFormat) {
                        ExportFormat.CSV -> onExportCsv()
                        ExportFormat.JSON -> onExportJson()
                        ExportFormat.ENCRYPTED -> onExportEncrypted()
                    }
                }
            ) {
                Text(stringResource(R.string.export_title))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.common_cancel))
            }
        }
    )
}
