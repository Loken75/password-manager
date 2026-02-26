package com.passwordmanager.android.ui.sync

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.passwordmanager.android.R
import com.passwordmanager.sync.EntryMerger

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConflictResolutionScreen(
    conflicts: List<EntryMerger.Conflict>,
    onResolve: (Map<String, Boolean>) -> Unit,
    onBack: () -> Unit
) {
    // Map of entryId -> keepLocal (true = local, false = remote)
    val resolutions = remember {
        mutableStateMapOf<String, Boolean>().apply {
            conflicts.forEach { put(it.localEntry.id, true) }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.sync_merge_conflicts)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                    }
                }
            )
        },
        bottomBar = {
            Surface(tonalElevation = 3.dp) {
                Button(
                    onClick = { onResolve(resolutions.toMap()) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                ) {
                    Text(stringResource(R.string.sync_merge_resolve))
                }
            }
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(conflicts) { conflict ->
                ConflictCard(
                    conflict = conflict,
                    keepLocal = resolutions[conflict.localEntry.id] ?: true,
                    onChooseLocal = { resolutions[conflict.localEntry.id] = true },
                    onChooseRemote = { resolutions[conflict.localEntry.id] = false }
                )
            }
        }
    }
}

@Composable
private fun ConflictCard(
    conflict: EntryMerger.Conflict,
    keepLocal: Boolean,
    onChooseLocal: () -> Unit,
    onChooseRemote: () -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = conflict.localEntry.title ?: conflict.remoteEntry.title ?: "",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Local version
            Row(modifier = Modifier.fillMaxWidth()) {
                RadioButton(selected = keepLocal, onClick = onChooseLocal)
                Column(modifier = Modifier.weight(1f).padding(start = 8.dp)) {
                    Text(
                        text = stringResource(R.string.sync_merge_local_version),
                        style = MaterialTheme.typography.labelLarge
                    )
                    Text(
                        text = "${conflict.localEntry.username ?: ""} — ${conflict.localEntry.updatedAt ?: ""}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Remote version
            Row(modifier = Modifier.fillMaxWidth()) {
                RadioButton(selected = !keepLocal, onClick = onChooseRemote)
                Column(modifier = Modifier.weight(1f).padding(start = 8.dp)) {
                    Text(
                        text = stringResource(R.string.sync_merge_remote_version),
                        style = MaterialTheme.typography.labelLarge
                    )
                    Text(
                        text = "${conflict.remoteEntry.username ?: ""} — ${conflict.remoteEntry.updatedAt ?: ""}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}
