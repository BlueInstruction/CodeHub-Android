package codehub.ai.agents

import codehub.core.diagnostics.DiagnosticSink
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ProjectContextGatherer @Inject constructor(
    private val diagnostics: DiagnosticSink
) {

    data class ProjectContext(
        val workspacePath: String,
        val buildGradleRoot: String?,
        val appBuildGradle: String?,
        val settingsGradle: String?,
        val versionCatalog: String?,
        val androidManifest: String?,
        val gradleProperties: String?,
        val gradleWrapperProperties: String?,
        val referencedSourceFiles: List<ReferencedFile>
    )

    data class ReferencedFile(
        val path: String,
        val relativePath: String,
        val content: String
    )

    fun gather(
        workspacePath: String,
        referencedPaths: List<String> = emptyList(),
        maxFileBytes: Int = 16_000
    ): ProjectContext {
        val workspace = File(workspacePath)
        return ProjectContext(
            workspacePath = workspacePath,
            buildGradleRoot = readTrimmed(File(workspace, "build.gradle.kts"), maxFileBytes),
            appBuildGradle = readTrimmed(File(workspace, "app/build.gradle.kts"), maxFileBytes),
            settingsGradle = readTrimmed(File(workspace, "settings.gradle.kts"), maxFileBytes),
            versionCatalog = readTrimmed(File(workspace, "gradle/libs.versions.toml"), maxFileBytes),
            androidManifest = readTrimmed(File(workspace, "app/src/main/AndroidManifest.xml"), maxFileBytes),
            gradleProperties = readTrimmed(File(workspace, "gradle.properties"), maxFileBytes),
            gradleWrapperProperties = readTrimmed(File(workspace, "gradle/wrapper/gradle-wrapper.properties"), maxFileBytes),
            referencedSourceFiles = referencedPaths.mapNotNull { path ->
                readReferenced(workspacePath, path, maxFileBytes)
            }
        )
    }

    fun gatherFromDiagnostics(
        workspacePath: String,
        diagnostics: List<codehub.build.api.BuildDiagnostic>,
        maxFileBytes: Int = 16_000
    ): ProjectContext {
        val referenced = diagnostics
            .mapNotNull { it.file }
            .filter { it.isNotBlank() }
            .map { normalizePath(workspacePath, it) }
            .distinct()
            .take(8)
        return gather(workspacePath, referenced, maxFileBytes)
    }

    private fun normalizePath(workspacePath: String, file: String): String {
        if (file.startsWith("/")) return file
        if (file.startsWith("file://")) return file.removePrefix("file://")
        return File(workspacePath, file).absolutePath
    }

    private fun readTrimmed(file: File, maxBytes: Int): String? {
        if (!file.exists() || !file.isFile) return null
        return runCatching {
            val bytes = file.readBytes()
            val truncated = if (bytes.size > maxBytes) {
                String(bytes, 0, maxBytes, Charsets.UTF_8) + "\n... (truncated, ${bytes.size} bytes total)"
            } else {
                String(bytes, Charsets.UTF_8)
            }
            truncated
        }.getOrNull()
    }

    private fun readReferenced(workspacePath: String, path: String, maxBytes: Int): ReferencedFile? {
        val file = File(path)
        val actual = if (file.isAbsolute) file else File(workspacePath, path)
        if (!actual.exists() || !actual.isFile) return null
        val content = readTrimmed(actual, maxBytes) ?: return null
        val relative = actual.absolutePath.removePrefix("$workspacePath/")
        return ReferencedFile(
            path = actual.absolutePath,
            relativePath = relative,
            content = content
        )
    }
}
