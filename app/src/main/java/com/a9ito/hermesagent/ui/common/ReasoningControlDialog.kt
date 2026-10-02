package com.a9ito.hermesagent.ui.common

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.a9ito.hermesagent.R
import com.a9ito.hermesagent.core.ReasoningEffort
import com.a9ito.hermesagent.core.ReasoningPref

/**
 * Shared per-turn reasoning + speed control dialog, used by both the quick Chat
 * and the per-session chat. Stateless: the caller owns the [ReasoningPref] and
 * the show flag. Picking an effort or toggling fast reports back immediately;
 * the dialog has no Apply button (changes take effect on the next turn).
 *
 * Effort chips map to the server's model_options.reasoning.effort ladder, plus
 * two app-level choices: "Default" sends nothing (instance default) and "Off"
 * sends reasoning.enabled=false. Fast maps to the priority service tier.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReasoningControlDialog(
    pref: ReasoningPref,
    onEffort: (ReasoningEffort) -> Unit,
    onFast: (Boolean) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.reasoning_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(stringResource(R.string.reasoning_effort_label))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    ReasoningEffort.entries.forEach { effort ->
                        FilterChip(
                            selected = pref.effort == effort,
                            onClick = { onEffort(effort) },
                            label = { Text(stringResource(effortLabel(effort))) },
                        )
                    }
                }
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(stringResource(R.string.reasoning_fast))
                        Text(
                            text = stringResource(R.string.reasoning_fast_sub),
                            style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
                        )
                    }
                    Switch(checked = pref.fast, onCheckedChange = onFast)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.reasoning_done)) }
        },
    )
}

private fun effortLabel(effort: ReasoningEffort): Int = when (effort) {
    ReasoningEffort.DEFAULT -> R.string.reasoning_effort_default
    ReasoningEffort.OFF -> R.string.reasoning_effort_off
    ReasoningEffort.MINIMAL -> R.string.reasoning_effort_minimal
    ReasoningEffort.LOW -> R.string.reasoning_effort_low
    ReasoningEffort.MEDIUM -> R.string.reasoning_effort_medium
    ReasoningEffort.HIGH -> R.string.reasoning_effort_high
    ReasoningEffort.XHIGH -> R.string.reasoning_effort_xhigh
    ReasoningEffort.MAX -> R.string.reasoning_effort_max
    ReasoningEffort.ULTRA -> R.string.reasoning_effort_ultra
}

/** Short status label for the app-bar affordance, e.g. "medium" or "fast - high". */
@Composable
fun reasoningBadge(pref: ReasoningPref): String {
    val effort = stringResource(effortLabel(pref.effort))
    return if (pref.fast) stringResource(R.string.reasoning_badge_fast, effort) else effort
}
