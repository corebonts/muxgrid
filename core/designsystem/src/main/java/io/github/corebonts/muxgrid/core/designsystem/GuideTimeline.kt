package io.github.corebonts.muxgrid.core.designsystem

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.BringIntoViewSpec
import androidx.compose.foundation.gestures.ScrollableState
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.corebonts.muxgrid.core.model.EpgEvent
import java.time.Instant
import java.time.ZoneId
import kotlin.math.roundToInt

/**
 * Coordinates continuous horizontal timeline scrolling across a sticky time ruler and all visible
 * channel rows without measuring a huge multi-day Compose container (avoiding Compose's 262,143 px
 * Constraints limit on high-DPI displays).
 */
@Stable
class TimelineScrollState(
    initialEpochSec: Long,
) {
    var originEpochSec: Long by mutableLongStateOf(initialEpochSec)
        private set
    var endEpochSec: Long by mutableLongStateOf(initialEpochSec + 3L * 3600L)
        private set
    var viewportWidthPx: Float by mutableFloatStateOf(0f)
        private set
    var pxPerSec: Float by mutableFloatStateOf(0.06f)
        private set
    var scrollOffsetPx: Float by mutableFloatStateOf(0f)
        private set

    private var hasInitializedBounds = false
    private var initialTargetEpochSec: Long = initialEpochSec

    val totalDurationSec: Long
        get() = (endEpochSec - originEpochSec).coerceAtLeast(HALF_HOUR_SEC)

    val maxScrollPx: Float
        get() = (totalDurationSec * pxPerSec - viewportWidthPx).coerceAtLeast(0f)

    val visibleStartEpochSec: Long
        get() = if (pxPerSec > 0f) {
            originEpochSec + (scrollOffsetPx / pxPerSec).toLong()
        } else {
            originEpochSec
        }

    val visibleDurationSec: Long
        get() = if (pxPerSec > 0f && viewportWidthPx > 0f) {
            (viewportWidthPx / pxPerSec).toLong().coerceAtLeast(HALF_HOUR_SEC)
        } else {
            3L * 3600L
        }

    val scrollableState: ScrollableState = ScrollableState { delta ->
        val target = (scrollOffsetPx + delta).coerceIn(0f, maxScrollPx)
        val consumed = target - scrollOffsetPx
        scrollOffsetPx = target
        consumed
    }

    fun updateBounds(
        newOriginSec: Long,
        newEndSec: Long,
        newViewportWidthPx: Float,
        newPxPerSec: Float,
    ) {
        val safeEndSec = maxOf(newEndSec, newOriginSec + 3L * 3600L)
        val safePxPerSec = newPxPerSec.coerceAtLeast(0.001f)
        val safeViewportPx = newViewportWidthPx.coerceAtLeast(0f)

        if (hasInitializedBounds &&
            originEpochSec == newOriginSec &&
            endEpochSec == safeEndSec &&
            viewportWidthPx == safeViewportPx &&
            pxPerSec == safePxPerSec
        ) {
            return
        }

        val anchorEpochSec = if (hasInitializedBounds) visibleStartEpochSec else initialTargetEpochSec
        originEpochSec = newOriginSec
        endEpochSec = safeEndSec
        viewportWidthPx = safeViewportPx
        pxPerSec = safePxPerSec

        val newMax = ((safeEndSec - newOriginSec) * safePxPerSec - safeViewportPx).coerceAtLeast(0f)
        scrollOffsetPx = ((anchorEpochSec - newOriginSec) * safePxPerSec).coerceIn(0f, newMax)
        if (safeViewportPx > 0f) {
            hasInitializedBounds = true
        }
    }

    fun scrollToEpoch(epochSec: Long) {
        if (!hasInitializedBounds) {
            initialTargetEpochSec = epochSec
        }
        val targetPx = ((epochSec - originEpochSec) * pxPerSec).coerceIn(0f, maxScrollPx)
        scrollOffsetPx = targetPx
    }

    suspend fun animateScrollToEpoch(epochSec: Long) {
        if (!hasInitializedBounds) {
            initialTargetEpochSec = epochSec
            return
        }
        val targetPx = ((epochSec - originEpochSec) * pxPerSec).coerceIn(0f, maxScrollPx)
        val delta = targetPx - scrollOffsetPx
        if (kotlin.math.abs(delta) > 1f) {
            scrollableState.animateScrollBy(delta)
        }
    }

    /**
     * Smoothly scrolls just enough to bring `[startSec, stopSec]` into the viewport when it is
     * outside (never moving if already visible, and avoiding ping-pong on shows wider than the
     * viewport).
     */
    suspend fun ensureIntervalVisible(
        startSec: Long,
        stopSec: Long,
        requireFullVisibility: Boolean = true,
    ) {
        if (!hasInitializedBounds || viewportWidthPx <= 0f) return
        val targetPx = computeEnsureVisibleScrollPx(
            startPx = (startSec - originEpochSec) * pxPerSec,
            stopPx = (stopSec - originEpochSec) * pxPerSec,
            currentScrollPx = scrollOffsetPx,
            viewportWidthPx = viewportWidthPx,
            maxScrollPx = maxScrollPx,
            requireFullVisibility = requireFullVisibility,
        )
        val delta = targetPx - scrollOffsetPx
        if (kotlin.math.abs(delta) > 1f) {
            scrollableState.animateScrollBy(delta)
        }
    }
}

@Composable
fun rememberTimelineScrollState(initialEpochSec: Long): TimelineScrollState =
    remember { TimelineScrollState(initialEpochSec) }

/**
 * Prevents Compose's `Modifier.scrollable` `ContentInViewNode` from auto-scrolling the horizontal
 * timeline when Up/Down D-pad or keyboard focus moves between channels; horizontal focus-following
 * is handled explicitly by Left/Right navigation via [TimelineScrollState.ensureIntervalVisible].
 */
val TimelineNoOpBringIntoViewSpec: BringIntoViewSpec = object : BringIntoViewSpec {
    override fun calculateScrollDistance(
        offset: Float,
        size: Float,
        containerSize: Float,
    ): Float = 0f
}

fun computeEnsureVisibleScrollPx(
    startPx: Float,
    stopPx: Float,
    currentScrollPx: Float,
    viewportWidthPx: Float,
    maxScrollPx: Float,
    requireFullVisibility: Boolean = true,
): Float {
    val viewportEndPx = currentScrollPx + viewportWidthPx
    val itemWidth = (stopPx - startPx).coerceAtLeast(0f)

    // Completely inside the viewport or already spanning the entire viewport -> do not move
    if ((startPx >= currentScrollPx && stopPx <= viewportEndPx) ||
        (startPx <= currentScrollPx && stopPx >= viewportEndPx)
    ) {
        return currentScrollPx.coerceIn(0f, maxScrollPx)
    }

    // Wider than the viewport -> avoid ping-pong; only scroll if less than half the viewport is covered
    if (itemWidth >= viewportWidthPx) {
        val visibleOverlap = (minOf(stopPx, viewportEndPx) - maxOf(startPx, currentScrollPx)).coerceAtLeast(0f)
        if (visibleOverlap >= viewportWidthPx * 0.5f) {
            return currentScrollPx.coerceIn(0f, maxScrollPx)
        }
        val rawTarget = if (startPx >= currentScrollPx) {
            startPx
        } else {
            stopPx - viewportWidthPx
        }
        return rawTarget.coerceIn(0f, maxScrollPx)
    }

    // For vertical (Up/Down) focus changes, don't jerk horizontally if the show is already comfortably visible
    if (!requireFullVisibility) {
        val visibleOverlap = (minOf(stopPx, viewportEndPx) - maxOf(startPx, currentScrollPx)).coerceAtLeast(0f)
        val comfortablePx = minOf(itemWidth * 0.4f, viewportWidthPx * 0.25f)
        if (visibleOverlap >= comfortablePx) {
            return currentScrollPx.coerceIn(0f, maxScrollPx)
        }
    }

    val rawTarget = when {
        startPx < currentScrollPx -> startPx
        stopPx > viewportEndPx -> stopPx - viewportWidthPx
        else -> currentScrollPx
    }
    return rawTarget.coerceIn(0f, maxScrollPx)
}

/**
 * Slices a channel's chronologically sorted [events] into positioned [TimelineCell]s covering
 * `[rangeStart, rangeEnd)`. Unlike [buildRowTimelineCells], overlapping events retain their true
 * `[startEpochSec, stopEpochSec]` (clamped only to a generous ±4h safety margin) so continuous
 * horizontal scrolling renders accurate cell boundaries and widths.
 */
fun buildContinuousTimelineCells(
    channelId: Long,
    events: List<EpgEvent>,
    rangeStart: Long,
    rangeEnd: Long,
): List<TimelineCell> {
    if (rangeEnd <= rangeStart) return emptyList()
    if (events.isEmpty()) {
        return listOf(
            TimelineCell(
                key = "empty_${channelId}_$rangeStart",
                weight = (rangeEnd - rangeStart).toFloat(),
                event = null,
                startEpochSec = rangeStart,
                stopEpochSec = rangeEnd,
            ),
        )
    }

    val safeMin = rangeStart - 4L * 3600L
    val safeMax = rangeEnd + 4L * 3600L
    val cells = ArrayList<TimelineCell>()
    var cursor = rangeStart
    for (ev in events) {
        if (ev.startEpochSec >= rangeEnd) break
        if (ev.stopEpochSec <= rangeStart) continue
        val evStart = ev.startEpochSec.coerceIn(safeMin, safeMax)
        val evEnd = ev.stopEpochSec.coerceIn(evStart, safeMax)
        if (evEnd <= evStart) continue

        if (evStart > cursor) {
            val gapSec = evStart - cursor
            if (gapSec >= 300L) {
                cells.add(
                    TimelineCell(
                        key = "gap_${channelId}_$cursor",
                        weight = gapSec.toFloat(),
                        event = null,
                        startEpochSec = cursor,
                        stopEpochSec = evStart,
                    ),
                )
            }
        }
        val renderedStart = maxOf(evStart, if (cells.isNotEmpty()) cells.last().stopEpochSec else safeMin)
        if (evEnd > renderedStart) {
            cells.add(
                TimelineCell(
                    key = "ev_${ev.id}",
                    weight = (evEnd - renderedStart).toFloat(),
                    event = ev,
                    startEpochSec = renderedStart,
                    stopEpochSec = evEnd,
                ),
            )
            cursor = maxOf(cursor, evEnd)
        }
    }
    if (cells.isEmpty()) {
        return listOf(
            TimelineCell(
                key = "empty_${channelId}_$rangeStart",
                weight = (rangeEnd - rangeStart).toFloat(),
                event = null,
                startEpochSec = rangeStart,
                stopEpochSec = rangeEnd,
            ),
        )
    }
    if (cursor < rangeEnd && (rangeEnd - cursor) >= 300L) {
        cells.add(
            TimelineCell(
                key = "tail_${channelId}_$cursor",
                weight = (rangeEnd - cursor).toFloat(),
                event = null,
                startEpochSec = cursor,
                stopEpochSec = rangeEnd,
            ),
        )
    }
    return cells
}

/**
 * Returns the previous (`direction < 0`) or next (`direction > 0`) event in a channel's sorted
 * [events] list relative to [currentEventId], bounded to `[minEpochSec, maxEpochSec]`.
 */
fun findAdjacentEpgEvent(
    events: List<EpgEvent>,
    currentEventId: Long,
    direction: Int,
    minEpochSec: Long,
    maxEpochSec: Long,
): EpgEvent? {
    if (events.isEmpty() || direction == 0) return null
    val idx = events.indexOfFirst { it.id == currentEventId }
    if (idx < 0) return null
    val step = if (direction > 0) 1 else -1
    var candidateIdx = idx + step
    while (candidateIdx in events.indices) {
        val candidate = events[candidateIdx]
        if (candidate.stopEpochSec > minEpochSec && candidate.startEpochSec < maxEpochSec) {
            return candidate
        }
        candidateIdx += step
    }
    return null
}

/**
 * Renders half-hour time markers and a live coral "Now" indicator synchronized with
 * [scrollState] in the layout phase (zero recomposition per scroll pixel).
 */
@Composable
fun TimelineRuler(
    scrollState: TimelineScrollState,
    bucketStartEpochSec: Long,
    bucketEndEpochSec: Long,
    nowEpochSec: Long,
    zoneId: ZoneId,
    modifier: Modifier = Modifier,
) {
    val slotStarts = remember(bucketStartEpochSec, bucketEndEpochSec) {
        val first = floorToHalfHour(bucketStartEpochSec)
        val count = (((bucketEndEpochSec - first) / HALF_HOUR_SEC).toInt() + 2).coerceIn(1, 32)
        List(count) { idx -> first + idx * HALF_HOUR_SEC }
    }
    val slotMeta = remember(slotStarts, nowEpochSec / 3600L, zoneId) {
        slotStarts.map { sec ->
            val zdt = Instant.ofEpochSecond(sec).atZone(zoneId)
            val isMidnight = zdt.hour == 0 && zdt.minute == 0
            val label = if (isMidnight) {
                "${formatDayLabel(zdt.toLocalDate(), nowEpochSec, zoneId)} · 00:00"
            } else {
                formatEpochHm(sec, zoneId)
            }
            Triple(sec, label, isMidnight)
        }
    }

    Layout(
        modifier = modifier.clipToBounds(),
        content = {
            slotMeta.forEach { (_, label, isMidnight) ->
                Text(
                    text = label,
                    style = MuxGridType.TabularSmall,
                    color = if (isMidnight) MuxGridColors.AccentSoft else MuxGridColors.TextSecondary,
                    fontWeight = if (isMidnight) FontWeight.SemiBold else FontWeight.Normal,
                    maxLines = 1,
                )
            }
            Box(
                modifier = Modifier
                    .width(2.dp)
                    .height(16.dp)
                    .background(MuxGridColors.CoralLive),
            )
        },
    ) { measurables, constraints ->
        val widthPx = constraints.maxWidth
        val heightPx = 18.dp.roundToPx()
        val labelConstraints = Constraints(maxWidth = widthPx, maxHeight = heightPx)
        val labelPlaceables = measurables.dropLast(1).map { it.measure(labelConstraints) }
        val nowPlaceable = measurables.last().measure(Constraints(maxWidth = widthPx, maxHeight = heightPx))

        layout(widthPx, heightPx) {
            val origin = scrollState.originEpochSec
            val pxPerSec = scrollState.pxPerSec
            val scrollPx = scrollState.scrollOffsetPx

            labelPlaceables.forEachIndexed { idx, placeable ->
                val sec = slotMeta[idx].first
                val x = ((sec - origin) * pxPerSec - scrollPx).roundToInt()
                if (x + placeable.width > 0 && x < widthPx) {
                    placeable.placeRelative(x = x, y = (heightPx - placeable.height) / 2)
                }
            }

            val nowX = ((nowEpochSec - origin) * pxPerSec - scrollPx).roundToInt()
            if (nowX in 0..widthPx) {
                nowPlaceable.placeRelative(x = nowX, y = (heightPx - nowPlaceable.height) / 2)
            }
        }
    }
}

/**
 * Places a channel row's [cells] and live coral "Now" hairline in exact pixel lockstep with
 * [scrollState]. Cells crossing the left or right viewport boundary are clamped to `[0, width]`,
 * which keeps long-program titles pinned at the left edge, keeps focus borders unclipped, and
 * prevents off-screen cells from overlapping the sticky channel column.
 */
@Composable
fun TimelineRowTrack(
    cells: List<TimelineCell>,
    scrollState: TimelineScrollState,
    nowEpochSec: Long,
    cellGap: Dp = 2.dp,
    modifier: Modifier = Modifier,
    cellContent: @Composable (TimelineCell) -> Unit,
) {
    Layout(
        modifier = modifier.clipToBounds(),
        content = {
            cells.forEach { cell ->
                key(cell.key) {
                    cellContent(cell)
                }
            }
            Box(
                modifier = Modifier
                    .width(2.dp)
                    .fillMaxHeight()
                    .background(MuxGridColors.CoralLive),
            )
        },
    ) { measurables, constraints ->
        val widthPx = constraints.maxWidth
        val heightPx = constraints.maxHeight
        val gapPx = cellGap.roundToPx()
        val origin = scrollState.originEpochSec
        val pxPerSec = scrollState.pxPerSec
        val scrollPx = scrollState.scrollOffsetPx

        val cellMeasurables = measurables.dropLast(1)
        val placements = ArrayList<Pair<androidx.compose.ui.layout.Placeable, Int>>(cellMeasurables.size)
        cellMeasurables.forEachIndexed { idx, measurable ->
            val cell = cells[idx]
            val rawLeft = ((cell.startEpochSec - origin) * pxPerSec - scrollPx).roundToInt()
            val rawRight = ((cell.stopEpochSec - origin) * pxPerSec - scrollPx).roundToInt() - gapPx
            val clampedLeft = rawLeft.coerceAtLeast(0)
            val clampedRight = rawRight.coerceAtMost(widthPx)
            val clampedWidth = clampedRight - clampedLeft
            if (clampedWidth >= 4) {
                val placeable = measurable.measure(Constraints.fixed(clampedWidth, heightPx))
                placements.add(placeable to clampedLeft)
            }
        }

        val nowX = ((nowEpochSec - origin) * pxPerSec - scrollPx).roundToInt()
        val nowPlaceable = if (nowX in 0..widthPx) {
            measurables.last().measure(Constraints.fixed(2.dp.roundToPx(), heightPx))
        } else {
            null
        }

        layout(widthPx, heightPx) {
            placements.forEach { (placeable, x) ->
                placeable.placeRelative(x = x, y = 0)
            }
            nowPlaceable?.placeRelative(x = nowX, y = 0)
        }
    }
}
