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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.hilt.navigation.compose.hiltViewModel
import com.passwordmanager.android.R
import com.passwordmanager.android.ui.theme.appColors
import com.passwordmanager.vault.PasswordEntry

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
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // ── Vue d'ensemble ──
            SectionLabel(stringResource(R.string.audit_overview))
            StatRow {
                StatCard(
                    Modifier.weight(1f),
                    stringResource(R.string.audit_stat_score),
                    "${state.score20}/20",
                    statusColor(state.score)
                )
                StatCard(
                    Modifier.weight(1f),
                    stringResource(R.string.audit_stat_to_fix),
                    state.totalIssues.toString(),
                    if (state.totalIssues > 0) MaterialTheme.appColors.statusWeak else MaterialTheme.appColors.statusStrong
                )
                StatCard(
                    Modifier.weight(1f),
                    stringResource(R.string.audit_stat_strong),
                    state.strongEntries.size.toString(),
                    if (state.strongEntries.isNotEmpty()) MaterialTheme.appColors.statusStrong else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // ── À risque ──
            SectionLabel(stringResource(R.string.audit_at_risk))
            AuditSection(
                title = "${stringResource(R.string.audit_weak_passwords)} (${state.weakEntries.size})",
                entries = state.weakEntries,
                onEntryClick = onEntryClick
            )
            AuditSection(
                title = "${stringResource(R.string.audit_duplicate_passwords)} (${state.duplicateEntries.size})",
                entries = state.duplicateEntries,
                onEntryClick = onEntryClick
            )
            AuditSection(
                title = "${stringResource(R.string.audit_old_passwords)
                    .replace("%1\$d", state.passwordExpiryDays.toString())} (${state.oldEntries.size})",
                entries = state.oldEntries,
                onEntryClick = onEntryClick
            )
            BreachedSection(state, viewModel, onEntryClick)

            // ── Points forts ──
            SectionLabel(stringResource(R.string.audit_strengths))
            AuditSection(
                title = "${stringResource(R.string.audit_strong_passwords)} (${state.strongEntries.size})",
                entries = state.strongEntries,
                onEntryClick = onEntryClick
            )
            InfoRowCard(
                label = stringResource(R.string.audit_stat_unique),
                value = "${state.uniquePercent} %",
                valueColor = statusColor(state.uniquePercent)
            )

            // ── Composition ──
            SectionLabel(stringResource(R.string.audit_composition))
            StatRow {
                StatCard(Modifier.weight(1f), stringResource(R.string.audit_stat_categories),
                    state.categoriesCount.toString(), MaterialTheme.colorScheme.onSurface)
                StatCard(Modifier.weight(1f), stringResource(R.string.audit_stat_favorites),
                    state.favoritesCount.toString(), MaterialTheme.colorScheme.onSurface)
            }

            // ── Complétude ──
            SectionLabel(stringResource(R.string.audit_completeness))
            StatRow {
                StatCard(Modifier.weight(1f), stringResource(R.string.audit_stat_no_url),
                    state.noUrlCount.toString(), MaterialTheme.colorScheme.onSurfaceVariant)
                StatCard(Modifier.weight(1f), stringResource(R.string.audit_stat_no_email),
                    state.noEmailCount.toString(), MaterialTheme.colorScheme.onSurfaceVariant)
            }

            // ── Activité ──
            SectionLabel(stringResource(R.string.audit_activity))
            StatRow {
                StatCard(Modifier.weight(1f), stringResource(R.string.audit_stat_added),
                    state.addedLast30.toString(), MaterialTheme.colorScheme.onSurface)
                StatCard(Modifier.weight(1f), stringResource(R.string.audit_stat_modified),
                    state.modifiedLast30.toString(), MaterialTheme.colorScheme.onSurface)
                StatCard(Modifier.weight(1f), stringResource(R.string.audit_stat_oldest),
                    ageLabel(state.oldestAgeDays), MaterialTheme.colorScheme.onSurface)
            }

            Spacer(Modifier.height(8.dp))
        }
    }
}

@Composable
private fun statusColor(score: Int): Color = when {
    score >= 80 -> MaterialTheme.appColors.statusStrong
    score >= 50 -> MaterialTheme.appColors.statusMedium
    else -> MaterialTheme.appColors.statusWeak
}

@Composable
private fun ageLabel(days: Long?): String {
    if (days == null) return stringResource(R.string.audit_age_none)
    return when {
        days < 60 -> stringResource(R.string.audit_age_days, days.toInt())
        days < 730 -> stringResource(R.string.audit_age_months, (days / 30).toInt())
        else -> stringResource(R.string.audit_age_years, (days / 365).toInt())
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text.uppercase(),
        style = MaterialTheme.typography.labelMedium,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = 4.dp, top = 8.dp)
    )
}

@Composable
private fun StatRow(content: @Composable RowScope.() -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), content = content)
}

@Composable
private fun StatCard(modifier: Modifier, caption: String, value: String, valueColor: Color) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(modifier = Modifier.padding(13.dp)) {
            Text(
                caption.uppercase(),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                value,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = valueColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 4.dp)
            )
        }
    }
}

@Composable
private fun InfoRowCard(label: String, value: String, valueColor: Color) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
            Text(value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = valueColor)
        }
    }
}

@Composable
private fun AuditSection(
    title: String,
    entries: List<PasswordEntry>,
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
                        entries.forEach { entry -> EntryRow(entry, onEntryClick) }
                    }
                }
            }
        }
    }
}

@Composable
private fun BreachedSection(
    state: SecurityAuditUiState,
    viewModel: SecurityAuditViewModel,
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
                Text(
                    text = "${stringResource(R.string.audit_breached_passwords)} (${state.breachedEntries.size})",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f)
                )
                Spacer(modifier = Modifier.width(8.dp))
                OutlinedButton(
                    onClick = {
                        expanded = true
                        viewModel.checkBreaches()
                    },
                    enabled = !state.isCheckingBreaches
                ) {
                    Text(stringResource(R.string.audit_check_now), maxLines = 1)
                }
                Spacer(modifier = Modifier.width(8.dp))
                Icon(
                    imageVector = if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = null
                )
            }

            AnimatedVisibility(visible = expanded) {
                Column {
                    HorizontalDivider()
                    when {
                        state.isCheckingBreaches -> {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(16.dp),
                                horizontalArrangement = Arrangement.Center
                            ) {
                                CircularProgressIndicator(modifier = Modifier.size(24.dp))
                            }
                        }
                        state.breachError -> {
                            Text(
                                text = stringResource(R.string.audit_breach_error),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.error,
                                modifier = Modifier.padding(16.dp)
                            )
                        }
                        state.breachedEntries.isNotEmpty() -> {
                            state.breachedEntries.forEach { entry -> EntryRow(entry, onEntryClick) }
                        }
                        state.breachesChecked -> {
                            Text(
                                text = stringResource(R.string.audit_no_issues),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(16.dp)
                            )
                        }
                        else -> {
                            Text(
                                text = stringResource(R.string.audit_breach_hint),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(16.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun EntryRow(entry: PasswordEntry, onEntryClick: (String) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onEntryClick(entry.id) }
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        Column {
            Text(text = entry.title ?: "", style = MaterialTheme.typography.bodyLarge)
            if (!entry.username.isNullOrBlank()) {
                Text(
                    text = entry.username,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
