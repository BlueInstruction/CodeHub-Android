package codehub.core.services.android

import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.Serializable

@Serializable
enum class AndroidPipelineState {
    Idle,
    Generating,
    Provisioning,
    Syncing,
    Building,
    Signing,
    Installing,
    Launching,
    Streaming,
    Failed,
    Succeeded
}

@Serializable
sealed interface AndroidPipelineEvent {
    @Serializable data class GeneratingProject(val projectPath: String, val template: String) : AndroidPipelineEvent
    @Serializable data class ProjectGenerated(val projectPath: String, val fileCount: Int) : AndroidPipelineEvent
    @Serializable data class Provisioning(val missing: List<String>) : AndroidPipelineEvent
    @Serializable data class Provisioned(val installed: List<String>) : AndroidPipelineEvent
    @Serializable data class Syncing(val workspacePath: String) : AndroidPipelineEvent
    @Serializable data class Synced(val workspacePath: String, val ok: Boolean) : AndroidPipelineEvent
    @Serializable data class BuildStarted(val targetId: String, val task: String) : AndroidPipelineEvent
    @Serializable data class BuildCompleted(val targetId: String, val status: String, val durationMs: Long) : AndroidPipelineEvent
    @Serializable data class ApkDiscovered(val apkPath: String, val sizeBytes: Long) : AndroidPipelineEvent
    @Serializable data class ApkInstalled(val packageName: String, val ok: Boolean) : AndroidPipelineEvent
    @Serializable data class AppLaunched(val packageName: String, val ok: Boolean) : AndroidPipelineEvent
    @Serializable data class LogcatStreaming(val packageName: String, val pid: Int?) : AndroidPipelineEvent
    @Serializable data class AiAnalysisTriggered(val reason: String) : AndroidPipelineEvent
    @Serializable data class PipelineSucceeded(val packageName: String) : AndroidPipelineEvent
    @Serializable data class PipelineFailed(val reason: String, val stage: String) : AndroidPipelineEvent
    @Serializable data class LogEntry(val timestamp: String, val level: Char, val tag: String, val message: String) : AndroidPipelineEvent
}

data class AndroidPipelineRequest(
    val projectPath: String,
    val templateName: String,
    val packageName: String,
    val displayName: String,
    val task: String = "assembleDebug",
    val skipProvisioning: Boolean = false,
    val skipInstall: Boolean = false
)
