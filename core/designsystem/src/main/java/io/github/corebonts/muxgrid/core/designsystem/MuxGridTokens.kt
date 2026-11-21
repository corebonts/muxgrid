package io.github.corebonts.muxgrid.core.designsystem

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.dp

object MuxGridRadii {
    val SeekTrack = 2.dp
    val Xs = 3.dp
    val Sm = 5.dp
    val Control = 5.dp
    val Badge = 3.dp
    val Row = 8.dp
    val Card = 8.dp
    val CardLg = 8.dp
    val Dialog = 12.dp
    val Sheet = 12.dp
}

object MuxGridShapes {
    val SeekTrack = RoundedCornerShape(MuxGridRadii.SeekTrack)
    val Xs = RoundedCornerShape(MuxGridRadii.Xs)
    val Sm = RoundedCornerShape(MuxGridRadii.Sm)
    val Control = RoundedCornerShape(MuxGridRadii.Control)
    val Badge = RoundedCornerShape(MuxGridRadii.Badge)
    val Row = RoundedCornerShape(MuxGridRadii.Row)
    val Card = RoundedCornerShape(MuxGridRadii.Card)
    val CardLg = RoundedCornerShape(MuxGridRadii.CardLg)
    val Dialog = RoundedCornerShape(MuxGridRadii.Dialog)
    val Sheet = RoundedCornerShape(MuxGridRadii.Sheet)
    val SheetTop = RoundedCornerShape(topStart = MuxGridRadii.Sheet, topEnd = MuxGridRadii.Sheet)
    val Progress = RoundedCornerShape(1.dp)
    val Pill = RoundedCornerShape(50)
}

object MuxGridBorders {
    val Divider = 0.5.dp
    val Hairline = 1.dp
    val Focused = 2.dp
    val FocusedCard = 2.dp
}

object MuxGridDurations {
    const val Fast = 120
    const val RailExpand = 140
    const val Switch = 150
    const val Progress = 220
    const val SeekFeedbackMs = 900L
    const val TvZapOsdMs = 3_000L
    const val MobileHudAutoHideMs = 3_500L
    const val TvHudAutoHideMs = 6_000L
}

object MuxGridAlphas {
    const val SubtleFill = 0.14f
    const val TintFill = 0.16f
    const val FocusOverlay = 0.22f
    const val ScrimBorder = 0.22f
    const val Disabled = 0.45f
    const val BorderSubtle = 0.45f
    const val BorderEmphasis = 0.55f
    const val BorderStrong = 0.65f
    const val SecondaryText = 0.78f
    const val BodyText = 0.85f
    const val SynopsisText = 0.88f
}
