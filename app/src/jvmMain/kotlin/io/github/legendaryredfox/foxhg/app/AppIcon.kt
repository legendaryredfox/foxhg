package io.github.legendaryredfox.foxhg.app

import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.res.loadImageBitmap
import androidx.compose.ui.res.useResource
import java.awt.Image
import javax.imageio.ImageIO

/**
 * The app icon, pre-rendered at several sizes (`icons/icon-<size>.png` in the resources, see `scripts/gen-app-icons.py`).
 * Scaling one big image down at run time is blurry, so each use picks the closest size that is not smaller than needed.
 */
object AppIcon {
    private val sizes = listOf(16, 24, 32, 48, 64, 96, 128, 256)

    /** The painter for an icon drawn [pixels] physical pixels wide (dp size times the screen density). */
    fun painter(pixels: Float): BitmapPainter {
        val size = sizes.firstOrNull { it >= pixels } ?: sizes.last()
        return BitmapPainter(useResource("icons/icon-$size.png", ::loadImageBitmap))
    }

    /** Every size, for the window and taskbar icon: the window manager picks the one it likes. */
    val windowImages: List<Image> by lazy {
        sizes.map { size -> useResource("icons/icon-$size.png") { ImageIO.read(it) } }
    }
}
