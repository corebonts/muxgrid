package io.github.corebonts.muxgrid.core.model

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class ThemeModeTest {

    @Test
    fun defaultForMatchesFormFactor() {
        assertEquals(ThemeMode.AUTO, ThemeMode.defaultFor(FormFactor.PHONE))
        assertEquals(ThemeMode.DARK, ThemeMode.defaultFor(FormFactor.TV))
    }

    @Test
    fun fromPersistedParsesKnownValues() {
        assertEquals(ThemeMode.AUTO, ThemeMode.fromPersisted("AUTO", FormFactor.TV))
        assertEquals(ThemeMode.LIGHT, ThemeMode.fromPersisted("LIGHT", FormFactor.TV))
        assertEquals(ThemeMode.DARK, ThemeMode.fromPersisted("DARK", FormFactor.PHONE))
    }

    @Test
    fun fromPersistedFallsBackToFormFactorDefault() {
        assertEquals(ThemeMode.AUTO, ThemeMode.fromPersisted(null, FormFactor.PHONE))
        assertEquals(ThemeMode.DARK, ThemeMode.fromPersisted(null, FormFactor.TV))
        assertEquals(ThemeMode.AUTO, ThemeMode.fromPersisted("UNKNOWN_MODE", FormFactor.PHONE))
        assertEquals(ThemeMode.DARK, ThemeMode.fromPersisted("UNKNOWN_MODE", FormFactor.TV))
    }
}
