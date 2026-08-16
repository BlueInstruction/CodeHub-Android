package codehub.build.toolchain

object ToolchainCompatibility {

    data class VersionRequirement(
        val component: ToolchainComponent,
        val minVersion: String,
        val maxVersion: String? = null,
        val recommendedVersion: String? = null
    )

    val requirements: Map<ToolchainComponent, VersionRequirement> = mapOf(
        ToolchainComponent.Jdk to VersionRequirement(
            component = ToolchainComponent.Jdk,
            minVersion = "17",
            maxVersion = "21.999",
            recommendedVersion = "17"
        ),
        ToolchainComponent.AndroidSdk to VersionRequirement(
            component = ToolchainComponent.AndroidSdk,
            minVersion = "35",
            recommendedVersion = "35"
        ),
        ToolchainComponent.BuildTools to VersionRequirement(
            component = ToolchainComponent.BuildTools,
            minVersion = "35.0.0",
            recommendedVersion = "35.0.0"
        ),
        ToolchainComponent.PlatformSdk to VersionRequirement(
            component = ToolchainComponent.PlatformSdk,
            minVersion = "34",
            recommendedVersion = "35"
        ),
        ToolchainComponent.Ndk to VersionRequirement(
            component = ToolchainComponent.Ndk,
            minVersion = "25",
            maxVersion = "28",
            recommendedVersion = "27"
        ),
        ToolchainComponent.Cmake to VersionRequirement(
            component = ToolchainComponent.Cmake,
            minVersion = "3.22",
            recommendedVersion = "3.31"
        ),
        ToolchainComponent.Ninja to VersionRequirement(
            component = ToolchainComponent.Ninja,
            minVersion = "1.10",
            recommendedVersion = "1.12"
        ),
        ToolchainComponent.Clang to VersionRequirement(
            component = ToolchainComponent.Clang,
            minVersion = "14",
            recommendedVersion = "18"
        ),
        ToolchainComponent.Git to VersionRequirement(
            component = ToolchainComponent.Git,
            minVersion = "2.30",
            recommendedVersion = "2.47"
        ),
        ToolchainComponent.Adb to VersionRequirement(
            component = ToolchainComponent.Adb,
            minVersion = "1.0.41",
            recommendedVersion = "35.0.0"
        ),
        ToolchainComponent.Gradle to VersionRequirement(
            component = ToolchainComponent.Gradle,
            minVersion = "8.9",
            maxVersion = "8.11",
            recommendedVersion = "8.10.2"
        )
    )

    fun isCompatible(component: ToolchainComponent, version: String?): Boolean {
        if (version.isNullOrBlank()) return false
        val req = requirements[component] ?: return true
        return compareVersions(version, req.minVersion) >= 0 &&
            (req.maxVersion == null || compareVersions(version, req.maxVersion) <= 0)
    }

    fun describeIncompatibility(component: ToolchainComponent, version: String?): String {
        val req = requirements[component] ?: return ""
        if (version.isNullOrBlank()) {
            return "${component.name} is not installed. Recommended: ${req.recommendedVersion ?: req.minVersion}+"
        }
        return when {
            compareVersions(version, req.minVersion) < 0 ->
                "${component.name} $version is too old. Minimum: ${req.minVersion}${req.recommendedVersion?.let { ", recommended: $it" } ?: ""}"
            req.maxVersion != null && compareVersions(version, req.maxVersion) > 0 ->
                "${component.name} $version is too new. Maximum: ${req.maxVersion}"
            else -> ""
        }
    }

    fun compareVersions(a: String, b: String): Int {
        val partsA = a.split(Regex("[.\\-]")).filter { it.isNotEmpty() }
        val partsB = b.split(Regex("[.\\-]")).filter { it.isNotEmpty() }
        val max = maxOf(partsA.size, partsB.size)
        for (i in 0 until max) {
            val pa = partsA.getOrNull(i)?.toIntOrNull() ?: 0
            val pb = partsB.getOrNull(i)?.toIntOrNull() ?: 0
            if (pa != pb) return pa.compareTo(pb)
        }
        return 0
    }

    fun agpGradleJdkMatrix(agpVersion: String): Triple<String, String, String> {
        return when {
            compareVersions(agpVersion, "8.7") >= 0 -> Triple("8.9", "17", "21")
            compareVersions(agpVersion, "8.5") >= 0 -> Triple("8.7", "17", "21")
            compareVersions(agpVersion, "8.0") >= 0 -> Triple("8.0", "17", "20")
            else -> Triple("7.4", "11", "17")
        }
    }
}
