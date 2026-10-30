package io.github.legendaryredfox.foxhg.core.game

/**
 * The values of Pokémon HeartGold and SoulSilver in English, every one of them `open` so the layout of another language is
 * an object that extends this class and overrides only what differs (see [GameLayout]).
 */
open class HgssLayoutBase : GameLayout {
    override val messages: String = "a/0/2/7"
    override val personal: String = "a/0/0/2"
    override val learnsets: String = "a/0/3/3"
    override val evolutions: String = "a/0/3/4"
    override val eggMoves: String = "a/2/2/9"
    override val moves: String = "a/0/1/1"
    override val items: String = "a/0/1/7"
    override val npcTrades: String = "a/1/1/2"
    override val maps: String = "a/0/6/5"
    override val photoLocations: String = "a/2/5/4"
    override val soundArchive: String = "data/sound/gs_sound_data.sdat"
    override val pokemonIcons: String = "a/0/2/0"
    override val itemIcons: String = "a/0/1/8"
    override val trainerFrontSprites: String = "a/0/5/8"
    override val overworldSprites: String = "a/0/8/1"
    override val areaData: String = "a/0/4/2"
    override val mapTextures: String = "a/0/4/4"
    override val buildingModels: String = "a/0/4/0"
    override val pokemonSprites: String = "a/0/0/4"
    override val alternatePokemonSprites: String = "a/1/1/4"
    override val matrices: String = "a/0/4/1"
    override val scripts: String = "a/0/1/2"
    override val events: String = "a/0/3/2"
    override val encountersHeartgold: String = "a/0/3/7"
    override val encountersSoulsilver: String = "a/1/3/6"
    override val trainerData: String = "a/0/5/5"
    override val trainerParties: String = "a/0/5/6"
    override val trainerMessageTable: String = "a/0/5/7"
    override val trainerMessageOffsets: String = "a/1/3/1"
    override val tmMovesArm9Offset: Int = 0x1000CC
    override val trainerClassGenderArm9Offset: Int = 0xFFB90
    override val hiddenItemsArm9Table: Int = 0xFA558
    override val hiddenItemsArm9Length: Int = 0x405E4
    override val hiddenItemsArm9Capacity: Int = 0x405E8
    override val flyTableArm9: Int = 0xF9E80
    override val flyTableEntries: Int = 30
    override val townMapOverlay: Int = 101
    override val townMapSpotsOffset: Int = 0x10274
    override val townMapSpots: Int = 27
    override val townMapSelectionsOffset: Int = 0xFC32
    override val townMapSelections: Int = 100
    override val syntheticOverlay: String = "a/0/2/8"
    override val syntheticOverlayAddress: Int = 0x023C8000
    override val syntheticOverlaySize: Int = 0x16000
    override val bugContest: String = "data/mushi/mushi_encount.bin"
    override val battleTowerTrainers: String = "a/2/0/2"
    override val battleTowerSets: String = "a/2/0/3"
    override val growthRates: String = "a/0/0/3"
    override val arm9Base: Int = 0x02000000
    override val specialMartPointers: Int = 0x10FA3C
    override val specialMarts: Int = 30
    override val badgeMart: Int = 0xFBF22
    override val badgeMartEntries: Int = 19
    override val martArea: Int = 0x4000
    override val martAreaSize: Int = 0x1000
    override val typeChartOverlay: Int = 12
    override val typeChartOffset: Int = 0x353BC
    override val typeChartSlots: Int = 111
    override val battleDisplay: String = "a/1/8/0"
    override val pokemonHeights: String = "a/0/0/5"
    override val headbutt: String = "a/2/5/2"
    override val safariZone: String = "a/2/3/0"
    override val rockSmash: String = "a/2/5/3"
    override val pickupOverlay: Int = 12
    override val pickupCommonItems: Int = 0x34B44
    override val pickupRareItems: Int = 0x34A4C
    override val pickupDivisor: Int = 0xC852
    override val pickupWeights: Int = 0x3518C
    override val rockSmashItemsOverlay: Int = 1
    override val rockSmashRuinsTable: Int = 0x23D04
    override val rockSmashDefaultTable: Int = 0x23D14
    override val rockSmashCliffTable: Int = 0x23D24
    override val starterChooserOverlay: Int = 61
    override val starterTextBank: Int = 190
    override val rivalScriptFiles: List<Int> = listOf(7, 23, 96, 110, 819, 850, 866)
    override val prizeOverlay: Int = 12
    override val prizeTableOverlayOffset: Int = 0x34C04
    override val trainerClassGenderPointerArm9: Int = 0x73600
    override val prizeTablePointerOverlay: Int = 0x8380
    override val prizeTablePointerPlus2Overlay: Int = 0x8384
    override val prizeCountImmediatesOverlay: List<Int> = listOf(0x8310, 0x8314, 0x831C)
    override val maxTrainerClasses: Int = 255
    override val classTableArea: Int = 0x5000
    override val classTableAreaPrizes: Int = classTableArea + 0x100
    override val classTableAreaSize: Int = 0x500
    override val encounterMusicPointerArm9: Int = 0x550E0
    override val encounterMusicCountArm9: Int = 0x550D4
    override val encounterMusicVanillaAddress: Int = 0x020FC61C
    override val encounterMusicVanillaRows: Int = 44
    override val encounterMusicArea: Int = classTableArea + classTableAreaSize
    override val encounterMusicAreaSize: Int = 0x600
    override val filesPerTrainerClassPicture: Int = 5
    override val textBanks: TextBanks = TextBanks()
}

/** HeartGold and SoulSilver, English: the layout every editor is written and verified against. */
object HgssEnglishLayout : HgssLayoutBase()

/** The layout for a ROM's game and language, or null when foxhg has none (such a ROM opens read-only, see [io.github.legendaryredfox.foxhg.core.nds.GameProfile.editable]). */
object GameLayouts {
    fun of(profile: io.github.legendaryredfox.foxhg.core.nds.GameProfile): GameLayout? = if (profile.editable) HgssEnglishLayout else null
}
