package io.github.legendaryredfox.foxhg.core.game

/** Indexes of the message archives (files of [GameLayout.messages]) that hold the lists editors need names from. */
object TextBanks {
    /** 496 entries, index = national dex number (0 is "-----"). Upper case. */
    const val SPECIES = 237
    /** 468 entries, index = move id. Title case; bank 751 has the same names in upper case. */
    const val MOVES = 750
    /** 537 entries, index = item id. */
    const val ITEMS = 222
    /** 124 entries, index = ability id. */
    const val ABILITIES = 720
    /** 18 entries, index = type id. */
    const val TYPES = 735
    /** 738 entries (9-bit compressed), index = trainer id. */
    const val TRAINER_NAMES = 729
    /** 129 entries, index = trainer class id. */
    const val TRAINER_CLASSES = 730
    /** 235 entries, index = map header name id. */
    const val LOCATIONS = 279
    /** Entry `n` is the name of Battle Tower trainer `n` (same numbers as the files of [GameLayout.battleTowerTrainers]). */
    const val BATTLE_TOWER_NAMES = 27
    /** Entries `3n` to `3n + 2` are the three messages of Battle Tower trainer `n`. */
    const val BATTLE_TOWER_MESSAGES = 724
    /** 1717 entries, the battle messages of the trainers, in the order of [GameLayout.trainerMessageTable]. */
    const val TRAINER_MESSAGES = 728
    /** Entry `n` is the nickname of trade `n`, entry `n + 13` the name of its original trainer. */
    const val TRADE_NAMES = 200

    /** What a bank holds, for the banks the editors know; null for the rest. */
    fun describe(bank: Int): String? = when (bank) {
        SPECIES -> "Species names"
        MOVES -> "Move names"
        ITEMS -> "Item names"
        ABILITIES -> "Ability names"
        TYPES -> "Type names"
        TRAINER_NAMES -> "Trainer names"
        TRAINER_MESSAGES -> "Trainer battle messages"
        TRAINER_CLASSES -> "Trainer class names"
        LOCATIONS -> "Location names"
        else -> null
    }
}
