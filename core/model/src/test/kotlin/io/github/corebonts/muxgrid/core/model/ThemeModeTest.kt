package io.github.corebonts.muxgrid.core.model

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class ThemeModeTest {

    @Test
    fun defaultForReturnsAmberAcrossFormFactors() {
        assertEquals(ThemeMode.AMBER, ThemeMode.defaultFor(FormFactor.PHONE))
        assertEquals(ThemeMode.AMBER, ThemeMode.defaultFor(FormFactor.TV))
    }

    @Test
    fun fromPersistedParsesKnownValues() {
        assertEquals(ThemeMode.CARBON, ThemeMode.fromPersisted("CARBON", FormFactor.TV))
        assertEquals(ThemeMode.AMBER, ThemeMode.fromPersisted("AMBER", FormFactor.PHONE))
        assertEquals(ThemeMode.AURORA, ThemeMode.fromPersisted("AURORA", FormFactor.TV))
        assertEquals(ThemeMode.INDIGO, ThemeMode.fromPersisted("INDIGO", FormFactor.PHONE))
    }

    @Test
    fun fromPersistedMigratesLegacyAndUnknownToAmber() {
        assertEquals(ThemeMode.AMBER, ThemeMode.fromPersisted(null, FormFactor.PHONE))
        assertEquals(ThemeMode.AMBER, ThemeMode.fromPersisted(null, FormFactor.TV))
        assertEquals(ThemeMode.AMBER, ThemeMode.fromPersisted("AUTO", FormFactor.PHONE))
        assertEquals(ThemeMode.AMBER, ThemeMode.fromPersisted("LIGHT", FormFactor.PHONE))
        assertEquals(ThemeMode.AMBER, ThemeMode.fromPersisted("DARK", FormFactor.TV))
        assertEquals(ThemeMode.AMBER, ThemeMode.fromPersisted("UNKNOWN_MODE", FormFactor.PHONE))
        assertEquals(ThemeMode.AMBER, ThemeMode.fromPersisted("UNKNOWN_MODE", FormFactor.TV))
    }
}
