package io.github.corebonts.muxgrid.core.designsystem

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.BasicText
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.corebonts.muxgrid.core.model.ConnectionState
import io.github.corebonts.muxgrid.core.model.MainTab

const val STARTUP_SPLASH_TIMEOUT_MS = 2_200L
const val STARTUP_SPLASH_STATUS_DELAY_MS = 400L
const val STARTUP_SPLASH_EXIT_DURATION_MS = 500
const val STARTUP_SPLASH_EXIT_SCALE = 1.8f

/**
 * Renders the MuxGrid brand mark matching `ic_launcher_foreground.xml` (108x108 viewport).
 */
@Composable
fun MuxGridLogo(
    modifier: Modifier = Modifier.size(220.dp),
) {
    Canvas(modifier = modifier) {
        val sx = size.width / 108f
        val sy = size.height / 108f

        val rectTopLeft = Offset(30f * sx, 32f * sy)
        val rectSize = Size(48f * sx, 44f * sy)
        val cornerRadius = CornerRadius(4.5f * sx, 4.5f * sy)

        drawRoundRect(
            color = Color(0xFF141417),
            topLeft = rectTopLeft,
            size = rectSize,
            cornerRadius = cornerRadius,
        )
        drawRoundRect(
            color = Color(0x17FFFFFF),
            topLeft = rectTopLeft,
            size = rectSize,
            cornerRadius = cornerRadius,
            style = Stroke(width = 0.9f * sx),
        )

        val playPath = Path().apply {
            moveTo(47f * sx, 41.5f * sy)
            lineTo(68f * sx, 54f * sy)
            lineTo(47f * sx, 66.5f * sy)
            close()
        }
        drawPath(
            path = playPath,
            color = Color(0xFFE5A00D),
        )

        drawCircle(
            color = Color(0xFFE53935),
            radius = 3.5f * sx,
            center = Offset(71f * sx, 37f * sy),
        )
    }
}

/**
 * Full-screen startup splash keeping [MuxGridLogo] strictly centered so delayed status text
 * fades in below it without shifting the mark.
 */
@Composable
fun MuxGridSplashScreen(
    backgroundColor: Color,
    statusText: String? = null,
    showStatus: Boolean = false,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(backgroundColor),
        contentAlignment = Alignment.Center,
    ) {
        MuxGridLogo(modifier = Modifier.size(220.dp))

        AnimatedVisibility(
            visible = showStatus && !statusText.isNullOrBlank(),
            enter = fadeIn(animationSpec = tween(durationMillis = 200)),
            exit = ExitTransition.None,
            modifier = Modifier
                .align(Alignment.Center)
                .padding(top = 200.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                CircularProgressIndicator(
                    modifier = Modifier.size(16.dp),
                    strokeWidth = 2.dp,
                    color = MuxGridColors.AccentSoft,
                )
                Text(
                    text = statusText.orEmpty(),
                    style = MuxGridType.CardSubtitle,
                    color = MuxGridColors.TextSecondary,
                )
            }
        }
    }
}

/**
 * Centered empty/syncing placeholder used across Live, Guide, and Recordings tabs.
 */
@Composable
fun MuxGridSyncingState(
    message: String,
    showSpinner: Boolean,
    isTv: Boolean,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (showSpinner) {
                CircularProgressIndicator(
                    modifier = Modifier.size(22.dp),
                    strokeWidth = 2.dp,
                    color = MuxGridColors.AccentSoft,
                )
            }
            if (isTv) {
                BasicText(
                    text = message,
                    style = MuxGridType.CardSubtitle.copy(
                        color = MuxGridColors.TextSecondary,
                        fontSize = 16.sp,
                    ),
                )
            } else {
                Text(
                    text = message,
                    style = MuxGridType.CardSubtitle,
                )
            }
        }
    }
}

fun syncStatusMessage(
    connectionState: ConnectionState,
    emptyLabel: String,
    disconnectedLabel: String = "Connecting…",
    syncingLabel: String = "Synchronizing…",
): Pair<String, Boolean> = when (connectionState) {
    ConnectionState.Connecting, is ConnectionState.Syncing -> syncingLabel to true
    ConnectionState.Disconnected -> disconnectedLabel to true
    is ConnectionState.Error -> connectionState.message to false
    is ConnectionState.Connected -> emptyLabel to false
}

fun isStartupSyncReady(
    startTab: MainTab,
    connectionState: ConnectionState,
    hasChannels: Boolean,
    hasEpg: Boolean,
    hasRecordings: Boolean,
): Boolean {
    if (connectionState is ConnectionState.Connected || connectionState is ConnectionState.Error) {
        return true
    }
    return when (startTab) {
        MainTab.LIVE -> hasChannels
        MainTab.GUIDE -> hasChannels && hasEpg
        MainTab.RECORDINGS -> hasRecordings
        MainTab.SETTINGS -> true
    }
}
