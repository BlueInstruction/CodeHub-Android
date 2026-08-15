package io.github.blueinstruction.codehub.core.process

import io.github.blueinstruction.codehub.core.diagnostics.DiagnosticEvent
import io.github.blueinstruction.codehub.core.diagnostics.DiagnosticEventKind
import io.github.blueinstruction.codehub.core.diagnostics.DiagnosticSeverity
import io.github.blueinstruction.codehub.core.diagnostics.DiagnosticSink
import io.github.blueinstruction.codehub.core.diagnostics.DiagnosticStatus
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.io.File
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class JavaProcessRunner @Inject constructor(
    private val diagnostics: DiagnosticSink
) : ProcessRunner {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val processes = ConcurrentHashMap<Long, RunningProcessRecord>()
    private val pidCounter = AtomicLong(1000)
    private val snapshots = MutableStateFlow<List<ProcessSnapshot>>(emptyList())

    override suspend fun run(spec: ProcessSpec): ProcessResult = withContext(Dispatchers.IO) {
        val started = System.currentTimeMillis()
        val pb = ProcessBuilder(spec.command).apply {
            directory(File(spec.workingDirectory).takeIf { it.exists() } ?: File("."))
            environment().putAll(spec.environment)
            redirectErrorStream(spec.redirectStderrToStdout)
        }
        val process = try {
            pb.start()
        } catch (e: IOException) {
            diagnostics.emit(
                DiagnosticEvent.now(
                    kind = DiagnosticEventKind.ProcessCrash,
                    severity = DiagnosticSeverity.Error,
                    status = DiagnosticStatus.Failed,
                    source = "JavaProcessRunner",
                    message = "Failed to launch ${spec.command.firstOrNull()}: ${e.message}"
                )
            )
            return@withContext ProcessResult(
                exitCode = -1,
                stdout = "",
                stderr = e.message ?: "IOException",
                durationMs = System.currentTimeMillis() - started,
                wasKilled = false,
                timedOut = false
            )
        }
        val stdoutText = StringBuilder()
        val stderrText = StringBuilder()
        val stdoutJob = scope.launch {
            process.inputStream.bufferedReader().useLines { lines ->
                lines.forEach { stdoutText.append(it).append('\n') }
            }
        }
        val stderrJob = scope.launch {
            process.errorStream.bufferedReader().useLines { lines ->
                lines.forEach { stderrText.append(it).append('\n') }
            }
        }
        val timedOut = spec.timeoutMs != null
        val exited = if (timedOut) {
            withTimeoutOrNull(spec.timeoutMs!!) { process.waitFor() } ?: -2
        } else {
            process.waitFor()
        }
        stdoutJob.join()
        stderrJob.join()
        if (exited == -2) {
            process.destroyForcibly()
            diagnostics.emit(
                DiagnosticEvent.now(
                    kind = DiagnosticEventKind.ProcessCrash,
                    severity = DiagnosticSeverity.Warn,
                    status = DiagnosticStatus.Failed,
                    source = "JavaProcessRunner",
                    message = "Process ${spec.command.firstOrNull()} timed out after ${spec.timeoutMs}ms"
                )
            )
        }
        ProcessResult(
            exitCode = if (exited == -2) -1 else process.exitValue(),
            stdout = stdoutText.toString(),
            stderr = stderrText.toString(),
            durationMs = System.currentTimeMillis() - started,
            wasKilled = exited == -2,
            timedOut = exited == -2
        )
    }

    override suspend fun launch(spec: ProcessSpec): RunningProcess = withContext(Dispatchers.IO) {
        val pb = ProcessBuilder(spec.command).apply {
            directory(File(spec.workingDirectory).takeIf { it.exists() } ?: File("."))
            environment().putAll(spec.environment)
            redirectErrorStream(spec.redirectStderrToStdout)
        }
        val process = pb.start()
        val pid = pidCounter.incrementAndGet()
        val stdoutFlow = MutableSharedFlow<String>(extraBufferCapacity = 256)
        val stderrFlow = MutableSharedFlow<String>(extraBufferCapacity = 256)
        val result = CompletableDeferred<ProcessResult>()
        var state = ProcessState.Running
        val started = System.currentTimeMillis()
        val stdoutJob: Job = scope.launch {
            process.inputStream.bufferedReader().useLines { lines ->
                lines.forEach { stdoutFlow.tryEmit(it) }
            }
        }
        val stderrJob: Job = scope.launch {
            process.errorStream.bufferedReader().useLines { lines ->
                lines.forEach { stderrFlow.tryEmit(it) }
            }
        }
        val awaitJob = scope.launch {
            val code = process.waitFor()
            stdoutJob.join()
            stderrJob.join()
            state = ProcessState.Exited
            result.complete(
                ProcessResult(
                    exitCode = code,
                    stdout = "",
                    stderr = "",
                    durationMs = System.currentTimeMillis() - started
                )
            )
            updateSnapshots()
        }
        val record = RunningProcessRecord(
            pid = pid,
            process = process,
            state = { state },
            stdout = stdoutFlow.asSharedFlow(),
            stderr = stderrFlow.asSharedFlow(),
            result = result,
            awaitJob = awaitJob,
            command = spec.command,
            workingDirectory = spec.workingDirectory,
            startedAt = started
        )
        processes[pid] = record
        updateSnapshots()
        object : RunningProcess {
            override val pid: Long get() = pid
            override val state: ProcessState get() = state
            override val stdout: Flow<String> get() = stdoutFlow.asSharedFlow()
            override val stderr: Flow<String> get() = stderrFlow.asSharedFlow()

            override suspend fun await(): ProcessResult = result.await()

            override suspend fun kill() = withContext(Dispatchers.IO) {
                process.destroyForcibly()
                state = ProcessState.Killed
                updateSnapshots()
            }

            override suspend fun writeStdin(data: String) = withContext(Dispatchers.IO) {
                process.outputStream.write(data.toByteArray())
                process.outputStream.flush()
            }
        }
    }

    override fun running(): StateFlow<List<ProcessSnapshot>> = snapshots.asStateFlow()

    private fun updateSnapshots() {
        snapshots.value = processes.values.map { rec ->
            ProcessSnapshot(
                pid = rec.pid,
                command = rec.command,
                startedAt = rec.startedAt,
                state = rec.state(),
                workingDirectory = rec.workingDirectory
            )
        }
    }

    private data class RunningProcessRecord(
        val pid: Long,
        val process: Process,
        val state: () -> ProcessState,
        val stdout: Flow<String>,
        val stderr: Flow<String>,
        val result: CompletableDeferred<ProcessResult>,
        val awaitJob: Job,
        val command: List<String>,
        val workingDirectory: String,
        val startedAt: Long
    )
}
