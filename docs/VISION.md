# CodeHub Studio Vision

CodeHub Studio is an **Android development workstation**, not a VS Code
clone for Android. The app manages the entire development environment
on the phone; the editor, AI, build system, terminal, and Android
tooling operate as layers on top of this environment.

## What the user must be able to do

Open CodeHub on a phone with nothing pre-installed (no JDK, no Android
SDK, no NDK, no Gradle, no CMake, no Clang) → tap **New Android
Project** → get a working APK installed and running on the same device,
with logcat streaming and AI-assisted fix-and-rebuild.

If that loop doesn't work, CodeHub isn't a mobile engineering
workstation. It's a terminal wrapper.

## Three development layers

CodeHub serves three distinct development layers. They share
infrastructure (workspace, terminal, toolchain, AI gateway) but have
different pipelines:

### Layer 1 — Android Application Development (primary use case)

```
New Project → SDK/NDK → Gradle/AGP → Kotlin/Java/C++ → Build → APK/AAB → Install → Run → Logcat → Debug → Git → AI
```

The user must be able to create a new Android project without needing
to know how to manually set up the environment.

### Layer 2 — Native/System Development

```
CMake → Ninja → Clang/LLVM → NDK → JNI → Vulkan → native libraries
```

Crucial for projects like emulators, Turnip, Mesa, VKD3D, llama.cpp,
ggml. This layer shares the toolchain with Layer 1 but has its own
build pipeline (CMake + Ninja + Clang, not Gradle/AGP).

### Layer 3 — AI Development Environment

The AI is not a standalone chatbot. It must be able to access:

- Workspace + source tree
- Gradle build output
- Git diff
- Logcat
- Compiler diagnostics

It should then understand, for example:

```
Gradle build failed
  → Kotlin compilation error
    → source location
      → relevant project files
        → proposed patch
          → rebuild
```

This makes the AI an integral part of the development cycle itself.

## Architecture

```
CodeHub Studio
│
├── Project Manager
│   ├── New Android Project
│   ├── Existing Project
│   ├── Templates
│   └── Workspace
│
├── Android Toolchain
│   ├── JDK
│   ├── Android SDK
│   ├── Build Tools
│   ├── Platform SDKs
│   ├── NDK
│   ├── CMake
│   ├── Ninja
│   └── Clang/LLVM
│
├── Build System
│   ├── Gradle
│   ├── AGP
│   ├── CMake
│   ├── Ninja
│   └── Native/NDK
│
├── Runtime
│   ├── Install APK
│   ├── Launch
│   ├── Logcat
│   ├── Crash/ANR
│   └── Debug
│
├── Development Environment
│   ├── Editor
│   ├── Kotlin LSP
│   ├── Java LSP
│   ├── C/C++ LSP
│   └── Git
│
├── AI
│   ├── Project Context
│   ├── Build Analysis
│   ├── Logcat Analysis
│   ├── Code Modification
│   └── Rebuild/Verify
│
└── Advanced Native Workloads
    ├── Vulkan
    ├── Mesa
    ├── VKD3D
    ├── Emulators
    └── llama.cpp/ggml
```

## Toolchain management is part of the product

CodeHub does **not** assume the user already has any of the following
installed:

- JDK
- Android SDK
- NDK
- Gradle
- CMake
- Ninja
- Clang
- Git
- adb

It must have a dedicated layer responsible for:

1. **Discovering** what's already installed (Termux packages, system
   paths, previously-provisioned SDKs in CodeHub's own data dir).
2. **Installing** missing components (Termux `pkg install` for
   clang/cmake/ninja/git; `sdkmanager` for Android SDK / Build Tools /
   Platform SDKs / NDK; direct download for JDK).
3. **Managing** versions (pinning known-good combinations, upgrading,
   rolling back).
4. **Verifying** compatibility (JDK ↔ AGP ↔ Gradle version matrix; NDK
   ↔ CMake version; Build Tools ↔ Platform SDK).

This is the lesson from Android Code Studio: the goal isn't to copy its
code, but to understand how it manages a complete Android environment
inside `/data/data/.../files/usr`, and how it handles dpkg, repositories,
toolchains, conflicts, and recovery.

## Minimum Viable Product

The MVP is **not** 36 modules. It is this pipeline:

```
New Android Project
        ↓
Generate project
        ↓
Provision JDK + SDK + Build Tools
        ↓
Resolve Gradle + AGP
        ↓
Sync
        ↓
Build Debug APK
        ↓
Install
        ↓
Launch
        ↓
Logcat
        ↓
Fix
        ↓
Rebuild
```

Once this works reliably, we can add C/C++, Vulkan, advanced AI, and
emulators. Until it works, everything else is premature.

## What CodeHub is not

- **Not a from-scratch IDE.** CodeHub does not reimplement Termux, VS
  Code, Android Code Studio, OpenClaw, or Hermes. It uses them as
  execution backends behind stable service interfaces.
- **Not an Android port of OpenClaw or Hermes.** Those are optional AI
  provider backends behind the AI Gateway, not the core.
- **Not a Termux clone.** Termux is the execution layer; CodeHub is the
  orchestration layer above it.
- **Not a PS5 emulator project.** The PS5 emulator is one of the
  heaviest workloads CodeHub can host — its presence proves the
  platform is real, but it is not the reason CodeHub exists.

## Reference projects

| Project | Role in CodeHub | License | Porting stance |
|---------|-----------------|---------|----------------|
| termux-app | Execution layer (PTY, terminal emulator, bootstrap) | Apache 2.0 (terminal-emulator) + GPLv3 (app) | Port Apache 2.0 PTY verbatim (DONE). Re-implement GPLv3 service layer. |
| termux-packages | Dependency knowledge base | Apache 2.0 | Codegen a dependency JSON snapshot for pre-flight checks. |
| termux-x11 | Optional X11 path for Linux GUI tools | GPL-3.0 + Chromium BSD input | External APK dependency. Port BSD input package only. |
| llama.cpp | On-device LLM inference | MIT | Vendor as native lib + JNI bindings. Layer 3 concern. |
| ggml | Tensor library for llama.cpp | MIT | Vendor alongside llama.cpp. |
| android-code-studio | **Behavioral reference for toolchain management** | GPL-3.0 | Reference only. Study how it manages the environment inside `/data/data/.../files/usr`. Do not copy code. |
| OpenClaw (TS) | Agent loop + approval protocol + tool-call repair | Permissive | Design patterns port into ai/gateway + ai/permissions. |
| Hermes (Python) | DANGEROUS_PATTERNS + IterationBudget + subagent lifecycle | MIT | Already ported. |
| PocketPal-AI | DownloadModule + HardwareInfoModule | MIT | Pure-Kotlin modules drop-in portable. Layer 3 concern. |
