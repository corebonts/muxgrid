package io.github.corebonts.muxgrid.core.player

import android.view.Surface
import io.github.corebonts.muxgrid.core.model.Channel
import io.github.corebonts.muxgrid.core.model.HwDecPreset
import io.github.corebonts.muxgrid.core.model.PlaybackState
import kotlinx.coroutines.flow.StateFlow

interface PlaybackEngine {
    val state: StateFlow<PlaybackState>

    fun playChannel(channel: Channel, preset: HwDecPreset)
    fun stop()
    fun togglePause()
    fun setPaused(paused: Boolean)
    fun setAudioTrack(trackId: Int)
    fun setSubtitleTrack(trackId: Int?)
    fun applyPreset(preset: HwDecPreset)

    fun attachSurface(surface: Surface, width: Int, height: Int)
    fun updateSurfaceSize(width: Int, height: Int)
    fun detachSurface()
    fun destroy()
}
