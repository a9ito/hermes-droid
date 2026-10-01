package com.a9ito.hermesagent.ui.common

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.a9ito.hermesagent.R

/**
 * A collapsed-by-default "Reasoning" disclosure for an assistant turn's thinking
 * text. Tapping the header toggles the full text. Shared by the session chat
 * (per-message, server-history replay) and the Runs screen (one accumulated
 * buffer streamed live from reasoning.available events) so both surfaces get the
 * exact same treatment — kept separate from the live tool-activity trail/log.
 *
 * Expansion is intentionally NOT keyed on [reasoning]: in Runs the text grows as
 * the run streams, and re-keying would snap the panel shut on every new chunk.
 * Per-item identity (the chat LazyColumn's message key) already scopes the state.
 */
@Composable
fun ReasoningPanel(reasoning: String, modifier: Modifier = Modifier) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = MaterialTheme.shapes.medium,
        onClick = { expanded = !expanded },
        modifier = modifier.padding(top = 2.dp),
    ) {
        Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
            Text(
                text = stringResource(
                    if (expanded) R.string.chat_reasoning_hide else R.string.chat_reasoning_show
                ),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
            )
            if (expanded) {
                Text(
                    text = reasoning,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
        }
    }
}
