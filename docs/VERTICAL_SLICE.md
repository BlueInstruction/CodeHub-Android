# CodeHub Vertical Slice — Operational Priority

> **MVP**: New Android Project → Generate → Provision toolchain → Gradle/AGP → Sync → Build Debug APK → Install → Launch → Logcat → Fix → Rebuild

This document defines the **operational priorities** for making CodeHub
Studio a real Android development workstation. The MVP is the
Android-app pipeline above — not the C++/Vulkan/PS5 slice.

## Priority tiers

### Tier 0 — Android-app MVP pipeline (highest priority, BLOCKING)

The MVP. Nothing else matters until a user can open CodeHub, tap "New
Android Project", and get a working APK on the device.

#### Tier 0a — Toolchain Manager

CodeHub must **not** assume the user has anything pre-installed. It
must discover, install, manage, and verify:

- **JDK** (OpenJDK 17 via Termux `pkg install openjdk-17`)
- **Android SDK** (cmdline-tools + platform-tools + build-tools)
- **Platform SDKs** (`platforms;android-35` etc. via `sdkmanager`)
- **NDK** (`ndk;27.x.x` via `sdkmanager`)
- **CMake** (Termux `pkg install cmake` or SDK-bundled CMake)
- **Ninja** (Termux `pkg install ninja`)
- **Clang** (Termux `pkg install clang` or NDK-bundled clang)
- **Git** (Termux `pkg install git`)
- **adb** (Termux `pkg install android-tools`)

Implementation (in `build/api` as `codehub.build.toolchain`):
- `ToolchainDescriptor` — typed descriptor for each component (name,
  version, install path, source: Termux/SDK/System/Manual).
- `ToolchainManager` — discovers what's installed, returns a
  `ToolchainReadiness` report (like `TermuxReadiness` but for the full
  Android toolchain).
- `ToolchainInstaller` — installs missing components via the right
  backend (Termux `pkg`, `sdkmanager`, or direct download for JDK).
- `ToolchainCompatibility` — verifies version matrix:
  - JDK 17 ↔ AGP 8.x ↔ Gradle 8.x
  - NDK r25+ ↔ CMake 3.22+
  - build-tools ↔ platform SDK
- Exposes environment variables: `ANDROID_HOME`, `ANDROID_NDK_HOME`,
  `JAVA_HOME`, `PATH` augmentation.

#### Tier 0b — New Project Wizard

The user taps "New Android Project" and gets a working Gradle project.

Implementation (in `core/workspace` as `codehub.workspace.template`):
- `ProjectTemplate` — sealed interface with variants:
  - `EmptyCompose` — single `MainActivity` + Compose, Material 3
  - `BasicViews` — single `MainActivity` + XML layout
  - `NativeActivity` — NDK + JNI hello-world
  - `AndroidLibrary` — library module skeleton
- `ProjectTemplateRegistry` — versioned templates bundled as assets.
- `ProjectGenerator` — takes template + package name + min SDK +
  display name → writes the full project tree:
  - `settings.gradle.kts`
  - `build.gradle.kts` (root)
  - `app/build.gradle.kts`
  - `gradle/libs.versions.toml`
  - `gradle/wrapper/gradle-wrapper.properties`
  - `gradlew` + `gradlew.bat` (shell scripts)
  - `app/src/main/AndroidManifest.xml`
  - `app/src/main/java/<pkg>/MainActivity.kt`
  - `app/src/main/res/values/{strings,themes,colors}.xml`
  - `app/src/main/res/mipmap-anydpi-v26/ic_launcher.xml`
  - `app/src/main/res/drawable/ic_launcher_foreground.xml`
  - `.gitignore`
  - `proguard-rules.pro`

#### Tier 0c — AGP-aware Build

`GradleBuildProvider` must understand AGP task semantics:
- `assembleDebug` → produces APK at `app/build/outputs/apk/debug/`
- `installDebug` → builds + installs in one step (skips separate
  `pm install` if used)
- `lint` → runs Android Lint
- `bundleRelease` → produces AAB
- `:app:dependencies` → dependency tree
- Signing config: auto-generate debug keystore if missing.

#### Tier 0d — Install + Launch + Logcat loop

Already partially scaffolded:
- `ApkInstaller.install()` runs `pm install -r <apk>`
- `ApkInstaller.launch()` fires the launch intent via
  `PackageInspector.launch()`
- `LogcatService.snapshot()` captures recent entries
- `LogcatService.stream()` streams new entries

**Fix needed**: Logcat must filter by the launched app's PID, not just
by tag/message substring. Use `logcat --pid=<pid>` (API 24+) or
resolve PID from `pm pid <package>`.

#### Tier 0e — AI-assisted fix-and-rebuild

When the build fails, the AI must understand:
- AGP task output (e.g. `:app:compileDebugKotlin FAILED`)
- Kotlin compiler diagnostics (file:line:col format)
- Android Lint output
- Manifest merge errors

The existing `BuildFailureAnalysis` + `ContextRetriever` handle the
generic case; they need Android-specific context augmentation:
- Include `AndroidManifest.xml` in context
- Include `build.gradle.kts` in context
- Include the failing Kotlin source file
- Parse AGP output to extract the failing task + source location

### Tier 1 — True Termux PTY (DONE)

Real interactive PTY via Apache 2.0 termux-app port. See commit
`b03d467`. `PtyBackendProvider` replaces the old ProcessBuilder-based
`TermuxBackendProvider`.

### Tier 2 — llama.cpp + ggml native bindings (DEFERRED)

On-device LLM inference. Layer 3 concern. Defer until Tier 0 MVP works.

### Tier 3 — C++/Vulkan vertical slice (DEFERRED)

The original `Workspace → Termux → Git → CMake → Ninja → Clang → APK →
Install → Logcat → AI` pipeline. This is the Layer 2 (Native/System
Development) proof, not the daily driver. Defer until Tier 0 MVP works.

### Tier 4 — AI engineering loop with patch+rebuild (DEFERRED)

Full autonomous fix-and-rebuild with patch review. Builds on Tier 0e.

### Tier 5 — Android Code Studio as architectural reference

Use ACS as a **behavioral reference** for:
- How it manages the environment inside `/data/data/.../files/usr`
- How it handles dpkg, repositories, toolchains, conflicts, recovery
- LSP client contracts (re-implement, don't copy GPL code)
- Gradle Tooling API integration (re-implement)
- LogWire structured logcat parsing (re-implement)

### Tier 6 — termux-packages dependency knowledge base

Codegen a dependency JSON snapshot for pre-flight checks.

### Tier 7 — termux-x11 (DEFERRED)

External APK dependency. Not needed for Tier 0 MVP.

### Tier 8 — OpenClaw + Hermes as provider backends (DEFERRED)

AI Gateway backends. Layer 3 concern. Defer until Tier 0 MVP works.

## MVP gaps closed

Three gaps were identified that prevented the MVP from working on a
clean device. All three are now closed:

### Gap #1 — Gradle Wrapper bootstrap (DONE)

The ProjectGenerator now bundles the real Apache 2.0 Gradle wrapper
files as assets (`gradle-wrapper.jar`, `gradlew`, `gradlew.bat`) and
copies them into every generated project. The wrapper is the single
source of truth for the project's Gradle version — CodeHub does NOT
install Gradle globally via Termux pkg.

Toolchain invariant enforced: JDK/CMake/Ninja/Clang/Git/Adb go through
Termux pkg (generic Linux tools). Android SDK/Platform Tools/Build
Tools/Platform SDK/NDK go through sdkmanager into CodeHub's own data
dir, isolating them from Termux packages. Gradle is NEVER installed
globally.

### Gap #2 — AI failure analysis with real context (DONE)

BuildFailureAnalysis now gathers real project context via
ProjectContextGatherer:
- settings.gradle.kts, root build.gradle.kts, app/build.gradle.kts
- gradle/libs.versions.toml
- AndroidManifest.xml
- gradle.properties, gradle-wrapper.properties
- Source files referenced by compiler diagnostics (up to 8 files)
- Build result (status, exit code, duration, stdout, stderr)
- Compiler/AGP diagnostics (up to 20 entries)
- Logcat filtered to the package (up to 50 entries)

The AI receives all of this and responds with ROOT_CAUSE / EVIDENCE /
PATCH sections, parsed and surfaced in the UI.

AndroidProjectBuildPipeline emits AiAnalysisTriggered events on sync
failure, build failure, and install failure — each with the full
context. NewProjectViewModel subscribes and invokes BuildFailureAnalysis.

### Gap #3 — SAF folder picker (DONE)

NewProjectScreen now has a "Pick folder" button that launches Android's
Storage Access Framework (OpenDocumentTree). The returned tree URI is
converted to a real filesystem path so Gradle (running via Termux) can
access it. An "Append display name" button creates a subdirectory named
after the app (sanitized). The user can also type a path manually.

## Definition of MVP done

The MVP is "fully operational" when:

1. A user opens CodeHub on a phone with Termux installed (but no JDK,
   no Android SDK, no NDK, no Gradle).
2. CodeHub's Toolchain Manager detects what's missing and offers to
   install it.
3. User taps "New Android Project" → picks Empty Compose → enters
   package name + display name → picks a folder via SAF.
4. CodeHub generates the full Gradle project tree (including the real
   gradle-wrapper.jar).
5. CodeHub runs `./gradlew :app:assembleDebug` (after auto-generating a
   debug keystore).
6. The APK is discovered at `app/build/outputs/apk/debug/`.
7. CodeHub installs it via `pm install`.
8. CodeHub launches it via `am start`.
9. CodeHub streams logcat filtered to that app's PID.
10. If the build fails, the AI agent is invoked with the AGP output,
    Kotlin diagnostics, Gradle files, manifest, source files, and
    logcat — and suggests a ROOT_CAUSE / EVIDENCE / PATCH the user
    can review.

When that loop completes on a real device, CodeHub Studio is a true
Android development workstation. Everything else (C++, Vulkan,
emulators, llama.cpp) layers on top.
