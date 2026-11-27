package io.github.corebonts.muxgrid.core.designsystem

import android.graphics.Bitmap
import android.graphics.Canvas
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
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import kotlin.math.pow
import kotlin.math.roundToInt

/**
 * Full-screen root canvas backdrop for the active [MuxGridPalette].
 *
 * - `CARBON` (`SOLID`): pure flat canvas fill with zero shader overhead.
 * - `AMBER` / `INDIGO` (`AMBIENT_WASH`): wide, subtle two-lobe wash over the base canvas.
 * - `AURORA` (`MESH_GRADIENT`): aspect-aware diagonal 4-lobe mesh (teal -> rose -> sapphire)
 *   rendered with cubic smoothstep falloff and 1.6/255 triangular-PDF dithering on API 33+,
 *   or a pre-rendered low-res bilinear-upscaled offscreen bitmap on API 28-32.
 */
@Composable
fun MuxGridCanvasBackdrop(
    isTv: Boolean,
    modifier: Modifier = Modifier,
) {
    val palette = LocalMuxGridColors.current
    val baseColor = if (isTv) palette.canvasTv else palette.canvasPhone

    if (palette.backdropStyle == CanvasBackdropStyle.SOLID) {
        Box(modifier = modifier.background(baseColor))
        return
    }

    val renderer = remember(
        palette.backdropStyle,
        baseColor,
        palette.backdropWash1,
        palette.backdropWash2,
        palette.backdropWash3,
    ) {
        createCanvasBackdropRenderer(
            style = palette.backdropStyle,
            baseColor = baseColor,
            wash1 = palette.backdropWash1,
            wash2 = palette.backdropWash2,
            wash3 = palette.backdropWash3,
        )
    }

    DisposableEffect(renderer) {
        onDispose { renderer.dispose() }
    }

    Box(
        modifier = modifier.drawBehind {
            if (size.width >= 1f && size.height >= 1f) {
                renderer.draw(
                    nativeCanvas = drawContext.canvas.nativeCanvas,
                    width = size.width,
                    height = size.height,
                )
            }
        },
    )
}

private interface CanvasBackdropRenderer {
    fun draw(nativeCanvas: Canvas, width: Float, height: Float)
    fun dispose()
}

private fun createCanvasBackdropRenderer(
    style: CanvasBackdropStyle,
    baseColor: Color,
    wash1: Color,
    wash2: Color,
    wash3: Color,
): CanvasBackdropRenderer {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        val agsl = runCatching {
            AgslCanvasBackdropRenderer(style, baseColor, wash1, wash2, wash3)
        }.getOrNull()
        if (agsl != null) return agsl
    }
    return LowResBitmapBackdropRenderer(style, baseColor, wash1, wash2, wash3)
}

@RequiresApi(Build.VERSION_CODES.TIRAMISU)
private class AgslCanvasBackdropRenderer(
    style: CanvasBackdropStyle,
    baseColor: Color,
    wash1: Color,
    wash2: Color,
    wash3: Color,
) : CanvasBackdropRenderer {

    private val shader = RuntimeShader(AGSL_SOURCE)
    private val paint = Paint().apply { isDither = true }
    private val modeFlag = if (style == CanvasBackdropStyle.MESH_GRADIENT) 1f else 0f

    private val bgR = baseColor.red
    private val bgG = baseColor.green
    private val bgB = baseColor.blue

    private val w1R = wash1.red
    private val w1G = wash1.green
    private val w1B = wash1.blue

    private val w2R = wash2.red
    private val w2G = wash2.green
    private val w2B = wash2.blue

    private val w3R = wash3.red
    private val w3G = wash3.green
    private val w3B = wash3.blue

    override fun draw(nativeCanvas: Canvas, width: Float, height: Float) {
        shader.setFloatUniform("uResolution", width, height)
        shader.setFloatUniform("uBgColor", bgR, bgG, bgB, 1f)
        shader.setFloatUniform("uWash1", w1R, w1G, w1B, modeFlag)
        shader.setFloatUniform("uWash2", w2R, w2G, w2B, DITHER_AMP)
        shader.setFloatUniform("uWash3", w3R, w3G, w3B, 1f)
        paint.shader = shader
        nativeCanvas.drawRect(0f, 0f, width, height, paint)
    }

    override fun dispose() = Unit

    companion object {
        private const val DITHER_AMP = 1.6f / 255f

        private const val AGSL_SOURCE = """
            uniform float2 uResolution;
            uniform float4 uBgColor;
            uniform float4 uWash1; // rgb, a = modeFlag (0 = wash, 1 = mesh)
            uniform float4 uWash2; // rgb, a = ditherAmp
            uniform float4 uWash3; // rgb

            float smoothLobe(float2 uv, float2 center, float2 radius) {
                float2 d = (uv - center) / radius;
                float dist = length(d);
                if (dist >= 1.0) return 0.0;
                float k = 1.0 - dist;
                // Cubic smoothstep squared for ultra-smooth zero-stripe feathering
                float s = k * k * (3.0 - 2.0 * k);
                return s * s;
            }

            float3 screenBlend(float3 baseCol, float3 lobeCol, float alpha) {
                float3 src = lobeCol * clamp(alpha, 0.0, 1.0);
                return src + baseCol * (1.0 - src);
            }

            half4 main(float2 fragCoord) {
                float2 uv = fragCoord / max(uResolution, float2(1.0, 1.0));
                float isPortrait = uResolution.y > uResolution.x ? 1.0 : 0.0;
                float3 col = uBgColor.rgb;

                if (uWash1.a > 0.5) {
                    // Aurora diagonal mesh gradient: wide, overlapping lobes tuned for both portrait & 16:9
                    float2 cTeal = mix(float2(0.18, 0.72), float2(0.14, 0.78), isPortrait);
                    float2 rTeal = mix(float2(0.68, 0.64), float2(1.10, 0.56), isPortrait);

                    float2 cRose = mix(float2(0.56, 0.84), float2(0.58, 0.62), isPortrait);
                    float2 rRose = mix(float2(0.62, 0.58), float2(1.05, 0.54), isPortrait);

                    float2 cBlue = mix(float2(0.86, 0.34), float2(0.88, 0.24), isPortrait);
                    float2 rBlue = mix(float2(0.66, 0.68), float2(1.12, 0.58), isPortrait);

                    float2 cUpperTeal = mix(float2(0.28, 0.16), float2(0.18, 0.22), isPortrait);
                    float2 rUpperTeal = mix(float2(0.58, 0.52), float2(0.95, 0.46), isPortrait);

                    col = screenBlend(col, uWash1.rgb, 0.28 * smoothLobe(uv, cTeal, rTeal));
                    col = screenBlend(col, uWash2.rgb, 0.23 * smoothLobe(uv, cRose, rRose));
                    col = screenBlend(col, uWash3.rgb, 0.28 * smoothLobe(uv, cBlue, rBlue));
                    col = screenBlend(col, uWash1.rgb, 0.12 * smoothLobe(uv, cUpperTeal, rUpperTeal));
                } else {
                    // Subtle Mica / Twilight Ambient Wash
                    float2 cPrimary = mix(float2(0.16, 0.14), float2(0.22, 0.12), isPortrait);
                    float2 rPrimary = mix(float2(0.76, 0.72), float2(1.15, 0.62), isPortrait);

                    float2 cSecondary = mix(float2(0.84, 0.88), float2(0.82, 0.84), isPortrait);
                    float2 rSecondary = mix(float2(0.72, 0.72), float2(1.12, 0.64), isPortrait);

                    col = screenBlend(col, uWash1.rgb, 0.14 * smoothLobe(uv, cPrimary, rPrimary));
                    col = screenBlend(col, uWash2.rgb, 0.12 * smoothLobe(uv, cSecondary, rSecondary));
                }

                // Triangular-PDF dithering to eliminate 8-bit dark-gradient banding
                float n1 = fract(sin(dot(fragCoord, float2(12.9898, 78.233))) * 43758.5453);
                float n2 = fract(sin(dot(fragCoord + float2(1.7, 3.1), float2(39.346, 11.135))) * 43758.5453);
                float dither = (n1 + n2 - 1.0) * uWash2.a;
                col = clamp(col + dither, 0.0, 1.0);

                return half4(half3(col), 1.0);
            }
        """
    }
}

private class LowResBitmapBackdropRenderer(
    private val style: CanvasBackdropStyle,
    baseColor: Color,
    private val wash1: Color,
    private val wash2: Color,
    private val wash3: Color,
) : CanvasBackdropRenderer {

    private val bgInt = baseColor.toArgb()
    private val blitPaint = Paint(Paint.FILTER_BITMAP_FLAG or Paint.DITHER_FLAG)
    private val screenPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        xfermode = PorterDuffXfermode(PorterDuff.Mode.SCREEN)
    }
    private val gradMatrix = Matrix()
    private val srcRect = Rect()
    private val dstRectF = RectF()

    private var landscapeBitmap: Bitmap? = null
    private var portraitBitmap: Bitmap? = null

    override fun draw(nativeCanvas: Canvas, width: Float, height: Float) {
        val isPortrait = height > width
        val bmp = if (isPortrait) {
            portraitBitmap ?: buildBitmap(PORTRAIT_W, PORTRAIT_H, isPortrait = true).also { portraitBitmap = it }
        } else {
            landscapeBitmap ?: buildBitmap(LANDSCAPE_W, LANDSCAPE_H, isPortrait = false).also { landscapeBitmap = it }
        }
        srcRect.set(0, 0, bmp.width, bmp.height)
        dstRectF.set(0f, 0f, width, height)
        nativeCanvas.drawBitmap(bmp, srcRect, dstRectF, blitPaint)
    }

    override fun dispose() {
        landscapeBitmap?.recycle()
        landscapeBitmap = null
        portraitBitmap?.recycle()
        portraitBitmap = null
    }

    private fun buildBitmap(bw: Int, bh: Int, isPortrait: Boolean): Bitmap {
        val bmp = Bitmap.createBitmap(bw, bh, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        c.drawColor(bgInt)

        val wf = bw.toFloat()
        val hf = bh.toFloat()
        if (style == CanvasBackdropStyle.MESH_GRADIENT) {
            drawSmoothLobe(c, wash1, 0.28f, (if (isPortrait) 0.14f else 0.18f) * wf, (if (isPortrait) 0.78f else 0.72f) * hf, (if (isPortrait) 1.10f else 0.68f) * wf, (if (isPortrait) 0.56f else 0.64f) * hf)
            drawSmoothLobe(c, wash2, 0.23f, (if (isPortrait) 0.58f else 0.56f) * wf, (if (isPortrait) 0.62f else 0.84f) * hf, (if (isPortrait) 1.05f else 0.62f) * wf, (if (isPortrait) 0.54f else 0.58f) * hf)
            drawSmoothLobe(c, wash3, 0.28f, (if (isPortrait) 0.88f else 0.86f) * wf, (if (isPortrait) 0.24f else 0.34f) * hf, (if (isPortrait) 1.12f else 0.66f) * wf, (if (isPortrait) 0.58f else 0.68f) * hf)
            drawSmoothLobe(c, wash1, 0.12f, (if (isPortrait) 0.18f else 0.28f) * wf, (if (isPortrait) 0.22f else 0.16f) * hf, (if (isPortrait) 0.95f else 0.58f) * wf, (if (isPortrait) 0.46f else 0.52f) * hf)
        } else {
            drawSmoothLobe(c, wash1, 0.14f, (if (isPortrait) 0.22f else 0.16f) * wf, (if (isPortrait) 0.12f else 0.14f) * hf, (if (isPortrait) 1.15f else 0.76f) * wf, (if (isPortrait) 0.62f else 0.72f) * hf)
            drawSmoothLobe(c, wash2, 0.12f, (if (isPortrait) 0.82f else 0.84f) * wf, (if (isPortrait) 0.84f else 0.88f) * hf, (if (isPortrait) 1.12f else 0.72f) * wf, (if (isPortrait) 0.64f else 0.72f) * hf)
        }
        return bmp
    }

    private fun drawSmoothLobe(
        canvas: Canvas,
        color: Color,
        peakAlpha: Float,
        cx: Float,
        cy: Float,
        rx: Float,
        ry: Float,
    ) {
        val r = (color.red * 255f).roundToInt().coerceIn(0, 255)
        val g = (color.green * 255f).roundToInt().coerceIn(0, 255)
        val b = (color.blue * 255f).roundToInt().coerceIn(0, 255)
        val stops = floatArrayOf(0f, 0.25f, 0.50f, 0.75f, 1f)
        val colors = IntArray(stops.size) { idx ->
            val k = 1f - stops[idx]
            val s = k * k * (3f - 2f * k)
            val a = (peakAlpha * (s * s) * 255f).roundToInt().coerceIn(0, 255)
            android.graphics.Color.argb(a, r, g, b)
        }
        val grad = RadialGradient(0f, 0f, 1f, colors, stops, Shader.TileMode.CLAMP)
        gradMatrix.reset()
        gradMatrix.setScale(rx.coerceAtLeast(1f), ry.coerceAtLeast(1f))
        gradMatrix.postTranslate(cx, cy)
        grad.setLocalMatrix(gradMatrix)
        screenPaint.shader = grad
        canvas.drawRect(0f, 0f, canvas.width.toFloat(), canvas.height.toFloat(), screenPaint)
    }

    companion object {
        private const val LANDSCAPE_W = 160
        private const val LANDSCAPE_H = 90
        private const val PORTRAIT_W = 90
        private const val PORTRAIT_H = 160
    }
}
