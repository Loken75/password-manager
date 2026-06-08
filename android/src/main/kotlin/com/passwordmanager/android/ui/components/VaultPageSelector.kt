package com.passwordmanager.android.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import com.passwordmanager.android.R

/**
 * Top-bar dropdown that switches between the two vault pages
 * (Passwords / Applications). Replaces the former TabRow; the chosen
 * index drives the parent HorizontalPager.
 */
@Composable
fun VaultPageSelector(selectedIndex: Int, onSelect: (Int) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    val labels = listOf(
        stringResource(R.string.tab_passwords),
        stringResource(R.string.tab_applications)
    )
    Box {
        TextButton(
            onClick = { expanded = true },
            colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.onSurface)
        ) {
            Text(labels[selectedIndex], style = MaterialTheme.typography.titleLarge)
            Icon(Icons.Default.ArrowDropDown, contentDescription = stringResource(R.string.vault_select_page))
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            labels.forEachIndexed { index, label ->
                DropdownMenuItem(
                    text = { Text(label) },
                    onClick = {
                        expanded = false
                        if (index != selectedIndex) onSelect(index)
                    },
                    trailingIcon = {
                        if (index == selectedIndex) {
                            Icon(Icons.Default.Check, contentDescription = null)
                        }
                    }
                )
            }
        }
    }
}
