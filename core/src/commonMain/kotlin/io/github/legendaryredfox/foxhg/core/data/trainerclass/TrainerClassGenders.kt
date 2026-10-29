package io.github.legendaryredfox.foxhg.core.data.trainerclass

import io.github.legendaryredfox.foxhg.core.FoxResult
import io.github.legendaryredfox.foxhg.core.game.HgssLayout
import io.github.legendaryredfox.foxhg.core.patch.PatchTarget
import io.github.legendaryredfox.foxhg.core.project.RomProject
import io.github.legendaryredfox.foxhg.core.u32
import io.github.legendaryredfox.foxhg.core.u8

/**
 * Gender of each trainer class: one byte per class in the ARM9 ([HgssLayout.TRAINER_CLASS_GENDER_ARM9_OFFSET]),
 * 0 male, 1 female, 2 neither. The game uses it for pronouns and sprites in battle text. The vanilla table has
 * [COUNT] entries; what follows it in the ARM9 is other data and is never touched. [NewTrainerClass] moves the table to the synthetic overlay.
 */
data class TrainerClassGenders(val genders: List<Int>) {
    fun with(index: Int, gender: Int): TrainerClassGenders = TrainerClassGenders(genders.toMutableList().also { it[index] = gender })

    /** Writes the table into a copy of [arm9]. */
    fun applyTo(arm9: ByteArray): ByteArray = arm9.copyOf().also { out ->
        genders.forEachIndexed { i, g -> out[HgssLayout.TRAINER_CLASS_GENDER_ARM9_OFFSET + i] = g.toByte() }
    }

    companion object {
        const val COUNT = 128
        val NAMES = listOf("Male", "Female", "Neither")

        fun parse(arm9: ByteArray): FoxResult<TrainerClassGenders> {
            val end = HgssLayout.TRAINER_CLASS_GENDER_ARM9_OFFSET + COUNT
            if (arm9.size < end) return FoxResult.Err("ARM9 is too small ($end bytes needed) for the trainer class gender table")
            return FoxResult.Ok(TrainerClassGenders(List(COUNT) { arm9.u8(HgssLayout.TRAINER_CLASS_GENDER_ARM9_OFFSET + it) }))
        }

        /**
         * The table the game reads: the vanilla [COUNT] entries, or one entry for each class after a class was added (the ARM9 word at
         * [HgssLayout.TRAINER_CLASS_GENDER_POINTER_ARM9] then points into the synthetic overlay).
         */
        fun load(project: RomProject): FoxResult<TrainerClassGenders> {
            val arm9 = when (val a = project.readArm9()) { is FoxResult.Ok -> a.value; is FoxResult.Err -> return a }
            val pointer = arm9.pointerWord()
                ?: return FoxResult.Err("ARM9 is too small for the trainer class gender table pointer")
            if (pointer == vanillaAddress) return parse(arm9)
            val count = when (val c = TrainerClassTables.classCount(project)) { is FoxResult.Ok -> c.value; is FoxResult.Err -> return c }
            val at = when (val a = TrainerClassTables.areaOffset(pointer, "trainer class gender", HgssLayout.CLASS_TABLE_AREA, count)) { is FoxResult.Ok -> a.value; is FoxResult.Err -> return a }
            val synth = TrainerClassTables.syntheticOverlay(project) ?: return FoxResult.Err("The gender table was moved but the ROM has no synthetic overlay")
            return FoxResult.Ok(TrainerClassGenders(List(count) { synth.u8(at + it) }))
        }

        fun save(project: RomProject, table: TrainerClassGenders): FoxResult<Unit> {
            val arm9 = when (val a = project.readArm9()) { is FoxResult.Ok -> a.value; is FoxResult.Err -> return a }
            val pointer = arm9.pointerWord()
                ?: return FoxResult.Err("ARM9 is too small for the trainer class gender table pointer")
            if (pointer == vanillaAddress) {
                if (table.genders.size != COUNT) return FoxResult.Err("The gender table has $COUNT entries until a class is added")
                return project.writeArm9(table.applyTo(arm9))
            }
            val at = when (val a = TrainerClassTables.areaOffset(pointer, "trainer class gender", HgssLayout.CLASS_TABLE_AREA, table.genders.size)) { is FoxResult.Ok -> a.value; is FoxResult.Err -> return a }
            val synth = TrainerClassTables.syntheticOverlay(project) ?: return FoxResult.Err("The gender table was moved but the ROM has no synthetic overlay")
            val out = synth.copyOf()
            table.genders.forEachIndexed { i, g -> out[at + i] = g.toByte() }
            return PatchTarget.SyntheticOverlay.write(project, out)
        }

        internal const val vanillaAddress = HgssLayout.ARM9_BASE.toLong() + HgssLayout.TRAINER_CLASS_GENDER_ARM9_OFFSET

        private fun ByteArray.pointerWord(): Long? =
            if (size >= HgssLayout.TRAINER_CLASS_GENDER_POINTER_ARM9 + 4) u32(HgssLayout.TRAINER_CLASS_GENDER_POINTER_ARM9) else null
    }
}
