package com.example.duofold.ui

import android.graphics.RenderEffect
import android.graphics.RuntimeShader
import android.graphics.Shader
import android.os.Build
import android.util.DisplayMetrics
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import com.example.duofold.R
import java.io.BufferedReader
import kotlin.math.abs

/** In-memory fallback so previews/tests without res/raw still work. */
private const val FALLBACK_PX_PER_MM = 6f

/**
 * Reads res/raw/duo_fold.agsl into a String. Called once per composition
 * (remembered by the caller) so per-frame cost is just uniform uploads.
 */
private fun loadShaderSource(context: android.content.Context): String {
    return try {
        context.resources.openRawResource(R.raw.duo_fold).use { stream ->
            stream.bufferedReader().use(BufferedReader::readText)
        }
    } catch (_: Exception) {
        // Should never happen in a real build; keeps @Preview from crashing.
        ""
    }
}

/**
 * Isolated AGSL RuntimeShader handler for Android 13+ (API 33, Tiramisu).
 * Keeps class references isolated so Android 12 (API 31/32) does not trigger
 * class verification errors.
 */
@RequiresApi(Build.VERSION_CODES.TIRAMISU)
private object AgslShaderHelper {
    fun createRenderEffect(
        shaderSrc: String,
        width: Float,
        height: Float,
        tiltDegrees: Float,
        hingeSide: Float,
        parameters: FoldParameters,
        resolvedPxPerMm: Float
    ): androidx.compose.ui.graphics.RenderEffect? {
        val shader = try {
            RuntimeShader(shaderSrc).apply {
                setFloatUniform("resolution", width, height)
                setFloatUniform("tiltDegrees", tiltDegrees)
                setFloatUniform(
                    "eyeDistancePx",
                    parameters.eyeDistanceMillimeters * resolvedPxPerMm
                )
                setFloatUniform("hingeSide", hingeSide)
                setFloatUniform("blurSpread", parameters.blurSpread)
                setFloatUniform("darkening", parameters.darkening * 6f / resolvedPxPerMm)
            }
        } catch (e: Exception) {
            Log.e("FoldEffect", "RuntimeShader creation failed: ${e.message}", e)
            return null
        }
        return try {
            RenderEffect
                .createRuntimeShaderEffect(shader, "content")
                .asComposeRenderEffect()
        } catch (e: Exception) {
            Log.e("FoldEffect", "createRuntimeShaderEffect failed: ${e.message}", e)
            null
        }
    }
}

/**
 * Android 12 (API 31 / 32) RenderEffect blur helper.
 */
@RequiresApi(Build.VERSION_CODES.S)
private object Android12BlurHelper {
    fun createBlurEffect(blurRadiusPx: Float): androidx.compose.ui.graphics.RenderEffect? {
        if (blurRadiusPx <= 0.5f) return null
        return try {
            RenderEffect
                .createBlurEffect(blurRadiusPx, blurRadiusPx, Shader.TileMode.CLAMP)
                .asComposeRenderEffect()
        } catch (e: Exception) {
            Log.e("FoldEffect", "RenderEffect.createBlurEffect failed: ${e.message}", e)
            null
        }
    }
}

/**
 * Applies the Duo-Fold frosted-glass effect to this layout subtree.
 *
 * Fully compatible with Android 12 (API 31+, including Infinix Note 11) using
 * hardware-accelerated 3D perspective transforms, dynamic hinge pivot, RenderEffect
 * blur, and crease shading. On Android 13+ (API 33+), it seamlessly uses AGSL
 * [RuntimeShader] for ray-traced glass physics.
 *
 * @param tiltDegrees Signed tilt in degrees around the screen-space Y axis.
 * @param hingeSide +1f = hinge on right edge, -1f = hinge on left edge.
 * @param parameters Physical tuning (eye distance, blur/darken curves).
 */
fun Modifier.foldEffect(
    tiltDegrees: Float,
    hingeSide: Float = if (tiltDegrees >= 0f) 1f else -1f,
    parameters: FoldParameters = FoldParameters()
): Modifier = composed {
    val context = LocalContext.current
    val density = LocalDensity.current
    val displayMetrics: DisplayMetrics = context.resources.displayMetrics

    val shaderSrc = androidx.compose.runtime.remember {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            loadShaderSource(context)
        } else {
            ""
        }
    }

    val autoPxPerMm = androidx.compose.runtime.remember(displayMetrics) {
        val xdpi = displayMetrics.xdpi
        if (xdpi.isFinite() && xdpi > 0f) xdpi / 25.4f else FALLBACK_PX_PER_MM
    }
    val resolvedPxPerMm = if (parameters.pixelsPerMillimeter > 0f) {
        parameters.pixelsPerMillimeter
    } else {
        autoPxPerMm
    }

    val graphicsLayerModifier = this.graphicsLayer {
        if (size.width <= 1f || size.height <= 1f) {
            renderEffect = null
            return@graphicsLayer
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            // Android 13+ (API 33): Full AGSL RuntimeShader
            if (shaderSrc.isNotEmpty()) {
                renderEffect = AgslShaderHelper.createRenderEffect(
                    shaderSrc = shaderSrc,
                    width = size.width,
                    height = size.height,
                    tiltDegrees = tiltDegrees,
                    hingeSide = hingeSide,
                    parameters = parameters,
                    resolvedPxPerMm = resolvedPxPerMm
                )
            } else {
                renderEffect = null
            }
            clip = true
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            // Android 12 (API 31 / 32) compatibility: 3D perspective fold around the hinge
            val pivotX = if (hingeSide > 0f) 1.0f else 0.0f
            transformOrigin = TransformOrigin(pivotX, 0.5f)
            cameraDistance = 12f * density.density
            rotationY = -tiltDegrees * 0.75f
            clip = true

            // RenderEffect blur is natively supported in Android 12 (API 31+)
            val blurPx = (abs(tiltDegrees) * 0.8f * (parameters.blurSpread / 0.12f)).coerceIn(0f, 28f)
            renderEffect = Android12BlurHelper.createBlurEffect(blurPx)
        } else {
            // Pre-Android 12 fallback: 3D perspective rotation
            val pivotX = if (hingeSide > 0f) 1.0f else 0.0f
            transformOrigin = TransformOrigin(pivotX, 0.5f)
            cameraDistance = 12f * density.density
            rotationY = -tiltDegrees * 0.75f
            clip = true
        }

        @Suppress("UNUSED_EXPRESSION")
        density
    }

    // On Android 12 or below, augment the 3D perspective with realistic fold crease shadow
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
        graphicsLayerModifier.drawWithContent {
            drawContent()
            val absTilt = abs(tiltDegrees)
            if (absTilt > 0.5f) {
                val shadowAlpha = (absTilt / 45f * 0.35f).coerceIn(0f, 0.4f)
                val shadowBrush = if (hingeSide > 0f) {
                    Brush.horizontalGradient(
                        colors = listOf(Color.Black.copy(alpha = shadowAlpha), Color.Transparent),
                        startX = 0f,
                        endX = size.width * 0.65f
                    )
                } else {
                    Brush.horizontalGradient(
                        colors = listOf(Color.Transparent, Color.Black.copy(alpha = shadowAlpha)),
                        startX = size.width * 0.35f,
                        endX = size.width
                    )
                }
                drawRect(shadowBrush)
            }
        }
    } else {
        graphicsLayerModifier
    }
}
