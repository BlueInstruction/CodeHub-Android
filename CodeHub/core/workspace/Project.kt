package com.codehub.core.workspace

data class Project(
    val name: String,
    val path: String,
    val description: String? = null,
    val metadata: Map<String, String> = mapOf()
)

data class WorkspaceState(
    val openFolders: List<String> = emptyList(),
    val lastAccessed: Long = System.currentTimeMillis(),
    val configuration: Map<String, String> = mapOf()
)

interface WorkspaceManager {
    fun openProject(path: String): Project
    fun closeProject(): Unit
    fun getState(): WorkspaceState
    fun updateConfiguration(config: Map<String, String>): Unit
    fun getMetadata(key: String): String?
    fun setMetadata(key: String, value: String): Unit
}