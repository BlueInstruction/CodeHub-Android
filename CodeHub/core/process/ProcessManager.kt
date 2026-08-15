package com.codehub.core.process

data class ProcessInfo(
    val pid: Int,
    val command: String,
    val workingDirectory: String,
    val environment: Map<String, String> = mapOf(),
    val startTime: Long = System.currentTimeMillis(),
    val status: ProcessStatus = ProcessStatus.RUNNING
)

enum class ProcessStatus {
    RUNNING,
    COMPLETED,
    FAILED,
    CANCELLED,
    TIMEOUT
}

interface ProcessManager {
    fun startProcess(command: String, args: List<String> = emptyList(), workingDir: String = ".", env: Map<String, String> = mapOf()): ProcessInfo
    fun stopProcess(pid: Int): Boolean
    fun killProcess(pid: Int): Boolean
    fun getProcessStatus(pid: Int): ProcessStatus
    fun getProcessOutput(pid: Int): String
    fun getProcessError(pid: Int): String
    fun waitForProcess(pid: Int, timeout: Long = 30000): ProcessStatus
}