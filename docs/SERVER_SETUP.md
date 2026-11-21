# Tvheadend Server Setup Guide

This guide walks through configuring **Tvheadend (`4.3+`)** so MuxGrid can connect, stream live TV/radio with zero transcoding, browse EPG data, pause/rewind live TV locally, and manage or chase-play DVR recordings — while remaining compatible with other clients such as Jellyfin or Kodi.

---

## 1. Requirements & Ports

Ensure your Tvheadend server is running **4.3+** (older `4.2.8` servers also work for core streaming/DVR, though `4.3` is recommended) and exposes both TCP ports on your LAN or VPN:

| Port | Protocol | Purpose |
| :--- | :--- | :--- |
| **`9981/tcp`** | HTTP | Live MPEG-TS streams (`/stream/channelid/...`), completed recordings (`/dvrfile/...`), and HTTP fallback for channel picons (`/imagecache/...`) |
| **`9982/tcp`** | HTSP | Control channel, real-time channel/EPG/DVR push sync, SHA-1 auth, stream tickets (`getTicket`), server-side resume sync, and VFS access for growing recordings |

If Tvheadend runs inside Docker or behind a host firewall, verify both `9981/tcp` and `9982/tcp` are published/allowed.

> **Security Note:** HTSP (`9982/tcp`) is a raw binary TCP protocol and does not sit behind a standard HTTPS reverse proxy. For remote access outside your LAN, use a **VPN (such as WireGuard or Tailscale)** rather than exposing `9981`/`9982` directly to the internet.

---

## 2. Recommended Settings Overview

| Area | Recommended Setting | Why |
| :--- | :--- | :--- |
| **Live TV Stream Profile** | **`pass`** (MPEG-TS Pass-thru), enabled & set as default | Zero server transcoding, fastest zapping (<1 s same-mux), preserves `1080i` H.264, `MP2`/`AC3`/`AAC` audio, and `DVBSUB` subtitles. |
| **HTSP Profile** | **`htsp`** enabled | Required for HTSP connections and capabilities. |
| **DVR Recording Profile** | **`matroska`** (`.mkv`) | Reliable seeking in completed recordings via native Matroska cues; broadly compatible with media servers. |
| **Client User / ACL** | Dedicated user (e.g., `muxgrid`) | Keeps Web UI/Admin rights disabled and isolates credentials from other clients. |
| **Server Timeshift** | Disabled or **On-Demand** | MuxGrid buffers live pause/rewind **locally on the Android device**; server-side timeshift is only used by HTSP `subscribe` clients (such as Kodi). |

---

## 3. Step-by-Step Tvheadend Web UI Setup

Open your Tvheadend Web UI (`http://<tvheadend-host>:9981`) as an administrator. Ensure you are in **Expert** or **Advanced** view level (top-right user/settings menu if options are hidden).

### Step A — Enable Required Stream Profiles

Go to **Configuration → Stream → Stream Profiles**:

1. **`pass`** (*MPEG-TS Pass-thru*): Check **Enabled** and set as **Default** (if appropriate for your HTTP clients).
2. **`htsp`** (*HTSP Default Stream Settings*): Check **Enabled**.
3. **`matroska`** (*Matroska (mkv) / built-in*): Check **Enabled** (used for DVR recordings).

### Step B — Verify the Default DVR Profile

Go to **Configuration → Recording → Digital Video Recorder Profiles → (Default profile)**:

1. **Stream profile:** Select **`matroska`** (`.mkv`).
   - *Note:* Tvheadend writes Matroska `Cues` and `SeekHead` indices when a recording finishes. While a recording is still in progress, MuxGrid streams it via HTSP VFS tail-follow and lets `libmpv` scan clusters for chase-play seeking.
2. **Subtitles / Reorder:** Keeping **`dvbsub_reorder`** enabled helps keep DVB bitmap subtitles clean in `.mkv` recordings.
3. **Subdirectories / Filename formatting:** Any clean pathname format works; MuxGrid addresses recordings by DVR entry ID rather than path.

### Step C — Create a Dedicated User & Access Entry

Create a dedicated user in **both** places under **Configuration → Users**:

1. **Configuration → Users → Passwords → Add**:
   - **Enabled:** Checked
   - **Username:** `muxgrid` (or your preferred client username)
   - **Password:** Set a strong password (MuxGrid authenticates via HTSP SHA-1 challenge-response and HTTP Digest MD5, and stores the password encrypted in Android Keystore via Google Tink)
2. **Configuration → Users → Access Entries → Add**:
   - **Enabled:** Checked
   - **Username:** `muxgrid` (must match the Passwords entry exactly)
   - **Allowed networks:**
     - LAN only: e.g. `192.168.1.0/24` (adjust to your subnet)
     - LAN + VPN: include your WireGuard/Tailscale CIDR (e.g. `100.64.0.0/10`)
   - **Streaming:**
     - `Basic` ✅
     - `Advanced` ✅
     - `HTSP` ✅
   - **Streaming profile:** Select **`pass`** and **`htsp`** *(do not restrict this user to `matroska` only, or live TV HTTP streams using `?profile=pass` will fail)*
   - **Video recorder** *(choose one permission tier)*:
     - **Recommended (Full Live + DVR + Chase-Play + Cross-Device Resume):**
       - `Basic` ✅
       - `HTSP` ✅ *(required by Tvheadend for HTSP `fileOpen`/`fileRead`/`fileStat` — used for watching in-progress recordings, HTSP channel picons, and server-side resume sync)*
       - `All (rw)` ✅ *(allows scheduling, stopping, canceling, and deleting recordings from MuxGrid)*
       - `Failed` ✅ *(optional, lets the app display missed/failed entries cleanly)*
     - **Read-Only Viewer (Live TV + Watch Completed Recordings Only):**
       - Leave `All (rw)` and `HTSP` DVR unchecked if you want a view-only profile (e.g., kids/guest TV). MuxGrid will automatically fall back to HTTP for channel picons and store DVR resume positions locally on the device. *(Note: Tvheadend restricts HTSP VFS `fileOpen` to users with `HTSP` DVR permission, so chasing in-progress recordings requires `HTSP` DVR access.)*
   - **Web interface:** Unchecked ❌
   - **Admin:** Unchecked ❌

Click **Save** on both **Access Entries** and **Passwords**.

### Step D — Server Timeshift Settings (Optional)

Go to **Configuration → Recording → Timeshift**:

- MuxGrid never triggers Tvheadend's server-side timeshift disk buffer because it streams live TV over HTTP (`pass`) and maintains a size-capped ring buffer locally on the Android device.
- If **no other client** uses HTSP `subscribe` timeshift (e.g., Kodi `pvr.hts`), you can leave server timeshift **Disabled** (or set **On-Demand** checked) to avoid unnecessary server disk writes.

---

## 4. Single-Tuner vs. Multi-Tuner Servers

- **Same-Mux Sharing:** Any number of viewers or recordings on channels sharing the **same frequency / mux** work simultaneously without conflict.
- **Single-Tuner Behavior:** If your server has only one tuner and a DVR recording is active on one mux while you tune a channel on a different mux, Tvheadend keeps the recording running and closes the new live HTTP stream after ~2 seconds with an empty reply. MuxGrid detects this via HTSP active recording state and displays a clear `"Tuner busy"` notice naming the active recording.
- **Priority:** Keep Tvheadend's default **DVR streaming priority** higher than live client priority so channel zapping never interrupts scheduled recordings.

---

## 5. Verifying Your Server

Run these checks from a machine on your LAN/VPN before connecting from the app (replace `<tvheadend-host>`, `<username>`, `<password>`, and `<channel-number>`):

```bash
# 1. Verify HTSP port 9982 is reachable
nc -zv <tvheadend-host> 9982
# Expected: Connection to <tvheadend-host> 9982 port [tcp/*] succeeded!

# 2. Verify HTTP Digest/Basic auth and server info
curl -s --anyauth -u "<username>:<password>" \
  "http://<tvheadend-host>:9981/api/serverinfo"
# Expected: JSON containing "sw_version" and "htspversion"

# 3. Verify live MPEG-TS stream with profile=pass (uses GET for 3s, not HEAD)
curl -s --anyauth -u "<username>:<password>" \
  -o /dev/null --max-time 3 -w '%{http_code} %{content_type}\n' \
  "http://<tvheadend-host>:9981/stream/channelnumber/<channel-number>?profile=pass"
# Expected: 200 video/mp2t
```

*(Why `--anyauth`? Tvheadend 4.3 defaults to HTTP Digest MD5 authentication and rejects plain HTTP Basic auth unless explicitly enabled on the server.)*

---

## 6. Troubleshooting

| Symptom | Likely Cause | Fix |
| :--- | :--- | :--- |
| **Connection test fails / times out** | Port `9982` blocked, wrong IP, or client IP outside `Allowed networks` | Open `9982/tcp` and `9981/tcp`; check the user's `Allowed networks` CIDR in Tvheadend. |
| **Authentication failed (`noaccess`)** | Username/password mismatch between **Access Entries** and **Passwords**, or user disabled | Ensure matching enabled entries exist in both **Access Entries** and **Passwords**. |
| **Live stream fails immediately with `Tuner busy` or empty stream** | Another recording holds the tuner on a different mux, or `pass` profile isn't allowed for the user | Confirm **`pass`** is enabled globally and checked in the user's **Streaming profile** list; check active recordings in Tvheadend Status. |
| **Channel picons fail to load** | User lacks HTTP `Streaming` rights or `imagecache` requires auth with mismatched HTTP auth mode | Ensure user has `Streaming: Basic` + `HTSP` enabled; MuxGrid supports both HTSP VFS and HTTP Digest/Basic fallback. |
| **Cannot pause/record or chase-play growing DVR** | User lacks `Video recorder` `HTSP` / `All (rw)` permissions | Grant `Video recorder: Basic`, `HTSP`, and `All (rw)` to the MuxGrid access entry. |
