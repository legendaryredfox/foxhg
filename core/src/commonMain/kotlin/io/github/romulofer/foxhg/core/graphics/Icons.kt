package io.github.romulofer.foxhg.core.graphics

import io.github.romulofer.foxhg.core.FoxResult
import io.github.romulofer.foxhg.core.game.HgssLayout
import io.github.romulofer.foxhg.core.narc.Narc
import io.github.romulofer.foxhg.core.project.RomProject

/**
 * The small pictures of the game: Pokémon icons and item icons, decoded on first use and kept. Whatever the project
 * has edited is what is shown. A missing or unreadable picture is null.
 */
class Icons(private val project: RomProject) {
    private val pokemonNarc: Narc? by lazy { narc(HgssLayout.POKEMON_ICONS) }
    private val itemNarc: Narc? by lazy { narc(HgssLayout.ITEM_ICONS) }
    private val pokemonPalette: Nclr? by lazy { pokemonNarc?.files?.firstOrNull()?.let { (Nclr.parse(it) as? FoxResult.Ok)?.value } }
    private val cache = HashMap<String, RgbaImage?>()

    private fun narc(path: String): Narc? = project.read(path)?.let { (Narc.parse(it) as? FoxResult.Ok)?.value }

    /** The icon of species [species] (a national dex number; 0 is the "?" icon), top frame, 32x32. */
    fun pokemon(species: Int): RgbaImage? = if (species !in 0..MAX_SPECIES) null else cache.getOrPut("p$species") {
        val file = pokemonNarc?.files?.getOrNull(species + 7) ?: return@getOrPut null
        val palette = pokemonPalette ?: return@getOrPut null
        val index = POKEMON_PALETTES.getOrNull(species) ?: 0
        val ncgr = (Ncgr.parse(file) as? FoxResult.Ok)?.value ?: return@getOrPut null
        val image = ncgr.toImage(palette.palette16(index))
        if (image.height >= 64) image.crop(0, 32) else image
    }

    /** The bag icon of item [item], 32x32. */
    fun item(item: Int): RgbaImage? = cache.getOrPut("i$item") {
        val (ncgrFile, nclrFile) = ITEM_FILES[item] ?: return@getOrPut null
        val files = itemNarc?.files ?: return@getOrPut null
        val ncgr = (Ncgr.parse(files.getOrNull(ncgrFile) ?: return@getOrPut null) as? FoxResult.Ok)?.value ?: return@getOrPut null
        val nclr = (Nclr.parse(files.getOrNull(nclrFile) ?: return@getOrPut null) as? FoxResult.Ok)?.value ?: return@getOrPut null
        ncgr.toImage(nclr.palette16(0))
    }

    private companion object {
        const val MAX_SPECIES = 493 // forms beyond it have icons in other slots, not decoded yet
        val POKEMON_PALETTES: List<Int> by lazy { POKEMON_ICON_PALETTES.split(',').map { it.toInt() } }
        val ITEM_FILES: Map<Int, Pair<Int, Int>> by lazy {
            ITEM_ICON_DATA.split('\n').associate { line ->
                val (item, ncgr, nclr) = line.split('|')
                item.toInt() to (ncgr.toInt() to nclr.toInt())
            }
        }
    }
}
