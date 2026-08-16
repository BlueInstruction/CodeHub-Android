package codehub.ui.screens.newproject

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import codehub.core.services.android.AndroidPipelineEvent
import codehub.core.services.android.AndroidPipelineState
import codehub.workspace.template.ProjectTemplateKind

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NewProjectScreen(
    viewModel: NewProjectViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val pipelineState by viewModel.pipelineState.collectAsState()
    val isRunning by viewModel.isRunning.collectAsState()
    val events by viewModel.events.collectAsState()
    val logs by viewModel.logEntries.collectAsState()
    val analysis by viewModel.analysis.collectAsState()
    val analyzing by viewModel.analyzing.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(title = { Text("New Android Project", style = MaterialTheme.typography.titleLarge) })
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item { TemplatePicker(uiState, viewModel::updateTemplate) }
            item { ProjectDetailsForm(uiState, viewModel, isRunning) }
            item { TaskOptions(uiState, viewModel, isRunning) }
            item { RunButton(uiState, pipelineState, isRunning, viewModel::runPipeline) }
            if (pipelineState != AndroidPipelineState.Idle) {
                item { PipelineStateCard(pipelineState) }
            }
            if (analyzing) {
                item { AnalyzingCard() }
            }
            analysis?.let { item { AnalysisCard(it) } }
            if (events.isNotEmpty()) {
                item {
                    Text("Pipeline events", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(top = 8.dp))
                }
                items(events.reversed()) { event -> EventRow(event) }
            }
            if (logs.isNotEmpty()) {
                item {
                    Text("Logcat", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(top = 16.dp))
                }
                items(logs.reversed().take(50)) { entry -> LogRow(entry) }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TemplatePicker(state: NewProjectUiState, onSelect: (ProjectTemplateKind) -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Template", style = MaterialTheme.typography.titleLarge)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ProjectTemplateKind.values().forEach { kind ->
                    FilterChip(
                        selected = state.templateKind == kind,
                        onClick = { onSelect(kind) },
                        label = { Text(kind.name) }
                    )
                }
            }
            Text(
                text = describeTemplate(state.templateKind),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun ProjectDetailsForm(
    state: NewProjectUiState,
    viewModel: NewProjectViewModel,
    isRunning: Boolean
) {
    val folderPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        if (uri != null) {
            viewModel.resolveFolderUri(uri)
        }
    }

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Project details", style = MaterialTheme.typography.titleLarge)
            OutlinedTextField(
                value = state.displayName,
                onValueChange = viewModel::updateDisplayName,
                label = { Text("Display name") },
                isError = state.displayNameError != null,
                supportingText = state.displayNameError?.let { { Text(it) } },
                enabled = !isRunning,
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = state.packageName,
                onValueChange = viewModel::updatePackageName,
                label = { Text("Package name") },
                isError = state.packageNameError != null,
                supportingText = state.packageNameError?.let { { Text(it) } },
                enabled = !isRunning,
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Ascii),
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = state.projectPath,
                onValueChange = viewModel::updateProjectPath,
                label = { Text("Project path") },
                enabled = !isRunning,
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = { if (!isRunning) folderPicker.launch(null) },
                    enabled = !isRunning
                ) {
                    Text("Pick folder")
                }
                OutlinedButton(
                    onClick = { if (!isRunning) viewModel.appendSubdir(state.displayName) },
                    enabled = !isRunning && state.projectPath.isNotBlank() && state.displayName.isNotBlank()
                ) {
                    Text("Append display name")
                }
            }
            Text(
                text = "Pick an existing folder (will be used as parent) or type a new path. " +
                    "Use 'Append display name' to create a subdirectory named after the app.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            state.pathWarning?.let { warning ->
                Box(
                    modifier = Modifier.fillMaxWidth()
                        .background(MaterialTheme.colorScheme.errorContainer, RoundedCornerShape(6.dp))
                        .padding(8.dp)
                ) {
                    Text(
                        text = warning,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onErrorContainer
                    )
                }
            }
        }
    }
}

@Composable
private fun TaskOptions(
    state: NewProjectUiState,
    viewModel: NewProjectViewModel,
    isRunning: Boolean
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Build options", style = MaterialTheme.typography.titleLarge)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                AssistChip(
                    onClick = { if (!isRunning) viewModel.updateTask("assembleDebug") },
                    label = { Text("assembleDebug") },
                    leadingIcon = if (state.task == "assembleDebug") {
                        { Icon(Icons.Filled.CheckCircle, contentDescription = null, modifier = Modifier.size(16.dp)) }
                    } else null
                )
                AssistChip(
                    onClick = { if (!isRunning) viewModel.updateTask("installDebug") },
                    label = { Text("installDebug") },
                    leadingIcon = if (state.task == "installDebug") {
                        { Icon(Icons.Filled.CheckCircle, contentDescription = null, modifier = Modifier.size(16.dp)) }
                    } else null
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(
                    checked = state.skipProvisioning,
                    onCheckedChange = { if (!isRunning) viewModel.toggleSkipProvisioning() },
                    enabled = !isRunning
                )
                Text("Skip toolchain provisioning (assume installed)")
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(
                    checked = state.skipInstall,
                    onCheckedChange = { if (!isRunning) viewModel.toggleSkipInstall() },
                    enabled = !isRunning
                )
                Text("Skip APK install (build only)")
            }
        }
    }
}

@Composable
private fun RunButton(
    state: NewProjectUiState,
    pipelineState: AndroidPipelineState,
    isRunning: Boolean,
    onClick: () -> Unit
) {
    Button(
        onClick = onClick,
        enabled = state.canSubmit && !isRunning,
        modifier = Modifier.fillMaxWidth()
    ) {
        Icon(Icons.Filled.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
        Text("  Run pipeline")
    }
    if (isRunning) {
        LinearProgressIndicator(modifier = Modifier.fillMaxWidth().padding(top = 4.dp).height(4.dp))
    }
}

@Composable
private fun PipelineStateCard(state: AndroidPipelineState) {
    val (icon, color) = when (state) {
        AndroidPipelineState.Idle -> Icons.Filled.PlayArrow to MaterialTheme.colorScheme.outline
        AndroidPipelineState.Generating,
        AndroidPipelineState.Provisioning,
        AndroidPipelineState.Syncing,
        AndroidPipelineState.Building,
        AndroidPipelineState.Signing,
        AndroidPipelineState.Installing,
        AndroidPipelineState.Launching,
        AndroidPipelineState.Streaming -> Icons.Filled.Bolt to MaterialTheme.colorScheme.primary
        AndroidPipelineState.Succeeded -> Icons.Filled.CheckCircle to MaterialTheme.colorScheme.secondary
        AndroidPipelineState.Failed -> Icons.Filled.Error to MaterialTheme.colorScheme.error
    }
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(20.dp))
            Text(
                text = "  ${describeState(state)}",
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.padding(start = 8.dp)
            )
        }
    }
}

@Composable
private fun EventRow(event: AndroidPipelineEvent) {
    val text = describeEvent(event)
    val color = when (event) {
        is AndroidPipelineEvent.PipelineFailed -> MaterialTheme.colorScheme.error
        is AndroidPipelineEvent.PipelineSucceeded -> MaterialTheme.colorScheme.secondary
        is AndroidPipelineEvent.AiAnalysisTriggered -> MaterialTheme.colorScheme.tertiary
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
        Box(modifier = Modifier.size(width = 4.dp, height = 14.dp).background(color, RoundedCornerShape(2.dp)))
        Text(
            text = "  $text",
            style = MaterialTheme.typography.bodySmall,
            fontFamily = FontFamily.Monospace
        )
    }
}

@Composable
private fun LogRow(entry: AndroidPipelineEvent.LogEntry) {
    val levelColor = when (entry.level) {
        'E', 'F' -> MaterialTheme.colorScheme.error
        'W' -> MaterialTheme.colorScheme.tertiary
        'I' -> MaterialTheme.colorScheme.primary
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    Row(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = entry.timestamp.substringAfter(' '),
            style = MaterialTheme.typography.bodySmall,
            fontFamily = FontFamily.Monospace,
            color = MaterialTheme.colorScheme.outline,
            modifier = Modifier.padding(end = 8.dp)
        )
        Text(
            text = entry.level.toString(),
            style = MaterialTheme.typography.bodySmall,
            fontFamily = FontFamily.Monospace,
            color = levelColor,
            modifier = Modifier.padding(end = 8.dp)
        )
        Text(
            text = "${entry.tag}: ${entry.message}",
            style = MaterialTheme.typography.bodySmall,
            fontFamily = FontFamily.Monospace,
            modifier = Modifier.weight(1f)
        )
    }
}

private fun describeState(state: AndroidPipelineState): String = when (state) {
    AndroidPipelineState.Idle -> "Idle"
    AndroidPipelineState.Generating -> "Generating project..."
    AndroidPipelineState.Provisioning -> "Provisioning toolchain..."
    AndroidPipelineState.Syncing -> "Gradle sync..."
    AndroidPipelineState.Building -> "Building APK..."
    AndroidPipelineState.Signing -> "Preparing keystore..."
    AndroidPipelineState.Installing -> "Installing APK..."
    AndroidPipelineState.Launching -> "Launching app..."
    AndroidPipelineState.Streaming -> "Streaming logcat..."
    AndroidPipelineState.Succeeded -> "Succeeded"
    AndroidPipelineState.Failed -> "Failed"
}

private fun describeEvent(event: AndroidPipelineEvent): String = when (event) {
    is AndroidPipelineEvent.GeneratingProject -> "Generating ${event.template} at ${event.projectPath}"
    is AndroidPipelineEvent.ProjectGenerated -> "Generated ${event.fileCount} files"
    is AndroidPipelineEvent.Provisioning -> "Provisioning: ${event.missing.joinToString(", ")}"
    is AndroidPipelineEvent.Provisioned -> "Provisioned: ${event.installed.joinToString(", ")}"
    is AndroidPipelineEvent.Syncing -> "Gradle sync started"
    is AndroidPipelineEvent.Synced -> "Gradle sync ${if (event.ok) "ok" else "failed"}"
    is AndroidPipelineEvent.BuildStarted -> "Build started: ${event.task}"
    is AndroidPipelineEvent.BuildCompleted -> "Build ${event.status} (${event.durationMs}ms)"
    is AndroidPipelineEvent.ApkDiscovered -> "APK found: ${event.apkPath.substringAfterLast('/')} (${event.sizeBytes / 1024}KB)"
    is AndroidPipelineEvent.ApkInstalled -> "Install ${if (event.ok) "ok" else "failed"}: ${event.packageName}"
    is AndroidPipelineEvent.AppLaunched -> "Launch ${if (event.ok) "ok" else "failed"}: ${event.packageName}"
    is AndroidPipelineEvent.LogcatStreaming -> "Logcat streaming for ${event.packageName} (pid=${event.pid ?: "?"})"
    is AndroidPipelineEvent.AiAnalysisTriggered -> "AI analysis: ${event.reason.take(80)}"
    is AndroidPipelineEvent.PipelineSucceeded -> "Pipeline succeeded: ${event.packageName}"
    is AndroidPipelineEvent.PipelineFailed -> "Pipeline failed (${event.stage}): ${event.reason.take(100)}"
    is AndroidPipelineEvent.LogEntry -> "[${event.level}/${event.tag}] ${event.message}"
}

private fun describeTemplate(kind: ProjectTemplateKind): String = when (kind) {
    ProjectTemplateKind.EmptyCompose -> "Single Activity with Jetpack Compose, Material 3, simple greeting. The simplest starting point."
    ProjectTemplateKind.BasicViews -> "Single Activity with XML layout, TextView, Button. For classic Android View system."
    ProjectTemplateKind.NativeActivity -> "NativeActivity with C++ via JNI, CMake build. For NDK development."
    ProjectTemplateKind.AndroidLibrary -> "Android library module skeleton. Produces an AAR."
}

@Composable
private fun AnalyzingCard() {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                text = "AI Failure Analysis",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.tertiary
            )
            Text(
                text = "Gathering project context and requesting diagnosis...",
                style = MaterialTheme.typography.bodyMedium
            )
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth().height(4.dp))
        }
    }
}

@Composable
private fun AnalysisCard(result: codehub.ai.agents.AnalysisResult) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                text = "AI Failure Analysis",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.tertiary
            )
            Text(
                text = "Type: ${result.failureType.name}  ·  Session: ${result.sessionId}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            result.rootCauseHypothesis?.let {
                Text(
                    text = "Root-cause hypothesis",
                    style = MaterialTheme.typography.labelLarge
                )
                Text(
                    text = it.take(1000),
                    style = MaterialTheme.typography.bodyMedium,
                    fontFamily = FontFamily.Monospace
                )
            }
            result.evidence?.let {
                Text(
                    text = "Evidence",
                    style = MaterialTheme.typography.labelLarge
                )
                Text(
                    text = it.take(1500),
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = FontFamily.Monospace
                )
            }
            result.suggestedPatch?.let { patch ->
                Text(
                    text = "Suggested patch",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary
                )
                Box(
                    modifier = Modifier.fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(6.dp))
                        .padding(8.dp)
                ) {
                    Text(
                        text = patch,
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = FontFamily.Monospace,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            if (result.suggestedPatch == null) {
                Text(
                    text = "No automated patch suggested — configuration change likely needed.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.outline
                )
            }
            if (result.errors != null) {
                Text(
                    text = "Provider errors: ${result.errors}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            }
            Text(
                text = "Context gathered: ${result.buildDiagnostics.size} diagnostics, ${result.logcatEntries.size} logcat entries, ${result.projectContext.referencedSourceFiles.size} source files",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
