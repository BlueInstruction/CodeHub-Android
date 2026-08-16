package codehub.core.workspace

import codehub.core.workspace.model.Project
import kotlinx.coroutines.flow.Flow

interface WorkspaceRepository {
    val activeWorkspaceId: Flow<String?>
    fun observeProjects(): Flow<List<Project>>
    suspend fun getProject(id: String): Project?
    suspend fun upsertProject(project: Project)
    suspend fun deleteProject(id: String)
    suspend fun setActiveWorkspace(workspaceId: String?)
}
