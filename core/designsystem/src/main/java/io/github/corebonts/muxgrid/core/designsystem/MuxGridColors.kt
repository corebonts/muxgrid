package io.github.corebonts.muxgrid.core.designsystem

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

@Immutable
data class MuxGridPalette(
    val canvasTv: Color,
    val canvasPhone: Color,
    val cardSurface: Color,
    val cardSurfaceElevated: Color,
    val cardSurfaceAlt: Color,
    val piconSurface: Color,
    val audioStageTop: Color,
    val crashLogSurface: Color,
    val glassSurface: Color,
    val scrimSurface: Color,
    val scrimLight: Color,
    val overlayBackdrop: Color,
    val divider: Color,
    val hairlineBorder: Color,
    val scrimControlBorder: Color,
    val focusBorder: Color,
    val onFocusBorder: Color,
    val accent: Color,
    val accentSoft: Color,
    val onAccent: Color,
    val coralLive: Color,
    val onCoralLive: Color,
    val emeraldOk: Color,
    val amberWarn: Color,
    val progressFill: Color,
    val seekThumb: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val textMuted: Color,
    val trackBackground: Color,
)

val MuxGridDarkPalette = MuxGridPalette(
    canvasTv = Color(0xFF09090B),
    canvasPhone = Color(0xFF0C0C0E),
    cardSurface = Color(0xFF141417),
    cardSurfaceElevated = Color(0xFF1D1D22),
    cardSurfaceAlt = Color(0xFF111114),
    piconSurface = Color(0xFF26262E),
    audioStageTop = Color(0xFF141417),
    crashLogSurface = Color(0xFF111114),
    glassSurface = Color(0xD6121216),
    scrimSurface = Color(0xDB09090B),
    scrimLight = Color(0xA6141417),
    overlayBackdrop = Color(0x99000000),
    divider = Color(0x12FFFFFF),
    hairlineBorder = Color(0x17FFFFFF),
    scrimControlBorder = Color(0x33FFFFFF),
    focusBorder = Color(0xFFF4F4F5),
    onFocusBorder = Color(0xFF09090B),
    accent = Color(0xFFE5A00D),
    accentSoft = Color(0xFFF5B82E),
    onAccent = Color(0xFF09090B),
    coralLive = Color(0xFFE53935),
    onCoralLive = Color(0xFFFFFFFF),
    emeraldOk = Color(0xFF3E9B63),
    amberWarn = Color(0xFFE5A00D),
    progressFill = Color(0xFFD4D4D8),
    seekThumb = Color(0xFFFFFFFF),
    textPrimary = Color(0xFFF4F4F5),
    textSecondary = Color(0xFF9A9AA3),
    textMuted = Color(0xFF5E5E66),
    trackBackground = Color(0x2BFFFFFF),
)

val MuxGridLightPalette = MuxGridPalette(
    canvasTv = Color(0xFFF2F2F5),
    canvasPhone = Color(0xFFF4F4F7),
    cardSurface = Color(0xFFFFFFFF),
    cardSurfaceElevated = Color(0xFFEAEAF0),
    cardSurfaceAlt = Color(0xFFF8F8FA),
    piconSurface = Color(0xFFDCDCE4),
    audioStageTop = Color(0xFF141417),
    crashLogSurface = Color(0xFFEFEFF4),
    glassSurface = Color(0xD9F4F4F7),
    scrimSurface = Color(0xE8F4F4F7),
    scrimLight = Color(0xA6FFFFFF),
    overlayBackdrop = Color(0x66000000),
    divider = Color(0x14000000),
    hairlineBorder = Color(0x1F000000),
    scrimControlBorder = Color(0x33000000),
    focusBorder = Color(0xFF18181B),
    onFocusBorder = Color(0xFFF4F4F5),
    accent = Color(0xFFE5A00D),
    accentSoft = Color(0xFFB87600),
    onAccent = Color(0xFF09090B),
    coralLive = Color(0xFFD32F2F),
    onCoralLive = Color(0xFFFFFFFF),
    emeraldOk = Color(0xFF2E7D4F),
    amberWarn = Color(0xFFC98600),
    progressFill = Color(0xFF52525B),
    seekThumb = Color(0xFF18181B),
    textPrimary = Color(0xFF141417),
    textSecondary = Color(0xFF52525B),
    textMuted = Color(0xFF71717A),
    trackBackground = Color(0x1F000000),
)

internal val LocalMuxGridColors = staticCompositionLocalOf { MuxGridDarkPalette }

object MuxGridColors {
    val CanvasTv: Color @Composable @ReadOnlyComposable get() = LocalMuxGridColors.current.canvasTv
    val CanvasPhone: Color @Composable @ReadOnlyComposable get() = LocalMuxGridColors.current.canvasPhone
    val CardSurface: Color @Composable @ReadOnlyComposable get() = LocalMuxGridColors.current.cardSurface
    val CardSurfaceElevated: Color @Composable @ReadOnlyComposable get() = LocalMuxGridColors.current.cardSurfaceElevated
    val CardSurfaceAlt: Color @Composable @ReadOnlyComposable get() = LocalMuxGridColors.current.cardSurfaceAlt
    val PiconSurface: Color @Composable @ReadOnlyComposable get() = LocalMuxGridColors.current.piconSurface
    val AudioStageTop: Color @Composable @ReadOnlyComposable get() = LocalMuxGridColors.current.audioStageTop
    val CrashLogSurface: Color @Composable @ReadOnlyComposable get() = LocalMuxGridColors.current.crashLogSurface
    val GlassSurface: Color @Composable @ReadOnlyComposable get() = LocalMuxGridColors.current.glassSurface
    val ScrimSurface: Color @Composable @ReadOnlyComposable get() = LocalMuxGridColors.current.scrimSurface
    val ScrimLight: Color @Composable @ReadOnlyComposable get() = LocalMuxGridColors.current.scrimLight
    val OverlayBackdrop: Color @Composable @ReadOnlyComposable get() = LocalMuxGridColors.current.overlayBackdrop
    val Divider: Color @Composable @ReadOnlyComposable get() = LocalMuxGridColors.current.divider
    val HairlineBorder: Color @Composable @ReadOnlyComposable get() = LocalMuxGridColors.current.hairlineBorder
    val ScrimControlBorder: Color @Composable @ReadOnlyComposable get() = LocalMuxGridColors.current.scrimControlBorder
    val FocusBorder: Color @Composable @ReadOnlyComposable get() = LocalMuxGridColors.current.focusBorder
    val OnFocusBorder: Color @Composable @ReadOnlyComposable get() = LocalMuxGridColors.current.onFocusBorder
    val Accent: Color @Composable @ReadOnlyComposable get() = LocalMuxGridColors.current.accent
    val AccentSoft: Color @Composable @ReadOnlyComposable get() = LocalMuxGridColors.current.accentSoft
    val OnAccent: Color @Composable @ReadOnlyComposable get() = LocalMuxGridColors.current.onAccent
    val CoralLive: Color @Composable @ReadOnlyComposable get() = LocalMuxGridColors.current.coralLive
    val OnCoralLive: Color @Composable @ReadOnlyComposable get() = LocalMuxGridColors.current.onCoralLive
    val EmeraldOk: Color @Composable @ReadOnlyComposable get() = LocalMuxGridColors.current.emeraldOk
    val AmberWarn: Color @Composable @ReadOnlyComposable get() = LocalMuxGridColors.current.amberWarn
    val ProgressFill: Color @Composable @ReadOnlyComposable get() = LocalMuxGridColors.current.progressFill
    val SeekThumb: Color @Composable @ReadOnlyComposable get() = LocalMuxGridColors.current.seekThumb
    val TextPrimary: Color @Composable @ReadOnlyComposable get() = LocalMuxGridColors.current.textPrimary
    val TextSecondary: Color @Composable @ReadOnlyComposable get() = LocalMuxGridColors.current.textSecondary
    val TextMuted: Color @Composable @ReadOnlyComposable get() = LocalMuxGridColors.current.textMuted
    val TrackBackground: Color @Composable @ReadOnlyComposable get() = LocalMuxGridColors.current.trackBackground
}
