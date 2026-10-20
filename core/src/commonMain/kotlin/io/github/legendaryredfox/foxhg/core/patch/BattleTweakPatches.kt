package io.github.legendaryredfox.foxhg.core.patch

/**
 * Battle rule changes from the wiki guide "Battle Logic Edits" (HeartGold and SoulSilver, US ROMs) that are fixed code edits and not a number to
 * pick, so they are switches and not entries of the Battle rules editor (the paralysis Speed cut is one of those, a number): all in overlay 12, all in place, so plain [BytePatch]es, reversible.
 */
object BattleTweakPatches {
    private const val BATTLE = 12

    private fun tweak(id: String, title: String, description: String, vararg edits: ByteEdit) =
        BytePatch(id = id, title = title, description = description, reversible = true, fixedEdits = edits.toList())

    val noHailDamage = tweak(
        "no-hail-damage", "No Hail damage",
        "Hail no longer hurts any Pokémon at the end of the turn.",
        edit(PatchTarget.Overlay(BATTLE), 0xA406, "16 D0", "16 E0", "the first Ice type check becomes an unconditional branch past the damage"),
    )

    val disableFixedTurns = tweak(
        "disable-fixed-turns", "Disable lasts a fixed time",
        "Disable always has a base of 4 turns (5 counting the turn it is used) and no random part, so a slower user still gets 4 turns of effect.",
        edit(PatchTarget.Overlay(BATTLE), 0x90DA, "20 40", "04 20", "the random part of the duration (ANDS R0, R4) becomes a fixed 4 (MOVS R0, #4)"),
    )

    val trainersCanTrick = tweak(
        "trainers-can-trick", "Trainers can use Trick and Switcheroo",
        "Trainers in ordinary battles can swap items; wild Pokémon still fail. Items swapped stay swapped after the battle.",
        edit(PatchTarget.Overlay(BATTLE), 0xB1DA, "84", "85", "the battle type mask, link or frontier, also trainer"),
    )

    val all: List<RomPatch> = listOf(noHailDamage, disableFixedTurns, trainersCanTrick)
}
