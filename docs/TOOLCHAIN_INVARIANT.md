# CodeHub Toolchain Isolation Invariant

CodeHub manages three distinct toolchain layers that must never
contaminate each other. This document defines the invariant and
explains why it exists.

## The three layers

```
Layer 1: Termux packages (owned by Termux pkg)
  /data/data/com.termux/files/usr/bin/
    bash, git, cmake, ninja, clang, python, node, java, adb, ...
  /data/data/com.termux/files/usr/lib/
    libc++, libcurl, libssl, etc.
  Managed by: pkg install / pkg remove
  Versioning: Termux package database

Layer 2: CodeHub SDK root (owned by CodeHub)
  <context.filesDir>/android-sdk/
    cmdline-tools/latest/bin/sdkmanager
    platform-tools/adb
    build-tools/35.0.0/
    platforms/android-35/
    ndk/27.0.12077973/
  Managed by: sdkmanager (CodeHub ToolchainInstaller)
  Versioning: sdkmanager package database

Layer 3: Project Gradle wrapper (owned by the project)
  <project>/gradlew
  <project>/gradle/wrapper/gradle-wrapper.jar
  <project>/gradle/wrapper/gradle-wrapper.properties
  Managed by: the wrapper itself (downloads Gradle distribution)
  Versioning: gradle-wrapper.properties
```

## The invariant

**CodeHub must never mix these layers.**

1. **Termux packages** are generic Linux tools. CodeHub installs them
   via `pkg install` and lets Termux own the package database. CodeHub
   does NOT install Android SDK components into the Termux prefix.

2. **CodeHub SDK root** is the Android SDK/NDK, installed via
   `sdkmanager` into CodeHub's own data directory
   (`context.filesDir/android-sdk/`). This is isolated from Termux
   packages. CodeHub does NOT use `pkg install` for SDK components.

3. **Project Gradle wrapper** is per-project. CodeHub does NOT install
   Gradle globally. Each generated project gets its own `gradlew` +
   `gradle-wrapper.jar` + `gradle-wrapper.properties`. The wrapper
   downloads the exact Gradle distribution declared in
   `gradle-wrapper.properties`.

## Why this matters

The previous log from Android Code Studio showed that `ndk-sysroot`
had to overwrite OpenSSL files owned by the `openssl` package. This
kind of provisioning makes the environment work momentarily but
creates an unhealthy package database state:

- `pkg list-installed` reports conflicting file ownership
- `pkg upgrade` may break the SDK
- `pkg remove openssl` may break the NDK
- The user cannot trust the package database

CodeHub avoids this by keeping the SDK in its own directory. Termux
packages never see the SDK; the SDK never touches Termux packages.

## Enforcement

The invariant is enforced in `ToolchainInstaller.ensureReady()`:

- `ToolchainComponent.Jdk`, `Cmake`, `Ninja`, `Clang`, `Git`, `Adb` →
  `installTermuxPackages()` via `pkg install` (Layer 1)
- `ToolchainComponent.AndroidSdk`, `PlatformTools`, `BuildTools`,
  `PlatformSdk`, `Ndk` → `provisionAndroidSdk()` via `sdkmanager` into
  `context.filesDir/android-sdk/` (Layer 2)
- `ToolchainComponent.Gradle` → never installed globally. The
  per-project wrapper is the single source of truth (Layer 3)

`ToolchainManager.probeGradle()` does NOT report `NotFound` when
Termux Gradle is absent. It reports `CodeHub` source with version
`'wrapper'` and `compatible=true`, because the per-project wrapper is
the canonical Gradle.

`ToolchainComponent.Gradle` is NOT in
`CRITICAL_FOR_ANDROID_APP` — it's per-project, not a global
requirement.

## Idempotency

`ToolchainInstaller.ensureReady()` is idempotent:

- **First run:** probes → finds missing → installs → re-probes →
  verifies → returns ready
- **Second run:** probes → finds nothing missing → returns immediately
  (no install, no re-download)
- **Third run:** same as second

`provisionAndroidSdk()` checks `isSdkPackageInstalled()` before
calling `sdkmanager` for each package. If the package directory
already exists and is non-empty, it skips the install.

`installTermuxPackages()` relies on `pkg install -y` being idempotent
(Termux reports "already installed" and exits 0).

## AGP ↔ Gradle ↔ JDK compatibility

The ProjectGenerator writes `gradle-wrapper.properties` with the
Gradle version from the project template. This version must be
compatible with the AGP version used by the same template.

`ToolchainCompatibility.agpGradleJdkMatrix(agpVersion)` returns the
required (Gradle, JDK-min, JDK-max) triple for a given AGP version.

The test `gradle-wrapper version is compatible with AGP version for
all templates` verifies that every template in
`ProjectTemplateRegistry` has a Gradle version compatible with its
AGP version.
