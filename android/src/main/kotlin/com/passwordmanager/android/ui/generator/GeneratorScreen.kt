package com.passwordmanager.android.ui.generator

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.hilt.navigation.compose.hiltViewModel
import com.passwordmanager.android.R
import com.passwordmanager.android.ui.components.PasswordStrengthBar
import androidx.lifecycle.ProcessLifecycleOwner
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GeneratorScreen(
    onBack: () -> Unit,
    onUsePassword: ((CharArray) -> Unit)? = null,
    showBackNavigation: Boolean = true,
    viewModel: GeneratorViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.generator_title)) },
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
        ) {
            // Generated password display
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                )
            ) {
                Text(
                    text = String(state.password),
                    style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                    modifier = Modifier.padding(16.dp),
                    maxLines = 3
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            PasswordStrengthBar(password = state.password)

            Spacer(modifier = Modifier.height(24.dp))

            // Length slider
            Text(
                text = "${stringResource(R.string.generator_length)}: ${state.length}",
                style = MaterialTheme.typography.titleMedium
            )
            Slider(
                value = state.length.toFloat(),
                onValueChange = { viewModel.setLength(it.toInt()) },
                valueRange = 8f..128f,
                steps = 0
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Character set switches
            SwitchRow(stringResource(R.string.generator_uppercase), state.useUppercase) {
                viewModel.toggleUppercase()
            }
            SwitchRow(stringResource(R.string.generator_lowercase), state.useLowercase) {
                viewModel.toggleLowercase()
            }
            SwitchRow(stringResource(R.string.generator_digits), state.useDigits) {
                viewModel.toggleDigits()
            }
            SwitchRow(stringResource(R.string.generator_special), state.useSpecial) {
                viewModel.toggleSpecial()
            }
            SwitchRow(stringResource(R.string.generator_exclude_ambiguous), state.excludeAmbiguous) {
                viewModel.toggleExcludeAmbiguous()
            }

            Spacer(modifier = Modifier.weight(1f))

            // Action buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = { viewModel.generate() },
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.Refresh, contentDescription = null)
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(stringResource(R.string.generator_generate))
                }

                OutlinedButton(
                    onClick = { copyToClipboard(context, state.password) },
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.ContentCopy, contentDescription = null)
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(stringResource(R.string.generator_copy))
                }

                if (onUsePassword != null) {
                    Button(
                        onClick = { onUsePassword(state.password.clone()) },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(stringResource(R.string.generator_use))
                    }
                }
            }
        }
    }
}

@Composable
private fun SwitchRow(label: String, checked: Boolean, onToggle: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, style = MaterialTheme.typography.bodyLarge)
        Switch(checked = checked, onCheckedChange = { onToggle() })
    }
}

private fun copyToClipboard(context: Context, password: CharArray, clearAfterSeconds: Int = 30) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    val clip = ClipData.newPlainText("", String(password))
    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
        clip.description.extras = android.os.PersistableBundle().apply {
            putBoolean("android.content.extra.IS_SENSITIVE", true)
        }
    }
    clipboard.setPrimaryClip(clip)

    ProcessLifecycleOwner.get().lifecycleScope.launch {
        delay(clearAfterSeconds * 1000L)
        clipboard.setPrimaryClip(ClipData.newPlainText("", ""))
    }
}
