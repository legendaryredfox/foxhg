package io.github.legendaryredfox.foxhg.core.data.trainerclass

import io.github.legendaryredfox.foxhg.core.game.GameLayout
import io.github.legendaryredfox.foxhg.core.FoxResult
import io.github.legendaryredfox.foxhg.core.patch.PatchTarget
import io.github.legendaryredfox.foxhg.core.project.RomProject
import io.github.legendaryredfox.foxhg.core.text.TextArchive

/** Where the trainer class tables are: in their vanilla places, or in [GameLayout.classTableArea] of the synthetic overlay after a class was added. */
internal object TrainerClassTables {
    /** The number of classes: the entries of the class name bank (129 in the vanilla ROM). */
    fun classCount(project: RomProject): FoxResult<Int> = when (val banks = TextArchive.load(project)) {
        is FoxResult.Ok -> banks.value.records.getOrNull(project.layout.textBanks.trainerClasses)?.entries?.size?.let { FoxResult.Ok(it) }
            ?: FoxResult.Err("The ROM has no trainer class name bank ${project.layout.textBanks.trainerClasses}")
        is FoxResult.Err -> banks
    }

    /** The synthetic overlay, or null (with no error) when the ROM does not have the expanded one. */
    fun syntheticOverlay(project: RomProject): ByteArray? =
        (PatchTarget.SyntheticOverlay.read(project) as? FoxResult.Ok)?.value?.takeIf { it.size >= project.layout.syntheticOverlaySize }

    /** The offset in the synthetic overlay a table pointer names, or an error when it is somewhere foxhg does not know. */
    fun areaOffset(layout: GameLayout, pointer: Long, what: String, from: Int, size: Int, end: Int = layout.classTableArea + layout.classTableAreaSize): FoxResult<Int> {
        val offset = pointer - layout.syntheticOverlayAddress
        return if (offset >= from && offset + size <= end) FoxResult.Ok(offset.toInt())
        else FoxResult.Err("The $what table is at 0x${pointer.toString(16).uppercase()}, not where foxhg or the game put it")
    }
}
