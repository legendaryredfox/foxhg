package io.github.legendaryredfox.foxhg.core.data.trainerclass

import io.github.legendaryredfox.foxhg.core.FoxResult
import io.github.legendaryredfox.foxhg.core.game.HgssLayout
import io.github.legendaryredfox.foxhg.core.project.RomProject
import io.github.legendaryredfox.foxhg.core.putU16
import io.github.legendaryredfox.foxhg.core.u16

data class PrizeEntry(val trainerClass: Int, val multiplier: Int)

/**
 * Prize money multiplier per trainer class: [COUNT] pairs of u16 `{class id, multiplier}` in overlay
 * [HgssLayout.PRIZE_OVERLAY] at [HgssLayout.PRIZE_TABLE_OVERLAY_OFFSET]. The money for a battle is the multiplier
 * times the level of the last Pokémon times a base amount. Order follows the class ids in the vanilla ROM.
 */
data class TrainerClassPrizes(val entries: List<PrizeEntry>) {
    fun multiplierOf(trainerClass: Int): Int? = entries.firstOrNull { it.trainerClass == trainerClass }?.multiplier

    /** Changes the multiplier of the table row [index] (rows are not in class order in the vanilla ROM). */
    fun withEntry(index: Int, multiplier: Int): TrainerClassPrizes =
        TrainerClassPrizes(entries.toMutableList().also { it[index] = it[index].copy(multiplier = multiplier) })

    /** Writes the table into a copy of [overlay]. */
    fun applyTo(overlay: ByteArray): ByteArray = overlay.copyOf().also { out ->
        entries.forEachIndexed { i, e ->
            out.putU16(HgssLayout.PRIZE_TABLE_OVERLAY_OFFSET + i * 4, e.trainerClass)
            out.putU16(HgssLayout.PRIZE_TABLE_OVERLAY_OFFSET + i * 4 + 2, e.multiplier)
        }
    }

    companion object {
        const val COUNT = 129

        fun parse(overlay: ByteArray): FoxResult<TrainerClassPrizes> {
            val end = HgssLayout.PRIZE_TABLE_OVERLAY_OFFSET + COUNT * 4
            if (overlay.size < end) return FoxResult.Err("Overlay is too small ($end bytes needed) for the prize table")
            return FoxResult.Ok(
                TrainerClassPrizes(
                    List(COUNT) {
                        val p = HgssLayout.PRIZE_TABLE_OVERLAY_OFFSET + it * 4
                        PrizeEntry(overlay.u16(p), overlay.u16(p + 2))
                    },
                ),
            )
        }

        fun load(project: RomProject): FoxResult<TrainerClassPrizes> = when (val o = project.readOverlay(HgssLayout.PRIZE_OVERLAY)) {
            is FoxResult.Ok -> parse(o.value)
            is FoxResult.Err -> o
        }

        fun save(project: RomProject, table: TrainerClassPrizes): FoxResult<Unit> = when (val o = project.readOverlay(HgssLayout.PRIZE_OVERLAY)) {
            is FoxResult.Ok -> project.writeOverlay(HgssLayout.PRIZE_OVERLAY, table.applyTo(o.value))
            is FoxResult.Err -> o
        }
    }
}
