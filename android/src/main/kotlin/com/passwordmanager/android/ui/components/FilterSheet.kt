package com.passwordmanager.android.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.FlowRowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.passwordmanager.android.R
import com.passwordmanager.android.ui.theme.spacing

/**
 * Modal bottom sheet that hosts list filters, keeping the screen's filter bar off the list itself.
 * Filters are applied live by the caller (each chip mutates the ViewModel directly); the sheet only
 * reflects the running [resultCount] and offers a reset shortcut, mirroring the Airbnb/Play Store
 * "filter in a sheet, see results update" pattern. Sections are supplied via [content] so the
 * password and app screens can share the same scaffold while exposing their own filter sets.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FilterSheet(
    resultCount: Int,
    activeFilterCount: Int,
    onReset: () -> Unit,
    onDismiss: () -> Unit,
    sheetState: SheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    content: @Composable ColumnScope.() -> Unit
) {
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = MaterialTheme.spacing.lg)
        ) {
            // Header: title + reset shortcut (disabled when nothing is active)
            androidx.compose.foundation.layout.Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(R.string.filter_title),
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.weight(1f)
                )
                TextButton(onClick = onReset, enabled = activeFilterCount > 0) {
                    Text(stringResource(R.string.filter_reset))
                }
            }

            // Filter sections — capped + scrollable so tall sets never push the CTA off-screen
            Column(
                modifier = Modifier
                    .heightIn(max = 420.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.lg)
            ) {
                content()
            }

            Spacer(Modifier.height(MaterialTheme.spacing.lg))
            Button(
                onClick = onDismiss,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(stringResource(R.string.filter_show_results, resultCount))
            }
            Spacer(Modifier.height(MaterialTheme.spacing.lg))
        }
    }
}

/**
 * A labelled group of filter chips that wraps onto multiple lines (no horizontal scrolling).
 * [content] runs in a [FlowRowScope]; drop [androidx.compose.material3.FilterChip]s straight in.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun FilterSection(
    title: String,
    content: @Composable FlowRowScope.() -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.sm)) {
        Text(
            text = title,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.sm),
            verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.sm)
        ) {
            content()
        }
    }
}
