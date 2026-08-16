package codehub.core.services

import codehub.build.api.BuildResult
import codehub.build.api.BuildService
import codehub.build.api.BuildStatus
import codehub.build.api.BuildTarget
import codehub.build.api.BuildTool
import codehub.core.diagnostics.DiagnosticEvent
import codehub.core.diagnostics.DiagnosticEventKind
import codehub.core.diagnostics.DiagnosticSeverity
import codehub.core.diagnostics.DiagnosticSink
import codehub.core.diagnostics.DiagnosticStatus
import codehub.devtools.packages.ApkInstallResult
import codehub.devtools.packages.ApkInstaller
import codehub.git.core.GitService
import codehub.terminal.termux.TermuxBootstrap
import codehub.terminal.termux.TermuxReadiness
import codehub.terminal.termux.TermuxTool
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File

@Singleton
class BuildPipeline @Inject constructor(
    private val termuxBootstrap: TermuxBootstrap,
    private val gitService: GitService,
    private val buildService: BuildService,
    private val apkInstaller: ApkInstaller,
    private val diagnostics: DiagnosticSink
) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val stateFlow = MutableStateFlow<PipelineState>(PipelineState.Idle)
    private val eventFlow = MutableSharedFlow<PipelineEvent>(extraBufferCapacity = 256)

    val state: StateFlow<PipelineState> = stateFlow.asStateFlow()
    val events: Flow<PipelineEvent> = eventFlow.asSharedFlow()

    fun execute(request: PipelineRequest) {
        scope.launch {
            runCatching { runPipeline(request) }
                .onFailure { t ->
                    transition(PipelineState.Failed("pipeline-crashed: ${t.message}"))
                    eventFlow.tryEmit(PipelineEvent.PipelineFailed(request.workspacePath, t.stackTraceToString()))
                }
        }
    }

    private suspend fun runPipeline(request: PipelineRequest) {
        transition(PipelineState.Starting)
        emit(PipelineEvent.WorkspaceOpened(request.workspacePath))
        if (!File(request.workspacePath).isDirectory) {
            fail(request.workspacePath, "Workspace directory does not exist: ${request.workspacePath}")
            return
        }

        val readiness = termuxBootstrap.probe()
        emit(PipelineEvent.TermuxVerified(readiness))
        if (!readiness.ready) {
            val missing = readiness.missingTools.filter { it in TermuxBootstrap.CRITICAL_TOOLS }
            if (missing.isNotEmpty()) {
                val installed = termuxBootstrap.installPackages(missing)
                if (!installed) {
                    fail(request.workspacePath, "Could not install missing Termux packages: ${missing.joinToString(", ")}")
                    return
                }
            }
        }

        val gitStatus = gitService.status(request.workspacePath)
        emit(PipelineEvent.GitStatusChecked(gitStatus.branch, gitStatus.ahead, gitStatus.behind, gitStatus.clean))
        transition(PipelineState.GitChecked(gitStatus.branch))

        val cmakeTarget = BuildTarget(
            id = "cmake-configure-${System.currentTimeMillis()}",
            displayName = "CMake configure",
            workspacePath = request.workspacePath,
            tool = BuildTool.CMake,
            tasks = listOf("-DCMAKE_BUILD_TYPE=Release"),
            environmentOverrides = termuxBootstrap.buildEnvironment(),
            parallelJobs = request.parallelJobs
        )
        transition(PipelineState.BuildConfiguring(cmakeTarget.id))
        emit(PipelineEvent.BuildStarted(cmakeTarget))
        val cmakeId = buildService.enqueue(cmakeTarget)
        val cmakeResult = awaitResult(cmakeId) ?: return fail(request.workspacePath, "CMake configure did not produce a result")
        emit(PipelineEvent.BuildCompleted(cmakeResult))
        if (cmakeResult.status != BuildStatus.Succeeded) {
            return failBuild(request.workspacePath, cmakeResult, "CMake configure failed")
        }

        val ninjaTarget = BuildTarget(
            id = "ninja-build-${System.currentTimeMillis()}",
            displayName = "Ninja build",
            workspacePath = request.workspacePath,
            tool = BuildTool.Ninja,
            tasks = listOf("all"),
            environmentOverrides = termuxBootstrap.buildEnvironment(),
            parallelJobs = request.parallelJobs
        )
        transition(PipelineState.BuildExecuting(ninjaTarget.id))
        emit(PipelineEvent.BuildStarted(ninjaTarget))
        val ninjaId = buildService.enqueue(ninjaTarget)
        val ninjaResult = awaitResult(ninjaId) ?: return fail(request.workspacePath, "Ninja build did not produce a result")
        emit(PipelineEvent.BuildCompleted(ninjaResult))
        if (ninjaResult.status != BuildStatus.Succeeded) {
            return failBuild(request.workspacePath, ninjaResult, "Ninja build failed")
        }

        if (request.nativeSources.isNotEmpty()) {
            val clangTarget = BuildTarget(
                id = "clang-compile-${System.currentTimeMillis()}",
                displayName = "Clang compile",
                workspacePath = request.workspacePath,
                tool = BuildTool.Clang,
                tasks = request.nativeSources,
                environmentOverrides = termuxBootstrap.buildEnvironment(),
                parallelJobs = request.parallelJobs
            )
            transition(PipelineState.NativeCompiling(clangTarget.id))
            emit(PipelineEvent.BuildStarted(clangTarget))
            val clangId = buildService.enqueue(clangTarget)
            val clangResult = awaitResult(clangId) ?: return fail(request.workspacePath, "Clang compile did not produce a result")
            emit(PipelineEvent.BuildCompleted(clangResult))
            if (clangResult.status != BuildStatus.Succeeded) {
                return failBuild(request.workspacePath, clangResult, "Clang compile failed")
            }
        }

        val apks = apkInstaller.discoverApks(request.workspacePath)
        emit(PipelineEvent.ApkDiscovered(apks))
        if (apks.isEmpty()) {
            return fail(request.workspacePath, "No APK discovered in workspace after build")
        }
        val apk = apks.first()
        transition(PipelineState.ApkInstalling(apk.path))
        emit(PipelineEvent.ApkInstalling(apk.path))
        val installResult = apkInstaller.install(apk.path, reinstall = request.reinstallApk)
        emit(PipelineEvent.ApkInstalled(installResult))
        if (!installResult.success) {
            return fail(request.workspacePath, "APK install failed: ${installResult.failureReason ?: installResult.output}")
        }

        val pkg = installResult.packageName ?: apk.packageName
        if (pkg != null) {
            transition(PipelineState.AppLaunching(pkg))
            emit(PipelineEvent.AppLaunching(pkg))
            val launched = apkInstaller.launch(pkg)
            emit(PipelineEvent.AppLaunched(pkg, launched))
            transition(PipelineState.LogcatStreaming(pkg))
            emit(PipelineEvent.LogcatStreaming(pkg))
        }

        transition(PipelineState.Succeeded)
        emit(PipelineEvent.PipelineSucceeded(request.workspacePath))
    }

    private suspend fun awaitResult(targetId: String): BuildResult? {
        var attempts = 0
        while (attempts < 600) {
            val result = buildService.getResult(targetId)
            if (result != null) return result
            kotlinx.coroutines.delay(500)
            attempts++
        }
        return null
    }

    private fun transition(newState: PipelineState) {
        stateFlow.value = newState
    }

    private fun emit(event: PipelineEvent) {
        eventFlow.tryEmit(event)
    }

    private suspend fun fail(workspacePath: String, reason: String) {
        transition(PipelineState.Failed(reason))
        emit(PipelineEvent.PipelineFailed(workspacePath, reason))
        diagnostics.emit(
            DiagnosticEvent.now(
                kind = DiagnosticEventKind.BuildFailed,
                severity = DiagnosticSeverity.Error,
                status = DiagnosticStatus.Failed,
                source = "BuildPipeline",
                message = "Pipeline failed: $reason",
                attributes = mapOf("workspace" to workspacePath)
            )
        )
    }

    private suspend fun failBuild(workspacePath: String, result: BuildResult, reason: String) {
        transition(PipelineState.Failed(reason))
        emit(PipelineEvent.PipelineFailed(workspacePath, reason))
        emit(PipelineEvent.AiAnalysisTriggered(workspacePath, result.stderr.take(4000), "build"))
        diagnostics.emit(
            DiagnosticEvent.now(
                kind = DiagnosticEventKind.BuildFailed,
                severity = DiagnosticSeverity.Error,
                status = DiagnosticStatus.Failed,
                source = "BuildPipeline",
                message = reason,
                reason = result.stderr.take(500),
                attributes = mapOf(
                    "workspace" to workspacePath,
                    "target" to result.target.id,
                    "tool" to result.target.tool.name
                )
            )
        )
    }
}

sealed interface PipelineState {
    data object Idle : PipelineState
    data object Starting : PipelineState
    data class GitChecked(val branch: String) : PipelineState
    data class BuildConfiguring(val targetId: String) : PipelineState
    data class BuildExecuting(val targetId: String) : PipelineState
    data class NativeCompiling(val targetId: String) : PipelineState
    data class ApkInstalling(val apkPath: String) : PipelineState
    data class AppLaunching(val packageName: String) : PipelineState
    data class LogcatStreaming(val packageName: String) : PipelineState
    data object Succeeded : PipelineState
    data class Failed(val reason: String) : PipelineState
}

sealed interface PipelineEvent {
    data class WorkspaceOpened(val path: String) : PipelineEvent
    data class TermuxVerified(val readiness: TermuxReadiness) : PipelineEvent
    data class GitStatusChecked(val branch: String, val ahead: Int, val behind: Int, val clean: Boolean) : PipelineEvent
    data class BuildStarted(val target: BuildTarget) : PipelineEvent
    data class BuildCompleted(val result: BuildResult) : PipelineEvent
    data class ApkDiscovered(val apks: List<codehub.devtools.packages.ApkDescriptor>) : PipelineEvent
    data class ApkInstalling(val apkPath: String) : PipelineEvent
    data class ApkInstalled(val result: ApkInstallResult) : PipelineEvent
    data class AppLaunching(val packageName: String) : PipelineEvent
    data class AppLaunched(val packageName: String, val success: Boolean) : PipelineEvent
    data class LogcatStreaming(val packageName: String) : PipelineEvent
    data class AiAnalysisTriggered(val workspacePath: String, val context: String, val trigger: String) : PipelineEvent
    data class PipelineSucceeded(val workspacePath: String) : PipelineEvent
    data class PipelineFailed(val workspacePath: String, val reason: String) : PipelineEvent
}

data class PipelineRequest(
    val workspacePath: String,
    val nativeSources: List<String> = emptyList(),
    val reinstallApk: Boolean = true,
    val parallelJobs: Int = Runtime.getRuntime().availableProcessors()
)
