package io.github.corebonts.muxgrid.core.data

import io.github.corebonts.muxgrid.core.htsp.HtspCapabilityProbeResult
import io.github.corebonts.muxgrid.core.htsp.HtspClient
import io.github.corebonts.muxgrid.core.htsp.ProbeOutcome
import io.github.corebonts.muxgrid.core.model.DiagnosticCheckId
import io.github.corebonts.muxgrid.core.model.DiagnosticItem
import io.github.corebonts.muxgrid.core.model.DiagnosticSeverity
import io.github.corebonts.muxgrid.core.model.PortProbeResult
import io.github.corebonts.muxgrid.core.model.ServerDiagnosticsReport
import io.github.corebonts.muxgrid.core.model.ServerProfile
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.Locale
import java.util.concurrent.TimeUnit
import kotlin.math.abs

internal data class HttpProbeResult(
    val reachable: Boolean,
    val statusCode: Int? = null,
    val error: String? = null,
)

/**
 * Coordinates fast port probing (Onboarding Step 1 -> Step 2) and full HTSP + HTTP
 * preflight diagnostics (Onboarding Step 3 & Settings Server Health).
 */
internal object ServerDiagnosticsRunner {

    private const val HTTP_PROBE_TIMEOUT_SEC = 3L
    private const val CLOCK_DRIFT_ADVISORY_MS = 30_000L
    private const val LOW_DISK_FREE_BYTES = 5L * 1024L * 1024L * 1024L
    private const val LOW_DISK_FREE_RATIO = 0.05

    private val baseHttpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(HTTP_PROBE_TIMEOUT_SEC, TimeUnit.SECONDS)
            .readTimeout(HTTP_PROBE_TIMEOUT_SEC, TimeUnit.SECONDS)
            .writeTimeout(HTTP_PROBE_TIMEOUT_SEC, TimeUnit.SECONDS)
            .callTimeout(HTTP_PROBE_TIMEOUT_SEC + 1L, TimeUnit.SECONDS)
            .build()
    }

    suspend fun probePorts(
        host: String,
        htspPort: Int,
        httpPort: Int,
    ): PortProbeResult = coroutineScope {
        val cleanHost = host.trim().removePrefix("[").removeSuffix("]")
        val tempProfile = ServerProfile(
            host = cleanHost,
            htspPort = htspPort,
            httpPort = httpPort,
        )
        val htspDeferred = async(Dispatchers.IO) {
            HtspClient.probeHello(cleanHost, htspPort)
        }
        val httpDeferred = async(Dispatchers.IO) {
            probeHttp(tempProfile)
        }
        val htsp = htspDeferred.await()
        val http = httpDeferred.await()
        PortProbeResult(
            host = cleanHost,
            htspPort = htspPort,
            httpPort = httpPort,
            htspReachable = htsp.reachable,
            httpReachable = http.reachable,
            serverName = htsp.serverName,
            serverVersion = htsp.serverVersion,
            htspVersion = htsp.htspVersion,
            htspError = htsp.error,
            httpError = http.error,
        )
    }

    suspend fun runPreflight(profile: ServerProfile): ServerDiagnosticsReport = coroutineScope {
        val htspDeferred = async(Dispatchers.IO) {
            HtspClient.probeServer(profile)
        }
        val httpDeferred = async(Dispatchers.IO) {
            probeHttp(profile)
        }
        buildDiagnosticsReport(
            profile = profile,
            htsp = htspDeferred.await(),
            http = httpDeferred.await(),
        )
    }

    suspend fun runLiveOrPreflight(
        profile: ServerProfile,
        probeActiveHtsp: suspend () -> HtspCapabilityProbeResult?,
    ): ServerDiagnosticsReport = coroutineScope {
        val httpDeferred = async(Dispatchers.IO) {
            probeHttp(profile)
        }
        val htsp = probeActiveHtsp() ?: withContext(Dispatchers.IO) {
            HtspClient.probeServer(profile)
        }
        buildDiagnosticsReport(
            profile = profile,
            htsp = htsp,
            http = httpDeferred.await(),
        )
    }

    private fun probeHttp(profile: ServerProfile): HttpProbeResult {
        val baseUrl = profile.httpBaseUrl ?: return HttpProbeResult(
            reachable = false,
            error = "Invalid HTTP port or host",
        )
        val client = baseHttpClient.newBuilder()
            .authenticator(TvheadendDigestAuthenticator { profile })
            .build()

        val request = Request.Builder()
            .url("$baseUrl/api/serverinfo")
            .get()
            .build()

        return try {
            client.newCall(request).execute().use { response ->
                HttpProbeResult(
                    reachable = true,
                    statusCode = response.code,
                )
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            HttpProbeResult(
                reachable = false,
                error = e.message ?: "Unable to connect to ${profile.formattedHttpHost}:${profile.httpPort}",
            )
        }
    }

    internal fun buildDiagnosticsReport(
        profile: ServerProfile,
        htsp: HtspCapabilityProbeResult,
        http: HttpProbeResult,
        checkedAtEpochMs: Long = System.currentTimeMillis(),
    ): ServerDiagnosticsReport {
        val items = ArrayList<DiagnosticItem>(9)
        val fingerprint = profile.diagnosticsFingerprint

        if (!htsp.connected) {
            items += DiagnosticItem(
                id = DiagnosticCheckId.HTSP_CONNECTION,
                title = "HTSP Connection",
                severity = DiagnosticSeverity.BLOCKER,
                summary = "Cannot reach HTSP port ${profile.htspPort}",
                detail = htsp.connectionError,
                suggestion = "Check the server host/IP, ensure Tvheadend is running, and verify TCP port ${profile.htspPort} is open.",
            )
            items += buildHttpDiagnosticItem(profile, http, hasDvrAccess = false, htspAuthenticated = false)
            return ServerDiagnosticsReport(
                checkedAtEpochMs = checkedAtEpochMs,
                serverKey = profile.serverKey,
                profileFingerprint = fingerprint,
                serverName = profile.name,
                serverVersion = "",
                items = items,
            )
        }

        items += DiagnosticItem(
            id = DiagnosticCheckId.HTSP_CONNECTION,
            title = "HTSP Connection",
            severity = DiagnosticSeverity.OK,
            summary = listOf(htsp.serverName, htsp.serverVersion)
                .filter { it.isNotBlank() }
                .joinToString(" ")
                .ifBlank { "Connected on port ${profile.htspPort}" },
            detail = "Port ${profile.htspPort} reachable" +
                (htsp.htspVersion?.let { " · HTSP v$it" } ?: ""),
        )

        if (!htsp.authSucceeded) {
            items += DiagnosticItem(
                id = DiagnosticCheckId.AUTHENTICATION,
                title = "Authentication",
                severity = DiagnosticSeverity.BLOCKER,
                summary = "Username or password rejected",
                detail = htsp.connectionError,
                suggestion = "Verify credentials and Allowed networks in Tvheadend → Configuration → Users → Access Entries.",
            )
            items += buildHttpDiagnosticItem(profile, http, hasDvrAccess = false, htspAuthenticated = false)
            return ServerDiagnosticsReport(
                checkedAtEpochMs = checkedAtEpochMs,
                serverKey = profile.serverKey,
                profileFingerprint = fingerprint,
                serverName = htsp.serverName,
                serverVersion = htsp.serverVersion,
                htspVersion = htsp.htspVersion,
                serverCapabilities = htsp.serverCapabilities,
                items = items,
            )
        }

        val isAnonymous = profile.username.isBlank() && profile.password.isBlank()
        items += when {
            isAnonymous && htsp.dvrOutcome == ProbeOutcome.NO_ACCESS -> DiagnosticItem(
                id = DiagnosticCheckId.AUTHENTICATION,
                title = "Authentication",
                severity = DiagnosticSeverity.ADVISORY,
                summary = "Connected anonymously (LAN access)",
                detail = "Signed in without credentials.",
                suggestion = "Sign in with a dedicated Tvheadend account if you want DVR recording permissions.",
            )
            isAnonymous -> DiagnosticItem(
                id = DiagnosticCheckId.AUTHENTICATION,
                title = "Authentication",
                severity = DiagnosticSeverity.OK,
                summary = "Anonymous LAN access accepted",
            )
            else -> DiagnosticItem(
                id = DiagnosticCheckId.AUTHENTICATION,
                title = "Authentication",
                severity = DiagnosticSeverity.OK,
                summary = "Signed in as '${profile.username}'",
            )
        }

        val htspVer = htsp.htspVersion
        if (htspVer != null) {
            items += when {
                htspVer < 15 -> DiagnosticItem(
                    id = DiagnosticCheckId.HTSP_PROTOCOL_VERSION,
                    title = "HTSP Version",
                    severity = DiagnosticSeverity.BLOCKER,
                    summary = "HTSP v$htspVer is too old",
                    detail = "MuxGrid requires HTSP v15 or newer (Tvheadend 4.0+).",
                    suggestion = "Upgrade Tvheadend to 4.2.8 or 4.3+.",
                )
                htspVer < 27 -> DiagnosticItem(
                    id = DiagnosticCheckId.HTSP_PROTOCOL_VERSION,
                    title = "HTSP Version",
                    severity = DiagnosticSeverity.DEGRADED,
                    summary = "Legacy HTSP v$htspVer",
                    detail = buildString {
                        if (htspVer < 25) {
                            append("Clean stop-recording (HTSP v25+) and ")
                        }
                        append("cross-device resume sync (HTSP v27+) are unavailable; MuxGrid keeps watch progress locally.")
                    },
                    suggestion = "Upgrade Tvheadend to 4.3+ for full DVR control and resume sync.",
                )
                else -> DiagnosticItem(
                    id = DiagnosticCheckId.HTSP_PROTOCOL_VERSION,
                    title = "HTSP Version",
                    severity = DiagnosticSeverity.OK,
                    summary = "HTSP v$htspVer",
                )
            }
        }

        items += when (htsp.streamingOutcome) {
            ProbeOutcome.GRANTED -> DiagnosticItem(
                id = DiagnosticCheckId.HTSP_STREAMING,
                title = "HTSP Streaming",
                severity = DiagnosticSeverity.OK,
                summary = "Streaming & EPG permission enabled",
            )
            ProbeOutcome.NO_ACCESS -> DiagnosticItem(
                id = DiagnosticCheckId.HTSP_STREAMING,
                title = "HTSP Streaming",
                severity = DiagnosticSeverity.BLOCKER,
                summary = "Missing HTSP Streaming permission",
                detail = "Tvheadend denied HTSP streaming access; channels, EPG, and playback require it.",
                suggestion = "In Tvheadend → Configuration → Users → Access Entries, enable Streaming → HTSP.",
            )
            ProbeOutcome.UNSUPPORTED, ProbeOutcome.ERROR -> DiagnosticItem(
                id = DiagnosticCheckId.HTSP_STREAMING,
                title = "HTSP Streaming",
                severity = DiagnosticSeverity.BLOCKER,
                summary = "HTSP streaming check failed",
                detail = "Server did not complete the HTSP streaming capability check.",
                suggestion = "Check Tvheadend logs and Streaming → HTSP permissions.",
            )
        }

        val hasDvr = htsp.dvrOutcome == ProbeOutcome.GRANTED
        items += buildHttpDiagnosticItem(profile, http, hasDvrAccess = hasDvr)

        items += when (htsp.dvrOutcome) {
            ProbeOutcome.GRANTED -> DiagnosticItem(
                id = DiagnosticCheckId.HTSP_DVR,
                title = "HTSP DVR",
                severity = DiagnosticSeverity.OK,
                summary = "Recording & chase-play enabled",
            )
            ProbeOutcome.NO_ACCESS -> DiagnosticItem(
                id = DiagnosticCheckId.HTSP_DVR,
                title = "HTSP DVR",
                severity = DiagnosticSeverity.DEGRADED,
                summary = "Read-only mode (no HTSP DVR)",
                detail = "Live TV and Guide work normally. Scheduling, deleting, in-progress chase-play, and server resume sync are disabled.",
                suggestion = "Enable Recorder → HTSP DVR in Tvheadend → Configuration → Users → Access Entries.",
            )
            ProbeOutcome.UNSUPPORTED, ProbeOutcome.ERROR -> DiagnosticItem(
                id = DiagnosticCheckId.HTSP_DVR,
                title = "HTSP DVR",
                severity = DiagnosticSeverity.ADVISORY,
                summary = "DVR access could not be verified",
                detail = "This server did not report DVR configuration metadata.",
            )
        }

        if (htsp.passProfilePresent != null) {
            items += if (htsp.passProfilePresent == true) {
                DiagnosticItem(
                    id = DiagnosticCheckId.STREAM_PROFILE_PASS,
                    title = "Stream Profile",
                    severity = DiagnosticSeverity.OK,
                    summary = "'pass' MPEG-TS profile active",
                )
            } else {
                val found = htsp.availableProfiles.joinToString(", ").ifEmpty { "none" }
                DiagnosticItem(
                    id = DiagnosticCheckId.STREAM_PROFILE_PASS,
                    title = "Stream Profile",
                    severity = DiagnosticSeverity.DEGRADED,
                    summary = "'pass' stream profile missing",
                    detail = "Live TV requests '?profile=pass', but server reported: $found.",
                    suggestion = "Enable the 'pass' profile in Tvheadend → Configuration → Stream → Stream Profiles.",
                )
            }
        }

        val clockOffsetMs = htsp.clockOffsetMs
        if (clockOffsetMs != null) {
            val absOffsetMs = abs(clockOffsetMs)
            items += if (absOffsetMs > CLOCK_DRIFT_ADVISORY_MS) {
                DiagnosticItem(
                    id = DiagnosticCheckId.SERVER_CLOCK,
                    title = "Server Clock",
                    severity = DiagnosticSeverity.ADVISORY,
                    summary = "Clock offset by ${formatClockOffset(clockOffsetMs)}",
                    detail = "MuxGrid compensates automatically so EPG and live progress stay accurate.",
                    suggestion = "Enable NTP on the Tvheadend server if timers start slightly early or late.",
                )
            } else {
                DiagnosticItem(
                    id = DiagnosticCheckId.SERVER_CLOCK,
                    title = "Server Clock",
                    severity = DiagnosticSeverity.OK,
                    summary = "Server clock in sync",
                )
            }
        }

        if (hasDvr) {
            val free = htsp.freeDiskBytes
            val total = htsp.totalDiskBytes
            if (free != null && total != null && total > 0L) {
                val freeRatio = free.toDouble() / total.toDouble()
                val isLow = free < LOW_DISK_FREE_BYTES || freeRatio < LOW_DISK_FREE_RATIO
                val summaryStr = "${formatStorageBytes(free)} free of ${formatStorageBytes(total)}"
                items += if (isLow) {
                    DiagnosticItem(
                        id = DiagnosticCheckId.DVR_DISK_SPACE,
                        title = "Recording Storage",
                        severity = DiagnosticSeverity.ADVISORY,
                        summary = "Low storage: $summaryStr",
                        detail = "Free space on the Tvheadend recording disk is running low.",
                        suggestion = "Delete watched recordings or expand storage on the server.",
                    )
                } else {
                    DiagnosticItem(
                        id = DiagnosticCheckId.DVR_DISK_SPACE,
                        title = "Recording Storage",
                        severity = DiagnosticSeverity.OK,
                        summary = summaryStr,
                    )
                }
            } else if (!htsp.diskSpaceError.isNullOrBlank()) {
                items += DiagnosticItem(
                    id = DiagnosticCheckId.DVR_DISK_SPACE,
                    title = "Recording Storage",
                    severity = DiagnosticSeverity.ADVISORY,
                    summary = "Storage status unavailable",
                    detail = htsp.diskSpaceError,
                    suggestion = "Verify the recording path in Tvheadend → Configuration → Recording.",
                )
            }
        }

        return ServerDiagnosticsReport(
            checkedAtEpochMs = checkedAtEpochMs,
            serverKey = profile.serverKey,
            profileFingerprint = fingerprint,
            serverName = htsp.serverName,
            serverVersion = htsp.serverVersion,
            htspVersion = htsp.htspVersion,
            serverCapabilities = htsp.serverCapabilities,
            clockOffsetMs = htsp.clockOffsetMs,
            freeDiskBytes = htsp.freeDiskBytes,
            totalDiskBytes = htsp.totalDiskBytes,
            items = items,
        )
    }

    private fun buildHttpDiagnosticItem(
        profile: ServerProfile,
        http: HttpProbeResult,
        hasDvrAccess: Boolean,
        htspAuthenticated: Boolean = true,
    ): DiagnosticItem {
        if (!http.reachable) {
            return DiagnosticItem(
                id = DiagnosticCheckId.HTTP_MEDIA,
                title = "HTTP Media Port",
                severity = DiagnosticSeverity.DEGRADED,
                summary = "HTTP port ${profile.httpPort} unreachable",
                detail = "Live TV and completed recording playback stream over HTTP (${http.error ?: "connection failed"}).",
                suggestion = "Check TCP port ${profile.httpPort} in Tvheadend and your firewall or reverse proxy.",
            )
        }
        val code = http.statusCode ?: 200
        return when {
            code in 200..299 -> DiagnosticItem(
                id = DiagnosticCheckId.HTTP_MEDIA,
                title = "HTTP Media Port",
                severity = DiagnosticSeverity.OK,
                summary = "HTTP port ${profile.httpPort} reachable",
            )
            code == 401 || code == 403 -> when {
                !htspAuthenticated -> DiagnosticItem(
                    id = DiagnosticCheckId.HTTP_MEDIA,
                    title = "HTTP Media Port",
                    severity = DiagnosticSeverity.ADVISORY,
                    summary = "HTTP port ${profile.httpPort} reachable (auth required)",
                    detail = "HTTP endpoint responded and requested authentication (HTTP $code).",
                )
                hasDvrAccess -> DiagnosticItem(
                    id = DiagnosticCheckId.HTTP_MEDIA,
                    title = "HTTP Media Port",
                    severity = DiagnosticSeverity.OK,
                    summary = "HTTP port ${profile.httpPort} reachable",
                    detail = "Playback uses HTSP stream tickets and picons use HTSP VFS, so Web UI login is not needed.",
                )
                else -> DiagnosticItem(
                    id = DiagnosticCheckId.HTTP_MEDIA,
                    title = "HTTP Media Port",
                    severity = DiagnosticSeverity.ADVISORY,
                    summary = "HTTP port ${profile.httpPort} returned HTTP $code",
                    detail = "Live TV and DVR stream tickets still work, but HTTP picon fallback is restricted.",
                    suggestion = "Enable HTSP DVR or HTTP Digest/Basic access if channel logos do not appear.",
                )
            }
            else -> DiagnosticItem(
                id = DiagnosticCheckId.HTTP_MEDIA,
                title = "HTTP Media Port",
                severity = DiagnosticSeverity.ADVISORY,
                summary = "HTTP port ${profile.httpPort} returned HTTP $code",
                detail = "HTTP endpoint responded with status $code.",
            )
        }
    }

    private fun formatClockOffset(offsetMs: Long): String {
        val totalSec = abs(offsetMs) / 1000L
        val sign = if (offsetMs >= 0) "+" else "-"
        return when {
            totalSec >= 3600L -> String.format(Locale.US, "%s%dh %dm", sign, totalSec / 3600L, (totalSec % 3600L) / 60L)
            totalSec >= 60L -> String.format(Locale.US, "%s%dm %ds", sign, totalSec / 60L, totalSec % 60L)
            else -> String.format(Locale.US, "%s%ds", sign, totalSec)
        }
    }

    private fun formatStorageBytes(bytes: Long): String {
        val gb = bytes / (1024.0 * 1024.0 * 1024.0)
        return if (gb >= 100.0) {
            String.format(Locale.US, "%.0f GB", gb)
        } else if (gb >= 1.0) {
            String.format(Locale.US, "%.1f GB", gb)
        } else {
            val mb = bytes / (1024.0 * 1024.0)
            String.format(Locale.US, "%.0f MB", mb)
        }
    }
}
