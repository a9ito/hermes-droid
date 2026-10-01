package com.a9ito.hermesagent.ui.common

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.a9ito.hermesagent.R
import com.a9ito.hermesagent.core.ModelOptions

/**
 * Shared model picker used by every screen that can switch models — the
 * per-session chat, the stateless quick chat, and the Runs screen. When the
 * instance exposes the rich /api/model/options catalog, models are grouped by
 * provider with capability + pricing hints and unavailable/needs-auth states;
 * otherwise it falls back to the flat /v1/models id list.
 *
 * [refreshing] drives the "checking tier" affordances: the picker is opened with
 * a refresh=true fetch (which settles free-tier pricing), so this shows progress
 * without hiding an already-loaded catalog. The caller owns the dialog's
 * visibility and the refresh trigger; this composable is pure presentation.
 */
@Composable
fun ModelPickerDialog(
    modelOptions: ModelOptions?,
    flatModels: List<String>,
    current: String?,
    refreshing: Boolean,
    onPick: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.session_model_title)) },
        text = {
            when {
                // Fresh fetch in flight AND nothing useful to show yet: a bare
                // spinner. If we already have a catalog we keep showing it (below)
                // so the list doesn't flicker away on every re-open.
                refreshing && (modelOptions == null || modelOptions.isEmpty) ->
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        CircularProgressIndicator(modifier = Modifier.padding(4.dp))
                        Text(stringResource(R.string.model_checking_tier), style = MaterialTheme.typography.bodyMedium)
                    }
                modelOptions != null && !modelOptions.isEmpty ->
                    RichModelList(modelOptions, current, refreshing, onPick)
                else ->
                    FlatModelList(flatModels, current, onPick)
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_dismiss)) }
        },
    )
}

@Composable
private fun RichModelList(options: ModelOptions, current: String?, refreshing: Boolean, onPick: (String) -> Unit) {
    LazyColumn {
        if (refreshing) {
            item(key = "refreshing_banner") {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    CircularProgressIndicator(modifier = Modifier.padding(2.dp))
                    Text(stringResource(R.string.model_checking_tier), style = MaterialTheme.typography.labelSmall)
                }
            }
        }
        options.providers.forEach { provider ->
            item(key = "hdr_${provider.slug}") {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 10.dp, bottom = 2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Text(
                        text = provider.name,
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    if (provider.freeTier) {
                        Text(stringResource(R.string.model_free_tier), style = MaterialTheme.typography.labelSmall)
                    }
                    if (provider.needsAuth) {
                        Text(stringResource(R.string.model_needs_key), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error)
                    }
                }
            }
            items(provider.models, key = { "${provider.slug}/${it.id}" }) { model ->
                // The current model is always selectable: it is demonstrably in use,
                // so a stale/pending `unavailable` flag must never grey it out. While
                // the provider's tier is still pending, availability is provisional —
                // don't lock rows on an unsettled catalog.
                val isCurrent = model.id == current
                val selectable = isCurrent ||
                    (!provider.needsAuth && (provider.pricingPending || !model.unavailable))
                val selected = isCurrent
                Surface(
                    color = if (selected) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surface,
                    shape = MaterialTheme.shapes.small,
                    onClick = { if (selectable) onPick(model.id) },
                    enabled = selectable,
                    modifier = Modifier.fillMaxWidth().padding(vertical = 1.dp),
                ) {
                    Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                        Text(
                            text = model.id,
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (selectable) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        val hints = buildList {
                            model.pricing?.let { p ->
                                when {
                                    p.free -> add(stringResource(R.string.model_price_free))
                                    p.input != null || p.output != null ->
                                        add(stringResource(R.string.model_price_io, p.input ?: "?", p.output ?: "?"))
                                }
                            }
                            if (model.supportsReasoning) add(stringResource(R.string.model_reasoning))
                            if (model.supportsFastMode) add(stringResource(R.string.model_fast))
                            // A pending catalog's "unavailable" is not final, so don't
                            // advertise it as a hard state; the current model never shows it.
                            if (model.unavailable && !provider.pricingPending && !isCurrent) {
                                add(stringResource(R.string.model_unavailable))
                            }
                        }
                        if (hints.isNotEmpty()) {
                            Text(
                                text = hints.joinToString(" · "),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun FlatModelList(models: List<String>, current: String?, onPick: (String) -> Unit) {
    Column {
        models.forEach { model ->
            val selected = model == current
            Surface(
                color = if (selected) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surface,
                shape = MaterialTheme.shapes.small,
                onClick = { onPick(model) },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    text = model,
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                )
            }
        }
    }
}
