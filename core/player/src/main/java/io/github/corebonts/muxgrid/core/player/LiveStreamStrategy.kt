package io.github.corebonts.muxgrid.core.player

/**
 * Internal seam isolating live stream transport resolution and teardown.
 * Implemented in v1 by [HttpPassStrategy] (LocalMediaProxy + HTTP MPEG-TS `pass` profile).
 */
internal interface LiveStreamStrategy {
    fun resolveLiveUrl(channelId: Long): String
    fun stopActive()
}

internal class HttpPassStrategy(
    private val proxy: LocalMediaProxy,
) : LiveStreamStrategy {
    override fun resolveLiveUrl(channelId: Long): String = proxy.liveStreamUrl(channelId)

    override fun stopActive() {
        proxy.stopActiveStream()
    }
}
