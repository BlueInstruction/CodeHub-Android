package codehub.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import codehub.ui.components.CodeHubScaffold
import codehub.core.services.PipelineEvent
import codehub.core.services.PipelineState

@Composable
fun VerticalSliceScreen(
    viewModel: VerticalSliceViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsState()
    val events by viewModel.events.collectAsState()
    val workspacePath by viewModel.workspacePath.collectAsState()

    CodeHubScaffold(title = "Vertical Slice") {
        Column(
            modifier = Modifier.fillMaxSize().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            WorkspaceRow(
                workspacePath = workspacePath,
                onPickWorkspace = viewModel::pickWorkspace,
                onRun = { viewModel.runPipeline() },
                isRunning = state is PipelineState.Starting ||
                    state is PipelineState.GitChecked ||
                    state is PipelineState.BuildConfiguring ||
                    state is PipelineState.BuildExecuting ||
                    state is PipelineState.NativeCompiling ||
                    state is PipelineState.ApkInstalling ||
                    state is PipelineState.AppLaunching ||
                    state is PipelineState.LogcatStreaming
            )
            StatusCard(state)
            EventsList(events)
        }
    }
}

@Composable
private fun WorkspaceRow(
    workspacePath: String,
    onPickWorkspace: () -> Unit,
    onRun: () -> Unit,
    isRunning: Boolean
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(modifier = Modifier.weight(1f)) {
            Text("Workspace", style = MaterialTheme.typography.labelLarge)
            Text(
                text = workspacePath.ifBlank { "(not selected)" },
                style = MaterialTheme.typography.bodyMedium,
                fontFamily = FontFamily.Monospace
            )
        }
        OutlinedButton(onClick = onPickWorkspace, modifier = Modifier.padding(end = 8.dp)) {
            Text("Pick")
        }
        Button(
            onClick = onRun,
            enabled = workspacePath.isNotBlank() && !isRunning
        ) {
            Icon(Icons.Filled.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
            Text("  Run pipeline")
        }
    }
}

@Composable
private fun StatusCard(state: PipelineState) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                val (icon, color) = when (state) {
                    is PipelineState.Idle -> Icons.Filled.Stop to MaterialTheme.colorScheme.outline
                    is PipelineState.Starting -> Icons.Filled.Bolt to MaterialTheme.colorScheme.primary
                    is PipelineState.GitChecked -> Icons.Filled.CheckCircle to MaterialTheme.colorScheme.primary
                    is PipelineState.BuildConfiguring -> Icons.Filled.Bolt to MaterialTheme.colorScheme.primary
                    is PipelineState.BuildExecuting -> Icons.Filled.Bolt to MaterialTheme.colorScheme.primary
                    is PipelineState.NativeCompiling -> Icons.Filled.Bolt to MaterialTheme.colorScheme.primary
                    is PipelineState.ApkInstalling -> Icons.Filled.Bolt to MaterialTheme.colorScheme.primary
                    is PipelineState.AppLaunching -> Icons.Filled.Bolt to MaterialTheme.colorScheme.primary
                    is PipelineState.LogcatStreaming -> Icons.Filled.CheckCircle to MaterialTheme.colorScheme.secondary
                    is PipelineState.Succeeded -> Icons.Filled.CheckCircle to MaterialTheme.colorScheme.secondary
                    is PipelineState.Failed -> Icons.Filled.Error to MaterialTheme.colorScheme.error
                }
                Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(20.dp))
                Text(
                    text = "  ${describeState(state)}",
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.padding(start = 8.dp)
                )
            }
            if (state is PipelineState.Failed) {
                Box(
                    modifier = Modifier.padding(top = 8.dp).fillMaxWidth()
                        .background(MaterialTheme.colorScheme.errorContainer, RoundedCornerShape(6.dp))
                        .padding(8.dp)
                ) {
                    Text(state.reason, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onErrorContainer)
                }
            }
            if (state is PipelineState.BuildConfiguring || state is PipelineState.BuildExecuting || state is PipelineState.NativeCompiling) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth().padding(top = 8.dp).height(4.dp))
            }
        }
    }
}

@Composable
private fun EventsList(events: List<PipelineEvent>) {
    LazyColumn(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        items(events.reversed()) { event ->
            EventRow(event)
        }
    }
}

@Composable
private fun EventRow(event: PipelineEvent) {
    val text = describeEvent(event)
    val color = when (event) {
        is PipelineEvent.PipelineFailed -> MaterialTheme.colorScheme.error
        is PipelineEvent.PipelineSucceeded -> MaterialTheme.colorScheme.secondary
        is PipelineEvent.AiAnalysisTriggered -> MaterialTheme.colorScheme.tertiary
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
        Box(
            modifier = Modifier.size(width = 4.dp, height = 14.dp)
                .background(color, RoundedCornerShape(2.dp))
        )
        Text(
            text = "  $text",
            style = MaterialTheme.typography.bodySmall,
            fontFamily = FontFamily.Monospace
        )
    }
}

private fun describeState(state: PipelineState): String = when (state) {
    is PipelineState.Idle -> "Idle"
    is PipelineState.Starting -> "Starting"
    is PipelineState.GitChecked -> "Git on branch ${state.branch}"
    is PipelineState.BuildConfiguring -> "Configuring build ${state.targetId}"
    is PipelineState.BuildExecuting -> "Building ${state.targetId}"
    is PipelineState.NativeCompiling -> "Compiling native sources (${state.targetId})"
    is PipelineState.ApkInstalling -> "Installing APK ${state.apkPath}"
    is PipelineState.AppLaunching -> "Launching ${state.packageName}"
    is PipelineState.LogcatStreaming -> "Streaming Logcat for ${state.packageName}"
    is PipelineState.Succeeded -> "Succeeded"
    is PipelineState.Failed -> "Failed"
}

private fun describeEvent(event: PipelineEvent): String = when (event) {
    is PipelineEvent.WorkspaceOpened -> "Workspace opened: ${event.path}"
    is PipelineEvent.TermuxVerified -> {
        val r = event.readiness
        "Termux ready=${r.ready}, missing=${r.missingTools.joinToString(",") { it.name }}"
    }
    is PipelineEvent.GitStatusChecked -> "Git: ${event.branch} +${event.ahead} -${event.behind} clean=${event.clean}"
    is PipelineEvent.BuildStarted -> "Build started: ${event.target.displayName} (${event.target.tool})"
    is PipelineEvent.BuildCompleted -> "Build finished: ${event.result.status} (${event.result.durationMs}ms, exit ${event.result.exitCode})"
    is PipelineEvent.ApkDiscovered -> "APKs found: ${event.apks.size}"
    is PipelineEvent.ApkInstalling -> "Installing APK: ${event.apkPath}"
    is PipelineEvent.ApkInstalled -> {
        if (event.result.success) "APK installed: ${event.result.packageName}"
        else "APK install failed: ${event.result.failureReason ?: event.result.output.take(80)}"
    }
    is PipelineEvent.AppLaunching -> "Launching app: ${event.packageName}"
    is PipelineEvent.AppLaunched -> "App launched: ${event.packageName} (ok=${event.success})"
    is PipelineEvent.LogcatStreaming -> "Logcat streaming for ${event.packageName}"
    is PipelineEvent.AiAnalysisTriggered -> "AI analysis triggered (${event.trigger})"
    is PipelineEvent.PipelineSucceeded -> "Pipeline succeeded: ${event.workspacePath}"
    is PipelineEvent.PipelineFailed -> "Pipeline FAILED: ${event.reason}"
}
