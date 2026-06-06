package com.passwordmanager.android.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.passwordmanager.android.R
import com.passwordmanager.android.ui.theme.appColors
import com.passwordmanager.android.ui.theme.spacing
import com.passwordmanager.crypto.PasswordStrengthAnalyzer
import com.passwordmanager.crypto.PasswordStrengthAnalyzer.Strength
import com.passwordmanager.util.SecureWiper
import com.passwordmanager.vault.PasswordEntry
import androidx.compose.ui.res.stringResource
import kotlin.math.roundToInt

/**
 * "Calme & confiance" vault dashboard: a bento row of health score, weak count and total.
 * Computed locally from the (displayed) entries; color is only a security signal.
 * Source: docs/design-system/components.md (BentoCard) + test_design/Android/02.
 */
@Composable
fun BentoDashboard(entries: List<PasswordEntry>, modifier: Modifier = Modifier) {
    var weak = 0
    var points = 0
    for (e in entries) {
        val p = e.password ?: continue
        try {
            when (PasswordStrengthAnalyzer.analyze(p)) {
                Strength.WEAK -> { weak++; points += 25 }
                Strength.MEDIUM -> points += 55
                Strength.STRONG -> points += 85
                Strength.VERY_STRONG -> points += 100
            }
        } finally {
            SecureWiper.wipe(p)
        }
    }
    val total = entries.size
    val score = if (total == 0) 100 else (points / total.toFloat()).roundToInt()
    val scoreColor = when {
        score >= 80 -> MaterialTheme.appColors.statusStrong
        score >= 50 -> MaterialTheme.appColors.statusMedium
        else -> MaterialTheme.appColors.statusWeak
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = MaterialTheme.spacing.screenEdge, vertical = MaterialTheme.spacing.sm),
        horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.md)
    ) {
        bentoCard(Modifier.weight(1f), stringResource(R.string.audit_title), score.toString(),
            "$total ${stringResource(R.string.vault_entries)}", scoreColor)
        bentoCard(Modifier.weight(1f), stringResource(R.string.strength_weak), weak.toString(),
            stringResource(R.string.filter_strength),
            if (weak > 0) MaterialTheme.appColors.statusWeak else MaterialTheme.colorScheme.onSurfaceVariant)
        bentoCard(Modifier.weight(1f), stringResource(R.string.vault_entries), total.toString(),
            "", MaterialTheme.colorScheme.onSurface)
    }
}

@Composable
private fun bentoCard(modifier: Modifier, caption: String, value: String, sub: String, valueColor: Color) {
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
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = valueColor,
                modifier = Modifier.padding(top = 4.dp)
            )
            Text(
                sub,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 2.dp)
            )
        }
    }
}
