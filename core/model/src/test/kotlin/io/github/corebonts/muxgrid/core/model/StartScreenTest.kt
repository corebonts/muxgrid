package io.github.corebonts.muxgrid.core.model

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class StartScreenTest {

    @Test
    fun `fromPersisted restores known options and defaults unknown to LIVE`() {
        assertEquals(StartScreen.LIVE, StartScreen.fromPersisted(null))
        assertEquals(StartScreen.LIVE, StartScreen.fromPersisted("UNKNOWN"))
        assertEquals(StartScreen.LIVE, StartScreen.fromPersisted("LIVE"))
        assertEquals(StartScreen.FAVORITES, StartScreen.fromPersisted("FAVORITES"))
        assertEquals(StartScreen.GUIDE, StartScreen.fromPersisted("GUIDE"))
        assertEquals(StartScreen.RECORDINGS, StartScreen.fromPersisted("RECORDINGS"))
    }

    @Test
    fun `each start screen maps to expected tab and live filter`() {
        assertEquals(MainTab.LIVE, StartScreen.LIVE.tab)
        assertEquals(LiveFilter.All, StartScreen.LIVE.liveFilter)

        assertEquals(MainTab.LIVE, StartScreen.FAVORITES.tab)
        assertEquals(LiveFilter.Favorites, StartScreen.FAVORITES.liveFilter)

        assertEquals(MainTab.GUIDE, StartScreen.GUIDE.tab)
        assertEquals(MainTab.RECORDINGS, StartScreen.RECORDINGS.tab)
    }
}
