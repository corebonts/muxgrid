package io.github.corebonts.muxgrid.core.designsystem

import io.github.corebonts.muxgrid.core.model.HwDecPreset
import io.github.corebonts.muxgrid.core.model.ThemeMode
import io.github.corebonts.muxgrid.core.model.TimeshiftWindow
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.nio.file.Path

class DebugInfoReportTest {

    @Test
    fun `formatDebugInfoReport renders non-personal hardware, storage, and software environment`() {
        val snapshot = DebugEnvironmentSnapshot(
            appVersion = "0.6.0 (4)",
            buildSha = "889d6c2",
            buildType = "release",
            formFactor = "Android TV",
            manufacturer = "Acme",
            model = "Acme SmartTV 4K",
            hardware = "amlogic",
            board = "p212",
            socModel = "S905X",
            androidRelease = "11",
            sdkInt = 30,
            abis = listOf("armeabi-v7a", "armeabi"),
            totalRamMb = 2048L,
            displayWidthPx = 1920,
            displayHeightPx = 1080,
            densityDpi = 320,
            cacheUsedBytes = 12L * 1024L * 1024L + 512L * 1024L,
            piconCacheBytes = 4L * 1024L * 1024L,
            timeshiftCacheBytes = 8L * 1024L * 1024L,
            epgCacheBytes = 512L * 1024L,
            freeStorageBytes = 3L * 1024L * 1024L * 1024L + 220L * 1024L * 1024L,
            totalStorageBytes = 8L * 1024L * 1024L * 1024L,
            decoderPreset = HwDecPreset.MEDIACODEC_ZERO_COPY,
            timeshiftWindow = TimeshiftWindow.MB_512,
            themeMode = ThemeMode.DARK,
            backgroundPlaybackMode = null,
            pipStatus = "N/A (TV)",
            serverSoftwareSummary = "Tvheadend 4.3-2375 (HTSP v34)",
        )

        val report = formatDebugInfoReport(snapshot)
        assertTrue(report.contains("MuxGrid 0.6.0 (4) (889d6c2, release)"))
        assertTrue(report.contains("Device: Acme SmartTV 4K"))
        assertFalse(report.contains("Acme Acme"))
        assertTrue(report.contains("Hardware: amlogic / p212 (S905X)"))
        assertTrue(report.contains("Android: 11 (API 30)"))
        assertTrue(report.contains("ABIs: armeabi-v7a, armeabi"))
        assertTrue(report.contains("RAM: 2.0 GB (2048 MB)"))
        assertTrue(report.contains("Display: 1920x1080 (320 dpi)"))
        assertTrue(report.contains("Cache Used: 12.5 MB (Picons 4.0 MB, Timeshift 8.0 MB, EPG 512 KB)"))
        assertTrue(report.contains("Storage Free: 3.2 GB / 8.0 GB"))
        assertTrue(report.contains("Decoder: MEDIACODEC_ZERO_COPY"))
        assertTrue(report.contains("Server: Tvheadend 4.3-2375 (HTSP v34)"))
        assertFalse(report.contains("Background Playback:"))
    }

    @Test
    fun `fileOrDirectorySizeBytes sums nested files and ignores missing paths`(@TempDir tempDir: Path) {
        val root = tempDir.toFile()
        assertEquals(0L, fileOrDirectorySizeBytes(File(root, "missing")))

        val nested = File(root, "picon_cache/sub").apply { mkdirs() }
        File(root, "a.bin").writeBytes(ByteArray(120))
        File(nested, "b.bin").writeBytes(ByteArray(380))

        assertEquals(500L, fileOrDirectorySizeBytes(root))
        assertEquals(380L, fileOrDirectorySizeBytes(File(nested, "b.bin")))
    }

    @Test
    fun `measureCacheDirectorySizes sums subcaches and epg file without double counting`(@TempDir tempDir: Path) {
        val cacheDir = File(tempDir.toFile(), "cache").apply { mkdirs() }
        val filesDir = File(tempDir.toFile(), "files").apply { mkdirs() }
        File(cacheDir, "picon_cache").apply { mkdirs() }.let { File(it, "1.png").writeBytes(ByteArray(100)) }
        File(cacheDir, "mpv-cache").apply { mkdirs() }.let { File(it, "buf.bin").writeBytes(ByteArray(250)) }
        File(cacheDir, "misc.tmp").writeBytes(ByteArray(50))
        val epgFile = File(filesDir, "epg_cache.dat").apply { writeBytes(ByteArray(300)) }

        val breakdown = measureCacheDirectorySizes(cacheDir, epgFile)
        assertEquals(100L, breakdown.piconCacheBytes)
        assertEquals(250L, breakdown.timeshiftCacheBytes)
        assertEquals(300L, breakdown.epgCacheBytes)
        assertEquals(700L, breakdown.totalCacheBytes)
    }
}
