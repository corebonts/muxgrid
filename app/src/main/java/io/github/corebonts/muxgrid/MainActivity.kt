package io.github.corebonts.muxgrid

import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.corebonts.muxgrid.core.designsystem.MuxGridTheme
import io.github.corebonts.muxgrid.core.model.FormFactor
import io.github.corebonts.muxgrid.core.model.PlaybackStatus
import io.github.corebonts.muxgrid.feature.mobile.MobileMvpRoot
import io.github.corebonts.muxgrid.feature.tv.TvMvpRoot
import java.io.File

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
                val context = LocalContext.current
                var lastCrashText by remember {
                    mutableStateOf(
                        runCatching {
                            val f = File(context.filesDir, "last_crash.txt")
                            if (f.exists()) f.readText().takeIf { it.isNotBlank() } else null
                        }.getOrNull(),
                    )
                }

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

                lastCrashText?.let { crashText ->
                    AlertDialog(
                        onDismissRequest = {
                            runCatching { File(context.filesDir, "last_crash.txt").delete() }
                            lastCrashText = null
                        },
                        title = { Text("Previous Crash Log") },
                        text = {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(Color(0xFF11131A))
                                    .padding(8.dp)
                                    .verticalScroll(rememberScrollState()),
                                verticalArrangement = Arrangement.spacedBy(4.dp),
                            ) {
                                Text(
                                    text = crashText.take(3500),
                                    fontSize = 11.sp,
                                    color = Color(0xFFF8FAFC),
                                )
                            }
                        },
                        confirmButton = {
                            Row {
                                TextButton(
                                    onClick = {
                                        val cm = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                                        cm.setPrimaryClip(android.content.ClipData.newPlainText("MuxGrid Crash", crashText))
                                    },
                                ) {
                                    Text("Copy")
                                }
                                TextButton(
                                    onClick = {
                                        runCatching { File(context.filesDir, "last_crash.txt").delete() }
                                        lastCrashText = null
                                    },
                                ) {
                                    Text("Dismiss")
                                }
                            }
                        },
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
            container.flushDvrProgress()
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
