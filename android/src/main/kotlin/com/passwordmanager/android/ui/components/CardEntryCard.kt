package com.passwordmanager.android.ui.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.sp
import com.passwordmanager.android.R
import com.passwordmanager.vault.CardEntry
import com.passwordmanager.vault.CardType

private val CardBarColor = Color(0xFF42A5F5) // Blue, card-themed

private val CardTypeColors = mapOf(
    CardType.VISA to Color(0xFF1A237E),
    CardType.MASTERCARD to Color(0xFFE65100),
    CardType.AMEX to Color(0xFF1565C0),
    CardType.CB to Color(0xFF2E7D32),
)
private val CardTypeDefaultColor = Color(0xFF546E7A) // Blue Grey fallback

private fun cardTypeColor(cardType: String?): Color {
    if (cardType.isNullOrBlank()) return CardTypeDefaultColor
    return CardTypeColors[cardType] ?: CardTypeDefaultColor
}

/**
 * Maps an internal card type key (e.g. "VISA") to a localized display name.
 */
@Composable
private fun cardTypeDisplayName(key: String?): String {
    return when (key) {
        CardType.VISA       -> stringResource(R.string.card_type_visa)
        CardType.MASTERCARD -> stringResource(R.string.card_type_mastercard)
        CardType.AMEX       -> stringResource(R.string.card_type_amex)
        CardType.CB         -> stringResource(R.string.card_type_cb)
        CardType.OTHER      -> stringResource(R.string.card_type_other)
        else                -> key ?: ""
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun CardEntryCard(
    entry: CardEntry,
    onClick: () -> Unit,
    onDelete: () -> Unit,
    onCopyCardNumber: () -> Unit,
    modifier: Modifier = Modifier,
    isSelected: Boolean = false,
    isSelectionMode: Boolean = false,
    onLongClick: () -> Unit = {},
    onToggleFavorite: () -> Unit = {}
) {
    val cardContent: @Composable () -> Unit = {
        Card(
            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
            colors = if (isSelected) CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer
            ) else CardDefaults.cardColors(),
            modifier = Modifier.combinedClickable(
                onClick = {
                    if (isSelectionMode) onLongClick() else onClick()
                },
                onLongClick = onLongClick
            )
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(IntrinsicSize.Min)
            ) {
                // Card-themed color bar on left edge
                Box(
                    modifier = Modifier
                        .width(4.dp)
                        .fillMaxHeight()
                        .background(CardBarColor)
                )

                // Selection checkbox
                if (isSelectionMode) {
                    Icon(
                        imageVector = if (isSelected) Icons.Default.CheckCircle
                            else Icons.Default.RadioButtonUnchecked,
                        contentDescription = null,
                        tint = if (isSelected) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier
                            .padding(start = 8.dp)
                            .align(Alignment.CenterVertically)
                            .size(24.dp)
                    )
                }

                Spacer(modifier = Modifier.width(if (isSelectionMode) 8.dp else 12.dp))

                // Avatar - letter with card-type-based color
                val avatarColor = cardTypeColor(entry.cardType)
                val initial = (entry.title?.firstOrNull() ?: '?').uppercaseChar()
                Box(
                    modifier = Modifier
                        .padding(vertical = 12.dp)
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(avatarColor),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = initial.toString(),
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp
                    )
                }

                Spacer(modifier = Modifier.width(12.dp))

                // Content
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .padding(vertical = 12.dp)
                ) {
                    Text(
                        text = entry.title ?: "",
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    val last4 = entry.getLast4Digits()
                    val subtitle = if (last4 != null) "\u2022\u2022\u2022\u2022 $last4" else null
                    if (subtitle != null) {
                        Text(
                            text = subtitle,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                // Favorite star
                IconButton(
                    onClick = onToggleFavorite,
                    modifier = Modifier.align(Alignment.CenterVertically).size(32.dp)
                ) {
                    Icon(
                        imageVector = if (entry.isFavorite) Icons.Default.Star else Icons.Default.StarBorder,
                        contentDescription = null,
                        tint = if (entry.isFavorite) Color(0xFFFFC107) else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp)
                    )
                }

                // Card type chip
                if (!entry.cardType.isNullOrBlank()) {
                    Box(modifier = Modifier.padding(end = 12.dp, top = 12.dp, bottom = 12.dp)) {
                        AssistChip(
                            onClick = {},
                            label = {
                                Text(
                                    text = cardTypeDisplayName(entry.cardType),
                                    style = MaterialTheme.typography.labelMedium
                                )
                            }
                        )
                    }
                }
            }
        }
    }

    if (isSelectionMode) {
        // No swipe in selection mode
        Box(modifier = modifier) { cardContent() }
    } else {
        val dismissState = rememberSwipeToDismissBoxState(
            confirmValueChange = { value ->
                when (value) {
                    SwipeToDismissBoxValue.EndToStart -> { onDelete(); false }
                    SwipeToDismissBoxValue.StartToEnd -> { onCopyCardNumber(); false }
                    SwipeToDismissBoxValue.Settled -> false
                }
            }
        )

        SwipeToDismissBox(
            state = dismissState,
            modifier = modifier,
            backgroundContent = {
                val direction = dismissState.dismissDirection
                val color = when (direction) {
                    SwipeToDismissBoxValue.StartToEnd -> Color(0xFF1E88E5)
                    SwipeToDismissBoxValue.EndToStart -> Color(0xFFE53935)
                    else -> Color.Transparent
                }
                val icon = when (direction) {
                    SwipeToDismissBoxValue.StartToEnd -> Icons.Default.ContentCopy
                    SwipeToDismissBoxValue.EndToStart -> Icons.Default.Delete
                    else -> null
                }
                val alignment = when (direction) {
                    SwipeToDismissBoxValue.StartToEnd -> Alignment.CenterStart
                    else -> Alignment.CenterEnd
                }

                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(color, MaterialTheme.shapes.medium)
                        .padding(horizontal = 20.dp),
                    contentAlignment = alignment
                ) {
                    icon?.let {
                        Icon(imageVector = it, contentDescription = null, tint = Color.White)
                    }
                }
            }
        ) {
            cardContent()
        }
    }
}
