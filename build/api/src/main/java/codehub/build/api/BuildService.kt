package codehub.build.api

import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.Serializable

@Serializable
enum class BuildStatus { Queued, Running, Succeeded, Failed, Cancelled, Skipped }

@Serializable
enum class BuildTool { Gradle, CMake, Ninja, Make, Meson, Clang, Cargo, GoBuild, Custom }

@Serializable
data class BuildTarget(
    val id: String,
    val displayName: String,
    val workspacePath: String,
    val tool: BuildTool,
    val tasks: List<String>,
    val environmentOverrides: Map<String, String> = emptyMap(),
    val parallelJobs: Int = Runtime.getRuntime().availableProcessors()
)

@Serializable
data class BuildArtifact(
    val path: String,
    val sizeBytes: Long,
    val checksum: String? = null
)

@Serializable
data class BuildDiagnostic(
    val severity: String,
    val file: String?,
    val line: Int?,
    val column: Int?,
    val code: String?,
    val message: String,
    val tool: String
)

@Serializable
data class BuildResult(
    val target: BuildTarget,
    val status: BuildStatus,
    val exitCode: Int,
    val stdout: String,
    val stderr: String,
    val durationMs: Long,
    val artifacts: List<BuildArtifact>,
    val diagnostics: List<BuildDiagnostic>,
    val startedAt: Long,
    val finishedAt: Long
)

@Serializable
sealed interface BuildEvent {
    @Serializable data class Queued(val targetId: String) : BuildEvent
    @Serializable data class Started(val target: BuildTarget) : BuildEvent
    @Serializable data class Stdout(val targetId: String, val line: String) : BuildEvent
    @Serializable data class Stderr(val targetId: String, val line: String) : BuildEvent
    @Serializable data class Completed(val result: BuildResult) : BuildEvent
    @Serializable data class Failed(val targetId: String, val reason: String) : BuildEvent
    @Serializable data class Cancelled(val targetId: String) : BuildEvent
}

interface BuildService {
    val events: Flow<BuildEvent>
    fun history(): Flow<List<BuildResult>>
    suspend fun enqueue(target: BuildTarget): String
    suspend fun cancel(targetId: String)
    suspend fun getResult(targetId: String): BuildResult?
}

interface BuildToolProvider {
    val tool: BuildTool
    suspend fun isAvailable(): Boolean
    suspend fun execute(target: BuildTarget): BuildResult
    fun parseDiagnostics(stdout: String, stderr: String): List<BuildDiagnostic>
}
