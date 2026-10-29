package io.github.legendaryredfox.foxhg.core.data.trainerclass

import io.github.legendaryredfox.foxhg.core.game.GameLayout
import io.github.legendaryredfox.foxhg.core.FoxResult
import io.github.legendaryredfox.foxhg.core.patch.PatchTarget
import io.github.legendaryredfox.foxhg.core.project.RomProject
import io.github.legendaryredfox.foxhg.core.putU16
import io.github.legendaryredfox.foxhg.core.u16
import io.github.legendaryredfox.foxhg.core.u32
import io.github.legendaryredfox.foxhg.core.u8

data class PrizeEntry(val trainerClass: Int, val multiplier: Int)

/**
 * Prize money multiplier per trainer class: [COUNT] pairs of u16 `{class id, multiplier}` in overlay
 * [GameLayout.prizeOverlay] at [GameLayout.prizeTableOverlayOffset]. The money for a battle is the multiplier
 * times the level of the last Pokémon times a base amount. Order follows the class ids in the vanilla ROM.
 */
data class TrainerClassPrizes(val entries: List<PrizeEntry>) {
    fun multiplierOf(trainerClass: Int): Int? = entries.firstOrNull { it.trainerClass == trainerClass }?.multiplier

    /** Changes the multiplier of the table row [index] (rows are not in class order in the vanilla ROM). */
    fun withEntry(index: Int, multiplier: Int): TrainerClassPrizes =
        TrainerClassPrizes(entries.toMutableList().also { it[index] = it[index].copy(multiplier = multiplier) })

    /** Writes the table into a copy of [overlay]. */
    fun applyTo(overlay: ByteArray, layout: GameLayout): ByteArray = overlay.copyOf().also { out ->
        entries.forEachIndexed { i, e ->
            out.putU16(layout.prizeTableOverlayOffset + i * 4, e.trainerClass)
            out.putU16(layout.prizeTableOverlayOffset + i * 4 + 2, e.multiplier)
        }
    }

    companion object {
        const val COUNT = 129

        fun parse(overlay: ByteArray, layout: GameLayout): FoxResult<TrainerClassPrizes> {
            val end = layout.prizeTableOverlayOffset + COUNT * 4
            if (overlay.size < end) return FoxResult.Err("Overlay is too small ($end bytes needed) for the prize table")
            return FoxResult.Ok(
                TrainerClassPrizes(
                    List(COUNT) {
                        val p = layout.prizeTableOverlayOffset + it * 4
                        PrizeEntry(overlay.u16(p), overlay.u16(p + 2))
                    },
                ),
            )
        }

        /**
         * The table the game reads: the [COUNT] vanilla rows, or the rows [NewTrainerClass] moved to the synthetic overlay (the two literal pool
         * words in overlay [GameLayout.prizeOverlay] then point into it, and the row count is the immediate of the `cmp` instructions).
         */
        fun load(project: RomProject): FoxResult<TrainerClassPrizes> {
            val overlay = when (val o = project.readOverlay(project.layout.prizeOverlay)) { is FoxResult.Ok -> o.value; is FoxResult.Err -> return o }
            val where = locate(project, overlay)
            return when (where) {
                is FoxResult.Err -> where
                is FoxResult.Ok -> if (where.value == null) parse(overlay, project.layout) else {
                    val count = overlay.u8(project.layout.prizeCountImmediatesOverlay[0])
                    val synth = TrainerClassTables.syntheticOverlay(project) ?: return FoxResult.Err("The prize table was moved but the ROM has no synthetic overlay")
                    val at = where.value!!
                    if (at + count * 4 > synth.size) return FoxResult.Err("The prize table is outside the synthetic overlay")
                    FoxResult.Ok(TrainerClassPrizes(List(count) { PrizeEntry(synth.u16(at + it * 4), synth.u16(at + it * 4 + 2)) }))
                }
            }
        }

        fun save(project: RomProject, table: TrainerClassPrizes): FoxResult<Unit> {
            val overlay = when (val o = project.readOverlay(project.layout.prizeOverlay)) { is FoxResult.Ok -> o.value; is FoxResult.Err -> return o }
            val at = when (val w = locate(project, overlay)) { is FoxResult.Ok -> w.value; is FoxResult.Err -> return w }
                ?: return if (table.entries.size == COUNT) project.writeOverlay(project.layout.prizeOverlay, table.applyTo(overlay, project.layout))
                else FoxResult.Err("The prize table has $COUNT rows until a class is added")
            if (table.entries.size != overlay.u8(project.layout.prizeCountImmediatesOverlay[0])) return FoxResult.Err("The number of prize rows is set by the game code; add a class to change it")
            val synth = TrainerClassTables.syntheticOverlay(project) ?: return FoxResult.Err("The prize table was moved but the ROM has no synthetic overlay")
            val out = synth.copyOf()
            table.entries.forEachIndexed { i, e -> out.putU16(at + i * 4, e.trainerClass); out.putU16(at + i * 4 + 2, e.multiplier) }
            return PatchTarget.SyntheticOverlay.write(project, out)
        }

        /** Null when the pointers are the vanilla ones, else the offset of the table in the synthetic overlay. */
        private fun locate(project: RomProject, overlay: ByteArray): FoxResult<Int?> {
            val entry = project.rom.arm9Overlays.firstOrNull { it.id == project.layout.prizeOverlay } ?: return FoxResult.Err("The ROM has no overlay ${project.layout.prizeOverlay}")
            if (overlay.size < project.layout.prizeTablePointerPlus2Overlay + 4) return FoxResult.Err("Overlay is too small for the prize table pointers")
            val pointer = overlay.u32(project.layout.prizeTablePointerOverlay)
            if (pointer == entry.ramAddress + project.layout.prizeTableOverlayOffset) return FoxResult.Ok(null)
            if (overlay.u32(project.layout.prizeTablePointerPlus2Overlay) != pointer + 2) return FoxResult.Err("The two prize table pointers disagree")
            val at = TrainerClassTables.areaOffset(project.layout, pointer, "trainer class prize", project.layout.classTableAreaPrizes, 4)
            return when (at) { is FoxResult.Ok -> FoxResult.Ok(at.value); is FoxResult.Err -> at }
        }
    }
}
