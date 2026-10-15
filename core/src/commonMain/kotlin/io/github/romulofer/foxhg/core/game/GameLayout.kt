package io.github.romulofer.foxhg.core.game

/**
 * Where HeartGold/SoulSilver (English) keep their data. Every editor reads its paths from here, never from a literal.
 *
 * Paths come from the DS Pokémon Hacking wiki "File Structure" table; the ones marked VERIFIED are exercised by a
 * test against the real ROM (see docs/formats.md), the others are still **[UNVERIFIED]** until their editor lands.
 */
object HgssLayout {
    /** VERIFIED: 829 message archives. */
    const val MESSAGES = "a/0/2/7"

    /** VERIFIED present (NARC). */
    const val PERSONAL = "a/0/0/2"
    const val LEARNSETS = "a/0/3/3"
    const val EVOLUTIONS = "a/0/3/4"
    const val EGG_MOVES = "a/2/2/9"
    const val MOVES = "a/0/1/1"
    const val ITEMS = "a/0/1/7"
    const val MATRICES = "a/0/4/1"
    const val SCRIPTS = "a/0/1/2"
    const val EVENTS = "a/0/3/2"
    const val ENCOUNTERS_HEARTGOLD = "a/0/3/7"
    const val ENCOUNTERS_SOULSILVER = "a/1/3/6"
    /** Both HeartGold and SoulSilver ROMs contain both tables; the game loads the one of its own version. */
    const val TRAINER_DATA = "a/0/5/5"
    const val TRAINER_PARTIES = "a/0/5/6"

    /** Offset in the decompressed ARM9 of the TM/HM move list: 92 TMs then 8 HMs, u16 each. */
    const val TM_MOVES_ARM9_OFFSET = 0x1000CC

    /** Offset in the decompressed ARM9 of the trainer class gender table: one byte per class, 128 classes. */
    const val TRAINER_CLASS_GENDER_ARM9_OFFSET = 0xFFB90

    /** Overlay and offset (in its decompressed image) of the trainer class prize multiplier table. */
    const val PRIZE_OVERLAY = 12
    const val PRIZE_TABLE_OVERLAY_OFFSET = 0x34C04
}

/** Indexes of the message archives (files of [HgssLayout.MESSAGES]) that hold the lists editors need names from. */
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

    /** What a bank holds, for the banks the editors know; null for the rest. */
    fun describe(bank: Int): String? = when (bank) {
        SPECIES -> "Species names"
        MOVES -> "Move names"
        ITEMS -> "Item names"
        ABILITIES -> "Ability names"
        TYPES -> "Type names"
        TRAINER_NAMES -> "Trainer names"
        TRAINER_CLASSES -> "Trainer class names"
        LOCATIONS -> "Location names"
        else -> null
    }
}
