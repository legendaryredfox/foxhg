package io.github.romulofer.foxhg.core.data.rocksmash

import io.github.romulofer.foxhg.core.FoxResult
import io.github.romulofer.foxhg.core.data.RecordSpec
import io.github.romulofer.foxhg.core.game.HgssLayout
import io.github.romulofer.foxhg.core.project.RomProject
import io.github.romulofer.foxhg.core.putU16
import io.github.romulofer.foxhg.core.u16

/**
 * The Rock Smash data of one map header (member N of `a/2/5/3` is map header N): [odds] is the chance in percent
 * (0 to 100) that smashing a rock without a wild encounter drops an item, 0 turns drops off; [type] picks one of the
 * three item tables ([TABLE_NAMES]). Layout from the DS Pokémon Hacking wiki's Rock Smash guide and DSPRE.
 */
data class RockSmashSite(val odds: Int, val type: Int) {
    fun toBytes(): ByteArray = ByteArray(SIZE).also { it.putU16(0, odds); it.putU16(2, type) }

    companion object {
        const val SIZE = 4
        const val DEFAULT = 0
        const val RUINS_OF_ALPH = 1
        const val CLIFF_CAVE = 2
        val TABLE_NAMES = listOf("Default", "Ruins of Alph", "Cliff Cave")

        fun parse(bytes: ByteArray): FoxResult<RockSmashSite> =
            if (bytes.size != SIZE) FoxResult.Err("A Rock Smash record is $SIZE bytes, got ${bytes.size}")
            else FoxResult.Ok(RockSmashSite(bytes.u16(0), bytes.u16(2)))

        val SPEC = RecordSpec(HgssLayout.ROCK_SMASH, ::parse, RockSmashSite::toBytes)
    }
}

/**
 * The three Rock Smash item tables of overlay 1: 8 items (u16) each, picked by the slot a roll lands on ([SLOT_CHANCE]).
 * They are shared by every map that uses the table. [tables] is indexed like [RockSmashSite.type].
 */
data class RockSmashItems(val tables: List<List<Int>>) {
    init { require(tables.size == 3 && tables.all { it.size == SLOTS }) { "Three tables of $SLOTS items" } }

    fun with(table: Int, slot: Int, item: Int): RockSmashItems =
        RockSmashItems(tables.mapIndexed { t, items -> if (t == table) items.toMutableList().also { it[slot] = item } else items })

    /** Writes the tables into a copy of [overlay]. */
    fun applyTo(overlay: ByteArray): ByteArray = overlay.copyOf().also { out ->
        tables.forEachIndexed { t, items -> items.forEachIndexed { slot, item -> out.putU16(OFFSETS[t] + 2 * slot, item) } }
    }

    companion object {
        const val SLOTS = 8

        /** Chance in percent of each slot before an ability shifts it (Serene Grace, Super Luck). */
        val SLOT_CHANCE = listOf(25, 20, 10, 10, 10, 10, 10, 5)

        /** Overlay offsets by table type: default, Ruins of Alph, Cliff Cave. */
        private val OFFSETS = listOf(HgssLayout.ROCK_SMASH_DEFAULT_TABLE, HgssLayout.ROCK_SMASH_RUINS_TABLE, HgssLayout.ROCK_SMASH_CLIFF_TABLE)

        fun parse(overlay: ByteArray): FoxResult<RockSmashItems> {
            val end = OFFSETS.max() + 2 * SLOTS
            if (overlay.size < end) return FoxResult.Err("Overlay ${HgssLayout.ROCK_SMASH_ITEMS_OVERLAY} is too small for the Rock Smash item tables")
            return FoxResult.Ok(RockSmashItems(OFFSETS.map { at -> List(SLOTS) { overlay.u16(at + 2 * it) } }))
        }

        fun load(project: RomProject): FoxResult<RockSmashItems> = when (val o = project.readOverlay(HgssLayout.ROCK_SMASH_ITEMS_OVERLAY)) {
            is FoxResult.Ok -> parse(o.value)
            is FoxResult.Err -> o
        }

        fun save(project: RomProject, items: RockSmashItems): FoxResult<Unit> = when (val o = project.readOverlay(HgssLayout.ROCK_SMASH_ITEMS_OVERLAY)) {
            is FoxResult.Ok -> project.writeOverlay(HgssLayout.ROCK_SMASH_ITEMS_OVERLAY, items.applyTo(o.value))
            is FoxResult.Err -> o
        }
    }
}
