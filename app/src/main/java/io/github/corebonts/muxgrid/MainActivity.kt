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

    private var appContainer: MuxGridAppContainer? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
        )
        super.onCreate(savedInstanceState)

        val container = (application as MuxGridApplication).container
        appContainer = container
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

    override fun onStart() {
        super.onStart()
        appContainer?.repository?.setForeground(isForeground = true)
    }

    override fun onStop() {
        val container = appContainer
        if (container != null) {
            val status = container.playbackEngine.state.value.status
            val keepAlive = status == PlaybackStatus.PLAYING || status == PlaybackStatus.BUFFERING
            container.repository.setForeground(
                isForeground = false,
                keepAliveInBackground = keepAlive,
            )
        }
        super.onStop()
    }
}
