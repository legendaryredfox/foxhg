package io.github.legendaryredfox.foxhg.core.data.battledisplay

import io.github.legendaryredfox.foxhg.core.FoxResult
import io.github.legendaryredfox.foxhg.core.data.NarcRecords
import io.github.legendaryredfox.foxhg.core.game.HgssLayout
import io.github.legendaryredfox.foxhg.core.project.RomProject

/**
 * Where a species' battle sprites stand: [spriteY] (signed, vertical offset of the sprite), [shadowX] (signed) and
 * [shadowSize] of its shadow, the raw [movement] byte, and the four sprite heights (female back, male back, female
 * front, male front; null when the species has no sprite for that slot, so there is nothing to edit).
 */
data class BattleDisplay(
    val movement: Int,
    val spriteY: Int,
    val shadowX: Int,
    val shadowSize: Int,
    val heights: List<Int?>,
) {
    init { require(heights.size == HEIGHT_SLOTS) { "$HEIGHT_SLOTS heights" } }

    companion object {
        const val HEIGHT_SLOTS = 4
        val HEIGHT_NAMES = listOf("Back, female", "Back, male", "Front, female", "Front, male")
    }
}

/**
 * The battle display data of every species: `a/1/8/0` (one member of [RECORD] bytes per species, only the movement byte
 * and the last three bytes are known, the rest is kept as read) and `a/0/0/5` (four 0 or 1 byte members per species).
 * Layout from DSPRE's `BattleDisplayEditor`, which reads both NARCs the same way for HeartGold and SoulSilver.
 */
class BattleDisplayTable private constructor(
    private val offsets: NarcRecords<ByteArray>,
    private val heights: NarcRecords<ByteArray>,
) {
    val count: Int get() = offsets.records[0].size / RECORD

    fun get(species: Int): BattleDisplay {
        val at = species * RECORD
        val data = offsets.records[0]
        return BattleDisplay(
            movement = data[at + MOVEMENT].toInt() and 0xFF,
            spriteY = data[at + RECORD - 3].toInt(),
            shadowX = data[at + RECORD - 2].toInt(),
            shadowSize = data[at + RECORD - 1].toInt() and 0xFF,
            heights = List(BattleDisplay.HEIGHT_SLOTS) { heights.records[species * BattleDisplay.HEIGHT_SLOTS + it].firstOrNull()?.toInt()?.and(0xFF) },
        )
    }

    fun with(species: Int, value: BattleDisplay): BattleDisplayTable {
        val data = offsets.records[0].copyOf()
        val at = species * RECORD
        data[at + MOVEMENT] = value.movement.toByte()
        data[at + RECORD - 3] = value.spriteY.toByte()
        data[at + RECORD - 2] = value.shadowX.toByte()
        data[at + RECORD - 1] = value.shadowSize.toByte()
        var newHeights = heights
        for (slot in 0 until BattleDisplay.HEIGHT_SLOTS) {
            val index = species * BattleDisplay.HEIGHT_SLOTS + slot
            val height = value.heights[slot]
            // A slot without a file stays without one; only a slot that has a byte is rewritten.
            if (height != null && heights.records[index].size == 1 && (heights.records[index][0].toInt() and 0xFF) != height) {
                newHeights = newHeights.with(index, byteArrayOf(height.toByte()))
            }
        }
        return BattleDisplayTable(offsets.with(0, data), newHeights)
    }

    fun save(project: RomProject): FoxResult<Unit> {
        val a = offsets.save(project)
        if (a is FoxResult.Err) return a
        return heights.save(project)
    }

    companion object {
        const val OFFSETS_PATH = HgssLayout.BATTLE_DISPLAY
        const val HEIGHTS_PATH = HgssLayout.POKEMON_HEIGHTS
        const val RECORD = 89
        const val MOVEMENT = 1

        private val raw: (ByteArray) -> FoxResult<ByteArray> = { FoxResult.Ok(it) }

        fun load(project: RomProject): FoxResult<BattleDisplayTable> {
            val offsets = when (val r = NarcRecords.load(project, OFFSETS_PATH, raw) { it }) {
                is FoxResult.Ok -> r.value
                is FoxResult.Err -> return r
            }
            val heights = when (val r = NarcRecords.load(project, HEIGHTS_PATH, raw) { it }) {
                is FoxResult.Ok -> r.value
                is FoxResult.Err -> return r
            }
            if (offsets.records.size != 1 || offsets.records[0].size % RECORD != 0) return FoxResult.Err("$OFFSETS_PATH is not a list of $RECORD byte records")
            val count = offsets.records[0].size / RECORD
            if (heights.records.size != count * BattleDisplay.HEIGHT_SLOTS) return FoxResult.Err("$HEIGHTS_PATH has ${heights.records.size} members, expected ${count * BattleDisplay.HEIGHT_SLOTS}")
            return FoxResult.Ok(BattleDisplayTable(offsets, heights))
        }
    }
}
