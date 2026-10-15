package io.github.romulofer.foxhg.app

import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.res.loadImageBitmap
import androidx.compose.ui.res.useResource

/** The app icon (`icon.png` in the resources), used for the window and the top bar. */
object AppIcon {
    val painter: BitmapPainter by lazy { BitmapPainter(useResource("icon.png", ::loadImageBitmap)) }
}
