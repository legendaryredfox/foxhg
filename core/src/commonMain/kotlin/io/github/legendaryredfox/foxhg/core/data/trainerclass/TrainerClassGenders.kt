package io.github.legendaryredfox.foxhg.core.data.trainerclass

import io.github.legendaryredfox.foxhg.core.game.GameLayout
import io.github.legendaryredfox.foxhg.core.FoxResult
import io.github.legendaryredfox.foxhg.core.patch.PatchTarget
import io.github.legendaryredfox.foxhg.core.project.RomProject
import io.github.legendaryredfox.foxhg.core.u32
import io.github.legendaryredfox.foxhg.core.u8

/**
 * Gender of each trainer class: one byte per class in the ARM9 ([GameLayout.trainerClassGenderArm9Offset]),
 * 0 male, 1 female, 2 neither. The game uses it for pronouns and sprites in battle text. The vanilla table has
 * [COUNT] entries; what follows it in the ARM9 is other data and is never touched. [NewTrainerClass] moves the table to the synthetic overlay.
 */
data class TrainerClassGenders(val genders: List<Int>) {
    fun with(index: Int, gender: Int): TrainerClassGenders = TrainerClassGenders(genders.toMutableList().also { it[index] = gender })

    /** Writes the table into a copy of [arm9]. */
    fun applyTo(arm9: ByteArray, layout: GameLayout): ByteArray = arm9.copyOf().also { out ->
        genders.forEachIndexed { i, g -> out[layout.trainerClassGenderArm9Offset + i] = g.toByte() }
    }

    companion object {
        const val COUNT = 128
        val NAMES = listOf("Male", "Female", "Neither")

        fun parse(arm9: ByteArray, layout: GameLayout): FoxResult<TrainerClassGenders> {
            val end = layout.trainerClassGenderArm9Offset + COUNT
            if (arm9.size < end) return FoxResult.Err("ARM9 is too small ($end bytes needed) for the trainer class gender table")
            return FoxResult.Ok(TrainerClassGenders(List(COUNT) { arm9.u8(layout.trainerClassGenderArm9Offset + it) }))
        }

        /**
         * The table the game reads: the vanilla [COUNT] entries, or one entry for each class after a class was added (the ARM9 word at
         * [GameLayout.trainerClassGenderPointerArm9] then points into the synthetic overlay).
         */
        fun load(project: RomProject): FoxResult<TrainerClassGenders> {
            val arm9 = when (val a = project.readArm9()) { is FoxResult.Ok -> a.value; is FoxResult.Err -> return a }
            val pointer = arm9.pointerWord(project)
                ?: return FoxResult.Err("ARM9 is too small for the trainer class gender table pointer")
            if (pointer == vanillaAddress(project)) return parse(arm9, project.layout)
            val count = when (val c = TrainerClassTables.classCount(project)) { is FoxResult.Ok -> c.value; is FoxResult.Err -> return c }
            val at = when (val a = TrainerClassTables.areaOffset(project.layout, pointer, "trainer class gender", project.layout.classTableArea, count)) { is FoxResult.Ok -> a.value; is FoxResult.Err -> return a }
            val synth = TrainerClassTables.syntheticOverlay(project) ?: return FoxResult.Err("The gender table was moved but the ROM has no synthetic overlay")
            return FoxResult.Ok(TrainerClassGenders(List(count) { synth.u8(at + it) }))
        }

        fun save(project: RomProject, table: TrainerClassGenders): FoxResult<Unit> {
            val arm9 = when (val a = project.readArm9()) { is FoxResult.Ok -> a.value; is FoxResult.Err -> return a }
            val pointer = arm9.pointerWord(project)
                ?: return FoxResult.Err("ARM9 is too small for the trainer class gender table pointer")
            if (pointer == vanillaAddress(project)) {
                if (table.genders.size != COUNT) return FoxResult.Err("The gender table has $COUNT entries until a class is added")
                return project.writeArm9(table.applyTo(arm9, project.layout))
            }
            val at = when (val a = TrainerClassTables.areaOffset(project.layout, pointer, "trainer class gender", project.layout.classTableArea, table.genders.size)) { is FoxResult.Ok -> a.value; is FoxResult.Err -> return a }
            val synth = TrainerClassTables.syntheticOverlay(project) ?: return FoxResult.Err("The gender table was moved but the ROM has no synthetic overlay")
            val out = synth.copyOf()
            table.genders.forEachIndexed { i, g -> out[at + i] = g.toByte() }
            return PatchTarget.SyntheticOverlay.write(project, out)
        }

        internal fun vanillaAddress(project: RomProject): Long = project.layout.arm9Base.toLong() + project.layout.trainerClassGenderArm9Offset

        private fun ByteArray.pointerWord(project: RomProject): Long? =
            if (size >= project.layout.trainerClassGenderPointerArm9 + 4) u32(project.layout.trainerClassGenderPointerArm9) else null
    }
}
