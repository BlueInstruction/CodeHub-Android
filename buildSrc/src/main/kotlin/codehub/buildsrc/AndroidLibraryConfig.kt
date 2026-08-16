import org.gradle.api.JavaVersion
import org.gradle.api.Project
import com.android.build.gradle.LibraryExtension

fun Project.androidLibraryConfig(
    namespace: String,
    extraBody: (LibraryExtension.() -> Unit)? = null
) {
    extensions.configure(LibraryExtension::class.java) {
        this.namespace = namespace
        compileSdk = 35
        defaultConfig {
            minSdk = 29
            consumerProguardFiles("consumer-rules.pro")
        }
        compileOptions {
            sourceCompatibility = JavaVersion.VERSION_17
            targetCompatibility = JavaVersion.VERSION_17
        }
        buildFeatures { buildConfig = true }
        testOptions {
            unitTests.isReturnDefaultValues = true
            unitTests.isIncludeAndroidResources = true
        }
        extraBody?.invoke(this)
    }
}
