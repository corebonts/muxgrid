package io.github.corebonts.muxgrid

import android.app.UiModeManager
import android.content.Context
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.os.Build
import android.os.Bundle
import android.os.StatFs
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import io.github.corebonts.muxgrid.core.designsystem.MuxGridColors
import io.github.corebonts.muxgrid.core.model.FormFactor
import io.github.corebonts.muxgrid.core.model.HwDecPreset
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        val formFactor = detectFormFactor(this)
        val freeDataMb = StatFs(filesDir.absolutePath).availableBytes / (1024L * 1024L)
        val abis = Build.SUPPORTED_ABIS.joinToString(",")

        setContent {
            MaterialTheme {
                SpikeScreen(
                    formFactor = formFactor,
                    abis = abis,
                    freeDataMb = freeDataMb,
                )
            }
        }
    }

    private fun detectFormFactor(context: Context): FormFactor {
        val uiModeManager = context.getSystemService(Context.UI_MODE_SERVICE) as UiModeManager
        val isTvUi = uiModeManager.currentModeType == Configuration.UI_MODE_TYPE_TELEVISION
        val hasLeanback = context.packageManager.hasSystemFeature(PackageManager.FEATURE_LEANBACK)
        return if (isTvUi || hasLeanback) FormFactor.TV else FormFactor.PHONE
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SpikeScreen(
    formFactor: FormFactor,
    abis: String,
    freeDataMb: Long,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var mpvView by remember { mutableStateOf<SpikeMpvView?>(null) }
    var preset by remember { mutableStateOf(HwDecPreset.MEDIACODEC_ZERO_COPY) }
    var activeChannel by remember { mutableStateOf("Idle") }
    var stats by remember { mutableStateOf(SpikeStats()) }

    LaunchedEffect(mpvView) {
        while (true) {
            mpvView?.let { stats = it.readStats() }
            delay(1000L)
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            mpvView?.destroy()
        }
    }

    fun buildChannelUrl(channelNumber: Int): String {
        val host = BuildConfig.SPIKE_TVH_HOST
        val user = BuildConfig.SPIKE_TVH_USER
        val pass = BuildConfig.SPIKE_TVH_PASS
        val auth = if (user.isNotEmpty()) "$user:$pass@" else ""
        return "http://$auth$host:9981/stream/channelnumber/$channelNumber?profile=pass"
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MuxGridColors.CanvasTv),
    ) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { ctx ->
                SpikeMpvView(ctx).apply {
                    val cfgDir = File(ctx.filesDir, "mpv").apply { mkdirs() }.absolutePath
                    val cacheDir = File(ctx.cacheDir, "mpv").apply { mkdirs() }.absolutePath
                    initialize(cfgDir, cacheDir)
                    mpvView = this
                }
            },
        )

        // Top-right diagnostic stats scrim
        Column(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(16.dp)
                .background(MuxGridColors.ScrimSurface, RoundedCornerShape(12.dp))
                .border(1.dp, Color.White.copy(alpha = 0.12f), RoundedCornerShape(12.dp))
                .padding(12.dp),
        ) {
            Text(
                text = "MuxGrid M0 Spike | $formFactor | ABI: $abis | Free: ${freeDataMb}MB",
                color = Color.White,
                fontSize = 12.sp,
                fontFamily = FontFamily.Monospace,
            )
            Text(
                text = "Stream: $activeChannel | hwdec: ${stats.hwdecCurrent} | deint: ${stats.deinterlace}",
                color = Color(0xFF93C5FD),
                fontSize = 12.sp,
                fontFamily = FontFamily.Monospace,
            )
            Text(
                text = "Video: ${stats.resolution} @ ${stats.fps}fps | drops: ${stats.droppedFrames} | aid=${stats.aid} sid=${stats.sid}",
                color = Color(0xFFD1D5DB),
                fontSize = 12.sp,
                fontFamily = FontFamily.Monospace,
            )
            Text(
                text = "State: pause=${stats.paused} | idle=${stats.coreIdle} | pos=${stats.timePos}",
                color = Color(0xFFA7F3D0),
                fontSize = 12.sp,
                fontFamily = FontFamily.Monospace,
            )
        }

        // Bottom control bar (D-pad + touch friendly)
        FlowRow(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(16.dp)
                .background(MuxGridColors.ScrimSurface, RoundedCornerShape(16.dp))
                .border(1.dp, Color.White.copy(alpha = 0.12f), RoundedCornerShape(16.dp))
                .padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            SpikeButton("Play Ch 4 (HD)") {
                activeChannel = "Ch 4"
                scope.launch(Dispatchers.IO) { mpvView?.loadUrl(buildChannelUrl(4)) }
            }
            SpikeButton("Play Ch 1") {
                activeChannel = "Ch 1"
                scope.launch(Dispatchers.IO) { mpvView?.loadUrl(buildChannelUrl(1)) }
            }
            SpikeButton("Stop") {
                activeChannel = "Stopped"
                scope.launch(Dispatchers.IO) { mpvView?.stopPlayback() }
            }
            SpikeButton("Pause/Play") {
                scope.launch(Dispatchers.IO) { mpvView?.togglePause() }
            }
            SpikeButton(
                label = if (preset == HwDecPreset.MEDIACODEC_ZERO_COPY) {
                    "HwDec: mediacodec"
                } else {
                    "HwDec: copy + bwdif"
                },
            ) {
                val next = if (preset == HwDecPreset.MEDIACODEC_ZERO_COPY) {
                    HwDecPreset.MEDIACODEC_COPY_BWDIF
                } else {
                    HwDecPreset.MEDIACODEC_ZERO_COPY
                }
                preset = next
                scope.launch(Dispatchers.IO) { mpvView?.applyPreset(next) }
            }
            SpikeButton("Audio (aid)") {
                scope.launch(Dispatchers.IO) { mpvView?.cycleAudio() }
            }
            SpikeButton("Sub (sid)") {
                scope.launch(Dispatchers.IO) { mpvView?.cycleSubtitle() }
            }
            SpikeButton("-10s") {
                scope.launch(Dispatchers.IO) { mpvView?.seekRelative(-10) }
            }
            SpikeButton("+30s") {
                scope.launch(Dispatchers.IO) { mpvView?.seekRelative(30) }
            }
        }
    }
}

@Composable
private fun SpikeButton(
    label: String,
    onClick: () -> Unit,
) {
    Button(
        onClick = onClick,
        colors = ButtonDefaults.buttonColors(
            containerColor = MuxGridColors.CardSurface,
            contentColor = Color.White,
        ),
    ) {
        Text(text = label, fontSize = 13.sp)
    }
}
