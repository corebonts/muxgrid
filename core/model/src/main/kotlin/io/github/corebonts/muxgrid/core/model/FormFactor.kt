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
