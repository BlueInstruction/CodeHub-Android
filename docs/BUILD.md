# CodeHub Build & Develop

CodeHub is built with Gradle 8.10+ and AGP 8.7+.

## Local requirements

- Android Studio Koala Feature Drop or newer (or just JDK 17 + Gradle)
- Android SDK Platform 35
- Android Build Tools 35.0.0
- (Optional) Termux installed on the target device for terminal backend

## Building

```sh
./gradlew :app:assembleDebug
```

The debug APK installs with the `.debug` applicationId suffix:

```
codehub.debug
```

## Running tests

Unit tests cover the core invariants of each module:

```sh
./gradlew test
```

## Module graph

See [docs/ARCHITECTURE.md](ARCHITECTURE.md) for the module map and
service lifecycle.

## Adding a new build tool provider

1. Implement `BuildToolProvider` in a new module under `build/`.
2. Bind it via a Hilt `@Provides` returning a
   `Map<BuildTool, BuildToolProvider>`.
3. The default `BuildService` picks it up automatically.

## Adding a new AI provider

1. Implement `AiProviderClient` (and `AiProviderConfig` for it).
2. Register with `AiGateway.registerProvider(config)` at startup.
3. The provider is immediately selectable from the UI.
