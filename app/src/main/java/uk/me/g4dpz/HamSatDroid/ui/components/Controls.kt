// SPDX-License-Identifier: GPL-3.0-or-later
package uk.me.g4dpz.HamSatDroid.ui.components

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.semantics
import uk.me.g4dpz.HamSatDroid.ui.LargeFont
import androidx.compose.ui.unit.dp

/** True when the system font size is large enough that labels go above their fields (see [LargeFont]). */
@Composable
fun rememberIsLargeFont(): Boolean = LargeFont.isLarge(LocalDensity.current.fontScale)

/**
 * A labelled drop-down list. [optionLabel] shows the text for each option; the current option is [selected].
 *
 * At a normal font size the label floats in the border of the box (one line, with an ellipsis if it is too long). At a
 * large font size a floating label that wraps collides with the border, so the label is shown above the box instead,
 * wrapping as needed, and the label and the box are read together by a screen reader.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun <T> DropdownField(
    label: String,
    options: List<T>,
    selected: T?,
    optionLabel: @Composable (T) -> String,
    onSelected: (T) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    var expanded by remember { mutableStateOf(false) }
    val largeFont = rememberIsLargeFont()
    val box: @Composable () -> Unit = {
        ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { if (enabled) expanded = it }) {
            OutlinedTextField(
                value = selected?.let { optionLabel(it) } ?: "",
                onValueChange = {},
                readOnly = true,
                enabled = enabled,
                // Up to two lines, so a long value (a satellite name such as "RS-44 & BREEZE-KM R/B" on a narrow screen
                // or at a large font size) wraps instead of being cut off without any sign.
                singleLine = false,
                maxLines = 2,
                label = if (largeFont) null else ({ Text(label, maxLines = 1, overflow = TextOverflow.Ellipsis) }),
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                modifier = Modifier.menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable).fillMaxWidth(),
            )
            ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                for (option in options) {
                    DropdownMenuItem(
                        text = { Text(optionLabel(option)) },
                        onClick = {
                            expanded = false
                            onSelected(option)
                        },
                    )
                }
            }
        }
    }
    if (largeFont) {
        Column(modifier = modifier.semantics(mergeDescendants = true) {}) {
            Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(4.dp))
            box()
        }
    } else {
        Box(modifier = modifier) { box() }
    }
}

/** A switch with its label on the left. */
@Composable
fun SwitchRow(label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier.fillMaxWidth().padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f).padding(end = 16.dp))
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}
