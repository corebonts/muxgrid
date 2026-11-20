package io.github.corebonts.muxgrid.core.player

internal data class LiveTimeshiftMetrics(
    val bufferStartMs: Long = 0L,
    val bufferEndMs: Long = 0L,
    val bufferWindowMs: Long = 0L,
    val liveEdgeOffsetMs: Long = 0L,
    val isTimeshiftSeekable: Boolean = false,
)

/**
 * Internal seam isolating live stream transport resolution, teardown, and timeshift metrics.
 * Implemented in v1 by [HttpPassStrategy] (LocalMediaProxy + HTTP MPEG-TS `pass` profile + libmpv cache).
 */
internal interface LiveStreamStrategy {
    fun resolveLiveUrl(channelId: Long): String
    fun stopActive()
    fun computeTimeshiftMetrics(
        positionMs: Long,
        cacheStartMs: Long,
        cacheEndMs: Long,
        timeshiftEnabled: Boolean,
    ): LiveTimeshiftMetrics
}

internal class HttpPassStrategy(
    private val proxy: LocalMediaProxy,
) : LiveStreamStrategy {
    override fun resolveLiveUrl(channelId: Long): String = proxy.liveStreamUrl(channelId)

    override fun stopActive() {
        proxy.stopActiveStream()
    }

    override fun computeTimeshiftMetrics(
        positionMs: Long,
        cacheStartMs: Long,
        cacheEndMs: Long,
        timeshiftEnabled: Boolean,
    ): LiveTimeshiftMetrics {
        val start = cacheStartMs.coerceAtLeast(0L)
        val end = cacheEndMs.coerceAtLeast(start)
        val window = (end - start).coerceAtLeast(0L)
        val offset = (end - positionMs.coerceAtLeast(0L)).coerceAtLeast(0L)
        val isTimeshiftSeekable = timeshiftEnabled && window >= 2_000L
        return LiveTimeshiftMetrics(
            bufferStartMs = start,
            bufferEndMs = end,
            bufferWindowMs = window,
            liveEdgeOffsetMs = offset,
            isTimeshiftSeekable = isTimeshiftSeekable,
        )
    }
}

/**
 * Smooths bursty MPEG-TS `demuxer-cache-state` updates by extrapolating the live broadcast edge
 * at 1x real-time between packet/GOP bursts, preventing 0.5-1s progress-bar and label jitter.
 */
internal class TimeshiftRangeSmoother {
    var isInitialized: Boolean = false
        private set
    private var smoothedStartMs: Long = 0L
    private var smoothedEndMs: Long = 0L
    private var lastSampleRealtimeMs: Long = 0L

    fun reset() {
        isInitialized = false
        smoothedStartMs = 0L
        smoothedEndMs = 0L
        lastSampleRealtimeMs = 0L
    }

    fun update(rawStartMs: Long, rawEndMs: Long, nowRealtimeMs: Long): Pair<Long, Long> {
        val clampedStart = rawStartMs.coerceAtLeast(0L)
        val clampedEnd = rawEndMs.coerceAtLeast(clampedStart)
        if (!isInitialized || clampedEnd + RESET_DROP_THRESHOLD_MS < smoothedEndMs) {
            isInitialized = true
            smoothedStartMs = clampedStart
            smoothedEndMs = clampedEnd
            lastSampleRealtimeMs = nowRealtimeMs
            return smoothedStartMs to smoothedEndMs
        }

        val dt = (nowRealtimeMs - lastSampleRealtimeMs).coerceIn(0L, MAX_EXTRAPOLATION_STEP_MS)
        val predictedEnd = smoothedEndMs + dt
        smoothedEndMs = when {
            clampedEnd - predictedEnd > SNAP_FORWARD_THRESHOLD_MS -> clampedEnd
            clampedEnd > predictedEnd -> predictedEnd + ((clampedEnd - predictedEnd) * 0.45).toLong()
            predictedEnd - clampedEnd <= NORMAL_BURST_GAP_MS -> predictedEnd
            else -> {
                val excess = (predictedEnd - clampedEnd) - NORMAL_BURST_GAP_MS
                (predictedEnd - (excess * 0.25).toLong()).coerceAtLeast(clampedEnd)
            }
        }

        smoothedStartMs = if (kotlin.math.abs(clampedStart - smoothedStartMs) > SNAP_START_THRESHOLD_MS) {
            clampedStart
        } else {
            smoothedStartMs + ((clampedStart - smoothedStartMs) * 0.35).toLong()
        }.coerceIn(0L, smoothedEndMs)

        lastSampleRealtimeMs = nowRealtimeMs
        return smoothedStartMs to smoothedEndMs
    }

    fun currentEstimatedEndMs(nowRealtimeMs: Long): Long {
        if (!isInitialized) return 0L
        val dt = (nowRealtimeMs - lastSampleRealtimeMs).coerceIn(0L, MAX_EXTRAPOLATION_STEP_MS)
        return smoothedEndMs + dt
    }

    companion object {
        private const val MAX_EXTRAPOLATION_STEP_MS = 2_000L
        private const val NORMAL_BURST_GAP_MS = 1_500L
        private const val SNAP_FORWARD_THRESHOLD_MS = 2_500L
        private const val SNAP_START_THRESHOLD_MS = 3_000L
        private const val RESET_DROP_THRESHOLD_MS = 4_000L
    }
}
