package io.github.corebonts.muxgrid.core.designsystem

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class BroadcastPagerTabRowTest {

    private val tabs = listOf(
        TabBounds(leftPx = 0f, widthPx = 40f),
        TabBounds(leftPx = 58f, widthPx = 60f),
        TabBounds(leftPx = 136f, widthPx = 80f),
    )

    @Test
    fun `computePagerIndicatorBounds returns exact bounds when settled`() {
        val result = computePagerIndicatorBounds(
            tabCount = tabs.size,
            currentPage = 1,
            currentPageOffsetFraction = 0f,
            boundsFor = { tabs.getOrNull(it) },
        )
        assertEquals(TabBounds(leftPx = 58f, widthPx = 60f), result)
    }

    @Test
    fun `computePagerIndicatorBounds interpolates continuously across midpoint`() {
        val beforeMidpoint = computePagerIndicatorBounds(
            tabCount = tabs.size,
            currentPage = 0,
            currentPageOffsetFraction = 0.25f,
            boundsFor = { tabs.getOrNull(it) },
        )!!
        assertEquals(14.5f, beforeMidpoint.leftPx, 0.001f)
        assertEquals(45f, beforeMidpoint.widthPx, 0.001f)

        // When dragging past halfway, PagerState switches currentPage to 1 with negative offset -0.25.
        // That represents page position 0.75, which must stay continuous with 0->1 interpolation.
        val afterMidpoint = computePagerIndicatorBounds(
            tabCount = tabs.size,
            currentPage = 1,
            currentPageOffsetFraction = -0.25f,
            boundsFor = { tabs.getOrNull(it) },
        )!!
        assertEquals(43.5f, afterMidpoint.leftPx, 0.001f)
        assertEquals(55f, afterMidpoint.widthPx, 0.001f)
    }

    @Test
    fun `computePagerIndicatorBounds clamps overscroll and handles missing bounds`() {
        val clampedStart = computePagerIndicatorBounds(
            tabCount = tabs.size,
            currentPage = 0,
            currentPageOffsetFraction = -0.3f,
            boundsFor = { tabs.getOrNull(it) },
        )
        assertEquals(tabs.first(), clampedStart)

        val clampedEnd = computePagerIndicatorBounds(
            tabCount = tabs.size,
            currentPage = 2,
            currentPageOffsetFraction = 0.4f,
            boundsFor = { tabs.getOrNull(it) },
        )
        assertEquals(tabs.last(), clampedEnd)

        assertNull(
            computePagerIndicatorBounds(
                tabCount = 0,
                currentPage = 0,
                currentPageOffsetFraction = 0f,
                boundsFor = { tabs.getOrNull(it) },
            ),
        )
        assertNull(
            computePagerIndicatorBounds(
                tabCount = tabs.size,
                currentPage = 1,
                currentPageOffsetFraction = 0f,
                boundsFor = { null },
            ),
        )
    }
}
