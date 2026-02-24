package com.passwordmanager.android.ui.settings

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.passwordmanager.android.R
import com.passwordmanager.android.ui.components.PasswordField
import com.passwordmanager.android.ui.components.PasswordStrengthBar

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChangeMasterPasswordScreen(
    onBack: () -> Unit,
    onChanged: () -> Unit,
    viewModel: ChangeMasterPasswordViewModel = viewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = SnackbarHostState()

    val successMsg = stringResource(R.string.security_password_changed)

    LaunchedEffect(state.success) {
        if (state.success) {
            snackbarHostState.showSnackbar(successMsg)
            onChanged()
        }
    }

    val errorMessage = state.error?.let { key ->
        when (key) {
            "error_invalid_password" -> stringResource(R.string.error_invalid_password)
            "security_password_mismatch" -> stringResource(R.string.security_password_mismatch)
            "security_password_requirements" -> stringResource(R.string.security_password_requirements)
            else -> key
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings_change_master)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
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
        ) {
            PasswordField(
                value = state.oldPassword,
                onValueChange = { viewModel.updateOldPassword(it) },
                label = stringResource(R.string.security_old_password),
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(16.dp))

            PasswordField(
                value = state.newPassword,
                onValueChange = { viewModel.updateNewPassword(it) },
                label = stringResource(R.string.security_new_password),
                modifier = Modifier.fillMaxWidth()
            )

            if (state.newPassword.isNotBlank()) {
                Spacer(modifier = Modifier.height(8.dp))
                PasswordStrengthBar(password = state.newPassword.toCharArray())
            }

            Spacer(modifier = Modifier.height(16.dp))

            PasswordField(
                value = state.confirmPassword,
                onValueChange = { viewModel.updateConfirmPassword(it) },
                label = stringResource(R.string.security_confirm_password),
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(8.dp))

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

            if (errorMessage != null) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = errorMessage,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            Button(
                onClick = { viewModel.changePassword() },
                modifier = Modifier.fillMaxWidth(),
                enabled = !state.isLoading
            ) {
                if (state.isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                }
                Text(stringResource(R.string.common_save))
            }
        }
    }
}
