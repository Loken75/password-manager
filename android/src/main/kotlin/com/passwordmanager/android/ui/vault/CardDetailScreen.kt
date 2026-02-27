package com.passwordmanager.android.ui.vault

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
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
import androidx.lifecycle.ProcessLifecycleOwner
import androidx.lifecycle.lifecycleScope
import com.passwordmanager.android.R
import com.passwordmanager.android.ui.components.ConfirmDialog
import com.passwordmanager.util.SecureWiper
import com.passwordmanager.vault.CardType
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CardDetailScreen(
    entryId: String,
    onBack: () -> Unit,
    onEdit: () -> Unit,
    onDeleted: () -> Unit,
    viewModel: CardDetailViewModel = hiltViewModel()
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
                    if (entry != null) {
                        IconButton(onClick = { viewModel.toggleFavorite(entryId) }) {
                            Icon(
                                imageVector = if (entry.isFavorite) Icons.Default.Star else Icons.Default.StarBorder,
                                contentDescription = stringResource(R.string.entry_toggle_favorite),
                                tint = if (entry.isFavorite) Color(0xFFFFC107) else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
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
            // -- Section: Card Information --
            Text(
                text = stringResource(R.string.detail_section_info),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(bottom = 12.dp)
            )

            // Cardholder Name
            if (!entry.cardholderName.isNullOrBlank()) {
                DetailRow(
                    icon = Icons.Default.Person,
                    label = stringResource(R.string.entry_cardholder_name),
                    value = entry.cardholderName,
                    onCopy = { copyToClipboard(context, entry.cardholderName, 30) }
                )
                Spacer(modifier = Modifier.height(16.dp))
            }

            // Card Type
            if (!entry.cardType.isNullOrBlank()) {
                DetailRow(
                    icon = Icons.Default.CreditCard,
                    label = stringResource(R.string.entry_card_type),
                    value = cardTypeDisplayName(entry.cardType)
                )
                Spacer(modifier = Modifier.height(16.dp))
            }

            // Expiry Date
            if (!entry.expiryDate.isNullOrBlank()) {
                DetailRow(
                    icon = Icons.Default.CalendarMonth,
                    label = stringResource(R.string.entry_expiry_date),
                    value = entry.expiryDate,
                    onCopy = { copyToClipboard(context, entry.expiryDate, 30) }
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

            // Card Number
            DetailRow(
                icon = Icons.Default.CreditCard,
                label = stringResource(R.string.entry_card_number),
                value = if (state.cardNumberVisible) {
                    entry.cardNumber?.let { cn ->
                        val text = String(cn)
                        SecureWiper.wipe(cn)
                        text
                    } ?: ""
                } else "\u2022\u2022\u2022\u2022 \u2022\u2022\u2022\u2022 \u2022\u2022\u2022\u2022 ${entry.last4Digits ?: "\u2022\u2022\u2022\u2022"}",
                onCopy = {
                    entry.cardNumber?.let { cn ->
                        val text = String(cn)
                        copyToClipboard(context, text, 30, sensitive = true)
                        SecureWiper.wipe(cn)
                    }
                },
                trailing = {
                    IconButton(onClick = { viewModel.toggleCardNumberVisibility() }) {
                        Icon(
                            imageVector = if (state.cardNumberVisible) Icons.Default.VisibilityOff
                                else Icons.Default.Visibility,
                            contentDescription = stringResource(R.string.entry_show_password)
                        )
                    }
                }
            )

            Spacer(modifier = Modifier.height(16.dp))

            // CVV
            DetailRow(
                icon = Icons.Default.Lock,
                label = stringResource(R.string.entry_cvv),
                value = if (state.cvvVisible) {
                    entry.cvv?.let { cvvChars ->
                        val text = String(cvvChars)
                        SecureWiper.wipe(cvvChars)
                        text
                    } ?: ""
                } else "\u2022".repeat(3),
                onCopy = {
                    entry.cvv?.let { cvv ->
                        val text = String(cvv)
                        copyToClipboard(context, text, 30, sensitive = true)
                        SecureWiper.wipe(cvv)
                    }
                },
                trailing = {
                    IconButton(onClick = { viewModel.toggleCvvVisibility() }) {
                        Icon(
                            imageVector = if (state.cvvVisible) Icons.Default.VisibilityOff
                                else Icons.Default.Visibility,
                            contentDescription = stringResource(R.string.entry_show_password)
                        )
                    }
                }
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Card PIN
            DetailRow(
                icon = Icons.Default.Pin,
                label = stringResource(R.string.entry_card_pin),
                value = if (state.pinVisible) {
                    entry.cardPin?.let { pinChars ->
                        val text = String(pinChars)
                        SecureWiper.wipe(pinChars)
                        text
                    } ?: ""
                } else "\u2022".repeat(4),
                onCopy = {
                    entry.cardPin?.let { pin ->
                        val text = String(pin)
                        copyToClipboard(context, text, 30, sensitive = true)
                        SecureWiper.wipe(pin)
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
                DetailRow(
                    icon = Icons.AutoMirrored.Filled.Notes,
                    label = stringResource(R.string.entry_notes),
                    value = entry.notes,
                    onCopy = { copyToClipboard(context, entry.notes, 30) }
                )
                Spacer(modifier = Modifier.height(16.dp))
            }

            Spacer(modifier = Modifier.height(8.dp))
            HorizontalDivider()
            Spacer(modifier = Modifier.height(12.dp))

            // -- Footer: Timestamps --
            DetailRow(
                icon = Icons.Default.CalendarToday,
                label = stringResource(R.string.entry_created),
                value = entry.createdAt ?: ""
            )
            Spacer(modifier = Modifier.height(8.dp))
            DetailRow(
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

/**
 * Maps an internal card type key (e.g. "VISA") to a localized display name.
 */
@Composable
private fun cardTypeDisplayName(key: String?): String {
    return when (key) {
        CardType.VISA       -> stringResource(R.string.card_type_visa)
        CardType.MASTERCARD -> stringResource(R.string.card_type_mastercard)
        CardType.AMEX       -> stringResource(R.string.card_type_amex)
        CardType.CB         -> stringResource(R.string.card_type_cb)
        CardType.OTHER      -> stringResource(R.string.card_type_other)
        else                -> key ?: ""
    }
}

@Composable
private fun DetailRow(
    label: String,
    value: String,
    icon: ImageVector? = null,
    onCopy: (() -> Unit)? = null,
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
            Text(
                text = value,
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.padding(top = 2.dp)
            )
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

private fun copyToClipboard(
    context: Context,
    text: String,
    clearAfterSeconds: Int,
    sensitive: Boolean = false
) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    val clip = ClipData.newPlainText("", text)
    if (sensitive && android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
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
