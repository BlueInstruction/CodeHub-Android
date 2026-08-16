package codehub.core.services.android

import codehub.build.api.BuildService
import codehub.build.api.BuildStatus
import codehub.build.api.BuildTarget
import codehub.build.api.BuildTool
import codehub.build.gradle.AgpTaskSemantics
import codehub.build.gradle.AndroidArtifactLocator
import codehub.build.signing.DebugKeystoreGenerator
import codehub.build.toolchain.ToolchainComponent
import codehub.build.toolchain.ToolchainInstaller
import codehub.build.toolchain.ToolchainManager
import codehub.core.diagnostics.DiagnosticEvent
import codehub.core.diagnostics.DiagnosticEventKind
import codehub.core.diagnostics.DiagnosticSeverity
import codehub.core.diagnostics.DiagnosticSink
import codehub.core.diagnostics.DiagnosticStatus
import codehub.devtools.logcat.LogcatService
import codehub.devtools.packages.ApkInstaller
import codehub.workspace.template.ProjectGenerationRequest
import codehub.workspace.template.ProjectTemplateKind
import codehub.workspace.template.ProjectTemplateRegistry
import codehub.workspace.template.ProjectGenerator
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
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AndroidProjectBuildPipeline @Inject constructor(
    private val projectGenerator: ProjectGenerator,
    private val toolchainManager: ToolchainManager,
    private val toolchainInstaller: ToolchainInstaller,
    private val keystoreGenerator: DebugKeystoreGenerator,
    private val buildService: BuildService,
    private val apkInstaller: ApkInstaller,
    private val logcatService: LogcatService,
    private val diagnostics: DiagnosticSink
) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val stateFlow = MutableStateFlow<AndroidPipelineState>(AndroidPipelineState.Idle)
    private val eventFlow = MutableSharedFlow<AndroidPipelineEvent>(extraBufferCapacity = 256)
    private val logFlow = MutableSharedFlow<AndroidPipelineEvent.LogEntry>(extraBufferCapacity = 1024)

    val state: StateFlow<AndroidPipelineState> = stateFlow.asStateFlow()
    val events: Flow<AndroidPipelineEvent> = eventFlow.asSharedFlow()
    val logEntries: Flow<AndroidPipelineEvent.LogEntry> = logFlow.asSharedFlow()

    fun execute(request: AndroidPipelineRequest) {
        scope.launch {
            runCatching { runPipeline(request) }
                .onFailure { t ->
                    transition(AndroidPipelineState.Failed)
                    emit(AndroidPipelineEvent.PipelineFailed(t.message ?: "crash", stateFlow.value.name))
                    diagnostics.emit(failureEvent("Pipeline crashed: ${t.message}", "pipeline"))
                }
        }
    }

    private suspend fun runPipeline(request: AndroidPipelineRequest) {
        transition(AndroidPipelineState.Generating)
        emit(AndroidPipelineEvent.GeneratingProject(request.projectPath, request.templateName))

        val templateKind = runCatching { ProjectTemplateKind.valueOf(request.templateName) }.getOrNull()
            ?: ProjectTemplateKind.EmptyCompose
        val template = ProjectTemplateRegistry.get(templateKind)
        val generationRequest = ProjectGenerationRequest(
            template = template,
            projectPath = request.projectPath,
            packageName = request.packageName,
            displayName = request.displayName
        )
        val generationResult = projectGenerator.generate(generationRequest)
        if (!generationResult.success) {
            return fail("Project generation failed: ${generationResult.errors.joinToString("; ")}", "generate")
        }
        emit(AndroidPipelineEvent.ProjectGenerated(generationResult.projectPath, generationResult.filesWritten.size))

        if (!request.skipProvisioning) {
            transition(AndroidPipelineState.Provisioning)
            val readiness = toolchainManager.probe()
            val missingNames = readiness.missing.map { it.name }
            if (missingNames.isNotEmpty()) {
                emit(AndroidPipelineEvent.Provisioning(missingNames))
                val ensured = toolchainInstaller.ensureReady(readiness)
                emit(AndroidPipelineEvent.Provisioned(ensured.missing.size.let { if (it == 0) ensured.components.filter { c -> c.installed }.map { c.component.name } else emptyList() }))
                if (!ensured.ready) {
                    return fail("Toolchain incomplete after provisioning: ${ensured.missing.joinToString(", ")}", "provision")
                }
            }
        }

        transition(AndroidPipelineState.Syncing)
        emit(AndroidPipelineEvent.Syncing(request.projectPath))
        val syncTarget = BuildTarget(
            id = "sync-${System.currentTimeMillis()}",
            displayName = "Gradle sync (tasks)",
            workspacePath = request.projectPath,
            tool = BuildTool.Gradle,
            tasks = listOf("tasks", "--quiet"),
            environmentOverrides = toolchainManager.buildEnvironment(toolchainManager.probe().components)
        )
        val syncId = buildService.enqueue(syncTarget)
        val syncResult = awaitResult(syncId)
        if (syncResult == null || syncResult.status != BuildStatus.Succeeded) {
            emit(AndroidPipelineEvent.Synced(request.projectPath, false))
            return fail("Gradle sync failed: ${syncResult?.stderr?.take(300) ?: "no result"}", "sync")
        }
        emit(AndroidPipelineEvent.Synced(request.projectPath, true))

        transition(AndroidPipelineState.Signing)
        val keystoreResult = keystoreGenerator.ensureDebugKeystore()
        if (!keystoreResult.success) {
            return fail("Keystore generation failed: ${keystoreResult.message}", "sign")
        }

        transition(AndroidPipelineState.Building)
        val buildTarget = BuildTarget(
            id = "build-${System.currentTimeMillis()}",
            displayName = request.displayName,
            workspacePath = request.projectPath,
            tool = BuildTool.Gradle,
            tasks = listOf(request.task),
            environmentOverrides = toolchainManager.buildEnvironment(toolchainManager.probe().components) +
                keystoreGenerator.signingProperties(keystoreResult.config)
        )
        val taskDesc = AgpTaskSemantics.describe(request.task)
        emit(AndroidPipelineEvent.BuildStarted(buildTarget.id, taskDesc.taskName))
        val buildId = buildService.enqueue(buildTarget)
        val buildResult = awaitResult(buildId)
        if (buildResult == null) {
            return fail("Build did not produce a result", "build")
        }
        emit(AndroidPipelineEvent.BuildCompleted(buildTarget.id, buildResult.status.name, buildResult.durationMs))
        if (buildResult.status != BuildStatus.Succeeded) {
            emit(AndroidPipelineEvent.AiAnalysisTriggered("Build failed: ${buildResult.stderr.take(500)}"))
            return fail("Build failed: ${buildResult.stderr.take(300)}", "build")
        }

        val apk = AndroidArtifactLocator.primaryApk(request.projectPath)
            ?: buildResult.artifacts.firstOrNull { it.path.endsWith(".apk") }
        if (apk == null) {
            return fail("No APK found after build", "build")
        }
        emit(AndroidPipelineEvent.ApkDiscovered(apk.path, apk.sizeBytes))

        if (taskDesc.skipsSeparateInstall || request.skipInstall) {
            emit(AndroidPipelineEvent.ApkInstalled(request.packageName, true))
        } else {
            transition(AndroidPipelineState.Installing)
            val installResult = apkInstaller.install(apk.path, reinstall = true)
            emit(AndroidPipelineEvent.ApkInstalled(installResult.packageName ?: request.packageName, installResult.success))
            if (!installResult.success) {
                emit(AndroidPipelineEvent.AiAnalysisTriggered("Install failed: ${installResult.failureReason ?: installResult.output.take(300)}"))
                return fail("APK install failed: ${installResult.failureReason ?: installResult.output.take(300)}", "install")
            }
        }

        transition(AndroidPipelineState.Launching)
        emit(AndroidPipelineEvent.AppLaunching(request.packageName, true))
        val launched = apkInstaller.launch(request.packageName)
        emit(AndroidPipelineEvent.AppLaunched(request.packageName, launched))
        if (!launched) {
            return fail("App launch failed for $request.packageName", "launch")
        }

        transition(AndroidPipelineState.Streaming)
        kotlinx.coroutines.delay(1_000)
        val pid = logcatService.resolvePid(request.packageName)
        emit(AndroidPipelineEvent.LogcatStreaming(request.packageName, pid))

        if (pid != null && pid > 0) {
            scope.launch {
                logcatService.streamForPid(pid).collect { entry ->
                    logFlow.tryEmit(
                        AndroidPipelineEvent.LogEntry(
                            timestamp = entry.timestamp,
                            level = entry.level,
                            tag = entry.tag,
                            message = entry.message
                        )
                    )
                }
            }
        }

        transition(AndroidPipelineState.Succeeded)
        emit(AndroidPipelineEvent.PipelineSucceeded(request.packageName))
    }

    private suspend fun awaitResult(targetId: String): codehub.build.api.BuildResult? {
        var attempts = 0
        while (attempts < 1200) {
            val result = buildService.getResult(targetId)
            if (result != null) return result
            kotlinx.coroutines.delay(500)
            attempts++
        }
        return null
    }

    private fun transition(newState: AndroidPipelineState) {
        stateFlow.value = newState
    }

    private fun emit(event: AndroidPipelineEvent) {
        eventFlow.tryEmit(event)
    }

    private suspend fun fail(reason: String, stage: String) {
        transition(AndroidPipelineState.Failed)
        emit(AndroidPipelineEvent.PipelineFailed(reason, stage))
        diagnostics.emit(failureEvent(reason, stage))
    }

    private fun failureEvent(reason: String, stage: String): DiagnosticEvent = DiagnosticEvent.now(
        kind = DiagnosticEventKind.BuildFailed,
        severity = DiagnosticSeverity.Error,
        status = DiagnosticStatus.Failed,
        source = "AndroidProjectBuildPipeline",
        message = reason,
        attributes = mapOf("stage" to stage)
    )
}
