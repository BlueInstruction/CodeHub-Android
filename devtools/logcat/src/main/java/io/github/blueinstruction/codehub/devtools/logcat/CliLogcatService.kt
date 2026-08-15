package io.github.blueinstruction.codehub.devtools.logcat

import io.github.blueinstruction.codehub.core.diagnostics.DiagnosticSink
import io.github.blueinstruction.codehub.core.process.ProcessRunner
import io.github.blueinstruction.codehub.core.process.ProcessSpec
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
    @Volatile private var running: io.github.blueinstruction.codehub.core.process.RunningProcess? = null

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
            .filter {
                filter.isNullOrBlank() ||
                    it.tag.contains(filter, ignoreCase = true) ||
                    it.message.contains(filter, ignoreCase = true)
            }
            .takeLast(limit)
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
