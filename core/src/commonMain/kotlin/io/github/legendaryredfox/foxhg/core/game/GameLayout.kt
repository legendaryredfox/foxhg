package io.github.legendaryredfox.foxhg.core.game

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
    /** One file per in-game NPC trade (13 in HeartGold); the nickname and trainer name are in [TextBanks.TRADE_NAMES]. */
    const val NPC_TRADES = "a/1/1/2"
    const val MAPS = "a/0/6/5"
    /** The sound archive (SDAT): sequences, banks and waves of music, sound effects and cries. Not a NARC. */
    const val SOUND_ARCHIVE = "data/sound/gs_sound_data.sdat"
    /** File 0 is the palette (16 palettes of 16 colours), file `species + 7` the 32x64 icon (two frames). */
    const val POKEMON_ICONS = "a/0/2/0"
    /** Pairs of an NCGR and an NCLR per item; which files belong to which item is in the generated icon table. */
    const val ITEM_ICONS = "a/0/1/8"
    /** Trainer class front pictures, five files per class (see [io.github.legendaryredfox.foxhg.core.graphics.Icons.trainerClass]). */
    const val TRAINER_FRONT_SPRITES = "a/0/5/8"
    /** Texture archives of the overworld sprites, picked through the sprite table of overlay 1. */
    const val OVERWORLD_SPRITES = "a/0/8/1"
    /** Per area: u16 building texture set, u16 map texture set, u16 dynamic texture type, u8 indoor/outdoor, u8 light. */
    const val AREA_DATA = "a/0/4/2"
    /** Map terrain texture sets (NSBTX), picked by the area data. */
    const val MAP_TEXTURES = "a/0/4/4"
    /** Models (NSBMD with their own textures) of the buildings placed on maps, by `Building.modelId`. */
    const val BUILDING_MODELS = "a/0/4/0"
    /** Battle pictures, six files per species 0..493: back (female, male), front (female, male), palette, shiny palette. Scrambled, see `Icons`. */
    const val POKEMON_SPRITES = "a/0/0/4"
    /** Battle pictures of eggs and alternate forms ("otherpoke"): pairs of back and front pictures, then palettes. */
    const val ALTERNATE_POKEMON_SPRITES = "a/1/1/4"
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

    /** In the decompressed ARM9: the hidden item table (8-byte entries), the byte holding its length and the one holding its capacity. */
    const val HIDDEN_ITEMS_ARM9_TABLE = 0xFA558
    const val HIDDEN_ITEMS_ARM9_LENGTH = 0x405E4
    const val HIDDEN_ITEMS_ARM9_CAPACITY = 0x405E8

    /** In the decompressed ARM9: the fly table, [FLY_TABLE_ENTRIES] entries of 18 bytes (one per town, the order of the Town Map). */
    const val FLY_TABLE_ARM9 = 0xF9E80
    const val FLY_TABLE_ENTRIES = 30

    /**
     * The synthetic overlay: member 0 of this NARC, once the ARM9 expansion patch has run, is a code and data area that the ARM9 loads to
     * [SYNTHETIC_OVERLAY_ADDRESS] at start-up. [SYNTHETIC_OVERLAY_SIZE] bytes after the expansion.
     */
    const val SYNTHETIC_OVERLAY = "a/0/2/8"
    const val SYNTHETIC_OVERLAY_ADDRESS = 0x023C8000
    const val SYNTHETIC_OVERLAY_SIZE = 0x16000

    /** A plain file (not a NARC): the Bug Catching Contest tables, 4 sets of 10 entries of 8 bytes. */
    const val BUG_CONTEST = "data/mushi/mushi_encount.bin"

    /** One file per Battle Tower trainer (315): u16 trainer class, u16 count, then that many u16 ids of the Pokémon sets it draws from. Names and messages are in [TextBanks.BATTLE_TOWER_NAMES] and [TextBanks.BATTLE_TOWER_MESSAGES]. */
    const val BATTLE_TOWER_TRAINERS = "a/2/0/2"

    /** One 16-byte file per Battle Tower Pokémon set (951, set 0 is empty): species, four moves, EV flags, nature, item, form. */
    const val BATTLE_TOWER_SETS = "a/2/0/3"

    /** Eight members of 404 bytes: the total experience needed at levels 0 to 100 (u32 each) for each growth rate, in the personal data's growth rate order. */
    const val GROWTH_RATES = "a/0/0/3"

    /** One member of 494 records of 89 bytes (one per species): two-frame animation data, then movement byte at 1 and, in the last 3 bytes, sprite Y, shadow X and shadow size. */
    const val BATTLE_DISPLAY = "a/1/8/0"

    /** 1976 members of 0 or 1 byte: four per species (female back, male back, female front, male front), the battle sprite height. */
    const val POKEMON_HEIGHTS = "a/0/0/5"

    /** One file per map header: the Headbutt encounters and tree coordinates of that map (4 bytes when the map has no trees). Same in HG and SS. */
    const val HEADBUTT = "a/2/5/2"

    /** One 4-byte file per map header: Rock Smash item drop odds and the item table it draws from. */
    const val ROCK_SMASH = "a/2/5/3"

    /**
     * Overlay 12 (battle, decompressed image) holds the Pickup data: [PICKUP_COMMON_ITEMS] 18 u16 items, [PICKUP_RARE_ITEMS] 11 u16 items,
     * [PICKUP_DIVISOR] one byte (the ability triggers when the roll modulo it is 0) and [PICKUP_WEIGHTS] 9 bytes of cumulative thresholds out of 100.
     */
    const val PICKUP_OVERLAY = 12
    const val PICKUP_COMMON_ITEMS = 0x34B44
    const val PICKUP_RARE_ITEMS = 0x34A4C
    const val PICKUP_DIVISOR = 0xC852
    const val PICKUP_WEIGHTS = 0x3518C

    /** Overlay 1 (field) holds the three Rock Smash item tables, 8 u16 items each, one after the other. */
    const val ROCK_SMASH_ITEMS_OVERLAY = 1
    const val ROCK_SMASH_RUINS_TABLE = 0x23D04
    const val ROCK_SMASH_DEFAULT_TABLE = 0x23D14
    const val ROCK_SMASH_CLIFF_TABLE = 0x23D24

    /** The overlay of the starter chooser; it keeps its own table of the three species (the cries). */
    const val STARTER_CHOOSER_OVERLAY = 61

    /** Text bank of the starter chooser screen (professor's questions and Poké Ball descriptions). */
    const val STARTER_TEXT_BANK = 190

    /** Script files (of [SCRIPTS]) in which the rival's team depends on the player's starter. */
    val RIVAL_SCRIPT_FILES = listOf(7, 23, 96, 110, 819, 850, 866)

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
    /** Entry `n` is the name of Battle Tower trainer `n` (same numbers as the files of [HgssLayout.BATTLE_TOWER_TRAINERS]). */
    const val BATTLE_TOWER_NAMES = 27
    /** Entries `3n` to `3n + 2` are the three messages of Battle Tower trainer `n`. */
    const val BATTLE_TOWER_MESSAGES = 724
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
        TRAINER_CLASSES -> "Trainer class names"
        LOCATIONS -> "Location names"
        else -> null
    }
}
