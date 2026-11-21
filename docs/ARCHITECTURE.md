# Architecture & Engineering Notes

This document describes MuxGrid's multi-module architecture, protocol/playback boundaries, and verified platform/Tvheadend edge cases.

---

## 1. Module Graph

Dependencies are kept strictly acyclic and shallow, with `:core:model` and `:core:htsp` remaining pure JVM Kotlin modules:

```text
:app                    (AndroidManifest, MainActivity, DI container, form-factor router)
 ├── :feature:mobile    (Phone & tablet Compose Material 3 screens, touch player HUD, PiP, mini-player)
 ├── :feature:tv        (10-ft Compose for TV screens, D-pad player HUD, quick channel strip)
 ├── :core:designsystem (Broadcast Monitor colors, typography, shapes, shared badges & onboarding)
 ├── :core:player       (PlaybackEngine, MpvPlaybackEngine, LocalMediaProxy, MediaSessionService)
 ├── :core:data         (TvheadendRepository, TvheadendStore, DataStore+Tink prefs, PiconFetcher)
 ├── :core:htsp         (Pure JVM: binary htsmsg codec, HtspClient, HtspSession, HtspVfsClient)
 └── :core:model        (Pure JVM: immutable domain models, PlaybackState, ServerProfile)
```

### Unified Phone + TV APK
A single APK supports both mobile/tablet and Android TV (`LAUNCHER` + `LEANBACK_LAUNCHER`, `android:banner="@drawable/tv_banner"`, `leanback` and `touchscreen` declared `required="false"`). Form factor is detected once at startup via `UiModeManager.currentModeType == UI_MODE_TYPE_TELEVISION` or `PackageManager.FEATURE_LEANBACK`, mounting either `MobileRoot` or `TvRoot` while sharing 100% of protocol, state, repositories, playback engine, and design tokens.

---

## 2. Hybrid Protocol & `LocalMediaProxy` Design

MuxGrid splits control and media responsibilities across Tvheadend's two protocols:

| Responsibility | Mechanism | Rationale |
| :--- | :--- | :--- |
| **Channels, Tags, EPG, DVR** | HTSP (`9982/tcp`) `enableAsyncMetadata` | Single persistent TCP socket with real-time push sync (`channel*`, `tag*`, `event*`, `dvrEntry*`), zero polling, and instant startup. |
| **Authentication & Tickets** | HTSP SHA-1 challenge-response + `getTicket` via `LocalMediaProxy` | Passwords are never embedded in media URLs. `LocalMediaProxy` refreshes short-lived HTTP tickets transparently and retries once on `401`. |
| **Live TV & Radio** | HTTP `pass` (MPEG-TS) via `LocalMediaProxy` | Zero server-side transcoding, sub-second same-mux zapping, preserves all audio tracks and `DVBSUB`, and enforces clean socket teardown between channel zaps. |
| **Live Timeshift** | `libmpv` disk-backed demuxer ring buffer | Instant local scrub within the buffered window (`cache=yes`, `demuxer-seekable-cache=yes`, `cache-on-disk=yes`) without implementing a fragile HTSP `muxpkt` elementary-stream bridge. |
| **Completed Recordings** | HTTP `/dvrfile/<id>?ticket=...` via `LocalMediaProxy` | Direct HTTP `Range` seeking against Matroska cues with transparent ticket refresh past the default 300 s TTL. |
| **Growing Recordings** | HTSP VFS (`fileOpen` / `fileStat` / `fileRead`) via `LocalMediaProxy` | Dedicated `HtspVfsClient` connection streams actively recording files in tail-follow mode (`Accept-Ranges: bytes`, `Content-Range: bytes X-Y/*`), polling `fileStat` at the write head so `libmpv` never hits premature EOF or `416`. |
| **Resume & Watched State** | HTSP `updateDvrEntry` (`playposition`, `playcount`) | Syncs "Continue Watching" and watched checkmarks across devices; falls back to DataStore for read-only users without DVR write access. |
| **Channel Picons** | Coil 3 `PiconFetcher` + `KeyerImpl` | Normalizes `imagecache/<id>`, `/imagecache/<id>`, and external `http(s)://` URLs; keys Coil's `MemoryCache` and reads/writes Coil's `DiskCache` (`picon_cache`), fetching via HTSP VFS first and falling back to authenticated HTTP (Digest/Basic) if the user lacks `ACCESS_HTSP_RECORDER`. |

### Reactive In-Memory Store & Atomic Shadow Resync
`:core:data` holds channels, tags, EPG events, and DVR entries in memory inside `TvheadendStore` (`StateFlow`), avoiding SQLite schema migrations while keeping filtering, search, and Now/Next lookups instantaneous. On reconnect or manual pull-to-refresh (`HtspClient.resync`), `enableAsyncMetadata` writes into temporary shadow maps and swaps them atomically on `initialSyncCompleted` so server-side deletions during downtime are never missed and the UI never blanks out. Persistent preferences and Tink-encrypted server credentials live in Jetpack DataStore.

---

## 3. Playback Engine & System Integration

- **Abstraction Boundary:** UI and repository layers depend only on `PlaybackEngine` and transport-agnostic `PlaybackState` (`positionMs`, `durationMs`, `liveEdgeOffsetMs`, `bufferWindowMs`, `isTimeshiftSeekable`) in `:core:player`. `LiveStreamStrategy` isolates live URL resolution and timeshift semantics (`HttpPassStrategy` in current builds, leaving room for a future `HtspSubscribeStrategy`).
- **Decoder & Deinterlacing (`io.github.abdallahmehiz:mpv-android-lib:0.2.1`):**
  - Renders via `vo=gpu`, `gpu-context=android`, `opengl-es=yes` onto a `SurfaceView` so bitmap `DVBSUB` subtitles and OSD composite reliably.
  - **TV default (`MEDIACODEC_ZERO_COPY`):** `hwdec=mediacodec` + `deinterlace=no` for smooth `1080i` playback on entry-level TV SoCs.
  - **Mobile default (`MEDIACODEC_COPY_BWDIF`):** `hwdec=mediacodec-copy` + `deinterlace=yes` using FFmpeg's `bwdif` filter to eliminate `1080i` combing. Both presets are user-selectable in Settings and the player Display Options panel.
- **Background Playback & Platform Locks:** `MuxGridMediaSessionService` (`Media3` `MediaSessionService` + `MuxGridSessionPlayer` `SimpleBasePlayer` adapter) integrates lock-screen controls, notifications, and Bluetooth/headset keys. Because `SimpleBasePlayer` does not bundle ExoPlayer's audio/lock helpers, `PlaybackPlatformLocks` explicitly manages `AudioFocusRequest`, `ACTION_AUDIO_BECOMING_NOISY`, `WifiManager.WifiLock`, and `PowerManager.PARTIAL_WAKE_LOCK`.

---

## 4. Verified Platform & Protocol Pitfalls

Every constraint below reflects verified behavior in Tvheadend, FFmpeg, `libmpv`, or Android:

1. **`bwdif` filter availability in Android `libmpv` AARs:** `dev.jdtech.mpv:libmpv` compiles FFmpeg with `--disable-filters`, stripping `bwdif`/`yadif`. `io.github.abdallahmehiz:mpv-android-lib` retains `bwdif`/`yadif`. Note that `hwdec=mediacodec` bypasses mpv's software filter chain, and `vo=mediacodec_embed` drops bitmap `DVBSUB` and OSD.
2. **Avoid HTSP `muxpkt` elementary-stream bridging in ExoPlayer:** `muxpkt` delivers raw elementary packets rather than MPEG-TS, requiring custom H.264 Annex-B/PTS reconstruction, `DVBSUB` transcoding, bundled `MP2` decoding, and still lacking reliable `1080i` deinterlacing.
3. **Growing recordings must not expose a frozen total size:** Tvheadend's HTTP `/dvrfile/<id>` snapshots `fstat` at request open time. Serving `Content-Length` or `Content-Range: bytes X-Y/TOTAL` causes FFmpeg to cap `s->filesize` and hit `AVERROR_EOF`. `LocalMediaProxy` uses HTSP VFS tail-follow with `Content-Range: bytes X-Y/*`, polls `fileStat`, and never returns `416` at the write head.
4. **Tvheadend ticket TTL & HTTP auth quirks:** HTSP `getTicket` tokens default to a 300 s lifetime (open transfers continue, but new `Range` seeks return `401`), and Tvheadend 4.3 defaults to HTTP Digest MD5 (rejecting Basic auth). `LocalMediaProxy` handles ticket refresh transparently without exposing credentials in player URLs.
5. **Single-tuner conflict detection:** Tvheadend does not return HTTP `503` when no tuner is free; after ~2 s of retries it closes the HTTP socket with 0 bytes. `LocalMediaProxy` uses raw HTTP/1.1 sockets (avoiding `HttpURLConnection` silent retries) and triggers active-DVR lookup on empty replies to emit `PlaybackError.TunerBusy`.
6. **Live timeshift cache sizing & `seekable` quirk:** While paused, `mpv` writes into the forward cache (`demuxer-max-bytes`) and stops reading once full. Size *both* `demuxer-max-bytes` and `demuxer-max-back-bytes` equally with `cache-on-disk=yes` in `context.cacheDir`, and derive `isTimeshiftSeekable` from `demuxer-cache-state.seekable-ranges` rather than mpv's `seekable` property (which reports `false` for live HTTP streams even while in-cache seeks work).
7. **`SimpleBasePlayer` does not manage AudioFocus or Wake/WifiLocks:** Always keep `AudioFocusRequest`, `ACTION_AUDIO_BECOMING_NOISY`, `WifiLock`, and `PARTIAL_WAKE_LOCK` explicit when wrapping a custom player in `MediaSessionService`.
8. **Do not blur a `SurfaceView` plane:** Compose `RenderEffect` / Haze cannot sample an external `SurfaceView` video plane, and switching TV playback to `TextureView` adds GPU copy overhead on `1080i50`. Reserve Haze blur for non-video Compose screens and use translucent dark scrims over video.
9. **Android Network Security Config & `targetSdk 37`:** NSC `<domain-config>` does not support CIDR ranges, and native C sockets (`libmpv`/FFmpeg) ignore Java NSC anyway; `<base-config cleartextTrafficPermitted="true" />` enables arbitrary LAN/VPN hosts in OkHttp/Coil and loopback `127.0.0.1`. Raising `targetSdk` to `37` (Android 17) will require handling runtime `ACCESS_LOCAL_NETWORK`.
10. **Never invoke `libmpv` commands synchronously on the main thread:** All `loadfile`, `seek`, `stop`, and property mutations execute on a dedicated single-thread executor (`muxgrid-mpv`).
11. **Android TV Compose fluidity, `AccessibilityService` amplification, and debug vs. release:** On entry-level 32-bit ARM TVs, `DEBUGGABLE` APKs cannot be AOT-compiled by ART (`run-from-apk` / `quicken`) and lack R8 inlining, making Compose several times slower than a minified `release` build. Furthermore, when a TV runs a custom launcher with a key-intercepting `AccessibilityService`, Compose runs `AndroidComposeViewAccessibilityDelegateCompat` bounds/semantics diffs during scrolling and focus animations. Keep TV focus memory non-observable (`TvFocusMemory`), overlay the navigation rail rather than resizing the grid/guide, avoid `SubcomposeAsyncImage` and `tv.material3.Surface` (`SurfaceGlowNode`) in dense lazy grids/timelines, merge per-card semantics (`clearAndSetSemantics`), run `TvheadendStore` event ingestion and periodic `NowNext` refreshes on `Dispatchers.Default`, and keep pure-JVM `:core:model` types declared in `compose_compiler_config.conf`.
12. **Coil 3 custom `Fetcher`s do not auto-cache custom model types:** Because `PiconModel` is a custom data class, Coil skips both `MemoryCache` and `DiskCache` unless `PiconFetcher.KeyerImpl` is registered and `PiconFetcher.fetch()` explicitly reads/writes `imageLoader.diskCache`.
13. **DVB MPEG-TS probe budget & `mpv` property throttling for smooth TV OSD playback:** Keep `demuxer-lavf-o` at `analyzeduration=2500000,probesize=2097152` (FFmpeg still exits early once SPS/PPS is found); smaller budgets such as `500 ms` frequently finish mid-GOP with `unspecified size`, causing `h264_mediacodec` to fail with `Could not open codec` and fall back to ~200% CPU software decode. Throttle `time-pos` updates (`250 ms`), avoid observing unused per-frame properties (`estimated-vf-fps`) or live-stream `duration` growth, exclude live `durationMs` from `MuxGridSessionPlayer`'s invalidation key, and keep `MainActivity` / HUD controls isolated from periodic position ticks so opening the OSD never starves video rendering.
