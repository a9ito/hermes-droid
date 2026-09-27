package com.a9ito.hermesagent.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.a9ito.hermesagent.R
import com.a9ito.hermesagent.ServiceLocator

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    modifier: Modifier = Modifier,
    viewModel: SettingsViewModel = viewModel(
        factory = SettingsViewModel.Factory(ServiceLocator.settings()),
    ),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    val savedMessage = stringResource(R.string.settings_saved)
    val clearedMessage = stringResource(R.string.settings_cleared)
    LaunchedEffect(state.saved, state.cleared) {
        if (state.saved) snackbarHostState.showSnackbar(savedMessage)
        if (state.cleared) snackbarHostState.showSnackbar(clearedMessage)
        if (state.saved || state.cleared) viewModel.consumeEvents()
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = { TopAppBar(title = { Text(stringResource(R.string.settings_title)) }) },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { innerPadding ->
        SettingsContent(
            state = state,
            contentPadding = innerPadding,
            onHostChange = viewModel::onHostChange,
            onPortChange = viewModel::onPortChange,
            onTokenChange = viewModel::onTokenChange,
            onToggleToken = viewModel::toggleTokenVisibility,
            onSave = viewModel::save,
            onClear = viewModel::clear,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SettingsContent(
    state: SettingsUiState,
    contentPadding: PaddingValues,
    onHostChange: (String) -> Unit,
    onPortChange: (String) -> Unit,
    onTokenChange: (String) -> Unit,
    onToggleToken: () -> Unit,
    onSave: () -> Unit,
    onClear: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(contentPadding)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(stringResource(R.string.settings_connection_header))
                Text(stringResource(R.string.settings_connection_subtitle))
            }
        }

        val hostError = state.invalidField == SettingsField.HOST
        OutlinedTextField(
            value = state.host,
            onValueChange = onHostChange,
            modifier = Modifier.fillMaxWidth(),
            label = { Text(stringResource(R.string.settings_host_label)) },
            placeholder = { Text(stringResource(R.string.settings_host_placeholder)) },
            singleLine = true,
            isError = hostError,
            supportingText = {
                Text(
                    if (hostError) stringResource(R.string.settings_error_host_invalid)
                    else stringResource(R.string.settings_host_supporting),
                )
            },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
        )

        val portError = state.invalidField == SettingsField.PORT
        OutlinedTextField(
            value = state.port,
            onValueChange = onPortChange,
            modifier = Modifier.fillMaxWidth(),
            label = { Text(stringResource(R.string.settings_port_label)) },
            placeholder = { Text(stringResource(R.string.settings_port_placeholder)) },
            singleLine = true,
            isError = portError,
            supportingText = {
                Text(
                    if (portError) stringResource(R.string.settings_error_port_invalid)
                    else stringResource(R.string.settings_port_supporting),
                )
            },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        )

        val tokenError = state.invalidField == SettingsField.TOKEN
        OutlinedTextField(
            value = state.token,
            onValueChange = onTokenChange,
            modifier = Modifier.fillMaxWidth(),
            label = { Text(stringResource(R.string.settings_token_label)) },
            placeholder = { Text(stringResource(R.string.settings_token_placeholder)) },
            singleLine = true,
            isError = tokenError,
            visualTransformation = if (state.tokenVisible) VisualTransformation.None else PasswordVisualTransformation(),
            trailingIcon = {
                TextButton(onClick = onToggleToken) {
                    Text(
                        if (state.tokenVisible) stringResource(R.string.settings_token_hide)
                        else stringResource(R.string.settings_token_show),
                    )
                }
            },
            supportingText = {
                Text(
                    if (tokenError) stringResource(R.string.settings_error_token_required)
                    else stringResource(R.string.settings_token_supporting),
                )
            },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
        )

        state.resolvedEndpoint?.let { endpoint ->
            Text(stringResource(R.string.settings_resolved_endpoint, endpoint))
        }

        Button(onClick = onSave, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.settings_save))
        }

        OutlinedButton(onClick = onClear, modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.Filled.Clear, contentDescription = null)
            Text(
                text = stringResource(R.string.settings_clear),
                modifier = Modifier.padding(start = 8.dp),
            )
        }
    }
}
