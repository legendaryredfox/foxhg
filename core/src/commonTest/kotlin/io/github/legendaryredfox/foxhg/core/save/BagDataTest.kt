package io.github.legendaryredfox.foxhg.core.save

import io.github.legendaryredfox.foxhg.core.FoxResult
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class BagDataTest {
    private val empty = BagData(List(BagData.SLOT_COUNT) { BagSlot.EMPTY }, listOf(0, 0))

    private fun ok(r: FoxResult<BagData>): BagData = (r as? FoxResult.Ok)?.value ?: error((r as FoxResult.Err).message)

    @Test
    fun theSlotsFillTheChunkExactly() {
        assertEquals(BagData.SIZE, BagData.SLOT_COUNT * 4 + 4)
        assertEquals(BagData.SIZE, SaveChunks.byId(SaveChunks.BAG).size)
        assertEquals(486, BagData.SLOT_COUNT)
        assertEquals(0, BagPocket.ITEMS.start)
        assertEquals(165 + 50 + 101 + 12 + 40 + 64 + 24, BagPocket.BATTLE_ITEMS.start)
    }

    @Test
    fun aPocketRoundTripsThroughBytes() {
        val bag = ok(ok(empty.adding(BagPocket.BALLS, 4, 20)).adding(BagPocket.TMS_HMS, 328, 1)).copy(registered = listOf(450, 0))
        val bytes = bag.toBytes()
        assertEquals(BagData.SIZE, bytes.size)
        val again = (BagData.parse(bytes) as FoxResult.Ok).value
        assertEquals(bag, again)
        assertContentEquals(bytes, again.toBytes())
        assertEquals(listOf(BagSlot(4, 20)), again.items(BagPocket.BALLS))
        assertEquals(listOf(BagSlot(328, 1)), again.items(BagPocket.TMS_HMS))
        assertEquals(20, again.count(4))
    }

    @Test
    fun addingMergesAndClampsAndRemovingCloses() {
        var bag = ok(empty.adding(BagPocket.MEDICINE, 17, 600))
        bag = ok(bag.adding(BagPocket.MEDICINE, 17, 600))
        assertEquals(listOf(BagSlot(17, 999)), bag.items(BagPocket.MEDICINE))
        bag = ok(bag.adding(BagPocket.MEDICINE, 18, 1))
        bag = ok(bag.removing(BagPocket.MEDICINE, 17))
        assertEquals(listOf(BagSlot(18, 1)), bag.items(BagPocket.MEDICINE))
        assertEquals(BagSlot.EMPTY, bag.slots[BagPocket.MEDICINE.start + 1])
        assertEquals(99, ok(ok(empty.adding(BagPocket.TMS_HMS, 328, 90)).adding(BagPocket.TMS_HMS, 328, 90)).items(BagPocket.TMS_HMS).single().quantity)
    }

    @Test
    fun badEditsAreRefused() {
        assertTrue(empty.adding(BagPocket.ITEMS, 0, 1) is FoxResult.Err)
        assertTrue(empty.adding(BagPocket.ITEMS, 537, 1) is FoxResult.Err)
        assertTrue(empty.adding(BagPocket.ITEMS, 5, 0) is FoxResult.Err)
        assertTrue(empty.withItems(BagPocket.ITEMS, listOf(BagSlot(5, 1), BagSlot(5, 2))) is FoxResult.Err)
        assertTrue(empty.withItems(BagPocket.MAIL, List(13) { BagSlot(it + 1, 1) }) is FoxResult.Err)
        assertTrue(BagData.parse(ByteArray(10)) is FoxResult.Err)
    }
}
