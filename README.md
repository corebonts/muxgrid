# MuxGrid — Tvheadend Android Client (Phone + Android TV)

<img src="./docs/assets/muxgrid-icon.svg" alt="MuxGrid icon" width="72" />

A lightweight, native Android & Android TV client for **Tvheadend** (`4.3+`), designed for instant channel zapping, live pause/rewind (timeshift), smooth playback of completed and in-progress recordings, background audio play on mobile, and a calm, iOS/iPadOS-inspired dark interface.

Built as a **single unified APK** targeting both modern Android phones/tablets and Android TV / Google TV devices (such as the TCL C635).

## Core Goals

- **Snappy & Lightweight:** Fast cold start, sub-second channel zapping on LAN, tiny memory footprint suitable for 2 GB RAM Android TVs.
- **Broadcast-Native Playback:** Clean playback of `1080i` interlaced H.264, `MP2`/`AC3`/`AAC` audio, DVB bitmap subtitles (`DVBSUB`), and radio channels with zero server-side transcoding.
- **Complete Live & DVR Experience:** Live TV pause/rewind, completed and **in-progress (growing)** recordings with accurate seeking, EPG browsing, and one-tap recording scheduling.
- **Phone-First Comforts:** Background audio playback (radio & live TV) with system media controls, Picture-in-Picture (PiP), and fluid touch gestures.
- **TV-First Ergonomics:** Pure Compose for TV D-pad navigation, instant Channel Up/Down zapping, progressive seek steps, and zero focus traps.
- **Modern Aesthetic:** Dark-first, content-forward canvas with translucent frosted-glass overlays, tabular typography, and minimal navigation clutter (inspired by Infuse, Channels, and Apple TV).

## Documentation Map

| Document | Purpose |
| :--- | :--- |
| [PLAN.md](./PLAN.md) | Master implementation plan: architecture, tech stack, module boundaries, phased milestones (M0–M6), and dead ends to avoid |
| [docs/DESIGN.md](./docs/DESIGN.md) | Visual design system, Phone/Tablet & Android TV layouts, player HUD, gestures, and D-pad interaction rules |
| [docs/muxgrid-prototype.html](./docs/muxgrid-prototype.html) | Self-contained interactive HTML prototype of **MuxGrid** (Android TV 10-ft & Phone portrait modes, animated player HUD, EPG, timeshift, PiP, and mini-player) — open directly in any browser |
| [docs/assets/muxgrid-icon.svg](./docs/assets/muxgrid-icon.svg) | **MuxGrid** VectorDrawable-ready SVG app icon source (`512×512`) |
| [docs/PROTOCOL.md](./docs/PROTOCOL.md) | Tvheadend HTSP wire format, RPC subset, HTTP streaming URLs, and the growing-recording loopback bridge contract |
| [docs/SERVER_SETUP.md](./docs/SERVER_SETUP.md) | Recommended Tvheadend server configuration (profiles, least-privilege ACL user, single-tuner & timeshift notes, verification steps) |

## Architecture at a Glance

```text
┌─────────────────────────────────────────────────────────────────────────┐
│                    Unified Android APK (Phone + TV)                     │
│                                                                         │
│  :feature:mobile (Compose M3)          :feature:tv (Compose for TV)     │
│                └──────────────┬───────────────┘                         │
│                               ▼                                         │
│      :core:data (Reactive StateFlow Store + DataStore Preferences)      │
│              ┌────────────────┴─────────────────┐                       │
│              ▼                                  ▼                       │
│  :core:htsp (Pure Kotlin TCP)        :core:player (PlaybackEngine)      │
│  • Channels, EPG, DVR push sync      • libmpv (1080i, MP2, DVBSUB)      │
│  • Auth tickets (getTicket)          • Live pause/rewind ring buffer    │
│  • fileOpen/Read/Stat (growing DVR)  • Media3 MediaSessionService + PiP │
└──────────────┬──────────────────────────────────┬───────────────────────┘
               │ HTSP (9982/tcp)                  │ HTTP (9981/tcp, pass / dvrfile)
               ▼                                  ▼
        Tvheadend 4.3+ Server (e.g. tv-server / 192.168.1.5)
```

## Licensing Note

Bundling prebuilt `libmpv` AAR binaries (`FFmpeg` + `libmpv`) subjects distributed APKs to **GPL-3.0-or-later** terms. Keep the playback engine behind a clean `PlaybackEngine` interface (`:core:player`) so the rest of the codebase stays modular and decoupled.
