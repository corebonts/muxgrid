package io.github.corebonts.muxgrid.core.model

data class AppBuildInfo(
    val versionName: String,
    val versionCode: Int,
    val buildSha: String,
    val githubUrl: String = DEFAULT_GITHUB_URL,
) {
    val shortSha: String
        get() = buildSha.trim().take(SHORT_SHA_LENGTH).ifEmpty { "unknown" }

    val versionDisplay: String
        get() {
            val name = versionName.trim().ifEmpty { "0.0.0" }
            return if (versionCode > 0) "$name ($versionCode)" else name
        }

    val githubDisplayUrl: String
        get() = githubUrl.removePrefix("https://").removePrefix("http://")

    companion object {
        const val SHORT_SHA_LENGTH = 7
        const val DEFAULT_GITHUB_URL = "https://github.com/corebonts/muxgrid"
    }
}
