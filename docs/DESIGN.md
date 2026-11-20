# Design & UX Specification

> **Interactive Prototype & App Icon:** Open [muxgrid-prototype.html](./muxgrid-prototype.html) in a browser to preview the **MuxGrid** UI across both **Android TV (10-ft)** (with keyboard D-pad navigation) and **Phone (Portrait)** form factors, including live timeshift scrubbing, EPG recording scheduling, DVB subtitles, PiP, background audio mini-player, and single-tuner toast feedback. The VectorDrawable-ready app icon source lives at [assets/muxgrid-icon.svg](./assets/muxgrid-icon.svg).

## 1. Visual Philosophy — Calm, Modern, iOS/iPadOS-Inspired

Think **Infuse**, **Channels**, and **Apple TV**: dark-first, content-forward, airy spacing, zero visual noise, and instant physical responsiveness.

### Core Principles
- **Video and artwork are the hero:** No busy borders, heavy drop shadows, garish gradients, or cluttered status text.
- **Dark-first surface hierarchy:**
  - Deep obsidian canvas (`#0A0B0F` on TV / `#0C0D12` on phone).
  - Elevated cards (`#161822` at 100% opaque, or translucent glass `#1C1F2B` @ 65–75% alpha with subtle `1dp` hairline stroke `white @ 10% alpha`).
  - **Surface Blur vs. Video Scrims (Platform Rule):**
    - Over **non-video Compose content** (floating bottom nav pill, mini-player, bottom sheets, drawers over lists): use **Haze (`2.1.0`)** backdrop blur on capable phones/tablets, gracefully falling back to clean translucent dark scrims on low-RAM TV hardware. *(Note: Compose `1.13+` native `Modifier.blur(BlurRadiusSpec...)` is a self-layer blur only, not a backdrop blur.)*
    - Over **live/recorded video (`SurfaceView`)**: Compose/Haze/`RenderEffect` cannot sample a `SurfaceView` plane (and switching to `TextureView` hurts 1080i50 smoothness on low-end TVs). Always use **crisp translucent dark gradient scrims + `1dp` hairline pill surfaces** (non-blurred) as the default for player HUDs and OSDs.
- **Accent palette (restraint is key):**
  - Primary interactive accent: crisp cool blue or icy white-on-glass (`#3B82F6` / `#60A5FA` on mobile; crisp white focus pill `#F8FAFC` with subtle glow on TV).
  - Live / Recording status dot: warm Apple-style coral red (`#FF453A`).
  - Progress bars: thin `3dp` rounded track (`white @ 18%` background, crisp accent or white fill).
- **Typography:**
  - Clean sans-serif (`Inter` or system variable sans-serif), generous line height, tight display headings (`-0.02em` tracking).
  - **Tabular numerals (`fontFeatureSettings = "tnum"`)** on all clocks, channel numbers, durations, and seekbar readouts so numbers never jitter while ticking.
- **Motion:**
  - Short, spring-driven transitions (snappy ~180–250ms, never sluggish).
  - Subtle focus scale (`1.04x`–`1.06x`) on TV cards with crisp focus ring — never jumpy layouts.

---

## 2. Information Architecture (Only 3 Primary Tabs + Settings)

Keep top-level navigation ruthlessly simple across both form factors:

1. **Live** — Channel cards with Now/Next program, progress bar, and instant one-tap playback.
2. **Guide** — Electronic Program Guide (EPG) for browsing upcoming shows and one-tap recording.
3. **Recordings** — Active (`Recording Now`), Completed (grouped cleanly by series/title), and Scheduled timers.
4. **Settings** — Compact sheet/screen (Server connection, Decoder/Deinterlace preset, Timeshift cache limit, Background Play toggle, App info).

---

## 3. Phone & Tablet Experience (`:feature:mobile`)

### Navigation Chrome
- **Portrait Phone:** Floating translucent frosted-glass bottom pill bar (`Live`, `Guide`, `Recordings`) with Settings icon in the top-right header.
- **Landscape / Tablet:** Compact left translucent navigation rail + adaptive multi-column grids (`2–4` columns).
- **Persistent Mini-Player:** When Radio or Live TV is playing in background audio mode, dock a sleek frosted-glass mini-player bar just above the bottom nav bar (Picon, Channel + Show title, Play/Pause, Stop/Close). Tapping expands back to the player.

### Screen Layouts
- **Live Screen:**
  - Top horizontal filter pills: `All`, `TV`, `Radio`, `Favorites` (plus Tvheadend tags if present).
  - Clean channel rows/cards: crisp channel picon on a neutral rounded badge, channel number, channel name, **Now** title + thin progress bar + remaining minutes, subtle **Next** preview line.
  - Long-press a channel to Favorite / View EPG / Record current show.
- **Guide (EPG) Screen:**
  - Portrait: Channel strip or date/time selector + clean vertical agenda per channel (much easier to read on a phone than a tiny cramped grid!), plus an optional horizontal timeline toggle for tablets/landscape.
  - Tap any program -> sleek frosted-glass bottom sheet with full description, time, channel, and prominent **Watch Live** (if airing now) or **Record / Cancel Recording** button.
- **Recordings Screen:**
  - Segmented pill switcher or clean stacked sections:
    - **Recording Now** (pinned at top when active, pulsing coral dot, live elapsed duration, one-tap **Watch from Start** or **Watch Live**).
    - **Completed** (grouped by show/series title with episode count badge, newest first, subtle resume progress bar & watched checkmark).
    - **Scheduled** (upcoming timers with channel picon, time, and one-tap Cancel).

### Mobile Fullscreen Player
- Edge-to-edge video (`SYSTEM_UI_FLAG` / `WindowInsetsController` immersive bars).
- **Tap** video: toggles translucent dark scrim top & bottom HUD (auto-hides after 3.5s).
- **Top bar:** Back button, Channel picon + Title + Current Show, **PiP** button, **Background Audio** button (or Radio mode toggle), **Tracks** button (Audio / DVB Subtitles / Aspect).
- **Bottom bar:**
  - Smooth scrub bar showing buffered timeshift range (for Live) or growing/total duration (for Recordings).
  - When behind live edge: show a crisp **`-02:14` · `Back to Live`** pill button.
  - Clean transport controls: `-10s`, `Play/Pause`, `+30s`, and **Channels** button (slides open a translucent quick-zap channel drawer without stopping video).
- **Gestures:**
  - Double-tap left (`-10s`) / double-tap right (`+30s`) with subtle translucent ripple feedback.
  - Vertical drag on left third = brightness; right third = volume (minimal floating pill indicator).
- **Background & PiP:**
  - Pressing Home while video plays enters **PiP** (if enabled) or transitions seamlessly to **Background Audio** (always default for Radio channels; configurable for Live TV).

---

## 4. Android TV Experience (`:feature:tv` — TCL C635)

Designed strictly for **10-foot viewing** and a standard TV remote (`D-pad`, `OK/Select`, `Back`, `Ch+/Ch-`, `Play/Pause`).

### Navigation Chrome
- Collapsible left **Navigation Rail** (icons when collapsed, icon + label when focused) with `Live`, `Guide`, `Recordings`, and `Settings`.
- Focus moving right from the rail enters content seamlessly; pressing `Left` on the leftmost column returns smoothly to the rail (no dead ends or focus traps!).

### Screen Layouts
- **Live Screen:**
  - Clean 2- or 3-column grid of large, legible glass channel cards (Picon, Channel #, Name, Now title, thin progress bar, Next preview).
  - Top filter pills (`All`, `TV`, `Radio`, `Favorites`) cleanly focusable above the grid.
- **Guide (EPG) Screen:**
  - Classic, airy **horizontal timeline grid** (Channels on left sticky column, 30-min/1-hour time slots horizontally, red vertical "Now" hairline).
  - Top preview header dynamically shows the full title, time, and synopsis of the currently focused program (no extra click needed just to read descriptions!).
  - Press `OK` on a live show -> Watch; press `OK` on a future show -> concise action modal (`Record`, `Cancel Recording`).
- **Recordings Screen:**
  - Clean horizontal shelves (Apple TV style):
    1. **Recording Now** (only visible when active — highlighted with coral live badge).
    2. **Continue Watching** (in-progress playback with resume bars).
    3. **All Recordings** (grouped by series/title or newest-first grid).
    4. **Scheduled Timers**.

### TV Fullscreen Player (Zero-Friction Remote Controls)
- **Channel Zapping (`Ch+`/`Ch-` or `D-pad Up`/`Down` while HUD hidden):**
  - Immediately stops previous stream and tunes next/previous channel.
  - Displays a sleek minimal **bottom-left translucent scrim OSD pill** (Picon, Channel #, Channel Name, Now/Next + progress bar) that fades out after 3 seconds.
- **Smooth Seeking (`D-pad Left`/`Right` while HUD hidden):**
  - Opens a minimal bottom timeline bar and steps smoothly through the timeshift buffer or recording.
  - **Progressive step escalation:** single press = `±10s`, rapid presses / hold escalate smoothly (`10s → 30s → 1m → 5m`) with instant preview timestamp and commit on short settle — avoids Kodi's clunky seek behavior!
- **Full Overlay (`OK`/`Select` while HUD hidden):**
  - Opens translucent dark scrim bottom HUD: Play/Pause, `Back to Live` pill (when timeshifted), Audio Track selector, DVB Subtitles toggle, and a horizontal **Quick Channel Strip** underneath so the user can browse and zap without leaving video.
- **Play/Pause remote key:** Toggles pause directly and reveals minimal timeline status.
- **Back remote key:** Dismisses open overlay/drawer first; if no overlay is visible, stops playback cleanly and returns to the previous screen.
- **Single-Tuner Notice:** If tuning fails because an active recording holds the sole DVB-T tuner on another mux, show a crisp, auto-dismissing top-right glass toast naming the active recording rather than an infinite loading spinner.
