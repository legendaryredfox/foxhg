package io.github.legendaryredfox.foxhg.core.save

import io.github.legendaryredfox.foxhg.core.FoxResult
import io.github.legendaryredfox.foxhg.core.putU16
import io.github.legendaryredfox.foxhg.core.putU32
import io.github.legendaryredfox.foxhg.core.text.Charmap

/**
 * A save written from scratch ([create]): one sound slot (slot 1, counter 1, as the game's first save), the other slot blank, every chunk zero except
 * the ones the game cannot start without: the player, an empty party, a new game's Pokédex, empty PC boxes with their default names and wallpapers, the place the player stands
 * at and the event flags that make the start menu show the Bag, Trainer Card, Save and Options entries. The chunks left at zero are those the game's
 * own init also leaves empty or fills with values it recomputes (see formats.md "New save" for what was checked).
 */
object NewSave {
    /** Event flags a new game sets in the first minutes, so the start menu has its buttons: from the decomp's `FLAG_GOT_*`. */
    val START_MENU_FLAGS = listOf(0x11B, 0x11C, 0x11D, 0x11E)

    const val HEARTGOLD = 7
    const val SOULSILVER = 8
    const val ENGLISH = 2

    fun create(name: String, gender: Int, place: Location, version: Int = HEARTGOLD, language: Int = ENGLISH, trainerId: Long = 0x00010000L, money: Long = 3000): FoxResult<SaveFile> {
        val file = ByteArray(SaveFormat.FILE_SIZE) { 0xFF.toByte() }
        val base = SaveFormat.SLOT_OFFSETS[1]
        for (kind in BlockKind.entries) {
            val offset = base + kind.offset
            file.fill(0, offset, offset + kind.size - BlockFooter.SIZE)
            val footer = offset + kind.size - BlockFooter.SIZE
            file.putU32(footer, 1); file.putU32(footer + 4, kind.size.toLong()); file.putU32(footer + 8, BlockFooter.MAGIC)
            file.putU16(footer + 12, kind.index)
        }
        val raw = (SaveFile.parse(file) as FoxResult.Ok).value
        val general = raw.block(1, BlockKind.GENERAL)
        val storage = raw.block(1, BlockKind.STORAGE)

        val player = when (val r = PlayerData.parse(ByteArray(PlayerData.SIZE)).let { (it as FoxResult.Ok).value.copy(trainerId = trainerId, money = money, gender = gender, language = language, version = version).withName(name) }) {
            is FoxResult.Ok -> r.value
            is FoxResult.Err -> return r
        }
        val party = PartyData(PartyData.SLOTS, 0, List(PartyData.SLOTS) { Pokemon.EMPTY }, ByteArray(PartyData.EXTRA))
        val names = List(PcStorageData.BOXES) { box ->
            val codes = (Charmap.HGSS.encode("BOX ${box + 1}") as FoxResult.Ok).value
            List(PcStorageData.NAME_SLOTS) { if (it < codes.size) codes[it] else if (it == codes.size) PcStorageData.END else 0 }
        }
        val pc = PcStorageData(
            List(PcStorageData.BOXES) { List(PcStorageData.PER_BOX) { Pokemon.EMPTY } }, List(PcStorageData.BOXES) { ByteArray(PcStorageData.TAIL) }, 0, 0, names,
            List(PcStorageData.BOXES) { it % 16 }, 0, ByteArray(PcStorageData.FILLER),
        )
        val none = Location(0, -1, 0, 0, 0)
        val field = LocalFieldData(place, place, place, place, none, 0, 0, 0, 0, 0, 1, 0, 0, 0, 0, 0, List(LocalFieldData.FILLER) { 0 })
        var flags = ByteArray(SaveChunks.byId(SaveChunks.FLAGS).size)
        for (flag in START_MENU_FLAGS + SaveVarsFlags.CONTINUE_AT_DYNAMIC_WARP) flags = SaveVarsFlags.with(flags, flag, true)

        return FoxResult.Ok(
            raw.withChunk(general, SaveChunks.byId(SaveChunks.PLAYERDATA), player.toBytes())
                .withChunk(general, SaveChunks.byId(SaveChunks.PARTY), party.toBytes())
                .withChunk(general, SaveChunks.byId(SaveChunks.LOCAL_FIELD_DATA), field.toBytes())
                .withChunk(general, SaveChunks.byId(SaveChunks.FLAGS), flags)
                .withChunk(general, SaveChunks.byId(SaveChunks.POKEDEX), PokedexData.fresh().toBytes())
                .withChunk(storage, SaveChunks.byId(SaveChunks.PCSTORAGE), pc.toBytes()),
        )
    }
}
