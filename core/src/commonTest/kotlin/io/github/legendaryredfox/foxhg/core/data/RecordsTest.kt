package io.github.legendaryredfox.foxhg.core.data

import io.github.legendaryredfox.foxhg.core.FoxResult
import io.github.legendaryredfox.foxhg.core.data.evolution.EvolutionData
import io.github.legendaryredfox.foxhg.core.data.evolution.EvolutionEntry
import io.github.legendaryredfox.foxhg.core.data.evolution.EvolutionMethod
import io.github.legendaryredfox.foxhg.core.data.evolution.TradeEvolutions
import io.github.legendaryredfox.foxhg.core.data.learnset.Learnset
import io.github.legendaryredfox.foxhg.core.data.learnset.LearnsetEntry
import io.github.legendaryredfox.foxhg.core.data.move.MoveData
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

class RecordsTest {
    @Test
    fun learnsetRoundTripsWithPaddingAndPacksMoveAndLevel() {
        val bytes = byteArrayOf(0x21, 0x02, 0x2D, 0x02, 0xFF.toByte(), 0xFF.toByte(), 0, 0) // move 33 lvl 1, move 45 lvl 1
        val parsed = (Learnset.parse(bytes) as FoxResult.Ok).value
        assertEquals(listOf(LearnsetEntry(33, 1), LearnsetEntry(45, 1)), parsed.entries)
        assertContentEquals(bytes, parsed.toBytes())
    }

    @Test
    fun learnsetSortingIsStableAndLimitIsReported() {
        val l = Learnset(listOf(LearnsetEntry(5, 10), LearnsetEntry(6, 3), LearnsetEntry(7, 10), LearnsetEntry(8, 3)))
        assertEquals(listOf(6, 8, 5, 7), l.sorted().entries.map { it.move })
        assertFalse(l.overLimit)
        assertTrue(Learnset(List(21) { LearnsetEntry(1, 1) }).overLimit)
        assertIs<FoxResult.Err>(Learnset.parse(byteArrayOf(1, 2, 3, 4)))
    }

    @Test
    fun evolutionRecordRoundTrips() {
        val random = Random(5)
        repeat(50) {
            val bytes = ByteArray(44) { random.nextInt(256).toByte() }
            val parsed = (EvolutionData.parse(bytes) as FoxResult.Ok).value
            assertContentEquals(bytes, parsed.toBytes())
        }
        assertIs<FoxResult.Err>(EvolutionData.parse(ByteArray(10)))
    }

    @Test
    fun tradeEvolutionsBecomeLevelAndItemUse() {
        val empty = EvolutionEntry(0, 0, 0)
        val kadabra = EvolutionData(listOf(EvolutionEntry(EvolutionMethod.TRADE.id, 0, 65), empty, empty, empty, empty, empty, empty))
        val onix = EvolutionData(listOf(EvolutionEntry(EvolutionMethod.TRADE_ITEM.id, 233, 208), empty, empty, empty, empty, empty, empty))
        val bulbasaur = EvolutionData(listOf(EvolutionEntry(EvolutionMethod.LEVEL.id, 16, 2), empty, empty, empty, empty, empty, empty))
        val (result, changes) = TradeEvolutions.convert(listOf(kadabra, onix, bulbasaur), levelForTrade = 36)
        assertEquals(EvolutionEntry(EvolutionMethod.LEVEL.id, 36, 65), result[0].slots[0])
        assertEquals(EvolutionEntry(EvolutionMethod.STONE.id, 233, 208), result[1].slots[0])
        assertEquals(bulbasaur, result[2])
        assertEquals(listOf(0, 1), changes.map { it.species })
        assertTrue(result.none { r -> r.slots.any { it.known?.isTrade == true } })
    }

    @Test
    fun moveRecordRoundTripsAndPriorityIsSigned() {
        val random = Random(9)
        repeat(100) {
            val bytes = ByteArray(MoveData.SIZE) { random.nextInt(256).toByte() }
            assertContentEquals(bytes, (MoveData.parse(bytes) as FoxResult.Ok).value.toBytes())
        }
        val quick = ByteArray(MoveData.SIZE).also { it[10] = (-6).toByte() }
        assertEquals(-6, (MoveData.parse(quick) as FoxResult.Ok).value.priority)
        assertIs<FoxResult.Err>(MoveData.parse(ByteArray(15)))
    }
}
