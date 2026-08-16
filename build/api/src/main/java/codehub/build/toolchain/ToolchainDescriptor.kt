package codehub.build.toolchain

import kotlinx.serialization.Serializable

@Serializable
enum class ToolchainComponent {
    Jdk,
    AndroidSdk,
    PlatformTools,
    BuildTools,
    PlatformSdk,
    Ndk,
    Cmake,
    Ninja,
    Clang,
    Git,
    Adb,
    Gradle
}

@Serializable
enum class ToolchainSource {
    Termux,
    AndroidSdk,
    System,
    CodeHub,
    Manual,
    NotFound
}

@Serializable
data class ToolchainDescriptor(
    val component: ToolchainComponent,
    val version: String?,
    val path: String?,
    val source: ToolchainSource,
    val compatible: Boolean,
    val issues: List<String> = emptyList()
) {
    val installed: Boolean get() = source != ToolchainSource.NotFound && path != null
}

@Serializable
data class ToolchainReadiness(
    val components: List<ToolchainDescriptor>,
    val environment: Map<String, String>,
    val missing: List<ToolchainComponent>,
    val incompatible: List<ToolchainDescriptor>,
    val ready: Boolean,
    val issues: List<String>
) {
    val jdk: ToolchainDescriptor? get() = components.firstOrNull { it.component == ToolchainComponent.Jdk }
    val androidSdk: ToolchainDescriptor? get() = components.firstOrNull { it.component == ToolchainComponent.AndroidSdk }
    val ndk: ToolchainDescriptor? get() = components.firstOrNull { it.component == ToolchainComponent.Ndk }
    val cmake: ToolchainDescriptor? get() = components.firstOrNull { it.component == ToolchainComponent.Cmake }
    val ninja: ToolchainDescriptor? get() = components.firstOrNull { it.component == ToolchainComponent.Ninja }
    val clang: ToolchainDescriptor? get() = components.firstOrNull { it.component == ToolchainComponent.Clang }
    val git: ToolchainDescriptor? get() = components.firstOrNull { it.component == ToolchainComponent.Git }
    val gradle: ToolchainDescriptor? get() = components.firstOrNull { it.component == ToolchainComponent.Gradle }
}
