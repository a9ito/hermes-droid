package com.a9ito.hermesagent.ui.sessions

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.a9ito.hermesagent.R
import com.a9ito.hermesagent.ServiceLocator
import com.a9ito.hermesagent.core.ChatMessage
import com.a9ito.hermesagent.ui.messageRes

/**
 * Chat bound to ONE persisted server session. Loads existing history and streams
 * each turn through /api/sessions/{id}/chat/stream, so the transcript is durable
 * and shared with every other Hermes surface.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SessionChatScreen(
    sessionId: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: SessionChatViewModel = viewModel(
        factory = SessionChatViewModel.Factory(sessionId, ServiceLocator.hermes()),
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
                        Text(stringResource(R.string.session_chat_title))
                        state.model?.let {
                            Text(it, style = MaterialTheme.typography.labelSmall)
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_back))
                    }
                },
                actions = {
                    if (state.availableModels.isNotEmpty()) {
                        TextButton(onClick = { showModelPicker = true }) {
                            Text(stringResource(R.string.session_model_pick))
                        }
                    }
                },
            )
        },
    ) { innerPadding ->
        if (showModelPicker) {
            ModelPickerDialog(
                models = state.availableModels,
                current = state.model,
                onPick = { showModelPicker = false; viewModel.selectModel(it) },
                onDismiss = { showModelPicker = false },
            )
        }
        Column(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
            val listState = rememberLazyListState()
            LaunchedEffect(state.history.messages.size, state.history.messages.lastOrNull()?.text) {
                val count = state.history.messages.size
                if (count > 0) listState.animateScrollToItem(count - 1)
            }

            if (state.loadingHistory && state.history.messages.isEmpty()) {
                Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            } else {
                LazyColumn(
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    state = listState,
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(state.history.messages, key = { it.id }) { message ->
                        MessageBubble(message)
                    }
                }
            }

            InputBar(
                input = state.input,
                sending = state.sending,
                onInputChange = viewModel::onInputChange,
                onSend = viewModel::send,
                onStop = viewModel::stop,
            )
        }
    }
}

@Composable
private fun MessageBubble(message: ChatMessage) {
    val isUser = message.role == ChatMessage.Role.USER
    val bubbleColor = when {
        message.error -> MaterialTheme.colorScheme.errorContainer
        isUser -> MaterialTheme.colorScheme.primaryContainer
        else -> MaterialTheme.colorScheme.surfaceVariant
    }
    val textColor = when {
        message.error -> MaterialTheme.colorScheme.onErrorContainer
        isUser -> MaterialTheme.colorScheme.onPrimaryContainer
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    val alignment = if (isUser) Alignment.End else Alignment.Start
    Column(modifier = Modifier.fillMaxWidth(), horizontalAlignment = alignment) {
        Text(
            text = stringResource(if (isUser) R.string.chat_sender_you else R.string.chat_sender_agent),
            style = MaterialTheme.typography.labelSmall,
        )
        Surface(
            color = bubbleColor,
            shape = MaterialTheme.shapes.large,
            modifier = Modifier.widthIn(max = 320.dp),
        ) {
            val shown = when {
                message.error -> stringResource(
                    R.string.chat_error_prefix,
                    stringResource((message.errorKind ?: com.a9ito.hermesagent.core.ErrorKind.UNEXPECTED).messageRes()),
                )
                message.text.isEmpty() && message.streaming -> stringResource(R.string.chat_thinking)
                else -> message.text
            }
            Text(
                text = shown,
                color = textColor,
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            )
        }
    }
}

@Composable
private fun InputBar(
    input: String,
    sending: Boolean,
    onInputChange: (String) -> Unit,
    onSend: () -> Unit,
    onStop: () -> Unit,
) {
    Surface(tonalElevation = 3.dp) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            OutlinedTextField(
                value = input,
                onValueChange = onInputChange,
                modifier = Modifier.weight(1f),
                placeholder = { Text(stringResource(R.string.chat_input_hint)) },
                maxLines = 5,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
            )
            if (sending) {
                IconButton(onClick = onStop) {
                    Box(contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(modifier = Modifier.padding(4.dp))
                    }
                }
            } else {
                IconButton(onClick = onSend, enabled = input.isNotBlank()) {
                    Icon(Icons.AutoMirrored.Filled.Send, contentDescription = stringResource(R.string.cd_send_message))
                }
            }
        }
    }
}

/** Simple single-choice model picker: tap a model to lock the session to it. */
@Composable
private fun ModelPickerDialog(
    models: List<String>,
    current: String?,
    onPick: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.session_model_title)) },
        text = {
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
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_dismiss)) }
        },
    )
}
