package com.passwordmanager.android.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

/**
 * A filter chip bound to an optional date. Tapping opens a Material date picker; when a date is
 * set the chip shows it and exposes a clear (×). Source: parity with the desktop date filters.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DateFilterChip(label: String, value: LocalDate?, onPick: (LocalDate?) -> Unit) {
    var showPicker by remember { mutableStateOf(false) }
    FilterChip(
        selected = value != null,
        onClick = { showPicker = true },
        label = { Text(if (value != null) "$label : $value" else label) },
        trailingIcon = if (value != null) {
            {
                Icon(
                    Icons.Default.Close,
                    contentDescription = stringResource(android.R.string.cancel),
                    modifier = Modifier.size(16.dp).clickable { onPick(null) }
                )
            }
        } else null
    )
    if (showPicker) {
        val initMillis = value?.atStartOfDay(ZoneOffset.UTC)?.toInstant()?.toEpochMilli()
        val dpState = rememberDatePickerState(initialSelectedDateMillis = initMillis)
        DatePickerDialog(
            onDismissRequest = { showPicker = false },
            confirmButton = {
                TextButton(onClick = {
                    val d = dpState.selectedDateMillis?.let {
                        Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate()
                    }
                    onPick(d)
                    showPicker = false
                }) { Text(stringResource(android.R.string.ok)) }
            },
            dismissButton = {
                TextButton(onClick = { showPicker = false }) { Text(stringResource(android.R.string.cancel)) }
            }
        ) {
            DatePicker(state = dpState)
        }
    }
}
