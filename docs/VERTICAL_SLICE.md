# CodeHub Vertical Slice

> CodeHub → Workspace → Termux → Git → CMake → Ninja → Clang → APK → Install → Logcat → AI Analysis

This is the true vertical slice of the project. **No new modules are added until
this pipeline runs end-to-end.** Every feature added in this phase must land
inside an existing module.

## Why a vertical slice first

CodeHub already has 36 modules scaffolded. The temptation is to keep widening
each module's surface area, but a wider half-built foundation is less useful
than one fully-working end-to-end pipeline. The vertical slice is the proof
that CodeHub is a real mobile engineering workstation, not just an
architecture diagram.

## Fork-review synthesis

Five forks were reviewed (see `/home/z/my-project/worklog.md` for details).
The following portable artifacts will be lifted into CodeHub without
introducing new modules:

| Source fork        | Artifact                                       | Target module              | Cost |
| ------------------ | ---------------------------------------------- | -------------------------- | ---- |
| hermes-agent       | DANGEROUS_PATTERNS regex bank (~150 entries)    | core/permissions           | low  |
| opencode           | BashArity prefix table (164 entries)           | core/permissions           | low  |
| hermes-agent       | IterationBudget (63-line counter)              | ai/agents                  | low  |
| pocketpal-ai       | HardwareInfoModule (SOC, GPU, /proc/cpuinfo)   | devtools/device            | low  |
| pocketpal-ai       | DownloadModule (Room + WorkManager + OkHttp)   | devtools/packages          | med  |
| roxum-ide          | Workspace sandbox (canonicalFilePath, isInside) | core/workspace             | low  |
| opencode           | Permission rule engine (allow/deny/ask)        | core/permissions           | med  |
| opencode           | Snapshot/revert (bare git repo undo)            | git/core                   | med  |
| openclaw           | Terminal open params (cols/rows bounds, confined) | terminal/api            | low  |
| openclaw           | Tool-call-repair stream normalizer             | ai/gateway                 | med  |

## Vertical slice components to add (no new modules)

### 1. Termux bootstrap — `terminal/termux/TermuxBootstrap.kt`

Detects Termux presence, checks essential binaries (git, cmake, ninja,
clang, make), returns a structured `TermuxReadiness` report. Used by the
pipeline before attempting any build.

### 2. APK installer — `devtools/packages/ApkInstaller.kt`

Uses `pm install` via the ProcessRunner. Falls back to `am start -a
android.intent.action.INSTALL_PACKAGE` if the foreground service lacks
`INSTALL_PACKAGES`. Returns `ApkInstallResult { success, packageName,
errorMessage }`. Lives in `devtools/packages` because that module
already owns `PackageInspector` and the install/launch lifecycle.

### 3. Pipeline orchestrator — `core/services/BuildPipeline.kt`

A `ManagedService` that runs the slice as a state machine:

```
WorkspaceOpened
   → TermuxVerified
   → GitStatusChecked
   → BuildConfigured (CMake)
   → BuildExecuted (Ninja)
   → NativeCompiled (Clang)
   → ApkDiscovered
   → ApkInstalled
   → AppLaunched
   → LogcatStreaming
   → (on failure) AiAnalysisTriggered
```

Each step emits a `PipelineEvent` that the UI subscribes to. The
pipeline is cancellable per-step. Failures short-circuit to the AI
analysis step with the failing stage's stderr attached.

### 4. Vertical slice screen — `app/src/main/java/codehub/ui/screens/VerticalSliceScreen.kt`

A single Compose screen that walks the user through the pipeline. Shows
the current step, the last event per step, and an "Approve and continue"
button for steps that need user confirmation (install APK, push to git,
etc.).

### 5. AI failure-analysis trigger — `ai/agents/BuildFailureAnalysis.kt`

When `BuildPipeline` reports a failure at any step, this triggers a
single-turn agent run with:
- The failing step's name and stderr
- The most recent logcat snapshot (if the failure was at app launch)
- A system prompt that asks for a root-cause hypothesis and a patch
  suggestion

The agent runs with `WORKSPACE_WRITE` permission by default, so the
suggested patch is staged (not committed) for user review.

## Out of scope for this phase

- New modules (termux verification, APK install, pipeline driver, etc.
  all live in existing modules)
- Vulkan / SPIR-V tooling (Phase 7)
- Offline LLM inference (Phase 4 — pocketpal bridge stays stubbed)
- Online agent backends beyond the existing HTTP client (Phase 6)
- Editor WebView embedding (Phase 2 — vscode.dev fallback remains)
- Plugin architecture (Phase 5 of original roadmap)

## Definition of done

The vertical slice is "fully established" when:

1. A user opens CodeHub, picks a workspace folder containing a CMake
   Android project.
2. CodeHub verifies Termux + git + cmake + ninja + clang are present,
   and shows a clear error if any are missing.
3. The user taps "Run pipeline".
4. CodeHub runs `git status`, then `cmake -S . -B build -G Ninja`, then
   `ninja -C build`, then `clang++` (if any .cpp sources need ad-hoc
   compilation), then locates the resulting `.apk` or `.so`.
5. CodeHub installs the APK via `pm install` (or shows the install
   dialog if it lacks the permission).
6. CodeHub launches the installed app and streams `logcat` filtered to
   that package.
7. If any step fails, the AI agent is invoked with the failure context
   and the user sees a suggested patch they can approve or reject.

Everything else in the roadmap waits until this works on a real device.
