package io.github.legendaryredfox.foxhg.core.patch

/**
 * HP bars that drain twice as fast in battle, from the wiki guide "Changing the HP bar Speed" (HeartGold and SoulSilver, US ROMs). The bar
 * speed is the THUMB instruction `LSLS R0, R0, #8` (256) at 0x2E17A of overlay 12; `LSLS R0, R0, #9` (512) doubles it. Two bytes, so the patch
 * is a plain [BytePatch], reversible (the app shows it as a switch).
 */
object FastHpBarPatch : BytePatch(
    id = "fast-hp-bar",
    title = "Faster HP bars",
    description = "The HP bar drains twice as fast in battle.",
    reversible = true,
    fixedEdits = listOf(
        edit(PatchTarget.Overlay(12), 0x2E17A, "00 02", "40 02", "the HP bar speed, LSLS R0, R0, #8 becomes LSLS R0, R0, #9 (x2)"),
    ),
)
