package io.github.corebonts.muxgrid.core.model

enum class FormFactor {
    PHONE,
    TV,
}

enum class HwDecPreset {
    MEDIACODEC_ZERO_COPY,
    MEDIACODEC_COPY_BWDIF,
    ;

    companion object {
        fun defaultFor(formFactor: FormFactor): HwDecPreset = when (formFactor) {
            FormFactor.TV -> MEDIACODEC_ZERO_COPY
            FormFactor.PHONE -> MEDIACODEC_COPY_BWDIF
        }
    }
}

enum class VideoAspectMode(val label: String) {
    FIT("Fit"),
    FILL("Fill"),
    RATIO_16_9("16:9"),
    RATIO_4_3("4:3"),
    STRETCH("Stretch"),
}

enum class TimeshiftWindow(
    val maxTotalBytes: Long,
    val maxBytesPerDirection: Long,
    val shortLabel: String,
    val description: String,
) {
    OFF(0L, 0L, "Off", "Off (64 MB memory buffer only)"),
    MB_512(512L * 1024L * 1024L, 256L * 1024L * 1024L, "512 MB", "512 MB max disk (~18 min SD or ~7 min HD)"),
    GB_1(1024L * 1024L * 1024L, 512L * 1024L * 1024L, "1 GB", "1 GB max disk (~35 min SD or ~15 min HD)"),
    GB_2(2048L * 1024L * 1024L, 1024L * 1024L * 1024L, "2 GB", "2 GB max disk (~70 min SD or ~30 min HD)"),
    GB_4(4096L * 1024L * 1024L, 2048L * 1024L * 1024L, "4 GB", "4 GB max disk (~2.5 h SD or ~60 min HD)"),
    ;

    companion object {
        fun defaultFor(formFactor: FormFactor): TimeshiftWindow = when (formFactor) {
            FormFactor.TV -> GB_1
            FormFactor.PHONE -> GB_2
        }

        fun fromPersisted(raw: String?, formFactor: FormFactor): TimeshiftWindow = when (raw) {
            "OFF" -> OFF
            "MB_512", "FIVE_MIN" -> MB_512
            "GB_1", "TEN_MIN" -> GB_1
            "GB_2", "FIFTEEN_MIN" -> GB_2
            "GB_4", "THIRTY_MIN" -> GB_4
            else -> defaultFor(formFactor)
        }
    }
}

enum class ThemeMode(
    val label: String,
    val description: String,
) {
    CARBON("Carbon", "Pure broadcast obsidian carbon with Signal Amber cues"),
    AMBER("Amber", "Warm espresso Mica surfaces with rich amber highlights"),
    AURORA("Aurora", "Teal, rose & sapphire ambient aurora with frosted glass cards"),
    INDIGO("Indigo", "Nocturnal twilight slate with cyan-violet accents"),
    ;

    companion object {
        val DEFAULT: ThemeMode = AMBER

        fun defaultFor(formFactor: FormFactor): ThemeMode = DEFAULT

        fun fromPersisted(raw: String?, formFactor: FormFactor = FormFactor.PHONE): ThemeMode =
            raw?.let { entries.firstOrNull { mode -> mode.name == it } }
                ?: DEFAULT
    }
}

enum class BackgroundPlaybackMode(
    val label: String,
    val description: String,
) {
    OFF("Off", "Pauses video when leaving the app or locking the screen (Radio always on)"),
    AUDIO("Audio", "Continues audio-only when leaving the app or locking the screen"),
    PIP("PiP", "Picture-in-Picture when switching apps; continues audio when locking the screen"),
    ;

    companion object {
        fun defaultFor(formFactor: FormFactor): BackgroundPlaybackMode = when (formFactor) {
            FormFactor.PHONE -> PIP
            FormFactor.TV -> OFF
        }

        fun fromPersisted(
            raw: String?,
            legacyPipEnabled: Boolean?,
            legacyBackgroundPlay: Boolean?,
            formFactor: FormFactor,
        ): BackgroundPlaybackMode {
            raw?.let { persisted ->
                entries.firstOrNull { it.name == persisted }?.let { return it }
            }
            if (legacyPipEnabled != null || legacyBackgroundPlay != null) {
                return when {
                    legacyPipEnabled == true -> PIP
                    legacyBackgroundPlay == true -> AUDIO
                    else -> OFF
                }
            }
            return defaultFor(formFactor)
        }
    }
}
