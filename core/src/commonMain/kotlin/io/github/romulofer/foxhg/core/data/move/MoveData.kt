package io.github.romulofer.foxhg.core.data.move

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
 * One 16-byte move record (`a/0/1/1`): effect (u16), category, power, type, accuracy, PP, effect chance, range (u16),
 * priority (s8), flags, then contest data. Layout from the HeartGold decomp's `MoveTbl`, checked against the ROM.
 */
data class MoveData(
    val effect: Int,
    /** 0 physical, 1 special, 2 status. */
    val category: Int,
    val power: Int,
    val type: Int,
    val accuracy: Int,
    val pp: Int,
    val effectChance: Int,
    val range: Int,
    val priority: Int,
    val flags: Int,
    val contestEffect: Int,
    val contestType: Int,
    val contestWord: Int,
) {
    fun toBytes(): ByteArray {
        val b = ByteArray(SIZE)
        b.putU16(0, effect)
        b[2] = category.toByte(); b[3] = power.toByte(); b[4] = type.toByte(); b[5] = accuracy.toByte()
        b[6] = pp.toByte(); b[7] = effectChance.toByte()
        b.putU16(8, range)
        b[10] = priority.toByte(); b[11] = flags.toByte()
        b[12] = contestEffect.toByte(); b[13] = contestType.toByte()
        b.putU16(14, contestWord)
        return b
    }

    companion object {
        const val SIZE = 16
        val CATEGORIES = listOf("Physical", "Special", "Status")

        fun parse(bytes: ByteArray): FoxResult<MoveData> {
            if (bytes.size != SIZE) return FoxResult.Err("Move record must be $SIZE bytes, got ${bytes.size}")
            return FoxResult.Ok(
                MoveData(
                    effect = bytes.u16(0), category = bytes.u8(2), power = bytes.u8(3), type = bytes.u8(4),
                    accuracy = bytes.u8(5), pp = bytes.u8(6), effectChance = bytes.u8(7), range = bytes.u16(8),
                    priority = bytes[10].toInt(), flags = bytes.u8(11), contestEffect = bytes.u8(12),
                    contestType = bytes.u8(13), contestWord = bytes.u16(14),
                ),
            )
        }

        val CSV = RecordCsv(
            listOf(
                CsvColumn<MoveData>("effect", 0..65535, { it.effect }, { r, v -> r.copy(effect = v) }),
                CsvColumn("category", 0..255, { it.category }, { r, v -> r.copy(category = v) }),
                CsvColumn("power", 0..255, { it.power }, { r, v -> r.copy(power = v) }),
                CsvColumn("type", 0..255, { it.type }, { r, v -> r.copy(type = v) }),
                CsvColumn("accuracy", 0..255, { it.accuracy }, { r, v -> r.copy(accuracy = v) }),
                CsvColumn("pp", 0..255, { it.pp }, { r, v -> r.copy(pp = v) }),
                CsvColumn("effect_chance", 0..255, { it.effectChance }, { r, v -> r.copy(effectChance = v) }),
                CsvColumn("range", 0..65535, { it.range }, { r, v -> r.copy(range = v) }),
                CsvColumn("priority", -128..127, { it.priority }, { r, v -> r.copy(priority = v) }),
                CsvColumn("flags", 0..255, { it.flags }, { r, v -> r.copy(flags = v) }),
                CsvColumn("contest_effect", 0..255, { it.contestEffect }, { r, v -> r.copy(contestEffect = v) }),
                CsvColumn("contest_type", 0..255, { it.contestType }, { r, v -> r.copy(contestType = v) }),
                CsvColumn("contest_word", 0..65535, { it.contestWord }, { r, v -> r.copy(contestWord = v) }),
            ),
        )

        val SPEC = RecordSpec(HgssLayout.MOVES, ::parse, MoveData::toBytes)

        fun load(project: RomProject): FoxResult<NarcRecords<MoveData>> = NarcRecords.load(project, SPEC)
    }
}
