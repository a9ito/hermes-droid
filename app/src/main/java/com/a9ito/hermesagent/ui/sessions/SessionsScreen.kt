package com.a9ito.hermesagent.ui.sessions

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
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
import com.a9ito.hermesagent.core.SessionSummary
import com.a9ito.hermesagent.ui.common.ConnectionGate
import com.a9ito.hermesagent.ui.common.ModelPickerDialog
import com.a9ito.hermesagent.ui.messageRes

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SessionsScreen(
    onOpenSettings: () -> Unit,
    onOpenSession: (String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: SessionsViewModel = viewModel(
        factory = SessionsViewModel.Factory(ServiceLocator.hermes()),
    ),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var showNewSession by rememberSaveable { mutableStateOf(false) }

    // A freshly created (or selected) session id -> navigate to its chat. Done in
    // a LaunchedEffect so navigation is a post-composition side effect, never run
    // inline during composition (which would loop / navigate mid-frame).
    LaunchedEffect(state.openSessionId) {
        state.openSessionId?.let { id ->
            viewModel.consumeOpen()
            onOpenSession(id)
        }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.nav_sessions)) },
                actions = {
                    if (state.configured) {
                        IconButton(onClick = viewModel::refresh) {
                            Icon(Icons.Filled.Refresh, contentDescription = stringResource(R.string.cd_refresh_status))
                        }
                    }
                },
            )
        },
        floatingActionButton = {
            if (state.configured) {
                FloatingActionButton(onClick = { showNewSession = true }) {
                    Icon(Icons.Filled.Add, contentDescription = stringResource(R.string.sessions_new))
                }
            }
        },
    ) { innerPadding ->
        if (state.configLoaded && !state.configured) {
            ConnectionGate(
                title = stringResource(R.string.sessions_locked_title),
                subtitle = stringResource(R.string.sessions_locked_subtitle),
                actionLabel = stringResource(R.string.chat_locked_action),
                onAction = onOpenSettings,
                modifier = Modifier.padding(innerPadding),
            )
            return@Scaffold
        }

        if (showNewSession) {
            NewSessionDialog(
                state = state,
                onRefreshModels = viewModel::refreshModelOptions,
                onDismiss = { showNewSession = false },
                onCreate = { title, model, systemPrompt ->
                    showNewSession = false
                    viewModel.createAndOpen(title, model, systemPrompt)
                },
            )
        }

        SessionsContent(
            state = state,
            contentPadding = innerPadding,
            onOpen = viewModel::open,
            onQueryChange = viewModel::onQueryChange,
            onSourceFilterChange = viewModel::onSourceFilterChange,
            onDelete = viewModel::delete,
            onFork = viewModel::fork,
            onRename = viewModel::rename,
            onTogglePin = viewModel::togglePin,
            onArchive = viewModel::archive,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SessionsContent(
    state: SessionsUiState,
    contentPadding: PaddingValues,
    onOpen: (String) -> Unit,
    onQueryChange: (String) -> Unit,
    onSourceFilterChange: (String?) -> Unit,
    onDelete: (String) -> Unit,
    onFork: (String) -> Unit,
    onRename: (String, String) -> Unit,
    onTogglePin: (String, Boolean) -> Unit,
    onArchive: (String) -> Unit,
) {
    var confirmDelete by remember { mutableStateOf<SessionSummary?>(null) }
    var confirmArchive by remember { mutableStateOf<SessionSummary?>(null) }
    var renaming by remember { mutableStateOf<SessionSummary?>(null) }
    val errorKind = state.errorKind
    val visible = state.visibleSessions

    Column(modifier = Modifier.fillMaxSize().padding(contentPadding)) {
        // Search box: shown once there is anything to search, so an empty instance
        // isn't cluttered. Filtering is client-side over the already-loaded list.
        if (state.sessions.isNotEmpty()) {
            OutlinedTextField(
                value = state.query,
                onValueChange = onQueryChange,
                singleLine = true,
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                trailingIcon = {
                    if (state.query.isNotEmpty()) {
                        IconButton(onClick = { onQueryChange("") }) {
                            Icon(Icons.Filled.Clear, contentDescription = stringResource(R.string.sessions_search_clear))
                        }
                    }
                },
                placeholder = { Text(stringResource(R.string.sessions_search_hint)) },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
            )
            // Source filter chips: only when the instance mixes sources (CLI, Discord, api_server,
            // ...). A single-source instance gets no clutter.
            if (state.sources.size > 1) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    FilterChip(
                        selected = state.sourceFilter == null,
                        onClick = { onSourceFilterChange(null) },
                        label = { Text(stringResource(R.string.sessions_source_all)) },
                    )
                    state.sources.forEach { src ->
                        FilterChip(
                            selected = state.sourceFilter == src,
                            onClick = { onSourceFilterChange(if (state.sourceFilter == src) null else src) },
                            label = { Text(src) },
                        )
                    }
                }
            }
        }
        when {
            state.loading && state.sessions.isEmpty() ->
                CenteredMessage { CircularProgressIndicator() }
            errorKind != null && state.sessions.isEmpty() ->
                CenteredMessage {
                    Text(
                        stringResource(R.string.sessions_error_prefix, stringResource(errorKind.messageRes())),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            state.sessions.isEmpty() ->
                CenteredMessage {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(stringResource(R.string.sessions_empty_title), style = MaterialTheme.typography.titleMedium)
                        Text(stringResource(R.string.sessions_empty_subtitle), style = MaterialTheme.typography.bodyMedium)
                    }
                }
            visible.isEmpty() ->
                CenteredMessage {
                    Text(
                        stringResource(R.string.sessions_search_empty, state.query),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            else -> LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(visible, key = { it.id }) { session ->
                    SessionRow(
                        session = session,
                        onOpen = { onOpen(session.id) },
                        onDelete = { confirmDelete = session },
                        onFork = { onFork(session.id) },
                        onRename = { renaming = session },
                        onTogglePin = { onTogglePin(session.id, session.pinned) },
                        onArchive = { confirmArchive = session },
                    )
                }
            }
        }
    }

    confirmDelete?.let { session ->
        AlertDialog(
            onDismissRequest = { confirmDelete = null },
            title = { Text(stringResource(R.string.sessions_delete_title)) },
            text = { Text(stringResource(R.string.sessions_delete_message, session.title)) },
            confirmButton = {
                TextButton(onClick = { onDelete(session.id); confirmDelete = null }) {
                    Text(stringResource(R.string.sessions_delete_confirm))
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = null }) { Text(stringResource(R.string.action_dismiss)) }
            },
        )
    }

    confirmArchive?.let { session ->
        AlertDialog(
            onDismissRequest = { confirmArchive = null },
            title = { Text(stringResource(R.string.sessions_archive_title)) },
            text = { Text(stringResource(R.string.sessions_archive_message, session.title)) },
            confirmButton = {
                TextButton(onClick = { onArchive(session.id); confirmArchive = null }) {
                    Text(stringResource(R.string.sessions_archive_confirm))
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmArchive = null }) { Text(stringResource(R.string.action_dismiss)) }
            },
        )
    }

    renaming?.let { session ->
        var newTitle by remember(session.id) { mutableStateOf(session.title) }
        AlertDialog(
            onDismissRequest = { renaming = null },
            title = { Text(stringResource(R.string.sessions_rename_title)) },
            text = {
                OutlinedTextField(
                    value = newTitle,
                    onValueChange = { newTitle = it },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            },
            confirmButton = {
                TextButton(onClick = { onRename(session.id, newTitle); renaming = null }) {
                    Text(stringResource(R.string.sessions_rename_confirm))
                }
            },
            dismissButton = {
                TextButton(onClick = { renaming = null }) { Text(stringResource(R.string.action_dismiss)) }
            },
        )
    }
}

@Composable
private fun NewSessionDialog(
    state: SessionsUiState,
    onRefreshModels: () -> Unit,
    onDismiss: () -> Unit,
    onCreate: (title: String?, model: String?, systemPrompt: String?) -> Unit,
) {
    var title by rememberSaveable { mutableStateOf("") }
    var systemPrompt by rememberSaveable { mutableStateOf("") }
    var model by rememberSaveable { mutableStateOf<String?>(null) }
    var showModelPicker by rememberSaveable { mutableStateOf(false) }
    val hasPicker = state.modelOptions?.isEmpty == false || state.availableModels.isNotEmpty()

    if (showModelPicker) {
        // Opening the picker triggers a fresh, tier-settling fetch (refresh=true):
        // the cheap startup load can report pricing_pending and lock every free
        // model, so this is what actually unlocks them. Runs once per open.
        LaunchedEffect(showModelPicker) { onRefreshModels() }
        ModelPickerDialog(
            modelOptions = state.modelOptions,
            flatModels = state.availableModels,
            current = model,
            refreshing = state.refreshingModels,
            onPick = { showModelPicker = false; model = it },
            onDismiss = { showModelPicker = false },
        )
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.sessions_new_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    singleLine = true,
                    label = { Text(stringResource(R.string.sessions_new_name_label)) },
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = systemPrompt,
                    onValueChange = { systemPrompt = it },
                    label = { Text(stringResource(R.string.sessions_new_system_label)) },
                    minLines = 2,
                    maxLines = 5,
                    modifier = Modifier.fillMaxWidth(),
                )
                if (hasPicker) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(
                            text = model ?: stringResource(R.string.sessions_new_model_default),
                            style = MaterialTheme.typography.bodyMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f),
                        )
                        TextButton(onClick = { showModelPicker = true }) {
                            Text(stringResource(R.string.sessions_new_model_label))
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onCreate(title.ifBlank { null }, model, systemPrompt.ifBlank { null }) }) {
                Text(stringResource(R.string.sessions_new_create))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_dismiss)) }
        },
    )
}

@Composable
private fun SessionRow(
    session: SessionSummary,
    onOpen: () -> Unit,
    onDelete: () -> Unit,
    onFork: () -> Unit,
    onRename: () -> Unit,
    onTogglePin: () -> Unit,
    onArchive: () -> Unit,
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = MaterialTheme.shapes.large,
        onClick = onOpen,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                if (session.pinned) {
                    Text(
                        text = stringResource(R.string.sessions_pinned_badge),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(end = 6.dp),
                    )
                }
                Text(
                    text = session.title,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = onDelete) {
                    Icon(Icons.Filled.Delete, contentDescription = stringResource(R.string.sessions_delete_confirm))
                }
            }
            val countLabel = stringResource(R.string.sessions_message_count, session.messageCount)
            val forkLabel = stringResource(R.string.sessions_fork_badge)
            val meta = buildString {
                append(countLabel)
                session.model?.let { append("  •  ").append(it) }
                if (session.isFork) append("  •  ").append(forkLabel)
            }
            Text(meta, style = MaterialTheme.typography.labelSmall)
            session.preview?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                TextButton(onClick = onTogglePin) {
                    Text(stringResource(
                        if (session.pinned) R.string.sessions_unpin_action else R.string.sessions_pin_action
                    ))
                }
                TextButton(onClick = onRename) { Text(stringResource(R.string.sessions_rename_action)) }
                TextButton(onClick = onFork) { Text(stringResource(R.string.sessions_fork_action)) }
                TextButton(onClick = onArchive) { Text(stringResource(R.string.sessions_archive_action)) }
            }
        }
    }
}

@Composable
private fun CenteredMessage(content: @Composable () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) { content() }
}
