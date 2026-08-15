package io.github.blueinstruction.codehub.core.workspace.model

import kotlinx.serialization.Serializable

@Serializable
data class FileEntry(
    val path: String,
    val name: String,
    val isDirectory: Boolean,
    val size: Long,
    val lastModified: Long,
    val readable: Boolean,
    val writable: Boolean,
    val executable: Boolean,
    val symlinkTarget: String? = null
)

@Serializable
data class DirectoryListing(
    val path: String,
    val entries: List<FileEntry>
)

@Serializable
data class ProjectMetadata(
    val projectId: String,
    val properties: Map<String, String>,
    val detectedLanguages: List<String>,
    val detectedBuildSystems: List<String>
)
