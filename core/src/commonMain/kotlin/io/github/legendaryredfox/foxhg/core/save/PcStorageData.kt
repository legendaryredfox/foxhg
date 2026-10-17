package io.github.legendaryredfox.foxhg.core.save

import io.github.legendaryredfox.foxhg.core.FoxResult
import io.github.legendaryredfox.foxhg.core.putU16
import io.github.legendaryredfox.foxhg.core.putU32
import io.github.legendaryredfox.foxhg.core.text.Charmap
import io.github.legendaryredfox.foxhg.core.u16
import io.github.legendaryredfox.foxhg.core.u32

/**
 * The `PCSTORAGE` chunk (0x122FC bytes, alone in the storage block): 18 boxes of 30 Pokemon (0x88 bytes each, the party form without the party data) and
 * 16 bytes the game keeps after each ([boxTails]), then the open box, a modified flag, the 18 box names (20 text codes each), the 18 wallpapers, the
 * unlocked wallpapers byte and 0x11 filler bytes ([filler]). From the decomp's `PokemonStorageSystem`; the offsets add up to the chunk size and the layout
 * was read on a real save (the names `BOX 1` to `BOX 18`, wallpapers 0 to 17, empty slots as encrypted zeros).
 */
data class PcStorageData(
    val boxes: List<List<Pokemon>>,
    val boxTails: List<ByteArray>,
    val currentBox: Int,
    val modifiedFlags: Long,
    val nameCodes: List<List<Int>>,
    val wallpapers: List<Int>,
    val unlockedWallpapers: Int,
    val filler: ByteArray,
) {
    init {
        require(boxes.size == BOXES && boxes.all { it.size == PER_BOX }) { "a PC has $BOXES boxes of $PER_BOX slots" }
        require(boxTails.size == BOXES && boxTails.all { it.size == TAIL }) { "each box keeps $TAIL bytes after its Pokémon" }
        require(nameCodes.size == BOXES && nameCodes.all { it.size == NAME_SLOTS }) { "a box name has $NAME_SLOTS slots" }
        require(wallpapers.size == BOXES) { "a PC has $BOXES wallpapers" }
        require(filler.size == FILLER) { "the filler is $FILLER bytes" }
    }

    fun boxName(box: Int): String = Charmap.HGSS.decode(nameCodes[box].takeWhile { it != END }.toIntArray())

    /** The Pokemon in [box], without the empty slots. */
    fun occupied(box: Int): List<Pokemon> = boxes[box].filter { !it.isEmpty }

    val total: Int get() = boxes.sumOf { b -> b.count { !it.isEmpty } }

    /** A copy with [mon] in [slot] of [box]. */
    fun placing(box: Int, slot: Int, mon: Pokemon): PcStorageData {
        require(box in 0 until BOXES && slot in 0 until PER_BOX) { "no slot $slot in box $box" }
        return copy(boxes = boxes.mapIndexed { b, list -> if (b == box) list.toMutableList().also { it[slot] = mon } else list })
    }

    /** A copy with [slot] of [box] emptied. */
    fun clearing(box: Int, slot: Int): PcStorageData = placing(box, slot, Pokemon.EMPTY)

    /** The first empty slot of [box], or null when it is full. */
    fun firstEmpty(box: Int): Int? = boxes[box].indexOfFirst { it.isEmpty }.takeIf { it >= 0 }

    /** A copy with [text] (at most [MAX_NAME] characters the game's text table has) as the name of [box]. */
    fun withBoxName(box: Int, text: String): FoxResult<PcStorageData> {
        val codes = when (val r = Charmap.HGSS.encode(text)) {
            is FoxResult.Ok -> r.value
            is FoxResult.Err -> return r
        }
        if (codes.isEmpty()) return FoxResult.Err("A box name cannot be empty")
        if (codes.size > MAX_NAME) return FoxResult.Err("A box name has at most $MAX_NAME characters")
        val row = List(NAME_SLOTS) { if (it < codes.size) codes[it] else if (it == codes.size) END else 0 }
        return FoxResult.Ok(copy(nameCodes = nameCodes.mapIndexed { b, old -> if (b == box) row else old }))
    }

    /** A copy with [wallpaper] (one of [WALLPAPERS]'s ids) on [box]. */
    fun withWallpaper(box: Int, wallpaper: Int): FoxResult<PcStorageData> {
        if (wallpaper !in WALLPAPERS) return FoxResult.Err("Wallpaper $wallpaper does not exist")
        return FoxResult.Ok(copy(wallpapers = wallpapers.mapIndexed { b, old -> if (b == box) wallpaper else old }))
    }

    fun toBytes(): ByteArray = ByteArray(SIZE).also { b ->
        for (box in 0 until BOXES) {
            for (slot in 0 until PER_BOX) boxes[box][slot].toBoxBytes().copyInto(b, box * BOX_STRIDE + slot * Pokemon.BOX_SIZE)
            boxTails[box].copyInto(b, box * BOX_STRIDE + PER_BOX * Pokemon.BOX_SIZE)
        }
        b.putU32(CURRENT_BOX, currentBox.toLong()); b.putU32(CURRENT_BOX + 4, modifiedFlags)
        nameCodes.forEachIndexed { box, codes -> codes.forEachIndexed { i, c -> b.putU16(NAMES + (box * NAME_SLOTS + i) * 2, c) } }
        wallpapers.forEachIndexed { i, w -> b[WALLPAPERS_AT + i] = w.toByte() }
        b[WALLPAPERS_AT + BOXES] = unlockedWallpapers.toByte()
        filler.copyInto(b, WALLPAPERS_AT + BOXES + 1)
    }

    override fun equals(other: Any?) = other is PcStorageData && toBytes().contentEquals(other.toBytes())
    override fun hashCode() = toBytes().contentHashCode()

    companion object {
        const val BOXES = 18
        const val PER_BOX = 30
        const val TAIL = 16
        const val NAME_SLOTS = 20
        const val MAX_NAME = 8
        const val END = 0xFFFF
        const val FILLER = 0x11
        const val BOX_STRIDE = PER_BOX * Pokemon.BOX_SIZE + TAIL
        const val CURRENT_BOX = BOXES * BOX_STRIDE
        const val NAMES = CURRENT_BOX + 8
        const val WALLPAPERS_AT = NAMES + BOXES * NAME_SLOTS * 2
        const val SIZE = 0x122FC

        /** Wallpaper names by id (`box_wallpaper.h`): 0 to 15 are there from the start, 32 to 39 are bonus ones that [unlockedWallpapers] (bit n for id 32 + n) opens. */
        val WALLPAPERS: Map<Int, String> = listOf(
            "Forest", "City", "Desert", "Savanna", "Crag", "Volcano", "Snow", "Cave", "Beach", "Seafloor", "River", "Sky", "Poké Center", "Machine", "Checks", "Simple",
        ).withIndex().associate { it.index to it.value } + listOf(
            "Heart", "Soul", "Big Brother", "Pokéathlon", "Trio", "Spiky-eared Pichu", "Kimono Girl", "Revival",
        ).withIndex().associate { 32 + it.index to it.value }

        fun parse(bytes: ByteArray): FoxResult<PcStorageData> {
            if (bytes.size != SIZE) return FoxResult.Err("The PC storage is $SIZE bytes, got ${bytes.size}")
            val boxes = ArrayList<List<Pokemon>>(BOXES)
            for (box in 0 until BOXES) {
                val list = ArrayList<Pokemon>(PER_BOX)
                for (slot in 0 until PER_BOX) {
                    val at = box * BOX_STRIDE + slot * Pokemon.BOX_SIZE
                    when (val m = Pokemon.parseBox(bytes.copyOfRange(at, at + Pokemon.BOX_SIZE))) {
                        is FoxResult.Ok -> list += m.value
                        is FoxResult.Err -> return FoxResult.Err("Box ${box + 1}, slot ${slot + 1}: ${m.message}")
                    }
                }
                boxes += list
            }
            return FoxResult.Ok(
                PcStorageData(
                    boxes = boxes,
                    boxTails = List(BOXES) { bytes.copyOfRange(it * BOX_STRIDE + PER_BOX * Pokemon.BOX_SIZE, (it + 1) * BOX_STRIDE) },
                    currentBox = bytes.u32(CURRENT_BOX).toInt(),
                    modifiedFlags = bytes.u32(CURRENT_BOX + 4),
                    nameCodes = List(BOXES) { box -> List(NAME_SLOTS) { bytes.u16(NAMES + (box * NAME_SLOTS + it) * 2) } },
                    wallpapers = List(BOXES) { bytes[WALLPAPERS_AT + it].toInt() and 0xFF },
                    unlockedWallpapers = bytes[WALLPAPERS_AT + BOXES].toInt() and 0xFF,
                    filler = bytes.copyOfRange(WALLPAPERS_AT + BOXES + 1, SIZE),
                ),
            )
        }

        fun read(save: SaveFile): FoxResult<PcStorageData> {
            val block = save.current(BlockKind.STORAGE) ?: return FoxResult.Err("The save has no sound storage block")
            return parse(save.chunk(block, SaveChunks.byId(SaveChunks.PCSTORAGE)))
        }
    }
}

/** A copy of the save with [pc] written into the current storage block (checksum recomputed). */
fun SaveFile.withPcStorage(pc: PcStorageData): FoxResult<SaveFile> {
    val block = current(BlockKind.STORAGE) ?: return FoxResult.Err("The save has no sound storage block")
    return FoxResult.Ok(withChunk(block, SaveChunks.byId(SaveChunks.PCSTORAGE), pc.toBytes()))
}
