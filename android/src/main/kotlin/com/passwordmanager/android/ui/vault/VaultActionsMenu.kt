package com.passwordmanager.android.ui.vault

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.passwordmanager.android.R
import com.passwordmanager.android.ui.components.ExportDialog
import com.passwordmanager.android.ui.components.ImportDialog
import com.passwordmanager.util.SecureWiper

/**
 * Shared vault overflow menu (Import / Export / Sync) plus its import/export
 * dialogs and file pickers. All actions operate on the whole vault, so this is reused
 * by both the Passwords and Applications pages with the same shared [VaultListViewModel]
 * instance — no duplicated import/export/sync logic. (Logging out is the bottom-bar "Quit".)
 *
 * Place this inside a TopAppBar `actions` slot. The snackbar feedback is handled
 * separately by [VaultActionsMessageEffect] so it survives selection/search modes.
 */
@Composable
fun VaultActionsMenu(
    viewModel: VaultListViewModel
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    var menuExpanded by remember { mutableStateOf(false) }
    var showImportDialog by remember { mutableStateOf(false) }
    var showExportDialog by remember { mutableStateOf(false) }

    // For encrypted import: store password until file is picked
    var pendingEncPassword by remember { mutableStateOf<CharArray?>(null) }
    var pendingEncImportUri by remember { mutableStateOf<Uri?>(null) }

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
            // pwd is wiped inside importEncryptedVault; clear the reference
            pendingEncPassword = null
        }
    }

    // Wipe password if import is cancelled (URI never picked)
    DisposableEffect(Unit) {
        onDispose {
            pendingEncPassword?.let { SecureWiper.wipe(it) }
        }
    }

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
                enabled = state.isSyncEnabled,
                onClick = {
                    menuExpanded = false
                    viewModel.syncNow()
                }
            )
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
}

/**
 * Shows snackbar feedback for [VaultListViewModel] messages (import/export/sync results,
 * password copied). Gated by [active] so that — since both vault pages observe the same
 * shared ViewModel via the HorizontalPager — only the currently visible page consumes and
 * clears each message (no duplicate snackbars). Keep this in the screen body (not the top
 * bar) so feedback still appears in selection/search modes.
 */
@Composable
fun VaultActionsMessageEffect(
    viewModel: VaultListViewModel,
    snackbarHostState: SnackbarHostState,
    active: Boolean
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    val importSuccessTemplate = stringResource(R.string.import_success)
    val importErrorStr = stringResource(R.string.import_error)
    val exportSuccessStr = stringResource(R.string.export_success)
    val exportErrorStr = stringResource(R.string.export_error)
    val passwordCopiedStr = stringResource(R.string.password_copied)
    val syncSuccessStr = stringResource(R.string.sync_success)
    val syncErrorStr = stringResource(R.string.sync_error)
    val syncAutoMergedStr = stringResource(R.string.sync_merge_auto_merged)

    LaunchedEffect(state.message, active) {
        val msg = state.message
        if (active && msg != null) {
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
                msg == "sync_merge_auto" -> syncAutoMergedStr
                else -> msg
            }
            snackbarHostState.showSnackbar(text)
            viewModel.clearMessage()
        }
    }
}
