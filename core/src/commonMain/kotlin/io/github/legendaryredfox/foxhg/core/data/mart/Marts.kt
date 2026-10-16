package io.github.legendaryredfox.foxhg.core.data.mart

import io.github.legendaryredfox.foxhg.core.FoxResult
import io.github.legendaryredfox.foxhg.core.game.HgssLayout
import io.github.legendaryredfox.foxhg.core.project.RomProject
import io.github.legendaryredfox.foxhg.core.putU16
import io.github.legendaryredfox.foxhg.core.u16
import io.github.legendaryredfox.foxhg.core.u32

/** One entry of the badge Poké Mart: [item] is on sale once the player has a [tier] (1 to 6) of badges. */
data class BadgeMartEntry(val item: Int, val tier: Int)

/**
 * The Poké Marts kept in the ARM9. [specials] are the lists that `SpMartScreen n` opens (n = index): u16 item numbers ended
 * by 0xFFFF, found through the pointer table at [HgssLayout.SPECIAL_MART_POINTERS]. A list is stored in place and the next
 * list follows it directly, so it can be shorter than it was but never longer: [capacity] is the number of items it can hold.
 * [badge] is the standard clerk's list, which grows with the badge count. Item prices are not here but in the item data.
 * Addresses and logic from the decomp's `scrcmd_mart.c`; checked against the ROM.
 */
data class Marts(val specials: List<List<Int>>, private val addresses: List<Int>, val capacity: List<Int>, val badge: List<BadgeMartEntry>) {
    init {
        require(specials.size == addresses.size && specials.size == capacity.size) { "one address and size per special mart" }
        require(specials.indices.all { specials[it].size <= capacity[it] }) { "a list is longer than the room it has" }
    }

    fun withItem(mart: Int, slot: Int, item: Int): Marts = copy(specials = specials.mapIndexed { m, l -> if (m == mart) l.toMutableList().also { it[slot] = item } else l })

    /** Whether [mart] has room for one more item. */
    fun canAdd(mart: Int): Boolean = specials[mart].size < capacity[mart]

    /** [mart] with [item] appended, or unchanged when the list is full. */
    fun withAdded(mart: Int, item: Int): Marts = if (!canAdd(mart)) this else copy(specials = specials.mapIndexed { m, l -> if (m == mart) l + item else l })

    fun withRemoved(mart: Int, slot: Int): Marts = copy(specials = specials.mapIndexed { m, l -> if (m == mart) l.filterIndexed { i, _ -> i != slot } else l })

    fun withBadge(index: Int, entry: BadgeMartEntry): Marts = copy(badge = badge.toMutableList().also { it[index] = entry })

    /** Writes every list and the badge table into a copy of [arm9]; the room a shorter list leaves is filled with 0xFFFF. */
    fun applyTo(arm9: ByteArray): ByteArray = arm9.copyOf().also { out ->
        specials.forEachIndexed { m, items ->
            for (i in 0 until capacity[m] + 1) out.putU16(addresses[m] + 2 * i, items.getOrElse(i) { END })
        }
        badge.forEachIndexed { i, e ->
            out.putU16(HgssLayout.BADGE_MART + 4 * i, e.item)
            out.putU16(HgssLayout.BADGE_MART + 4 * i + 2, e.tier)
        }
    }

    fun save(project: RomProject): FoxResult<Unit> = when (val a = project.readArm9()) {
        is FoxResult.Ok -> project.writeArm9(applyTo(a.value))
        is FoxResult.Err -> a
    }

    companion object {
        const val END = 0xFFFF

        /** Badges needed for each tier: 0 badges is tier 1, then one tier per two badges up to 8 badges for tier 6. */
        fun tierFor(badges: Int): Int = when (badges) { 0 -> 1; 1, 2 -> 2; 3, 4 -> 3; 5, 6 -> 4; 7 -> 5; else -> 6 }

        /** The items the badge mart sells to a player with [badges] badges, in table order. */
        fun badgeItems(badge: List<BadgeMartEntry>, badges: Int): List<Int> = badge.filter { tierFor(badges) >= it.tier }.map { it.item }

        fun parse(arm9: ByteArray): FoxResult<Marts> {
            val tableEnd = HgssLayout.SPECIAL_MART_POINTERS + 4 * HgssLayout.SPECIAL_MARTS
            if (arm9.size < tableEnd || arm9.size < HgssLayout.BADGE_MART + 4 * HgssLayout.BADGE_MART_ENTRIES) return FoxResult.Err("The ARM9 is too small for the mart tables")
            val specials = ArrayList<List<Int>>()
            val addresses = ArrayList<Int>()
            for (m in 0 until HgssLayout.SPECIAL_MARTS) {
                val address = arm9.u32(HgssLayout.SPECIAL_MART_POINTERS + 4 * m).toInt() - HgssLayout.ARM9_BASE
                if (address < 0 || address >= arm9.size) return FoxResult.Err("Special mart $m points outside the ARM9 (0x${address.toString(16)})")
                val items = ArrayList<Int>()
                var at = address
                while (at + 2 <= arm9.size && arm9.u16(at) != END) { items += arm9.u16(at); at += 2 }
                if (at + 2 > arm9.size) return FoxResult.Err("Special mart $m has no end marker")
                specials += items
                addresses += address
            }
            val badge = List(HgssLayout.BADGE_MART_ENTRIES) { BadgeMartEntry(arm9.u16(HgssLayout.BADGE_MART + 4 * it), arm9.u16(HgssLayout.BADGE_MART + 4 * it + 2)) }
            return FoxResult.Ok(Marts(specials, addresses, specials.map { it.size }, badge))
        }

        fun load(project: RomProject): FoxResult<Marts> = when (val a = project.readArm9()) {
            is FoxResult.Ok -> parse(a.value)
            is FoxResult.Err -> a
        }
    }
}
