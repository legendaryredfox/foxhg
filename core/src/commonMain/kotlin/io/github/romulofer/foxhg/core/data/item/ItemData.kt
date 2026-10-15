package io.github.romulofer.foxhg.core.data.item

import io.github.romulofer.foxhg.core.FoxResult
import io.github.romulofer.foxhg.core.data.NarcRecords
import io.github.romulofer.foxhg.core.data.RecordSpec
import io.github.romulofer.foxhg.core.data.csv.CsvColumn
import io.github.romulofer.foxhg.core.data.csv.RecordCsv
import io.github.romulofer.foxhg.core.game.HgssLayout
import io.github.romulofer.foxhg.core.project.RomProject
import io.github.romulofer.foxhg.core.putU16
import io.github.romulofer.foxhg.core.u16
import io.github.romulofer.foxhg.core.u8

/**
 * One 34-byte item record (`a/0/1/7`), layout from the HeartGold decomp's `ItemData`: price (u16), hold effect and
 * parameter, Pluck/Fling data, Natural Gift power, a u16 of packed bits (type 0-4, prevent toss 5, selectable 6,
 * field pocket 7-10, battle pocket 11-15), field/battle/party use, then 20 bytes of party-use parameters that are kept
 * raw ([partyParams]): their meaning depends on the item's effect.
 */
data class ItemData(
    val price: Int,
    val holdEffect: Int,
    val holdEffectParam: Int,
    val pluckEffect: Int,
    val flingEffect: Int,
    val flingPower: Int,
    val naturalGiftPower: Int,
    /** Type index; 31 means none. */
    val naturalGiftType: Int,
    val preventToss: Boolean,
    val selectable: Boolean,
    val fieldPocket: Int,
    /** Bit set, see [BATTLE_POCKETS]. */
    val battlePocket: Int,
    val fieldUseFunc: Int,
    val battleUseFunc: Int,
    val partyUse: Int,
    val partyParams: List<Int>,
) {
    fun toBytes(): ByteArray {
        val b = ByteArray(SIZE)
        b.putU16(0, price)
        b[2] = holdEffect.toByte(); b[3] = holdEffectParam.toByte(); b[4] = pluckEffect.toByte()
        b[5] = flingEffect.toByte(); b[6] = flingPower.toByte(); b[7] = naturalGiftPower.toByte()
        val bits = (naturalGiftType and 0x1F) or
            ((if (preventToss) 1 else 0) shl 5) or ((if (selectable) 1 else 0) shl 6) or
            ((fieldPocket and 0xF) shl 7) or ((battlePocket and 0x1F) shl 11)
        b.putU16(8, bits)
        b[10] = fieldUseFunc.toByte(); b[11] = battleUseFunc.toByte(); b[12] = partyUse.toByte()
        partyParams.forEachIndexed { i, v -> b[PARTY_OFFSET + i] = v.toByte() }
        return b
    }

    companion object {
        const val SIZE = 34
        private const val PARTY_OFFSET = 14
        const val PARTY_PARAM_SIZE = SIZE - PARTY_OFFSET

        val FIELD_POCKETS = listOf("Items", "Medicine", "Poké Balls", "TMs & HMs", "Berries", "Mail", "Battle items", "Key items")
        val BATTLE_POCKETS = listOf("Poké Balls", "Battle items", "HP restore", "Status healers", "PP restore")
        val BATTLE_USE = listOf("None", "Ball", "Healing", "Escape")

        fun parse(bytes: ByteArray): FoxResult<ItemData> {
            if (bytes.size != SIZE) return FoxResult.Err("Item record must be $SIZE bytes, got ${bytes.size}")
            val bits = bytes.u16(8)
            return FoxResult.Ok(
                ItemData(
                    price = bytes.u16(0), holdEffect = bytes.u8(2), holdEffectParam = bytes.u8(3),
                    pluckEffect = bytes.u8(4), flingEffect = bytes.u8(5), flingPower = bytes.u8(6),
                    naturalGiftPower = bytes.u8(7), naturalGiftType = bits and 0x1F,
                    preventToss = bits shr 5 and 1 == 1, selectable = bits shr 6 and 1 == 1,
                    fieldPocket = bits shr 7 and 0xF, battlePocket = bits shr 11 and 0x1F,
                    fieldUseFunc = bytes.u8(10), battleUseFunc = bytes.u8(11), partyUse = bytes.u8(12),
                    partyParams = List(PARTY_PARAM_SIZE) { bytes.u8(PARTY_OFFSET + it) },
                ),
            )
        }

        val CSV = RecordCsv(
            listOf(
                CsvColumn<ItemData>("price", 0..65535, { it.price }, { r, v -> r.copy(price = v) }),
                CsvColumn("hold_effect", 0..255, { it.holdEffect }, { r, v -> r.copy(holdEffect = v) }),
                CsvColumn("hold_param", 0..255, { it.holdEffectParam }, { r, v -> r.copy(holdEffectParam = v) }),
                CsvColumn("pluck_effect", 0..255, { it.pluckEffect }, { r, v -> r.copy(pluckEffect = v) }),
                CsvColumn("fling_effect", 0..255, { it.flingEffect }, { r, v -> r.copy(flingEffect = v) }),
                CsvColumn("fling_power", 0..255, { it.flingPower }, { r, v -> r.copy(flingPower = v) }),
                CsvColumn("natural_gift_power", 0..255, { it.naturalGiftPower }, { r, v -> r.copy(naturalGiftPower = v) }),
                CsvColumn("natural_gift_type", 0..31, { it.naturalGiftType }, { r, v -> r.copy(naturalGiftType = v) }),
                CsvColumn("prevent_toss", 0..1, { if (it.preventToss) 1 else 0 }, { r, v -> r.copy(preventToss = v == 1) }),
                CsvColumn("selectable", 0..1, { if (it.selectable) 1 else 0 }, { r, v -> r.copy(selectable = v == 1) }),
                CsvColumn("field_pocket", 0..15, { it.fieldPocket }, { r, v -> r.copy(fieldPocket = v) }),
                CsvColumn("battle_pocket", 0..31, { it.battlePocket }, { r, v -> r.copy(battlePocket = v) }),
                CsvColumn("field_use", 0..255, { it.fieldUseFunc }, { r, v -> r.copy(fieldUseFunc = v) }),
                CsvColumn("battle_use", 0..255, { it.battleUseFunc }, { r, v -> r.copy(battleUseFunc = v) }),
                CsvColumn("party_use", 0..255, { it.partyUse }, { r, v -> r.copy(partyUse = v) }),
            ) + List(PARTY_PARAM_SIZE) { n ->
                CsvColumn<ItemData>("party_$n", 0..255, { it.partyParams[n] }, { r, v -> r.copy(partyParams = r.partyParams.toMutableList().also { l -> l[n] = v }) })
            },
        )

        val SPEC = RecordSpec(HgssLayout.ITEMS, ::parse, ItemData::toBytes)

        fun load(project: RomProject): FoxResult<NarcRecords<ItemData>> = NarcRecords.load(project, SPEC)
    }
}
