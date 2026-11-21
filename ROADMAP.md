# Roadmap

Planned improvements and upcoming milestones for **MuxGrid**:

## Near-Term (Next Release)
- **In-App Open-Source Licenses:** Add a Third-Party Notices / Licenses view inside the Settings **App Info** section surfacing `NOTICE` attributions and `mpv-android-lib` GPLv3 source pointers.
- **On-Device Hardware Verification Matrix:** Complete and document multi-hour soak tests for background audio, PiP transitions, and Android TV remote ergonomics across target devices.

## Medium-Term
- **Multiple Server Profiles:** Store multiple Tvheadend servers with quick switching and optional LAN ↔ VPN (WireGuard/Tailscale) host fallback.
- **Android 17 (`targetSdk 37`) Local Network Permission:** Add runtime `android.permission.ACCESS_LOCAL_NETWORK` request and rationale flow before bumping `targetSdk` from `36` to `37`.
- **Startup Cache Hygiene:** Prune orphaned `libmpv` disk-cache files in `context.cacheDir/mpv-cache` on app startup in case of unexpected process termination.
- **Expanded Automated Test Coverage:** Add JVM unit tests for `TvheadendRepository` and Compose UI smoke tests to complement the existing protocol, store, picon, proxy, and formatter test suites.

## Future Ideas
- **AutoRec (Series Recording Rules):** Browse, create, and toggle Tvheadend `autorec` timer rules directly from the Guide and Recordings screens.
- **String Resources & Localization:** Extract hardcoded UI strings into `strings.xml` to enable community translations.
- **Optional HTSP `subscribe` Timeshift Strategy:** Implement an alternate `HtspSubscribeStrategy` behind the `LiveStreamStrategy` seam for users who prefer Tvheadend server-side timeshift over local device ring buffers.
