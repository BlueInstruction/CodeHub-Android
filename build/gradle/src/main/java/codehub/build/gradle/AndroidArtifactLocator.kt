package codehub.build.gradle

import codehub.build.api.BuildArtifact
import java.io.File

object AndroidArtifactLocator {

    fun findApks(workspacePath: String): List<BuildArtifact> {
        val results = mutableListOf<BuildArtifact>()
        val searchPaths = listOf(
            "app/build/outputs/apk/debug",
            "app/build/outputs/apk/release",
            "app/build/outputs/apk",
            "build/outputs/apk/debug",
            "build/outputs/apk/release",
            "build/outputs/apk"
        )
        for (sub in searchPaths) {
            val dir = File(workspacePath, sub)
            if (!dir.isDirectory) continue
            dir.walkTopDown()
                .filter { it.isFile && it.extension.equals("apk", ignoreCase = true) }
                .forEach { results.add(BuildArtifact(path = it.absolutePath, sizeBytes = it.length())) }
        }
        return results.distinctBy { it.path }
    }

    fun findAabs(workspacePath: String): List<BuildArtifact> {
        val results = mutableListOf<BuildArtifact>()
        val searchPaths = listOf(
            "app/build/outputs/bundle/debug",
            "app/build/outputs/bundle/release",
            "app/build/outputs/bundle"
        )
        for (sub in searchPaths) {
            val dir = File(workspacePath, sub)
            if (!dir.isDirectory) continue
            dir.walkTopDown()
                .filter { it.isFile && it.extension.equals("aab", ignoreCase = true) }
                .forEach { results.add(BuildArtifact(path = it.absolutePath, sizeBytes = it.length())) }
        }
        return results.distinctBy { it.path }
    }

    fun findAars(workspacePath: String): List<BuildArtifact> {
        val results = mutableListOf<BuildArtifact>()
        val searchPaths = listOf(
            "app/build/outputs/aar",
            "build/outputs/aar",
            "library/build/outputs/aar"
        )
        for (sub in searchPaths) {
            val dir = File(workspacePath, sub)
            if (!dir.isDirectory) continue
            dir.walkTopDown()
                .filter { it.isFile && it.extension.equals("aar", ignoreCase = true) }
                .forEach { results.add(BuildArtifact(path = it.absolutePath, sizeBytes = it.length())) }
        }
        return results.distinctBy { it.path }
    }

    fun findLintReports(workspacePath: String): List<BuildArtifact> {
        val results = mutableListOf<BuildArtifact>()
        val searchPaths = listOf(
            "app/build/reports",
            "build/reports"
        )
        for (sub in searchPaths) {
            val dir = File(workspacePath, sub)
            if (!dir.isDirectory) continue
            dir.walkTopDown()
                .filter { it.isFile && (it.extension.equals("html", ignoreCase = true) || it.extension.equals("xml", ignoreCase = true)) }
                .filter { it.nameWithoutExtension.startsWith("lint-results") }
                .forEach { results.add(BuildArtifact(path = it.absolutePath, sizeBytes = it.length())) }
        }
        return results.distinctBy { it.path }
    }

    fun findAll(workspacePath: String): List<BuildArtifact> {
        val all = mutableListOf<BuildArtifact>()
        all += findApks(workspacePath)
        all += findAabs(workspacePath)
        all += findAars(workspacePath)
        all += findLintReports(workspacePath)
        return all
    }

    fun primaryApk(workspacePath: String): BuildArtifact? {
        val apks = findApks(workspacePath)
        if (apks.isEmpty()) return null
        val debugApk = apks.firstOrNull { it.path.contains("debug", ignoreCase = true) }
        return debugApk ?: apks.first()
    }

    fun resolveArtifactName(apkPath: String, applicationId: String?): String {
        val file = File(apkPath)
        val baseName = file.nameWithoutExtension
        return when {
            baseName.endsWith("-debug", ignoreCase = true) -> "$applicationId (debug)"
            baseName.endsWith("-release", ignoreCase = true) -> "$applicationId (release)"
            else -> baseName
        }
    }
}
