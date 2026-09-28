package com.a9ito.hermesagent.ui.sessions

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Clear
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
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.a9ito.hermesagent.R
import com.a9ito.hermesagent.ServiceLocator
import com.a9ito.hermesagent.core.ChatAttachment
import com.a9ito.hermesagent.core.ChatMessage
import com.a9ito.hermesagent.core.ModelOptions
import com.a9ito.hermesagent.core.ToolActivity
import com.a9ito.hermesagent.ui.common.ImageAttachmentLoader
import com.a9ito.hermesagent.ui.messageRes
import kotlinx.coroutines.launch

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
                    val hasPicker = state.modelOptions?.isEmpty == false || state.availableModels.isNotEmpty()
                    if (hasPicker) {
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
                modelOptions = state.modelOptions,
                flatModels = state.availableModels,
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
                attachments = state.pendingAttachments,
                onInputChange = viewModel::onInputChange,
                onSend = viewModel::send,
                onStop = viewModel::stop,
                onAddAttachment = viewModel::addAttachment,
                onRemoveAttachment = viewModel::removeAttachment,
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
private fun MessageBubble(message: ChatMessage) {
    val isUser = message.role == ChatMessage.Role.USER
    val clipboard = LocalClipboardManager.current
    val haptics = LocalHapticFeedback.current
    val context = LocalContext.current
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
    val copiedLabel = stringResource(R.string.chat_copied)
    Column(modifier = Modifier.fillMaxWidth(), horizontalAlignment = alignment) {
        Text(
            text = stringResource(if (isUser) R.string.chat_sender_you else R.string.chat_sender_agent),
            style = MaterialTheme.typography.labelSmall,
        )
        // Live agent activity for this turn: reasoning indicator, tool run rows,
        // and mid-turn commentary — shown above the answer bubble (assistant only).
        if (!isUser) {
            ActivityTrail(message)
        }
        val hasBubbleText = message.error || message.text.isNotEmpty() ||
            (message.streaming && message.activities.isEmpty() && message.commentary.isEmpty())
        if (hasBubbleText) {
            Surface(
                color = bubbleColor,
                shape = MaterialTheme.shapes.large,
                // Long-press copies the answer text to the clipboard. Only offered
                // when there is real text (not the "thinking…" placeholder or an
                // error bubble, which have nothing worth copying).
                modifier = Modifier
                    .widthIn(max = 320.dp)
                    .combinedClickable(
                        enabled = message.text.isNotEmpty() && !message.error,
                        onClick = {},
                        onLongClick = {
                            clipboard.setText(AnnotatedString(message.text))
                            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                            Toast.makeText(context, copiedLabel, Toast.LENGTH_SHORT).show()
                        },
                    ),
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
        if (message.attachmentCount > 0) {
            Text(
                text = stringResource(R.string.chat_attachment_badge, message.attachmentCount),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        // Collapsible reasoning for a completed assistant turn (server-history replay).
        if (!isUser && !message.reasoning.isNullOrBlank()) {
            ReasoningPanel(message.reasoning)
        }
    }
}

/**
 * A collapsed-by-default "Reasoning" disclosure for a completed assistant turn.
 * Tapping the header toggles the full thinking text. Kept separate from the live
 * [ActivityTrail] (which shows in-progress reasoning) — this is the persisted,
 * reviewable transcript version.
 */
@Composable
private fun ReasoningPanel(reasoning: String) {
    var expanded by rememberSaveable(reasoning) { mutableStateOf(false) }
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = MaterialTheme.shapes.medium,
        onClick = { expanded = !expanded },
        modifier = Modifier.widthIn(max = 320.dp).padding(top = 2.dp),
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

/**
 * The agent's live "what am I doing" trail for one assistant turn: a thinking
 * chip while reasoning, one row per tool call with a status glyph, and any
 * mid-turn commentary. Renders nothing when the turn had no activity, so a
 * plain text answer looks exactly as before.
 */
@Composable
private fun ActivityTrail(message: ChatMessage) {
    val thinking = message.thinking && message.text.isEmpty()
    if (!thinking && message.activities.isEmpty() && message.commentary.isEmpty()) return
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = MaterialTheme.shapes.medium,
        modifier = Modifier.widthIn(max = 320.dp).padding(vertical = 2.dp),
    ) {
        Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
            if (thinking) {
                Text(
                    text = stringResource(R.string.chat_reasoning),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            message.activities.forEach { activity ->
                val glyph = when (activity.status) {
                    ToolActivity.Status.RUNNING -> "•"
                    ToolActivity.Status.DONE -> "✓"
                    ToolActivity.Status.FAILED -> "✕"
                }
                val tint = when (activity.status) {
                    ToolActivity.Status.FAILED -> MaterialTheme.colorScheme.error
                    else -> MaterialTheme.colorScheme.onSurfaceVariant
                }
                Text(
                    text = "$glyph ${activity.toolName}",
                    style = MaterialTheme.typography.labelMedium,
                    color = tint,
                )
            }
            message.commentary.forEach { line ->
                Text(
                    text = line,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun InputBar(
    input: String,
    sending: Boolean,
    attachments: List<ChatAttachment>,
    onInputChange: (String) -> Unit,
    onSend: () -> Unit,
    onStop: () -> Unit,
    onAddAttachment: (ChatAttachment) -> Unit,
    onRemoveAttachment: (Int) -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var attachError by remember { mutableStateOf<Int?>(null) }

    // OpenDocument gives a persistable read grant; "image/*" filters to images,
    // matching what the server's session-chat endpoint accepts (inline images only).
    val picker = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent(),
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            when (val res = ImageAttachmentLoader.load(context, uri)) {
                is ImageAttachmentLoader.Result.Ok -> { attachError = null; onAddAttachment(res.attachment) }
                ImageAttachmentLoader.Result.Error.NotAnImage -> attachError = R.string.chat_attach_not_image
                ImageAttachmentLoader.Result.Error.TooLarge -> attachError = R.string.chat_attach_too_large
                else -> attachError = R.string.chat_attach_failed
            }
        }
    }
    val canSend = (input.isNotBlank() || attachments.isNotEmpty()) && !sending

    Surface(tonalElevation = 3.dp) {
        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp)) {
            if (attachments.isNotEmpty()) {
                FlowRow(
                    modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    attachments.forEachIndexed { index, att ->
                        Surface(
                            color = MaterialTheme.colorScheme.secondaryContainer,
                            shape = MaterialTheme.shapes.small,
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(start = 8.dp, end = 2.dp, top = 2.dp, bottom = 2.dp),
                            ) {
                                Text(
                                    text = att.displayName ?: stringResource(R.string.chat_attach_image_generic),
                                    style = MaterialTheme.typography.labelSmall,
                                    modifier = Modifier.widthIn(max = 140.dp),
                                    maxLines = 1,
                                )
                                IconButton(onClick = { onRemoveAttachment(index) }, modifier = Modifier.padding(0.dp)) {
                                    Icon(
                                        Icons.Filled.Clear,
                                        contentDescription = stringResource(R.string.chat_attach_remove),
                                        modifier = Modifier.padding(2.dp),
                                    )
                                }
                            }
                        }
                    }
                }
            }
            attachError?.let {
                Text(
                    text = stringResource(it),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(bottom = 4.dp),
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                IconButton(
                    onClick = { attachError = null; picker.launch("image/*") },
                    enabled = !sending && attachments.size < ChatAttachment.MAX_PER_TURN,
                ) {
                    Icon(Icons.Filled.Add, contentDescription = stringResource(R.string.chat_attach_add))
                }
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
                    IconButton(onClick = onSend, enabled = canSend) {
                        Icon(Icons.AutoMirrored.Filled.Send, contentDescription = stringResource(R.string.cd_send_message))
                    }
                }
            }
        }
    }
}

/**
 * Model picker. When the instance exposes the rich /api/model/options catalog,
 * models are grouped by provider with capability + pricing hints and
 * unavailable/needs-auth states; otherwise it falls back to the flat id list.
 */
@Composable
private fun ModelPickerDialog(
    modelOptions: ModelOptions?,
    flatModels: List<String>,
    current: String?,
    onPick: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.session_model_title)) },
        text = {
            if (modelOptions != null && !modelOptions.isEmpty) {
                RichModelList(modelOptions, current, onPick)
            } else {
                FlatModelList(flatModels, current, onPick)
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_dismiss)) }
        },
    )
}

@Composable
private fun RichModelList(options: ModelOptions, current: String?, onPick: (String) -> Unit) {
    LazyColumn {
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
                val selectable = !model.unavailable && !provider.needsAuth
                val selected = model.id == current
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
                            if (model.unavailable) add(stringResource(R.string.model_unavailable))
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
