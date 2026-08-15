package com.codehub.core.build

data class BuildOutput(
    val command: String,
    val workingDirectory: String,
    val environment: Map<String, String> = mapOf(),
    val stdout: String = "",
    val stderr: String = "",
    val exitCode: Int,
    val duration: Long,
    val artifacts: List<String> = emptyList(),
    val diagnostics: List<String> = emptyList()
)

data class BuildRequest(
    val name: String,
    val command: String,
    val args: List<String> = emptyList(),
    val workingDirectory: String,
    val environment: Map<String, String> = mapOf(),
    val expectedArtifacts: List<String> = emptyList()
)

interface BuildService {
    fun executeBuild(request: BuildRequest): BuildOutput
    fun cancelBuild(): Boolean
    fun getBuildHistory(): List<BuildOutput>
    fun clearBuildHistory(): Unit
}