package com.passwordmanager.android.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties
import com.passwordmanager.android.R
import com.passwordmanager.android.ui.theme.appColors
import com.passwordmanager.android.ui.theme.spacing
import com.passwordmanager.crypto.PasswordStrengthAnalyzer
import com.passwordmanager.crypto.PasswordStrengthAnalyzer.Strength
import com.passwordmanager.util.SecureWiper
import com.passwordmanager.vault.PasswordEntry
import androidx.compose.ui.res.stringResource
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

/** How long an explanation bubble stays visible before auto-dismissing. */
private const val TIP_TIMEOUT_MS = 5_000L

/**
 * "Calme & confiance" vault dashboard: a bento row of total, health score and weak count.
 * Computed locally from the (displayed) entries; color is only a security signal.
 * Tapping a card reveals a short explanation bubble (dismissed on outside tap or after 5s).
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
    // Security is shown as a mark out of 20 (nearest), e.g. 75/100 -> 15/20.
    val score20 = (score / 5.0).roundToInt()
    val scoreColor = when {
        score >= 80 -> MaterialTheme.appColors.statusStrong
        score >= 50 -> MaterialTheme.appColors.statusMedium
        else -> MaterialTheme.appColors.statusWeak
    }

    // Index of the card whose explanation bubble is currently shown (null = none).
    var activeTip by remember { mutableStateOf<Int?>(null) }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = MaterialTheme.spacing.screenEdge, vertical = MaterialTheme.spacing.sm),
        horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.md)
    ) {
        bentoCard(
            modifier = Modifier.weight(1f),
            caption = stringResource(R.string.vault_entries),
            value = total.toString(),
            sub = "",
            valueColor = MaterialTheme.colorScheme.onSurface,
            tip = stringResource(R.string.dashboard_tip_entries),
            tipVisible = activeTip == 0,
            onClick = { activeTip = if (activeTip == 0) null else 0 },
            onDismissTip = { activeTip = null }
        )
        bentoCard(
            modifier = Modifier.weight(1f),
            caption = stringResource(R.string.dashboard_security),
            value = "$score20/20",
            sub = "$total ${stringResource(R.string.vault_entries)}",
            valueColor = scoreColor,
            tip = stringResource(R.string.dashboard_tip_security),
            tipVisible = activeTip == 1,
            onClick = { activeTip = if (activeTip == 1) null else 1 },
            onDismissTip = { activeTip = null }
        )
        bentoCard(
            modifier = Modifier.weight(1f),
            caption = stringResource(R.string.strength_weak),
            value = weak.toString(),
            sub = stringResource(R.string.filter_strength),
            valueColor = if (weak > 0) MaterialTheme.appColors.statusWeak else MaterialTheme.colorScheme.onSurfaceVariant,
            tip = stringResource(R.string.dashboard_tip_force),
            tipVisible = activeTip == 2,
            onClick = { activeTip = if (activeTip == 2) null else 2 },
            onDismissTip = { activeTip = null }
        )
    }
}

@Composable
private fun bentoCard(
    modifier: Modifier,
    caption: String,
    value: String,
    sub: String,
    valueColor: Color,
    tip: String,
    tipVisible: Boolean,
    onClick: () -> Unit,
    onDismissTip: () -> Unit
) {
    Box(modifier = modifier) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onClick() },
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

        if (tipVisible) {
            // Auto-dismiss after a few seconds even if the user never taps away.
            LaunchedEffect(Unit) {
                delay(TIP_TIMEOUT_MS)
                onDismissTip()
            }
            TipBubble(text = tip, onDismiss = onDismissTip)
        }
    }
}

/** A small explanation bubble anchored just below its parent card. */
@Composable
private fun TipBubble(text: String, onDismiss: () -> Unit) {
    val gapPx = with(LocalDensity.current) { 6.dp.roundToPx() }
    val positionProvider = remember(gapPx) {
        object : PopupPositionProvider {
            override fun calculatePosition(
                anchorBounds: IntRect,
                windowSize: IntSize,
                layoutDirection: LayoutDirection,
                popupContentSize: IntSize
            ): IntOffset {
                val x = anchorBounds.left + (anchorBounds.width - popupContentSize.width) / 2
                val maxX = (windowSize.width - popupContentSize.width).coerceAtLeast(0)
                return IntOffset(x.coerceIn(0, maxX), anchorBounds.bottom + gapPx)
            }
        }
    }
    Popup(
        popupPositionProvider = positionProvider,
        onDismissRequest = onDismiss,
        properties = PopupProperties(focusable = true)
    ) {
        Surface(
            shape = MaterialTheme.shapes.small,
            color = MaterialTheme.colorScheme.inverseSurface,
            contentColor = MaterialTheme.colorScheme.inverseOnSurface,
            shadowElevation = 6.dp,
            modifier = Modifier.widthIn(max = 240.dp)
        ) {
            Text(
                text,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
            )
        }
    }
}
