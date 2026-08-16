package codehub.core.process

import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.Serializable

@Serializable
data class ProcessSpec(
    val command: List<String>,
    val workingDirectory: String,
    val environment: Map<String, String> = emptyMap(),
    val timeoutMs: Long? = null,
    val stdin: String? = null,
    val redirectStderrToStdout: Boolean = false,
    val requireForeground: Boolean = false
)

@Serializable
data class ProcessResult(
    val exitCode: Int,
    val stdout: String,
    val stderr: String,
    val durationMs: Long,
    val wasKilled: Boolean = false,
    val timedOut: Boolean = false
)

@Serializable
data class ProcessSnapshot(
    val pid: Long,
    val command: List<String>,
    val startedAt: Long,
    val state: ProcessState,
    val workingDirectory: String
)

@Serializable
enum class ProcessState { Starting, Running, Exited, Killed, TimedOut, Failed }

interface ProcessRunner {
    suspend fun run(spec: ProcessSpec): ProcessResult
    suspend fun launch(spec: ProcessSpec): RunningProcess
    fun running(): Flow<List<ProcessSnapshot>>
}

interface RunningProcess {
    val pid: Long
    val state: ProcessState
    val stdout: Flow<String>
    val stderr: Flow<String>
    suspend fun await(): ProcessResult
    suspend fun kill()
    suspend fun writeStdin(data: String)
}
