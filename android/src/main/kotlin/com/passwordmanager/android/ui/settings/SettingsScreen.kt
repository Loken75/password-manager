package com.passwordmanager.android.ui.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
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
import com.passwordmanager.config.ThemeMode

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onChangeMasterPassword: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings_title)) },
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

            Spacer(modifier = Modifier.height(24.dp))

            // Change master password link
            OutlinedButton(
                onClick = onChangeMasterPassword,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(stringResource(R.string.settings_change_master))
            }
        }
    }
}
