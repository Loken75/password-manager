package com.passwordmanager.android.ui.vault

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.imePadding
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.hilt.navigation.compose.hiltViewModel
import com.passwordmanager.android.R
import com.passwordmanager.android.ui.components.PasswordField
import com.passwordmanager.vault.CardType

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CardEditScreen(
    entryId: String?,
    onBack: () -> Unit,
    onSaved: () -> Unit,
    viewModel: CardEditViewModel = hiltViewModel()
) {
    LaunchedEffect(entryId) { viewModel.loadEntry(entryId) }

    val state by viewModel.uiState.collectAsStateWithLifecycle()

    val cardTypes = listOf(
        CardType.VISA to stringResource(R.string.card_type_visa),
        CardType.MASTERCARD to stringResource(R.string.card_type_mastercard),
        CardType.AMEX to stringResource(R.string.card_type_amex),
        CardType.CB to stringResource(R.string.card_type_cb),
        CardType.OTHER to stringResource(R.string.card_type_other)
    )

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        if (state.isNew) stringResource(R.string.vault_new_entry)
                        else stringResource(R.string.vault_edit_entry)
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                    }
                },
                actions = {
                    IconButton(onClick = { viewModel.toggleFavorite() }) {
                        Icon(
                            imageVector = if (state.favorite) Icons.Default.Star else Icons.Default.StarBorder,
                            contentDescription = stringResource(R.string.entry_toggle_favorite),
                            tint = if (state.favorite) Color(0xFFFFC107) else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    TextButton(onClick = {
                        if (viewModel.save()) onSaved()
                    }) {
                        Text(stringResource(R.string.common_save))
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .imePadding()
                .padding(16.dp)
                .verticalScroll(rememberScrollState())
        ) {
            // Title
            OutlinedTextField(
                value = state.title,
                onValueChange = { viewModel.updateTitle(it) },
                label = { Text(stringResource(R.string.entry_title)) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                isError = state.error == "title_required"
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Cardholder Name
            OutlinedTextField(
                value = state.cardholderName,
                onValueChange = { viewModel.updateCardholderName(it) },
                label = { Text(stringResource(R.string.entry_cardholder_name)) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Card Number (masked)
            PasswordField(
                value = state.cardNumber,
                onValueChange = { viewModel.updateCardNumber(it) },
                label = stringResource(R.string.entry_card_number),
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Expiry Date
            OutlinedTextField(
                value = state.expiryDate,
                onValueChange = { viewModel.updateExpiryDate(it) },
                label = { Text(stringResource(R.string.entry_expiry_date)) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                placeholder = { Text("MM/YY") }
            )

            Spacer(modifier = Modifier.height(12.dp))

            // CVV (masked)
            PasswordField(
                value = state.cvv,
                onValueChange = { viewModel.updateCvv(it) },
                label = stringResource(R.string.entry_cvv),
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Card PIN (masked)
            PasswordField(
                value = state.cardPin,
                onValueChange = { viewModel.updateCardPin(it) },
                label = stringResource(R.string.entry_card_pin),
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Card Type (dropdown)
            var cardTypeExpanded by remember { mutableStateOf(false) }
            ExposedDropdownMenuBox(
                expanded = cardTypeExpanded,
                onExpandedChange = { cardTypeExpanded = it }
            ) {
                OutlinedTextField(
                    value = cardTypes.find { it.first == state.cardType }?.second ?: state.cardType,
                    onValueChange = {},
                    readOnly = true,
                    label = { Text(stringResource(R.string.entry_card_type)) },
                    modifier = Modifier.fillMaxWidth().menuAnchor(),
                    trailingIcon = {
                        ExposedDropdownMenuDefaults.TrailingIcon(expanded = cardTypeExpanded)
                    },
                    singleLine = true
                )
                ExposedDropdownMenu(
                    expanded = cardTypeExpanded,
                    onDismissRequest = { cardTypeExpanded = false }
                ) {
                    cardTypes.forEach { (value, label) ->
                        DropdownMenuItem(
                            text = { Text(label) },
                            onClick = {
                                viewModel.updateCardType(value)
                                cardTypeExpanded = false
                            }
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Notes
            OutlinedTextField(
                value = state.notes,
                onValueChange = { viewModel.updateNotes(it) },
                label = { Text(stringResource(R.string.entry_notes)) },
                modifier = Modifier.fillMaxWidth(),
                minLines = 3,
                maxLines = 6
            )
        }
    }
}
