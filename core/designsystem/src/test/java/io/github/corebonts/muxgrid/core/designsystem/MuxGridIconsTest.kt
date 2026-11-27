package io.github.corebonts.muxgrid.core.designsystem

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class MuxGridIconsTest {
    @Test
    fun allRoundedMaterialSymbolVectorsBuildCleanly() {
        val icons = listOf(
            MuxGridIcons.Live,
            MuxGridIcons.LiveFilled,
            MuxGridIcons.Guide,
            MuxGridIcons.GuideFilled,
            MuxGridIcons.Recordings,
            MuxGridIcons.RecordingsFilled,
            MuxGridIcons.Settings,
            MuxGridIcons.SettingsFilled,
            MuxGridIcons.Play,
            MuxGridIcons.Pause,
            MuxGridIcons.Stop,
            MuxGridIcons.Replay10,
            MuxGridIcons.Replay30,
            MuxGridIcons.Forward30,
            MuxGridIcons.FastRewind,
            MuxGridIcons.FastForward,
            MuxGridIcons.SkipPrevious,
            MuxGridIcons.SkipNext,
            MuxGridIcons.Sensors,
            MuxGridIcons.Replay,
            MuxGridIcons.Tracks,
            MuxGridIcons.Subtitles,
            MuxGridIcons.AudioTrack,
            MuxGridIcons.AspectRatio,
            MuxGridIcons.Pip,
            MuxGridIcons.Headphones,
            MuxGridIcons.ChannelsList,
            MuxGridIcons.ArrowBack,
            MuxGridIcons.Close,
            MuxGridIcons.ChevronLeft,
            MuxGridIcons.ChevronRight,
            MuxGridIcons.ExpandMore,
            MuxGridIcons.ExpandLess,
            MuxGridIcons.Star,
            MuxGridIcons.StarFilled,
            MuxGridIcons.Tv,
            MuxGridIcons.Radio,
            MuxGridIcons.Search,
            MuxGridIcons.Check,
            MuxGridIcons.CheckCircle,
            MuxGridIcons.Delete,
            MuxGridIcons.Cancel,
            MuxGridIcons.Record,
            MuxGridIcons.Schedule,
            MuxGridIcons.Edit,
            MuxGridIcons.Refresh,
            MuxGridIcons.Brightness,
            MuxGridIcons.VolumeUp,
            MuxGridIcons.Warning,
            MuxGridIcons.Dns,
            MuxGridIcons.Info,
            MuxGridIcons.MoreHoriz,
        )
        assertEquals(52, icons.size)
        icons.forEach { vector ->
            assertEquals(24f, vector.defaultWidth.value, "${vector.name} width")
            assertEquals(24f, vector.defaultHeight.value, "${vector.name} height")
            assertEquals(960f, vector.viewportWidth, "${vector.name} viewportWidth")
            assertEquals(960f, vector.viewportHeight, "${vector.name} viewportHeight")
            assertTrue(vector.root.size > 0, "${vector.name} root should have child group/path")
        }
        assertNotEquals(MuxGridIcons.Live.root, MuxGridIcons.LiveFilled.root)
        assertNotEquals(MuxGridIcons.Guide.root, MuxGridIcons.GuideFilled.root)
        assertNotEquals(MuxGridIcons.Recordings.root, MuxGridIcons.RecordingsFilled.root)
        assertNotEquals(MuxGridIcons.Settings.root, MuxGridIcons.SettingsFilled.root)
    }
}
