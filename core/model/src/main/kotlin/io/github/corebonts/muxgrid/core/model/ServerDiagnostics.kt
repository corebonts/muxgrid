package io.github.corebonts.muxgrid.core.model

enum class DiagnosticSeverity {
    OK,
    ADVISORY,
    DEGRADED,
    BLOCKER,
}

enum class DiagnosticCheckId {
    HTSP_CONNECTION,
    AUTHENTICATION,
    HTSP_STREAMING,
    HTTP_MEDIA,
    HTSP_DVR,
    STREAM_PROFILE_PASS,
    HTSP_PROTOCOL_VERSION,
    SERVER_CLOCK,
    DVR_DISK_SPACE,
}

data class DiagnosticItem(
    val id: DiagnosticCheckId,
    val title: String,
    val severity: DiagnosticSeverity,
    val summary: String,
    val detail: String? = null,
    val suggestion: String? = null,
)

data class PortProbeResult(
    val host: String,
    val htspPort: Int,
    val httpPort: Int,
    val htspReachable: Boolean,
    val httpReachable: Boolean,
    val serverName: String? = null,
    val serverVersion: String? = null,
    val htspVersion: Int? = null,
    val htspError: String? = null,
    val httpError: String? = null,
) {
    val bothReachable: Boolean
        get() = htspReachable && httpReachable
}

data class ServerDiagnosticsReport(
    val checkedAtEpochMs: Long,
    val serverKey: String,
    val profileFingerprint: String = "",
    val serverName: String,
    val serverVersion: String,
    val htspVersion: Int? = null,
    val serverCapabilities: List<String> = emptyList(),
    val clockOffsetMs: Long? = null,
    val freeDiskBytes: Long? = null,
    val totalDiskBytes: Long? = null,
    val items: List<DiagnosticItem> = emptyList(),
) {
    val overallSeverity: DiagnosticSeverity
        get() = items.maxOfOrNull { it.severity } ?: DiagnosticSeverity.OK

    val hasBlockers: Boolean
        get() = items.any { it.severity == DiagnosticSeverity.BLOCKER }

    val hasDegraded: Boolean
        get() = items.any { it.severity == DiagnosticSeverity.DEGRADED }

    val hasAdvisories: Boolean
        get() = items.any { it.severity == DiagnosticSeverity.ADVISORY }

    val canConnect: Boolean
        get() = !hasBlockers

    fun itemFor(id: DiagnosticCheckId): DiagnosticItem? = items.firstOrNull { it.id == id }

    val hasStreamingAccess: Boolean
        get() = itemFor(DiagnosticCheckId.HTSP_STREAMING)?.severity == DiagnosticSeverity.OK

    val hasDvrAccess: Boolean
        get() = itemFor(DiagnosticCheckId.HTSP_DVR)?.severity == DiagnosticSeverity.OK

    val hasHttpMediaAccess: Boolean
        get() = itemFor(DiagnosticCheckId.HTTP_MEDIA)?.let {
            it.severity == DiagnosticSeverity.OK || it.severity == DiagnosticSeverity.ADVISORY
        } ?: false

    val blockerItems: List<DiagnosticItem>
        get() = items.filter { it.severity == DiagnosticSeverity.BLOCKER }

    val degradedItems: List<DiagnosticItem>
        get() = items.filter { it.severity == DiagnosticSeverity.DEGRADED }

    val advisoryItems: List<DiagnosticItem>
        get() = items.filter { it.severity == DiagnosticSeverity.ADVISORY }

    val okItems: List<DiagnosticItem>
        get() = items.filter { it.severity == DiagnosticSeverity.OK }

    val capabilitySummary: String
        get() {
            if (hasBlockers) {
                return blockerItems.firstOrNull()?.title?.let { "Issue: $it" } ?: "Connection issue"
            }
            val parts = buildList {
                if (hasStreamingAccess) add("Streaming")
                if (hasHttpMediaAccess) add("HTTP") else add("No HTTP")
                if (hasDvrAccess) add("DVR") else add("Read-only")
            }
            return parts.joinToString(" · ")
        }

    val debugSummaryLine: String
        get() {
            val status = when (overallSeverity) {
                DiagnosticSeverity.OK -> "OK"
                DiagnosticSeverity.ADVISORY -> "OK (with notes)"
                DiagnosticSeverity.DEGRADED -> "Degraded"
                DiagnosticSeverity.BLOCKER -> "Blocked"
            }
            val issueTitles = items
                .filter { it.severity == DiagnosticSeverity.BLOCKER || it.severity == DiagnosticSeverity.DEGRADED }
                .joinToString(", ") { it.title }
            return if (issueTitles.isNotEmpty()) {
                "$status: $issueTitles"
            } else {
                "$status ($capabilitySummary)"
            }
        }
}
