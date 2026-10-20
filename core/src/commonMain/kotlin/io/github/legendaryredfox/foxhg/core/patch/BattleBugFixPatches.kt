package io.github.legendaryredfox.foxhg.core.patch

/**
 * The bug fixes of the wiki guide "Battle Logic Edits" (HeartGold and SoulSilver, US ROMs): two battle engine fixes in overlay 12 and the
 * Trainer AI fixes in overlay 10. Each one only changes bytes in place, so each is a plain [BytePatch], reversible (the app shows it as a
 * switch); a ROM that holds other bytes there is reported as unavailable.
 */
object BattleBugFixPatches {
    private const val BATTLE = 12
    private const val TRAINER_AI = 10

    private fun fix(id: String, title: String, description: String, vararg edits: ByteEdit) =
        BytePatch(id = id, title = title, description = description, reversible = true, fixedEdits = edits.toList())

    val fireFangWonderGuard = fix(
        "fix-fire-fang-wonder-guard", "Fix: Fire Fang and Wonder Guard",
        "Fire Fang no longer hits through Wonder Guard when the target has no weakness to Fire.",
        edit(PatchTarget.Overlay(BATTLE), 0x20BCC, "11", "10", "the move effect check of Fire Fang (11 becomes 10)"),
    )

    val rageGlitch = fix(
        "fix-rage-glitch", "Fix: Rage glitch",
        "Changing to another move after Rage no longer removes the Pokémon's volatile status conditions.",
        edit(PatchTarget.Overlay(BATTLE), 0x11B3C, "01", "41", "the flag byte that decides which conditions Rage keeps (01 becomes 41)"),
    )

    val aiDrySkin = fix(
        "fix-ai-dry-skin", "Fix: Trainer AI, Water moves against Dry Skin",
        "The basic Trainer AI checks Dry Skin instead of Levitate for its second Water immunity check.",
        edit(PatchTarget.Overlay(TRAINER_AI), 0x4DB4, "1A", "57", "the ability id compared, Levitate (26) becomes Dry Skin (87)"),
    )

    val aiSunnyDay = fix(
        "fix-ai-sunny-day", "Fix: Trainer AI, Sunny Day check",
        "The basic Trainer AI scores Sunny Day for Leaf Guard on a Pokémon that is not statused, not for Hydration on one that is.",
        edit(PatchTarget.Overlay(TRAINER_AI), 0x6310, "5D", "66", "the ability id compared, Hydration becomes Leaf Guard"),
        edit(PatchTarget.Overlay(TRAINER_AI), 0x6318, "09", "0A", "the status check, IfStatus becomes IfStatusNot"),
    )

    val aiForesight = fix(
        "fix-ai-foresight", "Fix: Trainer AI, Foresight and Odor Sleuth",
        "The expert Trainer AI looks at the target's types, not its own, when deciding whether Foresight or Odor Sleuth helps against a Ghost.",
        edit(PatchTarget.Overlay(TRAINER_AI), 0x9DD0, "01", "00", "type 1 loaded from the defender instead of the attacker"),
        edit(PatchTarget.Overlay(TRAINER_AI), 0x9DE4, "03", "02", "type 2 loaded from the defender instead of the attacker"),
    )

    val aiFacade = fix(
        "fix-ai-facade", "Fix: Trainer AI, Facade",
        "The expert Trainer AI rewards Facade when its own Pokémon is statused, not when the opponent is.",
        edit(PatchTarget.Overlay(TRAINER_AI), 0xA7F4, "00", "01", "the battler whose status is checked, the target becomes the user"),
    )

    val aiLeafGuard = fix(
        "fix-ai-leaf-guard", "Fix: Trainer AI, Leaf Guard and Sunny Day",
        "The expert Trainer AI rewards Sunny Day for Leaf Guard when its Pokémon is not statused, not when it is.",
        edit(PatchTarget.Overlay(TRAINER_AI), 0xA198, "09", "0A", "the status check, IfStatus becomes IfStatusNot"),
    )

    val aiWaterSpout = fix(
        "fix-ai-water-spout", "Fix: Trainer AI, Water Spout and Eruption",
        "The expert Trainer AI looks at its own HP, not the target's, when scoring Water Spout and Eruption.",
        edit(PatchTarget.Overlay(TRAINER_AI), 0xB110, "00", "01", "the battler whose HP is checked (faster branch), the target becomes the user"),
        edit(PatchTarget.Overlay(TRAINER_AI), 0xB128, "00", "01", "the battler whose HP is checked (slower branch), the target becomes the user"),
    )

    val aiChargeTurn = fix(
        "fix-ai-charge-turn", "Fix: Trainer AI, Fly and Dig scoring",
        "The expert Trainer AI lowers the score of a charge-turn move the target is immune to, instead of raising it.",
        edit(PatchTarget.Overlay(TRAINER_AI), 0xA710, "01 00 00 00", "FF FF FF FF", "the score change, +1 becomes -1"),
    )

    val aiThunder = fix(
        "fix-ai-thunder", "Fix: Trainer AI, Thunder scoring",
        "The expert Trainer AI reaches its weather routine for Thunder (a duplicated SolarBeam effect id made it unreachable).",
        edit(PatchTarget.Overlay(TRAINER_AI), 0x788C, "97", "98", "the second effect id of the dispatch, SolarBeam becomes Thunder"),
    )

    val aiDischarge = fix(
        "fix-ai-discharge", "Fix: Trainer AI, Discharge in double battles",
        "The Trainer AI checks a Ground partner first, so it no longer refuses Discharge for a Ground and Water or Flying partner that is immune anyway.",
        edit(
            PatchTarget.Overlay(TRAINER_AI), 0xE190,
            "52 00 00 00 03 00 00 00 0B 00 00 00 13 00 00 00 01 00 00 00 93 E4 FF FF 52 00 00 00 03 00 00 00 02 00 00 00 13 00 00 00 01 00 00 00 8D E4 FF FF " +
                "52 00 00 00 03 00 00 00 04 00 00 00 13 00 00 00 01 00 00 00 96 E4 FF FF",
            "52 00 00 00 03 00 00 00 04 00 00 00 13 00 00 00 01 00 00 00 A2 E4 FF FF 52 00 00 00 03 00 00 00 0B 00 00 00 13 00 00 00 01 00 00 00 8D E4 FF FF " +
                "52 00 00 00 03 00 00 00 02 00 00 00 13 00 00 00 01 00 00 00 87 E4 FF FF",
            "the three partner type checks in the order Ground, Water, Flying (they were Water, Flying, Ground)",
        ),
    )

    val all: List<RomPatch> = listOf(fireFangWonderGuard, rageGlitch, aiDrySkin, aiSunnyDay, aiForesight, aiFacade, aiLeafGuard, aiWaterSpout, aiChargeTurn, aiThunder, aiDischarge)
}
