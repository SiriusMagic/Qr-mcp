package com.zirakusa.qr.data

/** A rectangle in card pixels (left, top inclusive; right, bottom exclusive). */
data class Box(val left: Int, val top: Int, val right: Int, val bottom: Int) {
    val width get() = right - left
    val height get() = bottom - top
    fun overlaps(o: Box) = left < o.right && o.left < right && top < o.bottom && o.top < bottom
    fun contains(o: Box) = o.left >= left && o.top >= top && o.right <= right && o.bottom <= bottom
}

/**
 * Where everything goes on the «Arbolito» card (pure, with tests): the tree on top, the QR code below on its own white
 * panel, the name under it. The code is never covered: the tree stays above the panel, the panel keeps the code's light
 * margin (4 modules) plus padding, and each module is a whole number of pixels so the squares stay sharp.
 */
object ArtLayout {
    const val WIDTH = 1080
    const val HEIGHT = 1620
    /** The tree's drawing area: everything of the tree (star, glow, ground) stays inside it. */
    val tree = Box(120, 40, 960, 600)
    /** The white panel's outer box; the code is centred in it. */
    val panel = Box(130, 640, 950, 1460)
    const val PANEL_PADDING = 20
    /** Where the name and the «scan to save» line go, under the panel. */
    val caption = Box(60, 1480, 1020, 1620)

    /** The code's box for a grid of [modules] (quiet margin included) and the pixels per module; never under 3 px. */
    fun code(modules: Int): Pair<Box, Int> {
        val room = minOf(panel.width, panel.height) - 2 * PANEL_PADDING
        val scale = maxOf(3, room / modules)
        val side = modules * scale
        val left = panel.left + (panel.width - side) / 2
        val top = panel.top + (panel.height - side) / 2
        return Box(left, top, left + side, top + side) to scale
    }
}
