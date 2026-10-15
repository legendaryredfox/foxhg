package io.github.romulofer.foxhg.core.data.hiddenitem

import io.github.romulofer.foxhg.core.FoxResult
import io.github.romulofer.foxhg.core.game.HgssLayout
import io.github.romulofer.foxhg.core.project.RomProject
import io.github.romulofer.foxhg.core.putU16
import io.github.romulofer.foxhg.core.u16
import io.github.romulofer.foxhg.core.u8

/**
 * One hidden item (Itemfinder spot): item (u16), quantity (u8), a byte and a u16 whose meaning is unknown (zero in the
 * vanilla table, kept as they are), and the index (u16). The script of the spot is [SCRIPT_BASE] + [index]; the flag
 * that remembers it was picked up is [FLAG_BASE] + [index].
 */
data class HiddenItem(val item: Int, val quantity: Int, val unknown3: Int, val unknown4: Int, val index: Int) {
    val script: Int get() = SCRIPT_BASE + index
    val flag: Int get() = FLAG_BASE + index

    internal fun write(out: ByteArray, at: Int) {
        out.putU16(at, item)
        out[at + 2] = quantity.toByte()
        out[at + 3] = unknown3.toByte()
        out.putU16(at + 4, unknown4)
        out.putU16(at + 6, index)
    }

    companion object {
        const val SIZE = 8
        const val SCRIPT_BASE = 8000
        const val FLAG_BASE = 800

        internal fun read(bytes: ByteArray, at: Int) = HiddenItem(bytes.u16(at), bytes.u8(at + 2), bytes.u8(at + 3), bytes.u16(at + 4), bytes.u16(at + 6))
    }
}

/**
 * The hidden item table of the ARM9: [HgssLayout.HIDDEN_ITEMS_ARM9_LENGTH] holds how many entries the game reads and
 * [HgssLayout.HIDDEN_ITEMS_ARM9_CAPACITY] how many fit. Layout as in DSPRE's hidden items editor and the HeartGold
 * decomp's `HiddenItemData`; the offsets are those of HeartGold English.
 */
data class HiddenItems(val entries: List<HiddenItem>, val capacity: Int) {
    fun with(index: Int, entry: HiddenItem): HiddenItems = copy(entries = entries.toMutableList().also { it[index] = entry })
    fun added(entry: HiddenItem): HiddenItems = copy(entries = entries + entry)
    fun removed(index: Int): HiddenItems = copy(entries = entries.filterIndexed { i, _ -> i != index })

    /** The smallest index no entry uses yet, for a new spot. */
    fun freeIndex(): Int = generateSequence(0) { it + 1 }.first { i -> entries.none { it.index == i } }

    /**
     * Writes the table into a copy of [arm9] and sets the length. Slots a shorter table leaves behind are cleared;
     * whatever follows the length in the ARM9 is otherwise not touched.
     */
    fun applyTo(arm9: ByteArray): ByteArray {
        require(entries.size <= capacity) { "The table holds at most $capacity entries" }
        val out = arm9.copyOf()
        val oldLength = arm9.u8(HgssLayout.HIDDEN_ITEMS_ARM9_LENGTH)
        entries.forEachIndexed { i, e -> e.write(out, HgssLayout.HIDDEN_ITEMS_ARM9_TABLE + i * HiddenItem.SIZE) }
        for (i in entries.size until oldLength) out.fill(0, HgssLayout.HIDDEN_ITEMS_ARM9_TABLE + i * HiddenItem.SIZE, HgssLayout.HIDDEN_ITEMS_ARM9_TABLE + (i + 1) * HiddenItem.SIZE)
        out[HgssLayout.HIDDEN_ITEMS_ARM9_LENGTH] = entries.size.toByte()
        return out
    }

    companion object {
        fun parse(arm9: ByteArray): FoxResult<HiddenItems> {
            val needed = maxOf(HgssLayout.HIDDEN_ITEMS_ARM9_CAPACITY + 1, HgssLayout.HIDDEN_ITEMS_ARM9_TABLE + HiddenItem.SIZE)
            if (arm9.size < needed) return FoxResult.Err("ARM9 is too small for the hidden item table")
            val length = arm9.u8(HgssLayout.HIDDEN_ITEMS_ARM9_LENGTH)
            val capacity = arm9.u8(HgssLayout.HIDDEN_ITEMS_ARM9_CAPACITY)
            if (length > capacity) return FoxResult.Err("The hidden item table claims $length entries but holds at most $capacity: this is not a HeartGold English ARM9")
            val end = HgssLayout.HIDDEN_ITEMS_ARM9_TABLE + length * HiddenItem.SIZE
            if (arm9.size < end) return FoxResult.Err("ARM9 is too small for $length hidden items")
            return FoxResult.Ok(HiddenItems(List(length) { HiddenItem.read(arm9, HgssLayout.HIDDEN_ITEMS_ARM9_TABLE + it * HiddenItem.SIZE) }, capacity))
        }

        fun load(project: RomProject): FoxResult<HiddenItems> = when (val a = project.readArm9()) {
            is FoxResult.Ok -> parse(a.value)
            is FoxResult.Err -> a
        }

        fun save(project: RomProject, table: HiddenItems): FoxResult<Unit> = when (val a = project.readArm9()) {
            is FoxResult.Ok -> project.writeArm9(table.applyTo(a.value))
            is FoxResult.Err -> a
        }
    }
}
