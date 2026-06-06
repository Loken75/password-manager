package com.passwordmanager.android.ui.vault

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Notes
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.hilt.navigation.compose.hiltViewModel
import com.passwordmanager.android.R
import com.passwordmanager.android.ui.components.ConfirmDialog
import com.passwordmanager.android.ui.theme.appColors
import com.passwordmanager.util.SecureWiper
import androidx.lifecycle.ProcessLifecycleOwner
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppDetailScreen(
    entryId: String,
    onBack: () -> Unit,
    onEdit: () -> Unit,
    onDeleted: () -> Unit,
    onDuplicated: ((String) -> Unit)? = null,
    viewModel: AppDetailViewModel = hiltViewModel()
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
                    // Favorite star toggle
                    if (entry != null) {
                        IconButton(onClick = { viewModel.toggleFavorite(entryId) }) {
                            Icon(
                                imageVector = if (entry.isFavorite) Icons.Default.Star else Icons.Default.StarBorder,
                                contentDescription = stringResource(R.string.entry_toggle_favorite),
                                tint = if (entry.isFavorite) MaterialTheme.appColors.favorite else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    val dupPrefix = stringResource(R.string.menu_duplicate_prefix)
                    IconButton(onClick = {
                        val dupId = viewModel.duplicateEntry(entryId, dupPrefix)
                        if (dupId != null) onDuplicated?.invoke(dupId)
                    }) {
                        Icon(Icons.Default.ContentCopy, contentDescription = stringResource(R.string.menu_duplicate))
                    }
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
            // -- Section: Information --
            Text(
                text = stringResource(R.string.detail_section_info),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(bottom = 12.dp)
            )

            // Username
            if (!entry.username.isNullOrBlank()) {
                AppDetailRow(
                    icon = Icons.Default.Person,
                    label = stringResource(R.string.entry_username),
                    value = entry.username,
                    onCopy = { copyToClipboardSecure(context, entry.username, 30) }
                )
                Spacer(modifier = Modifier.height(16.dp))
            }

            Spacer(modifier = Modifier.height(8.dp))
            HorizontalDivider()
            Spacer(modifier = Modifier.height(16.dp))

            // -- Section: Security --
            Text(
                text = stringResource(R.string.detail_section_security),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(bottom = 12.dp)
            )

            // PIN
            AppDetailRow(
                icon = Icons.Default.Pin,
                label = stringResource(R.string.entry_pin),
                value = if (state.pinVisible) {
                    entry.pin?.let { pinChars ->
                        val text = String(pinChars)
                        SecureWiper.wipe(pinChars)
                        text
                    } ?: ""
                } else "\u2022".repeat(8),
                onCopy = {
                    entry.pin?.let { pin ->
                        val text = String(pin)
                        SecureWiper.wipe(pin)
                        copyToClipboardSecure(context, text, 30)
                    }
                },
                trailing = {
                    IconButton(onClick = { viewModel.togglePinVisibility() }) {
                        Icon(
                            imageVector = if (state.pinVisible) Icons.Default.VisibilityOff
                            else Icons.Default.Visibility,
                            contentDescription = stringResource(R.string.entry_show_password)
                        )
                    }
                }
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Notes
            if (!entry.notes.isNullOrBlank()) {
                AppDetailRow(
                    icon = Icons.AutoMirrored.Filled.Notes,
                    label = stringResource(R.string.entry_notes),
                    value = entry.notes,
                    onCopy = { copyToClipboardSecure(context, entry.notes, 30) }
                )
                Spacer(modifier = Modifier.height(16.dp))
            }

            Spacer(modifier = Modifier.height(8.dp))
            HorizontalDivider()
            Spacer(modifier = Modifier.height(12.dp))

            // -- Footer: Timestamps --
            AppDetailRow(
                icon = Icons.Default.CalendarToday,
                label = stringResource(R.string.entry_created),
                value = entry.createdAt ?: ""
            )
            Spacer(modifier = Modifier.height(8.dp))
            AppDetailRow(
                icon = Icons.Default.Update,
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
private fun AppDetailRow(
    label: String,
    value: String,
    icon: ImageVector? = null,
    onCopy: (() -> Unit)? = null,
    onClick: (() -> Unit)? = null,
    trailing: @Composable (() -> Unit)? = null
) {
    Row(modifier = Modifier.fillMaxWidth()) {
        if (icon != null) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .padding(top = 2.dp, end = 12.dp)
                    .size(20.dp)
            )
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (onClick != null) {
                Text(
                    text = value,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .padding(top = 2.dp)
                        .clickable { onClick() }
                )
            } else {
                Text(
                    text = value,
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.padding(top = 2.dp)
                )
            }
        }
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

private fun copyToClipboardSecure(context: Context, text: String, clearAfterSeconds: Int) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    val clip = ClipData.newPlainText("", text)
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
