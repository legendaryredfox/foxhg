package io.github.legendaryredfox.foxhg.core.data.trainerclass

import io.github.legendaryredfox.foxhg.core.FoxResult
import io.github.legendaryredfox.foxhg.core.game.HgssLayout
import io.github.legendaryredfox.foxhg.core.patch.PatchTarget
import io.github.legendaryredfox.foxhg.core.project.RomProject
import io.github.legendaryredfox.foxhg.core.putU16
import io.github.legendaryredfox.foxhg.core.putU32
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

    /** The table with the row of [row].trainerClass replaced, or the row added at the end when that class has none. */
    fun with(row: EncounterMusicRow): TrainerEncounterMusic {
        val at = rows.indexOfFirst { it.trainerClass == row.trainerClass }
        return TrainerEncounterMusic(if (at < 0) rows + row else rows.toMutableList().also { it[at] = row })
    }

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

        /**
         * Writes [table]. The vanilla [HgssLayout.ENCOUNTER_MUSIC_VANILLA_ROWS] rows are written in place; any other count (or a table that already moved) lives in
         * [HgssLayout.ENCOUNTER_MUSIC_AREA] of the synthetic overlay (the ARM9 expansion patch must be applied), with the ARM9 pointer and the `cmp r3, #rows` immediate set.
         */
        fun save(project: RomProject, table: TrainerEncounterMusic): FoxResult<Unit> {
            val rows = table.rows
            if (rows.isEmpty() || rows.size > HgssLayout.MAX_TRAINER_CLASSES) return FoxResult.Err("The eye-contact music table has 1 to ${HgssLayout.MAX_TRAINER_CLASSES} rows")
            if (rows.any { it.trainerClass !in 0..0xFF || it.first !in 0..0xFFFF || it.second !in 0..0xFFFF }) return FoxResult.Err("A class is a byte and a song a 16 bit sequence number")
            val arm9 = when (val a = project.readArm9()) { is FoxResult.Ok -> a.value; is FoxResult.Err -> return a }
            if (arm9.size < HgssLayout.ENCOUNTER_MUSIC_POINTER_ARM9 + 4) return FoxResult.Err("ARM9 is too small for the eye-contact music table pointer")
            if (arm9.u8(HgssLayout.ENCOUNTER_MUSIC_COUNT_ARM9 + 1) != 0x2B) return FoxResult.Err("ARM9 0x${HgssLayout.ENCOUNTER_MUSIC_COUNT_ARM9.toString(16)} is not the `cmp r3, #count` the music search uses")
            val vanilla = arm9.u32(HgssLayout.ENCOUNTER_MUSIC_POINTER_ARM9) == HgssLayout.ENCOUNTER_MUSIC_VANILLA_ADDRESS.toLong()
            fun put(image: ByteArray, at: Int) = rows.forEachIndexed { i, r -> image.putU16(at + i * 6, r.trainerClass); image.putU16(at + i * 6 + 2, r.first); image.putU16(at + i * 6 + 4, r.second) }
            if (vanilla && rows.size == HgssLayout.ENCOUNTER_MUSIC_VANILLA_ROWS) {
                return project.writeArm9(arm9.copyOf().also { put(it, HgssLayout.ENCOUNTER_MUSIC_VANILLA_ADDRESS - HgssLayout.ARM9_BASE) })
            }
            val synth = TrainerClassTables.syntheticOverlay(project) ?: return FoxResult.Err("A table with another row count needs the synthetic overlay: apply the ARM9 expansion patch in the ROM toolbox first")
            val area = HgssLayout.ENCOUNTER_MUSIC_AREA
            if (vanilla && (area until area + HgssLayout.ENCOUNTER_MUSIC_AREA_SIZE).any { synth[it] != 0.toByte() }) {
                return FoxResult.Err("The synthetic overlay is not empty at 0x${area.toString(16)}: something else is there")
            }
            if (!vanilla) {
                val pointer = arm9.u32(HgssLayout.ENCOUNTER_MUSIC_POINTER_ARM9)
                if (pointer != (HgssLayout.SYNTHETIC_OVERLAY_ADDRESS + area).toLong()) return FoxResult.Err("The eye-contact music table is at 0x${pointer.toString(16).uppercase()}, not where foxhg put it")
            }
            val out = synth.copyOf()
            put(out, area)
            val newArm9 = arm9.copyOf()
            newArm9.putU32(HgssLayout.ENCOUNTER_MUSIC_POINTER_ARM9, (HgssLayout.SYNTHETIC_OVERLAY_ADDRESS + area).toLong())
            newArm9[HgssLayout.ENCOUNTER_MUSIC_COUNT_ARM9] = rows.size.toByte()
            (project.writeArm9(newArm9) as? FoxResult.Err)?.let { return it }
            return PatchTarget.SyntheticOverlay.write(project, out)
        }
    }
}
