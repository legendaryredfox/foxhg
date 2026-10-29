package io.github.legendaryredfox.foxhg.core.graphics

import io.github.legendaryredfox.foxhg.core.FoxResult
import io.github.legendaryredfox.foxhg.core.data.event.OverworldSprites
import io.github.legendaryredfox.foxhg.core.narc.Narc
import io.github.legendaryredfox.foxhg.core.u16
import io.github.legendaryredfox.foxhg.core.u32
import io.github.legendaryredfox.foxhg.core.project.RomProject

/**
 * The small pictures of the game: Pokémon icons and item icons, decoded on first use and kept. Whatever the project
 * has edited is what is shown. A missing or unreadable picture is null.
 */
class Icons(private val project: RomProject) {
    private val pokemonNarc: Narc? by lazy { narc(project.layout.pokemonIcons) }
    private val itemNarc: Narc? by lazy { narc(project.layout.itemIcons) }
    private val pokemonPalette: Nclr? by lazy { pokemonNarc?.files?.firstOrNull()?.let { (Nclr.parse(it) as? FoxResult.Ok)?.value } }
    private val trainerNarc: Narc? by lazy { narc(project.layout.trainerFrontSprites) }
    private val battleNarc: Narc? by lazy { narc(project.layout.pokemonSprites) }
    private val alternateNarc: Narc? by lazy { narc(project.layout.alternatePokemonSprites) }
    private val spriteTable: OverworldSprites? by lazy { (OverworldSprites.load(project) as? FoxResult.Ok)?.value }
    private val spriteNarc: Narc? by lazy { narc(project.layout.overworldSprites) }
    private val sprites = HashMap<Int, Nsbtx?>()
    private val cache = HashMap<String, RgbaImage?>()

    private fun narc(path: String): Narc? = project.read(path)?.let { (Narc.parse(it) as? FoxResult.Ok)?.value }

    /**
     * The icon of species [species] (a national dex number; 0 is the "?" icon), top frame, 32x32. The numbers above
     * 493 are the personal data records of the eggs and alternate forms (494 egg, 495 Manaphy egg, 496-498 Deoxys,
     * 499-500 Wormadam, 501 Giratina, 502 Shaymin, 503-507 Rotom).
     */
    fun pokemon(species: Int): RgbaImage? {
        val (slot, form, egg) = recordForm(species) ?: return null
        return pokemonIcon(slot, form, egg)
    }

    /** The icon of [species] in [form] (Unown letter, Deoxys, Burmy, Wormadam, Shellos, Gastrodon, Giratina, Shaymin and Rotom forms) or as an egg. */
    fun pokemonIcon(species: Int, form: Int = 0, egg: Boolean = false): RgbaImage? = cache.getOrPut("p$species/$form/$egg") {
        val (naix, paletteSlot) = iconSlot(species, form, egg)
        val file = pokemonNarc?.files?.getOrNull(naix) ?: return@getOrPut null
        val palette = pokemonPalette ?: return@getOrPut null
        val ncgr = (Ncgr.parse(file) as? FoxResult.Ok)?.value ?: return@getOrPut null
        val image = ncgr.toImage(palette.palette16(POKEMON_PALETTES.getOrNull(paletteSlot) ?: 0))
        if (image.height >= 64) image.crop(0, 32) else image
    }

    /** The battle picture of [species] seen from the front (or the back), 80x80, first of its two frames. Species above 493 are the alternate form records (see [pokemon]). */
    fun pokemonFront(species: Int, shiny: Boolean = false): RgbaImage? = battleSprite(species, shiny, back = false)
    fun pokemonBack(species: Int, shiny: Boolean = false): RgbaImage? = battleSprite(species, shiny, back = true)

    private fun battleSprite(species: Int, shiny: Boolean, back: Boolean): RgbaImage? = cache.getOrPut("s$species/$shiny/$back") {
        val (graphic, palette) = when {
            species in 0..MAX_SPECIES -> {
                val files = battleNarc?.files ?: return@getOrPut null
                // Six files per species: back (female, male), front (female, male), palette, shiny palette. The male
                // picture is used; a species with only a female one (Nidoran, Chansey...) has an empty male file.
                val base = species * 6 + if (back) 0 else 2
                (files.getOrNull(base + 1)?.takeIf { it.size > 0x30 } ?: files.getOrNull(base)) to files.getOrNull(species * 6 + 4 + if (shiny) 1 else 0)
            }
            else -> {
                val form = ALTERNATE_SPRITES[species] ?: return@getOrPut null
                val files = alternateNarc?.files ?: return@getOrPut null
                files.getOrNull(if (back || form.single) form.back else form.back + 1) to files.getOrNull(form.palette + if (shiny && !form.single) 1 else 0)
            }
        }
        val ncgr = (Ncgr.parse(decryptSprite(graphic ?: return@getOrPut null)) as? FoxResult.Ok)?.value ?: return@getOrPut null
        val colors = (Nclr.parse(palette ?: return@getOrPut null) as? FoxResult.Ok)?.value ?: return@getOrPut null
        ncgr.toImage(colors.palette16(0)).let { if (it.width >= 160) it.crop(0, 0, 80, it.height) else it }
    }

    /** The bag icon of item [item], 32x32. */
    fun item(item: Int): RgbaImage? = cache.getOrPut("i$item") {
        val (ncgrFile, nclrFile) = ITEM_FILES[item] ?: return@getOrPut null
        val files = itemNarc?.files ?: return@getOrPut null
        val ncgr = (Ncgr.parse(files.getOrNull(ncgrFile) ?: return@getOrPut null) as? FoxResult.Ok)?.value ?: return@getOrPut null
        val nclr = (Nclr.parse(files.getOrNull(nclrFile) ?: return@getOrPut null) as? FoxResult.Ok)?.value ?: return@getOrPut null
        ncgr.toImage(nclr.palette16(0))
    }

    /** The front picture of trainer class [trainerClass]: five files per class (NCGR, NCLR, NCER, NANR, a second NCGR); cell 0 of the NCER places the NCGR's tiles. */
    fun trainerClass(trainerClass: Int): RgbaImage? = cache.getOrPut("t$trainerClass") {
        val files = trainerNarc?.files ?: return@getOrPut null
        val graphic = files.getOrNull(trainerClass * 5) ?: return@getOrPut null
        val nclr = (Nclr.parse(files.getOrNull(trainerClass * 5 + 1) ?: return@getOrPut null) as? FoxResult.Ok)?.value ?: return@getOrPut null
        val ncer = (Ncer.parse(files.getOrNull(trainerClass * 5 + 2) ?: return@getOrPut null) as? FoxResult.Ok)?.value ?: return@getOrPut null
        ncer.render(0, graphic, nclr)
    }

    /** Sprite files of overworld events are 2D pictures in a texture archive; facing is 0 up, 1 down, 2 left, 3 right. */
    fun overworld(spriteEntry: Int, facing: Int = 1): RgbaImage? = cache.getOrPut("o$spriteEntry/$facing") {
        val file = spriteTable?.entries?.get(spriteEntry)?.spriteFile ?: return@getOrPut null
        val archive = sprites.getOrPut(file) { spriteNarc?.files?.getOrNull(file)?.let { (Nsbtx.parse(it) as? FoxResult.Ok)?.value } } ?: return@getOrPut null
        archive.image(overworldTexture(archive.textureCount, facing.coerceIn(0, 3)))
    }

    private companion object {
        /** A personal data record number as (species, form, egg) for the icon tables; null above the last record. */
        fun recordForm(record: Int): Triple<Int, Int, Boolean>? = when (record) {
            in 0..MAX_SPECIES -> Triple(record, 0, false)
            494 -> Triple(494, 0, true)
            495 -> Triple(Species.MANAPHY, 0, true)
            in 496..498 -> Triple(Species.DEOXYS, record - 495, false)
            in 499..500 -> Triple(Species.WORMADAM, record - 498, false)
            501 -> Triple(Species.GIRATINA, 1, false)
            502 -> Triple(Species.SHAYMIN, 1, false)
            in 503..507 -> Triple(Species.ROTOM, record - 502, false)
            else -> null
        }

        /** The icon file and the palette table slot of a species and form (`GetMonIconNaixEx` and `GetMonIconPaletteEx` of the decomp). */
        fun iconSlot(species: Int, form: Int, egg: Boolean): Pair<Int, Int> {
            if (egg) return if (species == Species.MANAPHY) 502 to 495 else 501 to 494
            if (species > MAX_SPECIES) return 7 to 0
            if (form != 0) {
                val forms = mapOf(
                    Species.DEOXYS to (503 to 496), Species.UNOWN to (507 to 499), Species.BURMY to (534 to 527), Species.WORMADAM to (536 to 529),
                    Species.SHELLOS to (538 to 531), Species.GASTRODON to (539 to 532), Species.GIRATINA to (540 to 533), Species.SHAYMIN to (541 to 534),
                    Species.ROTOM to (542 to 535),
                )
                forms[species]?.let { (naix, palette) -> return naix + form - 1 to palette + form - 1 }
            }
            return species + 7 to species
        }

        /** The four fields of the front/back files of an alternate form in the "otherpoke" archive: the back picture (the front follows it) and the first of its two palettes. */
        class AlternateSprite(val back: Int, val palette: Int, val single: Boolean = false)

        /** Personal record -> files of the alternate form pictures, from the decomp's otherpoke list. */
        val ALTERNATE_SPRITES = mapOf(
            494 to AlternateSprite(132, 230, single = true), 495 to AlternateSprite(133, 231, single = true), // eggs: one picture
            496 to AlternateSprite(2, 158), 497 to AlternateSprite(4, 158), 498 to AlternateSprite(6, 158),
            499 to AlternateSprite(80, 178), 500 to AlternateSprite(82, 180),
            501 to AlternateSprite(152, 250), 502 to AlternateSprite(136, 234),
            503 to AlternateSprite(140, 238), 504 to AlternateSprite(142, 240), 505 to AlternateSprite(144, 242),
            506 to AlternateSprite(146, 244), 507 to AlternateSprite(148, 246),
        )

        /** Pokémon pictures are scrambled with a 16-bit stream cipher seeded by the first word of their tile data (Platinum and HeartGold). */
        fun decryptSprite(file: ByteArray): ByteArray {
            val out = file.copyOf()
            val chars = findSection(out, "RAHC") ?: return out
            val length = out.u32(chars + 24).toInt()
            val start = chars + 8 + out.u32(chars + 28).toInt()
            if (start < 0 || start + 2 > out.size) return out
            var key = out.u16(start).toLong()
            var i = 0
            while (i + 1 < length && start + i + 1 < out.size) {
                val word = out.u16(start + i) xor (key.toInt() and 0xFFFF)
                out[start + i] = word.toByte()
                out[start + i + 1] = (word shr 8).toByte()
                key = (key * 1103515245L + 24691L) and 0xFFFFFFFFL
                i += 2
            }
            return out
        }

        /** Which texture shows a facing: sprites carry 1, 4, 8, 16 or more frames, with different layouts (as DSPRE's event editor picks them). */
        fun overworldTexture(count: Int, facing: Int): Int = when {
            count <= 1 -> 0
            count <= 4 -> facing
            count <= 8 -> facing * 2
            count <= 16 -> intArrayOf(0, 11, 2, 4)[facing]
            else -> intArrayOf(0, 27, 2, 4)[facing]
        }

        const val MAX_SPECIES = 493
        val POKEMON_PALETTES: List<Int> by lazy { POKEMON_ICON_PALETTES.split(',').map { it.toInt() } }
        val ITEM_FILES: Map<Int, Pair<Int, Int>> by lazy {
            ITEM_ICON_DATA.split('\n').associate { line ->
                val (item, ncgr, nclr) = line.split('|')
                item.toInt() to (ncgr.toInt() to nclr.toInt())
            }
        }
    }
}

/** National dex numbers of the species that have several pictures, from the decomp's species list. */
private object Species {
    const val UNOWN = 201
    const val DEOXYS = 386
    const val BURMY = 412
    const val WORMADAM = 413
    const val SHELLOS = 422
    const val GASTRODON = 423
    const val ROTOM = 479
    const val MANAPHY = 490
    const val GIRATINA = 487
    const val SHAYMIN = 492
}
