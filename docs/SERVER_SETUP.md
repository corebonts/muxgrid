# Tvheadend Server Configuration Guide

This guide documents the optimal Tvheadend (`4.3+`) server settings for this Android Phone + TV client, tailored specifically to the `tv-server` (`192.168.1.5`) homelab setup while remaining 100% compatible with existing clients (such as Jellyfin).

---

## 1. Summary of Recommended Server Settings

| Area | Recommended Setting | Why |
| :--- | :--- | :--- |
| **Live TV Stream Profile** | **`pass`** (MPEG-TS Pass-thru) — enabled & default HTTP profile | Raw broadcast pass-through, fastest zapping (<1s), zero server CPU/VAAPI load, preserves `1080i` H.264, `MP2`/`AC3`/`AAC`, and `DVBSUB`. Also required by Jellyfin. |
| **Control / EPG / DVR Protocol** | **`htsp`** (port `9982/tcp`) — enabled | Real-time push sync, SHA-1 auth, `getTicket`, and `fileOpen`/`fileRead`/`fileStat` for growing recordings. |
| **DVR Recording Profile** | **`matroska`** (built-in `.mkv`, `dvbsub_reorder: true`) | Clean seeking for both completed and in-progress recordings via `libmpv` + HTSP `fileOpen`, preserves all audio & DVB subtitle tracks, and keeps Jellyfin's `TV Recordings` library working cleanly. |
| **Client User / ACL** | Dedicated least-privilege user (e.g., `androidtv`) | Separates permissions from `admin`, `jellyfinTV`, and wildcard LAN rules; disables Web UI/Admin access. |
| **Server Timeshift** | **`ondemand: true`** (or disabled if Kodi is retired) | Because this client buffers live pause/rewind locally in `libmpv`, continuous server timeshift (`ondemand: false`) only causes unnecessary HDD writes on `/st2000`. |
| **Remote Access** | **WireGuard / Tailscale VPN** (never expose `9981`/`9982` publicly) | HTSP (`9982`) is a raw binary TCP protocol that does not sit behind standard HTTPS reverse proxies; VPN provides end-to-end encryption with zero extra setup. |

---

## 2. Step-by-Step Tvheadend Web UI Setup (`http://192.168.1.5:9981`)

### Step A — Verify Stream Profiles
Go to **Configuration → Stream → Stream Profiles**:
1. Confirm **`pass`** (`MPEG-TS Pass-thru`) is **Enabled** and set as **Default** (already configured during the Jellyfin fix).
2. Confirm **`htsp`** (`HTSP Default Stream Settings`) is **Enabled**.
3. Confirm **`matroska`** (`Matroska (mkv) / built-in`) is **Enabled** (used by DVR, not HTTP default).

### Step B — Verify DVR Profile
Go to **Configuration → Recording → Digital Video Recorder Profiles → (Default profile)**:
1. **Stream profile:** Keep **`matroska`** (`.mkv`).
2. **Make subdirectories per title / pathname format:** Ensure clean filenames without broken separators (see parent repo `IMPROVEMENTS.md` for optional cleanup — does not break the client either way).
3. **Skip commercials / Scene markers:** Optional; harmless to keep enabled.

### Step C — Create a Dedicated Client User & Access Entry
Go to **Configuration → Users → Access Entries → Add** (and create matching password under **Configuration → Users → Passwords**):

- **Enabled:** Checked
- **Username:** `androidclient` *(or your preferred name)*
- **Allowed networks:**
  - LAN only: `192.168.1.0/24`
  - LAN + VPN (e.g. Tailscale/WireGuard): add your VPN subnet or `0.0.0.0/0,::/0` (safe when only accessible via LAN/VPN and protected by password)
- **Streaming:**
  - `Basic` ✅
  - `Advanced` ✅
  - `HTSP` ✅
- **Streaming profile:** Select **`pass`** and **`htsp`** *(do NOT restrict to `matroska` only!)*
- **Video recorder:**
  - `Basic` ✅
  - `HTSP` ✅
  - `All (rw)` ✅ *(allows scheduling, canceling, and deleting recordings from the app; omit `All (rw)` if you want read-only viewing on a shared TV)*
  - `Failed` ✅ *(optional, lets the app display failed recordings cleanly if desired)*
- **Web interface:** Unchecked ❌
- **Admin:** Unchecked ❌

### Step D — Optimize Server Timeshift (Optional, Recommended for HDD Longevity)
Go to **Configuration → Recording → Timeshift**:
- If **no other client** relies on continuous server-side HTSP timeshift (e.g., if replacing Kodi):
  - Set **On-Demand:** **Checked** (`ondemand: true`), or uncheck **Enabled** entirely.
  - This stops Tvheadend from continuously writing live buffers to `/st2000/media/tvheadend/timeshift` whenever someone watches TV.
- If you still use Kodi (`pvr.hts`) occasionally, keep **Enabled** with **On-Demand** checked so disk writes only happen when Kodi actually pauses live TV.

### Step E — Check Docker / Firewall Ports
Confirm both ports are reachable on the LAN (and over VPN if remote):
- **`9981/tcp`** — HTTP streaming (`/stream/channelid/...`, `/dvrfile/...`, `/imagecache/...`)
- **`9982/tcp`** — HTSP control, metadata push, `getTicket`, and `fileOpen`/`fileRead`

*(Already published in `docker-compose.yml` on `tv-server`.)*

---

## 3. Single-Tuner Considerations (`tv-server` IT9135 DVB-T)

`tv-server` has a **single USB DVB-T tuner** (`Afatech AF9033`) covering three active muxes:
- **514 MHz:** `M1 HD`, `Duna HD`, `M4 Sport HD`, `DunaW/M4Sport+`, and the 4 national radio stations (`101–104`).
- **554 MHz:** `M2 HD`, `M5 HD`, `RTL`, `Spektrum Home +`, `TV2`, `MAX4`.
- **570 MHz:** `Szeged TV`.

**What this means in practice:**
- Any number of clients or recordings on the **same mux** work simultaneously (e.g., recording `M4 Sport HD` while listening to `Kossuth Radio` or watching `Duna HD` works with zero conflict).
- If a scheduled recording is active on 514 MHz and you try to tune `RTL` (554 MHz), Tvheadend prioritizes the DVR recording and denies the live stream (standard behavior; the client detects this and shows a clean notice).
- Keep **DVR streaming priority** higher than live client streaming weight (Tvheadend's default) so live zapping never interrupts an active F1/F2/F3 recording.

---

## 4. Quick Server Readiness Verification

Run these checks from any LAN machine before testing the Android app:

```bash
# 1. Verify HTTP stream returns MPEG-TS (video/mp2t) with profile=pass
curl -sI -u androidclient:<password> \
  "http://192.168.1.5:9981/stream/channelnumber/4?profile=pass" | grep -iE "HTTP/|Content-Type"
# Expected: HTTP/1.1 200 OK  and  Content-Type: video/mp2t

# 2. Verify HTSP port 9982 is reachable
nc -zv 192.168.1.5 9982
# Expected: Connection to 192.168.1.5 9982 port [tcp/*] succeeded!

# 3. Verify Tvheadend HTTP API server info (confirms auth works)
curl -s -u androidclient:<password> "http://192.168.1.5:9981/api/serverinfo"
```
