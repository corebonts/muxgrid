package io.github.corebonts.muxgrid.core.designsystem

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.RadialGradient
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.RuntimeShader
import android.graphics.Shader
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import io.github.corebonts.muxgrid.core.model.AudioLevels
import io.github.corebonts.muxgrid.core.model.PlaybackStatus
import kotlin.math.roundToInt

private const val ACTIVE_FRAME_INTERVAL_NS = 33_333_333L // ~30 fps
private const val CALM_FRAME_INTERVAL_NS = 66_666_666L   // ~15 fps once paused & settled

/**
 * Low-overhead, bottom-anchored ambient audio glow inheriting the active theme's `radioGlow1..3`
 * palette tokens.
 *
 * Uses a single-pass AGSL [RuntimeShader] on API 33+ and a pre-allocated 160x90 bilinear-upscaled
 * offscreen buffer on API 28–32, running strictly in Compose's draw phase with zero recompositions
 * and zero per-frame heap allocations.
 */
@Composable
fun AmbientRadioGlow(
    audioLevelsProvider: () -> AudioLevels,
    playbackStatus: PlaybackStatus,
    isPaused: Boolean,
    modifier: Modifier = Modifier,
    reactivity: Float = 1.0f,
    glowHeight: Float = 0.55f,
    amberWarmth: Float = 0.75f,
    smoothness: Float = 0.94f,
    backgroundColor: Color = DefaultBackdropColor,
    glowColor1: Color = MuxGridColors.RadioGlow1,
    glowColor2: Color = MuxGridColors.RadioGlow2,
    glowColor3: Color = MuxGridColors.RadioGlow3,
) {
    val engine = remember(reactivity, glowHeight, amberWarmth, smoothness) {
        AmbientRadioGlowEngine(
            reactivity = reactivity,
            glowHeight = glowHeight,
            amberWarmth = amberWarmth,
            smoothness = smoothness,
        ).apply {
            step(0f, audioLevelsProvider(), playbackStatus, isPaused)
        }
    }

    val lobeColors = remember(glowColor1, glowColor2, glowColor3) {
        buildThemedLobeColors(glowColor1, glowColor2, glowColor3)
    }

    val renderer = remember(backgroundColor, lobeColors) {
        createGlowRenderer(
            backgroundColor = backgroundColor,
            lobeColors = lobeColors,
            rimBottomColor = glowColor1,
            rimTopColor = glowColor2,
        )
    }

    DisposableEffect(renderer) {
        onDispose { renderer.dispose() }
    }

    val currentLevelsProvider by rememberUpdatedState(audioLevelsProvider)
    val currentStatus by rememberUpdatedState(playbackStatus)
    val currentPaused by rememberUpdatedState(isPaused)
    val frameTick = remember { mutableLongStateOf(0L) }
    val lifecycleOwner = LocalLifecycleOwner.current

    LaunchedEffect(lifecycleOwner, engine) {
        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            var lastStepNanos = 0L
            while (true) {
                withFrameNanos { nowNanos ->
                    val minIntervalNs = if (engine.isSettledCalm) {
                        CALM_FRAME_INTERVAL_NS
                    } else {
                        ACTIVE_FRAME_INTERVAL_NS
                    }
                    if (lastStepNanos == 0L || (nowNanos - lastStepNanos) >= minIntervalNs) {
                        val dtSec = if (lastStepNanos == 0L) {
                            1f / 30f
                        } else {
                            (nowNanos - lastStepNanos) / 1_000_000_000f
                        }
                        lastStepNanos = nowNanos
                        engine.step(
                            dtSec = dtSec,
                            targetLevels = currentLevelsProvider(),
                            status = currentStatus,
                            isPaused = currentPaused,
                        )
                        frameTick.longValue = nowNanos
                    }
                }
            }
        }
    }

    Box(
        modifier = modifier.drawBehind {
            // Read frameTick strictly inside drawBehind so Compose skips recomposition & layout.
            @Suppress("UNUSED_EXPRESSION")
            frameTick.longValue
            if (size.width >= 1f && size.height >= 1f) {
                renderer.draw(
                    nativeCanvas = drawContext.canvas.nativeCanvas,
                    width = size.width,
                    height = size.height,
                    engine = engine,
                )
            }
        },
    )
}

private val DefaultBackdropColor = Color(0xFF040405)

private fun buildThemedLobeColors(
    glow1: Color,
    glow2: Color,
    glow3: Color,
): List<Color> {
    val deepBase0 = lerp(Color(0xFF1D1D22), glow3, 0.25f)
    val deepBase1 = lerp(Color(0xFF282628), glow2, 0.18f)
    val deepBase2 = lerp(Color(0xFF333030), glow1, 0.14f)

    // When glow3 is luminous (Aurora sapphire #2C62E0, Indigo #4C1D95), let it drive lobe 3 directly;
    // when glow3 is near-neutral charcoal (Carbon #26262E), keep lobe 3 warm via glow2.
    val lowLobe = if (glow3.luminance() > 0.04f) glow3 else lerp(glow2, glow3, 0.30f)
    val midLobe = glow2
    val highLobe = glow1
    val coreLobe = lerp(glow1, Color.White, 0.22f)

    return listOf(
        deepBase0,
        deepBase1,
        deepBase2,
        lowLobe,
        midLobe,
        highLobe,
        coreLobe,
    )
}

private interface GlowRenderer {
    fun draw(nativeCanvas: Canvas, width: Float, height: Float, engine: AmbientRadioGlowEngine)
    fun dispose()
}

private fun createGlowRenderer(
    backgroundColor: Color,
    lobeColors: List<Color>,
    rimBottomColor: Color,
    rimTopColor: Color,
): GlowRenderer {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        val agsl = runCatching {
            AgslGlowRenderer(backgroundColor, lobeColors, rimBottomColor, rimTopColor)
        }.getOrNull()
        if (agsl != null) return agsl
    }
    return LowResBitmapGlowRenderer(backgroundColor, lobeColors, rimBottomColor, rimTopColor)
}

@RequiresApi(Build.VERSION_CODES.TIRAMISU)
private class AgslGlowRenderer(
    backgroundColor: Color,
    lobeColors: List<Color>,
    private val rimBottomColor: Color,
    private val rimTopColor: Color,
) : GlowRenderer {

    private val shader = RuntimeShader(AGSL_SOURCE)
    private val paint = Paint().apply {
        isDither = true
    }
    private val bgR = backgroundColor.red
    private val bgG = backgroundColor.green
    private val bgB = backgroundColor.blue

    private val lobeGeom = FloatArray(AmbientRadioGlowEngine.LOBE_COUNT * 4)
    private val lobeColor = FloatArray(AmbientRadioGlowEngine.LOBE_COUNT * 4)

    init {
        for (i in 0 until AmbientRadioGlowEngine.LOBE_COUNT) {
            val c = lobeColors[i]
            val base = i * 4
            lobeColor[base] = c.red
            lobeColor[base + 1] = c.green
            lobeColor[base + 2] = c.blue
            lobeColor[base + 3] = 0f
        }
    }

    override fun draw(
        nativeCanvas: Canvas,
        width: Float,
        height: Float,
        engine: AmbientRadioGlowEngine,
    ) {
        for (i in 0 until AmbientRadioGlowEngine.LOBE_COUNT) {
            val base = i * 4
            val rxPx = engine.lobeRxNorm[i] * width
            val ryPx = engine.lobeRyNorm[i] * height
            lobeGeom[base] = engine.lobeCxNorm[i] * width
            lobeGeom[base + 1] = engine.lobeCyNorm[i] * height
            lobeGeom[base + 2] = 1f / rxPx.coerceAtLeast(1f)
            lobeGeom[base + 3] = 1f / ryPx.coerceAtLeast(1f)
            lobeColor[base + 3] = engine.lobeAlpha[i]
        }

        val rimHeightPx = (engine.rimHeightFrac * height).coerceAtLeast(24f)
        val topFadePx = (engine.topFadeFrac * height).coerceAtLeast(1f)

        shader.setFloatUniform("uResolution", width, height)
        shader.setFloatUniform("uBgColor", bgR, bgG, bgB, 1f)
        shader.setFloatUniform("uRimColorBottom", rimBottomColor.red, rimBottomColor.green, rimBottomColor.blue, 1f)
        shader.setFloatUniform("uRimColorMid", rimTopColor.red, rimTopColor.green, rimTopColor.blue, 1f)
        shader.setFloatUniform("uLobeGeom", lobeGeom)
        shader.setFloatUniform("uLobeColor", lobeColor)
        shader.setFloatUniform("uRimParams", rimHeightPx, engine.rimAlpha, topFadePx, DITHER_AMP)

        paint.shader = shader
        nativeCanvas.drawRect(0f, 0f, width, height, paint)
    }

    override fun dispose() = Unit

    companion object {
        private const val DITHER_AMP = 1.6f / 255f

        private const val AGSL_SOURCE = """
            uniform float2 uResolution;
            uniform float4 uBgColor;
            uniform float4 uRimColorBottom;
            uniform float4 uRimColorMid;
            uniform float4 uLobeGeom[7];
            uniform float4 uLobeColor[7];
            uniform float4 uRimParams;

            float radialWeight(float dist) {
                if (dist < 0.38) {
                    return mix(1.0, 0.55, dist / 0.38);
                } else if (dist < 0.72) {
                    return mix(0.55, 0.16, (dist - 0.38) / 0.34);
                } else {
                    return mix(0.16, 0.0, (dist - 0.72) / 0.28);
                }
            }

            half4 main(float2 fragCoord) {
                float3 col = uBgColor.rgb;

                // 3 smoky base layers (normal alpha blend)
                for (int i = 0; i < 3; i++) {
                    float alphaScale = uLobeColor[i].a;
                    if (alphaScale > 0.004) {
                        float2 d = (fragCoord - uLobeGeom[i].xy) * uLobeGeom[i].zw;
                        float dist2 = dot(d, d);
                        if (dist2 < 1.0) {
                            float a = alphaScale * radialWeight(sqrt(dist2));
                            col = mix(col, uLobeColor[i].rgb, a);
                        }
                    }
                }

                // 4 reactive theme-tinted glow lobes (screen blend)
                for (int i = 3; i < 7; i++) {
                    float alphaScale = uLobeColor[i].a;
                    if (alphaScale > 0.004) {
                        float2 d = (fragCoord - uLobeGeom[i].xy) * uLobeGeom[i].zw;
                        float dist2 = dot(d, d);
                        if (dist2 < 1.0) {
                            float a = alphaScale * radialWeight(sqrt(dist2));
                            float3 src = uLobeColor[i].rgb * a;
                            col = src + col * (1.0 - src);
                        }
                    }
                }

                // Soft themed horizon rim along bottom edge
                float rimHeight = max(uRimParams.x, 1.0);
                float fromBottom = (uResolution.y - fragCoord.y) / rimHeight;
                if (fromBottom >= 0.0 && fromBottom < 1.0 && uRimParams.y > 0.002) {
                    float3 rimRgb;
                    float rimA;
                    if (fromBottom < 0.45) {
                        float k = fromBottom / 0.45;
                        rimRgb = mix(uRimColorBottom.rgb, uRimColorMid.rgb, k);
                        rimA = uRimParams.y * mix(1.0, 0.38, k);
                    } else {
                        float k = (fromBottom - 0.45) / 0.55;
                        rimRgb = uRimColorMid.rgb;
                        rimA = uRimParams.y * mix(0.38, 0.0, k);
                    }
                    float3 rimSrc = rimRgb * rimA;
                    col = rimSrc + col * (1.0 - rimSrc);
                }

                // Subtle top vignette keeping the upper stage cleanly dark
                float topK = clamp(fragCoord.y / max(uRimParams.z, 1.0), 0.0, 1.0);
                col = mix(uBgColor.rgb, col, topK);

                // Triangular-PDF dithering to eliminate 8-bit dark-gradient banding
                float n1 = fract(sin(dot(fragCoord, float2(12.9898, 78.233))) * 43758.5453);
                float n2 = fract(sin(dot(fragCoord + float2(1.7, 3.1), float2(39.346, 11.135))) * 43758.5453);
                float dither = (n1 + n2 - 1.0) * uRimParams.w;
                col = clamp(col + dither, 0.0, 1.0);

                return half4(half3(col), 1.0);
            }
        """
    }
}

private class LowResBitmapGlowRenderer(
    backgroundColor: Color,
    lobeColors: List<Color>,
    rimBottomColor: Color,
    rimTopColor: Color,
) : GlowRenderer {

    private val bgInt = backgroundColor.toArgb()
    private val bgTransparentInt = bgInt and 0x00FFFFFF

    private val offscreenBitmap = Bitmap.createBitmap(BUF_W, BUF_H, Bitmap.Config.ARGB_8888)
    private val offscreenCanvas = Canvas(offscreenBitmap)
    private val lobeSprites: Array<Bitmap> = Array(AmbientRadioGlowEngine.LOBE_COUNT) { idx ->
        createLobeSprite(lobeColors[idx])
    }

    private val smokePaint = Paint(Paint.FILTER_BITMAP_FLAG)
    private val glowPaint = Paint(Paint.FILTER_BITMAP_FLAG).apply {
        xfermode = PorterDuffXfermode(PorterDuff.Mode.SCREEN)
    }

    private val rimShader = LinearGradient(
        0f,
        1f,
        0f,
        0f,
        intArrayOf(
            rimBottomColor.copy(alpha = 1f).toArgb(),
            rimTopColor.copy(alpha = 0.38f).toArgb(),
            rimTopColor.copy(alpha = 0f).toArgb(),
        ),
        floatArrayOf(0f, 0.45f, 1f),
        Shader.TileMode.CLAMP,
    )
    private val rimPaint = Paint().apply {
        shader = rimShader
        xfermode = PorterDuffXfermode(PorterDuff.Mode.SCREEN)
    }

    private val topFadeShader = LinearGradient(
        0f,
        0f,
        0f,
        1f,
        intArrayOf(bgInt, bgTransparentInt),
        floatArrayOf(0f, 1f),
        Shader.TileMode.CLAMP,
    )
    private val topFadePaint = Paint().apply {
        shader = topFadeShader
    }

    private val blitPaint = Paint(Paint.FILTER_BITMAP_FLAG or Paint.DITHER_FLAG)
    private val shaderMatrix = Matrix()
    private val srcRect = Rect(0, 0, BUF_W, BUF_H)
    private val dstRectF = RectF()
    private val lobeDstRectF = RectF()

    override fun draw(
        nativeCanvas: Canvas,
        width: Float,
        height: Float,
        engine: AmbientRadioGlowEngine,
    ) {
        val bw = BUF_W.toFloat()
        val bh = BUF_H.toFloat()

        // 1. Base backdrop
        offscreenCanvas.drawColor(bgInt)

        // 2. 7 overlapping radial lobes into tiny 160x90 buffer
        for (i in 0 until AmbientRadioGlowEngine.LOBE_COUNT) {
            val alpha = engine.lobeAlpha[i]
            if (alpha <= 0.004f) continue
            val cx = engine.lobeCxNorm[i] * bw
            val cy = engine.lobeCyNorm[i] * bh
            val rx = engine.lobeRxNorm[i] * bw
            val ry = engine.lobeRyNorm[i] * bh
            lobeDstRectF.set(cx - rx, cy - ry, cx + rx, cy + ry)

            val paint = if (AmbientRadioGlowEngine.LOBES[i].isAmber) glowPaint else smokePaint
            paint.alpha = (alpha * 255f).roundToInt().coerceIn(0, 255)
            offscreenCanvas.drawBitmap(lobeSprites[i], null, lobeDstRectF, paint)
        }

        // 3. Soft themed bottom rim
        val rimA = engine.rimAlpha
        if (rimA > 0.004f) {
            val rimH = (engine.rimHeightFrac * bh).coerceAtLeast(3f)
            val rimTop = bh - rimH
            shaderMatrix.setScale(1f, rimH)
            shaderMatrix.postTranslate(0f, rimTop)
            rimShader.setLocalMatrix(shaderMatrix)
            rimPaint.alpha = (rimA * 255f).roundToInt().coerceIn(0, 255)
            offscreenCanvas.drawRect(0f, rimTop, bw, bh, rimPaint)
        }

        // 4. Subtle top vignette
        val topFadeH = (engine.topFadeFrac * bh).coerceAtLeast(1f)
        shaderMatrix.setScale(1f, topFadeH)
        topFadeShader.setLocalMatrix(shaderMatrix)
        offscreenCanvas.drawRect(0f, 0f, bw, topFadeH, topFadePaint)

        // 5. Hardware bilinear + dithered upscale to viewport
        dstRectF.set(0f, 0f, width, height)
        nativeCanvas.drawBitmap(offscreenBitmap, srcRect, dstRectF, blitPaint)
    }

    override fun dispose() {
        offscreenBitmap.recycle()
        for (sprite in lobeSprites) {
            sprite.recycle()
        }
    }

    companion object {
        private const val BUF_W = 160
        private const val BUF_H = 90
        private const val SPRITE_SIZE = 96

        private fun createLobeSprite(color: Color): Bitmap {
            val bmp = Bitmap.createBitmap(SPRITE_SIZE, SPRITE_SIZE, Bitmap.Config.ARGB_8888)
            val c = Canvas(bmp)
            val r = (color.red * 255f).roundToInt().coerceIn(0, 255)
            val g = (color.green * 255f).roundToInt().coerceIn(0, 255)
            val b = (color.blue * 255f).roundToInt().coerceIn(0, 255)
            val half = SPRITE_SIZE / 2f
            val grad = RadialGradient(
                half,
                half,
                half,
                intArrayOf(
                    android.graphics.Color.argb(255, r, g, b),
                    android.graphics.Color.argb(140, r, g, b),
                    android.graphics.Color.argb(41, r, g, b),
                    android.graphics.Color.argb(0, r, g, b),
                ),
                floatArrayOf(0f, 0.38f, 0.72f, 1f),
                Shader.TileMode.CLAMP,
            )
            val p = Paint(Paint.ANTI_ALIAS_FLAG).apply { shader = grad }
            c.drawCircle(half, half, half, p)
            return bmp
        }
    }
}
