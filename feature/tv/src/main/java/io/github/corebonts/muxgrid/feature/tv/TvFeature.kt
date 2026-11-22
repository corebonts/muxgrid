package io.github.corebonts.muxgrid.feature.tv

import android.os.SystemClock
import android.view.KeyEvent
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.takeOrElse
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.zIndex
import androidx.tv.material3.Border
import androidx.tv.material3.Button
import androidx.tv.material3.ButtonDefaults
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Icon
import androidx.tv.material3.LocalContentColor
import androidx.tv.material3.LocalTextStyle
import androidx.tv.material3.MaterialTheme as TvMaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.darkColorScheme as tvDarkColorScheme
import coil3.ImageLoader
import io.github.corebonts.muxgrid.core.data.PiconModel
import io.github.corebonts.muxgrid.core.data.TvheadendRepository
import io.github.corebonts.muxgrid.core.designsystem.ChannelPiconBadge
import io.github.corebonts.muxgrid.core.designsystem.ConnectionStatusDot
import io.github.corebonts.muxgrid.core.designsystem.DvrConfirmKind
import io.github.corebonts.muxgrid.core.designsystem.DvrLookupIndex
import io.github.corebonts.muxgrid.core.designsystem.DvrStateBadge
import io.github.corebonts.muxgrid.core.designsystem.EpgLiveProgressSection
import io.github.corebonts.muxgrid.core.designsystem.LiveProgressBar
import io.github.corebonts.muxgrid.core.designsystem.MuxGridAlphas
import io.github.corebonts.muxgrid.core.designsystem.MuxGridBorders
import io.github.corebonts.muxgrid.core.designsystem.MuxGridColors
import io.github.corebonts.muxgrid.core.designsystem.MuxGridDetailModalLayout
import io.github.corebonts.muxgrid.core.designsystem.MuxGridDivider
import io.github.corebonts.muxgrid.core.designsystem.MuxGridDurations
import io.github.corebonts.muxgrid.core.designsystem.MuxGridIcons
import io.github.corebonts.muxgrid.core.designsystem.MuxGridRadii
import io.github.corebonts.muxgrid.core.designsystem.MuxGridShapes
import io.github.corebonts.muxgrid.core.designsystem.MuxGridSplashScreen
import io.github.corebonts.muxgrid.core.designsystem.MuxGridSyncingState
import io.github.corebonts.muxgrid.core.designsystem.MuxGridTheme
import io.github.corebonts.muxgrid.core.designsystem.MuxGridType
import io.github.corebonts.muxgrid.core.designsystem.OnboardingServerScreen
import io.github.corebonts.muxgrid.core.designsystem.STARTUP_SPLASH_EXIT_DURATION_MS
import io.github.corebonts.muxgrid.core.designsystem.STARTUP_SPLASH_EXIT_SCALE
import io.github.corebonts.muxgrid.core.designsystem.STARTUP_SPLASH_STATUS_DELAY_MS
import io.github.corebonts.muxgrid.core.designsystem.STARTUP_SPLASH_TIMEOUT_MS
import io.github.corebonts.muxgrid.core.designsystem.buildActiveRecordingsByChannel
import io.github.corebonts.muxgrid.core.designsystem.buildLiveFilters
import io.github.corebonts.muxgrid.core.designsystem.collectDebugEnvironmentSnapshot
import io.github.corebonts.muxgrid.core.designsystem.connectionStatusColor
import io.github.corebonts.muxgrid.core.designsystem.connectionStatusDotColor
import io.github.corebonts.muxgrid.core.designsystem.connectionStatusText
import io.github.corebonts.muxgrid.core.designsystem.copyTextToClipboard
import io.github.corebonts.muxgrid.core.designsystem.dvrConfirmSpec
import io.github.corebonts.muxgrid.core.designsystem.filterChannels
import io.github.corebonts.muxgrid.core.designsystem.findActiveRecordingForChannel
import io.github.corebonts.muxgrid.core.designsystem.formatDebugInfoReport
import io.github.corebonts.muxgrid.core.designsystem.formatEpochHm
import io.github.corebonts.muxgrid.core.designsystem.formatProgramModalMeta
import io.github.corebonts.muxgrid.core.designsystem.icon
import io.github.corebonts.muxgrid.core.designsystem.isStartupSyncReady
import io.github.corebonts.muxgrid.core.designsystem.label
import io.github.corebonts.muxgrid.core.designsystem.rememberNowEpochSec
import io.github.corebonts.muxgrid.core.designsystem.resolveModalSubtitleAndDescription
import io.github.corebonts.muxgrid.core.designsystem.serverHeadline
import io.github.corebonts.muxgrid.core.designsystem.syncStatusMessage
import io.github.corebonts.muxgrid.core.model.AppAttribution
import io.github.corebonts.muxgrid.core.model.AppBuildInfo
import io.github.corebonts.muxgrid.core.model.AppNotices
import io.github.corebonts.muxgrid.core.model.Channel
import io.github.corebonts.muxgrid.core.model.DvrEntry
import io.github.corebonts.muxgrid.core.model.DvrState
import io.github.corebonts.muxgrid.core.model.ThemeMode
import io.github.corebonts.muxgrid.core.model.ConnectionState
import io.github.corebonts.muxgrid.core.model.HwDecPreset
import io.github.corebonts.muxgrid.core.model.LiveFilter
import io.github.corebonts.muxgrid.core.model.MainTab
import io.github.corebonts.muxgrid.core.model.NowNextProgram
import io.github.corebonts.muxgrid.core.model.ServerProfile
import io.github.corebonts.muxgrid.core.model.StartScreen
import io.github.corebonts.muxgrid.core.model.TimeshiftWindow
import io.github.corebonts.muxgrid.core.player.PlaybackEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private enum class TvRoute {
    ONBOARDING,
    MAIN,
    PLAYER,
}

internal enum class TvSettingsFocusTarget {
    PRIMARY,
    EDIT_SERVER,
}

internal data class TvGuideFocusTarget(
    val channelId: Long,
    val eventId: Long? = null,
)

internal data class TvRecordingFocusTarget(
    val shelfKey: String,
    val entryId: Long,
)

/**
 * Non-observable focus memory so D-pad moves never trigger Compose recomposition.
 */
/**
 * Lightweight TV text wrapper delegating directly to [BasicText] while still honoring
 * `androidx.tv.material3` [LocalContentColor] and [LocalTextStyle]. Unlike
 * `androidx.tv.material3.Text`, it does not wrap every text node in `Modifier.graphicsLayer`,
 * which otherwise triggers expensive HWUI offscreen layer flushes (`flush layers`) during TV scroll.
 */
@Composable
internal fun Text(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = Color.Unspecified,
    fontSize: TextUnit = TextUnit.Unspecified,
    fontStyle: FontStyle? = null,
    fontWeight: FontWeight? = null,
    fontFamily: FontFamily? = null,
    letterSpacing: TextUnit = TextUnit.Unspecified,
    textDecoration: TextDecoration? = null,
    textAlign: TextAlign? = null,
    lineHeight: TextUnit = TextUnit.Unspecified,
    overflow: TextOverflow = TextOverflow.Clip,
    softWrap: Boolean = true,
    maxLines: Int = Int.MAX_VALUE,
    minLines: Int = 1,
    style: TextStyle = LocalTextStyle.current,
) {
    val textColor = color.takeOrElse { style.color.takeOrElse { LocalContentColor.current } }
    val mergedStyle = style.merge(
        color = textColor,
        fontSize = fontSize,
        fontWeight = fontWeight,
        textAlign = textAlign ?: TextAlign.Unspecified,
        lineHeight = lineHeight,
        fontFamily = fontFamily,
        textDecoration = textDecoration,
        fontStyle = fontStyle,
        letterSpacing = letterSpacing,
    )
    BasicText(
        text = text,
        modifier = modifier,
        style = mergedStyle,
        overflow = overflow,
        softWrap = softWrap,
        maxLines = maxLines,
        minLines = minLines,
    )
}

internal class TvFocusMemory {
    var liveChannelId: Long? = null
    var guideTarget: TvGuideFocusTarget? = null
    var guideWindowStartEpochSec: Long? = null
    var recordingTarget: TvRecordingFocusTarget? = null
    var settingsTarget: TvSettingsFocusTarget = TvSettingsFocusTarget.PRIMARY
}

/**
 * Draw-phase-only TV focus background, rounded border, and optional subtle scale so D-pad focus
 * moves never recompose list/grid items, mutate modifier chains, or push Skia rounded-rect clips.
 */
internal fun Modifier.tvFocusableSurface(
    focusedState: State<Boolean>,
    cornerRadius: Dp,
    containerColor: Color,
    focusedContainerColor: Color,
    focusBorderColor: Color,
    focusBorderWidth: Dp,
    unfocusedBorderColor: Color = Color.Transparent,
    unfocusedBorderWidth: Dp = 0.dp,
    focusedScale: Float = 1f,
): Modifier {
    val scaled = if (focusedScale != 1f) {
        graphicsLayer {
            val s = if (focusedState.value) focusedScale else 1f
            scaleX = s
            scaleY = s
        }
    } else {
        this
    }
    return scaled.drawWithContent {
        val focused = focusedState.value
        val radiusPx = cornerRadius.toPx()
        val cr = CornerRadius(radiusPx, radiusPx)
        drawRoundRect(
            color = if (focused) focusedContainerColor else containerColor,
            cornerRadius = cr,
        )
        drawContent()
        val bColor = if (focused) focusBorderColor else unfocusedBorderColor
        val bWidthDp = if (focused) focusBorderWidth else unfocusedBorderWidth
        if (bColor != Color.Transparent && bWidthDp > 0.dp) {
            val strokePx = bWidthDp.toPx()
            val halfStroke = strokePx / 2f
            drawRoundRect(
                color = bColor,
                topLeft = Offset(halfStroke, halfStroke),
                size = Size(size.width - strokePx, size.height - strokePx),
                cornerRadius = CornerRadius((radiusPx - halfStroke).coerceAtLeast(0f)),
                style = Stroke(width = strokePx),
            )
        }
    }
}

@Composable
internal fun TvFilterChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    leadingIcon: ImageVector? = null,
    unselectedContainerColor: Color = Color.Transparent,
    unselectedContentColor: Color = MuxGridColors.TextMuted,
) {
    var focused by remember { mutableStateOf(false) }
    val bg = when {
        focused -> MuxGridColors.FocusBorder
        selected -> MuxGridColors.Accent
        else -> unselectedContainerColor
    }
    val fg = when {
        focused -> MuxGridColors.OnFocusBorder
        selected -> MuxGridColors.OnAccent
        else -> unselectedContentColor
    }
    Row(
        modifier = modifier
            .heightIn(min = 32.dp)
            .background(bg, MuxGridShapes.Sm)
            .onFocusChanged { if (focused != it.isFocused) focused = it.isFocused }
            .clickable(
                interactionSource = null,
                indication = null,
                onClick = onClick,
            )
            .clearAndSetSemantics {
                role = Role.Tab
                contentDescription = label
            }
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        if (leadingIcon != null) {
            Icon(
                imageVector = leadingIcon,
                contentDescription = null,
                tint = fg,
                modifier = Modifier.size(15.dp),
            )
        }
        Text(
            text = label,
            color = fg,
            fontSize = 12.5.sp,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
            maxLines = 1,
            softWrap = false,
        )
    }
}

private val TV_RAIL_COLLAPSED_WIDTH = 68.dp
private val TV_RAIL_EXPANDED_WIDTH = 186.dp

@Composable
internal fun tvPrimaryButtonColors() = ButtonDefaults.colors(
    containerColor = MuxGridColors.Accent,
    contentColor = MuxGridColors.OnAccent,
    focusedContainerColor = MuxGridColors.FocusBorder,
    focusedContentColor = MuxGridColors.OnFocusBorder,
)

@Composable
internal fun tvSecondaryButtonColors() = ButtonDefaults.colors(
    containerColor = MuxGridColors.CardSurfaceElevated,
    contentColor = MuxGridColors.TextPrimary,
    focusedContainerColor = MuxGridColors.FocusBorder,
    focusedContentColor = MuxGridColors.OnFocusBorder,
)

@Composable
internal fun tvDialogSecondaryButtonColors() = ButtonDefaults.colors(
    containerColor = MuxGridColors.CardSurface,
    contentColor = MuxGridColors.TextPrimary,
    focusedContainerColor = MuxGridColors.FocusBorder,
    focusedContentColor = MuxGridColors.OnFocusBorder,
)

@Composable
internal fun tvDialogDestructiveButtonColors() = ButtonDefaults.colors(
    containerColor = MuxGridColors.CoralLive.copy(alpha = 0.16f),
    contentColor = MuxGridColors.CoralLive,
    focusedContainerColor = MuxGridColors.CoralLive,
    focusedContentColor = MuxGridColors.OnCoralLive,
)

@Composable
internal fun tvButtonShape() = ButtonDefaults.shape(shape = MuxGridShapes.Sm)

@Composable
internal fun TvScrollableDescriptionBox(
    text: String,
    resetKey: Any?,
    modifier: Modifier = Modifier,
) {
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    val descScrollState = rememberScrollState()
    var isDescFocused by remember(resetKey) { mutableStateOf(false) }
    val canScrollDesc = descScrollState.maxValue in 1 until Int.MAX_VALUE
    val scrollStepPx = with(density) { 48.dp.toPx() }
    LaunchedEffect(resetKey) {
        descScrollState.scrollTo(0)
    }
    Box(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(max = 220.dp)
            .then(
                if (canScrollDesc) {
                    Modifier
                        .clip(MuxGridShapes.Sm)
                        .background(MuxGridColors.CardSurface)
                        .border(
                            width = if (isDescFocused) MuxGridBorders.Focused else MuxGridBorders.Divider,
                            color = if (isDescFocused) MuxGridColors.FocusBorder else MuxGridColors.HairlineBorder,
                            shape = MuxGridShapes.Sm,
                        )
                        .onFocusChanged { isDescFocused = it.isFocused }
                        .onPreviewKeyEvent { keyEvent ->
                            if (keyEvent.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                            when (keyEvent.key) {
                                Key.DirectionDown -> {
                                    if (!descScrollState.canScrollForward) return@onPreviewKeyEvent false
                                    scope.launch { descScrollState.scrollBy(scrollStepPx) }
                                    true
                                }
                                Key.DirectionUp -> {
                                    if (!descScrollState.canScrollBackward) return@onPreviewKeyEvent false
                                    scope.launch { descScrollState.scrollBy(-scrollStepPx) }
                                    true
                                }
                                else -> false
                            }
                        }
                        .focusable()
                        .verticalScroll(descScrollState)
                        .padding(horizontal = 12.dp, vertical = 10.dp)
                } else {
                    Modifier.verticalScroll(descScrollState)
                },
            ),
    ) {
        Text(
            text = text,
            style = MuxGridType.CardSubtitle,
            color = MuxGridColors.TextPrimary.copy(alpha = MuxGridAlphas.SynopsisText),
            lineHeight = 19.sp,
        )
    }
}

@Composable
internal fun TvModalDialog(
    onDismissRequest: () -> Unit,
    minWidth: Dp = 560.dp,
    maxWidth: Dp = 740.dp,
    borderColor: Color = MuxGridColors.HairlineBorder,
    borderWidth: Dp = MuxGridBorders.Hairline,
    content: @Composable ColumnScope.() -> Unit,
) {
    // Guard against residual KeyDown repeats or KeyUp events when a dialog is opened by holding
    // OK/Enter (long-press) on a remote control. Only a fresh KeyDown (repeatCount == 0) that
    // started after this dialog opened can arm a confirm click inside the dialog.
    val dialogOpenedAtUptimeMs = remember { SystemClock.uptimeMillis() }
    var armedByFreshConfirmKeyDown by remember { mutableStateOf(false) }

    Dialog(
        onDismissRequest = onDismissRequest,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Column(
            modifier = Modifier
                .widthIn(min = minWidth, max = maxWidth)
                .clip(MuxGridShapes.Dialog)
                .background(MuxGridColors.CardSurfaceElevated)
                .border(borderWidth, borderColor, MuxGridShapes.Dialog)
                .onPreviewKeyEvent { keyEvent ->
                    val native = keyEvent.nativeKeyEvent
                    val isConfirmKey = when (native.keyCode) {
                        KeyEvent.KEYCODE_DPAD_CENTER,
                        KeyEvent.KEYCODE_ENTER,
                        KeyEvent.KEYCODE_NUMPAD_ENTER,
                        KeyEvent.KEYCODE_SPACE,
                        KeyEvent.KEYCODE_BUTTON_A -> true
                        else -> false
                    }
                    if (!isConfirmKey) return@onPreviewKeyEvent false

                    val startedBeforeDialogOpen = native.downTime < dialogOpenedAtUptimeMs + 160L ||
                        SystemClock.uptimeMillis() - dialogOpenedAtUptimeMs < 180L
                    when (keyEvent.type) {
                        KeyEventType.KeyDown -> {
                            if (startedBeforeDialogOpen || native.repeatCount > 0) {
                                return@onPreviewKeyEvent !armedByFreshConfirmKeyDown
                            }
                            armedByFreshConfirmKeyDown = true
                            false
                        }
                        KeyEventType.KeyUp -> {
                            if (!armedByFreshConfirmKeyDown || startedBeforeDialogOpen) {
                                armedByFreshConfirmKeyDown = false
                                return@onPreviewKeyEvent true
                            }
                            armedByFreshConfirmKeyDown = false
                            false
                        }
                        else -> false
                    }
                }
                .verticalScroll(rememberScrollState())
                .padding(22.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            content = content,
        )
    }
}

@Composable
internal fun TvConfirmDialog(
    title: String,
    message: String,
    confirmLabel: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    dismissLabel: String = "Cancel",
    isDestructive: Boolean = false,
    enabled: Boolean = true,
) {
    val cancelFocusRequester = remember { FocusRequester() }
    val confirmFocusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        runCatching {
            if (isDestructive) cancelFocusRequester.requestFocus() else confirmFocusRequester.requestFocus()
        }
    }
    TvModalDialog(
        onDismissRequest = { if (enabled) onDismiss() },
        minWidth = 380.dp,
        maxWidth = 520.dp,
        borderColor = if (isDestructive) MuxGridColors.CoralLive.copy(alpha = MuxGridAlphas.BorderSubtle) else MuxGridColors.HairlineBorder,
    ) {
        Text(
            text = title,
            style = MuxGridType.Title,
            fontSize = 18.sp,
            color = MuxGridColors.TextPrimary,
        )
        Text(
            text = message,
            style = MuxGridType.CardSubtitle,
            fontSize = 14.sp,
            color = MuxGridColors.TextSecondary,
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.End),
        ) {
            Button(
                enabled = enabled,
                onClick = onDismiss,
                modifier = Modifier.focusRequester(cancelFocusRequester),
                shape = tvButtonShape(),
                colors = tvDialogSecondaryButtonColors(),
            ) {
                Text(dismissLabel, maxLines = 1, softWrap = false)
            }
            Button(
                enabled = enabled,
                onClick = onConfirm,
                modifier = Modifier.focusRequester(confirmFocusRequester),
                shape = tvButtonShape(),
                colors = if (isDestructive) tvDialogDestructiveButtonColors() else tvPrimaryButtonColors(),
            ) {
                Text(confirmLabel, fontWeight = FontWeight.SemiBold, maxLines = 1, softWrap = false)
            }
        }
    }
}

@Composable
fun TvRoot(
    repository: TvheadendRepository,
    playbackEngine: PlaybackEngine,
    appInfo: AppBuildInfo = AppBuildInfo(versionName = "", versionCode = 0, buildSha = ""),
) {
    TvMaterialTheme(
        colorScheme = tvDarkColorScheme(
            primary = MuxGridColors.Accent,
            onPrimary = MuxGridColors.OnAccent,
            background = MuxGridColors.CanvasTv,
            onBackground = MuxGridColors.TextPrimary,
            surface = MuxGridColors.CardSurface,
            onSurface = MuxGridColors.TextPrimary,
            surfaceVariant = MuxGridColors.CardSurfaceElevated,
            onSurfaceVariant = MuxGridColors.TextSecondary,
        ),
    ) {
        TvContent(
            repository = repository,
            playbackEngine = playbackEngine,
            appInfo = appInfo,
        )
    }
}

@OptIn(ExperimentalTvMaterial3Api::class, ExperimentalComposeUiApi::class)
@Composable
private fun TvContent(
    repository: TvheadendRepository,
    playbackEngine: PlaybackEngine,
    appInfo: AppBuildInfo,
) {
    val scope = rememberCoroutineScope()
    val profile by repository.serverProfile.collectAsState()
    val startScreen by repository.startScreen.collectAsState()
    val connectionState by repository.connectionState.collectAsState()
    val channels by repository.channels.collectAsState()
    val favorites by repository.favoriteChannelIds.collectAsState()

    val currentProfile = profile
    val currentStartScreen = startScreen
    if (currentProfile == null || currentStartScreen == null) {
        MuxGridSplashScreen(backgroundColor = MuxGridColors.CanvasTv)
        return
    }

    var hasExitedStartupSplash by rememberSaveable { mutableStateOf(false) }
    var showSplashStatus by remember { mutableStateOf(false) }
    val epgByChannel by repository.epgByChannel.collectAsState()
    val dvrEntries by repository.dvrEntries.collectAsState()
    val startupReady = !currentProfile.isConfigured || isStartupSyncReady(
        startTab = currentStartScreen.tab,
        connectionState = connectionState,
        hasChannels = channels.isNotEmpty(),
        hasEpg = epgByChannel.isNotEmpty(),
        hasRecordings = dvrEntries.isNotEmpty(),
    )
    LaunchedEffect(hasExitedStartupSplash) {
        if (!hasExitedStartupSplash) {
            kotlinx.coroutines.delay(STARTUP_SPLASH_STATUS_DELAY_MS)
            showSplashStatus = true
        }
    }
    LaunchedEffect(hasExitedStartupSplash) {
        if (!hasExitedStartupSplash) {
            kotlinx.coroutines.delay(STARTUP_SPLASH_TIMEOUT_MS)
            hasExitedStartupSplash = true
        }
    }
    LaunchedEffect(startupReady) {
        if (startupReady && !hasExitedStartupSplash) {
            // Wait one frame so newly populated grids/timelines settle before the exit animation starts.
            withFrameNanos { }
            hasExitedStartupSplash = true
        }
    }

    var route by remember(currentProfile.isConfigured) {
        mutableStateOf(if (currentProfile.isConfigured) TvRoute.MAIN else TvRoute.ONBOARDING)
    }
    var activeTab by remember { mutableStateOf(currentStartScreen.tab) }
    var activeFilter by remember { mutableStateOf(currentStartScreen.liveFilter) }

    val filteredChannels = remember(channels, activeFilter, favorites) {
        filterChannels(channels, activeFilter, favorites)
    }

    val offsetMs = (connectionState as? ConnectionState.Connected)?.serverClockOffsetMs ?: 0L

    // Hoisted scroll states & non-reactive focus memory survive MAIN <-> PLAYER and MAIN <-> ONBOARDING
    val liveGridState = rememberLazyGridState()
    val guideListState = rememberLazyListState()
    val recordingsListState = rememberLazyListState()
    val recordingShelfStates = remember { mutableMapOf<String, LazyListState>() }

    val focusMemory = remember { TvFocusMemory() }
    var hasDeliveredInitialContentFocus by remember { mutableStateOf(false) }
    var contentFocusRequestNonce by remember { mutableIntStateOf(0) }
    var isGuideSearchOpen by remember { mutableStateOf(false) }

    Box(modifier = Modifier.fillMaxSize()) {
        when (route) {
        TvRoute.ONBOARDING -> {
            BackHandler(enabled = currentProfile.isConfigured) {
                route = TvRoute.MAIN
                contentFocusRequestNonce++
            }
            OnboardingServerScreen(
                initialProfile = currentProfile,
                isTv = true,
                canCancel = currentProfile.isConfigured,
                onTestConnection = { repository.testConnection(it) },
                onSaveAndConnect = { updated ->
                    repository.saveAndConnect(updated)
                    route = TvRoute.MAIN
                    contentFocusRequestNonce++
                },
                onCancel = {
                    route = TvRoute.MAIN
                    contentFocusRequestNonce++
                },
            )
        }
        TvRoute.MAIN -> {
            LaunchedEffect(activeTab) {
                if (activeTab != MainTab.GUIDE) {
                    isGuideSearchOpen = false
                }
            }
            var railHasFocus by remember { mutableStateOf(false) }
            var allowRailFocus by remember { mutableStateOf(false) }
            var railFocusRequestNonce by remember { mutableIntStateOf(0) }
            val contentFocusRequester = remember { FocusRequester() }

            // Put initial focus inside content on frame 0 before enabling rail focusability so
            // Android TV's initial focus never lands on the rail and flashes it open at startup.
            LaunchedEffect(Unit) {
                if (contentFocusRequestNonce == 0) {
                    runCatching { contentFocusRequester.requestFocus() }
                }
                withFrameNanos { }
                allowRailFocus = true
            }

            if (!hasDeliveredInitialContentFocus) {
                when (activeTab) {
                    MainTab.LIVE, MainTab.GUIDE -> {
                        LaunchedEffect(channels.isNotEmpty()) {
                            if (channels.isNotEmpty()) {
                                hasDeliveredInitialContentFocus = true
                                if (!railHasFocus) {
                                    contentFocusRequestNonce++
                                }
                            }
                        }
                    }
                    MainTab.RECORDINGS -> {
                        val dvrEntries by repository.dvrEntries.collectAsState()
                        LaunchedEffect(dvrEntries.isNotEmpty()) {
                            if (dvrEntries.isNotEmpty()) {
                                hasDeliveredInitialContentFocus = true
                                if (!railHasFocus) {
                                    contentFocusRequestNonce++
                                }
                            }
                        }
                    }
                    MainTab.SETTINGS -> {
                        LaunchedEffect(Unit) {
                            hasDeliveredInitialContentFocus = true
                            if (!railHasFocus) {
                                contentFocusRequestNonce++
                            }
                        }
                    }
                }
            }
            BackHandler(enabled = !railHasFocus && !isGuideSearchOpen) {
                railFocusRequestNonce++
            }
            val statusDotColor = connectionStatusDotColor(connectionState)

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MuxGridColors.CanvasTv),
            ) {
                // Keep content width fixed so expanding the nav rail overlays instead of
                // resizing and re-measuring the entire channel grid / EPG timeline every frame.
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(start = TV_RAIL_COLLAPSED_WIDTH + 20.dp, end = 24.dp, top = 16.dp, bottom = 12.dp)
                        .focusRequester(contentFocusRequester)
                        .focusRestorer()
                        .focusGroup(),
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 4.dp, vertical = 2.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = activeTab.label(),
                            style = MuxGridType.ScreenTitle,
                        )
                        if (activeTab == MainTab.GUIDE) {
                            TvIconButton(
                                onClick = { isGuideSearchOpen = !isGuideSearchOpen },
                                containerColor = if (isGuideSearchOpen) MuxGridColors.CardSurfaceElevated else Color.Transparent,
                                contentColor = if (isGuideSearchOpen) MuxGridColors.AccentSoft else MuxGridColors.TextSecondary,
                            ) {
                                Icon(
                                    imageVector = if (isGuideSearchOpen) MuxGridIcons.Close else MuxGridIcons.Search,
                                    contentDescription = if (isGuideSearchOpen) "Close search" else "Search programs",
                                    modifier = Modifier.size(18.dp),
                                )
                            }
                        }
                    }

                    Spacer(Modifier.height(8.dp))

                    Box(modifier = Modifier.weight(1f)) {
                        when (activeTab) {
                            MainTab.LIVE -> {
                                val tags by repository.tags.collectAsState()
                                val nowNextMap by repository.nowNextByChannel.collectAsState()
                                val dvrEntries by repository.dvrEntries.collectAsState()
                                val activeRecordingsByChannel = remember(dvrEntries, nowNextMap) {
                                    buildActiveRecordingsByChannel(dvrEntries, nowNextMap)
                                }
                                val nowEpochSec = rememberNowEpochSec(offsetMs)
                                TvLiveChannelsTab(
                                    connectionState = connectionState,
                                    channels = filteredChannels,
                                    hasAnyChannels = channels.isNotEmpty(),
                                    filters = remember(tags) { buildLiveFilters(tags) },
                                    activeFilter = activeFilter,
                                    favorites = favorites,
                                    nowNextMap = nowNextMap,
                                    dvrEntries = dvrEntries,
                                    activeRecordingsByChannel = activeRecordingsByChannel,
                                    nowEpochSec = nowEpochSec,
                                    imageLoader = repository.imageLoader,
                                    gridState = liveGridState,
                                    focusMemory = focusMemory,
                                    focusRequestNonce = contentFocusRequestNonce,
                                    onSelectFilter = { activeFilter = it },
                                    onChannelClick = { channel ->
                                        focusMemory.liveChannelId = channel.id
                                        hasDeliveredInitialContentFocus = true
                                        playbackEngine.playChannel(channel, repository.hwDecPreset.value)
                                        route = TvRoute.PLAYER
                                    },
                                    onPlayRecording = { entry, channel, startSec ->
                                        focusMemory.liveChannelId = channel.id
                                        hasDeliveredInitialContentFocus = true
                                        playbackEngine.playDvrEntry(entry, channel, repository.hwDecPreset.value, startSec)
                                        route = TvRoute.PLAYER
                                    },
                                    onToggleFavorite = { channel ->
                                        scope.launch { repository.toggleFavorite(channel.id) }
                                    },
                                    onOpenGuideForChannel = { channel ->
                                        focusMemory.liveChannelId = channel.id
                                        focusMemory.guideTarget = TvGuideFocusTarget(
                                            channelId = channel.id,
                                            eventId = nowNextMap[channel.id]?.now?.id,
                                        )
                                        focusMemory.guideWindowStartEpochSec = nowEpochSec
                                        hasDeliveredInitialContentFocus = true
                                        activeTab = MainTab.GUIDE
                                        contentFocusRequestNonce++
                                    },
                                    onRecordEvent = { eventId -> repository.scheduleRecording(eventId) },
                                    onStopRecording = { dvrId -> repository.stopRecording(dvrId) },
                                    onCancelRecording = { dvrId -> repository.cancelRecording(dvrId) },
                                )
                            }
                            MainTab.GUIDE -> {
                                val epgByChannel by repository.epgByChannel.collectAsState()
                                val dvrEntries by repository.dvrEntries.collectAsState()
                                val nowEpochSec = rememberNowEpochSec(offsetMs)
                                TvGuideScreen(
                                    channels = channels,
                                    connectionState = connectionState,
                                    epgByChannel = epgByChannel,
                                    dvrEntries = dvrEntries,
                                    nowEpochSec = nowEpochSec,
                                    imageLoader = repository.imageLoader,
                                    listState = guideListState,
                                    initialWindowStartEpochSec = focusMemory.guideWindowStartEpochSec ?: nowEpochSec,
                                    onWindowStartChange = {
                                        focusMemory.guideWindowStartEpochSec = it
                                    },
                                    focusMemory = focusMemory,
                                    focusRequestNonce = contentFocusRequestNonce,
                                    searchOpen = isGuideSearchOpen,
                                    onCloseSearch = { isGuideSearchOpen = false },
                                    onWatchChannel = { channel ->
                                        focusMemory.guideTarget = TvGuideFocusTarget(
                                            channelId = channel.id,
                                            eventId = focusMemory.guideTarget?.takeIf { it.channelId == channel.id }?.eventId,
                                        )
                                        isGuideSearchOpen = false
                                        playbackEngine.playChannel(channel, repository.hwDecPreset.value)
                                        route = TvRoute.PLAYER
                                    },
                                    onPlayRecording = { entry, channel, startSec ->
                                        channel?.let { ch ->
                                            focusMemory.guideTarget = TvGuideFocusTarget(
                                                channelId = ch.id,
                                                eventId = focusMemory.guideTarget?.takeIf { it.channelId == ch.id }?.eventId,
                                            )
                                        }
                                        isGuideSearchOpen = false
                                        playbackEngine.playDvrEntry(entry, channel, repository.hwDecPreset.value, startSec)
                                        route = TvRoute.PLAYER
                                    },
                                    onScheduleRecording = { eventId -> repository.scheduleRecording(eventId) },
                                    onStopRecording = { dvrId -> repository.stopRecording(dvrId) },
                                    onCancelRecording = { dvrId -> repository.cancelRecording(dvrId) },
                                )
                            }
                            MainTab.RECORDINGS -> {
                                val dvrEntries by repository.dvrEntries.collectAsState()
                                val nowEpochSec = rememberNowEpochSec(offsetMs)
                                TvRecordingsScreen(
                                    dvrEntries = dvrEntries,
                                    channels = channels,
                                    connectionState = connectionState,
                                    nowEpochSec = nowEpochSec,
                                    imageLoader = repository.imageLoader,
                                    listState = recordingsListState,
                                    shelfStateFor = { key -> recordingShelfStates.getOrPut(key) { LazyListState() } },
                                    focusMemory = focusMemory,
                                    focusRequestNonce = contentFocusRequestNonce,
                                    onWatchLiveChannel = { channel ->
                                        playbackEngine.playChannel(channel, repository.hwDecPreset.value)
                                        route = TvRoute.PLAYER
                                    },
                                    onPlayRecording = { entry, channel, startSec ->
                                        playbackEngine.playDvrEntry(entry, channel, repository.hwDecPreset.value, startSec)
                                        route = TvRoute.PLAYER
                                    },
                                    onStopRecording = { dvrId -> repository.stopRecording(dvrId) },
                                    onCancelRecording = { dvrId -> repository.cancelRecording(dvrId) },
                                    onDeleteRecording = { dvrId -> repository.deleteRecording(dvrId) },
                                    onToggleWatched = { dvrId, watched -> repository.markRecordingWatched(dvrId, watched) },
                                )
                            }
                            MainTab.SETTINGS -> {
                                val themeMode by repository.themeMode.collectAsState()
                                val hwDecPreset by repository.hwDecPreset.collectAsState()
                                val timeshiftWindow by repository.timeshiftWindow.collectAsState()
                                TvSettingsTab(
                                    profile = currentProfile,
                                    connectionState = connectionState,
                                    startScreen = currentStartScreen,
                                    themeMode = themeMode,
                                    hwDecPreset = hwDecPreset,
                                    timeshiftWindow = timeshiftWindow,
                                    appInfo = appInfo,
                                    focusMemory = focusMemory,
                                    focusRequestNonce = contentFocusRequestNonce,
                                    onSelectStartScreen = { screen ->
                                        scope.launch { repository.setStartScreen(screen) }
                                    },
                                    onSelectThemeMode = { mode ->
                                        scope.launch { repository.setThemeMode(mode) }
                                    },
                                    onSelectPreset = { preset ->
                                        scope.launch { repository.setHwDecPreset(preset) }
                                        playbackEngine.applyPreset(preset)
                                    },
                                    onSelectTimeshiftWindow = { window ->
                                        scope.launch { repository.setTimeshiftWindow(window) }
                                    },
                                    onRetryConnect = {
                                        focusMemory.settingsTarget = TvSettingsFocusTarget.PRIMARY
                                        repository.reconnect()
                                    },
                                    onOpenServerSetup = {
                                        focusMemory.settingsTarget = TvSettingsFocusTarget.EDIT_SERVER
                                        route = TvRoute.ONBOARDING
                                    },
                                )
                            }
                        }
                    }
                }

                TvNavigationRail(
                    activeTab = activeTab,
                    expanded = railHasFocus,
                    allowFocus = allowRailFocus,
                    focusRequestNonce = railFocusRequestNonce,
                    settingsStatusDotColor = statusDotColor,
                    onRailFocusChanged = { railHasFocus = it },
                    onSelectTab = { tab ->
                        hasDeliveredInitialContentFocus = true
                        activeTab = tab
                        contentFocusRequestNonce++
                    },
                    modifier = Modifier
                        .align(Alignment.CenterStart)
                        .zIndex(1f),
                )
            }
        }
        TvRoute.PLAYER -> {
            val playbackState by playbackEngine.state.collectAsState()
            val nowNextMap by repository.nowNextByChannel.collectAsState()
            val hwDecPreset by repository.hwDecPreset.collectAsState()
            val nowEpochSec = rememberNowEpochSec(offsetMs)

            val zapList = filteredChannels.ifEmpty { channels }
            val activeChannel = channels.firstOrNull { it.id == playbackState.channelId }
            val activeNowNext = activeChannel?.let { nowNextMap[it.id] }
            val activeRecording = remember(activeChannel?.id, dvrEntries, activeNowNext?.now?.id, activeNowNext?.now?.dvrId) {
                activeChannel?.let { ch ->
                    findActiveRecordingForChannel(ch.id, dvrEntries, activeNowNext?.now)
                }
            }

            MuxGridTheme(isTv = true, darkTheme = true) {
                TvPlayerScreen(
                    playbackEngine = playbackEngine,
                    playbackState = playbackState,
                    activeChannel = activeChannel,
                    nowNext = activeNowNext,
                    activeRecording = activeRecording,
                    channels = zapList,
                    nowNextMap = nowNextMap,
                    nowEpochSec = nowEpochSec,
                    hwDecPreset = hwDecPreset,
                    imageLoader = repository.imageLoader,
                    canZap = zapList.size > 1,
                    onSelectChannel = { channel ->
                        playbackEngine.playChannel(channel, hwDecPreset)
                    },
                    onPlayRecording = { entry, startPosSec ->
                        playbackEngine.playDvrEntry(entry, activeChannel, hwDecPreset, startPosSec)
                    },
                    onZapDelta = { delta ->
                        if (zapList.isNotEmpty()) {
                            val idx = zapList.indexOfFirst { it.id == playbackState.channelId }
                            val nextIdx = if (idx < 0) 0 else (idx + delta + zapList.size) % zapList.size
                            playbackEngine.playChannel(zapList[nextIdx], hwDecPreset)
                        }
                    },
                    onSelectPreset = { preset ->
                        scope.launch { repository.setHwDecPreset(preset) }
                        playbackEngine.applyPreset(preset)
                    },
                    onSelectAspectMode = { mode ->
                        playbackEngine.setVideoAspectMode(mode)
                    },
                    onExit = {
                        val finalChannelId = playbackState.channelId
                        val finalDvrId = playbackState.dvrEntryId
                        when (activeTab) {
                            MainTab.LIVE -> if (finalChannelId != null) {
                                focusMemory.liveChannelId = finalChannelId
                            }
                            MainTab.GUIDE -> if (finalChannelId != null && finalChannelId != focusMemory.guideTarget?.channelId) {
                                focusMemory.guideTarget = TvGuideFocusTarget(channelId = finalChannelId)
                            }
                            MainTab.RECORDINGS -> if (finalDvrId != null && finalDvrId != focusMemory.recordingTarget?.entryId) {
                                focusMemory.recordingTarget = TvRecordingFocusTarget(shelfKey = "", entryId = finalDvrId)
                            }
                            MainTab.SETTINGS -> Unit
                        }
                        playbackEngine.stop()
                        route = TvRoute.MAIN
                        contentFocusRequestNonce++
                    },
                )
            }
        }
        }

        AnimatedVisibility(
            visible = !hasExitedStartupSplash,
            enter = EnterTransition.None,
            exit = fadeOut(animationSpec = tween(durationMillis = STARTUP_SPLASH_EXIT_DURATION_MS)) +
                scaleOut(
                    targetScale = STARTUP_SPLASH_EXIT_SCALE,
                    animationSpec = tween(durationMillis = STARTUP_SPLASH_EXIT_DURATION_MS),
                ),
        ) {
            val splashStatus = when (connectionState) {
                ConnectionState.Connecting, is ConnectionState.Syncing -> "Synchronizing…"
                else -> "Connecting…"
            }
            MuxGridSplashScreen(
                backgroundColor = MuxGridColors.CanvasTv,
                statusText = splashStatus,
                showStatus = showSplashStatus && !hasExitedStartupSplash,
            )
        }
    }
}

@OptIn(ExperimentalComposeUiApi::class)
@Composable
private fun TvNavigationRail(
    activeTab: MainTab,
    expanded: Boolean,
    allowFocus: Boolean,
    focusRequestNonce: Int,
    settingsStatusDotColor: Color?,
    onRailFocusChanged: (Boolean) -> Unit,
    onSelectTab: (MainTab) -> Unit,
    modifier: Modifier = Modifier,
) {
    val railWidth by animateDpAsState(
        targetValue = if (expanded) TV_RAIL_EXPANDED_WIDTH else TV_RAIL_COLLAPSED_WIDTH,
        animationSpec = tween(durationMillis = MuxGridDurations.RailExpand),
        label = "tvRailWidth",
    )
    val labelAlpha = {
        ((railWidth - TV_RAIL_COLLAPSED_WIDTH) / (TV_RAIL_EXPANDED_WIDTH - TV_RAIL_COLLAPSED_WIDTH))
            .coerceIn(0f, 1f)
    }
    val tabFocusRequesters = remember { MainTab.entries.associateWith { FocusRequester() } }

    LaunchedEffect(focusRequestNonce) {
        if (focusRequestNonce > 0) {
            runCatching { tabFocusRequesters.getValue(activeTab).requestFocus() }
        }
    }

    Row(modifier = modifier.fillMaxHeight()) {
        Column(
            modifier = Modifier
                .width(railWidth)
                .fillMaxHeight()
                .background(MuxGridColors.CardSurfaceAlt)
                .onFocusChanged { onRailFocusChanged(it.hasFocus) }
                .focusProperties {
                    onEnter = {
                        tabFocusRequesters.getValue(activeTab).requestFocus()
                    }
                }
                .focusGroup()
                .padding(vertical = 20.dp, horizontal = 10.dp),
            verticalArrangement = Arrangement.SpaceBetween,
            horizontalAlignment = Alignment.Start,
        ) {
            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                horizontalAlignment = Alignment.Start,
            ) {
                listOf(MainTab.LIVE, MainTab.GUIDE, MainTab.RECORDINGS).forEach { tab ->
                    val selected = tab == activeTab
                    TvRailItem(
                        icon = tab.icon(selected),
                        label = tab.label(),
                        selected = selected,
                        allowFocus = allowFocus,
                        labelAlpha = labelAlpha,
                        onClick = { onSelectTab(tab) },
                        modifier = Modifier.focusRequester(tabFocusRequesters.getValue(tab)),
                    )
                }
            }

            val settingsSelected = activeTab == MainTab.SETTINGS
            TvRailItem(
                icon = MainTab.SETTINGS.icon(settingsSelected),
                label = MainTab.SETTINGS.label(),
                selected = settingsSelected,
                allowFocus = allowFocus,
                labelAlpha = labelAlpha,
                statusDotColor = settingsStatusDotColor,
                onClick = { onSelectTab(MainTab.SETTINGS) },
                modifier = Modifier.focusRequester(tabFocusRequesters.getValue(MainTab.SETTINGS)),
            )
        }
        Box(
            modifier = Modifier
                .width(MuxGridBorders.Divider)
                .fillMaxHeight()
                .background(MuxGridColors.Divider),
        )
    }
}

@Composable
private fun TvRailItem(
    icon: ImageVector,
    label: String,
    selected: Boolean,
    allowFocus: Boolean,
    labelAlpha: () -> Float,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    statusDotColor: Color? = null,
) {
    var isFocused by remember { mutableStateOf(false) }
    val containerColor = when {
        isFocused -> MuxGridColors.FocusBorder
        selected -> MuxGridColors.CardSurfaceElevated
        else -> Color.Transparent
    }
    val contentColor = when {
        isFocused -> MuxGridColors.OnFocusBorder
        selected -> MuxGridColors.TextPrimary
        else -> MuxGridColors.TextMuted
    }
    // Lightweight focusable container avoids tv.material3.Surface's SurfaceGlowNode render targets
    // Keep a single fixed-width [indicator + icon + label] row anchored at Start and clipped
    // to the animated container width so icons and spacing never jump on expand/collapse.
    Box(
        modifier = modifier
            .fillMaxWidth()
            .background(containerColor, MuxGridShapes.Sm)
            .clipToBounds()
            .focusProperties { canFocus = allowFocus }
            .onFocusChanged { if (isFocused != it.isFocused) isFocused = it.isFocused }
            .clickable(
                interactionSource = null,
                indication = null,
                onClick = onClick,
            )
            .clearAndSetSemantics {
                role = Role.Tab
                contentDescription = label
            },
        contentAlignment = Alignment.CenterStart,
    ) {
        Row(
            modifier = Modifier
                .wrapContentWidth(align = Alignment.Start, unbounded = true)
                .width(TV_RAIL_EXPANDED_WIDTH - 20.dp)
                .padding(vertical = 11.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Spacer(Modifier.width(4.dp))
            Box(
                modifier = Modifier
                    .width(3.dp)
                    .height(18.dp)
                    .background(if (selected) MuxGridColors.Accent else Color.Transparent),
            )
            Spacer(Modifier.width(6.dp))
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = icon,
                    contentDescription = label,
                    tint = contentColor,
                    modifier = Modifier.size(22.dp),
                )
                if (statusDotColor != null) {
                    ConnectionStatusDot(
                        color = statusDotColor,
                        borderColor = if (selected) MuxGridColors.CardSurfaceElevated else MuxGridColors.CardSurfaceAlt,
                        modifier = Modifier.align(Alignment.TopEnd),
                    )
                }
            }
            Spacer(Modifier.width(13.dp))
            Text(
                text = label,
                color = contentColor,
                fontSize = 14.sp,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                maxLines = 1,
                softWrap = false,
                overflow = TextOverflow.Clip,
                modifier = Modifier
                    .padding(end = 10.dp)
                    .graphicsLayer { alpha = labelAlpha() },
            )
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class, ExperimentalComposeUiApi::class, ExperimentalLayoutApi::class)
@Composable
private fun TvLiveChannelsTab(
    connectionState: ConnectionState,
    channels: List<Channel>,
    hasAnyChannels: Boolean,
    filters: List<LiveFilter>,
    activeFilter: LiveFilter,
    favorites: Set<Long>,
    nowNextMap: Map<Long, NowNextProgram>,
    dvrEntries: Map<Long, DvrEntry>,
    activeRecordingsByChannel: Map<Long, DvrEntry>,
    nowEpochSec: Long,
    imageLoader: ImageLoader,
    gridState: LazyGridState,
    focusMemory: TvFocusMemory,
    focusRequestNonce: Int,
    onSelectFilter: (LiveFilter) -> Unit,
    onChannelClick: (Channel) -> Unit,
    onPlayRecording: (DvrEntry, Channel, Long) -> Unit,
    onToggleFavorite: (Channel) -> Unit,
    onOpenGuideForChannel: (Channel) -> Unit,
    onRecordEvent: suspend (Long) -> Result<Long>,
    onStopRecording: suspend (Long) -> Result<Unit>,
    onCancelRecording: suspend (Long) -> Result<Unit>,
) {
    val scope = rememberCoroutineScope()
    val dvrIndex = remember(dvrEntries) { DvrLookupIndex(dvrEntries) }
    val activeFilterFocusRequester = remember { FocusRequester() }
    val channelFocusRequesters = remember { mutableMapOf<Long, FocusRequester>() }
    var actionChannelId by remember { mutableStateOf<Long?>(null) }
    var wasActionDialogOpen by remember { mutableStateOf(false) }
    var localFocusRestoreNonce by remember { mutableIntStateOf(0) }
    var pendingConfirm by remember { mutableStateOf<Pair<DvrConfirmKind, DvrEntry>?>(null) }
    var actionBusy by remember { mutableStateOf(false) }
    var actionError by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(actionChannelId) {
        val isOpen = actionChannelId != null
        if (!isOpen && wasActionDialogOpen) {
            localFocusRestoreNonce++
        }
        wasActionDialogOpen = isOpen
    }

    LaunchedEffect(focusRequestNonce, localFocusRestoreNonce) {
        if (focusRequestNonce == 0 && localFocusRestoreNonce == 0) return@LaunchedEffect
        if (channels.isEmpty()) {
            runCatching { activeFilterFocusRequester.requestFocus() }
            return@LaunchedEffect
        }
        val targetIndex = focusMemory.liveChannelId
            ?.let { id -> channels.indexOfFirst { it.id == id } }
            ?.takeIf { it >= 0 }
            ?: 0
        val resolvedChannelId = channels.getOrNull(targetIndex)?.id ?: return@LaunchedEffect
        val isVisible = gridState.layoutInfo.visibleItemsInfo.any { it.key == resolvedChannelId }
        if (!isVisible) {
            runCatching { gridState.scrollToItem(targetIndex) }
        }
        repeat(2) {
            withFrameNanos { }
            val requester = channelFocusRequesters[resolvedChannelId]
            if (requester != null && runCatching { requester.requestFocus() }.isSuccess) {
                return@LaunchedEffect
            }
        }
        val firstVisibleId = gridState.layoutInfo.visibleItemsInfo.firstOrNull()?.key as? Long
        val fallbackRequester = firstVisibleId?.let { channelFocusRequesters[it] }
        if (fallbackRequester != null) {
            runCatching { fallbackRequester.requestFocus() }
        } else {
            runCatching { activeFilterFocusRequester.requestFocus() }
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .focusGroup()
                .focusRestorer(activeFilterFocusRequester)
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 4.dp, vertical = 2.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            filters.forEachIndexed { idx, filter ->
                val selected = filter == activeFilter
                val isFallbackFilterTarget = selected || (idx == 0 && filters.none { it == activeFilter })
                TvFilterChip(
                    label = filter.label(),
                    selected = selected,
                    leadingIcon = filter.icon(),
                    onClick = { onSelectFilter(filter) },
                    modifier = if (isFallbackFilterTarget) {
                        Modifier.focusRequester(activeFilterFocusRequester)
                    } else {
                        Modifier
                    },
                )
            }
        }
        MuxGridDivider(modifier = Modifier.padding(horizontal = 4.dp))

        Spacer(Modifier.height(6.dp))

        if (channels.isEmpty()) {
            val (emptyText, showSpinner) = if (hasAnyChannels) {
                "No channels match this filter" to false
            } else {
                syncStatusMessage(
                    connectionState = connectionState,
                    emptyLabel = "No channels available",
                )
            }
            MuxGridSyncingState(
                message = emptyText,
                showSpinner = showSpinner,
                isTv = true,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            LazyVerticalGrid(
                state = gridState,
                columns = GridCells.Fixed(3),
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 4.dp, end = 4.dp, top = 8.dp, bottom = 24.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(
                    items = channels,
                    key = { it.id },
                    contentType = { "channel_card" },
                ) { channel ->
                    val cardFocusRequester = remember(channel.id) { FocusRequester() }
                    DisposableEffect(channel.id) {
                        channelFocusRequesters[channel.id] = cardFocusRequester
                        onDispose {
                            if (channelFocusRequesters[channel.id] === cardFocusRequester) {
                                channelFocusRequesters.remove(channel.id)
                            }
                        }
                    }
                    val activeRec = activeRecordingsByChannel[channel.id]
                    TvChannelCard(
                        channel = channel,
                        nowNext = nowNextMap[channel.id],
                        isRecording = activeRec != null,
                        nowEpochSec = nowEpochSec,
                        isFavorite = channel.id in favorites,
                        imageLoader = imageLoader,
                        focusRequester = cardFocusRequester,
                        onFocused = { focusMemory.liveChannelId = channel.id },
                        onChannelClick = onChannelClick,
                        onLongClick = { ch ->
                            focusMemory.liveChannelId = ch.id
                            actionError = null
                            actionChannelId = ch.id
                        },
                    )
                }
            }
        }
    }

    val actionChannel = actionChannelId?.let { id -> channels.firstOrNull { it.id == id } }
    if (actionChannel != null) {
        val primaryFocusRequester = remember { FocusRequester() }
        val chNowNext = nowNextMap[actionChannel.id]
        val nowEvent = chNowNext?.now
        val activeRecording = activeRecordingsByChannel[actionChannel.id]
        val linkedNowDvr = activeRecording ?: nowEvent?.let { dvrIndex.findMatch(it) }
        val isFav = actionChannel.id in favorites
        val isLiveNow = nowEvent != null && nowEpochSec in nowEvent.startEpochSec until nowEvent.stopEpochSec
        val headlineText = if (actionChannel.number > 0) {
            "${actionChannel.formattedNumber} · ${actionChannel.name}"
        } else {
            actionChannel.name
        }
        val metaText = when {
            nowEvent != null -> formatProgramModalMeta(
                channelName = nowEvent.title,
                startEpochSec = nowEvent.startEpochSec,
                stopEpochSec = nowEvent.stopEpochSec,
                nowEpochSec = nowEpochSec,
            )
            activeRecording != null -> formatProgramModalMeta(
                channelName = activeRecording.title,
                startEpochSec = activeRecording.startEpochSec,
                stopEpochSec = activeRecording.stopEpochSec,
                nowEpochSec = nowEpochSec,
                dataSizeBytes = activeRecording.dataSize,
            )
            actionChannel.isRadio -> "Live Radio"
            else -> "Live TV"
        }

        val (subtitleText, descText) = resolveModalSubtitleAndDescription(
            subtitle = nowEvent?.subtitle ?: activeRecording?.subtitle,
            description = nowEvent?.description ?: activeRecording?.description,
            summary = nowEvent?.summary,
        )

        LaunchedEffect(actionChannel.id, pendingConfirm == null) {
            if (pendingConfirm == null) {
                runCatching { primaryFocusRequester.requestFocus() }
            }
        }

        TvModalDialog(
            onDismissRequest = { if (!actionBusy) actionChannelId = null },
        ) {
            MuxGridDetailModalLayout(
                isSplitLayout = true,
                actionsColumnWidth = 224.dp,
                detailsContent = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        ChannelPiconBadge(
                            channelNumber = actionChannel.formattedNumber,
                            channelName = actionChannel.name,
                            piconModel = actionChannel.icon?.let { PiconModel(actionChannel.id, it) },
                            imageLoader = imageLoader,
                            size = 44.dp,
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = headlineText,
                                style = MuxGridType.Title,
                                fontSize = 18.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                text = metaText,
                                style = MuxGridType.TabularSmall,
                            )
                        }
                        if (linkedNowDvr != null) {
                            DvrStateBadge(state = linkedNowDvr.state, isWatched = linkedNowDvr.isWatched)
                        }
                    }

                    if (nowEvent != null && isLiveNow) {
                        EpgLiveProgressSection(
                            event = nowEvent,
                            nowEpochSec = nowEpochSec,
                            isRecording = activeRecording != null,
                        )
                    }

                    if (subtitleText != null) {
                        Text(
                            text = subtitleText,
                            style = MuxGridType.CardTitle,
                            color = MuxGridColors.AccentSoft,
                        )
                    }

                    if (descText != null) {
                        TvScrollableDescriptionBox(
                            text = descText,
                            resetKey = actionChannel.id to (nowEvent?.id ?: activeRecording?.id),
                        )
                    }

                    chNowNext?.next?.let { nextEv ->
                        Text(
                            text = "Next ${formatEpochHm(nextEv.startEpochSec)} · ${nextEv.title}",
                            style = MuxGridType.TabularSmall,
                            color = MuxGridColors.TextMuted,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }

                    actionError?.let { err ->
                        Text(err, color = MuxGridColors.CoralLive, fontSize = 13.sp)
                    }
                },
                actionsContent = {
                    Button(
                        enabled = !actionBusy,
                        onClick = {
                            actionChannelId = null
                            onChannelClick(actionChannel)
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .focusRequester(primaryFocusRequester),
                        shape = tvButtonShape(),
                        colors = tvPrimaryButtonColors(),
                    ) {
                        Icon(MuxGridIcons.Play, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Watch Live", fontWeight = FontWeight.SemiBold, maxLines = 1, softWrap = false)
                    }

                    if (activeRecording != null) {
                        Button(
                            enabled = !actionBusy,
                            onClick = {
                                actionChannelId = null
                                onPlayRecording(activeRecording, actionChannel, activeRecording.effectiveStartSec)
                            },
                            modifier = Modifier.fillMaxWidth(),
                            shape = tvButtonShape(),
                            colors = tvDialogSecondaryButtonColors(),
                        ) {
                            Icon(
                                imageVector = if (activeRecording.hasResumePosition) MuxGridIcons.Play else MuxGridIcons.Replay,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp),
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(
                                text = if (activeRecording.hasResumePosition) "Resume Chase Play" else "Watch from Start",
                                maxLines = 1,
                                softWrap = false,
                            )
                        }
                        if (activeRecording.hasResumePosition) {
                            Button(
                                enabled = !actionBusy,
                                onClick = {
                                    actionChannelId = null
                                    onPlayRecording(activeRecording, actionChannel, 0L)
                                },
                                modifier = Modifier.fillMaxWidth(),
                                shape = tvButtonShape(),
                                colors = tvDialogSecondaryButtonColors(),
                            ) {
                                Icon(MuxGridIcons.Replay, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("Start Over", maxLines = 1, softWrap = false)
                            }
                        }
                        Button(
                            enabled = !actionBusy,
                            onClick = { pendingConfirm = DvrConfirmKind.STOP_AND_SAVE to activeRecording },
                            modifier = Modifier.fillMaxWidth(),
                            shape = tvButtonShape(),
                            colors = tvDialogSecondaryButtonColors(),
                        ) {
                            Icon(MuxGridIcons.Stop, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Stop & Save", maxLines = 1, softWrap = false)
                        }
                        Button(
                            enabled = !actionBusy,
                            onClick = { pendingConfirm = DvrConfirmKind.ABORT_RECORDING to activeRecording },
                            modifier = Modifier.fillMaxWidth(),
                            shape = tvButtonShape(),
                            colors = tvDialogDestructiveButtonColors(),
                        ) {
                            Icon(MuxGridIcons.Cancel, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Abort Recording", maxLines = 1, softWrap = false)
                        }
                    } else if (linkedNowDvr?.state == DvrState.SCHEDULED) {
                        Button(
                            enabled = !actionBusy,
                            onClick = { pendingConfirm = DvrConfirmKind.CANCEL_TIMER to linkedNowDvr },
                            modifier = Modifier.fillMaxWidth(),
                            shape = tvButtonShape(),
                            colors = tvDialogDestructiveButtonColors(),
                        ) {
                            Icon(MuxGridIcons.Cancel, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Cancel Timer", maxLines = 1, softWrap = false)
                        }
                    } else if (nowEvent != null) {
                        Button(
                            enabled = !actionBusy,
                            onClick = {
                                scope.launch {
                                    actionBusy = true
                                    actionError = null
                                    try {
                                        onRecordEvent(nowEvent.id)
                                            .onSuccess { actionChannelId = null }
                                            .onFailure { actionError = it.message ?: "Failed to schedule recording" }
                                    } finally {
                                        actionBusy = false
                                    }
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                            shape = tvButtonShape(),
                            colors = tvDialogSecondaryButtonColors(),
                        ) {
                            Icon(MuxGridIcons.Record, contentDescription = null, modifier = Modifier.size(14.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Record Now", maxLines = 1, softWrap = false)
                        }
                    }

                    Button(
                        enabled = !actionBusy,
                        onClick = {
                            onToggleFavorite(actionChannel)
                            actionChannelId = null
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = tvButtonShape(),
                        colors = tvDialogSecondaryButtonColors(),
                    ) {
                        Icon(
                            imageVector = if (isFav) MuxGridIcons.StarFilled else MuxGridIcons.Star,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text = if (isFav) "Remove from Favorites" else "Add to Favorites",
                            maxLines = 1,
                            softWrap = false,
                        )
                    }

                    Button(
                        enabled = !actionBusy,
                        onClick = {
                            actionChannelId = null
                            onOpenGuideForChannel(actionChannel)
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = tvButtonShape(),
                        colors = tvDialogSecondaryButtonColors(),
                    ) {
                        Icon(MuxGridIcons.Guide, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("View in Guide", maxLines = 1, softWrap = false)
                    }

                    Button(
                        enabled = !actionBusy,
                        onClick = { actionChannelId = null },
                        modifier = Modifier.fillMaxWidth(),
                        shape = tvButtonShape(),
                        colors = tvDialogSecondaryButtonColors(),
                    ) {
                        Icon(MuxGridIcons.Close, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Close", maxLines = 1, softWrap = false)
                    }
                },
            )
        }
    }

    pendingConfirm?.let { (kind, entry) ->
        val spec = dvrConfirmSpec(kind, entry.title)
        TvConfirmDialog(
            title = spec.title,
            message = spec.message,
            confirmLabel = spec.confirmLabel,
            isDestructive = spec.isDestructive,
            enabled = !actionBusy,
            onDismiss = { pendingConfirm = null },
            onConfirm = {
                pendingConfirm = null
                scope.launch {
                    actionBusy = true
                    actionError = null
                    try {
                        val result = when (kind) {
                            DvrConfirmKind.STOP_AND_SAVE -> onStopRecording(entry.id)
                            DvrConfirmKind.ABORT_RECORDING, DvrConfirmKind.CANCEL_TIMER -> onCancelRecording(entry.id)
                            else -> Result.success(Unit)
                        }
                        result
                            .onSuccess { actionChannelId = null }
                            .onFailure { err ->
                                actionError = err.message ?: spec.failureFallbackMessage
                            }
                    } finally {
                        actionBusy = false
                    }
                }
            },
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun TvChannelCard(
    channel: Channel,
    nowNext: NowNextProgram?,
    isRecording: Boolean,
    nowEpochSec: Long,
    isFavorite: Boolean,
    imageLoader: ImageLoader,
    focusRequester: FocusRequester,
    onFocused: () -> Unit,
    onChannelClick: (Channel) -> Unit,
    onLongClick: (Channel) -> Unit,
    modifier: Modifier = Modifier,
) {
    val isFocusedState = remember { mutableStateOf(false) }
    val piconModel = remember(channel.id, channel.icon) {
        channel.icon?.let { PiconModel(channel.id, it) }
    }
    val nextProgram = nowNext?.next
    val nextLabel = remember(nextProgram?.id, nextProgram?.startEpochSec, nextProgram?.title) {
        nextProgram?.let { "Next ${formatEpochHm(it.startEpochSec)} · ${it.title}" } ?: ""
    }
    val handleClick = remember(channel, onChannelClick) { { onChannelClick(channel) } }
    val handleLongClick = remember(channel, onLongClick) { { onLongClick(channel) } }
    val now = nowNext?.now
    val cardDescription = remember(channel.formattedNumber, channel.name, now?.title) {
        if (now != null) "${channel.formattedNumber} ${channel.name}, ${now.title}" else "${channel.formattedNumber} ${channel.name}"
    }

    // Draw-phase focus styling avoids recomposition, modifier churn, and rounded-rect clip layers on dense grids
    Column(
        modifier = modifier
            .fillMaxWidth()
            .tvFocusableSurface(
                focusedState = isFocusedState,
                cornerRadius = MuxGridRadii.Card,
                containerColor = MuxGridColors.CardSurface,
                focusedContainerColor = MuxGridColors.CardSurfaceElevated,
                focusBorderColor = MuxGridColors.FocusBorder,
                focusBorderWidth = MuxGridBorders.FocusedCard,
                focusedScale = 1.015f,
            )
            .focusRequester(focusRequester)
            .onFocusChanged { state ->
                val focused = state.isFocused
                if (isFocusedState.value != focused) {
                    isFocusedState.value = focused
                }
                if (focused) {
                    onFocused()
                }
            }
            .combinedClickable(
                interactionSource = null,
                indication = null,
                onClick = handleClick,
                onLongClick = handleLongClick,
            )
            .clearAndSetSemantics {
                role = Role.Button
                contentDescription = cardDescription
            }
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(9.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            ChannelPiconBadge(
                channelNumber = channel.formattedNumber,
                channelName = channel.name,
                piconModel = piconModel,
                imageLoader = imageLoader,
                size = 48.dp,
            )
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    if (channel.number > 0) {
                        Text(
                            text = channel.formattedNumber,
                            style = MuxGridType.TabularNumber,
                            color = MuxGridColors.TextMuted,
                        )
                    }
                    if (isFavorite) {
                        Icon(
                            imageVector = MuxGridIcons.StarFilled,
                            contentDescription = null,
                            tint = MuxGridColors.AmberWarn,
                            modifier = Modifier.size(14.dp),
                        )
                    }
                    if (isRecording) {
                        DvrStateBadge(state = DvrState.RECORDING)
                    }
                }
                Text(
                    text = channel.name,
                    style = MuxGridType.CardTitle,
                    fontSize = 15.5.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }

        if (now != null) {
            Text(
                text = now.title,
                style = MuxGridType.CardSubtitle,
                color = MuxGridColors.TextPrimary.copy(alpha = 0.9f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            LiveProgressBar(
                progress = nowNext.progressFraction(nowEpochSec),
                modifier = Modifier.fillMaxWidth(),
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = nextLabel,
                    style = MuxGridType.TabularSmall,
                    color = MuxGridColors.TextMuted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                nowNext.remainingMinutes(nowEpochSec)?.let { mins ->
                    Spacer(Modifier.width(8.dp))
                    Text("${mins}m", style = MuxGridType.TabularSmall)
                }
            }
        } else {
            Text(
                text = if (channel.isRadio) "Live Radio" else "Live Stream",
                style = MuxGridType.CardSubtitle,
                color = MuxGridColors.TextMuted,
            )
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class, ExperimentalComposeUiApi::class)
@Composable
private fun TvSettingsTab(
    profile: ServerProfile,
    connectionState: ConnectionState,
    startScreen: StartScreen,
    themeMode: ThemeMode,
    hwDecPreset: HwDecPreset,
    timeshiftWindow: TimeshiftWindow,
    appInfo: AppBuildInfo,
    focusMemory: TvFocusMemory,
    focusRequestNonce: Int,
    onSelectStartScreen: (StartScreen) -> Unit,
    onSelectThemeMode: (ThemeMode) -> Unit,
    onSelectPreset: (HwDecPreset) -> Unit,
    onSelectTimeshiftWindow: (TimeshiftWindow) -> Unit,
    onRetryConnect: () -> Unit,
    onOpenServerSetup: () -> Unit,
) {
    val uriHandler = LocalUriHandler.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var debugReport by remember { mutableStateOf<String?>(null) }
    var showLicenses by remember { mutableStateOf(false) }
    val isServerError = connectionState is ConnectionState.Error
    val canRetryConnect = isServerError || connectionState is ConnectionState.Disconnected
    val reconnectFocusRequester = remember { FocusRequester() }
    val editServerFocusRequester = remember { FocusRequester() }

    LaunchedEffect(focusRequestNonce) {
        if (focusRequestNonce == 0) return@LaunchedEffect
        withFrameNanos { }
        val primary = when {
            focusMemory.settingsTarget == TvSettingsFocusTarget.EDIT_SERVER -> editServerFocusRequester
            canRetryConnect -> reconnectFocusRequester
            else -> editServerFocusRequester
        }
        if (runCatching { primary.requestFocus() }.isFailure) {
            runCatching { editServerFocusRequester.requestFocus() }
        }
    }

    val onOpenUrl: (String) -> Unit = { url ->
        runCatching {
            uriHandler.openUri(url)
        }.onFailure {
            Toast.makeText(context, url, Toast.LENGTH_LONG).show()
        }
    }
    val onOpenGithub: () -> Unit = { onOpenUrl(appInfo.githubUrl) }
    val onOpenLicenses: () -> Unit = { showLicenses = true }
    val onOpenDebugInfo: () -> Unit = {
        scope.launch {
            debugReport = withContext(Dispatchers.IO) {
                formatDebugInfoReport(
                    collectDebugEnvironmentSnapshot(
                        context = context,
                        appInfo = appInfo,
                        isTv = true,
                        connectionState = connectionState,
                        hwDecPreset = hwDecPreset,
                        timeshiftWindow = timeshiftWindow,
                        themeMode = themeMode,
                    ),
                )
            }
        }
    }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val useTwoColumns = maxWidth >= 680.dp
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(start = 4.dp, end = 4.dp, top = 8.dp, bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            if (useTwoColumns) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalAlignment = Alignment.Top,
                ) {
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .clampVerticalFocusExit(blockUp = true, blockDown = true),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        TvServerSettingsCard(
                            profile = profile,
                            connectionState = connectionState,
                            isServerError = isServerError,
                            canRetryConnect = canRetryConnect,
                            reconnectFocusRequester = reconnectFocusRequester,
                            editServerFocusRequester = editServerFocusRequester,
                            onRetryConnect = onRetryConnect,
                            onOpenServerSetup = onOpenServerSetup,
                        )
                        TvGeneralSettingsCard(
                            startScreen = startScreen,
                            themeMode = themeMode,
                            onSelectStartScreen = onSelectStartScreen,
                            onSelectThemeMode = onSelectThemeMode,
                        )
                    }
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .clampVerticalFocusExit(blockUp = false, blockDown = true),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        TvDecoderTimeshiftSettingsCard(
                            hwDecPreset = hwDecPreset,
                            timeshiftWindow = timeshiftWindow,
                            onSelectPreset = onSelectPreset,
                            onSelectTimeshiftWindow = onSelectTimeshiftWindow,
                        )
                        TvAppInfoSettingsCard(
                            appInfo = appInfo,
                            onOpenGithub = onOpenGithub,
                            onOpenLicenses = onOpenLicenses,
                            onOpenDebugInfo = onOpenDebugInfo,
                        )
                    }
                }
            } else {
                TvServerSettingsCard(
                    profile = profile,
                    connectionState = connectionState,
                    isServerError = isServerError,
                    canRetryConnect = canRetryConnect,
                    reconnectFocusRequester = reconnectFocusRequester,
                    editServerFocusRequester = editServerFocusRequester,
                    onRetryConnect = onRetryConnect,
                    onOpenServerSetup = onOpenServerSetup,
                )
                TvGeneralSettingsCard(
                    startScreen = startScreen,
                    themeMode = themeMode,
                    onSelectStartScreen = onSelectStartScreen,
                    onSelectThemeMode = onSelectThemeMode,
                )
                TvDecoderTimeshiftSettingsCard(
                    hwDecPreset = hwDecPreset,
                    timeshiftWindow = timeshiftWindow,
                    onSelectPreset = onSelectPreset,
                    onSelectTimeshiftWindow = onSelectTimeshiftWindow,
                )
                TvAppInfoSettingsCard(
                    appInfo = appInfo,
                    onOpenGithub = onOpenGithub,
                    onOpenLicenses = onOpenLicenses,
                    onOpenDebugInfo = onOpenDebugInfo,
                )
            }
        }
    }

    debugReport?.let { report ->
        val copyFocusRequester = remember { FocusRequester() }
        val reportScrollState = rememberScrollState()
        var isReportFocused by remember { mutableStateOf(false) }
        val scrollStepPx = with(LocalDensity.current) { 72.dp.toPx() }
        LaunchedEffect(report) {
            reportScrollState.scrollTo(0)
            runCatching { copyFocusRequester.requestFocus() }
        }
        TvModalDialog(
            onDismissRequest = { debugReport = null },
            minWidth = 500.dp,
            maxWidth = 700.dp,
        ) {
            Text(
                text = "Debug Info",
                style = MuxGridType.Title,
                fontSize = 18.sp,
                color = MuxGridColors.TextPrimary,
            )
            Text(
                text = "Non-personal hardware and software environment for issue reports.",
                style = MuxGridType.CardSubtitle,
                fontSize = 13.sp,
                color = MuxGridColors.TextSecondary,
            )
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 300.dp)
                    .clip(MuxGridShapes.Sm)
                    .background(MuxGridColors.CardSurface)
                    .border(
                        width = if (isReportFocused) MuxGridBorders.Focused else MuxGridBorders.Divider,
                        color = if (isReportFocused) MuxGridColors.FocusBorder else MuxGridColors.HairlineBorder,
                        shape = MuxGridShapes.Sm,
                    )
                    .onFocusChanged { isReportFocused = it.isFocused }
                    .onPreviewKeyEvent { event ->
                        if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                        when (event.key) {
                            Key.DirectionDown -> {
                                if (!reportScrollState.canScrollForward) return@onPreviewKeyEvent false
                                scope.launch { reportScrollState.scrollBy(scrollStepPx) }
                                true
                            }
                            Key.DirectionUp -> {
                                if (!reportScrollState.canScrollBackward) return@onPreviewKeyEvent false
                                scope.launch { reportScrollState.scrollBy(-scrollStepPx) }
                                true
                            }
                            else -> false
                        }
                    }
                    .focusable()
                    .verticalScroll(reportScrollState)
                    .padding(12.dp),
            ) {
                Text(
                    text = report,
                    fontSize = 12.sp,
                    lineHeight = 17.sp,
                    fontFamily = FontFamily.Monospace,
                    color = MuxGridColors.TextPrimary,
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.End),
            ) {
                Button(
                    onClick = { debugReport = null },
                    shape = tvButtonShape(),
                    colors = tvDialogSecondaryButtonColors(),
                ) {
                    Text("Close", maxLines = 1, softWrap = false)
                }
                Button(
                    onClick = {
                        copyTextToClipboard(
                            context = context,
                            label = "MuxGrid Debug Info",
                            text = report,
                            toastMessage = "Debug info copied",
                        )
                    },
                    modifier = Modifier.focusRequester(copyFocusRequester),
                    shape = tvButtonShape(),
                    colors = tvPrimaryButtonColors(),
                ) {
                    Text("Copy", fontWeight = FontWeight.SemiBold, maxLines = 1, softWrap = false)
                }
            }
        }
    }

    if (showLicenses) {
        TvLicensesDialog(
            onDismiss = { showLicenses = false },
            onOpenUrl = onOpenUrl,
        )
    }
}

@Composable
private fun TvServerSettingsCard(
    profile: ServerProfile,
    connectionState: ConnectionState,
    isServerError: Boolean,
    canRetryConnect: Boolean,
    reconnectFocusRequester: FocusRequester,
    editServerFocusRequester: FocusRequester,
    onRetryConnect: () -> Unit,
    onOpenServerSetup: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(MuxGridShapes.Card)
            .background(MuxGridColors.CardSurface)
            .border(
                width = if (isServerError) MuxGridBorders.Hairline else MuxGridBorders.Divider,
                color = if (isServerError) MuxGridColors.CoralLive.copy(alpha = MuxGridAlphas.BorderSubtle) else MuxGridColors.HairlineBorder,
                shape = MuxGridShapes.Card,
            )
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = "TVHEADEND SERVER",
            style = MuxGridType.Eyebrow,
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(end = 16.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    text = serverHeadline(profile, connectionState),
                    style = MuxGridType.TabularNumber,
                    fontSize = 15.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = connectionStatusText(profile, connectionState),
                    style = MuxGridType.TabularSmall,
                    color = connectionStatusColor(connectionState),
                    maxLines = if (isServerError) 3 else 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (canRetryConnect) {
                    TvIconButton(
                        onClick = onRetryConnect,
                        modifier = Modifier.focusRequester(reconnectFocusRequester),
                        size = 44.dp,
                        containerColor = MuxGridColors.CardSurfaceElevated,
                        contentColor = MuxGridColors.AccentSoft,
                    ) {
                        Icon(
                            imageVector = MuxGridIcons.Refresh,
                            contentDescription = "Reconnect",
                            modifier = Modifier.size(20.dp),
                        )
                    }
                }
                TvIconButton(
                    onClick = onOpenServerSetup,
                    modifier = Modifier.focusRequester(editServerFocusRequester),
                    size = 44.dp,
                    containerColor = MuxGridColors.CardSurfaceElevated,
                    contentColor = MuxGridColors.TextPrimary,
                ) {
                    Icon(
                        imageVector = MuxGridIcons.Edit,
                        contentDescription = "Edit Server",
                        modifier = Modifier.size(20.dp),
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalComposeUiApi::class)
@Composable
private fun TvGeneralSettingsCard(
    startScreen: StartScreen,
    themeMode: ThemeMode,
    onSelectStartScreen: (StartScreen) -> Unit,
    onSelectThemeMode: (ThemeMode) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(MuxGridShapes.Card)
            .background(MuxGridColors.CardSurface)
            .border(MuxGridBorders.Divider, MuxGridColors.HairlineBorder, MuxGridShapes.Card)
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(
            text = "GENERAL",
            style = MuxGridType.Eyebrow,
        )

        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                text = "Start Screen",
                style = MuxGridType.CardSubtitle,
                fontSize = 13.sp,
            )
            Row(
                modifier = Modifier
                    .focusGroup()
                    .focusRestorer()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                StartScreen.entries.forEach { option ->
                    TvUnderlineOption(
                        label = option.label,
                        selected = startScreen == option,
                        onClick = { onSelectStartScreen(option) },
                    )
                }
            }
            Text(
                text = startScreen.description,
                style = MuxGridType.TabularSmall,
                color = MuxGridColors.TextMuted,
            )
        }

        MuxGridDivider()

        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                text = "Theme",
                style = MuxGridType.CardSubtitle,
                fontSize = 13.sp,
            )
            Row(
                modifier = Modifier
                    .focusGroup()
                    .focusRestorer()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                ThemeMode.entries.forEach { option ->
                    TvUnderlineOption(
                        label = option.label,
                        selected = themeMode == option,
                        onClick = { onSelectThemeMode(option) },
                    )
                }
            }
            Text(
                text = themeMode.description,
                style = MuxGridType.TabularSmall,
                color = MuxGridColors.TextMuted,
            )
        }
    }
}

@OptIn(ExperimentalComposeUiApi::class)
@Composable
private fun TvDecoderTimeshiftSettingsCard(
    hwDecPreset: HwDecPreset,
    timeshiftWindow: TimeshiftWindow,
    onSelectPreset: (HwDecPreset) -> Unit,
    onSelectTimeshiftWindow: (TimeshiftWindow) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(MuxGridShapes.Card)
            .background(MuxGridColors.CardSurface)
            .border(MuxGridBorders.Divider, MuxGridColors.HairlineBorder, MuxGridShapes.Card)
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(
            text = "VIDEO DECODER & TIMESHIFT",
            style = MuxGridType.Eyebrow,
        )

        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                text = "Hardware Decode Preset",
                style = MuxGridType.CardSubtitle,
                fontSize = 13.sp,
            )
            Row(
                modifier = Modifier
                    .focusGroup()
                    .focusRestorer()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                listOf(
                    HwDecPreset.MEDIACODEC_ZERO_COPY to "Zero-Copy (TV Default)",
                    HwDecPreset.MEDIACODEC_COPY_BWDIF to "Copy + bwdif",
                ).forEach { (preset, label) ->
                    TvUnderlineOption(
                        label = label,
                        selected = hwDecPreset == preset,
                        onClick = { onSelectPreset(preset) },
                    )
                }
            }
        }

        MuxGridDivider()

        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                text = "Live Timeshift Cache Limit",
                style = MuxGridType.CardSubtitle,
                fontSize = 13.sp,
            )
            Row(
                modifier = Modifier
                    .focusGroup()
                    .focusRestorer()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                TimeshiftWindow.entries.forEach { option ->
                    TvUnderlineOption(
                        label = option.shortLabel,
                        selected = timeshiftWindow == option,
                        onClick = { onSelectTimeshiftWindow(option) },
                    )
                }
            }
            Text(
                text = "${timeshiftWindow.description} · split pause/rewind · applies on next tune",
                style = MuxGridType.TabularSmall,
                color = MuxGridColors.TextMuted,
            )
        }
    }
}

@Composable
private fun TvAppInfoSettingsCard(
    appInfo: AppBuildInfo,
    onOpenGithub: () -> Unit,
    onOpenLicenses: () -> Unit,
    onOpenDebugInfo: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(MuxGridShapes.Card)
            .background(MuxGridColors.CardSurface)
            .border(MuxGridBorders.Divider, MuxGridColors.HairlineBorder, MuxGridShapes.Card)
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text(
            text = "APP INFO",
            style = MuxGridType.Eyebrow,
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(32.dp),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = "Version",
                    style = MuxGridType.CardSubtitle,
                    fontSize = 12.5.sp,
                )
                Text(
                    text = appInfo.versionDisplay,
                    style = MuxGridType.TabularNumber,
                    fontSize = 14.5.sp,
                )
            }
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = "Build SHA",
                    style = MuxGridType.CardSubtitle,
                    fontSize = 12.5.sp,
                )
                Text(
                    text = appInfo.shortSha,
                    style = MuxGridType.TabularNumber,
                    fontSize = 14.5.sp,
                )
            }
        }

        MuxGridDivider()

        TvSettingsActionRow(
            title = "GitHub",
            subtitle = appInfo.githubDisplayUrl,
            contentDescription = "Open GitHub",
            onClick = onOpenGithub,
        )

        TvSettingsActionRow(
            title = "Licenses & Notices",
            subtitle = "Open-source attributions · GPL-3.0-or-later",
            contentDescription = "Show Licenses and Notices",
            onClick = onOpenLicenses,
        )

        TvSettingsActionRow(
            title = "Debug Info",
            subtitle = "Device, OS, and playback environment",
            contentDescription = "Show Debug Info",
            onClick = onOpenDebugInfo,
        )
    }
}

@Composable
private fun TvLicensesDialog(
    onDismiss: () -> Unit,
    onOpenUrl: (String) -> Unit,
) {
    val scope = rememberCoroutineScope()
    var selectedAttribution by remember { mutableStateOf<AppAttribution?>(null) }
    var documentText by remember { mutableStateOf("") }
    var isTextFocused by remember { mutableStateOf(false) }
    val firstRowFocusRequester = remember { FocusRequester() }
    val textFocusRequester = remember { FocusRequester() }
    val textScrollState = rememberScrollState()
    val scrollStepPx = with(LocalDensity.current) { 72.dp.toPx() }

    LaunchedEffect(selectedAttribution) {
        val selected = selectedAttribution
        if (selected == null) {
            documentText = ""
            runCatching { firstRowFocusRequester.requestFocus() }
            return@LaunchedEffect
        }
        documentText = ""
        textScrollState.scrollTo(0)
        documentText = withContext(Dispatchers.IO) { AppNotices.readLicenseText(selected.document) }
        runCatching { textFocusRequester.requestFocus() }
    }

    BackHandler(enabled = selectedAttribution != null) { selectedAttribution = null }

    TvModalDialog(
        onDismissRequest = onDismiss,
        minWidth = 520.dp,
        maxWidth = 720.dp,
    ) {
        val current = selectedAttribution
        if (current == null) {
            Text(
                text = "Licenses & Notices",
                style = MuxGridType.Title,
                fontSize = 18.sp,
                color = MuxGridColors.TextPrimary,
            )
            Text(
                text = AppNotices.DISTRIBUTION_NOTE,
                style = MuxGridType.CardSubtitle,
                fontSize = 13.sp,
                color = MuxGridColors.TextSecondary,
            )
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 300.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                AppNotices.attributions.forEachIndexed { index, entry ->
                    TvSettingsActionRow(
                        title = entry.name,
                        subtitle = "${entry.license} · ${entry.displayUrl}",
                        contentDescription = "Show ${entry.name} license",
                        onClick = { selectedAttribution = entry },
                        modifier = if (index == 0) Modifier.focusRequester(firstRowFocusRequester) else Modifier,
                    )
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.End),
            ) {
                Button(
                    onClick = onDismiss,
                    shape = tvButtonShape(),
                    colors = tvDialogSecondaryButtonColors(),
                ) {
                    Text("Close", maxLines = 1, softWrap = false)
                }
            }
        } else {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    Text(
                        text = current.name,
                        style = MuxGridType.Title,
                        fontSize = 18.sp,
                        color = MuxGridColors.TextPrimary,
                    )
                    Text(
                        text = current.license,
                        style = MuxGridType.CardSubtitle,
                        fontSize = 13.sp,
                        color = MuxGridColors.TextSecondary,
                    )
                }
                TvIconButton(
                    onClick = { onOpenUrl(current.url) },
                    size = 40.dp,
                    containerColor = MuxGridColors.CardSurfaceElevated,
                    contentColor = MuxGridColors.TextPrimary,
                ) {
                    Icon(
                        imageVector = MuxGridIcons.OpenInNew,
                        contentDescription = "Open ${current.name} website",
                        modifier = Modifier.size(18.dp),
                    )
                }
            }
            Text(
                text = "Press BACK or ▼ to leave the text.",
                style = MuxGridType.TabularSmall,
                color = MuxGridColors.TextMuted,
            )
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 300.dp)
                    .clip(MuxGridShapes.Sm)
                    .background(MuxGridColors.CardSurface)
                    .border(
                        width = if (isTextFocused) MuxGridBorders.Focused else MuxGridBorders.Divider,
                        color = if (isTextFocused) MuxGridColors.FocusBorder else MuxGridColors.HairlineBorder,
                        shape = MuxGridShapes.Sm,
                    )
                    .focusRequester(textFocusRequester)
                    .onFocusChanged { isTextFocused = it.isFocused }
                    .onPreviewKeyEvent { event ->
                        if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                        when (event.key) {
                            Key.DirectionDown -> {
                                if (!textScrollState.canScrollForward) return@onPreviewKeyEvent false
                                scope.launch { textScrollState.scrollBy(scrollStepPx) }
                                true
                            }
                            Key.DirectionUp -> {
                                if (!textScrollState.canScrollBackward) return@onPreviewKeyEvent false
                                scope.launch { textScrollState.scrollBy(-scrollStepPx) }
                                true
                            }
                            else -> false
                        }
                    }
                    .focusable()
                    .verticalScroll(textScrollState)
                    .padding(12.dp),
            ) {
                Text(
                    text = documentText.ifBlank { "Loading..." },
                    fontSize = 12.sp,
                    lineHeight = 17.sp,
                    fontFamily = FontFamily.Monospace,
                    color = MuxGridColors.TextPrimary,
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
            ) {
                Button(
                    onClick = { selectedAttribution = null },
                    shape = tvButtonShape(),
                    colors = tvDialogSecondaryButtonColors(),
                ) {
                    Text("Back", maxLines = 1, softWrap = false)
                }
            }
        }
    }
}

private fun Modifier.clampVerticalFocusExit(
    blockUp: Boolean,
    blockDown: Boolean,
): Modifier = focusProperties {
    onExit = {
        if ((blockUp && requestedFocusDirection == FocusDirection.Up) ||
            (blockDown && requestedFocusDirection == FocusDirection.Down)
        ) {
            cancelFocusChange()
        }
    }
}.focusGroup()

@Composable
private fun TvIconButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = 38.dp,
    containerColor: Color = Color.Transparent,
    contentColor: Color = MuxGridColors.TextSecondary,
    content: @Composable () -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    val bg = if (focused) MuxGridColors.FocusBorder else containerColor
    val fg = if (focused) MuxGridColors.OnFocusBorder else contentColor
    Box(
        modifier = modifier
            .size(size)
            .background(bg, MuxGridShapes.Sm)
            .onFocusChanged { if (focused != it.isFocused) focused = it.isFocused }
            .clickable(
                interactionSource = null,
                indication = null,
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        androidx.compose.runtime.CompositionLocalProvider(LocalContentColor provides fg) {
            content()
        }
    }
}

@Composable
private fun TvSettingsActionRow(
    title: String,
    subtitle: String,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        shape = ClickableSurfaceDefaults.shape(MuxGridShapes.Sm),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = MuxGridColors.CardSurfaceElevated,
            contentColor = MuxGridColors.TextPrimary,
            focusedContainerColor = MuxGridColors.FocusBorder,
            focusedContentColor = MuxGridColors.OnFocusBorder,
        ),
        border = ClickableSurfaceDefaults.border(
            border = Border.None,
            focusedBorder = Border(
                border = BorderStroke(MuxGridBorders.Focused, MuxGridColors.FocusBorder),
                shape = MuxGridShapes.Sm,
            ),
        ),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.015f),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
                Text(
                    text = title,
                    fontSize = 13.5.sp,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = subtitle,
                    fontSize = 12.sp,
                    color = LocalContentColor.current.copy(alpha = MuxGridAlphas.SecondaryText),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Icon(
                imageVector = MuxGridIcons.ChevronRight,
                contentDescription = contentDescription,
                modifier = Modifier.size(18.dp),
            )
        }
    }
}

@Composable
private fun TvUnderlineOption(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        shape = ClickableSurfaceDefaults.shape(MuxGridShapes.Sm),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = Color.Transparent,
            contentColor = if (selected) MuxGridColors.TextPrimary else MuxGridColors.TextMuted,
            focusedContainerColor = MuxGridColors.FocusBorder,
            focusedContentColor = MuxGridColors.OnFocusBorder,
        ),
        border = ClickableSurfaceDefaults.border(
            border = Border.None,
            focusedBorder = Border.None,
        ),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.04f),
    ) {
        Column(
            modifier = Modifier
                .width(IntrinsicSize.Max)
                .padding(horizontal = 8.dp, vertical = 6.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = label,
                fontSize = 13.5.sp,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                maxLines = 1,
                softWrap = false,
            )
            Spacer(Modifier.height(5.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(2.dp)
                    .background(if (selected) MuxGridColors.Accent else Color.Transparent),
            )
        }
    }
}
