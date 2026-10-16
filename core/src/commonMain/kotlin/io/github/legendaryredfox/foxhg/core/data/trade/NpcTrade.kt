package io.github.legendaryredfox.foxhg.core.data.trade

import io.github.legendaryredfox.foxhg.core.FoxResult
import io.github.legendaryredfox.foxhg.core.data.NarcRecords
import io.github.legendaryredfox.foxhg.core.data.RecordSpec
import io.github.legendaryredfox.foxhg.core.game.HgssLayout
import io.github.legendaryredfox.foxhg.core.project.RomProject
import io.github.legendaryredfox.foxhg.core.putU32
import io.github.legendaryredfox.foxhg.core.u32

/**
 * One in-game NPC trade (`a/1/1/2`, one file per trade), layout from the HeartGold decomp's `NPCTrade`: 21 little-endian
 * 32-bit values (0x54 bytes). [giveSpecies] is the Pokémon the NPC hands over, [askSpecies] the one the player must offer.
 * [pid] and [otId] are unsigned 32-bit values. [unknown] is the last word, unused by the game's code and kept as it is.
 */
data class NpcTrade(
    val giveSpecies: Int,
    val hpIv: Int,
    val atkIv: Int,
    val defIv: Int,
    val speedIv: Int,
    val spAtkIv: Int,
    val spDefIv: Int,
    val ability: Int,
    val otId: Long,
    val cool: Int,
    val beauty: Int,
    val cute: Int,
    val smart: Int,
    val tough: Int,
    val pid: Long,
    val heldItem: Int,
    /** Gender of the original trainer: 0 male, 1 female. */
    val otGender: Int,
    val sheen: Int,
    val language: Int,
    val askSpecies: Int,
    val unknown: Int,
) {
    fun toBytes(): ByteArray {
        val b = ByteArray(SIZE)
        val words = listOf(
            giveSpecies.toLong(), hpIv.toLong(), atkIv.toLong(), defIv.toLong(), speedIv.toLong(), spAtkIv.toLong(),
            spDefIv.toLong(), ability.toLong(), otId, cool.toLong(), beauty.toLong(), cute.toLong(), smart.toLong(),
            tough.toLong(), pid, heldItem.toLong(), otGender.toLong(), sheen.toLong(), language.toLong(),
            askSpecies.toLong(), unknown.toLong(),
        )
        words.forEachIndexed { i, v -> b.putU32(i * 4, v and 0xFFFFFFFFL) }
        return b
    }

    companion object {
        const val SIZE = 0x54

        val GENDERS = listOf("Male", "Female")

        fun parse(bytes: ByteArray): FoxResult<NpcTrade> {
            if (bytes.size != SIZE) return FoxResult.Err("Trade record must be $SIZE bytes, got ${bytes.size}")
            fun w(i: Int) = bytes.u32(i * 4)
            fun i(i: Int) = w(i).toInt()
            return FoxResult.Ok(
                NpcTrade(
                    giveSpecies = i(0), hpIv = i(1), atkIv = i(2), defIv = i(3), speedIv = i(4), spAtkIv = i(5),
                    spDefIv = i(6), ability = i(7), otId = w(8), cool = i(9), beauty = i(10), cute = i(11),
                    smart = i(12), tough = i(13), pid = w(14), heldItem = i(15), otGender = i(16), sheen = i(17),
                    language = i(18), askSpecies = i(19), unknown = i(20),
                ),
            )
        }

        val SPEC = RecordSpec(HgssLayout.NPC_TRADES, ::parse, NpcTrade::toBytes)

        fun load(project: RomProject): FoxResult<NarcRecords<NpcTrade>> = NarcRecords.load(project, SPEC)
    }
}
