# CodeHub Vision

CodeHub is a **development operating environment for Android**, not an
"Android version of VS Code".

The phone is the platform. CodeHub is the orchestration layer that
turns that platform into a place where real engineering work happens:
editing, building, debugging, running, and revising software — including
software as heavy as a PS5 emulator.

## What CodeHub is

CodeHub is an **orchestration layer** sitting between the user and a
stack of execution backends. It does not implement the editor, the
terminal, the build system, the AI runtime, or the Git engine. It
**composes** them, governs them, and gives them a single coherent UI.

```
                    CodeHub
                       |
         +-------------+-------------+
         |             |             |
      Editor        Terminal         AI
         |             |             |
    code-server      Termux      AI Gateway
         |             |             |
         +-------------+-------------+
                       |
                  Build System
                  |              |
               Gradle          Clang
                  |              |
                CMake          Android
                  |              |
                Ninja             |
         +--------+--------+      |
         |                 |     |
        APK              Logcat  |
         |                 |     |
         +--------+--------+     |
                       |
                  Diagnostics
                       |
                   AI Analysis
                       |
                     Patch
                       |
                      Git
```

## What CodeHub is not

- **Not a from-scratch IDE.** CodeHub does not reimplement Termux, VS
  Code, Android Code Studio, OpenClaw, or Hermes. It uses them as
  execution backends behind stable service interfaces.
- **Not an Android port of OpenClaw or Hermes.** Those projects
  contribute agent patterns, approval flows, and tool-call contracts
  — but CodeHub's `ai/agents` and `ai/gateway` are built natively,
  with OpenClaw and Hermes as optional provider backends.
- **Not a Termux clone.** Termux is the **execution layer** for the
  Linux userspace; CodeHub is the orchestration layer above it.
- **Not a PS5 emulator project.** The PS5 emulator is one of the
  heaviest workloads CodeHub can host — its presence proves the
  platform is real, but it is not the reason CodeHub exists.

## Architectural layering

```
Android
   |
   v
CodeHub
   |
   +-- UI
   +-- Workspace
   +-- Service Manager
   +-- Permission Manager
   +-- AI Gateway
   +-- Diagnostics
          |
          v
      Execution Layer
          |
          +-- Termux       (Linux userspace on Android)
          +-- Linux runtime (optional bundled)
          +-- SSH           (remote execution)
```

The execution layer is owned by Termux (primary), an optional bundled
Linux runtime (Phase 8), and SSH (for remote development). CodeHub
never executes commands directly — it always goes through an execution
backend.

## AI Gateway as the single AI boundary

CodeHub's UI never talks directly to a provider. All AI requests go
through `AiGateway`:

```
AI Gateway
    |
    +-- Local llama.cpp     (on-device inference via ggml)
    +-- OpenClaw            (companion agent patterns)
    +-- Hermes              (long-running durable turns)
    +-- Gemini              (online APIs)
    +-- Other providers     (OpenAI, Anthropic, custom)
```

The Gateway enforces:
- **Permissions** — every tool call is evaluated against the
  permission matrix (READ_ONLY → FULL_AUTONOMY) and the dangerous-
  command regex bank before execution.
- **Workspace boundaries** — agents cannot touch files outside the
  active workspace without explicit approval.
- **Approval policies** — destructive operations (delete, git push,
  git commit, run_command, run_build) trigger an approval request
  even when policy allows them.
- **Audit trail** — every tool call is recorded in AgentAuditLog.

## Reference projects

| Project | Role in CodeHub | License | Porting stance |
|---------|-----------------|---------|----------------|
| termux-app | Execution layer (PTY, terminal emulator, bootstrap) | Apache 2.0 (terminal-emulator) + GPLv3 (app) | Port Apache 2.0 PTY verbatim. Re-implement GPLv3 service layer. |
| termux-packages | Dependency knowledge base | Apache 2.0 | Codegen a dependency JSON snapshot for TermuxBootstrap pre-flight checks. |
| termux-x11 | Optional X11 path for Linux GUI tools | GPL-3.0 + Chromium BSD input | External APK dependency. Port BSD input package only. |
| llama.cpp | On-device LLM inference | MIT | Vendor as native lib + JNI bindings in integrations/pocketpal. |
| ggml | Tensor library for llama.cpp | MIT | Vendor alongside llama.cpp. Provides Vulkan + ARM CPU backends. |
| android-code-studio | Architectural reference for LSP, Gradle Tooling, Logcat, APK install | GPL-3.0 | Reference only. Re-implement contracts, do not copy code. |
| OpenClaw (TS) | Agent loop + approval protocol + tool-call repair | Permissive | Design patterns port into ai/gateway + ai/permissions. |
| OpenClaw-Android | Service lifecycle + keep-alive UX + typed JS bridge | GPL-3.0 (terminal) + unknown (app) | Port ~5 small Kotlin files (service, boot receiver, command runner). |
| Hermes (Python) | DANGEROUS_PATTERNS regex bank + IterationBudget + subagent lifecycle | MIT | Verbatim-portable artifacts already ported. |
| Hermes-Android | Durable turn lifecycle + permission UX patterns | Unknown | Contract patterns only — no native code. |
| PocketPal-AI | DownloadModule + HardwareInfoModule + GGUF memoryEstimator | MIT | Pure-Kotlin modules drop-in portable. |

## What "done" means

CodeHub transitions from architectural prototype to **true mobile
engineering workstation** when this pipeline runs end-to-end on an
Android phone:

```
Workspace
   ↓
Termux
   ↓
Git
   ↓
CMake
   ↓
Ninja
   ↓
Clang
   ↓
Build
   ↓
APK
   ↓
Install
   ↓
Launch
   ↓
Logcat
   ↓
AI analysis
   ↓
Patch
   ↓
Rebuild
```

When a build failure on a real C++/Vulkan project (such as the PS5
emulator) triggers AI analysis, produces a patch, applies it, and
rebuilds successfully — without leaving the phone — CodeHub is real.

Everything else in the roadmap is in service of that loop.
