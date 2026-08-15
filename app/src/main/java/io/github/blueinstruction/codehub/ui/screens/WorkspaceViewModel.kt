package io.github.blueinstruction.codehub.ui.screens

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.blueinstruction.codehub.core.workspace.WorkspaceRepository
import io.github.blueinstruction.codehub.core.workspace.model.Project
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class WorkspaceUiState(
    val projects: List<Project> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null
)

@HiltViewModel
class WorkspaceViewModel @Inject constructor(
    private val workspace: WorkspaceRepository
) : ViewModel() {

    private val _state = MutableStateFlow(WorkspaceUiState(isLoading = true))
    val state: StateFlow<WorkspaceUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            workspace.observeProjects()
                .collect { projects ->
                    _state.value = WorkspaceUiState(projects = projects, isLoading = false)
                }
        }
    }
}
