# Research Report: `libmpv` & Android Platform Details for MuxGrid

This report completes the research task initiated for `researcher-tvheadend-libmpv`, covering `libmpv` video output/deinterlacing, Maven Central AAR packaging, demuxer caching, FFmpeg HTTP range/chunked behavior, Android networking & manifest rules, TCL C635 hardware traits, Compose blur, Media3 `SimpleBasePlayer`, and current library versions (verified as of 2026-11-20).

---

## 1. `hwdec=mediacodec` (Zero-Copy) vs `vo=mediacodec_embed` vs `vo=gpu`

- **Answer:**
  - In `mpv`, `vo=mediacodec_embed` requires `hwdec=mediacodec` (along with `--wid=(intptr_t)(*android.view.Surface)`). Because `mediacodec_embed` renders `IMGFMT_MEDIACODEC` frames directly onto an Android `Surface`, **subtitle rendering (including bitmap `DVBSUB`), OSD/OSC, screenshots, and video filters (including `deinterlace`) are not supported** with `vo=mediacodec_embed`.
  - **`hwdec=mediacodec` can also be used with `vo=gpu` (or `vo=gpu-next`)** on Android when `--gpu-context=android` is set (`DOCS/man/vo.rst` explicitly notes: *"To use hardware decoding with `--vo=gpu` instead, use `--hwdec=mediacodec` or `mediacodec-copy` along with `--gpu-context=android`."*). With `vo=gpu`, `hwdec=mediacodec` decodes into an Android `SurfaceTexture` / `EGLImage` (`IMGFMT_MEDIACODEC`), which **does** allow `vo=gpu` to composite subtitles and OSD, though video filters expecting software frames (`bwdif`) still cannot operate directly on `IMGFMT_MEDIACODEC` without copy-back.
  - **`mpv-android` defaults:** Upstream `mpv-android` (`MPVView.kt` + `BaseMPVView.kt`) sets `profile=fast`, `vo=gpu` (optional `gpu-next` toggle), `gpu-context=android`, `opengl-es=yes`, and `hwdec=mediacodec,mediacodec-copy` (`HWDECS`), with `hwdec-codecs=h264,hevc,mpeg4,mpeg2video,vp8,vp9,av1`.
- **Confidence:** High
- **Sources:**
  - `mpv` `DOCS/man/vo.rst` (`mediacodec_embed`): <https://github.com/mpv-player/mpv/blob/master/DOCS/man/vo.rst>
  - `mpv-android` `MPVView.kt`: <https://github.com/mpv-android/mpv-android/blob/master/app/src/main/java/is/xyz/mpv/MPVView.kt>
  - `mpv-android` `BaseMPVView.kt`: <https://github.com/mpv-android/mpv-android/blob/master/app/src/main/java/is/xyz/mpv/BaseMPVView.kt>

---

## 2. Deinterlacing with `hwdec=mediacodec-copy` + `deinterlace=yes`

- **Answer:**
  - **Which deinterlacer runs:** In `mpv` (`filters/f_auto_filters.c`, `deint_process()`), hardware-specific deinterlacers are only used for `IMGFMT_VDPAU` (`vdpaupp`), `IMGFMT_D3D11` (`d3d11vpp`), `IMGFMT_CUDA` (`bwdif_cuda`), `IMGFMT_VULKAN` (`bwdif_vulkan`), and `IMGFMT_VAAPI` (`vavpp`). For software frames — including copied-back frames from `hwdec=mediacodec-copy` — `mpv` converts to a supported SW image format and inserts FFmpeg's **`bwdif`** filter (`mode=send_field`, `deint=0` for `deinterlace=yes` or `deint=1` for `deinterlace=auto`, `parity=auto|tff|bff`) **on the CPU**.
  - **GPU-based deinterlace option on Android `vo=gpu`:** None built-in. `video/out/vo_gpu.c` has no GLSL deinterlace shader; `bwdif_vulkan` requires Vulkan (`IMGFMT_VULKAN`), and all current Android `libmpv` AAR builds pass `--disable-vulkan`.
  - **CRITICAL AAR FINDING:** Because `mpv` delegates `deinterlace=yes` to `libavfilter`'s `bwdif`, the bundled `libavfilter.so` **must** include `bwdif`. Prebuilt AARs that configure FFmpeg with `--disable-filters` (such as `dev.jdtech.mpv:libmpv`) **lack `bwdif` and `yadif`** and cannot perform software deinterlacing!
  - **Android `MediaCodec` native interlaced output:** Behavior is vendor- and driver-dependent. Android's public `MediaFormat` / `MediaCodec` API exposes no standard deinterlace-mode control. Realtek TV SoCs often include hardware deinterlacing in their display/video pipeline when rendering directly to a `Surface`, whereas some MediaTek/mobile `MediaCodec` decoders either drop alternate fields (halving vertical resolution), output weaved frames with combing, or deinterlace internally depending on firmware. Hardware measurement on the target TCL C635 and phone is therefore essential in Milestone 0.
- **Confidence:** High (mpv source & AAR binary inspection); Medium-High (vendor MediaCodec behavior, hardware-dependent)
- **Sources:**
  - `mpv` `filters/f_auto_filters.c`: <https://github.com/mpv-player/mpv/blob/master/filters/f_auto_filters.c>
  - `mpv` `DOCS/man/options.rst` (`--deinterlace`): <https://github.com/mpv-player/mpv/blob/master/DOCS/man/options.rst>
  - Kodi / Android MediaCodec interlaced discussions: <https://forum.kodi.tv> (via web search)

---

## 3. `libmpv` Android AARs on Maven Central

Direct inspection of Maven Central metadata, POM/Gradle `.module` files, `.aar` archives, and `strings` on `arm64-v8a` `.so` binaries on 2026-11-20:

### Comparison of Active Maven Central Artifacts

| Artifact | Latest Version (Last Updated) | ABIs Included | JNI Package / API | FFmpeg / mpv Versions | `bwdif` / `yadif` Filters? | License (POM vs Binaries) | Per-ABI Size (Compressed / Uncompressed) |
| :--- | :--- | :--- | :--- | :--- | :--- | :--- | :--- |
| **`io.github.abdallahmehiz:mpv-android-lib`** *(KMP; resolves Android to `mpv-android-lib-android`)* | **`0.2.1`** (`2026-11-14`) | `arm64-v8a`, `armeabi-v7a`, `x86`, `x86_64` | `is.xyz.mpv.MPV`, `BaseMPVView`, `MPVSession`, `MPVNode`, `Utils` | FFmpeg `git-2026-11-13-f8bb61c`, mpv `v0.42.0-dev-gfe753a782` | **Yes** (`bwdif`, `yadif`, `scale`, `format`, `aresample` all present) | POM lists MIT (Kotlin wrapper), but `libavutil.so` is compiled with `--enable-gpl --enable-version3` (**GPL-3.0-or-later**) | `arm64-v8a`: 18.31 / 45.34 MB<br>`armeabi-v7a`: 17.01 / 38.53 MB<br>`x86`: 19.39 / 47.52 MB<br>`x86_64`: 19.73 / 51.43 MB<br>*(+ assets/subfont.ttf & classes: 2.93 / 6.32 MB)* |
| **`dev.jdtech.mpv:libmpv`** *(Findroid)* | **`1.1.0`** (`2026-11-15`) | `arm64-v8a`, `armeabi-v7a`, `x86`, `x86_64` | `dev.jdtech.mpv.MPVLib` | FFmpeg `n9.0.2`, mpv `v0.42.0` | **NO** (`--disable-filters` in `ffmpeg.sh`; only `null`, `scale`, `format`, `aresample` present) | **GPL-3.0-or-later** (`--enable-gpl --enable-version3`) | `arm64-v8a`: 11.96 / 26.11 MB<br>`armeabi-v7a`: 11.52 / 23.65 MB<br>`x86`: 12.67 / 28.79 MB<br>`x86_64`: 12.99 / 30.84 MB |
| **`io.github.cd16d:libmpv`** | **`1.1.0-filters.2`** | Fork of `jdtech` | `dev.jdtech.mpv.*` (assumed from fork) | Fork of `jdtech` | **NO video deinterlace** (`--disable-filters` + audio-only `--enable-filter=dynaudnorm,pan,volume,equalizer,acompressor,loudnorm,aresample,aformat,anull,channelsplit,channelcombine,join`) | **GPL-3.0-or-later** | Not downloaded; same base as `jdtech` |
| **`io.github.midasvo:libmpv`** *(mpvEx / fork)* | **`1.2.0`** | Fork of `jdtech` | `dev.jdtech.mpv.*` (assumed from fork) | Fork of `jdtech` | **NO** (`--disable-filters` in `ffmpeg.sh`) | **GPL-3.0-or-later** | Not downloaded; same base as `jdtech` |
| Other Maven artifacts observed | `io.github.makd:libmpv:1.2.0`, `org.jellycine.mpv:libmpv:1.0.4`, `io.github.706412584:libmpv:1.0.0` | — | — | — | Not inspected | — | — |

### Verified Codec / Demuxer / Protocol Support in Both `jdtech:1.1.0` & `mehiz:0.2.1`
Inspected directly via `strings` on `jni/arm64-v8a/libavcodec.so`, `libavformat.so`, `libavfilter.so`:
- **Decoders present in both:** `H.264 / AVC`, `HEVC`, `MPEG-2 video`, `MP2 (MPEG audio layer 2)`, `ATSC A/52A (AC-3)`, `EAC3`, `AAC (Advanced Audio Coding)`, `AAC LATM`, `DVB subtitles` (`dvb_subtitle`), `DVB teletext` (`dvb_teletext`), plus `libdav1d` and `MediaCodec` (`--enable-jni --enable-mediacodec`).
- **Demuxers & protocols present in both:** `mpegts`, `matroska`, `http`, `https` (`--enable-mbedtls`).
- **Recommendation for MuxGrid:** Use **`io.github.abdallahmehiz:mpv-android-lib:0.2.1`** (or build a custom `dev.jdtech.mpv:libmpv` fork enabling `bwdif`) if software deinterlacing (`deinterlace=yes`) is required, because `dev.jdtech.mpv:libmpv:1.1.0` strips `bwdif` and `yadif`. Also note `mehiz` 0.2.1 switched to a KMP artifact structure (`mpv-android-lib` → `mpv-android-lib-android`), bundles a 6.3 MB `assets/subfont.ttf`, and renames the JNI class from `is.xyz.mpv.MPVLib` to `is.xyz.mpv.MPV` (wrapped by `MPVSession` / `BaseMPVView`). **Always configure ABI splits (`armeabi-v7a`, `arm64-v8a`)** to avoid ~35–40 MB bloat in a universal APK.
- **Confidence:** High (verified directly from Maven Central `.pom`, `.module`, `.aar`, and ELF `.so` strings)
- **Sources:**
  - Maven Central: `https://repo1.maven.org/maven2/io/github/abdallahmehiz/mpv-android-lib/0.2.1/` & `.../mpv-android-lib-android/0.2.1/`
  - Maven Central: `https://repo1.maven.org/maven2/dev/jdtech/mpv/libmpv/1.1.0/`
  - Build scripts: <https://github.com/abdallahmehiz/mpv-android/blob/library/buildscripts/scripts/ffmpeg.sh>, <https://github.com/jarnedemeulemeester/libmpv-android/blob/main/buildscripts/scripts/ffmpeg.sh>

---

## 4. `mpv` Demuxer Cache Behavior for Live HTTP Streams

- **Answer (verified in `demux/demux.c` and `DOCS/man/options.rst` for mpv 0.38–0.42):**
  1. **Pausing stops reading when forward cache is full:** In `demux/demux.c` (`read_packet()`), while paused (`!read_more`, since the decoder stops consuming packets), `demux_thread` continues prefetching until `total_fw_bytes >= in->max_bytes` (`--demuxer-max-bytes`, default `150 MiB`) or `--cache-secs` (default `3600000` s = 1000 h) is reached, at which point it stops calling the underlying demuxer/HTTP socket. Tvheadend will see TCP backpressure and an idle/stalled HTTP client until playback resumes or Tvheadend drops the stalled connection.
  2. **`--demuxer-seekable-cache` default:** Defaults to `auto` (`-1`). In `update_opts()` (`demux/demux.c`), whenever `use_cache` is true (`--cache=yes`, or `--cache=auto` when `demuxer->is_streaming` is true — which covers HTTP streams), `seekable < 0` resolves to `1` (`true`). Thus, seeking within the cached live window works automatically without needing to set `--demuxer-seekable-cache=yes` explicitly (though setting `--cache=yes` and `--demuxer-seekable-cache=yes` explicitly guarantees it). Seeking *outside* the cached range triggers a low-level demuxer/HTTP seek (which fails on an unseekable live stream unless `force-seekable` or server support applies).
  3. **`--cache-on-disk=yes` + `--demuxer-cache-dir=<path>` interaction with `--demuxer-max-bytes` / `--demuxer-max-back-bytes`:**
     - `--cache-on-disk=yes` writes **packet payload data** to an append-only temporary file in `--demuxer-cache-dir` (deleted when playback closes; freed/pruned space inside the file is **not** reused while playing).
     - **Important:** `DOCS/man/options.rst` explicitly documents that **packet metadata is still kept in RAM**, and `--demuxer-max-bytes` / `--demuxer-max-back-bytes` still control pruning against the cache size/metadata limits (~50 MB metadata per hour of media is typical). Furthermore, with `--demuxer-donate-buffer=yes` (default), unused forward buffer space is donated to the back buffer once EOF is reached, keeping total usage bounded by `demuxer-max-bytes + demuxer-max-back-bytes`.
  4. **Valid option names in mpv 0.38–0.42:** `--cache=<yes|no|auto>`, `--cache-secs=<seconds>`, `--demuxer-max-bytes=<bytesize>`, `--demuxer-max-back-bytes=<bytesize>`, `--demuxer-donate-buffer=<yes|no>`, `--demuxer-seekable-cache=<yes|no|auto>`, `--cache-on-disk=<yes|no>`, `--demuxer-cache-dir=<path>`, `--force-seekable=<yes|no>`.
- **Confidence:** High
- **Sources:**
  - `mpv` `DOCS/man/options.rst`: <https://github.com/mpv-player/mpv/blob/master/DOCS/man/options.rst>
  - `mpv` `demux/demux.c` (`demux_conf`, `read_packet()`, `update_opts()`): <https://github.com/mpv-player/mpv/blob/master/demux/demux.c>

---

## 5. FFmpeg `http` Protocol: `Content-Range: bytes X-Y/*`, Chunked Encoding, EOF & Options

- **Answer (verified in FFmpeg `libavformat/http.c`):**
  1. **`Accept-Ranges: bytes` & `Content-Range: bytes X-Y/*`:**
     - In `process_line()`, `Accept-Ranges: bytes` sets `h->is_streamed = 0` when `s->seekable == -1` (default), marking the URL as **seekable**.
     - In `parse_content_range()`, when the total after `/` is `*` (non-digit), FFmpeg sets `s->content_range_unknown_size = 1` and keeps `s->filesize_from_content_range = UINT64_MAX`. Unless an earlier response already established a concrete `s->oc_filesize`, `s->filesize` remains `UINT64_MAX`.
     - In `http_seek_internal()`, `AVSEEK_SIZE` and `SEEK_END` return `AVERROR(ENOSYS)` when `s->filesize == UINT64_MAX`, **but `SEEK_SET` and `SEEK_CUR` still work** as long as `h->is_streamed == 0` (because `if (off && h->is_streamed) return AVERROR(ENOSYS);`). A `416` on an unknown-size resource (`is_end_of_resource()`) is treated as EOF.
  2. **`Transfer-Encoding: chunked`:**
     - In `process_line()`, `Transfer-Encoding: chunked` explicitly sets `s->filesize = UINT64_MAX` and `s->chunksize = 0`. Note that a comment in `http_read_header` states *"filesize from Content-Range can always be used, even if using chunked Transfer-Encoding"*, so if `Content-Range` provides a concrete total, `s->filesize` takes that total; if `Content-Range` uses `/*` (or is absent), `s->filesize` stays `UINT64_MAX`. Seekability (`h->is_streamed = 0`) still depends on `Accept-Ranges: bytes` (or `seekable=1`).
  3. **Stopping at `Content-Length` / `Content-Range` total:**
     - Yes: if the server returns `200 OK` with `Content-Length: N` or `206` with `Content-Range: bytes X-Y/N`, `s->filesize` (and `s->oc_filesize`) is set to `N`. FFmpeg treats `N` as the complete resource size: `http_seek_internal()` refuses seeks `>= requested_end(s)` and `http_read_stream()` returns `AVERROR_EOF` once `s->off` reaches `file_end` (or `s->filesize`). That is why a growing recording **must not** send a concrete frozen `Content-Length` or `Content-Range: bytes X-Y/TOTAL` while still growing!
  4. **mpv `force-seekable=yes` vs FFmpeg `seekable`:**
     - `mpv`'s `--force-seekable=yes` tells `mpv`'s stream/demuxer layer to allow seeking (especially within the demuxer cache) even if the underlying stream reports unseekable.
     - FFmpeg's `http` AVOption **`seekable`** (`demuxer-lavf-o=seekable=1` in mpv, default `-1` = probe) forces `h->is_streamed = 0` at `http_open` time and sends `Range:` headers even without waiting for `Accept-Ranges`.
  5. **Relevant FFmpeg reconnect options** (passed via `demuxer-lavf-o=...` in `libmpv`):
     - `reconnect=1` (reconnect on connection/read error for non-streamed or with `reconnect_streamed=1`)
     - `reconnect_streamed=1` (allow reconnect even when `is_streamed == 1`, e.g. live HTTP MPEG-TS)
     - `reconnect_at_eof=1` (useful if upstream drops cleanly at temporary EOF)
     - `reconnect_delay_max=<sec>`, `reconnect_max_retries=<n>`, `reconnect_on_http_error=<codes>` (e.g., `503`, `4xx`)
- **Confidence:** High
- **Sources:**
  - FFmpeg `libavformat/http.c`: <https://github.com/FFmpeg/FFmpeg/blob/master/libavformat/http.c> (`http_options`, `parse_content_range`, `process_line`, `http_read_header`, `http_read_stream`, `http_seek_internal`)

---

## 6. Android Network Security Config (NSC): IP Ranges & Native Code

- **Answer:**
  - **IP ranges / CIDR:** **No.** `<domain-config>` only accepts DNS hostnames (`<domain includeSubdomains="true">`) or exact literal IP addresses (e.g., `192.168.1.5`). CIDR blocks like `192.168.1.0/24` are not supported. For arbitrary user-entered LAN/VPN IPs, an app using Java HTTP stacks needs `<base-config cleartextTrafficPermitted="true" />` (or `android:usesCleartextTraffic="true"`).
  - **Does NSC affect native code (`libmpv` / FFmpeg / raw C sockets)?** **No.** Android's Network Security Config is enforced only by Java/Android framework networking stacks (`NetworkSecurityPolicy`, `HttpsURLConnection`, `TrustManager`, OkHttp, etc.). Native C/C++ code (`libmpv`, FFmpeg `libavformat/http.c` + `mbedtls`, or raw `sys/socket.h`) **completely bypasses NSC** for both cleartext checks and custom CA/pinning.
- **Confidence:** High
- **Sources:**
  - Android Developers — Network security configuration: <https://developer.android.com/privacy-and-security/security-config>

---

## 7. Android Local Network Access Permission (`ACCESS_LOCAL_NETWORK`, Android 17)

- **Answer:**
  - **Permission name:** `android.permission.ACCESS_LOCAL_NETWORK` (runtime permission in the **`NEARBY_DEVICES`** permission group).
  - **Enforcement threshold:** Enforced for apps with **`targetSdk >= 37` (Android 17)**, which rolled out in 2026. Apps targeting API 36 (Android 16) or lower continue to get an implicit grant via `INTERNET` for compatibility.
  - **What it affects:** Enforced in the OS networking stack, so it **does affect all sockets** to local addresses (`192.168.x.x`, `10.x.x.x`, link-local, `.local` mDNS, multicast/broadcast) — including Java `Socket`/OkHttp/Coil **and native C/C++ NDK sockets** (such as `libmpv` / FFmpeg connecting directly to `http://192.168.1.5:9981` and the HTSP `9982` TCP socket). Without the runtime grant on `targetSdk >= 37`, TCP connects typically time out and UDP fails with `EPERM`.
  - **Play Store note:** Google Play requires a restricted-permission declaration for apps targeting Android 17+ that request `ACCESS_LOCAL_NETWORK`.
- **Confidence:** High
- **Sources:**
  - Android Developers — Local network access / Android 17 behavior changes (via `developer.android.com` search results)

---

## 8. TCL C635 (2022 Google TV): Hardware, OS & ABI

- **Answer:**
  - **SoC:** Realtek **RTD2851** (TCL chassis **RT51M**; distinct from C735/C835 which use MediaTek MT9615).
  - **CPU & GPU:** Quad-core **ARM Cortex-A55** (~1.3 GHz), **ARM Mali-G31 MP2** GPU (~800 MHz).
  - **RAM & Storage:** **2 GB DDR** RAM, **16 GB** eMMC flash.
  - **Android version:** Shipped with **Android TV 11** (Google TV UI); official TCL OTA updates (`V5xx` series) largely remained on Android 11.
  - **Supported ABI (`ro.product.cpu.abilist`):** Despite the 64-bit Cortex-A55 CPU, Realtek RTD2851 / RT51M Android TV 11 firmware commonly runs a **32-bit-only userland (`armeabi-v7a`)** (some third-party spec summaries conflate C635 with MT9615 C735 and claim `arm64-v8a`).
  - **Actionable takeaway:** Always verify on the target TV with `adb shell getprop ro.product.cpu.abilist`, and **always build/ship `armeabi-v7a` native binaries** (both `mehiz` and `jdtech` AARs include `armeabi-v7a`).
- **Confidence:** High for SoC/RAM/CPU/GPU and the need to ship `armeabi-v7a`; Medium for whether a specific OTA firmware exposes 32-bit-only `abilist` (verify via `adb` in M0).
- **Sources:**
  - DisplaySpecifications / FoneArena / Setcombg / XDA Developers TCL RT51M/C635 threads (via web search)

---

## 9. Jetpack Compose Blur: `SurfaceView` vs `TextureView`, Haze & Progressive Blur (2026)

- **Answer:**
  - **Can Compose / Haze / `RenderEffect` blur a `SurfaceView` behind it?** **No.** `SurfaceView` punches a hole through the View/Compose window so `SurfaceFlinger` composites video on a separate hardware plane; Compose's drawing pass (and Haze's backdrop capture) cannot sample pixels from a `SurfaceView`.
  - **`TextureView` alternative & cost:** `TextureView` renders video as a GL texture inside the app's view hierarchy, so Haze/Compose *can* capture and blur it. However, `TextureView` adds extra GPU composition/copy overhead, consumes more memory bandwidth/battery, disables secure/zero-copy hardware overlay planes, and can hurt 50fps interlaced video smoothness on low-end 2 GB RAM TVs (`Mali-G31 MP2`). Keep **`SurfaceView`** for video playback and use translucent dark scrims (non-blurred) for video player HUD/OSD.
  - **Native backdrop/progressive blur in Compose 1.13 (2026):** Jetpack Compose added native **progressive blur** via `Modifier.blur(blurRadiusSpec = BlurRadiusSpec.verticalGradient(...), ...)` with `BlurStop` / custom AGSL `RuntimeShader`. **However**, `Modifier.blur` is strictly a **self/layer blur** (it blurs the composable's own drawn content), **not a backdrop blur** of content behind a floating overlay. True frosted-glass backdrop blur over non-video Compose lists still requires **Haze (`2.1.0`)**.
- **Confidence:** High
- **Sources:**
  - Chris Banes — Haze 2.x & Compose blur articles: <https://chrisbanes.me> (via web search)
  - Android Developers — `Modifier.blur` / progressive blur docs (via web search)

---

## 10. Media3 `SimpleBasePlayer` in `MediaSessionService`: Audio Focus, Becoming Noisy & Wake/Wifi Locks

- **Answer:** **Media3 does NOT automatically handle AudioFocus, `ACTION_AUDIO_BECOMING_NOISY`, `WakeLock`, or `WifiLock` for a custom `SimpleBasePlayer`.**
  - Direct inspection of `androidx/media` `release` branch source confirms:
    - `ExoPlayerImpl.java` owns and wires `AudioBecomingNoisyManager`, `WakeLockManager`, `WifiLockManager`, and `AudioFocus` handling inside `ExoPlayer`'s internal player.
    - `MediaSessionService.java` manages the **foreground service lifecycle and media notification** (`foregroundServiceType="mediaPlayback"`), and `SimpleBasePlayer.java` only exposes `setAudioAttributes(audioAttributes, handleAudioFocus)` by forwarding `handleAudioFocus` to your overridden `handleSetAudioAttributes(...)` method.
  - **Implication for `MpvPlaybackEngine`:** You must explicitly implement:
    1. `AudioManager.requestAudioFocus(AudioFocusRequest)` / `abandonAudioFocusRequest` (honoring `AUDIOFOCUS_LOSS`, `LOSS_TRANSIENT`, `LOSS_TRANSIENT_CAN_DUCK`, `GAIN`).
    2. A `BroadcastReceiver` for `AudioManager.ACTION_AUDIO_BECOMING_NOISY` (pausing when headphones/Bluetooth disconnect).
    3. A `WifiManager.WifiLock` (`WIFI_MODE_FULL_LOW_LATENCY` / `WIFI_MODE_FULL_HIGH_PERF` depending on API) + `PowerManager.PARTIAL_WAKE_LOCK` during active background audio/radio playback so Wi-Fi power-save does not stall the HTTP stream when the screen turns off.
- **Confidence:** High (verified directly in `androidx/media` source)
- **Sources:**
  - `androidx/media` `ExoPlayerImpl.java`: <https://github.com/androidx/media/blob/release/libraries/exoplayer/src/main/java/androidx/media3/exoplayer/ExoPlayerImpl.java>
  - `androidx/media` `SimpleBasePlayer.java`: <https://github.com/androidx/media/blob/release/libraries/common/src/main/java/androidx/media3/common/SimpleBasePlayer.java>
  - `androidx/media` `MediaSessionService.java`: <https://github.com/androidx/media/blob/release/libraries/session/src/main/java/androidx/media3/session/MediaSessionService.java>

---

## 11. Latest Stable Library Versions (Verified 2026-11-20) & `security-crypto` Status

Queried directly from `dl.google.com/android/maven2` and `repo1.maven.org/maven2`:

| Dependency | Latest Stable | Latest Pre-release (if newer) | Notes |
| :--- | :--- | :--- | :--- |
| **Compose BOM** (`androidx.compose:compose-bom`) | **`2026.11.01`** | — | Maps Compose `ui`/`foundation`/`animation` to **`1.13.0`** and `material3` to **`1.5.0`**. |
| **Compose for TV** (`androidx.tv:tv-material`) | **`1.1.0`** | — | Stable `1.1.0` available. |
| **Media3** (`androidx.media3:media3-session`) | **`1.11.1`** | `1.12.0-beta01` | — |
| **Coil 3** (`io.coil-kt.coil3:coil`) | **`3.6.3`** | — | Updated `2026-09-18`. |
| **Navigation Compose** (`androidx.navigation:navigation-compose`) | **`2.10.2`** | — | Type-safe `@Serializable` routes supported. |
| **Kotlin** (`org.jetbrains.kotlin:kotlin-stdlib`) | **`2.4.21`** | `2.5.0-RC` | — |
| **Android Gradle Plugin** (`com.android.tools.build:gradle`) | **`9.5.2`** | `9.6.0-alpha05` | — |
| **DataStore** (`androidx.datastore:datastore-preferences`) | **`1.3.0`** | — | — |
| **Google Tink** (`com.google.crypto.tink:tink-android`) | **`1.24.0`** | — | Recommended replacement for `security-crypto` encryption. |
| **Haze** (`dev.chrisbanes.haze:haze`) | **`2.1.0`** | — | Updated `2026-11-02`. |

- **`androidx.security:security-crypto` Deprecation Status:** **Officially deprecated** by Google (finalized alongside the `1.1.0` release). All APIs (`EncryptedSharedPreferences`, `EncryptedFile`) are deprecated due to OEM Keystore corruption bugs and synchronous main-thread I/O. **Recommended replacement:** **Jetpack DataStore (`1.3.0`) + Google Tink (`com.google.crypto.tink:tink-android:1.24.0`)** backed by Android Keystore AEAD (`AndroidKeysetManager`).
- **Confidence:** High
- **Sources:**
  - Google Maven `maven-metadata.xml` & `compose-bom-2026.11.01.pom`: <https://dl.google.com/android/maven2/>
  - Maven Central `maven-metadata.xml`: <https://repo1.maven.org/maven2/>
  - Android Developers / JetSec deprecation reports (via web search)

---

## 12. `LEANBACK_LAUNCHER` Manifest Requirements for Unified Phone + TV APK

- **Answer:**
  1. **`android:banner="@drawable/tv_banner"`** on `<application>` (recommended `320×180` dp/px banner graphic for the Android TV launcher).
  2. **`<uses-feature android:name="android.software.leanback" android:required="false" />`** (required so the APK appears on Android TV while still being installable on phones/tablets; omitting `required="false"` or setting `true` restricts distribution to TV only).
  3. **`<uses-feature android:name="android.hardware.touchscreen" android:required="false" />`** (required so TVs without touchscreens can install the APK).
  4. **`<category android:name="android.intent.category.LEANBACK_LAUNCHER" />`** alongside `LAUNCHER` in the `<activity>` `MAIN` intent-filter.
- **Confidence:** High
- **Sources:**
  - Android Developers — Build TV apps / Declare TV support in manifest: <https://developer.android.com/training/tv/start/start>
