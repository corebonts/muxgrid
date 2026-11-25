package io.github.corebonts.muxgrid.core.designsystem

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.corebonts.muxgrid.core.model.DiagnosticItem
import io.github.corebonts.muxgrid.core.model.DiagnosticSeverity
import io.github.corebonts.muxgrid.core.model.ServerDiagnosticsReport
import java.util.Locale

/**
 * Compact inline preflight diagnostics summary rendered inside Onboarding Step 3.
 * Keeps healthy/advisory setups quiet while highlighting blockers and non-blocking limitations.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ServerDiagnosticsSummaryCard(
    report: ServerDiagnosticsReport,
    modifier: Modifier = Modifier,
) {
    var showNotes by remember(report.checkedAtEpochMs) { mutableStateOf(false) }

    val (accentColor, headerIcon, headline) = when (report.overallSeverity) {
        DiagnosticSeverity.OK, DiagnosticSeverity.ADVISORY -> Triple(
            MuxGridColors.EmeraldOk,
            MuxGridIcons.CheckCircle,
            buildServerHeadline("Ready to connect", report),
        )
        DiagnosticSeverity.DEGRADED -> Triple(
            MuxGridColors.AmberWarn,
            MuxGridIcons.Warning,
            buildServerHeadline("Ready with limited features", report),
        )
        DiagnosticSeverity.BLOCKER -> Triple(
            MuxGridColors.CoralLive,
            MuxGridIcons.Cancel,
            "Connection check found an issue",
        )
    }

    val importantItems = remember(report) {
        report.items.filter {
            it.severity == DiagnosticSeverity.BLOCKER || it.severity == DiagnosticSeverity.DEGRADED
        }
    }
    val advisoryItems = remember(report) { report.advisoryItems }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(MuxGridShapes.Card)
            .background(accentColor.copy(alpha = MuxGridAlphas.SubtleFill))
            .border(
                width = MuxGridBorders.Hairline,
                color = accentColor.copy(alpha = MuxGridAlphas.BorderSubtle),
                shape = MuxGridShapes.Card,
            )
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = headerIcon,
                contentDescription = null,
                tint = accentColor,
                modifier = Modifier.size(18.dp),
            )
            Text(
                text = headline,
                color = accentColor,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f),
            )
        }

        if (!report.hasBlockers) {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                CapabilityPill(
                    label = if (report.hasStreamingAccess) "Streaming" else "No Streaming",
                    ok = report.hasStreamingAccess,
                )
                CapabilityPill(
                    label = if (report.hasHttpMediaAccess) "HTTP Playback" else "HTTP Unreachable",
                    ok = report.hasHttpMediaAccess,
                    warnIfFalse = true,
                )
                CapabilityPill(
                    label = if (report.hasDvrAccess) "DVR & Chase-Play" else "Read-only (No DVR)",
                    ok = report.hasDvrAccess,
                    warnIfFalse = true,
                )
            }
        }

        for (item in importantItems) {
            CompactDiagnosticFinding(item = item)
        }

        if (advisoryItems.isNotEmpty()) {
            val toggleLabel = if (showNotes) {
                "Hide notes"
            } else {
                val n = advisoryItems.size
                if (n == 1) "1 optional note" else "$n optional notes"
            }
            var toggleFocused by remember { mutableStateOf(false) }
            Row(
                modifier = Modifier
                    .clip(MuxGridShapes.Xs)
                    .background(if (toggleFocused) MuxGridColors.FocusBorder else Color.Transparent)
                    .onFocusChanged { if (toggleFocused != it.isFocused) toggleFocused = it.isFocused }
                    .clickable { showNotes = !showNotes }
                    .padding(horizontal = 6.dp, vertical = 3.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = toggleLabel,
                    color = if (toggleFocused) MuxGridColors.OnFocusBorder else MuxGridColors.TextSecondary,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                )
                Icon(
                    imageVector = if (showNotes) MuxGridIcons.ExpandLess else MuxGridIcons.ExpandMore,
                    contentDescription = null,
                    tint = if (toggleFocused) MuxGridColors.OnFocusBorder else MuxGridColors.TextSecondary,
                    modifier = Modifier.size(14.dp),
                )
            }
            if (showNotes) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    for (note in advisoryItems) {
                        CompactDiagnosticFinding(item = note)
                    }
                }
            }
        }
    }
}

@Composable
private fun CompactDiagnosticFinding(item: DiagnosticItem) {
    val itemColor = diagnosticSeverityColor(item.severity)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(MuxGridShapes.Sm)
            .background(MuxGridColors.CardSurface.copy(alpha = 0.72f))
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        Text(
            text = "${item.title}: ${item.summary}",
            color = itemColor,
            fontSize = 12.5.sp,
            fontWeight = FontWeight.SemiBold,
        )
        item.detail?.takeIf { it.isNotBlank() }?.let { detail ->
            Text(
                text = detail,
                color = MuxGridColors.TextSecondary,
                fontSize = 11.5.sp,
                lineHeight = 15.sp,
            )
        }
        item.suggestion?.takeIf { it.isNotBlank() }?.let { suggestion ->
            Text(
                text = suggestion,
                color = MuxGridColors.TextPrimary.copy(alpha = MuxGridAlphas.BodyText),
                fontSize = 11.5.sp,
                lineHeight = 15.sp,
            )
        }
    }
}

@Composable
private fun CapabilityPill(
    label: String,
    ok: Boolean,
    warnIfFalse: Boolean = false,
) {
    val color = when {
        ok -> MuxGridColors.EmeraldOk
        warnIfFalse -> MuxGridColors.AmberWarn
        else -> MuxGridColors.CoralLive
    }
    Row(
        modifier = Modifier
            .clip(MuxGridShapes.Badge)
            .background(color.copy(alpha = MuxGridAlphas.SubtleFill))
            .padding(horizontal = 7.dp, vertical = 3.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = if (ok) MuxGridIcons.Check else MuxGridIcons.Warning,
            contentDescription = null,
            tint = color,
            modifier = Modifier.size(12.dp),
        )
        Text(
            text = label,
            color = color,
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
        )
    }
}

/**
 * Full diagnostic breakdown used in Settings -> Server Health modal (Mobile & TV).
 */
@Composable
fun ServerDiagnosticsDetailContent(
    report: ServerDiagnosticsReport?,
    isRunning: Boolean,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        if (report == null) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(MuxGridShapes.Card)
                    .background(MuxGridColors.CardSurface)
                    .border(MuxGridBorders.Divider, MuxGridColors.HairlineBorder, MuxGridShapes.Card)
                    .padding(14.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (isRunning) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                        color = MuxGridColors.Accent,
                    )
                    Text(
                        text = "Checking Tvheadend capabilities and media ports…",
                        style = MuxGridType.CardSubtitle,
                    )
                } else {
                    Icon(
                        imageVector = MuxGridIcons.Info,
                        contentDescription = null,
                        tint = MuxGridColors.TextSecondary,
                        modifier = Modifier.size(18.dp),
                    )
                    Text(
                        text = "No server diagnostics collected yet. Run a check to verify streaming, HTTP playback, and DVR permissions.",
                        style = MuxGridType.CardSubtitle,
                    )
                }
            }
            return
        }

        val statusColor = diagnosticSeverityColor(report.overallSeverity)
        val statusLabel = when (report.overallSeverity) {
            DiagnosticSeverity.OK -> "All required & optional features ready"
            DiagnosticSeverity.ADVISORY -> "Ready · optional notes available"
            DiagnosticSeverity.DEGRADED -> "Partial features · non-blocking"
            DiagnosticSeverity.BLOCKER -> "Action required"
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(MuxGridShapes.Card)
                .background(statusColor.copy(alpha = MuxGridAlphas.SubtleFill))
                .border(
                    width = MuxGridBorders.Hairline,
                    color = statusColor.copy(alpha = MuxGridAlphas.BorderSubtle),
                    shape = MuxGridShapes.Card,
                )
                .padding(horizontal = 12.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = diagnosticSeverityIcon(report.overallSeverity),
                contentDescription = null,
                tint = statusColor,
                modifier = Modifier.size(20.dp),
            )
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(
                    text = statusLabel,
                    color = statusColor,
                    fontSize = 13.5.sp,
                    fontWeight = FontWeight.SemiBold,
                )
                val metaParts = buildList {
                    val srv = listOf(report.serverName, report.serverVersion)
                        .filter { it.isNotBlank() }
                        .joinToString(" ")
                    if (srv.isNotBlank()) add(srv)
                    report.htspVersion?.let { add("HTSP v$it") }
                    add(report.capabilitySummary)
                }
                Text(
                    text = metaParts.joinToString(" · "),
                    style = MuxGridType.TabularSmall,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (isRunning) {
                CircularProgressIndicator(
                    modifier = Modifier.size(16.dp),
                    strokeWidth = 2.dp,
                    color = MuxGridColors.Accent,
                )
            }
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(MuxGridShapes.Card)
                .background(MuxGridColors.CardSurface)
                .border(MuxGridBorders.Divider, MuxGridColors.HairlineBorder, MuxGridShapes.Card),
        ) {
            report.items.forEachIndexed { idx, item ->
                if (idx > 0) {
                    MuxGridDivider()
                }
                DiagnosticChecklistRow(item = item)
            }
        }
    }
}

@Composable
private fun DiagnosticChecklistRow(item: DiagnosticItem) {
    val color = diagnosticSeverityColor(item.severity)
    val icon = diagnosticSeverityIcon(item.severity)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = color,
            modifier = Modifier.padding(top = 2.dp).size(16.dp),
        )
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = item.title,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MuxGridColors.TextPrimary,
                )
                Text(
                    text = severityLabel(item.severity),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium,
                    color = color,
                )
            }
            Text(
                text = item.summary,
                fontSize = 12.sp,
                color = if (item.severity == DiagnosticSeverity.OK) {
                    MuxGridColors.TextSecondary
                } else {
                    color
                },
            )
            item.detail?.takeIf { it.isNotBlank() }?.let { detail ->
                Text(
                    text = detail,
                    fontSize = 11.5.sp,
                    lineHeight = 15.sp,
                    color = MuxGridColors.TextSecondary,
                )
            }
            item.suggestion?.takeIf { it.isNotBlank() }?.let { suggestion ->
                Text(
                    text = "Tip: $suggestion",
                    fontSize = 11.5.sp,
                    lineHeight = 15.sp,
                    color = MuxGridColors.TextPrimary.copy(alpha = MuxGridAlphas.BodyText),
                )
            }
        }
    }
}

@Composable
fun diagnosticSeverityColor(severity: DiagnosticSeverity): Color = when (severity) {
    DiagnosticSeverity.OK -> MuxGridColors.EmeraldOk
    DiagnosticSeverity.ADVISORY -> MuxGridColors.TextSecondary
    DiagnosticSeverity.DEGRADED -> MuxGridColors.AmberWarn
    DiagnosticSeverity.BLOCKER -> MuxGridColors.CoralLive
}

private fun diagnosticSeverityIcon(severity: DiagnosticSeverity): ImageVector = when (severity) {
    DiagnosticSeverity.OK -> MuxGridIcons.CheckCircle
    DiagnosticSeverity.ADVISORY -> MuxGridIcons.Info
    DiagnosticSeverity.DEGRADED -> MuxGridIcons.Warning
    DiagnosticSeverity.BLOCKER -> MuxGridIcons.Cancel
}

private fun severityLabel(severity: DiagnosticSeverity): String = when (severity) {
    DiagnosticSeverity.OK -> "OK"
    DiagnosticSeverity.ADVISORY -> "Note"
    DiagnosticSeverity.DEGRADED -> "Limited"
    DiagnosticSeverity.BLOCKER -> "Required"
}

private fun buildServerHeadline(prefix: String, report: ServerDiagnosticsReport): String {
    val srv = listOf(report.serverName, report.serverVersion)
        .filter { it.isNotBlank() }
        .joinToString(" ")
    val ver = report.htspVersion?.let { String.format(Locale.US, "HTSP v%d", it) }
    val suffix = listOfNotNull(srv.takeIf { it.isNotBlank() }, ver).joinToString(" · ")
    return if (suffix.isNotEmpty()) "$prefix · $suffix" else prefix
}
