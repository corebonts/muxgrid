package io.github.corebonts.muxgrid.core.player

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class RadioPlaybackConfigTest {

    @Test
    fun `resolveCachePauseWaitArg gives radio enough cushion above AudioTrack prefill`() {
        assertEquals("0.95", resolveCachePauseWaitArg(isRadio = true))
        assertEquals("1.0", resolveCachePauseWaitArg(isRadio = false))
    }

    @Test
    fun `resolveDemuxerLavfOptions uses fast audio probe for radio and full SPS-PPS budget for video`() {
        assertEquals("analyzeduration=500000,probesize=262144", resolveDemuxerLavfOptions(isRadio = true))
        assertEquals("analyzeduration=2500000,probesize=2097152", resolveDemuxerLavfOptions(isRadio = false))
    }

    @Test
    fun `shouldAttachAudioMeterFilter pre-attaches for radio when glow is enabled and ignores mini-player toggles`() {
        // Before MobilePlayerScreen mounts (requested = false) or when collapsed to mini-player:
        assertTrue(
            shouldAttachAudioMeterFilter(
                isPlaybackActive = true,
                isRadio = true,
                isAudioStage = true,
                radioAmbientGlowEnabled = true,
                audioMeteringRequested = false,
            ),
        )
        // Disabled in settings -> never attach
        assertFalse(
            shouldAttachAudioMeterFilter(
                isPlaybackActive = true,
                isRadio = true,
                isAudioStage = true,
                radioAmbientGlowEnabled = false,
                audioMeteringRequested = false,
            ),
        )
        // TV video playback -> never attach
        assertFalse(
            shouldAttachAudioMeterFilter(
                isPlaybackActive = true,
                isRadio = false,
                isAudioStage = false,
                radioAmbientGlowEnabled = true,
                audioMeteringRequested = false,
            ),
        )
        // TV background audio with glow UI active -> attach when requested
        assertTrue(
            shouldAttachAudioMeterFilter(
                isPlaybackActive = true,
                isRadio = false,
                isAudioStage = true,
                radioAmbientGlowEnabled = true,
                audioMeteringRequested = true,
            ),
        )
        // Stopped playback -> never attach
        assertFalse(
            shouldAttachAudioMeterFilter(
                isPlaybackActive = false,
                isRadio = true,
                isAudioStage = true,
                radioAmbientGlowEnabled = true,
                audioMeteringRequested = true,
            ),
        )
    }
}
