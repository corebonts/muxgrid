# Design & UX Specification

MuxGrid uses a low-glare **Broadcast Monitor** dark visual language (`Carbon & Signal Amber`) tuned for OLED phones, tablets, and 10-foot living-room TVs.

## 1. Visual Philosophy — Broadcast Monitor

- **Content-forward, zero noise:** No busy chrome, heavy drop shadows, or decorative clutter. Video and channel artwork remain the focus.
- **Carbon surface hierarchy (`MuxGridColors.kt`):**
  - Deep carbon canvas: `#09090B` (`CanvasTv`) on Android TV, `#0C0C0E` (`CanvasPhone`) on mobile/tablet.
  - Elevated cards: `#141417` (`CardSurface`), `#1D1D22` (`CardSurfaceElevated`) on focus/hover, `#111114` (`CardSurfaceAlt`) for secondary rows, and `1dp` / `0.5dp` subtle hairline borders (`rgba(255,255,255,0.09)` / `0.07`).
  - **Surface Blur vs. Video Scrims (Platform Rule):**
    - Over **non-video Compose chrome** (floating bottom nav bar, mini-player, bottom sheets): use **Haze (`2.1.0`)** backdrop blur on mobile/tablet, falling back cleanly to translucent carbon scrims (`#D6121216` / `#DB09090B`) on TV or over video.
    - Over **live/recorded video (`SurfaceView`)**: Compose/Haze/`RenderEffect` cannot sample a `SurfaceView` plane. Always use **translucent dark gradient scrims + hairline pill surfaces** (`ScrimSurface` / `ScrimLight`) for player HUDs and OSDs.
- **Signal Amber & Broadcast Status Palette:**
  - Primary interactive accent: Signal Amber `#E5A00D` (`Accent`), highlighted variant `#F5B82E` (`AccentSoft`), dark `#09090B` (`OnAccent`) foreground on primary actions.
  - TV D-pad focus border: crisp `#F4F4F5` (`FocusBorder`, `2dp`).
  - Live / Recording indicator: `#E53935` (`CoralLive`).
  - Success / Watched indicator: `#3E9B63` (`EmeraldOk`).
  - Progress bars: crisp `#D4D4D8` (`ProgressFill`) on `TrackBackground` (`rgba(255,255,255,0.17)`), with Signal Amber reserved for live/interactive scrub cues.
- **Typography & Radii (`MuxGridTheme.kt` & `MuxGridTokens.kt`):**
  - System sans-serif with tight display tracking (`-0.5sp` screen titles, `-0.35sp` section titles) and **tabular numerals (`fontFeatureSettings = "tnum"`)** on all clocks, channel numbers, durations, and seekbar readouts.
  - Compact crisp corners: `3dp` badges, `5dp` controls/buttons, `8dp` rows/cards, `12dp` dialogs/sheets.
- **Motion:**
  - Snappy transitions (~180–250ms) and subtle `2dp` focus borders + mild scale on TV cards without jumpy layouts.
- **Dialogs, Sheets & Confirmations:**
  - Modal sheets and action/confirmation dialogs use `CardSurfaceElevated` (`#1D1D22`), `12dp` corners (`MuxGridShapes.Dialog` / `MuxGridShapes.SheetTop`), and `1dp` `HairlineBorder` (or `CoralLive` at 55% alpha for playback errors); settings/options panels (**Server Setup**, **Display Options**) use `CardSurface` (`#141417`) with `CardSurfaceElevated` (`#1D1D22`) inner controls and `0.5dp` `HairlineBorder`.
  - Player overlays (**Display Options**, **Playback Error**, **Quick Zap**) render in-window over `OverlayBackdrop` (`#99000000`) rather than separate `Dialog` windows over `SurfaceView`, trap D-pad focus inside modal panels (**Display Options** focuses the first content option on open), and always intercept `Back` to close the overlay first.
  - Disruptive or irreversible DVR actions (**Stop & Save**, **Abort Recording**, **Cancel Timer**, **Delete Recording**) always require confirmation on both Mobile and TV, with TV destructive confirmations defaulting initial focus to **Cancel**.

---

## 2. Information Architecture (3 Primary Tabs + Settings)

Navigation stays minimal and consistent across both form factors:

1. **Live** — Channel cards/rows with Now/Next program, live progress bar, and one-tap/click playback.
2. **Guide** — Electronic Program Guide (EPG) for browsing upcoming broadcasts and scheduling recordings.
3. **Recordings** — Active (`Recording Now`), Completed (grouped cleanly by series/title), and Scheduled timers.
4. **Settings** — Compact sheet/dialog (Server connection, Decoder/Deinterlace preset, Timeshift cache limit, Background Play & PiP toggles).

---

## 3. Phone & Tablet Experience (`:feature:mobile`)

### Navigation Chrome
- **Portrait Phone:** Floating translucent frosted-glass bottom bar (`Live`, `Guide`, `Recordings`) with Settings action in the top-right header.
- **Landscape / Tablet:** Compact left translucent navigation rail + adaptive multi-column grids (`2–3` columns).
- **Persistent Mini-Player:** When Radio or Live TV is playing in background audio mode, docks cleanly above the bottom nav bar (Picon, Channel + Show title, Play/Pause, Close). Tapping expands back to the player.

### Screen Layouts
- **Live Screen:**
  - Top horizontal filter pills: `All`, `TV`, `Radio`, `Favorites`, plus Tvheadend server tags.
  - Channel rows/cards: picon badge, tabular channel number, channel name, **Now** title + thin progress bar + remaining minutes, subtle **Next** preview.
  - Long-press a channel to Favorite, open its Guide, or jump straight into playback.
- **Guide (EPG) Screen:**
  - Vertical channel agenda with instant search and day/channel filtering.
  - Tap any program to open a translucent bottom sheet with full description, time, channel, and **Watch Live** / **Record** / **Cancel Recording** actions.
- **Recordings Screen:**
  - Stacked sections for **Recording Now** (coral badge, live elapsed duration, **Watch from Start** / **Watch Live** / **Stop** / **Cancel**), **Completed** (grouped by series/title with episode count, resume progress bar, watched checkmark, Play/Resume/Delete), and **Scheduled** (Cancel).

### Mobile Fullscreen Player
- Edge-to-edge immersive video.
- **Tap** video: toggles top & bottom dark scrim HUD (auto-hides while playing).
- **Top bar:** Back button, Channel picon + Title + Current Show, **PiP**, **Background Audio** (`vid=no`), and **Display Options** (Audio / Subtitles / Aspect / Decoder preset).
- **Bottom bar:**
  - Smooth scrub bar showing buffered timeshift range (Live) or growing/total duration (Recordings).
  - **`-MM:SS · Back to Live`** pill when behind the live edge.
  - Centered transport controls (`-10s`, `Play/Pause`, `+30s`) plus quick-zap **Channels** drawer and **Display Options** shortcut.
- **Gestures:**
  - Double-tap left (`-10s`) / double-tap right (`+30s`) with visual feedback.
  - Vertical drag on left edge = brightness; right edge = volume.
- **Background & PiP:**
  - Leaving the app while playing video enters **PiP** (if enabled) or transitions smoothly to **Background Audio** (always active for Radio; configurable for Live TV).

---

## 4. Android TV Experience (`:feature:tv`)

Designed strictly for **10-foot viewing** and a standard TV remote (`D-pad`, `OK/Select`, `Back`, `Ch+/Ch-`, `Play/Pause`).

### Navigation Chrome
- Collapsible left **Navigation Rail** (`Live`, `Guide`, `Recordings`, `Settings`) with focus restoration and clean left/right traversal into screen content without focus traps.

### Screen Layouts
- **Live Screen:** Multi-column grid of large legible channel cards + focusable filter pills above the grid.
- **Guide (EPG) Screen:** Horizontal timeline grid (sticky channel column, time slots horizontally, red vertical "Now" hairline) paired with a dynamic top preview header showing title, air time, and synopsis of the currently focused program.
- **Recordings Screen:** Horizontal shelves (`Recording Now`, `Continue Watching`, `All Recordings`, `Scheduled`).

### TV Fullscreen Player (Zero-Friction Remote Controls)
- **Channel Zapping (`Ch+`/`Ch-` or `D-pad Up` while HUD hidden):** Stops the previous stream cleanly, tunes the next/previous channel, and displays a minimal bottom-left OSD pill fading after 3 seconds.
- **HUD Open (`OK`/`Select` or `D-pad Down` while HUD hidden):** Reveals the translucent dark scrim bottom HUD with focusable seek bar, `Play/Pause`, `Back to Live` pill, **Display Options**, and horizontal **Quick Channel Strip**.
- **Progressive Seeking (`D-pad Left`/`Right` while HUD hidden):** Steps smoothly (`10s → 30s → 1m → 5m`) with instant preview timestamp and commit on short settle.
- **Back key:** Dismisses open panels or the HUD first; otherwise stops playback cleanly and returns to the previous screen.
- **Single-Tuner Notice:** Auto-dismissing top-right toast naming the blocking active recording rather than an infinite loading spinner.
