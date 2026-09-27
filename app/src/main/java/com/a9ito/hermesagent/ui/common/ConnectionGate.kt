package com.a9ito.hermesagent.ui.common

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

/**
 * Full-screen "connection required" gate shown on Chat/Status when host+token
 * are not yet configured. Enforces the acceptance criterion that chat cannot be
 * opened without a saved host + token.
 */
@Composable
fun ConnectionGate(
    title: String,
    subtitle: String,
    actionLabel: String,
    onAction: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(text = title, style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center)
        Text(text = subtitle, style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center)
        Button(onClick = onAction) { Text(actionLabel) }
    }
}
