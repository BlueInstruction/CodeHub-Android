package codehub.ui.screens.newproject

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import codehub.ai.agents.AnalysisResult
import codehub.ai.agents.BuildFailureAnalysis
import codehub.ai.agents.FailureType
import codehub.core.services.android.AndroidPipelineEvent
import codehub.core.services.android.AndroidPipelineRequest
import codehub.core.services.android.AndroidPipelineState
import codehub.core.services.android.AndroidProjectBuildPipeline
import codehub.workspace.template.ProjectTemplateKind
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class NewProjectUiState(
    val templateKind: ProjectTemplateKind = ProjectTemplateKind.EmptyCompose,
    val packageName: String = "com.example.myapp",
    val displayName: String = "My App",
    val projectPath: String = "/storage/emulated/0/CodeHub/workspaces/myapp",
    val task: String = "assembleDebug",
    val skipProvisioning: Boolean = false,
    val skipInstall: Boolean = false,
    val packageNameError: String? = null,
    val displayNameError: String? = null,
    val canSubmit: Boolean = false
)

@HiltViewModel
class NewProjectViewModel @Inject constructor(
    private val pipeline: AndroidProjectBuildPipeline,
    private val failureAnalysis: BuildFailureAnalysis
) : ViewModel() {

    private val _uiState = MutableStateFlow(NewProjectUiState())
    val uiState: StateFlow<NewProjectUiState> = _uiState.asStateFlow()

    private val _events = MutableStateFlow<List<AndroidPipelineEvent>>(emptyList())
    val events: StateFlow<List<AndroidPipelineEvent>> = _events.asStateFlow()

    private val _logEntries = MutableStateFlow<List<AndroidPipelineEvent.LogEntry>>(emptyList())
    val logEntries: StateFlow<List<AndroidPipelineEvent.LogEntry>> = _logEntries.asStateFlow()

    private val _analysis = MutableStateFlow<AnalysisResult?>(null)
    val analysis: StateFlow<AnalysisResult?> = _analysis.asStateFlow()

    private val _analyzing = MutableStateFlow(false)
    val analyzing: StateFlow<Boolean> = _analyzing.asStateFlow()

    val pipelineState: StateFlow<AndroidPipelineState> = pipeline.state

    val isRunning: StateFlow<Boolean> = kotlinx.coroutines.flow.combine(
        pipeline.state,
        _uiState
    ) { pipeState, _ ->
        pipeState != AndroidPipelineState.Idle &&
            pipeState != AndroidPipelineState.Succeeded &&
            pipeState != AndroidPipelineState.Failed
    }.stateIn(viewModelScope, kotlinx.coroutines.flow.SharingStarted.Eagerly, false)

    init {
        viewModelScope.launch {
            pipeline.events.collect { event ->
                _events.value = (_events.value + event).takeLast(100)
                if (event is AndroidPipelineEvent.AiAnalysisTriggered) {
                    triggerAnalysis(event)
                }
            }
        }
        viewModelScope.launch {
            pipeline.logEntries.collect { entry ->
                _logEntries.value = (_logEntries.value + entry).takeLast(500)
            }
        }
        validate()
    }

    fun updateTemplate(kind: ProjectTemplateKind) {
        _uiState.value = _uiState.value.copy(templateKind = kind)
        validate()
    }

    fun updatePackageName(value: String) {
        _uiState.value = _uiState.value.copy(packageName = value)
        validate()
    }

    fun updateDisplayName(value: String) {
        _uiState.value = _uiState.value.copy(displayName = value)
        validate()
    }

    fun updateProjectPath(value: String) {
        _uiState.value = _uiState.value.copy(projectPath = value)
        validate()
    }

    fun updateTask(value: String) {
        _uiState.value = _uiState.value.copy(task = value)
        validate()
    }

    fun toggleSkipProvisioning() {
        _uiState.value = _uiState.value.copy(skipProvisioning = !_uiState.value.skipProvisioning)
    }

    fun toggleSkipInstall() {
        _uiState.value = _uiState.value.copy(skipInstall = !_uiState.value.skipInstall)
    }

    fun runPipeline() {
        val state = _uiState.value
        if (!state.canSubmit) return
        _events.value = emptyList()
        _logEntries.value = emptyList()
        _analysis.value = null
        _analyzing.value = false
        pipeline.execute(
            AndroidPipelineRequest(
                projectPath = state.projectPath,
                templateName = state.templateKind.name,
                packageName = state.packageName,
                displayName = state.displayName,
                task = state.task,
                skipProvisioning = state.skipProvisioning,
                skipInstall = state.skipInstall
            )
        )
    }

    private fun triggerAnalysis(event: AndroidPipelineEvent.AiAnalysisTriggered) {
        viewModelScope.launch {
            _analyzing.value = true
            val type = when (event.failureType) {
                "BuildConfigure" -> FailureType.BuildConfigure
                "BuildCompile" -> FailureType.BuildCompile
                "ApkInstall" -> FailureType.ApkInstall
                "AppLaunch" -> FailureType.AppLaunch
                "LogcatCrash" -> FailureType.LogcatCrash
                "Sync" -> FailureType.Sync
                else -> FailureType.BuildCompile
            }
            val result = runCatching {
                failureAnalysis.analyze(
                    workspacePath = event.workspacePath,
                    providerId = "offline",
                    failingContext = event.stderr,
                    failureType = type,
                    packageName = event.packageName
                )
            }
            result.onSuccess { _analysis.value = it }
                .onFailure { _analysis.value = null }
            _analyzing.value = false
        }
    }

    private fun validate() {
        val state = _uiState.value
        val packageError = if (!isValidPackageName(state.packageName)) {
            "Package name must be lowercase, dot-separated, start with a letter"
        } else null
        val nameError = if (state.displayName.isBlank()) "Display name must not be blank" else null
        _uiState.value = state.copy(
            packageNameError = packageError,
            displayNameError = nameError,
            canSubmit = packageError == null && nameError == null && state.projectPath.isNotBlank()
        )
    }

    private fun isValidPackageName(name: String): Boolean =
        name.matches(Regex("""^[a-z][a-z0-9]*(\.[a-z][a-z0-9]*)*$"""))
}
