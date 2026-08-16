package codehub.core.workspace

import com.google.common.truth.Truth.assertThat
import codehub.core.workspace.model.BuildSystem
import codehub.core.workspace.model.ProjectLanguage
import codehub.core.workspace.model.VcsKind
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class ProjectInferrerTest {

    @get:Rule val tmp = TemporaryFolder()

    private val inferrer = ProjectInferrer()

    @Test
    fun `empty directory returns Unknown language and build system`() {
        val root = tmp.newFolder("empty")
        val meta = inferrer.infer(root)
        assertThat(meta.languages).containsExactly(ProjectLanguage.Unknown)
        assertThat(meta.buildSystems).containsExactly(BuildSystem.Unknown)
        assertThat(meta.vcs).isEqualTo(VcsKind.None)
    }

    @Test
    fun `Gradle Kotlin project is detected`() {
        val root = tmp.newFolder("gradle-kt")
        File(root, "build.gradle.kts").writeText("plugins { kotlin(\"jvm\") }")
        File(root, "settings.gradle.kts").writeText("rootProject.name = \"test\"")
        File(root, ".git").mkdirs()
        val meta = inferrer.infer(root)
        assertThat(meta.buildSystems).contains(BuildSystem.Gradle)
        assertThat(meta.languages).contains(ProjectLanguage.Kotlin)
        assertThat(meta.vcs).isEqualTo(VcsKind.Git)
        assertThat(meta.properties["gradle.variant"]).isEqualTo("kotlin")
    }

    @Test
    fun `CMake project is detected`() {
        val root = tmp.newFolder("cmake")
        File(root, "CMakeLists.txt").writeText("cmake_minimum_required(VERSION 3.20)")
        File(root, "src").mkdirs()
        File(root, "src/main.cpp").writeText("int main(){}")
        val meta = inferrer.infer(root)
        assertThat(meta.buildSystems).contains(BuildSystem.CMake)
        assertThat(meta.languages).contains(ProjectLanguage.Cpp)
    }

    @Test
    fun `Cargo project is detected`() {
        val root = tmp.newFolder("rust")
        File(root, "Cargo.toml").writeText("[package]\nname=\"x\"")
        val meta = inferrer.infer(root)
        assertThat(meta.buildSystems).contains(BuildSystem.Cargo)
        assertThat(meta.languages).contains(ProjectLanguage.Rust)
    }
}
