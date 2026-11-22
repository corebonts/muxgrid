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
  - TV D-pad focus border: crisp `#F4F4F5` (`FocusBorder`, `2dp`), paired with contrasting `OnFocusBorder` (`#09090B` dark / `#F4F4F5` light) whenever `FocusBorder` acts as a focused container background, and `SeekThumb` (`#FFFFFF` dark / `#18181B` light) for interactive seekbar thumbs.
  - Live / Recording indicator: `#E53935` (`CoralLive`).
  - Success / Watched indicator: `#3E9B63` (`EmeraldOk`).
  - Progress bars: crisp `#D4D4D8` (`ProgressFill`) on `TrackBackground` (`rgba(255,255,255,0.17)`), with Signal Amber reserved for live/interactive scrub cues.
- **Theme Modes (`Auto` / `Light` / `Dark`):**
  - Users can switch between `Auto` (system appearance), `Light` (Daylight Monitor palette: `#F4F4F7` / `#F2F2F5` canvas, `#FFFFFF` cards, `#18181B` focus border, Signal Amber accents), and `Dark` (Carbon & Signal Amber) under **Settings → General → Theme** (defaults: `Auto` on Mobile, `Dark` on TV).
  - Fullscreen video/audio players (`MobilePlayerScreen`, `TvPlayerScreen`) and their over-video HUDs always remain locked to the dark palette to prevent glare and preserve contrast over video.
- **Typography, Radii & Tokens (`MuxGridTheme.kt` & `MuxGridTokens.kt`):**
  - System sans-serif with tight display tracking (`-0.5sp` screen titles, `-0.35sp` section titles) and **tabular numerals (`fontFeatureSettings = "tnum"`)** on all clocks, channel numbers, durations, and seekbar readouts.
  - Compact crisp corners (`MuxGridRadii` / `MuxGridShapes`): `2dp` seek tracks, `3dp` badges, `5dp` controls/buttons, `8dp` rows/cards, `12dp` dialogs/sheets, with centralized border widths (`MuxGridBorders`), animation/OSD durations (`MuxGridDurations`), and surface/text opacities (`MuxGridAlphas`).
- **Motion:**
  - Snappy transitions (~180–250ms) and subtle `2dp` focus borders + mild scale on TV cards without jumpy layouts.
- **Dialogs, Sheets & Confirmations:**
  - Modal sheets and action/confirmation dialogs use `CardSurfaceElevated` (`#1D1D22`), `12dp` corners (`MuxGridShapes.Dialog` / `MuxGridShapes.SheetTop`), and `1dp` `HairlineBorder` (or `CoralLive` at 55% alpha for playback errors); settings/options panels (**Server Setup**, **Display Options**) use `CardSurface` (`#141417`) with `CardSurfaceElevated` (`#1D1D22`) inner controls and `0.5dp` `HairlineBorder`.
  - Player overlays (**Display Options**, **Playback Info**, **Playback Error**, **Quick Zap**) render in-window over `OverlayBackdrop` (`#99000000`) rather than separate `Dialog` windows over `SurfaceView`, trap D-pad focus inside modal panels (**Display Options** focuses the first content option on open and uses `selectedAudioId`/`selectedSubtitleId` as the single source of truth), and always intercept `Back` to close the overlay first.
  - **Server Setup** validates hostnames, IPv4, and IPv6 literals (rejecting schemes, paths, spaces, and port suffixes) and preserves unsaved form input across configuration changes.
  - Disruptive or irreversible DVR actions (**Stop & Save**, **Abort Recording**, **Cancel Timer**, **Delete Recording**) always require confirmation on both Mobile and TV, with TV destructive confirmations defaulting initial focus to **Cancel**.

---

## 2. Information Architecture (4 Primary Tabs)

Navigation stays minimal and consistent across both form factors:

1. **Live** — Channel cards/rows with Now/Next program, live progress bar, and one-tap/click playback.
2. **Guide** — Electronic Program Guide (EPG) for browsing upcoming broadcasts and scheduling recordings.
3. **Recordings** — Active (`Recording Now`), Completed (grouped cleanly by series/title), and Scheduled timers.
4. **Settings** — Responsive full-width / two-column layout with Server card (connection status, error details, quick Retry & Edit), General (**Start Screen**, **Theme** `Auto` / `Light` / `Dark`), Decoder/Deinterlace preset, Timeshift cache limit, unified mobile **Background Playback** selector (`Off` / `Audio` / `PiP`), and **App Info** (version, build ABI/Git SHA, **Licenses & Notices** with offline GPLv3 / Apache-2.0 texts, copyable debug report).

---

## 3. Phone & Tablet Experience (`:feature:mobile`)

### Navigation Chrome
- **Portrait Phone:** Docked frosted-glass bottom bar (`Live`, `Guide`, `Recordings`, `Settings`) with no redundant per-tab top headers so content starts right below the status bar.
- **Landscape / Tablet:** Compact left translucent navigation rail with icon + label items and adaptive multi-column grids (`2–3` columns).
- **Unified Top Bar (`48dp`):** Live filter tabs, Guide day tabs / expanded search bar, and Recordings filter tabs share a consistent `48dp` height (`MobileTopBarHeight`) so toggling search or switching tabs never shifts the layout.
- **Server Status Indicator:** Non-connected states (`Connecting`/`Syncing` amber, `Error` coral, `Disconnected` muted) surface as a compact status dot on the **Settings** tab icon rather than a persistent top banner; opening **Settings** reveals the full status/error message and quick **Retry** / **Edit** actions on the server card.
- **Persistent Mini-Player:** When Radio or Live TV is playing in background audio mode, docks cleanly above the bottom nav bar (or floats centered above the bottom edge in rail mode) with Picon, Channel + Show title, Play/Pause, and Stop. Tapping expands back to the player.

### Screen Layouts
- **Live Screen:**
  - Top horizontal underline filter tabs (`All`, `TV`, `Radio`, `Favorites`, plus Tvheadend server tags).
  - Channel rows/cards: picon badge, tabular channel number, channel name, **Now** title + thin progress bar + remaining minutes, subtle **Next** preview.
  - Long-press a channel to Favorite, open its Guide, or jump straight into playback.
- **Guide (EPG) Screen:**
  - **Portrait Phone:** Vertical per-channel agenda with day underline tabs paired with an icon-only **Search** button on the right (tapping expands a full-width search bar in place of the day chooser) and a compact borderless channel strip below (hidden while searching).
  - **Landscape / Tablet (`>= 600dp`):** Free 2D-scrolling horizontal multi-channel timeline grid with sticky channel column (tap to tune), synchronized half-hour time ruler, live coral "Now" hairline, **Now** quick jump, keyboard/gamepad Left/Right focus auto-follow, and the same top-bar day tabs and expandable program search.
  - Tap any program in either layout to open a modal bottom sheet with full description, time, channel, and **Watch Live** / **Record** / **Cancel Recording** actions.
- **Recordings Screen:**
  - Underline filter tabs (`All`, `Completed`, `Scheduled`) and stacked sections for **Recording Now** (coral badge, live elapsed duration, **Chase Play** / **Stop** / overflow sheet), **Completed** (grouped by series/title with episode count, resume progress bar, watched badge, Play/Resume/Delete), and **Scheduled** (Cancel).

### Mobile Fullscreen Player
- Edge-to-edge immersive video.
- **Tap** video: toggles top & bottom dark scrim HUD (auto-hides while playing).
- **Top bar:** Back button, Channel picon + Title + Current Show, minimize/background action (**PiP** or **Background Audio** depending on mode), **Playback Info**, and **Display Options** (Audio / Subtitles / Aspect / Decoder preset).
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

### Navigation Chrome & D-Pad Focus Management
- Collapsible left **Navigation Rail** (`Live`, `Guide`, `Recordings`, `Settings`) overlaying the left edge of the fixed-width content pane (so expanding it never reflows or re-measures the channel grid or guide), with focus restoration, a server status indicator dot on **Settings** when not connected, and clean left/right traversal into screen content without focus traps. Selecting a rail tab moves focus directly into that tab's primary content.
- **Initial Focus & Restoration:** Opening the app lands focus directly on the first channel card in **Live** (waiting safely for initial sync). Scroll positions and focus targets are hoisted across `MAIN ↔ PLAYER` and `MAIN ↔ ONBOARDING` transitions so closing the player restores focus to the last-played or zapped channel, EPG program cell, or recording card (with safe fallbacks if an item moved shelves or was deleted), and closing Server Setup restores focus to **Edit Server**.
- **Back Key Hierarchy:** Dismisses open search/dialogs first; on non-Live tabs returns to **Live** with channel focus restored; when the rail is expanded on **Live**, collapses the rail back to the channel grid before exiting the app.
- Compact top header retains the tab title, a D-pad-focusable **Search** button on the Guide tab, and the **Refresh** action.

### Screen Layouts
- **Live Screen:** Multi-column grid of large legible channel cards + focusable filter chips above the grid.
- **Guide (EPG) Screen:** Continuous horizontal timeline grid (sticky channel column, synchronized half-hour time ruler, red vertical "Now" hairline, initialized to the server-clock-adjusted current half-hour upon entering Guide) where D-pad Left/Right moves across shows with the viewport smoothly following focus, paired with **Now** / day jump chips, top-bar program search, and a fixed-height bottom details bar showing title, air time, and synopsis of the currently focused program.
- **Recordings Screen:** Horizontal shelves (`Recording Now`, `Continue Watching`, `All Recordings`, `Scheduled`).

### TV Fullscreen Player (Zero-Friction Remote Controls)
- **Channel Zapping (`Ch+`/`Ch-` or `D-pad Up` while HUD hidden):** Stops the previous stream cleanly, tunes the next/previous channel, and displays a minimal bottom-left OSD pill fading after 3 seconds.
- **HUD Open (`OK`/`Select` or `D-pad Down` while HUD hidden):** Reveals the translucent dark scrim bottom HUD with focusable seek bar, `Play/Pause`, `Back to Live` pill, **Playback Info**, **Display Options**, and horizontal **Quick Channel Strip**.
- **Progressive Seeking (`D-pad Left`/`Right` while HUD hidden or on focused seek bar):** Quick taps follow an exact-target ladder (`10s → 30s → 1m → 5m → 10m`, then `+5m` per tap, stepping back down on opposite taps), while holding scrubs continuously at the active tap's step size (capped at `30s` per throttled tick) with instant preview timestamp and settle/release commit.
- **Back key:** Dismisses open panels or the HUD first; otherwise stops playback cleanly and returns to the previous screen.
- **Single-Tuner Notice:** Auto-dismissing top-right toast naming the blocking active recording rather than an infinite loading spinner.
