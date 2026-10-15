package io.github.romulofer.foxhg.app.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import io.github.romulofer.foxhg.core.graphics.Icons
import io.github.romulofer.foxhg.core.graphics.RgbaImage
import java.awt.image.BufferedImage

/** Pictures of the game for the editors, as bitmaps ready to draw. Decoded once per picture. */
class IconBitmaps(private val icons: Icons) {
    private val cache = HashMap<String, ImageBitmap?>()

    private fun bitmap(image: RgbaImage?): ImageBitmap? = image?.let {
        BufferedImage(it.width, it.height, BufferedImage.TYPE_INT_ARGB).also { b -> b.setRGB(0, 0, it.width, it.height, it.argb, 0, it.width) }.toComposeImageBitmap()
    }

    fun pokemon(species: Int): ImageBitmap? = cache.getOrPut("p$species") { bitmap(icons.pokemon(species)) }
    fun item(item: Int): ImageBitmap? = cache.getOrPut("i$item") { bitmap(icons.item(item)) }
}

/** The pictures of the open project; null when nothing is open (previews are then left out). */
val LocalIcons: ProvidableCompositionLocal<IconBitmaps?> = compositionLocalOf { null }

@Composable
fun ProvideIcons(icons: IconBitmaps?, content: @Composable () -> Unit) = CompositionLocalProvider(LocalIcons provides icons, content = content)

@Composable
private fun Picture(bitmap: ImageBitmap?, size: Dp) {
    Canvas(Modifier.size(size)) {
        if (bitmap != null) drawImage(bitmap, IntOffset.Zero, IntSize(bitmap.width, bitmap.height), IntOffset.Zero, IntSize(this.size.width.toInt(), this.size.height.toInt()), filterQuality = FilterQuality.None)
    }
}

/** The icon of a species (a national dex number), or nothing if the game has none. */
@Composable
fun PokemonIcon(species: Int, size: Dp = 26.dp) {
    val icons = LocalIcons.current ?: return
    Picture(icons.pokemon(species), size)
}

/** The bag icon of an item. */
@Composable
fun ItemIcon(item: Int, size: Dp = 26.dp) {
    val icons = LocalIcons.current ?: return
    Picture(icons.item(item), size)
}

/** A square of [color] with a thin border, for things that have a colour instead of a picture (tile types). */
@Composable
fun Swatch(color: Color, size: Dp = 18.dp) {
    Canvas(Modifier.size(size).background(color).border(1.dp, MaterialTheme.colorScheme.outline)) {}
}

/** A species picker that shows each species' icon. */
@Composable
fun SpeciesPicker(label: String, selected: Int, species: List<String>, onSelect: (Int) -> Unit, modifier: Modifier = Modifier.then(Modifier)) {
    PickerField(label, selected, species, onSelect, modifier, leading = { PokemonIcon(it, 22.dp) })
}

/** An item picker that shows each item's icon. */
@Composable
fun ItemPicker(label: String, selected: Int, items: List<String>, onSelect: (Int) -> Unit, modifier: Modifier = Modifier.then(Modifier)) {
    PickerField(label, selected, items, onSelect, modifier, leading = { ItemIcon(it, 22.dp) })
}
