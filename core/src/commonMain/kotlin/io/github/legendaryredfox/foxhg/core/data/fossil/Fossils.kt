package io.github.legendaryredfox.foxhg.core.data.fossil

import io.github.legendaryredfox.foxhg.core.FoxResult
import io.github.legendaryredfox.foxhg.core.project.RomProject
import io.github.legendaryredfox.foxhg.core.putU16
import io.github.legendaryredfox.foxhg.core.u16

/** One fossil item and the species the Pewter City museum scientist revives it into. */
data class Fossil(val item: Int, val species: Int)

/**
 * The fossil table `sFossilPokemonMap` of the script commands `CountFossils` and `GetFossilPokemon` (decomp `scrcmd_fossils.c`): seven pairs of
 * u16 item and u16 species, in overlay 21. The scientist's script (file 755) asks the table which Pokémon the fossil in the bag gives, so the
 * species is the only thing to edit here; the items are the ones the script and the bag know. The table is found by its seven fossil items in a
 * row, so edited species do not hide it.
 */
data class Fossils(val fossils: List<Fossil>) {
    init { require(fossils.size == COUNT) { "There are exactly $COUNT fossils" } }

    /** Writes the species into a copy of [overlay]; the items stay as they are. */
    fun applyTo(overlay: ByteArray): FoxResult<ByteArray> {
        val at = find(overlay) ?: return FoxResult.Err("The fossil table was not found in overlay $OVERLAY (it may be changed by another tool)")
        for ((i, f) in fossils.withIndex()) {
            if (f.item != ITEMS[i]) return FoxResult.Err("Fossil $i must stay item ${ITEMS[i]}")
            if (f.species !in 1..MAX_SPECIES) return FoxResult.Err("Species ${f.species} does not exist (1 to $MAX_SPECIES)")
        }
        val out = overlay.copyOf()
        fossils.forEachIndexed { i, f -> out.putU16(at + 4 * i, f.item); out.putU16(at + 4 * i + 2, f.species) }
        return FoxResult.Ok(out)
    }

    companion object {
        const val COUNT = 7
        const val OVERLAY = 21
        const val MAX_SPECIES = 493

        /** Old Amber, Helix, Dome, Root, Claw, Armor and Skull Fossil: the order of the table in the game. */
        val ITEMS = listOf(103, 101, 102, 99, 100, 104, 105)
        val NAMES = listOf("Old Amber", "Helix Fossil", "Dome Fossil", "Root Fossil", "Claw Fossil", "Armor Fossil", "Skull Fossil")
        val VANILLA = Fossils(listOf(142, 138, 140, 345, 347, 410, 408).mapIndexed { i, s -> Fossil(ITEMS[i], s) })

        /** Where the seven pairs start in [overlay], or null when absent or found twice. */
        internal fun find(overlay: ByteArray): Int? {
            var found = -1
            for (i in 0..overlay.size - 4 * COUNT) {
                if (ITEMS.indices.all { k -> overlay.u16(i + 4 * k) == ITEMS[k] && overlay.u16(i + 4 * k + 2) in 1..MAX_SPECIES }) {
                    if (found >= 0) return null
                    found = i
                }
            }
            return found.takeIf { it >= 0 }
        }

        fun parse(overlay: ByteArray): FoxResult<Fossils> {
            val at = find(overlay) ?: return FoxResult.Err("The fossil table was not found in overlay $OVERLAY (it may be changed by another tool)")
            return FoxResult.Ok(Fossils(List(COUNT) { Fossil(overlay.u16(at + 4 * it), overlay.u16(at + 4 * it + 2)) }))
        }

        fun load(project: RomProject): FoxResult<Fossils> = when (val o = project.readOverlay(OVERLAY)) {
            is FoxResult.Ok -> parse(o.value)
            is FoxResult.Err -> o
        }

        fun save(project: RomProject, fossils: Fossils): FoxResult<Unit> = when (val o = project.readOverlay(OVERLAY)) {
            is FoxResult.Ok -> when (val patched = fossils.applyTo(o.value)) {
                is FoxResult.Ok -> project.writeOverlay(OVERLAY, patched.value)
                is FoxResult.Err -> patched
            }
            is FoxResult.Err -> o
        }
    }
}
