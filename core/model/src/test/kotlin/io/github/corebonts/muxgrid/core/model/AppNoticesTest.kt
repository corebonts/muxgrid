package io.github.corebonts.muxgrid.core.model

import java.io.File
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class AppNoticesTest {

    @Test
    fun `attributions are well formed`() {
        assertTrue(AppNotices.attributions.size >= 8)
        AppNotices.attributions.forEach { entry ->
            assertTrue(entry.name.isNotBlank(), "blank name")
            assertTrue(entry.license.isNotBlank(), "blank license for ${entry.name}")
            assertTrue(entry.url.startsWith("https://"), "non-https url for ${entry.name}")
            assertFalse(entry.displayUrl.contains("://"), "displayUrl not stripped for ${entry.name}")
        }
    }

    @Test
    fun `gpl and apache license documents are bundled`() {
        val gpl = AppNotices.readLicenseText(AppLicenseDocument.GPLV3)
        assertTrue(gpl.contains("GNU GENERAL PUBLIC LICENSE"))
        assertTrue(gpl.contains("Version 3, 29 June 2007"))
        assertTrue(gpl.length > 30_000, "GPLv3 text looks truncated")

        val apache = AppNotices.readLicenseText(AppLicenseDocument.APACHE_2)
        assertTrue(apache.contains("Apache License"))
        assertTrue(apache.contains("Version 2.0, January 2004"))
    }

    @Test
    fun `gpl components and license pointers are surfaced`() {
        val names = AppNotices.attributions.map { it.name }
        assertTrue(names.containsAll(listOf("mpv", "FFmpeg", "mpv-android-lib")))
        assertTrue(AppNotices.GPL_LICENSE_URL.startsWith("https://"))
        assertTrue(AppNotices.SOURCE_CODE_URL.startsWith("https://"))
    }

    @Test
    fun `attributions stay in sync with repository NOTICE`() {
        val noticeFile = generateSequence(File(".").absoluteFile) { it.parentFile }
            .map { File(it, "NOTICE") }
            .firstOrNull { it.isFile }
            ?: return
        val noticeText = noticeFile.readText()

        AppNotices.attributions.forEach { entry ->
            assertTrue(
                noticeText.contains(entry.url),
                "AppNotices entry '${entry.name}' (${entry.url}) is missing from NOTICE",
            )
        }

        val attributionSection = noticeText.substringAfter("Third-Party Software & Attributions", "")
        val numberedItems = Regex("""(?m)^\d+\.\s+.*(?:\n(?!\d+\.\s).*)*""").findAll(attributionSection).toList()
        assertTrue(numberedItems.isNotEmpty(), "Could not parse numbered items in NOTICE")
        val attributionUrls = AppNotices.attributions.map { it.url }.toSet()
        numberedItems.forEach { match ->
            val block = match.value
            val header = block.lineSequence().first()
            assertTrue(
                attributionUrls.any { block.contains(it) },
                "NOTICE item '$header' has no matching entry in AppNotices.attributions",
            )
        }
    }
}
