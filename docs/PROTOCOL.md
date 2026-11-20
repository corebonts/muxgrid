# Protocol & Media Bridge Reference

This document summarizes the exact Tvheadend protocol subset required by `:core:htsp` and `:core:player`. It is intentionally concise — refer to the official Tvheadend HTSP specification for optional metadata fields.

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

### Field Type IDs (`HMF_*`)
| ID | Type | Payload Encoding |
| :---: | :--- | :--- |
| `1` | `MAP` | Recursive sequence of named `HTSMSG-Field` entries |
| `2` | `S64` | **Little-endian** 64-bit signed integer with trailing `0x00` bytes stripped (e.g., `1337` → `[0x39, 0x05]`). **No sign extension:** negative values MUST be encoded as full 8-byte two's complement little-endian (e.g., `-1` → 8×`0xFF`). |
| `3` | `STR` | Raw UTF-8 bytes (not null-terminated; length given by `dataLen`) |
| `4` | `BIN` | Raw binary bytes |
| `5` | `LIST` | Recursive sequence of **nameless** `HTSMSG-Field` entries (`nameLen == 0`) |
| `6` | `DBL` | IEEE-754 double (little-endian / platform standard; rarely needed beyond optional metadata) |
| `7` | `BOOL` | Boolean (1 byte) |

### Request / Response Correlation
- Client attaches an incrementing integer `seq` and `method` string to every RPC request.
- Server responds with a message containing the matching `seq` (and optional `error` string on failure).
- Server-initiated push events have a `method` field and no `seq`.
- Guard socket reads/writes with dedicated reader & writer coroutines so concurrent RPCs (`getTicket`, `fileRead`) never interleave frames.

---

## 2. Required HTSP Handshake & Sync Sequence

1. **`hello`** (Client -> Server):
   - Request: `method="hello"`, `htspversion=34` (or negotiate down to server's max), `clientname`, `clientversion`.
   - Reply: `htspversion`, `servername`, `serverversion`, `challenge` (`bin`, 32 bytes), `servercapability` (`list` of strings).
2. **`authenticate`** (Client -> Server, if credentials configured):
   - Compute `digest = SHA1(passwordBytes + challengeBytes)`.
   - Request: `method="authenticate"`, `username`, `digest` (`bin`).
   - Reply: `noaccess=1` if denied; empty map on success.
3. **`enableAsyncMetadata`** (Client -> Server):
   - Request: `method="enableAsyncMetadata"`, `epg=1` (optional: `epgMaxTime` epoch seconds, e.g., now + 7 days, to keep initial sync tiny and instant).
   - Server streams initial snapshot via push events (`tagAdd`, `channelAdd`, `eventAdd`, `dvrEntryAdd`), then sends **`initialSyncCompleted`** (`method="initialSyncCompleted"`).
   - After `initialSyncCompleted`, keep the socket open to receive live incremental updates (`*Add`, `*Update`, `*Delete`).

---

## 3. Core HTSP Push Messages & RPCs

### Push Events (Server -> Client)
- **Channels:** `channelAdd`, `channelUpdate`, `channelDelete`
  - Key fields: `channelId`, `channelNumber`, `channelNumberMinor`, `channelName`, `channelIcon` (may be `imagecache/<id>` or URL), `tags` (`list<u32>`), `services` (inspect type to distinguish Radio vs TV if tags don't already cover it).
- **Tags:** `tagAdd`, `tagUpdate`, `tagDelete` (`tagId`, `tagName`, `tagMembers`).
- **EPG Events:** `eventAdd`, `eventUpdate`, `eventDelete`
  - Key fields: `eventId`, `channelId`, `start`, `stop` (Unix epoch seconds), `title`, `subtitle`, `summary`, `description`, `nextEventId`, `dvrId` (links scheduled/active DVR entry!).
- **DVR Recordings:** `dvrEntryAdd`, `dvrEntryUpdate`, `dvrEntryDelete`
  - Key fields: `id` (`u32`), `channel` (`u32`), `start`, `stop`, `startExtra`, `stopExtra`, `title`, `subtitle`, `description`, `state` (`"scheduled"`, `"recording"`, `"completed"`, `"missed"`, `"invalid"`), `error` (`str`), `dataSize` (`s64`), `dataErrors` (`u32`).

### Control & Media RPCs (Client -> Server)
- **Stream/File Auth Ticket (`getTicket`):**
  - Request: `channelId` (for Live TV) OR `dvrId` (for completed recording).
  - Reply: `path` (e.g., `/stream/channelid/691304913` or `/dvrfile/1169292538`), `ticket` (`str`).
- **DVR Management:**
  - `addDvrEntry`: `eventId` (EPG-based) OR `channelId` + `start` + `stop` + `title` (manual timer).
  - `cancelDvrEntry`: `id` (cancels scheduled or stops active recording).
  - `deleteDvrEntry`: `id` (deletes recording entry & file).
- **Virtual File System (Growing Recordings & Picons):**
  - `fileOpen`: `file="/dvrfile/<id>"` (or `"/imagecache/<id>"`) -> Reply: `id` (handle), `size`, `mtime`.
  - `fileStat`: `id` -> Reply: `size` (live updated byte size!), `mtime`.
  - `fileRead`: `id`, `size`, `offset` -> Reply: `data` (`bin`, may be shorter than `size` at current EOF).
  - `fileClose`: `id`.

---

## 4. Media Delivery URLs & Growing-Recording Bridge

### Live TV & Radio (Direct HTTP `pass` -> `libmpv`)
1. Request ticket via HTSP: `getTicket(channelId = channel.id)`.
2. Build URL:
   ```text
   http://<host>:<httpPort><path>?ticket=<ticket>&profile=pass
   ```
3. Before calling `loadfile` on a new channel, **always stop the previous stream** so Tvheadend releases single-tuner adapters cleanly.

### Completed Recordings (`state == "completed"`)
1. Request ticket via HTSP: `getTicket(dvrId = entry.id)`.
2. Play directly in `libmpv`:
   ```text
   http://<host>:<httpPort><path>?ticket=<ticket>
   ```

### In-Progress / Growing Recordings (`state == "recording"`)
Use a lightweight in-process loopback HTTP/1.1 server (`HtspLoopbackProxy`) bound strictly to `127.0.0.1:<ephemeralPort>` (with a per-session random path token so no other app on the device can read it):

```text
libmpv  ──HTTP GET + Range: bytes=X-Y──▶  127.0.0.1:<port>/<token>/dvr/<dvrId>
                                                        │
                                          HTSP fileOpen / fileStat / fileRead
                                                        ▼
                                            Tvheadend Server (9982/tcp)
```

**Bridge Contract:**
- Opens HTSP virtual file `/dvrfile/<dvrId>` on first request (or keeps a handle per active playback session) and closes it via `fileClose` when playback stops.
- Calls `fileStat` to learn the latest growing file size on `HEAD` / `GET` / `Range` requests (avoiding stale `Content-Length` EOF cutoffs).
- Responds with `206 Partial Content`, `Accept-Ranges: bytes`, and streams `fileRead` chunks (e.g., 128 KB–256 KB per HTSP read) to satisfy `libmpv` reads smoothly.
- Pass `--force-seekable=yes` to `libmpv`, and compute the UI's live timeline duration from HTSP `dvrEntry` (`max(mpvDuration, now - actualStart)`), clamping user seeks slightly inside the recorded tail so `libmpv` never stalls past the current write head.

> **Implementation Tip:** Use a **separate HTSP connection** (or low-priority queuing) for `fileRead` chunks if needed so large bulk reads never delay metadata/control messages on the primary HTSP connection.

---

## 5. Recommended `libmpv` Baseline Options

Tune during Milestone 2 on actual hardware (phone + TCL C635); keep these configurable in `PlaybackConfig`:

| Scenario | Key `libmpv` Options | Rationale |
| :--- | :--- | :--- |
| **Fast Live TV startup** | `demuxer-lavf-o=analyzeduration=500000,probesize=524288` | Avoids multi-second FFmpeg probe stalls on MPEG-TS while still detecting all audio/DVBSUB tracks. (Tune upward if a channel's secondary audio/sub track is missed.) |
| **Live Pause / Rewind** | `cache=yes`, `demuxer-max-bytes=128MiB`, `demuxer-max-back-bytes=128MiB` (or `cache-on-disk=yes` + `demuxer-cache-dir=<appCacheDir>`) | Provides instant in-memory or disk-backed timeshift without triggering OOM on 2 GB RAM TVs. |
| **1080i Deinterlacing (Phone)** | `hwdec=mediacodec-copy`, `deinterlace=yes` | Eliminates combing artifacts on mobile SoCs while preserving hardware decode speed. |
| **1080i Playback (Android TV)** | `hwdec=mediacodec`, `deinterlace=auto` | Zero-copy hardware path utilizing TV display processor; user-overridable to `mediacodec-copy` + `deinterlace=yes` in Settings. |
| **Background Audio (Phone)** | `vid=no` when surface detaches (restore `vid=auto` on foreground) | Keeps Radio & Live TV audio playing smoothly with screen off while releasing GPU/video decode resources. |
| **Growing Recordings** | `force-seekable=yes` | Ensures `libmpv` allows timeline seeking on actively growing `.mkv` recordings. |
