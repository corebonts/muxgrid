package io.github.corebonts.muxgrid.core.designsystem

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.dp

object MuxGridRadii {
    val Xs = 6.dp
    val Sm = 8.dp
    val Control = 10.dp
    val Badge = 12.dp
    val Row = 14.dp
    val Card = 16.dp
    val CardLg = 18.dp
    val Dialog = 20.dp
    val Sheet = 22.dp
    val Dock = 24.dp
}

object MuxGridShapes {
    val Xs = RoundedCornerShape(MuxGridRadii.Xs)
    val Sm = RoundedCornerShape(MuxGridRadii.Sm)
    val Control = RoundedCornerShape(MuxGridRadii.Control)
    val Badge = RoundedCornerShape(MuxGridRadii.Badge)
    val Row = RoundedCornerShape(MuxGridRadii.Row)
    val Card = RoundedCornerShape(MuxGridRadii.Card)
    val CardLg = RoundedCornerShape(MuxGridRadii.CardLg)
    val Dialog = RoundedCornerShape(MuxGridRadii.Dialog)
    val Sheet = RoundedCornerShape(MuxGridRadii.Sheet)
    val Dock = RoundedCornerShape(MuxGridRadii.Dock)
    val Pill = RoundedCornerShape(50)
}

object MuxGridBorders {
    val Hairline = 1.dp
    val Focused = 2.dp
    val FocusedCard = 2.5.dp
}
