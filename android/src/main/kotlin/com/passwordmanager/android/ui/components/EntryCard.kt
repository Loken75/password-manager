package com.passwordmanager.android.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Circle
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.passwordmanager.android.ui.theme.*
import com.passwordmanager.crypto.PasswordStrengthAnalyzer
import com.passwordmanager.crypto.PasswordStrengthAnalyzer.Strength
import com.passwordmanager.vault.VaultEntry

@Composable
fun EntryCard(
    entry: VaultEntry,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val strength = entry.password?.let { PasswordStrengthAnalyzer.analyze(it) }
    val strengthColor = when (strength) {
        Strength.WEAK -> StrengthWeak
        Strength.MEDIUM -> StrengthMedium
        Strength.STRONG -> StrengthStrong
        Strength.VERY_STRONG -> StrengthVeryStrong
        null -> MaterialTheme.colorScheme.outline
    }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.Circle,
                contentDescription = null,
                tint = strengthColor,
                modifier = Modifier.size(12.dp)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = entry.title ?: "",
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (!entry.username.isNullOrBlank()) {
                    Text(
                        text = entry.username,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            if (!entry.category.isNullOrBlank()) {
                AssistChip(
                    onClick = {},
                    label = {
                        Text(
                            text = entry.category,
                            style = MaterialTheme.typography.labelMedium
                        )
                    }
                )
            }
        }
    }
}
