# Tvheadend Protocol & Media Delivery Research (`4.3 / master`)

Source-verified answers to the 14 protocol, authentication, muxer, and streaming questions against upstream [`tvheadend/tvheadend`](https://github.com/tvheadend/tvheadend) (`master` branch, plus `release/4.2` / `release/4.0` where version history was checked).

---

## Summary of Key Implications for MuxGrid

- **Tickets expire in 300 s (5 min) by default** (`ticket_expires`), are reusable within that window, and keep an already-open TCP stream alive past expiry — but **new** HTTP `Range` requests (e.g., seeking in a completed recording) or stream reconnects fail with `401` once expired. Either route through the loopback proxy (refreshing tickets), use Digest/Basic auth, or use 4.3 `auth=` persistent streaming tokens.
- **`fileOpen("imagecache/...")` over HTSP requires `ACCESS_HTSP_RECORDER`**, NOT just `ACCESS_HTSP_STREAMING`. A read-only viewing user without DVR access cannot fetch picons via HTSP VFS, whereas HTTP `GET /imagecache/<id>` accepts `ACCESS_STREAMING` / `ACCESS_HTSP_STREAMING` / `auth=`.
- **In-progress `.mkv` recordings (`muxer_mkv.c`) have no `Cues`, no `SeekHead`, no `Duration`, and an unknown Segment size (`0x01FFFFFFFFFFFFFF`)** until `mk_mux_close()` runs at recording end. Seeking during recording relies on demuxer scanning/probing rather than index lookup.
- **No-tuner failure on HTTP `/stream/channelid/<id>` does NOT return HTTP `503`:** after ~2 s of retries, `http_stream_channel()` overwrites `res = 0` and closes the socket with **0 bytes / empty reply** (no HTTP status line).
- **`htsmsg` wire types include `7` (`HMF_BOOL`) and `8` (`HMF_UUID`) natively** (`false` `HMF_BOOL` has `dataLen = 0`!), while `6` (`HMF_DBL`) calls `abort()` in the server serializer and never goes on the wire.
- **HTSP `stopDvrEntry` (v25+) vs `cancelDvrEntry` (v5+) vs `deleteDvrEntry` (v4+):** `stopDvrEntry` stops an active recording cleanly (`SM_CODE_OK` -> `completed`), `cancelDvrEntry` aborts an active recording (`SM_CODE_ABORTED`) or deletes a scheduled entry, and `deleteDvrEntry` deletes files + entry (or moves to removed state until retention expires). `removeDvrEntry` does not exist.

---

## 1. HTTP Stream Tickets (`getTicket`)

- **Default lifetime:** **300 seconds (5 minutes)** (`config.ticket_expires = 5 * 60`).
- **Configurable:** **Yes.** Option ID `ticket_expires` (*Ticket expiration (seconds)*, `PT_U32`, expert option), clamped in code to **30–3600 seconds** via `MINMAX(config.ticket_expires, 30, 3600)`.
- **Reusable vs. single-use:** **Reusable multiple times within the lifetime.** `http_access_verify_ticket()` calls `access_ticket_verify2()`, which looks up the ticket in `access_tickets` and returns `access_copy(at->at_access)` without deleting it. (Additionally, `access_ticket_create()` reuses an existing ticket for the same resource and access profile if it is less than 60 seconds old.)
- **Scope:** `htsp_method_getTicket()` only accepts `channelId` (path `/stream/channelid/<id>`) or `dvrId` (path `/dvrfile/<id>`). It cannot issue tickets for `/imagecache/<id>`.
- **Expiry behavior (in-progress vs. new request):**
  - **New request after expiry:** `access_ticket_timeout()` destroys the `access_ticket_t` when its `mtimer` expires; `access_ticket_verify2()` returns `NULL`, and `http_access_verify()` falls through to `auth=` / `Authorization` / IP ACL checks (returning `401 Unauthorized` if none succeed).
  - **Request already in progress:** `http_exec()` calls `http_access_verify()` once before invoking `hp->hp_callback()` and only destroys `hc->hc_access` after `hp_callback()` returns. Because `hc->hc_access` holds an independent `access_copy()`, an already-open live `/stream/...` or single `/dvrfile/...` transfer continues past ticket expiry. Subsequent HTTP `Range` requests or reconnects fail.
- **Confidence:** **High**
- **Sources:**
  - `src/config.c` (`config.ticket_expires = 5 * 60`, `ticket_expires` `INTEXTRA_RANGE(30, 3600, 1)`): https://github.com/tvheadend/tvheadend/blob/master/src/config.c
  - `src/access.c` (`access_ticket_create`, `access_ticket_verify2`, `access_ticket_timeout`): https://github.com/tvheadend/tvheadend/blob/master/src/access.c
  - `src/http.c` (`http_access_verify_ticket`, `http_access_verify`, `http_exec`): https://github.com/tvheadend/tvheadend/blob/master/src/http.c
  - `src/htsp_server.c` (`htsp_method_getTicket`): https://github.com/tvheadend/tvheadend/blob/master/src/htsp_server.c

---

## 2. Persistent Authentication (`auth=` URL Parameter)

- **How it works:**
  - Each `passwd_entry_t` has `pw_auth_enabled` (bool) and `pw_auth` (token string, configured under *Users → Passwords → Persistent authentication* / `auth`).
  - On HTTP requests, `http_access_verify_auth()` reads the `auth` query parameter (`?auth=<token>`) and calls `access_get_by_auth(hc->hc_peer, auth_id)`.
  - `access_get_by_auth()` locates the matching enabled `passwd_entry_t`, evaluates IP/ACL rules via `access_get()`, and **masks privileges down to streaming only**:
    ```c
    a->aa_rights &= ACCESS_ADVANCED_STREAMING | ACCESS_STREAMING;
    ```
    Thus an `auth=` token cannot access Web UI, Admin, or DVR write/API endpoints, and still enforces the user's IP masks, channel tags, and allowed streaming profiles.
- **Which endpoints accept it:** Any route processed by `http_exec()` -> `http_access_verify()` whose required permission mask is satisfied by `ACCESS_STREAMING | ACCESS_ADVANCED_STREAMING`:
  - `/stream/...` (`http_stream` -> `http_access_verify_channel(hc, ACCESS_STREAMING, ch)`): **Yes**
  - `/dvrfile/<id>` (`page_dvrfile` checks `ACCESS_STREAMING | ACCESS_ADVANCED_STREAMING | ACCESS_RECORDER`, plus `dvr_entry_verify()`): **Yes**
  - `/imagecache/<id>` (`page_imagecache` checks `ACCESS_WEB_INTERFACE | ACCESS_STREAMING | ACCESS_ADVANCED_STREAMING | ACCESS_HTSP_STREAMING | ACCESS_HTSP_RECORDER | ACCESS_RECORDER`): **Yes**
  - `/play/auth/...` and `/playlist/auth/...` (`page_play_auth`, `page_http_playlist_auth`) also require `hc->hc_access->aa_auth` to be non-empty.
- **Version introduced:** **Tvheadend 4.3 (`master` only; not in `4.2.x`).** Introduced in commit `ee714fc1` (2018-10-19, *"add pernament tickets for the authentization, fixes #5274"*), with `ACCESS_ADVANCED_STREAMING` added in `ac48db1f` (2018-10-27).
- **Confidence:** **High**
- **Sources:**
  - `src/access.c` (`passwd_auth_find`, `access_get_by_auth`), commit `ee714fc1`, commit `ac48db1f`: https://github.com/tvheadend/tvheadend/blob/master/src/access.c
  - `src/http.c` (`http_access_verify_auth`, `http_access_verify`): https://github.com/tvheadend/tvheadend/blob/master/src/http.c
  - `src/webui/webui.c` (`page_dvrfile`, `page_imagecache`, `page_play_auth`): https://github.com/tvheadend/tvheadend/blob/master/src/webui/webui.c
  - `docs/markdown/url.md`: https://github.com/tvheadend/tvheadend/blob/master/docs/markdown/url.md

---

## 3. HTTP Basic vs. Digest & `/imagecache/<id>` Auth

- **Default HTTP auth mode in 4.3:** **`HTTP_AUTH_DIGEST`** (`Digest`) with **`HTTP_AUTH_ALGO_MD5`** (`config.http_auth = HTTP_AUTH_DIGEST; config.http_auth_algo = HTTP_AUTH_ALGO_MD5;`).
  - `process_request()` rejects `Authorization: Basic ...` with `401 Unauthorized` unless `config.http_auth` is `HTTP_AUTH_PLAIN` or `HTTP_AUTH_PLAIN_DIGEST`.
  - Supported Digest algorithms: `MD5`, `SHA-256` (`HTTP_AUTH_ALGO_SHA256`), and `SHA-512/256` (`HTTP_AUTH_ALGO_SHA512_256`).
- **Does `/imagecache/<id>` require auth by default?** **Yes, unless a wildcard `*` anonymous ACL entry matches the client IP.**
  - Although `/imagecache` is registered with `ACCESS_ANONYMOUS` in `http_path_add()`, `page_imagecache()` explicitly checks:
    ```c
    access_verify2(hc->hc_access, ACCESS_OR | ACCESS_WEB_INTERFACE | ACCESS_STREAMING |
                   ACCESS_ADVANCED_STREAMING | ACCESS_HTSP_STREAMING |
                   ACCESS_HTSP_RECORDER | ACCESS_RECORDER)
    ```
    and returns `http_noaccess_code(hc)` (`401` if unauthenticated) if none of those rights are present.
  - On a brand-new empty config (`access_init()`), Tvheadend creates a wide-open `*` entry; once `*` is removed/restricted (standard secure setup), `/imagecache/<id>` requires authentication (Digest/Basic, `auth=`, or HTSP `fileOpen`).
- **Confidence:** **High**
- **Sources:**
  - `src/config.c` (`config.http_auth = HTTP_AUTH_DIGEST`, `config.http_auth_algo = HTTP_AUTH_ALGO_MD5`): https://github.com/tvheadend/tvheadend/blob/master/src/config.c
  - `src/http.c` (`process_request`): https://github.com/tvheadend/tvheadend/blob/master/src/http.c
  - `src/webui/webui.c` (`page_imagecache`, `http_path_add("/imagecache", ...)`): https://github.com/tvheadend/tvheadend/blob/master/src/webui/webui.c
  - `src/access.c` (`access_init`, `access_get`): https://github.com/tvheadend/tvheadend/blob/master/src/access.c

---

## 4. HTTP `/dvrfile/<id>` for an In-Progress Recording

- **Size snapshotting vs. growing files:** **Snapshots size at open; does NOT follow growing files.**
  - `page_dvrfile()` delegates to `http_serve_file()`, which opens the file (`tvh_open`) and calls `fstat(fd, &st)` **once**.
  - It computes `file_end = st.st_size - 1`, sets `Content-Length` (and `Content-Range: bytes <start>-<end>/<st.st_size>` if `Range` is present), and streams `content_len` bytes via `sendfile()` in a tight loop, then closes `fd` and finishes the response. Any bytes appended after `fstat` are ignored.
- **Range support & edge-case behavior:**
  - Supports single byte ranges (`sscanf(range, "bytes=%jd-%jd", &file_start, &file_end)`), including open-ended `bytes=X-` (where `file_end` remains `st.st_size - 1`), responding with `206 Partial Content` and `Content-Range`.
  - **Out-of-bounds / suffix-range quirk:** If `file_start < 0`, `file_start >= st.st_size`, `file_end < 0`, `file_end >= st.st_size`, or `file_start > file_end` (which also happens for suffix ranges like `bytes=-500`, parsed as `file_start = -500`), `http_serve_file()` closes `fd` and returns `HTTP_STATUS_OK` (`200`). `http_exec()` then calls `http_error(hc, 200)`, sending an HTML `200 OK` page rather than `416 Range Not Satisfiable`.
- **Confidence:** **High**
- **Sources:**
  - `src/webui/webui.c` (`page_dvrfile`, `http_serve_file`): https://github.com/tvheadend/tvheadend/blob/master/src/webui/webui.c
  - `src/http.c` (`http_exec`, `http_error`): https://github.com/tvheadend/tvheadend/blob/master/src/http.c

---

## 5. HTSP File VFS (`fileOpen`, `fileStat`, `fileRead`, `fileSeek`, `fileClose`)

- **Supported paths in `htsp_method_file_open()`** (leading `/` is stripped if present):
  - `dvr/<id>` or `dvrfile/<id>`: looks up `dvr_entry_find_by_id(atoi(s2))`, enforces `dvr_entry_verify(de, htsp->htsp_granted_access, 1)`, opens `dvr_get_filename(de)` `O_RDONLY`, and attaches a `"HTSP"` file subscription for transfer stats and playcount tracking.
  - `imagecache/<id>`: looks up `imagecache_filename(atoi(s2), ...)`, opens `O_RDONLY` (no `dvr_entry` subscription).
  - Any other path returns error `"Unknown file"`.
- **`fileStat` (`htsp_method_file_stat`):** Calls `fstat(hf->hf_fd, &st)` directly on the open file descriptor and returns live `size` (`s64`) and `mtime` (`s64`). **Reflects the real-time growing byte size** of an active recording.
- **`fileRead` (`htsp_method_file_read`):** Takes `id` (`u32`), `size` (`s64`), and optional `offset` (`s64`, performing `lseek(fd, off, SEEK_SET)` before reading). Calls POSIX `read(fd, m, size)` and returns `data` (`HMF_BIN`) of length `r`.
  - **At EOF / past write head:** `read()` returns `0` (an empty `data` binary field, `r == 0`) or a short read (`r < size`) **without an error**. A proxy should poll `fileStat` and retry `fileRead` while `state == "recording"`.
- **`fileSeek` (`htsp_method_file_seek`):** Takes `id` (`u32`), `offset` (`s64`), and optional `whence` (`str`: `"SEEK_SET"` default, `"SEEK_CUR"`, `"SEEK_END"`). Calls `lseek(fd, off, whence)` and returns new `offset` (`s64`).
- **`fileClose` (`htsp_method_file_close`):** Closes `fd` and unsubscribes stats. For DVR files, if `htsp_version < 27` (or `playcount == HTSP_DVR_PLAYCOUNT_INCR`), increments `de_playcount`; if `htsp_version >= 27`, also updates `de_playposition` if provided.
- **Minimum HTSP version:**
  - `fileOpen`, `fileRead`, `fileClose`, `fileStat`, `fileSeek` were introduced in **HTSP v8** (all present in `release/4.0` HTSP v20+, including `imagecache/` support).
  - `fileClose` `playposition`/`playcount` fields require **`htsp_version >= 27`**.
- **Required access rights:** **`ACCESS_HTSP_RECORDER`** in `htsp_methods[]` (`privmask` for all five `file*` RPCs).
  - **Gotcha:** Even `fileOpen("imagecache/...")` requires **`ACCESS_HTSP_RECORDER`** (`HTSP DVR`), NOT just `ACCESS_HTSP_STREAMING`. Users without DVR access get `noaccess=1`.
- **Confidence:** **High**
- **Sources:**
  - `src/htsp_server.c` (`htsp_method_file_open`, `htsp_method_file_read`, `htsp_method_file_close`, `htsp_method_file_stat`, `htsp_method_file_seek`, `htsp_file_open`, `htsp_methods[]`): https://github.com/tvheadend/tvheadend/blob/master/src/htsp_server.c

---

## 6. Matroska Muxer (`src/muxer/muxer_mkv.c`)

- **When are `SeekHead` (MetaSeek) and `Cues` written?** **Only at close (`mk_mux_close`), never periodically during recording.**
  - At init (`mkv_muxer_init` -> `mk_build_segment`), if `mk->seekable`, Tvheadend writes a 500-byte EBML `Void` placeholder (`ebml_append_pad(p, 500)`).
  - During recording, `addcue()` only appends `mk_cue_t` entries to an in-memory queue (`mk->cues`).
  - Only in `mk_mux_close()` does Tvheadend flush the final cluster, call `mk_write_cues(mk)` (`muxer_mkv.c:1314`), call `mk_write_chapters(mk)`, seek back to `segment_pos` to overwrite the 500-byte placeholder via `mk_write_metaseek(mk, 0)`, rewrite `SegmentInfo` (with `Duration`), and rewrite the `Segment` header size.
- **Is Segment size unknown while recording?** **Yes.** `mkv_muxer_init()` calls `mk_build_segment_header(0)`, which writes the 8-byte EBML unknown-size sentinel (`0x01FFFFFFFFFFFFFF`) until `mk_mux_close()` rewrites it with `totsize - mk->segment_header_pos - 12`.
- **Is there any option for periodic cues?** **No.** Neither `muxer_mkv.c` nor `muxer.h` / `profile.c` exposes any option for periodic Cues or SeekHead flushing.
- **Implication for seeking an in-progress `.mkv`:** While recording is active, the file on disk has an unknown Segment size, no `Duration`, no `Cues` index, and an empty `SeekHead` placeholder. Demuxers (FFmpeg/libmpv) cannot perform O(1) cue-based seeks; seeking requires linear cluster scanning/probing (and `force-seekable=yes` if the demuxer treats unknown-duration Matroska over HTTP as unseekable).
- **Confidence:** **High**
- **Sources:**
  - `src/muxer/muxer_mkv.c` (`mkv_muxer_init`, `mk_build_segment_header`, `mk_build_segment`, `mk_write_metaseek`, `addcue`, `mk_write_cues`, `mk_mux_close`): https://github.com/tvheadend/tvheadend/blob/master/src/muxer/muxer_mkv.c
  - `src/muxer.h` (`muxer_config_t`): https://github.com/tvheadend/tvheadend/blob/master/src/muxer.h

---

## 7. HTSP DVR RPCs & `playposition` / `playcount`

All DVR control RPCs require `ACCESS_HTSP_RECORDER` and look up the entry via `htsp_findDvrEntry()` + `dvr_entry_verify()`.

| RPC | Min HTSP Version | Backing Function (`src/dvr/dvr_db.c`) | Exact Semantics |
| :--- | :---: | :--- | :--- |
| **`stopDvrEntry`** | **25** (commit `b1e89e6f` / `2b5ae9a7`, 2016-02-09) | `dvr_entry_stop(de)` | **Active recordings only (`DVR_RECORDING`):** stops recording cleanly with `SM_CODE_OK` (marking entry `completed` with no error). No-op for `scheduled`, `completed`, `missed`. |
| **`cancelDvrEntry`** | **5** | `dvr_entry_cancel(de, 0)` | **`DVR_RECORDING`:** aborts recording with `SM_CODE_ABORTED`. **`DVR_SCHEDULED` / `DVR_NOSTATE`:** destroys DB entry. **`DVR_COMPLETED` / `DVR_MISSED_TIME`:** no-op. Marks autorec parent `dont_rerecord`. |
| **`deleteDvrEntry`** | **4** | `dvr_entry_cancel_remove(de, 0)` -> `dvr_entry_cancel_delete_remove(de, 0, 0)` | **`DVR_RECORDING`:** aborts with `SM_CODE_ABORTED` then deletes files. **`DVR_COMPLETED` / `DVR_MISSED_TIME`:** deletes recorded file(s) (`dvr_entry_delete`); destroys DB entry immediately if retention expired, or keeps as removed recording until retention expires. **`DVR_SCHEDULED`:** destroys DB entry. |
| **`removeDvrEntry`** | — | **Does not exist** | Not present in `htsp_methods[]` or `htsp_server.c`. |

- **`updateDvrEntry` (`playposition` & `playcount`):**
  - `updateDvrEntry` (base method v5) accepts `playcount` (`u32`) and `playposition` (`u32`, **in seconds** — `uint32_t de_playposition; /* Recording last played position in seconds */` in `src/dvr/dvr.h`).
  - `playcount` supports special values (`HTSP_DVR_PLAYCOUNT_INCR`, `HTSP_DVR_PLAYCOUNT_SET`, `HTSP_DVR_PLAYCOUNT_RESET`, `HTSP_DVR_PLAYCOUNT_KEEP`) or an explicit integer count.
  - **Pushed in `dvrEntryAdd` / `dvrEntryUpdate`:** **Yes** (`htsp_build_dvrentry()` always includes `playcount` and `playposition` when `de_sched_state == DVR_RECORDING || de_sched_state == DVR_COMPLETED`, with no version gate on output in `master`).
  - **Minimum version for `playposition` / `playcount`:** Introduced in **HTSP v26** (commit `790b38ce` + `d220e59a`, 2016-11-04) and finalized/refined in **HTSP v27** (commit `729d7b66` + `1ad3fb28`, 2017-04-14; `fileClose` gates on `htsp_version >= 27`).
- **Confidence:** **High**
- **Sources:**
  - `src/htsp_server.c` (`htsp_method_stopDvrEntry`, `htsp_method_cancelDvrEntry`, `htsp_method_deleteDvrEntry`, `htsp_method_updateDvrEntry`, `htsp_build_dvrentry`): https://github.com/tvheadend/tvheadend/blob/master/src/htsp_server.c
  - `src/dvr/dvr_db.c` (`dvr_entry_stop`, `dvr_entry_cancel`, `dvr_entry_cancel_delete_remove`, `dvr_entry_cancel_remove`): https://github.com/tvheadend/tvheadend/blob/master/src/dvr/dvr_db.c
  - `src/dvr/dvr.h` (`de_playposition`, `de_playcount`): https://github.com/tvheadend/tvheadend/blob/master/src/dvr/dvr.h
  - Commits `b1e89e6f`, `2b5ae9a7`, `790b38ce`, `d220e59a`, `729d7b66`, `1ad3fb28`.

---

## 8. `dvrEntry` `state` Field & Related Status Fields

- **Is `state` still sent or deprecated?** **Still actively sent** unconditionally in `htsp_build_dvrentry()` (not deprecated).
- **Values of `state` (`str`):**
  - `DVR_SCHEDULED` -> `"scheduled"`
  - `DVR_RECORDING` -> `"recording"`
  - `DVR_COMPLETED` -> `"completed"`
  - `DVR_MISSED_TIME` -> `"missed"`
  - `DVR_NOSTATE` -> `"invalid"`
- **Other status/error fields in `dvrEntryAdd` / `dvrEntryUpdate`:**
  - `error` (`str`, optional): set to `streaming_code2txt(de->de_last_error)` when `recording` is in `DVR_RS_ERROR` (or `DVR_RS_PENDING` with `de_last_error != SM_CODE_OK`), or when `completed` failed `!dvr_entry_is_completed_ok(de)`; set to `"File missing"` if `completed` and `dvr_get_filesize() < 0`.
  - `subscriptionError` (`str`, optional): `_htsp_get_subscription_status(de->de_last_error)` when `recording` has an error/pending error.
  - `streamErrors` (`u32`, optional): emitted when `de->de_errors != 0`.
  - `dataErrors` (`u32`, optional): emitted when `de->de_data_errors != 0`.
  - `dataSize` (`s64`, optional): live byte size via `dvr_get_filesize(de, DVR_FILESIZE_UPDATE)` for `recording` and `completed` when `>= 0`.
  - `duplicate` (`u32`, optional): `0` or `1` when `dvr_entry_is_upcoming(de)`.
  - Note: HTSP uses `state`, **not** `status` or `schedStatus` (`schedStatus` is only an HTTP JSON API field).
- **Confidence:** **High**
- **Sources:**
  - `src/htsp_server.c` (`htsp_build_dvrentry`): https://github.com/tvheadend/tvheadend/blob/master/src/htsp_server.c

---

## 9. Current `HTSP_PROTO_VERSION`

- **`master` (4.3 dev):** **`44`** (`#define HTSP_PROTO_VERSION 44`)
- **`release/4.2` / `v4.2.8`:** **`27`** (`#define HTSP_PROTO_VERSION 27`)
- **`release/4.0`:** **`20`** (`#define HTSP_PROTO_VERSION 20`)
- **Negotiation (`htsp_method_hello`):** Server replies with `htspversion = HTSP_PROTO_VERSION` and sets `htsp->htsp_version = MIN(HTSP_PROTO_VERSION, clientVersion)`.
- **Confidence:** **High**
- **Sources:**
  - `src/htsp_server.c` (`master`, `release/4.2`, `v4.2.8`, `release/4.0`).

---

## 10. `htsmsg` Binary Wire Types (`src/htsmsg_binary.c`, `src/htsmsg.h`)

| Type ID | Constant | Wire Serialization Behavior (`htsmsg_binary_write` / `des0`) |
| :---: | :--- | :--- |
| `1` | `HMF_MAP` | Supported: sequence of named `HTSMSG-Field` entries. |
| `2` | `HMF_S64` | Supported: unsigned/two's-complement little-endian 64-bit int with trailing `0x00` bytes stripped (`0` -> `dataLen = 0`; negative -> 8 bytes LE). |
| `3` | `HMF_STR` | Supported: raw UTF-8 bytes (not null-terminated). |
| `4` | `HMF_BIN` | Supported: raw binary bytes. |
| `5` | `HMF_LIST` | Supported: sequence of nameless (`nameLen == 0`) `HTSMSG-Field` entries. |
| `6` | `HMF_DBL` | **NOT supported on wire:** `htsmsg_binary_write()` has no `HMF_DBL` case and hits `default: abort()`; `htsmsg_binary_des0()` fails (`return -1`) on unknown types. |
| `7` | `HMF_BOOL` | **Serialized natively with type ID `7`** (NOT as `S64`): `true` writes `dataLen = 1` (`0x01`); **`false` writes `dataLen = 0` (empty payload)**! Deserializer sets `hmf_bool = datalen == 1 ? buf[0] : 0`. |
| `8` | `HMF_UUID` | **Serialized natively with type ID `8`** (NOT as `BIN`): fixed `dataLen = 16` (`UUID_BIN_SIZE`). |

- **Practical note:** Core HTSP RPCs/events use types `1–5` (`u32`/`s64` for booleans, hex strings for UUIDs), though `htsp_method_api` proxies internal `api_exec` responses which could theoretically construct `HMF_BOOL` or `HMF_UUID` (and must avoid `HMF_DBL`). Client decoders should support `1–5`, `7`, and `8`, and skip any unrecognized future type ID by advancing `dataLen` bytes.
- **Confidence:** **High**
- **Sources:**
  - `src/htsmsg.h` (`HMF_*` defines): https://github.com/tvheadend/tvheadend/blob/master/src/htsmsg.h
  - `src/htsmsg_binary.c` (`htsmsg_binary_des0`, `htsmsg_binary_count`, `htsmsg_binary_write`): https://github.com/tvheadend/tvheadend/blob/master/src/htsmsg_binary.c

---

## 11. HTSP Keepalive & Idle Connection Timeout

- **Is there a `ping` method?** **No.** `htsp_methods[]` contains no `ping`, `pong`, `noop`, or `keepalive` method.
- **Is `getSysTime` recommended?** **Yes.** `htsp_method_getSysTime` (requires `ACCESS_HTSP_STREAMING`) is lightweight (just `gettimeofday()` + `localtime_r()`, returning `time`, `timezone`, `gmtoffset`) and is the standard application-level heartbeat / clock-skew check used by HTSP clients (e.g., `pvr.hts`).
- **Does the server time out idle HTSP connections?** **No.** `htsp_read_loop()` calls `htsp_read_message(htsp, &m, 0)` where `timeout = 0` means infinite blocking `tcp_read()`. Idle HTSP connections remain open indefinitely until TCP disconnect/error, server shutdown, or OS-level TCP keepalive failure.
- **Confidence:** **High**
- **Sources:**
  - `src/htsp_server.c` (`htsp_read_message`, `htsp_read_loop`, `htsp_method_getSysTime`, `htsp_methods[]`): https://github.com/tvheadend/tvheadend/blob/master/src/htsp_server.c

---

## 12. Server Timeshift: HTTP `/stream` vs. HTSP

- **HTSP only.**
  - `htsp_method_subscribe()` reads `timeshiftPeriod` and passes it to `profile_chain_work(&hs->hs_prch, &hs->hs_input, timeshiftPeriod)` (`src/profile.c`), which calls `timeshift_create()` when `timeshift_period != 0`.
  - `http_stream_channel()` / `http_stream_service()` in `src/webui/webui.c` only calls `profile_chain_open()` (never `profile_chain_work()`), so server-side timeshift is **never activated** for HTTP `/stream/...` subscriptions.
- **Confidence:** **High**
- **Sources:**
  - `src/htsp_server.c` (`htsp_method_subscribe`): https://github.com/tvheadend/tvheadend/blob/master/src/htsp_server.c
  - `src/webui/webui.c` (`http_stream_channel`): https://github.com/tvheadend/tvheadend/blob/master/src/webui/webui.c
  - `src/profile.c` (`profile_chain_work`, `profile_mpegts_pass_open`): https://github.com/tvheadend/tvheadend/blob/master/src/profile.c

---

## 13. HTTP `/stream/channelid/<id>` When No Free Tuner Is Available

- **Status code & response:** **Closes the TCP connection with 0 bytes / empty reply (NO HTTP `503` status line sent).**
  - `http_stream_channel()` initializes `res = HTTP_STATUS_SERVICE` (`503`), creates the subscription (`SUBSCRIPTION_MPEGTS | SUBSCRIPTION_STREAMING`, without `SUBSCRIPTION_RESTART`), and enters `http_stream_run()`.
  - `http_stream_run()` only calls `http_output_content()` (`200 OK`) upon receiving `SMT_START` (`started = 1`).
  - When no adapter/tuner is free, `subscription_reschedule()` (`src/subscriptions.c`) retries for **~2 seconds** (`s->ths_last_find + sec2mono(2) >= mclk()`), then emits `SMT_NOSTART` (`SM_CODE_NO_FREE_ADAPTER`).
  - `http_stream_run()` handles `SMT_NOSTART` by setting `run = 0` and returning (still with `started == 0`, so nothing was written to the socket).
  - `http_stream_channel()` then unconditionally runs `res = 0;` after `http_stream_run()` returns (`src/webui/webui.c`), returning `0` to `http_exec()`. `http_exec()` skips `http_error()`, and because `do_stream()` set `hc->hc_keep_alive = 0`, `http_serve_requests()` exits its loop and calls `close(fd)`.
  - **Delay:** **~2 seconds** for `pass` / standard `SUBSCRIPTION_STREAMING` (up to `grace = 20s` if `SUBSCRIPTION_RESTART` were enabled or packets never arrived).
- **Client implication:** `libmpv`/FFmpeg will receive an immediate EOF / premature end-of-headers error after ~2 s — **not** an HTTP `503` status code. Single-tuner conflict detection cannot rely on matching `503` from mpv; it must inspect HTSP `dvrEntry` `state == "recording"` (or `subscriptions` API) on *any* live stream open failure.
- **Confidence:** **High**
- **Sources:**
  - `src/webui/webui.c` (`do_stream`, `http_stream_channel`, `http_stream_run`): https://github.com/tvheadend/tvheadend/blob/master/src/webui/webui.c
  - `src/subscriptions.c` (`subscription_reschedule`): https://github.com/tvheadend/tvheadend/blob/master/src/subscriptions.c
  - `src/http.c` (`http_exec`, `http_serve_requests`): https://github.com/tvheadend/tvheadend/blob/master/src/http.c

---

## 14. `channelIcon` (and Image Fields) Format in HTSP

- Generated by `htsp_image(htsp, image, buf, buflen, version, airtime)` in `src/htsp_server.c`:
  - If `imagecache_get_id_prio()` returns `0` (not cached / disabled / raw URL unused by cache): returns the raw `image` string unchanged.
  - If cached under integer `id`:
    - `htsp_version < version` (`version = 8` for `channelIcon`; `version = 34` for `tagIcon`, DVR `image`/`fanartImage`, EPG `image`): returns full URL `http://<local-ip>:<webui_port><webroot>/imagecache/<id>`.
    - `htsp_version < 15`: returns `"/imagecache/<id>"` (with leading slash).
    - `htsp_version >= 15` (all modern clients, e.g., v44): returns `"imagecache/<id>"` (**relative path without leading slash**).
- **Confidence:** **High**
- **Sources:**
  - `src/htsp_server.c` (`htsp_image`, `htsp_build_channel`, `htsp_build_dvrentry`, `htsp_build_event`): https://github.com/tvheadend/tvheadend/blob/master/src/htsp_server.c
