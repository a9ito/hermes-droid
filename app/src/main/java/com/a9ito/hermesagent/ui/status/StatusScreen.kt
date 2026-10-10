package com.a9ito.hermesagent.ui.status

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.a9ito.hermesagent.R
import com.a9ito.hermesagent.ServiceLocator
import com.a9ito.hermesagent.core.InstanceStatus
import com.a9ito.hermesagent.ui.common.ConnectionGate
import com.a9ito.hermesagent.ui.messageRes

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StatusScreen(
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: StatusViewModel = viewModel(
        factory = StatusViewModel.Factory(ServiceLocator.hermes()),
    ),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            androidx.compose.material3.TopAppBar(
                title = { Text(stringResource(R.string.status_title)) },
                actions = {
                    if (state.configured) {
                        IconButton(onClick = viewModel::refresh) {
                            Icon(Icons.Filled.Refresh, contentDescription = stringResource(R.string.cd_refresh_status))
                        }
                    }
                },
            )
        },
    ) { innerPadding ->
        if (state.configLoaded && !state.configured) {
            ConnectionGate(
                title = stringResource(R.string.status_locked_title),
                subtitle = stringResource(R.string.status_locked_subtitle),
                actionLabel = stringResource(R.string.status_locked_action),
                onAction = onOpenSettings,
                modifier = Modifier.padding(innerPadding),
            )
            return@Scaffold
        }

        StatusContent(state = state, contentPadding = innerPadding)
    }
}

@Composable
private fun StatusContent(state: StatusUiState, contentPadding: PaddingValues) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(contentPadding)
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        when {
            state.loading && state.status == null -> {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    CircularProgressIndicator()
                    Text(stringResource(R.string.status_loading))
                }
            }
            state.errorKind != null -> {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = stringResource(
                            R.string.status_error_prefix,
                            stringResource(state.errorKind.messageRes()),
                        ),
                        modifier = Modifier.padding(16.dp),
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
            state.status != null -> {
                StatusCard(state.status)
                state.capabilities?.let { CapabilitiesCard(it) }
            }
        }
    }
}

@Composable
private fun CapabilitiesCard(caps: com.a9ito.hermesagent.core.Capabilities) {
    val on = stringResource(R.string.status_value_yes)
    val off = stringResource(R.string.status_value_no)
    fun mark(b: Boolean) = if (b) on else off

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(R.string.caps_title), style = MaterialTheme.typography.titleMedium)
            if (!caps.knownReachable) {
                Text(
                    stringResource(R.string.caps_unknown),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            HorizontalDivider()
            StatusRow(stringResource(R.string.caps_run_control), mark(caps.supportsRunControl))
            StatusRow(stringResource(R.string.caps_run_steer), mark(caps.runSteer))
            StatusRow(stringResource(R.string.caps_run_approval), mark(caps.runApproval))
            StatusRow(stringResource(R.string.caps_sessions), mark(caps.sessionChat))
            StatusRow(stringResource(R.string.caps_session_fork), mark(caps.sessionFork))
            StatusRow(stringResource(R.string.caps_model_lock), mark(caps.sessionModelLock))
            StatusRow(stringResource(R.string.caps_skills), mark(caps.skillsApi))
            StatusRow(stringResource(R.string.caps_artifacts), mark(caps.supportsArtifacts))
        }
    }
}

@Composable
private fun StatusCard(status: InstanceStatus) {
    val unknown = stringResource(R.string.status_value_unknown)
    val none = stringResource(R.string.status_value_none)
    val yes = stringResource(R.string.status_value_yes)
    val no = stringResource(R.string.status_value_no)
    val reachable = if (status.reachable) stringResource(R.string.status_reachable) else stringResource(R.string.status_unreachable)

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(reachable, style = MaterialTheme.typography.titleMedium)
            HorizontalDivider()
            StatusRow(stringResource(R.string.status_field_state), status.gatewayState ?: unknown)
            StatusRow(stringResource(R.string.status_field_readiness), status.readiness ?: status.overallStatus ?: unknown)
            StatusRow(
                stringResource(R.string.status_field_busy),
                when (status.busy) { true -> yes; false -> no; null -> unknown },
            )
            StatusRow(stringResource(R.string.status_field_active_agents), status.activeAgents?.toString() ?: unknown)
            status.activeApiRuns?.let { StatusRow(stringResource(R.string.status_field_active_runs), it.toString()) }
            status.activeDelegations?.let { StatusRow(stringResource(R.string.status_field_subagents), it.toString()) }
            StatusRow(stringResource(R.string.status_field_model), status.model ?: unknown)
            StatusRow(stringResource(R.string.status_field_version), status.version ?: unknown)
            StatusRow(
                stringResource(R.string.status_field_platforms),
                status.connectedPlatforms.takeIf { it.isNotEmpty() }?.joinToString(", ") ?: none,
            )
            StatusRow(stringResource(R.string.status_field_updated), status.updatedAt ?: unknown)
        }
    }
}

@Composable
private fun StatusRow(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        Text(text = label, style = MaterialTheme.typography.labelLarge, modifier = Modifier.weight(1f))
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f),
            textAlign = TextAlign.End,
        )
    }
    Spacer(Modifier.padding(0.dp))
}
