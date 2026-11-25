package io.github.corebonts.muxgrid.core.designsystem

import android.app.ActivityManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.StatFs
import io.github.corebonts.muxgrid.core.model.AppBuildInfo
import io.github.corebonts.muxgrid.core.model.BackgroundPlaybackMode
import io.github.corebonts.muxgrid.core.model.ConnectionState
import io.github.corebonts.muxgrid.core.model.HwDecPreset
import io.github.corebonts.muxgrid.core.model.ServerDiagnosticsReport
import io.github.corebonts.muxgrid.core.model.ThemeMode
import io.github.corebonts.muxgrid.core.model.TimeshiftWindow
import java.io.File
import java.nio.file.Files
import java.util.Locale

data class DebugEnvironmentSnapshot(
    val appVersion: String,
    val buildSha: String,
    val buildType: String,
    val formFactor: String,
    val manufacturer: String,
    val model: String,
    val hardware: String,
    val board: String,
    val socModel: String?,
    val androidRelease: String,
    val sdkInt: Int,
    val abis: List<String>,
    val totalRamMb: Long?,
    val displayWidthPx: Int,
    val displayHeightPx: Int,
    val densityDpi: Int,
    val cacheUsedBytes: Long?,
    val piconCacheBytes: Long?,
    val timeshiftCacheBytes: Long?,
    val freeStorageBytes: Long?,
    val totalStorageBytes: Long?,
    val decoderPreset: HwDecPreset,
    val timeshiftWindow: TimeshiftWindow,
    val themeMode: ThemeMode,
    val backgroundPlaybackMode: BackgroundPlaybackMode?,
    val pipStatus: String,
    val serverSoftwareSummary: String,
    val serverDiagnosticsSummary: String? = null,
)

fun collectDebugEnvironmentSnapshot(
    context: Context,
    appInfo: AppBuildInfo,
    isTv: Boolean,
    connectionState: ConnectionState,
    hwDecPreset: HwDecPreset,
    timeshiftWindow: TimeshiftWindow,
    themeMode: ThemeMode,
    backgroundPlaybackMode: BackgroundPlaybackMode? = null,
    serverDiagnostics: ServerDiagnosticsReport? = null,
): DebugEnvironmentSnapshot {
    val isDebuggable = context.isDebuggableBuild()
    val resolvedBuildType = appInfo.buildType.trim().ifEmpty {
        if (isDebuggable) "debug" else "release"
    }
    val totalRamMb = runCatching {
        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
        val info = ActivityManager.MemoryInfo()
        if (am != null) {
            am.getMemoryInfo(info)
            (info.totalMem / (1024L * 1024L)).takeIf { it > 0L }
        } else {
            null
        }
    }.getOrNull()

    val cacheDir = context.cacheDir
    val cacheSizes = measureCacheDirectorySizes(cacheDir)
    val piconCacheBytes = cacheSizes.piconCacheBytes
    val timeshiftCacheBytes = cacheSizes.timeshiftCacheBytes
    val cacheUsedBytes = cacheSizes.totalCacheBytes
    val storageStats = runCatching {
        val target = cacheDir.apply { mkdirs() }
        val stat = StatFs(target.absolutePath)
        stat.availableBytes to stat.totalBytes
    }.getOrNull()

    val dm = context.resources.displayMetrics
    val socModel = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        Build.SOC_MODEL.takeIf { it.isNotBlank() && !it.equals("unknown", ignoreCase = true) }
    } else {
        null
    }

    val pipStatus = if (isTv) {
        "N/A (TV)"
    } else if (context.packageManager.hasSystemFeature(PackageManager.FEATURE_PICTURE_IN_PICTURE)) {
        "Supported"
    } else {
        "Unsupported"
    }

    val serverSummary = when (connectionState) {
        is ConnectionState.Connected ->
            "Tvheadend ${connectionState.serverVersion} (HTSP v${connectionState.htspVersion})"
        is ConnectionState.Syncing ->
            "Syncing (${connectionState.serverVersion}, HTSP v${connectionState.htspVersion})"
        ConnectionState.Connecting -> "Connecting"
        ConnectionState.Disconnected -> "Disconnected"
        is ConnectionState.Error -> "Error"
    }

    return DebugEnvironmentSnapshot(
        appVersion = appInfo.versionDisplay,
        buildSha = appInfo.shortSha,
        buildType = resolvedBuildType,
        formFactor = if (isTv) "Android TV" else "Phone / Tablet",
        manufacturer = Build.MANUFACTURER.orEmpty(),
        model = Build.MODEL.orEmpty(),
        hardware = Build.HARDWARE.orEmpty(),
        board = Build.BOARD.orEmpty(),
        socModel = socModel,
        androidRelease = Build.VERSION.RELEASE.orEmpty(),
        sdkInt = Build.VERSION.SDK_INT,
        abis = Build.SUPPORTED_ABIS?.toList().orEmpty(),
        totalRamMb = totalRamMb,
        displayWidthPx = dm.widthPixels,
        displayHeightPx = dm.heightPixels,
        densityDpi = dm.densityDpi,
        cacheUsedBytes = cacheUsedBytes,
        piconCacheBytes = piconCacheBytes,
        timeshiftCacheBytes = timeshiftCacheBytes,
        freeStorageBytes = storageStats?.first,
        totalStorageBytes = storageStats?.second,
        decoderPreset = hwDecPreset,
        timeshiftWindow = timeshiftWindow,
        themeMode = themeMode,
        backgroundPlaybackMode = backgroundPlaybackMode,
        pipStatus = pipStatus,
        serverSoftwareSummary = serverSummary,
        serverDiagnosticsSummary = serverDiagnostics?.debugSummaryLine,
    )
}

internal data class CacheDirectorySizes(
    val piconCacheBytes: Long?,
    val timeshiftCacheBytes: Long?,
    val totalCacheBytes: Long?,
)

internal fun measureCacheDirectorySizes(
    cacheDir: File,
): CacheDirectorySizes {
    val piconCacheBytes = runCatching { fileOrDirectorySizeBytes(File(cacheDir, "picon_cache")) }.getOrNull()
    val timeshiftCacheBytes = runCatching { fileOrDirectorySizeBytes(File(cacheDir, "mpv-cache")) }.getOrNull()
    val otherCacheBytes = runCatching {
        if (!cacheDir.exists() || Files.isSymbolicLink(cacheDir.toPath()) || !cacheDir.isDirectory) {
            0L
        } else {
            var other = 0L
            val children = cacheDir.listFiles().orEmpty()
            for (child in children) {
                if (Files.isSymbolicLink(child.toPath())) continue
                if (child.name == "picon_cache" || child.name == "mpv-cache") continue
                other += fileOrDirectorySizeBytes(child)
            }
            other
        }
    }.getOrNull()

    val totalCacheBytes = if (piconCacheBytes != null || timeshiftCacheBytes != null || otherCacheBytes != null) {
        (piconCacheBytes ?: 0L) + (timeshiftCacheBytes ?: 0L) + (otherCacheBytes ?: 0L)
    } else {
        null
    }
    return CacheDirectorySizes(
        piconCacheBytes = piconCacheBytes,
        timeshiftCacheBytes = timeshiftCacheBytes,
        totalCacheBytes = totalCacheBytes,
    )
}

internal fun fileOrDirectorySizeBytes(root: File): Long {
    if (!root.exists() || Files.isSymbolicLink(root.toPath())) return 0L
    if (root.isFile) return root.length().coerceAtLeast(0L)
    var total = 0L
    val stack = ArrayDeque<File>()
    stack.addLast(root)
    while (stack.isNotEmpty()) {
        val current = stack.removeLast()
        val children = current.listFiles() ?: continue
        for (child in children) {
            if (Files.isSymbolicLink(child.toPath())) continue
            if (child.isDirectory) {
                stack.addLast(child)
            } else if (child.isFile) {
                total += child.length().coerceAtLeast(0L)
            }
        }
    }
    return total
}

fun formatDebugInfoReport(snapshot: DebugEnvironmentSnapshot): String {
    val deviceName = buildString {
        val mfr = snapshot.manufacturer.trim()
        val mdl = snapshot.model.trim()
        when {
            mfr.isEmpty() -> append(mdl.ifEmpty { "Unknown" })
            mdl.isEmpty() -> append(mfr)
            mdl.startsWith(mfr, ignoreCase = true) -> append(mdl)
            else -> append("$mfr $mdl")
        }
    }
    val hwLine = buildString {
        append(snapshot.hardware.ifBlank { "unknown" })
        if (snapshot.board.isNotBlank() && !snapshot.board.equals(snapshot.hardware, ignoreCase = true)) {
            append(" / ").append(snapshot.board)
        }
        if (!snapshot.socModel.isNullOrBlank()) {
            append(" (").append(snapshot.socModel).append(")")
        }
    }
    val ramLine = snapshot.totalRamMb?.let { mb ->
        val gb = mb / 1024.0
        String.format(Locale.US, "%.1f GB (%d MB)", gb, mb)
    } ?: "Unknown"

    val cacheLine = snapshot.cacheUsedBytes?.let { total ->
        val parts = listOfNotNull(
            snapshot.piconCacheBytes?.let { "Picons ${formatByteCount(it)}" },
            snapshot.timeshiftCacheBytes?.let { "Timeshift ${formatByteCount(it)}" },
        )
        if (parts.isNotEmpty()) {
            "${formatByteCount(total)} (${parts.joinToString(", ")})"
        } else {
            formatByteCount(total)
        }
    } ?: "Unknown"

    val storageLine = when {
        snapshot.freeStorageBytes != null && snapshot.totalStorageBytes != null && snapshot.totalStorageBytes > 0L ->
            "${formatByteCount(snapshot.freeStorageBytes)} / ${formatByteCount(snapshot.totalStorageBytes)}"
        snapshot.freeStorageBytes != null -> formatByteCount(snapshot.freeStorageBytes)
        else -> "Unknown"
    }

    return buildString {
        appendLine("MuxGrid ${snapshot.appVersion} (${snapshot.buildSha}, ${snapshot.buildType})")
        appendLine("Form Factor: ${snapshot.formFactor}")
        appendLine("Device: $deviceName")
        appendLine("Hardware: $hwLine")
        appendLine("Android: ${snapshot.androidRelease.ifBlank { "unknown" }} (API ${snapshot.sdkInt})")
        appendLine("ABIs: ${snapshot.abis.joinToString(", ").ifBlank { "unknown" }}")
        appendLine("RAM: $ramLine")
        appendLine("Display: ${snapshot.displayWidthPx}x${snapshot.displayHeightPx} (${snapshot.densityDpi} dpi)")
        appendLine("Cache Used: $cacheLine")
        appendLine("Storage Free: $storageLine")
        appendLine("Decoder: ${snapshot.decoderPreset.name}")
        appendLine("Timeshift: ${snapshot.timeshiftWindow.shortLabel}")
        appendLine("Theme: ${snapshot.themeMode.name}")
        snapshot.backgroundPlaybackMode?.let { mode ->
            appendLine("Background Playback: ${mode.label}")
        }
        appendLine("PiP: ${snapshot.pipStatus}")
        val diagLine = snapshot.serverDiagnosticsSummary
        if (!diagLine.isNullOrBlank()) {
            appendLine("Server: ${snapshot.serverSoftwareSummary}")
            append("Server Health: $diagLine")
        } else {
            append("Server: ${snapshot.serverSoftwareSummary}")
        }
    }
}
