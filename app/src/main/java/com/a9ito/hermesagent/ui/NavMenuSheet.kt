package com.a9ito.hermesagent.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.a9ito.hermesagent.R

/**
 * A Google-Messages-style menu sheet: a modal bottom sheet with a header row
 * (app identity + close affordance) and the SECONDARY destinations rendered as a
 * single rounded "grouped list" card with hairline dividers between rows — the
 * same list pattern Google Messages uses for its account/settings menu.
 *
 * The bottom bar shows the primary destinations plus a "More" entry that opens
 * this; picking a row navigates and dismisses. The row matching the current
 * route gets the Material 3 active treatment (secondaryContainer pill + primary
 * icon/label tint) so the sheet reflects where you are, exactly like a nav
 * component's selected state.
 *
 * All labels come from strings.xml, so this is localized for free.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NavMenuSheet(
    destinations: List<Destination>,
    currentRoute: String?,
    onSelect: (Destination) -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(bottom = 24.dp)) {
            // Header: app identity on the left, close affordance on the right —
            // mirrors the Messages menu's header + "X".
            Row(
                modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(R.string.app_name),
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.nav_menu_close))
                }
            }

            // One rounded grouped container holding every secondary destination.
            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant,
                shape = RoundedCornerShape(20.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column {
                    destinations.forEachIndexed { index, destination ->
                        if (index > 0) {
                            HorizontalDivider(
                                color = MaterialTheme.colorScheme.outlineVariant,
                                modifier = Modifier.padding(horizontal = 12.dp),
                            )
                        }
                        NavMenuRow(
                            destination = destination,
                            selected = destination.route == currentRoute,
                            onClick = { onSelect(destination) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun NavMenuRow(
    destination: Destination,
    selected: Boolean,
    onClick: () -> Unit,
) {
    // Selected row gets the M3 active-destination treatment: a tinted pill and
    // primary-colored icon/label, the same signal a nav bar item shows.
    val rowColor = if (selected) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceVariant
    val contentColor = if (selected) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
    Surface(
        color = rowColor,
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            Icon(
                destination.icon,
                contentDescription = null,
                tint = contentColor,
                modifier = Modifier.size(24.dp),
            )
            Text(
                text = stringResource(destination.titleRes),
                style = MaterialTheme.typography.bodyLarge,
                color = contentColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
        }
    }
}
