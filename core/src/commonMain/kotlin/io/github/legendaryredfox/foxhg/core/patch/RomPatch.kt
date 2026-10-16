package io.github.legendaryredfox.foxhg.core.patch

import io.github.legendaryredfox.foxhg.core.FoxResult
import io.github.legendaryredfox.foxhg.core.project.RomProject

/** Whether a patch can be applied to the open ROM. */
sealed interface PatchStatus {
    data object Available : PatchStatus
    data object Applied : PatchStatus

    /** The patch can not be applied now: [reason] says why (another patch is needed first, or the ROM is not what the patch expects). */
    data class Unavailable(val reason: String) : PatchStatus
}

/**
 * A change to the code or data of the ROM that the toolbox offers as one step. Every patch checks the bytes it
 * replaces before writing, so a ROM that differs from the one it was written for is refused instead of corrupted.
 * Applying goes through the project, so it is journaled (undo) and only reaches the ROM when the project is built.
 */
interface RomPatch {
    val id: String
    val title: String
    val description: String

    /** One line per change, shown before the patch is applied. */
    val changes: List<String>

    /** The same list for the open ROM, for patches whose edits depend on it. */
    fun changesFor(project: RomProject): List<String> = changes

    fun status(project: RomProject): PatchStatus
    fun apply(project: RomProject): FoxResult<Unit>
}
