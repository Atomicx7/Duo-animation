package com.example.duofold.ui

/**
 * Physical parameters of the frosted-glass fold (mirrors Swift `FoldParameters`).
 *
 * @param eyeDistanceMillimeters Distance from the viewer's eyes to the untilted
 *   screen, looking at it head-on. The eye stays there while the device tilts.
 *   Default 450mm (the original uses 320): a larger distance flattens the
 *   perspective magnification — less sideways stretch and less edge cropping
 *   at big tilts — while the gap-driven blur/darken is completely unaffected.
 * @param pixelsPerMillimeter Density of layer pixels (≈ xdpi / 25.4). If <= 0,
 *   [foldEffect] auto-resolves it from display metrics. The original uses
 *   ~6 pt/mm for iPhone panels.
 * @param blurSpread Blur radius gained per px of separation between the glass
 *   and the UI plane (tangent of the scattering half-angle).
 * @param darkening Fraction of light lost per px of blur radius — the frostier
 *   the glass, the darker it gets. Authored at the 6 units/mm reference
 *   density of the original; [foldEffect] normalizes it by the real display
 *   density so the look matches iOS on any screen.
 */
data class FoldParameters(
    val eyeDistanceMillimeters: Float = 450f,
    val pixelsPerMillimeter: Float = 0f, // 0 = auto-resolve from display metrics
    val blurSpread: Float = 0.12f,
    val darkening: Float = 0.015f
)
