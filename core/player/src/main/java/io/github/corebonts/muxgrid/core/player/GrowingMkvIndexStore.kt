package io.github.corebonts.muxgrid.core.player

import io.github.corebonts.muxgrid.core.model.GrowingDvrFile
import java.io.ByteArrayOutputStream

data class ClusterIndexEntry(
    val timestampMs: Long,
    val byteOffset: Long,
)

/**
 * Sparse Matroska cluster index (`timestampMs -> byteOffset`) plus the opening MKV header
 * (`0..firstClusterOffset`) for a single actively recording DVR entry.
 */
class GrowingMkvIndex(
    val dvrId: Long,
    val startEpochSec: Long,
) {
    enum class ContainerKind {
        UNKNOWN,
        MKV,
        NON_MKV,
    }

    private val lock = Any()
    private var _containerKind: ContainerKind = ContainerKind.UNKNOWN
    private var _timestampScaleNs: Long = DEFAULT_TIMESTAMP_SCALE_NS
    private var _headerBytes: ByteArray? = null
    private val _entries = ArrayList<ClusterIndexEntry>(64)

    val containerKind: ContainerKind
        get() = synchronized(lock) { _containerKind }

    val timestampScaleNs: Long
        get() = synchronized(lock) { _timestampScaleNs }

    val hasHeader: Boolean
        get() = synchronized(lock) { _headerBytes != null }

    val headerSize: Int
        get() = synchronized(lock) { _headerBytes?.size ?: 0 }

    val headerBytes: ByteArray?
        get() = synchronized(lock) { _headerBytes?.copyOf() }

    val entryCount: Int
        get() = synchronized(lock) { _entries.size }

    internal fun headerBytesRef(): ByteArray? = synchronized(lock) { _headerBytes }

    fun markNonMkv() {
        synchronized(lock) {
            if (_containerKind == ContainerKind.UNKNOWN) {
                _containerKind = ContainerKind.NON_MKV
            }
        }
    }

    fun recordHeader(bytes: ByteArray, scaleNs: Long) {
        if (bytes.isEmpty() || bytes.size > MAX_HEADER_BYTES) return
        synchronized(lock) {
            _containerKind = ContainerKind.MKV
            if (_headerBytes == null) {
                _headerBytes = bytes.copyOf()
                if (scaleNs > 0L) {
                    _timestampScaleNs = scaleNs
                }
            }
        }
    }

    fun recordCluster(timestampMs: Long, byteOffset: Long) {
        if (timestampMs < 0L || byteOffset <= 0L) return
        synchronized(lock) {
            if (_containerKind == ContainerKind.NON_MKV) return
            _containerKind = ContainerKind.MKV
            if (_entries.isEmpty()) {
                _entries.add(ClusterIndexEntry(timestampMs, byteOffset))
                return
            }
            val last = _entries.last()
            if (timestampMs > last.timestampMs && byteOffset > last.byteOffset) {
                if (timestampMs - last.timestampMs >= MIN_INDEX_INTERVAL_MS) {
                    _entries.add(ClusterIndexEntry(timestampMs, byteOffset))
                }
                return
            }
            var lo = 0
            var hi = _entries.size - 1
            while (lo <= hi) {
                val mid = (lo + hi) ushr 1
                val cur = _entries[mid]
                when {
                    cur.timestampMs < timestampMs -> lo = mid + 1
                    cur.timestampMs > timestampMs -> hi = mid - 1
                    else -> return
                }
            }
            val prev = if (lo > 0) _entries[lo - 1] else null
            val next = if (lo < _entries.size) _entries[lo] else null
            if (prev != null && (timestampMs - prev.timestampMs < MIN_INDEX_INTERVAL_MS || byteOffset <= prev.byteOffset)) {
                return
            }
            if (next != null && (next.timestampMs - timestampMs < MIN_INDEX_INTERVAL_MS || byteOffset >= next.byteOffset)) {
                return
            }
            _entries.add(lo, ClusterIndexEntry(timestampMs, byteOffset))
        }
    }

    /**
     * Returns the latest indexed cluster with `timestampMs <= targetMs`, or `null` if none exists.
     */
    fun findClusterAtOrBefore(targetMs: Long): ClusterIndexEntry? = synchronized(lock) {
        if (_entries.isEmpty()) return null
        var lo = 0
        var hi = _entries.size - 1
        var best = -1
        while (lo <= hi) {
            val mid = (lo + hi) ushr 1
            if (_entries[mid].timestampMs <= targetMs) {
                best = mid
                lo = mid + 1
            } else {
                hi = mid - 1
            }
        }
        if (best >= 0) _entries[best] else null
    }

    /**
     * Returns the earliest indexed cluster with `timestampMs >= targetMs`, or `null` if none exists.
     */
    fun findClusterAtOrAfter(targetMs: Long): ClusterIndexEntry? = synchronized(lock) {
        if (_entries.isEmpty()) return null
        var lo = 0
        var hi = _entries.size - 1
        var best = -1
        while (lo <= hi) {
            val mid = (lo + hi) ushr 1
            if (_entries[mid].timestampMs >= targetMs) {
                best = mid
                hi = mid - 1
            } else {
                lo = mid + 1
            }
        }
        if (best >= 0) _entries[best] else null
    }

    /**
     * Estimates the byte offset for [targetMs] using the observed bitrate between indexed clusters,
     * or `null` if insufficient indexed span exists.
     */
    internal fun estimateBytesPerMs(): Double? = synchronized(lock) {
        if (_entries.size < 2) return null
        val first = _entries.first()
        val last = _entries.last()
        val dt = last.timestampMs - first.timestampMs
        val db = last.byteOffset - first.byteOffset
        if (dt < MIN_BITRATE_ESTIMATE_SPAN_MS || db <= 0L) return null
        db.toDouble() / dt.toDouble()
    }

    companion object {
        const val DEFAULT_TIMESTAMP_SCALE_NS: Long = 1_000_000L
        const val MAX_HEADER_BYTES: Int = 262_144
        const val MIN_INDEX_INTERVAL_MS: Long = 500L
        private const val MIN_BITRATE_ESTIMATE_SPAN_MS: Long = 4_000L
    }
}

/**
 * Shared EBML / Matroska Cluster parsing helpers.
 */
internal object MkvEbml {
    const val MIN_CLUSTER_PROBE_BYTES = 16
    const val OVERLAP_BYTES = 24
    const val MAX_RECORDING_DURATION_MS = 24L * 3600L * 1000L

    fun isEbmlMagic(bytes: ByteArray, length: Int = bytes.size): Boolean =
        length >= 4 &&
            (bytes[0].toInt() and 0xFF) == 0x1A &&
            (bytes[1].toInt() and 0xFF) == 0x45 &&
            (bytes[2].toInt() and 0xFF) == 0xDF &&
            (bytes[3].toInt() and 0xFF) == 0xA3

    fun isClusterIdAt(buf: ByteArray, pos: Int): Boolean =
        (buf[pos].toInt() and 0xFF) == 0x1F &&
            (buf[pos + 1].toInt() and 0xFF) == 0x43 &&
            (buf[pos + 2].toInt() and 0xFF) == 0xB6 &&
            (buf[pos + 3].toInt() and 0xFF) == 0x75

    fun parseClusterTimestamp(
        buf: ByteArray,
        clusterPos: Int,
        bufLen: Int,
        timestampScaleNs: Long,
    ): Long? {
        val sizePos = clusterPos + 4
        if (sizePos >= bufLen) return null
        val sizeVintLen = vintLength(buf[sizePos].toInt() and 0xFF) ?: return null
        val childPos = sizePos + sizeVintLen
        if (childPos + 2 >= bufLen) return null
        // Immediate child must be Cluster Timestamp (0xE7)
        if ((buf[childPos].toInt() and 0xFF) != 0xE7) return null
        val tsLenByte = buf[childPos + 1].toInt() and 0xFF
        // Timestamp length is a 1-byte VINT (0x81..0x88 -> 1..8 bytes)
        if ((tsLenByte and 0x80) == 0) return null
        val tsByteCount = tsLenByte and 0x7F
        if (tsByteCount !in 1..8) return null
        val valuePos = childPos + 2
        val afterTsPos = valuePos + tsByteCount
        if (afterTsPos > bufLen) return null
        // Guard against random payload byte matches by verifying the next byte starts a valid Cluster child
        if (afterTsPos < bufLen && !isValidClusterChildFirstByte(buf[afterTsPos].toInt() and 0xFF)) {
            return null
        }
        var rawTs = 0L
        for (k in 0 until tsByteCount) {
            rawTs = (rawTs shl 8) or (buf[valuePos + k].toInt() and 0xFF).toLong()
        }
        if (rawTs < 0L || timestampScaleNs <= 0L) return null
        if (rawTs > Long.MAX_VALUE / timestampScaleNs) return null
        return (rawTs * timestampScaleNs) / 1_000_000L
    }

    fun parseTimestampScaleFromHeader(header: ByteArray, length: Int = header.size): Long? {
        // Search for TimestampScale (0x2A 0xD7 0xB1), stopping at Tracks (0x16 0x54 0xAE 0x6B)
        var i = 0
        val limit = length - 5
        while (i <= limit) {
            val b0 = header[i].toInt() and 0xFF
            if (b0 == 0x16 &&
                (header[i + 1].toInt() and 0xFF) == 0x54 &&
                (header[i + 2].toInt() and 0xFF) == 0xAE &&
                (header[i + 3].toInt() and 0xFF) == 0x6B
            ) {
                break
            }
            if (b0 == 0x2A &&
                (header[i + 1].toInt() and 0xFF) == 0xD7 &&
                (header[i + 2].toInt() and 0xFF) == 0xB1
            ) {
                val lenByte = header[i + 3].toInt() and 0xFF
                if ((lenByte and 0x80) != 0) {
                    val byteCount = lenByte and 0x7F
                    if (byteCount in 1..8 && i + 4 + byteCount <= length) {
                        var scale = 0L
                        for (k in 0 until byteCount) {
                            scale = (scale shl 8) or (header[i + 4 + k].toInt() and 0xFF).toLong()
                        }
                        if (scale in 1L..1_000_000_000L) return scale
                    }
                }
            }
            i++
        }
        return null
    }

    /**
     * Scans all valid Cluster headers inside [buf] (`[0, bufLen)`) starting at [startPos],
     * using [timestampScaleNs] and validating against `[0, maxPlausibleTsMs]`.
     */
    inline fun forEachCluster(
        buf: ByteArray,
        bufLen: Int,
        baseOffset: Long,
        startPos: Int,
        timestampScaleNs: Long,
        maxPlausibleTsMs: Long,
        onCluster: (tsMs: Long, byteOffset: Long) -> Unit,
    ) {
        val limit = bufLen - MIN_CLUSTER_PROBE_BYTES
        var i = startPos.coerceAtLeast(0)
        while (i <= limit) {
            if (isClusterIdAt(buf, i)) {
                val tsMs = parseClusterTimestamp(buf, i, bufLen, timestampScaleNs)
                if (tsMs != null && tsMs in 0L..maxPlausibleTsMs) {
                    onCluster(tsMs, baseOffset + i)
                    i += 12
                    continue
                }
            }
            i++
        }
    }

    private fun isValidClusterChildFirstByte(b: Int): Boolean = when (b) {
        0xA3, // SimpleBlock
        0xA0, // BlockGroup
        0xA1, // Block
        0xA7, // Position
        0xAB, // PrevSize
        0x58, // SilentTracks (0x58 0x54)
        0xAF, // EncryptedBlock
        0xBF, // CRC-32
        0xEC, // Void
        0x1F, // Next Cluster (empty cluster)
        -> true
        else -> false
    }

    private fun vintLength(firstByte: Int): Int? {
        if (firstByte == 0) return null
        for (len in 1..8) {
            if ((firstByte and (0x80 ushr (len - 1))) != 0) return len
        }
        return null
    }
}

/**
 * Lightweight single-pass MKV opening-header capturer (`0..firstClusterOffset`) used when
 * streaming or probing from byte 0. Immediately releases all state once the first Cluster (or
 * non-MKV magic) is observed so ongoing streaming has zero per-chunk CPU or allocation overhead.
 */
internal class MkvHeaderCapturer(
    private val index: GrowingMkvIndex,
) {
    private var headerBuffer: ByteArrayOutputStream? = null
    private var magicVerified: Boolean = index.hasHeader
    private var bytesSeen: Int = 0

    var isDone: Boolean = index.hasHeader || index.containerKind == GrowingMkvIndex.ContainerKind.NON_MKV
        private set

    fun onChunk(fileOffset: Long, data: ByteArray, length: Int = data.size) {
        if (isDone || length <= 0) return
        if (fileOffset != bytesSeen.toLong()) {
            finish()
            return
        }

        // Fast path: chunk 0 covers the entire opening header in a single buffer (zero BAOS copy).
        if (bytesSeen == 0 && headerBuffer == null && length >= 4) {
            if (!MkvEbml.isEbmlMagic(data, length)) {
                index.markNonMkv()
                finish()
                return
            }
            magicVerified = true
            val scanLen = minOf(length, GrowingMkvIndex.MAX_HEADER_BYTES)
            if (tryCaptureFromBuffer(data, scanLen, fullChunk = data, fullChunkLen = length, fullChunkOffset = 0L)) {
                return
            }
            if (length >= GrowingMkvIndex.MAX_HEADER_BYTES) {
                finish()
                return
            }
            headerBuffer = ByteArrayOutputStream(maxOf(8_192, length)).apply { write(data, 0, length) }
            bytesSeen = length
            return
        }

        val hb = headerBuffer ?: ByteArrayOutputStream(maxOf(4_096, length)).also { headerBuffer = it }
        val remaining = GrowingMkvIndex.MAX_HEADER_BYTES - hb.size()
        if (remaining <= 0) {
            finish()
            return
        }
        val prevSize = hb.size()
        val toWrite = minOf(length, remaining)
        hb.write(data, 0, toWrite)
        bytesSeen += toWrite

        val accumulated = hb.toByteArray()
        if (!magicVerified) {
            if (accumulated.size < 4) return
            if (!MkvEbml.isEbmlMagic(accumulated, accumulated.size)) {
                index.markNonMkv()
                finish()
                return
            }
            magicVerified = true
        }

        val scanStart = (prevSize - MkvEbml.OVERLAP_BYTES).coerceAtLeast(0)
        if (tryCaptureFromBuffer(
                buf = accumulated,
                bufLen = accumulated.size,
                fullChunk = data,
                fullChunkLen = length,
                fullChunkOffset = fileOffset,
                startPos = scanStart,
            )
        ) {
            return
        }

        if (hb.size() >= GrowingMkvIndex.MAX_HEADER_BYTES) {
            finish()
        }
    }

    private fun tryCaptureFromBuffer(
        buf: ByteArray,
        bufLen: Int,
        fullChunk: ByteArray,
        fullChunkLen: Int,
        fullChunkOffset: Long,
        startPos: Int = 0,
    ): Boolean {
        val limit = bufLen - MkvEbml.MIN_CLUSTER_PROBE_BYTES
        var i = startPos
        while (i <= limit) {
            if (MkvEbml.isClusterIdAt(buf, i) && i > 0) {
                val scale = MkvEbml.parseTimestampScaleFromHeader(buf, length = i)
                    ?: GrowingMkvIndex.DEFAULT_TIMESTAMP_SCALE_NS
                val tsMs = MkvEbml.parseClusterTimestamp(buf, i, bufLen, scale)
                if (tsMs != null && tsMs in 0L..MAX_FIRST_CLUSTER_TS_MS) {
                    index.recordHeader(buf.copyOfRange(0, i), scale)
                    index.recordCluster(tsMs, i.toLong())
                    // Index any additional clusters already sitting in the current chunk for free
                    val resumePosInChunk = (i.toLong() + 12L - fullChunkOffset).toInt().coerceAtLeast(0)
                    if (resumePosInChunk < fullChunkLen) {
                        var lastTs: Long = tsMs
                        MkvEbml.forEachCluster(
                            buf = fullChunk,
                            bufLen = fullChunkLen,
                            baseOffset = fullChunkOffset,
                            startPos = resumePosInChunk,
                            timestampScaleNs = scale,
                            maxPlausibleTsMs = MkvEbml.MAX_RECORDING_DURATION_MS,
                        ) { nextTs, nextOffset ->
                            if (nextTs >= lastTs && (nextTs - lastTs) <= MAX_ADJACENT_CLUSTER_GAP_MS) {
                                lastTs = nextTs
                                index.recordCluster(nextTs, nextOffset)
                            }
                        }
                    }
                    finish()
                    return true
                }
            }
            i++
        }
        return false
    }

    private fun finish() {
        headerBuffer = null
        isDone = true
    }

    companion object {
        private const val MAX_FIRST_CLUSTER_TS_MS = 600_000L
        private const val MAX_ADJACENT_CLUSTER_GAP_MS = 300_000L
    }
}

/**
 * Thread-safe LRU in-memory cache of [GrowingMkvIndex] instances keyed by DVR entry ID.
 */
class GrowingMkvIndexStore {
    private val lock = Any()
    private val cache = object : LinkedHashMap<Long, GrowingMkvIndex>(MAX_CACHED_INDICES, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<Long, GrowingMkvIndex>?): Boolean =
            size > MAX_CACHED_INDICES
    }

    fun getOrLoad(dvrId: Long, startEpochSec: Long): GrowingMkvIndex = synchronized(lock) {
        val existing = cache[dvrId]
        if (existing != null && existing.startEpochSec == startEpochSec) {
            return existing
        }
        val index = GrowingMkvIndex(dvrId = dvrId, startEpochSec = startEpochSec)
        cache[dvrId] = index
        index
    }

    companion object {
        private const val MAX_CACHED_INDICES = 16
    }
}

/**
 * On-demand interpolated binary searcher for un-indexed regions of an actively growing Matroska
 * recording.
 *
 * Reads bounded chunks via HTSP `fileRead`, enriches [GrowingMkvIndex] with every cluster seen
 * inside each fetched chunk, and skips straight to `null` for non-MKV streams (e.g. MPEG-TS).
 */
internal object GrowingMkvBinarySearcher {
    private const val HEADER_PROBE_BYTES = 65_536
    private const val STEP_CHUNK_BYTES = 262_144
    private const val MAX_PROBE_WINDOW_BYTES = 2L * 1024L * 1024L
    private const val MAX_ITERATIONS = 8
    private const val MAX_INTERP_ITERATIONS = 4
    private const val TARGET_TOLERANCE_MS = 3_000L
    private const val MAX_ACCEPTABLE_DELTA_MS = 45_000L
    private const val MIN_SEARCH_WINDOW_BYTES = 262_144L
    private const val MAX_DURATION_SLOP_MS = 3_600_000L // allow up to 60m startExtra/padding

    private data class ProbeResult(
        val bestAtOrBefore: ClusterIndexEntry?,
        val firstAfter: ClusterIndexEntry?,
        val scannedToEnd: Boolean,
        val nextOffset: Long,
    )

    suspend fun findClusterForTarget(
        file: GrowingDvrFile,
        index: GrowingMkvIndex,
        targetMs: Long,
        durationMs: Long,
    ): ClusterIndexEntry? {
        if (targetMs <= 0L) return null
        if (index.containerKind == GrowingMkvIndex.ContainerKind.NON_MKV) return null

        // 1. Ensure MKV header is known; returns null immediately for non-MKV (e.g. MPEG-TS).
        if (!index.hasHeader) {
            ensureHeaderLoaded(file, index)
            if (!index.hasHeader) return null
        }
        val headerSize = index.headerSize.toLong()

        val fileSize = if (file.initialSize > headerSize) {
            file.initialSize
        } else {
            maxOf(file.initialSize, file.statSize()).coerceAtLeast(0L)
        }
        if (fileSize <= headerSize) return null

        // 2. Fast-path if index already has a close cluster.
        val existingBefore = index.findClusterAtOrBefore(targetMs)
        if (existingBefore != null && (targetMs - existingBefore.timestampMs) <= TARGET_TOLERANCE_MS) {
            return existingBefore
        }

        val existingAfter = index.findClusterAtOrAfter(targetMs)
        var loByte = if (existingBefore != null) existingBefore.byteOffset + 1L else headerSize
        var hiByte = existingAfter?.byteOffset ?: fileSize
        var loTsMs = existingBefore?.timestampMs ?: 0L
        var hiTsMs: Long? = existingAfter?.timestampMs
        if (hiByte - loByte < MIN_SEARCH_WINDOW_BYTES) {
            return existingBefore?.takeIf { (targetMs - it.timestampMs) <= MAX_ACCEPTABLE_DELTA_MS }
        }

        val scaleNs = index.timestampScaleNs
        val maxPlausibleTsMs = if (durationMs > 0L) {
            minOf(MkvEbml.MAX_RECORDING_DURATION_MS, maxOf(durationMs, targetMs) + MAX_DURATION_SLOP_MS)
        } else {
            MkvEbml.MAX_RECORDING_DURATION_MS
        }
        var best: ClusterIndexEntry? = existingBefore
        var probeBiasHi: Long? = null

        for (iter in 0 until MAX_ITERATIONS) {
            if (hiByte - loByte < MIN_SEARCH_WINDOW_BYTES) break

            val activeHi = probeBiasHi?.takeIf { it in (loByte + MIN_SEARCH_WINDOW_BYTES)..hiByte } ?: hiByte
            val spanBytes = activeHi - loByte
            val probeByte = chooseProbeByte(
                iter = iter,
                loByte = loByte,
                hiByte = activeHi,
                spanBytes = spanBytes,
                loTsMs = loTsMs,
                hiTsMs = hiTsMs,
                targetMs = targetMs,
                durationMs = durationMs,
                index = index,
            )

            val probe = probeClusterRegion(
                file = file,
                index = index,
                startOffset = probeByte,
                maxEndOffset = activeHi,
                targetMs = targetMs,
                loTsMs = loTsMs,
                hiTsMs = hiTsMs,
                timestampScaleNs = scaleNs,
                maxPlausibleTsMs = maxPlausibleTsMs,
            )

            if (probe.bestAtOrBefore != null) {
                probeBiasHi = null
                val candidate = probe.bestAtOrBefore
                if (best == null || candidate.timestampMs > best.timestampMs) {
                    best = candidate
                }
                if ((targetMs - best.timestampMs) <= TARGET_TOLERANCE_MS) {
                    return best
                }
                // If this single contiguous probe saw both sides of targetMs, best is the exact
                // tightest cluster in the file <= targetMs — no further probes needed.
                if (probe.firstAfter != null) {
                    return best.takeIf { (targetMs - it.timestampMs) <= MAX_ACCEPTABLE_DELTA_MS }
                }
                loByte = candidate.byteOffset + 1L
                loTsMs = candidate.timestampMs
            } else if (probe.firstAfter != null) {
                hiByte = probe.firstAfter.byteOffset
                hiTsMs = probe.firstAfter.timestampMs
                probeBiasHi = null
            } else if (probe.scannedToEnd) {
                // Actually scanned all the way to activeHi/EOF without finding any cluster.
                if (activeHi == hiByte) {
                    hiByte = probeByte
                    probeBiasHi = null
                } else {
                    probeBiasHi = probeByte
                }
            } else {
                // Hit probe cap mid-window (e.g. >2 MiB cluster) without reaching activeHi:
                // explore [loByte, probeByte) first without falsely discarding [probeByte, hiByte].
                if (probeByte - loByte >= MIN_SEARCH_WINDOW_BYTES) {
                    probeBiasHi = probeByte
                } else {
                    loByte = maxOf(loByte + 1L, probe.nextOffset)
                    probeBiasHi = null
                }
            }
        }

        return best?.takeIf { (targetMs - it.timestampMs) <= MAX_ACCEPTABLE_DELTA_MS }
    }

    private suspend fun ensureHeaderLoaded(file: GrowingDvrFile, index: GrowingMkvIndex) {
        val capturer = MkvHeaderCapturer(index)
        var offset = 0L
        while (!capturer.isDone && offset < GrowingMkvIndex.MAX_HEADER_BYTES) {
            val toRead = minOf(HEADER_PROBE_BYTES, GrowingMkvIndex.MAX_HEADER_BYTES - offset.toInt())
            val chunk = file.read(offset, toRead)
            if (chunk.isEmpty()) break
            capturer.onChunk(offset, chunk)
            offset += chunk.size
            if (chunk.size < toRead) break
        }
    }

    private fun chooseProbeByte(
        iter: Int,
        loByte: Long,
        hiByte: Long,
        spanBytes: Long,
        loTsMs: Long,
        hiTsMs: Long?,
        targetMs: Long,
        durationMs: Long,
        index: GrowingMkvIndex,
    ): Long {
        val maxProbeStart = (hiByte - STEP_CHUNK_BYTES).coerceAtLeast(loByte)
        if (iter >= MAX_INTERP_ITERATIONS) {
            return (loByte + spanBytes / 2L).coerceIn(loByte, maxProbeStart)
        }

        val estimatedOffset: Long? = when {
            hiTsMs != null && hiTsMs > loTsMs && targetMs in loTsMs..hiTsMs -> {
                val rawFrac = (targetMs - loTsMs).toDouble() / (hiTsMs - loTsMs).toDouble()
                val safeFrac = if (iter == 0) rawFrac.coerceIn(0.02, 0.98) else rawFrac.coerceIn(0.15, 0.85)
                loByte + (spanBytes.toDouble() * safeFrac).toLong()
            }
            hiTsMs == null && durationMs > targetMs && durationMs > loTsMs -> {
                val rawFrac = (targetMs - loTsMs).toDouble() / (durationMs - loTsMs).toDouble()
                val safeFrac = if (iter == 0) rawFrac.coerceIn(0.02, 0.98) else rawFrac.coerceIn(0.15, 0.85)
                loByte + (spanBytes.toDouble() * safeFrac).toLong()
            }
            hiTsMs == null -> {
                // Duration underestimated (e.g. pre-padding) or unknown -> use real indexed bitrate
                index.estimateBytesPerMs()?.let { bytesPerMs ->
                    loByte + ((targetMs - loTsMs).toDouble() * bytesPerMs).toLong()
                }
            }
            else -> null
        }

        val rawProbe = estimatedOffset ?: (loByte + spanBytes / 2L)
        return rawProbe.coerceIn(loByte, maxProbeStart)
    }

    private suspend fun probeClusterRegion(
        file: GrowingDvrFile,
        index: GrowingMkvIndex,
        startOffset: Long,
        maxEndOffset: Long,
        targetMs: Long,
        loTsMs: Long,
        hiTsMs: Long?,
        timestampScaleNs: Long,
        maxPlausibleTsMs: Long,
    ): ProbeResult {
        var offset = startOffset.coerceAtLeast(0L)
        val scanLimitOffset = minOf(maxEndOffset, offset + MAX_PROBE_WINDOW_BYTES)
        var tail = ByteArray(0)
        var tailStart = offset
        var bestBefore: ClusterIndexEntry? = null
        var firstAfter: ClusterIndexEntry? = null
        var reachedEnd = false

        while (offset < scanLimitOffset) {
            val remaining = scanLimitOffset - offset
            val toRead = minOf(STEP_CHUNK_BYTES.toLong(), remaining).toInt()
            val chunk = file.read(offset, toRead)
            if (chunk.isEmpty()) {
                reachedEnd = true
                break
            }

            val combined: ByteArray
            val baseOffset: Long
            if (tail.isEmpty()) {
                combined = chunk
                baseOffset = offset
            } else {
                combined = ByteArray(tail.size + chunk.size)
                System.arraycopy(tail, 0, combined, 0, tail.size)
                System.arraycopy(chunk, 0, combined, tail.size, chunk.size)
                baseOffset = tailStart
            }

            var prevInChunkTs = loTsMs
            MkvEbml.forEachCluster(
                buf = combined,
                bufLen = combined.size,
                baseOffset = baseOffset,
                startPos = 0,
                timestampScaleNs = timestampScaleNs,
                maxPlausibleTsMs = maxPlausibleTsMs,
            ) { tsMs, byteOff ->
                // Reject any stray match that breaks monotonicity with known bounds
                if (tsMs < prevInChunkTs) return@forEachCluster
                if (hiTsMs != null && tsMs > hiTsMs) return@forEachCluster
                prevInChunkTs = tsMs
                val entry = ClusterIndexEntry(timestampMs = tsMs, byteOffset = byteOff)
                index.recordCluster(tsMs, byteOff)
                if (tsMs <= targetMs) {
                    if (bestBefore == null || tsMs > bestBefore.timestampMs) {
                        bestBefore = entry
                    }
                } else if (firstAfter == null || tsMs < firstAfter.timestampMs) {
                    firstAfter = entry
                }
            }

            offset += chunk.size
            if (bestBefore != null || firstAfter != null) {
                return ProbeResult(
                    bestAtOrBefore = bestBefore,
                    firstAfter = firstAfter,
                    scannedToEnd = offset >= maxEndOffset,
                    nextOffset = offset,
                )
            }

            val keep = minOf(MkvEbml.OVERLAP_BYTES, combined.size)
            tail = combined.copyOfRange(combined.size - keep, combined.size)
            tailStart = baseOffset + (combined.size - keep)
        }

        return ProbeResult(
            bestAtOrBefore = null,
            firstAfter = null,
            scannedToEnd = reachedEnd || offset >= maxEndOffset,
            nextOffset = offset,
        )
    }
}
