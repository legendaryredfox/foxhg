package io.github.legendaryredfox.foxhg.core.data.mart

import io.github.legendaryredfox.foxhg.core.FoxResult
import io.github.legendaryredfox.foxhg.core.game.HgssLayout
import io.github.legendaryredfox.foxhg.core.patch.PatchStatus
import io.github.legendaryredfox.foxhg.core.patch.PatchTarget
import io.github.legendaryredfox.foxhg.core.patch.dspre.DsprePatches
import io.github.legendaryredfox.foxhg.core.project.RomProject
import io.github.legendaryredfox.foxhg.core.putU16
import io.github.legendaryredfox.foxhg.core.putU32
import io.github.legendaryredfox.foxhg.core.u16
import io.github.legendaryredfox.foxhg.core.u32

/** One entry of the badge Poké Mart: [item] is on sale once the player has a [tier] (1 to 6) of badges. */
data class BadgeMartEntry(val item: Int, val tier: Int)

/** Whether a special mart may grow past the room it has in the ARM9. */
enum class MartGrowth {
    /** The synthetic overlay is there and its mart area is free: lists can grow. */
    AVAILABLE,

    /** The ARM9 expansion patch is not applied, so there is no synthetic overlay to move a list to. */
    NEEDS_EXPANSION,

    /** Something other than foxhg's mart lists is in the mart area of the synthetic overlay. */
    AREA_IN_USE,
}

/**
 * The Poké Marts kept in the ARM9. [specials] are the lists that `SpMartScreen n` opens (n = index): u16 item numbers ended
 * by 0xFFFF, found through the pointer table at [HgssLayout.SPECIAL_MART_POINTERS]. In the game they sit one after another in the
 * ARM9, so a list keeps its place while it has at most [capacity] items. A longer one is moved to the mart area of the synthetic
 * overlay and its pointer set there (the wiki guide "Adding New Poké Marts" does the same by hand); that needs the ARM9 expansion
 * patch ([growth]). [badge] is the standard clerk's list, which grows with the badge count. Item prices are not here but in the item
 * data. Addresses and logic from the decomp's `scrcmd_mart.c`; checked against the ROM.
 */
data class Marts(val specials: List<List<Int>>, val badge: List<BadgeMartEntry>, val growth: MartGrowth = MartGrowth.NEEDS_EXPANSION) {
    init {
        require(specials.size == VANILLA_ADDRESSES.size) { "${VANILLA_ADDRESSES.size} special marts" }
    }

    /** Items each list holds in its own place in the ARM9. */
    val capacity: List<Int> get() = VANILLA_CAPACITY

    /** True when [mart] has more items than its own place holds, so it lives in the synthetic overlay. */
    fun isMoved(mart: Int): Boolean = specials[mart].size > capacity[mart]

    /** Bytes of the mart area that the moved lists use. */
    val areaBytes: Int get() = specials.indices.filter { isMoved(it) }.sumOf { 2 * (specials[it].size + 1) }

    /** Whether [mart] can take one more item: it has room, or may move, and the mart area has room. */
    fun canAdd(mart: Int): Boolean {
        val size = specials[mart].size
        if (size >= MAX_ITEMS) return false
        if (size < capacity[mart]) return true
        if (growth != MartGrowth.AVAILABLE) return false
        return areaBytes + (if (isMoved(mart)) 2 else 2 * (size + 2)) <= HgssLayout.MART_AREA_SIZE
    }

    fun withItem(mart: Int, slot: Int, item: Int): Marts = copy(specials = specials.mapIndexed { m, l -> if (m == mart) l.toMutableList().also { it[slot] = item } else l })

    /** [mart] with [item] appended, or unchanged when [canAdd] says no. */
    fun withAdded(mart: Int, item: Int): Marts = if (!canAdd(mart)) this else copy(specials = specials.mapIndexed { m, l -> if (m == mart) l + item else l })

    fun withRemoved(mart: Int, slot: Int): Marts = copy(specials = specials.mapIndexed { m, l -> if (m == mart) l.filterIndexed { i, _ -> i != slot } else l })

    fun withBadge(index: Int, entry: BadgeMartEntry): Marts = copy(badge = badge.toMutableList().also { it[index] = entry })

    /**
     * Writes the marts into copies of [arm9] and [synthetic] (null when the ROM has no synthetic overlay): lists that fit go to their own
     * place (the room a shorter list leaves is filled with 0xFFFF) and their pointer is set back, the others are laid out one after
     * another in the mart area, which is cleared first.
     */
    fun applyTo(arm9: ByteArray, synthetic: ByteArray?): Pair<ByteArray, ByteArray?> {
        val outArm9 = arm9.copyOf()
        val outSynth = synthetic?.copyOf()
        outSynth?.fill(0, HgssLayout.MART_AREA, HgssLayout.MART_AREA + HgssLayout.MART_AREA_SIZE)
        var next = HgssLayout.MART_AREA
        specials.forEachIndexed { m, items ->
            val pointerAt = HgssLayout.SPECIAL_MART_POINTERS + 4 * m
            if (!isMoved(m)) {
                for (i in 0 until capacity[m] + 1) outArm9.putU16(VANILLA_ADDRESSES[m] + 2 * i, items.getOrElse(i) { END })
                outArm9.putU32(pointerAt, (HgssLayout.ARM9_BASE + VANILLA_ADDRESSES[m]).toLong())
            } else {
                requireNotNull(outSynth) { "A mart outgrew its place but the ROM has no synthetic overlay" }
                items.forEachIndexed { i, item -> outSynth.putU16(next + 2 * i, item) }
                outSynth.putU16(next + 2 * items.size, END)
                outArm9.putU32(pointerAt, (HgssLayout.SYNTHETIC_OVERLAY_ADDRESS + next).toLong())
                next += 2 * (items.size + 1)
            }
        }
        badge.forEachIndexed { i, e ->
            outArm9.putU16(HgssLayout.BADGE_MART + 4 * i, e.item)
            outArm9.putU16(HgssLayout.BADGE_MART + 4 * i + 2, e.tier)
        }
        return outArm9 to outSynth
    }

    fun save(project: RomProject): FoxResult<Unit> {
        val arm9 = when (val a = project.readArm9()) {
            is FoxResult.Ok -> a.value
            is FoxResult.Err -> return a
        }
        val synth = (PatchTarget.SyntheticOverlay.read(project) as? FoxResult.Ok)?.value?.takeIf { it.size >= HgssLayout.SYNTHETIC_OVERLAY_SIZE }
        if (synth == null && specials.indices.any { isMoved(it) }) return FoxResult.Err("A mart is longer than its place in the ARM9: apply the ARM9 expansion patch in the ROM toolbox first")
        val (newArm9, newSynth) = applyTo(arm9, synth)
        return project.atomically {
            val wroteArm9 = project.writeArm9(newArm9)
            if (wroteArm9 is FoxResult.Err) return@atomically wroteArm9
            // Only touch the synthetic overlay when the mart area changes, so a ROM without moved lists stays clean.
            if (synth != null && newSynth != null && !newSynth.contentEquals(synth)) PatchTarget.SyntheticOverlay.write(project, newSynth) else FoxResult.Ok(Unit)
        }
    }

    companion object {
        const val END = 0xFFFF

        /** Most items a mart shows: the game counts at most 256 and keeps the count in a byte. */
        const val MAX_ITEMS = 255

        /** Where the 30 lists are in the decompressed ARM9 of the retail ROM (the decomp's `_020FBA54` and the rest, minus 0x02000000). */
        val VANILLA_ADDRESSES = listOf(
            0xFBA54, 0xFBA78, 0xFBAB0, 0xFBBEA, 0xFBC1A, 0xFBBB4, 0xFBAFA, 0xFBC34, 0xFBACA, 0xFBA60,
            0xFBA88, 0xFBAD4, 0xFBA80, 0xFBB84, 0xFBAC0, 0xFBA70, 0xFBA98, 0xFBA5A, 0xFBC02, 0xFBC4E,
            0xFBC68, 0xFBA68, 0xFBBC6, 0xFBB08, 0xFBA90, 0xFBAA0, 0xFBAA8, 0xFBB16, 0xFBAB8, 0xFBBD8,
        )

        /** How many items each list holds in its own place: the number of items it has in the retail ROM. */
        val VANILLA_CAPACITY = listOf(2, 3, 3, 11, 12, 8, 6, 12, 4, 3, 3, 5, 3, 7, 4, 3, 3, 2, 11, 12, 12, 3, 8, 6, 3, 3, 3, 6, 3, 8)

        /** Badges needed for each tier: 0 badges is tier 1, then one tier per two badges up to 8 badges for tier 6. */
        fun tierFor(badges: Int): Int = when (badges) { 0 -> 1; 1, 2 -> 2; 3, 4 -> 3; 5, 6 -> 4; 7 -> 5; else -> 6 }

        /** The items the badge mart sells to a player with [badges] badges, in table order. */
        fun badgeItems(badge: List<BadgeMartEntry>, badges: Int): List<Int> = badge.filter { tierFor(badges) >= it.tier }.map { it.item }

        /** [synthetic] is the synthetic overlay when the ROM has the expanded one (null otherwise). */
        fun parse(arm9: ByteArray, synthetic: ByteArray? = null): FoxResult<Marts> {
            val tableEnd = HgssLayout.SPECIAL_MART_POINTERS + 4 * VANILLA_ADDRESSES.size
            if (arm9.size < tableEnd || arm9.size < HgssLayout.BADGE_MART + 4 * HgssLayout.BADGE_MART_ENTRIES) return FoxResult.Err("The ARM9 is too small for the mart tables")
            val specials = ArrayList<List<Int>>()
            val used = ArrayList<IntRange>()
            for (m in VANILLA_ADDRESSES.indices) {
                val pointer = arm9.u32(HgssLayout.SPECIAL_MART_POINTERS + 4 * m).toInt()
                val inSynth = synthetic != null && pointer >= HgssLayout.SYNTHETIC_OVERLAY_ADDRESS && pointer < HgssLayout.SYNTHETIC_OVERLAY_ADDRESS + synthetic.size
                val image = if (inSynth) synthetic!! else arm9
                val start = if (inSynth) pointer - HgssLayout.SYNTHETIC_OVERLAY_ADDRESS else pointer - HgssLayout.ARM9_BASE
                if (!inSynth && start != VANILLA_ADDRESSES[m]) return FoxResult.Err("Special mart $m points to 0x${pointer.toString(16)}, not to where the game keeps it: not supported")
                val items = ArrayList<Int>()
                var at = start
                while (at + 2 <= image.size && image.u16(at) != END) { items += image.u16(at); at += 2 }
                if (at + 2 > image.size) return FoxResult.Err("Special mart $m has no end marker")
                specials += items
                if (inSynth) used += start until at + 2
            }
            val growth = when {
                synthetic == null -> MartGrowth.NEEDS_EXPANSION
                synthetic.size < HgssLayout.MART_AREA + HgssLayout.MART_AREA_SIZE -> MartGrowth.NEEDS_EXPANSION
                (HgssLayout.MART_AREA until HgssLayout.MART_AREA + HgssLayout.MART_AREA_SIZE).any { synthetic[it].toInt() != 0 && used.none { r -> it in r } } -> MartGrowth.AREA_IN_USE
                else -> MartGrowth.AVAILABLE
            }
            val badge = List(HgssLayout.BADGE_MART_ENTRIES) { BadgeMartEntry(arm9.u16(HgssLayout.BADGE_MART + 4 * it), arm9.u16(HgssLayout.BADGE_MART + 4 * it + 2)) }
            return FoxResult.Ok(Marts(specials, badge, growth))
        }

        fun load(project: RomProject): FoxResult<Marts> {
            val arm9 = when (val a = project.readArm9()) {
                is FoxResult.Ok -> a.value
                is FoxResult.Err -> return a
            }
            // The synthetic overlay is only loaded by the game once the ARM9 expansion patch is in.
            val synthetic = if (DsprePatches.arm9Expansion.status(project) == PatchStatus.Applied) (PatchTarget.SyntheticOverlay.read(project) as? FoxResult.Ok)?.value else null
            return parse(arm9, synthetic)
        }
    }
}
