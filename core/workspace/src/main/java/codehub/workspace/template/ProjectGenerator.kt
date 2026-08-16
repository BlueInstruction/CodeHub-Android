package codehub.workspace.template

import codehub.core.diagnostics.DiagnosticEvent
import codehub.core.diagnostics.DiagnosticEventKind
import codehub.core.diagnostics.DiagnosticSeverity
import codehub.core.diagnostics.DiagnosticSink
import codehub.core.diagnostics.DiagnosticStatus
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ProjectGenerator @Inject constructor(
    private val diagnostics: DiagnosticSink,
    private val wrapperAssets: WrapperAssets
) {

    suspend fun generate(request: ProjectGenerationRequest): ProjectGenerationResult {
        val errors = mutableListOf<String>()
        val filesWritten = mutableListOf<String>()

        val projectDir = File(request.projectPath)
        if (projectDir.exists() && projectDir.listFiles()?.isNotEmpty() == true) {
            errors.add("Directory ${request.projectPath} is not empty")
            return ProjectGenerationResult(
                request = request,
                projectPath = request.projectPath,
                filesWritten = emptyList(),
                mainActivityClass = "",
                applicationId = request.packageName,
                success = false,
                errors = errors
            )
        }
        projectDir.mkdirs()

        val packagePath = request.packageName.replace('.', '/')
        val srcDir = File(projectDir, "app/src/main/java/$packagePath")
        srcDir.mkdirs()

        try {
            filesWritten += writeRootBuildGradle(projectDir, request.template)
            filesWritten += writeSettingsGradle(projectDir, request.displayName, request.packageName)
            filesWritten += writeVersionCatalog(projectDir, request.template)
            filesWritten += writeGradleWrapper(projectDir, request.template.gradleVersion)
            filesWritten += writeGradleWrapperJar(projectDir)
            filesWritten += writeGradleWrapperScript(projectDir)
            filesWritten += writeGradlewBat(projectDir)
            filesWritten += writeGradleProperties(projectDir)
            filesWritten += writeGitignore(projectDir)
            filesWritten += writeProguardRules(projectDir)

            filesWritten += writeAppBuildGradle(
                projectDir,
                request.packageName,
                request.template,
                request.minSdk,
                request.targetSdk,
                request.compileSdk,
                request.versionCode,
                request.versionName
            )
            filesWritten += writeAndroidManifest(
                projectDir,
                request.packageName,
                request.displayName,
                request.template,
                request.isLibrary()
            )
            filesWritten += writeMainActivity(projectDir, request.packageName, request.template)
            filesWritten += writeStrings(projectDir, request.displayName)
            filesWritten += writeColors(projectDir)
            filesWritten += writeThemes(projectDir, request.template)
            filesWritten += writeLauncherIcon(projectDir)
            filesWritten += writeLauncherForeground(projectDir)

            if (request.template.usesCompose) {
                filesWritten += writeComposeTheme(projectDir, request.packageName)
                filesWritten += writeComposePreview(projectDir, request.packageName)
            }

            if (request.template.usesNdk) {
                filesWritten += writeNativeSource(projectDir, request.packageName)
                filesWritten += writeCMakeLists(projectDir, request.packageName)
            }

            if (request.template.isLibrary) {
                filesWritten += writeConsumerProguardRules(projectDir)
            }

            filesWritten += writeReadme(projectDir, request.displayName, request.packageName, request.template)
        } catch (t: Throwable) {
            errors.add(t.message ?: "Unknown error during generation")
        }

        val success = errors.isEmpty()
        diagnostics.emit(
            DiagnosticEvent.now(
                kind = DiagnosticEventKind.RuntimeInitialization,
                severity = if (success) DiagnosticSeverity.Info else DiagnosticSeverity.Error,
                status = if (success) DiagnosticStatus.Ok else DiagnosticStatus.Failed,
                source = "ProjectGenerator",
                message = if (success) "Project generated at ${request.projectPath} (${filesWritten.size} files)"
                    else "Project generation failed: ${errors.joinToString("; ")}",
                attributes = mapOf(
                    "template" to request.template.kind.name,
                    "package" to request.packageName,
                    "files" to filesWritten.size.toString()
                )
            )
        )

        return ProjectGenerationResult(
            request = request,
            projectPath = request.projectPath,
            filesWritten = filesWritten,
            mainActivityClass = "${request.packageName}.MainActivity",
            applicationId = request.packageName,
            success = success,
            errors = errors
        )
    }

    private fun ProjectGenerationRequest.isLibrary(): Boolean = template.isLibrary

    private fun writeRootBuildGradle(projectDir: File, template: ProjectTemplate): String {
        val file = File(projectDir, "build.gradle.kts")
        file.writeText(
            """plugins {
    alias(libs.plugins.android.application) apply false
    ${if (template.isLibrary) "alias(libs.plugins.android.library) apply false" else ""}
    alias(libs.plugins.kotlin.android) apply false
    ${if (template.usesCompose) "alias(libs.plugins.compose.compiler) apply false" else ""}
}
"""
        )
        return file.absolutePath
    }

    private fun writeSettingsGradle(projectDir: File, projectName: String, packageName: String): String {
        val file = File(projectDir, "settings.gradle.kts")
        file.writeText(
            """pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "$projectName"
include(":app")
"""
        )
        return file.absolutePath
    }

    private fun writeVersionCatalog(projectDir: File, template: ProjectTemplate): String {
        val file = File(projectDir, "gradle/libs.versions.toml")
        file.writeText(
            """[versions]
agp = "${template.agpVersion}"
kotlin = "${template.kotlinVersion}"
coreKtx = "1.15.0"
lifecycle = "2.8.7"
activityCompose = "1.9.3"
composeBom = "2024.12.01"
junit = "4.13.2"
junitExt = "1.2.1"
espresso = "3.6.1"

[libraries]
androidx-core-ktx = { group = "androidx.core", name = "core-ktx", version.ref = "coreKtx" }
androidx-lifecycle-runtime-ktx = { group = "androidx.lifecycle", name = "lifecycle-runtime-ktx", version.ref = "lifecycle" }
androidx-activity-compose = { group = "androidx.activity", name = "activity-compose", version.ref = "activityCompose" }
androidx-compose-bom = { group = "androidx.compose", name = "compose-bom", version.ref = "composeBom" }
androidx-compose-ui = { group = "androidx.compose.ui", name = "ui" }
androidx-compose-ui-tooling = { group = "androidx.compose.ui", name = "ui-tooling" }
androidx-compose-ui-tooling-preview = { group = "androidx.compose.ui", name = "ui-tooling-preview" }
androidx-compose-material3 = { group = "androidx.compose.material3", name = "material3" }
junit = { group = "junit", name = "junit", version.ref = "junit" }
androidx-junit = { group = "androidx.test.ext", name = "junit", version.ref = "junitExt" }
androidx-espresso-core = { group = "androidx.test.espresso", name = "espresso-core", version.ref = "espresso" }

[plugins]
android-application = { id = "com.android.application", version.ref = "agp" }
android-library = { id = "com.android.library", version.ref = "agp" }
kotlin-android = { id = "org.jetbrains.kotlin.android", version.ref = "kotlin" }
compose-compiler = { id = "org.jetbrains.kotlin.plugin.compose", version.ref = "kotlin" }
"""
        )
        return file.absolutePath
    }

    private fun writeGradleWrapper(projectDir: File, gradleVersion: String): String {
        val wrapperDir = File(projectDir, "gradle/wrapper")
        wrapperDir.mkdirs()
        val file = File(wrapperDir, "gradle-wrapper.properties")
        file.writeText(
            """distributionBase=GRADLE_USER_HOME
distributionPath=wrapper/dists
distributionUrl=https\\://services.gradle.org/distributions/gradle-$gradleVersion-bin.zip
networkTimeout=10000
validateDistributionUrl=true
zipStoreBase=GRADLE_USER_HOME
zipStorePath=wrapper/dists
"""
        )
        return file.absolutePath
    }

    private fun writeGradleWrapperJar(projectDir: File): String {
        val wrapperDir = File(projectDir, "gradle/wrapper")
        wrapperDir.mkdirs()
        val file = File(wrapperDir, "gradle-wrapper.jar")
        file.writeBytes(wrapperAssets.gradleWrapperJar())
        return file.absolutePath
    }

    private fun writeGradleWrapperScript(projectDir: File): String {
        val file = File(projectDir, "gradlew")
        file.writeBytes(wrapperAssets.gradlewScript())
        file.setExecutable(true)
        return file.absolutePath
    }

    private fun writeGradlewBat(projectDir: File): String {
        val file = File(projectDir, "gradlew.bat")
        file.writeBytes(wrapperAssets.gradlewBatScript())
        return file.absolutePath
    }

    private fun writeGradleProperties(projectDir: File): String {
        val file = File(projectDir, "gradle.properties")
        file.writeText(
            """org.gradle.jvmargs=-Xmx4g -Dfile.encoding=UTF-8 -XX:+UseParallelGC
org.gradle.parallel=true
org.gradle.caching=true
android.useAndroidX=true
android.nonTransitiveRClass=true
kotlin.code.style=official
"""
        )
        return file.absolutePath
    }

    private fun writeGitignore(projectDir: File): String {
        val file = File(projectDir, ".gitignore")
        file.writeText(
            """*.iml
.gradle
/local.properties
/.idea
.DS_Store
/build
/app/build
/captures
.externalNativeBuild
.cxx
local.properties
*.apk
*.aab
"""
        )
        return file.absolutePath
    }

    private fun writeProguardRules(projectDir: File): String {
        val file = File(projectDir, "app/proguard-rules.pro")
        file.parentFile?.mkdirs()
        file.writeText("# Add project-specific ProGuard rules here.\n")
        return file.absolutePath
    }

    private fun writeAppBuildGradle(
        projectDir: File,
        packageName: String,
        template: ProjectTemplate,
        minSdk: Int,
        targetSdk: Int,
        compileSdk: Int,
        versionCode: Int,
        versionName: String
    ): String {
        val file = File(projectDir, "app/build.gradle.kts")
        file.parentFile?.mkdirs()
        val plugins = buildString {
            appendLine("plugins {")
            appendLine("    alias(libs.plugins.${if (template.isLibrary) "android.library" else "android.application"})")
            appendLine("    alias(libs.plugins.kotlin.android)")
            if (template.usesCompose) appendLine("    alias(libs.plugins.compose.compiler)")
            appendLine("}")
        }
        val android = buildString {
            appendLine("android {")
            appendLine("    namespace = \"$packageName\"")
            appendLine("    compileSdk = $compileSdk")
            appendLine()
            appendLine("    defaultConfig {")
            if (!template.isLibrary) {
                appendLine("        applicationId = \"$packageName\"")
            }
            appendLine("        minSdk = $minSdk")
            appendLine("        targetSdk = $targetSdk")
            appendLine("        versionCode = $versionCode")
            appendLine("        versionName = \"$versionName\"")
            if (template.usesNdk) {
                appendLine("        ndk {")
                appendLine("            abiFilters += listOf(\"arm64-v8a\", \"x86_64\")")
                appendLine("        }")
            }
            appendLine("        testInstrumentationRunner = \"androidx.test.runner.AndroidJUnitRunner\"")
            appendLine("    }")
            appendLine()
            appendLine("    buildTypes {")
            appendLine("        release {")
            appendLine("            isMinifyEnabled = false")
            appendLine("            proguardFiles(")
            appendLine("                getDefaultProguardFile(\"proguard-android-optimize.txt\"),")
            appendLine("                \"proguard-rules.pro\"")
            appendLine("            )")
            appendLine("        }")
            appendLine("    }")
            appendLine()
            appendLine("    compileOptions {")
            appendLine("        sourceCompatibility = JavaVersion.VERSION_17")
            appendLine("        targetCompatibility = JavaVersion.VERSION_17")
            appendLine("    }")
            appendLine()
            appendLine("    kotlinOptions {")
            appendLine("        jvmTarget = \"17\"")
            appendLine("    }")
            if (template.usesCompose) {
                appendLine()
                appendLine("    buildFeatures {")
                appendLine("        compose = true")
                appendLine("    }")
            }
            if (template.usesNdk) {
                appendLine()
                appendLine("    externalNativeBuild {")
                appendLine("        cmake {")
                appendLine("            path = file(\"src/main/cpp/CMakeLists.txt\")")
                appendLine("            version = \"3.31.6\"")
                appendLine("        }")
                appendLine("    }")
            }
            appendLine("}")
        }
        val deps = buildString {
            appendLine("dependencies {")
            appendLine("    implementation(libs.androidx.core.ktx)")
            appendLine("    implementation(libs.androidx.lifecycle.runtime.ktx)")
            if (template.usesCompose) {
                appendLine("    implementation(platform(libs.androidx.compose.bom))")
                appendLine("    implementation(libs.androidx.activity.compose)")
                appendLine("    implementation(libs.androidx.compose.ui)")
                appendLine("    implementation(libs.androidx.compose.ui.tooling.preview)")
                appendLine("    implementation(libs.androidx.compose.material3)")
                appendLine("    debugImplementation(libs.androidx.compose.ui.tooling)")
            }
            appendLine("    testImplementation(libs.junit)")
            appendLine("    androidTestImplementation(libs.androidx.junit)")
            appendLine("    androidTestImplementation(libs.androidx.espresso.core)")
            appendLine("}")
        }
        file.writeText("$plugins\n$android\n$deps\n")
        return file.absolutePath
    }

    private fun writeAndroidManifest(
        projectDir: File,
        packageName: String,
        displayName: String,
        template: ProjectTemplate,
        isLibrary: Boolean
    ): String {
        val file = File(projectDir, "app/src/main/AndroidManifest.xml")
        file.parentFile?.mkdirs()
        val labelAttr = if (isLibrary) "" else "android:label=\"$displayName\""
        val activityBlock = if (isLibrary) "" else """
        <activity
            android:name=".MainActivity"
            android:exported="true"
            android:label="$displayName">
            <intent-filter>
                <action android:name="android.intent.action.MAIN" />
                <category android:name="android.intent.category.LAUNCHER" />
            </intent-filter>
        </activity>"""
        file.writeText(
            """<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="http://schemas.android.com/apk/res/android">

    <application
        android:allowBackup="true"
        android:icon="@mipmap/ic_launcher"
        android:label="@string/app_name"
        android:roundIcon="@mipmap/ic_launcher_round"
        android:supportsRtl="true"
        android:theme="@style/Theme.${displayName.replace(" ", "")}">$activityBlock
    </application>

</manifest>
"""
        )
        return file.absolutePath
    }

    private fun writeMainActivity(projectDir: File, packageName: String, template: ProjectTemplate): String {
        val file = File(projectDir, "app/src/main/java/${packageName.replace('.', '/')}/MainActivity.kt")
        file.parentFile?.mkdirs()
        val content = when (template.kind) {
            ProjectTemplateKind.EmptyCompose -> """package $packageName

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    Surface(
                        modifier = Modifier.fillMaxSize().padding(innerPadding)
                    ) {
                        Greeting(name = "CodeHub")
                    }
                }
            }
        }
    }
}

@Composable
fun Greeting(name: String, modifier: Modifier = Modifier) {
    Text(
        text = "Hello, ${'$'}name!",
        modifier = modifier.padding(24.dp)
    )
}
"""
            ProjectTemplateKind.BasicViews -> """package $packageName

import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        val textView = findViewById<TextView>(R.id.textView)
        val button = findViewById<Button>(R.id.button)
        var count = 0
        button.setOnClickListener {
            count++
            textView.text = "Clicked ${'$'}count times"
        }
    }
}
"""
            ProjectTemplateKind.NativeActivity -> """package $packageName

import android.app.NativeActivity
import android.os.Bundle

class MainActivity : NativeActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
    }

    companion object {
        init {
            System.loadLibrary("${packageName.substringAfterLast('.')}")
        }
    }
}
"""
            ProjectTemplateKind.AndroidLibrary -> """package $packageName

object Library {
    const val VERSION = "0.1.0"
    fun greet(name: String): String = "Hello from $packageName, ${'$'}name!"
}
"""
        }
        file.writeText(content)
        return file.absolutePath
    }

    private fun writeStrings(projectDir: File, displayName: String): String {
        val file = File(projectDir, "app/src/main/res/values/strings.xml")
        file.parentFile?.mkdirs()
        file.writeText(
            """<resources>
    <string name="app_name">$displayName</string>
</resources>
"""
        )
        return file.absolutePath
    }

    private fun writeColors(projectDir: File): String {
        val file = File(projectDir, "app/src/main/res/values/colors.xml")
        file.parentFile?.mkdirs()
        file.writeText(
            """<resources>
    <color name="black">#FF000000</color>
    <color name="white">#FFFFFFFF</color>
    <color name="purple_200">#FFBB86FC</color>
    <color name="purple_500">#FF6200EE</color>
    <color name="purple_700">#FF3700B3</color>
    <color name="teal_200">#FF03DAC5</color>
    <color name="teal_700">#FF018786</color>
</resources>
"""
        )
        return file.absolutePath
    }

    private fun writeThemes(projectDir: File, template: ProjectTemplate): String {
        val file = File(projectDir, "app/src/main/res/values/themes.xml")
        file.parentFile?.mkdirs()
        file.writeText(
            """<resources>
    <style name="Theme.CodeHub" parent="android:Theme.Material.NoActionBar">
        <item name="android:statusBarColor">@color/purple_700</item>
    </style>
</resources>
"""
        )
        return file.absolutePath
    }

    private fun writeLauncherIcon(projectDir: File): String {
        val file = File(projectDir, "app/src/main/res/mipmap-anydpi-v26/ic_launcher.xml")
        file.parentFile?.mkdirs()
        file.writeText(
            """<?xml version="1.0" encoding="utf-8"?>
<adaptive-icon xmlns:android="http://schemas.android.com/apk/res/android">
    <background android:drawable="@color/purple_500" />
    <foreground android:drawable="@drawable/ic_launcher_foreground" />
</adaptive-icon>
"""
        )
        val roundFile = File(projectDir, "app/src/main/res/mipmap-anydpi-v26/ic_launcher_round.xml")
        roundFile.writeText(file.readText())
        return file.absolutePath
    }

    private fun writeLauncherForeground(projectDir: File): String {
        val file = File(projectDir, "app/src/main/res/drawable/ic_launcher_foreground.xml")
        file.parentFile?.mkdirs()
        file.writeText(
            """<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="108dp"
    android:height="108dp"
    android:viewportWidth="108"
    android:viewportHeight="108">
    <path
        android:fillColor="#FFFFFF"
        android:pathData="M30,30 L78,30 L78,40 L40,40 L40,52 L70,52 L70,62 L40,62 L40,78 L30,78 Z" />
</vector>
"""
        )
        return file.absolutePath
    }

    private fun writeComposeTheme(projectDir: File, packageName: String): String {
        val file = File(projectDir, "app/src/main/java/${packageName.replace('.', '.')}/ui/theme/Theme.kt")
        file.parentFile?.mkdirs()
        file.writeText(
            """package $packageName.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val DarkColors = darkColorScheme(
    primary = Color(0xFFBB86FC),
    background = Color(0xFF000000),
    surface = Color(0xFF121212)
)

private val LightColors = lightColorScheme(
    primary = Color(0xFF6200EE),
    background = Color(0xFFFFFFFF),
    surface = Color(0xFFFFFBFE)
)

@Composable
fun CodeHubTheme(
    useDarkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = if (useDarkTheme) DarkColors else LightColors,
        content = content
    )
}
"""
        )
        return file.absolutePath
    }

    private fun writeComposePreview(projectDir: File, packageName: String): String {
        val file = File(projectDir, "app/src/main/java/${packageName.replace('.', '.')}/MainActivityPreview.kt")
        file.parentFile?.mkdirs()
        file.writeText(
            """package $packageName

import androidx.compose.desktop.ui.tooling.preview.Preview
import androidx.compose.runtime.Composable

@Preview
@Composable
fun GreetingPreview() {
    Greeting(name = "CodeHub")
}
"""
        )
        return file.absolutePath
    }

    private fun writeNativeSource(projectDir: File, packageName: String): String {
        val dir = File(projectDir, "app/src/main/cpp")
        dir.mkdirs()
        val libName = packageName.substringAfterLast('.')
        val file = File(dir, "native-lib.cpp")
        file.writeText(
            """#include <jni.h>
#include <string>

extern "C" JNIEXPORT jstring JNICALL
Java_${packageName.replace('.', '_')}_MainActivity_stringFromJNI(
    JNIEnv* env,
    jobject /* this */
) {
    std::string hello = "Hello from C++";
    return env->NewStringUTF(hello.c_str());
}
"""
        )
        return file.absolutePath
    }

    private fun writeCMakeLists(projectDir: File, packageName: String): String {
        val dir = File(projectDir, "app/src/main/cpp")
        dir.mkdirs()
        val libName = packageName.substringAfterLast('.')
        val file = File(dir, "CMakeLists.txt")
        file.writeText(
            """cmake_minimum_required(VERSION 3.22)
project("$libName")

add_library($libName SHARED native-lib.cpp)
target_link_libraries($libName log)
"""
        )
        return file.absolutePath
    }

    private fun writeConsumerProguardRules(projectDir: File): String {
        val file = File(projectDir, "app/consumer-rules.pro")
        file.parentFile?.mkdirs()
        file.writeText("# Consumer ProGuard rules\n")
        return file.absolutePath
    }

    private fun writeReadme(projectDir: File, displayName: String, packageName: String, template: ProjectTemplate): String {
        val file = File(projectDir, "README.md")
        file.writeText(
            """# $displayName

Generated by CodeHub Studio using the **${template.displayName}** template.

- Package: `$packageName`
- Kotlin: ${template.kotlinVersion}
- AGP: ${template.agpVersion}
- Gradle: ${template.gradleVersion}
- minSdk: ${template.minSdk}
- targetSdk: ${template.targetSdk}

## Build

```
./gradlew :app:assembleDebug
```

## Install

```
./gradlew :app:installDebug
```

or

```
pm install app/build/outputs/apk/debug/app-debug.apk
```
"""
        )
        return file.absolutePath
    }
}
