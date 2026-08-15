package io.github.blueinstruction.codehub.core.workspace.repo

import io.github.blueinstruction.codehub.core.workspace.WorkspaceRepository
import io.github.blueinstruction.codehub.core.workspace.db.ProjectDao
import io.github.blueinstruction.codehub.core.workspace.db.ProjectEntity
import io.github.blueinstruction.codehub.core.workspace.model.BuildSystem
import io.github.blueinstruction.codehub.core.workspace.model.Project
import io.github.blueinstruction.codehub.core.workspace.model.ProjectLanguage
import io.github.blueinstruction.codehub.core.workspace.model.VcsKind
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map

@Singleton
class RoomWorkspaceRepository @Inject constructor(
    private val dao: ProjectDao
) : WorkspaceRepository {

    private val _activeWorkspaceId = MutableStateFlow<String?>(null)
    override val activeWorkspaceId: StateFlow<String?> = _activeWorkspaceId.asStateFlow()

    override fun observeProjects(): Flow<List<Project>> =
        dao.observeAll().map { list -> list.map { it.toDomain() } }

    override suspend fun getProject(id: String): Project? =
        dao.findById(id)?.toDomain()

    override suspend fun upsertProject(project: Project) {
        val now = System.currentTimeMillis()
        dao.upsert(project.toEntity(now))
    }

    override suspend fun deleteProject(id: String) {
        dao.deleteById(id)
    }

    override suspend fun setActiveWorkspace(workspaceId: String?) {
        _activeWorkspaceId.value = workspaceId
        if (workspaceId != null) {
            dao.markOpened(workspaceId, System.currentTimeMillis())
        }
    }

    private fun ProjectEntity.toDomain(): Project = Project(
        id = id,
        name = name,
        path = path,
        language = runCatching { ProjectLanguage.valueOf(language) }.getOrDefault(ProjectLanguage.Unknown),
        buildSystem = runCatching { BuildSystem.valueOf(buildSystem) }.getOrDefault(BuildSystem.Unknown),
        vcs = runCatching { VcsKind.valueOf(vcs) }.getOrDefault(VcsKind.None),
        createdAt = createdAt,
        updatedAt = updatedAt
    )

    private fun Project.toEntity(updatedAt: Long): ProjectEntity = ProjectEntity(
        id = id,
        name = name,
        path = path,
        language = language.name,
        buildSystem = buildSystem.name,
        vcs = vcs.name,
        createdAt = createdAt,
        updatedAt = updatedAt,
        pinned = false
    )
}
