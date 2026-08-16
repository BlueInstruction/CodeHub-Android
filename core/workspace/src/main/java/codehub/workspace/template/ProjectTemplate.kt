package codehub.workspace.template

import kotlinx.serialization.Serializable

@Serializable
enum class ProjectTemplateKind {
    EmptyCompose,
    BasicViews,
    NativeActivity,
    AndroidLibrary
}

@Serializable
data class ProjectTemplate(
    val kind: ProjectTemplateKind,
    val displayName: String,
    val description: String,
    val minSdk: Int,
    val targetSdk: Int,
    val usesCompose: Boolean,
    val usesNdk: Boolean,
    val isLibrary: Boolean,
    val kotlinVersion: String,
    val agpVersion: String,
    val gradleVersion: String,
    val jdkVersion: String
)

object ProjectTemplateRegistry {

    val templates: List<ProjectTemplate> = listOf(
        ProjectTemplate(
            kind = ProjectTemplateKind.EmptyCompose,
            displayName = "Empty Compose Activity",
            description = "A single Activity with Jetpack Compose, Material 3, and a simple greeting. The simplest starting point for modern Android apps.",
            minSdk = 24,
            targetSdk = 35,
            usesCompose = true,
            usesNdk = false,
            isLibrary = false,
            kotlinVersion = "2.0.21",
            agpVersion = "8.7.3",
            gradleVersion = "8.10.2",
            jdkVersion = "17"
        ),
        ProjectTemplate(
            kind = ProjectTemplateKind.BasicViews,
            displayName = "Basic Views Activity",
            description = "A single Activity with an XML layout, TextView, and Button. Use this if you're working with the classic Android View system.",
            minSdk = 24,
            targetSdk = 35,
            usesCompose = false,
            usesNdk = false,
            isLibrary = false,
            kotlinVersion = "2.0.21",
            agpVersion = "8.7.3",
            gradleVersion = "8.10.2",
            jdkVersion = "17"
        ),
        ProjectTemplate(
            kind = ProjectTemplateKind.NativeActivity,
            displayName = "Native Activity (NDK)",
            description = "A NativeActivity with C++ via JNI, CMake build, and Vulkan-ready structure. For native/NDK development including emulators and game engines.",
            minSdk = 24,
            targetSdk = 35,
            usesCompose = false,
            usesNdk = true,
            isLibrary = false,
            kotlinVersion = "2.0.21",
            agpVersion = "8.7.3",
            gradleVersion = "8.10.2",
            jdkVersion = "17"
        ),
        ProjectTemplate(
            kind = ProjectTemplateKind.AndroidLibrary,
            displayName = "Android Library",
            description = "An Android library module skeleton with a public API, resources, and Manifest. Produces an AAR.",
            minSdk = 24,
            targetSdk = 35,
            usesCompose = false,
            usesNdk = false,
            isLibrary = true,
            kotlinVersion = "2.0.21",
            agpVersion = "8.7.3",
            gradleVersion = "8.10.2",
            jdkVersion = "17"
        )
    )

    fun get(kind: ProjectTemplateKind): ProjectTemplate =
        templates.first { it.kind == kind }
}
