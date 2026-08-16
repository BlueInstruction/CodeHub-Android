package codehub.workspace.template

import kotlinx.serialization.Serializable

@Serializable
data class ProjectGenerationRequest(
    val template: ProjectTemplate,
    val projectPath: String,
    val packageName: String,
    val displayName: String,
    val minSdk: Int = template.minSdk,
    val targetSdk: Int = template.targetSdk,
    val compileSdk: Int = 35,
    val versionCode: Int = 1,
    val versionName: String = "0.1.0"
) {
    init {
        require(packageName.matches(Regex("""^[a-z][a-z0-9]*(\.[a-z][a-z0-9]*)*$"""))) {
            "Package name must be lowercase, dot-separated, and start with a letter. Got: $packageName"
        }
        require(displayName.isNotBlank()) {
            "Display name must not be blank"
        }
        require(minSdk <= targetSdk) {
            "minSdk ($minSdk) must be <= targetSdk ($targetSdk)"
        }
    }
}

@Serializable
data class ProjectGenerationResult(
    val request: ProjectGenerationRequest,
    val projectPath: String,
    val filesWritten: List<String>,
    val mainActivityClass: String,
    val applicationId: String,
    val success: Boolean,
    val errors: List<String> = emptyList()
)
