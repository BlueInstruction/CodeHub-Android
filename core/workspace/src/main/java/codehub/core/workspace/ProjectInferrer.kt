package codehub.core.workspace

import codehub.core.workspace.model.BuildSystem
import codehub.core.workspace.model.Project
import codehub.core.workspace.model.ProjectLanguage
import codehub.core.workspace.model.VcsKind
import java.io.File

class ProjectInferrer {

    fun infer(root: File): ProjectMeta {
        val languages = mutableSetOf<ProjectLanguage>()
        val buildSystems = mutableSetOf<BuildSystem>()
        val props = mutableMapOf<String, String>()

        if (File(root, "build.gradle.kts").exists() || File(root, "build.gradle").exists()) {
            buildSystems += BuildSystem.Gradle
            languages += ProjectLanguage.Kotlin
            props["gradle.variant"] = if (File(root, "build.gradle.kts").exists()) "kotlin" else "groovy"
        }
        if (File(root, "settings.gradle.kts").exists() || File(root, "settings.gradle").exists()) {
            props["has.settings"] = "true"
        }
        if (File(root, "CMakeLists.txt").exists()) {
            buildSystems += BuildSystem.CMake
            languages += ProjectLanguage.Cpp
        }
        if (File(root, "build.ninja").exists() || hasNinjaFiles(root)) {
            buildSystems += BuildSystem.Ninja
        }
        if (File(root, "Makefile").exists()) {
            buildSystems += BuildSystem.Make
        }
        if (File(root, "meson.build").exists()) {
            buildSystems += BuildSystem.Meson
        }
        if (File(root, "Cargo.toml").exists()) {
            buildSystems += BuildSystem.Cargo
            languages += ProjectLanguage.Rust
        }
        if (File(root, "go.mod").exists()) {
            buildSystems += BuildSystem.GoBuild
            languages += ProjectLanguage.Go
        }
        if (File(root, "package.json").exists()) {
            languages += ProjectLanguage.JavaScript
            languages += ProjectLanguage.TypeScript
        }
        if (File(root, "pyproject.toml").exists() || File(root, "setup.py").exists()) {
            languages += ProjectLanguage.Python
        }
        if (File(root, "AndroidManifest.xml").exists() || File(root, "app/src/main/AndroidManifest.xml").exists()) {
            props["android.project"] = "true"
        }
        if (File(root, ".git").exists()) {
            props["vcs.git"] = "true"
        }

        if (languages.isEmpty()) languages += ProjectLanguage.Unknown
        if (buildSystems.isEmpty()) buildSystems += BuildSystem.Unknown

        return ProjectMeta(
            languages = languages.toList(),
            buildSystems = buildSystems.toList(),
            vcs = if (File(root, ".git").exists()) VcsKind.Git else VcsKind.None,
            properties = props
        )
    }

    private fun hasNinjaFiles(root: File): Boolean {
        val dirs = listOf("build", "out", ".ninja")
        return dirs.any { File(root, it).exists() && File(root, it).listFiles()?.any { it.name.endsWith(".ninja") } == true }
    }
}

data class ProjectMeta(
    val languages: List<ProjectLanguage>,
    val buildSystems: List<BuildSystem>,
    val vcs: VcsKind,
    val properties: Map<String, String>
)

fun ProjectMeta.toProject(root: File, name: String = root.name): Project {
    val now = System.currentTimeMillis()
    return Project(
        id = root.absolutePath,
        name = name,
        path = root.absolutePath,
        language = languages.firstOrNull() ?: ProjectLanguage.Unknown,
        buildSystem = buildSystems.firstOrNull() ?: BuildSystem.Unknown,
        vcs = vcs,
        createdAt = now,
        updatedAt = now
    )
}
