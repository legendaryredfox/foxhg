package io.github.legendaryredfox.foxhg.core.save

import io.github.legendaryredfox.foxhg.core.FoxResult
import io.github.legendaryredfox.foxhg.core.putU16
import io.github.legendaryredfox.foxhg.core.u16

/** One bag slot: an item and how many of it ([id] 0 is an empty slot). */
data class BagSlot(val id: Int, val quantity: Int) {
    val isEmpty: Boolean get() = id == 0

    companion object { val EMPTY = BagSlot(0, 0) }
}

/** The eight bag pockets, in the order the game keeps them in the save ([capacity] slots each, `NUM_BAG_*`); [fieldPocket] is the number the item data gives the pocket. */
enum class BagPocket(val label: String, val capacity: Int, val fieldPocket: Int, val maxQuantity: Int) {
    ITEMS("Items", 165, 0, 999),
    KEY_ITEMS("Key items", 50, 7, 1),
    TMS_HMS("TMs & HMs", 101, 3, 99),
    MAIL("Mail", 12, 5, 999),
    MEDICINE("Medicine", 40, 1, 999),
    BERRIES("Berries", 64, 4, 999),
    BALLS("Poké Balls", 24, 2, 999),
    BATTLE_ITEMS("Battle items", 30, 6, 999);

    /** Where the pocket starts, in slots. */
    val start: Int get() = entries.takeWhile { it != this }.sumOf { it.capacity }
}

/**
 * The `BAG` chunk (0x79C bytes): the eight pockets as arrays of 4 byte slots (u16 item, u16 quantity), 486 slots in all, then the two items
 * registered to the touch screen buttons (u16 each). From the decomp's `Bag` (`bag_types_def.h`, `constants/items.h`); the sizes add up to the
 * chunk size. Unused slots are `0, 0` and used ones come first in a pocket, as the game's `Bag_AddItem` keeps them.
 */
data class BagData(val slots: List<BagSlot>, val registered: List<Int>) {
    init {
        require(slots.size == SLOT_COUNT) { "a bag has $SLOT_COUNT slots" }
        require(registered.size == 2) { "a bag has two registered items" }
    }

    /** The used slots of [pocket], in the order the game lists them. */
    fun items(pocket: BagPocket): List<BagSlot> = slots.subList(pocket.start, pocket.start + pocket.capacity).filter { !it.isEmpty }

    /** How many of [item] the bag holds, in whatever pocket it is. */
    fun count(item: Int): Int = slots.filter { it.id == item }.sumOf { it.quantity }

    /** A copy where [pocket] holds exactly [list] (used slots first, the rest emptied); an error when it does not fit or a quantity is out of range. */
    fun withItems(pocket: BagPocket, list: List<BagSlot>): FoxResult<BagData> {
        if (list.size > pocket.capacity) return FoxResult.Err("${pocket.label} holds at most ${pocket.capacity} different items")
        for (s in list) {
            if (s.id !in 1..MAX_ITEM) return FoxResult.Err("Item ${s.id} does not exist (1 to $MAX_ITEM)")
            if (s.quantity !in 1..pocket.maxQuantity) return FoxResult.Err("${pocket.label} holds 1 to ${pocket.maxQuantity} of an item")
        }
        if (list.map { it.id }.toSet().size != list.size) return FoxResult.Err("An item is in ${pocket.label} twice")
        val padded = list + List(pocket.capacity - list.size) { BagSlot.EMPTY }
        return FoxResult.Ok(copy(slots = slots.subList(0, pocket.start) + padded + slots.subList(pocket.start + pocket.capacity, SLOT_COUNT)))
    }

    /** A copy with [quantity] more of [item] in [pocket]: added to its slot when it has one, else in a new slot at the end. */
    fun adding(pocket: BagPocket, item: Int, quantity: Int): FoxResult<BagData> {
        val list = items(pocket)
        val at = list.indexOfFirst { it.id == item }
        val next = if (at >= 0) list.toMutableList().also { it[at] = BagSlot(item, minOf(pocket.maxQuantity, it[at].quantity + quantity)) } else list + BagSlot(item, quantity)
        return withItems(pocket, next)
    }

    /** A copy without [item] in [pocket]. */
    fun removing(pocket: BagPocket, item: Int): FoxResult<BagData> = withItems(pocket, items(pocket).filter { it.id != item })

    fun toBytes(): ByteArray = ByteArray(SIZE).also { b ->
        slots.forEachIndexed { i, s -> b.putU16(i * 4, s.id); b.putU16(i * 4 + 2, s.quantity) }
        registered.forEachIndexed { i, id -> b.putU16(SLOT_COUNT * 4 + i * 2, id) }
    }

    companion object {
        const val SIZE = 0x79C
        val SLOT_COUNT = BagPocket.entries.sumOf { it.capacity }

        /** Item ids the game has: 1 to 536 in the English HeartGold's item table (the table has 537 entries, 0 being none). */
        const val MAX_ITEM = 536

        fun parse(bytes: ByteArray): FoxResult<BagData> {
            if (bytes.size != SIZE) return FoxResult.Err("The bag is $SIZE bytes, got ${bytes.size}")
            return FoxResult.Ok(BagData(List(SLOT_COUNT) { BagSlot(bytes.u16(it * 4), bytes.u16(it * 4 + 2)) }, List(2) { bytes.u16(SLOT_COUNT * 4 + it * 2) }))
        }

        fun read(save: SaveFile): FoxResult<BagData> {
            val block = save.current(BlockKind.GENERAL) ?: return FoxResult.Err("The save has no sound general block")
            return parse(save.chunk(block, SaveChunks.byId(SaveChunks.BAG)))
        }
    }
}

/** A copy of the save with [bag] written into the current general block (checksums recomputed). */
fun SaveFile.withBag(bag: BagData): FoxResult<SaveFile> {
    val block = current(BlockKind.GENERAL) ?: return FoxResult.Err("The save has no sound general block")
    return FoxResult.Ok(withChunk(block, SaveChunks.byId(SaveChunks.BAG), bag.toBytes()))
}
