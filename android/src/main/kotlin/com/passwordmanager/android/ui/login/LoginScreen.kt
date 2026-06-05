package com.passwordmanager.android.ui.login

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.documentfile.provider.DocumentFile
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.hilt.navigation.compose.hiltViewModel
import com.passwordmanager.android.BuildConfig
import com.passwordmanager.android.R
import com.passwordmanager.android.data.WorkspaceManager
import com.passwordmanager.android.ui.components.PasswordField
import com.passwordmanager.android.update.AndroidUpdateManager
import com.passwordmanager.android.update.UpdateResult
import com.passwordmanager.update.UpdateInfo
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LoginScreen(
    onLoginSuccess: () -> Unit,
    viewModel: LoginViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val activity = LocalContext.current as FragmentActivity

    val errorMessage = state.error?.let { key ->
        when (key) {
            "error_invalid_password" -> stringResource(R.string.error_invalid_password)
            "error_empty_password" -> stringResource(R.string.error_empty_password)
            "error_too_many_attempts" -> stringResource(R.string.error_too_many_attempts)
            else -> key
        }
    }

    val biometricErrorMessage = state.biometricError?.let { key ->
        when (key) {
            "biometric_key_invalidated" -> stringResource(R.string.biometric_key_invalidated)
            "biometric_credential_expired" -> stringResource(R.string.biometric_credential_expired)
            "biometric_lockout" -> stringResource(R.string.biometric_lockout)
            else -> key
        }
    }

    // Localized categories for new user creation
    val localizedCategories = listOf(
        stringResource(R.string.category_default_email),
        stringResource(R.string.category_default_banking),
        stringResource(R.string.category_default_social),
        stringResource(R.string.category_default_work),
        stringResource(R.string.category_default_other)
    )

    // Update check state
    val context = LocalContext.current

    // SAF folder picker: persist the grant, then switch the workspace to that tree.
    val folderPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { uri: Uri? ->
        if (uri != null) {
            try {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                )
                viewModel.switchWorkspace(WorkspaceManager.safSpec(uri.toString()))
            } catch (e: SecurityException) {
                // The grant was not persistable — leave the workspace unchanged.
            }
        }
    }

    val coroutineScope = rememberCoroutineScope()
    var isCheckingUpdate by remember { mutableStateOf(false) }
    var updateInfo by remember { mutableStateOf<UpdateInfo?>(null) }
    var showUpdateDialog by remember { mutableStateOf(false) }

    // Snackbar for user created success
    val snackbarHostState = remember { SnackbarHostState() }
    val userCreatedMsg = stringResource(R.string.login_user_created)
    val upToDateMsg = stringResource(R.string.update_up_to_date)
    val updateErrorMsg = stringResource(R.string.update_error)

    LaunchedEffect(state.createSuccess) {
        if (state.createSuccess) {
            snackbarHostState.showSnackbar(userCreatedMsg)
            viewModel.clearCreateSuccess()
        }
    }

    // Auto-trigger biometric login when enabled for user
    LaunchedEffect(state.biometricEnabledForUser, state.selectedUser) {
        if (state.biometricEnabledForUser && state.selectedUser != null) {
            viewModel.loginWithBiometric(activity, onLoginSuccess)
        }
    }

    Scaffold(snackbarHost = { SnackbarHost(snackbarHostState) }) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 32.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Spacer(modifier = Modifier.weight(1f))

            // Logo
            Image(
                painter = painterResource(R.drawable.logo_transparent),
                contentDescription = null,
                modifier = Modifier.size(120.dp)
            )

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = stringResource(R.string.app_title),
                style = MaterialTheme.typography.headlineLarge
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "V${BuildConfig.VERSION_NAME}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(32.dp))

            // Login form in ElevatedCard
            ElevatedCard(
                modifier = Modifier.fillMaxWidth(),
                elevation = CardDefaults.elevatedCardElevation(defaultElevation = 4.dp)
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // Working-folder (workspace) selector: known locations + "Choose folder…" (SAF)
                    run {
                        var workspaceExpanded by remember { mutableStateOf(false) }
                        ExposedDropdownMenuBox(
                            expanded = workspaceExpanded,
                            onExpandedChange = { workspaceExpanded = it }
                        ) {
                            OutlinedTextField(
                                value = workspaceSpecLabel(state.workspaceSpec),
                                onValueChange = {},
                                readOnly = true,
                                label = { Text(stringResource(R.string.workspace_label)) },
                                trailingIcon = {
                                    Icon(Icons.Default.ArrowDropDown, contentDescription = null)
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .menuAnchor()
                            )
                            ExposedDropdownMenu(
                                expanded = workspaceExpanded,
                                onDismissRequest = { workspaceExpanded = false }
                            ) {
                                state.workspaceOptions.forEach { spec ->
                                    DropdownMenuItem(
                                        text = { Text(workspaceSpecLabel(spec)) },
                                        onClick = {
                                            viewModel.switchWorkspace(spec)
                                            workspaceExpanded = false
                                        }
                                    )
                                }
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.workspace_choose_folder)) },
                                    onClick = {
                                        workspaceExpanded = false
                                        folderPicker.launch(null)
                                    }
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))
                    }

                    // User dropdown
                    var dropdownExpanded by remember { mutableStateOf(false) }

                    ExposedDropdownMenuBox(
                        expanded = dropdownExpanded,
                        onExpandedChange = { dropdownExpanded = it }
                    ) {
                        OutlinedTextField(
                            value = state.selectedUser ?: "",
                            onValueChange = {},
                            readOnly = true,
                            label = { Text(stringResource(R.string.login_username)) },
                            trailingIcon = {
                                Icon(Icons.Default.ArrowDropDown, contentDescription = null)
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .menuAnchor()
                        )
                        ExposedDropdownMenu(
                            expanded = dropdownExpanded,
                            onDismissRequest = { dropdownExpanded = false }
                        ) {
                            state.users.forEach { user ->
                                DropdownMenuItem(
                                    text = { Text(user) },
                                    onClick = {
                                        viewModel.selectUser(user)
                                        dropdownExpanded = false
                                    }
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    PasswordField(
                        value = state.password,
                        onValueChange = { viewModel.updatePassword(it) },
                        label = stringResource(R.string.login_password),
                        modifier = Modifier.fillMaxWidth(),
                        isError = errorMessage != null
                    )

                    if (errorMessage != null) {
                        Text(
                            text = errorMessage,
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }

                    if (biometricErrorMessage != null) {
                        Text(
                            text = biometricErrorMessage,
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(24.dp))

                    Button(
                        onClick = { viewModel.login(onLoginSuccess) },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !state.isLoading && !state.isRateLimited && state.selectedUser != null
                    ) {
                        if (state.isLoading) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.onPrimary
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                        }
                        Text(stringResource(R.string.login_button))
                    }

                    // Biometric unlock button
                    if (state.biometricEnabledForUser) {
                        Spacer(modifier = Modifier.height(12.dp))

                        OutlinedButton(
                            onClick = { viewModel.loginWithBiometric(activity, onLoginSuccess) },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(
                                Icons.Default.Fingerprint,
                                contentDescription = null,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(stringResource(R.string.biometric_unlock))
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    TextButton(onClick = { viewModel.showCreateDialog() }) {
                        Text(stringResource(R.string.login_create_user))
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Check for updates button
            TextButton(
                onClick = {
                    isCheckingUpdate = true
                    coroutineScope.launch {
                        when (val result = AndroidUpdateManager.checkForUpdate()) {
                            is UpdateResult.Available -> {
                                updateInfo = result.info
                                showUpdateDialog = true
                            }
                            is UpdateResult.UpToDate -> {
                                snackbarHostState.showSnackbar(upToDateMsg)
                            }
                            is UpdateResult.Error -> {
                                snackbarHostState.showSnackbar(updateErrorMsg)
                            }
                        }
                        isCheckingUpdate = false
                    }
                },
                enabled = !isCheckingUpdate
            ) {
                if (isCheckingUpdate) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(stringResource(R.string.update_checking))
                } else {
                    Text(stringResource(R.string.update_check))
                }
            }

            Spacer(modifier = Modifier.weight(1f))
        }
    }

    // Update available dialog
    if (showUpdateDialog && updateInfo != null) {
        AlertDialog(
            onDismissRequest = { showUpdateDialog = false },
            title = { Text(stringResource(R.string.update_available, updateInfo!!.version)) },
            confirmButton = {
                TextButton(onClick = {
                    showUpdateDialog = false
                    AndroidUpdateManager.openReleasePage(context, updateInfo!!)
                }) {
                    Text(stringResource(R.string.update_download))
                }
            },
            dismissButton = {
                TextButton(onClick = { showUpdateDialog = false }) {
                    Text(stringResource(R.string.update_dismiss))
                }
            }
        )
    }

    // Biometric enrollment dialog
    if (state.showBiometricEnrollDialog) {
        AlertDialog(
            onDismissRequest = { viewModel.dismissBiometricEnrollment() },
            title = { Text(stringResource(R.string.biometric_enroll_title)) },
            text = { Text(stringResource(R.string.biometric_enroll_message)) },
            confirmButton = {
                TextButton(onClick = { viewModel.enrollBiometric(activity) }) {
                    Text(stringResource(R.string.biometric_enable))
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.dismissBiometricEnrollment() }) {
                    Text(stringResource(R.string.biometric_skip))
                }
            }
        )
    }

    // Workspace switch / migration dialog
    if (state.pendingWorkspaceSwitch != null) {
        AlertDialog(
            onDismissRequest = { viewModel.cancelWorkspaceSwitch() },
            title = { Text(stringResource(R.string.workspace_migrate_title)) },
            text = {
                Text(stringResource(R.string.workspace_migrate_message, state.pendingWorkspaceVaultCount))
            },
            confirmButton = {
                TextButton(onClick = { viewModel.confirmWorkspaceSwitch(migrate = true) }) {
                    Text(stringResource(R.string.workspace_migrate_move))
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.confirmWorkspaceSwitch(migrate = false) }) {
                    Text(stringResource(R.string.workspace_migrate_keep))
                }
            }
        )
    }

    // Create user dialog
    if (state.showCreateDialog) {
        CreateUserDialog(
            state = state,
            onDismiss = { viewModel.dismissCreateDialog() },
            onUsernameChange = { viewModel.updateNewUsername(it) },
            onPasswordChange = { viewModel.updateNewPassword(it) },
            onConfirmChange = { viewModel.updateNewPasswordConfirm(it) },
            onCreate = { viewModel.createUser(localizedCategories) }
        )
    }
}

@Composable
private fun workspaceSpecLabel(spec: String): String = when {
    spec == WorkspaceManager.SPEC_EXTERNAL -> stringResource(R.string.workspace_external)
    spec.startsWith(WorkspaceManager.SAF_PREFIX) -> {
        val context = LocalContext.current
        val fallback = stringResource(R.string.workspace_folder)
        // Resolve the folder name once per spec (a ContentResolver query), not every recomposition.
        remember(spec) {
            DocumentFile.fromTreeUri(context, Uri.parse(spec.removePrefix(WorkspaceManager.SAF_PREFIX)))
                ?.name ?: fallback
        }
    }
    else -> stringResource(R.string.workspace_internal)
}

@Composable
private fun CreateUserDialog(
    state: LoginUiState,
    onDismiss: () -> Unit,
    onUsernameChange: (String) -> Unit,
    onPasswordChange: (String) -> Unit,
    onConfirmChange: (String) -> Unit,
    onCreate: () -> Unit
) {
    val createErrorMessage = state.createError?.let { key ->
        when (key) {
            "error_empty_username" -> stringResource(R.string.error_empty_username)
            "error_invalid_username" -> stringResource(R.string.error_invalid_username)
            "error_user_exists" -> stringResource(R.string.error_user_exists)
            "security_password_mismatch" -> stringResource(R.string.security_password_mismatch)
            "security_password_requirements" -> stringResource(R.string.security_password_requirements)
            else -> key
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.login_create_user)) },
        text = {
            Column {
                OutlinedTextField(
                    value = state.newUsername,
                    onValueChange = onUsernameChange,
                    label = { Text(stringResource(R.string.login_new_username)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))
                PasswordField(
                    value = state.newPassword,
                    onValueChange = onPasswordChange,
                    label = stringResource(R.string.security_new_password),
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))
                PasswordField(
                    value = state.newPasswordConfirm,
                    onValueChange = onConfirmChange,
                    label = stringResource(R.string.security_confirm_password),
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = stringResource(R.string.security_password_requirements),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = stringResource(R.string.security_no_recovery),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.error
                )
                if (createErrorMessage != null) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = createErrorMessage,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onCreate, enabled = !state.isLoading) {
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
