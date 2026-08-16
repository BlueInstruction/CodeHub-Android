package codehub.build.signing

import codehub.build.toolchain.ToolchainManager
import codehub.core.diagnostics.DiagnosticEvent
import codehub.core.diagnostics.DiagnosticEventKind
import codehub.core.diagnostics.DiagnosticSeverity
import codehub.core.diagnostics.DiagnosticSink
import codehub.core.diagnostics.DiagnosticStatus
import codehub.core.process.ProcessRunner
import codehub.core.process.ProcessSpec
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DebugKeystoreGenerator @Inject constructor(
    private val processRunner: ProcessRunner,
    private val diagnostics: DiagnosticSink,
    private val toolchainManager: ToolchainManager
) {

    private val androidDir = File(System.getProperty("user.home"), ".android")

    suspend fun ensureDebugKeystore(): KeystoreResult {
        val keystoreFile = File(androidDir, "debug.keystore")
        if (keystoreFile.exists() && keystoreFile.length() > 0) {
            return KeystoreResult(
                config = SigningConfig.debug(keystoreFile.absolutePath),
                created = false,
                existed = true,
                success = true,
                message = "Debug keystore already exists at ${keystoreFile.absolutePath}"
            )
        }
        return generate(keystoreFile, SigningConfig.debug(keystoreFile.absolutePath))
    }

    suspend fun generateReleaseKeystore(
        storeFile: File,
        storePassword: String,
        keyAlias: String,
        keyPassword: String,
        organization: String = "CodeHub"
    ): KeystoreResult {
        val config = SigningConfig(
            storeFile = storeFile.absolutePath,
            storePassword = storePassword,
            keyAlias = keyAlias,
            keyPassword = keyPassword
        )
        return generate(storeFile, config, organization)
    }

    private suspend fun generate(
        keystoreFile: File,
        config: SigningConfig,
        organization: String = "CodeHub"
    ): KeystoreResult {
        keystoreFile.parentFile?.mkdirs()
        val keytool = resolveKeytool()
        if (keytool == null) {
            val msg = "keytool not found. JDK must be installed to generate a keystore."
            diagnostics.emit(
                DiagnosticEvent.now(
                    kind = DiagnosticEventKind.RuntimeInitialization,
                    severity = DiagnosticSeverity.Error,
                    status = DiagnosticStatus.Failed,
                    source = "DebugKeystoreGenerator",
                    message = msg
                )
            )
            return KeystoreResult(
                config = config,
                created = false,
                existed = false,
                success = false,
                message = msg
            )
        }

        val command = listOf(
            keytool,
            "-genkeypair",
            "-v",
            "-keystore", keystoreFile.absolutePath,
            "-storepass", config.storePassword,
            "-alias", config.keyAlias,
            "-keypass", config.keyPassword,
            "-keyalg", config.keyAlgorithm,
            "-keysize", config.keySize.toString(),
            "-validity", config.validityDays.toString(),
            "-dname", "CN=$organization, OU=CodeHub, O=CodeHub, L=Unknown, ST=Unknown, C=US"
        )

        diagnostics.emit(
            DiagnosticEvent.now(
                kind = DiagnosticEventKind.RuntimeInitialization,
                severity = DiagnosticSeverity.Info,
                status = DiagnosticStatus.Pending,
                source = "DebugKeystoreGenerator",
                message = "Generating keystore at ${keystoreFile.absolutePath} via $keytool"
            )
        )

        val spec = ProcessSpec(
            command = command,
            workingDirectory = keystoreFile.parentFile?.absolutePath ?: "/",
            environment = emptyMap(),
            timeoutMs = 60_000
        )
        val result = processRunner.run(spec)
        val success = result.exitCode == 0 && keystoreFile.exists() && keystoreFile.length() > 0

        diagnostics.emit(
            DiagnosticEvent.now(
                kind = DiagnosticEventKind.RuntimeInitialization,
                severity = if (success) DiagnosticSeverity.Info else DiagnosticSeverity.Error,
                status = if (success) DiagnosticStatus.Ok else DiagnosticStatus.Failed,
                source = "DebugKeystoreGenerator",
                message = if (success) "Keystore generated at ${keystoreFile.absolutePath}"
                    else "Keystore generation failed: ${result.stderr.take(300)}",
                attributes = mapOf("exit_code" to result.exitCode.toString())
            )
        )

        return KeystoreResult(
            config = config,
            created = success,
            existed = false,
            success = success,
            message = if (success) "Keystore generated at ${keystoreFile.absolutePath}"
                else "Failed: ${result.stderr.take(300)}"
        )
    }

    fun signingProperties(config: SigningConfig): Map<String, String> = mapOf(
        "android.injected.build.api" to "35",
        "android.injected.signing.store.file" to config.storeFile,
        "android.injected.signing.store.password" to config.storePassword,
        "android.injected.signing.key.alias" to config.keyAlias,
        "android.injected.signing.key.password" to config.keyPassword
    )

    suspend fun verifyKeystore(config: SigningConfig): Boolean {
        val keytool = resolveKeytool() ?: return false
        val spec = ProcessSpec(
            command = listOf(
                keytool,
                "-list",
                "-v",
                "-keystore", config.storeFile,
                "-storepass", config.storePassword,
                "-alias", config.keyAlias,
                "-keypass", config.keyPassword
            ),
            workingDirectory = "/",
            environment = emptyMap(),
            timeoutMs = 10_000
        )
        val result = processRunner.run(spec)
        return result.exitCode == 0
    }

    private suspend fun resolveKeytool(): String? {
        val readiness = toolchainManager.probe()
        val jdk = readiness.components.firstOrNull { it.component == codehub.build.toolchain.ToolchainComponent.Jdk }
        if (jdk?.path != null) {
            val keytool = File(jdk.path, "bin/keytool")
            if (keytool.exists() && keytool.canExecute()) return keytool.absolutePath
        }
        val whichResult = processRunner.run(
            ProcessSpec(
                command = listOf("which", "keytool"),
                workingDirectory = "/",
                environment = emptyMap(),
                timeoutMs = 2_000
            )
        )
        if (whichResult.exitCode == 0 && whichResult.stdout.isNotBlank()) {
            return whichResult.stdout.trim().lines().first()
        }
        return null
    }
}
