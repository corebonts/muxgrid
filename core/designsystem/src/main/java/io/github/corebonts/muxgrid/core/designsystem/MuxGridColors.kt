package io.github.corebonts.muxgrid.core.designsystem

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import io.github.corebonts.muxgrid.core.model.ThemeMode

enum class CanvasBackdropStyle {
    SOLID,
    AMBIENT_WASH,
    MESH_GRADIENT,
}

@Immutable
data class MuxGridPalette(
    val themeMode: ThemeMode,
    val backdropStyle: CanvasBackdropStyle,
    val cardBlurEnabled: Boolean,
    val canvasTv: Color,
    val canvasPhone: Color,
    val cardSurface: Color,
    val cardSurfaceElevated: Color,
    val cardSurfaceAlt: Color,
    val modalSurface: Color,
    val cardBorder: Color,
    val cardTopHighlight: Color,
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
    val focusGlow: Color,
    val accent: Color,
    val accentSoft: Color,
    val accentGradientEnd: Color,
    val onAccent: Color,
    val accentContainer: Color,
    val accentBorder: Color,
    val navActiveContainer: Color,
    val navActiveIndicator: Color,
    val navActiveContent: Color,
    val segmentedTrack: Color,
    val segmentedActiveBg: Color,
    val segmentedActiveFg: Color,
    val segmentedActiveBorder: Color,
    val buttonSecondaryContainer: Color,
    val buttonSecondaryBorder: Color,
    val epgLiveSurface: Color,
    val epgLiveBorder: Color,
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
    val radioGlow1: Color,
    val radioGlow2: Color,
    val radioGlow3: Color,
    val backdropWash1: Color,
    val backdropWash2: Color,
    val backdropWash3: Color,
)

val MuxGridCarbonPalette = MuxGridPalette(
    themeMode = ThemeMode.CARBON,
    backdropStyle = CanvasBackdropStyle.SOLID,
    cardBlurEnabled = false,
    canvasTv = Color(0xFF09090B),
    canvasPhone = Color(0xFF0C0C0E),
    cardSurface = Color(0xFF141418),
    cardSurfaceElevated = Color(0xFF1D1D23),
    cardSurfaceAlt = Color(0xFF101014),
    modalSurface = Color(0xFF1D1D23),
    cardBorder = Color(0x14FFFFFF),
    cardTopHighlight = Color(0x0AFFFFFF),
    piconSurface = Color(0xFF26262E),
    audioStageTop = Color(0xFF141418),
    crashLogSurface = Color(0xFF101014),
    glassSurface = Color(0xD6121216),
    scrimSurface = Color(0xDB09090B),
    scrimLight = Color(0xA6141418),
    overlayBackdrop = Color(0x99000000),
    divider = Color(0x12FFFFFF),
    hairlineBorder = Color(0x17FFFFFF),
    scrimControlBorder = Color(0x33FFFFFF),
    focusBorder = Color(0xFFF4F4F5),
    onFocusBorder = Color(0xFF09090B),
    focusGlow = Color(0x40F4F4F5),
    accent = Color(0xFFE5A00D),
    accentSoft = Color(0xFFF5B82E),
    accentGradientEnd = Color(0xFFE5A00D),
    onAccent = Color(0xFF09090B),
    accentContainer = Color(0x26E5A00D),
    accentBorder = Color(0x73F5B82E),
    navActiveContainer = Color(0x14FFFFFF),
    navActiveIndicator = Color(0xFFE5A00D),
    navActiveContent = Color(0xFFF4F4F5),
    segmentedTrack = Color(0xFF101014),
    segmentedActiveBg = Color(0xFF1D1D23),
    segmentedActiveFg = Color(0xFFF4F4F5),
    segmentedActiveBorder = Color(0x1FFFFFFF),
    buttonSecondaryContainer = Color(0xFF1D1D23),
    buttonSecondaryBorder = Color(0x21FFFFFF),
    epgLiveSurface = Color(0xFF1E1D21),
    epgLiveBorder = Color(0x66E5A00D),
    coralLive = Color(0xFFE53935),
    onCoralLive = Color(0xFFFFFFFF),
    emeraldOk = Color(0xFF3E9B63),
    amberWarn = Color(0xFFE5A00D),
    progressFill = Color(0xFFD4D4D8),
    seekThumb = Color(0xFFFFFFFF),
    textPrimary = Color(0xFFF4F4F5),
    textSecondary = Color(0xFF9A9AA3),
    textMuted = Color(0xFF666670),
    trackBackground = Color(0x2BFFFFFF),
    radioGlow1 = Color(0xFFE5A00D),
    radioGlow2 = Color(0xFFB86214),
    radioGlow3 = Color(0xFF26262E),
    backdropWash1 = Color.Transparent,
    backdropWash2 = Color.Transparent,
    backdropWash3 = Color.Transparent,
)

val MuxGridAmberPalette = MuxGridPalette(
    themeMode = ThemeMode.AMBER,
    backdropStyle = CanvasBackdropStyle.AMBIENT_WASH,
    cardBlurEnabled = false,
    canvasTv = Color(0xFF13100E),
    canvasPhone = Color(0xFF161210),
    cardSurface = Color(0xFF211C19),
    cardSurfaceElevated = Color(0xFF2B2420),
    cardSurfaceAlt = Color(0xFF1B1714),
    modalSurface = Color(0xFF2B2420),
    cardBorder = Color(0x17FFEAD6),
    cardTopHighlight = Color(0x0DFFEAD6),
    piconSurface = Color(0xFF302722),
    audioStageTop = Color(0xFF211C19),
    crashLogSurface = Color(0xFF1B1714),
    glassSurface = Color(0xD61B1613),
    scrimSurface = Color(0xDB13100E),
    scrimLight = Color(0xA6211C19),
    overlayBackdrop = Color(0x990D0906),
    divider = Color(0x14FFEAD6),
    hairlineBorder = Color(0x1CFFEAD6),
    scrimControlBorder = Color(0x36FFEAD6),
    focusBorder = Color(0xFFFFF4E6),
    onFocusBorder = Color(0xFF16100C),
    focusGlow = Color(0x40FFF4E6),
    accent = Color(0xFFF08A14),
    accentSoft = Color(0xFFF6AA42),
    accentGradientEnd = Color(0xFFF08A14),
    onAccent = Color(0xFF130B03),
    accentContainer = Color(0x28F08A14),
    accentBorder = Color(0x73F6AA42),
    navActiveContainer = Color(0x14FFECDA),
    navActiveIndicator = Color(0xFFF08A14),
    navActiveContent = Color(0xFFFFF7EE),
    segmentedTrack = Color(0xFF171310),
    segmentedActiveBg = Color(0xFF2D251F),
    segmentedActiveFg = Color(0xFFFFF4E6),
    segmentedActiveBorder = Color(0x26FFEAD6),
    buttonSecondaryContainer = Color(0xFF2B2420),
    buttonSecondaryBorder = Color(0x24FFEAD6),
    epgLiveSurface = Color(0xFF2D231B),
    epgLiveBorder = Color(0x6BF08A14),
    coralLive = Color(0xFFEF4444),
    onCoralLive = Color(0xFFFFFFFF),
    emeraldOk = Color(0xFF3E9B63),
    amberWarn = Color(0xFFF59E0B),
    progressFill = Color(0xFFE7DCD2),
    seekThumb = Color(0xFFFFF7EE),
    textPrimary = Color(0xFFFDF8F3),
    textSecondary = Color(0xFFB3A396),
    textMuted = Color(0xFF7B6D62),
    trackBackground = Color(0x2BFFECDA),
    radioGlow1 = Color(0xFFF08A14),
    radioGlow2 = Color(0xFFC45B0E),
    radioGlow3 = Color(0xFF3B2614),
    backdropWash1 = Color(0xFFF08A14),
    backdropWash2 = Color(0xFFC45B0E),
    backdropWash3 = Color(0xFF3B2614),
)

val MuxGridAuroraPalette = MuxGridPalette(
    themeMode = ThemeMode.AURORA,
    backdropStyle = CanvasBackdropStyle.MESH_GRADIENT,
    cardBlurEnabled = true,
    canvasTv = Color(0xFF08090D),
    canvasPhone = Color(0xFF090A0E),
    cardSurface = Color(0x8F181A24),
    cardSurfaceElevated = Color(0xAD242836),
    cardSurfaceAlt = Color(0x7A12141C),
    modalSurface = Color(0xEB1B1E2B),
    cardBorder = Color(0x24FFFFFF),
    cardTopHighlight = Color(0x38FFFFFF),
    piconSurface = Color(0x9E262A38),
    audioStageTop = Color(0xFF141824),
    crashLogSurface = Color(0xFF12141C),
    glassSurface = Color(0xA6141620),
    scrimSurface = Color(0xCC090A0E),
    scrimLight = Color(0x99181A24),
    overlayBackdrop = Color(0x9E040508),
    divider = Color(0x1AFFFFFF),
    hairlineBorder = Color(0x24FFFFFF),
    scrimControlBorder = Color(0x3DFFFFFF),
    focusBorder = Color(0xFFFFFFFF),
    onFocusBorder = Color(0xFF0B0C10),
    focusGlow = Color(0x47FFFFFF),
    accent = Color(0xFFFF6B35),
    accentSoft = Color(0xFFFF946B),
    accentGradientEnd = Color(0xFFF97316),
    onAccent = Color(0xFFFFFFFF),
    accentContainer = Color(0x2EFF6B35),
    accentBorder = Color(0x78FF946B),
    navActiveContainer = Color(0x24FFFFFF),
    navActiveIndicator = Color(0xFFFF6B35),
    navActiveContent = Color(0xFFFFFFFF),
    segmentedTrack = Color(0x7A12141C),
    segmentedActiveBg = Color(0x33FFFFFF),
    segmentedActiveFg = Color(0xFFFFFFFF),
    segmentedActiveBorder = Color(0x38FFFFFF),
    buttonSecondaryContainer = Color(0x24FFFFFF),
    buttonSecondaryBorder = Color(0x2EFFFFFF),
    epgLiveSurface = Color(0xA62B2228),
    epgLiveBorder = Color(0x7AFF6B35),
    coralLive = Color(0xFFFF4D4F),
    onCoralLive = Color(0xFFFFFFFF),
    emeraldOk = Color(0xFF34D399),
    amberWarn = Color(0xFFFF9F43),
    progressFill = Color(0xFFE2E6F0),
    seekThumb = Color(0xFFFFFFFF),
    textPrimary = Color(0xFFF8FAFC),
    textSecondary = Color(0xFFAEB5C6),
    textMuted = Color(0xFF778096),
    trackBackground = Color(0x33FFFFFF),
    radioGlow1 = Color(0xFF109C8A),
    radioGlow2 = Color(0xFFD0307E),
    radioGlow3 = Color(0xFF2C62E0),
    backdropWash1 = Color(0xFF109C8A),
    backdropWash2 = Color(0xFFD0307E),
    backdropWash3 = Color(0xFF2C62E0),
)

val MuxGridIndigoPalette = MuxGridPalette(
    themeMode = ThemeMode.INDIGO,
    backdropStyle = CanvasBackdropStyle.AMBIENT_WASH,
    cardBlurEnabled = false,
    canvasTv = Color(0xFF111022),
    canvasPhone = Color(0xFF141329),
    cardSurface = Color(0xFF1E1C38),
    cardSurfaceElevated = Color(0xFF28254A),
    cardSurfaceAlt = Color(0xFF181730),
    modalSurface = Color(0xFF262347),
    cardBorder = Color(0x1FC4B5FD),
    cardTopHighlight = Color(0x14DDD6FE),
    piconSurface = Color(0xFF2A2750),
    audioStageTop = Color(0xFF1E1C38),
    crashLogSurface = Color(0xFF181730),
    glassSurface = Color(0xD617152E),
    scrimSurface = Color(0xDB111022),
    scrimLight = Color(0xA61E1C38),
    overlayBackdrop = Color(0x9E080714),
    divider = Color(0x1AC4B5FD),
    hairlineBorder = Color(0x21C4B5FD),
    scrimControlBorder = Color(0x38DDD6FE),
    focusBorder = Color(0xFFEDE9FE),
    onFocusBorder = Color(0xFF121024),
    focusGlow = Color(0x478B5CF6),
    accent = Color(0xFF8B5CF6),
    accentSoft = Color(0xFFA78BFA),
    accentGradientEnd = Color(0xFF38BDF8),
    onAccent = Color(0xFFFFFFFF),
    accentContainer = Color(0x2E8B5CF6),
    accentBorder = Color(0x7AA78BFA),
    navActiveContainer = Color(0x2E8B5CF6),
    navActiveIndicator = Color(0xFF8B5CF6),
    navActiveContent = Color(0xFFF5F3FF),
    segmentedTrack = Color(0xFF15142B),
    segmentedActiveBg = Color(0xFF2D2954),
    segmentedActiveFg = Color(0xFFF5F3FF),
    segmentedActiveBorder = Color(0x33A78BFA),
    buttonSecondaryContainer = Color(0xFF28254A),
    buttonSecondaryBorder = Color(0x2BC4B5FD),
    epgLiveSurface = Color(0xFF29254D),
    epgLiveBorder = Color(0x788B5CF6),
    coralLive = Color(0xFFF43F5E),
    onCoralLive = Color(0xFFFFFFFF),
    emeraldOk = Color(0xFF34D399),
    amberWarn = Color(0xFFFBBF24),
    progressFill = Color(0xFFDDD6FE),
    seekThumb = Color(0xFFF5F3FF),
    textPrimary = Color(0xFFF5F3FF),
    textSecondary = Color(0xFFB4AFD4),
    textMuted = Color(0xFF7B769E),
    trackBackground = Color(0x2EC4B5FD),
    radioGlow1 = Color(0xFF8B5CF6),
    radioGlow2 = Color(0xFF38BDF8),
    radioGlow3 = Color(0xFF4C1D95),
    backdropWash1 = Color(0xFF8B5CF6),
    backdropWash2 = Color(0xFF38BDF8),
    backdropWash3 = Color(0xFF4C1D95),
)

fun paletteFor(themeMode: ThemeMode): MuxGridPalette = when (themeMode) {
    ThemeMode.CARBON -> MuxGridCarbonPalette
    ThemeMode.AMBER -> MuxGridAmberPalette
    ThemeMode.AURORA -> MuxGridAuroraPalette
    ThemeMode.INDIGO -> MuxGridIndigoPalette
}

internal val LocalMuxGridColors = staticCompositionLocalOf { MuxGridAmberPalette }

object MuxGridColors {
    val Theme: ThemeMode @Composable @ReadOnlyComposable get() = LocalMuxGridColors.current.themeMode
    val BackdropStyle: CanvasBackdropStyle @Composable @ReadOnlyComposable get() = LocalMuxGridColors.current.backdropStyle
    val CardBlurEnabled: Boolean @Composable @ReadOnlyComposable get() = LocalMuxGridColors.current.cardBlurEnabled
    val CanvasTv: Color @Composable @ReadOnlyComposable get() = LocalMuxGridColors.current.canvasTv
    val CanvasPhone: Color @Composable @ReadOnlyComposable get() = LocalMuxGridColors.current.canvasPhone
    val CardSurface: Color @Composable @ReadOnlyComposable get() = LocalMuxGridColors.current.cardSurface
    val CardSurfaceElevated: Color @Composable @ReadOnlyComposable get() = LocalMuxGridColors.current.cardSurfaceElevated
    val CardSurfaceAlt: Color @Composable @ReadOnlyComposable get() = LocalMuxGridColors.current.cardSurfaceAlt
    val ModalSurface: Color @Composable @ReadOnlyComposable get() = LocalMuxGridColors.current.modalSurface
    val CardBorder: Color @Composable @ReadOnlyComposable get() = LocalMuxGridColors.current.cardBorder
    val CardTopHighlight: Color @Composable @ReadOnlyComposable get() = LocalMuxGridColors.current.cardTopHighlight
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
    val FocusGlow: Color @Composable @ReadOnlyComposable get() = LocalMuxGridColors.current.focusGlow
    val Accent: Color @Composable @ReadOnlyComposable get() = LocalMuxGridColors.current.accent
    val AccentSoft: Color @Composable @ReadOnlyComposable get() = LocalMuxGridColors.current.accentSoft
    val AccentGradientEnd: Color @Composable @ReadOnlyComposable get() = LocalMuxGridColors.current.accentGradientEnd
    val OnAccent: Color @Composable @ReadOnlyComposable get() = LocalMuxGridColors.current.onAccent
    val AccentContainer: Color @Composable @ReadOnlyComposable get() = LocalMuxGridColors.current.accentContainer
    val AccentBorder: Color @Composable @ReadOnlyComposable get() = LocalMuxGridColors.current.accentBorder
    val NavActiveContainer: Color @Composable @ReadOnlyComposable get() = LocalMuxGridColors.current.navActiveContainer
    val NavActiveIndicator: Color @Composable @ReadOnlyComposable get() = LocalMuxGridColors.current.navActiveIndicator
    val NavActiveContent: Color @Composable @ReadOnlyComposable get() = LocalMuxGridColors.current.navActiveContent
    val SegmentedTrack: Color @Composable @ReadOnlyComposable get() = LocalMuxGridColors.current.segmentedTrack
    val SegmentedActiveBg: Color @Composable @ReadOnlyComposable get() = LocalMuxGridColors.current.segmentedActiveBg
    val SegmentedActiveFg: Color @Composable @ReadOnlyComposable get() = LocalMuxGridColors.current.segmentedActiveFg
    val SegmentedActiveBorder: Color @Composable @ReadOnlyComposable get() = LocalMuxGridColors.current.segmentedActiveBorder
    val ButtonSecondaryContainer: Color @Composable @ReadOnlyComposable get() = LocalMuxGridColors.current.buttonSecondaryContainer
    val ButtonSecondaryBorder: Color @Composable @ReadOnlyComposable get() = LocalMuxGridColors.current.buttonSecondaryBorder
    val EpgLiveSurface: Color @Composable @ReadOnlyComposable get() = LocalMuxGridColors.current.epgLiveSurface
    val EpgLiveBorder: Color @Composable @ReadOnlyComposable get() = LocalMuxGridColors.current.epgLiveBorder
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
    val RadioGlow1: Color @Composable @ReadOnlyComposable get() = LocalMuxGridColors.current.radioGlow1
    val RadioGlow2: Color @Composable @ReadOnlyComposable get() = LocalMuxGridColors.current.radioGlow2
    val RadioGlow3: Color @Composable @ReadOnlyComposable get() = LocalMuxGridColors.current.radioGlow3
}
