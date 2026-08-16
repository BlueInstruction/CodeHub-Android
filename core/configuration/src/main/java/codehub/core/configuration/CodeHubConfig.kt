package codehub.core.configuration

import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.Serializable

@Serializable
data class CodeHubConfig(
    val workspaceRootPath: String,
    val cacheDirPath: String,
    val terminal: TerminalConfig = TerminalConfig(),
    val editor: EditorConfig = EditorConfig(),
    val build: BuildConfig = BuildConfig(),
    val ai: AiConfig = AiConfig(),
    val device: DeviceConfig = DeviceConfig()
)

@Serializable
data class TerminalConfig(
    val backend: String = "termux",
    val historyLimit: Int = 1000,
    val scrollbackLines: Int = 4000,
    val bellEnabled: Boolean = false,
    val defaultShell: String? = null
)

@Serializable
data class EditorConfig(
    val backend: String = "code-server",
    val port: Int = 8443,
    val autoStart: Boolean = false,
    val userSettingsJson: String? = null,
    val fallbackToVscodeDev: Boolean = false
)

@Serializable
data class BuildConfig(
    val parallelJobs: Int = Runtime.getRuntime().availableProcessors(),
    val warnAsError: Boolean = false,
    val ccacheEnabled: Boolean = false,
    val defaultToolchain: String? = null
)

@Serializable
data class AiConfig(
    val defaultProvider: String = "offline",
    val streamingEnabled: Boolean = true,
    val contextMaxTokens: Int = 8_000,
    val logRequests: Boolean = false,
    val auditAgentActions: Boolean = true
)

@Serializable
data class DeviceConfig(
    val thermalMonitoring: Boolean = true,
    val pollIntervalMs: Long = 2_000L,
    val lowBatteryThreshold: Int = 15,
    val thermalThrottleThreshold: Float = 0.85f
)

interface ConfigurationStore {
    fun observe(): Flow<CodeHubConfig>
    suspend fun current(): CodeHubConfig
    suspend fun update(transform: (CodeHubConfig) -> CodeHubConfig)
    suspend fun reset()
}
