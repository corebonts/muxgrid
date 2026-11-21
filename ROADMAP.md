# Roadmap

Planned improvements and upcoming milestones for **MuxGrid**:

## Near-Term (Next Release)
- **Automated Signed Release Builds:** Wire CI workflow to produce signed `arm64-v8a` and `armeabi-v7a` GitHub Release APKs from repository secrets, alongside GPLv3 binary attribution notes.
- **In-App About & Open-Source Licenses:** Add a compact About / Third-Party Notices section in Settings displaying app version, build ABI, and open-source attributions (`NOTICE` / `mpv-android-lib` GPLv3 source pointers).
- **On-Device Hardware Verification Matrix:** Complete and document multi-hour soak tests for background audio, PiP transitions, and Android TV remote ergonomics across target devices.

## Medium-Term
- **Multiple Server Profiles:** Store multiple Tvheadend servers with quick switching and optional LAN ↔ VPN (WireGuard/Tailscale) host fallback.
- **Android 17 (`targetSdk 37`) Local Network Permission:** Add runtime `android.permission.ACCESS_LOCAL_NETWORK` request and rationale flow before bumping `targetSdk` from `36` to `37`.
- **Startup Cache Hygiene:** Prune orphaned `libmpv` disk-cache files in `context.cacheDir/mpv-cache` on app startup in case of unexpected process termination.
- **Expanded Automated Test Coverage:** Add JVM unit tests for `PiconFetcher` (Digest auth and icon URL normalization), `TvheadendRepository`, and `MuxGridFormatters`, plus Compose UI smoke tests.

## Future Ideas
- **AutoRec (Series Recording Rules):** Browse, create, and toggle Tvheadend `autorec` timer rules directly from the Guide and Recordings screens.
- **String Resources & Localization:** Extract hardcoded UI strings into `strings.xml` to enable community translations.
- **Optional HTSP `subscribe` Timeshift Strategy:** Implement an alternate `HtspSubscribeStrategy` behind the `LiveStreamStrategy` seam for users who prefer Tvheadend server-side timeshift over local device ring buffers.
