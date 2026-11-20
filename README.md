# MuxGrid — Tvheadend Android Client (Phone + Android TV)

<img src="./docs/assets/muxgrid-icon.svg" alt="MuxGrid icon" width="72" />

A lightweight, native Android & Android TV client for **Tvheadend** (`4.3+`), designed for instant channel zapping, live pause/rewind (timeshift), smooth playback of completed and in-progress recordings, background audio play on mobile, and a calm, iOS/iPadOS-inspired dark interface.

Built as a **single unified APK** targeting both modern Android phones/tablets and Android TV / Google TV devices (such as the TCL C635).

## Core Goals

- **Snappy & Lightweight:** Fast cold start, sub-second channel zapping on LAN, tiny memory footprint suitable for 2 GB RAM Android TVs.
- **Broadcast-Native Playback:** Clean playback of `1080i` interlaced H.264, `MP2`/`AC3`/`AAC` audio, DVB bitmap subtitles (`DVBSUB`), and radio channels with zero server-side transcoding.
- **Complete Live & DVR Experience:** Live TV pause/rewind, completed and **in-progress (growing)** recordings with accurate seeking, EPG browsing, and one-tap recording scheduling.
- **Phone-First Comforts:** Background audio playback (radio & live TV) with system media controls, Picture-in-Picture (PiP), and fluid touch gestures.
- **TV-First Ergonomics:** Pure Compose for TV D-pad navigation, instant Channel Up/Down zapping, progressive seek steps, and zero focus traps.
- **Modern Aesthetic:** Dark-first, content-forward canvas with translucent frosted-glass overlays, tabular typography, and minimal navigation clutter (inspired by Infuse, Channels, and Apple TV).

## Current Implementation Status

- **Completed (M0–M3):** Multi-module Gradle project (`compileSdk 37.2`, `targetSdk 36`, `minSdk 28`), HTSP binary client + atomic shadow-store resync + dedicated HTSP VFS connection (`HtspVfsClient`), DataStore + Tink credential storage, Coil HTSP/HTTP picon fetcher, `LocalMediaProxy` (live streams, completed `/dvrfile` Range relay with ticket auto-refresh, and growing recording HTSP tail-follow mode), `libmpv` playback engine (`mediacodec` on TV, `mediacodec-copy` + `bwdif` on phone, disk-backed live timeshift ring buffer with free-space check & buffer-full auto-resume), onboarding Server Setup, Mobile & TV Live/Guide/Recordings tabs with series grouping, DVR management, resume sync, live timeshift seekbar (`-MM:SS · Back to Live`), and "Watch from Start" chase-play for in-progress recordings.
- **Implemented (M4–M5, awaiting device verification):** Media3 `MediaSessionService` + `SimpleBasePlayer` adapter, explicit `AudioFocusRequest` / `ACTION_AUDIO_BECOMING_NOISY` / `WifiLock` / `PARTIAL_WAKE_LOCK`, `vid=no` audio-only backgrounding, PiP support, mobile gestures (double-tap seek, brightness/volume vertical drag), aspect ratio modes, Quick-Zap channel drawer, Haze 2.1.0 frosted-glass chrome, persistent mini-player, adaptive tablet/landscape navigation rail & grid, TV collapsible Navigation Rail, horizontal timeline EPG with red "Now" hairline, progressive D-pad seeking (`10s → 30s → 1m → 5m`), and TV HUD quick channel strip.
- **Planned (M6):** Multi-profile switching, automated release signing, and Android 17 `ACCESS_LOCAL_NETWORK` readiness.

## Build, Test & Sideload

1. **Optional debug defaults:** Create an untracked `.env` in `android-client/` (already ignored by `.gitignore`) if you want `BuildConfig` prefill defaults during local builds:
   ```properties
   TVH_HOST=192.168.1.5
   TVH_USER=androidclient
   TVH_PASS=...
   ```
2. **Run JVM unit tests and build ABI-split debug APKs:**
   ```bash
   ./gradlew test assembleDebug
   ```
   Outputs:
   - Phone (`arm64-v8a`): `app/build/outputs/apk/debug/app-arm64-v8a-debug.apk`
   - 32-bit Android TV (`armeabi-v7a`, e.g., TCL C635): `app/build/outputs/apk/debug/app-armeabi-v7a-debug.apk`
3. **Install / Sideload:**
   - Via ADB: `adb install -r app/build/outputs/apk/debug/app-arm64-v8a-debug.apk` (or `app-armeabi-v7a-debug.apk` for TV)
   - Over LAN browser (phone): run `python3 -m http.server 18123 --bind 0.0.0.0 --directory app/build/outputs/apk/debug` and download `http://<host-ip>:18123/app-arm64-v8a-debug.apk` on your phone.

## Documentation Map

| Document | Purpose |
| :--- | :--- |
| [PLAN.md](./PLAN.md) | Master implementation plan: architecture, tech stack, module boundaries, phased milestones (M0–M6), and dead ends to avoid |
| [docs/DESIGN.md](./docs/DESIGN.md) | Visual design system, Phone/Tablet & Android TV layouts, player HUD, gestures, and D-pad interaction rules |
| [docs/muxgrid-prototype.html](./docs/muxgrid-prototype.html) | Self-contained interactive HTML prototype of **MuxGrid** (Android TV 10-ft & Phone portrait modes, animated player HUD, EPG, timeshift, PiP, and mini-player) — open directly in any browser |
| [docs/assets/muxgrid-icon.svg](./docs/assets/muxgrid-icon.svg) | **MuxGrid** VectorDrawable-ready SVG app icon source (`512×512`) |
| [docs/PROTOCOL.md](./docs/PROTOCOL.md) | Tvheadend HTSP wire format, RPC subset, HTTP streaming URLs, and the growing-recording loopback bridge contract |
| [docs/SERVER_SETUP.md](./docs/SERVER_SETUP.md) | Recommended Tvheadend server configuration (profiles, least-privilege ACL user, single-tuner & timeshift notes, verification steps) |

## Architecture at a Glance

```text
┌─────────────────────────────────────────────────────────────────────────┐
│                    Unified Android APK (Phone + TV)                     │
│                                                                         │
│  :feature:mobile (Compose M3)          :feature:tv (Compose for TV)     │
│                └──────────────┬───────────────┘                         │
│                               ▼                                         │
│      :core:data (Reactive StateFlow Store + DataStore Preferences)      │
│              ┌────────────────┴─────────────────┐                       │
│              ▼                                  ▼                       │
│  :core:htsp (Pure Kotlin TCP)        :core:player (PlaybackEngine)      │
│  • Channels, EPG, DVR push sync      • libmpv (1080i, MP2, DVBSUB)      │
│  • Auth tickets (getTicket)          • Live pause/rewind ring buffer    │
│  • fileOpen/Read/Stat (growing DVR)  • Media3 MediaSessionService + PiP │
└──────────────┬──────────────────────────────────┬───────────────────────┘
               │ HTSP (9982/tcp)                  │ HTTP (9981/tcp, pass / dvrfile)
               ▼                                  ▼
        Tvheadend 4.3+ Server (e.g. tv-server / 192.168.1.5)
```

## Acknowledgments & Attribution

### Third-Party Projects & Libraries
- **[Tvheadend](https://tvheadend.org/)** — For the TV streaming server and the HTSP (Home TV Streaming Protocol) specification implemented by `:core:htsp`.
- **[mpv](https://mpv.io/)**, **[FFmpeg](https://ffmpeg.org/)**, and **[mpv-android-lib](https://github.com/abdallahmehiz/mpv-android-lib)** (by [Abdallah Mehiz](https://github.com/abdallahmehiz)) — For the native Android `libmpv` playback engine, hardware decoding bridge, and `bwdif` deinterlacing filter.
- **[Haze](https://github.com/chrisbanes/haze)** (by [Chris Banes](https://github.com/chrisbanes)) — For real-time frosted-glass backdrop blur across mobile and tablet chrome.
- **[Coil 3](https://coil-kt.github.io/coil/)** — For asynchronous channel picon loading and caching.
- **[Google Tink](https://developers.google.com/tink)** — For Android Keystore-backed AEAD credential encryption.
- **[Google Material Symbols](https://fonts.google.com/icons)** — For the Rounded icon vector paths embedded in `:core:designsystem` (`MuxGridIcons.kt`).
- **[Android Jetpack](https://developer.android.com/jetpack)** & **[Kotlin Coroutines](https://github.com/Kotlin/kotlinx.coroutines)** — Compose, Compose for TV (`androidx.tv`), Media3 (`MediaSessionService`), Navigation, and DataStore.

See [NOTICE](./NOTICE) for full third-party copyright and license notices.

### AI Assistance
Architected, researched, designed, and implemented with the assistance of **Google Antigravity** (powered by **Gemini**), including the clean-room HTSP protocol implementation, interactive HTML design prototype, SVG iconography, and multi-module Kotlin/Compose codebase.

## License

- **Source Code ([LICENSE](./LICENSE)):** The original Kotlin source code, documentation, and assets in this repository are licensed under the **[Apache License, Version 2.0](./LICENSE)** (permissive, and one-way compatible with GPLv3).
- **Compiled Binaries / APKs:** Because compiled builds link and bundle prebuilt `libmpv` and `FFmpeg` native libraries (`io.github.abdallahmehiz:mpv-android-lib`, built with `--enable-gpl --enable-version3`), any distributed APKs are governed by the **GNU General Public License v3.0 or later (GPL-3.0-or-later)**. The playback engine is isolated behind the `PlaybackEngine` interface in `:core:player` so the rest of the codebase remains modular and reusable under Apache-2.0.
