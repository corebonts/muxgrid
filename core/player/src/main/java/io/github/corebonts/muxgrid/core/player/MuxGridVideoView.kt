package io.github.corebonts.muxgrid.core.player

import android.content.Context
import android.view.SurfaceHolder
import android.view.SurfaceView

/**
 * Plain SurfaceView adapter wired to [PlaybackEngine] without exposing `libmpv` types to UI modules.
 */
class MuxGridVideoView(
    context: Context,
    private val playbackEngine: PlaybackEngine,
) : SurfaceView(context), SurfaceHolder.Callback {

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        holder.addCallback(this)
    }

    override fun surfaceCreated(holder: SurfaceHolder) {
        val frame = holder.surfaceFrame
        playbackEngine.attachSurface(holder.surface, frame.width(), frame.height())
    }

    override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
        playbackEngine.updateSurfaceSize(width, height)
    }

    override fun surfaceDestroyed(holder: SurfaceHolder) {
        playbackEngine.detachSurface()
    }

    override fun onDetachedFromWindow() {
        holder.removeCallback(this)
        super.onDetachedFromWindow()
    }
}
