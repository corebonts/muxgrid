# Changelog

All notable changes to **MuxGrid** are documented in this file.

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
