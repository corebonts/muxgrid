package io.github.corebonts.muxgrid.core.designsystem

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import io.github.corebonts.muxgrid.core.model.ThemeMode

private fun materialDarkSchemeFor(palette: MuxGridPalette): ColorScheme = darkColorScheme(
    primary = palette.accent,
    onPrimary = palette.onAccent,
    secondary = palette.accentSoft,
    onSecondary = palette.onAccent,
    background = palette.canvasPhone,
    onBackground = palette.textPrimary,
    surface = palette.cardSurface,
    onSurface = palette.textPrimary,
    surfaceVariant = palette.cardSurfaceElevated,
    onSurfaceVariant = palette.textSecondary,
    error = palette.coralLive,
    onError = palette.onCoralLive,
    outline = palette.hairlineBorder,
    outlineVariant = palette.divider,
)

private val MuxGridMaterialShapes = Shapes(
    extraSmall = MuxGridShapes.Xs,
    small = MuxGridShapes.Sm,
    medium = MuxGridShapes.Card,
    large = MuxGridShapes.Dialog,
    extraLarge = MuxGridShapes.Sheet,
)

@Immutable
internal class MuxGridTypographyTokens(palette: MuxGridPalette) {
    val screenTitle = TextStyle(
        fontSize = 24.sp,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = (-0.5).sp,
        color = palette.textPrimary,
    )

    val title = TextStyle(
        fontSize = 19.sp,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = (-0.35).sp,
        color = palette.textPrimary,
    )

    val eyebrow = TextStyle(
        fontSize = 11.sp,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = 0.9.sp,
        color = palette.textMuted,
    )

    val cardTitle = TextStyle(
        fontSize = 14.5.sp,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = (-0.1).sp,
        color = palette.textPrimary,
    )

    val cardSubtitle = TextStyle(
        fontSize = 12.5.sp,
        fontWeight = FontWeight.Normal,
        color = palette.textSecondary,
    )

    val tabularSmall = TextStyle(
        fontSize = 11.5.sp,
        fontWeight = FontWeight.Medium,
        fontFeatureSettings = "tnum",
        color = palette.textSecondary,
    )

    val tabularNumber = TextStyle(
        fontSize = 13.sp,
        fontWeight = FontWeight.SemiBold,
        fontFeatureSettings = "tnum",
        color = palette.textPrimary,
    )

    val materialTypography = Typography(
        headlineSmall = screenTitle,
        titleLarge = title,
        titleMedium = cardTitle,
        bodyMedium = cardSubtitle,
        labelMedium = tabularSmall,
    )
}

private val ThemeSchemes: Map<ThemeMode, ColorScheme> = ThemeMode.entries.associateWith { mode ->
    materialDarkSchemeFor(paletteFor(mode))
}

private val ThemeTypographies: Map<ThemeMode, MuxGridTypographyTokens> = ThemeMode.entries.associateWith { mode ->
    MuxGridTypographyTokens(paletteFor(mode))
}

internal val LocalMuxGridType = staticCompositionLocalOf {
    ThemeTypographies.getValue(ThemeMode.DEFAULT)
}

object MuxGridType {
    val ScreenTitle: TextStyle @Composable @ReadOnlyComposable get() = LocalMuxGridType.current.screenTitle
    val Title: TextStyle @Composable @ReadOnlyComposable get() = LocalMuxGridType.current.title
    val Eyebrow: TextStyle @Composable @ReadOnlyComposable get() = LocalMuxGridType.current.eyebrow
    val CardTitle: TextStyle @Composable @ReadOnlyComposable get() = LocalMuxGridType.current.cardTitle
    val CardSubtitle: TextStyle @Composable @ReadOnlyComposable get() = LocalMuxGridType.current.cardSubtitle
    val TabularSmall: TextStyle @Composable @ReadOnlyComposable get() = LocalMuxGridType.current.tabularSmall
    val TabularNumber: TextStyle @Composable @ReadOnlyComposable get() = LocalMuxGridType.current.tabularNumber
}

@Composable
fun MuxGridTheme(
    isTv: Boolean = false,
    themeMode: ThemeMode = ThemeMode.DEFAULT,
    content: @Composable () -> Unit,
) {
    val palette = paletteFor(themeMode)
    val typographyTokens = ThemeTypographies.getValue(themeMode)
    val baseScheme = ThemeSchemes.getValue(themeMode)
    val scheme = if (isTv) {
        baseScheme.copy(background = palette.canvasTv)
    } else {
        baseScheme
    }
    CompositionLocalProvider(
        LocalMuxGridColors provides palette,
        LocalMuxGridType provides typographyTokens,
    ) {
        MaterialTheme(
            colorScheme = scheme,
            shapes = MuxGridMaterialShapes,
            typography = typographyTokens.materialTypography,
            content = content,
        )
    }
}
