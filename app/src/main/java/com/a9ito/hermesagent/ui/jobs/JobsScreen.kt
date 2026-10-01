package com.a9ito.hermesagent.ui.jobs

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.FloatingActionButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
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
import com.a9ito.hermesagent.core.CronJob
import com.a9ito.hermesagent.ui.common.ConnectionGate
import com.a9ito.hermesagent.ui.messageRes

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun JobsScreen(
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: JobsViewModel = viewModel(
        factory = JobsViewModel.Factory(ServiceLocator.hermes()),
    ),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    var showCreate by rememberSaveable { mutableStateOf(false) }
    // Job currently being edited (its id), or null. Stored by id so the dialog
    // re-resolves the row from fresh state rather than holding a stale copy.
    var editingId by rememberSaveable { mutableStateOf<String?>(null) }

    val runMsg = stringResource(R.string.jobs_run_triggered)
    val failMsg = stringResource(R.string.jobs_action_failed)
    LaunchedEffect(state.notice) {
        when (state.notice) {
            JobsUiState.Notice.RUN_TRIGGERED -> snackbar.showMessage(runMsg)
            JobsUiState.Notice.ACTION_FAILED -> snackbar.showMessage(failMsg)
            null -> Unit
        }
        if (state.notice != null) viewModel.consumeNotice()
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.nav_jobs)) },
                actions = {
                    if (state.configured) {
                        IconButton(onClick = viewModel::refresh) {
                            Icon(Icons.Filled.Refresh, contentDescription = stringResource(R.string.cd_refresh_status))
                        }
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
        floatingActionButton = {
            if (state.configured) {
                FloatingActionButton(onClick = { showCreate = true }) {
                    Icon(Icons.Filled.Add, contentDescription = stringResource(R.string.jobs_new))
                }
            }
        },
    ) { innerPadding ->
        if (state.configLoaded && !state.configured) {
            ConnectionGate(
                title = stringResource(R.string.jobs_locked_title),
                subtitle = stringResource(R.string.jobs_locked_subtitle),
                actionLabel = stringResource(R.string.chat_locked_action),
                onAction = onOpenSettings,
                modifier = Modifier.padding(innerPadding),
            )
            return@Scaffold
        }

        JobsContent(
            state = state,
            contentPadding = innerPadding,
            onPause = viewModel::pause,
            onResume = viewModel::resume,
            onRun = viewModel::runNow,
            onDelete = viewModel::delete,
            onEdit = { editingId = it },
        )
    }

    if (showCreate) {
        JobDialog(
            titleRes = R.string.jobs_new,
            confirmRes = R.string.jobs_create_confirm,
            initialName = "",
            initialSchedule = "",
            initialPrompt = "",
            onConfirm = { name, schedule, prompt ->
                showCreate = false
                viewModel.create(name, schedule, prompt)
            },
            onDismiss = { showCreate = false },
        )
    }

    editingId?.let { id ->
        val job = state.jobs.firstOrNull { it.id == id }
        if (job == null) {
            // Row vanished (deleted/refreshed) while the dialog was open: close it.
            editingId = null
        } else {
            JobDialog(
                titleRes = R.string.jobs_edit,
                confirmRes = R.string.jobs_save_confirm,
                initialName = job.name,
                initialSchedule = job.scheduleDisplay,
                initialPrompt = job.prompt.orEmpty(),
                onConfirm = { name, schedule, prompt ->
                    editingId = null
                    viewModel.update(id, name, schedule, prompt)
                },
                onDismiss = { editingId = null },
            )
        }
    }
}

@Composable
private fun JobsContent(
    state: JobsUiState,
    contentPadding: PaddingValues,
    onPause: (String) -> Unit,
    onResume: (String) -> Unit,
    onRun: (String) -> Unit,
    onDelete: (String) -> Unit,
    onEdit: (String) -> Unit,
) {
    var confirmDelete by remember { mutableStateOf<CronJob?>(null) }
    val errorKind = state.errorKind

    Column(modifier = Modifier.fillMaxSize().padding(contentPadding)) {
        when {
            state.loading && state.jobs.isEmpty() ->
                Centered { CircularProgressIndicator() }
            errorKind != null && state.jobs.isEmpty() ->
                Centered {
                    Text(
                        stringResource(R.string.jobs_error_prefix, stringResource(errorKind.messageRes())),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            state.jobs.isEmpty() ->
                Centered {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(stringResource(R.string.jobs_empty_title), style = MaterialTheme.typography.titleMedium)
                        Text(stringResource(R.string.jobs_empty_subtitle), style = MaterialTheme.typography.bodyMedium)
                    }
                }
            else -> LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(state.jobs, key = { it.id }) { job ->
                    JobRow(
                        job = job,
                        onPause = { onPause(job.id) },
                        onResume = { onResume(job.id) },
                        onRun = { onRun(job.id) },
                        onDelete = { confirmDelete = job },
                        onEdit = { onEdit(job.id) },
                    )
                }
            }
        }
    }

    confirmDelete?.let { job ->
        AlertDialog(
            onDismissRequest = { confirmDelete = null },
            title = { Text(stringResource(R.string.jobs_delete_title)) },
            text = { Text(stringResource(R.string.jobs_delete_message, job.name)) },
            confirmButton = {
                TextButton(onClick = { onDelete(job.id); confirmDelete = null }) {
                    Text(stringResource(R.string.jobs_delete_confirm))
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = null }) { Text(stringResource(R.string.action_dismiss)) }
            },
        )
    }
}

@Composable
private fun JobRow(
    job: CronJob,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onRun: () -> Unit,
    onDelete: () -> Unit,
    onEdit: () -> Unit,
) {
    val stateLabel = when (job.state) {
        CronJob.State.SCHEDULED -> stringResource(R.string.jobs_state_scheduled)
        CronJob.State.PAUSED -> stringResource(R.string.jobs_state_paused)
        CronJob.State.COMPLETED -> stringResource(R.string.jobs_state_completed)
        CronJob.State.ERROR -> stringResource(R.string.jobs_state_error)
        CronJob.State.UNKNOWN -> stringResource(R.string.jobs_state_unknown)
    }
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = MaterialTheme.shapes.large,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(job.name, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                stringResource(R.string.jobs_meta, stateLabel, job.scheduleDisplay),
                style = MaterialTheme.typography.labelSmall,
            )
            job.nextRunAt?.let {
                Text(stringResource(R.string.jobs_next_run, it), style = MaterialTheme.typography.labelSmall)
            }
            job.lastError?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                TextButton(onClick = onRun) { Text(stringResource(R.string.jobs_run)) }
                TextButton(onClick = onEdit) { Text(stringResource(R.string.jobs_edit_action)) }
                if (job.canPause) TextButton(onClick = onPause) { Text(stringResource(R.string.jobs_pause)) }
                if (job.canResume) TextButton(onClick = onResume) { Text(stringResource(R.string.jobs_resume)) }
                TextButton(onClick = onDelete) { Text(stringResource(R.string.jobs_delete_confirm)) }
            }
        }
    }
}

@Composable
private fun JobDialog(
    titleRes: Int,
    confirmRes: Int,
    initialName: String,
    initialSchedule: String,
    initialPrompt: String,
    onConfirm: (name: String, schedule: String, prompt: String) -> Unit,
    onDismiss: () -> Unit,
) {
    // Keyed on the initial values so reopening the dialog for a different job
    // (or switching create->edit) reseeds the fields instead of keeping stale text.
    var name by rememberSaveable(initialName) { mutableStateOf(initialName) }
    var schedule by rememberSaveable(initialSchedule) { mutableStateOf(initialSchedule) }
    var prompt by rememberSaveable(initialPrompt) { mutableStateOf(initialPrompt) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(titleRes)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = name, onValueChange = { name = it }, singleLine = true,
                    label = { Text(stringResource(R.string.jobs_field_name)) },
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = schedule, onValueChange = { schedule = it }, singleLine = true,
                    label = { Text(stringResource(R.string.jobs_field_schedule)) },
                    placeholder = { Text(stringResource(R.string.jobs_field_schedule_hint)) },
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = prompt, onValueChange = { prompt = it },
                    label = { Text(stringResource(R.string.jobs_field_prompt)) },
                    maxLines = 4, modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(name, schedule, prompt) },
                enabled = name.isNotBlank() && schedule.isNotBlank(),
            ) { Text(stringResource(confirmRes)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_dismiss)) }
        },
    )
}

@Composable
private fun Centered(content: @Composable () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) { content() }
}

private suspend fun SnackbarHostState.showMessage(message: String) {
    // Replace any in-flight snackbar so rapid actions don't queue up.
    currentSnackbarData?.dismiss()
    showSnackbar(message)
}
