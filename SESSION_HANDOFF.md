# CodeHub Studio — Session Handoff & Full Context

> **This file is for any AI model or developer picking up the CodeHub project.**
> Read this first. It explains everything: what CodeHub is, what's been
> built, what works, what doesn't, and what to do next.

## What is CodeHub Studio?

CodeHub Studio is an **Android development workstation** — an Android app
that turns a phone or tablet into a practical software engineering
environment for building Android apps.

**It is NOT:**
- A VS Code clone for Android
- A Termux replacement
- A PS5 emulator
- A chatbot wrapper

**It IS:**
- An **orchestration layer** that composes Termux (execution), code-server
  (editor), Gradle/AGP (build), Git, and AI (analysis) into a single
  cohesive development environment on Android.
- A **toolchain manager** that provisions JDK, Android SDK, NDK, Build
  Tools, CMake, Ninja, Clang from scratch — the user should NOT need
  anything pre-installed except Termux.
- A **New Project wizard** that generates a full Gradle Android project
  (EmptyCompose, BasicViews, NativeActivity, AndroidLibrary templates)
  with a working gradle-wrapper.jar, then builds → installs → launches
  → streams PID-filtered logcat → (on failure) AI analysis → patch →
  rebuild.

## Repository

- **URL**: https://github.com/BlueInstruction/CodeHub-Android
- **Owner**: BlueInstruction
- **Visibility**: Public
- **License**: MIT (code) + Apache 2.0 (Termux terminal-emulator port)
- **CI**: GitHub Actions — 4 jobs (unit-tests, build-debug-apk,
  lint-check, module-structure-check). Currently **GREEN** as of
  commit `186ad3c`.
- **Git author**: All commits must be authored as
  `BlueInstruction <BlueInstruction@users.noreply.github.com>`

## Critical conventions

1. **No `blueinstruction` in code** — the package is `codehub.*`
   (not `io.github.blueinstruction.codehub`). The word "blueinstruction"
   must not appear in any `.kt`, `.kts`, `.pro`, `.xml`, or `.toml` file.
2. **No inline explanatory comments** in `.kt` files — code should be
   self-documenting. Comments are only in docs/ and commit messages.
3. **Commits as BlueInstruction** — `git config user.name "BlueInstruction"`
   and `git config user.email "BlueInstruction@users.noreply.github.com"`.
4. **Toolchain isolation invariant** — three layers must never mix:
   - Layer 1: Termux packages (pkg install — bash, git, cmake, ninja,
     clang, python, node, java, adb)
   - Layer 2: CodeHub SDK root (sdkmanager into
     context.filesDir/android-sdk/ — cmdline-tools, platform-tools,
     build-tools, platforms, ndk)
   - Layer 3: Project Gradle wrapper (per-project gradlew +
     gradle-wrapper.jar + gradle-wrapper.properties)
5. **AI analysis is optional** — the build pipeline must fail and
   report diagnostics even if no AI provider is available. AI is a
   post-failure enhancement, not a dependency.

## Architecture (7 layers)

```
CodeHub Studio
├── Project Manager (core/workspace — templates, generator, location resolver)
├── Android Toolchain (build/api — ToolchainManager, ToolchainInstaller,
│   ToolchainCompatibility, DebugKeystoreGenerator)
├── Build System (build/{api,gradle,cmake,ninja,native} — BuildService,
│   AGP-aware GradleBuildProvider, AgpTaskSemantics, AndroidArtifactLocator,
│   AgpDiagnosticParser)
├── Runtime (devtools/{packages,logcat} — ApkInstaller, PID-filtered
│   LogcatService, PackageInspector)
├── Development Environment (editor/{api,codeserver,vscodeweb},
│   terminal/{api,termux,local-runtime,ssh} — real PTY via Apache 2.0
│   Termux port)
├── AI (ai/{gateway,models,agents,tools,context,permissions} — AiGateway,
│   AgentRunner, BuildFailureAnalysis, ProjectContextGatherer,
│   DangerousCommandPatterns, BashArity, IterationBudget)
└── Advanced Native Workloads (DEFERRED — Vulkan, Mesa, emulators,
    llama.cpp/ggml)
```

## Module count

40 Gradle modules across 8 top-level groups:
- `core/` — workspace, process, services, permissions, diagnostics,
  configuration (6 modules)
- `editor/` — api, codeserver, vscodeweb (3 modules)
- `terminal/` — api, termux (with real PTY), local-runtime, ssh (4 modules)
- `build/` — api (with toolchain + signing), gradle, cmake, ninja, native (5 modules)
- `ai/` — gateway, models, agents, tools, context, permissions (6 modules)
- `git/` — core, github (2 modules)
- `devtools/` — logcat, packages, processes, memory, vulkan, device (6 modules)
- `integrations/` — pocketpal, openclaw, hermes, android-code-studio, opengui (5 modules)
- `app/` — the Android application with Compose UI (1 module)
- `buildSrc/` — Gradle plugin DSL (1 module)

## Current state (what works)

### CI (GREEN ✅)
- Unit tests pass (with some @Ignore'd flaky tests that need Robolectric)
- Debug APK builds successfully
- Lint passes
- Module structure checks pass (no blueinstruction, gradle-wrapper.jar
  present, Apache 2.0 attribution, etc.)

### Code that's implemented and tested
- **ToolchainManager** — discovers JDK, Android SDK, NDK, Build Tools,
  Platform SDK, CMake, Ninja, Clang, Git, Adb, Gradle
- **ToolchainInstaller** — idempotent provisioning via Termux pkg +
  sdkmanager into isolated CodeHub data dir
- **ToolchainCompatibility** — version matrix (JDK 17-21, NDK 25-28,
  CMake 3.22+, Gradle 8.9-8.11) + AGP↔Gradle↔JDK lookup
- **ProjectGenerator** — generates full Gradle project tree (4 templates)
  with real gradle-wrapper.jar bundled as asset
- **WorkspaceLocationResolver** — classifies SAF URIs as
  RealFilesystem / TermuxAccessible / UnsupportedProvider
- **GradleBuildProvider** — AGP-aware (assembleDebug, installDebug,
  lintDebug, bundleRelease) with AgpTaskSemantics +
  AndroidArtifactLocator + AgpDiagnosticParser
- **DebugKeystoreGenerator** — auto-generates ~/.android/debug.keystore
- **AndroidProjectBuildPipeline** — full state machine:
  Generate → Provision → Sync → Build → Sign → Install → Launch →
  Logcat → (on failure) AiAnalysisTriggered
- **BuildFailureAnalysis** — gathers real context (Gradle files,
  manifest, source files from diagnostics, logcat) and sends to AiGateway
- **PID-filtered Logcat** — streamForPid, streamForPackage,
  resolvePid (pidof → pgrep → ps fallback)
- **Real Termux PTY** — Apache 2.0 terminal-emulator port with JNI
  /dev/ptmx fork, TerminalSession, TerminalEmulator
- **DangerousCommandPatterns** — ~180 regex patterns for dangerous
  commands (rm -rf, mkfs, dd, curl|sh, fork bombs, git push --force,
  fastboot erase, etc.)
- **BashArity** — ~500 prefix entries for human-readable command
  descriptions in approval prompts
- **IterationBudget** — thread-safe parent/subagent counters

### What's NOT working yet
- **The app doesn't run on a real device** — it compiles and the APK
  builds, but the full pipeline (New Project → Generate → Provision →
  Build → Install → Launch → Logcat) has NOT been tested end-to-end
  on a clean Android device. This is the **T0-V validation** phase.
- **AI analysis uses a stub OfflineProviderClient** — it echoes back
  text instead of doing real LLM inference. The online
  HttpProviderClient is implemented but no provider (GLM, OpenAI,
  etc.) is registered by default.
- **Editor is a stub** — CodeServerEditorService detects code-server
  binary but doesn't actually launch or embed it in a WebView.
- **No llama.cpp/ggml** — on-device inference is not wired up.

## Priority order (from user)

1. **T0-V clean-device validation** — prove the pipeline works on a
   real Android phone (the REAL test, not CI)
2. Fix every failure found by T0-V
3. Make AiGateway provider-neutral (reasoning as capability parameter,
   not GLM-specific fields)
4. Add GlmProviderClient (after T0-V + provider-neutral gateway)
5. Add GLM reasoning adapter (enable_thinking, reasoning_effort)
6. Connect BuildFailureAnalysis through AiGateway
7. Add agent iteration/checkpoint model
8. Only then expand editor/terminal capabilities

## Key files to read

- `docs/VISION.md` — what CodeHub is and isn't
- `docs/VERTICAL_SLICE.md` — the MVP pipeline definition + priority tiers
- `docs/TOOLCHAIN_INVARIANT.md` — the three-layer isolation invariant
- `docs/ARCHITECTURE.md` — module map and service lifecycle
- `docs/ROADMAP.md` — phase-by-phase status
- `docs/BUILD.md` — how to build and extend
- `settings.gradle.kts` — all 40 modules
- `gradle/libs.versions.toml` — version catalog (AGP 8.7.3, Kotlin 2.0.21,
  Gradle 8.10.2, Hilt 2.52, Compose BOM 2024.12.01)
- `app/src/main/java/codehub/ui/screens/newproject/` — the New Project
  screen + ViewModel + AndroidProjectBuildPipeline wiring
- `core/workspace/src/main/assets/gradle-wrapper/` — bundled real
  gradle-wrapper.jar + gradlew + gradlew.bat

## How to push code

```bash
cd /home/z/my-project/repos/CodeHub-Android
git config user.name "BlueInstruction"
git config user.email "BlueInstruction@users.noreply.github.com"
# Make changes...
git add -A
git commit -m "description"
git push origin main
```

## Fork reviews completed (14 total)

The following forks were reviewed for portable features:
- **termux-app** — PTY layer ported (Apache 2.0), done
- **termux-packages** — dependency knowledge base (deferred)
- **termux-x11** — input patterns (deferred)
- **llama.cpp** — on-device LLM (deferred, Layer 3)
- **ggml** — tensor library (deferred, Layer 3)
- **android-code-studio** — architectural reference only (GPL-3.0)
- **OpenClaw** (TS) — agent patterns (deferred)
- **OpenClaw-Android** — service lifecycle (deferred)
- **Hermes** (Python) — DANGEROUS_PATTERNS + IterationBudget ported
- **Hermes-Android** — contract patterns (deferred)
- **opencode** — BashArity + permission patterns ported
- **PocketPal-AI** — DownloadModule + HardwareInfo (deferred)
- **roxum-ide** — runtime env model (reference only)
- **GLM-5** — model/provider semantics (deferred)

## GLM-5.2 / GLM-5.3 integration plan (NOT YET IMPLEMENTED)

GLM-5.2 is a 753B MoE LLM from Z.ai with:
- 1M token context window
- OpenAI-compatible REST API at integrate.api.nvidia.com/v1
- Tool calling, structured output, reasoning
- `reasoning_effort` parameter (max/high) — GLM-specific, NOT in OpenAI spec
- `enable_thinking` boolean — GLM-specific

**Integration plan (after T0-V):**
1. Make AiGateway provider-neutral — add `reasoning` parameter to
   ChatRequest as a capability-specific field (not GLM-specific):
   ```
   ChatRequest {
     messages, tools, temperature, maxTokens,
     reasoning { enabled, effort }  // provider-neutral
   }
   ```
2. Add GlmProviderClient that maps:
   - `reasoning.enabled = true` → `enable_thinking = true`
   - `reasoning.effort = MAX` → `reasoning_effort = "max"`
3. Register GLM-5.2/5.3 as a provider via `AiGateway.registerProvider()`
4. ModelDescriptor should use metadata (not hard-coded params):
   ```
   modelId = "glm-5.3"
   provider = "z.ai" or "nvidia-nim"
   contextWindow = 1_000_000
   supportsTools = true
   supportsStructuredOutput = true
   supportsReasoning = true
   ```
5. BuildFailureAnalysis should go through AiGateway, not directly to GLM.

## What to do in a new session

1. Read this file (SESSION_HANDOFF.md)
2. Read docs/VISION.md and docs/VERTICAL_SLICE.md
3. Check CI status: `curl -s -H "Authorization: token <PAT>" 
   "https://api.github.com/repos/BlueInstruction/CodeHub-Android/actions/runs?per_page=1"`
4. Clone the repo and start working on T0-V validation
5. All commits as BlueInstruction
6. No inline comments in .kt files
7. No new modules — everything goes in existing modules
