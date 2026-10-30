package io.github.legendaryredfox.foxhg.core.game

/**
 * Where one game and language keep their data: file paths, offsets in the ARM9 and the overlays, table sizes. Every editor reads
 * these from `project.layout`, never from a literal. [HgssEnglishLayout] holds the values of HeartGold and SoulSilver in English;
 * a language or game is another implementation (a class that overrides only what differs).
 *
 * Paths come from the DS Pokémon Hacking wiki "File Structure" table; the ones marked VERIFIED are exercised by a
 * test against the real ROM (see docs/formats.md), the others are still **[UNVERIFIED]** until their editor lands.
 */
interface GameLayout {
    /** VERIFIED: 829 message archives. */
    val messages: String

    /** VERIFIED present (NARC). */
    val personal: String

    val learnsets: String

    val evolutions: String

    val eggMoves: String

    val moves: String

    val items: String

    /** One file per in-game NPC trade (13 in HeartGold); the nickname and trainer name are in [TextBanks.tradeNames]. */
    val npcTrades: String

    val maps: String

    /** One 16 byte file per photo location the `TakePhoto` script command can use; the album icons are in `a/1/7/1`. */
    val photoLocations: String

    /** The sound archive (SDAT): sequences, banks and waves of music, sound effects and cries. Not a NARC. */
    val soundArchive: String

    /** File 0 is the palette (16 palettes of 16 colours), file `species + 7` the 32x64 icon (two frames). */
    val pokemonIcons: String

    /** Pairs of an NCGR and an NCLR per item; which files belong to which item is in the generated icon table. */
    val itemIcons: String

    /** Trainer class front pictures, five files per class (see [io.github.legendaryredfox.foxhg.core.graphics.Icons.trainerClass]). */
    val trainerFrontSprites: String

    /** Texture archives of the overworld sprites, picked through the sprite table of overlay 1. */
    val overworldSprites: String

    /** Per area: u16 building texture set, u16 map texture set, u16 dynamic texture type, u8 indoor/outdoor, u8 light. */
    val areaData: String

    /** Map terrain texture sets (NSBTX), picked by the area data. */
    val mapTextures: String

    /** Models (NSBMD with their own textures) of the buildings placed on maps, by `Building.modelId`. */
    val buildingModels: String

    /** Battle pictures, six files per species 0..493: back (female, male), front (female, male), palette, shiny palette. Scrambled, see `Icons`. */
    val pokemonSprites: String

    /** Battle pictures of eggs and alternate forms ("otherpoke"): pairs of back and front pictures, then palettes. */
    val alternatePokemonSprites: String

    val matrices: String

    val scripts: String

    val events: String

    val encountersHeartgold: String

    val encountersSoulsilver: String

    /** Both HeartGold and SoulSilver ROMs contain both tables; the game loads the one of its own version. */
    val trainerData: String

    val trainerParties: String

    /** One-file NARC of `u16 trainer, u16 trigger` pairs, one for each battle message; entry `n` goes with message `n` of [TextBanks.trainerMessages]. */
    val trainerMessageTable: String

    /** One-file NARC of one `u16` for each trainer: the byte offset in [trainerMessageTable] of the trainer's first message (0 for a trainer with none). */
    val trainerMessageOffsets: String

    /** Offset in the decompressed ARM9 of the TM/HM move list: 92 TMs then 8 HMs, u16 each. */
    val tmMovesArm9Offset: Int

    /** Offset in the decompressed ARM9 of the trainer class gender table: one byte per class, 128 classes. */
    val trainerClassGenderArm9Offset: Int

    /** In the decompressed ARM9: the hidden item table (8-byte entries), the byte holding its length and the one holding its capacity. */
    val hiddenItemsArm9Table: Int

    val hiddenItemsArm9Length: Int

    val hiddenItemsArm9Capacity: Int

    /** In the decompressed ARM9: the fly table, [flyTableEntries] entries of 18 bytes (one per town, the order of the Town Map). */
    val flyTableArm9: Int

    val flyTableEntries: Int

    /** The PokeGear map (Town Map) tables live in overlay 101: [townMapSpots] visited-town entries of 14 bytes, [townMapSelections] selection entries of 16 bytes. */
    val townMapOverlay: Int

    val townMapSpotsOffset: Int

    val townMapSpots: Int

    val townMapSelectionsOffset: Int

    val townMapSelections: Int

    /**
     * The synthetic overlay: member 0 of this NARC, once the ARM9 expansion patch has run, is a code and data area that the ARM9 loads to
     * [syntheticOverlayAddress] at start-up. [syntheticOverlaySize] bytes after the expansion.
     */
    val syntheticOverlay: String

    val syntheticOverlayAddress: Int

    val syntheticOverlaySize: Int

    /** A plain file (not a NARC): the Bug Catching Contest tables, 4 sets of 10 entries of 8 bytes. */
    val bugContest: String

    /** One file per Battle Tower trainer (315): u16 trainer class, u16 count, then that many u16 ids of the Pokémon sets it draws from. Names and messages are in [TextBanks.battleTowerNames] and [TextBanks.battleTowerMessages]. */
    val battleTowerTrainers: String

    /** One 16-byte file per Battle Tower Pokémon set (951, set 0 is empty): species, four moves, EV flags, nature, item, form. */
    val battleTowerSets: String

    /** Eight members of 404 bytes: the total experience needed at levels 0 to 100 (u32 each) for each growth rate, in the personal data's growth rate order. */
    val growthRates: String

    /** The ARM9 is loaded at this address, so a pointer into its data minus this is an offset in the decompressed ARM9. */
    val arm9Base: Int

    /**
     * In the decompressed ARM9: the table of [specialMarts] u32 pointers (to lists of u16 item numbers ended by 0xFFFF) that the
     * `SpMartScreen n` script command picks from, and the badge Poké Mart: [badgeMartEntries] entries of u16 item, u16 tier.
     */
    val specialMartPointers: Int

    val specialMarts: Int

    val badgeMart: Int

    val badgeMartEntries: Int

    /**
     * Where foxhg puts a special mart that outgrows its place in the ARM9: a block of the synthetic overlay (offsets in it are runtime
     * address - [syntheticOverlayAddress]), after the script command table copy at 0x200 and before the camera routine at 0x115B0.
     * It must be empty (zeros) or hold only mart lists foxhg wrote.
     */
    val martArea: Int

    val martAreaSize: Int

    /**
     * Overlay 12 (battle, decompressed image): the type effectiveness table, [typeChartSlots] slots of 3 bytes (attacker type, defender type,
     * multiplier times 10) holding the pairs and the Foresight divider, then one terminator slot; the data after it is something else.
     */
    val typeChartOverlay: Int

    val typeChartOffset: Int

    val typeChartSlots: Int

    /** One member of 494 records of 89 bytes (one per species): two-frame animation data, then movement byte at 1 and, in the last 3 bytes, sprite Y, shadow X and shadow size. */
    val battleDisplay: String

    /** 1976 members of 0 or 1 byte: four per species (female back, male back, female front, male front), the battle sprite height. */
    val pokemonHeights: String

    /** One file per map header: the Headbutt encounters and tree coordinates of that map (4 bytes when the map has no trees). Same in HG and SS. */
    val headbutt: String

    /** Twelve files, one per Safari Zone area type: the Pokémon each kind of ground and water draws, and the objects the player places to change them. */
    val safariZone: String

    /** One 4-byte file per map header: Rock Smash item drop odds and the item table it draws from. */
    val rockSmash: String

    /**
     * Overlay 12 (battle, decompressed image) holds the Pickup data: [pickupCommonItems] 18 u16 items, [pickupRareItems] 11 u16 items,
     * [pickupDivisor] one byte (the ability triggers when the roll modulo it is 0) and [pickupWeights] 9 bytes of cumulative thresholds out of 100.
     */
    val pickupOverlay: Int

    val pickupCommonItems: Int

    val pickupRareItems: Int

    val pickupDivisor: Int

    val pickupWeights: Int

    /** Overlay 1 (field) holds the three Rock Smash item tables, 8 u16 items each, one after the other. */
    val rockSmashItemsOverlay: Int

    val rockSmashRuinsTable: Int

    val rockSmashDefaultTable: Int

    val rockSmashCliffTable: Int

    /** The overlay of the starter chooser; it keeps its own table of the three species (the cries). */
    val starterChooserOverlay: Int

    /** Text bank of the starter chooser screen (professor's questions and Poké Ball descriptions). */
    val starterTextBank: Int

    /** Script files (of [scripts]) in which the rival's team depends on the player's starter. */
    val rivalScriptFiles: List<Int>

    /** Overlay and offset (in its decompressed image) of the trainer class prize multiplier table. */
    val prizeOverlay: Int

    val prizeTableOverlayOffset: Int

    /**
     * What a new trainer class needs moved (HeartGold English). The gender table is read through one literal pool word in the ARM9 (vanilla
     * 0x020FFB90, [trainerClassGenderArm9Offset]) with no bound. The prize table is searched by class id in `CalcPrizeMoney` (overlay
     * [prizeOverlay]): two literal pool words (the table and the table + 2) and three `cmp r4, #count` (THUMB, vanilla `81 2C`) whose
     * immediate is the row count. A class that is not found gets the prize of table row 2.
     */
    val trainerClassGenderPointerArm9: Int

    val prizeTablePointerOverlay: Int

    val prizeTablePointerPlus2Overlay: Int

    val prizeCountImmediatesOverlay: List<Int>

    /** The battle code keeps the class in a byte and the count in an 8 bit immediate, so there are at most 255 classes. */
    val maxTrainerClasses: Int

    /**
     * Where foxhg puts the gender table (one byte per class, [maxTrainerClasses] + 1 reserved) and, after it, the prize table (4 bytes a
     * row) once a class is added: a block of the synthetic overlay between the marts ([martArea]) and the camera routine. It must be empty
     * (zeros) or hold only those tables.
     */
    val classTableArea: Int

    val classTableAreaPrizes: Int

    val classTableAreaSize: Int

    /**
     * The eye-contact music table (`sTrainerEncounterMusicParam`, decompressed ARM9): [encounterMusicVanillaRows] rows of three u16 (class, the sequence of
     * region 0, the sequence of region 1) at [encounterMusicVanillaAddress]; `FieldBGM_GetEyesMeetForTrainer` searches it by class through the literal
     * pool word at [encounterMusicPointerArm9] with `cmp r3, #rows` (immediate at [encounterMusicCountArm9], opcode byte `2B` after it). A class that is
     * not in it plays the sequence 1108 (the pool word before the pointer). Moved to [encounterMusicArea] when a class is added with its own music.
     */
    val encounterMusicPointerArm9: Int

    val encounterMusicCountArm9: Int

    val encounterMusicVanillaAddress: Int

    val encounterMusicVanillaRows: Int

    val encounterMusicArea: Int

    val encounterMusicAreaSize: Int

    /** Front pictures: [trainerFrontSprites] has this many files per class. */
    val filesPerTrainerClassPicture: Int

    /** The numbers of the text banks the editors read names from (species, moves, items, trainers, ...). */
    val textBanks: TextBanks
}
