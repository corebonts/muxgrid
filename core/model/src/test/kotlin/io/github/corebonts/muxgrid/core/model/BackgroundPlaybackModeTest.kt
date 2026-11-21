package io.github.corebonts.muxgrid.core.model

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class BackgroundPlaybackModeTest {

    @Test
    fun `defaults to PIP on phone and OFF on TV`() {
        assertEquals(BackgroundPlaybackMode.PIP, BackgroundPlaybackMode.defaultFor(FormFactor.PHONE))
        assertEquals(BackgroundPlaybackMode.OFF, BackgroundPlaybackMode.defaultFor(FormFactor.TV))
    }

    @Test
    fun `fromPersisted prefers explicit persisted value over legacy booleans`() {
        assertEquals(
            BackgroundPlaybackMode.OFF,
            BackgroundPlaybackMode.fromPersisted(
                raw = "OFF",
                legacyPipEnabled = true,
                legacyBackgroundPlay = true,
                formFactor = FormFactor.PHONE,
            ),
        )
        assertEquals(
            BackgroundPlaybackMode.AUDIO,
            BackgroundPlaybackMode.fromPersisted(
                raw = "AUDIO",
                legacyPipEnabled = true,
                legacyBackgroundPlay = false,
                formFactor = FormFactor.PHONE,
            ),
        )
    }

    @Test
    fun `fromPersisted migrates legacy pip and background audio flags`() {
        assertEquals(
            BackgroundPlaybackMode.PIP,
            BackgroundPlaybackMode.fromPersisted(
                raw = null,
                legacyPipEnabled = true,
                legacyBackgroundPlay = true,
                formFactor = FormFactor.PHONE,
            ),
        )
        assertEquals(
            BackgroundPlaybackMode.AUDIO,
            BackgroundPlaybackMode.fromPersisted(
                raw = null,
                legacyPipEnabled = false,
                legacyBackgroundPlay = true,
                formFactor = FormFactor.PHONE,
            ),
        )
        assertEquals(
            BackgroundPlaybackMode.OFF,
            BackgroundPlaybackMode.fromPersisted(
                raw = null,
                legacyPipEnabled = false,
                legacyBackgroundPlay = false,
                formFactor = FormFactor.PHONE,
            ),
        )
    }

    @Test
    fun `fromPersisted falls back to form-factor default when unset or invalid`() {
        assertEquals(
            BackgroundPlaybackMode.PIP,
            BackgroundPlaybackMode.fromPersisted(
                raw = "UNKNOWN",
                legacyPipEnabled = null,
                legacyBackgroundPlay = null,
                formFactor = FormFactor.PHONE,
            ),
        )
        assertEquals(
            BackgroundPlaybackMode.OFF,
            BackgroundPlaybackMode.fromPersisted(
                raw = null,
                legacyPipEnabled = null,
                legacyBackgroundPlay = null,
                formFactor = FormFactor.TV,
            ),
        )
    }
}
