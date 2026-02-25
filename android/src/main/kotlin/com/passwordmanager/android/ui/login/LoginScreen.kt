package com.passwordmanager.android.ui.login

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.hilt.navigation.compose.hiltViewModel
import com.passwordmanager.android.R
import com.passwordmanager.android.ui.components.PasswordField

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LoginScreen(
    onLoginSuccess: () -> Unit,
    viewModel: LoginViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    val errorMessage = state.error?.let { key ->
        when (key) {
            "error_invalid_password" -> stringResource(R.string.error_invalid_password)
            "error_empty_password" -> stringResource(R.string.error_empty_password)
            "error_too_many_attempts" -> stringResource(R.string.error_too_many_attempts)
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

    // Snackbar for user created success
    val snackbarHostState = remember { SnackbarHostState() }
    val userCreatedMsg = stringResource(R.string.login_user_created)

    LaunchedEffect(state.createSuccess) {
        if (state.createSuccess) {
            snackbarHostState.showSnackbar(userCreatedMsg)
            viewModel.clearCreateSuccess()
        }
    }

    Scaffold(snackbarHost = { SnackbarHost(snackbarHostState) }) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = stringResource(R.string.app_title),
                style = MaterialTheme.typography.headlineLarge
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = stringResource(R.string.app_version),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(48.dp))

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

            Spacer(modifier = Modifier.height(16.dp))

            TextButton(onClick = { viewModel.showCreateDialog() }) {
                Text(stringResource(R.string.login_create_user))
            }
        }
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
