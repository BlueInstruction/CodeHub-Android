package io.github.blueinstruction.codehub.terminal.ssh

import kotlinx.serialization.Serializable

@Serializable
data class SshTarget(
    val host: String,
    val port: Int = 22,
    val user: String,
    val password: String? = null,
    val privateKeyPath: String? = null,
    val knownHostsPath: String? = null,
    val strictHostKeyChecking: Boolean = true
)

interface SshClient {
    suspend fun connect(target: SshTarget): SshSession
}

interface SshSession {
    val id: String
    suspend fun exec(command: String, timeoutMs: Long? = null): SshExecResult
    suspend fun close()
}

@Serializable
data class SshExecResult(
    val exitCode: Int,
    val stdout: String,
    val stderr: String,
    val durationMs: Long
)
