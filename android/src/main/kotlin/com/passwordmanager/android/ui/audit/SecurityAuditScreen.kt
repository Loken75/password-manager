package com.passwordmanager.android.ui.audit

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.hilt.navigation.compose.hiltViewModel
import com.passwordmanager.android.R
import com.passwordmanager.vault.VaultEntry

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SecurityAuditScreen(
    onBack: () -> Unit,
    onEntryClick: (String) -> Unit,
    showBackNavigation: Boolean = true,
    viewModel: SecurityAuditViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.audit_title)) },
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
                .verticalScroll(rememberScrollState())
        ) {
            // Summary card
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = if (state.totalIssues == 0)
                        MaterialTheme.colorScheme.primaryContainer
                    else MaterialTheme.colorScheme.errorContainer
                )
            ) {
                Text(
                    text = if (state.totalIssues == 0)
                        stringResource(R.string.audit_no_issues)
                    else stringResource(R.string.audit_issues_found)
                        .replace("%1\$d", state.totalIssues.toString()),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(16.dp)
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Weak passwords section
            AuditSection(
                title = "${stringResource(R.string.audit_weak_passwords)} (${state.weakEntries.size})",
                entries = state.weakEntries,
                onEntryClick = onEntryClick
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Duplicate passwords section
            AuditSection(
                title = "${stringResource(R.string.audit_duplicate_passwords)} (${state.duplicateEntries.size})",
                entries = state.duplicateEntries,
                onEntryClick = onEntryClick
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Old passwords section
            AuditSection(
                title = "${stringResource(R.string.audit_old_passwords)
                    .replace("%1\$d", state.passwordExpiryDays.toString())} (${state.oldEntries.size})",
                entries = state.oldEntries,
                onEntryClick = onEntryClick
            )
        }
    }
}

@Composable
private fun AuditSection(
    title: String,
    entries: List<VaultEntry>,
    onEntryClick: (String) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }

    Card(modifier = Modifier.fillMaxWidth()) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { expanded = !expanded }
                    .padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(text = title, style = MaterialTheme.typography.titleMedium)
                Icon(
                    imageVector = if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = null
                )
            }

            AnimatedVisibility(visible = expanded) {
                Column {
                    HorizontalDivider()
                    if (entries.isEmpty()) {
                        Text(
                            text = stringResource(R.string.audit_no_issues),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(16.dp)
                        )
                    } else {
                        entries.forEach { entry ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { onEntryClick(entry.id) }
                                    .padding(horizontal = 16.dp, vertical = 12.dp)
                            ) {
                                Column {
                                    Text(
                                        text = entry.title ?: "",
                                        style = MaterialTheme.typography.bodyLarge
                                    )
                                    if (!entry.username.isNullOrBlank()) {
                                        Text(
                                            text = entry.username,
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                            HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                        }
                    }
                }
            }
        }
    }
}
