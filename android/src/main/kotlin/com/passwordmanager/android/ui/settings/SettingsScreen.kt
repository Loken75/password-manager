package com.passwordmanager.android.ui.settings

import android.content.Intent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.hilt.navigation.compose.hiltViewModel
import com.passwordmanager.android.R
import com.passwordmanager.android.ui.components.PasswordField
import com.passwordmanager.config.StorageMode
import com.passwordmanager.config.ThemeMode

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onChangeMasterPassword: () -> Unit,
    onManageCategories: () -> Unit = {},
    onManageSshKeys: () -> Unit = {},
    showBackNavigation: Boolean = true,
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current

    // Refresh SSH key list when returning from SshKeyManagementScreen
    LifecycleResumeEffect(Unit) {
        viewModel.refreshSshKeys()
        onPauseOrDispose {}
    }

    val connectionOkStr = stringResource(R.string.settings_connection_ok)
    val connectionFailStr = stringResource(R.string.settings_connection_fail)

    LaunchedEffect(state.connectionTestResult) {
        state.connectionTestResult?.let { result ->
            val text = if (result == "ok") connectionOkStr else connectionFailStr
            snackbarHostState.showSnackbar(text)
            viewModel.clearConnectionTestResult()
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings_title)) },
                navigationIcon = {
                    if (showBackNavigation) {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                        }
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
            // --- General section ---
            Text(
                text = stringResource(R.string.settings_general),
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(bottom = 12.dp)
            )

            // Language
            var langExpanded by remember { mutableStateOf(false) }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(stringResource(R.string.settings_language), style = MaterialTheme.typography.bodyLarge)
                ExposedDropdownMenuBox(
                    expanded = langExpanded,
                    onExpandedChange = { langExpanded = it }
                ) {
                    OutlinedTextField(
                        value = if (state.language == "fr") "Français" else "English",
                        onValueChange = {},
                        readOnly = true,
                        modifier = Modifier.width(160.dp).menuAnchor(),
                        trailingIcon = { Icon(Icons.Default.ArrowDropDown, null) }
                    )
                    ExposedDropdownMenu(
                        expanded = langExpanded,
                        onDismissRequest = { langExpanded = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("English") },
                            onClick = { viewModel.setLanguage("en"); langExpanded = false }
                        )
                        DropdownMenuItem(
                            text = { Text("Français") },
                            onClick = { viewModel.setLanguage("fr"); langExpanded = false }
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Theme
            var themeExpanded by remember { mutableStateOf(false) }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(stringResource(R.string.settings_theme), style = MaterialTheme.typography.bodyLarge)
                ExposedDropdownMenuBox(
                    expanded = themeExpanded,
                    onExpandedChange = { themeExpanded = it }
                ) {
                    OutlinedTextField(
                        value = when (state.themeMode) {
                            ThemeMode.SYSTEM -> stringResource(R.string.settings_theme_system)
                            ThemeMode.LIGHT -> stringResource(R.string.settings_theme_light)
                            ThemeMode.DARK -> stringResource(R.string.settings_theme_dark)
                        },
                        onValueChange = {},
                        readOnly = true,
                        modifier = Modifier.width(160.dp).menuAnchor(),
                        trailingIcon = { Icon(Icons.Default.ArrowDropDown, null) }
                    )
                    ExposedDropdownMenu(
                        expanded = themeExpanded,
                        onDismissRequest = { themeExpanded = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.settings_theme_system)) },
                            onClick = { viewModel.setTheme(ThemeMode.SYSTEM); themeExpanded = false }
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.settings_theme_light)) },
                            onClick = { viewModel.setTheme(ThemeMode.LIGHT); themeExpanded = false }
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.settings_theme_dark)) },
                            onClick = { viewModel.setTheme(ThemeMode.DARK); themeExpanded = false }
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Manage categories
            OutlinedButton(
                onClick = onManageCategories,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(stringResource(R.string.settings_manage_categories))
            }

            Spacer(modifier = Modifier.height(32.dp))

            // --- Security section ---
            Text(
                text = stringResource(R.string.settings_security),
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(bottom = 12.dp)
            )

            // Auto-lock slider
            Text(
                text = "${stringResource(R.string.settings_auto_lock)}: ${state.autoLockMinutes}",
                style = MaterialTheme.typography.bodyLarge
            )
            Slider(
                value = state.autoLockMinutes.toFloat(),
                onValueChange = { viewModel.setAutoLockMinutes(it.toInt()) },
                valueRange = 1f..60f,
                steps = 0
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Clipboard clear slider
            Text(
                text = "${stringResource(R.string.settings_clipboard_clear)}: ${state.clipboardClearSeconds}",
                style = MaterialTheme.typography.bodyLarge
            )
            Slider(
                value = state.clipboardClearSeconds.toFloat(),
                onValueChange = { viewModel.setClipboardClearSeconds(it.toInt()) },
                valueRange = 5f..120f,
                steps = 0
            )

            // Biometric toggle
            if (state.biometricAvailable) {
                val activity = context as FragmentActivity

                Spacer(modifier = Modifier.height(16.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        stringResource(R.string.settings_biometric),
                        style = MaterialTheme.typography.bodyLarge
                    )
                    Switch(
                        checked = state.biometricEnabled,
                        onCheckedChange = { enabled ->
                            if (enabled) {
                                viewModel.showBiometricPasswordPrompt()
                            } else {
                                viewModel.disableBiometric()
                            }
                        }
                    )
                }

                // Password confirmation dialog for biometric enrollment
                if (state.showBiometricPasswordDialog) {
                    val passwordError = state.biometricPasswordError?.let { key ->
                        when (key) {
                            "error_invalid_password" -> stringResource(R.string.error_invalid_password)
                            else -> key
                        }
                    }

                    AlertDialog(
                        onDismissRequest = { viewModel.dismissBiometricPasswordPrompt() },
                        title = { Text(stringResource(R.string.biometric_enroll_title)) },
                        text = {
                            Column {
                                Text(
                                    stringResource(R.string.biometric_enroll_message),
                                    style = MaterialTheme.typography.bodyMedium
                                )
                                Spacer(modifier = Modifier.height(16.dp))
                                PasswordField(
                                    value = state.biometricPasswordInput,
                                    onValueChange = { viewModel.updateBiometricPasswordInput(it) },
                                    label = stringResource(R.string.login_password),
                                    modifier = Modifier.fillMaxWidth(),
                                    isError = passwordError != null
                                )
                                if (passwordError != null) {
                                    Text(
                                        text = passwordError,
                                        color = MaterialTheme.colorScheme.error,
                                        style = MaterialTheme.typography.bodySmall,
                                        modifier = Modifier.padding(top = 4.dp)
                                    )
                                }
                            }
                        },
                        confirmButton = {
                            TextButton(
                                onClick = { viewModel.confirmBiometricPassword(activity) },
                                enabled = !state.biometricPasswordLoading && state.biometricPasswordInput.isNotBlank()
                            ) {
                                if (state.biometricPasswordLoading) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(16.dp),
                                        strokeWidth = 2.dp
                                    )
                                } else {
                                    Text(stringResource(R.string.biometric_enable))
                                }
                            }
                        },
                        dismissButton = {
                            TextButton(onClick = { viewModel.dismissBiometricPasswordPrompt() }) {
                                Text(stringResource(R.string.common_cancel))
                            }
                        }
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Change master password link
            OutlinedButton(
                onClick = onChangeMasterPassword,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(stringResource(R.string.settings_change_master))
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Autofill service
            OutlinedButton(
                onClick = {
                    val intent = Intent(android.provider.Settings.ACTION_REQUEST_SET_AUTOFILL_SERVICE)
                    intent.data = android.net.Uri.parse("package:${context.packageName}")
                    try { context.startActivity(intent) } catch (_: Exception) {}
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(stringResource(R.string.settings_autofill))
            }

            Spacer(modifier = Modifier.height(32.dp))

            // --- Sync section ---
            Text(
                text = stringResource(R.string.settings_sync),
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(bottom = 12.dp)
            )

            // Storage mode radio
            Column(modifier = Modifier.selectableGroup()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .selectable(
                            selected = state.storageMode == StorageMode.LOCAL,
                            onClick = { viewModel.setStorageMode(StorageMode.LOCAL) },
                            role = Role.RadioButton
                        )
                        .padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RadioButton(
                        selected = state.storageMode == StorageMode.LOCAL,
                        onClick = null
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(stringResource(R.string.settings_local_only), style = MaterialTheme.typography.bodyLarge)
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .selectable(
                            selected = state.storageMode == StorageMode.REMOTE,
                            onClick = { viewModel.setStorageMode(StorageMode.REMOTE) },
                            role = Role.RadioButton
                        )
                        .padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RadioButton(
                        selected = state.storageMode == StorageMode.REMOTE,
                        onClick = null
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(stringResource(R.string.settings_remote_server), style = MaterialTheme.typography.bodyLarge)
                }
            }

            // SFTP fields (only visible when REMOTE)
            if (state.storageMode == StorageMode.REMOTE) {
                Spacer(modifier = Modifier.height(12.dp))

                OutlinedTextField(
                    value = state.sftpHost,
                    onValueChange = { viewModel.setSftpHost(it) },
                    label = { Text(stringResource(R.string.settings_sftp_host)) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                Spacer(modifier = Modifier.height(8.dp))

                OutlinedTextField(
                    value = state.sftpPort,
                    onValueChange = { viewModel.setSftpPort(it) },
                    label = { Text(stringResource(R.string.settings_sftp_port)) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                Spacer(modifier = Modifier.height(8.dp))

                OutlinedTextField(
                    value = state.sftpUser,
                    onValueChange = { viewModel.setSftpUser(it) },
                    label = { Text(stringResource(R.string.settings_sftp_user)) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                Spacer(modifier = Modifier.height(8.dp))

                // SSH Key picker (from vault)
                var sshKeyExpanded by remember { mutableStateOf(false) }
                val selectedKeyName = state.sshKeys.find { it.id == state.selectedSshKeyId }?.name ?: ""
                ExposedDropdownMenuBox(
                    expanded = sshKeyExpanded,
                    onExpandedChange = { sshKeyExpanded = it }
                ) {
                    OutlinedTextField(
                        value = selectedKeyName,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text(stringResource(R.string.settings_sftp_key)) },
                        modifier = Modifier.fillMaxWidth().menuAnchor(),
                        trailingIcon = { Icon(Icons.Default.ArrowDropDown, null) },
                        placeholder = {
                            if (state.sshKeys.isEmpty()) {
                                Text(stringResource(R.string.ssh_key_none_available))
                            }
                        }
                    )
                    ExposedDropdownMenu(
                        expanded = sshKeyExpanded,
                        onDismissRequest = { sshKeyExpanded = false }
                    ) {
                        state.sshKeys.forEach { key ->
                            DropdownMenuItem(
                                text = { Text(key.name) },
                                onClick = {
                                    viewModel.selectSshKey(key.id)
                                    sshKeyExpanded = false
                                }
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                OutlinedButton(
                    onClick = onManageSshKeys,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(stringResource(R.string.ssh_key_manage))
                }

                Spacer(modifier = Modifier.height(8.dp))

                OutlinedTextField(
                    value = state.sftpRemotePath,
                    onValueChange = { viewModel.setSftpRemotePath(it) },
                    label = { Text(stringResource(R.string.settings_sftp_remote_path)) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                Spacer(modifier = Modifier.height(16.dp))

                OutlinedButton(
                    onClick = { viewModel.testConnection() },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(stringResource(R.string.settings_test_connection))
                }
            }

            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}
