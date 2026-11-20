# Implementation Plan — Lightweight Tvheadend Android Client (Phone + TV)

This plan is written for any human engineer or coding agent to implement end-to-end. It specifies **architectural decisions, module contracts, acceptance criteria, and known pitfalls**, while intentionally leaving internal class layouts and boilerplate flexible so implementers can use idiomatic patterns without hitting artificial dead ends.

---

## 1. Architectural Decisions (The "Why")

### 1.1 Protocol & Profile Split (Best-in-Class Hybrid)

Use **HTSP (port `9982`)** for control, real-time state, authentication tickets, and growing recordings; use **HTTP (port `9981`)** with Tvheadend's **`pass` (MPEG-TS)** profile for live media streaming and **`matroska` (`.mkv`)** for DVR storage.

| Function | Mechanism | Why |
| :--- | :--- | :--- |
| **Channels, Tags, EPG, DVR, Autorec** | **HTSP (`9982`)** `enableAsyncMetadata` | Single persistent TCP socket with real-time push updates (`channel*`, `event*`, `dvrEntry*`). Zero polling, instant startup, live recording status updates. |
| **Authentication** | **HTSP SHA-1 challenge-response + `getTicket`** | Password is never sent in plaintext URLs; short-lived tickets authorize HTTP stream/recording URLs cleanly without Basic/Digest `401` issues. |
| **Live TV & Radio Playback** | **HTTP `pass` (MPEG-TS)** via `?ticket=...&profile=pass` | Raw DVB broadcast pass-through with zero server transcoding or remuxing overhead (<1s zap on LAN). Preserves all audio tracks, DVB subtitles, and radio streams. |
| **Live Pause & Rewind (Timeshift)** | **`libmpv` demuxer ring buffer** (`cache=yes`, `demuxer-max-back-bytes`, optional `cache-on-disk`) | Instant 0ms scrub within the buffered live window without building a fragile HTSP `muxpkt` elementary-stream bridge. |
| **Completed Recordings** | **HTTP `/dvrfile/<id>?ticket=...`** (`.mkv` DVR profile) | Direct byte-range seeking with native Matroska cues; zero conflict with existing Jellyfin `TV Recordings` library. |
| **In-Progress (Growing) Recordings** | **HTSP `fileOpen` / `fileRead` / `fileStat`** via an in-process `127.0.0.1` HTTP Range bridge | Tvheadend's HTTP `/dvrfile/<id>` snapshots `Content-Length` at request open time (causing premature EOF or `416` on seek). HTSP VFS dynamically tracks growing file sizes while keeping `libmpv` fed with normal HTTP Range reads. |
| **Channel Picons** | **HTSP `fileOpen("/imagecache/...")` or HTTP `/imagecache/<id>`** cached via Coil 3 | Zero credential leaks; instant local disk/memory cache after first fetch. |

> See [docs/PROTOCOL.md](./docs/PROTOCOL.md) for wire details and [docs/SERVER_SETUP.md](./docs/SERVER_SETUP.md) for required Tvheadend server settings.

### 1.2 Playback Engine: `libmpv` + Media3 `MediaSessionService`

- **Core Decoder/Renderer (`libmpv` via Maven Central AAR):**
  European DVB-T broadcasts (such as Hungarian MindigTV) deliver **`1440x1080i` interlaced H.264**, **`MP2` / `AC3` / `AAC` audio**, and **bitmap DVB subtitles (`DVBSUB`)**.
  `libmpv` handles all of these natively out of the box without NDK compilation:
  - **Phone:** `hwdec=mediacodec-copy` + `deinterlace=yes` (prevents 1080i combing artifacts on mobile SoCs).
  - **Android TV (e.g. TCL C635):** `hwdec=mediacodec` default (zero-copy hardware decode & TV-panel deinterlacing for low RAM/CPU), with a user toggle in Settings to switch to `mediacodec-copy` + deinterlace if needed.
  - Native `MP2`/`AC3`/`AAC` audio and bitmap `DVBSUB` rendering with instant track switching.
- **System Integration (`androidx.media3:media3-session`):**
  Wrap the `PlaybackEngine` inside a **`MediaSessionService`** (using a lightweight `SimpleBasePlayer` adapter or Media3 `Player` bridge) so Android handles:
  - **Background audio playback** on phone (radio channels and live TV audio-only when minimized, detaching video output via `vid=no` while keeping audio alive).
  - System media notification, lock-screen controls, Bluetooth/headset media keys, AudioFocus, and audio-becoming-noisy pauses.
  - Clean **Picture-in-Picture (PiP)** transitions on phone.
- **Abstraction Boundary:**
  Define a clean `PlaybackEngine` interface in `:core:player`. Neither UI modules nor repositories may import `libmpv` classes directly.

### 1.3 Single Unified APK (Phone/Tablet + Android TV)

Build **one APK** containing both form factors:
- Declare both `LAUNCHER` and `LEANBACK_LAUNCHER` intent filters, with `<uses-feature android:name="android.hardware.touchscreen" android:required="false" />`.
- Detect form factor once at entrypoint (`PackageManager.FEATURE_LEANBACK` or `UiModeManager.currentModeType == Configuration.UI_MODE_TYPE_TELEVISION`) and mount either the TV root composable (`:feature:tv`) or Phone/Tablet root composable (`:feature:mobile`).
- Share 100% of protocol, state, repositories, playback engine, and design tokens across both targets.

---

## 2. Modern Tech Stack (2026 Baseline)

> **Version Pinning Guidance:** Always verify the latest mutually compatible stable versions (AGP, Kotlin, Compose BOM, compileSdk) in `gradle/libs.versions.toml` when bootstrapping Milestone 0. Do not mix alpha APIs where stable equivalents exist.

| Layer | Recommended Choice | Notes |
| :--- | :--- | :--- |
| **Language** | Kotlin `2.2+` + Coroutines + `StateFlow` | Strict concurrency; zero RxJava. |
| **Build** | Gradle Kotlin DSL + Version Catalog (`libs.versions.toml`) | Multi-module builds with fast incremental compilation; Java 11/17 bytecode target (required by Coil 3). |
| **Mobile UI** | Jetpack Compose BOM (`2026.11.01` / Compose `1.13+`, Material 3 `1.5+`) | Supports native progressive blur & dialog/popup blur for iOS/iPadOS-style frosted glass. |
| **TV UI** | Compose for TV (`androidx.tv:tv-material:1.1.0+`) | Never use legacy Leanback XML (`androidx.leanback`). |
| **Navigation** | Stable type-safe Navigation Compose (`androidx.navigation:navigation-compose` with `@Serializable` routes) | Avoid pre-release `navigation3` alpha unless it reaches stable before implementation. |
| **Playback Core** | `libmpv` Maven Central AAR (`dev.jdtech.mpv:libmpv` or `io.github.abdallahmehiz:mpv-android-lib`) | Verify active artifact coordinates on Maven Central during M0; zero custom NDK build required. |
| **Media Session / Background** | Jetpack Media3 (`androidx.media3:media3-session:1.11.1+`) | Foreground `MediaSessionService` (`FOREGROUND_SERVICE_MEDIA_PLAYBACK`), PiP, AudioFocus. |
| **Images (Picons)** | Coil 3 (`io.coil-kt.coil3:coil-compose` + `coil-network-okhttp`, `3.6.x+`) | Aggressive disk + memory cache for channel logos. |
| **Local Storage** | Jetpack DataStore (Preferences / Proto) + Android Keystore encryption | Server profiles, playback preferences, favorites, local recording resume positions. |
| **DI** | Lightweight constructor injection / Koin / Metro / Hilt | Choose the simplest option that keeps `:core:htsp` pure JVM without annotation-processor bloat. |
| **Testing** | JUnit 5 / Kotlin Test + Coroutines Test + Turbine | Pure JVM tests for HTSP codec, state reducer, and loopback range proxy. |

---

## 3. Module Map & Boundaries

Keep the dependency graph strictly acyclic and shallow:

```text
:app                 (AndroidManifest, MainActivity, DI graph, form-factor router)
 ├── :feature:mobile (Phone & tablet screens, touch player HUD, PiP, mini-player)
 ├── :feature:tv     (10-foot Compose for TV screens, D-pad player HUD, quick zap OSD)
 ├── :core:designsystem (Colors, typography, glass surfaces, adaptive tokens, shared badges)
 ├── :core:player    (PlaybackEngine interface, MpvPlaybackEngine, HtspLoopbackProxy, MediaSessionService)
 ├── :core:data      (TvheadendRepository, reactive in-memory store, DataStore prefs, resume positions)
 ├── :core:htsp      (PURE JVM Kotlin: binary htsmsg codec, TCP socket client, reconnect/heartbeat, RPCs)
 └── :core:model     (PURE JVM Kotlin: immutable domain models & enums)
```

### Key Store Decision (Keeping It Lightweight)
For typical homelab setups (15–200 channels, ~7 days of EPG), holding live HTSP state in a **reactive in-memory `StateFlow` store** inside `:core:data` gives instantaneous filtering, searching, and Now/Next lookups with minimal RAM and zero SQLite schema migrations. Only persist **server connection settings, favorites, playback preferences, and recording resume positions** in DataStore. (Room can be added later as an optional cache if scaling to thousands of channels.)

---

## 4. Phased Implementation Milestones

Each milestone must be independently testable before moving to the next.

### Milestone 0 — Project Skeleton & Server Smoke Check
- Scaffold multi-module Gradle project with `libs.versions.toml`, Compose BOM, Compose for TV, Coil 3, DataStore, and `libmpv` AAR.
- Configure unified `AndroidManifest.xml` (`LAUNCHER` + `LEANBACK_LAUNCHER`, `touchscreen required=false`, `FOREGROUND_SERVICE_MEDIA_PLAYBACK`, `supportsPictureInPicture="true"`, `usesCleartextTraffic` scoped cleanly via Network Security Config for RFC1918 LAN/VPN addresses).
- **Acceptance:** Empty app builds cleanly (`assembleDebug`) and launches on both an Android phone emulator/device and an Android TV emulator/TCL C635, correctly detecting form factor.

### Milestone 1 — Pure Kotlin HTSP Client (`:core:htsp` & `:core:data`)
- Implement `htsmsg` binary encoder/decoder (Map, List, S64, Str, Bin, Dbl, Bool) with comprehensive JVM unit tests (including negative S64 values, nested lists/maps, UTF-8 Hungarian accented strings).
- Implement TCP connection manager: `hello` -> SHA-1 `authenticate` -> `enableAsyncMetadata`, request/response sequence ID (`seq`) correlation, automatic reconnect with exponential backoff, and clean shutdown.
- Map incoming push messages (`channelAdd/Update/Delete`, `tag*`, `eventAdd/Update/Delete`, `dvrEntryAdd/Update/Delete`) into `:core:data` reactive `StateFlow` snapshots with Now/Next helpers.
- Implement RPCs: `getTicket`, `addDvrEntry`, `cancelDvrEntry`, `deleteDvrEntry`, `fileOpen`, `fileRead`, `fileStat`, `fileClose`.
- **Acceptance:** Unit tests pass for binary framing & state reduction; integration smoke test against live Tvheadend server (`tv-server`) connects, lists all channels + Now/Next EPG + recordings, and handles disconnect/reconnect cleanly.

### Milestone 2 — Playback Engine, Live Timeshift & Growing Recordings (`:core:player`)
- Implement `PlaybackEngine` backed by `libmpv` (`MpvPlaybackEngine`):
  - Expose reactive `StateFlow<PlaybackState>` (playing/paused/buffering/error, `positionMs`, `durationMs`, `liveEdgeOffsetMs`, `bufferWindowMs`, audio tracks, subtitle tracks, video dimensions).
  - Configure `pass` HTTP live playback with fast startup probe tuning and live ring buffer (`cache=yes`, configurable `demuxer-max-bytes` / `demuxer-max-back-bytes`, `cache-on-disk=yes` into `context.cacheDir` with cleanup on stop).
  - Enforce **single-tuner safety**: explicitly stop/close the active stream and wait for teardown before opening the next channel stream.
  - Configure hardware decode presets (`mediacodec-copy` + `deinterlace=yes` default on phone; `mediacodec` default on TV; user-overridable in Settings).
- Implement `HtspLoopbackProxy` (`127.0.0.1:<ephemeral-port>`):
  - Serves `GET /dvr/<id>` with full HTTP/1.1 `Range` support backed by HTSP `fileOpen("/dvrfile/<id>")`, `fileStat`, `fileRead`, and `fileClose`.
  - Used automatically for **in-progress (`recording`)** DVR entries so `libmpv` can play and seek smoothly as the file grows without premature EOF.
  - Combine with HTSP `dvrEntry` timestamps so the UI shows live-updating total duration.
- Integrate Media3 `MediaSessionService`:
  - Background audio play (detach video surface / `vid=no` on backgrounding when enabled or playing Radio; restore video surface on foreground).
  - Media notification, lock-screen play/pause/stop, AudioFocus, noisy-audio pause.
- **Acceptance:** On both phone and TCL C635:
  - Live `1080i` HD channel starts in ~1s with clean deinterlaced video, `MP2`/`AC3` audio, and toggleable `DVBSUB` subtitles.
  - Pausing live TV for 60s and rewinding/seeking works smoothly, plus "Jump to Live".
  - Radio channel continues playing smoothly with screen off and controllable from lock-screen notification.
  - Both a completed `.mkv` recording and an actively recording show play and seek cleanly.

### Milestone 3 — Design System & Core Browsing (Phone & Tablet) (`:core:designsystem`, `:feature:mobile`)
- Implement the Apple/iPadOS-inspired dark-first design system per [docs/DESIGN.md](./docs/DESIGN.md).
- Build onboarding / server setup screen with live connection test (`host`, HTSP port `9982`, HTTP port `9981`, username/password).
- Build the 3 top-level destinations (**Live**, **Guide**, **Recordings**) + compact **Settings** sheet:
  - **Live:** Channel cards with Coil-cached picons, channel number, Now/Next title, live progress bar, filter pills (`All`, `TV`, `Radio`, `Favorites`, server tags).
  - **Guide (EPG):** Clean timeline/agenda view with search, program detail bottom sheet, and one-tap **Record** / **Cancel Recording**.
  - **Recordings:** Clean sections (`Recording Now` with live badge, `Completed` grouped by series/title with resume bars, `Scheduled`) with Play / Resume / Delete actions.
- Add persistent bottom **Mini-Player** when background audio (Radio or TV) is active.
- **Acceptance:** Smooth 60/120fps scrolling, instant navigation, full server configuration and DVR scheduling working end-to-end on phone.

### Milestone 4 — Mobile Player HUD, Gestures & PiP (`:feature:mobile`)
- Edge-to-edge fullscreen video player with translucent frosted-glass HUD auto-hiding after inactivity.
- Touch interactions: tap to toggle HUD, double-tap left/right (±10s / ±30s), smooth seekbar with live time preview + "Live" pill when timeshifted, left/right edge vertical drag for brightness/volume.
- Quick-switch channel drawer overlay (zap without leaving player).
- Track picker bottom sheet (Audio language/codec, DVB Subtitles, Aspect ratio / Zoom).
- Automatic/manual **Picture-in-Picture (PiP)** mode with clean UI stripping and surface preservation.
- **Acceptance:** Fluid phone viewing experience; PiP, background audio transition, track switching, and timeshift scrubbing all work without crashes or surface black-screens.

### Milestone 5 — Android TV Experience (`:feature:tv`)
- Build 10-foot UI with `androidx.tv:tv-material:1.1.0` following [docs/DESIGN.md](./docs/DESIGN.md):
  - Clean left collapsible navigation rail (**Live**, **Guide**, **Recordings**, **Settings**).
  - Strict D-pad focus behavior (visible focus ring + subtle scale, initial focus restoration, zero focus traps).
- Build TV Fullscreen Player:
  - **Instant Zap:** `Channel Up/Down` or `D-pad Up/Down` immediately switches channel and shows a sleek minimal bottom-left glass OSD (Channel #, Picon, Now/Next + progress bar) that fades after 3s.
  - **Progressive Seeking:** `D-pad Left/Right` scrubs timeshift buffer or recording with smooth step escalation (`10s → 30s → 1m → 5m`) and instant preview.
  - **OK / Select:** Opens translucent bottom glass HUD (Play/Pause, Back to Live, Audio/Subtitles, Quick Channel Strip without stopping playback).
  - **Single-Tuner Feedback:** If tuning fails due to an active recording locking another mux, display a crisp glass notification explaining why instead of spinning forever.
- **Acceptance:** Tested on TCL C635 (or Android TV hardware/emulator) using ONLY a standard TV remote D-pad + Back + OK + Ch+/Ch-. Memory stays well within budget during multi-hour viewing.

### Milestone 6 — Polish, Resilience & Release Readiness
- Save/restore recording resume positions (auto-mark watched at ≥92%).
- Graceful network transitions (Wi-Fi <-> VPN/cellular reconnect, HTSP auto-resync without blanking the UI).
- Wake-lock / `KEEP_SCREEN_ON` only during active video playback (never while audio-only backgrounded).
- Clean cache directory pruning on app start (orphaned `libmpv` disk-cache files).
- Signed release APK + sideload instructions (`adb install` for phone & TCL C635).

---

## 5. Known Pitfalls & Dead Ends to Avoid

Read this section carefully before writing code — every item below represents a real failure mode observed in Tvheadend/Android clients:

1. **DO NOT build a custom Media3 `MediaSource`/`SampleQueue` for HTSP `muxpkt` (nor a Kotlin TS/MKV remuxer for `libmpv`):**
   HTSP `muxpkt` delivers raw elementary stream packets (not MPEG-TS). Bridging them manually into ExoPlayer requires parsing H.264 extradata/Annex-B NALUs, normalizing PTS discontinuities across timeshift jumps, hooking Media3 1.4+ `SubtitleTranscodingExtractor` for raw `DVBSUB`, compiling/bundling an FFmpeg audio decoder for `MP2`, and *still* leaves 1080i combing on some mobile SoCs. Stick to HTSP control + HTTP `pass` + `libmpv`.
2. **DO NOT rely solely on HTTP `/dvrfile/<id>` for actively recording shows:**
   Tvheadend's HTTP endpoint can freeze `Content-Length` at open time, breaking seeking or halting playback early. Always route `schedStatus == "recording"` entries through the HTSP `fileOpen`/`fileRead`/`fileStat` loopback proxy.
3. **DO NOT overlap streams on single-tuner servers during channel zapping:**
   A single DVB-T dongle (`IT9135`) can tune only one mux at a time. Opening channel B on a different mux before fully closing channel A's HTTP connection triggers tuner contention (`No free adapter`). Always tear down the active `libmpv` stream cleanly before loading the next URL.
4. **DO NOT embed plaintext `user:pass@host` in URLs:**
   Modern HTTP clients (OkHttp/Coil) and media players strip or reject inline URL credentials or fail Digest auth challenges. Always request a short-lived ticket via HTSP `getTicket` (or pass explicit `Authorization` headers when required).
5. **DO NOT allocate huge RAM-only `libmpv` buffers on Android TV:**
   The TCL C635 has ~2 GB RAM total. A 500 MB RAM back-buffer will trigger Android's Low Memory Killer (LMK). Cap RAM cache conservatively (e.g., 64–128 MB on TV) and spill longer timeshift windows to `cache-on-disk=yes` in `context.cacheDir`, clearing temp files on teardown.
6. **DO NOT destroy `libmpv` when backgrounding on phone:**
   Surface destruction in `Activity.onStop()` must NOT kill playback when entering PiP or background audio mode. Own `libmpv` inside `MediaSessionService`, detach the `Surface` / set `vid=no` when backgrounded for audio-only play, and reattach seamlessly on return.
7. **DO NOT use deprecated Leanback XML or alpha Navigation libraries:**
   Use `androidx.tv:tv-material` (Compose for TV) and stable type-safe Navigation Compose.
8. **DO NOT ignore `libmpv` async calls on the UI thread:**
   Always invoke `seek` / `loadfile` / property changes off the main thread (or via `mpv_command_async`) so seeking and zapping never trigger ANRs or frame drops.
9. **DO NOT hardcode `localhost` or a single hardcoded LAN IP:**
   Support saved server profiles (including switching between LAN IP and WireGuard/Tailscale address) with automatic reachability feedback.
