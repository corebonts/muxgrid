# Protocol & Media Bridge Reference

This document summarizes the exact Tvheadend (`4.3+` / `master`, backward-compatible with `4.2.8`) protocol subset required by `:core:htsp` and `:core:player`. Claims below have been verified directly against upstream Tvheadend and FFmpeg/`mpv` source.

---

## 1. HTSP Binary Wire Format (`htsmsg` over TCP `9982`)

Every message on the TCP stream is a length-prefixed binary map:

```text
┌───────────────────────────┬──────────────────────────────────────────┐
│ totalLength (u32, BE, 4B) │ Sequence of HTSMSG-Fields (totalLength B)│
└───────────────────────────┴──────────────────────────────────────────┘
```

Each `HTSMSG-Field` is laid out as:

```text
┌──────────┬──────────────┬─────────────────────────┬───────────────┬──────────────┐
│ type(1B) │ nameLen (1B) │ dataLen (u32, BE, 4B)   │ name (UTF-8)  │ data (bytes) │
└──────────┴──────────────┴─────────────────────────┴───────────────┴──────────────┘
```

### Field Type IDs (`HMF_*` — Verified in `src/htsmsg_binary.c`)
| ID | Type | Payload Encoding |
| :---: | :--- | :--- |
| `1` | `MAP` | Recursive sequence of named `HTSMSG-Field` entries |
| `2` | `S64` | **Little-endian** 64-bit signed integer with trailing `0x00` bytes stripped (`0` → `dataLen = 0`; `1337` → `[0x39, 0x05]`). **No sign extension:** negative values MUST be encoded as full 8-byte two's complement little-endian (e.g., `-1` → 8×`0xFF`). |
| `3` | `STR` | Raw UTF-8 bytes (not null-terminated; length given by `dataLen`) |
| `4` | `BIN` | Raw binary bytes |
| `5` | `LIST` | Recursive sequence of **nameless** `HTSMSG-Field` entries (`nameLen == 0`) |
| `6` | `DBL` | **NOT supported on the wire** (`htsmsg_binary_write` calls `abort()` on `HMF_DBL`!). Never emit type `6`. |
| `7` | `BOOL` | **Native wire type:** `true` = `dataLen = 1` (`0x01`); **`false` = `dataLen = 0` (empty payload)**! |
| `8` | `UUID` | **Native wire type:** fixed 16-byte binary UUID (`dataLen = 16`). |

> **Decoder Robustness Rule:** Decode types `1–5`, `7`, and `8`. For any unrecognized future type ID, safely skip `dataLen` bytes instead of aborting the stream.

### Request / Response Correlation & Heartbeat
- Client attaches an incrementing integer `seq` and `method` string to every RPC request.
- Server responds with a message containing the matching `seq` (and optional `error` string or `noaccess=1` on failure).
- Server-initiated push events have a `method` field and no `seq`.
- Guard socket reads/writes with dedicated reader & writer coroutines so concurrent RPCs (`getTicket`, `fileRead`, `getSysTime`) never interleave frames.
- **Heartbeat & Idle Behavior:** HTSP has **no `ping` method** and **no server-side idle timeout** (server blocks indefinitely on TCP read). Enable `SO_KEEPALIVE` and issue periodic **`getSysTime`** RPCs (e.g., every 30–60 s), which also yields server clock offset for accurate EPG/live progress bars.
- **Reconnect / Atomic Resync:** Deletes occurring while disconnected are not replayed. On reconnect, re-run `enableAsyncMetadata` into a **shadow store** and swap atomically into the exposed `StateFlow` upon receiving `initialSyncCompleted`.

---

## 2. Required HTSP Handshake & Sync Sequence

1. **`hello`** (Client -> Server):
   - Request: `method="hello"`, `htspversion=44` (server sets negotiated version to `min(client, server)`; `master`/4.3 reports `44`, `4.2.8` reports `27`, `4.0` reports `20`), `clientname`, `clientversion`.
   - Reply: `htspversion`, `servername`, `serverversion`, `challenge` (`bin`, 32 bytes), `servercapability` (`list` of strings).
2. **`authenticate`** (Client -> Server, if credentials configured):
   - Compute `digest = SHA1(passwordBytes + challengeBytes)`.
   - Request: `method="authenticate"`, `username`, `digest` (`bin`).
   - Reply: `noaccess=1` if denied; empty map on success.
3. **`enableAsyncMetadata`** (Client -> Server):
   - Request: `method="enableAsyncMetadata"`, `epg=1`, `epgMaxTime` (epoch seconds, e.g. `now + 7d`).
   - Server streams initial snapshot via push events (`tagAdd`, `channelAdd`, `eventAdd`, `dvrEntryAdd`), then sends **`initialSyncCompleted`** (`method="initialSyncCompleted"`).
   - Keep the socket open while active to receive live incremental updates (`*Add`, `*Update`, `*Delete`). Close after a grace period when backgrounded and not playing to save battery.

---

## 3. Core HTSP Push Messages & RPCs

### Push Events (Server -> Client)
- **Channels:** `channelAdd`, `channelUpdate`, `channelDelete`
  - Key fields: `channelId`, `channelNumber`, `channelNumberMinor`, `channelName`, `channelIcon`, `tags` (`list<u32>`), `services` (inspect type to distinguish Radio vs TV if tags don't already cover it).
  - **`channelIcon` format:** Returns `"imagecache/<id>"` (no leading slash) at `htspversion >= 15`, `"/imagecache/<id>"` at `8..14`, or a raw external `http(s)://...` URL if uncached. Normalize all three forms in the Coil fetcher.
- **Tags:** `tagAdd`, `tagUpdate`, `tagDelete` (`tagId`, `tagName`, `tagMembers`).
- **EPG Events:** `eventAdd`, `eventUpdate`, `eventDelete`
  - Key fields: `eventId`, `channelId`, `start`, `stop` (Unix epoch seconds), `title`, `subtitle`, `summary`, `description`, `nextEventId`, `dvrId` (links scheduled/active DVR entry).
- **DVR Recordings:** `dvrEntryAdd`, `dvrEntryUpdate`, `dvrEntryDelete`
  - Key fields: `id` (`u32`), `channel` (`u32`), `start`, `stop`, `startExtra`, `stopExtra`, `title`, `subtitle`, `description`, `state` (`"scheduled"`, `"recording"`, `"completed"`, `"missed"`, `"invalid"`), `error` (`str`), `subscriptionError` (`str`), `streamErrors` (`u32`), `dataErrors` (`u32`), `dataSize` (`s64`), `duplicate` (`u32`), `playposition` (`u32`, **seconds**), `playcount` (`u32`).
  - *(Note: HTSP always sends `state`, not the HTTP JSON API field `schedStatus`.)*

### Control & Media RPCs (Client -> Server)
- **Heartbeat / Clock Sync:**
  - `getSysTime`: returns `time`, `timezone`, `gmtoffset`. Requires `ACCESS_HTSP_STREAMING`.
- **Stream/File Auth Ticket (`getTicket`):**
  - Request: `channelId` (for Live TV) OR `dvrId` (for completed recording). *(Cannot issue tickets for `/imagecache/<id>`.)*
  - Reply: `path` (e.g., `/stream/channelid/691304913` or `/dvrfile/1169292538`), `ticket` (`str`).
  - **Ticket TTL:** Defaults to **300 s** (configurable 30–3600 s), reusable within its lifetime; open transfers survive expiry, but new `Range` requests or reconnects fail with `401`. Handled automatically by `LocalMediaProxy`.
- **DVR Management (all require `ACCESS_HTSP_RECORDER`):**
  - `addDvrEntry`: `eventId` (EPG-based) OR `channelId` + `start` + `stop` + `title` (manual timer).
  - `stopDvrEntry` (**HTSP v25+**): `id` — cleanly stops an active `recording` (`SM_CODE_OK` → `completed`; no-op on other states).
  - `cancelDvrEntry` (**v5+**): `id` — cancels `scheduled` entries OR aborts an active `recording` (`SM_CODE_ABORTED`, marking autorec `dont_rerecord`).
  - `deleteDvrEntry` (**v4+**): `id` — aborts/deletes recorded file(s) and removes or expires the entry. *(Note: `removeDvrEntry` does not exist.)*
  - `updateDvrEntry` (**`playposition`/`playcount` in v26/27+**): `id`, `playposition` (seconds), `playcount` (count or special constants). Keeps resume/watched state synced across Phone and TV.
- **Virtual File System (`fileOpen` / `fileStat` / `fileRead` / `fileSeek` / `fileClose` — HTSP v8+):**
  - **Permission Catch:** All five `file*` RPCs require **`ACCESS_HTSP_RECORDER`** (`HTSP DVR`), NOT just `ACCESS_HTSP_STREAMING`. Read-only users without `HTSP DVR` receive `noaccess=1` even on `fileOpen("imagecache/...")`, so the picon fetcher falls back to authenticated HTTP (`/imagecache/<id>`).
  - `fileOpen`: `file="dvrfile/<id>"` (or `"imagecache/<id>"`; leading `/` is stripped by server) → Reply: `id` (handle), `size`, `mtime`.
  - `fileStat`: `id` → Reply: live `fstat` `size` (reflects growing recording size in real time) and `mtime`.
  - `fileRead`: `id`, `size`, `offset` → Reply: `data` (`bin`; returns `0` bytes / short read at current EOF without error).
  - `fileClose`: `id` (optionally accepts `playposition` / `playcount` on `htspversion >= 27`).

---

## 4. Unified `LocalMediaProxy` & Media Delivery Contract

All `libmpv` playback URLs point to an in-process HTTP/1.1 loopback server (`LocalMediaProxy`) bound strictly to `127.0.0.1:<ephemeralPort>` with a per-session random path token so no other app on the device can access it:

```text
libmpv  ──HTTP GET (+ Range)──▶  127.0.0.1:<port>/<token>/{live|dvr|growing}/<id>
                                                  │
                    ┌─────────────────────────────┴──────────────────────────────┐
                    ▼ (live & completed DVR)                                     ▼ (growing DVR)
      HTSP getTicket (auto-refreshed <300s)                          HTSP fileOpen / fileStat / fileRead
      + Upstream HTTP :9981 proxy                                    (Tail-follow, Content-Range: X-Y/*)
                    │                                                            │
                    └─────────────────────────────┬──────────────────────────────┘
                                                  ▼
                                   Tvheadend Server (9981 & 9982)
```

### A. Live TV & Radio (`/<token>/live/<channelId>`)
1. `LocalMediaProxy` requests a fresh ticket via HTSP `getTicket(channelId)` and opens `http://<host>:<httpPort><path>?ticket=<ticket>&profile=pass`.
2. Streams bytes straight through to `libmpv` (`video/mp2t`). If `libmpv` reconnects later, the proxy fetches a fresh ticket automatically (no 300 s `401` failures).
3. **Single-Tuner Teardown:** Stopping or zapping explicitly cancels and closes the upstream HTTP socket before opening the next channel.
4. **Single-Tuner Conflict Detection:** When no DVB-T adapter is free, Tvheadend retries for ~2 s and closes the upstream TCP socket with **0 bytes / empty reply** (it does **not** send HTTP `503`). Seeing a 0-byte/EOF open failure, the proxy notifies `PlaybackEngine`, which checks the HTSP store for any `dvrEntry` with `state == "recording"` on another channel and surfaces `TunerBusy(recordingTitle)`.

### B. Completed Recordings (`/<token>/dvr/<dvrId>`)
1. `LocalMediaProxy` requests (and caches up to ~240 s, refreshing automatically on expiry or `401`) a ticket via HTSP `getTicket(dvrId)`.
2. Forwards `HEAD` / `GET` / `Range` requests to `http://<host>:<httpPort><path>?ticket=<ticket>`, preserving `206 Partial Content`, `Content-Range`, and `Content-Length` for instant Matroska cue-based seeking across multi-hour recordings.

### C. In-Progress / Growing Recordings (`/<token>/growing/<dvrId>`)
Tvheadend's HTTP `/dvrfile/<id>` snapshots `fstat` once at request open time (and returns an HTML `200` on out-of-bounds ranges). Therefore, any recording with `state == "recording"` is served via HTSP VFS with **tail-follow semantics**:
- Opens HTSP `/dvrfile/<dvrId>` via `fileOpen` (using a dedicated/separate HTSP connection or non-blocking read queue so bulk reads never delay metadata/control messages) and **always** calls `fileClose` when playback stops or the client disconnects.
- **Tail-Follow HTTP Contract (Verified in FFmpeg `libavformat/http.c`):**
  - **NEVER** send a frozen concrete `Content-Length` or `Content-Range: bytes X-Y/TOTAL` while `state == "recording"` — FFmpeg saves `TOTAL` in `s->filesize`, refuses seeks past it, and hits `AVERROR_EOF`.
  - Reply with **`Accept-Ranges: bytes`** and **`Content-Range: bytes X-Y/*`** (unknown total) or `Transfer-Encoding: chunked` without a known total. This keeps FFmpeg's `h->is_streamed = 0` and `s->filesize = UINT64_MAX`, allowing `SEEK_SET`/`SEEK_CUR` without capping EOF.
  - **NEVER** return `416` at the growing write head (FFmpeg treats `416` on an unknown-size resource as EOF). When `fileRead` returns `0`/short bytes while `state == "recording"`, poll `fileStat` every ~500 ms and keep streaming (waiting or clamping if a requested range starts past current size).
  - End the response cleanly only when the DVR entry leaves `recording` (`completed` / `missed`) or the client disconnects.
- **Growing MKV Note:** Tvheadend's `src/muxer/muxer_mkv.c` writes an unknown EBML Segment size (`0x01FFFFFFFFFFFFFF`) and a 500-byte `Void` placeholder, writing `Cues`, `SeekHead`, and `Duration` only in `mk_mux_close()`. Seeking an actively recording `.mkv` therefore depends on FFmpeg/`libmpv` linear cluster scanning (with `force-seekable=yes`). Compute UI total duration as `max(mpvDuration, now - actualStart)` and clamp user seeks slightly behind the write head.

---

## 5. Recommended `libmpv` Baseline Options

Device-appropriate hardware decode/deinterlace presets are configurable in Settings and the player Display Options panel:

| Scenario | Key `libmpv` Options | Rationale |
| :--- | :--- | :--- |
| **Android Video Output** | `vo=gpu`, `gpu-context=android`, `opengl-es=yes` (on `SurfaceView`) | Required so bitmap `DVBSUB` subtitles and OSD composite cleanly with both `mediacodec` and `mediacodec-copy`. (`vo=mediacodec_embed` drops `DVBSUB`/OSD.) |
| **1080i Deinterlacing (Phone / Copy Mode)** | `hwdec=mediacodec-copy`, `deinterlace=yes` | Uses FFmpeg `bwdif` on CPU (requires `mpv-android-lib:0.2.1`, NOT `jdtech:libmpv` which strips `bwdif`). Eliminates 1080i combing on mobile SoCs. |
| **1080i Playback (Android TV Fast Path)** | `hwdec=mediacodec` (`vo=gpu`), `deinterlace=no` | Zero-copy hardware decode (skips mpv's `bwdif` filter; relies on TV display pipeline deinterlacing). User-overridable in Settings. |
| **Fast Live TV Startup** | `demuxer-lavf-o=analyzeduration=500000,probesize=524288` | Avoids multi-second FFmpeg probe stalls on MPEG-TS while still detecting all audio/DVBSUB tracks. (Tune upward if a secondary track is missed.) |
| **Live Pause / Rewind (Timeshift)** | `cache=yes`, `demuxer-seekable-cache=yes`, `cache-on-disk=yes`, `demuxer-cache-dir=<appCacheDir>/mpv-cache`, `demuxer-max-bytes=<halfCap>`, `demuxer-max-back-bytes=<halfCap>` | **Both** forward (`demuxer-max-bytes`, caps pause duration!) and back (`demuxer-max-back-bytes`, caps rewind) must be sized to half the total disk cap (e.g., `1 GB` max disk = `512 MiB` each on TV [~35 min SD / ~15 min HD], `2 GB` max disk = `1 GiB` each on Phone [~70 min SD / ~30 min HD]). Temp file is append-only (deleted on close); packet metadata stays in RAM (~50 MB/hr). Track seekability via `demuxer-cache-state.seekable-ranges` (`mpv` reports `seekable=false` for live HTTP even while in-cache seeks work). |
| **Background Audio (Phone)** | `vid=no` when surface detaches (restore `vid=auto` on foreground) | Keeps Radio & Live TV audio playing smoothly with screen off while releasing GPU/video decode resources. |
| **Growing Recordings** | `force-seekable=yes` | Allows timeline seeking on actively growing `.mkv` streams with unknown Segment size / duration. |
