package com.a9ito.hermesagent.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
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
import com.a9ito.hermesagent.core.AccentPreset
import com.a9ito.hermesagent.core.AppearancePrefs
import com.a9ito.hermesagent.core.CornerStyle
import com.a9ito.hermesagent.core.FontChoice
import com.a9ito.hermesagent.core.ThemeMode
import com.a9ito.hermesagent.core.UiScale
import com.a9ito.hermesagent.ui.theme.AppearanceViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    modifier: Modifier = Modifier,
    viewModel: SettingsViewModel = viewModel(
        factory = SettingsViewModel.Factory(ServiceLocator.settings(), ServiceLocator.hermes()),
    ),
    appearanceViewModel: AppearanceViewModel = viewModel(
        factory = AppearanceViewModel.Factory(ServiceLocator.appearance()),
    ),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val appearance by appearanceViewModel.prefs.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    val savedMessage = stringResource(R.string.settings_saved)
    val clearedMessage = stringResource(R.string.settings_cleared)
    LaunchedEffect(state.saved, state.cleared) {
        if (state.saved) snackbarHostState.showSnackbar(savedMessage)
        if (state.cleared) snackbarHostState.showSnackbar(clearedMessage)
        if (state.saved || state.cleared) viewModel.consumeEvents()
    }

    val captureMessages = mapOf(
        CaptureOutcome.CAPTURED to stringResource(R.string.settings_cert_capture_ok),
        CaptureOutcome.ALREADY_PRESENT to stringResource(R.string.settings_cert_capture_dup),
        CaptureOutcome.NOT_HTTPS to stringResource(R.string.settings_cert_capture_not_https),
        CaptureOutcome.BAD_URL to stringResource(R.string.settings_cert_capture_bad_url),
        CaptureOutcome.FAILED to stringResource(R.string.settings_cert_capture_failed),
    )
    LaunchedEffect(state.captureOutcome) {
        state.captureOutcome?.let { outcome ->
            captureMessages[outcome]?.let { snackbarHostState.showSnackbar(it) }
            viewModel.consumeCaptureOutcome()
        }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = { TopAppBar(title = { Text(stringResource(R.string.settings_title)) }) },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { innerPadding ->
        SettingsContent(
            state = state,
            appearance = appearance,
            contentPadding = innerPadding,
            onHostChange = viewModel::onHostChange,
            onPortChange = viewModel::onPortChange,
            onTokenChange = viewModel::onTokenChange,
            onProfileChange = viewModel::onProfileChange,
            onCertPinsChange = viewModel::onCertPinsChange,
            onCaptureCertPin = viewModel::captureCertPin,
            onToggleToken = viewModel::toggleTokenVisibility,
            onSave = viewModel::save,
            onClear = viewModel::clear,
            onConfirmCleartextSave = viewModel::confirmSaveCleartext,
            onDismissCleartextWarning = viewModel::dismissCleartextWarning,
            onThemeMode = appearanceViewModel::setThemeMode,
            onDynamicColor = appearanceViewModel::setDynamicColor,
            onPureBlack = appearanceViewModel::setPureBlack,
            onAccent = appearanceViewModel::setAccent,
            onFont = appearanceViewModel::setFont,
            onUiScale = appearanceViewModel::setUiScale,
            onCornerStyle = appearanceViewModel::setCornerStyle,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SettingsContent(
    state: SettingsUiState,
    appearance: AppearancePrefs,
    contentPadding: PaddingValues,
    onHostChange: (String) -> Unit,
    onPortChange: (String) -> Unit,
    onTokenChange: (String) -> Unit,
    onProfileChange: (String) -> Unit,
    onCertPinsChange: (String) -> Unit,
    onCaptureCertPin: () -> Unit,
    onToggleToken: () -> Unit,
    onSave: () -> Unit,
    onClear: () -> Unit,
    onConfirmCleartextSave: () -> Unit,
    onDismissCleartextWarning: () -> Unit,
    onThemeMode: (ThemeMode) -> Unit,
    onDynamicColor: (Boolean) -> Unit,
    onPureBlack: (Boolean) -> Unit,
    onAccent: (AccentPreset) -> Unit,
    onFont: (FontChoice) -> Unit,
    onUiScale: (UiScale) -> Unit,
    onCornerStyle: (CornerStyle) -> Unit,
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

        val profileError = state.invalidField == SettingsField.PROFILE
        OutlinedTextField(
            value = state.profile,
            onValueChange = onProfileChange,
            modifier = Modifier.fillMaxWidth(),
            label = { Text(stringResource(R.string.settings_profile_label)) },
            placeholder = { Text(stringResource(R.string.settings_profile_placeholder)) },
            singleLine = true,
            isError = profileError,
            supportingText = {
                Text(
                    if (profileError) stringResource(R.string.settings_error_profile_invalid)
                    else stringResource(R.string.settings_profile_supporting),
                )
            },
        )

        val certPinsError = state.invalidField == SettingsField.CERT_PINS
        OutlinedTextField(
            value = state.certPins,
            onValueChange = onCertPinsChange,
            modifier = Modifier.fillMaxWidth(),
            label = { Text(stringResource(R.string.settings_cert_pins_label)) },
            placeholder = { Text(stringResource(R.string.settings_cert_pins_placeholder)) },
            singleLine = false,
            isError = certPinsError,
            supportingText = {
                Text(
                    if (certPinsError) stringResource(R.string.settings_error_cert_pins_invalid)
                    else stringResource(R.string.settings_cert_pins_supporting),
                )
            },
        )

        // Trust-on-first-use capture: shown only when the typed endpoint is https
        // (a pin can only come from a TLS handshake). It fetches the server's
        // current leaf-certificate pin and appends it to the field above; the user
        // still has to Save to persist, and nothing is pinned until then.
        if (state.canCapturePin) {
            OutlinedButton(
                onClick = onCaptureCertPin,
                enabled = !state.capturing,
                modifier = Modifier.fillMaxWidth(),
            ) {
                if (state.capturing) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                    )
                    Text(
                        text = stringResource(R.string.settings_cert_capturing),
                        modifier = Modifier.padding(start = 8.dp),
                    )
                } else {
                    Icon(Icons.Filled.Lock, contentDescription = null)
                    Text(
                        text = stringResource(R.string.settings_cert_capture),
                        modifier = Modifier.padding(start = 8.dp),
                    )
                }
            }
        }

        state.resolvedEndpoint?.let { endpoint ->
            Text(stringResource(R.string.settings_resolved_endpoint, endpoint))
        }

        // Hold the save when it targets plain http:// on a non-local host: the
        // bearer token guards an endpoint that can run terminal commands, so
        // sending it over an unencrypted hop to the open internet deserves an
        // explicit warning rather than a silent save.
        state.pendingCleartextUrl?.let { url ->
            AlertDialog(
                onDismissRequest = onDismissCleartextWarning,
                title = { Text(stringResource(R.string.settings_cleartext_title)) },
                text = {
                    Text(
                        stringResource(R.string.settings_cleartext_body) +
                            "\n\n" + url
                    )
                },
                dismissButton = {
                    TextButton(onClick = onDismissCleartextWarning) {
                        Text(stringResource(R.string.settings_cleartext_cancel))
                    }
                },
                confirmButton = {
                    TextButton(onClick = onConfirmCleartextSave) {
                        Text(stringResource(R.string.settings_cleartext_confirm))
                    }
                },
            )
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

        AppearanceSettings(
            prefs = appearance,
            onThemeMode = onThemeMode,
            onDynamicColor = onDynamicColor,
            onPureBlack = onPureBlack,
            onAccent = onAccent,
            onFont = onFont,
            onUiScale = onUiScale,
            onCornerStyle = onCornerStyle,
        )
    }
}
