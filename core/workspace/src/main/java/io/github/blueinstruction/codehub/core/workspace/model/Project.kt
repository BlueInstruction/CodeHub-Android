package io.github.blueinstruction.codehub.core.workspace.model

import kotlinx.serialization.Serializable

@Serializable
data class Project(
    val id: String,
    val name: String,
    val path: String,
    val language: ProjectLanguage = ProjectLanguage.Unknown,
    val buildSystem: BuildSystem = BuildSystem.Unknown,
    val vcs: VcsKind = VcsKind.None,
    val createdAt: Long,
    val updatedAt: Long
)

@Serializable
enum class ProjectLanguage {
    Unknown, Kotlin, Java, Cpp, C, Rust, Python, JavaScript, TypeScript, Go, Mixed
}

@Serializable
enum class BuildSystem {
    Unknown, Gradle, CMake, Ninja, Make, Meson, Bazel, Cargo, GoBuild, Mixed
}

@Serializable
enum class VcsKind { None, Git, Mercurial, Subversion }
