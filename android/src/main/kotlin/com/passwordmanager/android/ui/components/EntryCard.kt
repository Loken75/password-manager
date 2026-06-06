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
import androidx.compose.ui.unit.sp
import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.ui.graphics.asImageBitmap
import com.passwordmanager.android.ui.theme.*
import com.passwordmanager.crypto.PasswordStrengthAnalyzer
import com.passwordmanager.vault.PasswordEntry

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun EntryCard(
    entry: PasswordEntry,
    onClick: () -> Unit,
    onDelete: () -> Unit,
    onCopyPassword: () -> Unit,
    modifier: Modifier = Modifier,
    isSelected: Boolean = false,
    isSelectionMode: Boolean = false,
    onLongClick: () -> Unit = {},
    onToggleFavorite: () -> Unit = {},
    favicon: Bitmap? = null
) {
    val strength = entry.password?.let { PasswordStrengthAnalyzer.analyze(it) }
    val strengthBarColor = strength?.let { strengthColor(it) } ?: MaterialTheme.colorScheme.outline

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
                // Strength bar on left edge
                Box(
                    modifier = Modifier
                        .width(4.dp)
                        .fillMaxHeight()
                        .background(strengthBarColor)
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

                // Avatar (favicon if available, otherwise neutral letter — color is reserved
                // for security signals in the "Calme & confiance" direction)
                val avatarColor = MaterialTheme.colorScheme.surfaceVariant
                val initial = (entry.title?.firstOrNull() ?: '?').uppercaseChar()
                Box(
                    modifier = Modifier
                        .padding(vertical = 12.dp)
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(if (favicon != null) Color.Transparent else avatarColor),
                    contentAlignment = Alignment.Center
                ) {
                    if (favicon != null) {
                        Image(
                            bitmap = favicon.asImageBitmap(),
                            contentDescription = null,
                            modifier = Modifier.size(32.dp)
                        )
                    } else {
                        Text(
                            text = initial.toString(),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp
                        )
                    }
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
                    val subtitle = when {
                        !entry.username.isNullOrBlank() -> entry.username
                        !entry.url.isNullOrBlank() -> entry.url
                        else -> null
                    }
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
                        tint = if (entry.isFavorite) MaterialTheme.appColors.favorite else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp)
                    )
                }

                // Category chip
                if (!entry.category.isNullOrBlank()) {
                    Box(modifier = Modifier.padding(end = 12.dp, top = 12.dp, bottom = 12.dp)) {
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
    }

    if (isSelectionMode) {
        // No swipe in selection mode
        Box(modifier = modifier) { cardContent() }
    } else {
        val dismissState = rememberSwipeToDismissBoxState(
            confirmValueChange = { value ->
                when (value) {
                    SwipeToDismissBoxValue.EndToStart -> { onDelete(); false }
                    SwipeToDismissBoxValue.StartToEnd -> { onCopyPassword(); false }
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
                    SwipeToDismissBoxValue.StartToEnd -> MaterialTheme.appColors.statusVeryStrong
                    SwipeToDismissBoxValue.EndToStart -> MaterialTheme.appColors.statusWeak
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
