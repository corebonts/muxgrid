package io.github.corebonts.muxgrid.feature.tv

import io.github.corebonts.muxgrid.core.model.PlaybackSeekDefaults
import io.github.corebonts.muxgrid.core.model.PlaybackState
import kotlin.math.abs

internal data class TvSeekUiState(
    val pendingTargetMs: Long? = null,
    val pendingDeltaMs: Long = 0L,
    val direction: Int = 0,
    val badgeLabel: String = "",
    val osdVisible: Boolean = false,
    val isHolding: Boolean = false,
)

internal data class SeekKeyResult(
    val uiState: TvSeekUiState,
    val commitDelayMs: Long?,
)

/**
 * Pure state machine for TV remote progressive seeking (D-pad Left/Right & Rewind/Fast-Forward):
 * - Tapping advances along an exact-target ladder (`10s -> 30s -> 1m -> 5m -> 10m`, then `+5m` per tap),
 *   so 5 taps always lands at exactly `10m`.
 * - Tapping in the opposite direction steps back down the ladder and cleanly cancels at `0s`.
 * - Holding locks the continuous scrub increment to the step size of the tap that started the hold,
 *   capped at `30s` per throttled tick (`240ms`), preventing runaway scrubbing.
 */
internal class TvSeekController {

    private var anchorPositionMs: Long = 0L
    private var hasActivePending: Boolean = false
    private var tapTier: Int = 0
    private var hasHeldInSession: Boolean = false
    private var holdGearMs: Long = MIN_HOLD_GEAR_MS
    private var activeHoldDirection: Int = 0
    private var lastHoldTickElapsedMs: Long = 0L
    private var pendingDeltaMs: Long = 0L
    private var pendingTargetMs: Long? = null
    private var osdVisible: Boolean = false

    fun currentUiState(): TvSeekUiState = TvSeekUiState(
        pendingTargetMs = pendingTargetMs,
        pendingDeltaMs = pendingDeltaMs,
        direction = pendingDeltaMs.dirSign(),
        badgeLabel = if (osdVisible) formatSeekDeltaLabel(pendingDeltaMs) else "",
        osdVisible = osdVisible,
        isHolding = activeHoldDirection != 0 && hasHeldInSession,
    )

    fun onKeyDown(
        direction: Int,
        isRepeat: Boolean,
        playbackState: PlaybackState,
        nowElapsedMs: Long,
    ): SeekKeyResult {
        require(direction == -1 || direction == 1) { "direction must be -1 or 1, was $direction" }

        if (!isRepeat || !hasActivePending || (activeHoldDirection != 0 && activeHoldDirection != direction)) {
            return handleFreshPress(direction, playbackState, nowElapsedMs)
        }

        activeHoldDirection = direction
        if (nowElapsedMs - lastHoldTickElapsedMs < HOLD_TICK_INTERVAL_MS) {
            return SeekKeyResult(
                uiState = currentUiState(),
                commitDelayMs = HOLD_STALE_COMMIT_DELAY_MS,
            )
        }

        hasHeldInSession = true
        lastHoldTickElapsedMs = nowElapsedMs
        val rawNext = pendingDeltaMs + direction * holdGearMs
        val snappedNext = if (pendingDeltaMs != 0L && rawNext.dirSign() != pendingDeltaMs.dirSign()) 0L else rawNext
        val clampedTarget = playbackState.clampSeekPositionMs(anchorPositionMs + snappedNext)
        pendingDeltaMs = clampedTarget - anchorPositionMs
        pendingTargetMs = clampedTarget
        osdVisible = true
        return SeekKeyResult(
            uiState = currentUiState(),
            commitDelayMs = HOLD_STALE_COMMIT_DELAY_MS,
        )
    }

    private fun handleFreshPress(
        direction: Int,
        playbackState: PlaybackState,
        nowElapsedMs: Long,
    ): SeekKeyResult {
        if (!hasActivePending) {
            anchorPositionMs = playbackState.positionMs
            hasActivePending = true
            hasHeldInSession = false
            tapTier = 0
            holdGearMs = MIN_HOLD_GEAR_MS
            activeHoldDirection = direction
            lastHoldTickElapsedMs = nowElapsedMs
            val clampedTarget = playbackState.clampSeekPositionMs(anchorPositionMs + direction * ladderDeltaForTier(0))
            pendingDeltaMs = clampedTarget - anchorPositionMs
            pendingTargetMs = clampedTarget
            osdVisible = true
            return SeekKeyResult(
                uiState = currentUiState(),
                commitDelayMs = TAP_COMMIT_DELAY_MS,
            )
        }

        activeHoldDirection = direction
        lastHoldTickElapsedMs = nowElapsedMs

        if (!hasHeldInSession) {
            val currentDir = pendingDeltaMs.dirSign()
            if (currentDir == 0 || currentDir == direction) {
                val nextTier = if (currentDir == 0) 0 else tapTier + 1
                val rawDelta = direction * ladderDeltaForTier(nextTier)
                val clampedTarget = playbackState.clampSeekPositionMs(anchorPositionMs + rawDelta)
                val actualDelta = clampedTarget - anchorPositionMs
                if (actualDelta != pendingDeltaMs) {
                    tapTier = nextTier
                }
                holdGearMs = holdGearForTier(nextTier)
                pendingDeltaMs = actualDelta
                pendingTargetMs = clampedTarget
            } else {
                // Opposite tap in ladder mode steps back toward anchor cleanly.
                if (tapTier > 0) {
                    tapTier -= 1
                    val rawDelta = currentDir * ladderDeltaForTier(tapTier)
                    val clampedTarget = playbackState.clampSeekPositionMs(anchorPositionMs + rawDelta)
                    pendingDeltaMs = clampedTarget - anchorPositionMs
                    pendingTargetMs = clampedTarget
                } else {
                    pendingDeltaMs = 0L
                    pendingTargetMs = anchorPositionMs
                }
                holdGearMs = MIN_HOLD_GEAR_MS
            }
        } else {
            val currentDir = pendingDeltaMs.dirSign()
            if (currentDir == 0 || currentDir == direction) {
                holdGearMs = nextHoldGear(holdGearMs)
            } else {
                holdGearMs = MIN_HOLD_GEAR_MS
            }
            val rawNext = pendingDeltaMs + direction * holdGearMs
            val snappedNext = if (pendingDeltaMs != 0L && rawNext.dirSign() != pendingDeltaMs.dirSign()) 0L else rawNext
            val clampedTarget = playbackState.clampSeekPositionMs(anchorPositionMs + snappedNext)
            pendingDeltaMs = clampedTarget - anchorPositionMs
            pendingTargetMs = clampedTarget
        }

        osdVisible = true
        return SeekKeyResult(
            uiState = currentUiState(),
            commitDelayMs = TAP_COMMIT_DELAY_MS,
        )
    }

    fun onKeyUp(direction: Int): Long? {
        if (!hasActivePending) return null
        val wasHolding = hasHeldInSession && activeHoldDirection == direction
        if (activeHoldDirection == direction) {
            activeHoldDirection = 0
        }
        return if (wasHolding) HOLD_RELEASE_COMMIT_DELAY_MS else TAP_COMMIT_AFTER_KEY_UP_MS
    }

    /**
     * Commits the pending seek session and returns the absolute target in milliseconds to seek to,
     * or `null` if there is nothing pending or the user stepped back to `0s` (no-op).
     */
    fun consumeCommitTarget(): Long? {
        if (!hasActivePending) return null
        val targetToSeek = if (pendingDeltaMs != 0L) pendingTargetMs else null
        hasActivePending = false
        pendingTargetMs = null
        tapTier = 0
        hasHeldInSession = false
        holdGearMs = MIN_HOLD_GEAR_MS
        activeHoldDirection = 0
        if (pendingDeltaMs == 0L) {
            osdVisible = false
        }
        return targetToSeek
    }

    fun hideOsd() {
        if (!hasActivePending) {
            osdVisible = false
            pendingDeltaMs = 0L
        }
    }

    fun reset() {
        anchorPositionMs = 0L
        hasActivePending = false
        tapTier = 0
        hasHeldInSession = false
        holdGearMs = MIN_HOLD_GEAR_MS
        activeHoldDirection = 0
        lastHoldTickElapsedMs = 0L
        pendingDeltaMs = 0L
        pendingTargetMs = null
        osdVisible = false
    }

    companion object {
        private val TAP_LADDER_MS = longArrayOf(
            PlaybackSeekDefaults.SEEK_BACK_STEP_MS,    // 1 tap: 10s
            PlaybackSeekDefaults.SEEK_FORWARD_STEP_MS, // 2 taps: 30s
            60_000L,   // 3 taps: 1m
            300_000L,  // 4 taps: 5m
            600_000L,  // 5 taps: 10m
        )
        private const val EXTRA_TAP_STEP_MS = 300_000L
        const val MIN_HOLD_GEAR_MS = PlaybackSeekDefaults.SEEK_BACK_STEP_MS
        const val MAX_HOLD_GEAR_MS = PlaybackSeekDefaults.SEEK_FORWARD_STEP_MS
        const val HOLD_TICK_INTERVAL_MS = 240L
        const val TAP_COMMIT_DELAY_MS = 650L
        const val TAP_COMMIT_AFTER_KEY_UP_MS = 500L
        const val HOLD_RELEASE_COMMIT_DELAY_MS = 0L
        const val HOLD_STALE_COMMIT_DELAY_MS = 600L
        const val OSD_LINGER_MS = 1_800L

        internal fun ladderDeltaForTier(tier: Int): Long {
            val idx = tier.coerceAtLeast(0)
            return if (idx < TAP_LADDER_MS.size) {
                TAP_LADDER_MS[idx]
            } else {
                TAP_LADDER_MS.last() + (idx - TAP_LADDER_MS.lastIndex) * EXTRA_TAP_STEP_MS
            }
        }

        private fun holdGearForTier(tier: Int): Long {
            if (tier <= 0) return MIN_HOLD_GEAR_MS
            val delta = ladderDeltaForTier(tier)
            return delta.coerceIn(MIN_HOLD_GEAR_MS, MAX_HOLD_GEAR_MS)
        }

        private fun nextHoldGear(currentGearMs: Long): Long =
            if (currentGearMs < MAX_HOLD_GEAR_MS) MAX_HOLD_GEAR_MS else currentGearMs
    }
}

internal fun formatSeekDeltaLabel(deltaMs: Long): String {
    if (deltaMs == 0L) return "0s"
    val sign = if (deltaMs < 0L) "-" else "+"
    val totalSec = ((abs(deltaMs) + 500L) / 1000L).coerceAtLeast(1L)
    val hours = totalSec / 3600L
    val mins = (totalSec % 3600L) / 60L
    val secs = totalSec % 60L
    return buildString {
        append(sign)
        val parts = ArrayList<String>(3)
        if (hours > 0L) parts.add("${hours}h")
        if (mins > 0L) parts.add("${mins}m")
        if (secs > 0L || parts.isEmpty()) parts.add("${secs}s")
        append(parts.joinToString(" "))
    }
}

private fun Long.dirSign(): Int = when {
    this > 0L -> 1
    this < 0L -> -1
    else -> 0
}
