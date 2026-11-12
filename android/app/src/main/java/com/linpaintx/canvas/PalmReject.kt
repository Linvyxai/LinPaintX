package com.linpaintx.canvas

/**
 * How wide a finger's contact is, in millimetres, or null when the screen cannot say.
 *
 * The system's `xdpi` is a lie on a number of tablets — some report the panel's native dot pitch,
 * others round it to a stock value, and some give no useful figure at all. The palm test compares a
 * pixel width against millimetres, so a too-small `xdpi` reads an ordinary fingertip as 30 mm wide:
 * every touch would be taken for a palm, and the canvas would paint nothing at all. So `xdpi` is
 * trusted only between [SANE_DPI_LOW] and [SANE_DPI_HIGH], and the density the app itself is laid
 * out by answers instead; neither of them usable means no palm test at all.
 */
fun touchMillimetres(majorPx: Float, xdpi: Float, densityDpi: Int): Float? {
    val dpi = when {
        xdpi >= SANE_DPI_LOW && xdpi <= SANE_DPI_HIGH -> xdpi
        densityDpi >= SANE_DPI_LOW -> densityDpi.toFloat()
        else -> return null
    }
    return majorPx / dpi * 25.4f
}

private const val SANE_DPI_LOW = 100f
private const val SANE_DPI_HIGH = 600f
