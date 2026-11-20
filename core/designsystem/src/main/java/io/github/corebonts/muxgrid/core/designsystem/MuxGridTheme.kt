package io.github.corebonts.muxgrid.core.designsystem

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

private val MuxGridDarkScheme = darkColorScheme(
    primary = MuxGridColors.AccentBlue,
    onPrimary = MuxGridColors.TextPrimary,
    secondary = MuxGridColors.AccentBlueLight,
    background = MuxGridColors.CanvasPhone,
    onBackground = MuxGridColors.TextPrimary,
    surface = MuxGridColors.CardSurface,
    onSurface = MuxGridColors.TextPrimary,
    surfaceVariant = MuxGridColors.CardSurfaceElevated,
    onSurfaceVariant = MuxGridColors.TextSecondary,
    error = MuxGridColors.CoralLive,
    outline = MuxGridColors.HairlineBorder,
)

object MuxGridType {
    val TabularSmall = TextStyle(
        fontSize = 12.sp,
        fontWeight = FontWeight.Medium,
        fontFeatureSettings = "tnum",
        color = MuxGridColors.TextSecondary,
    )

    val TabularNumber = TextStyle(
        fontSize = 13.sp,
        fontWeight = FontWeight.SemiBold,
        fontFeatureSettings = "tnum",
        color = MuxGridColors.TextPrimary,
    )

    val Title = TextStyle(
        fontSize = 20.sp,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = (-0.02).sp,
        color = MuxGridColors.TextPrimary,
    )

    val CardTitle = TextStyle(
        fontSize = 15.sp,
        fontWeight = FontWeight.SemiBold,
        color = MuxGridColors.TextPrimary,
    )

    val CardSubtitle = TextStyle(
        fontSize = 13.sp,
        fontWeight = FontWeight.Normal,
        color = MuxGridColors.TextSecondary,
    )
}

@Composable
fun MuxGridTheme(
    isTv: Boolean = false,
    content: @Composable () -> Unit,
) {
    val scheme = if (isTv) {
        MuxGridDarkScheme.copy(background = MuxGridColors.CanvasTv)
    } else {
        MuxGridDarkScheme
    }
    MaterialTheme(
        colorScheme = scheme,
        typography = Typography(),
        content = content,
    )
}
