package io.github.corebonts.muxgrid

import android.app.PictureInPictureParams
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.os.Build
import android.os.Bundle
import android.util.Rational
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.corebonts.muxgrid.core.designsystem.MuxGridColors
import io.github.corebonts.muxgrid.core.designsystem.MuxGridShapes
import io.github.corebonts.muxgrid.core.designsystem.MuxGridTheme
import io.github.corebonts.muxgrid.core.model.AppBuildInfo
import io.github.corebonts.muxgrid.core.model.FormFactor
import io.github.corebonts.muxgrid.core.model.PlaybackState
import io.github.corebonts.muxgrid.core.model.PlaybackStatus
import io.github.corebonts.muxgrid.core.model.ThemeMode
import io.github.corebonts.muxgrid.core.player.MuxGridMediaSessionService
import io.github.corebonts.muxgrid.feature.mobile.MobileRoot
import io.github.corebonts.muxgrid.feature.tv.TvRoot
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import java.io.File

class MainActivity : ComponentActivity() {

    private var appContainer: MuxGridAppContainer? = null
    private var isInPipModeState by mutableStateOf(false)
    private var isMobilePlayerRouteActive by mutableStateOf(false)
    private var openPlayerSignal by mutableIntStateOf(0)

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
        )
        super.onCreate(savedInstanceState)

        val container = (application as MuxGridApplication).container
        appContainer = container
        val isTv = container.formFactor == FormFactor.TV
        val pipSupported = !isTv && packageManager.hasSystemFeature(PackageManager.FEATURE_PICTURE_IN_PICTURE)

        if (intent?.getBooleanExtra(MuxGridMediaSessionService.EXTRA_OPEN_PLAYER, false) == true) {
            openPlayerSignal++
        }

        setContent {
            val pipEnabled by container.repository.pipEnabled.collectAsState()
            val themeMode by container.repository.themeMode.collectAsState()
            val systemDark = isSystemInDarkTheme()
            val isDarkTheme = when (themeMode) {
                ThemeMode.AUTO -> systemDark
                ThemeMode.LIGHT -> false
                ThemeMode.DARK -> true
            }
            val useDarkSystemBars = isDarkTheme || isMobilePlayerRouteActive
            LaunchedEffect(useDarkSystemBars) {
                val barStyle = if (useDarkSystemBars) {
                    SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
                } else {
                    SystemBarStyle.light(
                        android.graphics.Color.TRANSPARENT,
                        android.graphics.Color.TRANSPARENT,
                    )
                }
                enableEdgeToEdge(
                    statusBarStyle = barStyle,
                    navigationBarStyle = barStyle,
                )
            }
            val rootView = LocalView.current
            val keepScreenOn by remember(container.playbackEngine) {
                container.playbackEngine.state
                    .map { pb ->
                        !pb.isRadio &&
                            !pb.isAudioOnly &&
                            (pb.status == PlaybackStatus.PLAYING || pb.status == PlaybackStatus.BUFFERING)
                    }
                    .distinctUntilChanged()
            }.collectAsState(initial = false)

            // Use View.keepScreenOn rather than Window.addFlags/clearFlags so ViewRootImpl
            // updates the flag during layout without invoking WindowManagerImpl.updateViewLayout
            // (which triggers a Xiaomi HyperOS 4 SeempLog fdsan crash on Window.clearFlags).
            LaunchedEffect(rootView, keepScreenOn) {
                rootView.keepScreenOn = keepScreenOn
            }
            DisposableEffect(rootView) {
                onDispose {
                    rootView.keepScreenOn = false
                }
            }

            if (pipSupported && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val videoDims by remember(container.playbackEngine) {
                    container.playbackEngine.state
                        .map { pb -> pb.videoWidth to pb.videoHeight }
                        .distinctUntilChanged()
                }.collectAsState(initial = 0 to 0)
                val shouldAutoPip = isMobilePlayerRouteActive && pipEnabled && keepScreenOn
                LaunchedEffect(shouldAutoPip, videoDims) {
                    runCatching {
                        setPictureInPictureParams(
                            buildPipParams(
                                videoWidth = videoDims.first,
                                videoHeight = videoDims.second,
                                autoEnter = shouldAutoPip,
                            ),
                        )
                    }
                }
            }

            MuxGridTheme(isTv = isTv, darkTheme = isDarkTheme) {
                val context = LocalContext.current
                val isDebuggable = (context.applicationInfo.flags and android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE) != 0
                var lastCrashText by remember {
                    mutableStateOf(
                        if (!isDebuggable) {
                            null
                        } else {
                            runCatching {
                                val f = File(context.filesDir, "last_crash.txt")
                                if (f.exists()) f.readText().takeIf { it.isNotBlank() } else null
                            }.getOrNull()
                        },
                    )
                }

                val appInfo = remember {
                    AppBuildInfo(
                        versionName = BuildConfig.VERSION_NAME,
                        versionCode = BuildConfig.VERSION_CODE,
                        buildSha = BuildConfig.GIT_SHA,
                    )
                }

                if (isTv) {
                    TvRoot(
                        repository = container.repository,
                        playbackEngine = container.playbackEngine,
                        appInfo = appInfo,
                    )
                } else {
                    MobileRoot(
                        repository = container.repository,
                        playbackEngine = container.playbackEngine,
                        appInfo = appInfo,
                        isInPictureInPictureMode = isInPipModeState,
                        openPlayerSignal = openPlayerSignal,
                        canEnterPip = pipSupported && pipEnabled,
                        onPlayerScreenActiveChanged = { active -> isMobilePlayerRouteActive = active },
                        onEnterPip = { enterPip(container.playbackEngine.state.value) },
                    )
                }

                lastCrashText?.let { crashText ->
                    AlertDialog(
                        onDismissRequest = {
                            runCatching { File(context.filesDir, "last_crash.txt").delete() }
                            lastCrashText = null
                        },
                        shape = MuxGridShapes.Dialog,
                        containerColor = MuxGridColors.CardSurfaceElevated,
                        titleContentColor = MuxGridColors.TextPrimary,
                        textContentColor = MuxGridColors.TextSecondary,
                        title = { Text("Previous Crash Log") },
                        text = {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(MuxGridShapes.Sm)
                                    .background(MuxGridColors.CrashLogSurface)
                                    .padding(8.dp)
                                    .verticalScroll(rememberScrollState()),
                                verticalArrangement = Arrangement.spacedBy(4.dp),
                            ) {
                                Text(
                                    text = crashText.take(3500),
                                    fontSize = 11.sp,
                                    color = MuxGridColors.TextPrimary,
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
                                    Text("Copy", color = MuxGridColors.TextSecondary)
                                }
                                TextButton(
                                    onClick = {
                                        runCatching { File(context.filesDir, "last_crash.txt").delete() }
                                        lastCrashText = null
                                    },
                                ) {
                                    Text("Dismiss", color = MuxGridColors.AccentSoft)
                                }
                            }
                        },
                    )
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        if (intent.getBooleanExtra(MuxGridMediaSessionService.EXTRA_OPEN_PLAYER, false)) {
            openPlayerSignal++
        }
    }

    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        val container = appContainer ?: return
        if (container.formFactor == FormFactor.TV) return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) return
        if (!isMobilePlayerRouteActive || !container.repository.pipEnabled.value) return
        val pb = container.playbackEngine.state.value
        val activeVideo = !pb.isRadio &&
            !pb.isAudioOnly &&
            (pb.status == PlaybackStatus.PLAYING || pb.status == PlaybackStatus.BUFFERING)
        if (activeVideo) {
            enterPip(pb)
        }
    }

    override fun onPictureInPictureModeChanged(
        isInPictureInPictureMode: Boolean,
        newConfig: Configuration,
    ) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig)
        isInPipModeState = isInPictureInPictureMode
    }

    private fun enterPip(playbackState: PlaybackState) {
        if (!packageManager.hasSystemFeature(PackageManager.FEATURE_PICTURE_IN_PICTURE)) return
        if (playbackState.isRadio || playbackState.isAudioOnly) return
        runCatching {
            enterPictureInPictureMode(
                buildPipParams(
                    videoWidth = playbackState.videoWidth,
                    videoHeight = playbackState.videoHeight,
                    autoEnter = false,
                ),
            )
        }
    }

    private fun buildPipParams(videoWidth: Int, videoHeight: Int, autoEnter: Boolean): PictureInPictureParams {
        val ratio = if (videoWidth > 0 && videoHeight > 0) {
            val raw = videoWidth.toFloat() / videoHeight.toFloat()
            val clamped = raw.coerceIn(0.45f, 2.35f)
            Rational((clamped * 1000).toInt(), 1000)
        } else {
            Rational(16, 9)
        }
        val builder = PictureInPictureParams.Builder()
            .setAspectRatio(ratio)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            builder.setAutoEnterEnabled(autoEnter)
        }
        return builder.build()
    }

    override fun onStart() {
        super.onStart()
        isInPipModeState = isInPictureInPictureMode
        appContainer?.repository?.setForeground(isForeground = true)
    }

    override fun onStop() {
        val container = appContainer
        if (container != null) {
            container.flushDvrProgress()
            val pb = container.playbackEngine.state.value
            val isPlaying = pb.status == PlaybackStatus.PLAYING || pb.status == PlaybackStatus.BUFFERING
            val allowBgAudio = pb.isRadio || pb.isAudioOnly || container.repository.backgroundPlayEnabled.value
            val keepAlive = when {
                isInPictureInPictureMode -> isPlaying
                isChangingConfigurations -> isPlaying
                isPlaying && allowBgAudio -> true
                isPlaying && !allowBgAudio -> {
                    container.playbackEngine.setPaused(true)
                    false
                }
                else -> false
            }
            container.repository.setForeground(
                isForeground = false,
                keepAliveInBackground = keepAlive,
            )
        }
        super.onStop()
    }
}
