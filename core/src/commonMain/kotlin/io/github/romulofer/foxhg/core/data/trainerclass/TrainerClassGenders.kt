package io.github.romulofer.foxhg.core.data.trainerclass

import io.github.romulofer.foxhg.core.FoxResult
import io.github.romulofer.foxhg.core.game.HgssLayout
import io.github.romulofer.foxhg.core.project.RomProject
import io.github.romulofer.foxhg.core.u8

/**
 * Gender of each trainer class: one byte per class in the ARM9 ([HgssLayout.TRAINER_CLASS_GENDER_ARM9_OFFSET]),
 * 0 male, 1 female, 2 neither. The game uses it for pronouns and sprites in battle text. The vanilla table has
 * [COUNT] entries; what follows it in the ARM9 is other data and is never touched.
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

        fun load(project: RomProject): FoxResult<TrainerClassGenders> = when (val a = project.readArm9()) {
            is FoxResult.Ok -> parse(a.value)
            is FoxResult.Err -> a
        }

        fun save(project: RomProject, table: TrainerClassGenders): FoxResult<Unit> = when (val a = project.readArm9()) {
            is FoxResult.Ok -> project.writeArm9(table.applyTo(a.value))
            is FoxResult.Err -> a
        }
    }
}
