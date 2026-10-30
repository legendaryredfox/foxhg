package io.github.legendaryredfox.foxhg.core.game

/**
 * Indexes of the message archives (files of [GameLayout.messages]) that hold the lists editors need names from. The defaults are the
 * banks of HeartGold and SoulSilver in English; a layout that differs passes its own numbers (`GameLayout.textBanks`).
 */
class TextBanks(
    /** 496 entries, index = national dex number (0 is "-----"). Upper case. */
    val species: Int = 237,
    /** 468 entries, index = move id. Title case; bank 751 has the same names in upper case. */
    val moves: Int = 750,
    /** 537 entries, index = item id. */
    val items: Int = 222,
    /** 124 entries, index = ability id. */
    val abilities: Int = 720,
    /** 18 entries, index = type id. */
    val types: Int = 735,
    /** 738 entries (9-bit compressed), index = trainer id. */
    val trainerNames: Int = 729,
    /** 129 entries, index = trainer class id. */
    val trainerClasses: Int = 730,
    /** 235 entries, index = map header name id. */
    val locations: Int = 279,
    /** Entry `n` is the name of Battle Tower trainer `n` (same numbers as the files of [GameLayout.battleTowerTrainers]). */
    val battleTowerNames: Int = 27,
    /** Entries `3n` to `3n + 2` are the three messages of Battle Tower trainer `n`. */
    val battleTowerMessages: Int = 724,
    /** 1717 entries, the battle messages of the trainers, in the order of [GameLayout.trainerMessageTable]. */
    val trainerMessages: Int = 728,
    /** Entry `n` is the nickname of trade `n`, entry `n + 13` the name of its original trainer. */
    val tradeNames: Int = 200,
) {
    /** What a bank holds, for the banks the editors know; null for the rest. */
    fun describe(bank: Int): String? = when (bank) {
        species -> "Species names"
        moves -> "Move names"
        items -> "Item names"
        abilities -> "Ability names"
        types -> "Type names"
        trainerNames -> "Trainer names"
        trainerMessages -> "Trainer battle messages"
        trainerClasses -> "Trainer class names"
        locations -> "Location names"
        else -> null
    }
}
