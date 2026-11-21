package io.github.corebonts.muxgrid.core.model

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class AppBuildInfoTest {

    @Test
    fun `shortSha truncates to first 7 characters and falls back when blank`() {
        val full = AppBuildInfo(
            versionName = "0.6.0",
            versionCode = 4,
            buildSha = "50eb030123456789abcdef",
        )
        assertEquals("50eb030", full.shortSha)

        val blank = AppBuildInfo(
            versionName = "0.6.0",
            versionCode = 4,
            buildSha = "   ",
        )
        assertEquals("unknown", blank.shortSha)
    }

    @Test
    fun `versionDisplay and githubDisplayUrl format cleanly`() {
        val info = AppBuildInfo(
            versionName = "0.6.0",
            versionCode = 4,
            buildSha = "50eb030",
        )
        assertEquals("0.6.0 (4)", info.versionDisplay)
        assertEquals("github.com/corebonts/muxgrid", info.githubDisplayUrl)
    }
}
