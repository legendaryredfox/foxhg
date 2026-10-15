package io.github.romulofer.foxhg.core.data.encounter

import io.github.romulofer.foxhg.core.FoxResult
import io.github.romulofer.foxhg.core.data.NarcRecords
import io.github.romulofer.foxhg.core.data.RecordSpec
import io.github.romulofer.foxhg.core.game.HgssLayout
import io.github.romulofer.foxhg.core.project.RomProject
import io.github.romulofer.foxhg.core.putU16
import io.github.romulofer.foxhg.core.u16
import io.github.romulofer.foxhg.core.u8

/** A water, rock-smash or rod slot: level range and species (4 bytes). */
data class EncounterSlot(val levelMin: Int, val levelMax: Int, val species: Int)

/**
 * The wild Pokémon of one area (196 bytes, `a/0/3/7` for HeartGold, `a/1/3/6` for SoulSilver). Layout from the
 * HeartGold decomp's `EncounterData`; confirmed against the real ROM by `RealRomEncounterTest`.
 *
 * Land encounters share one level per slot across times of day; each slot has a morning, day and night species.
 * [swarmLand], [swarmSurf], [nightFish] and [swarmFish] replace a slot of their kind (species ids, 0 = none).
 */
data class EncounterData(
    val rateWalking: Int,
    val rateSurfing: Int,
    val rateRockSmash: Int,
    val rateOldRod: Int,
    val rateGoodRod: Int,
    val rateSuperRod: Int,
    /** Two unused bytes at 0x06, kept for exactness. */
    val dummy: Int,
    val landLevels: List<Int>,
    val landMorning: List<Int>,
    val landDay: List<Int>,
    val landNight: List<Int>,
    val hoennSounds: List<Int>,
    val sinnohSounds: List<Int>,
    val surf: List<EncounterSlot>,
    val rockSmash: List<EncounterSlot>,
    val oldRod: List<EncounterSlot>,
    val goodRod: List<EncounterSlot>,
    val superRod: List<EncounterSlot>,
    val swarmLand: Int,
    val swarmSurf: Int,
    val nightFish: Int,
    val swarmFish: Int,
) {
    fun toBytes(): ByteArray {
        val b = ByteArray(SIZE)
        b[0] = rateWalking.toByte(); b[1] = rateSurfing.toByte(); b[2] = rateRockSmash.toByte()
        b[3] = rateOldRod.toByte(); b[4] = rateGoodRod.toByte(); b[5] = rateSuperRod.toByte()
        b.putU16(6, dummy)
        for (i in 0 until LAND) {
            b[8 + i] = landLevels[i].toByte()
            b.putU16(20 + 2 * i, landMorning[i]); b.putU16(44 + 2 * i, landDay[i]); b.putU16(68 + 2 * i, landNight[i])
        }
        for (i in 0 until 2) { b.putU16(0x5C + 2 * i, hoennSounds[i]); b.putU16(0x60 + 2 * i, sinnohSounds[i]) }
        fun put(slots: List<EncounterSlot>, at: Int) = slots.forEachIndexed { i, s ->
            b[at + 4 * i] = s.levelMin.toByte(); b[at + 4 * i + 1] = s.levelMax.toByte(); b.putU16(at + 4 * i + 2, s.species)
        }
        put(surf, 0x64); put(rockSmash, 0x78); put(oldRod, 0x80); put(goodRod, 0x94); put(superRod, 0xA8)
        b.putU16(0xBC, swarmLand); b.putU16(0xBE, swarmSurf); b.putU16(0xC0, nightFish); b.putU16(0xC2, swarmFish)
        return b
    }

    companion object {
        const val SIZE = 196
        const val LAND = 12

        /** The 12 land slots' chance in percent, in slot order (fixed by the game, not stored in the file). */
        val LAND_SLOT_PERCENT = listOf(20, 20, 10, 10, 10, 10, 5, 5, 4, 4, 1, 1)

        fun parse(bytes: ByteArray): FoxResult<EncounterData> {
            if (bytes.size != SIZE) return FoxResult.Err("Encounter record must be $SIZE bytes, got ${bytes.size}")
            fun slots(at: Int, n: Int) = List(n) { EncounterSlot(bytes.u8(at + 4 * it), bytes.u8(at + 4 * it + 1), bytes.u16(at + 4 * it + 2)) }
            return FoxResult.Ok(
                EncounterData(
                    rateWalking = bytes.u8(0), rateSurfing = bytes.u8(1), rateRockSmash = bytes.u8(2),
                    rateOldRod = bytes.u8(3), rateGoodRod = bytes.u8(4), rateSuperRod = bytes.u8(5),
                    dummy = bytes.u16(6),
                    landLevels = List(LAND) { bytes.u8(8 + it) },
                    landMorning = List(LAND) { bytes.u16(20 + 2 * it) },
                    landDay = List(LAND) { bytes.u16(44 + 2 * it) },
                    landNight = List(LAND) { bytes.u16(68 + 2 * it) },
                    hoennSounds = List(2) { bytes.u16(0x5C + 2 * it) },
                    sinnohSounds = List(2) { bytes.u16(0x60 + 2 * it) },
                    surf = slots(0x64, 5), rockSmash = slots(0x78, 2),
                    oldRod = slots(0x80, 5), goodRod = slots(0x94, 5), superRod = slots(0xA8, 5),
                    swarmLand = bytes.u16(0xBC), swarmSurf = bytes.u16(0xBE), nightFish = bytes.u16(0xC0), swarmFish = bytes.u16(0xC2),
                ),
            )
        }

        val HEARTGOLD = RecordSpec(HgssLayout.ENCOUNTERS_HEARTGOLD, ::parse, EncounterData::toBytes)
        val SOULSILVER = RecordSpec(HgssLayout.ENCOUNTERS_SOULSILVER, ::parse, EncounterData::toBytes)

        fun loadHeartGold(project: RomProject): FoxResult<NarcRecords<EncounterData>> = NarcRecords.load(project, HEARTGOLD)
        fun loadSoulSilver(project: RomProject): FoxResult<NarcRecords<EncounterData>> = NarcRecords.load(project, SOULSILVER)
    }
}
