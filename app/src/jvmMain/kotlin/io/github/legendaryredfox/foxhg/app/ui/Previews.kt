package io.github.legendaryredfox.foxhg.app.ui

import io.github.legendaryredfox.foxhg.app.tr
import org.jetbrains.compose.resources.stringResource
import io.github.legendaryredfox.foxhg.app.resources.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import io.github.legendaryredfox.foxhg.core.graphics.Icons
import io.github.legendaryredfox.foxhg.core.graphics.RgbaImage
import java.awt.image.BufferedImage

/** Pictures of the game for the editors, as bitmaps ready to draw. Decoded once per picture. */
class IconBitmaps(private val icons: Icons) {
    private val cache = HashMap<String, ImageBitmap?>()

    private fun bitmap(image: RgbaImage?): ImageBitmap? = image?.let {
        BufferedImage(it.width, it.height, BufferedImage.TYPE_INT_ARGB).also { b -> b.setRGB(0, 0, it.width, it.height, it.argb, 0, it.width) }.toComposeImageBitmap()
    }

    fun pokemon(species: Int): ImageBitmap? = cache.getOrPut("p$species") { bitmap(icons.pokemon(species)) }
    fun item(item: Int): ImageBitmap? = cache.getOrPut("i$item") { bitmap(icons.item(item)) }
    fun overworld(spriteEntry: Int, facing: Int): ImageBitmap? = cache.getOrPut("o$spriteEntry/$facing") { bitmap(icons.overworld(spriteEntry, facing)) }
    fun front(species: Int, shiny: Boolean): ImageBitmap? = cache.getOrPut("f$species/$shiny") { bitmap(icons.pokemonFront(species, shiny)) }
    fun back(species: Int, shiny: Boolean): ImageBitmap? = cache.getOrPut("k$species/$shiny") { bitmap(icons.pokemonBack(species, shiny)) }
    fun trainerClass(trainerClass: Int): ImageBitmap? = cache.getOrPut("t$trainerClass") { bitmap(icons.trainerClass(trainerClass)) }
}

/** The pictures of the open project; null when nothing is open (previews are then left out). */
val LocalIcons: ProvidableCompositionLocal<IconBitmaps?> = compositionLocalOf { null }

@Composable
fun ProvideIcons(icons: IconBitmaps?, content: @Composable () -> Unit) = CompositionLocalProvider(LocalIcons provides icons, content = content)

@Composable
private fun Picture(bitmap: ImageBitmap?, size: Dp, description: String, quality: FilterQuality = FilterQuality.None) {
    Canvas(Modifier.size(size).testTag(if (bitmap != null) "preview:image" else "preview:none").semantics { contentDescription = description }) {
        if (bitmap != null) drawImage(bitmap, IntOffset.Zero, IntSize(bitmap.width, bitmap.height), IntOffset.Zero, IntSize(this.size.width.toInt(), this.size.height.toInt()), filterQuality = quality)
    }
}

/** The icon of a species (a national dex number), or nothing if the game has none. */
@Composable
fun PokemonIcon(species: Int, size: Dp = 26.dp) {
    val icons = LocalIcons.current ?: return
    Picture(icons.pokemon(species), size, tr(Res.string.previews_pok_mon_icon))
}

/** The bag icon of an item. */
@Composable
fun ItemIcon(item: Int, size: Dp = 26.dp) {
    val icons = LocalIcons.current ?: return
    Picture(icons.item(item), size, tr(Res.string.previews_item_icon))
}

/** The front picture of a trainer class (80x80, scaled smoothly because it is drawn smaller than it is). */
@Composable
fun TrainerClassIcon(trainerClass: Int, size: Dp = 26.dp) {
    val icons = LocalIcons.current ?: return
    Picture(icons.trainerClass(trainerClass), size, tr(Res.string.previews_trainer_class_picture), FilterQuality.Medium)
}

/** The battle pictures of a species, front and back, with a switch to see the shiny colours. */
@Composable
fun PokemonPortraits(species: Int, size: Dp = 88.dp) {
    val icons = LocalIcons.current ?: return
    var shiny by remember(species) { mutableStateOf(false) }
    val front = icons.front(species, shiny)
    val back = icons.back(species, shiny)
    if (front == null && back == null) return
    Row(Modifier.testTag("portraits"), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        Picture(front, size, tr(Res.string.previews_front_picture))
        Picture(back, size, tr(Res.string.previews_back_picture))
        ToggleChip(stringResource(Res.string.previews_shiny), shiny, { shiny = it })
    }
}

/** The front picture of a species (80x80 scaled down), for rows that show one Pokémon. */
@Composable
fun PokemonFront(species: Int, size: Dp = 56.dp) {
    val icons = LocalIcons.current ?: return
    Picture(icons.front(species, false), size, tr(Res.string.previews_front_picture), FilterQuality.Medium)
}

/** The overworld sprite of an event's sprite entry, facing 0 up, 1 down, 2 left, 3 right. Nothing when the entry has no 2D sprite. */
@Composable
fun OverworldIcon(spriteEntry: Int, facing: Int = 1, size: Dp = 26.dp) {
    val icons = LocalIcons.current ?: return
    Picture(icons.overworld(spriteEntry, facing), size, tr(Res.string.previews_overworld_sprite))
}

/** What an id stands for, so a field can show it: a picture or a play button. */
enum class PreviewKind { SPECIES, ITEM, TRAINER_CLASS, OVERWORLD, SOUND, CRY }

/**
 * The preview of [id] as [kind]: the icon of a species, an item or a trainer class, the overworld sprite of a sprite entry (looking
 * [facing]: 0 up, 1 down, 2 left, 3 right), or a play button for a sound (with its name when [named]) or a species' cry. Fields get one by
 * passing a [PreviewKind] (`NumberField`, `PickerField`), so an editor never wires pictures or players itself.
 */
@Composable
fun Preview(kind: PreviewKind, id: Int, size: Dp = 26.dp, facing: Int = 1, named: Boolean = false) {
    when (kind) {
        PreviewKind.SPECIES -> PokemonIcon(id, size)
        PreviewKind.ITEM -> ItemIcon(id, size)
        PreviewKind.TRAINER_CLASS -> TrainerClassIcon(id, size)
        PreviewKind.OVERWORLD -> OverworldIcon(id, facing, size)
        PreviewKind.SOUND -> SoundButton(id, withName = named)
        PreviewKind.CRY -> CryButton(id)
    }
}

/** A square of [color] with a thin border, for things that have a colour instead of a picture (tile types). */
@Composable
fun Swatch(color: Color, size: Dp = 18.dp) {
    Canvas(Modifier.size(size).background(color).border(1.dp, MaterialTheme.colorScheme.outline)) {}
}

/** A species picker that shows each species' icon. */
@Composable
fun SpeciesPicker(label: String, selected: Int, species: List<String>, onSelect: (Int) -> Unit, modifier: Modifier = Modifier.then(Modifier)) {
    PickerField(label, selected, species, onSelect, modifier, preview = PreviewKind.SPECIES)
}

/** A trainer class picker that shows each class' picture. */
@Composable
fun TrainerClassPicker(label: String, selected: Int, classes: List<String>, onSelect: (Int) -> Unit, modifier: Modifier = Modifier.then(Modifier)) {
    PickerField(label, selected, classes, onSelect, modifier, preview = PreviewKind.TRAINER_CLASS)
}

/** An item picker that shows each item's icon. */
@Composable
fun ItemPicker(label: String, selected: Int, items: List<String>, onSelect: (Int) -> Unit, modifier: Modifier = Modifier.then(Modifier)) {
    PickerField(label, selected, items, onSelect, modifier, preview = PreviewKind.ITEM)
}
