package io.github.corebonts.muxgrid

import android.content.Context
import android.util.AttributeSet
import io.github.corebonts.muxgrid.core.model.HwDecPreset
import `is`.xyz.mpv.BaseMPVView

data class SpikeStats(
    val hwdecCurrent: String = "-",
    val resolution: String = "-",
    val fps: String = "-",
    val droppedFrames: String = "0",
    val aid: String = "-",
    val sid: String = "-",
    val deinterlace: String = "-",
)

class SpikeMpvView(
    context: Context,
    attrs: AttributeSet? = null,
) : BaseMPVView(context, attrs) {

    private var preset: HwDecPreset = HwDecPreset.MEDIACODEC_ZERO_COPY

    override fun initOptions() {
        mpv.setOptionString("profile", "fast")
        mpv.setOptionString("vo", "gpu")
        mpv.setOptionString("gpu-context", "android")
        mpv.setOptionString("opengl-es", "yes")
        mpv.setOptionString("hwdec", "mediacodec")
        mpv.setOptionString("hwdec-codecs", "h264,hevc,mpeg4,mpeg2video,vp8,vp9,av1")
        mpv.setOptionString("deinterlace", "no")
        mpv.setOptionString("demuxer-lavf-o", "analyzeduration=500000,probesize=524288")
        mpv.setOptionString("cache", "yes")
        mpv.setOptionString("demuxer-max-bytes", "64MiB")
        mpv.setOptionString("demuxer-max-back-bytes", "64MiB")
        mpv.setOptionString("sub-visibility", "yes")
    }

    fun loadUrl(url: String) {
        if (mpv.isInitialized) {
            mpv.command("stop")
        }
        playFile(url)
    }

    fun stopPlayback() {
        if (mpv.isInitialized) {
            mpv.command("stop")
        }
    }

    fun applyPreset(newPreset: HwDecPreset) {
        preset = newPreset
        if (!mpv.isInitialized) return
        when (newPreset) {
            HwDecPreset.MEDIACODEC_ZERO_COPY -> {
                mpv.setPropertyString("deinterlace", "no")
                mpv.setPropertyString("hwdec", "mediacodec")
            }
            HwDecPreset.MEDIACODEC_COPY_BWDIF -> {
                mpv.setPropertyString("hwdec", "mediacodec-copy")
                mpv.setPropertyString("deinterlace", "yes")
            }
        }
    }

    fun cycleAudio() {
        if (mpv.isInitialized) mpv.command("cycle", "aid")
    }

    fun cycleSubtitle() {
        if (mpv.isInitialized) mpv.command("cycle", "sid")
    }

    fun seekRelative(seconds: Int) {
        if (mpv.isInitialized) mpv.command("seek", seconds.toString(), "relative")
    }

    fun readStats(): SpikeStats {
        if (!mpv.isInitialized) return SpikeStats()
        val w = mpv.getPropertyInt("video-params/w") ?: 0
        val h = mpv.getPropertyInt("video-params/h") ?: 0
        val fps = mpv.getPropertyDouble("estimated-vf-fps")?.let { "%.1f".format(it) } ?: "-"
        return SpikeStats(
            hwdecCurrent = mpv.getPropertyString("hwdec-current") ?: "-",
            resolution = if (w > 0 && h > 0) "${w}x${h}" else "-",
            fps = fps,
            droppedFrames = (mpv.getPropertyInt("frame-drop-count") ?: 0).toString(),
            aid = mpv.getPropertyString("aid") ?: "-",
            sid = mpv.getPropertyString("sid") ?: "-",
            deinterlace = mpv.getPropertyString("deinterlace") ?: "-",
        )
    }
}
