package dev.couchtv.launcher.util

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.drawable.Drawable
import androidx.core.graphics.drawable.toBitmap
import kotlin.math.abs
import kotlin.math.max

/**
 * Colour from an icon, for the tiles the launcher draws itself.
 *
 * The Mac app does this in `TileArt.swift`: read the icon's ground, keep the brand colour, and paint
 * the tile with it. On Android we only need one number — the accent — because the tile's layout
 * carries the rest. The average is weighted towards saturated pixels so a Netflix icon comes out red
 * and not muddy grey: white logos, black backgrounds and transparent corners contribute nothing.
 */
object IconTools {

    fun dominantColor(drawable: Drawable, size: Int = 32): Int {
        val bitmap = runCatching { drawable.toBitmap(size, size) }.getOrNull() ?: return FALLBACK
        return dominantColor(bitmap)
    }

    fun dominantColor(bitmap: Bitmap): Int {
        val width = bitmap.width.coerceAtLeast(1)
        val height = bitmap.height.coerceAtLeast(1)
        var red = 0f
        var green = 0f
        var blue = 0f
        var weightSum = 0f

        // Sample a grid rather than every pixel: 256 reads is plenty and costs nothing.
        val stepX = max(1, width / 16)
        val stepY = max(1, height / 16)
        val hsv = FloatArray(3)
        var y = 0
        while (y < height) {
            var x = 0
            while (x < width) {
                val pixel = bitmap.getPixel(x, y)
                val alpha = Color.alpha(pixel)
                if (alpha > 40) {
                    Color.colorToHSV(pixel, hsv)
                    // Skip whites, blacks and greys: they are the icon's paper, not its ink.
                    val weight = hsv[1].coerceIn(0f, 1f) * (0.25f + 0.75f * (hsv[2].coerceIn(0f, 1f)))
                    val meaningful = hsv[1] > 0.12f || hsv[2] in 0.15f..0.9f
                    if (meaningful) {
                        red += Color.red(pixel) * weight
                        green += Color.green(pixel) * weight
                        blue += Color.blue(pixel) * weight
                        weightSum += weight
                    }
                }
                x += stepX
            }
            y += stepY
        }

        if (weightSum < 0.01f) return FALLBACK
        val r = (red / weightSum).coerceIn(0f, 255f).toInt()
        val g = (green / weightSum).coerceIn(0f, 255f).toInt()
        val b = (blue / weightSum).coerceIn(0f, 255f).toInt()

        // Keep the accent usable as a background behind white text: darken bright colours a little.
        val result = FloatArray(3)
        Color.colorToHSV(Color.rgb(r, g, b), result)
        result[2] = result[2].coerceIn(0.18f, 0.62f)
        result[1] = result[1].coerceIn(0.22f, 0.95f)
        return Color.HSVToColor(result)
    }

    /** A deterministic accent for content without artwork, from its id. */
    fun colorFor(seed: String): Int {
        var hash = 0
        for (char in seed) hash = hash * 31 + char.code
        val hue = abs(hash % 360).toFloat()
        return Color.HSVToColor(floatArrayOf(hue, 0.55f, 0.5f))
    }

    private val FALLBACK = Color.rgb(0x2C, 0x2C, 0x2E)
}
