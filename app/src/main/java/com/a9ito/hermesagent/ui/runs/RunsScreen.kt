package com.a9ito.hermesagent.ui.runs

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.a9ito.hermesagent.R
import com.a9ito.hermesagent.ServiceLocator
import com.a9ito.hermesagent.core.AgentRun
import com.a9ito.hermesagent.core.ModelOptions
import com.a9ito.hermesagent.ui.common.ConnectionGate
import com.a9ito.hermesagent.ui.common.ModelPickerDialog
import com.a9ito.hermesagent.ui.common.ReasoningPanel
import com.a9ito.hermesagent.ui.messageRes

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RunsScreen(
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: RunsViewModel = viewModel(
        factory = RunsViewModel.Factory(ServiceLocator.hermes()),
    ),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var showModelPicker by rememberSaveable { mutableStateOf(false) }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(stringResource(R.string.nav_runs))
                        state.model?.let {
                            Text(it, style = MaterialTheme.typography.labelSmall)
                        }
                    }
                },
                actions = {
                    if (state.configured && state.capabilities?.supportsRunControl != false) {
                        val hasPicker = ModelOptions.hasPicker(state.modelOptions, state.availableModels)
                        if (hasPicker) {
                            TextButton(onClick = { showModelPicker = true }) {
                                Text(stringResource(R.string.session_model_pick))
                            }
                        }
                    }
                },
            )
        },
    ) { innerPadding ->
        if (showModelPicker) {
            // Opening the picker triggers a fresh, tier-settling fetch (refresh=true):
            // the cheap startup load can report pricing_pending and lock every free
            // model, so this is what actually unlocks them. Runs once per open.
            LaunchedEffect(showModelPicker) { viewModel.refreshModelOptions() }
            ModelPickerDialog(
                modelOptions = state.modelOptions,
                flatModels = state.availableModels,
                current = state.model,
                refreshing = state.refreshingModels,
                onPick = { showModelPicker = false; viewModel.selectModel(it) },
                onDismiss = { showModelPicker = false },
            )
        }
        // Bind delegated state to locals: `state` is a `by` delegate, so its
        // nullable fields don't smart-cast after a null check (CI compile error).
        val capsError = state.capabilitiesError
        when {
            state.configLoaded && !state.configured -> ConnectionGate(
                title = stringResource(R.string.runs_locked_title),
                subtitle = stringResource(R.string.runs_locked_subtitle),
                actionLabel = stringResource(R.string.chat_locked_action),
                onAction = onOpenSettings,
                modifier = Modifier.padding(innerPadding),
            )
            // The capabilities PROBE failed (auth/network/5xx) — this is NOT the same
            // as the gateway reporting it lacks run control. Offer retry instead of the
            // misleading "update your gateway" message, which sent users chasing a
            // gateway upgrade for what was really a connection/token problem.
            capsError != null -> Centered(Modifier.padding(innerPadding)) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(stringResource(R.string.runs_caps_failed_title), style = MaterialTheme.typography.titleMedium)
                    Text(
                        stringResource(R.string.runs_caps_failed_subtitle, stringResource(capsError.messageRes())),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    OutlinedButton(onClick = viewModel::retryCapabilities) {
                        Text(stringResource(R.string.action_retry))
                    }
                }
            }
            // Capabilities resolved AND the instance can't drive runs -> explain, don't offer.
            state.capabilities?.supportsRunControl == false -> Centered(Modifier.padding(innerPadding)) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(stringResource(R.string.runs_unsupported_title), style = MaterialTheme.typography.titleMedium)
                    Text(
                        stringResource(R.string.runs_unsupported_subtitle),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
            else -> RunsContent(
                state = state,
                contentPadding = innerPadding,
                onInputChange = viewModel::onInputChange,
                onSubmit = viewModel::submit,
                onStop = viewModel::stop,
                onSteer = viewModel::steer,
                onApprove = viewModel::approve,
            )
        }
    }
}

@Composable
private fun RunsContent(
    state: RunsUiState,
    contentPadding: PaddingValues,
    onInputChange: (String) -> Unit,
    onSubmit: () -> Unit,
    onStop: () -> Unit,
    onSteer: (String) -> Unit,
    onApprove: (String) -> Unit,
) {
    var showSteer by rememberSaveable { mutableStateOf(false) }

    Column(
        modifier = Modifier.fillMaxSize().padding(contentPadding).padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        // Prompt box + submit. Disabled while a run is active so we don't stack runs.
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                value = state.input,
                onValueChange = onInputChange,
                modifier = Modifier.weight(1f),
                label = { Text(stringResource(R.string.runs_prompt_label)) },
                enabled = !state.isActive && !state.submitting,
                minLines = 1,
                maxLines = 4,
                keyboardOptions = KeyboardOptions.Default,
            )
            IconButton(onClick = onSubmit, enabled = state.input.isNotBlank() && !state.isActive && !state.submitting) {
                if (state.submitting) CircularProgressIndicator(modifier = Modifier.padding(4.dp))
                else Icon(Icons.AutoMirrored.Filled.Send, contentDescription = stringResource(R.string.runs_submit))
            }
        }

        state.run?.let { run -> RunStatusRow(run) }

        state.errorKind?.let { kind ->
            Text(
                stringResource(R.string.runs_error_prefix, stringResource(kind.messageRes())),
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodyMedium,
            )
        }

        // Control buttons for the live run.
        if (state.isActive) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (state.canStop) OutlinedButton(onClick = onStop) { Text(stringResource(R.string.runs_stop)) }
                if (state.canSteer) OutlinedButton(onClick = { showSteer = true }) { Text(stringResource(R.string.runs_steer)) }
            }
        }

        // Streamed answer.
        if (state.answer.isNotEmpty()) {
            Card(modifier = Modifier.fillMaxWidth()) {
                Text(
                    state.answer,
                    modifier = Modifier.padding(12.dp).verticalScroll(rememberScrollState()),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }

        // Collapsible reasoning — same treatment as the session chat, kept out of the tool log.
        if (state.reasoning.isNotBlank()) {
            ReasoningPanel(state.reasoning, modifier = Modifier.fillMaxWidth())
        }

        // Live activity log.
        if (state.log.isNotEmpty()) {
            Text(stringResource(R.string.runs_activity), style = MaterialTheme.typography.labelLarge)
            LazyColumn(
                modifier = Modifier.fillMaxWidth().weight(1f, fill = false),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                items(state.log) { line -> RunLogRow(line) }
            }
        }
    }

    // Approval prompt: one button per server-advertised choice.
    state.approval?.let { approval ->
        AlertDialog(
            onDismissRequest = { /* must resolve; no silent dismiss */ },
            title = { Text(stringResource(R.string.runs_approval_title)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    approval.tool?.let { Text(stringResource(R.string.runs_approval_tool, it), style = MaterialTheme.typography.labelLarge) }
                    approval.command?.let { Text(it, style = MaterialTheme.typography.bodySmall, maxLines = 6, overflow = TextOverflow.Ellipsis) }
                }
            },
            confirmButton = {
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    approval.choices.forEach { choice ->
                        TextButton(onClick = { onApprove(choice) }) { Text(choice) }
                    }
                }
            },
        )
    }

    if (showSteer) {
        SteerDialog(
            onSend = { text -> showSteer = false; onSteer(text) },
            onDismiss = { showSteer = false },
        )
    }
}

@Composable
private fun RunStatusRow(run: AgentRun) {
    val label = when (run.status) {
        AgentRun.Status.QUEUED -> stringResource(R.string.runs_state_queued)
        AgentRun.Status.RUNNING -> stringResource(R.string.runs_state_running)
        AgentRun.Status.WAITING_FOR_APPROVAL -> stringResource(R.string.runs_state_waiting)
        AgentRun.Status.STOPPING -> stringResource(R.string.runs_state_stopping)
        AgentRun.Status.COMPLETED -> stringResource(R.string.runs_state_completed)
        AgentRun.Status.FAILED -> stringResource(R.string.runs_state_failed)
        AgentRun.Status.CANCELLED -> stringResource(R.string.runs_state_cancelled)
        AgentRun.Status.INTERRUPTED -> stringResource(R.string.runs_state_interrupted)
        AgentRun.Status.UNKNOWN -> stringResource(R.string.runs_state_unknown)
    }
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        if (run.status == AgentRun.Status.RUNNING || run.status == AgentRun.Status.QUEUED) {
            CircularProgressIndicator(modifier = Modifier.padding(2.dp))
        }
        Text(label, style = MaterialTheme.typography.titleSmall)
    }
    run.error?.let {
        Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall, maxLines = 3, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun RunLogRow(line: RunLogLine) {
    val color = when (line.kind) {
        RunLogLine.Kind.TOOL_ERROR -> MaterialTheme.colorScheme.error
        else -> MaterialTheme.colorScheme.onSurface
    }
    val prefix = when (line.kind) {
        RunLogLine.Kind.TOOL_START -> "▶ "
        RunLogLine.Kind.TOOL_DONE -> "✓ "
        RunLogLine.Kind.TOOL_ERROR -> "✗ "
        RunLogLine.Kind.INTERIM -> "» "
    }
    Text(
        prefix + line.text,
        color = color,
        style = MaterialTheme.typography.bodySmall,
        maxLines = 3,
        overflow = TextOverflow.Ellipsis,
    )
}

@Composable
private fun SteerDialog(onSend: (String) -> Unit, onDismiss: () -> Unit) {
    var text by rememberSaveable { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.runs_steer_title)) },
        text = {
            OutlinedTextField(
                value = text, onValueChange = { text = it },
                label = { Text(stringResource(R.string.runs_steer_label)) },
                maxLines = 4, modifier = Modifier.fillMaxWidth(),
            )
        },
        confirmButton = {
            TextButton(onClick = { onSend(text) }, enabled = text.isNotBlank()) {
                Text(stringResource(R.string.runs_steer_send))
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_dismiss)) } },
    )
}

@Composable
private fun Centered(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Column(
        modifier = modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) { content() }
}
