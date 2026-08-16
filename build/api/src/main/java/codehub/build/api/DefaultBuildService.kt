package codehub.build.api

import codehub.core.diagnostics.DiagnosticEvent
import codehub.core.diagnostics.DiagnosticEventKind
import codehub.core.diagnostics.DiagnosticSeverity
import codehub.core.diagnostics.DiagnosticSink
import codehub.core.diagnostics.DiagnosticStatus
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
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DefaultBuildService @Inject constructor(
    private val diagnostics: DiagnosticSink,
    private val providers: Map<BuildTool, @JvmSuppressWildcards BuildToolProvider>
) : BuildService {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val eventFlow = MutableSharedFlow<BuildEvent>(extraBufferCapacity = 256)
    private val historyFlow = MutableStateFlow<List<BuildResult>>(emptyList())
    private val results = ConcurrentHashMap<String, BuildResult>()
    private val jobs = ConcurrentHashMap<String, Job>()

    override val events: Flow<BuildEvent> get() = eventFlow.asSharedFlow()

    override fun history(): StateFlow<List<BuildResult>> = historyFlow.asStateFlow()

    override suspend fun enqueue(target: BuildTarget): String {
        val targetId = target.id.ifBlank { UUID.randomUUID().toString() }
        eventFlow.tryEmit(BuildEvent.Queued(targetId))
        val provider = providers[target.tool]
        if (provider == null || !provider.isAvailable()) {
            val result = BuildResult(
                target = target,
                status = BuildStatus.Failed,
                exitCode = -1,
                stdout = "",
                stderr = "Build tool ${target.tool} not available",
                durationMs = 0,
                artifacts = emptyList(),
                diagnostics = emptyList(),
                startedAt = System.currentTimeMillis(),
                finishedAt = System.currentTimeMillis()
            )
            results[targetId] = result
            historyFlow.value = historyFlow.value + result
            eventFlow.tryEmit(BuildEvent.Failed(targetId, result.stderr))
            return targetId
        }
        val job = scope.launch {
            eventFlow.tryEmit(BuildEvent.Started(target))
            diagnostics.emit(
                DiagnosticEvent.now(
                    kind = DiagnosticEventKind.BuildStarted,
                    severity = DiagnosticSeverity.Info,
                    status = DiagnosticStatus.Ok,
                    source = "BuildService",
                    message = "Build ${target.displayName} started",
                    attributes = mapOf("tool" to target.tool.name, "target" to targetId)
                )
            )
            val started = System.currentTimeMillis()
            val result = try {
                val r = provider.execute(target)
                r.copy(startedAt = started, finishedAt = System.currentTimeMillis())
            } catch (t: Throwable) {
                BuildResult(
                    target = target,
                    status = BuildStatus.Failed,
                    exitCode = -1,
                    stdout = "",
                    stderr = t.stackTraceToString(),
                    durationMs = System.currentTimeMillis() - started,
                    artifacts = emptyList(),
                    diagnostics = emptyList(),
                    startedAt = started,
                    finishedAt = System.currentTimeMillis()
                )
            }
            results[targetId] = result
            historyFlow.value = historyFlow.value + result
            if (result.status == BuildStatus.Succeeded) {
                eventFlow.tryEmit(BuildEvent.Completed(result))
                diagnostics.emit(
                    DiagnosticEvent.now(
                        kind = DiagnosticEventKind.BuildCompleted,
                        severity = DiagnosticSeverity.Info,
                        status = DiagnosticStatus.Ok,
                        source = "BuildService",
                        message = "Build ${target.displayName} succeeded",
                        attributes = mapOf("target" to targetId, "duration" to result.durationMs.toString())
                    )
                )
            } else {
                eventFlow.tryEmit(BuildEvent.Failed(targetId, result.stderr))
                diagnostics.emit(
                    DiagnosticEvent.now(
                        kind = DiagnosticEventKind.BuildFailed,
                        severity = DiagnosticSeverity.Error,
                        status = DiagnosticStatus.Failed,
                        source = "BuildService",
                        message = "Build ${target.displayName} failed",
                        reason = result.stderr.take(500),
                        attributes = mapOf("target" to targetId)
                    )
                )
            }
        }
        jobs[targetId] = job
        return targetId
    }

    override suspend fun cancel(targetId: String) {
        jobs.remove(targetId)?.cancel()
        eventFlow.tryEmit(BuildEvent.Cancelled(targetId))
    }

    override suspend fun getResult(targetId: String): BuildResult? = results[targetId]
}
