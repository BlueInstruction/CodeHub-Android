# CodeHub Architecture

CodeHub is an Android application that orchestrates a software engineering
environment on the device. The Android app is the UI and lifecycle owner;
real workloads run in execution backends (Termux, code-server, native
processes, AI providers).

## Module map

```
core/         Foundation shared by every layer
  workspace/    Project, FileSystemGateway, ProjectIndexer, Room repo
  process/      ProcessRunner with stdout/stderr flows
  services/     ServiceManager + ManagedService base class
  permissions/  PermissionLevel matrix, approval queue, agent policies
  diagnostics/  Structured DiagnosticEvent sink (not string logs)
  configuration/  Typed CodeHubConfig backed by DataStore

editor/       Editor subsystem
  api/          EditorService + EditorBackendProvider
  codeserver/   Local code-server backend (preferred)
  vscodeweb/    Fallback vscode.dev provider

terminal/     Terminal subsystem
  api/          TerminalService + TerminalBackendProvider
  termux/       Termux PTY backend
  local-runtime/ Minimal /system/bin/sh backend
  ssh/          SSH backend via system ssh

build/        Build subsystem
  api/          BuildService + BuildToolProvider
  gradle/       Gradle wrapper invocation, diagnostics parsing, artifact discovery
  cmake/        CMake configure+build with Ninja generator
  ninja/        Direct ninja invocation against build.ninja
  native/       Clang driver for ad-hoc C++ compilation

ai/           AI subsystem
  gateway/      AiGateway + provider clients (offline stub + OpenAI-compatible HTTP)
  models/       ModelDescriptor registry
  agents/       AgentRunner with session lifecycle and approval flow
  tools/        AgentTool interface + 15 builtin tools + impls
  context/      ContextRetriever on top of ProjectIndexer
  permissions/  AgentAuditLog

git/          Git subsystem
  core/         CliGitService over git binary
  github/       HttpGitHubClient for repos/issues/PRs

devtools/     Android developer tools
  logcat/       Streaming logcat collector and parser
  packages/     PackageManager-backed inspector
  processes/    ps -A inspector
  memory/       ActivityManager + native heap snapshot
  vulkan/       StubVulkanInspector (native impl pending)
  device/       Aggregated device snapshot

integrations/ Optional third-party bridges
  pocketpal/    PocketPal AI bridge
  openclaw/     OpenClaw bridge
  hermes/       Hermes Agent bridge
  android-code-studio/  ACS component gate
  opengui/      OpenGUI component gate

app/          Application UI, foreground service, navigation
```

## Service lifecycle

CodeHubService is a foreground service that owns the ServiceManager.
On `onCreate` it calls `serviceManager.startAll()` which starts every
registered ManagedService in registration order. `onDestroy` stops them
in reverse order. Long-running workloads (builds, terminal sessions, AI
sessions) are owned by their respective subsystems, not by the service.

## AI tool flow

1. User sends a message via the UI.
2. AgentRunner assembles a ChatRequest with system prompt + context
   retrieved via ContextRetriever.
3. AiGateway dispatches to the configured provider client.
4. When the provider emits a `ToolCallChunk`, AgentRunner looks up the
   AgentTool by name and asks PermissionDecider to evaluate.
5. Destructive tools block on `AgentApprovalRequired` until the user
   resolves via `AgentRunner.resolveApproval`.
6. Approved tools execute and the result is forwarded back as a tool
   message to the gateway.
7. Every tool call is recorded in AgentAuditLog.

## Permission matrix

| Level             | Tools                                                        |
| ----------------- | ------------------------------------------------------------ |
| READ_ONLY         | read_file, list_directory, search_code, search_symbols,     |
|                   | git_status, git_diff, git_log, read_build_log,              |
|                   | read_logcat, inspect_vulkan                                 |
| WORKSPACE_WRITE   | write_file, create_file, move_file, rename_file              |
| BUILD             | run_build, read_build_log                                    |
| GIT_WRITE         | git_commit, git_push, git_branch, git_tag                    |
| FULL_AUTONOMY     | * (requires explicit approval)                              |

Destructive tools (delete_file, git_push, git_commit, run_command,
run_build, install_package) always trigger an approval request even
when policy allows them, unless `requireApprovalForDestructive` is
explicitly false in the AgentPolicy.

## Diagnostic model

Diagnostics are structured events with kind, severity, status, source,
message, reason, attributes and related event IDs. The same model covers
builds, runtime initialization, process failures, permission denials,
terminal failures, crashes, Vulkan initialization and AI backend failures.

The UI renders human-readable text from these events; agents consume the
same structured data without parsing strings.

## Build flow

1. UI calls `BuildService.enqueue(target)`.
2. DefaultBuildService looks up the `BuildToolProvider` for the target
   tool.
3. Provider runs the build via `ProcessRunner` and captures stdout,
   stderr, exit code, duration, artifacts and diagnostics.
4. Diagnostics are parsed from compiler output (Clang/GCC/Gradle/CMake
   formats).
5. `BuildResult` is emitted via the events flow and added to history.
6. The AI agent can read the most recent build log via the
   `read_build_log` tool to drive patch suggestions.
