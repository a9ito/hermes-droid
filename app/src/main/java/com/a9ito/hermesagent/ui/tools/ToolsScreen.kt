package com.a9ito.hermesagent.ui.tools

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SecondaryTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.a9ito.hermesagent.R
import com.a9ito.hermesagent.ServiceLocator
import com.a9ito.hermesagent.data.remote.dto.SkillDto
import com.a9ito.hermesagent.data.remote.dto.ToolsetDto
import com.a9ito.hermesagent.ui.common.ConnectionGate
import com.a9ito.hermesagent.ui.messageRes

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ToolsScreen(
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ToolsViewModel = viewModel(
        factory = ToolsViewModel.Factory(ServiceLocator.hermes()),
    ),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.nav_tools)) },
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
                title = stringResource(R.string.tools_locked_title),
                subtitle = stringResource(R.string.tools_locked_subtitle),
                actionLabel = stringResource(R.string.chat_locked_action),
                onAction = onOpenSettings,
                modifier = Modifier.padding(innerPadding),
            )
            return@Scaffold
        }

        Column(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
            SecondaryTabRow(selectedTabIndex = state.tab.ordinal) {
                Tab(
                    selected = state.tab == ToolsTab.SKILLS,
                    onClick = { viewModel.selectTab(ToolsTab.SKILLS) },
                    text = { Text(stringResource(R.string.tools_tab_skills)) },
                )
                Tab(
                    selected = state.tab == ToolsTab.TOOLSETS,
                    onClick = { viewModel.selectTab(ToolsTab.TOOLSETS) },
                    text = { Text(stringResource(R.string.tools_tab_toolsets)) },
                )
            }

            when {
                state.loading ->
                    Centered { CircularProgressIndicator() }
                state.errorKind != null ->
                    Centered {
                        Text(
                            stringResource(R.string.status_error_prefix, stringResource(state.errorKind.messageRes())),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                state.tab == ToolsTab.SKILLS -> SkillList(state.skills)
                else -> ToolsetList(state.toolsets)
            }
        }
    }
}

@Composable
private fun SkillList(skills: List<SkillDto>) {
    if (skills.isEmpty()) {
        Centered { Text(stringResource(R.string.tools_empty), style = MaterialTheme.typography.bodyMedium) }
        return
    }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(skills, key = { it.name }) { skill ->
            Surface(color = MaterialTheme.colorScheme.surfaceVariant, shape = MaterialTheme.shapes.large, modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(skill.name, style = MaterialTheme.typography.titleSmall)
                    skill.category?.let { Text(it, style = MaterialTheme.typography.labelSmall) }
                    skill.description?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun ToolsetList(toolsets: List<ToolsetDto>) {
    if (toolsets.isEmpty()) {
        Centered { Text(stringResource(R.string.tools_empty), style = MaterialTheme.typography.bodyMedium) }
        return
    }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(toolsets, key = { it.name }) { toolset ->
            Surface(color = MaterialTheme.colorScheme.surfaceVariant, shape = MaterialTheme.shapes.large, modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(toolset.label ?: toolset.name, style = MaterialTheme.typography.titleSmall)
                    val stateLabel = if (toolset.enabled) stringResource(R.string.tools_enabled) else stringResource(R.string.tools_disabled)
                    Text(stateLabel, style = MaterialTheme.typography.labelSmall)
                    toolset.description?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
                    if (toolset.tools.isNotEmpty()) {
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            toolset.tools.forEach { tool ->
                                Surface(color = MaterialTheme.colorScheme.secondaryContainer, shape = MaterialTheme.shapes.small) {
                                    Text(tool, style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun Centered(content: @Composable () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) { content() }
}
