package com.a9ito.hermesagent.ui.common

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.a9ito.hermesagent.R
import com.a9ito.hermesagent.core.StatusFormat
import kotlinx.coroutines.delay

/**
 * A floating status strip that sits just ABOVE the chat input, matching the
 * app's Expressive floating-pill design (rounded, tonally elevated, inset from
 * the edges, not docked edge-to-edge). It surfaces, left to right:
 *
 *  - the live agent-turn timer while a turn is streaming (ticks every second),
 *  - the gateway-wide subagent count while a turn is active (omitted when zero),
 *  - the last turn's token count (raw "~N tok"; the API server exposes no
 *    context-window size, so this is deliberately a count, never a percentage),
 *  - the session age (how long this session has existed).
 *
 * When idle (no turn running) it stays visible but shows only the always-true
 * facts (session age, and the last token count if a turn has completed). It
 * hides entirely only when there is nothing at all to show.
 *
 * Pure presentation: the caller owns all state. The one bit of local state is
 * the ticking clock, driven by [agentRunning] so it stops when the turn ends.
 */
@Composable
fun AgentStatusStrip(
    agentRunning: Boolean,
    turnStartedAtMs: Long?,
    sessionStartedAtEpochSec: Double?,
    lastTurnTokens: Long,
    subagents: Int,
    modifier: Modifier = Modifier,
) {
    // A clock that only advances while a turn is live, so the timer ticks during
    // a run and freezes (and the strip stops recomposing per-second) when idle.
    var nowMs by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(agentRunning) {
        while (agentRunning) {
            nowMs = System.currentTimeMillis()
            delay(1_000)
        }
    }

    val agentElapsed: String? = if (agentRunning && turnStartedAtMs != null) {
        StatusFormat.elapsedTimer(nowMs - turnStartedAtMs)
    } else {
        null
    }
    val sessionAge: String? = sessionStartedAtEpochSec?.let { startedSec ->
        val ageMs = System.currentTimeMillis() - (startedSec * 1000).toLong()
        if (ageMs >= 0) StatusFormat.age(ageMs) else null
    }
    val tokenLabel: String? = if (lastTurnTokens > 0) {
        stringResource(R.string.status_strip_tokens, StatusFormat.tokens(lastTurnTokens))
    } else {
        null
    }
    val subagentLabel: String? = if (agentRunning && subagents > 0) {
        stringResource(R.string.status_strip_subagents, subagents)
    } else {
        null
    }

    val hasAnything = agentElapsed != null || sessionAge != null || tokenLabel != null || subagentLabel != null

    AnimatedVisibility(
        visible = hasAnything,
        enter = fadeIn() + expandVertically(),
        exit = fadeOut() + shrinkVertically(),
        modifier = modifier,
    ) {
        Surface(
            // Same floating-pill treatment as the nav pill: rounded, tonally
            // elevated, inset from the edges so it reads as hovering over the
            // input rather than a docked bar.
            shape = MaterialTheme.shapes.large,
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            tonalElevation = 3.dp,
            shadowElevation = 4.dp,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 4.dp),
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                agentElapsed?.let {
                    StatusCell(stringResource(R.string.status_strip_agent, it), emphasis = true)
                }
                subagentLabel?.let { StatusCell(it) }
                tokenLabel?.let { StatusCell(it) }
                sessionAge?.let {
                    StatusCell(stringResource(R.string.status_strip_session, it))
                }
            }
        }
    }
}

@Composable
private fun StatusCell(text: String, emphasis: Boolean = false) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelMedium,
        color = if (emphasis) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
}
