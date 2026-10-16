package io.github.legendaryredfox.foxhg.core.save

/** One chunk of a save block: what the game keeps there ([name] from `save_arrays.h`), how many bytes it uses ([size]) and where it starts in the block ([offset]). */
data class SaveChunk(val id: Int, val name: String, val size: Int, val offset: Int, val block: BlockKind) {
    /** Bytes the chunk takes in the block: [size] rounded up to 4, and 4 more where its own CRC would be. */
    val stride: Int get() = ((size + 3) and 3.inv()) + 4

    /** True for the chunks the game keeps a CRC-16 of (at the end of the rounded-up data): it must be recomputed when the chunk changes. */
    val hasOwnCrc: Boolean get() = id in WITH_OWN_CRC

    /** Where the chunk's own CRC is, relative to the block. */
    val crcOffset: Int get() = offset + ((size + 3) and 3.inv())

    companion object {
        /** The chunks whose CRC the game maintains (`SaveSubstruct_UpdateCRC`): Wi-Fi history, Mystery Gift, Easy Chat and three more, found on the real saves. */
        val WITH_OWN_CRC = setOf(23, 24, 26, 27, 30, 31)
    }
}

/**
 * The 42 chunks of a HeartGold/SoulSilver save, in order: 41 in the general block and the PC storage alone in the storage block. The sizes are
 * those of the game's own `*_sizeof` functions, read from the ARM9 table `gSaveChunkHeaders` (ARM9 0xF64C4, 16 bytes each: id, block, size
 * function, init function) and checked on the real saves: they add up to exactly the blocks (0xF618 and 0x12300 bytes of data), and the six chunks
 * that keep their own CRC have it where these offsets put it. The names are the decomp's; the `UNK_` ones are not decoded.
 */
object SaveChunks {
    private val SPEC = listOf(
        "SYSINFO" to 0x5C, "PLAYERDATA" to 0x2C, "PARTY" to 0x5B0, "BAG" to 0x79C, "FLAGS" to 0x44C, "LOCAL_FIELD_DATA" to 0x80,
        "POKEDEX" to 0x340, "DAYCARE" to 0x1E0, "PALPAD" to 0x880, "MISC" to 0x2E0, "MAP_OBJECTS" to 0x1400, "LINK_BATTLE_RULESET" to 0x20,
        "DRESSUP_DATA" to 0x834, "MAILBOX" to 0x460, "FRIEND_GROUP" to 0x108, "TRAINER_CARD" to 0x620, "GAMESTATS" to 0x1C0, "SEAL_CASE" to 0x170,
        "CHATOT" to 0x3EC, "UNK_19" to 0x1628, "SPECIAL_RIBBONS" to 0xE, "ROAMER" to 0x68, "UNK_22" to 0xF8, "UNK_23" to 0xBC8, "RANKINGS" to 0xEA0,
        "UNK_25" to 0x8C0, "WIFI_HISTORY" to 0xFF8, "MYSTERY_GIFT" to 0x1680, "UNK_28" to 0x688, "POKEATHLON_FRIENDSHIP_RECORDS" to 0x28,
        "EASY_CHAT" to 0x8, "UNK_31" to 0x40, "UNK_32" to 0x8, "FOLLOW_MON" to 0x8, "POKEGEAR" to 0x658, "SAFARI_ZONE" to 0x5FC, "PHOTO_ALBUM" to 0x1294,
        "POKEATHLON" to 0xB80, "APRICORN_BOX" to 0x80, "POKEWALKER" to 0x134, "TRAINER_HOUSE" to 0xF00, "PCSTORAGE" to 0x122FC,
    )

    val all: List<SaveChunk> = run {
        var offset = 0
        SPEC.mapIndexed { id, (name, size) ->
            val block = if (id == PCSTORAGE) BlockKind.STORAGE else BlockKind.GENERAL
            if (id == PCSTORAGE) offset = 0
            SaveChunk(id, name, size, offset, block).also { offset += it.stride }
        }
    }

    const val SYSINFO = 0
    const val PLAYERDATA = 1
    const val PARTY = 2
    const val BAG = 3
    const val FLAGS = 4
    const val LOCAL_FIELD_DATA = 5
    const val POKEDEX = 6
    const val PCSTORAGE = 41

    fun byId(id: Int): SaveChunk = all[id]
    fun byName(name: String): SaveChunk = all.first { it.name == name }
    fun of(kind: BlockKind): List<SaveChunk> = all.filter { it.block == kind }
}
