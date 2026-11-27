package io.github.corebonts.muxgrid.core.designsystem

import android.content.Context
import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import io.github.corebonts.muxgrid.core.model.BackgroundPlaybackMode
import io.github.corebonts.muxgrid.core.model.ConnectionState
import io.github.corebonts.muxgrid.core.model.DiagnosticCheckId
import io.github.corebonts.muxgrid.core.model.DiagnosticItem
import io.github.corebonts.muxgrid.core.model.DiagnosticMessageCode
import io.github.corebonts.muxgrid.core.model.DiagnosticSeverity
import io.github.corebonts.muxgrid.core.model.LiveFilter
import io.github.corebonts.muxgrid.core.model.MainTab
import io.github.corebonts.muxgrid.core.model.MediaTrack
import io.github.corebonts.muxgrid.core.model.PlaybackError
import io.github.corebonts.muxgrid.core.model.ServerDiagnosticsReport
import io.github.corebonts.muxgrid.core.model.ServerProfile
import io.github.corebonts.muxgrid.core.model.StartScreen
import io.github.corebonts.muxgrid.core.model.ThemeMode
import io.github.corebonts.muxgrid.core.model.TimeshiftNotice
import io.github.corebonts.muxgrid.core.model.TimeshiftWindow
import io.github.corebonts.muxgrid.core.model.VideoAspectMode

@StringRes
fun MainTab.labelRes(): Int = when (this) {
    MainTab.LIVE -> R.string.nav_live
    MainTab.GUIDE -> R.string.nav_guide
    MainTab.RECORDINGS -> R.string.nav_recordings
    MainTab.SETTINGS -> R.string.nav_settings
}

@Composable
fun MainTab.localizedLabel(): String = stringResource(labelRes())

@Composable
fun LiveFilter.localizedLabel(): String = when (this) {
    LiveFilter.All -> stringResource(R.string.live_filter_all)
    LiveFilter.Tv -> stringResource(R.string.live_filter_tv)
    LiveFilter.Radio -> stringResource(R.string.live_filter_radio)
    LiveFilter.Favorites -> stringResource(R.string.live_filter_favorites)
    is LiveFilter.Tag -> name
}

@StringRes
fun StartScreen.labelRes(): Int = when (this) {
    StartScreen.LIVE -> R.string.nav_live
    StartScreen.FAVORITES -> R.string.live_filter_favorites
    StartScreen.GUIDE -> R.string.nav_guide
    StartScreen.RECORDINGS -> R.string.nav_recordings
}

@StringRes
fun StartScreen.descriptionRes(): Int = when (this) {
    StartScreen.LIVE -> R.string.settings_start_screen_live_desc
    StartScreen.FAVORITES -> R.string.settings_start_screen_favorites_desc
    StartScreen.GUIDE -> R.string.settings_start_screen_guide_desc
    StartScreen.RECORDINGS -> R.string.settings_start_screen_recordings_desc
}

@StringRes
fun ThemeMode.labelRes(): Int = when (this) {
    ThemeMode.AUTO -> R.string.settings_theme_auto
    ThemeMode.LIGHT -> R.string.settings_theme_light
    ThemeMode.DARK -> R.string.settings_theme_dark
}

@StringRes
fun ThemeMode.descriptionRes(): Int = when (this) {
    ThemeMode.AUTO -> R.string.settings_theme_auto_desc
    ThemeMode.LIGHT -> R.string.settings_theme_light_desc
    ThemeMode.DARK -> R.string.settings_theme_dark_desc
}

@StringRes
fun BackgroundPlaybackMode.labelRes(): Int = when (this) {
    BackgroundPlaybackMode.OFF -> R.string.common_off
    BackgroundPlaybackMode.AUDIO -> R.string.settings_bg_audio
    BackgroundPlaybackMode.PIP -> R.string.settings_bg_pip
}

@StringRes
fun BackgroundPlaybackMode.descriptionRes(): Int = when (this) {
    BackgroundPlaybackMode.OFF -> R.string.settings_bg_off_desc
    BackgroundPlaybackMode.AUDIO -> R.string.settings_bg_audio_desc
    BackgroundPlaybackMode.PIP -> R.string.settings_bg_pip_desc
}

@Composable
fun TimeshiftWindow.localizedShortLabel(): String = when (this) {
    TimeshiftWindow.OFF -> stringResource(R.string.common_off)
    TimeshiftWindow.MB_512,
    TimeshiftWindow.GB_1,
    TimeshiftWindow.GB_2,
    TimeshiftWindow.GB_4,
    -> shortLabel
}

@StringRes
fun TimeshiftWindow.descriptionRes(): Int = when (this) {
    TimeshiftWindow.OFF -> R.string.settings_timeshift_off_desc
    TimeshiftWindow.MB_512 -> R.string.settings_timeshift_512mb_desc
    TimeshiftWindow.GB_1 -> R.string.settings_timeshift_1gb_desc
    TimeshiftWindow.GB_2 -> R.string.settings_timeshift_2gb_desc
    TimeshiftWindow.GB_4 -> R.string.settings_timeshift_4gb_desc
}

@Composable
fun VideoAspectMode.localizedLabel(): String = when (this) {
    VideoAspectMode.FIT -> stringResource(R.string.player_aspect_fit)
    VideoAspectMode.FILL -> stringResource(R.string.player_aspect_fill)
    VideoAspectMode.RATIO_16_9 -> label
    VideoAspectMode.RATIO_4_3 -> label
    VideoAspectMode.STRETCH -> stringResource(R.string.player_aspect_stretch)
}

@Composable
fun MediaTrack.localizedDisplayLabel(): String {
    val parts = buildList {
        if (!language.isNullOrBlank()) add(language!!.uppercase())
        if (!title.isNullOrBlank() && !title.equals(language, ignoreCase = true)) add(title!!)
        if (!codec.isNullOrBlank()) add(codec!!.uppercase())
    }
    return if (parts.isNotEmpty()) {
        parts.joinToString(" · ")
    } else {
        stringResource(R.string.player_track_fallback, id)
    }
}

@StringRes
fun TimeshiftNotice.labelRes(): Int = when (this) {
    TimeshiftNotice.AT_LIVE_EDGE -> R.string.player_notice_at_live_edge
    TimeshiftNotice.LOW_STORAGE_MEMORY_BUFFER -> R.string.player_notice_low_storage_memory
    TimeshiftNotice.BUFFER_NEARLY_FULL -> R.string.player_notice_buffer_nearly_full
    TimeshiftNotice.BUFFER_FULL_RESUMED -> R.string.player_notice_buffer_full_resumed
    TimeshiftNotice.LIVE_STREAM_RECONNECTED -> R.string.player_notice_live_reconnected
}

@Composable
fun TimeshiftNotice.localizedMessage(): String = stringResource(labelRes())

@Composable
fun PlaybackError.localizedTitle(): String = when (this) {
    is PlaybackError.TunerBusy -> stringResource(R.string.player_error_tuner_busy_title)
    is PlaybackError.StreamEndedUnexpectedly,
    is PlaybackError.StreamOpenFailed,
    is PlaybackError.PlaybackFailed,
    -> stringResource(R.string.player_error_title)
}

@Composable
fun PlaybackError.localizedDetail(): String? = when (this) {
    is PlaybackError.TunerBusy -> stringResource(R.string.player_error_tuner_busy_detail)
    is PlaybackError.StreamEndedUnexpectedly,
    is PlaybackError.StreamOpenFailed,
    is PlaybackError.PlaybackFailed,
    -> null
}

@Composable
fun PlaybackError.localizedMessage(): String = when (this) {
    is PlaybackError.TunerBusy -> when {
        !recordingTitle.isNullOrBlank() && !channelName.isNullOrBlank() ->
            stringResource(R.string.player_error_tuner_busy_recording_channel, recordingTitle!!, channelName!!)
        !recordingTitle.isNullOrBlank() ->
            stringResource(R.string.player_error_tuner_busy_recording, recordingTitle!!)
        else -> stringResource(R.string.player_error_tuner_busy_generic)
    }
    is PlaybackError.StreamEndedUnexpectedly -> stringResource(R.string.player_error_stream_ended)
    is PlaybackError.StreamOpenFailed -> message
    is PlaybackError.PlaybackFailed -> message
}

@Composable
fun localizedConnectionStatusText(
    profile: ServerProfile,
    connectionState: ConnectionState,
): String = when (connectionState) {
    is ConnectionState.Connected -> stringResource(
        R.string.connection_status_connected,
        connectionState.serverVersion,
        connectionState.htspVersion,
    )
    ConnectionState.Connecting -> stringResource(R.string.common_connecting)
    is ConnectionState.Syncing -> stringResource(R.string.common_syncing)
    is ConnectionState.Error -> connectionState.message
    ConnectionState.Disconnected -> stringResource(R.string.connection_status_disconnected, profile.httpPort)
}

@StringRes
fun DiagnosticCheckId.titleRes(): Int = when (this) {
    DiagnosticCheckId.HTSP_CONNECTION -> R.string.diagnostics_check_htsp_connection
    DiagnosticCheckId.AUTHENTICATION -> R.string.diagnostics_check_authentication
    DiagnosticCheckId.HTSP_PROTOCOL_VERSION -> R.string.diagnostics_check_htsp_version
    DiagnosticCheckId.HTSP_STREAMING -> R.string.diagnostics_check_htsp_streaming
    DiagnosticCheckId.HTTP_MEDIA -> R.string.diagnostics_check_http_media
    DiagnosticCheckId.HTSP_DVR -> R.string.diagnostics_check_htsp_dvr
    DiagnosticCheckId.STREAM_PROFILE_PASS -> R.string.diagnostics_check_stream_profile
    DiagnosticCheckId.SERVER_CLOCK -> R.string.diagnostics_check_server_clock
    DiagnosticCheckId.DVR_DISK_SPACE -> R.string.diagnostics_check_dvr_disk
}

@StringRes
fun DiagnosticSeverity.badgeLabelRes(): Int = when (this) {
    DiagnosticSeverity.OK -> R.string.diagnostics_severity_ok
    DiagnosticSeverity.ADVISORY -> R.string.diagnostics_severity_advisory
    DiagnosticSeverity.DEGRADED -> R.string.diagnostics_severity_degraded
    DiagnosticSeverity.BLOCKER -> R.string.diagnostics_severity_blocker
}

@Composable
fun DiagnosticItem.localizedTitle(): String = stringResource(id.titleRes())

@Composable
fun DiagnosticItem.localizedSummary(): String {
    val code = messageCode ?: return summary
    val a0 = messageArgs.getOrNull(0).orEmpty()
    val a1 = messageArgs.getOrNull(1).orEmpty()
    return when (code) {
        DiagnosticMessageCode.HTSP_UNREACHABLE -> stringResource(R.string.diagnostics_htsp_unreachable_summary, a0)
        DiagnosticMessageCode.HTSP_CONNECTED -> a0.ifBlank {
            stringResource(R.string.diagnostics_htsp_connected_fallback, a1)
        }
        DiagnosticMessageCode.AUTH_FAILED -> stringResource(R.string.diagnostics_auth_failed_summary)
        DiagnosticMessageCode.AUTH_ANONYMOUS_NO_DVR -> stringResource(R.string.diagnostics_auth_anon_no_dvr_summary)
        DiagnosticMessageCode.AUTH_ANONYMOUS_OK -> stringResource(R.string.diagnostics_auth_anon_ok_summary)
        DiagnosticMessageCode.AUTH_SIGNED_IN -> stringResource(R.string.diagnostics_auth_signed_in_summary, a0)
        DiagnosticMessageCode.HTSP_VERSION_TOO_OLD -> stringResource(R.string.diagnostics_htsp_ver_old_summary, a0)
        DiagnosticMessageCode.HTSP_VERSION_LEGACY_NO_STOP,
        DiagnosticMessageCode.HTSP_VERSION_LEGACY_NO_RESUME,
        -> stringResource(R.string.diagnostics_htsp_ver_legacy_summary, a0)
        DiagnosticMessageCode.HTSP_VERSION_OK -> "HTSP v$a0"
        DiagnosticMessageCode.HTSP_STREAMING_OK -> stringResource(R.string.diagnostics_htsp_streaming_ok_summary)
        DiagnosticMessageCode.HTSP_STREAMING_NO_ACCESS -> stringResource(R.string.diagnostics_htsp_streaming_no_access_summary)
        DiagnosticMessageCode.HTSP_STREAMING_FAILED -> stringResource(R.string.diagnostics_htsp_streaming_failed_summary)
        DiagnosticMessageCode.HTSP_DVR_OK -> stringResource(R.string.diagnostics_htsp_dvr_ok_summary)
        DiagnosticMessageCode.HTSP_DVR_NO_ACCESS -> stringResource(R.string.diagnostics_htsp_dvr_no_access_summary)
        DiagnosticMessageCode.HTSP_DVR_UNVERIFIED -> stringResource(R.string.diagnostics_htsp_dvr_unverified_summary)
        DiagnosticMessageCode.STREAM_PROFILE_PASS_OK -> stringResource(R.string.diagnostics_profile_pass_ok_summary)
        DiagnosticMessageCode.STREAM_PROFILE_PASS_MISSING -> stringResource(R.string.diagnostics_profile_pass_missing_summary)
        DiagnosticMessageCode.SERVER_CLOCK_OFFSET -> stringResource(R.string.diagnostics_clock_offset_summary, a0)
        DiagnosticMessageCode.SERVER_CLOCK_OK -> stringResource(R.string.diagnostics_clock_ok_summary)
        DiagnosticMessageCode.DVR_DISK_LOW -> stringResource(R.string.diagnostics_disk_low_summary, a0, a1)
        DiagnosticMessageCode.DVR_DISK_OK -> stringResource(R.string.diagnostics_disk_summary, a0, a1)
        DiagnosticMessageCode.DVR_DISK_UNAVAILABLE -> stringResource(R.string.diagnostics_disk_unavailable_summary)
        DiagnosticMessageCode.HTTP_UNREACHABLE -> stringResource(R.string.diagnostics_http_unreachable_summary, a0)
        DiagnosticMessageCode.HTTP_OK,
        DiagnosticMessageCode.HTTP_AUTH_OK_WITH_DVR,
        -> stringResource(R.string.diagnostics_http_ok_summary, a0)
        DiagnosticMessageCode.HTTP_AUTH_UNAUTHENTICATED -> stringResource(R.string.diagnostics_http_auth_unauth_summary, a0)
        DiagnosticMessageCode.HTTP_AUTH_LIMITED,
        DiagnosticMessageCode.HTTP_UNEXPECTED_STATUS,
        -> stringResource(R.string.diagnostics_http_returned_summary, a0, a1)
    }
}

@Composable
fun DiagnosticItem.localizedDetail(): String? {
    val code = messageCode ?: return detail
    val a0 = messageArgs.getOrNull(0).orEmpty()
    val a1 = messageArgs.getOrNull(1).orEmpty()
    val a2 = messageArgs.getOrNull(2).orEmpty()
    return when (code) {
        DiagnosticMessageCode.HTSP_UNREACHABLE,
        DiagnosticMessageCode.AUTH_FAILED,
        DiagnosticMessageCode.DVR_DISK_UNAVAILABLE,
        -> detail
        DiagnosticMessageCode.HTSP_CONNECTED -> if (a2.isNotBlank()) {
            stringResource(R.string.diagnostics_htsp_port_reachable_ver, a1, a2)
        } else {
            stringResource(R.string.diagnostics_htsp_port_reachable, a1)
        }
        DiagnosticMessageCode.AUTH_ANONYMOUS_NO_DVR -> stringResource(R.string.diagnostics_auth_anon_no_dvr_detail)
        DiagnosticMessageCode.HTSP_VERSION_TOO_OLD -> stringResource(R.string.diagnostics_htsp_ver_old_detail)
        DiagnosticMessageCode.HTSP_VERSION_LEGACY_NO_STOP -> stringResource(R.string.diagnostics_htsp_ver_legacy_no_stop_detail)
        DiagnosticMessageCode.HTSP_VERSION_LEGACY_NO_RESUME -> stringResource(R.string.diagnostics_htsp_ver_legacy_no_resume_detail)
        DiagnosticMessageCode.HTSP_STREAMING_NO_ACCESS -> stringResource(R.string.diagnostics_htsp_streaming_no_access_detail)
        DiagnosticMessageCode.HTSP_STREAMING_FAILED -> stringResource(R.string.diagnostics_htsp_streaming_failed_detail)
        DiagnosticMessageCode.HTSP_DVR_NO_ACCESS -> stringResource(R.string.diagnostics_htsp_dvr_no_access_detail)
        DiagnosticMessageCode.HTSP_DVR_UNVERIFIED -> stringResource(R.string.diagnostics_htsp_dvr_unverified_detail)
        DiagnosticMessageCode.STREAM_PROFILE_PASS_MISSING -> stringResource(R.string.diagnostics_profile_pass_missing_detail, a0)
        DiagnosticMessageCode.SERVER_CLOCK_OFFSET -> stringResource(R.string.diagnostics_clock_offset_detail)
        DiagnosticMessageCode.DVR_DISK_LOW -> stringResource(R.string.diagnostics_disk_low_detail)
        DiagnosticMessageCode.HTTP_UNREACHABLE -> {
            val err = a1.ifBlank { stringResource(R.string.diagnostics_http_connection_failed) }
            stringResource(R.string.diagnostics_http_unreachable_detail, err)
        }
        DiagnosticMessageCode.HTTP_AUTH_UNAUTHENTICATED -> stringResource(R.string.diagnostics_http_auth_unauth_detail, a1)
        DiagnosticMessageCode.HTTP_AUTH_OK_WITH_DVR -> stringResource(R.string.diagnostics_http_auth_ok_dvr_detail)
        DiagnosticMessageCode.HTTP_AUTH_LIMITED -> stringResource(R.string.diagnostics_http_auth_limited_detail)
        DiagnosticMessageCode.HTTP_UNEXPECTED_STATUS -> stringResource(R.string.diagnostics_http_unexpected_detail, a1)
        else -> null
    }
}

@Composable
fun DiagnosticItem.localizedSuggestion(): String? {
    val code = messageCode ?: return suggestion
    val a0 = messageArgs.getOrNull(0).orEmpty()
    return when (code) {
        DiagnosticMessageCode.HTSP_UNREACHABLE -> stringResource(R.string.diagnostics_htsp_unreachable_suggestion, a0)
        DiagnosticMessageCode.AUTH_FAILED -> stringResource(R.string.diagnostics_auth_failed_suggestion)
        DiagnosticMessageCode.AUTH_ANONYMOUS_NO_DVR -> stringResource(R.string.diagnostics_auth_anon_no_dvr_suggestion)
        DiagnosticMessageCode.HTSP_VERSION_TOO_OLD -> stringResource(R.string.diagnostics_htsp_ver_old_suggestion)
        DiagnosticMessageCode.HTSP_VERSION_LEGACY_NO_STOP,
        DiagnosticMessageCode.HTSP_VERSION_LEGACY_NO_RESUME,
        -> stringResource(R.string.diagnostics_htsp_ver_legacy_suggestion)
        DiagnosticMessageCode.HTSP_STREAMING_NO_ACCESS -> stringResource(R.string.diagnostics_htsp_streaming_no_access_suggestion)
        DiagnosticMessageCode.HTSP_STREAMING_FAILED -> stringResource(R.string.diagnostics_htsp_streaming_failed_suggestion)
        DiagnosticMessageCode.HTSP_DVR_NO_ACCESS -> stringResource(R.string.diagnostics_htsp_dvr_no_access_suggestion)
        DiagnosticMessageCode.STREAM_PROFILE_PASS_MISSING -> stringResource(R.string.diagnostics_profile_pass_missing_suggestion)
        DiagnosticMessageCode.SERVER_CLOCK_OFFSET -> stringResource(R.string.diagnostics_clock_offset_suggestion)
        DiagnosticMessageCode.DVR_DISK_LOW -> stringResource(R.string.diagnostics_disk_low_suggestion)
        DiagnosticMessageCode.DVR_DISK_UNAVAILABLE -> stringResource(R.string.diagnostics_disk_unavailable_suggestion)
        DiagnosticMessageCode.HTTP_UNREACHABLE -> stringResource(R.string.diagnostics_http_unreachable_suggestion, a0)
        DiagnosticMessageCode.HTTP_AUTH_LIMITED -> stringResource(R.string.diagnostics_http_auth_limited_suggestion)
        else -> null
    }
}

@Composable
fun ServerDiagnosticsReport.localizedCapabilitySummary(): String {
    if (hasBlockers) {
        val blockerTitle = blockerItems.firstOrNull()?.localizedTitle()
        return if (blockerTitle != null) {
            stringResource(R.string.diagnostics_summary_issue, blockerTitle)
        } else {
            stringResource(R.string.diagnostics_summary_connection_issue)
        }
    }
    val parts = buildList {
        if (hasStreamingAccess) add(stringResource(R.string.diagnostics_chip_streaming))
        if (hasHttpMediaAccess) {
            add(stringResource(R.string.diagnostics_summary_http))
        } else {
            add(stringResource(R.string.diagnostics_summary_no_http))
        }
        if (hasDvrAccess) {
            add(stringResource(R.string.diagnostics_summary_dvr))
        } else {
            add(stringResource(R.string.diagnostics_summary_read_only))
        }
    }
    return parts.joinToString(" · ")
}

fun AppLanguageOption.localizedLabel(context: Context): String =
    nativeName ?: context.getString(R.string.settings_language_system_default)

@Composable
fun AppLanguageOption.localizedLabel(): String =
    nativeName ?: stringResource(R.string.settings_language_system_default)
