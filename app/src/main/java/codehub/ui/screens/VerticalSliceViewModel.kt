package codehub.ui.screens

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import codehub.core.services.BuildPipeline
import codehub.core.services.PipelineEvent
import codehub.core.services.PipelineRequest
import codehub.core.services.PipelineState
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch

@HiltViewModel
class VerticalSliceViewModel @Inject constructor(
    private val pipeline: BuildPipeline
) : ViewModel() {

    private val _workspacePath = MutableStateFlow("")
    val workspacePath: StateFlow<String> = _workspacePath.asStateFlow()

    private val _events = MutableStateFlow<List<PipelineEvent>>(emptyList())
    val events: StateFlow<List<PipelineEvent>> = _events.asStateFlow()

    val state: StateFlow<PipelineState> = pipeline.state

    init {
        viewModelScope.launch {
            pipeline.events.collect { event ->
                _events.value = _events.value + event
                if (event is PipelineEvent.PipelineSucceeded || event is PipelineEvent.PipelineFailed) {
                    val keep = _events.value.takeLast(200)
                    _events.value = keep
                }
            }
        }
    }

    fun pickWorkspace() {
        viewModelScope.launch {
            _workspacePath.value = "/storage/emulated/0/CodeHub/workspaces/demo"
        }
    }

    fun setWorkspacePath(path: String) {
        _workspacePath.value = path
    }

    fun runPipeline() {
        val path = _workspacePath.value
        if (path.isBlank()) return
        _events.value = emptyList()
        pipeline.execute(
            PipelineRequest(
                workspacePath = path,
                nativeSources = emptyList(),
                reinstallApk = true,
                parallelJobs = Runtime.getRuntime().availableProcessors()
            )
        )
    }
}
