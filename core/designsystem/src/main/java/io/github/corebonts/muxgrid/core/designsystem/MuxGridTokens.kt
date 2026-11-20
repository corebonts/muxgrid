package io.github.corebonts.muxgrid.core.designsystem

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.dp

object MuxGridRadii {
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
