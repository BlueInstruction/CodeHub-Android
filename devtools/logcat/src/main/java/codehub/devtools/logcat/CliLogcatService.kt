package codehub.devtools.logcat

import codehub.core.diagnostics.DiagnosticSink
import codehub.core.process.ProcessRunner
import codehub.core.process.ProcessSpec
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CliLogcatService @Inject constructor(
    private val processRunner: ProcessRunner,
    private val diagnostics: DiagnosticSink
) : LogcatService {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val stream = MutableSharedFlow<LogcatEntry>(extraBufferCapacity = 1024)
    @Volatile private var running: codehub.core.process.RunningProcess? = null

    init {
        startCollector()
    }

    private fun startCollector() {
        scope.launch {
            val spec = ProcessSpec(
                command = listOf("logcat", "-v", "threadtime"),
                workingDirectory = "/",
                environment = emptyMap(),
                timeoutMs = null
            )
            val proc = runCatching { processRunner.launch(spec) }.getOrNull() ?: return@launch
            running = proc
            proc.stdout.collect { line ->
                parse(line)?.let { stream.tryEmit(it) }
            }
        }
    }

    override fun stream(filter: String?): Flow<LogcatEntry> {
        if (filter.isNullOrBlank()) return stream.asSharedFlow()
        return kotlinx.coroutines.flow.flow {
            stream.asSharedFlow().collect { entry ->
                if (entry.tag.contains(filter, ignoreCase = true) || entry.message.contains(filter, ignoreCase = true)) {
                    emit(entry)
                }
            }
        }
    }

    override fun streamForPid(pid: Int, filter: String?): Flow<LogcatEntry> {
        return kotlinx.coroutines.flow.flow {
            val spec = ProcessSpec(
                command = buildList {
                    add("logcat")
                    add("--pid=$pid")
                    add("-v")
                    add("threadtime")
                    if (!filter.isNullOrBlank()) add(filter)
                },
                workingDirectory = "/",
                environment = emptyMap(),
                timeoutMs = null
            )
            val proc = runCatching { processRunner.launch(spec) }.getOrNull() ?: return@flow
            proc.stdout.collect { line ->
                val entry = parse(line)
                if (entry != null && matchesFilter(entry, filter)) {
                    emit(entry)
                }
            }
        }
    }

    override fun streamForPackage(packageName: String, filter: String?): Flow<LogcatEntry> {
        return kotlinx.coroutines.flow.flow {
            val pid = resolvePid(packageName)
            if (pid != null && pid > 0) {
                streamForPid(pid, filter).collect { emit(it) }
            }
        }
    }

    override suspend fun snapshot(filter: String?, limit: Int): List<LogcatEntry> {
        val spec = ProcessSpec(
            command = buildList {
                add("logcat")
                add("-d")
                add("-v")
                add("threadtime")
                if (!filter.isNullOrBlank()) add(filter)
            },
            workingDirectory = "/",
            environment = emptyMap(),
            timeoutMs = 5_000
        )
        val result = processRunner.run(spec)
        return result.stdout.lines()
            .filter { it.isNotBlank() }
            .mapNotNull { parse(it) }
            .filter { matchesFilter(it, filter) }
            .takeLast(limit)
    }

    override suspend fun snapshotForPid(pid: Int, filter: String?, limit: Int): List<LogcatEntry> {
        val spec = ProcessSpec(
            command = buildList {
                add("logcat")
                add("-d")
                add("--pid=$pid")
                add("-v")
                add("threadtime")
                if (!filter.isNullOrBlank()) add(filter)
            },
            workingDirectory = "/",
            environment = emptyMap(),
            timeoutMs = 5_000
        )
        val result = processRunner.run(spec)
        return result.stdout.lines()
            .filter { it.isNotBlank() }
            .mapNotNull { parse(it) }
            .filter { matchesFilter(it, filter) }
            .takeLast(limit)
    }

    override suspend fun snapshotForPackage(packageName: String, filter: String?, limit: Int): List<LogcatEntry> {
        val pid = resolvePid(packageName) ?: return emptyList()
        return snapshotForPid(pid, filter, limit)
    }

    override suspend fun resolvePid(packageName: String): Int? {
        val spec = ProcessSpec(
            command = listOf("pidof", packageName),
            workingDirectory = "/",
            environment = emptyMap(),
            timeoutMs = 2_000
        )
        val result = processRunner.run(spec)
        if (result.exitCode == 0) {
            val pid = result.stdout.trim().split(Regex("\\s+")).firstOrNull()?.toIntOrNull()
            if (pid != null && pid > 0) return pid
        }
        val pgrepSpec = ProcessSpec(
            command = listOf("pgrep", "-f", packageName),
            workingDirectory = "/",
            environment = emptyMap(),
            timeoutMs = 2_000
        )
        val pgrepResult = processRunner.run(pgrepSpec)
        if (pgrepResult.exitCode == 0) {
            val pid = pgrepResult.stdout.trim().split(Regex("\\s+")).firstOrNull()?.toIntOrNull()
            if (pid != null && pid > 0) return pid
        }
        val psSpec = ProcessSpec(
            command = listOf("ps", "-A", "-o", "PID,NAME"),
            workingDirectory = "/",
            environment = emptyMap(),
            timeoutMs = 2_000
        )
        val psResult = processRunner.run(psSpec)
        for (line in psResult.stdout.lines()) {
            val parts = line.trim().split(Regex("\\s+"))
            if (parts.size >= 2 && parts[1] == packageName) {
                return parts[0].toIntOrNull()
            }
        }
        return null
    }

    override suspend fun clear() {
        processRunner.run(
            ProcessSpec(
                command = listOf("logcat", "-c"),
                workingDirectory = "/",
                environment = emptyMap(),
                timeoutMs = 1_500
            )
        )
    }

    private fun matchesFilter(entry: LogcatEntry, filter: String?): Boolean {
        if (filter.isNullOrBlank()) return true
        return entry.tag.contains(filter, ignoreCase = true) ||
            entry.message.contains(filter, ignoreCase = true)
    }

    private fun parse(line: String): LogcatEntry? {
        val match = Regex("""^(\d+-\d+ \d+:\d+:\d+\.\d+)\s+(\d+)\s+(\d+)\s+([VDIWEF])\s+([^:]+):\s*(.*)$""").find(line)
            ?: return null
        return LogcatEntry(
            timestamp = match.groups[1]!!.value,
            pid = match.groups[2]?.value?.toIntOrNull(),
            tid = match.groups[3]?.value?.toIntOrNull(),
            level = match.groups[4]!!.value.first(),
            tag = match.groups[5]!!.value.trim(),
            message = match.groups[6]!!.value,
            raw = line
        )
    }
}
