package io.github.corebonts.muxgrid.core.designsystem

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

private val MuxGridDarkScheme = darkColorScheme(
    primary = MuxGridColors.Accent,
    onPrimary = MuxGridColors.OnAccent,
    secondary = MuxGridColors.AccentSoft,
    onSecondary = MuxGridColors.OnAccent,
    background = MuxGridColors.CanvasPhone,
    onBackground = MuxGridColors.TextPrimary,
    surface = MuxGridColors.CardSurface,
    onSurface = MuxGridColors.TextPrimary,
    surfaceVariant = MuxGridColors.CardSurfaceElevated,
    onSurfaceVariant = MuxGridColors.TextSecondary,
    error = MuxGridColors.CoralLive,
    onError = MuxGridColors.TextPrimary,
    outline = MuxGridColors.HairlineBorder,
    outlineVariant = MuxGridColors.Divider,
)

private val MuxGridMaterialShapes = Shapes(
    extraSmall = MuxGridShapes.Xs,
    small = MuxGridShapes.Sm,
    medium = MuxGridShapes.Card,
    large = MuxGridShapes.Dialog,
    extraLarge = MuxGridShapes.Sheet,
)

object MuxGridType {
    val ScreenTitle = TextStyle(
        fontSize = 24.sp,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = (-0.5).sp,
        color = MuxGridColors.TextPrimary,
    )

    val Title = TextStyle(
        fontSize = 19.sp,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = (-0.35).sp,
        color = MuxGridColors.TextPrimary,
    )

    val Eyebrow = TextStyle(
        fontSize = 11.sp,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = 0.9.sp,
        color = MuxGridColors.TextMuted,
    )

    val CardTitle = TextStyle(
        fontSize = 14.5.sp,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = (-0.1).sp,
        color = MuxGridColors.TextPrimary,
    )

    val CardSubtitle = TextStyle(
        fontSize = 12.5.sp,
        fontWeight = FontWeight.Normal,
        color = MuxGridColors.TextSecondary,
    )

    val TabularSmall = TextStyle(
        fontSize = 11.5.sp,
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
}

private val MuxGridTypography = Typography(
    headlineSmall = MuxGridType.ScreenTitle,
    titleLarge = MuxGridType.Title,
    titleMedium = MuxGridType.CardTitle,
    bodyMedium = MuxGridType.CardSubtitle,
    labelMedium = MuxGridType.TabularSmall,
)

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
        shapes = MuxGridMaterialShapes,
        typography = MuxGridTypography,
        content = content,
    )
}
