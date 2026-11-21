# AI Agent Guidelines — MuxGrid (`android-client`)

This file is the single source of truth for AI coding assistants working in this repository (Antigravity, OpenCode, Claude Code, Codex, and compatible tools).

## Project Context

MuxGrid is a unified native Android & Android TV client for Tvheadend (`4.3+`) built with Kotlin, Jetpack Compose, and Gradle (`minSdk 28`, JDK 17).

- **Modules:** `:app`, `:core:model`, `:core:htsp`, `:core:data`, `:core:player`, `:core:designsystem`, `:feature:mobile`, `:feature:tv`
- **Key Docs:** [README.md](README.md), [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md), [docs/DESIGN.md](docs/DESIGN.md), [docs/PROTOCOL.md](docs/PROTOCOL.md), [gradle/libs.versions.toml](gradle/libs.versions.toml)

---

## 1. Git Hygiene & Secrets

- **Never commit secrets or local config:** Keep credentials, signing keys, and local environment files out of git (`*.jks`, `*.keystore`, `keystore.properties`, `local.properties`, `.env`, `.env.*`, `google-services.json`).
- **No hardcoded private data:** Never embed real server hostnames, private LAN/VPN IPs, usernames, passwords, or tokens in source code, tests, logs, or docs—use placeholders or synthetic test fixtures.
- **Inspect before staging:** Always check `git status` and `git diff` before committing. Do not stage unrelated edits, build outputs, or temporary files.
- **Protect history:** Never rewrite shared git history (`push --force`, `reset --hard` on pushed commits) or bypass git hooks unless explicitly requested by the user.

## 2. Verify Before Commit

- **Always verify after your final edit** before committing or reporting a task as done.
- **Standard verification** (matches [CI](.github/workflows/ci.yml)):
  ```bash
  ./gradlew test assembleDebug
  ```
- During iteration you may run targeted module tests (e.g., `./gradlew :core:htsp:test`), but run the full `./gradlew test assembleDebug` check before committing code changes.
- Only report tests, builds, or lints as passing if you actually ran them after your last edit and observed them pass.

## 3. Parallel Device Deployment (ADB)

When deploying to multiple connected Android / Android TV devices:

- Inspect connected devices and their ABIs first (`adb devices -l`, `adb -s <serial> shell getprop ro.product.cpu.abi`).
- Match each device to the corresponding ABI-split APK (`app/build/outputs/apk/debug/app-arm64-v8a-debug.apk` or `app-armeabi-v7a-debug.apk`).
- **Deploy in parallel** rather than sequentially by launching `adb -s <serial> install -r <apk>` concurrently and waiting for all jobs:
  ```bash
  adb -s <device1> install -r app/build/outputs/apk/debug/app-arm64-v8a-debug.apk &
  adb -s <device2> install -r app/build/outputs/apk/debug/app-armeabi-v7a-debug.apk &
  wait
  ```
- **Evaluating TV fluidity (debug vs. release):** Always profile and judge Android TV smoothness on an R8-minified, non-debuggable `release` build (optionally followed by `adb -s <tv> shell cmd package compile -m speed -f io.github.corebonts.muxgrid` so ART compiles immediately instead of waiting for idle dexopt). Android refuses to AOT-compile `DEBUGGABLE` APKs (`run-from-apk` / `quicken`), making Compose several times slower on entry-level 32-bit ARM TVs. For local in-place testing over a debug install without wiping app data, sign `app/build/outputs/apk/release/app-<abi>-release-unsigned.apk` into `app/build/tmp/` with `~/.android/debug.keystore` via `apksigner`—never wire `debug` signing into the `release` build type in [app/build.gradle.kts](app/build.gradle.kts).

## 4. Dependencies & FOSS Policy

- **Prefer FOSS libraries:** Use Free and Open Source Software with licenses compatible with this project (Apache-2.0 source code; GPL-3.0-or-later distributed APKs due to `libmpv`/`FFmpeg`). Never introduce proprietary SDKs, closed-source binaries, or telemetry/analytics trackers.
- **Justify every new dependency:** Do not add a third-party library for trivial functionality—such as a single utility method, small data transformation, or anything already handled by the Kotlin standard library, Jetpack/AndroidX, or existing libraries in [gradle/libs.versions.toml](gradle/libs.versions.toml). Only introduce a new library when it solves a substantial, non-trivial problem whose in-house maintenance cost would be disproportionate.
- **Catalog & attribution:** Declare approved dependencies in [gradle/libs.versions.toml](gradle/libs.versions.toml) and update [NOTICE](NOTICE) / [README.md](README.md) when required by the library's license.

## 5. Architecture & Code Conventions

- **Keep `:core:player` isolated:** Maintain the `PlaybackEngine` abstraction boundary in `:core:player` so GPL-licensed `libmpv` bindings stay decoupled from the rest of the Apache-2.0 codebase.
- **Respect form-factor separation:** Keep phone/tablet UI in `:feature:mobile` and 10-ft D-pad TV UI in `:feature:tv`, sharing state/models via `:core:*` and visual tokens via `:core:designsystem` ([docs/DESIGN.md](docs/DESIGN.md)).
- **Protect Android TV Compose performance:** On entry-level 32-bit ARM TVs, avoid patterns that trigger UI-thread jank:
  - Keep D-pad focus memory non-observable (`TvFocusMemory`) and overlay the collapsible TV navigation rail over a fixed-width content area so focus/rail transitions never recompose or resize lazy grids/timelines.
  - Never use `SubcomposeAsyncImage`, `tv.material3.Surface` (`SurfaceGlowNode` + multi-frame scale animations), or per-item `animate*AsState` inside dense TV lazy lists/grids; use `AsyncImage` with Coil memory/disk caching (`PiconFetcher.KeyerImpl` + `DiskCache`), lightweight focusable containers with merged `clearAndSetSemantics`, and explicit `contentType`.
  - Keep HTSP event ingestion, EPG sorting, and periodic `NowNext` refreshes off the main thread (`Dispatchers.Default`), and keep pure-JVM Compose models listed in [compose_compiler_config.conf](compose_compiler_config.conf).
- **Minimal, clean diffs:** Keep changes focused on the task, reuse existing helpers, and preserve unrelated comments and docstrings.
