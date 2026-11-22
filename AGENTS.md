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
- **Evaluating TV fluidity (`noDebug` vs. `release`):** Android refuses to AOT-compile `DEBUGGABLE` APKs (`run-from-apk` / `quicken`), making Compose several times slower on entry-level 32-bit ARM TVs.
  - For fast local TV iteration, use the `noDebug` build type (`./gradlew assembleNoDebug -x lintVitalNoDebug`), which sets `isDebuggable = false` and `matchingFallbacks = ["release"]` while skipping R8 and signing with `debug` so `app/build/outputs/apk/noDebug/app-<abi>-noDebug.apk` installs in-place over debug builds (optionally followed by `adb -s <tv> shell cmd package compile -m speed -f io.github.corebonts.muxgrid` so ART compiles immediately instead of waiting for idle dexopt).
  - For reproducible scroll profiling without touching the user's real app install, deploy an isolated `.demo` `noDebug` build compiled with `-m speed`, run `python3 scripts/bench_tv_scroll.py --serial <tv> --pkg io.github.corebonts.muxgrid.demo` (see [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md#5-android-tv-scroll-benchmarking) for the full procedure and metrics), and always revert any temporary `applicationIdSuffix = ".demo"` edits and uninstall `.demo` afterward.
  - For final profiling of R8-minified code over a debug/noDebug install without wiping app data, build `assembleRelease`, `zipalign -f -p 4` `app/build/outputs/apk/release/app-<abi>-release-unsigned.apk` into `app/build/tmp/`, sign with `~/.android/debug.keystore` via `apksigner` (and pass `--no-incremental` to `adb install -r` if streamed/incremental install fails with `INSTALL_PARSE_FAILED_NOT_APK`)—never wire `debug` signing into the `release` build type in [app/build.gradle.kts](app/build.gradle.kts).

## 4. Dependencies & FOSS Policy

- **Prefer FOSS libraries:** Use Free and Open Source Software with licenses compatible with this project (Apache-2.0 source code; GPL-3.0-or-later distributed APKs due to `libmpv`/`FFmpeg`). Never introduce proprietary SDKs, closed-source binaries, or telemetry/analytics trackers.
- **Justify every new dependency:** Do not add a third-party library for trivial functionality—such as a single utility method, small data transformation, or anything already handled by the Kotlin standard library, Jetpack/AndroidX, or existing libraries in [gradle/libs.versions.toml](gradle/libs.versions.toml). Only introduce a new library when it solves a substantial, non-trivial problem whose in-house maintenance cost would be disproportionate.
- **Catalog & attribution:** Declare approved dependencies in [gradle/libs.versions.toml](gradle/libs.versions.toml) and update [NOTICE](NOTICE), [README.md](README.md), and `AppNotices.attributions` in [AppNotices.kt](core/model/src/main/kotlin/io/github/corebonts/muxgrid/core/model/AppNotices.kt) together when required by the library's license (`AppNoticesTest` verifies that `NOTICE` and `AppNotices.attributions` stay in sync).

## 5. Architecture & Code Conventions

- **Keep `:core:player` isolated:** Maintain the `PlaybackEngine` abstraction boundary in `:core:player` so GPL-licensed `libmpv` bindings stay decoupled from the rest of the Apache-2.0 codebase.
- **Respect form-factor separation:** Keep phone/tablet UI in `:feature:mobile` and 10-ft D-pad TV UI in `:feature:tv`, sharing state/models via `:core:*` and visual tokens via `:core:designsystem` ([docs/DESIGN.md](docs/DESIGN.md)).
- **Protect Android TV Compose performance:** On entry-level 32-bit ARM TVs, avoid patterns that trigger UI-thread jank or GPU layer stalls:
  - Keep D-pad focus memory non-observable (`TvFocusMemory`, including `guideWindowStartEpochSec`) and overlay the collapsible TV navigation rail over a fixed-width content area so focus/rail transitions never recompose or resize lazy grids/timelines.
  - Never use `SubcomposeAsyncImage`, `tv.material3.Surface` (`SurfaceGlowNode`), `tv.material3.Text` (wraps every label in `Modifier.graphicsLayer`, forcing offscreen HWUI FBO allocations), rounded-rect `.clip(...)` on dense TV cards/badges/EPG cells, or per-item `animate*AsState` / `MutableInteractionSource` allocations inside TV lazy lists/grids/timelines. Instead use `:feature:tv`'s package-internal `BasicText` `Text(...)`, draw-phase `Modifier.tvFocusableSurface` with `clickable(interactionSource = null, indication = null)`, rectangular `.clipToBounds()` where overflow clipping is needed (e.g., EPG sticky-text cells), `AsyncImage` with Coil memory/disk caching (`PiconFetcher.KeyerImpl` + `DiskCache` + `ChannelPiconBadge` warm LRU cache), merged `clearAndSetSemantics`, and explicit `contentType`.
  - Keep `TimelineRuler` and `TimelineRowTrack` horizontal scrolling on a GPU `graphicsLayer { translationX = ... }` anchored to the buffer window via `computeTimelineBufferWindow` (preserve its half-hour hysteresis margin—tighter margins double buffer rebuild spikes during horizontal D-pad scrolling), and use synchronous `FocusRequester.requestFocus()` + `findAdjacentEpgEvent` binary search for EPG Left/Right moves.
  - Keep HTSP event ingestion, EPG sorting, and periodic `NowNext` refreshes off the main thread (`Dispatchers.Default`), and keep pure-JVM Compose models listed in [compose_compiler_config.conf](compose_compiler_config.conf).
- **Minimal, clean diffs:** Keep changes focused on the task, reuse existing helpers, and preserve unrelated comments and docstrings.
