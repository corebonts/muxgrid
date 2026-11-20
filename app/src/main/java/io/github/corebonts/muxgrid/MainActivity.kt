package io.github.corebonts.muxgrid

import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import io.github.corebonts.muxgrid.core.designsystem.MuxGridTheme
import io.github.corebonts.muxgrid.core.model.FormFactor
import io.github.corebonts.muxgrid.core.model.PlaybackStatus
import io.github.corebonts.muxgrid.feature.mobile.MobileMvpRoot
import io.github.corebonts.muxgrid.feature.tv.TvMvpRoot

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
        )
        super.onCreate(savedInstanceState)

        val container = (application as MuxGridApplication).container
        val isTv = container.formFactor == FormFactor.TV

        setContent {
            val playbackState by container.playbackEngine.state.collectAsState()
            val keepScreenOn = !playbackState.isRadio &&
                (playbackState.status == PlaybackStatus.PLAYING || playbackState.status == PlaybackStatus.BUFFERING)

            DisposableEffect(keepScreenOn) {
                if (keepScreenOn) {
                    window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                } else {
                    window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                }
                onDispose {
                    window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                }
            }

            MuxGridTheme(isTv = isTv) {
                if (isTv) {
                    TvMvpRoot(
                        repository = container.repository,
                        playbackEngine = container.playbackEngine,
                    )
                } else {
                    MobileMvpRoot(
                        repository = container.repository,
                        playbackEngine = container.playbackEngine,
                    )
                }
            }
        }
    }
}
