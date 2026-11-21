package io.github.corebonts.muxgrid.core.model

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class SubtitlePreferenceTest {
    @Test
    fun defaultsToEnabledWithNoPreferredLanguage() {
        val pref = SubtitlePreference()
        assertTrue(pref.enabled)
        assertNull(pref.preferredLanguage)
        assertNull(pref.sanitizedLanguage)
    }

    @Test
    fun sanitizeLanguageAcceptsValidCodesAndRejectsUnsafeOrUndefinedValues() {
        assertEquals("eng", SubtitlePreference.sanitizeLanguage("  eng  "))
        assertEquals("sv", SubtitlePreference.sanitizeLanguage("sv"))
        assertEquals("en-US", SubtitlePreference.sanitizeLanguage("en-US"))
        assertEquals("pt_BR", SubtitlePreference.sanitizeLanguage("pt_BR"))

        assertNull(SubtitlePreference.sanitizeLanguage(null))
        assertNull(SubtitlePreference.sanitizeLanguage("   "))
        assertNull(SubtitlePreference.sanitizeLanguage("und"))
        assertNull(SubtitlePreference.sanitizeLanguage("UND"))
        assertNull(SubtitlePreference.sanitizeLanguage("eng,swe"))
        assertNull(SubtitlePreference.sanitizeLanguage("en;rm -rf"))
        assertNull(SubtitlePreference.sanitizeLanguage("1"))
    }
}
