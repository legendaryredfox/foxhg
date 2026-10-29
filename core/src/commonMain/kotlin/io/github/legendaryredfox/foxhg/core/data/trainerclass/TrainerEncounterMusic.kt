package io.github.legendaryredfox.foxhg.core.data.trainerclass

import io.github.legendaryredfox.foxhg.core.FoxResult
import io.github.legendaryredfox.foxhg.core.game.HgssLayout
import io.github.legendaryredfox.foxhg.core.project.RomProject
import io.github.legendaryredfox.foxhg.core.u16
import io.github.legendaryredfox.foxhg.core.u32
import io.github.legendaryredfox.foxhg.core.u8

/** One row of the eye-contact music table: the sequence (an SDAT SSEQ number) played for [trainerClass] in region 0 and in region 1. */
data class EncounterMusicRow(val trainerClass: Int, val first: Int, val second: Int)

/**
 * The eye-contact music of each trainer class (see [HgssLayout.ENCOUNTER_MUSIC_POINTER_ARM9]): [HgssLayout.ENCOUNTER_MUSIC_VANILLA_ROWS] rows in the ARM9, or the
 * rows [NewTrainerClass] moved to the synthetic overlay. A class with no row plays sequence 1108.
 */
data class TrainerEncounterMusic(val rows: List<EncounterMusicRow>) {
    fun of(trainerClass: Int): EncounterMusicRow? = rows.firstOrNull { it.trainerClass == trainerClass }

    companion object {
        const val DEFAULT_SEQUENCE = 1108

        fun load(project: RomProject): FoxResult<TrainerEncounterMusic> {
            val arm9 = when (val a = project.readArm9()) { is FoxResult.Ok -> a.value; is FoxResult.Err -> return a }
            if (arm9.size < HgssLayout.ENCOUNTER_MUSIC_POINTER_ARM9 + 4) return FoxResult.Err("ARM9 is too small for the eye-contact music table pointer")
            val pointer = arm9.u32(HgssLayout.ENCOUNTER_MUSIC_POINTER_ARM9)
            val count = arm9.u8(HgssLayout.ENCOUNTER_MUSIC_COUNT_ARM9)
            val (image, at) = if (pointer == HgssLayout.ENCOUNTER_MUSIC_VANILLA_ADDRESS.toLong()) {
                arm9 to (pointer - HgssLayout.ARM9_BASE).toInt()
            } else {
                val synth = TrainerClassTables.syntheticOverlay(project) ?: return FoxResult.Err("The eye-contact music table was moved but the ROM has no synthetic overlay")
                val offset = when (val o = TrainerClassTables.areaOffset(pointer, "eye-contact music", HgssLayout.ENCOUNTER_MUSIC_AREA, count * 6, HgssLayout.ENCOUNTER_MUSIC_AREA + HgssLayout.ENCOUNTER_MUSIC_AREA_SIZE)) { is FoxResult.Ok -> o.value; is FoxResult.Err -> return o }
                synth to offset
            }
            if (at + count * 6 > image.size) return FoxResult.Err("The eye-contact music table is outside its image")
            return FoxResult.Ok(TrainerEncounterMusic(List(count) { EncounterMusicRow(image.u16(at + it * 6), image.u16(at + it * 6 + 2), image.u16(at + it * 6 + 4)) }))
        }
    }
}
