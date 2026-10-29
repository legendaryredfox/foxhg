package io.github.legendaryredfox.foxhg.core.data.trainerclass

import io.github.legendaryredfox.foxhg.core.FoxResult
import io.github.legendaryredfox.foxhg.core.game.HgssLayout
import io.github.legendaryredfox.foxhg.core.game.TextBanks
import io.github.legendaryredfox.foxhg.core.patch.PatchTarget
import io.github.legendaryredfox.foxhg.core.project.RomProject
import io.github.legendaryredfox.foxhg.core.text.TextArchive

/** Where the trainer class tables are: in their vanilla places, or in [HgssLayout.CLASS_TABLE_AREA] of the synthetic overlay after a class was added. */
internal object TrainerClassTables {
    /** The number of classes: the entries of the class name bank (129 in the vanilla ROM). */
    fun classCount(project: RomProject): FoxResult<Int> = when (val banks = TextArchive.load(project)) {
        is FoxResult.Ok -> banks.value.records.getOrNull(TextBanks.TRAINER_CLASSES)?.entries?.size?.let { FoxResult.Ok(it) }
            ?: FoxResult.Err("The ROM has no trainer class name bank ${TextBanks.TRAINER_CLASSES}")
        is FoxResult.Err -> banks
    }

    /** The synthetic overlay, or null (with no error) when the ROM does not have the expanded one. */
    fun syntheticOverlay(project: RomProject): ByteArray? =
        (PatchTarget.SyntheticOverlay.read(project) as? FoxResult.Ok)?.value?.takeIf { it.size >= HgssLayout.SYNTHETIC_OVERLAY_SIZE }

    /** The offset in the synthetic overlay a table pointer names, or an error when it is somewhere foxhg does not know. */
    fun areaOffset(pointer: Long, what: String, from: Int, size: Int, end: Int = HgssLayout.CLASS_TABLE_AREA + HgssLayout.CLASS_TABLE_AREA_SIZE): FoxResult<Int> {
        val offset = pointer - HgssLayout.SYNTHETIC_OVERLAY_ADDRESS
        return if (offset >= from && offset + size <= end) FoxResult.Ok(offset.toInt())
        else FoxResult.Err("The $what table is at 0x${pointer.toString(16).uppercase()}, not where foxhg or the game put it")
    }
}
