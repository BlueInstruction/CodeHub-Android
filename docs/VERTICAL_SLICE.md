# CodeHub Vertical Slice — Operational Priority

> Workspace → Termux → Git → CMake → Ninja → Clang → Build → APK → Install → Launch → Logcat → AI Analysis → Patch → Rebuild

This document defines the **operational priorities** for making the
vertical slice real. It supersedes any earlier priority list.

The slice is "fully operational" when the pipeline runs end-to-end on
a real Android phone against a real C++/Vulkan project, with AI-driven
patch-and-rebuild on failure.

## Priority tiers

### Tier 1 — True Termux PTY (DONE)

Replaced the `ProcessBuilder`-based `TermuxBackendProvider` with a real
interactive PTY layer ported from termux-app's Apache 2.0
`terminal-emulator/` module.

Status: **implemented**.

Why it matters:
- PS5 emulator development requires an **interactive shell** with
  signals, stdin/stdout, and long-running processes.
- `ProcessBuilder` cannot do PTY emulation, raw mode, or signal
  forwarding. It can only run commands and capture output.
- Every downstream build/git/cmake step is a child of an interactive
  shell, so the PTY is the foundation of everything else.

What was ported:
- `terminal-emulator/src/main/jni/termux.c` (Apache 2.0) →
  `terminal/termux/src/main/cpp/termux.c` — forks /dev/ptmx, setsid,
  dup2 stdio, IUTF8 mode, TIOCSWINSZ resize.
- `terminal-emulator/src/main/java/com/termux/terminal/*` (Apache 2.0) →
  `terminal/termux/src/main/java/com/termux/terminal/*` — preserved
  original package name so JNI symbols resolve without modification.
  Includes TerminalSession, TerminalEmulator, TerminalBuffer,
  TerminalRow, ByteQueue, KeyHandler, TerminalColors, TextStyle, etc.
- `terminal-emulator/src/main/jni/Android.mk` →
  `terminal/termux/src/main/cpp/Android.mk` (ndk-build).
- Apache 2.0 LICENSE + NOTICE files added at `terminal/termux/`.

What was added (original CodeHub code, MIT):
- `codehub.terminal.termux.pty.PtyBackendProvider` — implements
  `TerminalBackendProvider`, wraps `TerminalSession`, exposes a
  `Flow<TerminalOutput>` with rendered screen text + exit events.
- `codehub.terminal.termux.pty.PtyEnvironment` — builds HOME/PREFIX/
  PATH/LD_LIBRARY_PATH/TERM/LANG/TMPDIR env map for the forked shell.
- `codehub.terminal.termux.pty.DefaultPtyTerminalSessionClientFactory`
  — creates `TerminalSessionClient` instances that bridge
  `TerminalSession` callbacks to CodeHub's `Flow<TerminalOutput>` and
  `DiagnosticSink`.
- `terminal/termux/build.gradle.kts` — wired `externalNativeBuild`
  with `ndkBuild` path; abiFilters `arm64-v8a` + `x86_64`.
- `terminal/termux/consumer-rules.pro` — keep rules for
  `com.termux.terminal.**` and native methods (proguard-safe JNI).
- `PtyEnvironmentTest` — 11 tests for env builder + shell resolver.

What was removed:
- Old `codehub.terminal.termux.TermuxBackendProvider` — the
  ProcessBuilder-based stub. Replaced by `PtyBackendProvider`.

### Tier 2 — llama.cpp + ggml native bindings

Convert `OfflineProviderClient` from a stub into actual on-device
inference.

Why it matters:
- The AI engineering loop (Tier 4) requires on-device inference to be
  truly mobile. Online-only providers defeat the purpose.
- `llama.cpp/examples/llama.android/` is a complete NDK + JNI + Kotlin
  reference. `ggml` provides 7 Android ARM variants + Vulkan backend.

Implementation:
- Vendor `ggml` + `llama.cpp` as native libs in
  `integrations/pocketpal/src/main/cpp/`.
- Port `examples/llama.android/lib/src/main/java/com/arm/aichat/` into
  `integrations/pocketpal/src/main/java/codehub/integrations/pocketpal/`
  as `InferenceEngine`, `AiChat`, `GgufMetadataReader`.
- Wire `PocketPalBridge.client(config)` to return a real
  `LlamaCppProviderClient` that streams tokens via `Flow<ChatChunk>`.
- Replace `OfflineProviderClient` in `DefaultAiGateway.createClient()`
  with the PocketPal bridge.
- Build setup: NDK r25+, two-pass CMake (host vulkan-shaders-gen, then
  cross-compile), per-ABI `.so` packaging, Play Asset Delivery for
  GGUF models.

### Tier 3 — Real Build Pipeline

The `BuildPipeline` orchestrator exists as a state machine, but it
must become **genuinely executable** end-to-end.

Implementation:
- Verify each step actually runs:
  - `TermuxBootstrap.probe()` correctly detects missing tools.
  - `TermuxBootstrap.installPackages()` actually installs them.
  - `GitService.status()` parses `git status --porcelain=v2` correctly.
  - `CMakeBuildProvider` produces a working build directory.
  - `NinjaBuildProvider` runs the build.
  - `ClangBuildProvider` compiles ad-hoc C++ sources.
  - `ApkInstaller.discoverApks()` finds the resulting APK.
  - `ApkInstaller.install()` runs `pm install` successfully.
  - `ApkInstaller.launch()` starts the app.
  - `LogcatService.snapshot()` captures recent entries.
- Fix the hardcoded workspace path in `VerticalSliceViewModel`.
- Wire a real Android folder picker (SAF or storage access).
- Surface logcat entries in the UI (state is set but not displayed).

### Tier 4 — AI Engineering Loop

The post-build cycle: `Build failure → diagnostics → context retrieval
→ local/online agent → patch → diff → rebuild`.

Implementation:
- `BuildFailureAnalysis` exists but only produces a single-turn
  hypothesis + patch suggestion. It must become a **loop**:
  1. Build fails → `BuildPipeline` emits `AiAnalysisTriggered`.
  2. `BuildFailureAnalysis.analyze()` produces a patch.
  3. Patch is **staged** (not applied) for user review.
  4. User approves → patch is applied via `FileSystemGateway.write()`.
  5. `BuildPipeline.execute()` re-runs.
  6. If rebuild succeeds, loop ends. If not, repeat with updated context.
- Add a `PatchReviewScreen` for the user to see the diff and approve.
- Add iteration budget enforcement (`IterationBudget`) to cap retries.

### Tier 5 — Android Code Studio as reference

Use ACS as an **architectural reference** for LSP, Gradle tooling,
Logcat, and APK installation. Do **not** port GPL-3.0 code.

Implementation:
- Re-implement `ILanguageServer` contract (from `core/lsp-api/`) in
  `editor/api`. Add a `LspStdioClient` that speaks LSP over stdio for
  clangd (Termux package).
- Re-implement `GradleBuildService` (from `core/app/.../services/builder/`)
  using the Gradle Tooling API (not `./gradlew` subprocess) in
  `build/gradle`. Provides structured project model + task execution.
- Use `LogWire` (from `external/logwire/`) as a reference for
  structured logcat parsing in `devtools/logcat`.
- Use `IPackageInstaller` (from `core/app/.../handlers/system/installer/`)
  as a reference for the install-session API in `devtools/packages`.

### Tier 6 — termux-packages dependency knowledge base

Codegen a dependency JSON snapshot from termux-packages'
`build.sh` files into `terminal/termux/TermuxBootstrap`.

Why it matters:
- Lets `TermuxBootstrap` say "installing cmake also pulls libarchive,
  libc++, libcurl, libexpat, jsonjsoncpp, libuv, rhash, zlib (recommends
  clang, make)" before running `pkg install`.
- Critical for large CMake/Clang/Vulkan projects that have deep
  dependency trees.

Implementation:
- Write a Python script that walks `packages/*/build.sh` and emits a
  JSON map of `package → {depends, recommends, conflicts, breaks}`.
- Ship the JSON as an asset in `terminal/termux/src/main/assets/`.
- Update `TermuxBootstrap.probe()` to load the JSON and report missing
  transitive dependencies in `TermuxReadiness.issues`.

### Tier 7 — termux-x11 (deferred)

Becomes important when CodeHub reaches the stage of running GUI/Linux
tools inside the app. Not a priority for the initial vertical slice.

When the time comes:
- Keep termux-x11 as an **external APK dependency** (do not embed GPL
  code into CodeHub).
- Port the Chromium-BSD `input/` package (TouchInputHandler, etc.) into
  `editor/vscodeweb` for touch→mouse/keyboard mapping in the WebView.
- Adopt the `memfd + AHardwareBuffer + Unix-socket fd passing` IPC
  pattern from `buffer.c` (re-implement from spec, don't copy GPL).

### Tier 8 — OpenClaw + Hermes as provider backends

Keep OpenClaw and Hermes as **backends** behind `AiGateway`, not as
the core. CodeHub's UI must not be tightly coupled to either.

Implementation:
- `integrations/openclaw/OpenClawBridge.client(config)` returns a
  `OpenClawProviderClient` that talks to a running OpenClaw instance
  (Termux subprocess or remote).
- `integrations/hermes/HermesBridge.client(config)` returns a
  `HermesProviderClient` that talks to a Hermes Gateway.
- Both are registered via `AiGateway.registerProvider()` and selected
  by `providerId` in `ChatRequest`.
- `PermissionManager` and `AgentAuditLog` apply uniformly across all
  providers.

## Out of scope for this phase

- New modules (everything lands in existing modules).
- Vulkan / SPIR-V tooling beyond `StubVulkanInspector`.
- Plugin architecture.
- Online agent backends beyond the existing HTTP client.
- Editor WebView embedding (Phase 2 — vscode.dev fallback remains).
- PS5 emulator itself (workload, not component).

## Definition of operational

The vertical slice is "fully operational" when:

1. A user opens CodeHub on an Android phone with Termux installed.
2. Picks a real C++/CMake project from the filesystem.
3. Taps "Run pipeline".
4. CodeHub runs `git status`, then `cmake -S . -B build -G Ninja`,
   then `ninja -C build`, then locates the resulting artifact.
5. If the artifact is an APK, CodeHub installs and launches it.
6. CodeHub streams `logcat` filtered to that package.
7. If any step fails, the AI agent is invoked with the failure context
   and the user sees a suggested patch.
8. The user approves the patch; CodeHub applies it and rebuilds.
9. The rebuild succeeds.

When that loop completes on a real device, CodeHub is a true mobile
engineering workstation.
