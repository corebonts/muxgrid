# Roadmap

Planned improvements and upcoming milestones for **MuxGrid**:

## Near-Term (Pre-Announcement & Community Readiness)
- **Onboarding Preflight Server Diagnostics:** Extend server Test Connection to verify HTTP (`9981/tcp`), `pass` stream profile availability, and `HTSP DVR` (`ACCESS_HTSP_RECORDER`) permissions upfront, surfacing clear setup warnings (and read-only mode notes) before playback.
- **In-Player Audio-Only Toggle (Mobile & TV):** Add an explicit **Audio-Only** toggle in both the mobile and Android TV player HUDs (reusing `PlaybackEngine.setAudioOnly` / `vid=no`) for hands-free listening without leaving the player or keeping TV displays illuminated.
- **Startup Cache Hygiene:** Prune orphaned `libmpv` disk-cache files in `context.cacheDir/mpv-cache` on app startup in case of unexpected process termination.
- **On-Device Hardware Verification Matrix:** Complete and document multi-hour soak tests for background audio, PiP transitions, long-paused growing DVR chase-play (including `RECORDING → COMPLETED` transitions mid-pause), and Android TV remote ergonomics across target devices.

## Medium-Term (DVR, Playback & Multi-Device Polish)
- **AutoRec (Series & Keyword Recording Rules):** Browse, toggle, delete, and create Tvheadend `autorec` rules directly from Guide program details and Recordings.
- **Multiple Server Profiles & LAN ↔ VPN Fallback:** Store multiple Tvheadend servers with quick switching and optional LAN ↔ VPN (WireGuard/Tailscale) host fallback for seamless TV-to-mobile handoffs.
- **Match Content Frame Rate (Android TV):** Support Android display refresh-rate matching (`Off`, `Seamless only`, `Always`) on Android 11+/12+ TVs so `50i`/`50p` and `59.94` sports broadcasts play without `60 Hz` cadence judder.
- **Preferred Audio Language & Passthrough:** Persist preferred audio track language (`alang`) alongside subtitle preferences, and add an optional audio passthrough setting (`audio-spdif`) for AVR and soundbar setups.
- **Jetpack Baseline Profiles:** Add a `:baselineprofile` module for startup and TV Guide/channel browse journeys so release APKs get ART AOT compilation immediately on install.
- **Android 17 (`targetSdk 37`) Local Network Permission:** Add runtime `android.permission.ACCESS_LOCAL_NETWORK` request and rationale flow before bumping `targetSdk` from `36` to `37`.
- **Expanded Automated Test Coverage:** Add JVM unit tests for `TvheadendRepository` and Compose UI smoke tests to complement the existing protocol, store, picon, proxy, and formatter test suites.

## Future Ideas (Community & Ecosystem Growth)
- **String Resources & Localization:** Extract hardcoded UI strings into `strings.xml` to enable community translations.
- **LAN Server Auto-Discovery:** Discover Tvheadend instances on the local network (`_htsp._tcp` / NSD) during onboarding.
- **Manual Time-Window Timers & EPG Genre Filtering:** Support Tvheadend repeating time-window timers (`timerec`) and optional DVB genre filtering/colors in the EPG Guide.
- **Optional HTSP `subscribe` Timeshift Strategy:** Implement an alternate `HtspSubscribeStrategy` behind the `LiveStreamStrategy` seam for users who prefer Tvheadend server-side timeshift over local device ring buffers (and HTSP-only `9982/tcp` networking).
