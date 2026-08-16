package codehub.core.workspace.model

import kotlinx.serialization.Serializable

@Serializable
data class WorkspaceDescriptor(
    val id: String,
    val rootPath: String,
    val displayName: String,
    val openFolders: List<String>,
    val activeFilePath: String? = null,
    val pinnedBranch: String? = null,
    val createdAt: Long,
    val updatedAt: Long
)

@Serializable
data class WorkspaceState(
    val descriptor: WorkspaceDescriptor,
    val openedFiles: List<String> = emptyList(),
    val unsavedFiles: Set<String> = emptySet(),
    val environmentOverrides: Map<String, String> = emptyMap()
)
