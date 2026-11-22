package io.github.corebonts.muxgrid.core.player

import io.github.corebonts.muxgrid.core.model.GrowingDvrFile
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.ByteArrayOutputStream

class GrowingMkvIndexStoreTest {

    private fun buildFakeMkv(
        clusterTimestampsMs: List<Long>,
        headerPadBytes: Int = 32,
        payloadSizeForIndex: (Int) -> Int = { 64 },
        injectCodecPrivateTrap: Boolean = false,
        injectPayloadFalseCluster: Boolean = false,
    ): Pair<ByteArray, Map<Long, Long>> {
        val out = ByteArrayOutputStream()
        // EBML header magic + minimal TimestampScale (1_000_000 ns = 0x0F4240)
        out.write(byteArrayOf(0x1A, 0x45, 0xDF.toByte(), 0xA3.toByte(), 0x84.toByte(), 0x01, 0x02, 0x03, 0x04))
        out.write(byteArrayOf(0x2A, 0xD7.toByte(), 0xB1.toByte(), 0x83.toByte(), 0x0F, 0x42, 0x40))
        if (injectCodecPrivateTrap) {
            // Tracks element (16 54 AE 6B) followed by a fake TimestampScale (500_000_000 ns) inside CodecPrivate
            out.write(byteArrayOf(0x16, 0x54, 0xAE.toByte(), 0x6B))
            out.write(byteArrayOf(0x2A, 0xD7.toByte(), 0xB1.toByte(), 0x84.toByte(), 0x1D, 0xCD.toByte(), 0x65, 0x00))
        }
        out.write(ByteArray(headerPadBytes) { 0x55 })

        val offsets = LinkedHashMap<Long, Long>()
        clusterTimestampsMs.forEachIndexed { idx, tsMs ->
            offsets[tsMs] = out.size().toLong()
            // Cluster ID (1F 43 B6 75) + unknown 8-byte VINT size (01 FF FF FF FF FF FF FF)
            out.write(
                byteArrayOf(
                    0x1F, 0x43, 0xB6.toByte(), 0x75,
                    0x01, 0xFF.toByte(), 0xFF.toByte(), 0xFF.toByte(),
                    0xFF.toByte(), 0xFF.toByte(), 0xFF.toByte(), 0xFF.toByte(),
                ),
            )
            // Cluster Timestamp (0xE7) + 4-byte length VINT (0x84) + big-endian uint32
            out.write(0xE7)
            out.write(0x84)
            out.write(((tsMs ushr 24) and 0xFF).toInt())
            out.write(((tsMs ushr 16) and 0xFF).toInt())
            out.write(((tsMs ushr 8) and 0xFF).toInt())
            out.write((tsMs and 0xFF).toInt())
            // SimpleBlock ID (0xA3) + payload
            out.write(0xA3)
            val payloadLen = payloadSizeForIndex(idx)
            if (injectPayloadFalseCluster && idx == 1 && payloadLen >= 32) {
                // Random bitstream sequence mimicking Cluster + Timestamp but invalid next child byte (0x22)
                out.write(
                    byteArrayOf(
                        0x1F, 0x43, 0xB6.toByte(), 0x75, 0x8F.toByte(),
                        0xE7.toByte(), 0x84.toByte(), 0x00, 0x01, 0x86.toByte(), 0xA0.toByte(), 0x22,
                    ),
                )
                out.write(ByteArray(payloadLen - 12) { 0x22 })
            } else {
                out.write(ByteArray(payloadLen) { 0x22 })
            }
        }
        return out.toByteArray() to offsets
    }

    private fun memoryFile(bytes: ByteArray, onRead: () -> Unit = {}): GrowingDvrFile =
        object : GrowingDvrFile {
            override val initialSize: Long = bytes.size.toLong()
            override suspend fun statSize(): Long = bytes.size.toLong()
            override suspend fun read(offset: Long, size: Int): ByteArray {
                onRead()
                val off = offset.toInt()
                if (off >= bytes.size) return ByteArray(0)
                return bytes.copyOfRange(off, minOf(bytes.size, off + size))
            }
            override fun close() = Unit
        }

    @Test
    fun `header capturer extracts MKV header across arbitrary chunk splits and ignores Tracks trap`() {
        val timestamps = listOf(0L, 1_000L, 2_500L, 5_000L)
        val (mkvBytes, expectedOffsets) = buildFakeMkv(timestamps, injectCodecPrivateTrap = true)
        val index = GrowingMkvIndex(dvrId = 7L, startEpochSec = 1000L)
        val capturer = MkvHeaderCapturer(index)

        // Feed in tiny 13-byte chunks so EBML magic, TimestampScale, and Cluster ID split across chunks
        var pos = 0
        while (pos < mkvBytes.size && !capturer.isDone) {
            val end = minOf(mkvBytes.size, pos + 13)
            capturer.onChunk(pos.toLong(), mkvBytes.copyOfRange(pos, end))
            pos = end
        }

        assertTrue(capturer.isDone)
        val header = index.headerBytes
        assertNotNull(header)
        assertEquals(GrowingMkvIndex.DEFAULT_TIMESTAMP_SCALE_NS, index.timestampScaleNs)
        assertEquals(expectedOffsets[0L]!!.toInt(), header!!.size)
        assertArrayEquals(mkvBytes.copyOfRange(0, expectedOffsets[0L]!!.toInt()), header)
        assertEquals(GrowingMkvIndex.ContainerKind.MKV, index.containerKind)
        assertEquals(expectedOffsets[0L], index.findClusterAtOrBefore(500L)?.byteOffset)
    }

    @Test
    fun `header capturer marks non-MKV streams and stops immediately`() {
        val tsData = ByteArray(512) { idx -> if (idx % 188 == 0) 0x47 else 0x00 }
        val index = GrowingMkvIndex(dvrId = 9L, startEpochSec = 2000L)
        val capturer = MkvHeaderCapturer(index)
        capturer.onChunk(0L, tsData)
        assertTrue(capturer.isDone)
        assertEquals(GrowingMkvIndex.ContainerKind.NON_MKV, index.containerKind)
        assertNull(index.headerBytes)
        assertEquals(0, index.entryCount)
    }

    @Test
    fun `store uses LRU eviction and invalidates on mismatched startEpochSec`() {
        val (mkvBytes, expectedOffsets) = buildFakeMkv(listOf(0L, 3_000L, 6_000L))
        val store = GrowingMkvIndexStore()
        val idx = store.getOrLoad(dvrId = 42L, startEpochSec = 1700000000L)
        MkvHeaderCapturer(idx).onChunk(0L, mkvBytes)

        val reloaded = store.getOrLoad(dvrId = 42L, startEpochSec = 1700000000L)
        assertNotNull(reloaded.headerBytes)
        assertEquals(expectedOffsets[6_000L], reloaded.findClusterAtOrBefore(7_000L)?.byteOffset)

        // Touch dvrId 42L while cycling 16 other IDs so LRU keeps 42L alive
        for (otherId in 100L until 115L) {
            store.getOrLoad(otherId, 1000L)
        }
        assertTrue(store.getOrLoad(42L, 1700000000L).hasHeader)

        // Mismatched startEpochSec invalidates stale index
        val mismatched = store.getOrLoad(dvrId = 42L, startEpochSec = 1700009999L)
        assertNull(mismatched.headerBytes)
        assertEquals(0, mismatched.entryCount)
    }

    @Test
    fun `binary searcher converges tightly on CBR and VBR streams and skips cached NON_MKV`() = runBlocking {
        // 1. CBR stream (~2 MB, 2s clusters up to 60s)
        val cbrTimestamps = (0..60 step 2).map { it * 1_000L }
        val (cbrBytes, cbrOffsets) = buildFakeMkv(
            clusterTimestampsMs = cbrTimestamps,
            headerPadBytes = 128,
            payloadSizeForIndex = { 65_536 },
            injectPayloadFalseCluster = true,
        )
        var cbrReads = 0
        val cbrIndex = GrowingMkvIndex(dvrId = 77L, startEpochSec = 1000L)
        val found = GrowingMkvBinarySearcher.findClusterForTarget(
            file = memoryFile(cbrBytes) { cbrReads++ },
            index = cbrIndex,
            targetMs = 42_000L,
            durationMs = 60_000L,
        )
        assertNotNull(found)
        assertTrue(found!!.timestampMs in 40_000L..42_000L, "Unexpected cluster ts=${found.timestampMs}")
        assertEquals(cbrOffsets[found.timestampMs], found.byteOffset)
        assertTrue(cbrReads <= 3, "Expected CBR search in <= 3 reads, got $cbrReads")

        // 2. VBR stream (first half 3x bitrate of second half)
        val vbrTimestamps = (0..120 step 2).map { it * 1_000L }
        val (vbrBytes, vbrOffsets) = buildFakeMkv(
            clusterTimestampsMs = vbrTimestamps,
            headerPadBytes = 128,
            payloadSizeForIndex = { idx -> if (idx < 30) 98_304 else 32_768 },
        )
        var vbrReads = 0
        val vbrIndex = GrowingMkvIndex(dvrId = 79L, startEpochSec = 1000L)
        val vbrFound = GrowingMkvBinarySearcher.findClusterForTarget(
            file = memoryFile(vbrBytes) { vbrReads++ },
            index = vbrIndex,
            targetMs = 80_000L,
            durationMs = 120_000L,
        )
        assertNotNull(vbrFound)
        assertTrue(vbrFound!!.timestampMs in 76_000L..80_000L, "Unexpected VBR cluster ts=${vbrFound.timestampMs}")
        assertEquals(vbrOffsets[vbrFound.timestampMs], vbrFound.byteOffset)
        assertTrue(vbrReads <= 5, "Expected VBR search in <= 5 reads, got $vbrReads")

        // 3. Non-MKV (MPEG-TS) marks NON_MKV on first probe and performs 0 reads on subsequent seeks
        val tsBytes = ByteArray(1_024) { idx -> if (idx % 188 == 0) 0x47 else 0x00 }
        var tsReads = 0
        val tsFile = memoryFile(tsBytes) { tsReads++ }
        val tsIndex = GrowingMkvIndex(dvrId = 78L, startEpochSec = 1000L)
        assertNull(GrowingMkvBinarySearcher.findClusterForTarget(tsFile, tsIndex, 30_000L, 60_000L))
        assertEquals(1, tsReads)
        assertEquals(GrowingMkvIndex.ContainerKind.NON_MKV, tsIndex.containerKind)
        assertNull(GrowingMkvBinarySearcher.findClusterForTarget(tsFile, tsIndex, 45_000L, 60_000L))
        assertEquals(1, tsReads)
    }

    @Test
    fun `binary searcher handles startExtra pre-padding, large headers, and oversized clusters`() = runBlocking {
        // Pre-padding + >64 KiB header (80 KiB) + one oversized 2.5 MiB cluster mid-file
        val timestamps = (0..18).map { it * 30_000L } // 0s .. 540s (9 min)
        val (mkvBytes, offsets) = buildFakeMkv(
            clusterTimestampsMs = timestamps,
            headerPadBytes = 80_000,
            payloadSizeForIndex = { idx -> if (idx == 9) 2_600_000 else 131_072 },
        )
        val index = GrowingMkvIndex(dvrId = 88L, startEpochSec = 5000L)
        // Wall-clock durationMs is 4 minutes (240_000ms) while pre-padding pushed MKV timestamps to 540_000ms
        val targetFound = GrowingMkvBinarySearcher.findClusterForTarget(
            file = memoryFile(mkvBytes),
            index = index,
            targetMs = 450_000L,
            durationMs = 240_000L,
        )
        assertNotNull(targetFound)
        assertEquals(450_000L, targetFound!!.timestampMs)
        assertEquals(offsets[450_000L], targetFound.byteOffset)
        assertTrue(index.headerSize > 65_536)
    }
}
