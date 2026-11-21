package io.github.corebonts.muxgrid.core.data

import io.github.corebonts.muxgrid.core.model.EpgEvent
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.nio.file.Path

class EpgDiskCacheTest {

    @TempDir
    lateinit var tempDir: Path

    @Test
    fun `round-trips strings over 64 KiB and filters expired or mismatched server keys`() {
        val cacheFile = File(tempDir.toFile(), "epg_cache.dat")
        val cache = EpgDiskCache(cacheFile)
        val longDescription = "A".repeat(75_000)

        val events = listOf(
            EpgEvent(
                id = 1L,
                channelId = 10L,
                startEpochSec = 900L,
                stopEpochSec = 1_500L,
                title = "Long Show",
                subtitle = "Part 1",
                summary = "Summary",
                description = longDescription,
                nextEventId = 2L,
                dvrId = 44L,
            ),
            EpgEvent(
                id = 2L,
                channelId = 10L,
                startEpochSec = 100L,
                stopEpochSec = 500L,
                title = "Expired Show",
            ),
        )

        cache.save("tvh.local:9982", events, minStopEpochSec = 1_000L)
        assertFalse(File(tempDir.toFile(), "epg_cache.dat.tmp").exists())

        val loaded = cache.load("tvh.local:9982", minStopEpochSec = 1_000L)
        assertEquals(1, loaded.size)
        assertEquals(longDescription, loaded[0].description)
        assertEquals(44L, loaded[0].dvrId)

        assertTrue(cache.load("other.local:9982", minStopEpochSec = 1_000L).isEmpty())
        assertTrue(cache.load("tvh.local:9982", minStopEpochSec = 2_000L).isEmpty())
    }
}
