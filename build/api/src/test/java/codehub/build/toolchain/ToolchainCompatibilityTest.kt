package codehub.build.toolchain

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class ToolchainCompatibilityTest {

    @Test
    fun `JDK 17 is compatible`() {
        assertThat(ToolchainCompatibility.isCompatible(ToolchainComponent.Jdk, "17.0.13")).isTrue()
    }

    @Test
    fun `JDK 11 is not compatible`() {
        assertThat(ToolchainCompatibility.isCompatible(ToolchainComponent.Jdk, "11.0.21")).isFalse()
    }

    @Test
    fun `JDK 21 is compatible`() {
        assertThat(ToolchainCompatibility.isCompatible(ToolchainComponent.Jdk, "21.0.5")).isTrue()
    }

    @Test
    fun `JDK 22 is not compatible`() {
        assertThat(ToolchainCompatibility.isCompatible(ToolchainComponent.Jdk, "22.0.1")).isFalse()
    }

    @Test
    fun `NDK 27 is compatible`() {
        assertThat(ToolchainCompatibility.isCompatible(ToolchainComponent.Ndk, "27.0.12077973")).isTrue()
    }

    @Test
    fun `NDK 25 is compatible (minimum)`() {
        assertThat(ToolchainCompatibility.isCompatible(ToolchainComponent.Ndk, "25.1.8937393")).isTrue()
    }

    @Test
    fun `NDK 24 is not compatible (below minimum)`() {
        assertThat(ToolchainCompatibility.isCompatible(ToolchainComponent.Ndk, "24.0.8215888")).isFalse()
    }

    @Test
    fun `NDK 29 is not compatible (above maximum)`() {
        assertThat(ToolchainCompatibility.isCompatible(ToolchainComponent.Ndk, "29.0.1234567")).isFalse()
    }

    @Test
    fun `CMake 3 31 is compatible`() {
        assertThat(ToolchainCompatibility.isCompatible(ToolchainComponent.Cmake, "3.31.6")).isTrue()
    }

    @Test
    fun `CMake 3 22 is compatible (minimum)`() {
        assertThat(ToolchainCompatibility.isCompatible(ToolchainComponent.Cmake, "3.22.1")).isTrue()
    }

    @Test
    fun `CMake 3 20 is not compatible`() {
        assertThat(ToolchainCompatibility.isCompatible(ToolchainComponent.Cmake, "3.20.0")).isFalse()
    }

    @Test
    fun `Gradle 8 10 is compatible`() {
        assertThat(ToolchainCompatibility.isCompatible(ToolchainComponent.Gradle, "8.10.2")).isTrue()
    }

    @Test
    fun `Gradle 8 8 is not compatible (below min 8 9)`() {
        assertThat(ToolchainCompatibility.isCompatible(ToolchainComponent.Gradle, "8.8.0")).isFalse()
    }

    @Test
    fun `Gradle 8 12 is not compatible (above max 8 11)`() {
        assertThat(ToolchainCompatibility.isCompatible(ToolchainComponent.Gradle, "8.12.0")).isFalse()
    }

    @Test
    fun `null version is not compatible`() {
        assertThat(ToolchainCompatibility.isCompatible(ToolchainComponent.Jdk, null)).isFalse()
    }

    @Test
    fun `blank version is not compatible`() {
        assertThat(ToolchainCompatibility.isCompatible(ToolchainComponent.Jdk, "")).isFalse()
    }

    @Test
    fun `describeIncompatibility for missing component`() {
        val desc = ToolchainCompatibility.describeIncompatibility(ToolchainComponent.Jdk, null)
        assertThat(desc).contains("not installed")
        assertThat(desc).contains("17")
    }

    @Test
    fun `describeIncompatibility for old version`() {
        val desc = ToolchainCompatibility.describeIncompatibility(ToolchainComponent.Jdk, "11.0.21")
        assertThat(desc).contains("too old")
        assertThat(desc).contains("Minimum: 17")
    }

    @Test
    fun `describeIncompatibility for too new version`() {
        val desc = ToolchainCompatibility.describeIncompatibility(ToolchainComponent.Ndk, "29.0.1234567")
        assertThat(desc).contains("too new")
        assertThat(desc).contains("Maximum: 28")
    }

    @Test
    fun `describeIncompatibility for compatible version is empty`() {
        val desc = ToolchainCompatibility.describeIncompatibility(ToolchainComponent.Jdk, "17.0.13")
        assertThat(desc).isEmpty()
    }

    @Test
    fun `compareVersions handles semver correctly`() {
        assertThat(ToolchainCompatibility.compareVersions("3.31.6", "3.22.1")).isGreaterThan(0)
        assertThat(ToolchainCompatibility.compareVersions("3.22.1", "3.31.6")).isLessThan(0)
        assertThat(ToolchainCompatibility.compareVersions("3.31.6", "3.31.6")).isEqualTo(0)
    }

    @Test
    fun `compareVersions handles different segment counts`() {
        assertThat(ToolchainCompatibility.compareVersions("17", "17.0.1")).isLessThan(0)
        assertThat(ToolchainCompatibility.compareVersions("17.0.1", "17")).isGreaterThan(0)
    }

    @Test
    fun `compareVersions handles NDK r-style versions`() {
        assertThat(ToolchainCompatibility.compareVersions("27.0.12077973", "25.1.8937393")).isGreaterThan(0)
    }

    @Test
    fun `AGP 8 7 requires Gradle 8 9 and JDK 17`() {
        val (gradle, jdkMin, jdkMax) = ToolchainCompatibility.agpGradleJdkMatrix("8.7.3")
        assertThat(gradle).isEqualTo("8.9")
        assertThat(jdkMin).isEqualTo("17")
        assertThat(jdkMax).isEqualTo("21")
    }

    @Test
    fun `AGP 8 5 requires Gradle 8 7`() {
        val (gradle, _, _) = ToolchainCompatibility.agpGradleJdkMatrix("8.5.2")
        assertThat(gradle).isEqualTo("8.7")
    }

    @Test
    fun `AGP 8 0 requires Gradle 8 0`() {
        val (gradle, _, _) = ToolchainCompatibility.agpGradleJdkMatrix("8.0.2")
        assertThat(gradle).isEqualTo("8.0")
    }

    @Test
    fun `AGP below 8 0 requires Gradle 7 4 and JDK 11`() {
        val (gradle, jdkMin, _) = ToolchainCompatibility.agpGradleJdkMatrix("7.4.2")
        assertThat(gradle).isEqualTo("7.4")
        assertThat(jdkMin).isEqualTo("11")
    }
}
