# Translating MuxGrid

MuxGrid keeps all translatable UI strings in a **single canonical XML file** so community contributors and translation platforms only need to manage one file per language.

## Where Strings Live

- **Canonical English source:** `core/designsystem/src/main/res/values/strings.xml`
- **Existing translations:**
  - Hungarian (`hu`): `core/designsystem/src/main/res/values-hu/strings.xml`
- **Non-translatable brand name:** `app/src/main/res/values/strings.xml` (`app_name` is marked `translatable="false"` — do not translate it).

## Adding a New Language

1. Copy `core/designsystem/src/main/res/values/strings.xml` to:
   ```text
   core/designsystem/src/main/res/values-<bcp47>/strings.xml
   ```
   Examples: `values-de`, `values-fr`, `values-es`, `values-pt-rBR`, `values-pl`.
2. Translate the text inside `<string>` and `<plurals>` elements.
3. Register the new language with its **native endonym** in `AppLocaleManager.supportedLanguages` (`core/designsystem/src/main/java/io/github/corebonts/muxgrid/core/designsystem/AppLocaleManager.kt`), e.g.:
   ```kotlin
   AppLanguageOption(tag = "de", nativeName = "Deutsch"),
   ```
   - Android 13+ per-app language settings (`generateLocaleConfig = true`) will automatically pick up the new `values-<bcp47>` directory at build time.
   - Registering it in `AppLocaleManager` also makes it selectable in **Settings → General → Language** on Android 9–12 and Android TV.

## Translation Guidelines

- **Keep placeholders intact:** Preserve positional placeholders such as `%1$s`, `%2$d`, and escaped percent signs (`%%`). You may reorder `%1$s` and `%2$s` to match your language's grammar, but never change their index or type.
- **XML escaping:** Escape apostrophes as `\'`, double quotes as `\"`, and ampersands as `&amp;`.
- **Plurals:** Include the plural `<item quantity="...">` categories required by your language (see the [CLDR Plural Rules](https://cldr.unicode.org/index/cldr-spec/plural-rules)).
- **10-ft TV & compact badges:** Keep navigation tabs (`nav_*`), filter chips (`live_filter_*`), and status badges (`recordings_status_*`, `live_badge`) concise so they fit cleanly on both phones and Android TV screens.
- **What stays in English:**
  - Brand and protocol names: `MuxGrid`, `Tvheadend`, `HTSP`, `HTTP`, `EPG`, `DVR`, `PiP`.
  - **Debug Info** and **Playback Info** clipboard export text (kept in English so GitHub issue reports remain readable by maintainers).
  - Bundled third-party license texts (`LICENSE`, `licenses/gpl-3.0.txt`).

## Validating a Translation Locally

Run the unit tests (including `StringResourcesTest`, which verifies XML well-formedness, duplicate keys, and placeholder parity) and Android Lint:

```bash
./gradlew :core:designsystem:test lintDebug
```

Before opening a pull request, run the full CI check:

```bash
./gradlew test assembleDebug
```
