---
name: capture-readme-screenshots
description: >-
  Capture authentic, anonymized Android & Android TV README screenshots for MuxGrid
  on real devices using the local mock Tvheadend server (scripts/mock_tvh_server.py),
  an isolated side-by-side .demo debug build, and ADB port forwarding — without
  touching the user's real MuxGrid installation or credentials.
---

# Capture Anonymized MuxGrid README Screenshots

Use this procedure whenever MuxGrid's UI changes and you need fresh, authentic
screenshots for `README.md` (`docs/assets/screenshots/`) without exposing real
server addresses, credentials, or local broadcast channels.

## Prerequisites

- Host machine with `python3`, `Pillow` (`PIL`), `ffmpeg`, and `adb`.
- Connected Android TV and/or Android phone (`adb devices -l`).

## 1. Build an Isolated Side-by-Side Demo APK

Temporarily add `applicationIdSuffix = ".demo"` to the `debug` build type in
`app/build.gradle.kts` so the demo app installs as `io.github.corebonts.muxgrid.demo`
with its own isolated DataStore (leaving the user's real `io.github.corebonts.muxgrid`
install and saved server profile untouched):

```kotlin
buildTypes {
    debug {
        applicationIdSuffix = ".demo"
    }
    release {
        // unchanged
    }
}
```

Build the ABI-split debug APKs:

```bash
./gradlew assembleDebug
```

- 32-bit Android TV (`armeabi-v7a`): `app/build/outputs/apk/debug/app-armeabi-v7a-debug.apk`
- Phone / 64-bit TV (`arm64-v8a`): `app/build/outputs/apk/debug/app-arm64-v8a-debug.apk`

## 2. Start the Mock Tvheadend Server

Run the standalone mock server on non-colliding host ports (`19982` / `19981`):

```bash
python3 scripts/mock_tvh_server.py --htsp-port 19982 --http-port 19981
```

It serves:
- **HTSP (`19982`)**: Generic English TV & Radio channels, clock-anchored EPG schedules,
  active/completed/scheduled DVR entries, `getTicket`, and HTSP VFS picons.
- **HTTP (`19981`)**: Generated PNG picons (`/imagecache/<id>`) and a looping 720p
  H.264/AAC scenic MPEG-TS stream (`/stream/channelid/<id>`) for real `libmpv` playback.

## 3. Install, Forward Ports, and Inject Demo Server Config

For each target `<serial>` (match ABI to APK):

```bash
adb -s <serial> install -r app/build/outputs/apk/debug/app-<abi>-debug.apk
adb -s <serial> reverse tcp:9982 tcp:19982
adb -s <serial> reverse tcp:9981 tcp:19981
```

> **OEM fallback (e.g. Xiaomi HyperOS):** If `adb install` fails with
> `INSTALL_FAILED_USER_RESTRICTED`, either enable **Developer Options → Install via USB**
> or push the APK to `/sdcard/Download/muxgrid-demo.apk` for manual tap-to-install.

Instead of typing into the setup screen (TV soft keyboards can drop characters via
`adb shell input text`), inject a minimal Preferences DataStore pointing to
`127.0.0.1:9982` / `9981` into the debuggable `.demo` app:

```bash
python3 - <<'PY'
def varint(n):
    out = bytearray()
    while True:
        b = n & 0x7F
        n >>= 7
        if n: out.append(b | 0x80)
        else: out.append(b); break
    return bytes(out)

def str_entry(k, v):
    v_msg = b'\x2a' + varint(len(v.encode())) + v.encode()
    kb = k.encode()
    e = b'\x0a' + varint(len(kb)) + kb + b'\x12' + varint(len(v_msg)) + v_msg
    return b'\x0a' + varint(len(e)) + e

def int_entry(k, v):
    v_msg = b'\x18' + varint(v)
    kb = k.encode()
    e = b'\x0a' + varint(len(kb)) + kb + b'\x12' + varint(len(v_msg)) + v_msg
    return b'\x0a' + varint(len(e)) + e

pb = (
    str_entry("server_name", "MuxGrid Demo")
    + str_entry("server_host", "127.0.0.1")
    + int_entry("server_htsp_port", 9982)
    + int_entry("server_http_port", 9981)
    + str_entry("server_username", "demo")
)
open("/tmp/demo_settings.preferences_pb", "wb").write(pb)
PY

adb -s <serial> shell am force-stop io.github.corebonts.muxgrid.demo
adb -s <serial> push /tmp/demo_settings.preferences_pb /data/local/tmp/demo_settings.preferences_pb
adb -s <serial> shell "run-as io.github.corebonts.muxgrid.demo mkdir -p files/datastore && run-as io.github.corebonts.muxgrid.demo cp /data/local/tmp/demo_settings.preferences_pb files/datastore/muxgrid_settings.preferences_pb"
adb -s <serial> shell am start -n io.github.corebonts.muxgrid.demo/io.github.corebonts.muxgrid.MainActivity
```

## 4. Capture Screenshots

Wait ~4 seconds after launch for initial HTSP sync and Coil picon loading, then capture
into `docs/assets/screenshots/`:

- **Android TV** (`tv-channels.png`, `tv-epg.png`, `tv-dvr.png`, `tv-player.png`):
  - Capture via `adb -s <tv-serial> exec-out screencap -p > docs/assets/screenshots/<name>.png`.
  - Navigate the TV rail with D-pad keyevents (`KEYCODE_DPAD_LEFT` into the rail,
    `KEYCODE_DPAD_UP` ×4 to anchor reliably at **Live**, then `KEYCODE_DPAD_DOWN` +
    `KEYCODE_DPAD_CENTER` for **Guide** or **Recordings**).
  - Avoid extra `KEYCODE_BACK` presses, which can exit `.demo` and reveal the real
    MuxGrid app underneath.
- **Android Phone** (`mobile-channels.png`, `mobile-epg.png`, `mobile-dvr.png`, `mobile-player.png`):
  - Capture via `adb -s <phone-serial> exec-out screencap -p`, or if the OEM blocks
    `adb shell input tap` (`INJECT_EVENTS`), take screenshots directly on the device
    and pull them from `/sdcard/Pictures/Screenshots/`, converting to optimized PNGs
    with Pillow.

## 5. Visual Verification & Cleanup (Mandatory)

1. **Inspect every PNG visually** before committing to verify:
   - Only generic English demo channels/shows appear (no real local channels or server IPs).
   - Picons, EPG progress bars, and `libmpv` video frames rendered cleanly.
2. **Revert temporary Gradle edit:** Remove `debug { applicationIdSuffix = ".demo" }`
   from `app/build.gradle.kts` and verify `git diff app/` is empty.
3. **Clean up devices and host:**
   ```bash
   adb -s <serial> uninstall io.github.corebonts.muxgrid.demo
   adb -s <serial> reverse --remove-all
   adb -s <serial> shell rm -f /data/local/tmp/demo_settings.preferences_pb /sdcard/Download/muxgrid-demo.apk /sdcard/ui.xml
   rm -f /tmp/demo_settings.preferences_pb
   ```
4. Stop `scripts/mock_tvh_server.py`.
5. Update `README.md` if filenames changed and commit only the intended files.
