package io.github.corebonts.muxgrid.core.player

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class VideoRestoreSeekTest {

    @Test
    fun `computeLiveVideoRestoreSeekSec skips audio-only cache gap when cache is ahead`() {
        val target = computeLiveVideoRestoreSeekSec(posSec = 42.0, cacheEndSec = 46.5)
        assertEquals(46.35, target!!, 0.001)
    }

    @Test
    fun `computeLiveVideoRestoreSeekSec returns null when already at live edge or inputs invalid`() {
        assertNull(computeLiveVideoRestoreSeekSec(posSec = 42.0, cacheEndSec = 42.4))
        assertNull(computeLiveVideoRestoreSeekSec(posSec = 42.0, cacheEndSec = 41.0))
        assertNull(computeLiveVideoRestoreSeekSec(posSec = null, cacheEndSec = 45.0))
        assertNull(computeLiveVideoRestoreSeekSec(posSec = 42.0, cacheEndSec = Double.NaN))
    }
}
