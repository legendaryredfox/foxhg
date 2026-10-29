package io.github.legendaryredfox.foxhg.core.data.tmhm

import io.github.legendaryredfox.foxhg.core.game.GameLayout
import io.github.legendaryredfox.foxhg.core.FoxResult
import io.github.legendaryredfox.foxhg.core.project.RomProject
import io.github.legendaryredfox.foxhg.core.putU16
import io.github.legendaryredfox.foxhg.core.u16

/**
 * The move taught by each TM and HM: 100 u16 move ids in the ARM9 ([GameLayout.tmMovesArm9Offset]), TM01-TM92
 * first, then HM01-HM08. Which species can learn them is the TM/HM bit set of the personal data.
 */
data class TmHmTable(val moves: List<Int>) {
    /** "TM01" ... "TM92", "HM01" ... "HM08". */
    fun name(index: Int): String = if (index < TM_COUNT) "TM%02d".format(index + 1) else "HM%02d".format(index - TM_COUNT + 1)

    fun with(index: Int, move: Int): TmHmTable = TmHmTable(moves.toMutableList().also { it[index] = move })

    /** Writes the table into a copy of [arm9]. */
    fun applyTo(arm9: ByteArray, layout: GameLayout): ByteArray = arm9.copyOf().also { out ->
        moves.forEachIndexed { i, m -> out.putU16(layout.tmMovesArm9Offset + i * 2, m) }
    }

    companion object {
        const val TM_COUNT = 92
        const val HM_COUNT = 8
        const val COUNT = TM_COUNT + HM_COUNT

        fun parse(arm9: ByteArray, layout: GameLayout): FoxResult<TmHmTable> {
            val end = layout.tmMovesArm9Offset + COUNT * 2
            if (arm9.size < end) return FoxResult.Err("ARM9 is too small ($end bytes needed) for the TM table")
            return FoxResult.Ok(TmHmTable(List(COUNT) { arm9.u16(layout.tmMovesArm9Offset + it * 2) }))
        }

        fun load(project: RomProject): FoxResult<TmHmTable> = when (val a = project.readArm9()) {
            is FoxResult.Ok -> parse(a.value, project.layout)
            is FoxResult.Err -> a
        }

        fun save(project: RomProject, table: TmHmTable): FoxResult<Unit> = when (val a = project.readArm9()) {
            is FoxResult.Ok -> project.writeArm9(table.applyTo(a.value, project.layout))
            is FoxResult.Err -> a
        }
    }
}
