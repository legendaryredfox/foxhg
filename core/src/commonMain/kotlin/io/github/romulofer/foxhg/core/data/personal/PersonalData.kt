package io.github.romulofer.foxhg.core.data.personal

import io.github.romulofer.foxhg.core.FoxResult
import io.github.romulofer.foxhg.core.putU16
import io.github.romulofer.foxhg.core.putU32
import io.github.romulofer.foxhg.core.u16
import io.github.romulofer.foxhg.core.u32
import io.github.romulofer.foxhg.core.u8

/**
 * One 44-byte record of the HeartGold/SoulSilver personal NARC (`a/0/0/2`), one per species and alternate form.
 * Layout follows the DS Pokémon Hacking wiki and DSPRE's `PokemonPersonalData`; confirmed against the real ROM by
 * `RealRomPersonalTest`.
 *
 * Bytes this editor does not model (the two padding bytes at 26..27) are kept in [padding] so a record that is
 * parsed and written back is identical.
 */
data class PersonalData(
    val baseHp: Int,
    val baseAttack: Int,
    val baseDefense: Int,
    val baseSpeed: Int,
    val baseSpAttack: Int,
    val baseSpDefense: Int,
    val type1: Int,
    val type2: Int,
    val catchRate: Int,
    val baseExp: Int,
    /** EV yield, 2 bits each, in the order HP, Attack, Defense, Speed, Sp.Atk, Sp.Def. */
    val evHp: Int,
    val evAttack: Int,
    val evDefense: Int,
    val evSpeed: Int,
    val evSpAttack: Int,
    val evSpDefense: Int,
    /** Bits 12..15 of the EV word; unused by the game, kept for exactness. */
    val evUnused: Int,
    val heldItemCommon: Int,
    val heldItemRare: Int,
    /** 0..254 = male chance in 1/256 steps; 254 all female, 255 genderless. */
    val genderRatio: Int,
    val hatchCycles: Int,
    val baseFriendship: Int,
    val growthRate: Int,
    val eggGroup1: Int,
    val eggGroup2: Int,
    val ability1: Int,
    val ability2: Int,
    val safariFleeRate: Int,
    /** Low 7 bits of byte 25: dex colour; bit 7: sprite flip. */
    val color: Int,
    val flip: Boolean,
    val padding: Int,
    /** TM01..TM92 then HM01..HM08, bit i of the four little-endian words; the remaining bits are tutor flags. */
    val tmHmWords: List<Long>,
) {
    fun learnsTmHm(index: Int): Boolean = index in 0 until 128 && (tmHmWords[index / 32] ushr (index % 32)) and 1L == 1L

    fun withTmHm(index: Int, learns: Boolean): PersonalData {
        require(index in 0 until 128) { "TM/HM bit $index outside 0..127" }
        val words = tmHmWords.toMutableList()
        val bit = 1L shl (index % 32)
        words[index / 32] = if (learns) words[index / 32] or bit else words[index / 32] and bit.inv()
        return copy(tmHmWords = words)
    }

    val baseStatTotal: Int get() = baseHp + baseAttack + baseDefense + baseSpeed + baseSpAttack + baseSpDefense

    fun toBytes(): ByteArray {
        val b = ByteArray(SIZE)
        b[0] = baseHp.toByte(); b[1] = baseAttack.toByte(); b[2] = baseDefense.toByte()
        b[3] = baseSpeed.toByte(); b[4] = baseSpAttack.toByte(); b[5] = baseSpDefense.toByte()
        b[6] = type1.toByte(); b[7] = type2.toByte()
        b[8] = catchRate.toByte(); b[9] = baseExp.toByte()
        b.putU16(
            10,
            (evHp and 3) or ((evAttack and 3) shl 2) or ((evDefense and 3) shl 4) or ((evSpeed and 3) shl 6) or
                ((evSpAttack and 3) shl 8) or ((evSpDefense and 3) shl 10) or ((evUnused and 0xF) shl 12),
        )
        b.putU16(12, heldItemCommon); b.putU16(14, heldItemRare)
        b[16] = genderRatio.toByte(); b[17] = hatchCycles.toByte(); b[18] = baseFriendship.toByte()
        b[19] = growthRate.toByte(); b[20] = eggGroup1.toByte(); b[21] = eggGroup2.toByte()
        b[22] = ability1.toByte(); b[23] = ability2.toByte(); b[24] = safariFleeRate.toByte()
        b[25] = ((color and 0x7F) or (if (flip) 0x80 else 0)).toByte()
        b.putU16(26, padding)
        for (i in 0 until 4) b.putU32(28 + 4 * i, tmHmWords[i])
        return b
    }

    companion object {
        const val SIZE = 44

        fun parse(bytes: ByteArray): FoxResult<PersonalData> {
            if (bytes.size != SIZE) return FoxResult.Err("Personal data record must be $SIZE bytes, got ${bytes.size}")
            val ev = bytes.u16(10)
            return FoxResult.Ok(
                PersonalData(
                    baseHp = bytes.u8(0), baseAttack = bytes.u8(1), baseDefense = bytes.u8(2),
                    baseSpeed = bytes.u8(3), baseSpAttack = bytes.u8(4), baseSpDefense = bytes.u8(5),
                    type1 = bytes.u8(6), type2 = bytes.u8(7),
                    catchRate = bytes.u8(8), baseExp = bytes.u8(9),
                    evHp = ev and 3, evAttack = (ev shr 2) and 3, evDefense = (ev shr 4) and 3,
                    evSpeed = (ev shr 6) and 3, evSpAttack = (ev shr 8) and 3, evSpDefense = (ev shr 10) and 3,
                    evUnused = ev shr 12,
                    heldItemCommon = bytes.u16(12), heldItemRare = bytes.u16(14),
                    genderRatio = bytes.u8(16), hatchCycles = bytes.u8(17), baseFriendship = bytes.u8(18),
                    growthRate = bytes.u8(19), eggGroup1 = bytes.u8(20), eggGroup2 = bytes.u8(21),
                    ability1 = bytes.u8(22), ability2 = bytes.u8(23), safariFleeRate = bytes.u8(24),
                    color = bytes.u8(25) and 0x7F, flip = bytes.u8(25) and 0x80 != 0,
                    padding = bytes.u16(26),
                    tmHmWords = List(4) { bytes.u32(28 + 4 * it) },
                ),
            )
        }
    }
}
