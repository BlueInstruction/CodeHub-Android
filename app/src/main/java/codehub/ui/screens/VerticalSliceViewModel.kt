package codehub.ui.screens

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import codehub.ai.agents.BuildFailureAnalysis
import codehub.ai.agents.FailureType
import codehub.core.services.BuildPipeline
import codehub.core.services.PipelineEvent
import codehub.core.services.PipelineRequest
import codehub.core.services.PipelineState
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

@HiltViewModel
class VerticalSliceViewModel @Inject constructor(
    private val pipeline: BuildPipeline,
    private val failureAnalysis: BuildFailureAnalysis
) : ViewModel() {

    private val _workspacePath = MutableStateFlow("")
    val workspacePath: StateFlow<String> = _workspacePath.asStateFlow()

    private val _events = MutableStateFlow<List<PipelineEvent>>(emptyList())
    val events: StateFlow<List<PipelineEvent>> = _events.asStateFlow()

    private val _analysis = MutableStateFlow<codehub.ai.agents.AnalysisResult?>(null)
    val analysis: StateFlow<codehub.ai.agents.AnalysisResult?> = _analysis.asStateFlow()

    val state: StateFlow<PipelineState> = pipeline.state

    init {
        viewModelScope.launch {
            pipeline.events.collect { event ->
                _events.value = (_events.value + event).takeLast(200)
                if (event is PipelineEvent.AiAnalysisTriggered) {
                    triggerAnalysis(event)
                }
            }
        }
    }

    fun pickWorkspace() {
        _workspacePath.value = "/storage/emulated/0/CodeHub/workspaces/demo"
    }

    fun setWorkspacePath(path: String) {
        _workspacePath.value = path
    }

    fun runPipeline() {
        val path = _workspacePath.value
        if (path.isBlank()) return
        _events.value = emptyList()
        _analysis.value = null
        pipeline.execute(
            PipelineRequest(
                workspacePath = path,
                nativeSources = emptyList(),
                reinstallApk = true,
                parallelJobs = Runtime.getRuntime().availableProcessors()
            )
        )
    }

    private fun triggerAnalysis(event: PipelineEvent.AiAnalysisTriggered) {
        viewModelScope.launch {
            val type = when (event.trigger) {
                "build" -> FailureType.BuildCompile
                "configure" -> FailureType.BuildConfigure
                "install" -> FailureType.ApkInstall
                "launch" -> FailureType.AppLaunch
                "crash" -> FailureType.LogcatCrash
                else -> FailureType.BuildCompile
            }
            val result = runCatching {
                failureAnalysis.analyze(
                    workspacePath = event.workspacePath,
                    providerId = "offline",
                    failingContext = event.context,
                    failureType = type
                )
            }
            result.onSuccess { _analysis.value = it }
                .onFailure { _analysis.value = null }
        }
    }
}
