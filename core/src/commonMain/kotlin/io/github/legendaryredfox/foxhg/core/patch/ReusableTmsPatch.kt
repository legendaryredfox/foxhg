package io.github.legendaryredfox.foxhg.core.patch

/**
 * TMs that are not used up, from the wiki guide "Enabling Reusable TMs" (HeartGold and SoulSilver, US ROMs). Two conditional THUMB
 * branches become unconditional branches (the condition byte `Dx` becomes `E0`): one in the ARM9 that skips removing the item from the bag
 * after a TM is taught, and one in overlay 15 that skips the "how many are left" count of the TM in the bag. Both are one byte, so the
 * patch is a plain [BytePatch], reversible (the app shows it as a switch); a ROM that holds other bytes there is reported as unavailable.
 */
object ReusableTmsPatch : BytePatch(
    id = "reusable-tms",
    title = "Reusable TMs",
    description = "TMs are not used up when taught, and the bag no longer shows a count for them.",
    reversible = true,
    fixedEdits = listOf(
        edit(PatchTarget.Arm9, 0x825A7, "D1", "E0", "the branch that removes a TM from the bag after use becomes unconditional"),
        edit(PatchTarget.Overlay(15), 0x6239, "D3", "E0", "the branch that draws the TM count in the bag becomes unconditional"),
    ),
)
