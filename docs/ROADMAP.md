# CodeHub Roadmap

Tracks the README implementation roadmap with current status.

## Phase 0: Architecture

- [x] Android application shell (`app` module)
- [x] Workspace model (`core.workspace`)
- [x] Project manager (`WorkspaceRepository`, `ProjectInferrer`)
- [x] Service manager (`core.services.ServiceManager`)
- [x] Terminal API (`terminal.api`)
- [x] Editor API (`editor.api`)
- [x] AI Gateway API (`ai.gateway`)
- [x] Diagnostic event model (`core.diagnostics.DiagnosticEvent`)

## Phase 1: Local development

- [x] Termux integration (`terminal.termux.TermuxBackendProvider`)
- [x] Shell execution (`core.process.JavaProcessRunner`)
- [x] Git (`git.core.CliGitService`)
- [ ] Clang availability check wired into build
- [ ] CMake wired into the build subsystem
- [ ] Ninja wired into the build subsystem
- [ ] Python, Node.js, Java detection (Termux package checks)
- [x] Build output capture (`build.api.BuildResult`)
- [ ] Command history persistence

## Phase 2: IDE

- [x] code-server integration scaffold (`editor.codeserver`)
- [x] Local WebView placeholder (UI present, embedding pending)
- [ ] Workspace restore on app relaunch
- [ ] Editor lifecycle management (lifecycle tied to ServiceManager)
- [ ] Keyboard and mouse support (edge-to-edge is enabled)
- [x] Optional vscode.dev backend (`editor.vscodeweb`)

## Phase 3: Android build and test

- [x] Android SDK detection hook (termux-package check stub)
- [ ] Android NDK detection
- [x] Gradle (`build.gradle.GradleBuildProvider`)
- [ ] APK installation (pm install / am install flows)
- [ ] Application launch (`devtools.packages.PackageInspector.launch`)
- [x] Logcat (`devtools.logcat.CliLogcatService`)
- [ ] Crash diagnostics (dropbox parsing)
- [x] Device information (`devtools.device.DeviceMonitor`)
- [x] Vulkan diagnostics scaffold (`devtools.vulkan.StubVulkanInspector`)

## Phase 4: Offline AI

- [x] PocketPal integration bridge (`integrations.pocketpal`)
- [x] Local model discovery (`ai.models.ModelRegistry`)
- [ ] Code explanation (provider prompt templates)
- [ ] Compiler error analysis (`ai.tools.read_build_log` exists, prompt TBD)
- [ ] Linker error analysis
- [ ] Logcat analysis
- [ ] Patch generation
- [ ] Diff review

## Phase 5: Engineering agents

- [x] Tool execution (`ai.tools.AgentTool` and impls)
- [x] Workspace permissions (`core.permissions`)
- [x] Build tools (`build.api.BuildToolProvider`)
- [x] Git tools (descriptors registered; impls pending)
- [x] Approval system (`DefaultPermissionDecider`)
- [x] Agent sessions (`ai.agents.AgentRunner`)
- [x] Context retrieval (`ai.context.DefaultContextRetriever`)
- [x] Agent audit logs (`ai.permissions.AgentAuditLog`)

## Phase 6: Online agents

- [x] OpenClaw backend bridge (`integrations.openclaw`)
- [x] Hermes Agent backend bridge (`integrations.hermes`)
- [ ] Gemini backend (HttpProviderClient supports OpenAI-compatible APIs)
- [ ] API-compatible providers
- [ ] Provider selection UI
- [ ] Authentication management (DataStoreConfigurationStore ready)

## Phase 7: Systems development

- [ ] C/C++ language services
- [ ] Language Server Protocol
- [ ] Vulkan tooling (StubVulkanInspector placeholder)
- [ ] SPIR-V tools
- [ ] Native crash analysis
- [ ] Performance monitoring
- [x] Large repository indexing (`core.workspace.index.DefaultProjectIndexer`)
- [ ] Cross-compilation workflows

## Phase 8: Independent runtime

- [ ] Reduce external application dependencies
- [ ] Optional bundled Linux runtime
- [ ] Local compiler/runtime management
- [ ] Reproducible toolchain installation
