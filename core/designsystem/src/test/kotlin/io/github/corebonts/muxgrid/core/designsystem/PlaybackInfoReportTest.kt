package io.github.corebonts.muxgrid.core.designsystem

import io.github.corebonts.muxgrid.core.model.HwDecPreset
import io.github.corebonts.muxgrid.core.model.MediaTrack
import io.github.corebonts.muxgrid.core.model.PlaybackDiagnostics
import io.github.corebonts.muxgrid.core.model.PlaybackState
import io.github.corebonts.muxgrid.core.model.PlaybackStatus
import io.github.corebonts.muxgrid.core.model.TrackType
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class PlaybackInfoReportTest {

    @Test
    fun `formatBitrateBps formats bps kbps and Mbps cleanly`() {
        assertEquals("850 bps", formatBitrateBps(850L))
        assertEquals("384 kbps", formatBitrateBps(384_000L))
        assertEquals("6.50 Mbps", formatBitrateBps(6_500_000L))
    }

    @Test
    fun `formatPlaybackInfoReport includes stream decoder network cache and usage sections`() {
        val state = PlaybackState(
            status = PlaybackStatus.PLAYING,
            channelId = 7L,
            channelNumber = "1",
            channelName = "Channel One",
            audioTracks = listOf(
                MediaTrack(id = 1, type = TrackType.AUDIO, language = "eng", codec = "eac3", isSelected = true),
            ),
            selectedAudioId = 1,
            videoWidth = 1920,
            videoHeight = 1080,
            hwdecCurrent = "mediacodec",
            deinterlaceActive = false,
        )
        val diag = PlaybackDiagnostics(
            transportLabel = "HTTP MPEG-TS (pass)",
            containerFormat = "mpegts",
            upstreamHost = "tv.example.local:9981",
            upstreamStatus = "HTTP 200",
            sessionUptimeMs = 125_000L,
            videoCodec = "h264",
            videoWidth = 1920,
            videoHeight = 1080,
            pixelFormat = "mediacodec",
            hwPixelFormat = "nv12",
            colorInfo = "bt.709",
            containerFps = 50.0,
            actualFps = 49.98,
            videoBitrateBps = 5_200_000L,
            hwdecCurrent = "mediacodec",
            videoOutput = "gpu",
            droppedFramesVo = 1L,
            droppedFramesDecoder = 0L,
            avSyncMs = -2.4,
            audioCodec = "eac3",
            audioChannels = "5.1",
            audioSampleRateHz = 48_000,
            audioBitrateBps = 384_000L,
            audioOutput = "audiotrack",
            networkRateBps = 5_800_000L,
            demuxerInputRateBps = 5_600_000L,
            totalBytesReceived = 88L * 1024L * 1024L,
            cacheUsedBytes = 32L * 1024L * 1024L,
            cacheForwardBytes = 20L * 1024L * 1024L,
            cacheBackBytes = 12L * 1024L * 1024L,
            cacheDurationSec = 42.0,
            cacheBudgetPerDirBytes = 512L * 1024L * 1024L,
            cacheOnDisk = true,
            diskCacheBytes = 34L * 1024L * 1024L,
            processCpuPercent = 12.5,
            cpuCores = 8,
            processPssMb = 164.0,
            nativePssMb = 92.0,
            jvmHeapUsedMb = 28.4,
            jvmHeapMaxMb = 256.0,
        )

        val report = formatPlaybackInfoReport(state, diag, HwDecPreset.MEDIACODEC_ZERO_COPY)

        assertTrue(report.contains("MuxGrid Playback Info — 1 · Channel One"))
        assertTrue(report.contains("Session Uptime: 02:05"))
        assertTrue(report.contains("Transport: HTTP MPEG-TS (pass)"))
        assertTrue(report.contains("Upstream: tv.example.local:9981 (HTTP 200)"))
        assertTrue(report.contains("Codec: H264"))
        assertTrue(report.contains("Resolution: 1920x1080"))
        assertTrue(report.contains("Dropped Frames: 1 VO · 0 dec"))
        assertTrue(report.contains("Network Rate: 5.80 Mbps"))
        assertTrue(report.contains("Data Received: 88.0 MB"))
        assertTrue(report.contains("Disk Cache: 34.0 MB / 1.0 GB"))
        assertTrue(report.contains("RAM Hot Cache: 32.0 MB"))
        assertTrue(report.contains("RAM Ahead / Rewind: 20.0 MB / 12.0 MB"))
        assertTrue(report.contains("App CPU: 12.5% (8 cores)"))
        assertTrue(report.contains("Process Memory: 164 MB PSS (native 92 MB)"))
    }

    @Test
    fun `formatPlaybackInfoReport distinguishes ahead time from ring buffer window and handles RAM cache mode`() {
        val state = PlaybackState(
            status = PlaybackStatus.PAUSED,
            channelId = 4L,
            channelNumber = "4",
            channelName = "M4 Sport HD",
            bufferWindowMs = 62_000L,
        )
        val diag = PlaybackDiagnostics(
            cacheOnDisk = true,
            cacheUsedBytes = 1_500_000L,
            cacheBudgetPerDirBytes = 2L * 1024L * 1024L * 1024L,
            diskCacheBytes = 0L,
            cacheDurationSec = 22.0,
        )
        val diskReport = formatPlaybackInfoReport(state, diag, HwDecPreset.MEDIACODEC_ZERO_COPY)
        assertTrue(diskReport.contains("Disk Cache: Active (budget 4.0 GB)"))
        assertTrue(diskReport.contains("Buffered Time: 00:22 ahead · 01:02 window"))

        val ramReport = formatPlaybackInfoReport(
            playbackState = state.copy(bufferWindowMs = 0L),
            diagnostics = diag.copy(
                cacheOnDisk = false,
                cacheUsedBytes = 32L * 1024L * 1024L,
                cacheForwardBytes = 20L * 1024L * 1024L,
                cacheBackBytes = 12L * 1024L * 1024L,
                cacheBudgetPerDirBytes = 64L * 1024L * 1024L,
                cacheDurationSec = 42.0,
            ),
            hwDecPreset = HwDecPreset.MEDIACODEC_ZERO_COPY,
        )
        assertTrue(ramReport.contains("Cache Used: 32.0 MB / 128.0 MB"))
        assertTrue(ramReport.contains("Ahead / Rewind: 20.0 MB / 12.0 MB"))
        assertTrue(ramReport.contains("Buffered Time: 00:42"))
    }
}
