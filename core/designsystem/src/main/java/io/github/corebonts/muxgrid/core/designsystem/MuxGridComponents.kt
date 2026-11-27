package io.github.corebonts.muxgrid.core.designsystem

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.res.Configuration
import android.widget.Toast
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import io.github.corebonts.muxgrid.core.model.ThemeMode
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.util.lerp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.roundToInt
import coil3.ImageLoader
import coil3.compose.AsyncImage
import io.github.corebonts.muxgrid.core.model.Channel
import io.github.corebonts.muxgrid.core.model.ChannelTag
import io.github.corebonts.muxgrid.core.model.ConnectionState
import io.github.corebonts.muxgrid.core.model.DvrState
import io.github.corebonts.muxgrid.core.model.EpgEvent
import io.github.corebonts.muxgrid.core.model.HwDecPreset
import io.github.corebonts.muxgrid.core.model.LiveFilter
import io.github.corebonts.muxgrid.core.model.MainTab
import io.github.corebonts.muxgrid.core.model.MediaTrack
import io.github.corebonts.muxgrid.core.model.PlaybackError
import io.github.corebonts.muxgrid.core.model.VideoAspectMode
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.math.abs

@Composable
fun MuxGridDivider(
    modifier: Modifier = Modifier,
    color: Color = MuxGridColors.Divider,
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(MuxGridBorders.Divider)
            .background(color),
    )
}

/**
 * Draws the shared 1dp top specular rim highlight inside rounded surfaces (prominent on Aurora
 * frosted glass, subtle on Carbon/Amber/Indigo).
 */
fun Modifier.muxGridTopSpecularHighlight(
    cornerRadius: Dp,
    highlightColor: Color,
): Modifier = if (highlightColor.alpha <= 0.005f) {
    this
} else {
    drawWithContent {
        drawContent()
        val inset = cornerRadius.toPx().coerceAtMost(size.width * 0.25f)
        val stroke = 1.dp.toPx()
        drawLine(
            color = highlightColor,
            start = Offset(inset, stroke * 0.5f),
            end = Offset((size.width - inset).coerceAtLeast(inset), stroke * 0.5f),
            strokeWidth = stroke,
        )
    }
}

/**
 * Applies the unified card container chrome (12dp shape, surface fill, 1dp outer border, and
 * top specular highlight).
 */
@Composable
fun Modifier.muxGridCardSurface(
    shape: Shape = MuxGridShapes.Card,
    cornerRadius: Dp = MuxGridRadii.Card,
    containerColor: Color = MuxGridColors.CardSurface,
    borderColor: Color = MuxGridColors.CardBorder,
    topHighlightColor: Color = MuxGridColors.CardTopHighlight,
): Modifier = this
    .clip(shape)
    .background(containerColor)
    .border(MuxGridBorders.Hairline, borderColor, shape)
    .muxGridTopSpecularHighlight(cornerRadius, topHighlightColor)

@Composable
@ReadOnlyComposable
fun muxGridAccentBrush(
    startColor: Color = MuxGridColors.Accent,
    endColor: Color = MuxGridColors.AccentGradientEnd,
): Brush = if (startColor == endColor) {
    SolidColor(startColor)
} else {
    Brush.horizontalGradient(listOf(startColor, endColor))
}

@Composable
fun MuxGridPrimaryActionButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    leadingIcon: ImageVector? = null,
    enabled: Boolean = true,
) {
    val start = MuxGridColors.Accent
    val end = MuxGridColors.AccentGradientEnd
    val alpha = if (enabled) 1f else MuxGridAlphas.Disabled
    val shape = MuxGridShapes.Control
    Row(
        modifier = modifier
            .heightIn(min = 42.dp)
            .clip(shape)
            .background(
                brush = if (start == end) {
                    SolidColor(start.copy(alpha = start.alpha * alpha))
                } else {
                    Brush.horizontalGradient(
                        listOf(
                            start.copy(alpha = start.alpha * alpha),
                            end.copy(alpha = end.alpha * alpha),
                        ),
                    )
                },
            )
            .then(if (enabled) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (leadingIcon != null) {
            Icon(
                imageVector = leadingIcon,
                contentDescription = null,
                tint = MuxGridColors.OnAccent.copy(alpha = alpha),
                modifier = Modifier.size(18.dp),
            )
            Spacer(Modifier.width(6.dp))
        }
        Text(
            text = text,
            color = MuxGridColors.OnAccent.copy(alpha = alpha),
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            softWrap = false,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
fun MuxGridSecondaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    leadingIcon: ImageVector? = null,
    contentColor: Color = MuxGridColors.TextPrimary,
    containerColor: Color = MuxGridColors.ButtonSecondaryContainer,
    borderColor: Color = MuxGridColors.ButtonSecondaryBorder,
    enabled: Boolean = true,
) {
    OutlinedButton(
        onClick = onClick,
        enabled = enabled,
        shape = MuxGridShapes.Control,
        colors = ButtonDefaults.outlinedButtonColors(
            containerColor = containerColor,
            contentColor = contentColor,
            disabledContainerColor = containerColor.copy(alpha = MuxGridAlphas.Disabled),
            disabledContentColor = contentColor.copy(alpha = MuxGridAlphas.Disabled),
        ),
        border = BorderStroke(
            width = MuxGridBorders.Hairline,
            color = if (enabled) borderColor else borderColor.copy(alpha = MuxGridAlphas.Disabled),
        ),
        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 10.dp),
        modifier = modifier.heightIn(min = 42.dp),
    ) {
        if (leadingIcon != null) {
            Icon(
                imageVector = leadingIcon,
                contentDescription = null,
                tint = if (enabled) contentColor else contentColor.copy(alpha = MuxGridAlphas.Disabled),
                modifier = Modifier.size(16.dp),
            )
            Spacer(Modifier.width(6.dp))
        }
        Text(
            text = text,
            color = if (enabled) contentColor else contentColor.copy(alpha = MuxGridAlphas.Disabled),
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            softWrap = false,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/**
 * Shared segmented pill control for 2-5 short options across Mobile and Android TV Settings & HUD panels.
 * Measures options symmetrically so items fill the bar cleanly when they fit, and gracefully
 * enables horizontal scrolling if localized labels exceed equal column widths.
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun <T> MuxGridSegmentedControl(
    options: List<T>,
    selectedOption: T,
    labelFor: @Composable (T) -> String,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
    isTv: Boolean = false,
) {
    val activeBg = MuxGridColors.SegmentedActiveBg
    val activeFg = MuxGridColors.SegmentedActiveFg
    val activeBorder = MuxGridColors.SegmentedActiveBorder
    val focusBg = MuxGridColors.FocusBorder
    val focusFg = MuxGridColors.OnFocusBorder
    val inactiveFg = MuxGridColors.TextSecondary
    val scrollState = rememberScrollState()

    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .clip(MuxGridShapes.Control)
            .background(MuxGridColors.SegmentedTrack)
            .border(MuxGridBorders.Hairline, MuxGridColors.CardBorder, MuxGridShapes.Control)
            .then(
                if (isTv) {
                    Modifier
                        .focusGroup()
                        .focusRestorer()
                } else {
                    Modifier
                },
            )
            .padding(3.dp),
    ) {
        val count = options.size.coerceAtLeast(1)
        val gap = 3.dp
        val slotMinWidth = ((maxWidth - gap * (count - 1)) / count).coerceAtLeast(48.dp)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(scrollState),
            horizontalArrangement = Arrangement.spacedBy(gap),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            options.forEach { option ->
                val selected = option == selectedOption
                val label = labelFor(option)
                var focused by remember(option) { mutableStateOf(false) }
                val bg = when {
                    isTv && focused -> focusBg
                    selected -> activeBg
                    else -> Color.Transparent
                }
                val fg = when {
                    isTv && focused -> focusFg
                    selected -> activeFg
                    else -> inactiveFg
                }
                val segmentShape = RoundedCornerShape(6.dp)
                Box(
                    modifier = Modifier
                        .widthIn(min = slotMinWidth)
                        .clip(segmentShape)
                        .background(bg)
                        .then(
                            if (selected && !(isTv && focused)) {
                                Modifier.border(MuxGridBorders.Hairline, activeBorder, segmentShape)
                            } else {
                                Modifier
                            },
                        )
                        .then(
                            if (isTv) {
                                Modifier
                                    .onFocusChanged { if (focused != it.isFocused) focused = it.isFocused }
                                    .clickable(
                                        interactionSource = null,
                                        indication = null,
                                        onClick = { onSelect(option) },
                                    )
                            } else {
                                Modifier.clickable(onClick = { onSelect(option) })
                            },
                        )
                        .padding(horizontal = 10.dp, vertical = 7.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = label,
                        color = fg,
                        fontSize = 12.sp,
                        fontWeight = if (selected || (isTv && focused)) FontWeight.SemiBold else FontWeight.Medium,
                        maxLines = 1,
                        softWrap = false,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

/**
 * Shared 4-theme visual swatch picker (`Carbon`, `Amber`, `Aurora`, `Indigo`) for Mobile & TV Settings.
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun MuxGridThemeSwatchPicker(
    selectedTheme: ThemeMode,
    onSelectTheme: (ThemeMode) -> Unit,
    modifier: Modifier = Modifier,
    isTv: Boolean = false,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .then(
                if (isTv) {
                    Modifier
                        .focusGroup()
                        .focusRestorer()
                } else {
                    Modifier
                },
            ),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        ThemeMode.entries.forEach { mode ->
            val palette = paletteFor(mode)
            val selected = mode == selectedTheme
            var focused by remember(mode) { mutableStateOf(false) }
            val label = stringResource(mode.labelRes())
            val borderColor = when {
                isTv && focused -> MuxGridColors.FocusBorder
                selected -> palette.accent
                else -> MuxGridColors.CardBorder
            }
            val borderWidth = if ((isTv && focused) || selected) 1.5.dp else MuxGridBorders.Hairline
            val tileShape = MuxGridShapes.Control

            Column(
                modifier = Modifier
                    .weight(1f)
                    .clip(tileShape)
                    .background(if (selected) MuxGridColors.CardSurfaceElevated else MuxGridColors.SegmentedTrack)
                    .border(borderWidth, borderColor, tileShape)
                    .then(
                        if (isTv) {
                            Modifier
                                .onFocusChanged { if (focused != it.isFocused) focused = it.isFocused }
                                .clickable(
                                    interactionSource = null,
                                    indication = null,
                                    onClick = { onSelectTheme(mode) },
                                )
                        } else {
                            Modifier.clickable { onSelectTheme(mode) }
                        },
                    )
                    .padding(6.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(5.dp),
            ) {
                // Miniature theme canvas + card + accent bar preview
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(32.dp)
                        .clip(RoundedCornerShape(5.dp))
                        .background(
                            if (palette.backdropStyle == CanvasBackdropStyle.SOLID) {
                                SolidColor(palette.canvasPhone)
                            } else {
                                Brush.linearGradient(
                                    listOf(
                                        palette.canvasPhone,
                                        palette.backdropWash1.copy(alpha = 0.30f),
                                        palette.backdropWash2.copy(alpha = 0.28f),
                                    ),
                                )
                            },
                        )
                        .border(0.5.dp, palette.cardBorder, RoundedCornerShape(5.dp))
                        .padding(4.dp),
                ) {
                    Row(
                        modifier = Modifier
                            .align(Alignment.Center)
                            .fillMaxWidth()
                            .height(18.dp)
                            .clip(RoundedCornerShape(3.dp))
                            .background(palette.cardSurface)
                            .border(0.5.dp, palette.cardBorder, RoundedCornerShape(3.dp))
                            .padding(horizontal = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(3.dp)
                                .clip(RoundedCornerShape(2.dp))
                                .background(
                                    if (palette.accent == palette.accentGradientEnd) {
                                        SolidColor(palette.accent)
                                    } else {
                                        Brush.horizontalGradient(listOf(palette.accent, palette.accentGradientEnd))
                                    },
                                ),
                        )
                        Spacer(Modifier.width(4.dp))
                        Box(
                            modifier = Modifier
                                .size(6.dp)
                                .clip(CircleShape)
                                .background(palette.accentSoft),
                        )
                    }
                }

                Text(
                    text = label,
                    color = when {
                        isTv && focused -> MuxGridColors.FocusBorder
                        selected -> MuxGridColors.TextPrimary
                        else -> MuxGridColors.TextSecondary
                    },
                    fontSize = 11.5.sp,
                    fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                    maxLines = 1,
                    softWrap = false,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
fun MuxGridRadioOptionRow(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    isTv: Boolean = false,
) {
    SelectableOptionRow(
        label = label,
        selected = selected,
        onClick = onClick,
        modifier = modifier,
        isTv = isTv,
    )
}

val MobileTopBarHeight: Dp = 48.dp

@Composable
fun BroadcastUnderlineTab(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    leadingIcon: ImageVector? = null,
    showUnderline: Boolean = selected,
) {
    val accentColor = MuxGridColors.Accent
    Box(
        modifier = modifier
            .clickable(onClick = onClick)
            .drawBehind {
                if (showUnderline) {
                    val strokePx = 2.dp.toPx()
                    drawRect(
                        color = accentColor,
                        topLeft = Offset(0f, size.height - strokePx),
                        size = Size(size.width, strokePx),
                    )
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp),
            modifier = Modifier.padding(top = 6.dp, bottom = 8.dp),
        ) {
            if (leadingIcon != null) {
                Icon(
                    imageVector = leadingIcon,
                    contentDescription = null,
                    tint = if (selected) MuxGridColors.TextPrimary else MuxGridColors.TextMuted,
                    modifier = Modifier.size(14.dp),
                )
            }
            Text(
                text = label,
                color = if (selected) MuxGridColors.TextPrimary else MuxGridColors.TextMuted,
                fontSize = 13.sp,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                maxLines = 1,
            )
        }
    }
}

internal data class TabBounds(
    val leftPx: Float,
    val widthPx: Float,
)

internal fun computePagerIndicatorBounds(
    tabCount: Int,
    currentPage: Int,
    currentPageOffsetFraction: Float,
    boundsFor: (Int) -> TabBounds?,
): TabBounds? {
    if (tabCount <= 0) return null
    val pagePosition = (currentPage + currentPageOffsetFraction)
        .coerceIn(0f, (tabCount - 1).toFloat())
    val fromIndex = floor(pagePosition).toInt().coerceIn(0, tabCount - 1)
    val toIndex = ceil(pagePosition).toInt().coerceIn(0, tabCount - 1)
    val fromBounds = boundsFor(fromIndex) ?: return null
    val toBounds = boundsFor(toIndex) ?: fromBounds
    val fraction = pagePosition - fromIndex
    return TabBounds(
        leftPx = lerp(fromBounds.leftPx, toBounds.leftPx, fraction),
        widthPx = lerp(fromBounds.widthPx, toBounds.widthPx, fraction),
    )
}

/**
 * Scrollable underline tab bar synchronized with a [PagerState] so the accent underline glides
 * smoothly across tabs as the user swipes between pages.
 */
@Composable
fun <T> BroadcastPagerTabRow(
    tabs: List<T>,
    pagerState: PagerState,
    labelFor: @Composable (T) -> String,
    modifier: Modifier = Modifier,
    leadingIconFor: ((T) -> ImageVector?)? = null,
    onTabClick: ((index: Int, tab: T) -> Unit)? = null,
) {
    val scope = rememberCoroutineScope()
    val tabScrollState = rememberScrollState()
    val tabBounds = remember { mutableStateMapOf<Int, TabBounds>() }
    var viewportWidthPx by remember { mutableIntStateOf(0) }
    var hasScrolledOnce by remember { mutableStateOf(false) }
    val accentColor = MuxGridColors.Accent
    val horizontalPaddingPx = with(LocalDensity.current) { 16.dp.toPx() }

    LaunchedEffect(tabs.size) {
        tabBounds.keys.filter { it >= tabs.size }.forEach { tabBounds.remove(it) }
    }

    LaunchedEffect(pagerState.currentPage, viewportWidthPx, tabs.size) {
        if (viewportWidthPx <= 0 || tabs.isEmpty()) return@LaunchedEffect
        val bounds = snapshotFlow { tabBounds[pagerState.currentPage] }
            .filterNotNull()
            .first()
        val target = (horizontalPaddingPx + bounds.leftPx + bounds.widthPx / 2f - viewportWidthPx / 2f)
            .roundToInt()
            .coerceIn(0, tabScrollState.maxValue)
        if (!hasScrolledOnce) {
            tabScrollState.scrollTo(target)
            hasScrolledOnce = true
        } else {
            tabScrollState.animateScrollTo(target)
        }
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(MobileTopBarHeight)
            .onSizeChanged { viewportWidthPx = it.width }
            .horizontalScroll(tabScrollState)
            .padding(horizontal = 16.dp)
            .drawBehind {
                val indicator = computePagerIndicatorBounds(
                    tabCount = tabs.size,
                    currentPage = pagerState.currentPage,
                    currentPageOffsetFraction = pagerState.currentPageOffsetFraction,
                    boundsFor = { tabBounds[it] },
                ) ?: return@drawBehind
                val strokePx = 2.dp.toPx()
                drawRect(
                    color = accentColor,
                    topLeft = Offset(indicator.leftPx, size.height - strokePx),
                    size = Size(indicator.widthPx, strokePx),
                )
            },
        horizontalArrangement = Arrangement.spacedBy(18.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        tabs.forEachIndexed { index, tab ->
            val selected = index == pagerState.currentPage
            BroadcastUnderlineTab(
                label = labelFor(tab),
                selected = selected,
                leadingIcon = leadingIconFor?.invoke(tab),
                showUnderline = false,
                onClick = {
                    onTabClick?.invoke(index, tab)
                    scope.launch { pagerState.animateScrollToPage(index) }
                },
                modifier = Modifier
                    .fillMaxHeight()
                    .onPlaced { coords ->
                        val bounds = TabBounds(
                            leftPx = coords.positionInParent().x,
                            widthPx = coords.size.width.toFloat(),
                        )
                        if (tabBounds[index] != bounds) {
                            tabBounds[index] = bounds
                        }
                    },
            )
        }
    }
}

private val loadedPiconCache = android.util.LruCache<Any, Boolean>(512)

@Composable
fun ChannelPiconBadge(
    channelNumber: String,
    channelName: String,
    piconModel: Any?,
    imageLoader: ImageLoader,
    modifier: Modifier = Modifier,
    size: Dp = 46.dp,
    contentDescription: String? = null,
) {
    val shape = MuxGridShapes.Sm
    Box(
        modifier = modifier
            .size(size)
            .background(MuxGridColors.PiconSurface, shape)
            .border(MuxGridBorders.Divider, MuxGridColors.HairlineBorder, shape),
        contentAlignment = Alignment.Center,
    ) {
        if (piconModel != null) {
            var loadSucceeded by remember(piconModel) {
                mutableStateOf(loadedPiconCache.get(piconModel) == true)
            }
            if (!loadSucceeded) {
                PiconFallbackLabel(channelNumber, channelName)
            }
            AsyncImage(
                model = piconModel,
                imageLoader = imageLoader,
                contentDescription = contentDescription,
                contentScale = ContentScale.Fit,
                onLoading = {
                    if (loadedPiconCache.get(piconModel) != true) {
                        loadSucceeded = false
                    }
                },
                onError = {
                    loadedPiconCache.remove(piconModel)
                    loadSucceeded = false
                },
                onSuccess = {
                    loadedPiconCache.put(piconModel, true)
                    loadSucceeded = true
                },
                modifier = Modifier
                    .fillMaxSize()
                    .padding(5.dp),
            )
        } else {
            PiconFallbackLabel(channelNumber, channelName)
        }
    }
}

@Composable
private fun PiconFallbackLabel(
    channelNumber: String,
    channelName: String,
) {
    val text = if (channelNumber.isNotBlank() && channelNumber != "0") {
        channelNumber
    } else {
        channelName.take(3).uppercase()
    }
    Text(
        text = text,
        style = MuxGridType.TabularNumber,
        color = MuxGridColors.TextSecondary,
        maxLines = 1,
    )
}

@Composable
fun rememberNowEpochSec(
    serverClockOffsetMs: Long,
    intervalMs: Long = 30_000L,
): Long {
    val lifecycleOwner = LocalLifecycleOwner.current
    val nowState = remember(serverClockOffsetMs) {
        mutableLongStateOf((System.currentTimeMillis() + serverClockOffsetMs) / 1000L)
    }
    LaunchedEffect(lifecycleOwner, serverClockOffsetMs, intervalMs) {
        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            while (true) {
                val nowMs = System.currentTimeMillis() + serverClockOffsetMs
                nowState.longValue = nowMs / 1000L
                val remainderMs = nowMs % intervalMs
                val delayMs = (intervalMs - remainderMs).coerceIn(1_000L, intervalMs)
                delay(delayMs)
            }
        }
    }
    return nowState.longValue
}

internal fun stableDurationSec(ms: Long, previousSec: Long): Long {
    val clampedMs = ms.coerceAtLeast(0L)
    val diffMs = clampedMs - (previousSec * 1000L)
    return if (abs(diffMs) >= 700L) (clampedMs + 500L) / 1000L else previousSec
}

@Composable
fun rememberStableDurationSec(ms: Long, resetKey: Any? = null): Long {
    val clampedMs = ms.coerceAtLeast(0L)
    val lastShownSec = remember(resetKey) { LongArray(1) { (clampedMs + 500L) / 1000L } }
    val nextSec = stableDurationSec(clampedMs, lastShownSec[0])
    SideEffect {
        lastShownSec[0] = nextSec
    }
    return nextSec
}

@Composable
fun LiveProgressBar(
    progress: Float,
    modifier: Modifier = Modifier,
    fillColor: Color = MuxGridColors.ProgressFill,
    fillEndColor: Color = fillColor,
    height: Dp = 2.dp,
    animate: Boolean = false,
    bufferedFraction: Float? = null,
    seekTargetFraction: Float? = null,
) {
    val clamped = progress.coerceIn(0f, 1f)
    val clampedBuffered = bufferedFraction?.coerceIn(0f, 1f)
    val clampedTarget = seekTargetFraction?.coerceIn(0f, 1f)
    val displayProgress = if (animate) {
        animateFloatAsState(
            targetValue = clamped,
            animationSpec = tween(durationMillis = MuxGridDurations.Progress, easing = LinearEasing),
            label = "liveProgress",
        ).value
    } else {
        clamped
    }
    val trackBackground = MuxGridColors.TrackBackground
    val bufferedColor = fillColor.copy(alpha = 0.34f)
    val targetMarkerColor = Color.White
    val fillBrush = remember(fillColor, fillEndColor) {
        if (fillColor == fillEndColor) null else Brush.horizontalGradient(listOf(fillColor, fillEndColor))
    }
    Spacer(
        modifier = modifier
            .height(height)
            .drawBehind {
                val radius = CornerRadius(size.height / 2f, size.height / 2f)
                drawRoundRect(
                    color = trackBackground,
                    cornerRadius = radius,
                )
                if (clampedBuffered != null && clampedBuffered > displayProgress) {
                    drawRoundRect(
                        color = bufferedColor,
                        size = Size(size.width * clampedBuffered, size.height),
                        cornerRadius = radius,
                    )
                }
                if (displayProgress > 0f) {
                    if (fillBrush != null) {
                        drawRoundRect(
                            brush = fillBrush,
                            size = Size(size.width * displayProgress, size.height),
                            cornerRadius = radius,
                        )
                    } else {
                        drawRoundRect(
                            color = fillColor,
                            size = Size(size.width * displayProgress, size.height),
                            cornerRadius = radius,
                        )
                    }
                }
                if (clampedTarget != null) {
                    val markerWidth = 3.dp.toPx().coerceAtLeast(2f)
                    val markerHeight = (size.height + 6.dp.toPx()).coerceAtLeast(size.height)
                    val x = (size.width * clampedTarget - markerWidth / 2f)
                        .coerceIn(0f, (size.width - markerWidth).coerceAtLeast(0f))
                    val y = (size.height - markerHeight) / 2f
                    drawRoundRect(
                        color = targetMarkerColor,
                        topLeft = Offset(x, y),
                        size = Size(markerWidth, markerHeight),
                        cornerRadius = CornerRadius(markerWidth / 2f, markerWidth / 2f),
                    )
                }
            },
    )
}

/**
 * Full-width live broadcast progress block (`ON NOW` + remaining minutes + [LiveProgressBar])
 * shared by TV and Mobile EPG show detail overlays.
 */
@Composable
fun EpgLiveProgressSection(
    event: EpgEvent,
    nowEpochSec: Long,
    isRecording: Boolean,
    modifier: Modifier = Modifier,
) {
    val remaining = event.remainingMinutes(nowEpochSec)
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(R.string.live_on_now),
                color = MuxGridColors.CoralLive,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = stringResource(R.string.time_minutes_left, remaining),
                style = MuxGridType.TabularSmall,
                color = MuxGridColors.TextSecondary,
            )
        }
        LiveProgressBar(
            progress = event.progressFraction(nowEpochSec),
            fillColor = if (isRecording) MuxGridColors.CoralLive else MuxGridColors.Accent,
            height = 3.dp,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
fun MuxGridSwitch(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val trackColor by animateColorAsState(
        targetValue = if (checked) MuxGridColors.Accent else MuxGridColors.TrackBackground,
        animationSpec = tween(durationMillis = MuxGridDurations.Switch),
        label = "switchTrack",
    )
    val thumbColor by animateColorAsState(
        targetValue = if (checked) MuxGridColors.OnAccent else MuxGridColors.TextSecondary,
        animationSpec = tween(durationMillis = MuxGridDurations.Switch),
        label = "switchThumb",
    )
    val thumbOffset by animateDpAsState(
        targetValue = if (checked) 19.dp else 3.dp,
        animationSpec = tween(durationMillis = MuxGridDurations.Switch),
        label = "switchThumbOffset",
    )
    Box(
        modifier = modifier
            .size(width = 38.dp, height = 22.dp)
            .clip(MuxGridShapes.Pill)
            .background(trackColor)
            .semantics { role = Role.Switch }
            .clickable { onCheckedChange(!checked) },
        contentAlignment = Alignment.CenterStart,
    ) {
        Box(
            modifier = Modifier
                .offset(x = thumbOffset)
                .size(16.dp)
                .clip(CircleShape)
                .background(thumbColor),
        )
    }
}

@Composable
fun MuxGridCompactButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    leadingIcon: ImageVector? = null,
    containerColor: Color = MuxGridColors.Accent,
    contentColor: Color = MuxGridColors.OnAccent,
    borderColor: Color? = null,
    enabled: Boolean = true,
) {
    val alpha = if (enabled) 1f else MuxGridAlphas.Disabled
    Row(
        modifier = modifier
            .heightIn(min = 32.dp)
            .clip(MuxGridShapes.Sm)
            .background(containerColor.copy(alpha = containerColor.alpha * alpha))
            .then(
                if (borderColor != null) {
                    Modifier.border(MuxGridBorders.Hairline, borderColor.copy(alpha = borderColor.alpha * alpha), MuxGridShapes.Sm)
                } else {
                    Modifier
                },
            )
            .then(if (enabled) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        if (leadingIcon != null) {
            Icon(
                imageVector = leadingIcon,
                contentDescription = null,
                tint = contentColor.copy(alpha = contentColor.alpha * alpha),
                modifier = Modifier.size(14.dp),
            )
        }
        Text(
            text = text,
            color = contentColor.copy(alpha = contentColor.alpha * alpha),
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            softWrap = false,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
fun MuxGridSeekBar(
    value: Float,
    onValueChange: (Float) -> Unit,
    onValueChangeFinished: () -> Unit,
    modifier: Modifier = Modifier,
    activeTrackColor: Color = MuxGridColors.Accent,
    activeTrackEndColor: Color = if (activeTrackColor == MuxGridColors.Accent) MuxGridColors.AccentGradientEnd else activeTrackColor,
    inactiveTrackColor: Color = MuxGridColors.TrackBackground,
    thumbColor: Color = MuxGridColors.SeekThumb,
    bufferedFraction: Float? = null,
    seekTargetFraction: Float? = null,
) {
    var widthPx by remember { mutableIntStateOf(1) }
    var dragging by remember { mutableStateOf(false) }
    val currentOnValueChange by rememberUpdatedState(onValueChange)
    val currentOnValueChangeFinished by rememberUpdatedState(onValueChangeFinished)
    val clamped = value.coerceIn(0f, 1f)
    val clampedBuffered = bufferedFraction?.coerceIn(0f, 1f)
    val clampedTarget = seekTargetFraction?.coerceIn(0f, 1f)
    val barHeight by animateDpAsState(
        targetValue = if (dragging) 6.dp else 4.dp,
        animationSpec = tween(durationMillis = MuxGridDurations.Fast),
        label = "seekBarHeight",
    )

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(24.dp)
            .onSizeChanged { widthPx = it.width.coerceAtLeast(1) }
            .pointerInput(Unit) {
                detectTapGestures { offset ->
                    val frac = (offset.x / widthPx.toFloat()).coerceIn(0f, 1f)
                    currentOnValueChange(frac)
                    currentOnValueChangeFinished()
                }
            }
            .pointerInput(Unit) {
                detectHorizontalDragGestures(
                    onDragStart = { offset ->
                        dragging = true
                        val frac = (offset.x / widthPx.toFloat()).coerceIn(0f, 1f)
                        currentOnValueChange(frac)
                    },
                    onHorizontalDrag = { change, _ ->
                        change.consume()
                        val frac = (change.position.x / widthPx.toFloat()).coerceIn(0f, 1f)
                        currentOnValueChange(frac)
                    },
                    onDragEnd = {
                        dragging = false
                        currentOnValueChangeFinished()
                    },
                    onDragCancel = {
                        dragging = false
                        currentOnValueChangeFinished()
                    },
                )
            },
        contentAlignment = Alignment.CenterStart,
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(barHeight)
                .clip(MuxGridShapes.SeekTrack)
                .background(inactiveTrackColor),
        ) {
            if (clampedBuffered != null && clampedBuffered > clamped) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(clampedBuffered)
                        .fillMaxHeight()
                        .clip(MuxGridShapes.SeekTrack)
                        .background(activeTrackColor.copy(alpha = 0.34f)),
                )
            }
            if (clamped > 0f) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(clamped)
                        .fillMaxHeight()
                        .clip(MuxGridShapes.SeekTrack)
                        .background(muxGridAccentBrush(activeTrackColor, activeTrackEndColor)),
                )
            }
        }
        if (clampedTarget != null && !dragging) {
            Box(
                modifier = Modifier
                    .offset {
                        val markerWidthPx = 3.dp.toPx()
                        val x = (clampedTarget * widthPx - markerWidthPx / 2f).roundToInt()
                            .coerceIn(0, (widthPx - markerWidthPx.roundToInt()).coerceAtLeast(0))
                        IntOffset(x, 0)
                    }
                    .size(width = 3.dp, height = 12.dp)
                    .clip(MuxGridShapes.SeekTrack)
                    .background(Color.White),
            )
        }
        if (dragging) {
            Box(
                modifier = Modifier
                    .offset {
                        val thumbRadiusPx = 6.dp.toPx()
                        val x = (clamped * widthPx - thumbRadiusPx).roundToInt()
                            .coerceIn(0, (widthPx - (thumbRadiusPx * 2f).roundToInt()).coerceAtLeast(0))
                        IntOffset(x, 0)
                    }
                    .size(12.dp)
                    .clip(CircleShape)
                    .background(thumbColor),
            )
        }
    }
}

@Composable
@ReadOnlyComposable
fun connectionStatusDotColor(state: ConnectionState): Color? = when (state) {
    is ConnectionState.Connected -> null
    ConnectionState.Connecting, is ConnectionState.Syncing -> MuxGridColors.AmberWarn
    is ConnectionState.Error -> MuxGridColors.CoralLive
    ConnectionState.Disconnected -> MuxGridColors.TextMuted
}

@Composable
@ReadOnlyComposable
fun connectionStatusColor(state: ConnectionState): Color = when (state) {
    is ConnectionState.Connected -> MuxGridColors.EmeraldOk
    ConnectionState.Connecting, is ConnectionState.Syncing -> MuxGridColors.AmberWarn
    is ConnectionState.Error -> MuxGridColors.CoralLive
    ConnectionState.Disconnected -> MuxGridColors.TextMuted
}

@Composable
fun MainTab.label(): String = localizedLabel()

fun MainTab.icon(selected: Boolean): ImageVector = when (this) {
    MainTab.LIVE -> if (selected) MuxGridIcons.LiveFilled else MuxGridIcons.Live
    MainTab.GUIDE -> if (selected) MuxGridIcons.GuideFilled else MuxGridIcons.Guide
    MainTab.RECORDINGS -> if (selected) MuxGridIcons.RecordingsFilled else MuxGridIcons.Recordings
    MainTab.SETTINGS -> if (selected) MuxGridIcons.SettingsFilled else MuxGridIcons.Settings
}

fun copyTextToClipboard(
    context: Context,
    label: String,
    text: String,
    toastMessage: String? = null,
) {
    val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager ?: return
    cm.setPrimaryClip(ClipData.newPlainText(label, text))
    if (!toastMessage.isNullOrEmpty()) {
        Toast.makeText(context, toastMessage, Toast.LENGTH_SHORT).show()
    }
}

fun Context.isDebuggableBuild(): Boolean =
    (applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0

@Composable
fun ConnectionStatusDot(
    color: Color,
    modifier: Modifier = Modifier,
    borderColor: Color = MuxGridColors.CanvasPhone,
    size: Dp = 8.dp,
) {
    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(color)
            .border(1.5.dp, borderColor, CircleShape),
    )
}

@Composable
fun PlayerErrorToast(
    error: PlaybackError,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val title = error.displayTitle()
    Row(
        modifier = modifier
            .clip(MuxGridShapes.Row)
            .background(MuxGridColors.ScrimSurface)
            .border(
                width = MuxGridBorders.Hairline,
                color = MuxGridColors.CoralLive.copy(alpha = MuxGridAlphas.BorderStrong),
                shape = MuxGridShapes.Row,
            )
            .clickable(onClick = onDismiss)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = MuxGridIcons.Warning,
            contentDescription = title,
            tint = MuxGridColors.CoralLive,
            modifier = Modifier.size(18.dp),
        )
        Spacer(Modifier.width(10.dp))
        Column {
            Text(
                text = title,
                color = MuxGridColors.TextPrimary,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = error.message,
                color = MuxGridColors.TextSecondary,
                fontSize = 12.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
fun ModalCloseIconButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var closeFocused by remember { mutableStateOf(false) }
    Box(
        modifier = modifier
            .size(36.dp)
            .onFocusChanged { closeFocused = it.isFocused }
            .clip(MuxGridShapes.Sm)
            .background(if (closeFocused) MuxGridColors.FocusBorder else MuxGridColors.CardSurfaceElevated)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = MuxGridIcons.Close,
            contentDescription = stringResource(R.string.common_close),
            tint = if (closeFocused) MuxGridColors.OnFocusBorder else MuxGridColors.TextSecondary,
            modifier = Modifier.size(18.dp),
        )
    }
}

@Composable
fun TrackAndPresetPanel(
    audioTracks: List<MediaTrack>,
    subtitleTracks: List<MediaTrack>,
    selectedAudioId: Int?,
    selectedSubtitleId: Int?,
    hwDecPreset: HwDecPreset,
    onSelectAudio: (Int) -> Unit,
    onSelectSubtitle: (Int?) -> Unit,
    onSelectPreset: (HwDecPreset) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
    aspectMode: VideoAspectMode? = null,
    onSelectAspectMode: ((VideoAspectMode) -> Unit)? = null,
) {
    val firstOptionFocusRequester = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        runCatching { firstOptionFocusRequester.requestFocus() }
    }

    Column(
        modifier = modifier
            .heightIn(max = 500.dp)
            .clip(MuxGridShapes.Dialog)
            .background(MuxGridColors.ModalSurface)
            .border(MuxGridBorders.Hairline, MuxGridColors.CardBorder, MuxGridShapes.Dialog)
            .muxGridTopSpecularHighlight(MuxGridRadii.Dialog, MuxGridColors.CardTopHighlight)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = {},
            )
            .focusProperties { onExit = { cancelFocusChange() } }
            .focusGroup()
            .verticalScroll(rememberScrollState())
            .padding(22.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(
                modifier = Modifier.weight(1f),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Icon(
                    imageVector = MuxGridIcons.Tracks,
                    contentDescription = null,
                    tint = MuxGridColors.AccentSoft,
                    modifier = Modifier.size(20.dp),
                )
                Text(
                    text = stringResource(R.string.player_display_options),
                    style = MuxGridType.Title,
                    fontSize = 18.sp,
                )
            }
            ModalCloseIconButton(onClick = onClose)
        }

        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(stringResource(R.string.player_section_audio), style = MuxGridType.Eyebrow, modifier = Modifier.padding(horizontal = 2.dp))
            if (audioTracks.isEmpty()) {
                Text(
                    text = stringResource(R.string.player_no_audio_tracks),
                    style = MuxGridType.TabularSmall,
                    modifier = Modifier.padding(horizontal = 2.dp),
                )
            } else {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(MuxGridShapes.Card)
                        .background(MuxGridColors.CardBorder)
                        .border(MuxGridBorders.Hairline, MuxGridColors.CardBorder, MuxGridShapes.Card),
                    verticalArrangement = Arrangement.spacedBy(1.dp),
                ) {
                    val effectiveAudioId = selectedAudioId ?: audioTracks.firstOrNull { it.isSelected }?.id
                    audioTracks.forEachIndexed { idx, track ->
                        val selected = track.id == effectiveAudioId
                        SelectableOptionRow(
                            label = track.localizedDisplayLabel(),
                            selected = selected,
                            onClick = { onSelectAudio(track.id) },
                            modifier = if (idx == 0) Modifier.focusRequester(firstOptionFocusRequester) else Modifier,
                        )
                    }
                }
            }
        }

        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(stringResource(R.string.player_section_subtitles), style = MuxGridType.Eyebrow, modifier = Modifier.padding(horizontal = 2.dp))
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(MuxGridShapes.Card)
                    .background(MuxGridColors.CardBorder)
                    .border(MuxGridBorders.Hairline, MuxGridColors.CardBorder, MuxGridShapes.Card),
                verticalArrangement = Arrangement.spacedBy(1.dp),
            ) {
                SelectableOptionRow(
                    label = stringResource(R.string.common_off),
                    selected = selectedSubtitleId == null,
                    onClick = { onSelectSubtitle(null) },
                    modifier = if (audioTracks.isEmpty()) Modifier.focusRequester(firstOptionFocusRequester) else Modifier,
                )
                subtitleTracks.forEach { track ->
                    val selected = track.id == selectedSubtitleId
                    SelectableOptionRow(
                        label = track.localizedDisplayLabel(),
                        selected = selected,
                        onClick = { onSelectSubtitle(track.id) },
                    )
                }
            }
        }

        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(stringResource(R.string.player_section_decoder_preset), style = MuxGridType.Eyebrow, modifier = Modifier.padding(horizontal = 2.dp))
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(MuxGridShapes.Card)
                    .background(MuxGridColors.CardBorder)
                    .border(MuxGridBorders.Hairline, MuxGridColors.CardBorder, MuxGridShapes.Card),
                verticalArrangement = Arrangement.spacedBy(1.dp),
            ) {
                SelectableOptionRow(
                    label = stringResource(R.string.settings_decoder_zero_copy_full),
                    selected = hwDecPreset == HwDecPreset.MEDIACODEC_ZERO_COPY,
                    onClick = { onSelectPreset(HwDecPreset.MEDIACODEC_ZERO_COPY) },
                )
                SelectableOptionRow(
                    label = stringResource(R.string.settings_decoder_copy_bwdif_full),
                    selected = hwDecPreset == HwDecPreset.MEDIACODEC_COPY_BWDIF,
                    onClick = { onSelectPreset(HwDecPreset.MEDIACODEC_COPY_BWDIF) },
                )
            }
        }

        if (aspectMode != null && onSelectAspectMode != null) {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(stringResource(R.string.player_section_aspect_zoom), style = MuxGridType.Eyebrow, modifier = Modifier.padding(horizontal = 2.dp))
                MuxGridSegmentedControl(
                    options = VideoAspectMode.entries,
                    selectedOption = aspectMode,
                    labelFor = { it.localizedLabel() },
                    onSelect = onSelectAspectMode,
                    isTv = true,
                )
            }
        }
    }
}

@Composable
private fun SelectableOptionRow(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    isTv: Boolean = false,
) {
    var focused by remember { mutableStateOf(false) }
    val bg = when {
        focused -> MuxGridColors.FocusBorder
        selected -> MuxGridColors.CardSurfaceElevated
        else -> MuxGridColors.CardSurface
    }
    val textColor = when {
        focused -> MuxGridColors.OnFocusBorder
        selected -> MuxGridColors.TextPrimary
        else -> MuxGridColors.TextSecondary
    }
    Row(
        modifier = modifier
            .fillMaxWidth()
            .onFocusChanged { if (focused != it.isFocused) focused = it.isFocused }
            .background(bg)
            .then(
                if (isTv) {
                    Modifier.clickable(
                        interactionSource = null,
                        indication = null,
                        onClick = onClick,
                    )
                } else {
                    Modifier.clickable(onClick = onClick)
                },
            )
            .padding(horizontal = 12.dp, vertical = 11.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            color = textColor,
            fontSize = 13.sp,
            fontWeight = if (selected || focused) FontWeight.SemiBold else FontWeight.Normal,
            modifier = Modifier.weight(1f),
        )
        if (selected) {
            Spacer(Modifier.width(10.dp))
            Icon(
                imageVector = MuxGridIcons.Check,
                contentDescription = stringResource(R.string.common_active),
                tint = if (focused) MuxGridColors.OnFocusBorder else MuxGridColors.Accent,
                modifier = Modifier.size(16.dp),
            )
        }
    }
}

fun filterChannels(
    channels: List<Channel>,
    filter: LiveFilter,
    favorites: Set<Long>,
): List<Channel> = when (filter) {
    LiveFilter.All -> channels
    LiveFilter.Tv -> channels.filter { !it.isRadio }
    LiveFilter.Radio -> channels.filter { it.isRadio }
    LiveFilter.Favorites -> channels.filter { it.id in favorites }
    is LiveFilter.Tag -> channels.filter { filter.tagId in it.tagIds }
}

fun buildLiveFilters(tags: List<ChannelTag>): List<LiveFilter> {
    val base = mutableListOf<LiveFilter>(
        LiveFilter.All,
        LiveFilter.Tv,
        LiveFilter.Radio,
        LiveFilter.Favorites,
    )
    tags.forEach { tag ->
        base.add(LiveFilter.Tag(tag.id, tag.name))
    }
    return base
}

@Composable
fun LiveFilter.label(): String = localizedLabel()

fun LiveFilter.icon(): ImageVector? = when (this) {
    LiveFilter.Tv -> MuxGridIcons.Tv
    LiveFilter.Radio -> MuxGridIcons.Radio
    LiveFilter.Favorites -> MuxGridIcons.StarFilled
    LiveFilter.All, is LiveFilter.Tag -> null
}

private val TIME_FMT: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")

fun formatEpochHm(
    epochSec: Long,
    zoneId: ZoneId = ZoneId.systemDefault(),
): String =
    Instant.ofEpochSecond(epochSec)
        .atZone(zoneId)
        .format(TIME_FMT)

@Composable
fun DvrStateBadge(
    state: DvrState,
    isWatched: Boolean = false,
    modifier: Modifier = Modifier,
) {
    val (color, label) = when {
        state == DvrState.RECORDING -> MuxGridColors.CoralLive to stringResource(R.string.recordings_status_rec)
        state == DvrState.SCHEDULED -> MuxGridColors.AccentSoft to stringResource(R.string.recordings_status_scheduled)
        state == DvrState.COMPLETED && isWatched -> MuxGridColors.EmeraldOk to stringResource(R.string.recordings_status_watched)
        state == DvrState.MISSED -> MuxGridColors.AmberWarn to stringResource(R.string.recordings_status_missed)
        state == DvrState.INVALID -> MuxGridColors.CoralLive to stringResource(R.string.recordings_status_failed)
        else -> return
    }
    Row(
        modifier = modifier
            .background(color.copy(alpha = MuxGridAlphas.TintFill), MuxGridShapes.Badge)
            .padding(horizontal = 7.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        when {
            state == DvrState.RECORDING -> {
                Box(
                    modifier = Modifier
                        .size(6.dp)
                        .clip(CircleShape)
                        .background(color),
                )
            }
            state == DvrState.SCHEDULED -> {
                Icon(
                    imageVector = MuxGridIcons.Schedule,
                    contentDescription = null,
                    tint = color,
                    modifier = Modifier.size(11.dp),
                )
            }
            state == DvrState.COMPLETED && isWatched -> {
                Icon(
                    imageVector = MuxGridIcons.CheckCircle,
                    contentDescription = null,
                    tint = color,
                    modifier = Modifier.size(11.dp),
                )
            }
            state == DvrState.MISSED || state == DvrState.INVALID -> {
                Icon(
                    imageVector = MuxGridIcons.Warning,
                    contentDescription = null,
                    tint = color,
                    modifier = Modifier.size(11.dp),
                )
            }
        }
        Text(
            text = label,
            color = color,
            fontSize = 10.5.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
        )
    }
}

@Composable
fun TimeshiftNoticeBanner(
    notice: String,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .clip(MuxGridShapes.Sm)
            .background(MuxGridColors.ScrimSurface)
            .border(MuxGridBorders.Hairline, MuxGridColors.AmberWarn.copy(alpha = MuxGridAlphas.BorderEmphasis), MuxGridShapes.Sm)
            .padding(horizontal = 12.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(
            imageVector = MuxGridIcons.Schedule,
            contentDescription = null,
            tint = MuxGridColors.AmberWarn,
            modifier = Modifier.size(15.dp),
        )
        Text(
            text = notice,
            color = MuxGridColors.TextPrimary,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

enum class DvrConfirmKind {
    STOP_AND_SAVE,
    ABORT_RECORDING,
    CANCEL_TIMER,
    DELETE_RECORDING,
}

data class DvrConfirmSpec(
    val title: String,
    val message: String,
    val confirmLabel: String,
    val isDestructive: Boolean,
    val failureFallbackMessage: String,
)

data class DvrConfirmSpecRes(
    @androidx.annotation.StringRes val titleRes: Int,
    @androidx.annotation.StringRes val messageRes: Int,
    @androidx.annotation.StringRes val confirmLabelRes: Int,
    val isDestructive: Boolean,
    @androidx.annotation.StringRes val failureFallbackRes: Int,
)

fun dvrConfirmSpecRes(kind: DvrConfirmKind): DvrConfirmSpecRes = when (kind) {
    DvrConfirmKind.STOP_AND_SAVE -> DvrConfirmSpecRes(
        titleRes = R.string.recordings_confirm_stop_title,
        messageRes = R.string.recordings_confirm_stop_message,
        confirmLabelRes = R.string.recordings_stop_and_save,
        isDestructive = false,
        failureFallbackRes = R.string.recordings_confirm_stop_failed,
    )
    DvrConfirmKind.ABORT_RECORDING -> DvrConfirmSpecRes(
        titleRes = R.string.recordings_confirm_abort_title,
        messageRes = R.string.recordings_confirm_abort_message,
        confirmLabelRes = R.string.recordings_abort_recording,
        isDestructive = true,
        failureFallbackRes = R.string.recordings_confirm_abort_failed,
    )
    DvrConfirmKind.CANCEL_TIMER -> DvrConfirmSpecRes(
        titleRes = R.string.recordings_confirm_cancel_title,
        messageRes = R.string.recordings_confirm_cancel_message,
        confirmLabelRes = R.string.recordings_cancel_timer,
        isDestructive = true,
        failureFallbackRes = R.string.recordings_confirm_cancel_failed,
    )
    DvrConfirmKind.DELETE_RECORDING -> DvrConfirmSpecRes(
        titleRes = R.string.recordings_confirm_delete_title,
        messageRes = R.string.recordings_confirm_delete_message,
        confirmLabelRes = R.string.recordings_delete_recording,
        isDestructive = true,
        failureFallbackRes = R.string.recordings_confirm_delete_failed,
    )
}

@Composable
fun dvrConfirmSpec(kind: DvrConfirmKind, entryTitle: String): DvrConfirmSpec {
    val res = dvrConfirmSpecRes(kind)
    return DvrConfirmSpec(
        title = stringResource(res.titleRes),
        message = stringResource(res.messageRes, entryTitle),
        confirmLabel = stringResource(res.confirmLabelRes),
        isDestructive = res.isDestructive,
        failureFallbackMessage = stringResource(res.failureFallbackRes),
    )
}

@Composable
fun MuxGridConfirmDialog(
    title: String,
    message: String,
    confirmLabel: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    dismissLabel: String = stringResource(R.string.common_cancel),
    isDestructive: Boolean = false,
    enabled: Boolean = true,
) {
    AlertDialog(
        onDismissRequest = { if (enabled) onDismiss() },
        shape = MuxGridShapes.Dialog,
        containerColor = MuxGridColors.ModalSurface,
        titleContentColor = MuxGridColors.TextPrimary,
        textContentColor = MuxGridColors.TextSecondary,
        title = { Text(title) },
        text = { Text(message) },
        confirmButton = {
            TextButton(
                enabled = enabled,
                onClick = onConfirm,
            ) {
                Text(
                    text = confirmLabel,
                    color = if (isDestructive) MuxGridColors.CoralLive else MuxGridColors.AccentSoft,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        },
        dismissButton = {
            TextButton(
                enabled = enabled,
                onClick = onDismiss,
            ) {
                Text(dismissLabel, color = MuxGridColors.TextSecondary)
            }
        },
    )
}

private val ConsumeVerticalSheetDragConnection = object : NestedScrollConnection {
    override fun onPostScroll(
        consumed: Offset,
        available: Offset,
        source: NestedScrollSource,
    ): Offset = Offset(0f, available.y)

    override suspend fun onPostFling(
        consumed: Velocity,
        available: Velocity,
    ): Velocity = Velocity(0f, available.y)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MuxGridModalSheet(
    onDismissRequest: () -> Unit,
    contentSpacing: Dp = 12.dp,
    content: @Composable ColumnScope.() -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        sheetMaxWidth = 760.dp,
        shape = MuxGridShapes.SheetTop,
        containerColor = MuxGridColors.ModalSurface,
        contentColor = MuxGridColors.TextPrimary,
        dragHandle = null,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .nestedScroll(ConsumeVerticalSheetDragConnection)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 18.dp)
                .padding(bottom = 12.dp),
            verticalArrangement = Arrangement.spacedBy(contentSpacing),
            content = content,
        )
    }
}

/**
 * Unified responsive detail & actions layout shared by Live Channel, EPG Program, and DVR
 * Recording overlays across Mobile and Android TV:
 * - On **Android TV** and **Mobile Landscape / Tablet** ([isSplitLayout] = `true`): renders a
 *   two-column split layout with program/channel details on the left and a vertical stack of
 *   equal-width action buttons on the right.
 * - On **Mobile Portrait** ([isSplitLayout] = `false`): renders a single-column vertical stack
 *   with details on top and action buttons below.
 */
@Composable
fun MuxGridDetailModalLayout(
    isSplitLayout: Boolean = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE,
    actionsColumnWidth: Dp = 220.dp,
    detailsContent: @Composable ColumnScope.() -> Unit,
    actionsContent: @Composable ColumnScope.() -> Unit,
) {
    if (isSplitLayout) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(20.dp),
            verticalAlignment = Alignment.Top,
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                content = detailsContent,
            )
            Column(
                modifier = Modifier.width(actionsColumnWidth),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                content = actionsContent,
            )
        }
    } else {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                content = detailsContent,
            )
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                content = actionsContent,
            )
        }
    }
}

/**
 * Ensures the item at [index] in a horizontal (or vertical) [LazyListState] stays cleanly inside
 * the visible viewport, centering it when it is off-screen or partially clipped at an edge.
 */
suspend fun scrollItemIntoViewport(
    listState: LazyListState,
    index: Int,
    animate: Boolean,
) {
    if (index < 0) return
    if (listState.layoutInfo.totalItemsCount <= index) {
        withFrameNanos { }
        if (listState.layoutInfo.totalItemsCount <= index) return
    }
    var info = listState.layoutInfo
    var viewportWidth = info.viewportEndOffset - info.viewportStartOffset
    if (viewportWidth <= 0) {
        listState.scrollToItem(index)
        withFrameNanos { }
        info = listState.layoutInfo
        viewportWidth = info.viewportEndOffset - info.viewportStartOffset
        if (viewportWidth <= 0) return
    }

    val visibleItem = info.visibleItemsInfo.firstOrNull { it.index == index }
    if (visibleItem == null) {
        listState.scrollToItem(index)
        withFrameNanos { }
        val updatedInfo = listState.layoutInfo
        val measured = updatedInfo.visibleItemsInfo.firstOrNull { it.index == index } ?: return
        val vpWidth = updatedInfo.viewportEndOffset - updatedInfo.viewportStartOffset
        if (vpWidth <= 0) return
        val desiredStart = updatedInfo.viewportStartOffset + (vpWidth - measured.size) / 2
        val delta = (measured.offset - desiredStart).toFloat()
        if (abs(delta) > 1f) {
            listState.scrollBy(delta)
        }
        return
    }

    val itemStart = visibleItem.offset
    val itemEnd = visibleItem.offset + visibleItem.size
    if (itemStart >= info.viewportStartOffset && itemEnd <= info.viewportEndOffset) {
        return
    }

    val desiredStart = info.viewportStartOffset + (viewportWidth - visibleItem.size) / 2
    val delta = (itemStart - desiredStart).toFloat()
    if (abs(delta) > 1f) {
        if (animate) {
            listState.animateScrollBy(delta)
        } else {
            listState.scrollBy(delta)
        }
    }
}
