package codehub.build.gradle

import kotlinx.serialization.Serializable

@Serializable
enum class AgpTaskKind {
    AssembleDebug,
    AssembleRelease,
    InstallDebug,
    InstallRelease,
    BundleDebug,
    BundleRelease,
    LintDebug,
    LintRelease,
    CompileDebugKotlin,
    CompileReleaseKotlin,
    Dependencies,
    Clean,
    Test,
    GenericGradle
}

@Serializable
data class AgpTaskDescriptor(
    val kind: AgpTaskKind,
    val taskName: String,
    val producesArtifact: Boolean,
    val artifactKind: ArtifactKind?,
    val skipsSeparateInstall: Boolean,
    val outputSubpath: String?
)

@Serializable
enum class ArtifactKind { Apk, Aab, Aar, Jar, LintReport }

object AgpTaskSemantics {

    private val taskMap: Map<String, AgpTaskDescriptor> = mapOf(
        "assembleDebug" to AgpTaskDescriptor(
            kind = AgpTaskKind.AssembleDebug,
            taskName = "assembleDebug",
            producesArtifact = true,
            artifactKind = ArtifactKind.Apk,
            skipsSeparateInstall = false,
            outputSubpath = "app/build/outputs/apk/debug"
        ),
        "assembleRelease" to AgpTaskDescriptor(
            kind = AgpTaskKind.AssembleRelease,
            taskName = "assembleRelease",
            producesArtifact = true,
            artifactKind = ArtifactKind.Apk,
            skipsSeparateInstall = false,
            outputSubpath = "app/build/outputs/apk/release"
        ),
        "installDebug" to AgpTaskDescriptor(
            kind = AgpTaskKind.InstallDebug,
            taskName = "installDebug",
            producesArtifact = true,
            artifactKind = ArtifactKind.Apk,
            skipsSeparateInstall = true,
            outputSubpath = "app/build/outputs/apk/debug"
        ),
        "installRelease" to AgpTaskDescriptor(
            kind = AgpTaskKind.InstallRelease,
            taskName = "installRelease",
            producesArtifact = true,
            artifactKind = ArtifactKind.Apk,
            skipsSeparateInstall = true,
            outputSubpath = "app/build/outputs/apk/release"
        ),
        "bundleDebug" to AgpTaskDescriptor(
            kind = AgpTaskKind.BundleDebug,
            taskName = "bundleDebug",
            producesArtifact = true,
            artifactKind = ArtifactKind.Aab,
            skipsSeparateInstall = false,
            outputSubpath = "app/build/outputs/bundle/debug"
        ),
        "bundleRelease" to AgpTaskDescriptor(
            kind = AgpTaskKind.BundleRelease,
            taskName = "bundleRelease",
            producesArtifact = true,
            artifactKind = ArtifactKind.Aab,
            skipsSeparateInstall = false,
            outputSubpath = "app/build/outputs/bundle/release"
        ),
        "lintDebug" to AgpTaskDescriptor(
            kind = AgpTaskKind.LintDebug,
            taskName = "lintDebug",
            producesArtifact = true,
            artifactKind = ArtifactKind.LintReport,
            skipsSeparateInstall = false,
            outputSubpath = "app/build/reports/lint-results-debug.html"
        ),
        "lintRelease" to AgpTaskDescriptor(
            kind = AgpTaskKind.LintRelease,
            taskName = "lintRelease",
            producesArtifact = true,
            artifactKind = ArtifactKind.LintReport,
            skipsSeparateInstall = false,
            outputSubpath = "app/build/reports/lint-results-release.html"
        ),
        "compileDebugKotlin" to AgpTaskDescriptor(
            kind = AgpTaskKind.CompileDebugKotlin,
            taskName = "compileDebugKotlin",
            producesArtifact = false,
            artifactKind = null,
            skipsSeparateInstall = false,
            outputSubpath = null
        ),
        "compileReleaseKotlin" to AgpTaskDescriptor(
            kind = AgpTaskKind.CompileReleaseKotlin,
            taskName = "compileReleaseKotlin",
            producesArtifact = false,
            artifactKind = null,
            skipsSeparateInstall = false,
            outputSubpath = null
        ),
        "dependencies" to AgpTaskDescriptor(
            kind = AgpTaskKind.Dependencies,
            taskName = "dependencies",
            producesArtifact = false,
            artifactKind = null,
            skipsSeparateInstall = false,
            outputSubpath = null
        ),
        "clean" to AgpTaskDescriptor(
            kind = AgpTaskKind.Clean,
            taskName = "clean",
            producesArtifact = false,
            artifactKind = null,
            skipsSeparateInstall = false,
            outputSubpath = null
        ),
        "test" to AgpTaskDescriptor(
            kind = AgpTaskKind.Test,
            taskName = "test",
            producesArtifact = false,
            artifactKind = null,
            skipsSeparateInstall = false,
            outputSubpath = null
        )
    )

    fun describe(taskName: String): AgpTaskDescriptor {
        val normalized = normalizeTaskName(taskName)
        return taskMap[normalized] ?: AgpTaskDescriptor(
            kind = AgpTaskKind.GenericGradle,
            taskName = taskName,
            producesArtifact = false,
            artifactKind = null,
            skipsSeparateInstall = false,
            outputSubpath = null
        )
    }

    fun describeAll(tasks: List<String>): List<AgpTaskDescriptor> = tasks.map { describe(it) }

    fun isAgpProject(workspacePath: String): Boolean {
        val appGradle = java.io.File(workspacePath, "app/build.gradle.kts")
        if (!appGradle.exists()) return false
        val content = appGradle.readText()
        return content.contains("android.application") || content.contains("com.android.application")
    }

    fun primaryArtifactTask(tasks: List<String>): AgpTaskDescriptor? {
        val descriptors = describeAll(tasks)
        return descriptors.firstOrNull { it.producesArtifact && it.artifactKind == ArtifactKind.Apk }
            ?: descriptors.firstOrNull { it.producesArtifact }
    }

    private fun normalizeTaskName(taskName: String): String {
        if (taskName.startsWith(":app:")) return taskName.removePrefix(":app:")
        return taskName
    }
}
