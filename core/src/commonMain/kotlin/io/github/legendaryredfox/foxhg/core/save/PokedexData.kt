package io.github.legendaryredfox.foxhg.core.save

import io.github.legendaryredfox.foxhg.core.FoxResult
import io.github.legendaryredfox.foxhg.core.u32

/**
 * The `POKEDEX` chunk (0x340 bytes): the magic 0xBEEFCAFE, the caught and seen bitmaps (bit species - 1, 16 words each, 493 species), the seen genders,
 * the order forms were seen in, the languages caught and four flags ([dexEnabled], [nationalDex], ...). From the decomp's `Pokedex`; the fresh chunk
 * ([fresh]) was checked byte for byte against the Pokedex of a new game's save. The bytes the editor does not decode are kept in [bytes], so an unedited
 * chunk writes back unchanged.
 *
 * A species counts as caught only when its seen bit is set too (the game's `Pokedex_CheckMonCaughtFlag`), so [withCaught] sets both. Forms, genders and
 * languages are left as they are: a Pokémon marked seen here shows as its first gender and form.
 */
class PokedexData private constructor(val bytes: ByteArray) {
    init { require(bytes.size == SIZE) { "the Pokédex is $SIZE bytes" } }

    val magic: Long get() = bytes.u32(0)

    private fun bit(base: Int, species: Int): Boolean {
        require(species in 1..SPECIES) { "species $species is outside 1..$SPECIES" }
        val i = species - 1
        return bytes[base + (i shr 3)].toInt() and (1 shl (i and 7)) != 0
    }

    private fun withBit(base: Int, species: Int, on: Boolean): ByteArray {
        require(species in 1..SPECIES) { "species $species is outside 1..$SPECIES" }
        val i = species - 1
        val copy = bytes.copyOf()
        val at = base + (i shr 3)
        val mask = 1 shl (i and 7)
        copy[at] = (if (on) copy[at].toInt() or mask else copy[at].toInt() and mask.inv()).toByte()
        return copy
    }

    fun isSeen(species: Int): Boolean = bit(SEEN, species)

    /** Caught as the game counts it: the caught bit and the seen bit. */
    fun isCaught(species: Int): Boolean = bit(CAUGHT, species) && bit(SEEN, species)

    val seenCount: Int get() = (1..SPECIES).count { isSeen(it) }
    val caughtCount: Int get() = (1..SPECIES).count { isCaught(it) }

    fun withSeen(species: Int, on: Boolean): PokedexData {
        var b = withBit(SEEN, species, on)
        // not seen means not caught
        if (!on) b = PokedexData(b).withBit(CAUGHT, species, false)
        return PokedexData(b)
    }

    fun withCaught(species: Int, on: Boolean): PokedexData {
        val b = withBit(CAUGHT, species, on)
        return PokedexData(if (on) PokedexData(b).withBit(SEEN, species, true) else b)
    }

    /** A copy with every species seen, and caught too when [caught] is true. */
    fun withAll(caught: Boolean): PokedexData = (1..SPECIES).fold(this) { d, s -> if (caught) d.withCaught(s, true) else d.withSeen(s, true) }

    /** A copy with every species neither seen nor caught. */
    fun cleared(): PokedexData = PokedexData(bytes.copyOf().also { b -> b.fill(0, CAUGHT, CAUGHT + BITMAP); b.fill(0, SEEN, SEEN + BITMAP) }.also { restorePadding(it) })

    /** The Pokédex has been given to the player (the game's `dexEnabled`). */
    val dexEnabled: Boolean get() = bytes[DEX_ENABLED].toInt() != 0

    /** The National Dex is on (the game's own `nationalDex`; the player data keeps a second flag for the same thing). */
    val nationalDex: Boolean get() = bytes[NATIONAL_DEX].toInt() != 0

    fun withDexEnabled(on: Boolean) = PokedexData(bytes.copyOf().also { it[DEX_ENABLED] = if (on) 1 else 0 })
    fun withNationalDex(on: Boolean) = PokedexData(bytes.copyOf().also { it[NATIONAL_DEX] = if (on) 1 else 0 })

    fun toBytes(): ByteArray = bytes.copyOf()

    override fun equals(other: Any?) = other is PokedexData && bytes.contentEquals(other.bytes)
    override fun hashCode() = bytes.contentHashCode()

    companion object {
        const val SIZE = 0x340
        const val MAGIC = 0xBEEFCAFEL
        const val SPECIES = 493
        const val CAUGHT = 4
        const val SEEN = 0x44
        const val BITMAP = 64
        const val DEX_ENABLED = 0x336
        const val NATIONAL_DEX = 0x337

        /** The last byte of both bitmaps is 0xFF in a new game (the game's init marks the eight flags past the last species). */
        private fun restorePadding(b: ByteArray) { b[CAUGHT + BITMAP - 1] = 0xFF.toByte(); b[SEEN + BITMAP - 1] = 0xFF.toByte() }

        /** The Pokédex of a new game: the magic, nothing seen, and the 0xFF fills the game's `Save_Pokedex_Init` writes for form orders. */
        fun fresh(): PokedexData = PokedexData(ByteArray(SIZE).also { b ->
            b[0] = 0xFE.toByte(); b[1] = 0xCA.toByte(); b[2] = 0xEF.toByte(); b[3] = 0xBE.toByte()
            restorePadding(b)
            b.fill(0xFF.toByte(), 0x108, 0x144)
            b.fill(0xFF.toByte(), 0x338, 0x33F)
        })

        fun parse(bytes: ByteArray): FoxResult<PokedexData> {
            if (bytes.size != SIZE) return FoxResult.Err("The Pokédex is $SIZE bytes, got ${bytes.size}")
            if (bytes.u32(0) != MAGIC) return FoxResult.Err("The Pokédex does not start with the game's mark")
            return FoxResult.Ok(PokedexData(bytes.copyOf()))
        }

        fun read(save: SaveFile): FoxResult<PokedexData> {
            val block = save.current(BlockKind.GENERAL) ?: return FoxResult.Err("The save has no sound general block")
            return parse(save.chunk(block, SaveChunks.byId(SaveChunks.POKEDEX)))
        }
    }
}

/** A copy of the save with [dex] written into the current general block (checksums recomputed). */
fun SaveFile.withPokedex(dex: PokedexData): FoxResult<SaveFile> {
    val block = current(BlockKind.GENERAL) ?: return FoxResult.Err("The save has no sound general block")
    return FoxResult.Ok(withChunk(block, SaveChunks.byId(SaveChunks.POKEDEX), dex.toBytes()))
}
