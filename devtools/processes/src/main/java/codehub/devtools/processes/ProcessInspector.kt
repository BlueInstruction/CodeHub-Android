package codehub.devtools.processes

import codehub.core.process.ProcessRunner
import codehub.core.process.ProcessSpec
import kotlinx.serialization.Serializable
import javax.inject.Inject
import javax.inject.Singleton

@Serializable
data class ProcessRow(
    val pid: Int,
    val ppid: Int?,
    val user: String,
    val name: String,
    val state: String,
    val rssKb: Long?,
    val cpuPercent: Float?
)

@Singleton
class ProcessInspector @Inject constructor(
    private val processRunner: ProcessRunner
) {
    suspend fun list(): List<ProcessRow> {
        val result = processRunner.run(
            ProcessSpec(
                command = listOf("ps", "-A", "-o", "PID,PPID,USER,NAME,STATE,RSS,PCPU"),
                workingDirectory = "/",
                environment = emptyMap(),
                timeoutMs = 2_000
            )
        )
        return result.stdout.lines()
            .drop(1)
            .filter { it.isNotBlank() }
            .mapNotNull { line ->
                val parts = line.trim().split(Regex("\\s+"))
                if (parts.size < 5) return@mapNotNull null
                ProcessRow(
                    pid = parts[0].toIntOrNull() ?: 0,
                    ppid = parts.getOrNull(1)?.toIntOrNull(),
                    user = parts.getOrNull(2) ?: "",
                    name = parts.getOrNull(3) ?: "",
                    state = parts.getOrNull(4) ?: "",
                    rssKb = parts.getOrNull(5)?.toLongOrNull(),
                    cpuPercent = parts.getOrNull(6)?.toFloatOrNull()
                )
            }
    }

    suspend fun kill(pid: Int, signal: String = "TERM"): Boolean {
        val result = processRunner.run(
            ProcessSpec(
                command = listOf("kill", "-$signal", pid.toString()),
                workingDirectory = "/",
                environment = emptyMap(),
                timeoutMs = 1_500
            )
        )
        return result.exitCode == 0
    }
}
