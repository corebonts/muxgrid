package io.github.corebonts.muxgrid.core.data

import io.github.corebonts.muxgrid.core.htsp.HtspCapabilityProbeResult
import io.github.corebonts.muxgrid.core.htsp.ProbeOutcome
import io.github.corebonts.muxgrid.core.model.DiagnosticCheckId
import io.github.corebonts.muxgrid.core.model.DiagnosticSeverity
import io.github.corebonts.muxgrid.core.model.ServerProfile
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.net.InetAddress
import java.net.ServerSocket
import java.nio.charset.StandardCharsets

class ServerDiagnosticsRunnerTest {

    private val defaultProfile = ServerProfile(
        host = "tvheadend.local",
        htspPort = 9982,
        httpPort = 9981,
        username = "muxgrid",
        password = "secret",
    )

    @Test
    fun `full permissions and healthy HTTP yield OK report`() {
        val report = ServerDiagnosticsRunner.buildDiagnosticsReport(
            profile = defaultProfile,
            htsp = HtspCapabilityProbeResult(
                connected = true,
                authSucceeded = true,
                serverName = "Tvheadend",
                serverVersion = "4.3-2375",
                htspVersion = 44,
                streamingOutcome = ProbeOutcome.GRANTED,
                dvrOutcome = ProbeOutcome.GRANTED,
                passProfilePresent = true,
                availableProfiles = listOf("pass", "htsp"),
                clockOffsetMs = 1_500L,
                freeDiskBytes = 100L * 1024L * 1024L * 1024L,
                totalDiskBytes = 500L * 1024L * 1024L * 1024L,
            ),
            http = HttpProbeResult(reachable = true, statusCode = 200),
            checkedAtEpochMs = 1_700_000_000_000L,
        )

        assertEquals(DiagnosticSeverity.OK, report.overallSeverity)
        assertTrue(report.canConnect)
        assertFalse(report.hasBlockers)
        assertFalse(report.hasDegraded)
        assertTrue(report.hasStreamingAccess)
        assertTrue(report.hasDvrAccess)
        assertTrue(report.hasHttpMediaAccess)
        assertEquals("Streaming · HTTP · DVR", report.capabilitySummary)
    }

    @Test
    fun `clock drift and low disk space produce quiet ADVISORY without degrading`() {
        val report = ServerDiagnosticsRunner.buildDiagnosticsReport(
            profile = defaultProfile,
            htsp = HtspCapabilityProbeResult(
                connected = true,
                authSucceeded = true,
                serverName = "Tvheadend",
                serverVersion = "4.3",
                htspVersion = 44,
                streamingOutcome = ProbeOutcome.GRANTED,
                dvrOutcome = ProbeOutcome.GRANTED,
                passProfilePresent = true,
                clockOffsetMs = 125_000L,
                freeDiskBytes = 2L * 1024L * 1024L * 1024L,
                totalDiskBytes = 100L * 1024L * 1024L * 1024L,
            ),
            http = HttpProbeResult(reachable = true, statusCode = 401),
        )

        assertEquals(DiagnosticSeverity.ADVISORY, report.overallSeverity)
        assertTrue(report.canConnect)
        assertFalse(report.hasDegraded)
        assertEquals(DiagnosticSeverity.ADVISORY, report.itemFor(DiagnosticCheckId.SERVER_CLOCK)?.severity)
        assertEquals(DiagnosticSeverity.ADVISORY, report.itemFor(DiagnosticCheckId.DVR_DISK_SPACE)?.severity)
        // HTTP 401 with HTSP DVR enabled is OK because getTicket + HTSP VFS picons bypass Web UI auth.
        assertEquals(DiagnosticSeverity.OK, report.itemFor(DiagnosticCheckId.HTTP_MEDIA)?.severity)
    }

    @Test
    fun `read-only DVR and unreachable HTTP are non-blocking DEGRADED`() {
        val report = ServerDiagnosticsRunner.buildDiagnosticsReport(
            profile = defaultProfile,
            htsp = HtspCapabilityProbeResult(
                connected = true,
                authSucceeded = true,
                serverName = "Tvheadend",
                serverVersion = "4.2.8",
                htspVersion = 24,
                streamingOutcome = ProbeOutcome.GRANTED,
                dvrOutcome = ProbeOutcome.NO_ACCESS,
                passProfilePresent = false,
                availableProfiles = listOf("htsp"),
            ),
            http = HttpProbeResult(reachable = false, error = "Connection refused"),
        )

        assertEquals(DiagnosticSeverity.DEGRADED, report.overallSeverity)
        assertTrue(report.canConnect)
        assertFalse(report.hasBlockers)
        assertTrue(report.hasDegraded)
        assertFalse(report.hasDvrAccess)
        assertFalse(report.hasHttpMediaAccess)
        assertEquals(DiagnosticSeverity.DEGRADED, report.itemFor(DiagnosticCheckId.HTSP_DVR)?.severity)
        assertEquals(DiagnosticSeverity.DEGRADED, report.itemFor(DiagnosticCheckId.HTTP_MEDIA)?.severity)
        assertEquals(DiagnosticSeverity.DEGRADED, report.itemFor(DiagnosticCheckId.STREAM_PROFILE_PASS)?.severity)
        assertEquals(DiagnosticSeverity.DEGRADED, report.itemFor(DiagnosticCheckId.HTSP_PROTOCOL_VERSION)?.severity)
    }

    @Test
    fun `missing HTSP streaming permission and auth failure are BLOCKERS`() {
        val noStreaming = ServerDiagnosticsRunner.buildDiagnosticsReport(
            profile = defaultProfile,
            htsp = HtspCapabilityProbeResult(
                connected = true,
                authSucceeded = true,
                serverName = "Tvheadend",
                serverVersion = "4.3",
                htspVersion = 44,
                streamingOutcome = ProbeOutcome.NO_ACCESS,
                dvrOutcome = ProbeOutcome.NO_ACCESS,
            ),
            http = HttpProbeResult(reachable = true, statusCode = 200),
        )
        assertEquals(DiagnosticSeverity.BLOCKER, noStreaming.overallSeverity)
        assertFalse(noStreaming.canConnect)
        assertEquals(DiagnosticSeverity.BLOCKER, noStreaming.itemFor(DiagnosticCheckId.HTSP_STREAMING)?.severity)

        val authFailed = ServerDiagnosticsRunner.buildDiagnosticsReport(
            profile = defaultProfile,
            htsp = HtspCapabilityProbeResult(
                connected = true,
                authSucceeded = false,
                isAuthError = true,
                connectionError = "Invalid username or password",
            ),
            http = HttpProbeResult(reachable = true, statusCode = 401),
        )
        assertEquals(DiagnosticSeverity.BLOCKER, authFailed.overallSeverity)
        assertFalse(authFailed.canConnect)
        assertEquals(DiagnosticSeverity.BLOCKER, authFailed.itemFor(DiagnosticCheckId.AUTHENTICATION)?.severity)
        assertEquals(defaultProfile.diagnosticsFingerprint, authFailed.profileFingerprint)
        val httpItem = authFailed.itemFor(DiagnosticCheckId.HTTP_MEDIA)
        assertEquals(DiagnosticSeverity.ADVISORY, httpItem?.severity)
        assertFalse(httpItem?.detail?.contains("stream tickets still work") == true)
    }

    @Test
    fun `runLiveOrPreflight runs HTTP probe concurrently with active HTSP probe`() = runBlocking {
        ServerSocket(0, 5, InetAddress.getByName("127.0.0.1")).use { server ->
            val httpAccepted = CompletableDeferred<Unit>()
            val httpTask = async(Dispatchers.IO) {
                server.accept().use { sock ->
                    httpAccepted.complete(Unit)
                    val reader = sock.getInputStream().bufferedReader()
                    while (true) {
                        val line = reader.readLine() ?: break
                        if (line.isEmpty()) break
                    }
                    val body = "{}"
                    val resp = "HTTP/1.1 200 OK\r\nContent-Length: ${body.length}\r\nConnection: close\r\n\r\n$body"
                    sock.getOutputStream().write(resp.toByteArray(StandardCharsets.US_ASCII))
                    sock.getOutputStream().flush()
                }
            }

            val profile = defaultProfile.copy(host = "127.0.0.1", httpPort = server.localPort)
            val report = ServerDiagnosticsRunner.runLiveOrPreflight(profile) {
                // Must observe the HTTP request in flight before HTSP probing finishes.
                withTimeout(5_000) { httpAccepted.await() }
                HtspCapabilityProbeResult(
                    connected = true,
                    authSucceeded = true,
                    serverName = "Tvheadend",
                    serverVersion = "4.3",
                    htspVersion = 44,
                    streamingOutcome = ProbeOutcome.GRANTED,
                    dvrOutcome = ProbeOutcome.GRANTED,
                    passProfilePresent = true,
                    clockOffsetMs = 0L,
                )
            }
            httpTask.await()

            assertEquals(DiagnosticSeverity.OK, report.overallSeverity)
            assertEquals(DiagnosticSeverity.OK, report.itemFor(DiagnosticCheckId.HTTP_MEDIA)?.severity)
            assertEquals(DiagnosticSeverity.OK, report.itemFor(DiagnosticCheckId.HTSP_STREAMING)?.severity)
        }
    }
}
