package com.passwordmanager.android.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.layout.*
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.passwordmanager.android.R
import com.passwordmanager.android.ui.theme.*
import com.passwordmanager.crypto.PasswordStrengthAnalyzer
import com.passwordmanager.crypto.PasswordStrengthAnalyzer.Strength

@Composable
fun PasswordStrengthBar(
    password: CharArray?,
    modifier: Modifier = Modifier
) {
    if (password == null || password.isEmpty()) return

    val strength = PasswordStrengthAnalyzer.analyze(password)
    val score = PasswordStrengthAnalyzer.getScore(password)

    val progress by animateFloatAsState(
        targetValue = score / 100f,
        label = "strength_progress"
    )

    val color by animateColorAsState(
        targetValue = strengthColor(strength),
        label = "strength_color"
    )

    val label = when (strength) {
        Strength.WEAK -> stringResource(R.string.strength_weak)
        Strength.MEDIUM -> stringResource(R.string.strength_medium)
        Strength.STRONG -> stringResource(R.string.strength_strong)
        Strength.VERY_STRONG -> stringResource(R.string.strength_very_strong)
    }

    Column(modifier = modifier) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = stringResource(R.string.strength_label),
                style = MaterialTheme.typography.labelMedium
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = color
            )
        }
        Spacer(modifier = Modifier.height(4.dp))
        LinearProgressIndicator(
            progress = { progress },
            modifier = Modifier.fillMaxWidth().height(6.dp),
            color = color,
            trackColor = MaterialTheme.colorScheme.surfaceVariant
        )
    }
}
