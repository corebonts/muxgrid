package io.github.corebonts.muxgrid.core.designsystem

import androidx.compose.foundation.gestures.BringIntoViewSpec
import androidx.compose.foundation.gestures.ScrollableState
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
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
import androidx.compose.ui.graphics.graphicsLayer
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
    minStartEpochSec: Long = Long.MIN_VALUE,
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

    val safeMin = maxOf(rangeStart - 4L * 3600L, minStartEpochSec)
    val safeMax = rangeEnd + 4L * 3600L
    val cells = ArrayList<TimelineCell>()
    var cursor = rangeStart
    // Binary-search near rangeStart - 24h so multi-week EPG lists skip earlier days in O(log N)
    var startIdx = 0
    if (events.size > 16) {
        val searchStart = rangeStart - 24L * 3600L
        var lo = 0
        var hi = events.size - 1
        while (lo < hi) {
            val mid = (lo + hi) ushr 1
            if (events[mid].startEpochSec < searchStart) {
                lo = mid + 1
            } else {
                hi = mid
            }
        }
        startIdx = lo
    }
    for (i in startIdx until events.size) {
        val ev = events[i]
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
 * Computes a quantized horizontal timeline buffer window with hysteresis so small scroll steps
 * do not invalidate visible rows or re-slice EPG cells every half-hour boundary.
 */
fun computeTimelineBufferWindow(
    visibleStartSec: Long,
    visibleDurationSec: Long,
    timelineOriginSec: Long,
    timelineEndSec: Long,
    prevStartSec: Long,
    prevEndSec: Long,
): Pair<Long, Long> {
    val visEnd = visibleStartSec + visibleDurationSec
    val marginSec = HALF_HOUR_SEC
    val needsRebuild = prevEndSec <= prevStartSec ||
        prevStartSec < timelineOriginSec ||
        prevEndSec > timelineEndSec ||
        (visibleStartSec < prevStartSec + marginSec && prevStartSec > timelineOriginSec) ||
        (visEnd > prevEndSec - marginSec && prevEndSec < timelineEndSec)
    if (!needsRebuild) {
        return prevStartSec to prevEndSec
    }
    val anchor = floorToHalfHour(visibleStartSec)
    val nextStart = (anchor - 3600L).coerceAtLeast(timelineOriginSec)
    val nextEnd = (anchor + visibleDurationSec + 2L * 3600L).coerceAtMost(timelineEndSec)
    return nextStart to nextEnd
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
    currentStartEpochSec: Long? = null,
): EpgEvent? {
    if (events.isEmpty() || direction == 0) return null
    var idx = -1
    if (currentStartEpochSec != null) {
        // Binary search by startEpochSec first, then scan matching/nearby items
        var lo = 0
        var hi = events.size - 1
        while (lo <= hi) {
            val mid = (lo + hi) ushr 1
            val s = events[mid].startEpochSec
            when {
                s < currentStartEpochSec -> lo = mid + 1
                s > currentStartEpochSec -> hi = mid - 1
                else -> {
                    if (events[mid].id == currentEventId) {
                        idx = mid
                        break
                    }
                    var k = mid - 1
                    while (k >= 0 && events[k].startEpochSec == currentStartEpochSec) {
                        if (events[k].id == currentEventId) {
                            idx = k
                            break
                        }
                        k--
                    }
                    if (idx < 0) {
                        k = mid + 1
                        while (k < events.size && events[k].startEpochSec == currentStartEpochSec) {
                            if (events[k].id == currentEventId) {
                                idx = k
                                break
                            }
                            k++
                        }
                    }
                    break
                }
            }
        }
    }
    if (idx < 0) {
        idx = events.indexOfFirst { it.id == currentEventId }
    }
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
 * Renders half-hour time markers synchronized with [scrollState] via a single `graphicsLayer`
 * translation (zero recomposition and zero measure/layout per horizontal scroll frame).
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
    val firstSlot = floorToHalfHour(bucketStartEpochSec)
    val lastSlot = floorToHalfHour(bucketEndEpochSec) + HALF_HOUR_SEC
    val slotStarts = remember(firstSlot, lastSlot) {
        val count = (((lastSlot - firstSlot) / HALF_HOUR_SEC).toInt() + 1).coerceIn(1, 32)
        List(count) { idx -> firstSlot + idx * HALF_HOUR_SEC }
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

    Box(
        modifier = modifier
            .height(18.dp)
            .clipToBounds(),
    ) {
        Layout(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    translationX = (firstSlot - scrollState.originEpochSec) * scrollState.pxPerSec - scrollState.scrollOffsetPx
                },
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
            },
        ) { measurables, constraints ->
            val widthPx = constraints.maxWidth
            val heightPx = constraints.maxHeight
            val labelConstraints = Constraints(maxWidth = widthPx, maxHeight = heightPx)
            val labelPlaceables = measurables.map { it.measure(labelConstraints) }
            val origin = scrollState.originEpochSec
            val pxPerSec = scrollState.pxPerSec

            layout(widthPx, heightPx) {
                labelPlaceables.forEachIndexed { idx, placeable ->
                    val sec = slotMeta[idx].first
                    if (sec >= origin) {
                        val x = ((sec - firstSlot) * pxPerSec).roundToInt()
                        placeable.placeRelative(x = x, y = (heightPx - placeable.height) / 2)
                    }
                }
            }
        }
    }
}

/**
 * Draw-phase-only horizontal offset that keeps a program cell's text pinned to the left edge of
 * the timeline viewport while the cell's start is scrolled off-screen to the left.
 */
fun Modifier.timelineStickyTextOffset(
    cellStartSec: Long,
    cellStopSec: Long,
    scrollState: TimelineScrollState,
): Modifier = graphicsLayer {
    val origin = scrollState.originEpochSec
    val pxPerSec = scrollState.pxPerSec
    val rawLeft = (cellStartSec - origin) * pxPerSec - scrollState.scrollOffsetPx
    if (rawLeft < 0f) {
        val cellWidth = (cellStopSec - cellStartSec) * pxPerSec
        val maxShift = (cellWidth - 96f).coerceAtLeast(0f)
        translationX = (-rawLeft).coerceAtMost(maxShift)
    } else {
        translationX = 0f
    }
}

/**
 * Places a channel row's [cells] relative to [anchorEpochSec] and translates the whole track via a
 * single `graphicsLayer` reading [TimelineScrollState.scrollOffsetPx]. Horizontal scrolling performs
 * zero recomposition, zero re-measurement, and zero per-frame Compose layout invalidations.
 */
@Composable
fun TimelineRowTrack(
    cells: List<TimelineCell>,
    scrollState: TimelineScrollState,
    cellGap: Dp = 2.dp,
    modifier: Modifier = Modifier,
    anchorEpochSec: Long = cells.firstOrNull()?.startEpochSec ?: scrollState.originEpochSec,
    cellContent: @Composable (TimelineCell) -> Unit,
) {
    Box(modifier = modifier.clipToBounds()) {
        Layout(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    translationX = (anchorEpochSec - scrollState.originEpochSec) * scrollState.pxPerSec - scrollState.scrollOffsetPx
                },
            content = {
                cells.forEach { cell ->
                    key(cell.key) {
                        cellContent(cell)
                    }
                }
            },
        ) { measurables, constraints ->
            val widthPx = constraints.maxWidth
            val heightPx = constraints.maxHeight
            val gapPx = cellGap.roundToPx()
            val pxPerSec = scrollState.pxPerSec
            val maxCellWidthPx = (widthPx * 4).coerceAtLeast(widthPx)

            val cellPlaceables = ArrayList<androidx.compose.ui.layout.Placeable>(measurables.size)
            measurables.forEachIndexed { idx, measurable ->
                val cell = cells[idx]
                val naturalWidthPx = (((cell.stopEpochSec - cell.startEpochSec) * pxPerSec).roundToInt() - gapPx)
                    .coerceIn(4, maxCellWidthPx)
                cellPlaceables.add(measurable.measure(Constraints.fixed(naturalWidthPx, heightPx)))
            }

            layout(widthPx, heightPx) {
                cellPlaceables.forEachIndexed { idx, placeable ->
                    val cell = cells[idx]
                    val x = ((cell.startEpochSec - anchorEpochSec) * pxPerSec).roundToInt()
                    placeable.placeRelative(x = x, y = 0)
                }
            }
        }
    }
}
