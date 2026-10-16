package io.github.legendaryredfox.foxhg.core.save

import io.github.legendaryredfox.foxhg.core.FoxResult
import io.github.legendaryredfox.foxhg.core.putU16
import io.github.legendaryredfox.foxhg.core.putU32
import io.github.legendaryredfox.foxhg.core.text.Charmap
import io.github.legendaryredfox.foxhg.core.u16
import io.github.legendaryredfox.foxhg.core.u32
import io.github.legendaryredfox.foxhg.core.u8

/**
 * The `PLAYERDATA` chunk (0x2C bytes) of the save: the options word, the player's profile and the coins and play time. Layout from the decomp's
 * `player_data.h`, `options.h` and `igt.h`; the name, money, play time, language and version were checked on real saves (the shop screen showed the
 * money, the Continue screen the name and the time).
 *
 * [nameCodes] are the 8 text codes of the name (up to 7 characters then 0xFFFF); [optionsWord] holds text speed (bits 0 to 3), sound (4 to 5),
 * battle style (6), battle scene (7), button mode (8 to 9) and frame (10 to 14). [flags] is the byte with the game clear (bit 0) and National Dex
 * (bit 1) bits. Bytes the game does not use ([optionsPadding], [dummy], [tail]) are kept so an unedited chunk writes back unchanged.
 */
data class PlayerData(
    val optionsWord: Int,
    val optionsPadding: Int,
    val nameCodes: List<Int>,
    val trainerId: Long,
    val money: Long,
    val gender: Int,
    val language: Int,
    val johtoBadges: Int,
    val avatar: Int,
    val version: Int,
    val flags: Int,
    val dummy: Int,
    val kantoBadges: Int,
    val coins: Int,
    val hours: Int,
    val minutes: Int,
    val seconds: Int,
    val tail: Int,
) {
    init { require(nameCodes.size == NAME_SLOTS) { "the name has $NAME_SLOTS slots" } }

    /** The player's name as text; the codes up to the end mark. */
    val name: String get() = Charmap.HGSS.decode(nameCodes.takeWhile { it != END }.toIntArray())

    val gameClear: Boolean get() = flags and 1 != 0
    val nationalDex: Boolean get() = flags and 2 != 0
    val badgeCount: Int get() = Integer.bitCount(johtoBadges and 0xFF) + Integer.bitCount(kantoBadges and 0xFF)

    /** The visible half of the trainer ID, the one the Trainer Card shows. */
    val visibleId: Int get() = (trainerId and 0xFFFF).toInt()

    /** A copy with [text] as the name: at most [MAX_NAME] characters the game's text table has, followed by the end mark and zeros as the game's own init leaves them. */
    fun withName(text: String): FoxResult<PlayerData> {
        val codes = when (val r = Charmap.HGSS.encode(text)) {
            is FoxResult.Ok -> r.value
            is FoxResult.Err -> return r
        }
        if (codes.isEmpty()) return FoxResult.Err("The name cannot be empty")
        if (codes.size > MAX_NAME) return FoxResult.Err("A player's name has at most $MAX_NAME characters")
        return FoxResult.Ok(copy(nameCodes = List(NAME_SLOTS) { if (it < codes.size) codes[it] else if (it == codes.size) END else 0 }))
    }

    fun toBytes(): ByteArray = ByteArray(SIZE).also { b ->
        b.putU16(0, optionsWord); b.putU16(2, optionsPadding)
        nameCodes.forEachIndexed { i, c -> b.putU16(4 + 2 * i, c) }
        b.putU32(0x14, trainerId); b.putU32(0x18, money)
        b[0x1C] = gender.toByte(); b[0x1D] = language.toByte(); b[0x1E] = johtoBadges.toByte(); b[0x1F] = avatar.toByte()
        b[0x20] = version.toByte(); b[0x21] = flags.toByte(); b[0x22] = dummy.toByte(); b[0x23] = kantoBadges.toByte()
        b.putU16(0x24, coins); b.putU16(0x26, hours); b[0x28] = minutes.toByte(); b[0x29] = seconds.toByte(); b.putU16(0x2A, tail)
    }

    companion object {
        const val SIZE = 0x2C
        const val NAME_SLOTS = 8
        const val MAX_NAME = 7
        const val END = 0xFFFF
        const val MAX_MONEY = 999_999L

        val GENDERS = listOf("Boy", "Girl")

        fun parse(b: ByteArray): FoxResult<PlayerData> {
            if (b.size != SIZE) return FoxResult.Err("The player data is $SIZE bytes, got ${b.size}")
            return FoxResult.Ok(
                PlayerData(
                    optionsWord = b.u16(0), optionsPadding = b.u16(2), nameCodes = List(NAME_SLOTS) { b.u16(4 + 2 * it) },
                    trainerId = b.u32(0x14), money = b.u32(0x18), gender = b.u8(0x1C), language = b.u8(0x1D), johtoBadges = b.u8(0x1E), avatar = b.u8(0x1F),
                    version = b.u8(0x20), flags = b.u8(0x21), dummy = b.u8(0x22), kantoBadges = b.u8(0x23),
                    coins = b.u16(0x24), hours = b.u16(0x26), minutes = b.u8(0x28), seconds = b.u8(0x29), tail = b.u16(0x2A),
                ),
            )
        }

        /** The player data of the save's current general block. */
        fun read(save: SaveFile): FoxResult<PlayerData> {
            val block = save.current(BlockKind.GENERAL) ?: return FoxResult.Err("The save has no sound general block")
            return parse(save.chunk(block, SaveChunks.byId(SaveChunks.PLAYERDATA)))
        }
    }
}

/** A copy of the save with [player] written into the current general block (its checksums recomputed), or an error when there is no sound block or [player] is out of range. */
fun SaveFile.withPlayer(player: PlayerData): FoxResult<SaveFile> {
    val block = current(BlockKind.GENERAL) ?: return FoxResult.Err("The save has no sound general block")
    if (player.money !in 0..PlayerData.MAX_MONEY) return FoxResult.Err("Money is 0 to ${PlayerData.MAX_MONEY}")
    if (player.minutes !in 0..59 || player.seconds !in 0..59 || player.hours !in 0..999) return FoxResult.Err("The play time is up to 999 hours, minutes and seconds up to 59")
    return FoxResult.Ok(withChunk(block, SaveChunks.byId(SaveChunks.PLAYERDATA), player.toBytes()))
}
