package io.github.legendaryredfox.foxhg.core.data

import io.github.legendaryredfox.foxhg.core.FoxResult
import io.github.legendaryredfox.foxhg.core.data.hiddenitem.HiddenItem
import io.github.legendaryredfox.foxhg.core.data.hiddenitem.HiddenItems
import io.github.legendaryredfox.foxhg.core.game.HgssLayout
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class HiddenItemsTest {
    private fun arm9(vararg entries: HiddenItem, capacity: Int = 4): ByteArray {
        val bytes = ByteArray(0x110000) { 0x5A }
        bytes[HgssLayout.HIDDEN_ITEMS_ARM9_LENGTH] = entries.size.toByte()
        bytes[HgssLayout.HIDDEN_ITEMS_ARM9_CAPACITY] = capacity.toByte()
        // the slots after the table hold other data in a real ARM9
        entries.forEachIndexed { i, e -> e.write(bytes, HgssLayout.HIDDEN_ITEMS_ARM9_TABLE + i * HiddenItem.SIZE) }
        return bytes
    }

    private fun parse(bytes: ByteArray) = (HiddenItems.parse(bytes) as FoxResult.Ok).value

    @Test
    fun entriesRoundTripAndKeepTheUnknownFields() {
        val a = HiddenItem(17, 1, 7, 0x1234, 0)
        val bytes = arm9(a, HiddenItem(92, 2, 0, 0, 225))
        val table = parse(bytes)
        assertEquals(listOf(a, HiddenItem(92, 2, 0, 0, 225)), table.entries)
        assertEquals(4, table.capacity)
        assertContentEquals(bytes, table.applyTo(bytes))
        assertEquals(8000, a.script)
        assertEquals(800 + 225, table.entries[1].flag)
    }

    @Test
    fun shorteningTheTableClearsOnlyTheDroppedSlots() {
        val bytes = arm9(HiddenItem(1, 1, 0, 0, 0), HiddenItem(2, 1, 0, 0, 1), HiddenItem(3, 1, 0, 0, 2))
        val out = parse(bytes).removed(1).applyTo(bytes)
        val reread = parse(out)
        assertEquals(listOf(1, 3), reread.entries.map { it.item }, "the later entry moved up")
        val dropped = HgssLayout.HIDDEN_ITEMS_ARM9_TABLE + 2 * HiddenItem.SIZE
        assertTrue((0 until HiddenItem.SIZE).all { out[dropped + it] == 0.toByte() })
        val beyond = HgssLayout.HIDDEN_ITEMS_ARM9_TABLE + 3 * HiddenItem.SIZE
        assertEquals(0x5A.toByte(), out[beyond], "what follows the table is left alone")
    }

    @Test
    fun aNewSpotGetsTheSmallestFreeIndex() {
        val table = parse(arm9(HiddenItem(1, 1, 0, 0, 0), HiddenItem(2, 1, 0, 0, 2)))
        assertEquals(1, table.freeIndex())
    }

    @Test
    fun aTableLongerThanItsCapacityIsRefused() {
        val bytes = arm9(HiddenItem(1, 1, 0, 0, 0), capacity = 0)
        assertTrue(HiddenItems.parse(bytes) is FoxResult.Err)
        assertTrue(HiddenItems.parse(ByteArray(16)) is FoxResult.Err)
    }
}
