package io.github.corebonts.muxgrid.core.model

/**
 * One third-party attribution surfaced in Settings → App Info → Licenses & Notices.
 * Mirrors the corresponding entry in the repository's `NOTICE` file.
 */
data class AppAttribution(
    val name: String,
    val license: String,
    val url: String,
    val document: AppLicenseDocument,
) {
    val displayUrl: String
        get() = url.removePrefix("https://").removePrefix("http://")
}

/**
 * Full license documents bundled with the app (see `core/model/build.gradle.kts`) so license terms
 * are readable offline, without depending on an external browser.
 */
enum class AppLicenseDocument(
    val title: String,
    val resourcePath: String,
) {
    GPLV3("GNU GPL v3.0", "licenses/gpl-3.0.txt"),
    APACHE_2("Apache License 2.0", "licenses/apache-2.0.txt"),
}

/**
 * Static open-source attribution data for the in-app notices view.
 *
 * Keep [attributions] in sync with `NOTICE`; `AppNoticesTest` guards the shape of each entry.
 */
object AppNotices {

    const val GPL_LICENSE_URL = "https://www.gnu.org/licenses/gpl-3.0.txt"
    const val APACHE_LICENSE_URL = "https://www.apache.org/licenses/LICENSE-2.0"
    const val SOURCE_CODE_URL = AppBuildInfo.DEFAULT_GITHUB_URL

    const val DISTRIBUTION_NOTE =
        "MuxGrid source is Apache-2.0. APKs that bundle prebuilt libmpv/FFmpeg binaries " +
            "(via mpv-android-lib) are distributed under GPL-3.0-or-later."

    val attributions: List<AppAttribution> = listOf(
        AppAttribution(
            name = "Tvheadend",
            license = "GPL-3.0 · HTSP protocol reference",
            url = "https://tvheadend.org/",
            document = AppLicenseDocument.GPLV3,
        ),
        AppAttribution(
            name = "mpv",
            license = "GPL-2.0-or-later / LGPL-2.1-or-later",
            url = "https://github.com/mpv-player/mpv",
            document = AppLicenseDocument.GPLV3,
        ),
        AppAttribution(
            name = "FFmpeg",
            license = "GPL-3.0-or-later / LGPL-2.1-or-later",
            url = "https://github.com/FFmpeg/FFmpeg",
            document = AppLicenseDocument.GPLV3,
        ),
        AppAttribution(
            name = "mpv-android-lib",
            license = "MIT wrapper · GPL-3.0-or-later binaries",
            url = "https://github.com/abdallahmehiz/mpv-android-lib",
            document = AppLicenseDocument.GPLV3,
        ),
        AppAttribution(
            name = "Material Symbols",
            license = "Apache-2.0",
            url = "https://github.com/google/material-design-icons",
            document = AppLicenseDocument.APACHE_2,
        ),
        AppAttribution(
            name = "Haze",
            license = "Apache-2.0",
            url = "https://github.com/chrisbanes/haze",
            document = AppLicenseDocument.APACHE_2,
        ),
        AppAttribution(
            name = "Coil",
            license = "Apache-2.0",
            url = "https://github.com/coil-kt/coil",
            document = AppLicenseDocument.APACHE_2,
        ),
        AppAttribution(
            name = "OkHttp",
            license = "Apache-2.0",
            url = "https://square.github.io/okhttp/",
            document = AppLicenseDocument.APACHE_2,
        ),
        AppAttribution(
            name = "Google Tink",
            license = "Apache-2.0",
            url = "https://github.com/tink-crypto/tink-java",
            document = AppLicenseDocument.APACHE_2,
        ),
        AppAttribution(
            name = "Android Jetpack & Coroutines",
            license = "Apache-2.0",
            url = "https://developer.android.com/jetpack",
            document = AppLicenseDocument.APACHE_2,
        ),
    )

    /**
     * Reads a bundled license document from the classpath. The text is copied into the app's
     * resources at build time, so it is available offline on device and in JVM tests.
     */
    fun readLicenseText(document: AppLicenseDocument): String =
        AppNotices::class.java.getResourceAsStream("/${document.resourcePath}")
            ?.bufferedReader(Charsets.UTF_8)
            ?.use { it.readText() }
            ?.takeIf { it.isNotBlank() }
            ?: "License text unavailable."
}
