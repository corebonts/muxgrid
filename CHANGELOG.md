# Changelog

All notable changes to **MuxGrid** are documented in this file.

## [0.8.0] — 2026-11-22 — Guide me to the good part

### Continuous Program Guide (EPG) & Show Details
- Enabled the **2D timeline EPG** in mobile portrait with a collapsible channel column (`MobileGuideScreen`).
- Anchored the EPG timeline origin at **Now** (`GuideTimeline`, `TvGuideScreen`, `MobileGuideScreen`).
- Added an **EPG show details & DVR actions overlay** on Android TV selection and moved the live progress bar into the details panel (`TvGuideScreen`, `TvFeature`).

### Ongoing Recordings & Fast MKV Seeking
- Added **fast MKV binary-search seeking** (`GrowingMkvIndexStore`, `GrowingMkvBinarySearcher`) and virtual MKV header splicing (`LocalMediaProxy`) for ongoing Matroska recordings, with cached MPEG-TS skip and cold-resume support in `MpvPlaybackEngine`.
- Added buffered/target seekbar markers, Back-to-cancel seeking, write-head clamping, and a **Live `REC` badge + "Watch from Start (Recording)"** shortcut on Mobile and TV.
- Unified the mobile recordings card and row layout (`MobileRecordingsScreen`).

### Android TV Performance & Profiling
- Optimized Live, Recordings, and EPG scrolling on Android TV (`TvFeature`, `TvGuideScreen`, `TvRecordingsScreen`) and added TV scroll benchmarking (`scripts/bench_tv_scroll.py`, `docs/ARCHITECTURE.md`).

### Licensing, Branding & Documentation
- Added an in-app **Licenses & Notices** screen with bundled GPLv3 and Apache-2.0 texts (`AppNotices`, `licenses/gpl-3.0.txt`).
- Added an **adaptive launcher icon** and PNG Android TV banner.
- Updated `README.md` with a hero banner and Shields.io badges.

## [0.7.1] — 2026-11-21 — Next stop - Playing

### Fixes
- Registered the active `MediaSession` with `MuxGridMediaSessionService` and requested Android 13+ notification permission (`POST_NOTIFICATIONS`) so media playback notifications and lock-screen controls appear reliably (`MainActivity`, `MuxGridMediaSessionService`, `MuxGridSessionPlayer`, `MpvPlaybackEngine`).
- Validated linked `Now`/`Next` EPG event timestamps in `TvheadendStore` so stale or out-of-range linked events are not displayed as the current or upcoming program.

## [0.7.0] — 2026-11-21 — Scroll without borders

### Continuous Program Guide (EPG) & Tablet/Landscape Grid
- Replaced the stepped EPG time window with a **continuous horizontal timeline** (`GuideTimeline`) across Android TV and mobile landscape/tablet, with placement-only horizontal scrolling and draw-phase sticky channel/program labels.
- Added a **2D horizontal multi-channel EPG timeline grid** in mobile landscape and tablet mode (`>= 600dp`) with keyboard/gamepad focus auto-follow.
- Extended the HTSP EPG sync window from 7 days to **2 weeks (`+14d`)** and kept selected day/channel items scrolled into the viewport.
- Refined EPG focus navigation to avoid unnecessary horizontal scrolling on already-visible or wide cells and prevent `BringIntoViewNode` from shifting horizontally on Up/Down focus moves.
- Offloaded EPG search to background threads (`searchEpgEvents`), optimized TV guide search rows, and scoped high-frequency `StateFlow` collection to active tabs.

### Player Controls, Diagnostics & Preferences
- Added an in-player **Playback Info** panel (`PlaybackInfoPanel`) displaying live stream, decoder, cache, and proxy diagnostics, and reordered the player stop button.
- Persisted **subtitle on/off state and preferred language** across playbacks.
- Added **tap-ladder and gear-locked hold seeking** on Android TV (`10s → 30s → 1m → 5m → 10m`, continuous hold capped at `30s`) backed by shared seek bounds (`TvSeekController`).
- Unified mobile **Background Playback** into a single `Off` / `Audio` / `PiP` selector (`BackgroundPlaybackMode`) and streamlined player HUD minimize controls.
- Fixed video restoration after audio-only mode by configuring `vo=gpu` before `vid=auto` and resyncing live playback to the live head.
- Fixed measuring unlinked `mpv` disk-cache files and clarified network cache metrics.

### Settings, Themes & Navigation Polish
- Added **Auto / Light / Dark** theme selection under **Settings → General → Theme** (introducing the Daylight Monitor light palette while keeping fullscreen players dark).
- Added a **Start Screen** setting (`Live`, `Guide`, `Recordings`) across mobile and Android TV.
- Added an **App Info** section in Settings displaying app version, build ABI/Git SHA, and a **copyable debug info report** (`DebugInfoReport`).
- Redesigned Settings with full-width and responsive **two-column layouts** on wide screens and Android TV, with clamped TV column focus.
- Polished Android TV **Navigation Rail** focus, back-key behavior, and slide animation, and fixed underline selection highlight widths.

### Security, Stability & Lifecycle Hardening
- Hardened HTTP Digest authentication (server host/port scoping, algorithm/qop validation, quoted-string escaping), `HtsMsgCodec` overflow-safe bounds and nesting depth limits, Google Tink Keystore recovery without silently wiping credentials, and `LocalMediaProxy` header limits and CR/LF sanitization.
- Hardened HTSP session lifecycle, picon fetching, EPG/DVR cache integrity, and kept the `Syncing` state visible until initial HTSP metadata is published.
- Hardened player lifecycle, `PlaybackPlatformLocks`, DVR progress tracking, `MainActivity` PiP/intent lifecycle, and Onboarding Server Setup host/IP validation.
- Consolidated shared DVR/playback helpers, design tokens, and constants.

### Build, CI & Documentation
- Added an automated signed **GitHub Release** workflow (`.github/workflows/release.yml`).
- Added the `noDebug` build type (`isDebuggable = false` with debug signing) for fast local ART AOT testing on Android TV and enabled Gradle configuration cache.
- Added anonymized Android phone and Android TV screenshots to `README.md`, a local mock Tvheadend server (`scripts/mock_tvh_server.py`), and a screenshot capture skill.

## [0.6.0] — 2026-11-21

### Android TV Performance & Playback Smoothness
- Eliminated TV live OSD stutter, `MediaSession` churn, and mid-GOP software decode fallbacks during live playback (`TvPlayerScreen`, `MpvPlaybackEngine`, `MuxGridSessionPlayer`, `MainActivity`).
- Optimized Android TV Compose fluidity with `compose_compiler_config.conf` stability configuration, Coil memory/disk picon caching (`PiconFetcher`), and background EPG sorting/`NowNext` sync (`TvheadendStore`, `TvheadendRepository`).

### Android TV Navigation, Search & D-Pad Ergonomics
- Added top-bar program search to the Android TV Guide (`TvGuideScreen`, `TvFeature`).
- Improved Android TV initial focus, back-navigation focus restoration, and D-pad ergonomics across Live, Guide, Recordings, and Player screens.

### Unified UI, Settings & Space Efficiency
- Promoted **Settings** to a main navigation tab on both TV and mobile instead of a modal dialog/sheet, and replaced Cancel with a top-right close icon on mobile server setup.
- Unified dialogs, modal sheets, and DVR confirmations across mobile and TV (`MuxGridComponents`).
- Made mobile and TV chrome more space-efficient and fixed live UI refresh issues.

### Stability & Documentation
- Fixed `TunerBusy` `fdsan` crashes on mobile (`ytdl_hook` subprocess handling, in-window alert, and synchronous surface detach).
- Added cross-agent AI guidelines in `AGENTS.md`.

## [0.5.0] — 2026-11-21

### Broadcast Monitor Facelift & Polish
- Redesigned UI around the **Broadcast Monitor** aesthetic (`Carbon #09090B` / `#0C0C0E` surfaces, `Signal Amber #E5A00D` accent, `Coral #E53935` live/recording dots, tabular numerals, crisp hairline borders).
- Added Google Material Symbols Rounded vector iconography in `:core:designsystem` (`MuxGridIcons`).
- Added pull-to-refresh across mobile screens triggering full HTSP metadata resync (`HtspClient.resync()`).
- Added focusable seek progress bar and `D-pad Down` HUD opening in the Android TV player.
- Unified player track/aspect/decoder settings under **Display Options** with adaptive portrait/landscape controls.
- Excluded unused `mpv-android-lib` bundled assets (`subfont.ttf`, `cacert.pem`), enabled release R8 shrinking/minification, and added Apache-2.0 `LICENSE` + third-party `NOTICE`.

### Background Audio, PiP & 10-ft TV Experience
- Wrapped `PlaybackEngine` in Media3 `MuxGridMediaSessionService` (`SimpleBasePlayer` adapter) with lock-screen/media-key controls, persistent mobile mini-player, `vid=no` audio-only backgrounding, and explicit `AudioFocusRequest`, `ACTION_AUDIO_BECOMING_NOISY`, `WifiLock`, and `PARTIAL_WAKE_LOCK`.
- Added Picture-in-Picture (PiP) support on mobile/tablet (`autoEnterEnabled` on API 31+, `onUserLeaveHint` on older APIs).
- Added mobile player gestures (double-tap `-10s`/`+30s`, vertical left/right drag for brightness/volume), aspect ratio modes (`Fit`, `Fill`, `16:9`, `4:3`, `Stretch`), Quick-Zap channel drawer, adaptive tablet/landscape navigation rail, and Haze `2.1.0` backdrop blur on non-video overlays.
- Built dedicated 10-ft Android TV UI (`androidx.tv:tv-material`): collapsible Navigation Rail, multi-column Live grid, horizontal EPG timeline with live "Now" hairline and top program preview, horizontal Recordings shelves, instant `Ch+`/`Ch-` zapping OSD, progressive D-pad seeking (`10s → 30s → 1m → 5m`), and Quick Channel Strip.

### Live Timeshift & Growing Recordings
- Added disk-backed `libmpv` live pause/rewind ring buffer (`Off` / `512 MB` / `1 GB` / `2 GB` / `4 GB`) with free-space checks, smoothed seekable-range reporting (`TimeshiftRangeSmoother`), and buffer-full auto-resume handling.
- Added HTSP VFS (`HtspVfsClient`: `fileOpen`, `fileStat`, `fileRead`, `fileClose`) tail-follow handler in `LocalMediaProxy` for chase-play and seeking inside actively growing recordings.

### EPG, DVR Management & Live TV MVP
- Implemented pure-JVM HTSP binary protocol client (`HtsMsgCodec`, `HtspClient`) with SHA-1 auth, `getSysTime` clock-offset heartbeat, `enableAsyncMetadata` push sync, and atomic shadow-store resync (`TvheadendStore`).
- Added EPG Guide browsing, DVR scheduling/stop/cancel/delete (`addDvrEntry`, `stopDvrEntry`, `cancelDvrEntry`, `deleteDvrEntry`), completed `/dvrfile` Range playback with automatic ticket refresh, and cross-device resume/watched sync (`updateDvrEntry` + local DataStore fallback).
- Added Coil 3 `PiconFetcher` (HTSP VFS primary + HTTP Digest/Basic fallback), Google Tink Keystore-encrypted credential storage, single-tuner conflict detection (`TunerBusy`), and onboarding Server Setup with live connection testing.
