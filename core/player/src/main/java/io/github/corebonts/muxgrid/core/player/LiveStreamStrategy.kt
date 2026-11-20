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
        seekable: Boolean,
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
        seekable: Boolean,
        cacheStartMs: Long,
        cacheEndMs: Long,
        timeshiftEnabled: Boolean,
    ): LiveTimeshiftMetrics {
        val start = cacheStartMs.coerceAtLeast(0L)
        val end = cacheEndMs.coerceAtLeast(start)
        val window = (end - start).coerceAtLeast(0L)
        val offset = (end - positionMs.coerceAtLeast(0L)).coerceAtLeast(0L)
        val isTimeshiftSeekable = timeshiftEnabled && seekable && window >= 2_000L
        return LiveTimeshiftMetrics(
            bufferStartMs = start,
            bufferEndMs = end,
            bufferWindowMs = window,
            liveEdgeOffsetMs = offset,
            isTimeshiftSeekable = isTimeshiftSeekable,
        )
    }
}
